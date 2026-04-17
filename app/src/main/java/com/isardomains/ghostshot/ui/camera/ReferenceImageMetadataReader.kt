package com.isardomains.ghostshot.ui.camera

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.InputStream

object ReferenceImageMetadataReader {

    fun read(openInputStream: () -> InputStream?): ReferenceImageMetadata? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = try {
            openInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
        if (!boundsRead || opts.outWidth <= 0 || opts.outHeight <= 0) {
            return null
        }

        val exifOrientation = try {
            openInputStream()?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            } ?: return null
        } catch (_: Exception) {
            return null
        }

        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE

        return ReferenceImageMetadata(
            rawWidth = opts.outWidth,
            rawHeight = opts.outHeight,
            orientedWidth = if (isRotated) opts.outHeight else opts.outWidth,
            orientedHeight = if (isRotated) opts.outWidth else opts.outHeight,
            exifOrientation = exifOrientation
        )
    }
}
