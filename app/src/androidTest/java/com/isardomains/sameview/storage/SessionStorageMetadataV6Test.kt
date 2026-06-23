package com.isardomains.sameview.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.CaptureSessionSnapshot
import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import com.isardomains.sameview.ui.camera.ReferenceImageMetadata
import com.isardomains.sameview.ui.camera.SessionScanner
import com.isardomains.sameview.ui.camera.SessionStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that SessionStorage writes schema version 6 for new sessions and that
 * the resulting metadata.json is accepted by SessionScanner with no branding state.
 *
 * Block 0 — SESSION_BRANDING_V1.md §9
 */
@RunWith(AndroidJUnit4::class)
class SessionStorageMetadataV6Test {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionStorageMetadataV6Test")
    private lateinit var captureMediaStoreUri: Uri

    @Before
    fun setUp() {
        cleanTestRoot()
        testRoot.mkdirs()
        // Minimal JPEG stub: writeCaptureOriginal() only copies bytes, so this is sufficient.
        val tempCapture = File(appContext.cacheDir, "sv6test_capture.jpg")
        tempCapture.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))
        captureMediaStoreUri = Uri.fromFile(tempCapture)
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

    private fun buildTestSnapshot(referenceUri: Uri): CaptureSessionSnapshot {
        val exifOrientation = testContext.assets.open("exif_90.jpg").use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
            )
        }
        val options = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it, null, options) }
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
        return CaptureSessionSnapshot(
            referenceImageUri = referenceUri,
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                orientedWidth = if (isRotated) rawHeight else rawWidth,
                orientedHeight = if (isRotated) rawWidth else rawHeight,
                exifOrientation = exifOrientation,
            ),
            overlayScale = 1.0f,
            overlayOffsetX = 0f,
            overlayOffsetY = 0f,
            referenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            viewportWidth = 80,
            viewportHeight = 120,
        )
    }

    private fun saveTestSession(): File {
        val tempRef = File(appContext.cacheDir, "sv6test_reference.jpg")
        testContext.assets.open("exif_90.jpg").use { input ->
            tempRef.outputStream().use { input.copyTo(it) }
        }
        val snapshot = buildTestSnapshot(Uri.fromFile(tempRef))
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun newSession_writesVersion6() {
        val sessionDir = saveTestSession()
        val json = JSONObject(File(sessionDir, "metadata.json").readText())
        assertEquals(6, json.getInt("version"))
    }

    @Test
    fun newSession_hasNoBrandingBlock() {
        val sessionDir = saveTestSession()
        val json = JSONObject(File(sessionDir, "metadata.json").readText())
        assertFalse("branding block must be absent by default", json.has("branding"))
    }

    @Test
    fun newSession_hasNoBrandingHandleInFilesBlock() {
        val sessionDir = saveTestSession()
        val json = JSONObject(File(sessionDir, "metadata.json").readText())
        val filesObj = json.getJSONObject("files")
        assertFalse("files.brandingHandle must be absent by default", filesObj.has("brandingHandle"))
    }

    @Test
    fun newSession_isAcceptedByScanner_withNullBranding() {
        saveTestSession()
        val sessions = SessionScanner.scan(testRoot)
        assertEquals("Exactly one session must be scanned", 1, sessions.size)
        assertNull("branding must be null for a session without branding", sessions[0].branding)
    }

    @Test
    fun newSession_versionField_isCorrectlyReadByScanner() {
        saveTestSession()
        val sessions = SessionScanner.scan(testRoot)
        assertEquals(1, sessions.size)
        assert(sessions[0].timestamp > 0L) { "Timestamp must be > 0 for a successfully scanned session" }
    }
}
