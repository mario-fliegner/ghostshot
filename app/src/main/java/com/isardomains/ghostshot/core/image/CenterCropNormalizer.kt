// path: app/src/main/java/com/isardomains/ghostshot/core/image/CenterCropNormalizer.kt
package com.isardomains.ghostshot.core.image

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Normalizes bitmaps by center-cropping to a target aspect ratio and scaling to a fixed size.
 *
 * No Android context, ContentResolver, or UI dependency. Input bitmaps are never recycled.
 */
object CenterCropNormalizer {

    /**
     * Center-crops [source] to [targetAspectRatio] (width / height).
     *
     * If the source already matches the target ratio the full source dimensions are used.
     * The input bitmap is not recycled.
     *
     * @throws IllegalArgumentException if [targetAspectRatio] is <= 0.
     */
    fun centerCrop(source: Bitmap, targetAspectRatio: Float): Bitmap {
        require(targetAspectRatio > 0f) {
            "targetAspectRatio must be > 0, was $targetAspectRatio"
        }

        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        if (kotlin.math.abs(sourceRatio - targetAspectRatio) < 0.0001f) return source

        val cropWidth: Int
        val cropHeight: Int

        if (sourceRatio > targetAspectRatio) {
            // Source is wider than target: keep height, reduce width.
            cropHeight = source.height
            cropWidth = (source.height * targetAspectRatio).roundToInt()
                .coerceAtMost(source.width)
        } else {
            // Source is taller than target (or equal): keep width, reduce height.
            cropWidth = source.width
            cropHeight = (source.width / targetAspectRatio).roundToInt()
                .coerceAtMost(source.height)
        }

        val x = (source.width - cropWidth) / 2
        val y = (source.height - cropHeight) / 2

        return Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
    }

    /**
     * Scales [source] to exactly [targetWidth] × [targetHeight] pixels.
     *
     * The input bitmap is not recycled.
     *
     * @throws IllegalArgumentException if [targetWidth] or [targetHeight] is <= 0.
     */
    fun scaleTo(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        require(targetWidth > 0) { "targetWidth must be > 0, was $targetWidth" }
        require(targetHeight > 0) { "targetHeight must be > 0, was $targetHeight" }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
