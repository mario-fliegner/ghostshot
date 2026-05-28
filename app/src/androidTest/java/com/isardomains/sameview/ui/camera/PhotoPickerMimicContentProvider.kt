package com.isardomains.sameview.ui.camera

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Test-only ContentProvider simulating the Photo Picker / MediaStore split-source behaviour
 * that causes the double-rotation format-mismatch bug.
 *
 * Real-device scenario reproduced here:
 * - A portrait HEIC is physically stored with landscape sensor pixels (e.g. rawW=4032, rawH=3024)
 *   plus EXIF ORIENTATION_ROTATE_90.
 * - MediaStore.openInputStream(uri) may return a transcoded pre-rotated JPEG with portrait pixels
 *   (rawW=3024, rawH=4032) and no EXIF rotation tag.
 * - MediaStore.openFileDescriptor(setRequireOriginal(uri)) returns the original HEIC with
 *   landscape pixels and EXIF ROTATE_90.
 *
 * Without the fix the reader combined portrait dims from the plain stream with ROTATE_90 from
 * the original, doubling the rotation to an incorrect landscape oriented result.
 *
 * This provider simulates the split by serving different files based on the presence of the
 * "require_original" query parameter that [android.provider.MediaStore.setRequireOriginal] appends:
 * - URI without "require_original" → serves the pre-rotated file (simulates transcoded stream)
 * - URI with "require_original=1" → serves the original file (simulates setRequireOriginal path)
 *
 * The provider authority is [AUTHORITY], which is NOT "media". Tests pass [AUTHORITY] explicitly
 * via the [com.isardomains.sameview.ui.camera.ReferenceImageMetadataReader.read]
 * `requireOriginalAuthorities` parameter so the reader applies setRequireOriginal to this URI,
 * exercising the production code path without touching real MediaStore.
 */
internal class PhotoPickerMimicContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val useOriginal = uri.getQueryParameter("require_original") != null
        val path = if (useOriginal) {
            uri.getQueryParameter(PARAM_ORIGINAL_PATH)
        } else {
            uri.getQueryParameter(PARAM_PREROTATED_PATH)
        } ?: throw FileNotFoundException("Missing path parameter in URI: $uri")
        val file = File(path)
        if (!file.exists()) throw FileNotFoundException("File not found: $path")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val path = uri.getQueryParameter(PARAM_ORIGINAL_PATH) ?: return null
        val file = File(path)
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        cursor.addRow(arrayOf(file.name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.isardomains.sameview.test.photopickermimicmedia"
        private const val PARAM_ORIGINAL_PATH = "original_path"
        private const val PARAM_PREROTATED_PATH = "prerotated_path"

        /**
         * Builds a URI that [PhotoPickerMimicContentProvider] can serve.
         *
         * @param originalFile The file with the original sensor-native pixels and EXIF orientation.
         * @param prerotatedFile The file with rotation baked into pixels and no EXIF rotation.
         */
        fun uriFor(originalFile: File, prerotatedFile: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .path("/image")
            .appendQueryParameter(PARAM_ORIGINAL_PATH, originalFile.absolutePath)
            .appendQueryParameter(PARAM_PREROTATED_PATH, prerotatedFile.absolutePath)
            .build()
    }
}
