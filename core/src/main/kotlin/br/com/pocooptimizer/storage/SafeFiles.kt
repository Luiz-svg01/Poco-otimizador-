package br.com.pocooptimizer.storage

import br.com.pocooptimizer.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

interface FileAccess {
    fun stat(uri: String): FileEntry?
    fun open(uri: String): InputStream
    fun delete(uri: String): Boolean
}
object Hashing {
    suspend fun sha256(input: InputStream, progress: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var bytes = 0L
        input.use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count); bytes += count; progress(bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
class SafeFiles(private val access: FileAccess) {
    suspend fun prepare(report: StorageReport, ids: Set<String>, now: Long): DeletePlan {
        require(ids.isNotEmpty()) { "Selecione arquivos." }
        require(Analysis.preservesCopies(ids, report.duplicates)) { "Mantenha ao menos uma cópia de cada grupo." }
        val index = report.files.associateBy { it.uri }
        val prepared = ids.map { id ->
            val file = requireNotNull(index[id]) { "Arquivo não pertence à análise." }
            require(file.size >= 0) { "Tamanho desconhecido; exclusão bloqueada." }
            require(file.canDelete) { "O provedor não autoriza excluir ${file.name}." }
            require(access.stat(id) == file) { "Arquivo alterado. Analise novamente: ${file.name}" }
            var bytesRead = 0L
            val hash = Hashing.sha256(access.open(id)) { bytesRead = it }
            require(bytesRead == file.size) { "O tamanho informado difere do conteúdo lido." }
            require(access.stat(id) == file) { "Arquivo mudou durante a leitura." }
            report.hashes[id]?.let { require(it == hash) { "Conteúdo alterado. Analise novamente." } }
            PreparedFile(file, hash)
        }
        return DeletePlan(prepared, report.duplicates, now)
    }
    suspend fun execute(plan: DeletePlan, now: Long, record: suspend (DeleteItem) -> Unit = {}): DeleteResult {
        require(now - plan.createdAt in 0..300_000) { "Confirmação expirada. Revise novamente." }
        val ids = plan.selected.map { it.file.uri }.toSet()
        require(ids.size == plan.selected.size && Analysis.preservesCopies(ids, plan.groups)) { "Seleção inválida." }
        val results = mutableListOf<DeleteItem>()
        for (item in plan.selected) {
            currentCoroutineContext().ensureActive()
            val f = item.file
            val result = try {
                require(access.stat(f.uri) == f) { "Metadados alterados ou acesso revogado." }
                var bytesRead = 0L
                require(Hashing.sha256(access.open(f.uri)) { bytesRead = it } == item.hash) { "Conteúdo mudou desde a confirmação." }
                require(bytesRead == f.size) { "Tamanho alterado desde a confirmação." }
                // A retained duplicate must still exist AND match immediately before each deletion.
                for (group in plan.groups.filter { g -> g.files.any { it.uri == f.uri } }) {
                    var keeperFound = false
                    for (keeper in group.files.filter { it.uri !in ids }) {
                        try {
                            if (access.stat(keeper.uri) == keeper && Hashing.sha256(access.open(keeper.uri)) == group.hash) keeperFound = true
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { /* Try another retained copy. */ }
                        if (keeperFound) break
                    }
                    require(keeperFound) { "Nenhuma cópia preservada pôde ser validada." }
                }
                require(access.stat(f.uri) == f) { "Arquivo alterado durante validação." }
                if (access.delete(f.uri) && access.stat(f.uri) == null)
                    DeleteItem(f.name, f.path, f.size, true, "Exclusão confirmada pelo provedor")
                else DeleteItem(f.name, f.path, 0, false, "O provedor não confirmou a exclusão")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { DeleteItem(f.name, f.path, 0, false, e.message ?: "Falha de acesso") }
            results += result; record(result)
        }
        return DeleteResult(results)
    }
}
