package br.com.pocooptimizer.scanner

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import br.com.pocooptimizer.domain.*
import br.com.pocooptimizer.storage.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

data class AppAudit(val name: String, val pkg: String, val icon: Bitmap?, val source: String,
    val permissions: List<String>, val signatures: List<String>, val findings: List<Finding>)
data class SecurityReport(val apps: List<AppAudit> = emptyList(), val findings: List<Finding> = emptyList(),
    val warnings: List<String> = emptyList(), val checks: List<String> = emptyList())
data class FileAudit(val name: String, val mime: String, val size: Long, val hash: String, val details: String)

class SecurityScanner(private val context: Context, private val storage: SafStorage) {
    private val pm = context.packageManager
    @Suppress("DEPRECATION")
    suspend fun scan(progress: (Int, Int) -> Unit): SecurityReport {
        val warnings = mutableListOf("Visibilidade limitada aos apps iniciáveis e ao próprio Poco Optimizer. Apps ocultos ou restritos podem não aparecer.",
            "Sem motor antivírus ou reputação externa: nenhuma ameaça é confirmada por esta análise. Assinatura identifica o signatário, não garante segurança.")
        val findings = mutableListOf<Finding>()
        val checks = mutableListOf<String>()
        try {
            val locked = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
            checks += "Bloqueio seguro configurado: ${if(locked) "sim" else "não"}"
            if(!locked) findings += Finding("Bloqueio de tela ausente", "O Android não informou credencial segura.", "Configure PIN, senha ou padrão nas configurações de segurança.", Risk.POTENTIAL)
            val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            checks += "Depuração USB: ${if(adb) "ativada" else "desativada"}"
            if(adb) findings += Finding("Depuração USB ativada", "ADB_ENABLED está ativo.", "Desative quando não estiver desenvolvendo e revise computadores autorizados.")
        } catch (e: Exception) { warnings += "Algumas configurações de segurança não puderam ser consultadas: ${e.javaClass.simpleName}" }
        val packages = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName } + context.packageName).distinct().sorted()
        val apps = packages.mapIndexedNotNull { index, pkg ->
            currentCoroutineContext().ensureActive()
            try {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
                val flags = info.requestedPermissionsFlags ?: intArrayOf()
                val granted = info.requestedPermissions.orEmpty().filterIndexed { i, _ ->
                    i < flags.size && flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                }.toSet()
                val perms = info.requestedPermissions.orEmpty().map { "$it — ${if(it in granted) "concedida" else "não concedida"}" }
                val source = try {
                    if(Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else pm.getInstallerPackageName(pkg)
                } catch (_: Exception) { null }
                val risks = Analysis.permissionFindings(granted).toMutableList()
                if(source != null && source !in setOf("com.android.vending", "com.xiaomi.mipicks", "com.xiaomi.market", "com.google.android.packageinstaller", "com.android.packageinstaller"))
                    risks += Finding("Origem de instalação alternativa", "Instalador informado: $source", "Verifique se reconhece a origem; isso não caracteriza malware.")
                if(source in setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.miui.packageinstaller"))
                    risks += Finding("Instalado por um instalador de pacotes", "Origem observada: $source", "Confirme de onde veio o APK. Instalação manual não prova ameaça.")
                val appInfo = info.applicationInfo
                val icon = try { appInfo?.loadIcon(pm)?.toBitmap(48,48) } catch (_: Exception) { null }
                AppAudit(appInfo?.loadLabel(pm)?.toString() ?: pkg, pkg, icon, source ?: "Origem não informada pelo Android (não implica fonte maliciosa)",
                    perms, info.signingInfo?.apkContentsSigners.orEmpty().map { digest(it.toByteArray()) }, risks)
            } catch (e: Exception) { warnings += "App não consultado: $pkg (${e.javaClass.simpleName})"; null }
            finally { progress(index+1, packages.size) }
        }
        checks += "Consultados nomes, origem, permissões e certificados de ${apps.size} apps visíveis."
        return SecurityReport(apps.sortedBy { it.name.lowercase() }, findings, warnings, checks)
    }
    @Suppress("DEPRECATION")
    suspend fun inspect(uri: Uri, progress: (Long) -> Unit): FileAudit {
        val meta = storage.selectedMetadata(uri)
        val hash = Hashing.sha256(storage.open(uri.toString()), progress)
        val header = ByteArray(8)
        val count = storage.open(uri.toString()).use { it.read(header) }
        val zip = count >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte()
        var details = "Tipo informado pelo provedor: ${meta.mime}. " + if(zip) "Cabeçalho compatível com ZIP/APK. " else "Tipo inferido apenas por metadados; não executado. "
        if(meta.name.endsWith(".apk", true)) {
            val temp = File.createTempFile("audit-", ".apk", context.cacheDir)
            try {
                var size = 0L
                storage.open(uri.toString()).use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if(n < 0) break
                        size += n; require(size <= 256L * 1024 * 1024) { "APK acima de 256 MiB: metadados internos não inspecionados" }
                        output.write(buffer,0,n)
                    }
                } }
                require(Hashing.sha256(temp.inputStream()) == hash) { "Arquivo mudou durante a inspeção; selecione novamente" }
                val info = pm.getPackageArchiveInfo(temp.path, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
                details += if(info == null) "O Android não reconheceu um APK válido. Pode ser corrompido ou incompatível; não é confirmação de malware."
                else "Pacote: ${info.packageName}; versão: ${info.versionName}. Permissões declaradas: ${info.requestedPermissions.orEmpty().joinToString()}. Certificados SHA-256: ${info.signingInfo?.apkContentsSigners.orEmpty().joinToString { digest(it.toByteArray()) }}."
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { details += "Metadados internos indisponíveis: ${e.message}." }
            finally { temp.delete() }
        }
        details += " Hash serve para comparação de integridade; não comprova ausência de vírus. Nenhum dado foi enviado."
        return FileAudit(meta.name, meta.mime, meta.size, hash, details)
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
