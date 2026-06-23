// path: app/src/test/java/com/isardomains/sameview/branding/GlobalBrandingRepositoryTest.kt
package com.isardomains.sameview.branding

import com.isardomains.sameview.ui.camera.SessionBrandingMeta
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [GlobalBrandingRepository].
 *
 * Uses [TemporaryFolder] so no actual filesDir is required. All file I/O runs on the
 * test JVM via [runTest] which allows suspend functions to execute on the calling thread.
 *
 * Block 2 — SESSION_BRANDING_V1.md §5 / SESSION_BRANDING_IMPLEMENTATION_PLAN.md §5.7
 */
class GlobalBrandingRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repo: GlobalBrandingRepository

    @Before
    fun setUp() {
        repo = GlobalBrandingRepository(File(tempFolder.root, "branding"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalPng(): ByteArray = ByteArray(16) { it.toByte() }

    private val handleFile get() = File(tempFolder.root, "branding/handle.png")
    private val metaFile get() = File(tempFolder.root, "branding/handle-meta.json")

    // ── U-14: hasBranding false when no file ──────────────────────────────────

    @Test
    fun hasBranding_false_whenNoBrandingFilesExist() {
        assertFalse(repo.hasBranding())
    }

    @Test
    fun getBrandingFile_null_whenNoBrandingFilesExist() {
        assertNull(repo.getBrandingFile())
    }

    @Test
    fun getBrandingMeta_null_whenNoMetaFileExists() {
        assertNull(repo.getBrandingMeta())
    }

    // ── U-15: setBranding creates both files ──────────────────────────────────

    @Test
    fun setBranding_imageType_createsBothFiles() = runTest {
        val png = minimalPng()
        repo.setBranding(png, "image", null)

        assertTrue("handle.png must exist after setBranding", handleFile.isFile)
        assertTrue("handle-meta.json must exist after setBranding", metaFile.isFile)
    }

    @Test
    fun setBranding_imageType_pngContentIsPreservedExactly() = runTest {
        val png = minimalPng()
        repo.setBranding(png, "image", null)

        assertArrayEquals("handle.png content must match input", png, handleFile.readBytes())
    }

    @Test
    fun setBranding_imageType_metaContainsCorrectType() = runTest {
        repo.setBranding(minimalPng(), "image", null)

        val meta = repo.getBrandingMeta()
        assertNotNull(meta)
        assertEquals("image", meta!!.type)
    }

    @Test
    fun setBranding_imageType_metaHasNullBuiltinId() = runTest {
        repo.setBranding(minimalPng(), "image", null)

        val meta = repo.getBrandingMeta()
        assertNotNull(meta)
        assertNull("builtinId must be null for type=image", meta!!.builtinId)
    }

    @Test
    fun setBranding_builtinType_metaContainsCorrectTypeAndBuiltinId() = runTest {
        repo.setBranding(minimalPng(), "builtin", "fire")

        val meta = repo.getBrandingMeta()
        assertNotNull(meta)
        assertEquals("builtin", meta!!.type)
        assertEquals("fire", meta.builtinId)
    }

    @Test
    fun hasBranding_true_afterSuccessfulSetBranding() = runTest {
        repo.setBranding(minimalPng(), "image", null)
        assertTrue(repo.hasBranding())
    }

    @Test
    fun getBrandingFile_returnsHandleFile_afterSetBranding() = runTest {
        repo.setBranding(minimalPng(), "image", null)
        val f = repo.getBrandingFile()
        assertNotNull(f)
        assertEquals(handleFile.absolutePath, f!!.absolutePath)
    }

    // ── U-16: removeBranding deletes both files ───────────────────────────────

    @Test
    fun removeBranding_deletesBothFiles() = runTest {
        repo.setBranding(minimalPng(), "image", null)
        repo.removeBranding()

        assertFalse("handle.png must not exist after removeBranding", handleFile.exists())
        assertFalse("handle-meta.json must not exist after removeBranding", metaFile.exists())
    }

    @Test
    fun hasBranding_false_afterRemoveBranding() = runTest {
        repo.setBranding(minimalPng(), "image", null)
        repo.removeBranding()
        assertFalse(repo.hasBranding())
    }

    @Test
    fun removeBranding_noOp_whenNoBrandingExists() = runTest {
        // Must not throw
        repo.removeBranding()
        assertFalse(repo.hasBranding())
    }

    // ── U-17: getBrandingMeta correct after setBranding ───────────────────────

    @Test
    fun getBrandingMeta_returnsCorrectMeta_forAllBuiltinIds() = runTest {
        BuiltinBrandingSymbol.entries.forEach { symbol ->
            repo.setBranding(minimalPng(), "builtin", symbol.id)
            val meta = repo.getBrandingMeta()
            assertNotNull("meta must not be null for symbol '${symbol.id}'", meta)
            assertEquals("builtin", meta!!.type)
            assertEquals(symbol.id, meta.builtinId)
        }
    }

    // ── setBranding overwrites existing branding ──────────────────────────────

    @Test
    fun setBranding_overwritesExistingBranding_withNewValues() = runTest {
        val png1 = ByteArray(16) { 0x01 }
        val png2 = ByteArray(16) { 0x02 }

        repo.setBranding(png1, "builtin", "heart")
        repo.setBranding(png2, "image", null)

        assertArrayEquals("handle.png must contain second PNG", png2, handleFile.readBytes())
        val meta = repo.getBrandingMeta()
        assertNotNull(meta)
        assertEquals("image", meta!!.type)
        assertNull("builtinId must be null for second branding", meta.builtinId)
    }

    // ── hasBranding consistency checks ───────────────────────────────────────

    @Test
    fun hasBranding_false_whenHandlePngExistsButMetaMissing() = runTest {
        // Simulate: handle.png present, handle-meta.json absent
        val brandingDir = File(tempFolder.root, "branding").also { it.mkdirs() }
        File(brandingDir, "handle.png").writeBytes(minimalPng())
        // handle-meta.json intentionally not created

        assertFalse(
            "hasBranding must be false when meta file is missing",
            repo.hasBranding()
        )
    }

    @Test
    fun hasBranding_false_whenMetaExistsButHandlePngMissing() = runTest {
        val brandingDir = File(tempFolder.root, "branding").also { it.mkdirs() }
        // handle.png intentionally not created
        File(brandingDir, "handle-meta.json").writeText("""{"type":"image"}""")

        assertFalse(
            "hasBranding must be false when handle.png is missing",
            repo.hasBranding()
        )
    }

    @Test
    fun hasBranding_false_whenMetaJsonIsInvalid() = runTest {
        val brandingDir = File(tempFolder.root, "branding").also { it.mkdirs() }
        File(brandingDir, "handle.png").writeBytes(minimalPng())
        File(brandingDir, "handle-meta.json").writeText("not valid json {{ }")

        assertFalse(
            "hasBranding must be false when meta JSON is invalid",
            repo.hasBranding()
        )
    }

    @Test
    fun getBrandingMeta_null_whenMetaJsonIsInvalid() {
        val brandingDir = File(tempFolder.root, "branding").also { it.mkdirs() }
        File(brandingDir, "handle-meta.json").writeText("not valid json {{ }")

        assertNull(
            "getBrandingMeta must return null for invalid JSON",
            repo.getBrandingMeta()
        )
    }

    @Test
    fun hasBranding_false_whenMetaJsonHasEmptyType() = runTest {
        val brandingDir = File(tempFolder.root, "branding").also { it.mkdirs() }
        File(brandingDir, "handle.png").writeBytes(minimalPng())
        File(brandingDir, "handle-meta.json").writeText("""{"type":""}""")

        assertFalse(
            "hasBranding must be false when meta type field is empty",
            repo.hasBranding()
        )
    }

    // ── temp files are cleaned up ─────────────────────────────────────────────

    @Test
    fun setBranding_leavesNoTempFiles_afterSuccess() = runTest {
        repo.setBranding(minimalPng(), "image", null)

        val brandingDir = File(tempFolder.root, "branding")
        val files = brandingDir.listFiles() ?: emptyArray()
        val names = files.map { it.name }
        assertFalse("handle-new.png must not remain after successful setBranding", "handle-new.png" in names)
        assertFalse("handle-meta-new.json must not remain after successful setBranding", "handle-meta-new.json" in names)
    }

    @Test
    fun setBranding_brandingDirIsCreatedIfMissing() = runTest {
        // Use a nested dir that doesn't exist yet
        val newRepo = GlobalBrandingRepository(File(tempFolder.root, "new/nested/branding"))
        newRepo.setBranding(minimalPng(), "image", null)
        assertTrue(newRepo.hasBranding())
    }
}
