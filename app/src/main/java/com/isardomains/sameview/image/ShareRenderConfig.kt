// path: app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt
package com.isardomains.sameview.image

import java.io.File

/**
 * All parameters required by [ShareImageRenderer] to produce the exported JPEG.
 *
 * @param style Visual composition style.
 * @param quality Output resolution tier.
 * @param captionData Pre-computed caption lines; null = no caption area rendered.
 * @param sessionDir Directory containing reference.jpg, capture.jpg, metadata.json.
 * @param exportTimestamp Wall-clock time at export start, formatted as yyyyMMdd_HHmmss.
 *   Used in the output filename; never the session capture timestamp.
 */
data class ShareRenderConfig(
    val style: ShareComparisonStyle,
    val quality: ShareQuality,
    val captionData: ShareCaptionData?,
    val sessionDir: File,
    val exportTimestamp: String,
    /**
     * When true and the session contains a valid branding-handle.png, the Slider export
     * renders a branding handle instead of the standard SameView handle.
     *
     * No effect on Side by side style (which has no handle).
     * Defaults to false; not persisted between export sessions.
     */
    val useBranding: Boolean = false
)

/** Resolved canvas and comparison-area dimensions, pre-computed before bitmap allocation. */
internal data class CanvasDimensions(
    /** Full canvas width including outer padding. Even number. */
    val canvasW: Int,
    /** Full canvas height including outer padding and caption area. Even number. */
    val canvasH: Int,
    /** Comparison area width (without outer padding). Even number. */
    val compW: Int,
    /** Comparison area height (without outer padding). Even number. */
    val compH: Int,
    /** Outer padding on all sides, in pixels. */
    val outerPad: Int
) {
    /** Left edge of the comparison area within the canvas. */
    val compLeft get() = outerPad.toFloat()
    /** Top edge of the comparison area within the canvas. */
    val compTop get() = outerPad.toFloat()
    /** Right edge of the comparison area. */
    val compRight get() = (outerPad + compW).toFloat()
    /** Bottom edge of the comparison area. */
    val compBottom get() = (outerPad + compH).toFloat()
}

private fun makeEven(value: Int): Int = if (value % 2 == 0) value else (value - 1).coerceAtLeast(2)

internal const val MAX_COMPARISON_LONGEST_EDGE = 2048
private const val OUTER_PADDING_FRACTION = 0.04f
private const val CAPTION_GAP_FRACTION = 0.04f
// Caption text sizes as fractions of min(compW, compH)
private const val DATE_SIZE_FRACTION = 0.045f
private const val TITLE_SIZE_FRACTION = 0.035f
private const val LOCATION_SIZE_FRACTION = 0.035f
private const val LINE_SPACING = 1.20f

/**
 * Computes canvas and comparison-area dimensions from the session viewport and render config.
 *
 * @param viewportW Session viewport width (from metadata.json or capture.jpg fallback).
 * @param viewportH Session viewport height.
 * @param quality Output resolution tier.
 * @param captionData Caption lines; used to estimate caption area height.
 * @param style Export style. Side by side sets [CanvasDimensions.compH] to half the Slider value
 *   because each image occupies only half the comparison width; with Fit semantics the natural
 *   visible height is `(compW / 2) / ratio = compH / 2`. Without this adjustment the comparison
 *   area would be twice as tall as the visible image content, producing large empty dark zones.
 */
