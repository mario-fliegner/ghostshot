package com.isardomains.sameview.ui.camera

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.database.MatrixCursor
import java.io.File
import java.io.FileNotFoundException

/**
 * Test-only ContentProvider that simulates a SAF document provider (authority ≠ "media").
 *
 * Behaviour:
 * - Serves any file whose absolute path is supplied as the "path" query parameter.
 * - Throws SecurityException if the URI contains the "require_original" query parameter,
 *   reproducing the real-device behaviour of com.android.providers.media.documents when
 *   MediaStore.setRequireOriginal() is incorrectly applied to a document URI.
 *
 * Used exclusively by [ReferenceImageMetadataReaderTest].
 */
internal class SafMimicContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        rejectRequireOriginal(uri)
        val path = uri.getQueryParameter(PARAM_PATH)
            ?: throw FileNotFoundException("Missing path parameter in URI: $uri")
        val file = File(path)
        if (!file.exists()) throw FileNotFoundException("File not found: $path")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        rejectRequireOriginal(uri)
        val path = uri.getQueryParameter(PARAM_PATH) ?: return null
        val file = File(path)
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        cursor.addRow(arrayOf(file.name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    private fun rejectRequireOriginal(uri: Uri) {
        if (uri.getQueryParameter("require_original") != null) {
            throw SecurityException(
                "Test SAF provider: setRequireOriginal is not supported for document URIs — " +
                    "use ACTION_OPEN_DOCUMENT to obtain a document URI instead."
            )
        }
    }

    companion object {
        const val AUTHORITY = "com.isardomains.sameview.test.safmimic"
        private const val PARAM_PATH = "path"

        fun uriFor(file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .path("/image")
            .appendQueryParameter(PARAM_PATH, file.absolutePath)
            .build()
    }
}
