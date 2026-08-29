// path: app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt
package com.isardomains.sameview.image.wackelbild

import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/** Target pixel dimensions shared by both output images of a Wackelbild print pair. */
data class WackelbildTargetDimensions(val width: Int, val height: Int)

/**
 * Thrown by [WackelbildDimensionResolver.resolve] when the computed common scale would produce
 * a degenerate (unusably tiny) output. Callers must treat this as an HQ-reconstruction failure
 * and route to the frozen-pair fallback (`WackelbildPrintRenderer` §9.5) — never as a target to
 * upscale toward.
 */
class WackelbildHqUnusableException(message: String) : Exception(message)

/**
 * Pure, file-I/O-free resolution of the common HQ output resolution for a Wackelbild print pair,
 * and of the frozen-pair fallback's dimension/ratio-compatibility rules.
 *
 * Reference/Capture genuine-source scale derivation and the fallback ratio-compatibility check
 * are both proven/justified in `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
 * §10.2 (Correction I) and §9.5 (Correction J / Block 5B/C Correction B).
 */
object WackelbildDimensionResolver {

    private const val MIN_OUTPUT_SIDE_PX = 64f
    private const val DEFAULT_MAX_SIDE_PX = 16_000
    private const val DEFAULT_MAX_MEGAPIXELS = 80_000_000L

    /**
     * Relative ratio-error check shared by the Capture HQ guard and the fallback
     * ratio-compatibility check — same named-tolerance concept, two different callers.
     *
     * `relativeError = abs(actualRatio - expectedRatio) / expectedRatio`
     */
    fun isRatioWithinTolerance(
        actualW: Int,
        actualH: Int,
        expectedW: Int,
        expectedH: Int,
        tolerance: Float
    ): Boolean {
        val actualRatio = actualW.toFloat() / actualH
        val expectedRatio = expectedW.toFloat() / expectedH
        val relativeError = abs(actualRatio - expectedRatio) / expectedRatio
        return relativeError <= tolerance
    }

    /**
     * The only proven-legitimate source-ratio tolerance (Block 5E): at most one pixel of integer
     * truncation/rounding in [expectedW]/[expectedH]'s own upstream derivation (e.g.
     * `CameraScreen.kt`'s `size.width * 16 / 9` / `(w * 9f/16f).toInt()` viewport formulas), never
     * an arbitrary flat percentage. A flat `0.02f` (2%) previously used here wrongly accepted
     * genuinely different source aspect ratios (e.g. `1920×1088` vs a `1920×1080` viewport, a real
     * ~0.735% ratio difference — no repository or Android-API evidence supports treating such a
     * difference as invisible codec/stream padding for a still JPEG) that would then be
     * non-uniformly stretched by `ImageDecoder.setTargetSize`/`Bitmap.createScaledBitmap`. This
     * tolerance self-scales with the expected dimensions — tighter for large viewports, looser for
     * small ones — exactly matching how the underlying rounding noise actually behaves.
     */
    fun roundingToleranceFor(expectedW: Int, expectedH: Int): Float {
        require(expectedW > 0 && expectedH > 0) { "expected dimensions must be positive" }
        return 1f / min(expectedW, expectedH)
    }

