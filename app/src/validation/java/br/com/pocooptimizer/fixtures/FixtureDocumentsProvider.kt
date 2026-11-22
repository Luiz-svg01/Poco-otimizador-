package br.com.pocooptimizer.fixtures

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Validation-only fixture protected by the platform MANAGE_DOCUMENTS permission. Cannot access user-selected documents or external paths. */
class FixtureDocumentsProvider : DocumentsProvider() {
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS)
    override fun onCreate() = true
    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9._-]+")) && id!=".." && id!=".")
        return File(requireNotNull(context).cacheDir,"saf-fixtures/$id")
    }
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(arrayOf("root_id"))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val f=file(documentId); if(!f.exists()) throw FileNotFoundException(documentId)
        val cols=projection?.map { it }?.toTypedArray() ?: columns
        return MatrixCursor(cols).apply {
            val row=newRow()
            cols.forEach { col -> row.add(when(col) {
                Document.COLUMN_DOCUMENT_ID -> documentId
                Document.COLUMN_DISPLAY_NAME -> f.name
                Document.COLUMN_SIZE -> f.length()
                Document.COLUMN_LAST_MODIFIED -> f.lastModified()
                Document.COLUMN_MIME_TYPE -> "application/octet-stream"
                Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_DELETE
                else -> null
            }) }
        }
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = MatrixCursor(columns)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) = ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.MODE_READ_ONLY)
    override fun deleteDocument(documentId: String) { if(!file(documentId).delete()) throw FileNotFoundException("delete failed") }
}
