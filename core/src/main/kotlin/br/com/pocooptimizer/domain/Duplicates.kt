package br.com.pocooptimizer.domain

object Duplicates {
    fun candidates(files: List<FileEntry>): List<FileEntry> = files.distinctBy { it.uri }
        .filter { it.size >= 0 }.groupBy { it.size }.values.filter { it.size > 1 }.flatten()
    fun groups(files: List<FileEntry>, hashes: Map<String,String>): List<DuplicateGroup> =
        files.distinctBy { it.uri }.filter { it.size >= 0 && hashes.containsKey(it.uri) }
            .groupBy { "${it.size}:${hashes.getValue(it.uri)}" }.values.filter { it.size > 1 }
            .map { DuplicateGroup(hashes.getValue(it.first().uri),it) }
}
