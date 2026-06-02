package com.isardomains.sameview.video

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException

internal class MediaStoreVideoWriter(private val contentResolver: ContentResolver) {

    data class PendingEntry(val uri: Uri, val pfd: ParcelFileDescriptor)

    fun insertPending(displayName: String): PendingEntry {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SameView")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned null for: $displayName")

        val pfd = try {
            // "rw" required: MediaMuxer writes MP4 container headers retroactively (seekable write).
            contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("openFileDescriptor returned null for: $uri")
        } catch (e: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw e
        }

        return PendingEntry(uri, pfd)
    }

    fun commit(uri: Uri) {
        contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null
        )
    }

    fun abort(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
    }
}
