package com.isardomains.sameview.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRenderConfigTest {

    // --- computeCanvasDimensions ---

    @Test
    fun standard_viewportFitsIn2048_noScalingApplied() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        // comparison area <= 2048 on longest edge
        assertTrue(maxOf(dims.compW, dims.compH) <= MAX_COMPARISON_LONGEST_EDGE)
        assertEquals(1080, dims.compW)
        assertEquals(1920, dims.compH)
    }

    @Test
    fun standard_viewportExceeds2048_scaledDown() {
        val dims = computeCanvasDimensions(3024, 4032, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        assertTrue(maxOf(dims.compW, dims.compH) <= MAX_COMPARISON_LONGEST_EDGE)
        // Aspect ratio preserved: 3024:4032 = 3:4
        val ratio = dims.compH.toDouble() / dims.compW.toDouble()
        assertEquals(4.0 / 3.0, ratio, 0.05)
    }

    @Test
    fun original_usesViewportDimensionsDirectly() {
        val dims = computeCanvasDimensions(3024, 4032, ShareQuality.ORIGINAL, null, ShareComparisonStyle.SLIDER)
        assertEquals(3024, dims.compW)
        assertEquals(4032, dims.compH)
    }

    @Test
    fun canvasDimensions_alwaysEven() {
        // Feed an odd-ish viewport to trigger rounding
        val dims = computeCanvasDimensions(1081, 1921, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        assertEquals(0, dims.compW % 2)
        assertEquals(0, dims.compH % 2)
        assertEquals(0, dims.canvasW % 2)
        assertEquals(0, dims.canvasH % 2)
    }

    @Test
    fun canvas_largerThanComparison_dueToPadding() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        assertTrue(dims.canvasW > dims.compW)
        assertTrue(dims.canvasH > dims.compH)
    }

    @Test
    fun canvas_withCaption_tallerThanWithout() {
        val noCaptionDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        val withCaptionData = ShareCaptionData(titleLine = "My title", dateLine = "2008 → 2026", locationLine = null)
        val captionDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, withCaptionData, ShareComparisonStyle.SLIDER)
        assertTrue(captionDims.canvasH > noCaptionDims.canvasH)
    }

    @Test
    fun canvas_withoutCaption_compTopPlusCompHeightPlusCompTopEqualsCanvasH() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
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

    // --- Style-dependent compH (Side by side fix) ---

    @Test
    fun sideBySide_compH_isHalfOfSlider() {
        // For any viewport, Side by side compH must equal Slider compH / 2.
        // This ensures Fit-scaled images fill each half-width slot without empty dark zones.
        val sliderDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        val sbsDims    = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SIDE_BY_SIDE)

        assertEquals("SbS compH must be half of Slider compH", sliderDims.compH / 2, sbsDims.compH)
        assertEquals("compW must be unchanged between styles", sliderDims.compW, sbsDims.compW)
    }

    @Test
    fun sideBySide_canvasH_isCorrect() {
        // Verify that the full canvas height reflects the reduced comparison height.
        val sbsDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SIDE_BY_SIDE)

        // compH must be 960 (1920 / 2), canvasH = compH + 2 * outerPad (no caption)
        assertEquals(960, sbsDims.compH)
        val expectedCanvasH = sbsDims.compH + 2 * sbsDims.outerPad
        assertTrue(
            "canvasH (${sbsDims.canvasH}) must be close to compH + 2*outerPad ($expectedCanvasH)",
            kotlin.math.abs(sbsDims.canvasH - expectedCanvasH) <= 2
        )
    }

    // --- preciseCaptionHeight: dynamic line-count-dependent sizing ---

    @Test
    fun captionHeight_noCaption_isZero() {
        assertEquals(0, preciseCaptionHeight(null, 1080, 1920))
        assertEquals(0, preciseCaptionHeight(ShareCaptionData(null, null, null), 1080, 1920))
    }

    @Test
    fun captionHeight_lines_strictlyIncreasing() {
        val oneLine   = ShareCaptionData(null, "2008 → 2026", null)           // date only
        val twoLines  = ShareCaptionData("Title", "2008 → 2026", null)        // title + date
        val threeLines = ShareCaptionData("Title", "2008 → 2026", "München")  // all three

        val h1 = preciseCaptionHeight(oneLine, 1080, 1920)
        val h2 = preciseCaptionHeight(twoLines, 1080, 1920)
        val h3 = preciseCaptionHeight(threeLines, 1080, 1920)

        assertTrue("0-line must be 0", preciseCaptionHeight(null, 1080, 1920) == 0)
        assertTrue("1-line ($h1) must be > 0", h1 > 0)
        assertTrue("2-line ($h2) must be > 1-line ($h1)", h2 > h1)
        assertTrue("3-line ($h3) must be > 2-line ($h2)", h3 > h2)
    }

    @Test
    fun captionHeight_oneLine_significantlySmallerThanThreeLines() {
        // Core regression: 1 visible line must NOT reserve space for 3 lines.
        val oneLine    = ShareCaptionData(null, "2008 → 2026", null)
        val threeLines = ShareCaptionData("Title", "2008 → 2026", "München")

        val h1 = preciseCaptionHeight(oneLine, 1080, 1920)
        val h3 = preciseCaptionHeight(threeLines, 1080, 1920)

        // 1-line must be at most 55 % of 3-line height (no blanket 3-line reservation).
        assertTrue(
            "1-line height ($h1) must be < 55% of 3-line height ($h3)",
            h1 < h3 * 0.55f
        )
    }

    @Test
    fun captionHeight_canvasH_scalesWithLineCount() {
        // The canvas height must grow proportionally with caption line count.
        val noCaptionDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        val oneLineDims   = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD,
            ShareCaptionData(null, "2008 → 2026", null), ShareComparisonStyle.SLIDER)
        val threeLineDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD,
            ShareCaptionData("Title", "2008 → 2026", "München"), ShareComparisonStyle.SLIDER)

        assertTrue("1-line canvas (${oneLineDims.canvasH}) > no-caption (${noCaptionDims.canvasH})",
            oneLineDims.canvasH > noCaptionDims.canvasH)
        assertTrue("3-line canvas (${threeLineDims.canvasH}) > 1-line (${oneLineDims.canvasH})",
            threeLineDims.canvasH > oneLineDims.canvasH)
    }
}
