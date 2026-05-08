// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/SessionStorage.kt
package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.isardomains.ghostshot.AppConstants
import com.isardomains.ghostshot.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SavedSessionRef(
    val sessionId: String,
    val timestamp: Long,
    val referenceFileUri: Uri,
    val captureFileUri: Uri
)

/**
 * Writes a v2 session (capture.jpg + reference-original.jpg + reference.jpg + metadata.json)
 * to app-internal storage under filesDir/sessions/YYYY-MM-DD_HH-mm-ss/.
 *
 * A session is only created as a complete set. If any file cannot be written,
 * the session directory is removed so no partial session is ever left on disk.
 *
 * metadata.json is always written last so SessionScanner never sees an incomplete session.
 *
 * Must be called from a background dispatcher.
 */
internal object SessionStorage {

    private const val TAG = "SessionStorage"
    private const val SESSIONS_DIR = "sessions"
    private const val FILE_CAPTURE = "capture.jpg"
    private const val FILE_REFERENCE = "reference.jpg"
    private const val FILE_REFERENCE_ORIGINAL = "reference-original.jpg"
    private const val METADATA_VERSION = 2
    private const val JPEG_QUALITY = 90

    /**
     * Creates a session directory and writes all four session files.
     *
     * [capturedBitmap] must already be correctly rotated; the caller retains ownership
     * and must not recycle it before this call returns.
     * [snapshot] carries the frozen capture-time rendering state used to render reference.jpg
     * and to populate metadata.json.
     *
     * On any error the partially created session directory is deleted. Never throws.
     */
    fun saveSession(
        context: Context,
        capturedBitmap: Bitmap,
        snapshot: CaptureSessionSnapshot,
        captureMediaStoreUri: Uri
    ) = saveSession(
        context = context,
        sessionsRoot = File(context.filesDir, SESSIONS_DIR),
        capturedBitmap = capturedBitmap,
        snapshot = snapshot,
        captureMediaStoreUri = captureMediaStoreUri
    )

