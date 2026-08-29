// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManagerTest.kt
package com.isardomains.sameview.ui.wackelbild

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WackelbildTempFileManagerTest {

    private val cacheDir = Files.createTempDirectory("wb-tempfile-test-").toFile()

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun createOperationDir_createsDirectoryUnderCacheDirWackelbild() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-1")
        assertTrue(dir.isDirectory)
        assertEquals(cacheDir, dir.parentFile?.parentFile)
        assertEquals("wackelbild", dir.parentFile?.name)
    }

    @Test
    fun createOperationDir_differentCallsWithoutExplicitId_produceDifferentDirs() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dirA = manager.createOperationDir()
        val dirB = manager.createOperationDir()
        assertNotEquals(dirA, dirB)
    }

    @Test
    fun referenceCandidateFile_isImageOneJpg() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-2")
        assertEquals("image_one.jpg", manager.referenceCandidateFile(dir).name)
    }

    @Test
    fun captureCandidateFile_isImageTwoJpg() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-3")
        assertEquals("image_two.jpg", manager.captureCandidateFile(dir).name)
    }

    @Test
    fun candidateFiles_areDeterministic_samePathAcrossCalls() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-4")
        assertEquals(manager.referenceCandidateFile(dir), manager.referenceCandidateFile(dir))
        assertEquals(manager.captureCandidateFile(dir), manager.captureCandidateFile(dir))
    }
}
