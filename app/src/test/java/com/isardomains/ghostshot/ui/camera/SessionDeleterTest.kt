package com.isardomains.ghostshot.ui.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionDeleterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun validSession_isDeletedSuccessfully() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionDir = File(sessionsRoot, "2024-01-15_10-30-00")
        sessionDir.mkdirs()
        File(sessionDir, "capture.jpg").writeText("fake")
        File(sessionDir, "reference.jpg").writeText("fake")
        File(sessionDir, "metadata.json").writeText("{}")

        val result = SessionDeleter.delete(sessionsRoot, "2024-01-15_10-30-00")

        assertTrue(result)
        assertFalse(sessionDir.exists())
    }

    @Test
    fun missingSession_returnsTrue() {
        val sessionsRoot = tempFolder.newFolder("sessions")

        val result = SessionDeleter.delete(sessionsRoot, "does-not-exist")

        assertTrue(result)
    }

    @Test
    fun pathTraversalWithDotDot_isRejected() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sibling = tempFolder.newFolder("sibling")
        File(sibling, "important.txt").writeText("should not be deleted")

        val result = SessionDeleter.delete(sessionsRoot, "../sibling")

        assertFalse(result)
        assertTrue(sibling.exists())
    }

    @Test
    fun absolutePathAsSessionId_isRejected() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val outsideDir = tempFolder.newFolder("outside")

        val result = SessionDeleter.delete(sessionsRoot, outsideDir.absolutePath)

        assertFalse(result)
        assertTrue(outsideDir.exists())
    }
}
