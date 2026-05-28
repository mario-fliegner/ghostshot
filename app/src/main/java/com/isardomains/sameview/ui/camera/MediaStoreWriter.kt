package com.isardomains.sameview.ui.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.isardomains.sameview.AppConstants
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves bitmaps to the MediaStore using [ContentResolver] exclusively.
 * No raw file path access, no legacy storage flags.
 *
 * The [IS_PENDING] flag (API 29+) is always resolved in the finally block:
 * cleared on success, cleared-then-deleted on failure — so a pending entry
 * is never left behind regardless of which step fails.
 */
object MediaStoreWriter {

    private const val FOLDER = "Pictures/SameView"
    private const val FILE_PREFIX = "SameView"
    private const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSS"
    private const val MIME_TYPE = "image/jpeg"
    private const val JPEG_QUALITY = 95

    /**
     * Saves [bitmap] as a JPEG into [FOLDER] via [resolver].
     *
     * GPS is written fail-soft when [gpsSnapshot] is non-null: a GPS write failure
     * never destroys a successfully saved image.
     *
     * Must be called from a background dispatcher — the caller is responsible for
     * dispatching to [kotlinx.coroutines.Dispatchers.IO] before invoking this function.
     *
     * @return [Result.success] with the saved [Uri], or [Result.failure] on any error.
     *   On failure the orphaned MediaStore entry is cleaned up automatically.
     */
    internal fun save(
        resolver: ContentResolver,
        bitmap: Bitmap,
        gpsSnapshot: GpsSnapshot? = null
    ): Result<Uri> {
        val filename = generateDisplayName()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IOException("MediaStore insert returned null"))

        var success = false
        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IOException("Bitmap.compress failed")
                }
            } ?: throw IOException("openOutputStream returned null for $uri")

            writeSoftwareExif(resolver, uri)

            // GPS: fail-soft — a write failure never destroys the saved image
            if (gpsSnapshot != null) {
                GpsExifWriter.writeGpsToUri(resolver, uri, gpsSnapshot)
            }

            success = true
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Result.failure(e)
        } finally {
            // IS_PENDING is always cleared, whether the save succeeded or failed.
            // On failure the entry is also deleted after clearing pending.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null
                    )
                } catch (_: Exception) { }
            }
            if (!success) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            }
        }
    }

    internal fun generateDisplayName(timestampMillis: Long = System.currentTimeMillis()): String {
        val timestamp = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date(timestampMillis))
        return "${FILE_PREFIX}_$timestamp.jpg"
    }

    internal fun relativePath(): String = FOLDER

    private fun writeSoftwareExif(resolver: ContentResolver, uri: Uri) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor).apply {
                    setAttribute(ExifInterface.TAG_SOFTWARE, AppConstants.CAPTURE_EXIF_SOFTWARE)
                    saveAttributes()
                }
            }
        } catch (_: Exception) { }
    }
}
