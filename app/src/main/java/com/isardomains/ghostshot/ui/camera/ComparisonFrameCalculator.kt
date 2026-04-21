// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/ComparisonFrameCalculator.kt
package com.isardomains.ghostshot.ui.camera

import kotlin.math.max
import kotlin.math.min

/**
 * All inputs required to compute a [ComparisonFrame].
 *
 * All pixel dimensions are in the same physical pixel space (no dp conversion).
 * [overlayOffsetX] and [overlayOffsetY] are normalized fractions of the viewport size,
 * matching the semantics stored in [CameraUiState].
 *
 * @param viewportWidth         Width of the camera preview viewport in pixels. Must be > 0.
 * @param viewportHeight        Height of the camera preview viewport in pixels. Must be > 0.
 * @param captureWidth          Width of the final rotated captured bitmap in pixels. Must be > 0.
 * @param captureHeight         Height of the final rotated captured bitmap in pixels. Must be > 0.
 * @param referenceOrientedWidth  Width of the reference image after EXIF orientation in pixels.
 * @param referenceOrientedHeight Height of the reference image after EXIF orientation in pixels.
 * @param overlayOffsetX        Horizontal overlay offset as a normalized fraction of viewport width.
 * @param overlayOffsetY        Vertical overlay offset as a normalized fraction of viewport height.
 * @param overlayScale          Scale factor applied to the overlay (>0).
 * @param displayMode           The active [ReferenceImageDisplayMode] at capture time.
 */
data class CalculatorInput(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val captureWidth: Int,
    val captureHeight: Int,
    val referenceOrientedWidth: Int,
    val referenceOrientedHeight: Int,
    val overlayOffsetX: Float,
    val overlayOffsetY: Float,
    val overlayScale: Float,
    val displayMode: ReferenceImageDisplayMode
)

/**
 * Pure Kotlin calculator for [ComparisonFrame].
 *
 * No Android APIs, no Compose APIs, no ViewModel dependencies.
 * All intermediate calculations use Double precision; final [NormalizedRect] fields are Float.
 *
 * Geometry mirrors [CompareReferenceImage] (COMPARE_WITH_PREVIEW) and the SHOW_FULL_IMAGE
 * rendering branch in CameraScreen exactly. Any change to rendering geometry must be
 * reflected here and verified by [ComparisonFrameCalculatorTest].
 */
object ComparisonFrameCalculator {

    /**
     * Computes a [ComparisonFrame] from [input], or returns null if:
     * - any dimension is <= 0
     * - [CalculatorInput.overlayScale] <= 0
     * - the computed intersection is empty
     * - either resulting rect is degenerate (left >= right or top >= bottom)
     */
    fun calculate(input: CalculatorInput): ComparisonFrame? {
        if (input.viewportWidth <= 0 || input.viewportHeight <= 0) return null
        if (input.captureWidth <= 0 || input.captureHeight <= 0) return null
        if (input.referenceOrientedWidth <= 0 || input.referenceOrientedHeight <= 0) return null
        if (input.overlayScale <= 0f) return null

        return when (input.displayMode) {
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> calculateCompareWithPreview(input)
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE -> calculateShowFullImage(input)
        }
    }

    // ── COMPARE_WITH_PREVIEW ──────────────────────────────────────────────────────────────────

    private fun calculateCompareWithPreview(input: CalculatorInput): ComparisonFrame? {
        val vw = input.viewportWidth.toDouble()
        val vh = input.viewportHeight.toDouble()
        val iw = input.referenceOrientedWidth.toDouble()
        val ih = input.referenceOrientedHeight.toDouble()
        val scale = input.overlayScale.toDouble()

        // Mirror CompareReferenceImage rendering: fillScale (CENTER_CROP for reference)
        val fillScale = max(vw / iw, vh / ih)
        val displayedWidth = iw * fillScale
        val displayedHeight = ih * fillScale

        val scaledWidth = displayedWidth * scale
        val scaledHeight = displayedHeight * scale

        // Render-clamp (exact mirror of CompareReferenceImage lines 598–601)
        val maxTranslationX = max(0.0, (scaledWidth - vw) / 2.0)
        val maxTranslationY = max(0.0, (scaledHeight - vh) / 2.0)
        val translationX = (input.overlayOffsetX.toDouble() * vw).coerceIn(-maxTranslationX, maxTranslationX)
        val translationY = (input.overlayOffsetY.toDouble() * vh).coerceIn(-maxTranslationY, maxTranslationY)

        // ReferenceRect: which part of the reference image is visible in the viewport?
        // The image is centered in the viewport, scaled by fillScale * overlayScale, then translated.
        // Center of rendered image in viewport coords:
        val imageCenterX = vw / 2.0 + translationX
        val imageCenterY = vh / 2.0 + translationY

        // Edges of rendered image in viewport coords:
        val imgLeft = imageCenterX - scaledWidth / 2.0
        val imgTop = imageCenterY - scaledHeight / 2.0
        val imgRight = imageCenterX + scaledWidth / 2.0
        val imgBottom = imageCenterY + scaledHeight / 2.0

        // Intersection with viewport:
        val visLeft = max(0.0, imgLeft)
        val visTop = max(0.0, imgTop)
        val visRight = min(vw, imgRight)
        val visBottom = min(vh, imgBottom)

        if (visLeft >= visRight || visTop >= visBottom) return null

        // Map viewport intersection back to reference image normalized coords:
        // A viewport pixel (px, py) maps to reference image pixel:
        //   refX = (px - imgLeft) / (scaledWidth) * iw
        //   refY = (py - imgTop)  / (scaledHeight) * ih
        val refLeft = (visLeft - imgLeft) / scaledWidth
        val refTop = (visTop - imgTop) / scaledHeight
        val refRight = (visRight - imgLeft) / scaledWidth
        val refBottom = (visBottom - imgTop) / scaledHeight

        val referenceRect = makeRect(refLeft, refTop, refRight, refBottom) ?: return null

        // CaptureRect: which part of the capture bitmap corresponds to the viewport?
        // Preview uses FILL_CENTER (CENTER_CROP) semantics.
        val captureRect = captureRectFromViewport(
            viewportLeft = 0.0,
            viewportTop = 0.0,
            viewportRight = vw,
            viewportBottom = vh,
            viewportWidth = vw,
            viewportHeight = vh,
            captureWidth = input.captureWidth.toDouble(),
            captureHeight = input.captureHeight.toDouble()
        ) ?: return null

        return ComparisonFrame(captureRect = captureRect, referenceRect = referenceRect)
    }