    /**
     * Resolves the common HQ target dimensions for one Wackelbild print pair.
     *
     * `commonScale = min(captureScale, referenceScale, apiSideScale, apiMpScale)` — no
     * `.coerceAtLeast(1f)` anywhere: a source may legitimately determine an output smaller than
     * the session viewport, and API limits are upper bounds only, never targets.
     *
     * Callers must only invoke this once the Capture ratio-tolerance guard has already passed
     * for this operation ([isRatioWithinTolerance] against `captureOriginalDims`) — this function
     * does not itself re-check that guard.
     *
     * @throws WackelbildHqUnusableException if the resolved dimensions would be degenerate.
     */
    fun resolve(
        viewportW: Int,
        viewportH: Int,
        captureOriginalDims: Pair<Int, Int>,
        referenceOriginalDims: Pair<Int, Int>,
        overlayScale: Float,
        displayMode: ReferenceImageDisplayMode,
        maxSidePx: Int = DEFAULT_MAX_SIDE_PX,
        maxMegapixels: Long = DEFAULT_MAX_MEGAPIXELS
    ): WackelbildTargetDimensions {
        require(viewportW > 0 && viewportH > 0) { "viewport dimensions must be positive" }
        require(captureOriginalDims.first > 0 && captureOriginalDims.second > 0) {
            "captureOriginalDims must be positive"
        }
        require(referenceOriginalDims.first > 0 && referenceOriginalDims.second > 0) {
            "referenceOriginalDims must be positive"
        }

        // Capture side: this call is only ever reached once the Capture ratio-tolerance guard has
        // already confirmed captureOriginalDims' ratio matches the viewport ratio within tolerance
        // for THIS session — so this is a plain "how much bigger is the real capture" factor.
        val captureScale = min(
            captureOriginalDims.first.toFloat() / viewportW,
            captureOriginalDims.second.toFloat() / viewportH
        )

        // Reference side: derived from ReferenceRenderer's OWN compositing math, not from
        // reference-original.jpg's raw pixel dimensions alone. Scale and translate are applied as
        // independent matrix operations in ReferenceRenderer.render() (scale first, then a
        // translate that never modifies the scale factor) — so effectiveScale alone determines
        // source-pixel-to-output-pixel sampling density for every offset value and both display
        // modes; offset only ever changes which source region is visible, never the density.
        val (refW, refH) = referenceOriginalDims
        val fillOrFitScale = when (displayMode) {
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW ->
                maxOf(viewportW.toFloat() / refW, viewportH.toFloat() / refH)
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE ->
                min(viewportW.toFloat() / refW, viewportH.toFloat() / refH)
        }
        val effectiveScale = fillOrFitScale * overlayScale
        val referenceScale = 1f / effectiveScale

        val apiSideScale = maxSidePx.toFloat() / maxOf(viewportW, viewportH)
        val apiMpScale = sqrt(maxMegapixels.toDouble() / (viewportW.toLong() * viewportH.toLong())).toFloat()

        val commonScale = minOf(captureScale, referenceScale, apiSideScale, apiMpScale)

        val targetW = viewportW * commonScale
        val targetH = viewportH * commonScale
        if (targetW < MIN_OUTPUT_SIDE_PX || targetH < MIN_OUTPUT_SIDE_PX) {
            throw WackelbildHqUnusableException(
                "Resolved Wackelbild output dimensions too small: ${targetW}x$targetH (commonScale=$commonScale)"
            )
        }

        return WackelbildTargetDimensions(makeEven(round(targetW).toInt()), makeEven(round(targetH).toInt()))
    }

    /** Shrinks both dimensions by [factor] together, preserving the even-dimension invariant. */
    fun stepDownDimensions(dims: WackelbildTargetDimensions, factor: Float = 0.85f): WackelbildTargetDimensions =
        WackelbildTargetDimensions(
            makeEven(round(dims.width * factor).toInt()),
            makeEven(round(dims.height * factor).toInt())
        )

    /**
     * Resolves the frozen-pair fallback's shared target dimensions (§9.5 Correction J,
     * Block 5B/C Correction B — Cases A/B/C).
     *
     * - Case A (identical dimensions): returned as-is.
     * - Case B (different dimensions, compatible ratio within [roundingToleranceFor] of
     *   `referenceDims` — `reference.jpg` is always the exact integer viewport, so it plays the
     *   same "expected" role here that the viewport plays in the Capture HQ guard): the weaker
     *   (smaller-pixel-count) source's own dimensions become the shared, no-upscale target for
     *   both sides — never crop, never stretch, never letterbox.
     * - Case C (incompatible ratio): returns `null`. Callers must treat `null` as a hard failure
     *   (`PERMANENT_NO_VALID_SOURCE`), never as an invitation to guess a crop/stretch/letterbox
     *   transform.
     */
    fun resolveFallbackDimensions(referenceDims: Pair<Int, Int>, captureDims: Pair<Int, Int>): WackelbildTargetDimensions? {
        if (referenceDims == captureDims) {
            return WackelbildTargetDimensions(referenceDims.first, referenceDims.second) // Case A
        }
        val compatible = isRatioWithinTolerance(
            actualW = captureDims.first, actualH = captureDims.second,
            expectedW = referenceDims.first, expectedH = referenceDims.second,
            tolerance = roundingToleranceFor(referenceDims.first, referenceDims.second)
        )
        if (!compatible) return null // Case C

        val refPixels = referenceDims.first.toLong() * referenceDims.second
        val capPixels = captureDims.first.toLong() * captureDims.second
        val weaker = if (refPixels <= capPixels) referenceDims else captureDims
        return WackelbildTargetDimensions(weaker.first, weaker.second) // Case B
    }

    /** Duplicated trivially from `ShareRenderConfig`'s private `makeEven` — a single, self-contained
     * one-liner where duplication carries no realistic drift risk (unlike the HQ decode functions
     * reused via visibility widening, §9.2). */
    fun makeEven(value: Int): Int = if (value % 2 == 0) value else (value - 1).coerceAtLeast(2)
}
