package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Composites a reference image over a camera photo, replicating the exact visual
 * representation shown on-screen by the Compose overlay layer.
 *
 * The rendering model mirrors how Compose draws the overlay:
 *  1. The reference image is fitted into the camera frame using ContentScale.Fit
 *     (uniform scale, centred, no cropping).
 *  2. The fitted image is scaled by [CameraUiState.overlayScale] around the frame centre,
 *     matching graphicsLayer's default pivot of (0.5, 0.5).
 *  3. It is then translated by [CameraUiState.overlayOffsetX] * frameWidth and
 *     [CameraUiState.overlayOffsetY] * frameHeight, matching graphicsLayer translationX/Y.
 *  4. [CameraUiState.overlayAlpha] is applied via [Paint.alpha].
 */
object ImageCompositor {

    /**
     * Returns a new [Bitmap] with [referenceBitmap] composited over [cameraBitmap].
     *
     * @param cameraBitmap Rotation-corrected camera photo. Defines the canvas size and
     *   acts as the base layer.
     * @param referenceBitmap Decoded reference image. Must not be recycled.
     * @param state Current overlay parameters used to position and draw the reference image.
     */
    fun composite(
        cameraBitmap: Bitmap,
        referenceBitmap: Bitmap,
        state: CameraUiState,
    ): Bitmap {
        val fw = cameraBitmap.width.toFloat()
        val fh = cameraBitmap.height.toFloat()

        val result = Bitmap.createBitmap(cameraBitmap.width, cameraBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Base layer: camera photo
        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)

        // Step 1: ContentScale.Fit — uniform scale so the reference image fits inside the frame
        val fitScale = minOf(fw / referenceBitmap.width, fh / referenceBitmap.height)
        val fittedW = referenceBitmap.width * fitScale
        val fittedH = referenceBitmap.height * fitScale

        val matrix = Matrix()
        // Scale to ContentScale.Fit size
        matrix.postScale(fitScale, fitScale)
        // Centre within frame (ContentScale.Fit alignment = centre)
        matrix.postTranslate((fw - fittedW) / 2f, (fh - fittedH) / 2f)

        // Step 2: overlayScale around the frame centre (graphicsLayer pivot = 0.5, 0.5)
        matrix.postTranslate(-fw / 2f, -fh / 2f)
        matrix.postScale(state.overlayScale, state.overlayScale)
        matrix.postTranslate(fw / 2f, fh / 2f)

        // Step 3: normalised translation (graphicsLayer translationX/Y)
        matrix.postTranslate(state.overlayOffsetX * fw, state.overlayOffsetY * fh)

        // Step 4: alpha
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (state.overlayAlpha * 255f).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(referenceBitmap, matrix, paint)

        return result
    }
}
