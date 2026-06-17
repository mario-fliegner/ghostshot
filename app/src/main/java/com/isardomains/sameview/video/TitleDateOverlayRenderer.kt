package com.isardomains.sameview.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

class TitleDateOverlayRenderer(
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    overlay: VideoOverlay
) {
    private val baseDim = minOf(canvasWidth, canvasHeight).toFloat()
    private val titleTextSize = baseDim * 0.035f
    private val dateTextSize = baseDim * 0.045f
    private val locationTextSize = baseDim * 0.035f
    private val lineSpacingMultiplier = 1.20f
    private val leftPaddingPx = canvasWidth * 0.04f
    private val bottomPaddingPx = canvasHeight * 0.04f
    private val shadowRadius = (baseDim * 0.004f).coerceAtLeast(2f)

    private inner class Line(val text: String, val textSize: Float, val paint: Paint)

    private val titlePaint = makePaint(titleTextSize, bold = false)
    private val datePaint = makePaint(dateTextSize, bold = true)
    private val locationPaint = makePaint(locationTextSize, bold = false)

    private val lines: List<Line> = buildList {
        if (!overlay.title.isNullOrBlank()) add(Line(overlay.title, titleTextSize, titlePaint))
        if (!overlay.dateLine.isNullOrBlank()) add(Line(overlay.dateLine, dateTextSize, datePaint))
        if (!overlay.locationLine.isNullOrBlank()) add(Line(overlay.locationLine, locationTextSize, locationPaint))
    }

    private fun makePaint(textSize: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        color = Color.WHITE
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun applyAlpha(paint: Paint, alpha: Int) {
        paint.alpha = alpha
        val shadowAlpha = (191 * alpha / 255).coerceIn(0, 255)
        paint.setShadowLayer(shadowRadius, 1f, 1f, Color.argb(shadowAlpha, 0, 0, 0))
    }

    fun renderOnCanvas(frameIndex: Int, holdFrameCount: Int, canvas: Canvas) {
        if (lines.isEmpty()) return
        if (holdFrameCount <= 0 || frameIndex >= holdFrameCount) return
        val alpha = alphaForFrame(frameIndex, holdFrameCount)
        if (alpha <= 0f) return
        val intAlpha = (alpha * 255f).toInt().coerceIn(1, 255)

        lines.forEach { applyAlpha(it.paint, intAlpha) }

        var yBase = canvasHeight.toFloat() - bottomPaddingPx
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            canvas.drawText(line.text, leftPaddingPx, yBase, line.paint)
            if (i > 0) {
                yBase -= lines[i - 1].textSize * lineSpacingMultiplier
            }
        }
    }

    companion object {
        internal fun alphaForFrame(frameIndex: Int, holdFrameCount: Int): Float {
            if (holdFrameCount <= 0 || frameIndex >= holdFrameCount) return 0f
            val fadeOutStart = (holdFrameCount * 0.80f).toInt()
            if (frameIndex < fadeOutStart) return 1f
            val fadeRange = holdFrameCount - fadeOutStart
            if (fadeRange <= 0) return 0f
            return 1f - (frameIndex - fadeOutStart).toFloat() / fadeRange
        }
    }
}
