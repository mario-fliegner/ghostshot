package com.isardomains.sameview.image

import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareRenderConfigTest {

    // --- computeCanvasDimensions: Standard ---

    @Test
    fun standard_viewportFitsIn2048_noScalingApplied() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
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
        // null captureOriginalDims → non-HQ path → viewport dimensions directly
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
        val sliderDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        val sbsDims    = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SIDE_BY_SIDE)

        assertEquals("SbS compH must be half of Slider compH", sliderDims.compH / 2, sbsDims.compH)
        assertEquals("compW must be unchanged between styles", sliderDims.compW, sbsDims.compW)
    }

    @Test
    fun sideBySide_canvasH_isCorrect() {
        val sbsDims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SIDE_BY_SIDE)

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
        val oneLine   = ShareCaptionData(null, "2008 → 2026", null)
        val twoLines  = ShareCaptionData("Title", "2008 → 2026", null)
        val threeLines = ShareCaptionData("Title", "2008 → 2026", "München")

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
        val oneLine    = ShareCaptionData(null, "2008 → 2026", null)
        val threeLines = ShareCaptionData("Title", "2008 → 2026", "München")

        val h1 = preciseCaptionHeight(oneLine, 1080, 1920)
        val h3 = preciseCaptionHeight(threeLines, 1080, 1920)

        assertTrue(
            "1-line height ($h1) must be < 55% of 3-line height ($h3)",
            h1 < h3 * 0.55f
        )
    }

    @Test
    fun captionHeight_canvasH_scalesWithLineCount() {
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

    // ── useBranding / captureOriginalFile fields ─────────────────────────────

    @Test
    fun shareRenderConfig_useBranding_defaultFalse() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = File("/fake"),
            exportTimestamp = "20260101_120000"
        )
        assertFalse("useBranding must default to false", config.useBranding)
    }

    @Test
    fun shareRenderConfig_useBranding_canBeSetTrue() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = File("/fake"),
            exportTimestamp = "20260101_120000",
            useBranding = true
        )
        assertTrue("useBranding must be true when set", config.useBranding)
    }

    @Test
    fun shareRenderConfig_captureOriginalFile_defaultNull() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.ORIGINAL,
            captionData = null,
            sessionDir = File("/fake"),
            exportTimestamp = "20260101_120000"
        )
        assertNull("captureOriginalFile must default to null", config.captureOriginalFile)
    }

    @Test
    fun shareRenderConfig_captureOriginalFile_canBeSet() {
        val file = File("/fake/capture-original.jpg")
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.ORIGINAL,
            captionData = null,
            sessionDir = File("/fake"),
            exportTimestamp = "20260101_120000",
            captureOriginalFile = file
        )
        assertEquals(file, config.captureOriginalFile)
    }

    @Test
    fun computeCanvasDimensions_unaffectedByUseBranding() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null, ShareComparisonStyle.SLIDER)
        assertEquals(1080, dims.compW)
        assertEquals(1920, dims.compH)
    }

    // ── T-HQ-U-01: Standard ignores captureOriginalDims ──────────────────────

    @Test
    fun standard_captureOriginalDimsIgnored() {
        // STANDARD must be unaffected by captureOriginalDims regardless of its value.
        val dimsWithout = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null)
        val dimsWith    = computeCanvasDimensions(1080, 1920, ShareQuality.STANDARD, null,
            captureOriginalDims = Pair(4032, 3024))
        assertEquals("Standard compW must be unaffected", dimsWithout.compW, dimsWith.compW)
        assertEquals("Standard compH must be unaffected", dimsWithout.compH, dimsWith.compH)
        assertEquals("Standard canvasW must be unaffected", dimsWithout.canvasW, dimsWith.canvasW)
        assertEquals("Standard canvasH must be unaffected", dimsWithout.canvasH, dimsWith.canvasH)
    }

    // ── T-HQ-U-02: Original + HQ dims → canvas exceeds 2048 px ──────────────

    @Test
    fun original_withHqDims_canvasLongestEdgeExceeds2048() {
        // viewport 1080x1920 (longest=1920), capture-original 3024x4032 → scale driven by cap
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            captureOriginalDims = Pair(3024, 4032))
        val longest = maxOf(dims.compW, dims.compH)
        assertTrue("Longest edge $longest must be > 2048", longest > MAX_COMPARISON_LONGEST_EDGE)
        assertTrue("Longest edge $longest must be <= 3840", longest <= MAX_HQ_LONGEST_EDGE)
    }

    // ── T-HQ-U-03: Aspect ratio matches viewport ─────────────────────────────

    @Test
    fun original_withHqDims_canvasAspectRatioMatchesViewport() {
        val vW = 1080; val vH = 1920
        val dims = computeCanvasDimensions(vW, vH, ShareQuality.ORIGINAL, null,
            captureOriginalDims = Pair(3024, 4032))
        val expectedRatio = vH.toDouble() / vW.toDouble()
        val actualRatio   = dims.compH.toDouble() / dims.compW.toDouble()
        assertEquals("Aspect ratio must match viewport (±5 %)", expectedRatio, actualRatio, expectedRatio * 0.05)
    }

    // ── T-HQ-U-04: 3840 px cap enforced ─────────────────────────────────────

    @Test
    fun original_withVeryLargeHqDims_cappedAt3840() {
        // Huge capture-original: scale would far exceed cap → longest edge = MAX_HQ_LONGEST_EDGE
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            captureOriginalDims = Pair(10000, 20000))
        val longest = maxOf(dims.compW, dims.compH)
        assertEquals("Longest edge must be capped at $MAX_HQ_LONGEST_EDGE", MAX_HQ_LONGEST_EDGE, longest)
    }

    // ── T-HQ-U-05: null captureOriginalDims → viewport dimensions ────────────

    @Test
    fun original_withNullHqDims_usesViewportDirectly() {
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            captureOriginalDims = null)
        assertEquals("compW must equal viewport width", 1080, dims.compW)
        assertEquals("compH must equal viewport height", 1920, dims.compH)
    }

    // ── T-HQ-U-06: coerceAtLeast(1f) — no downscaling when source < viewport ─

    @Test
    fun original_withHqDimsSmallerThanViewport_noDownscale() {
        // capture-original 540x960 is half the viewport 1080x1920 → scale 0.5, clamped to 1.0
        val dims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            captureOriginalDims = Pair(540, 960))
        assertEquals("compW must not be downscaled below viewport", 1080, dims.compW)
        assertEquals("compH must not be downscaled below viewport", 1920, dims.compH)
    }

    // ── T-HQ-U-07: Even dimensions for HQ canvas ─────────────────────────────

    @Test
    fun original_withHqDims_allDimensionsEven() {
        // Odd-ish inputs to exercise rounding paths
        val dims = computeCanvasDimensions(1081, 1921, ShareQuality.ORIGINAL, null,
            captureOriginalDims = Pair(3025, 4033))
        assertEquals("compW must be even", 0, dims.compW % 2)
        assertEquals("compH must be even", 0, dims.compH % 2)
        assertEquals("canvasW must be even", 0, dims.canvasW % 2)
        assertEquals("canvasH must be even", 0, dims.canvasH % 2)
    }

    // ── T-HQ-U-08: Side by side compH = makeEven(compHBase / 2) for HQ ──────

    @Test
    fun original_withHqDims_sideBySide_compHIsHalfOfSlider() {
        val origDims = Pair(3024, 4032)
        val sliderDims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            ShareComparisonStyle.SLIDER, captureOriginalDims = origDims)
        val sbsDims = computeCanvasDimensions(1080, 1920, ShareQuality.ORIGINAL, null,
            ShareComparisonStyle.SIDE_BY_SIDE, captureOriginalDims = origDims)

        assertEquals("HQ SbS compH must be half of HQ Slider compH",
            sliderDims.compH / 2, sbsDims.compH)
        assertEquals("compW must be equal for both styles", sliderDims.compW, sbsDims.compW)
    }

    // ── T-HQ-U-15: readOverlayParams — valid overlay block ───────────────────

    @Test
    fun readOverlayParams_validOverlayBlock_parsesCorrectly() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            File(dir, "metadata.json").writeText(
                """{"overlay":{"scale":1.25,"offsetX":-0.1,"offsetY":0.05,"displayMode":"COMPARE_WITH_PREVIEW"}}"""
            )
            val params = readOverlayParams(dir)
            assertNotNull("readOverlayParams must return non-null for valid block", params)
            assertEquals(1.25f, params!!.scale, 0.001f)
            assertEquals(-0.1f, params.offsetX, 0.001f)
            assertEquals(0.05f, params.offsetY, 0.001f)
            assertEquals(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, params.displayMode)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readOverlayParams_showFullImageMode_parsesCorrectly() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            File(dir, "metadata.json").writeText(
                """{"overlay":{"scale":0.8,"offsetX":0.0,"offsetY":0.0,"displayMode":"SHOW_FULL_IMAGE"}}"""
            )
            val params = readOverlayParams(dir)
            assertNotNull(params)
            assertEquals(ReferenceImageDisplayMode.SHOW_FULL_IMAGE, params!!.displayMode)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── T-HQ-U-16: readOverlayParams — null cases ────────────────────────────

    @Test
    fun readOverlayParams_missingOverlayBlock_returnsNull() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            File(dir, "metadata.json").writeText("""{"viewport":{"width":1080,"height":1920}}""")
            assertNull("Must return null when overlay block absent", readOverlayParams(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readOverlayParams_noMetadataJson_returnsNull() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            assertNull("Must return null when metadata.json absent", readOverlayParams(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readOverlayParams_invalidDisplayMode_returnsNull() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            File(dir, "metadata.json").writeText(
                """{"overlay":{"scale":1.0,"offsetX":0.0,"offsetY":0.0,"displayMode":"UNKNOWN_MODE"}}"""
            )
            assertNull("Must return null for unrecognised displayMode", readOverlayParams(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readOverlayParams_missingScaleField_returnsNull() {
        val dir = Files.createTempDirectory("sv-test-").toFile()
        try {
            File(dir, "metadata.json").writeText(
                """{"overlay":{"offsetX":0.0,"offsetY":0.0,"displayMode":"COMPARE_WITH_PREVIEW"}}"""
            )
            assertNull("Must return null when scale field absent", readOverlayParams(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
