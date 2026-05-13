package com.isardomains.ghostshot.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.AppConstants
import com.isardomains.ghostshot.ui.camera.CaptureSessionSnapshot
import com.isardomains.ghostshot.ui.camera.ReferenceImageDisplayMode
import com.isardomains.ghostshot.ui.camera.ReferenceImageMetadata
import com.isardomains.ghostshot.ui.camera.SessionScanner
import com.isardomains.ghostshot.ui.camera.SessionStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionStorageMetadataTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionStorageMetadataTest")

    private val captureMediaStoreUri = Uri.parse("content://test/capture/123")

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
    fun metadataFile_containsVersion2() {
        val json = readMetadata(saveTestSession())
        assertEquals(2, json.getInt("version"))
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
    fun metadataFile_capture_containsMediaStoreUri() {
        val json = readMetadata(saveTestSession())
        assertEquals(captureMediaStoreUri.toString(), json.getJSONObject("capture").getString("mediaStoreUri"))
    }

    @Test
    fun metadataFile_reference_containsSourceDisplayName() {
        val tempFile = File(appContext.cacheDir, "test_reference.jpg")
        val json = readMetadata(saveTestSession())
        assertEquals(Uri.fromFile(tempFile).toString(), json.getJSONObject("reference").getString("sourceDisplayName"))
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
    fun saveSession_producesScannableV2Session() {
        val sessionDir = saveTestSession()
        val sessions = SessionScanner.scan(testRoot)
        assertEquals(1, sessions.size)
        assertEquals(sessionDir.name, sessions[0].sessionId)
        assertTrue(sessions[0].timestamp > 0L)
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
}
