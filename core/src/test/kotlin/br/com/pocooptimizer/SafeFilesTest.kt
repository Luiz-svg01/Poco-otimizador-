package br.com.pocooptimizer

import br.com.pocooptimizer.domain.*
import br.com.pocooptimizer.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class SafeFilesTest {
    private class DiskAccess(val root: File) : FileAccess {
        var failDelete=false
        fun entry(name: String)=stat(File(root,name).path)!!
        override fun stat(uri: String): FileEntry? {
            val f=File(uri); if(!f.exists()) return null
            return FileEntry(uri,f.name,f.path,f.length(),f.lastModified(),"application/octet-stream",true)
        }
        override fun open(uri: String)=File(uri).inputStream()
        override fun delete(uri: String)=!failDelete && File(uri).delete()
    }
    private fun withDisk(block: suspend (DiskAccess) -> Unit) = runBlocking {
        val dir=Files.createTempDirectory("poco-test").toFile()
        try { block(DiskAccess(dir)) } finally { dir.deleteRecursively() }
    }
    private suspend fun report(a: DiskAccess): StorageReport {
        val files=a.root.listFiles()!!.map { a.entry(it.name) }
        val hashes=files.associate { it.uri to Hashing.sha256(a.open(it.uri)) }
        val groups=Duplicates.groups(files,hashes)
        return StorageReport(files,groups,hashes=hashes)
    }
    @Test fun discoveryRequiresTwoDistinctKnownSizeFiles() {
        fun f(id: String,size: Long)=FileEntry(id,id,id,size,0,"text/plain",true)
        val a=f("a",3); val b=f("b",3); val unknown=f("u",-1)
        assertEquals(listOf(a,b),Duplicates.candidates(listOf(a,a,b,unknown)))
        assertTrue(Duplicates.groups(listOf(a,a),mapOf("a" to "hash")).isEmpty())
        assertTrue(Duplicates.groups(listOf(a,b),mapOf("a" to "hash")).isEmpty())
    }
    @Test fun sha256UsesActualBytes() = runBlocking {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",Hashing.sha256(ByteArrayInputStream("abc".toByteArray())))
    }
    @Test fun equalSizeDifferentContentsAreNotDuplicates() = withDisk { a ->
        File(a.root,"a").writeText("abc"); File(a.root,"b").writeText("def")
        assertTrue(report(a).duplicates.isEmpty())
    }
    @Test fun confirmedDeletionAccountsOnlyRealDeletedBytes() = withDisk { a ->
        File(a.root,"a").writeText("abc"); File(a.root,"b").writeText("abc")
        val r=report(a); val id=a.entry("a").uri
        val plan=SafeFiles(a).prepare(r,setOf(id),100)
        assertTrue(File(id).exists())
        val result=SafeFiles(a).execute(plan,101)
        assertEquals(3L,result.bytes); assertEquals(1,result.deleted); assertTrue(File(a.root,"b").exists())
    }
    @Test fun allCopiesRejectedBeforeAnyDeletion() = withDisk { a ->
        File(a.root,"a").writeText("abc"); File(a.root,"b").writeText("abc")
        val r=report(a)
        try { SafeFiles(a).prepare(r,r.files.map { it.uri }.toSet(),100); fail("must reject") } catch(_: IllegalArgumentException) {}
        assertEquals(2,a.root.listFiles()!!.size)
    }
    @Test fun changedContentsWithSameMetadataAreNotDeleted() = withDisk { a ->
        val f=File(a.root,"a"); f.writeText("abc")
        val plan=SafeFiles(a).prepare(report(a),setOf(f.path),100)
        val old=f.lastModified(); f.writeText("def"); f.setLastModified(old)
        val result=SafeFiles(a).execute(plan,101)
        assertEquals(0,result.deleted); assertTrue(f.exists())
    }
    @Test fun missingKeeperProtectsLastCopy() = withDisk { a ->
        File(a.root,"a").writeText("abc"); File(a.root,"b").writeText("abc")
        val plan=SafeFiles(a).prepare(report(a),setOf(a.entry("a").uri),100)
        File(a.root,"b").delete()
        assertEquals(0,SafeFiles(a).execute(plan,101).deleted)
        assertTrue(File(a.root,"a").exists())
    }
    @Test fun modifiedKeeperProtectsSelectedCopy() = withDisk { a ->
        File(a.root,"a").writeText("abc"); val keeper=File(a.root,"b").apply { writeText("abc") }
        val plan=SafeFiles(a).prepare(report(a),setOf(a.entry("a").uri),100)
        val time=keeper.lastModified(); keeper.writeText("def"); keeper.setLastModified(time)
        assertEquals(0,SafeFiles(a).execute(plan,101).deleted)
        assertTrue(File(a.root,"a").exists())
    }
    @Test fun inaccurateSizeCannotInflateRecoveredBytes() = runBlocking {
        val file=FileEntry("x","x","x",500,0,"text/plain",true)
        val access=object: FileAccess {
            override fun stat(uri:String)=file
            override fun open(uri:String)=ByteArrayInputStream("abc".toByteArray())
            override fun delete(uri:String):Boolean { fail("Must not delete"); return false }
        }
        try { SafeFiles(access).prepare(StorageReport(listOf(file)),setOf("x"),100); fail("must reject size") }
        catch(_:IllegalArgumentException) {}
    }
    @Test fun revokedAccessDoesNotDeleteOrCountBytes() = runBlocking {
        val file=FileEntry("x","x","x",3,0,"text/plain",true)
        var revoked=false
        val access=object: FileAccess {
            override fun stat(uri:String):FileEntry { if(revoked) throw SecurityException("revoked"); return file }
            override fun open(uri:String)=ByteArrayInputStream("abc".toByteArray())
            override fun delete(uri:String):Boolean { fail("Must not delete"); return false }
        }
        val safe=SafeFiles(access)
        val plan=safe.prepare(StorageReport(listOf(file)),setOf("x"),100)
        revoked=true
        val result=safe.execute(plan,101)
        assertEquals(0L,result.bytes); assertEquals(0,result.deleted)
    }
    @Test fun failedProviderDoesNotInventRecoveredSpace() = withDisk { a ->
        File(a.root,"a").writeText("abc")
        val plan=SafeFiles(a).prepare(report(a),setOf(a.entry("a").uri),100)
        a.failDelete=true
        assertEquals(0L,SafeFiles(a).execute(plan,101).bytes)
    }
    @Test fun expiredConfirmationRejected() = withDisk { a ->
        File(a.root,"a").writeText("abc")
        val plan=SafeFiles(a).prepare(report(a),setOf(a.entry("a").uri),100)
        try { SafeFiles(a).execute(plan,400_000); fail("must expire") } catch(_: IllegalArgumentException) {}
        assertTrue(File(a.root,"a").exists())
    }
    @Test fun unknownSelectionCannotDeleteUnscannedFile() = withDisk { a ->
        try { SafeFiles(a).prepare(StorageReport(),setOf("unknown"),100); fail("must reject") } catch(_: IllegalArgumentException) {}
    }
    @Test fun hashingSupportsCancellationAndClosesStream() = runBlocking {
        var closed=false
        val input=object: ByteArrayInputStream(ByteArray(1024*1024)) { override fun close() { closed=true; super.close() } }
        val job=launch(start=CoroutineStart.UNDISPATCHED) { Hashing.sha256(input) { if(it>0) cancel() } }
        job.join(); assertTrue(job.isCancelled); assertTrue(closed)
    }
}