    // ── SHOW_FULL_IMAGE ───────────────────────────────────────────────────────────────────────

    private fun calculateShowFullImage(input: CalculatorInput): ComparisonFrame? {
        val vw = input.viewportWidth.toDouble()
        val vh = input.viewportHeight.toDouble()
        val iw = input.referenceOrientedWidth.toDouble()
        val ih = input.referenceOrientedHeight.toDouble()
        val scale = input.overlayScale.toDouble()

        // Mirror SHOW_FULL_IMAGE rendering: ContentScale.Fit (FIT, not FILL)
        val fitScale = min(vw / iw, vh / ih)
        val displayedWidth = iw * fitScale
        val displayedHeight = ih * fitScale

        val scaledWidth = displayedWidth * scale
        val scaledHeight = displayedHeight * scale

        // NO clamp — exact mirror of the else-branch in ReferenceImageOverlay
        val translationX = input.overlayOffsetX.toDouble() * vw
        val translationY = input.overlayOffsetY.toDouble() * vh

        // Center of rendered image in viewport coords:
        val imageCenterX = vw / 2.0 + translationX
        val imageCenterY = vh / 2.0 + translationY

        val imgLeft = imageCenterX - scaledWidth / 2.0
        val imgTop = imageCenterY - scaledHeight / 2.0
        val imgRight = imageCenterX + scaledWidth / 2.0
        val imgBottom = imageCenterY + scaledHeight / 2.0

        // Intersection of rendered reference image with viewport:
        val visLeft = max(0.0, imgLeft)
        val visTop = max(0.0, imgTop)
        val visRight = min(vw, imgRight)
        val visBottom = min(vh, imgBottom)

        if (visLeft >= visRight || visTop >= visBottom) return null

        // ReferenceRect: visible portion of the reference image (normalized):
        val refLeft = (visLeft - imgLeft) / scaledWidth
        val refTop = (visTop - imgTop) / scaledHeight
        val refRight = (visRight - imgLeft) / scaledWidth
        val refBottom = (visBottom - imgTop) / scaledHeight

        val referenceRect = makeRect(refLeft, refTop, refRight, refBottom) ?: return null

        // CaptureRect: the viewport region occupied by the visible reference, back-projected
        // into the final rotated capture bitmap space (via FILL_CENTER / CENTER_CROP mapping).
        val captureRect = captureRectFromViewport(
            viewportLeft = visLeft,
            viewportTop = visTop,
            viewportRight = visRight,
            viewportBottom = visBottom,
            viewportWidth = vw,
            viewportHeight = vh,
            captureWidth = input.captureWidth.toDouble(),
            captureHeight = input.captureHeight.toDouble()
        ) ?: return null

        return ComparisonFrame(captureRect = captureRect, referenceRect = referenceRect)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    /**
     * Back-projects a viewport sub-rectangle into normalized capture-bitmap space using
     * FILL_CENTER (CENTER_CROP) preview scale semantics.
     *
     * Returns null if the resulting rect is degenerate after clamping to [0, 1].
     */
    private fun captureRectFromViewport(
        viewportLeft: Double,
        viewportTop: Double,
        viewportRight: Double,
        viewportBottom: Double,
        viewportWidth: Double,
        viewportHeight: Double,
        captureWidth: Double,
        captureHeight: Double
    ): NormalizedRect? {
        // Preview CENTER_CROP scale: how the capture bitmap is scaled to fill the viewport.
        val previewScale = max(viewportWidth / captureWidth, viewportHeight / captureHeight)

        // Offset of capture bitmap origin (top-left) in viewport coords (may be negative = cropped).
        val capOriginX = (viewportWidth - captureWidth * previewScale) / 2.0
        val capOriginY = (viewportHeight - captureHeight * previewScale) / 2.0

        // Map viewport rect to capture pixel coords, then normalize:
        val capLeft = (viewportLeft - capOriginX) / previewScale / captureWidth
        val capTop = (viewportTop - capOriginY) / previewScale / captureHeight
        val capRight = (viewportRight - capOriginX) / previewScale / captureWidth
        val capBottom = (viewportBottom - capOriginY) / previewScale / captureHeight

        return makeRect(capLeft, capTop, capRight, capBottom)
    }

    /**
     * Clamps all four values to [0, 1] and validates the resulting rect.
     * Returns null if left >= right or top >= bottom after clamping.
     */
    private fun makeRect(left: Double, top: Double, right: Double, bottom: Double): NormalizedRect? {
        val l = left.coerceIn(0.0, 1.0).toFloat()
        val t = top.coerceIn(0.0, 1.0).toFloat()
        val r = right.coerceIn(0.0, 1.0).toFloat()
        val b = bottom.coerceIn(0.0, 1.0).toFloat()
        if (l >= r || t >= b) return null
        return NormalizedRect(left = l, top = t, right = r, bottom = b)
    }
}
