// path: app/src/main/java/com/isardomains/sameview/image/SideBySideRenderStrategy.kt
package com.isardomains.sameview.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders the Side by side comparison style onto a canvas.
 *
 * Rendering sequence (SHARE_COMPARISON_IMAGE_V1.md §10.3):
 *  1. Fill canvas with #0D1424 (canvas background)
 *  2. Fill comparison area with #17202F (fallback for empty areas)
 *  3. Reference — Fit semantics into left half
 *  4. Capture — Fit semantics into right half
 *  5. 2 px #17202F vertical separator at horizontal midpoint
 *  6. Comparison border (1 px #17202F rounded rect)
 *  7. Caption (delegated to caller)
 *
 * No labels are drawn in the comparison area.
 */
internal class SideBySideRenderStrategy(
    private val dims: CanvasDimensions,
    private val reference: Bitmap,
    private val capture: Bitmap
) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COMPARISON_BG_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val separatorPaint = Paint().apply {
        color = COMPARISON_BG_COLOR
        style = Paint.Style.FILL
    }
    private val compBgPaint = Paint().apply { color = COMPARISON_BG_COLOR }

    fun render(canvas: Canvas, captionData: ShareCaptionData?) {
        val cW = dims.compW.toFloat()
        val cH = dims.compH.toFloat()
        val compRect = RectF(dims.compLeft, dims.compTop, dims.compRight, dims.compBottom)
        val halfW = cW / 2f
        val midX = dims.compLeft + halfW

        // 1. Fill full canvas background.
        canvas.drawColor(CANVAS_BG_COLOR)

        // 2. Fill comparison area with fallback background.
        canvas.drawRect(compRect, compBgPaint)

        // 3. Reference — Fit into left half.
        val leftRect = RectF(dims.compLeft, dims.compTop, midX, dims.compBottom)
        canvas.save()
        canvas.clipRect(leftRect)
        canvas.drawRect(leftRect, compBgPaint)
        drawBitmapFit(canvas, reference, leftRect)
        canvas.restore()

        // 4. Capture — Fit into right half.
        val rightRect = RectF(midX, dims.compTop, dims.compRight, dims.compBottom)
        canvas.save()
        canvas.clipRect(rightRect)
        canvas.drawRect(rightRect, compBgPaint)
        drawBitmapFit(canvas, capture, rightRect)
        canvas.restore()

        // 5. 2 px vertical separator at horizontal midpoint.
        canvas.drawRect(midX - 1f, dims.compTop, midX + 1f, dims.compBottom, separatorPaint)

        // 6. Comparison border.
        canvas.drawRect(compRect, borderPaint)

        // 7. Caption (if active).
        if (captionData != null && captionData.hasContent) {
            CaptionRenderer(dims, captionData).render(canvas)
        }
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val rw = rect.width()
        val rh = rect.height()
        if (bw <= 0f || bh <= 0f || rw <= 0f || rh <= 0f) return
        val scale = minOf(rw / bw, rh / bh)
        val sw = bw * scale
        val sh = bh * scale
        val left = rect.left + (rw - sw) / 2f
        val top = rect.top + (rh - sh) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + sw, top + sh), null)
    }
}
