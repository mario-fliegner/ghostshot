package com.isardomains.ghostshot.ui.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.IOException

/**
 * Saves bitmaps to the MediaStore using [ContentResolver] exclusively.
 * No raw file path access, no legacy storage flags.
 *
 * The [IS_PENDING] flag (API 29+) is set during the write and cleared on success,
 * preventing the system gallery from indexing an incomplete file.
 */
object MediaStoreWriter {

    private const val FOLDER = "Pictures/GhostShot"
    private const val MIME_TYPE = "image/jpeg"
    private const val JPEG_QUALITY = 95

    /**
     * Saves [bitmap] as a JPEG into [FOLDER] via [resolver].
     *
     * Must be called from a background dispatcher — the caller is responsible for
     * dispatching to [kotlinx.coroutines.Dispatchers.IO] before invoking this function.
     *
     * @return [Result.success] with the saved [Uri], or [Result.failure] on any error.
     *   On failure the orphaned MediaStore entry is cleaned up automatically.
     */
    fun save(resolver: ContentResolver, bitmap: Bitmap): Result<Uri> {
        val filename = "GhostShot_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IOException("MediaStore insert returned null"))

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IOException("Bitmap.compress failed")
                }
            } ?: throw IOException("openOutputStream returned null for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }

            Result.success(uri)
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            Result.failure(e)
        }
    }
}
