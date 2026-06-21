// path: app/src/main/java/com/isardomains/sameview/image/SliderRenderStrategy.kt
package com.isardomains.sameview.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader

/**
 * Renders the Slider (50/50) comparison style onto a canvas.
 *
 * Rendering sequence (SHARE_COMPARISON_IMAGE_V1.md §10.2):
 *  1. Fill canvas with #0D1424 (canvas background)
 *  2. Fill comparison area with #17202F (empty-area fallback)
 *  3. Reference bitmap — fill semantics (covers full comparison area)
 *  4. Capture bitmap — composited over reference with gradient soft-transition zone
 *  5. 1 px white core line at sliderX
 *  6. SameView handle at divider centre (filled SameViewAccent circle + white arrows)
 *  7. Comparison border (1 px #17202F rounded rect)
 *  8. Caption (delegated to caller)
 *
 * Explicit distinction from VideoExport: the handle is included here (SHARE_COMPARISON_IMAGE_V1.md
 * FD-17); VIDEO_EXPORT_V1.md §16.1 is unaffected.
 */
internal class SliderRenderStrategy(
    private val dims: CanvasDimensions,
    private val reference: Bitmap,
    private val capture: Bitmap
) {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val corePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT_COLOR
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COMPARISON_BG_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val bgPaint = Paint().apply { color = CANVAS_BG_COLOR }
    private val compBgPaint = Paint().apply { color = COMPARISON_BG_COLOR }

    fun render(canvas: Canvas, captionData: ShareCaptionData?) {
        val cW = dims.compW.toFloat()
        val cH = dims.compH.toFloat()
        val compRect = RectF(dims.compLeft, dims.compTop, dims.compRight, dims.compBottom)
        val sliderX = dims.compLeft + cW / 2f

        // 1. Fill full canvas background.
        canvas.drawColor(CANVAS_BG_COLOR)

        // 2. Fill comparison area with fallback background.
        canvas.drawRect(compRect, compBgPaint)

        // 3. Reference — fill semantics (base layer, clips to comparison area).
        canvas.save()
        canvas.clipRect(compRect)
        drawBitmapFill(canvas, reference, compRect)
        canvas.restore()

        // 4. Capture — gradient composite over reference (always at 50 %).
        val halfWidth = (GRADIENT_HALF_WIDTH_BASE_PX * (cH / 1080f)).coerceAtLeast(GRADIENT_MIN_PX)
        val gradLeft = (sliderX - halfWidth).coerceAtLeast(dims.compLeft)
        val gradRight = (sliderX + halfWidth).coerceAtMost(dims.compRight)

        canvas.saveLayer(RectF(dims.compLeft, dims.compTop, gradRight, dims.compBottom), null)
        canvas.clipRect(compRect)
        drawBitmapFill(canvas, capture, compRect)
        maskPaint.shader = LinearGradient(
            gradLeft, 0f, gradRight, 0f,
            Color.BLACK, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(gradLeft, dims.compTop, gradRight, dims.compBottom, maskPaint)
        canvas.restore()

        // 5. 1 px white core line at exact divider position.
        canvas.drawLine(sliderX, dims.compTop, sliderX, dims.compBottom, corePaint)

        // 6. SameView handle — blue circle with white arrows.
        val handleDiam = (minOf(cW, cH) * 0.12f).coerceAtLeast(40f)
        val handleR = handleDiam / 2f
        val hcx = sliderX
        val hcy = dims.compTop + cH / 2f
        canvas.drawCircle(hcx, hcy, handleR, handleFillPaint)
        drawArrows(canvas, hcx, hcy, handleDiam)

        // 7. Comparison border — drawn last so it sits on top of image content.
        val cornerRadius = (minOf(cW, cH) * 0.015f).coerceAtLeast(4f)
        canvas.drawRoundRect(compRect, cornerRadius, cornerRadius, borderPaint)

        // 8. Caption (if active).
        if (captionData != null && captionData.hasContent) {
            CaptionRenderer(dims, captionData).render(canvas)
        }
    }

    /**
     * Draws white ◀ ▶ arrows centred at (cx, cy) scaled to [diameter].
     * Geometry mirrors the CompareScreen handle arrows (CompareScreen.kt CompareDivider).
     */
    private fun drawArrows(canvas: Canvas, cx: Float, cy: Float, diameter: Float) {
        val unit = diameter / 48f
        arrowPaint.strokeWidth = unit * 2.5f
        val off = unit * 9f
        val depth = unit * 4f
        val halfH = unit * 7f

        val leftPath = Path().apply {
            moveTo(cx - off + depth, cy - halfH)
            lineTo(cx - off - depth, cy)
            lineTo(cx - off + depth, cy + halfH)
        }
        val rightPath = Path().apply {
            moveTo(cx + off - depth, cy - halfH)
            lineTo(cx + off + depth, cy)
            lineTo(cx + off - depth, cy + halfH)
        }
        canvas.drawPath(leftPath, arrowPaint)
        canvas.drawPath(rightPath, arrowPaint)
    }

    companion object {
        private const val GRADIENT_HALF_WIDTH_BASE_PX = 12f
        private const val GRADIENT_MIN_PX = 4f
    }
}

private fun drawBitmapFill(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val rw = rect.width()
    val rh = rect.height()
    if (bw <= 0f || bh <= 0f) return
    val scale = maxOf(rw / bw, rh / bh)
    val sw = bw * scale
    val sh = bh * scale
    val left = rect.left + (rw - sw) / 2f
    val top = rect.top + (rh - sh) / 2f
    canvas.drawBitmap(bitmap, null, RectF(left, top, left + sw, top + sh), null)
}

// Colour constants — raw Int values to avoid Compose dependency in the renderer.
internal const val CANVAS_BG_COLOR: Int = 0xFF0D1424.toInt()
internal const val COMPARISON_BG_COLOR: Int = 0xFF17202F.toInt()
internal const val ACCENT_COLOR: Int = 0xFF4F8CFF.toInt()
