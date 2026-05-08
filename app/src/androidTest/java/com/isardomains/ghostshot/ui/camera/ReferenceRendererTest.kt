package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceRendererTest {

    // 100×100, left half RED (x=0..49), right half BLUE (x=50..99)
    private fun splitBitmap(width: Int = 100, height: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.RED)
        canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        return bmp
    }

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    // -------------------------------------------------------------------------
    // Output dimensions
    // -------------------------------------------------------------------------

    @Test
    fun renderOutputDimensionsMatchViewport() {
        val source = solidBitmap(100, 100, Color.GREEN)
        val result = ReferenceRenderer.render(source, 150, 200, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(150, result.width)
        assertEquals(200, result.height)
    }

    // -------------------------------------------------------------------------
    // Fully opaque
    // -------------------------------------------------------------------------

    @Test
    fun outputIsFullyOpaque() {
        // 100×100 red source in 80×80 viewport — no empty areas, all pixels come from source.
        // fillScale = max(80/100, 80/100) = 0.8 → image fills viewport exactly.
        val source = solidBitmap(100, 100, Color.RED)
        val result = ReferenceRenderer.render(source, 80, 80, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                assertEquals("Pixel ($x,$y) alpha", 0xFF, Color.alpha(result.getPixel(x, y)))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Empty areas use app surface color
    // -------------------------------------------------------------------------

    @Test
    fun emptyAreasHaveAppSurfaceColor() {
        // 10×5 green source in 100×100 viewport, SHOW_FULL_IMAGE, scale=1, offset=0.
        // fitScale = min(100/10, 100/5) = min(10, 20) = 10
        // scaledW=100, scaledH=50 → top/bottom bars of 25px each.
        val source = solidBitmap(10, 5, Color.GREEN)
        val result = ReferenceRenderer.render(source, 100, 100, 1f, 0f, 0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        assertEquals(0xFF17202F.toInt(), result.getPixel(50, 0))    // top bar
        assertEquals(0xFF17202F.toInt(), result.getPixel(50, 99))   // bottom bar
        assertNotEquals(0xFF17202F.toInt(), result.getPixel(50, 50)) // center is green
    }

    // -------------------------------------------------------------------------
    // Source bitmap must not be recycled
    // -------------------------------------------------------------------------

    @Test
    fun sourceBitmapIsNotRecycled() {
        val source = solidBitmap(100, 100, Color.RED)
        ReferenceRenderer.render(source, 100, 100, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertFalse(source.isRecycled)
    }

    // -------------------------------------------------------------------------
    // Deterministic output
    // -------------------------------------------------------------------------

    @Test
    fun deterministicOutput() {
        val source = solidBitmap(100, 100, Color.CYAN)
        val result1 = ReferenceRenderer.render(source, 100, 100, 1.5f, 0.1f, 0.2f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        val result2 = ReferenceRenderer.render(source, 100, 100, 1.5f, 0.1f, 0.2f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        val pixels1 = IntArray(100 * 100)
        val pixels2 = IntArray(100 * 100)
        result1.getPixels(pixels1, 0, 100, 0, 0, 100, 100)
        result2.getPixels(pixels2, 0, 100, 0, 0, 100, 100)
        assertArrayEquals(pixels1, pixels2)
    }

    // -------------------------------------------------------------------------
    // COMPARE_WITH_PREVIEW — crop scale applied
    // -------------------------------------------------------------------------

    @Test
    fun compareMode_cropScaleApplied() {
        // Portrait 50×100 green source in 100×100 square viewport.
        // Crop: fillScale = max(100/50, 100/100) = 2.0 → scaledW=100 → no black borders.
        // Fit:  fitScale = min(100/50, 100/100) = 1.0 → scaledW=50 → left/right black bars.
        // imgX=0, imgY=-50, s=2 → pixel(50,50): source x=25, source y=50 → GREEN.
        val source = solidBitmap(50, 100, Color.GREEN)
        val result = ReferenceRenderer.render(source, 100, 100, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(Color.GREEN, result.getPixel(0, 50))   // left edge — black with Fit
        assertEquals(Color.GREEN, result.getPixel(98, 50))  // right edge — black with Fit
        assertEquals(Color.GREEN, result.getPixel(50, 50))  // center
    }

    // -------------------------------------------------------------------------
    // COMPARE_WITH_PREVIEW — clamp enforced
    // -------------------------------------------------------------------------

    @Test
    fun compareMode_clampEnforced() {
        // splitBitmap 100×100 in 100×100 viewport, overlayScale=2.
        // fillScale=1, s=2, scaledW=200, maxTX=50.
        // offset=0.5 → tX=50 (exactly at boundary); offset=5.0 → tX clamped to 50.
        // Both: imgX=-50+50=0 → pixel(90,50): source x=90/2=45 → RED.
        // Without clamp: offset=5.0 → imgX=450 → source x=-180 → BLACK (would fail).
        val source = splitBitmap()
        val resultAtBoundary = ReferenceRenderer.render(source, 100, 100, 2f, 0.5f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        val resultExtreme    = ReferenceRenderer.render(source, 100, 100, 2f, 5.0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(Color.RED, resultAtBoundary.getPixel(90, 50))
        assertEquals(Color.RED, resultExtreme.getPixel(90, 50))
    }

    // -------------------------------------------------------------------------
    // COMPARE_WITH_PREVIEW — clamp uses overlayScale in maxTX computation
    // -------------------------------------------------------------------------

    @Test
    fun compareMode_clampCorrectWithOverlayScale() {
        // splitBitmap 100×100 in 100×100 viewport, overlayScale=2, offsetX=0.4.
        // fillScale=1, s=2, scaledW=200, maxTX=50.
        // tX=40 (within clamp), imgX=-10.
        // pixel(60,50): source x=(60-(-10))/2=35 → RED.
        //
        // Wrong formula (maxTX ignores overlayScale): maxTX=0, tX=0, imgX=-50.
        // pixel(60,50): source x=(60-(-50))/2=55 → BLUE (test would fail).
        val source = splitBitmap()
        val result = ReferenceRenderer.render(source, 100, 100, 2f, 0.4f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(Color.RED, result.getPixel(60, 50))
    }

    // -------------------------------------------------------------------------
    // COMPARE_WITH_PREVIEW — positive offset shifts image right
    // -------------------------------------------------------------------------

    @Test
    fun compareMode_positiveOffsetShiftsImageCorrectly() {
        // splitBitmap 100×100 in 100×100 viewport, overlayScale=2.
        // fillScale=1, s=2, maxTX=50.
        // offset=0:   imgX=-50 → pixel(75,50): source x=62.5 → BLUE.
        // offset=0.4: imgX=-10 → pixel(75,50): source x=42.5 → RED.
        val source = splitBitmap()
        val resultNoOffset   = ReferenceRenderer.render(source, 100, 100, 2f, 0f,  0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        val resultWithOffset = ReferenceRenderer.render(source, 100, 100, 2f, 0.4f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(Color.BLUE, resultNoOffset.getPixel(75, 50))
        assertEquals(Color.RED,  resultWithOffset.getPixel(75, 50))
    }

    // -------------------------------------------------------------------------
    // SHOW_FULL_IMAGE — fit scale applied
    // -------------------------------------------------------------------------

    @Test
    fun showFullMode_fitScaleApplied() {
        // Portrait 50×100 green source in 100×100 square viewport.
        // fitScale = min(100/50, 100/100) = 1.0 → scaledW=50, imgX=25.
        // pixel(0,50):  source x=-25 → BLACK (left bar).
        // pixel(50,50): source x=25  → GREEN.
        // pixel(99,50): source x=74  → BLACK (right bar, beyond source width 50).
        val source = solidBitmap(50, 100, Color.GREEN)
        val result = ReferenceRenderer.render(source, 100, 100, 1f, 0f, 0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        assertEquals(0xFF17202F.toInt(), result.getPixel(0, 50))   // left bar
        assertEquals(Color.GREEN, result.getPixel(50, 50))         // center
        assertEquals(0xFF17202F.toInt(), result.getPixel(99, 50))  // right bar
    }

    // -------------------------------------------------------------------------
    // SHOW_FULL_IMAGE — no clamp applied
    // -------------------------------------------------------------------------

    @Test
    fun showFullMode_noClampApplied() {
        // Solid RED 100×100 in 100×100 viewport, overlayScale=1, offsetX=1.5.
        // SHOW_FULL_IMAGE: fitScale=1, tX=150, imgX=150 → image entirely off screen → BLACK.
        // COMPARE_WITH_PREVIEW: maxTX=0, tX clamped to 0, imgX=0 → image covers viewport → RED.
        val source = solidBitmap(100, 100, Color.RED)
        val showFullResult  = ReferenceRenderer.render(source, 100, 100, 1f, 1.5f, 0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        val compareResult   = ReferenceRenderer.render(source, 100, 100, 1f, 1.5f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(0xFF17202F.toInt(), showFullResult.getPixel(50, 50))
        assertEquals(Color.RED,   compareResult.getPixel(50, 50))
    }

    // -------------------------------------------------------------------------
    // SHOW_FULL_IMAGE — positive offset shifts image right
    // -------------------------------------------------------------------------

    @Test
    fun showFullMode_positiveOffsetShiftsImageCorrectly() {
        // Solid RED 100×100 in 100×100 viewport, fitScale=1.
        // offset=0:   imgX=0  → pixel(0,50): source x=0  → RED.
        // offset=0.3: imgX=30 → pixel(0,50): source x=-30 → out of bounds → BLACK.
        val source = solidBitmap(100, 100, Color.RED)
        val resultNoOffset   = ReferenceRenderer.render(source, 100, 100, 1f, 0f,  0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        val resultWithOffset = ReferenceRenderer.render(source, 100, 100, 1f, 0.3f, 0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        assertEquals(Color.RED,   resultNoOffset.getPixel(0, 50))
        assertEquals(0xFF17202F.toInt(), resultWithOffset.getPixel(0, 50))
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun viewportWidthZeroThrows() {
        val source = solidBitmap(100, 100, Color.RED)
        ReferenceRenderer.render(source, 0, 100, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
    }

    @Test(expected = IllegalArgumentException::class)
    fun viewportHeightZeroThrows() {
        val source = solidBitmap(100, 100, Color.RED)
        ReferenceRenderer.render(source, 100, 0, 1f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
    }

    @Test
    fun compareMode_scaleMinStillRenders() {
        val source = solidBitmap(100, 100, Color.RED)
        val result = ReferenceRenderer.render(source, 100, 100, 0.001f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(100, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun compareMode_scaleMaxStillRenders() {
        val source = solidBitmap(100, 100, Color.RED)
        val result = ReferenceRenderer.render(source, 100, 100, 100f, 0f, 0f, ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(100, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun showFullMode_extremeOffsetsCanRenderAppSurfaceFrame() {
        // offset=10 → tX=1000, imgX=1000 → image entirely off screen to the right.
        val source = solidBitmap(100, 100, Color.RED)
        val result = ReferenceRenderer.render(source, 100, 100, 1f, 10f, 0f, ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        assertEquals(0xFF17202F.toInt(), result.getPixel(50, 50))
    }
}
