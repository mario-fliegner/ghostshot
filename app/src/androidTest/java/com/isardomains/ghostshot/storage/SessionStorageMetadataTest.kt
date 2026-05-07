package com.isardomains.ghostshot.storage

import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.AppConstants
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
    private val referencePickerUri = Uri.parse("content://test/picker/456")

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

    private fun saveTestSession(): File {
        val tempFile = File(appContext.cacheDir, "test_reference.jpg")
        testContext.assets.open("exif_90.jpg").use { input ->
            tempFile.outputStream().use { input.copyTo(it) }
        }
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            referenceUri = Uri.fromFile(tempFile),
            exifOrientation = null,
            captureMediaStoreUri = captureMediaStoreUri,
            referencePickerUri = referencePickerUri
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

    @Test
    fun metadataFile_existsAfterSuccessfulSession() {
        val sessionDir = saveTestSession()
        assertTrue(File(sessionDir, "metadata.json").exists())
    }

    @Test
    fun metadataFile_containsVersion1() {
        val json = readMetadata(saveTestSession())
        assertEquals(1, json.getInt("version"))
    }

    @Test
    fun metadataFile_containsSessionTimestampMsGreaterThanZero() {
        val json = readMetadata(saveTestSession())
        assertTrue(json.getLong("sessionTimestampMs") > 0L)
    }

    @Test
    fun metadataFile_containsReferenceFile() {
        val json = readMetadata(saveTestSession())
        assertEquals("reference.jpg", json.getString("referenceFile"))
    }

    @Test
    fun metadataFile_containsCaptureFile() {
        val json = readMetadata(saveTestSession())
        assertEquals("capture.jpg", json.getString("captureFile"))
    }

    @Test
    fun metadataFile_containsCaptureMediaStoreUri() {
        val json = readMetadata(saveTestSession())
        assertEquals(captureMediaStoreUri.toString(), json.getString("captureMediaStoreUri"))
    }

    @Test
    fun metadataFile_containsReferencePickerUri() {
        val json = readMetadata(saveTestSession())
        assertEquals(referencePickerUri.toString(), json.getString("referencePickerUri"))
    }

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
    fun captureJpeg_hasExifSoftwareTag() {
        val sessionDir = saveTestSession()
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        assertEquals(AppConstants.CAPTURE_EXIF_SOFTWARE, exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    }

    @Test
    fun referenceJpeg_doesNotHaveAppExifSoftwareTag() {
        val sessionDir = saveTestSession()
        val exif = ExifInterface(File(sessionDir, "reference.jpg").absolutePath)
        assertNotEquals(AppConstants.CAPTURE_EXIF_SOFTWARE, exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    }
}
