// path: app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt
package com.isardomains.sameview.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.isardomains.sameview.AppConstants
import com.isardomains.sameview.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class SavedSessionRef(
    val sessionId: String,
    val timestamp: Long,
    val referenceFileUri: Uri,
    val captureFileUri: Uri,
    val referenceDate: String? = null
)

/** Result of [SessionStorage.writeReferenceSourceOriginal]. */
internal data class ReferenceSourceOriginalResult(
    val filename: String,
    val mimeType: String?
)

/**
 * Writes a v5 session (all six files) to app-internal storage under
 * filesDir/sessions/YYYY-MM-DD_HH-mm-ss/.
 *
 * Write order: capture-original.jpg → reference-source-original.[ext] → capture.jpg
 * → reference-original.jpg → reference.jpg → metadata.json (always last).
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
    private const val FILE_CAPTURE_ORIGINAL = "capture-original.jpg"
    private const val FILE_REFERENCE_SOURCE_ORIGINAL_BASE = "reference-source-original"
    private const val METADATA_VERSION = 5
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
            val captureOriginalFilename = writeCaptureOriginal(context, captureMediaStoreUri, sessionDir)
            val referenceSourceOriginalResult = writeReferenceSourceOriginal(context, snapshot.referenceImageUri, sessionDir)
            writeCapture(capturedBitmap, sessionDir, snapshot.gpsSnapshot)
            writeReferenceOriginalAndReference(context, snapshot, sessionDir)
            writeMetadata(sessionDir, sessionTimestampMs, snapshot, captureMediaStoreUri, captureOriginalFilename, referenceSourceOriginalResult)
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session saved") }
            return SavedSessionRef(
                sessionId = sessionDir.name,
                timestamp = sessionTimestampMs,
                referenceFileUri = Uri.fromFile(File(sessionDir, FILE_REFERENCE)),
                captureFileUri = Uri.fromFile(File(sessionDir, FILE_CAPTURE)),
                referenceDate = snapshot.referenceImageMetadata.exifDateTimeOriginal
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
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            val normalizedTitle = title?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }

            val metadataFile = File(sessionDir, "metadata.json")
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
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            return false
        }
    }

    /**
     * Atomically writes [title] and [description] into the content block of the session's
     * metadata.json.
     *
     * Both fields are trimmed; an empty or blank value is treated as absent (null).
     * When a field is non-null after normalisation it is written; when null it is removed.
     * The content block is **always** written back (never removed), even when both fields
     * are null — this preserves the block for future fields.
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, IO or security
     * errors.
     */
    fun updateContent(sessionsRoot: File, sessionId: String, title: String?, description: String?): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            val normalizedTitle = title?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedDescription = description?.let { MetadataTextSanitizer.sanitizeMultiLine(it) }

            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            val content = json.optJSONObject("content") ?: JSONObject()
            if (normalizedTitle != null) content.put("title", normalizedTitle) else content.remove("title")
            if (normalizedDescription != null) content.put("description", normalizedDescription) else content.remove("description")
            json.put("content", content)

            metadataFile.writeText(json.toString())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            return false
        }
    }

    /**
     * Updates [reference.date], [reference.dateSource], and [reference.userEdited] in the session's
     * metadata.json.
     *
     * When [date] is a valid ISO 8601 date string ("YYYY", "YYYY-MM", or "YYYY-MM-DD"):
     * sets [reference.date] to [date], [reference.dateSource] to "manual",
     * and [reference.userEdited] to true.
     *
     * When [date] is null: removes [reference.date] and [reference.dateSource];
     * sets [reference.userEdited] to true to record the deliberate user action.
     *
     * When [date] is a non-null invalid value: returns false without modifying anything.
     *
     * All other reference block fields (sourceDisplayName, dimensions, exifOrientation) are
     * preserved unchanged.
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, IO or security errors.
     */
    fun updateReferenceDate(sessionsRoot: File, sessionId: String, date: String?): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false

            if (date != null && !isValidReferenceDate(date)) return false

            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            val reference = json.optJSONObject("reference") ?: JSONObject()
            if (date != null) {
                reference.put("date", date)
                reference.put("dateSource", "manual")
            } else {
                reference.remove("date")
                reference.remove("dateSource")
            }
            reference.put("userEdited", true)
            json.put("reference", reference)

            metadataFile.writeText(json.toString())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Updates [location.displayName], [location.city], [location.country], and [location.userEdited]
     * in the session's metadata.json.
     *
     * Each string parameter is trimmed; blank strings are treated as null (field absent).
     * When at least one field is non-null after normalization: sets the respective fields and
     * sets [location.userEdited] to true. Fields that are null after normalization are removed.
     * When all three fields are null after normalization: removes the entire [location] block.
     *
     * [captureLocation] and [referenceLocation] are never modified.
     * All other metadata.json fields are preserved unchanged.
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, IO or security errors.
     */
    fun updateLocation(
        sessionsRoot: File,
        sessionId: String,
        displayName: String?,
        city: String?,
        country: String?
    ): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false

            val normalizedDisplayName = displayName?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedCity = city?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedCountry = country?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }

            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            val hasAnyField = normalizedDisplayName != null || normalizedCity != null || normalizedCountry != null
            if (hasAnyField) {
                val location = json.optJSONObject("location") ?: JSONObject()
                if (normalizedDisplayName != null) location.put("displayName", normalizedDisplayName) else location.remove("displayName")
                if (normalizedCity != null) location.put("city", normalizedCity) else location.remove("city")
                if (normalizedCountry != null) location.put("country", normalizedCountry) else location.remove("country")
                location.put("userEdited", true)
                json.put("location", location)
            } else {
                json.remove("location")
            }

            metadataFile.writeText(json.toString())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Sets [additional.isFavorite] in the session's metadata.json.
     *
     * If the [additional] block is absent (e.g. v2/v3 session), a new block is created
     * containing only [isFavorite]. No other fields ([visibility], [source]) are added.
     * If the [additional] block already exists, all existing fields are preserved and only
     * [isFavorite] is updated.
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, IO or
     * security errors.
     */
    fun updateFavorite(sessionsRoot: File, sessionId: String, isFavorite: Boolean): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false

            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            val additional = json.optJSONObject("additional") ?: JSONObject()
            additional.put("isFavorite", isFavorite)
            json.put("additional", additional)

            metadataFile.writeText(json.toString())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    private fun resolveDirectSessionDir(sessionsRoot: File, sessionId: String): File? {
        if (sessionId.isEmpty()) return null
        if (sessionId == "." || sessionId == "..") return null
        if (sessionId.contains('/') || sessionId.contains('\\')) return null
        if (File(sessionId).isAbsolute) return null

        val rootCanonical = sessionsRoot.canonicalFile
        val targetCanonical = File(sessionsRoot, sessionId).canonicalFile
        val parentCanonical = targetCanonical.parentFile?.canonicalFile ?: return null
        if (parentCanonical.path != rootCanonical.path) return null
        if (targetCanonical.name != sessionId) return null
        return targetCanonical
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

    /**
     * Returns true if [date] is a valid reference date in one of the three supported ISO 8601
     * precision levels: "YYYY", "YYYY-MM", or "YYYY-MM-DD".
     *
     * Rules:
     * - No trimming; any whitespace makes the value invalid.
     * - Only "-" is accepted as a separator.
     * - Year must be exactly 4 ASCII digits, >= 1826 and <= current device year.
     * - Month (when present) must be exactly 2 ASCII digits, 01–12.
     * - Day (when present) must be a real calendar day for the given month and year,
     *   validated with a non-lenient Calendar (rejects e.g. Feb 31, Apr 31).
     * - Empty string is invalid; removal is expressed by passing null to [updateReferenceDate].
     */
    internal fun isValidReferenceDate(date: String): Boolean {
        if (date.isEmpty()) return false
        val parts = date.split("-")
        if (parts.size < 1 || parts.size > 3) return false

        // Year: exactly 4 ASCII digits
        if (parts[0].length != 4 || !parts[0].all { it in '0'..'9' }) return false
        val year = parts[0].toIntOrNull() ?: return false

        // Year plausibility: 1826 (earliest photograph) <= year <= current device year
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (year < 1826 || year > currentYear) return false

        if (parts.size >= 2) {
            // Month: exactly 2 ASCII digits, 01–12
            if (parts[1].length != 2 || !parts[1].all { it in '0'..'9' }) return false
            val month = parts[1].toIntOrNull() ?: return false
            if (month < 1 || month > 12) return false
        }

        if (parts.size == 3) {
            // Day: exactly 2 ASCII digits; validate with non-lenient Calendar
            if (parts[2].length != 2 || !parts[2].all { it in '0'..'9' }) return false
            val month = parts[1].toIntOrNull() ?: return false
            val day = parts[2].toIntOrNull() ?: return false
            val cal = Calendar.getInstance().apply { isLenient = false }
            cal.set(year, month - 1, day)
            try {
                cal.time // throws IllegalArgumentException for out-of-range dates
            } catch (_: Exception) {
                return false
            }
        }

        return true
    }

    private fun writeCapture(bitmap: Bitmap, sessionDir: File, gpsSnapshot: GpsSnapshot?) {
        val file = File(sessionDir, FILE_CAPTURE)
        writeBitmapAsJpeg(bitmap, file)
        writeSoftwareExif(file)
        // GPS: fail-soft — a write failure never invalidates the session
        if (gpsSnapshot != null) {
            GpsExifWriter.writeGpsToFile(file, gpsSnapshot)
        }
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
            // GPS preservation: copy reference GPS to reference-original.jpg when guidance is ON.
            // reference.jpg never gets GPS — no write here.
            if (snapshot.recreationGuidanceEnabled) {
                val refLat = snapshot.referenceImageMetadata.gpsLatitude
                val refLon = snapshot.referenceImageMetadata.gpsLongitude
                if (refLat != null && refLon != null) {
                    val refGps = GpsSnapshot(
                        latitude = refLat,
                        longitude = refLon,
                        altitude = snapshot.referenceImageMetadata.gpsAltitude,
                        accuracyMeters = null
                    )
                    GpsExifWriter.writeGpsToFile(File(sessionDir, FILE_REFERENCE_ORIGINAL), refGps)
                }
            }
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
        captureMediaStoreUri: Uri,
        captureOriginalFilename: String,
        referenceSourceOriginalResult: ReferenceSourceOriginalResult
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
                put("captureOriginal", captureOriginalFilename)
                put("reference", FILE_REFERENCE)
                put("referenceOriginal", FILE_REFERENCE_ORIGINAL)
                put("referenceSourceOriginal", referenceSourceOriginalResult.filename)
            })
            put("capture", JSONObject().apply {
                put("timestampMs", sessionTimestampMs)
                put("mediaStoreUri", captureMediaStoreUri.toString())
            })
            val gps = snapshot.gpsSnapshot
            if (gps != null) {
                put("captureLocation", JSONObject().apply {
                    put("latitude", gps.latitude)
                    put("longitude", gps.longitude)
                    if (gps.altitude != null) put("altitude", gps.altitude)
                    if (gps.accuracyMeters != null) put("accuracyMeters", gps.accuracyMeters.toDouble())
                    if (gps.provider != null) put("provider", gps.provider)
                    if (gps.fixTimestampMs != null) put("fixTimestampMs", gps.fixTimestampMs)
                })
            }
            if (snapshot.recreationGuidanceEnabled) {
                val refLat = snapshot.referenceImageMetadata.gpsLatitude
                val refLon = snapshot.referenceImageMetadata.gpsLongitude
                if (refLat != null && refLon != null) {
                    put("referenceLocation", JSONObject().apply {
                        put("latitude", refLat)
                        put("longitude", refLon)
                        val refAlt = snapshot.referenceImageMetadata.gpsAltitude
                        if (refAlt != null) put("altitude", refAlt)
                        put("source", "exif")
                    })
                }
            }
            put("reference", JSONObject().apply {
                put("sourceUri", snapshot.referenceImageUri.toString())
                val mimeType = referenceSourceOriginalResult.mimeType
                if (!mimeType.isNullOrBlank()) put("sourceMimeType", mimeType)
                put("originalWidth", snapshot.referenceImageMetadata.rawWidth)
                put("originalHeight", snapshot.referenceImageMetadata.rawHeight)
                put("orientedWidth", snapshot.referenceImageMetadata.orientedWidth)
                put("orientedHeight", snapshot.referenceImageMetadata.orientedHeight)
                val exifOri = snapshot.referenceImageMetadata.exifOrientation
                if (exifOri != null) put("exifOrientation", exifOri) else put("exifOrientation", JSONObject.NULL)
                val exifDate = snapshot.referenceImageMetadata.exifDateTimeOriginal
                if (exifDate != null) {
                    put("date", exifDate)
                    put("dateSource", "exif")
                    put("userEdited", false)
                }
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
            put("additional", JSONObject().apply {
                put("isFavorite", false)
                put("visibility", "private")
                put("source", "sameview")
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

    /**
     * Maps a MIME type string to a file extension for [FILE_REFERENCE_SOURCE_ORIGINAL_BASE].
     *
     * Returns `.bin` for null, empty, or unrecognized MIME types so the bytes are preserved
     * without asserting a format that cannot be confirmed.
     */
    internal fun resolveExtensionForMimeType(mimeType: String?): String = when (mimeType?.lowercase()) {
        "image/jpeg" -> ".jpg"
        "image/heic", "image/heif" -> ".heic"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/avif" -> ".avif"
        "image/bmp" -> ".bmp"
        else -> ".bin"
    }

    /**
     * Applies [MediaStore.setRequireOriginal] for classic MediaStore image URIs
     * (authority "media", path NOT starting with "/picker/") so that the byte-copy
     * receives the original file rather than a transcoded version.
     *
     * Photo Picker URIs (Android 13+) also carry authority "media" but their path
     * starts with "/picker/". They do NOT support setRequireOriginal: on Android 16,
     * the method succeeds in appending "?requireOriginal=1" to the URI, but the picker
     * provider then rejects the modified URI in openInputStream with
     * "Require Original is not supported for Picker URI", causing the session save to fail.
     * Such URIs are returned unchanged so openInputStream receives the original picker URI.
     *
     * Falls back to the plain [uri] on any exception from [MediaStore.setRequireOriginal]
     * itself, or for non-media URIs.
     */
    internal fun resolveSourceUri(uri: Uri): Uri {
        if (uri.scheme != "content" || uri.authority != "media") return uri
        if (uri.path?.startsWith("/picker/") == true) return uri
        return try {
            MediaStore.setRequireOriginal(uri)
        } catch (_: UnsupportedOperationException) {
            uri
        } catch (_: SecurityException) {
            uri
        } catch (_: IllegalArgumentException) {
            uri
        }
    }

    /** Copies bytes from [sourceUri] to [destFile] in 8 KB chunks. Throws [IOException] on failure. */
    private fun copyStreamToFile(context: Context, sourceUri: Uri, destFile: File) {
        context.contentResolver.openInputStream(sourceUri)
            ?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            } ?: throw IOException("Could not open input stream for $sourceUri")
    }

    /**
     * Copies the committed MediaStore capture file byte-for-byte to [FILE_CAPTURE_ORIGINAL]
     * inside [sessionDir]. [captureMediaStoreUri] must already be committed (IS_PENDING = 0).
     * [MediaStore.setRequireOriginal] is intentionally not applied — this is our own committed entry.
     *
     * @return [FILE_CAPTURE_ORIGINAL] (the fixed filename).
     * @throws IOException if the stream cannot be opened or the copy fails.
     */
    private fun writeCaptureOriginal(context: Context, captureMediaStoreUri: Uri, sessionDir: File): String {
        copyStreamToFile(context, captureMediaStoreUri, File(sessionDir, FILE_CAPTURE_ORIGINAL))
        return FILE_CAPTURE_ORIGINAL
    }

    /**
     * Copies the reference source file byte-for-byte to a file named
     * `reference-source-original.[ext]` inside [sessionDir], where `[ext]` is derived from
     * the source MIME type. For `media`-authority URIs [MediaStore.setRequireOriginal] is applied
     * so that the original format (e.g. HEIC) is preserved rather than a transcoded copy.
     *
     * @return [ReferenceSourceOriginalResult] containing the constructed filename and the
     *   resolved MIME type (may be null when the content provider does not report one).
     * @throws IOException if the stream cannot be opened or the copy fails.
     */
    private fun writeReferenceSourceOriginal(
        context: Context,
        referenceUri: Uri,
        sessionDir: File
    ): ReferenceSourceOriginalResult {
        val resolvedUri = resolveSourceUri(referenceUri)
        val mimeType = context.contentResolver.getType(resolvedUri)
            ?: context.contentResolver.getType(referenceUri)
        val extension = resolveExtensionForMimeType(mimeType)
        val filename = "$FILE_REFERENCE_SOURCE_ORIGINAL_BASE$extension"
        copyStreamToFile(context, resolvedUri, File(sessionDir, filename))
        return ReferenceSourceOriginalResult(filename = filename, mimeType = mimeType)
    }
}
