package br.com.pocooptimizer.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import br.com.pocooptimizer.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream

class SafStorage(private val context: Context) : FileAccess {
    private val resolver = context.contentResolver
    private val known = mutableMapOf<String, FileEntry>()
    fun roots(): List<Uri> = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
    fun grant(uri: Uri) {
        try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun revoke(uri: Uri) {
        resolver.persistedUriPermissions.firstOrNull { it.uri == uri }?.let {
            val flags = (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            resolver.releasePersistableUriPermission(uri, flags)
        }
    }
    private val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS)
    override fun stat(uri: String): FileEntry? { return try {
        val u = Uri.parse(uri)
        resolver.query(u, columns, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            return FileEntry(uri, c.getString(1) ?: "Sem nome", known[uri]?.path ?: uri,
                if (c.isNull(2)) -1 else c.getLong(2), if (c.isNull(3)) 0 else c.getLong(3), c.getString(4) ?: "application/octet-stream",
                c.getInt(5) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)
        } ?: error("Provedor não respondeu à consulta")
        null
    } catch (_: java.io.FileNotFoundException) { null } }
    override fun open(uri: String): InputStream = requireNotNull(resolver.openInputStream(Uri.parse(uri))) { "Não foi possível abrir o arquivo" }
    override fun delete(uri: String): Boolean = DocumentsContract.deleteDocument(resolver, Uri.parse(uri))

    suspend fun scan(hashDuplicates: Boolean, progress: (String, Int, Int?) -> Unit): StorageReport {
        known.clear()
        val warnings = mutableListOf<String>()
        val files = mutableListOf<FileEntry>()
        val visited = mutableSetOf<String>()
        val roots = roots()
        if (roots.isEmpty()) warnings += "Nenhuma pasta autorizada. Armazenamento pessoal não foi analisado."
        val queue = ArrayDeque<Triple<Uri, String, Int>>()
        roots.forEach { root ->
            val id = DocumentsContract.getTreeDocumentId(root)
            queue.add(Triple(DocumentsContract.buildDocumentUriUsingTree(root, id), id.substringAfter(':'), 0))
        }
        var dirs = 0
        var processed = 0
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (processed >= 25_000 || dirs >= 5_000) { warnings += "Limite de 25.000 entradas ou 5.000 pastas atingido; análise parcial. Selecione pastas menores."; break }
            val (dir, path, depth) = queue.removeFirst()
            val identity = "${dir.authority}:${DocumentsContract.getDocumentId(dir)}"
            if (!visited.add(identity)) continue
            dirs++
            if (depth > 40) { warnings += "Profundidade máxima atingida em $path"; continue }
            try {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
                val cursor = resolver.query(children, columns, null, null, null) ?: error("Consulta sem resposta")
                cursor.use { c ->
                    while (processed < 25_000 && c.moveToNext()) {
                        currentCoroutineContext().ensureActive(); processed++
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: "Sem nome"
                        val uri = DocumentsContract.buildDocumentUriUsingTree(dir, id)
                        val mime = c.getString(4) ?: "application/octet-stream"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (queue.size < 5_000) queue.add(Triple(uri, "$path/$name", depth + 1))
                            else warnings += "Pastas excedentes ignoradas em $path"
                        } else {
                            if (!visited.add("${uri.authority}:$id")) continue
                            if (c.getInt(5) and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0) {
                                warnings += "Documento virtual ignorado: $path/$name"; continue
                            }
                            val f = FileEntry(uri.toString(), name, "$path/$name", if(c.isNull(2)) -1 else c.getLong(2),
                                if(c.isNull(3)) 0 else c.getLong(3), mime, c.getInt(5) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)
                            files += f; known[f.uri] = f
                        }
                        if (processed % 50 == 0) progress("Enumerando arquivos • $processed entradas", processed, null)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { warnings += "Pasta inacessível: $path (${e.javaClass.simpleName})" }
        }
        progress("Enumeração concluída • ${files.size} arquivos", files.size, files.size)
        val hashes = mutableMapOf<String, String>()
        if (hashDuplicates) {
            val candidates = Duplicates.candidates(files)
            candidates.forEachIndexed { i, file ->
                currentCoroutineContext().ensureActive()
                try {
                    var last = 0L
                    val hash = Hashing.sha256(open(file.uri)) { bytes ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - last > 150) {
                            progress("SHA-256 ${i + 1}/${candidates.size} • ${file.name} • $bytes bytes", i, candidates.size); last = now
                        }
                    }
                    if (stat(file.uri) == file) hashes[file.uri] = hash
                    else warnings += "Arquivo alterado durante análise: ${file.path}"
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { warnings += "Hash indisponível: ${file.path} (${e.javaClass.simpleName})" }
                progress("Hashes verificados: ${i + 1}/${candidates.size}", i + 1, candidates.size)
            }
        } else warnings += "Detecção de duplicatas desativada nas preferências."
        val groups = Duplicates.groups(files,hashes)
        return StorageReport(files, groups, warnings.distinct(), hashes)
    }
    fun selectedMetadata(uri: Uri): FileEntry {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if(c.moveToFirst()) return FileEntry(uri.toString(), c.getString(0) ?: "Arquivo", uri.toString(),
                if(c.isNull(1)) -1 else c.getLong(1), 0, resolver.getType(uri) ?: "application/octet-stream", false)
        }
        error("Metadados indisponíveis")
    }
}
