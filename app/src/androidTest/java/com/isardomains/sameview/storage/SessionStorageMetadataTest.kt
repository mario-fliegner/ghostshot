package com.isardomains.sameview.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.AppConstants
import com.isardomains.sameview.ui.camera.CaptureSessionSnapshot
import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import com.isardomains.sameview.ui.camera.ReferenceImageMetadata
import com.isardomains.sameview.ui.camera.GpsExifWriter
import com.isardomains.sameview.ui.camera.GpsSnapshot
import com.isardomains.sameview.ui.camera.PRESERVATION_BYTE_COPY
import com.isardomains.sameview.ui.camera.PRESERVATION_METADATA_STRIPPED
import com.isardomains.sameview.ui.camera.PRESERVATION_NOT_POSSIBLE
import com.isardomains.sameview.ui.camera.SessionScanner
import com.isardomains.sameview.ui.camera.SessionStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class SessionStorageMetadataTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionStorageMetadataTest")

    // Initialized in setUp() to a real file URI so writeCaptureOriginal() can open an InputStream.
    private lateinit var captureMediaStoreUri: Uri

    // Fixed snapshot values used in saveTestSession() — referenced in assertion tests.
    private val testViewportWidth = 80
    private val testViewportHeight = 120
    private val testOverlayScale = 1.5f
    private val testOverlayOffsetX = 0.1f
    private val testOverlayOffsetY = -0.05f
    private val testDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW

    @Before
    fun clearSessions() {
        cleanTestRoot()
        // Create a minimal JPEG stub in cacheDir so writeCaptureOriginal() can open an InputStream.
        val tempCapture = File(appContext.cacheDir, "test_capture.jpg")
        tempCapture.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))
        captureMediaStoreUri = Uri.fromFile(tempCapture)
    }

    @After
    fun cleanup() {
        cleanTestRoot()
    }

    private fun cleanTestRoot() {
        require(testRoot.absolutePath.contains("session-tests")) {
            "Refusing to delete non-test session root: ${testRoot.absolutePath}"
        }
        testRoot.deleteRecursively()
    }

    private fun buildTestSnapshot(referenceUri: Uri): CaptureSessionSnapshot {
        val exifOrientation = testContext.assets.open("exif_90.jpg").use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
            )
        }
        // exif_90.jpg raw is 100×60; after 90° rotation oriented is 60×100
        val options = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it, null, options) }
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
        val orientedWidth = if (isRotated) rawHeight else rawWidth
        val orientedHeight = if (isRotated) rawWidth else rawHeight
        return CaptureSessionSnapshot(
            referenceImageUri = referenceUri,
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
                exifOrientation = exifOrientation,
            ),
            overlayScale = testOverlayScale,
            overlayOffsetX = testOverlayOffsetX,
            overlayOffsetY = testOverlayOffsetY,
            referenceImageDisplayMode = testDisplayMode,
            viewportWidth = testViewportWidth,
            viewportHeight = testViewportHeight,
        )
    }

    private fun saveTestSession(): File {
        val tempFile = File(appContext.cacheDir, "test_reference.jpg")
        testContext.assets.open("exif_90.jpg").use { input ->
            tempFile.outputStream().use { input.copyTo(it) }
        }
        val snapshot = buildTestSnapshot(Uri.fromFile(tempFile))
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshot,
            captureMediaStoreUri = captureMediaStoreUri
        )
        captureBitmap.recycle()
        return testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    private fun readMetadata(sessionDir: File): JSONObject =
        JSONObject(File(sessionDir, "metadata.json").readText())

    private fun createV2Session(sessionId: String = "test-session-v2"): File {
        val sessionDir = File(testRoot, sessionId)
        sessionDir.mkdirs()
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 1_000_000L) })
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
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
        return sessionDir
    }

    // ── Core metadata structure ───────────────────────────────────────────────

    @Test
    fun metadataFile_existsAfterSuccessfulSession() {
        val sessionDir = saveTestSession()
        assertTrue(File(sessionDir, "metadata.json").exists())
    }

    @Test
    fun metadataFile_containsVersion6() {
        val json = readMetadata(saveTestSession())
        assertEquals(6, json.getInt("version"))
    }

    @Test
    fun metadataFile_capture_containsTimestampMs_greaterThanZero() {
        val json = readMetadata(saveTestSession())
        assertTrue(json.getJSONObject("capture").getLong("timestampMs") > 0L)
    }

    @Test
    fun metadataFile_capture_timestampMs_equalsSessionCreatedAtMs() {
        val json = readMetadata(saveTestSession())
        val sessionCreatedAtMs = json.getJSONObject("session").getLong("createdAtMs")
        val captureTimestampMs = json.getJSONObject("capture").getLong("timestampMs")
        assertEquals(sessionCreatedAtMs, captureTimestampMs)
    }

    @Test
    fun metadataFile_session_containsCreatedAtMsGreaterThanZero() {
        val json = readMetadata(saveTestSession())
        assertTrue(json.getJSONObject("session").getLong("createdAtMs") > 0L)
    }

    @Test
    fun metadataFile_files_containsReference() {
        val json = readMetadata(saveTestSession())
        assertEquals("reference.jpg", json.getJSONObject("files").getString("reference"))
    }

    @Test
    fun metadataFile_files_containsCapture() {
        val json = readMetadata(saveTestSession())
        assertEquals("capture.jpg", json.getJSONObject("files").getString("capture"))
    }

    @Test
    fun metadataFile_files_containsReferenceOriginal() {
        val json = readMetadata(saveTestSession())
        assertEquals("reference-original.jpg", json.getJSONObject("files").getString("referenceOriginal"))
    }

    @Test
    fun metadataFile_files_containsCaptureOriginal() {
        val json = readMetadata(saveTestSession())
        assertEquals("capture-original.jpg", json.getJSONObject("files").getString("captureOriginal"))
    }

    @Test
    fun metadataFile_files_containsReferenceSourceOriginal() {
        val json = readMetadata(saveTestSession())
        val refSourceOriginal = json.getJSONObject("files").getString("referenceSourceOriginal")
        assertTrue("referenceSourceOriginal must start with 'reference-source-original.'",
            refSourceOriginal.startsWith("reference-source-original."))
    }

    @Test
    fun captureOriginalFile_exists() {
        val sessionDir = saveTestSession()
        assertTrue(File(sessionDir, "capture-original.jpg").exists())
    }

    @Test
    fun referenceSourceOriginalFile_exists() {
        val sessionDir = saveTestSession()
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        assertTrue(File(sessionDir, filename).exists())
    }

    @Test
    fun captureOriginalFile_isByteIdenticalToCaptureSource() {
        val sessionDir = saveTestSession()
        val srcBytes = File(appContext.cacheDir, "test_capture.jpg").readBytes()
        val sessionBytes = File(sessionDir, "capture-original.jpg").readBytes()
        assertArrayEquals(srcBytes, sessionBytes)
    }

    @Test
    fun referenceSourceOriginalFile_isByteIdenticalToReferenceSource() {
        val sessionDir = saveTestSession()
        val srcBytes = File(appContext.cacheDir, "test_reference.jpg").readBytes()
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        val sessionBytes = File(sessionDir, filename).readBytes()
        assertArrayEquals(srcBytes, sessionBytes)
    }

    @Test
    fun metadataFile_reference_sourceMimeType_whenPresentIsNotBlank() {
        val json = readMetadata(saveTestSession())
        val refObj = json.getJSONObject("reference")
        if (refObj.has("sourceMimeType")) {
            assertTrue("sourceMimeType must not be blank when present",
                refObj.getString("sourceMimeType").isNotBlank())
        }
        // When sourceMimeType is absent (e.g. null MIME from file:// URI), that is also valid.
    }

    @Test
    fun metadataFile_reference_doesNotContainSourceDisplayName() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.getJSONObject("reference").has("sourceDisplayName"))
    }

    @Test
    fun metadataFile_capture_containsMediaStoreUri() {
        val json = readMetadata(saveTestSession())
        assertEquals(captureMediaStoreUri.toString(), json.getJSONObject("capture").getString("mediaStoreUri"))
    }

    @Test
    fun metadataFile_reference_containsSourceUri() {
        val tempFile = File(appContext.cacheDir, "test_reference.jpg")
        val json = readMetadata(saveTestSession())
        assertEquals(Uri.fromFile(tempFile).toString(), json.getJSONObject("reference").getString("sourceUri"))
    }

    // ── New v2 block assertions ───────────────────────────────────────────────

    @Test
    fun metadataFile_overlay_block_matchesSnapshot() {
        val json = readMetadata(saveTestSession())
        val overlay = json.getJSONObject("overlay")
        assertEquals(testOverlayScale.toDouble(), overlay.getDouble("scale"), 0.0001)
        assertEquals(testOverlayOffsetX.toDouble(), overlay.getDouble("offsetX"), 0.0001)
        assertEquals(testOverlayOffsetY.toDouble(), overlay.getDouble("offsetY"), 0.0001)
        assertEquals(testDisplayMode.name, overlay.getString("displayMode"))
    }

    @Test
    fun metadataFile_viewport_block_matchesSnapshot() {
        val json = readMetadata(saveTestSession())
        val viewport = json.getJSONObject("viewport")
        assertEquals(testViewportWidth, viewport.getInt("width"))
        assertEquals(testViewportHeight, viewport.getInt("height"))
        assertEquals("PORTRAIT", viewport.getString("orientation")) // 80 < 120 → PORTRAIT
    }

    // ── File existence and geometry ───────────────────────────────────────────

    @Test
    fun referenceOriginalJpeg_exists() {
        val sessionDir = saveTestSession()
        assertTrue(File(sessionDir, "reference-original.jpg").exists())
    }

    @Test
    fun referenceJpg_dimensions_equalViewport() {
        val sessionDir = saveTestSession()
        val bitmap = BitmapFactory.decodeFile(File(sessionDir, "reference.jpg").absolutePath)
            ?: error("reference.jpg missing or unreadable")
        try {
            assertEquals(testViewportWidth, bitmap.width)
            assertEquals(testViewportHeight, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    // ── Scanner integration ───────────────────────────────────────────────────

    @Test
    fun saveSession_producesScannableSession() {
        val sessionDir = saveTestSession()
        val sessions = SessionScanner.scan(testRoot)
        assertEquals(1, sessions.size)
        assertEquals(sessionDir.name, sessions[0].sessionId)
        assertTrue(sessions[0].timestamp > 0L)
    }

    @Test
    fun writtenJson_hasNoLocationBlock() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.has("location"))
    }

    @Test
    fun writtenJson_contentBlock_isAbsent_atCreationWithoutTitle() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.has("content"))
    }

    @Test
    fun metadataFile_additional_isFavorite_isFalse() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.getJSONObject("additional").getBoolean("isFavorite"))
    }

    @Test
    fun metadataFile_additional_visibility_isPrivate() {
        val json = readMetadata(saveTestSession())
        assertEquals("private", json.getJSONObject("additional").getString("visibility"))
    }

    @Test
    fun metadataFile_additional_source_isSameview() {
        val json = readMetadata(saveTestSession())
        assertEquals("sameview", json.getJSONObject("additional").getString("source"))
    }

    // ── EXIF software tag ─────────────────────────────────────────────────────

    @Test
    fun captureJpeg_hasExifSoftwareTag() {
        val sessionDir = saveTestSession()
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        assertEquals(AppConstants.CAPTURE_EXIF_SOFTWARE, exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    }

    @Test
    fun referenceAndReferenceOriginalJpeg_doNotHaveAppExifSoftwareTag() {
        val sessionDir = saveTestSession()
        val exifRef = ExifInterface(File(sessionDir, "reference.jpg").absolutePath)
        assertNotEquals(AppConstants.CAPTURE_EXIF_SOFTWARE, exifRef.getAttribute(ExifInterface.TAG_SOFTWARE))
        val exifOrig = ExifInterface(File(sessionDir, "reference-original.jpg").absolutePath)
        assertNotEquals(AppConstants.CAPTURE_EXIF_SOFTWARE, exifOrig.getAttribute(ExifInterface.TAG_SOFTWARE))
    }

    // ── updateTitle (unchanged from Block 3) ─────────────────────────────────

    @Test
    fun updateTitle_writesTitle() {
        val sessionDir = createV2Session()
        val sessionId = sessionDir.name

        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        val json = readMetadata(sessionDir)
        assertFalse(json.has("title"))
        assertEquals("My Title", json.getJSONObject("content").getString("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenNull() {
        val sessionDir = createV2Session()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, null)

        val json = readMetadata(sessionDir)
        assertFalse(json.getJSONObject("content").has("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenEmptyString() {
        val sessionDir = createV2Session()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, "")

        val json = readMetadata(sessionDir)
        assertFalse(json.getJSONObject("content").has("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenWhitespaceOnly() {
        val sessionDir = createV2Session()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, "   ")

        val json = readMetadata(sessionDir)
        assertFalse(json.getJSONObject("content").has("title"))
    }

    @Test
    fun updateTitle_preservesAllOtherFields() {
        val sessionDir = createV2Session()
        val sessionId = sessionDir.name
        val jsonBefore = readMetadata(sessionDir)

        SessionStorage.updateTitle(testRoot, sessionId, "New Title")

        val jsonAfter = readMetadata(sessionDir)
        assertEquals(jsonBefore.getInt("version"), jsonAfter.getInt("version"))
        assertEquals(
            jsonBefore.getJSONObject("session").getLong("createdAtMs"),
            jsonAfter.getJSONObject("session").getLong("createdAtMs")
        )
        assertEquals(
            jsonBefore.getJSONObject("files").getString("capture"),
            jsonAfter.getJSONObject("files").getString("capture")
        )
        assertEquals(
            jsonBefore.getJSONObject("files").getString("reference"),
            jsonAfter.getJSONObject("files").getString("reference")
        )
        assertEquals(
            jsonBefore.getJSONObject("overlay").getDouble("scale"),
            jsonAfter.getJSONObject("overlay").getDouble("scale"),
            0.0
        )
    }

    @Test
    fun updateTitle_pathTraversal_returnsFalse() {
        val result = SessionStorage.updateTitle(testRoot, "../some-other-dir", "Title")

        assertFalse(result)
    }

    @Test
    fun updateTitle_missingDirectSession_returnsFalse() {
        val result = SessionStorage.updateTitle(testRoot, "does-not-exist", "Title")

        assertFalse(result)
    }

    @Test
    fun updateTitle_absolutePath_returnsFalse() {
        val absoluteSessionId = File(testRoot, "absolute-session").absolutePath

        val result = SessionStorage.updateTitle(testRoot, absoluteSessionId, "Title")

        assertFalse(result)
    }

    @Test
    fun updateTitle_nestedRelativePath_returnsFalseAndDoesNotWrite() {
        val nestedDir = createV2Session("a/b")
        val metadataFile = File(nestedDir, "metadata.json")
        val metadataBefore = metadataFile.readText()

        val result = SessionStorage.updateTitle(testRoot, "a/b", "Title")

        assertFalse(result)
        assertEquals(metadataBefore, metadataFile.readText())
    }

    @Test
    fun updateTitle_backslashPath_returnsFalse() {
        val result = SessionStorage.updateTitle(testRoot, "a\\b", "Title")

        assertFalse(result)
    }

    @Test
    fun updateTitle_emptyDotAndDotDotSessionIds_returnFalse() {
        assertFalse(SessionStorage.updateTitle(testRoot, "", "Title"))
        assertFalse(SessionStorage.updateTitle(testRoot, ".", "Title"))
        assertFalse(SessionStorage.updateTitle(testRoot, "..", "Title"))
    }

    @Test
    fun updateTitle_newline_replacedWithSpace() {
        val sessionDir = createV2Session()
        SessionStorage.updateTitle(testRoot, sessionDir.name, "München\nHauptstadt")
        assertEquals("München Hauptstadt", readMetadata(sessionDir).getJSONObject("content").getString("title"))
    }

    @Test
    fun updateTitle_zeroWidthChar_removed() {
        val sessionDir = createV2Session()
        val zws = Char(0x200B)
        SessionStorage.updateTitle(testRoot, sessionDir.name, "Mün${zws}chen")
        assertEquals("München", readMetadata(sessionDir).getJSONObject("content").getString("title"))
    }

    // ── Block D: reference.date EXIF auto-population ──────────────────────────

    private fun saveTestSessionWithDate(exifDate: String): File {
        val tempFile = File(appContext.cacheDir, "test_reference_date.jpg")
        testContext.assets.open("exif_90.jpg").use { input ->
            tempFile.outputStream().use { input.copyTo(it) }
        }
        val exifOrientation = testContext.assets.open("exif_90.jpg").use { stream ->
            android.media.ExifInterface(stream).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_UNDEFINED
            )
        }
        val options = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it, null, options) }
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        val isRotated = exifOrientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                exifOrientation == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                exifOrientation == android.media.ExifInterface.ORIENTATION_TRANSVERSE
        val orientedWidth = if (isRotated) rawHeight else rawWidth
        val orientedHeight = if (isRotated) rawWidth else rawHeight
        val snapshot = CaptureSessionSnapshot(
            referenceImageUri = Uri.fromFile(tempFile),
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
                exifOrientation = exifOrientation,
                exifDateTimeOriginal = exifDate,
            ),
            overlayScale = testOverlayScale,
            overlayOffsetX = testOverlayOffsetX,
            overlayOffsetY = testOverlayOffsetY,
            referenceImageDisplayMode = testDisplayMode,
            viewportWidth = testViewportWidth,
            viewportHeight = testViewportHeight,
        )
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshot,
            captureMediaStoreUri = captureMediaStoreUri
        )
        captureBitmap.recycle()
        return testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    @Test
    fun reference_date_isPopulated_whenExifDateTimeOriginalPresent() {
        val json = readMetadata(saveTestSessionWithDate("2008-06-15"))
        assertEquals("2008-06-15", json.getJSONObject("reference").getString("date"))
    }

    @Test
    fun reference_dateSource_isExif_whenAutoPopulated() {
        val json = readMetadata(saveTestSessionWithDate("2008-06-15"))
        assertEquals("exif", json.getJSONObject("reference").getString("dateSource"))
    }

    @Test
    fun reference_userEdited_isFalse_whenAutoPopulated() {
        val json = readMetadata(saveTestSessionWithDate("2008-06-15"))
        assertFalse(json.getJSONObject("reference").getBoolean("userEdited"))
    }

    @Test
    fun reference_date_isAbsent_whenNoExifDateTimeOriginal() {
        // Standard saveTestSession() uses buildTestSnapshot() which has exifDateTimeOriginal=null
        val json = readMetadata(saveTestSession())
        assertFalse(json.getJSONObject("reference").has("date"))
    }

    @Test
    fun reference_dateSource_isAbsent_whenNoExifDateTimeOriginal() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.getJSONObject("reference").has("dateSource"))
    }

    @Test
    fun reference_userEdited_isAbsent_whenNoExifDateTimeOriginal() {
        val json = readMetadata(saveTestSession())
        assertFalse(json.getJSONObject("reference").has("userEdited"))
    }

    // ── Block E: updateReferenceDate ──────────────────────────────────────────

    /**
     * Creates a minimal session with a reference block carrying specified date fields.
     * No image files are written — only metadata.json is needed for updateReferenceDate tests.
     */
    private fun createSessionWithReferenceDateFields(
        sessionId: String = "test-session-refdate",
        date: String? = null,
        dateSource: String? = null,
        userEdited: Boolean? = null
    ): File {
        val sessionDir = File(testRoot, sessionId)
        sessionDir.mkdirs()
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 1_000_000L) })
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
            put("reference", JSONObject().apply {
                put("sourceDisplayName", "content://test/reference/1")
                put("originalWidth", 100)
                put("originalHeight", 60)
                put("orientedWidth", 60)
                put("orientedHeight", 100)
                if (date != null) put("date", date)
                if (dateSource != null) put("dateSource", dateSource)
                if (userEdited != null) put("userEdited", userEdited)
            })
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
        return sessionDir
    }

    @Test
    fun updateReferenceDate_writesDate_andDateSource_andUserEdited() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2008-06-15")

        assertTrue(result)
        val reference = readMetadata(sessionDir).getJSONObject("reference")
        assertEquals("2008-06-15", reference.getString("date"))
        assertEquals("manual", reference.getString("dateSource"))
        assertTrue(reference.getBoolean("userEdited"))
    }

    @Test
    fun updateReferenceDate_setsManualSource() {
        val sessionDir = createSessionWithReferenceDateFields()

        SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2019")

        assertEquals("manual", readMetadata(sessionDir).getJSONObject("reference").getString("dateSource"))
    }

    @Test
    fun updateReferenceDate_doesNotRevertToExif() {
        val sessionDir = createSessionWithReferenceDateFields(
            date = "2008-06-15",
            dateSource = "exif",
            userEdited = false
        )

        SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2009-01")

        val reference = readMetadata(sessionDir).getJSONObject("reference")
        assertEquals("2009-01", reference.getString("date"))
        assertEquals("manual", reference.getString("dateSource"))
        assertTrue(reference.getBoolean("userEdited"))
    }

    @Test
    fun updateReferenceDate_removesDate_removesDateSource() {
        val sessionDir = createSessionWithReferenceDateFields(
            date = "2008-06-15",
            dateSource = "manual",
            userEdited = true
        )

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, null)

        assertTrue(result)
        val reference = readMetadata(sessionDir).getJSONObject("reference")
        assertFalse(reference.has("date"))
        assertFalse(reference.has("dateSource"))
    }

    @Test
    fun updateReferenceDate_setsUserEdited_true_whenRemovingDate() {
        val sessionDir = createSessionWithReferenceDateFields(
            date = "2008-06-15",
            dateSource = "exif",
            userEdited = false
        )

        SessionStorage.updateReferenceDate(testRoot, sessionDir.name, null)

        assertTrue(readMetadata(sessionDir).getJSONObject("reference").getBoolean("userEdited"))
    }

    @Test
    fun updateReferenceDate_preservesAllOtherFields() {
        val sessionDir = createSessionWithReferenceDateFields()
        val jsonBefore = readMetadata(sessionDir)

        SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2020-03-15")

        val jsonAfter = readMetadata(sessionDir)
        assertEquals(jsonBefore.getInt("version"), jsonAfter.getInt("version"))
        assertEquals(
            jsonBefore.getJSONObject("session").getLong("createdAtMs"),
            jsonAfter.getJSONObject("session").getLong("createdAtMs")
        )
        assertEquals(
            jsonBefore.getJSONObject("files").getString("capture"),
            jsonAfter.getJSONObject("files").getString("capture")
        )
        assertEquals(
            jsonBefore.getJSONObject("overlay").getDouble("scale"),
            jsonAfter.getJSONObject("overlay").getDouble("scale"),
            0.0
        )
        val refAfter = jsonAfter.getJSONObject("reference")
        assertEquals("content://test/reference/1", refAfter.getString("sourceDisplayName"))
        assertEquals(100, refAfter.getInt("originalWidth"))
        assertEquals(60, refAfter.getInt("originalHeight"))
        assertEquals(60, refAfter.getInt("orientedWidth"))
        assertEquals(100, refAfter.getInt("orientedHeight"))
    }

    @Test
    fun updateReferenceDate_pathTraversal_returnsFalse() {
        assertFalse(SessionStorage.updateReferenceDate(testRoot, "../some-other-dir", "2020"))
    }

    @Test
    fun updateReferenceDate_absolutePath_returnsFalse() {
        val absoluteSessionId = File(testRoot, "absolute-session").absolutePath
        assertFalse(SessionStorage.updateReferenceDate(testRoot, absoluteSessionId, "2020"))
    }

    @Test
    fun updateReferenceDate_yearOnly_isValid() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2008")

        assertTrue(result)
        assertEquals("2008", readMetadata(sessionDir).getJSONObject("reference").getString("date"))
    }

    @Test
    fun updateReferenceDate_yearMonth_isValid() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2008-06")

        assertTrue(result)
        assertEquals("2008-06", readMetadata(sessionDir).getJSONObject("reference").getString("date"))
    }

    @Test
    fun updateReferenceDate_invalidDate_emptyString_returnsFalse() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "")

        assertFalse(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("reference").has("date"))
    }

    @Test
    fun updateReferenceDate_invalidDate_invalidMonth_returnsFalse() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2008-13")

        assertFalse(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("reference").has("date"))
    }

    @Test
    fun updateReferenceDate_invalidDate_invalidCalendarDay_returnsFalse() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "2008-02-31")

        assertFalse(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("reference").has("date"))
    }

    @Test
    fun updateReferenceDate_invalidDate_yearBefore1826_returnsFalse() {
        val sessionDir = createSessionWithReferenceDateFields()

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "1825")

        assertFalse(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("reference").has("date"))
    }

    @Test
    fun updateReferenceDate_invalidDate_yearAfterCurrentYear_returnsFalse() {
        val sessionDir = createSessionWithReferenceDateFields()
        val futureYear = Calendar.getInstance().get(Calendar.YEAR) + 1

        val result = SessionStorage.updateReferenceDate(testRoot, sessionDir.name, "$futureYear")

        assertFalse(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("reference").has("date"))
    }

    @Test
    fun updateReferenceDate_missingSession_returnsFalse() {
        assertFalse(SessionStorage.updateReferenceDate(testRoot, "does-not-exist", "2020"))
    }

    // ── Block F: updateLocation ───────────────────────────────────────────────

    /**
     * Creates a minimal session with optional location fields in metadata.json.
     * No image files are written — only metadata.json is needed for updateLocation tests.
     */
    private fun createSessionWithLocationFields(
        sessionId: String = "test-session-location",
        displayName: String? = null,
        city: String? = null,
        country: String? = null
    ): File {
        val sessionDir = File(testRoot, sessionId)
        sessionDir.mkdirs()
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 1_000_000L) })
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
            put("reference", JSONObject().apply {
                put("sourceDisplayName", "content://test/reference/1")
                put("originalWidth", 100)
                put("originalHeight", 60)
            })
            val hasAny = displayName != null || city != null || country != null
            if (hasAny) {
                put("location", JSONObject().apply {
                    if (displayName != null) put("displayName", displayName)
                    if (city != null) put("city", city)
                    if (country != null) put("country", country)
                    put("userEdited", true)
                })
            }
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
        return sessionDir
    }

    @Test
    fun updateLocation_writesAllThreeFields_andSetsUserEdited() {
        val sessionDir = createSessionWithLocationFields()

        val result = SessionStorage.updateLocation(testRoot, sessionDir.name, "Zugspitze Summit", "Garmisch-Partenkirchen", "Deutschland")

        assertTrue(result)
        val location = readMetadata(sessionDir).getJSONObject("location")
        assertEquals("Zugspitze Summit", location.getString("displayName"))
        assertEquals("Garmisch-Partenkirchen", location.getString("city"))
        assertEquals("Deutschland", location.getString("country"))
        assertTrue(location.getBoolean("userEdited"))
    }

    @Test
    fun updateLocation_writesPartialFields_onlyCitySet() {
        val sessionDir = createSessionWithLocationFields()

        val result = SessionStorage.updateLocation(testRoot, sessionDir.name, null, "München", null)

        assertTrue(result)
        val location = readMetadata(sessionDir).getJSONObject("location")
        assertFalse(location.has("displayName"))
        assertEquals("München", location.getString("city"))
        assertFalse(location.has("country"))
        assertTrue(location.getBoolean("userEdited"))
    }

    @Test
    fun updateLocation_setsUserEdited_true_whenAnyFieldSet() {
        val sessionDir = createSessionWithLocationFields()

        SessionStorage.updateLocation(testRoot, sessionDir.name, null, null, "France")

        assertTrue(readMetadata(sessionDir).getJSONObject("location").getBoolean("userEdited"))
    }

    @Test
    fun updateLocation_normalizesBlankToAbsent_doesNotWriteBlankField() {
        val sessionDir = createSessionWithLocationFields()

        val result = SessionStorage.updateLocation(testRoot, sessionDir.name, "   ", "Paris", null)

        assertTrue(result)
        val location = readMetadata(sessionDir).getJSONObject("location")
        assertFalse(location.has("displayName"))
        assertEquals("Paris", location.getString("city"))
    }

    @Test
    fun updateLocation_removesIndividualField_whenSetToNull() {
        val sessionDir = createSessionWithLocationFields(
            displayName = "Old Name",
            city = "Old City",
            country = "Old Country"
        )

        SessionStorage.updateLocation(testRoot, sessionDir.name, null, "New City", "Old Country")

        val location = readMetadata(sessionDir).getJSONObject("location")
        assertFalse(location.has("displayName"))
        assertEquals("New City", location.getString("city"))
        assertEquals("Old Country", location.getString("country"))
    }

    @Test
    fun updateLocation_removesBlock_whenAllFieldsNull() {
        val sessionDir = createSessionWithLocationFields(
            displayName = "Some Place",
            city = "Some City",
            country = "Some Country"
        )

        val result = SessionStorage.updateLocation(testRoot, sessionDir.name, null, null, null)

        assertTrue(result)
        assertFalse(readMetadata(sessionDir).has("location"))
    }

    @Test
    fun updateLocation_removesBlock_whenAllFieldsBlank() {
        val sessionDir = createSessionWithLocationFields(
            displayName = "Some Place",
            city = "Some City",
            country = "Some Country"
        )

        val result = SessionStorage.updateLocation(testRoot, sessionDir.name, "", "  ", "")

        assertTrue(result)
        assertFalse(readMetadata(sessionDir).has("location"))
    }

    @Test
    fun updateLocation_preservesAllOtherFields() {
        val sessionDir = createSessionWithLocationFields()
        val jsonBefore = readMetadata(sessionDir)

        SessionStorage.updateLocation(testRoot, sessionDir.name, "Marienplatz", "München", "Deutschland")

        val jsonAfter = readMetadata(sessionDir)
        assertEquals(jsonBefore.getInt("version"), jsonAfter.getInt("version"))
        assertEquals(
            jsonBefore.getJSONObject("session").getLong("createdAtMs"),
            jsonAfter.getJSONObject("session").getLong("createdAtMs")
        )
        assertEquals(
            jsonBefore.getJSONObject("files").getString("capture"),
            jsonAfter.getJSONObject("files").getString("capture")
        )
        assertEquals(
            jsonBefore.getJSONObject("overlay").getDouble("scale"),
            jsonAfter.getJSONObject("overlay").getDouble("scale"),
            0.0
        )
        assertEquals(
            jsonBefore.getJSONObject("reference").getString("sourceDisplayName"),
            jsonAfter.getJSONObject("reference").getString("sourceDisplayName")
        )
    }

    @Test
    fun updateLocation_updatesExistingFields() {
        val sessionDir = createSessionWithLocationFields(
            displayName = "Old Name",
            city = "Old City",
            country = "Old Country"
        )

        SessionStorage.updateLocation(testRoot, sessionDir.name, "New Name", "New City", "New Country")

        val location = readMetadata(sessionDir).getJSONObject("location")
        assertEquals("New Name", location.getString("displayName"))
        assertEquals("New City", location.getString("city"))
        assertEquals("New Country", location.getString("country"))
        assertTrue(location.getBoolean("userEdited"))
    }

    @Test
    fun updateLocation_pathTraversal_returnsFalse() {
        assertFalse(SessionStorage.updateLocation(testRoot, "../some-other-dir", "Place", null, null))
    }

    @Test
    fun updateLocation_absolutePath_returnsFalse() {
        val absoluteSessionId = File(testRoot, "absolute-session").absolutePath
        assertFalse(SessionStorage.updateLocation(testRoot, absoluteSessionId, "Place", null, null))
    }

    @Test
    fun updateLocation_missingSession_returnsFalse() {
        assertFalse(SessionStorage.updateLocation(testRoot, "does-not-exist", "Place", "City", "Country"))
    }

    @Test
    fun updateLocation_returnsTrue_onSuccess() {
        val sessionDir = createSessionWithLocationFields()
        assertTrue(SessionStorage.updateLocation(testRoot, sessionDir.name, "Place", "City", "Country"))
    }

    // ── Block UX: updateContent ─────────────────────────────────────────────────

    /**
     * Creates a minimal session with optional content fields in metadata.json.
     * No image files are written — only metadata.json is needed for updateContent tests.
     */
    private fun createSessionWithContentFields(
        sessionId: String = "test-session-content",
        title: String? = null,
        description: String? = null
    ): File {
        val sessionDir = File(testRoot, sessionId)
        sessionDir.mkdirs()
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 1_000_000L) })
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
            put("capture", JSONObject().apply { put("timestampMs", 1_000_000L) })
            put("reference", JSONObject().apply { put("sourceDisplayName", "content://test/ref/1") })
            val hasAny = title != null || description != null
            if (hasAny) {
                put("content", JSONObject().apply {
                    if (title != null) put("title", title)
                    if (description != null) put("description", description)
                })
            }
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
        return sessionDir
    }

    // ── Block A: updateFavorite ───────────────────────────────────────────────

    /**
     * Creates a minimal v4 session with an [additional] block.
     * When [isFavorite] is non-null the block is written with that value plus the standard
     * [visibility] and [source] defaults (realistic v4 state).
     * When [isFavorite] is null no [additional] block is written (simulates a v2/v3 session).
     * No actual image files are created — only metadata.json is required for updateFavorite tests.
     */
    private fun createSessionWithAdditionalBlock(
        sessionId: String = "test-session-additional",
        isFavorite: Boolean? = null
    ): File {
        val sessionDir = File(testRoot, sessionId)
        sessionDir.mkdirs()
        val json = JSONObject().apply {
            put("version", 4)
            put("session", JSONObject().apply { put("createdAtMs", 1_000_000L) })
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
            put("capture", JSONObject().apply { put("timestampMs", 1_000_000L) })
            put("reference", JSONObject().apply { put("sourceDisplayName", "content://test/ref/1") })
            if (isFavorite != null) {
                put("additional", JSONObject().apply {
                    put("isFavorite", isFavorite)
                    put("visibility", "private")
                    put("source", "sameview")
                })
            }
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
        return sessionDir
    }

    @Test
    fun updateFavorite_setsTrue() {
        val sessionDir = createSessionWithAdditionalBlock(isFavorite = false)

        val result = SessionStorage.updateFavorite(testRoot, sessionDir.name, true)

        assertTrue(result)
        assertTrue(readMetadata(sessionDir).getJSONObject("additional").getBoolean("isFavorite"))
    }

    @Test
    fun updateFavorite_setsFalse() {
        val sessionDir = createSessionWithAdditionalBlock(isFavorite = true)

        val result = SessionStorage.updateFavorite(testRoot, sessionDir.name, false)

        assertTrue(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("additional").getBoolean("isFavorite"))
    }

    @Test
    fun updateFavorite_togglesFromTrueToFalse() {
        val sessionDir = createSessionWithAdditionalBlock(isFavorite = false)
        SessionStorage.updateFavorite(testRoot, sessionDir.name, true)

        val result = SessionStorage.updateFavorite(testRoot, sessionDir.name, false)

        assertTrue(result)
        assertFalse(readMetadata(sessionDir).getJSONObject("additional").getBoolean("isFavorite"))
    }

    @Test
    fun updateFavorite_preservesAllOtherFields() {
        val sessionDir = createSessionWithAdditionalBlock(isFavorite = false)
        val jsonBefore = readMetadata(sessionDir)

        SessionStorage.updateFavorite(testRoot, sessionDir.name, true)

        val jsonAfter = readMetadata(sessionDir)
        // All blocks outside of additional must be identical
        assertEquals(jsonBefore.getInt("version"), jsonAfter.getInt("version"))
        assertEquals(
            jsonBefore.getJSONObject("session").getLong("createdAtMs"),
            jsonAfter.getJSONObject("session").getLong("createdAtMs")
        )
        assertEquals(
            jsonBefore.getJSONObject("files").getString("capture"),
            jsonAfter.getJSONObject("files").getString("capture")
        )
        assertEquals(
            jsonBefore.getJSONObject("overlay").getDouble("scale"),
            jsonAfter.getJSONObject("overlay").getDouble("scale"),
            0.0001
        )
    }

    @Test
    fun updateFavorite_preservesOtherAdditionalFields() {
        val sessionDir = createSessionWithAdditionalBlock(isFavorite = false)

        SessionStorage.updateFavorite(testRoot, sessionDir.name, true)

        val additional = readMetadata(sessionDir).getJSONObject("additional")
        assertEquals("private", additional.getString("visibility"))
        assertEquals("sameview", additional.getString("source"))
    }

    @Test
    fun updateFavorite_pathTraversal_returnsFalse() {
        assertFalse(SessionStorage.updateFavorite(testRoot, "../some-other-dir", true))
    }

    @Test
    fun updateFavorite_missingSession_returnsFalse() {
        assertFalse(SessionStorage.updateFavorite(testRoot, "does-not-exist", true))
    }

    @Test
    fun updateFavorite_createsAdditionalBlock_whenAbsent() {
        // Uses createV2Session() to produce a session without any additional block
        val sessionDir = createV2Session(sessionId = "test-session-v2-noadditional")

        val result = SessionStorage.updateFavorite(testRoot, sessionDir.name, true)

        assertTrue(result)
        val additional = readMetadata(sessionDir).getJSONObject("additional")
        assertTrue(additional.getBoolean("isFavorite"))
        // visibility and source must NOT be added — only isFavorite is written
        assertFalse("visibility must not be present in newly created additional block", additional.has("visibility"))
        assertFalse("source must not be present in newly created additional block", additional.has("source"))
    }

    @Test
    fun updateContent_writesTitleAndDescription() {
        val sessionDir = createSessionWithContentFields()

        val result = SessionStorage.updateContent(testRoot, sessionDir.name, "Zugspitze 2026", "A scenic alpine view")

        assertTrue(result)
        val content = readMetadata(sessionDir).getJSONObject("content")
        assertEquals("Zugspitze 2026", content.getString("title"))
        assertEquals("A scenic alpine view", content.getString("description"))
    }

    @Test
    fun updateContent_removesTitleWhenNull() {
        val sessionDir = createSessionWithContentFields(title = "Old Title", description = "Old Description")

        SessionStorage.updateContent(testRoot, sessionDir.name, null, "Old Description")

        val content = readMetadata(sessionDir).getJSONObject("content")
        assertFalse(content.has("title"))
        assertEquals("Old Description", content.getString("description"))
    }

    @Test
    fun updateContent_removesDescriptionWhenNull() {
        val sessionDir = createSessionWithContentFields(title = "Old Title", description = "Old Description")

        SessionStorage.updateContent(testRoot, sessionDir.name, "Old Title", null)

        val content = readMetadata(sessionDir).getJSONObject("content")
        assertEquals("Old Title", content.getString("title"))
        assertFalse(content.has("description"))
    }

    @Test
    fun updateContent_keepsContentBlockWhenBothNull() {
        val sessionDir = createSessionWithContentFields(title = "Old Title", description = "Old Description")

        val result = SessionStorage.updateContent(testRoot, sessionDir.name, null, null)

        assertTrue(result)
        val json = readMetadata(sessionDir)
        assertTrue(json.has("content"))
        val content = json.getJSONObject("content")
        assertFalse(content.has("title"))
        assertFalse(content.has("description"))
    }

    @Test
    fun updateContent_rejectsPathTraversal() {
        assertFalse(SessionStorage.updateContent(testRoot, "../some-other-dir", "Title", null))
    }

    @Test
    fun updateContent_returnsFalseWhenMetadataMissing() {
        assertFalse(SessionStorage.updateContent(testRoot, "does-not-exist", "Title", null))
    }

    // ── Block B Privacy: saveSession(stripMetadata) ───────────────────────────

    /**
     * Saves a session with a real capture file URI that contains GPS EXIF and returns
     * the session directory. Used by privacy tests to verify EXIF removal.
     */
    private fun saveTestSessionWithPrivacy(stripMetadata: Boolean): File {
        // Reference file
        val tempRef = File(appContext.cacheDir, "test_reference_priv.jpg")
        testContext.assets.open("exif_90.jpg").use { tempRef.outputStream().use { os -> it.copyTo(os) } }

        // Capture file — write a minimal JPEG and embed GPS EXIF into it
        val tempCapture = File(appContext.cacheDir, "test_capture_priv.jpg")
        // Write a tiny valid JPEG (reuse exif_90.jpg as content)
        testContext.assets.open("exif_90.jpg").use { tempCapture.outputStream().use { os -> it.copyTo(os) } }
        // Embed GPS EXIF into the capture file so we can verify it is removed
        val gpsSnapshot = GpsSnapshot(latitude = 48.137, longitude = 11.575, altitude = 520.0, accuracyMeters = 5.0f)
        GpsExifWriter.writeGpsToFile(tempCapture, gpsSnapshot)

        val snapshot = buildTestSnapshot(Uri.fromFile(tempRef))
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshot,
            captureMediaStoreUri = Uri.fromFile(tempCapture),
            stripMetadata = stripMetadata
        )
        captureBitmap.recycle()
        return testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    @Test
    fun privacyOff_metadata_hasNoOriginalsBlock() {
        val json = readMetadata(saveTestSession())  // default stripMetadata = false
        assertFalse("originals block must be absent when privacy is OFF", json.has("originals"))
    }

    @Test
    fun privacyOn_metadata_hasOriginalsBlock_withCorrectFields() {
        val sessionDir = saveTestSessionWithPrivacy(stripMetadata = true)
        val json = readMetadata(sessionDir)
        assertTrue("originals block must be present when privacy is ON", json.has("originals"))
        val originals = json.getJSONObject("originals")
        assertTrue("originals.privacyMode must be true", originals.getBoolean("privacyMode"))
        assertEquals("capturePreservation must be metadata_stripped",
            PRESERVATION_METADATA_STRIPPED, originals.getString("capturePreservation"))
        assertEquals("referenceSourcePreservation must be metadata_stripped for a JPEG reference",
            PRESERVATION_METADATA_STRIPPED, originals.getString("referenceSourcePreservation"))
    }

    @Test
    fun privacyOn_captureOriginal_hasNoGpsTags() {
        val sessionDir = saveTestSessionWithPrivacy(stripMetadata = true)
        val captureOrig = File(sessionDir, "capture-original.jpg")
        assertTrue("capture-original.jpg must exist", captureOrig.exists())
        val exif = ExifInterface(captureOrig.absolutePath)
        assertNull("GPS latitude must be absent after stripping",
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must be absent after stripping",
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test
    fun privacyOn_captureOriginal_resolutionPreserved() {
        val sessionDir = saveTestSessionWithPrivacy(stripMetadata = true)
        val captureOrig = File(sessionDir, "capture-original.jpg")
        val bitmap = BitmapFactory.decodeFile(captureOrig.absolutePath)
            ?: error("Could not decode capture-original.jpg after privacy strip")
        try {
            assertTrue("Width must be > 0", bitmap.width > 0)
            assertTrue("Height must be > 0", bitmap.height > 0)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun privacyOff_captureOriginal_isByteIdenticalToCaptureSrc_unchanged() {
        // Existing byte-for-byte behavior must be unaffected when privacy is OFF
        val sessionDir = saveTestSession()  // default stripMetadata = false
        val srcBytes = File(appContext.cacheDir, "test_capture.jpg").readBytes()
        val sessionBytes = File(sessionDir, "capture-original.jpg").readBytes()
        assertArrayEquals("capture-original.jpg must be byte-for-byte copy when privacy OFF",
            srcBytes, sessionBytes)
    }

    // ── Block C: reference-source-original privacy ────────────────────────────

    private fun savePrivacySessionWithCustomRef(referenceUri: Uri): File {
        // Use a real JPEG for the capture file — the 3-byte stub fails when privacy stripping
        // falls back to decode+re-encode, because BitmapFactory can't decode a truncated JPEG.
        val tempCapture = File(appContext.cacheDir, "test_capture_block_c.jpg")
        testContext.assets.open("exif_90.jpg").use { tempCapture.outputStream().use { os -> it.copyTo(os) } }

        val snapshot = buildTestSnapshot(referenceUri)
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshot,
            captureMediaStoreUri = Uri.fromFile(tempCapture),
            stripMetadata = true
        )
        captureBitmap.recycle()
        return testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    @Test
    fun privacyOff_referenceSourceOriginal_isByteIdenticalToSource() {
        // Privacy OFF: byte-for-byte copy must be unaffected
        val sessionDir = saveTestSession()  // stripMetadata = false
        val srcBytes = File(appContext.cacheDir, "test_reference.jpg").readBytes()
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        val sessionBytes = File(sessionDir, filename).readBytes()
        assertArrayEquals("reference-source-original must be byte-for-byte copy when privacy OFF",
            srcBytes, sessionBytes)
    }

    @Test
    fun privacyOn_jpegReferenceSource_preservation_isMetadataStripped() {
        // JPEG reference → decode → JPEG 95 → preservation = metadata_stripped
        val tempRef = File(appContext.cacheDir, "test_ref_jpeg_c.jpg")
        testContext.assets.open("exif_90.jpg").use { tempRef.outputStream().use { os -> it.copyTo(os) } }

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempRef))
        val originals = readMetadata(sessionDir).getJSONObject("originals")
        assertEquals(PRESERVATION_METADATA_STRIPPED, originals.getString("referenceSourcePreservation"))
    }

    @Test
    fun privacyOn_jpegReferenceSource_storedAsJpeg_extensionIsJpg() {
        val tempRef = File(appContext.cacheDir, "test_ref_jpeg_ext.jpg")
        testContext.assets.open("exif_90.jpg").use { tempRef.outputStream().use { os -> it.copyTo(os) } }

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempRef))
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        assertTrue("JPEG reference must produce .jpg output file", filename.endsWith(".jpg"))
        assertTrue("Output file must exist", File(sessionDir, filename).exists())
    }

    @Test
    fun privacyOn_jpegReferenceSource_isDecodable() {
        // Verify the stripped JPEG can still be decoded (resolution preserved)
        val tempRef = File(appContext.cacheDir, "test_ref_decodable.jpg")
        testContext.assets.open("exif_90.jpg").use { tempRef.outputStream().use { os -> it.copyTo(os) } }

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempRef))
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        val bitmap = BitmapFactory.decodeFile(File(sessionDir, filename).absolutePath)
        assertNotNull("Stripped reference source must be decodable", bitmap)
        bitmap?.recycle()
    }

    @Test
    fun privacyOn_pngReferenceSource_storedAsPng_extensionIsPng() {
        // Create a minimal PNG file from a Bitmap
        val tempPng = File(appContext.cacheDir, "test_ref_block_c.png")
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        tempPng.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 0, it) }
        bmp.recycle()

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempPng))
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        assertTrue("PNG reference must produce .png output file", filename.endsWith(".png"))
        assertTrue("Output PNG file must exist", File(sessionDir, filename).exists())
        val originals = json.getJSONObject("originals")
        assertEquals(PRESERVATION_METADATA_STRIPPED, originals.getString("referenceSourcePreservation"))
    }

    @Test
    fun privacyOn_unknownMimeReference_preservation_isNotPossible() {
        // A JPEG file with an unrecognized extension (.xyz) → ContentResolver returns null MIME type
        // → extension inference also returns null → stored as-is with not_possible.
        // Using a valid JPEG file so that writeReferenceOriginalAndReference() can still decode it.
        val tempUnknown = File(appContext.cacheDir, "test_ref_unknown.xyz")
        testContext.assets.open("exif_90.jpg").use { tempUnknown.outputStream().use { os -> it.copyTo(os) } }

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempUnknown))
        val json = readMetadata(sessionDir)
        val originals = json.getJSONObject("originals")
        assertEquals(PRESERVATION_NOT_POSSIBLE, originals.getString("referenceSourcePreservation"))
        // Session was saved successfully despite not_possible
        assertTrue("metadata.json must exist", File(sessionDir, "metadata.json").exists())
    }

    @Test
    fun privacyOn_jpegReferenceSource_noExifGpsInOutput() {
        // Embed GPS EXIF into a reference JPEG and verify it is removed after privacy stripping
        val tempRef = File(appContext.cacheDir, "test_ref_gps_strip.jpg")
        testContext.assets.open("exif_90.jpg").use { tempRef.outputStream().use { os -> it.copyTo(os) } }
        val gpsSnapshot = GpsSnapshot(latitude = 47.5, longitude = 11.0, altitude = 800.0, accuracyMeters = 5.0f)
        GpsExifWriter.writeGpsToFile(tempRef, gpsSnapshot)

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempRef))
        val json = readMetadata(sessionDir)
        val filename = json.getJSONObject("files").getString("referenceSourceOriginal")
        val exif = ExifInterface(File(sessionDir, filename).absolutePath)
        assertNull("GPS latitude must be absent from stripped reference source",
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must be absent from stripped reference source",
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    // ── Block C: HEIC reference source (real asset) ───────────────────────────

    @Test
    fun privacyOn_heicReferenceSource_convertedToJpeg_withMetadataStripped() {
        // Copy the real HEIC test asset from androidTest/assets to cacheDir so it can be
        // referenced via Uri.fromFile(). The .heic extension triggers HEIC extension inference
        // in writeReferenceSourceOriginalStripped() if ContentResolver returns null MIME.
        val tempHeic = File(appContext.cacheDir, "test_ref_heic_privacy.heic")
        testContext.assets.open("privacy/reference_source_original_heic_test.heic").use { input ->
            tempHeic.outputStream().use { input.copyTo(it) }
        }

        val sessionDir = savePrivacySessionWithCustomRef(Uri.fromFile(tempHeic))
        val json = readMetadata(sessionDir)

        // 1. files.referenceSourceOriginal must be a .jpg (HEIC converted to JPEG)
        val refSrcFilename = json.getJSONObject("files").getString("referenceSourceOriginal")
        assertEquals("HEIC reference source must be stored as reference-source-original.jpg",
            "reference-source-original.jpg", refSrcFilename)

        // 2. The stored file must exist and be JPEG-decodable
        val refSrcFile = File(sessionDir, refSrcFilename)
        assertTrue("reference-source-original.jpg must exist after HEIC conversion", refSrcFile.exists())
        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(refSrcFile.absolutePath, opts)
        assertTrue("Converted HEIC must produce a decodable JPEG with positive width", opts.outWidth > 0)
        assertTrue("Converted HEIC must produce a decodable JPEG with positive height", opts.outHeight > 0)

        // 3. originals block: preservation = metadata_stripped
        val originals = json.getJSONObject("originals")
        assertEquals("referenceSourcePreservation must be metadata_stripped for HEIC",
            PRESERVATION_METADATA_STRIPPED, originals.getString("referenceSourcePreservation"))

        // 4. originals block: stored MIME = image/jpeg (confirms HEIC→JPEG conversion)
        assertEquals("referenceSourceStoredMimeType must be image/jpeg",
            "image/jpeg", originals.getString("referenceSourceStoredMimeType"))

        // 5. reference.sourceMimeType (if present) must reflect the HEIC source
        val refBlock = json.optJSONObject("reference")
        if (refBlock != null && refBlock.has("sourceMimeType")) {
            assertEquals("When present, reference.sourceMimeType must be image/heic",
                "image/heic", refBlock.getString("sourceMimeType"))
        }
        // Absence of sourceMimeType is also valid when ContentResolver returns null for file:// URI

        // 6. Output JPEG must have no EXIF metadata (GPS, camera make/model)
        val exif = ExifInterface(refSrcFile.absolutePath)
        assertNull("GPS latitude must be absent after HEIC→JPEG conversion",
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must be absent after HEIC→JPEG conversion",
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("Camera make must be absent after HEIC→JPEG conversion",
            exif.getAttribute(ExifInterface.TAG_MAKE))
    }
}
