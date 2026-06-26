// path: app/src/main/java/com/isardomains/sameview/branding/BrandingHandleRenderer.kt
package com.isardomains.sameview.branding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws the branding handle onto an Android [Canvas].
 *
 * Handle specification (SESSION_BRANDING_V1.md §8.3):
 *   - Outer ring: [RING_COLOR] (SameViewAccent blue), same arc geometry as the standard handle
 *   - Inner circle: [CIRCLE_BG_COLOR] (#F5F7FA, off-white)
 *   - Logo: [logoBitmap] centered, Fit semantics (no crop), [LOGO_SIZE_FRACTION] of circle diameter
 *
 * The branding handle diameter is 1.5× the standard handle diameter; callers are
 * responsible for computing the correct diameter before calling [draw].
 *
 * No Compose, ViewModel, or screen dependencies. Designed to be reused by
 * both Share Comparison Image and future Video Export.
 *
 * Threading: all operations are synchronous and must be called from a background
 * dispatcher (GPU/CPU drawing work).
 */
internal object BrandingHandleRenderer {

    /** Outer ring color — white, identical to the standard SameView handle ring. */
    private const val RING_COLOR: Int = 0xFFFFFFFF.toInt()

    /** Inner circle background — white, identical to the standard SameView handle circle. */
    private const val CIRCLE_BG_COLOR: Int = 0xFFFFFFFF.toInt()

    /** Logo occupies this fraction of the branding circle diameter. */
    private const val LOGO_SIZE_FRACTION = 0.72f

    /**
     * Draws the branding handle at ([cx], [cy]) with the given [diameter].
     *
     * The ring arc geometry (sweep angles, gap degrees) matches the standard SameView handle
     * so the visual language is consistent across styles.
     *
     * @param canvas Target canvas.
     * @param cx     Horizontal centre of the handle.
     * @param cy     Vertical centre of the handle.
     * @param diameter Full circle diameter in pixels.
     * @param logoBitmap Normalized 512×512 RGBA PNG; drawn centered at [LOGO_SIZE_FRACTION] scale.
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, diameter: Float, logoBitmap: Bitmap) {
        val radius = diameter / 2f

        // ── Outer ring: SameViewAccent, two arcs with 12° gaps at top/bottom ────
        val ringThickness = diameter * (2f / 48f)
        val ringGap = diameter * (1f / 48f)
        val ringR = radius + ringGap + ringThickness / 2f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RING_COLOR
            style = Paint.Style.STROKE
            strokeWidth = ringThickness
        }
        val ringOval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        canvas.drawArc(ringOval, 102f, 156f, false, ringPaint)  // left half
        canvas.drawArc(ringOval, 282f, 156f, false, ringPaint)  // right half

        // ── Inner circle: #F5F7FA background ──────────────────────────────────
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CIRCLE_BG_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // ── Logo: centered, Fit (preserve aspect ratio, no crop) ──────────────
        val logoMaxDim = diameter * LOGO_SIZE_FRACTION
        val logoW = logoBitmap.width.toFloat()
        val logoH = logoBitmap.height.toFloat()
        if (logoW > 0f && logoH > 0f) {
            val scale = minOf(logoMaxDim / logoW, logoMaxDim / logoH)
            val sw = logoW * scale
            val sh = logoH * scale
            val dst = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
            val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logoBitmap, null, dst, bitmapPaint)
        }
    }
}
