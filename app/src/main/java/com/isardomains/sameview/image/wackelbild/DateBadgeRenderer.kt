// path: app/src/main/java/com/isardomains/sameview/image/wackelbild/DateBadgeRenderer.kt
package com.isardomains.sameview.image.wackelbild

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Draws the optional date badge into a Wackelbild print output bitmap.
 *
 * This renderer receives an already-formatted date string — it never reads `metadata.json`,
 * never formats a date itself, never touches the ViewModel, and never uses Compose dp/sp
 * (Block 4's live-preview badge in `WackelbildScreen.kt`/`DateBadgeFormatter.kt` is unrelated and
 * untouched by this class). Geometry is expressed as fixed fractions of the output image's own
 * short edge (`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §8.3 Correction H),
 * derived from Block 4's shipped dp constants against a documented 360dp baseline — never a
 * device-density mapping, so behavior is identical regardless of the physical output resolution.
 */
internal object DateBadgeRenderer {

    // Fractions of min(canvasW, canvasH), derived from Block 4's 6dp/8dp/4dp/8dp/12sp constants
    // normalized against a 360dp reference baseline (value_dp / 360).
    internal const val CORNER_RADIUS_FRACTION = 6f / 360f
    internal const val PADDING_HORIZONTAL_FRACTION = 8f / 360f
    internal const val PADDING_VERTICAL_FRACTION = 4f / 360f
    internal const val EDGE_MARGIN_FRACTION = 8f / 360f
    internal const val TEXT_SIZE_FRACTION = 12f / 360f

    private val BACKGROUND_COLOR = 0xFF17202F.toInt() // SameViewAppSurface

    /** Pure, output-relative badge geometry — computable and testable without touching Canvas. */
    internal data class BadgeGeometry(
        val cornerRadius: Float,
        val paddingHorizontal: Float,
        val paddingVertical: Float,
        val edgeMargin: Float,
        val textSize: Float
    )

    internal fun computeGeometry(canvasW: Int, canvasH: Int): BadgeGeometry {
        val shortEdge = minOf(canvasW, canvasH).toFloat()
        return BadgeGeometry(
            cornerRadius = shortEdge * CORNER_RADIUS_FRACTION,
            paddingHorizontal = shortEdge * PADDING_HORIZONTAL_FRACTION,
            paddingVertical = shortEdge * PADDING_VERTICAL_FRACTION,
            edgeMargin = shortEdge * EDGE_MARGIN_FRACTION,
            textSize = shortEdge * TEXT_SIZE_FRACTION
        )
    }

    /**
     * Bottom-right badge rect `[left, top, right, bottom]` for a badge sized to fit
     * [textWidth] × [textHeight] plus [geometry]'s padding, anchored [geometry.edgeMargin] from
     * the image's right/bottom edges. Pure — no Canvas/Paint dependency, independently testable.
     */
    internal fun computeBadgeRect(canvasW: Int, canvasH: Int, textWidth: Float, textHeight: Float, geometry: BadgeGeometry): FloatArray {
        val badgeWidth = textWidth + 2 * geometry.paddingHorizontal
        val badgeHeight = textHeight + 2 * geometry.paddingVertical
        val right = canvasW - geometry.edgeMargin
        val bottom = canvasH - geometry.edgeMargin
        val left = right - badgeWidth
        val top = bottom - badgeHeight
        return floatArrayOf(left, top, right, bottom)
    }

    /**
     * Draws [dateText] into [bitmap] as a bottom-right rounded badge: `SameViewAppSurface`
     * background, white anti-aliased text, no shadow, no border, no title/location/branding.
     * Draws nothing when [dateText] is null or blank.
     */
    fun draw(bitmap: Bitmap, dateText: String?) {
        if (dateText.isNullOrBlank()) return

        val geometry = computeGeometry(bitmap.width, bitmap.height)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = geometry.textSize
        }
        val textWidth = textPaint.measureText(dateText)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val rect = computeBadgeRect(bitmap.width, bitmap.height, textWidth, textHeight, geometry)
        val left = rect[0]; val top = rect[1]; val right = rect[2]; val bottom = rect[3]

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND_COLOR }
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(left, top, right, bottom, geometry.cornerRadius, geometry.cornerRadius, backgroundPaint)

        val textX = left + geometry.paddingHorizontal
        val textY = top + geometry.paddingVertical - fontMetrics.ascent
        canvas.drawText(dateText, textX, textY, textPaint)
    }
}
