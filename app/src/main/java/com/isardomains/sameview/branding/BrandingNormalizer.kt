// path: app/src/main/java/com/isardomains/sameview/branding/BrandingNormalizer.kt
package com.isardomains.sameview.branding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/**
 * Normalizes any [Bitmap] to a 512×512 RGBA PNG [ByteArray].
 *
 * Output properties:
 *   - Dimensions: [OUTPUT_SIZE] × [OUTPUT_SIZE] pixels (512×512)
 *   - Config: [Bitmap.Config.ARGB_8888]
 *   - Background: transparent (alpha = 0)
 *   - Scaling: Fit — aspect ratio is preserved, source is centered, no crop
 *   - Encoding: PNG, quality 100 (lossless)
 *
 * Privacy guarantee: the output is metadata-clean by construction.
 * [Bitmap] carries only pixel data. [Bitmap.compress] with [Bitmap.CompressFormat.PNG]
 * writes no EXIF, GPS, XMP, IPTC, MakerNotes, or any other metadata segment.
 * No source URI, filename, or provenance information is written.
 *
 * Threading: CPU-bound; call on [kotlinx.coroutines.Dispatchers.Default].
 */
internal object BrandingNormalizer {

    /** Output canvas dimension in pixels. Both width and height equal this value. */
    const val OUTPUT_SIZE = 512

    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Normalizes [source] to a 512×512 transparent RGBA PNG [ByteArray].
     *
     * [source] must not be recycled before this function returns.
     * The caller retains ownership of [source]; this function does not recycle it.
     */
    fun normalize(source: Bitmap): ByteArray {
        val scale = minOf(
            OUTPUT_SIZE.toFloat() / source.width,
            OUTPUT_SIZE.toFloat() / source.height
        )
        val scaledW = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(1)

        // createBitmap initialises all pixels to 0x00000000 (fully transparent).
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val left = (OUTPUT_SIZE - scaledW) / 2f
        val top = (OUTPUT_SIZE - scaledH) / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + scaledW, top + scaledH), filterPaint)

        val out = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, 100, out)
        output.recycle()
        return out.toByteArray()
    }
}
