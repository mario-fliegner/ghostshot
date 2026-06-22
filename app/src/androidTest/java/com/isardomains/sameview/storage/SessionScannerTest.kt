package com.isardomains.sameview.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.SessionScanner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        version: Int = 2,
        timestamp: Long = 1_000L,
        referenceFile: String = "reference.jpg",
        captureFile: String = "capture.jpg",
        title: String? = null,
        referenceDate: String? = null,
        locationDisplayName: String? = null,
        locationCity: String? = null,
        locationCountry: String? = null,
        isFavorite: Boolean? = null
    ) {
        val json = JSONObject().apply {
            put("version", version)
            put("session", JSONObject().apply { put("createdAtMs", timestamp) })
            put("files", JSONObject().apply {
                put("capture", captureFile)
                put("reference", referenceFile)
            })
            if (title != null) {
                put("content", JSONObject().apply { put("title", title) })
            }
            if (referenceDate != null) {
                put("reference", JSONObject().apply { put("date", referenceDate) })
            }
            if (locationDisplayName != null || locationCity != null || locationCountry != null) {
                put("location", JSONObject().apply {
                    if (locationDisplayName != null) put("displayName", locationDisplayName)
                    if (locationCity != null) put("city", locationCity)
                    if (locationCountry != null) put("country", locationCountry)
                })
            }
            if (isFavorite != null) {
                put("additional", JSONObject().apply { put("isFavorite", isFavorite) })
            }
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
        writeMetadata(dir, version = 1)
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
        writeMetadata(dir, captureFile = "/data/user/0/com.isardomains.sameview/files/sessions/capture.jpg")
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
            put("version", 2)
            put("session", JSONObject()) // createdAtMs intentionally omitted
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun session_withTitle_titleIsRead() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, title = "My Shot")
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
        writeMetadata(dir, timestamp = 5_000L, title = "")
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    @Test
    fun session_withWhitespaceTitle_titleIsNull() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, title = "   ")
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    @Test
    fun metadataExtraFields_areIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("overlay", JSONObject().apply {
                put("scale", 1.0)
                put("offsetX", 0.0)
                put("offsetY", 0.0)
                put("displayMode", "COMPARE_WITH_PREVIEW")
            })
            put("location", JSONObject().apply {
                put("latitude", JSONObject.NULL)
                put("longitude", JSONObject.NULL)
            })
            put("unknownField", "someValue")
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
    }

    // ── New v2-specific tests ─────────────────────────────────────────────────

    @Test
    fun v2_missingSessionBlock_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            // session block intentionally omitted
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v2_missingFilesBlock_isIgnored() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            // files block intentionally omitted
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v2_referenceOriginalMissing_sessionIsStillVisible() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
                put("referenceOriginal", "reference-original.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")
        // reference-original.jpg intentionally absent

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
    }

    @Test
    fun v2_withoutContentBlock_titleIsNull() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            // content block intentionally omitted
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
        assertNull(result[0].title)
    }

    // ── v3 compatibility tests ────────────────────────────────────────────────

    @Test
    fun v3_withoutGpsFields_isAccepted() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 3)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
    }

    @Test
    fun v3_withCaptureLocation_isAccepted() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 3)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("captureLocation", JSONObject().apply {
                put("latitude", 48.123456)
                put("longitude", 11.654321)
                put("accuracyMeters", 8.5)
                put("provider", "gps")
                put("fixTimestampMs", 1748000000000L)
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
    }

    @Test
    fun v4_isAccepted() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("capture", JSONObject().apply { put("timestampMs", 5_000L) })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertEquals(1, SessionScanner.scan(testRoot).size)
    }

    @Test
    fun v4_sessionWithCaptureTsMs_timestampReadCorrectly() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 5_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("capture", JSONObject().apply { put("timestampMs", 9_000L) })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
        assertEquals(9_000L, result[0].timestamp)
    }

    @Test
    fun v3_sessionWithoutCaptureBlock_fallsBackToSessionCreatedAtMs() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, version = 3, timestamp = 7_000L)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
        assertEquals(7_000L, result[0].timestamp)
    }

    // ── referenceDate tests ───────────────────────────────────────────────────

    @Test
    fun session_withReferenceDate_referenceDateIsRead() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, timestamp = 5_000L, referenceDate = "2008-06-15")
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("2008-06-15", result[0].referenceDate)
    }

    @Test
    fun session_withoutReferenceDate_referenceDateIsNull() {
        fullSession("2026-04-24_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].referenceDate)
    }

    // ── location tests ────────────────────────────────────────────────────────

    @Test
    fun session_withLocationFields_allThreeRead() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(
            dir,
            timestamp = 5_000L,
            locationDisplayName = "Zugspitzgipfel",
            locationCity = "Garmisch-Partenkirchen",
            locationCountry = "Deutschland"
        )
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("Zugspitzgipfel", result[0].locationDisplayName)
        assertEquals("Garmisch-Partenkirchen", result[0].locationCity)
        assertEquals("Deutschland", result[0].locationCountry)
    }

    @Test
    fun session_withoutLocationBlock_locationFieldsAreNull() {
        fullSession("2026-04-24_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertNull(result[0].locationDisplayName)
        assertNull(result[0].locationCity)
        assertNull(result[0].locationCountry)
    }

    // ── Block A: isFavorite scanner tests ─────────────────────────────────────

    @Test
    fun isFavorite_true_whenSetInMetadata() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, isFavorite = true)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertTrue(result[0].isFavorite)
    }

    @Test
    fun isFavorite_false_whenSetFalseInMetadata() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        writeMetadata(dir, isFavorite = false)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
    }

    @Test
    fun isFavorite_false_whenAdditionalBlockAbsent() {
        // writeMetadata() without isFavorite parameter writes no additional block
        fullSession("2026-04-24_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
    }

    @Test
    fun isFavorite_false_whenFieldAbsentInAdditionalBlock() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        // additional block present but without isFavorite key
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 1_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("additional", JSONObject().apply {
                put("visibility", "private")
                put("source", "sameview")
                // isFavorite intentionally omitted
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
    }

    @Test
    fun isFavorite_false_whenValueIsInvalidType() {
        val dir = createSessionDir("2026-04-24_10-00-00")
        // isFavorite stored as a String instead of Boolean
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 1_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
            put("additional", JSONObject().apply { put("isFavorite", "yes") })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertFalse(result[0].isFavorite)
    }

    // ── v5 original file validation ───────────────────────────────────────────

    /**
     * Writes a v5-compatible metadata.json. Pass null for [captureOriginalFile] or
     * [referenceSourceOriginalFile] to omit the respective key from the files block,
     * simulating a corrupt or incomplete v5 session.
     */
    private fun writeMetadataV5(
        sessionDir: File,
        timestamp: Long = 1_000L,
        captureOriginalFile: String? = "capture-original.jpg",
        referenceSourceOriginalFile: String? = "reference-source-original.jpg"
    ) {
        val json = JSONObject().apply {
            put("version", 5)
            put("session", JSONObject().apply { put("createdAtMs", timestamp) })
            put("capture", JSONObject().apply { put("timestampMs", timestamp) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                if (captureOriginalFile != null) put("captureOriginal", captureOriginalFile)
                put("reference", "reference.jpg")
                put("referenceOriginal", "reference-original.jpg")
                if (referenceSourceOriginalFile != null) put("referenceSourceOriginal", referenceSourceOriginalFile)
            })
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
    }

    /** Creates a complete v5 session directory with all six required files. */
    private fun fullSessionV5(sessionId: String, timestamp: Long = 1_000L): File {
        val dir = createSessionDir(sessionId)
        writeMetadataV5(dir, timestamp = timestamp)
        touch(dir, "capture.jpg")
        touch(dir, "capture-original.jpg")
        touch(dir, "reference.jpg")
        touch(dir, "reference-original.jpg")
        touch(dir, "reference-source-original.jpg")
        return dir
    }

    @Test
    fun v5Session_allRequiredFiles_isValid() {
        fullSessionV5("2026-07-01_10-00-00", timestamp = 5_000L)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("2026-07-01_10-00-00", result[0].sessionId)
    }

    @Test
    fun v5Session_captureOriginalKeyMissing_isIgnored() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadataV5(dir, captureOriginalFile = null)  // key absent from files block
        touch(dir, "capture.jpg")
        touch(dir, "reference.jpg")
        touch(dir, "reference-source-original.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v5Session_referenceSourceOriginalKeyMissing_isIgnored() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadataV5(dir, referenceSourceOriginalFile = null)  // key absent from files block
        touch(dir, "capture.jpg")
        touch(dir, "capture-original.jpg")
        touch(dir, "reference.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v5Session_captureOriginalFileNotOnDisk_isIgnored() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadataV5(dir)
        touch(dir, "capture.jpg")
        // capture-original.jpg referenced in metadata but intentionally absent from disk
        touch(dir, "reference.jpg")
        touch(dir, "reference-source-original.jpg")

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v5Session_referenceSourceOriginalFileNotOnDisk_isIgnored() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadataV5(dir, referenceSourceOriginalFile = "reference-source-original.bin")
        touch(dir, "capture.jpg")
        touch(dir, "capture-original.jpg")
        touch(dir, "reference.jpg")
        // reference-source-original.bin referenced in metadata but intentionally absent from disk

        assertTrue(SessionScanner.scan(testRoot).isEmpty())
    }

    @Test
    fun v4Session_withoutOriginalFiles_isValid() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadata(dir, version = 4, timestamp = 1_000L)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertEquals(1, SessionScanner.scan(testRoot).size)
    }

    @Test
    fun v3Session_withoutOriginalFiles_isValid() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeMetadata(dir, version = 3, timestamp = 1_000L)
        touch(dir, "reference.jpg")
        touch(dir, "capture.jpg")

        assertEquals(1, SessionScanner.scan(testRoot).size)
    }

    @Test
    fun v2Session_withoutOriginalFiles_isValid() {
        fullSession("2026-07-01_10-00-00")  // fullSession uses version 2 by default

        assertEquals(1, SessionScanner.scan(testRoot).size)
    }
}
