package com.isardomains.sameview.branding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
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
 * Verifies BrandingNormalizer output format (size, config, transparency, Fit semantics)
 * and the metadata-clean guarantee (no EXIF, GPS, or other metadata in output PNG).
 *
 * Block 1 — SESSION_BRANDING_V1.md §7.3 and §7.5
 * Instrumentation test: Bitmap APIs require Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class BrandingNormalizerTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val tempFile by lazy { File(appContext.cacheDir, "branding_normalizer_test_output.png") }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a solid-colour bitmap of given dimensions. */
    private fun solidBitmap(w: Int, h: Int, color: Int = Color.RED): Bitmap {
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bm.eraseColor(color)
        return bm
    }

    /** Creates a bitmap with transparent (alpha=0) pixels everywhere. */
    private fun transparentBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // createBitmap initialises all pixels to 0x00000000 (fully transparent)

    /** Decodes the output ByteArray back to a Bitmap for pixel inspection. */
    private fun decodeOutput(bytes: ByteArray): Bitmap {
        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("Output ByteArray must decode to a valid Bitmap", bm)
        return bm!!
    }

    // ── U-01: dimensions ──────────────────────────────────────────────────────

    @Test
    fun normalize_anyInput_outputIs512x512() {
        val source = solidBitmap(800, 600)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        assertEquals("Output width must be 512", 512, decoded.width)
        assertEquals("Output height must be 512", 512, decoded.height)
        decoded.recycle()
    }

    // ── U-02: config and transparency ─────────────────────────────────────────

    @Test
    fun normalize_squareBitmap_cornersAreTransparent_andConfigIsARGB8888() {
        // A square bitmap exactly fills the output — corners should still be transparent
        // because createBitmap initialises to transparent and the source is drawn on top.
        // For a non-square source, corners outside the drawn region are transparent.
        // Use a landscape source so the top-left corner is definitely outside the drawn region.
        val source = solidBitmap(800, 200, Color.BLUE)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        assertEquals(Bitmap.Config.ARGB_8888, decoded.config)
        // Top-left corner is outside the 512×128 centered content strip.
        val topLeft = decoded.getPixel(0, 0)
        assertEquals("Top-left corner alpha must be 0 (transparent)", 0, Color.alpha(topLeft))
        decoded.recycle()
    }

    // ── U-03: landscape → transparent top/bottom ──────────────────────────────

    @Test
    fun normalize_landscapeBitmap_topAndBottomPaddingIsTransparent() {
        // 800×200 → scale = min(512/800, 512/200) = 0.64 → scaledH = 128
        // content occupies rows 192..320; top/bottom rows are transparent.
        val source = solidBitmap(800, 200, Color.GREEN)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        // Row 0 is outside the content strip.
        val topEdgePixel = decoded.getPixel(256, 0)
        assertEquals("Top padding row must be transparent", 0, Color.alpha(topEdgePixel))
        // Row 511 is outside the content strip.
        val bottomEdgePixel = decoded.getPixel(256, 511)
        assertEquals("Bottom padding row must be transparent", 0, Color.alpha(bottomEdgePixel))
        decoded.recycle()
    }

    // ── U-04: portrait → transparent left/right ───────────────────────────────

    @Test
    fun normalize_portraitBitmap_leftAndRightPaddingIsTransparent() {
        // 200×800 → scale = 0.64 → scaledW = 128; content occupies cols 192..320.
        val source = solidBitmap(200, 800, Color.YELLOW)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        val leftEdge = decoded.getPixel(0, 256)
        assertEquals("Left padding column must be transparent", 0, Color.alpha(leftEdge))
        val rightEdge = decoded.getPixel(511, 256)
        assertEquals("Right padding column must be transparent", 0, Color.alpha(rightEdge))
        decoded.recycle()
    }

    // ── U-05: square input ────────────────────────────────────────────────────

    @Test
    fun normalize_squareInputExactOutputSize_outputIs512x512() {
        val source = solidBitmap(512, 512, Color.CYAN)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
        // Centre pixel should be non-transparent (solid colour was drawn).
        val centre = decoded.getPixel(256, 256)
        assertEquals("Centre pixel alpha must be 255", 255, Color.alpha(centre))
        decoded.recycle()
    }

    // ── U-06: very small input ────────────────────────────────────────────────

    @Test
    fun normalize_verySmallBitmap_outputIs512x512() {
        val source = solidBitmap(32, 32)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
        decoded.recycle()
    }

    // ── U-07: very large input ────────────────────────────────────────────────

    @Test
    fun normalize_largeBitmap_outputIs512x512() {
        val source = solidBitmap(4096, 4096)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        val decoded = decodeOutput(output)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
        decoded.recycle()
    }

    // ── U-08: output is non-empty ─────────────────────────────────────────────

    @Test
    fun normalize_anyInput_outputBytesAreNonEmpty() {
        val source = solidBitmap(100, 100)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        assertTrue("Output ByteArray must not be empty", output.isNotEmpty())
    }

    // ── N-09: metadata-clean — no GPS in output ───────────────────────────────

    @Test
    fun normalize_jpegWithGps_outputPngHasNoGpsLatitude() {
        // Decode a GPS-bearing JPEG to a clean Bitmap (decoding discards all metadata).
        val source = testContext.assets.open("reference_jpg_with_gps.jpg").use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("reference_jpg_with_gps.jpg could not be decoded")

        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        // Write to temp file so ExifInterface can inspect it.
        tempFile.writeBytes(output)
        val exif = ExifInterface(tempFile.absolutePath)
        val latLon = FloatArray(2)
        assertFalse("Output PNG must not contain GPS coordinates", exif.getLatLong(latLon))
        assertNull(
            "TAG_GPS_LATITUDE must be absent in normalized PNG",
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        )
    }

    // ── N-10: metadata-clean — no Make/Model ─────────────────────────────────

    @Test
    fun normalize_anyBitmap_outputPngHasNoMakeOrModel() {
        // Any Bitmap: no source metadata is transferred to the PNG output.
        val source = solidBitmap(200, 200)
        val output = BrandingNormalizer.normalize(source)
        source.recycle()

        tempFile.writeBytes(output)
        val exif = ExifInterface(tempFile.absolutePath)
        assertNull(
            "TAG_MAKE must be absent in normalized PNG",
            exif.getAttribute(ExifInterface.TAG_MAKE)
        )
        assertNull(
            "TAG_MODEL must be absent in normalized PNG",
            exif.getAttribute(ExifInterface.TAG_MODEL)
        )
    }
}
