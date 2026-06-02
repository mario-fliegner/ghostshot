package com.isardomains.sameview.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders Before & After frames onto a canvas.
 *
 * Fit semantics: both bitmaps are scaled to fit entirely within the canvas while preserving
 * their aspect ratio (letterbox/pillarbox). Remaining areas are filled with the background
 * color (#17202F). Reference is shown first, capture second.
 *
 * Timing:
 *   - Hold reference (fully opaque) for roughly the first half of the animation.
 *   - Linear crossfade over exactly (frameRate / 2) frames = 0.5 s at 30 FPS.
 *   - Hold capture (fully opaque) for the remainder.
 */
class BeforeAfterRenderEngine(
    private val config: VideoRenderConfig,
    private val referenceBitmap: Bitmap,
    private val captureBitmap: Bitmap
) : VideoFrameRenderer {

    val animationFrameCount: Int = config.animationFrameCount

    override fun renderFrame(frameIndex: Int, canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawColor(VIDEO_BACKGROUND_COLOR)

        val (refAlpha, capAlpha) = alphaAt(frameIndex, config)

        if (refAlpha > 0f) drawBitmapFit(canvas, referenceBitmap, w, h, refAlpha)
        if (capAlpha > 0f) drawBitmapFit(canvas, captureBitmap, w, h, capAlpha)
    }

    internal fun alphaAt(frameIndex: Int, config: VideoRenderConfig): Pair<Float, Float> =
        Companion.alphaAt(frameIndex, config)

    companion object {
        fun animationFrameCount(config: VideoRenderConfig): Int = config.animationFrameCount

        internal fun alphaAt(frameIndex: Int, config: VideoRenderConfig): Pair<Float, Float> {
            val n = config.animationFrameCount
            val crossfade = config.frameRate / 2
            val holdEachSide = (n - crossfade) / 2

            return when {
                frameIndex < holdEachSide -> Pair(1f, 0f)
                frameIndex < holdEachSide + crossfade -> {
                    val t = (frameIndex - holdEachSide).toFloat() / crossfade
                    Pair(1f - t, t)
                }
                else -> Pair(0f, 1f)
            }
        }
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, canvasW: Float, canvasH: Float, alpha: Float) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = minOf(canvasW / bw, canvasH / bh)
        val sw = bw * scale
        val sh = bh * scale
        val left = (canvasW - sw) / 2f
        val top = (canvasH - sh) / 2f
        val paint = Paint().apply {
            this.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + sw, top + sh), paint)
    }
}
