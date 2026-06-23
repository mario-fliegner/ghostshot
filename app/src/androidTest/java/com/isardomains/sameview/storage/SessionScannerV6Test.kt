package com.isardomains.sameview.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.SessionScanner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies SessionScanner behaviour for schema version 6 sessions.
 *
 * Block 0 — SESSION_BRANDING_V1.md §9.8 / SESSION_BRANDING_IMPLEMENTATION_PLAN.md §3.7
 */
@RunWith(AndroidJUnit4::class)
class SessionScannerV6Test {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionScannerV6Test")

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createSessionDir(sessionId: String): File =
        File(testRoot, sessionId).also { it.mkdirs() }

    private fun touch(dir: File, name: String) = File(dir, name).createNewFile()

    /**
     * Creates a minimal v6 metadata.json. Branding fields are optional; pass non-null values
     * to include them. [brandingHandleFilename] goes into files.brandingHandle.
     * [brandingType] and [brandingBuiltinId] go into the branding block (only when
     * [brandingHandleFilename] is non-null).
     */
    private fun writeV6Metadata(
        sessionDir: File,
        timestamp: Long = 1_000L,
        brandingHandleFilename: String? = null,
        brandingType: String? = null,
        brandingBuiltinId: String? = null,
        brandingUpdatedAtMs: Long = 2_000L,
        includeBrandingBlock: Boolean = brandingHandleFilename != null
    ) {
        val json = JSONObject().apply {
            put("version", 6)
            put("session", JSONObject().apply {
                put("id", sessionDir.name)
                put("createdAtMs", timestamp)
            })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("captureOriginal", "capture-original.jpg")
                put("reference", "reference.jpg")
                put("referenceOriginal", "reference-original.jpg")
                put("referenceSourceOriginal", "reference-source-original.jpg")
                if (brandingHandleFilename != null) {
                    put("brandingHandle", brandingHandleFilename)
                }
            })
            put("capture", JSONObject().apply { put("timestampMs", timestamp) })
            put("additional", JSONObject().apply {
                put("isFavorite", false)
                put("visibility", "private")
                put("source", "sameview")
            })
            if (includeBrandingBlock) {
                put("branding", JSONObject().apply {
                    put("handleFile", brandingHandleFilename ?: "branding-handle.png")
                    put("type", brandingType ?: "image")
                    if (brandingBuiltinId != null) put("builtinId", brandingBuiltinId)
                    put("updatedAtMs", brandingUpdatedAtMs)
                })
            }
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
    }

    /** Creates all mandatory v6 session files (touch stubs). */
    private fun touchMandatoryFiles(sessionDir: File) {
        touch(sessionDir, "capture.jpg")
        touch(sessionDir, "capture-original.jpg")
        touch(sessionDir, "reference.jpg")
        touch(sessionDir, "reference-original.jpg")
        touch(sessionDir, "reference-source-original.jpg")
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun v6Session_withoutBranding_isAccepted() {
        val dir = createSessionDir("2026-07-01_10-00-00")
        writeV6Metadata(dir, timestamp = 5_000L)
        touchMandatoryFiles(dir)

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        assertEquals("2026-07-01_10-00-00", result[0].sessionId)
        assertNull("branding must be null when no branding fields are present", result[0].branding)
    }

    @Test
    fun v6Session_withBrandingImageAndFilePresent_isAccepted_withBrandingFilled() {
        val dir = createSessionDir("2026-07-01_10-00-01")
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "branding-handle.png",
            brandingType = "image",
            brandingBuiltinId = null,
            brandingUpdatedAtMs = 9_000L
        )
        touchMandatoryFiles(dir)
        touch(dir, "branding-handle.png")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        val branding = result[0].branding
        assertNotNull("branding must not be null when file and block are both present", branding)
        assertEquals("branding-handle.png", branding!!.handleFile)
        assertEquals("image", branding.type)
        assertNull("builtinId must be null for type=image", branding.builtinId)
        assertEquals(9_000L, branding.updatedAtMs)
    }

    @Test
    fun v6Session_withBuiltinSymbol_isAccepted_withBuiltinIdFilled() {
        val dir = createSessionDir("2026-07-01_10-00-02")
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "branding-handle.png",
            brandingType = "builtin",
            brandingBuiltinId = "fire",
            brandingUpdatedAtMs = 8_000L
        )
        touchMandatoryFiles(dir)
        touch(dir, "branding-handle.png")

        val result = SessionScanner.scan(testRoot)

        assertEquals(1, result.size)
        val branding = result[0].branding
        assertNotNull(branding)
        assertEquals("builtin", branding!!.type)
        assertEquals("fire", branding.builtinId)
    }

    @Test
    fun v6Session_withUnknownBrandingType_isAccepted() {
        val dir = createSessionDir("2026-07-01_10-00-03")
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "branding-handle.png",
            brandingType = "future_type_unknown",
            brandingBuiltinId = null
        )
        touchMandatoryFiles(dir)
        touch(dir, "branding-handle.png")

        // Forward-compatibility: unknown type must not reject the session.
        val result = SessionScanner.scan(testRoot)
        assertEquals(1, result.size)
        assertNotNull(result[0].branding)
        assertEquals("future_type_unknown", result[0].branding!!.type)
    }

    @Test
    fun v6Session_brandingHandleFileReferencedButMissing_isRejected() {
        val dir = createSessionDir("2026-07-01_10-00-04")
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "branding-handle.png",
            brandingType = "image"
        )
        touchMandatoryFiles(dir)
        // branding-handle.png intentionally NOT created

        val result = SessionScanner.scan(testRoot)
        assertTrue("Session must be rejected when brandingHandle file is missing", result.isEmpty())
    }

    @Test
    fun v6Session_brandingHandleUnsafePath_isRejected() {
        val dir = createSessionDir("2026-07-01_10-00-05")
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "../branding-handle.png",
            brandingType = "image"
        )
        touchMandatoryFiles(dir)

        val result = SessionScanner.scan(testRoot)
        assertTrue("Session with path-traversal brandingHandle must be rejected", result.isEmpty())
    }

    @Test
    fun v6Session_brandingBlockPresentButNoBrandingHandleInFiles_treatedAsNoBranding() {
        val dir = createSessionDir("2026-07-01_10-00-06")
        // Write branding block but omit files.brandingHandle — inconsistency, tolerated.
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = null,          // files.brandingHandle absent
            includeBrandingBlock = true,            // branding block present
            brandingType = "image"
        )
        touchMandatoryFiles(dir)

        val result = SessionScanner.scan(testRoot)
        assertEquals("Session must be accepted despite inconsistency", 1, result.size)
        assertNull("branding must be null due to inconsistency", result[0].branding)
    }

    @Test
    fun v6Session_brandingHandleInFilesPresentButNoBrandingBlock_treatedAsNoBranding() {
        val dir = createSessionDir("2026-07-01_10-00-07")
        // Write files.brandingHandle but omit the branding block — inconsistency, tolerated.
        writeV6Metadata(
            dir,
            timestamp = 5_000L,
            brandingHandleFilename = "branding-handle.png",
            includeBrandingBlock = false             // branding block absent
        )
        touchMandatoryFiles(dir)
        touch(dir, "branding-handle.png")

        val result = SessionScanner.scan(testRoot)
        assertEquals("Session must be accepted despite inconsistency", 1, result.size)
        assertNull("branding must be null due to inconsistency", result[0].branding)
    }

    // ── Regression guards ─────────────────────────────────────────────────────

    @Test
    fun v5Session_remainsAccepted_withNullBranding() {
        val dir = createSessionDir("2026-06-01_10-00-00")
        val json = JSONObject().apply {
            put("version", 5)
            put("session", JSONObject().apply {
                put("id", dir.name)
                put("createdAtMs", 3_000L)
            })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("captureOriginal", "capture-original.jpg")
                put("reference", "reference.jpg")
                put("referenceOriginal", "reference-original.jpg")
                put("referenceSourceOriginal", "reference-source-original.jpg")
            })
            put("capture", JSONObject().apply { put("timestampMs", 3_000L) })
            put("additional", JSONObject().apply { put("isFavorite", false) })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "capture.jpg")
        touch(dir, "capture-original.jpg")
        touch(dir, "reference.jpg")
        touch(dir, "reference-original.jpg")
        touch(dir, "reference-source-original.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals("v5 session must remain scannable", 1, result.size)
        assertNull("branding must be null for v5 sessions", result[0].branding)
        assertEquals(3_000L, result[0].timestamp)
    }

    @Test
    fun v2Session_remainsAccepted_withNullBranding() {
        val dir = createSessionDir("2026-01-01_10-00-00")
        val json = JSONObject().apply {
            put("version", 2)
            put("session", JSONObject().apply { put("createdAtMs", 1_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "capture.jpg")
        touch(dir, "reference.jpg")

        val result = SessionScanner.scan(testRoot)

        assertEquals("v2 session must remain scannable", 1, result.size)
        assertNull("branding must be null for v2 sessions", result[0].branding)
    }

    @Test
    fun version7_remainsUnsupported() {
        val dir = createSessionDir("2026-07-01_12-00-00")
        val json = JSONObject().apply {
            put("version", 7)
            put("session", JSONObject().apply { put("createdAtMs", 1_000L) })
            put("files", JSONObject().apply {
                put("capture", "capture.jpg")
                put("reference", "reference.jpg")
            })
        }
        File(dir, "metadata.json").writeText(json.toString())
        touch(dir, "capture.jpg")
        touch(dir, "reference.jpg")

        assertTrue("Version 7 must not be accepted", SessionScanner.scan(testRoot).isEmpty())
    }
}
