// path: app/src/main/java/com/isardomains/ghostshot/core/image/ComparisonCropProcessor.kt
package com.isardomains.ghostshot.core.image

import android.graphics.Bitmap
import com.isardomains.ghostshot.ui.camera.ComparisonFrame
import com.isardomains.ghostshot.ui.camera.NormalizedRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Result of a successful [ComparisonCropProcessor.process] call.
 *
 * Both bitmaps are owned by the caller and must not be recycled by the processor.
 *
 * @param captureCrop Cropped and scaled sub-region of the capture bitmap, matching
 *   [referenceCrop] in pixel dimensions exactly.
 * @param referenceCrop Cropped sub-region of the reference bitmap at its natural crop size.
 */
internal data class ComparisonCrops(
    val captureCrop: Bitmap,
    val referenceCrop: Bitmap
)

/**
 * Produces deterministic comparison crops from two already-oriented bitmaps and a [ComparisonFrame].
 *
 * [ComparisonFrame] is the sole geometric authority — no geometry is computed here.
 * Both input bitmaps must already be correctly oriented before calling [process].
 * No bitmaps are loaded, rotated, or decoded by this class.
 */
object ComparisonCropProcessor {

    /**
     * Crops [captureBitmap] by [ComparisonFrame.captureRect] and [referenceBitmap] by
     * [ComparisonFrame.referenceRect], then scales the capture crop to the exact pixel
     * dimensions of the reference crop.
     *
     * Rounding: floor for left/top edges, ceil for right/bottom edges, clamped to bitmap bounds.
     * Returns null if either computed crop has a width or height of zero or less after
     * rounding and clamping.
     *
     * Neither input bitmap is recycled. Output bitmaps are owned by the caller.
     *
     * @param captureBitmap Correctly oriented capture bitmap.
     * @param referenceBitmap Correctly oriented reference bitmap.
     * @param comparisonFrame Geometric definition of both crop regions.
     * @return [ComparisonCrops] with both result bitmaps, or null if either crop is degenerate.
     */
    internal fun process(
        captureBitmap: Bitmap,
        referenceBitmap: Bitmap,
        comparisonFrame: ComparisonFrame
    ): ComparisonCrops? {
        val referenceCrop = cropBitmap(referenceBitmap, comparisonFrame.referenceRect)
            ?: return null

        val captureCropRaw = cropBitmap(captureBitmap, comparisonFrame.captureRect)
        if (captureCropRaw == null) {
            referenceCrop.recycle()
            return null
        }

        val captureCropScaled = Bitmap.createScaledBitmap(
            captureCropRaw,
            referenceCrop.width,
            referenceCrop.height,
            true
        )
        captureCropRaw.recycle()

        return ComparisonCrops(
            captureCrop = captureCropScaled,
            referenceCrop = referenceCrop
        )
    }

    /**
     * Crops [source] to the region described by [rect].
     *
     * Uses floor for left/top and ceil for right/bottom, then clamps all coordinates
     * to the bitmap bounds. Returns null if the resulting width or height is zero or less.
     */
    private fun cropBitmap(source: Bitmap, rect: NormalizedRect): Bitmap? {
        val left   = floor(rect.left   * source.width).toInt().coerceIn(0, source.width)
        val top    = floor(rect.top    * source.height).toInt().coerceIn(0, source.height)
        val right  = ceil(rect.right   * source.width).toInt().coerceIn(0, source.width)
        val bottom = ceil(rect.bottom  * source.height).toInt().coerceIn(0, source.height)

        val width  = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null

        return Bitmap.createBitmap(source, left, top, width, height)
    }
}
