package com.isardomains.sameview.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF

/**
 * Renders Flash mode frames onto a canvas.
 *
 * Fill semantics: both bitmaps are scaled to cover the full canvas (maxOf) — same as
 * CompareSliderRenderEngine. No alpha, no gradient, no layer compositing.
 *
 * Timing (fixed regardless of branding toggle, since animationFrameCount = selected duration):
 *   Phase 1 (0 … FLASH_HOLD_FRAMES-1):  Reference only, with optional text overlay via pipeline.
 *   Phase 2 (FLASH_HOLD_FRAMES … n-1):  Hard-cut alternation: Reference (even) / Capture (odd).
 *                                        Always begins with Reference, always ends on Capture.
 *
 * Cycle counts: 4 s → 2, 6 s → 4, 8 s → 6 (see [cycleCount]).
 */
class FlashRenderEngine(
    private val config: VideoRenderConfig,
    private val referenceBitmap: Bitmap,
    private val captureBitmap: Bitmap
) : VideoFrameRenderer {

    override fun renderFrame(frameIndex: Int, canvas: Canvas) {
        canvas.drawColor(VIDEO_BACKGROUND_COLOR)
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        if (frameIndex < FLASH_HOLD_FRAMES) {
            // Phase 1: hold Reference; pipeline overlays text if active.
            drawBitmapFill(canvas, referenceBitmap, w, h)
        } else {
            val phase2Frames = config.animationFrameCount - FLASH_HOLD_FRAMES
            val totalFlashFrames = cycleCount(config.durationMs) * 2
            if (phase2Frames > 0 && totalFlashFrames > 0) {
                val phase2FrameIndex = frameIndex - FLASH_HOLD_FRAMES
                // Integer division distributes frames evenly; last flash frame is always odd → Capture.
                val flashFrameIndex = (phase2FrameIndex * totalFlashFrames) / phase2Frames
                if (flashFrameIndex % 2 == 0) {
                    drawBitmapFill(canvas, referenceBitmap, w, h)
                } else {
                    drawBitmapFill(canvas, captureBitmap, w, h)
                }
            } else {
                drawBitmapFill(canvas, captureBitmap, w, h)
            }
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

    companion object {
        /** Hold phase: 1.5 s × 30 FPS = 45 frames (fixed). */
        const val FLASH_HOLD_FRAMES = 45

        /**
         * Number of Reference ↔ Capture cycles for the given duration preset.
         * Flash always ends on Capture (cycleCount × 2 − 1 is always odd).
         */
        fun cycleCount(durationMs: Int): Int = when (durationMs) {
            4000 -> 2
            6000 -> 4
            8000 -> 6
            else -> 4
        }

        /**
         * Whether [frameIndex] should show Capture (true) or Reference (false).
         * Pure function exposed for unit testing — does not depend on a Canvas.
         */
        internal fun showCaptureAt(frameIndex: Int, config: VideoRenderConfig): Boolean {
            if (frameIndex < FLASH_HOLD_FRAMES) return false
            val phase2Frames = config.animationFrameCount - FLASH_HOLD_FRAMES
            val totalFlashFrames = cycleCount(config.durationMs) * 2
            if (phase2Frames <= 0 || totalFlashFrames <= 0) return true
            val flashFrameIndex = ((frameIndex - FLASH_HOLD_FRAMES) * totalFlashFrames) / phase2Frames
            return flashFrameIndex % 2 == 1
        }
    }
}