internal fun computeCanvasDimensions(
    viewportW: Int,
    viewportH: Int,
    quality: ShareQuality,
    captionData: ShareCaptionData?,
    style: ShareComparisonStyle = ShareComparisonStyle.SLIDER
): CanvasDimensions {
    require(viewportW > 0 && viewportH > 0) { "Viewport dimensions must be positive" }

    // 1. Scale comparison area to quality tier (full viewport ratio, style-agnostic).
    val (compW, compHBase) = when (quality) {
        ShareQuality.STANDARD -> {
            val longest = maxOf(viewportW, viewportH)
            if (longest <= MAX_COMPARISON_LONGEST_EDGE) {
                Pair(makeEven(viewportW), makeEven(viewportH))
            } else {
                val scale = MAX_COMPARISON_LONGEST_EDGE.toFloat() / longest
                Pair(makeEven((viewportW * scale).toInt()), makeEven((viewportH * scale).toInt()))
            }
        }
        ShareQuality.ORIGINAL -> Pair(makeEven(viewportW), makeEven(viewportH))
    }

    // 2. Apply style-specific comparison height.
    //    Side by side: each image occupies halfW = compW / 2. With ContentScale.Fit the
    //    natural visible height is halfW / ratio = compHBase / 2. Using the full compHBase
    //    would produce 50% empty dark space above and below every image.
    val compH = when (style) {
        ShareComparisonStyle.SLIDER -> compHBase
        ShareComparisonStyle.SIDE_BY_SIDE -> makeEven(compHBase / 2)
    }

    // 3. Outer padding proportional to shortest comparison dimension.
    val outerPad = (minOf(compW, compH) * OUTER_PADDING_FRACTION).toInt().coerceAtLeast(4)

    // 4. Caption area height (precise, line-count-dependent).
    val captionH = preciseCaptionHeight(captionData, compW, compH)
    // captionGap equals outerPad so the spacing above the caption matches the uniform canvas margin.
    // Using compH × CAPTION_GAP_FRACTION would make the gap scale with the image height, producing
    // a 2× larger gap for portrait Slider exports than for Side-by-side (where compH is halved).
    val captionGap = if (captionH > 0) outerPad else 0

    // 5. Full canvas.
    val canvasW = makeEven(compW + 2 * outerPad)
    val canvasH = makeEven(compH + 2 * outerPad + captionGap + captionH)

    return CanvasDimensions(canvasW, canvasH, compW, compH, outerPad)
}

/**
 * Computes precise caption area height in pixels, matching the [CaptionRenderer] rendering logic.
 *
 * Mirrors the bottom-up rendering sequence of [CaptionRenderer.render]: starts from the bottom
 * line and adds per-pair line spacing going upward. This ensures the canvas is sized exactly for
 * the visible lines — 1 active line allocates space for 1 line, not for 3.
 *
 * A +10 % margin is added for descenders/ascenders and anti-aliasing.
 */
internal fun preciseCaptionHeight(captionData: ShareCaptionData?, compW: Int, compH: Int): Int {
    if (captionData == null || !captionData.hasContent) return 0
    val baseDim = minOf(compW, compH).toFloat()

    // Build line sizes in display order (title → date → location), matching CaptionRenderer.
    val lineSizes = buildList<Float> {
        if (!captionData.titleLine.isNullOrBlank()) add(baseDim * TITLE_SIZE_FRACTION)
        if (!captionData.dateLine.isNullOrBlank()) add(baseDim * DATE_SIZE_FRACTION)
        if (!captionData.locationLine.isNullOrBlank()) add(baseDim * LOCATION_SIZE_FRACTION)
    }
    if (lineSizes.isEmpty()) return 0

    // Start from the bottom line; add baseline-to-baseline spacing for each line above.
    // CaptionRenderer iterates indices.reversed(), so spacing is max(current, previous) × lineSpacing.
    var h = lineSizes.last()
    for (i in lineSizes.size - 2 downTo 0) {
        h += maxOf(lineSizes[i + 1], lineSizes[i]) * LINE_SPACING
    }
    return (h * 1.1f).toInt().coerceAtLeast(1)
}

/** Builds the MediaStore DISPLAY_NAME from the export timestamp and style. */
internal fun buildDisplayName(timestamp: String, style: ShareComparisonStyle): String {
    val styleStr = when (style) {
        ShareComparisonStyle.SLIDER -> "slider"
        ShareComparisonStyle.SIDE_BY_SIDE -> "sidebyside"
    }
    return "SameView_${timestamp}_$styleStr.jpg"
}
