package com.isardomains.ghostshot.storage

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.camera.SessionStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val sessionDir = saveTestSession()
        val sessionId = sessionDir.name

        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        val json = readMetadata(sessionDir)
        assertEquals("My Title", json.getString("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenNull() {
        val sessionDir = saveTestSession()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, null)

        val json = readMetadata(sessionDir)
        assertFalse(json.has("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenEmptyString() {
        val sessionDir = saveTestSession()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, "")

        val json = readMetadata(sessionDir)
        assertFalse(json.has("title"))
    }

    @Test
    fun updateTitle_removesTitle_whenWhitespaceOnly() {
        val sessionDir = saveTestSession()
        val sessionId = sessionDir.name
        SessionStorage.updateTitle(testRoot, sessionId, "My Title")

        SessionStorage.updateTitle(testRoot, sessionId, "   ")

        val json = readMetadata(sessionDir)
        assertFalse(json.has("title"))
    }

    @Test
    fun updateTitle_preservesAllOtherFields() {
        val sessionDir = saveTestSession()
        val sessionId = sessionDir.name
        val jsonBefore = readMetadata(sessionDir)

        SessionStorage.updateTitle(testRoot, sessionId, "New Title")

        val jsonAfter = readMetadata(sessionDir)
        assertEquals(jsonBefore.getInt("version"), jsonAfter.getInt("version"))
        assertEquals(jsonBefore.getLong("sessionTimestampMs"), jsonAfter.getLong("sessionTimestampMs"))
        assertEquals(jsonBefore.getString("referenceFile"), jsonAfter.getString("referenceFile"))
        assertEquals(jsonBefore.getString("captureFile"), jsonAfter.getString("captureFile"))
        assertEquals(jsonBefore.getString("captureMediaStoreUri"), jsonAfter.getString("captureMediaStoreUri"))
        assertEquals(jsonBefore.getString("referencePickerUri"), jsonAfter.getString("referencePickerUri"))
    }

    @Test
    fun updateTitle_pathTraversal_returnsFalse() {
        val result = SessionStorage.updateTitle(testRoot, "../some-other-dir", "Title")

        assertFalse(result)
    }
}
