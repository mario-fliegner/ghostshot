// path: app/src/main/java/com/isardomains/sameview/branding/BuiltinSymbolRenderer.kt
package com.isardomains.sameview.branding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Renders a [BuiltinBrandingSymbol] VectorDrawable to a metadata-clean 512×512 RGBA PNG
 * [ByteArray] via [BrandingNormalizer].
 *
 * Pipeline: VectorDrawable → [Bitmap] → [BrandingNormalizer.normalize] → PNG [ByteArray].
 *
 * The [Bitmap] intermediate carries only pixel data; the resulting PNG is therefore
 * metadata-clean by construction (no EXIF, GPS, XMP, IPTC, or MakerNotes).
 *
 * The rendered size is [BrandingNormalizer.OUTPUT_SIZE] × [BrandingNormalizer.OUTPUT_SIZE]
 * before normalization, so the [BrandingNormalizer] Fit pass is a no-op size-wise; it is
 * retained to guarantee a uniform output contract for all callers regardless of origin.
 *
 * Threading: CPU-bound; call on [kotlinx.coroutines.Dispatchers.Default].
 */
internal object BuiltinSymbolRenderer {

    /**
     * Renders [symbol] to a 512×512 RGBA PNG [ByteArray].
     *
     * @throws IllegalStateException if the VectorDrawable for [symbol] cannot be loaded.
     */
    fun render(context: Context, symbol: BuiltinBrandingSymbol): ByteArray {
        val bitmap = renderToBitmap(context, symbol)
        return try {
            BrandingNormalizer.normalize(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderToBitmap(context: Context, symbol: BuiltinBrandingSymbol): Bitmap {
        val size = BrandingNormalizer.OUTPUT_SIZE
        // createBitmap initialises pixels to 0x00000000 (fully transparent).
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = context.getDrawable(symbol.drawableRes)
            ?: throw IllegalStateException("Cannot load drawable for symbol '${symbol.id}'")
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
