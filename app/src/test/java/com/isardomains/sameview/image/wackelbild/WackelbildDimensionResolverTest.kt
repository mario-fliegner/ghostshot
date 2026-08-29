// path: app/src/test/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolverTest.kt
package com.isardomains.sameview.image.wackelbild

import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WackelbildDimensionResolverTest {

    // ── resolve(): Capture is the weaker source ──────────────────────────────

    @Test
    fun resolve_captureIsWeakerSource_outputBoundedByCapture() {
        // viewport 1080x1920, capture-original only 1.2x, reference-original very large (10x)
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(1296, 2304), // 1.2x
            referenceOriginalDims = Pair(10800, 19200), // 10x
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        // commonScale should be ~1.2 (capture-bound), not 10 (reference) nor API caps.
        assertTrue("width $dims must be close to 1080*1.2=1296", dims.width in 1280..1310)
    }

    // ── resolve(): Reference is the weaker source ────────────────────────────

    @Test
    fun resolve_referenceIsWeakerSource_outputBoundedByReference() {
        // Reference source is only exactly the same size as the viewport at fitScale=1 with a
        // SHOW_FULL_IMAGE display mode and overlayScale=1 -> referenceScale=1 (no headroom).
        // Capture is huge (10x) -> reference must be the binding constraint.
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(10800, 19200), // 10x
            referenceOriginalDims = Pair(1080, 1920), // exactly viewport size at fitScale=1
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertEquals(1080, dims.width)
        assertEquals(1920, dims.height)
    }

    // ── resolve(): API side limit is the limiting factor ─────────────────────

    @Test
    fun resolve_apiSideLimit_isLimitingFactor() {
        // Square viewport, sources 100x oversized (never binding), a tight custom maxSidePx=2000
        // (apiSideScale=2) chosen to bind well before the default 80MP cap (apiMpScale=8.94 here).
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 1000,
            captureOriginalDims = Pair(100_000, 100_000),
            referenceOriginalDims = Pair(100_000, 100_000),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            maxSidePx = 2_000
        )
        assertEquals("side cap must bind exactly at maxSidePx", 2_000, dims.width)
        assertEquals(2_000, dims.height)
    }

    // ── resolve(): API megapixel limit is the limiting factor ────────────────

    @Test
    fun resolve_apiMegapixelLimit_isLimitingFactor() {
        // Large square viewport (8000x8000, 64MP) with a generous side cap (16000, apiSideScale=2)
        // but the default 80MP cap yields apiMpScale=sqrt(80e6/64e6)=1.118, which binds first.
        // Sources are 10x oversized so neither source scale can bind.
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 8_000, viewportH = 8_000,
            captureOriginalDims = Pair(80_000, 80_000),
            referenceOriginalDims = Pair(80_000, 80_000),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            maxSidePx = 16_000,
            maxMegapixels = 80_000_000L
        )
        val totalPixels = dims.width.toLong() * dims.height
        assertTrue("total pixels must be close to the 80MP cap, was $totalPixels", totalPixels in 78_000_000L..80_500_000L)
        assertTrue("MP cap (~8944px side) must bind well below the 16000px side cap", dims.width < 16_000)
    }

    // ── resolve(): no-upscale ─────────────────────────────────────────────────

    @Test
    fun resolve_bothSourcesSmallerThanViewport_outputSmallerThanViewport_noUpscale() {
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(540, 960), // 0.5x
            referenceOriginalDims = Pair(540, 960), // 0.5x at fitScale=1 -> referenceScale=1... use overlayScale to force <1
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        // Capture is the binding 0.5x constraint -> output must be ~half viewport, never upscaled to 1080x1920.
        assertTrue("width ${dims.width} must be < viewport width 1080 (no upscale)", dims.width < 1080)
        assertTrue("height ${dims.height} must be < viewport height 1920 (no upscale)", dims.height < 1920)
    }

    @Test
    fun resolve_commonScaleBelowOne_isAllowed_notCoercedToViewport() {
        // overlayScale > 1 (an originally-upscaled reference) drives referenceScale below 1,
        // and this must be honored, not coerced back up to the viewport size.
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 1000,
            captureOriginalDims = Pair(5000, 5000),
            referenceOriginalDims = Pair(1000, 1000),
            overlayScale = 2f, // effectiveScale = 1*2 = 2 -> referenceScale = 0.5
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertTrue("width ${dims.width} must reflect the sub-1 referenceScale", dims.width in 480..520)
    }

    // ── resolve(): identical output dimensions for width/height driven by same scale ─

    @Test
    fun resolve_squareViewport_producesSquareOutput() {
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 1000,
            captureOriginalDims = Pair(2000, 2000),
            referenceOriginalDims = Pair(2000, 2000),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertEquals(dims.width, dims.height)
    }

    // ── resolve(): deterministic rounding / even dimensions ──────────────────

    @Test
    fun resolve_alwaysProducesEvenDimensions() {
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1081, viewportH = 1921,
            captureOriginalDims = Pair(1297, 2305),
            referenceOriginalDims = Pair(1297, 2305),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertEquals(0, dims.width % 2)
        assertEquals(0, dims.height % 2)
    }

    @Test
    fun resolve_isDeterministic_sameInputsSameOutput() {
        fun compute() = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(2160, 3840),
            referenceOriginalDims = Pair(2160, 3840),
            overlayScale = 1.1f,
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )
        val a = compute()
        val b = compute()
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
    }

    // ── resolve(): session-ratio preservation ────────────────────────────────

    @Test
    fun resolve_outputRatioMatchesViewportRatio() {
        val vW = 1080; val vH = 1920
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = vW, viewportH = vH,
            captureOriginalDims = Pair(3024, 4032),
            referenceOriginalDims = Pair(3024, 4032),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        val expectedRatio = vH.toDouble() / vW
        val actualRatio = dims.height.toDouble() / dims.width
        assertEquals(expectedRatio, actualRatio, 0.01)
    }

    // ── resolve(): degenerate dimensions fail ────────────────────────────────

    @Test(expected = WackelbildHqUnusableException::class)
    fun resolve_degenerateOutput_throwsHqUnusable() {
        WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(10, 18), // absurdly tiny capture source
            referenceOriginalDims = Pair(10800, 19200),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
    }

    // ── resolve(): Reference offset does not affect sampling-density scale (Correction I) ─

    @Test
    fun resolve_referenceScale_unaffectedByOffset_fitMode() {
        // The resolve() function itself takes no offset parameter (proof: offset is orthogonal to
        // scale, §10.2 Correction I) -- this test asserts identical referenceScale-driven output
        // for both a "would be panned left" and "would be panned right" conceptual case, i.e. the
        // function signature has no offset input to vary in the first place, and the same
        // (viewport, referenceOriginalDims, overlayScale, displayMode) always yields the same
        // dims regardless of what offset the caller separately applies at render time.
        val dimsA = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(3000, 5333),
            referenceOriginalDims = Pair(1500, 2667),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        val dimsB = WackelbildDimensionResolver.resolve(
            viewportW = 1080, viewportH = 1920,
            captureOriginalDims = Pair(3000, 5333),
            referenceOriginalDims = Pair(1500, 2667),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertEquals(dimsA.width, dimsB.width)
        assertEquals(dimsA.height, dimsB.height)
    }

    @Test
    fun resolve_overlayScaleGreaterThanOne_reducesReferenceGenuineScale() {
        val dimsScale1 = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 1000,
            captureOriginalDims = Pair(5000, 5000),
            referenceOriginalDims = Pair(1000, 1000),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        val dimsScale2 = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 1000,
            captureOriginalDims = Pair(5000, 5000),
            referenceOriginalDims = Pair(1000, 1000),
            overlayScale = 2f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertTrue("higher overlayScale must reduce the resolvable output (${dimsScale2.width} < ${dimsScale1.width})",
            dimsScale2.width < dimsScale1.width)
    }

    @Test
    fun resolve_compareWithPreviewMode_fillSemantics_differsFromFitMode() {
        // FILL (COMPARE_WITH_PREVIEW) uses max(...) scale, FIT (SHOW_FULL_IMAGE) uses min(...) --
        // for a non-square reference source these diverge, exercising both supported modes.
        val fillDims = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 2000,
            captureOriginalDims = Pair(5000, 10000),
            referenceOriginalDims = Pair(2000, 2000), // non-viewport-ratio source
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )
        val fitDims = WackelbildDimensionResolver.resolve(
            viewportW = 1000, viewportH = 2000,
            captureOriginalDims = Pair(5000, 10000),
            referenceOriginalDims = Pair(2000, 2000),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertTrue("Fill and Fit must produce different genuine-scale-bound outputs for a non-viewport-ratio source",
            fillDims.width != fitDims.width || fillDims.height != fitDims.height)
    }

    // ── isRatioWithinTolerance() / roundingToleranceFor() (Block 5E) ─────────

    @Test
    fun isRatioWithinTolerance_exactMatch_isAcceptable() {
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(1080, 1920)
        assertTrue(WackelbildDimensionResolver.isRatioWithinTolerance(1080, 1920, 1080, 1920, tolerance))
    }

    @Test
    fun isRatioWithinTolerance_sameRatioDifferentSize_isAcceptable() {
        // 1280x720 is exactly 16:9, same as a 1920x1080 target -- relative error is exactly 0,
        // so this must pass regardless of how tight the dynamic tolerance is.
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(1920, 1080)
        assertTrue(WackelbildDimensionResolver.isRatioWithinTolerance(1280, 720, 1920, 1080, tolerance))
    }

    @Test
    fun isRatioWithinTolerance_1920x1088VsTarget1920x1080_isRejected() {
        // Block 5E: disproves the prior "benign encoder padding" assumption. 1920x1088 vs
        // 1920x1080 is a genuine ~0.735% ratio difference -- no repository/Android-API evidence
        // supports treating this as invisible codec/stream padding for a still JPEG (JPEG decoders
        // always expose the exact declared SOF width/height, never hidden padding). The dynamic
        // tolerance for a 1920x1080 target is 1/1080 ~= 0.0926%, roughly 8x smaller than the
        // actual mismatch -- correctly and decisively rejected.
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(1920, 1080)
        assertFalse(WackelbildDimensionResolver.isRatioWithinTolerance(1920, 1088, 1920, 1080, tolerance))
    }

    @Test
    fun isRatioWithinTolerance_grossMismatch_isRejected() {
        // 3:4 vs 9:16 -- ~33% relative error, far outside any realistic rounding tolerance.
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(900, 1600)
        assertFalse(WackelbildDimensionResolver.isRatioWithinTolerance(300, 400, 900, 1600, tolerance))
    }

    @Test
    fun isRatioWithinTolerance_justInsideDynamicBoundary_isAccepted() {
        // expectedW=2000 > expectedH=1000 (min=1000) so tolerance=1/1000=0.1%, and a 1-pixel
        // actualW step (actualH held at expectedH) only costs 1/expectedW=0.05% of relative error
        // -- fine enough granularity to land clearly inside without sitting exactly on the boundary.
        val expectedW = 2000; val expectedH = 1000
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(expectedW, expectedH)
        // actualW=2001 -> relErr = 1/2000 = 0.05%, half of the 0.1% tolerance.
        assertTrue(WackelbildDimensionResolver.isRatioWithinTolerance(2001, expectedH, expectedW, expectedH, tolerance))
    }

    @Test
    fun isRatioWithinTolerance_justOutsideDynamicBoundary_isRejected() {
        val expectedW = 2000; val expectedH = 1000
        val tolerance = WackelbildDimensionResolver.roundingToleranceFor(expectedW, expectedH)
        // actualW=2003 -> relErr = 3/2000 = 0.15%, 1.5x the 0.1% tolerance.
        assertFalse(WackelbildDimensionResolver.isRatioWithinTolerance(2003, expectedH, expectedW, expectedH, tolerance))
    }

    // ── roundingToleranceFor(): dynamic scaling ───────────────────────────────

    @Test
    fun roundingToleranceFor_1920x1080_equalsOneOver1080() {
        assertEquals(1f / 1080f, WackelbildDimensionResolver.roundingToleranceFor(1920, 1080), 1e-9f)
    }

    @Test
    fun roundingToleranceFor_1080x1920_equalsOneOver1080() {
        // min(1080, 1920) = 1080 regardless of argument order.
        assertEquals(1f / 1080f, WackelbildDimensionResolver.roundingToleranceFor(1080, 1920), 1e-9f)
    }

    @Test
    fun roundingToleranceFor_smallerViewport_getsProportionallyLargerTolerance() {
        val small = WackelbildDimensionResolver.roundingToleranceFor(300, 400)
        val large = WackelbildDimensionResolver.roundingToleranceFor(1920, 1080)
        assertTrue("smaller viewport's tolerance ($small) must be larger than a large viewport's ($large)",
            small > large)
    }

    @Test
    fun roundingToleranceFor_largerViewport_getsProportionallySmallerTolerance() {
        val tolerance4k = WackelbildDimensionResolver.roundingToleranceFor(3840, 2160)
        val toleranceHd = WackelbildDimensionResolver.roundingToleranceFor(1920, 1080)
        assertTrue("larger (4K) viewport's tolerance ($tolerance4k) must be smaller than HD's ($toleranceHd)",
            tolerance4k < toleranceHd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun roundingToleranceFor_nonPositiveDimension_throws() {
        WackelbildDimensionResolver.roundingToleranceFor(0, 1080)
    }

    // ── stepDownDimensions() ──────────────────────────────────────────────────

    @Test
    fun stepDownDimensions_appliesFactorToBothAxes_stayEven() {
        val original = WackelbildTargetDimensions(1000, 2000)
        val stepped = WackelbildDimensionResolver.stepDownDimensions(original, 0.85f)
        assertEquals(0, stepped.width % 2)
        assertEquals(0, stepped.height % 2)
        assertTrue(stepped.width < original.width)
        assertTrue(stepped.height < original.height)
    }

    // ── resolveFallbackDimensions(): Case A (identical) ──────────────────────

    @Test
    fun resolveFallbackDimensions_identicalDimensions_returnsThemDirectly() {
        val result = WackelbildDimensionResolver.resolveFallbackDimensions(Pair(1080, 1920), Pair(1080, 1920))
        assertNotNull(result)
        assertEquals(1080, result!!.width)
        assertEquals(1920, result.height)
    }

    // ── resolveFallbackDimensions(): Case B (compatible, different dims) ────

    @Test
    fun resolveFallbackDimensions_compatibleRatio_choosesWeakerSourceDims() {
        // Reference is smaller (weaker), same ratio as capture.
        val referenceDims = Pair(1000, 1778) // ~9:16
        val captureDims = Pair(2000, 3556)   // same ratio, larger
        val result = WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims)
        assertNotNull(result)
        assertEquals(referenceDims.first, result!!.width)
        assertEquals(referenceDims.second, result.height)
    }

    @Test
    fun resolveFallbackDimensions_compatibleRatio_neverUpscalesWeakerSource() {
        val referenceDims = Pair(1000, 1778)
        val captureDims = Pair(2000, 3556)
        val result = WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims)!!
        assertTrue(result.width <= referenceDims.first)
        assertTrue(result.height <= referenceDims.second)
    }

    // ── resolveFallbackDimensions(): Case C (incompatible) ───────────────────

    @Test
    fun resolveFallbackDimensions_incompatibleRatio_returnsNull() {
        val referenceDims = Pair(1080, 1920) // 9:16 portrait
        val captureDims = Pair(1600, 1200)   // 4:3 landscape
        val result = WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims)
        assertNull("Incompatible ratios must be a hard failure (null), never a coerced downscale", result)
    }

    @Test
    fun resolveFallbackDimensions_ratioBoundary_justInsideIsCompatible() {
        // referenceDims (2000x1000, min=1000) is the "expected" anchor -> tolerance = 1/1000 = 0.1%.
        // captureDims actualW step relErr = 1/2000 = 0.05% per pixel (see isRatioWithinTolerance
        // dynamic-boundary tests above for the derivation) -- 2001 lands clearly inside.
        val referenceDims = Pair(2000, 1000)
        val captureDims = Pair(2001, 1000)
        assertNotNull(WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims))
    }

    @Test
    fun resolveFallbackDimensions_ratioBoundary_justOutsideIsIncompatible() {
        val referenceDims = Pair(2000, 1000)
        val captureDims = Pair(2003, 1000) // relErr = 3/2000 = 0.15%, 1.5x the 0.1% tolerance
        assertNull(WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims))
    }

    @Test
    fun resolveFallbackDimensions_1080x1920VsNearMismatch1088x1920_isIncompatible() {
        // The exact near-mismatch shape from the Block 5E problem statement, transposed to a
        // portrait fallback pair: must not stretch, must hard-fail.
        val referenceDims = Pair(1080, 1920)
        val captureDims = Pair(1088, 1920)
        assertNull(
            "A genuine ~0.7% ratio mismatch must never be silently accepted for the fallback pair",
            WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims)
        )
    }

    // ── Output rounding is independent of the input-compatibility tolerance ──

    @Test
    fun resolve_outputEvenRoundingBehavior_unaffectedByToleranceChange() {
        // resolve()'s own makeEven/round() output step has no tolerance parameter at all -- it is
        // structurally independent of the Block 5E input-compatibility tolerance change. This is
        // confirmed by resolve()'s signature (no tolerance argument) and re-verified here with an
        // odd-viewport case that previously exercised the same rounding path pre-Block-5E.
        val dims = WackelbildDimensionResolver.resolve(
            viewportW = 1081, viewportH = 1921,
            captureOriginalDims = Pair(1297, 2305),
            referenceOriginalDims = Pair(1297, 2305),
            overlayScale = 1f,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )
        assertEquals(0, dims.width % 2)
        assertEquals(0, dims.height % 2)
    }

    // ── makeEven() ────────────────────────────────────────────────────────────

    @Test
    fun makeEven_oddValue_roundsDownToEven() {
        assertEquals(100, WackelbildDimensionResolver.makeEven(101))
    }

    @Test
    fun makeEven_evenValue_unchanged() {
        assertEquals(100, WackelbildDimensionResolver.makeEven(100))
    }
}
