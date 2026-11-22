package br.com.pocooptimizer.domain

data class FileEntry(val uri: String, val name: String, val path: String, val size: Long,
    val modified: Long, val mime: String, val canDelete: Boolean)
data class DuplicateGroup(val hash: String, val files: List<FileEntry>)
data class StorageReport(val files: List<FileEntry> = emptyList(), val duplicates: List<DuplicateGroup> = emptyList(),
    val warnings: List<String> = emptyList(), val hashes: Map<String, String> = emptyMap())
data class Finding(val title: String, val observation: String, val recommendation: String,
    val risk: Risk = Risk.ATTENTION, val confidence: String = "Alta para o fato observado; não confirma malware")
enum class Risk(val label: String) { NONE("Nenhum problema identificado"), ATTENTION("Atenção recomendada"), POTENTIAL("Risco potencial"), CONFIRMED("Ameaça confirmada por fonte confiável") }
data class DeviceSnapshot(val model: String, val android: String, val totalStorage: Long, val freeStorage: Long,
    val totalRam: Long, val freeRam: Long, val lowMemory: Boolean, val battery: Int?, val charging: Boolean?,
    val temperature: Float?, val powerSave: Boolean, val batteryHealth: Int?)
data class PreparedFile(val file: FileEntry, val hash: String)
data class DeletePlan(val selected: List<PreparedFile>, val groups: List<DuplicateGroup>, val createdAt: Long)
data class DeleteItem(val name: String, val path: String, val bytes: Long, val deleted: Boolean, val reason: String)
data class DeleteResult(val items: List<DeleteItem>) {
    val bytes: Long get() = items.filter { it.deleted }.sumOf { it.bytes }
    val deleted: Int get() = items.count { it.deleted }
}
