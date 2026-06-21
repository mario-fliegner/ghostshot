// path: app/src/main/java/com/isardomains/sameview/image/ShareMediaStoreWriter.kt
package com.isardomains.sameview.image

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException

/**
 * Writes the rendered JPEG to MediaStore under Pictures/SameView.
 *
 * Uses the IS_PENDING lifecycle: insert with IS_PENDING = 1 → write → commit to IS_PENDING = 0.
 * On failure, the pending entry is deleted from MediaStore (best-effort).
 *
 * No GPS, EXIF, XMP, or IPTC metadata is written. Bitmap.compress() produces a JPEG with
 * only pixel data; ExifInterface is never called on the output.
 */
internal class ShareMediaStoreWriter(private val contentResolver: ContentResolver) {

    data class PendingEntry(val uri: Uri, val pfd: ParcelFileDescriptor)

    /**
     * Inserts a pending MediaStore entry and opens a FileDescriptor for writing.
     *
     * @throws IOException if the insert returns null or the FD cannot be opened.
     */
    fun insertPending(displayName: String): PendingEntry {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SameView")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned null for: $displayName")

        val pfd = try {
            contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("openFileDescriptor returned null for: $uri")
        } catch (e: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw e
        }

        return PendingEntry(uri, pfd)
    }

    /** Closes the FileDescriptor and sets IS_PENDING = 0, making the image visible in Gallery. */
    fun commit(entry: PendingEntry) {
        entry.pfd.close()
        contentResolver.update(
            entry.uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
    }

    /** Closes the FileDescriptor and deletes the pending entry from MediaStore (best-effort). */
    fun abort(entry: PendingEntry) {
        runCatching { entry.pfd.close() }
        runCatching { contentResolver.delete(entry.uri, null, null) }
    }
}
