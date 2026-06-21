package com.isardomains.sameview.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRenderConfigTest {

    // --- computeCanvasDimensions ---

    @Test
    fun standard_viewportFitsIn2048_noScalingApplied() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null)
        // comparison area <= 2048 on longest edge
        assertTrue(maxOf(dims.compW, dims.compH) <= MAX_COMPARISON_LONGEST_EDGE)
        assertEquals(1080, dims.compW)
        assertEquals(1920, dims.compH)
    }

    @Test
    fun standard_viewportExceeds2048_scaledDown() {
        val dims = computeCanvasDimensions(3024, 4032, ShareQuality.STANDARD, null)
        assertTrue(maxOf(dims.compW, dims.compH) <= MAX_COMPARISON_LONGEST_EDGE)
        // Aspect ratio preserved: 3024:4032 = 3:4
        val ratio = dims.compH.toDouble() / dims.compW.toDouble()
        assertEquals(4.0 / 3.0, ratio, 0.05)
    }

    @Test
    fun original_usesViewportDimensionsDirectly() {
        val dims = computeCanvasDimensions(3024, 4032, ShareQuality.ORIGINAL, null)
        assertEquals(3024, dims.compW)
        assertEquals(4032, dims.compH)
    }

    @Test
    fun canvasDimensions_alwaysEven() {
        // Feed an odd-ish viewport to trigger rounding
        val dims = computeCanvasDimensions(1081, 1921, ShareQuality.STANDARD, null)
        assertEquals(0, dims.compW % 2)
        assertEquals(0, dims.compH % 2)
        assertEquals(0, dims.canvasW % 2)
        assertEquals(0, dims.canvasH % 2)
    }

    @Test
    fun canvas_largerThanComparison_dueToPadding() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null)
        assertTrue(dims.canvasW > dims.compW)
        assertTrue(dims.canvasH > dims.compH)
    }

    @Test
    fun canvas_withCaption_tallerThanWithout() {
        val noCaptionDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null)
        val withCaptionData = ShareCaptionData(titleLine = "My title", dateLine = "2008 → 2026", locationLine = null)
        val captionDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, withCaptionData)
        assertTrue(captionDims.canvasH > noCaptionDims.canvasH)
    }

    @Test
    fun canvas_withoutCaption_compTopPlusCompHeightPlusCompTopEqualsCanvasH() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null)
        // canvasH = compH + 2 * outerPad (no caption)
        val expected = dims.compH + 2 * dims.outerPad
        // Allow ± 2 for even-rounding adjustments
        assertTrue(kotlin.math.abs(dims.canvasH - expected) <= 2)
    }

    // --- buildDisplayName ---

    @Test
    fun buildDisplayName_slider_containsSlider() {
        val name = buildDisplayName("20240615_143022", ShareComparisonStyle.SLIDER)
        assertTrue(name.contains("slider"))
        assertTrue(name.startsWith("SameView_"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun buildDisplayName_sideBySide_containsSidebyside() {
        val name = buildDisplayName("20240615_143022", ShareComparisonStyle.SIDE_BY_SIDE)
        assertTrue(name.contains("sidebyside"))
    }

    @Test
    fun buildDisplayName_containsTimestamp() {
        val ts = "20260621_120000"
        val name = buildDisplayName(ts, ShareComparisonStyle.SLIDER)
        assertTrue(name.contains(ts))
    }

    @Test
    fun buildDisplayName_noSessionId_noUnicode() {
        // Filename must be ASCII-safe regardless of session or user content
        val name = buildDisplayName("20260621_120000", ShareComparisonStyle.SLIDER)
        assertTrue(name.all { it.code < 128 })
    }

    // --- ShareCaptionData ---

    @Test
    fun captionData_allNull_hasNoContent() {
        val data = ShareCaptionData(null, null, null)
        assertFalse(data.hasContent)
        assertEquals(0, data.lineCount)
    }

    @Test
    fun captionData_withTitle_hasContent() {
        val data = ShareCaptionData(titleLine = "My title", dateLine = null, locationLine = null)
        assertTrue(data.hasContent)
        assertEquals(1, data.lineCount)
    }

    @Test
    fun captionData_allPresent_lineCountIsThree() {
        val data = ShareCaptionData("Title", "2008 → 2026", "München")
        assertEquals(3, data.lineCount)
    }

    @Test
    fun captionData_blankTitle_doesNotCountAsContent() {
        val data = ShareCaptionData(titleLine = "   ", dateLine = null, locationLine = null)
        assertFalse(data.hasContent)
        assertEquals(0, data.lineCount)
    }
}