    internal fun saveSession(
        context: Context,
        sessionsRoot: File,
        capturedBitmap: Bitmap,
        snapshot: CaptureSessionSnapshot,
        captureMediaStoreUri: Uri
    ): SavedSessionRef? {
        val sessionTimestampMs = System.currentTimeMillis()
        val baseName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(sessionTimestampMs))
        val sessionDir = resolveUniqueDir(sessionsRoot, baseName)
        try {
            if (!sessionDir.mkdirs()) {
                throw IOException("Could not create session directory: $sessionDir")
            }
            writeCapture(capturedBitmap, sessionDir)
            writeReferenceOriginalAndReference(context, snapshot, sessionDir)
            writeMetadata(sessionDir, sessionTimestampMs, snapshot, captureMediaStoreUri)
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session saved") }
            return SavedSessionRef(
                sessionId = sessionDir.name,
                timestamp = sessionTimestampMs,
                referenceFileUri = Uri.fromFile(File(sessionDir, FILE_REFERENCE)),
                captureFileUri = Uri.fromFile(File(sessionDir, FILE_CAPTURE))
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "Session save failed, removing partial session: ${e.message}") }
            sessionDir.deleteRecursively()
            return null
        } catch (e: OutOfMemoryError) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "Session save OOM, removing partial session") }
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

        val content = json.optJSONObject("content") ?: JSONObject()
        if (normalizedTitle != null) {
            content.put("title", normalizedTitle)
        } else {
            content.remove("title")
        }
        json.put("content", content)

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

    private fun writeCapture(bitmap: Bitmap, sessionDir: File) {
        val file = File(sessionDir, FILE_CAPTURE)
        writeBitmapAsJpeg(bitmap, file)
        writeSoftwareExif(file)
    }

    private fun writeSoftwareExif(file: File) {
        try {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_SOFTWARE, AppConstants.CAPTURE_EXIF_SOFTWARE)
                saveAttributes()
            }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "Failed to write EXIF software tag: ${e.message}") }
        }
    }

    /**
     * Decodes the source reference image, writes the EXIF-oriented full image as
     * reference-original.jpg, then renders the final compare reference via ReferenceRenderer
     * and writes it as reference.jpg.
     *
     * OOM is not caught here — it propagates up to saveSession() where the session directory
     * is cleaned up.
     */
    private fun writeReferenceOriginalAndReference(
        context: Context,
        snapshot: CaptureSessionSnapshot,
        sessionDir: File
    ) {
        var raw: Bitmap? = null
        var oriented: Bitmap? = null
        var rendered: Bitmap? = null
        try {
            raw = context.contentResolver.openInputStream(snapshot.referenceImageUri)
                ?.use { BitmapFactory.decodeStream(it) }
                ?: throw IOException("Could not decode reference bitmap from ${snapshot.referenceImageUri}")
            oriented = applyExifOrientation(raw, snapshot.referenceImageMetadata.exifOrientation)
            writeBitmapAsJpeg(oriented, File(sessionDir, FILE_REFERENCE_ORIGINAL))
            rendered = ReferenceRenderer.render(
                sourceBitmap = oriented,
                viewportWidth = snapshot.viewportWidth,
                viewportHeight = snapshot.viewportHeight,
                overlayScale = snapshot.overlayScale,
                overlayOffsetX = snapshot.overlayOffsetX,
                overlayOffsetY = snapshot.overlayOffsetY,
                displayMode = snapshot.referenceImageDisplayMode,
            )
            writeBitmapAsJpeg(rendered, File(sessionDir, FILE_REFERENCE))
        } finally {
            rendered?.recycle()
            if (oriented !== raw) oriented?.recycle()
            raw?.recycle()
        }
    }

    private fun writeMetadata(
        sessionDir: File,
        sessionTimestampMs: Long,
        snapshot: CaptureSessionSnapshot,
        captureMediaStoreUri: Uri
    ) {
        val orientation = if (snapshot.viewportWidth > snapshot.viewportHeight) "LANDSCAPE" else "PORTRAIT"
        val json = JSONObject().apply {
            put("version", METADATA_VERSION)
            put("session", JSONObject().apply {
                put("id", sessionDir.name)
                put("createdAtMs", sessionTimestampMs)
            })
            put("files", JSONObject().apply {
                put("capture", FILE_CAPTURE)
                put("reference", FILE_REFERENCE)
                put("referenceOriginal", FILE_REFERENCE_ORIGINAL)
            })
            put("content", JSONObject().apply {
                put("description", JSONObject.NULL)
                put("tags", JSONArray())
            })
            put("capture", JSONObject().apply {
                put("mediaStoreUri", captureMediaStoreUri.toString())
            })
            put("reference", JSONObject().apply {
                put("sourceDisplayName", snapshot.referenceImageUri.toString())
                put("originalWidth", snapshot.referenceImageMetadata.rawWidth)
                put("originalHeight", snapshot.referenceImageMetadata.rawHeight)
                put("orientedWidth", snapshot.referenceImageMetadata.orientedWidth)
                put("orientedHeight", snapshot.referenceImageMetadata.orientedHeight)
                val exifOri = snapshot.referenceImageMetadata.exifOrientation
                if (exifOri != null) put("exifOrientation", exifOri) else put("exifOrientation", JSONObject.NULL)
            })
            put("viewport", JSONObject().apply {
                put("width", snapshot.viewportWidth)
                put("height", snapshot.viewportHeight)
                put("orientation", orientation)
            })
            put("overlay", JSONObject().apply {
                put("scale", snapshot.overlayScale.toDouble())
                put("offsetX", snapshot.overlayOffsetX.toDouble())
                put("offsetY", snapshot.overlayOffsetY.toDouble())
                put("displayMode", snapshot.referenceImageDisplayMode.name)
            })
            put("rendering", JSONObject().apply {
                put("referenceBackgroundColor", "#17202F")
                put("referenceJpegQuality", JPEG_QUALITY)
            })
            put("location", JSONObject().apply {
                put("latitude", JSONObject.NULL)
                put("longitude", JSONObject.NULL)
                put("accuracyMeters", JSONObject.NULL)
                put("source", JSONObject.NULL)
            })
        }
        File(sessionDir, "metadata.json").writeText(json.toString())
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
