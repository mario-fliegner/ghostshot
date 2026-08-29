// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManagerTest.kt
package com.isardomains.sameview.ui.wackelbild

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

    // --- deleteOperationDir ---

    @Test
    fun deleteOperationDir_removesDirectoryRecursively() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-delete")
        manager.referenceCandidateFile(dir).writeText("ref")
        manager.captureCandidateFile(dir).writeText("cap")

        manager.deleteOperationDir(dir)

        assertFalse(dir.exists())
    }

    @Test
    fun deleteOperationDir_isIdempotent() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-idempotent")
        manager.referenceCandidateFile(dir).writeText("ref")

        manager.deleteOperationDir(dir)
        manager.deleteOperationDir(dir) // second call must not throw

        assertFalse(dir.exists())
    }

    @Test
    fun deleteOperationDir_missingDirectory_isNoOp() {
        val manager = WackelbildTempFileManager(cacheDir)
        val neverCreated = File(cacheDir, "wackelbild/never-created")

        manager.deleteOperationDir(neverCreated) // must not throw

        assertFalse(neverCreated.exists())
    }

    @Test
    fun deleteOperationDir_removesNestedCandidateFiles() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dir = manager.createOperationDir("op-nested")
        val nestedDir = File(dir, "nested")
        nestedDir.mkdirs()
        val nestedFile = File(nestedDir, "leftover.tmp")
        nestedFile.writeText("data")

        manager.deleteOperationDir(dir)

        assertFalse(nestedFile.exists())
        assertFalse(nestedDir.exists())
        assertFalse(dir.exists())
    }

    @Test
    fun deleteOperationDir_pathOutsideRoot_isRejectedSafely() {
        val manager = WackelbildTempFileManager(cacheDir)
        val outsideSibling = File(cacheDir, "not-wackelbild")
        outsideSibling.mkdirs()
        val sentinel = File(outsideSibling, "sentinel.txt")
        sentinel.writeText("do not delete")

        manager.deleteOperationDir(outsideSibling) // must be rejected, not deleted

        assertTrue(sentinel.exists())
        assertEquals("do not delete", sentinel.readText())
    }

    @Test
    fun deleteOperationDir_cannotDeleteSessionsDirectory() {
        val manager = WackelbildTempFileManager(cacheDir)
        val sessionsDir = File(cacheDir, "sessions/2026-06-21_10-00-00")
        sessionsDir.mkdirs()
        val referenceFile = File(sessionsDir, "reference.jpg")
        referenceFile.writeText("persisted session data")

        manager.deleteOperationDir(sessionsDir) // must be rejected, not deleted

        assertTrue(referenceFile.exists())
        assertEquals("persisted session data", referenceFile.readText())
    }

    @Test
    fun deleteOperationDir_wackelbildRootItself_isRejectedSafely() {
        val manager = WackelbildTempFileManager(cacheDir)
        manager.createOperationDir("op-present")
        val wackelbildRoot = File(cacheDir, "wackelbild")

        manager.deleteOperationDir(wackelbildRoot) // the root is not a valid operation dir

        assertTrue(wackelbildRoot.exists())
    }

    // --- sweepStaleOperationDirs ---

    @Test
    fun sweepStaleOperationDirs_removesOrphanOperationDirectories() {
        val manager = WackelbildTempFileManager(cacheDir)
        val dirA = manager.createOperationDir("op-a")
        val dirB = manager.createOperationDir("op-b")
        manager.referenceCandidateFile(dirA).writeText("ref")
        manager.captureCandidateFile(dirB).writeText("cap")

        manager.sweepStaleOperationDirs()

        assertFalse(dirA.exists())
        assertFalse(dirB.exists())
    }

    @Test
    fun sweepStaleOperationDirs_removesUnknownChildFiles() {
        val manager = WackelbildTempFileManager(cacheDir)
        val wackelbildRoot = File(cacheDir, "wackelbild")
        wackelbildRoot.mkdirs()
        val strayFile = File(wackelbildRoot, "unexpected.tmp")
        strayFile.writeText("stray")

        manager.sweepStaleOperationDirs()

        assertFalse(strayFile.exists())
    }

    @Test
    fun sweepStaleOperationDirs_missingRoot_isSafeNoOp() {
        val manager = WackelbildTempFileManager(cacheDir)

        manager.sweepStaleOperationDirs() // wackelbild/ was never created; must not throw
    }

    @Test
    fun sweepStaleOperationDirs_emptyRoot_isSafeNoOp() {
        val manager = WackelbildTempFileManager(cacheDir)
        val wackelbildRoot = File(cacheDir, "wackelbild")
        wackelbildRoot.mkdirs()

        manager.sweepStaleOperationDirs() // must not throw

        assertTrue(wackelbildRoot.exists())
        assertEquals(0, wackelbildRoot.listFiles()?.size)
    }

    @Test
    fun sweepStaleOperationDirs_doesNotTraverseOutsideRoot() {
        val manager = WackelbildTempFileManager(cacheDir)
        manager.createOperationDir("op-present")
        val outsideSibling = File(cacheDir, "not-wackelbild")
        outsideSibling.mkdirs()
        val sentinel = File(outsideSibling, "sentinel.txt")
        sentinel.writeText("do not delete")
        val sessionsDir = File(cacheDir, "sessions/2026-06-21_10-00-00")
        sessionsDir.mkdirs()
        val referenceFile = File(sessionsDir, "reference.jpg")
        referenceFile.writeText("persisted session data")

        manager.sweepStaleOperationDirs()

        assertTrue(sentinel.exists())
        assertEquals("do not delete", sentinel.readText())
        assertTrue(referenceFile.exists())
        assertEquals("persisted session data", referenceFile.readText())
    }
}
