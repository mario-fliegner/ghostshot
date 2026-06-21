// path: app/src/main/java/com/isardomains/sameview/image/CaptionRenderer.kt
package com.isardomains.sameview.image

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

/**
 * Renders the optional caption area below the comparison image.
 *
 * Typography and layout match SHARE_COMPARISON_IMAGE_V1.md §12:
 *   - Title: regular, 3.5 % of min(compW, compH)
 *   - Date pair: bold, 4.5 %
 *   - Location: regular, 3.5 %
 *   - Shadow via setShadowLayer() (acceptable for static Bitmap Canvas rendering)
 *   - Lines rendered top-down: title → date → location
 *
 * Title and date share one UI toggle but are always rendered as separate lines.
 * No GPS data, no hidden metadata — only visible text pixels.
 */
internal class CaptionRenderer(
    private val dims: CanvasDimensions,
    captionData: ShareCaptionData
) {
    private val baseDim = minOf(dims.compW, dims.compH).toFloat()
    private val titleSize = baseDim * 0.035f
    private val dateSize = baseDim * 0.045f
    private val locationSize = baseDim * 0.035f
    private val lineSpacing = 1.20f

    private val leftPad = dims.compLeft
    // outerPad is the bottom margin allocated in canvasH by computeCanvasDimensions.
    // Using canvasH * 0.04 would give ~2× outerPad (canvasH >> min(compW,compH)),
    // which pushes the text too high and leaves a large empty zone below.
    private val bottomPad = dims.outerPad.toFloat()
    private val maxTextWidth = dims.canvasW * 0.92f
    private val shadowRadius = (baseDim * 0.004f).coerceAtLeast(2f)

    private inner class Line(val text: String, val textSize: Float, val paint: Paint)

    private val titlePaint = makePaint(titleSize, bold = false)
    private val datePaint = makePaint(dateSize, bold = true)
    private val locationPaint = makePaint(locationSize, bold = false)

    // Lines in top-to-bottom display order (rendered bottom-up).
    private val lines: List<Line> = buildList {
        if (!captionData.titleLine.isNullOrBlank()) add(Line(captionData.titleLine, titleSize, titlePaint))
        if (!captionData.dateLine.isNullOrBlank()) add(Line(captionData.dateLine, dateSize, datePaint))
        if (!captionData.locationLine.isNullOrBlank()) add(Line(captionData.locationLine, locationSize, locationPaint))
    }

    val hasContent: Boolean get() = lines.isNotEmpty()

    private fun makePaint(textSize: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        color = Color.WHITE
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setShadowLayer(shadowRadius, 1f, 1f, Color.argb(191, 0, 0, 0)) // ~75 % opacity shadow
    }

    private fun ellipsized(text: String, paint: Paint): String {
        if (paint.measureText(text) <= maxTextWidth) return text
        return TextUtils.ellipsize(
            text,
            TextPaint(paint),
            maxTextWidth,
            TextUtils.TruncateAt.END
        ).toString()
    }

    /**
     * Renders all caption lines onto [canvas] at full opacity, top-down.
     *
     * The first line is anchored at the TOP of the caption zone so that the gap between the
     * comparison image and the first text line equals exactly [captionGap]. Bottom-up rendering
     * (the previous approach) placed the last line at [canvasH - bottomPad] and worked upward,
     * which left the gap above the first line larger than intended.
     *
     * Caption zone top = canvasH − outerPad − captionH, where captionH is recomputed inline
     * from the lines list using the same formula as preciseCaptionHeight().
     */
    fun render(canvas: Canvas) {
        if (lines.isEmpty()) return

        // Recompute captionH from the lines list to derive the caption zone top position.
        // Mirrors preciseCaptionHeight(): bottom line height + per-pair baseline spacings + 10%.
        val rawTextH = run {
            var h = lines.last().textSize
            for (i in lines.size - 2 downTo 0) {
                h += maxOf(lines[i + 1].textSize, lines[i].textSize) * lineSpacing
            }
            h
        }
        val captionH = (rawTextH * 1.1f).toInt()

        // Caption zone top: exactly outerPad + compH + captionGap from the canvas top.
        val captionAreaTop = (dims.canvasH - dims.outerPad - captionH).toFloat()

        // First line baseline = caption zone top + the font's ascent for that line.
        // fontMetrics.ascent is negative in Android (distance from baseline upward), so negate it.
        var y = captionAreaTop + (-lines.first().paint.fontMetrics.ascent)

        // Render top-down: title → date → location.
        for (i in lines.indices) {
            val line = lines[i]
            canvas.drawText(ellipsized(line.text, line.paint), leftPad, y, line.paint)
            if (i < lines.size - 1) {
                y += maxOf(line.textSize, lines[i + 1].textSize) * lineSpacing
            }
        }
    }
}
