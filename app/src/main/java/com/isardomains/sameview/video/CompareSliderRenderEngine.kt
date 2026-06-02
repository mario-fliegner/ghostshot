package com.isardomains.sameview.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders Compare Slider frames onto a canvas.
 *
 * Fill semantics: both bitmaps are scaled to cover the full canvas (center-crop fill),
 * independently of each other. The canvas is split at the slider position — capture fills
 * the left portion [0, sliderX), reference fills the right portion [sliderX, width].
 * A two-stroke divider (3 px dark + 1 px white core) is drawn when the slider is between
 * 0 and 1. No handle is rendered.
 *
 * Animation timing (% of animationFrameCount):
 *   0–12 %   hold at 0 (left edge)
 *   12–44 %  move right 0 → 100 % (cubic ease-in-out)
 *   44–56 %  hold at 100 % (right edge)
 *   56–88 %  move left 100 → 0 % (cubic ease-in-out)
 *   88–100 % hold at 0 (left edge)
 */
class CompareSliderRenderEngine(
    private val config: VideoRenderConfig,
    private val referenceBitmap: Bitmap,
    private val captureBitmap: Bitmap
) : VideoFrameRenderer {

    val animationFrameCount: Int = config.animationFrameCount

    override fun renderFrame(frameIndex: Int, canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawColor(VIDEO_BACKGROUND_COLOR)

        val sliderPos = sliderPositionAt(frameIndex, config)
        val sliderX = sliderPos * w

        canvas.save()
        canvas.clipRect(sliderX, 0f, w, h)
        drawBitmapFill(canvas, referenceBitmap, w, h)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0f, 0f, sliderX, h)
        drawBitmapFill(canvas, captureBitmap, w, h)
        canvas.restore()

        if (sliderPos > 0f && sliderPos < 1f) {
            drawDivider(canvas, sliderX, h)
        }
    }

    internal fun sliderPositionAt(frameIndex: Int, config: VideoRenderConfig): Float =
        Companion.sliderPositionAt(frameIndex, config)

    companion object {
        fun animationFrameCount(config: VideoRenderConfig): Int = config.animationFrameCount

        internal fun sliderPositionAt(frameIndex: Int, config: VideoRenderConfig): Float {
            val n = config.animationFrameCount
            if (n <= 0) return 0f
            val t = frameIndex.toFloat() / n
            return when {
                t < 0.12f -> 0f
                t < 0.44f -> cubicEaseInOut((t - 0.12f) / 0.32f)
                t < 0.56f -> 1f
                t < 0.88f -> 1f - cubicEaseInOut((t - 0.56f) / 0.32f)
                else -> 0f
            }
        }

        private fun cubicEaseInOut(t: Float): Float {
            val tc = t.coerceIn(0f, 1f)
            return 3f * tc * tc - 2f * tc * tc * tc
        }
    }

    private fun drawBitmapFill(canvas: Canvas, bitmap: Bitmap, canvasW: Float, canvasH: Float) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = maxOf(canvasW / bw, canvasH / bh)
        val sw = bw * scale
        val sh = bh * scale
        val left = (canvasW - sw) / 2f
        val top = (canvasH - sh) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + sw, top + sh), null)
    }

    private fun drawDivider(canvas: Canvas, x: Float, h: Float) {
        // Two-stroke approach: shadow layer is forbidden (§16.2).
        val darkPaint = Paint().apply {
            color = 0x8C000000.toInt() // rgba(0,0,0,0.55)
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = false
        }
        canvas.drawLine(x, 0f, x, h, darkPaint)

        val whitePaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = false
        }
        canvas.drawLine(x, 0f, x, h, whitePaint)
    }
}
