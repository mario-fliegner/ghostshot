package com.isardomains.sameview.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.branding.GlobalBranding
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.ui.camera.CaptureSessionSnapshot
import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import com.isardomains.sameview.ui.camera.ReferenceImageMetadata
import com.isardomains.sameview.ui.camera.SessionBrandingMeta
import com.isardomains.sameview.ui.camera.SessionScanner
import com.isardomains.sameview.ui.camera.SessionStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies SessionStorage branding functions: auto-copy on session creation,
 * updateSessionBranding, removeSessionBranding, copyGlobalBrandingToSession.
 *
 * Block 3 — SESSION_BRANDING_V1.md §10 / SESSION_BRANDING_IMPLEMENTATION_PLAN.md §6
 */
@RunWith(AndroidJUnit4::class)
class SessionStorageBrandingTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val sessionsRoot = File(appContext.filesDir, "session-tests/SessionStorageBrandingTest")
    private val brandingDir = File(appContext.filesDir, "session-tests/branding-test")
    private lateinit var captureMediaStoreUri: Uri

    @Before
    fun setUp() {
        cleanTestDirs()
        sessionsRoot.mkdirs()
        brandingDir.mkdirs()
        val tempCapture = File(appContext.cacheDir, "svbrandtest_capture.jpg")
        tempCapture.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))
        captureMediaStoreUri = Uri.fromFile(tempCapture)
    }

    @After
    fun tearDown() {
        cleanTestDirs()
    }

    private fun cleanTestDirs() {
        require(sessionsRoot.absolutePath.contains("session-tests")) {
            "Refusing to delete non-test root: ${sessionsRoot.absolutePath}"
        }
        sessionsRoot.deleteRecursively()
        brandingDir.deleteRecursively()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalPng(): ByteArray = ByteArray(64) { it.toByte() }

    private fun buildSnapshot(referenceUri: Uri): CaptureSessionSnapshot {
        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it, null, opts) }
        val exifOrientation = testContext.assets.open("exif_90.jpg").use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        }
        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == ExifInterface.ORIENTATION_ROTATE_270
        return CaptureSessionSnapshot(
            referenceImageUri = referenceUri,
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = opts.outWidth,
                rawHeight = opts.outHeight,
                orientedWidth = if (isRotated) opts.outHeight else opts.outWidth,
                orientedHeight = if (isRotated) opts.outWidth else opts.outHeight,
                exifOrientation = exifOrientation,
            ),
            overlayScale = 1.0f, overlayOffsetX = 0f, overlayOffsetY = 0f,
            referenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            viewportWidth = 80, viewportHeight = 120,
        )
    }

    /** Saves a full test session and returns its directory. */
    private fun saveTestSession(globalBrandingRepository: GlobalBrandingRepository? = null): File {
        val tempRef = File(appContext.cacheDir, "svbrandtest_reference.jpg")
        testContext.assets.open("exif_90.jpg").use { it.copyTo(tempRef.outputStream()) }
        val snapshot = buildSnapshot(Uri.fromFile(tempRef))
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = sessionsRoot,
            capturedBitmap = bitmap,
            snapshot = snapshot,
            captureMediaStoreUri = captureMediaStoreUri,
            globalBranding = globalBrandingRepository?.getBranding()
        )
        bitmap.recycle()
        return sessionsRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    /** Creates a minimal v6 session directory with metadata for update/remove tests. */
    private fun createMinimalSession(sessionId: String): File {
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        val json = JSONObject().apply {
            put("version", 6)
            put("session", JSONObject().apply { put("id", sessionId); put("createdAtMs", 1000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("captureOriginal", "capture-original.jpg")
                put("reference", "reference.jpg")
                put("referenceOriginal", "reference-original.jpg")
                put("referenceSourceOriginal", "reference-source-original.jpg")
            })
            put("capture", JSONObject().apply { put("timestampMs", 1000L) })
            put("additional", JSONObject().apply { put("isFavorite", false); put("visibility", "private"); put("source", "sameview") })
        }
        File(dir, "metadata.json").writeText(json.toString())
        // Create stub files so Scanner can validate them
        listOf("capture.jpg", "capture-original.jpg", "reference.jpg", "reference-original.jpg", "reference-source-original.jpg")
            .forEach { File(dir, it).createNewFile() }
        return dir
    }

    /** Sets up global branding in [brandingDir] and returns the repository. */
    private suspend fun setupGlobalBranding(type: String = "image", builtinId: String? = null): GlobalBrandingRepository {
        val repo = GlobalBrandingRepository(brandingDir)
        repo.setBranding(minimalPng(), type, builtinId)
        return repo
    }

    // ── I-06: saveSession with global branding → file copied ─────────────────

    @Test
    fun saveSession_withGlobalBranding_copiesBrandingHandle() {
        runBlocking {
            val repo = setupGlobalBranding("image")
            val sessionDir = saveTestSession(repo)

            val brandingFile = File(sessionDir, "branding-handle.png")
            assertTrue("branding-handle.png must exist when global branding is set", brandingFile.isFile)
            assertArrayEquals("branding-handle.png content must match global branding", minimalPng(), brandingFile.readBytes())
        }
    }

    @Test
    fun saveSession_withGlobalBranding_writesBrandingMetadata() {
        runBlocking {
            val repo = setupGlobalBranding("builtin", "fire")
            val sessionDir = saveTestSession(repo)

            val json = JSONObject(File(sessionDir, "metadata.json").readText())
            assertTrue("files.brandingHandle must be set", json.getJSONObject("files").has("brandingHandle"))
            assertEquals("branding-handle.png", json.getJSONObject("files").getString("brandingHandle"))
            assertTrue("branding block must exist", json.has("branding"))
            assertEquals("builtin", json.getJSONObject("branding").getString("type"))
            assertEquals("fire", json.getJSONObject("branding").getString("builtinId"))
            assertEquals("branding-handle.png", json.getJSONObject("branding").getString("handleFile"))
        }
    }

    @Test
    fun saveSession_withGlobalBranding_sessionIsAcceptedByScannerWithBranding() {
        runBlocking {
            val repo = setupGlobalBranding("image")
            saveTestSession(repo)

            val sessions = SessionScanner.scan(sessionsRoot)
            assertEquals(1, sessions.size)
            val branding = sessions[0].branding
            assertNotNull("Scanner must read branding from metadata", branding)
            assertEquals("image", branding!!.type)
        }
    }

    // ── I-07: saveSession without global branding → no branding file ──────────

    @Test
    fun saveSession_withoutGlobalBranding_createsNoFrandingFile() {
        val sessionDir = saveTestSession(null)

        assertFalse("branding-handle.png must not exist when no global branding", File(sessionDir, "branding-handle.png").exists())
        val json = JSONObject(File(sessionDir, "metadata.json").readText())
        assertFalse("files.brandingHandle must be absent", json.getJSONObject("files").has("brandingHandle"))
        assertFalse("branding block must be absent", json.has("branding"))
    }

    @Test
    fun saveSession_withoutGlobalBranding_scannerReturnsNullBranding() {
        saveTestSession(null)
        val sessions = SessionScanner.scan(sessionsRoot)
        assertEquals(1, sessions.size)
        assertNull("Scanner branding must be null when no branding", sessions[0].branding)
    }

    // ── I-08: updateSessionBranding ───────────────────────────────────────────

    @Test
    fun updateSessionBranding_writesFileAndUpdatesMetadata() {
        val sessionId = "2026-07-15_10-00-00"
        createMinimalSession(sessionId)
        val png = minimalPng()

        val result = SessionStorage.updateSessionBranding(sessionsRoot, sessionId, png, "image", null)

        assertTrue("updateSessionBranding must return true", result)
        val brandingFile = File(sessionsRoot, "$sessionId/branding-handle.png")
        assertTrue("branding-handle.png must exist after update", brandingFile.isFile)
        assertArrayEquals("PNG content must match input", png, brandingFile.readBytes())

        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertEquals("branding-handle.png", json.getJSONObject("files").getString("brandingHandle"))
        assertEquals("image", json.getJSONObject("branding").getString("type"))
    }

    @Test
    fun updateSessionBranding_builtin_writesBuiltinIdToMetadata() {
        val sessionId = "2026-07-15_10-00-01"
        createMinimalSession(sessionId)

        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "builtin", "heart")

        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertEquals("builtin", json.getJSONObject("branding").getString("type"))
        assertEquals("heart", json.getJSONObject("branding").getString("builtinId"))
    }

    @Test
    fun updateSessionBranding_overwritesExistingBranding() {
        val sessionId = "2026-07-15_10-00-02"
        createMinimalSession(sessionId)
        val png1 = ByteArray(64) { 0x01 }
        val png2 = ByteArray(64) { 0x02 }

        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, png1, "image", null)
        val result = SessionStorage.updateSessionBranding(sessionsRoot, sessionId, png2, "builtin", "star")

        assertTrue(result)
        val brandingFile = File(sessionsRoot, "$sessionId/branding-handle.png")
        assertArrayEquals("Second PNG must replace first", png2, brandingFile.readBytes())
        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertEquals("star", json.getJSONObject("branding").getString("builtinId"))
    }

    @Test
    fun updateSessionBranding_preservesExistingMetadataFields() {
        val sessionId = "2026-07-15_10-00-03"
        createMinimalSession(sessionId)
        // Pre-set a title in metadata
        SessionStorage.updateContent(sessionsRoot, sessionId, "My Title", null)

        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "image", null)

        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertEquals("My Title", json.optJSONObject("content")?.optString("title"))
    }

    @Test
    fun updateSessionBranding_invalidSessionId_returnsFalse() {
        assertFalse(SessionStorage.updateSessionBranding(sessionsRoot, "../bad", minimalPng(), "image", null))
        assertFalse(SessionStorage.updateSessionBranding(sessionsRoot, "", minimalPng(), "image", null))
    }

    @Test
    fun updateSessionBranding_missingMetadata_returnsFalse() {
        val dir = File(sessionsRoot, "no-metadata-session").also { it.mkdirs() }
        // No metadata.json created
        assertFalse(SessionStorage.updateSessionBranding(sessionsRoot, dir.name, minimalPng(), "image", null))
    }

    @Test
    fun updateSessionBranding_sessionIsStillScannable_afterUpdate() {
        val sessionId = "2026-07-15_10-00-04"
        createMinimalSession(sessionId)
        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "image", null)

        val sessions = SessionScanner.scan(sessionsRoot)
        assertEquals(1, sessions.size)
        assertNotNull("Scanner must read branding after update", sessions[0].branding)
    }

    // ── I-09: removeSessionBranding ───────────────────────────────────────────

    @Test
    fun removeSessionBranding_deletesFileAndClearsMetadata() {
        val sessionId = "2026-07-15_11-00-00"
        createMinimalSession(sessionId)
        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "image", null)

        val result = SessionStorage.removeSessionBranding(sessionsRoot, sessionId)

        assertTrue("removeSessionBranding must return true", result)
        assertFalse("branding-handle.png must not exist after removal", File(sessionsRoot, "$sessionId/branding-handle.png").exists())

        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertFalse("files.brandingHandle must be removed from metadata", json.getJSONObject("files").has("brandingHandle"))
        assertFalse("branding block must be removed from metadata", json.has("branding"))
    }

    @Test
    fun removeSessionBranding_noOpWhenNoBrandingExists() {
        val sessionId = "2026-07-15_11-00-01"
        createMinimalSession(sessionId)

        val result = SessionStorage.removeSessionBranding(sessionsRoot, sessionId)
        assertTrue("removeSessionBranding must return true even when no branding was set", result)
    }

    @Test
    fun removeSessionBranding_preservesOtherMetadataFields() {
        val sessionId = "2026-07-15_11-00-02"
        createMinimalSession(sessionId)
        SessionStorage.updateContent(sessionsRoot, sessionId, "My Title", null)
        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "image", null)

        SessionStorage.removeSessionBranding(sessionsRoot, sessionId)

        val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
        assertEquals("My Title", json.optJSONObject("content")?.optString("title"))
    }

    @Test
    fun removeSessionBranding_sessionIsStillScannable_afterRemoval() {
        val sessionId = "2026-07-15_11-00-03"
        createMinimalSession(sessionId)
        SessionStorage.updateSessionBranding(sessionsRoot, sessionId, minimalPng(), "image", null)
        SessionStorage.removeSessionBranding(sessionsRoot, sessionId)

        val sessions = SessionScanner.scan(sessionsRoot)
        assertEquals("Session must still be scannable after branding removal", 1, sessions.size)
        assertNull("Scanner branding must be null after removal", sessions[0].branding)
    }

    @Test
    fun removeSessionBranding_invalidSessionId_returnsFalse() {
        assertFalse(SessionStorage.removeSessionBranding(sessionsRoot, "../evil"))
        assertFalse(SessionStorage.removeSessionBranding(sessionsRoot, ""))
    }

    // ── I-10: copyGlobalBrandingToSession ─────────────────────────────────────

    @Test
    fun copyGlobalBrandingToSession_copiesFileAndUpdatesMetadata() {
        runBlocking {
            val sessionId = "2026-07-15_12-00-00"
            createMinimalSession(sessionId)
            val repo = setupGlobalBranding("builtin", "home")
            val globalBranding = repo.getBranding()!!

            val result = SessionStorage.copyGlobalBrandingToSession(sessionsRoot, sessionId, globalBranding)

            assertTrue("copyGlobalBrandingToSession must return true", result)
            val brandingFile = File(sessionsRoot, "$sessionId/branding-handle.png")
            assertTrue("branding-handle.png must exist after copy", brandingFile.isFile)
            assertArrayEquals("Copied PNG must match global branding", minimalPng(), brandingFile.readBytes())

            val json = JSONObject(File(sessionsRoot, "$sessionId/metadata.json").readText())
            assertEquals("builtin", json.getJSONObject("branding").getString("type"))
            assertEquals("home", json.getJSONObject("branding").getString("builtinId"))
        }
    }

    @Test
    fun copyGlobalBrandingToSession_invalidSessionId_returnsFalse() {
        runBlocking {
            val repo = setupGlobalBranding("image")
            val globalBranding = repo.getBranding()!!
            assertFalse(SessionStorage.copyGlobalBrandingToSession(sessionsRoot, "../evil", globalBranding))
        }
    }

    // ── Session independence: global change does not affect existing session ──

    @Test
    fun sessionBranding_independentFromGlobalBranding_afterCreation() {
        runBlocking {
            // Create session with global branding A
            val repo = GlobalBrandingRepository(brandingDir)
            val pngA = ByteArray(64) { 0xAA.toByte() }
            repo.setBranding(pngA, "image", null)
            val sessionDir = saveTestSession(repo)

            // Change global branding to B
            val pngB = ByteArray(64) { 0xBB.toByte() }
            repo.setBranding(pngB, "image", null)

            // Session branding must still be A
            val sessionBrandingFile = File(sessionDir, "branding-handle.png")
            assertArrayEquals("Session branding must be independent from global branding changes", pngA, sessionBrandingFile.readBytes())
        }
    }
}
