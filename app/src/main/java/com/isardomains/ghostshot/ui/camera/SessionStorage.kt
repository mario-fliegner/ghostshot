// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/SessionStorage.kt
package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.isardomains.ghostshot.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SavedSessionRef(
    val sessionId: String,
    val timestamp: Long
)

/**
 * Writes a session pair (capture.jpg + reference.jpg) to app-internal storage under
 * filesDir/sessions/YYYY-MM-DD_HH-mm-ss/.
 *
 * A session is only created as a complete pair. If either file cannot be written,
 * the session directory is removed so no partial session is ever left on disk.
 *
 * Must be called from a background dispatcher.
 */
internal object SessionStorage {

    private const val TAG = "SessionStorage"
    private const val SESSIONS_DIR = "sessions"
    private const val JPEG_QUALITY = 90

    /**
     * Creates a session directory and writes capture.jpg and reference.jpg.
     *
     * Both files are always written as JPEG regardless of the original source format.
     * [capturedBitmap] must already be correctly rotated; the caller retains ownership
     * and must not recycle it before this call returns.
     * [referenceUri] is decoded, EXIF-oriented using [exifOrientation], then written as JPEG.
     *
     * On any error the partially created session directory is deleted. Never throws.
     */
    fun saveSession(
        context: Context,
        capturedBitmap: Bitmap,
        referenceUri: Uri,
        exifOrientation: Int?,
        captureMediaStoreUri: Uri,
        referencePickerUri: Uri
    ) = saveSession(
        context = context,
        sessionsRoot = File(context.filesDir, SESSIONS_DIR),
        capturedBitmap = capturedBitmap,
        referenceUri = referenceUri,
        exifOrientation = exifOrientation,
        captureMediaStoreUri = captureMediaStoreUri,
        referencePickerUri = referencePickerUri
    )

    internal fun saveSession(
        context: Context,
        sessionsRoot: File,
        capturedBitmap: Bitmap,
        referenceUri: Uri,
        exifOrientation: Int?,
        captureMediaStoreUri: Uri,
        referencePickerUri: Uri
    ): SavedSessionRef? {
        val sessionTimestampMs = System.currentTimeMillis()
        val baseName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(sessionTimestampMs))
        val sessionDir = resolveUniqueDir(sessionsRoot, baseName)
        try {
            if (!sessionDir.mkdirs()) {
                throw IOException("Could not create session directory: $sessionDir")
            }
            writeCapture(capturedBitmap, sessionDir)
            writeReference(context, referenceUri, exifOrientation, sessionDir)
            writeMetadata(sessionDir, sessionTimestampMs, captureMediaStoreUri, referencePickerUri)
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session saved") }
            return SavedSessionRef(sessionId = sessionDir.name, timestamp = sessionTimestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "Session save failed, removing partial session: ${e.message}")
            sessionDir.deleteRecursively()
            return null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Session save OOM, removing partial session")
            sessionDir.deleteRecursively()
            return null
        }
    }

    fun updateTitle(sessionsRoot: File, sessionId: String, title: String?): Boolean {
        val rootCanonical = sessionsRoot.canonicalPath + File.separator
        val targetCanonical = File(sessionsRoot, sessionId).canonicalPath
        if (!targetCanonical.startsWith(rootCanonical)) return false

        val normalizedTitle = title?.trim()?.ifEmpty { null }

        val metadataFile = File(File(sessionsRoot, sessionId), "metadata.json")
        if (!metadataFile.exists()) return false

        val json = try {
            JSONObject(metadataFile.readText())
        } catch (e: Exception) {
            return false
        }

        if (normalizedTitle != null) {
            json.put("title", normalizedTitle)
        } else {
            json.remove("title")
        }

        metadataFile.writeText(json.toString())
        return true
    }

    private fun resolveUniqueDir(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "${baseName}_$counter")
            counter++
        }
        return candidate
    }

    private fun writeMetadata(
        sessionDir: File,
        sessionTimestampMs: Long,
        captureMediaStoreUri: Uri,
        referencePickerUri: Uri
    ) {
        val json = JSONObject().apply {
            put("version", 1)
            put("sessionTimestampMs", sessionTimestampMs)
            put("referenceFile", "reference.jpg")
            put("captureFile", "capture.jpg")
            put("captureMediaStoreUri", captureMediaStoreUri.toString())
            put("referencePickerUri", referencePickerUri.toString())
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
    }

    private fun writeCapture(bitmap: Bitmap, sessionDir: File) {
        writeBitmapAsJpeg(bitmap, File(sessionDir, "capture.jpg"))
    }

    private fun writeReference(
        context: Context,
        referenceUri: Uri,
        exifOrientation: Int?,
        sessionDir: File
    ) {
        var raw: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            raw = context.contentResolver.openInputStream(referenceUri)
                ?.use { BitmapFactory.decodeStream(it) }
                ?: throw IOException("Could not decode reference bitmap from $referenceUri")
            oriented = applyExifOrientation(raw, exifOrientation)
            writeBitmapAsJpeg(oriented, File(sessionDir, "reference.jpg"))
        } finally {
            if (oriented !== raw) oriented?.recycle()
            raw?.recycle()
        }
    }

    private fun writeBitmapAsJpeg(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                throw IOException("Bitmap.compress failed for ${file.name}")
            }
        }
    }

    private fun applyExifOrientation(source: Bitmap, exifOrientation: Int?): Bitmap {
        val matrix = Matrix()
        val needsTransform = when (exifOrientation) {
            null,
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL -> false
            ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); true }
            ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); true }
            ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); true }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f); true
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f, source.width / 2f, source.height / 2f); true
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f); true
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f); matrix.postScale(-1f, 1f); true
            }
            else -> false
        }
        return if (needsTransform) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }
    }
}
