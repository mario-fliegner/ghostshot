// path: app/src/main/java/com/isardomains/sameview/video/BrandingEndcardRenderer.kt
package com.isardomains.sameview.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.isardomains.sameview.R

/**
 * Renders the branding endcard into a [Canvas].
 *
 * Layout (top to bottom, centered):
 *   [SameView logo]
 *   "Made with ❤️"   (smaller)
 *   "#MadeWithSameView"  (visually dominant)
 *
 * Background: #0D1424. Text: white. The ❤️ emoji renders red from the system emoji font.
 *
 * Animation: 200 ms fade-in, 1100 ms static, 200 ms fade-out (45 frames total at 30 FPS).
 *
 * Lifecycle: call [release] after all frames have been rendered to free the pre-scaled logo bitmap.
 *
 * @param context Used to decode the app icon from mipmap resources.
 * @param canvasWidth  Canvas width in pixels (must match the video frame bitmap).
 * @param canvasHeight Canvas height in pixels (must match the video frame bitmap).
 */
class BrandingEndcardRenderer(
    private val context: Context,
    private val canvasWidth: Int,
    private val canvasHeight: Int
) {

    private companion object {
        const val ENDCARD_BACKGROUND_COLOR: Int = 0xFF0D1424.toInt()
        val FADE_IN_FRAMES = VideoRenderConfig.BRANDING_FADE_IN_FRAMES
        val STATIC_FRAMES = VideoRenderConfig.BRANDING_STATIC_FRAMES
        val FADE_OUT_FRAMES = VideoRenderConfig.BRANDING_FADE_OUT_FRAMES
    }

    private val baseSize = minOf(canvasWidth, canvasHeight).toFloat()
    private val cx = canvasWidth / 2f

    /** Pre-scaled logo loaded lazily on first renderFrame call. */
    private var logoBitmap: Bitmap? = null

    private val logoSizePx = (baseSize * 0.22f).toInt().coerceAtLeast(1)
    private val logoToTextGap = baseSize * 0.05f
    private val textGap = baseSize * 0.030f

    // "#MadeWithSameView" — visually dominant
    private val hashtagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = baseSize * 0.065f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // "Made with ❤️" — smaller supporting line
    private val madePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = baseSize * 0.033f
        textAlign = Paint.Align.CENTER
    }

    // Cached layout metrics (computed once on first render)
    private var layoutCached = false
    private var logoTopY = 0f
    private var madeBaseline = 0f
    private var hashtagBaseline = 0f

    /**
     * Renders one endcard frame into [canvas].
     *
     * @param endcardFrameIndex 0-based index within the endcard segment
     *                          (0 .. [VideoRenderConfig.BRANDING_FRAME_COUNT] − 1).
     * @param canvas            The canvas backed by the shared frame bitmap; fully overwritten here.
     */
    fun renderFrame(endcardFrameIndex: Int, canvas: Canvas) {
        val alpha = computeAlpha(endcardFrameIndex)
        val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)

        canvas.drawColor(ENDCARD_BACKGROUND_COLOR)

        if (!layoutCached) computeLayout()

        val logo = getLogoBitmap()

        val logoPaint = Paint().apply { this.alpha = alphaInt }
        canvas.drawBitmap(logo, cx - logoSizePx / 2f, logoTopY, logoPaint)

        madePaint.alpha = alphaInt
        canvas.drawText("Made with ❤️", cx, madeBaseline, madePaint)

        hashtagPaint.alpha = alphaInt
        canvas.drawText("#MadeWithSameView", cx, hashtagBaseline, hashtagPaint)
    }

    /** Releases the pre-scaled logo bitmap. Must be called after rendering completes. */
    fun release() {
        logoBitmap?.let { if (!it.isRecycled) it.recycle() }
        logoBitmap = null
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun computeAlpha(endcardFrameIndex: Int): Float = when {
        endcardFrameIndex < FADE_IN_FRAMES ->
            endcardFrameIndex.toFloat() / (FADE_IN_FRAMES - 1)
        endcardFrameIndex >= FADE_IN_FRAMES + STATIC_FRAMES -> {
            val fadeOutIndex = endcardFrameIndex - FADE_IN_FRAMES - STATIC_FRAMES
            1.0f - fadeOutIndex.toFloat() / (FADE_OUT_FRAMES - 1)
        }
        else -> 1.0f
    }

    private fun computeLayout() {
        val hashtagFm = hashtagPaint.fontMetrics
        val hashtagLineH = -hashtagFm.ascent + hashtagFm.descent

        val madeFm = madePaint.fontMetrics
        val madeLineH = -madeFm.ascent + madeFm.descent

        val totalContentH = logoSizePx + logoToTextGap + madeLineH + textGap + hashtagLineH
        val startY = (canvasHeight - totalContentH) / 2f

        logoTopY = startY

        val madeTopY = startY + logoSizePx + logoToTextGap
        madeBaseline = madeTopY + (-madeFm.ascent)

        val hashtagTopY = madeTopY + madeLineH + textGap
        hashtagBaseline = hashtagTopY + (-hashtagFm.ascent)

        layoutCached = true
    }

    private fun getLogoBitmap(): Bitmap {
        logoBitmap?.let { return it }
        val source = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
        val scaled = if (source.width != logoSizePx || source.height != logoSizePx) {
            val s = Bitmap.createScaledBitmap(source, logoSizePx, logoSizePx, true)
            if (s !== source) source.recycle()
            s
        } else {
            source
        }
        logoBitmap = scaled
        return scaled
    }
}
