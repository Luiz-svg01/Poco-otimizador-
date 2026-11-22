package br.com.pocooptimizer

import br.com.pocooptimizer.domain.*
import org.junit.Assert.*
import org.junit.Test

class AnalysisTest {
    private fun file(name: String, path: String = name, size: Long = 10, modified: Long = 1) = FileEntry(name,name,path,size,modified,"application/octet-stream",true)
    @Test fun personalFilesAreNeverClassifiedAsTrash() {
        assertTrue(Analysis.categories(file("photo.jpg"),200*Analysis.DAY).isEmpty())
        assertEquals(listOf("Documento antigo"),Analysis.categories(file("report.pdf"),200*Analysis.DAY))
    }
    @Test fun oldDownloadsRequireKnownDateAndDownloadDirectory() {
        assertTrue(Analysis.categories(file("a.bin","Download/a.bin",modified=0),200*Analysis.DAY).isEmpty())
        assertEquals(listOf("Download antigo"),Analysis.categories(file("a.bin","Download/a.bin"),200*Analysis.DAY))
    }
    @Test fun thresholdsAndArchiveTypesAreRespected() {
        assertEquals(listOf("Arquivo grande","Arquivo compactado"),Analysis.categories(file("a.zip",size=101*1024L*1024),1000))
        assertFalse(Analysis.categories(file("photo.jpg",size=100),1000,large=101).contains("Arquivo grande"))
    }
    @Test fun finalDuplicateCannotBeSelected() {
        val a=file("a"); val b=file("b"); val groups=listOf(DuplicateGroup("hash",listOf(a,b)))
        assertTrue(Analysis.preservesCopies(setOf("a"),groups))
        assertFalse(Analysis.preservesCopies(setOf("a","b"),groups))
    }
    @Test fun grantedPermissionCombinationIsOnlyPotentialRisk() {
        assertTrue(Analysis.permissionFindings(emptySet()).isEmpty())
        val risks=Analysis.permissionFindings(setOf("android.permission.READ_SMS","android.permission.READ_CONTACTS"))
        assertTrue(risks.any { it.risk==Risk.POTENTIAL })
        assertFalse(risks.any { it.risk==Risk.CONFIRMED })
    }
    @Test fun realDeviceIndicatorsTriggerUsefulAdvice() {
        val d=DeviceSnapshot("test","test",100L*1024*1024*1024,1024L*1024*1024,8,2,true,10,false,48f,false,3)
        assertEquals(5,Analysis.performance(d).size)
        assertTrue(Analysis.performance(d.copy(freeStorage=d.totalStorage,lowMemory=false,battery=null,temperature=null,batteryHealth=null)).isEmpty())
    }
}
