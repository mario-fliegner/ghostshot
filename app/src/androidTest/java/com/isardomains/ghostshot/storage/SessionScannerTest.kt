package com.isardomains.ghostshot.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.camera.ScannedSession
import com.isardomains.ghostshot.ui.camera.SessionScanner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionScannerTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionScannerTest")

    @Before
    fun setUp() {
        cleanTestRoot()
        testRoot.mkdirs()
    }

    @After
    fun tearDown() {
        cleanTestRoot()
    }

    private fun cleanTestRoot() {
        require(testRoot.absolutePath.contains("session-tests")) {
            "Refusing to delete non-test session root: ${testRoot.absolutePath}"
        }
        testRoot.deleteRecursively()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createSessionDir(sessionId: String): File =
        File(testRoot, sessionId).also { it.mkdirs() }

    private fun writeMetadata(
        sessionDir: File,
        version: Int = 1,
        timestamp: Long = 1_000L,
        referenceFile: String = "reference.jpg",
        captureFile: String = "capture.jpg",
        extra: Map<String, String> = emptyMap()
    ) {
        val json = JSONObject().apply {
            put("version", version)
            put("sessionTimestampMs", timestamp)
            put("referenceFile", referenceFile)
            put("captureFile", captureFile)
            extra.forEach { (k, v) -> put(k, v) }
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
    }

    private fun touch(dir: File, name: String) {
        File(dir, name).createNewFile()
    }

    private fun fullSession(
        sessionId: String,
        timestamp: Long = 1_000L
    ): File {
        val dir = createSessionDir(sessionId)
        writeMetadata(dir, timestamp = timestamp)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")
        return dir
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun validSession_isReturned() {
        fullSession("2026-04-24_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("2026-04-24_10-00-00", result[0].sessionId)
        assertEquals(5_000L, result[0].timestamp)
    }

    @Test
    fun sessionWithoutMetadata_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun invalidJson_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        File(dir, "metadata.json").writeText("not { valid json }")
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun wrongVersion_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, version = 2)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun missingReferenceFile_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir)
        // reference.jpg intentionally absent
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun missingCaptureFile_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir)
        touch(dir, "reference.jpg")
        // capture.jpg intentionally absent

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun sessionsAreSortedNewestFirst() {
        fullSession("2026-04-24_08-00-00", timestamp = 1_000L)
        fullSession("2026-04-24_09-00-00", timestamp = 3_000L)
        fullSession("2026-04-24_10-00-00", timestamp = 2_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(3, result.size)
        assertEquals(3_000L, result[0].timestamp)
        assertEquals(2_000L, result[1].timestamp)
        assertEquals(1_000L, result[2].timestamp)
    }

    @Test
    fun emptySessionsDirectory_returnsEmptyList() {
        // testRoot exists but is empty (created in setUp)
        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun missingSessionsDirectory_returnsEmptyList() {
        cleanTestRoot()

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun nonDirectoryInsideSessionsRoot_isIgnored() {
        touch(testRoot, "stray_file.txt")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun emptyReferenceFileName_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, referenceFile = "")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun emptyCaptureFileName_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, captureFile = "")
        touch(dir, "reference.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun metadataWithPathTraversalReference_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, referenceFile = "../reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun metadataWithAbsoluteCapturePath_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, captureFile = "/data/user/0/com.isardomains.ghostshot/files/sessions/capture.jpg")
        touch(dir, "reference.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun metadataWithDirectorySeparator_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, referenceFile = "subdir/reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun metadataWithMissingTimestamp_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 1)
            put("referenceFile", "reference.jpg")
            put("captureFile", "capture.jpg")
            // sessionTimestampMs intentionally omitted
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun session_withTitle_titleIsRead() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, extra = mapOf("title" to "My Shot"))
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("My Shot", result[0].title)
    }

    @Test
    fun session_withoutTitle_titleIsNull() {
        fullSession("2026-04-24_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    @Test
    fun session_withEmptyTitle_titleIsNull() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, extra = mapOf("title" to ""))
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    @Test
    fun session_withWhitespaceTitle_titleIsNull() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, extra = mapOf("title" to "   "))
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    @Test
    fun metadataExtraFields_areIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(
            dir,
            timestamp = 5_000L,
            extra = mapOf(
                "captureMediaStoreUri" to "content://media/external/images/123",
                "referencePickerUri" to "content://picker/images/456",
                "unknownField" to "someValue"
            )
        )
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
    }
}
