package com.isardomains.sameview.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader

/**
 * Renders Compare Slider frames onto a canvas (single-pass reveal).
 *
 * Fill semantics: both bitmaps are independently scaled to cover the full canvas.
 * Reference is drawn as the base layer; capture is composited on top with a gradient
 * soft-transition zone centered on the divider position, plus a 1 px white core line.
 *
 * Animation timing (% of animationFrameCount):
 *   0–15 %    hold reference (slider at 0.0, reference fully visible)
 *   15–60 %   sweep: 0.0 → 1.0 (cubic smoothstep)
 *   60–100 %  hold capture (slider at 1.0, capture fully visible)
 */
class CompareSliderRenderEngine(
    private val config: VideoRenderConfig,
    private val referenceBitmap: Bitmap,
    private val captureBitmap: Bitmap
) : VideoFrameRenderer {

    val animationFrameCount: Int = config.animationFrameCount

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private val corePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    override fun renderFrame(frameIndex: Int, canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawColor(VIDEO_BACKGROUND_COLOR)

        val sliderPos = sliderPositionAt(frameIndex, config)
        val sliderX = sliderPos * w

        // Reference is always the base layer (full canvas, fill semantics)
        drawBitmapFill(canvas, referenceBitmap, w, h)

        when {
            sliderPos <= 0f -> {
                // Hold Reference: reference only, nothing more to draw
            }
            sliderPos >= 1f -> {
                // Hold Capture: capture covers full canvas, no gradient needed
                drawBitmapFill(canvas, captureBitmap, w, h)
            }
            else -> {
                // Sweep: capture composited over reference with gradient soft edge
                val gradientHalfWidth = (GRADIENT_HALF_WIDTH_BASE_PX * (h / 1080f))
                    .coerceAtLeast(GRADIENT_MIN_PX)
                val gradientLeft = (sliderX - gradientHalfWidth).coerceAtLeast(0f)
                val gradientRight = (sliderX + gradientHalfWidth).coerceAtMost(w)

                // Save layer bounded to the capture region; DST_IN fades the right edge
                canvas.saveLayer(RectF(0f, 0f, gradientRight, h), null)
                drawBitmapFill(canvas, captureBitmap, w, h)
                maskPaint.shader = LinearGradient(
                    gradientLeft, 0f, gradientRight, 0f,
                    Color.BLACK, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(gradientLeft, 0f, gradientRight, h, maskPaint)
                canvas.restore()

                // 1 px core line at exact divider position for orientation
                canvas.drawLine(sliderX, 0f, sliderX, h, corePaint)
                // TODO VIDEO_BRANDING: Check sessionDir for branding-handle.png and render it here
                // instead of the standard arrows. See SESSION_BRANDING_V1.md §16.
            }
        }
    }

    internal fun sliderPositionAt(frameIndex: Int, config: VideoRenderConfig): Float =
        Companion.sliderPositionAt(frameIndex, config)

    companion object {
        // Single-pass timing fractions (proportion of animationFrameCount)
        private const val HOLD_REFERENCE_FRACTION = 0.15f   // 0 %–15 %: reference visible
        private const val SWEEP_END_FRACTION = 0.60f         // 15 %–60 %: sweep; 60 %+: capture hold
        // Gradient half-width in px at 1080 p base height; scales linearly with canvas height
        private const val GRADIENT_HALF_WIDTH_BASE_PX = 12f
        private const val GRADIENT_MIN_PX = 4f

        fun animationFrameCount(config: VideoRenderConfig): Int = config.animationFrameCount

        internal fun sliderPositionAt(frameIndex: Int, config: VideoRenderConfig): Float {
            val n = config.animationFrameCount
            if (n <= 0) return 0f
            val t = frameIndex.toFloat() / n
            return when {
                t < HOLD_REFERENCE_FRACTION -> 0f
                t < SWEEP_END_FRACTION ->
                    cubicEaseInOut(
                        (t - HOLD_REFERENCE_FRACTION) / (SWEEP_END_FRACTION - HOLD_REFERENCE_FRACTION)
                    )
                else -> 1f
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
}
