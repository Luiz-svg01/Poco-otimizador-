package br.com.pocooptimizer

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.pocooptimizer.domain.*
import br.com.pocooptimizer.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafIntegrationTest {
    @Test fun providerBytesHashAndConfirmedDeletion() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val dir=File(context.cacheDir,"saf-fixtures").apply { mkdirs() }
        val a=File(dir,"a.txt").apply { writeText("abc") }
        val b=File(dir,"b.txt").apply { writeText("abc") }
        try {
            val access=SafStorage(context)
            fun uri(name:String)=DocumentsContract.buildDocumentUri("${context.packageName}.fixtures",name).toString()
            val fa=access.stat(uri("a.txt"))!!; val fb=access.stat(uri("b.txt"))!!
            val hash=Hashing.sha256(access.open(fa.uri))
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",hash)
            val report=StorageReport(listOf(fa,fb),listOf(DuplicateGroup(hash,listOf(fa,fb))))
            val safe=SafeFiles(access)
            val plan=safe.prepare(report,setOf(fa.uri),100)
            assertTrue(a.exists()); assertTrue(b.exists())
            val result=safe.execute(plan,101)
            assertEquals(1,result.deleted); assertEquals(3L,result.bytes)
            assertFalse(a.exists()); assertTrue(b.exists()); assertNull(access.stat(fa.uri))
        } finally { dir.deleteRecursively() }
    }
}
