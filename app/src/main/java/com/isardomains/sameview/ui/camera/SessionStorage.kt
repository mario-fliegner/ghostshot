// path: app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt
package com.isardomains.sameview.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.isardomains.sameview.AppConstants
import com.isardomains.sameview.BuildConfig
import com.isardomains.sameview.branding.GlobalBranding
import com.isardomains.sameview.branding.GlobalBrandingRepository
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

/** Preservation mode constants written to the metadata.json `originals` block. */
internal const val PRESERVATION_BYTE_COPY = "byte_copy"
internal const val PRESERVATION_METADATA_STRIPPED = "metadata_stripped"
internal const val PRESERVATION_NOT_POSSIBLE = "not_possible"

/** Result of [SessionStorage.writeReferenceSourceOriginal]. */
internal data class ReferenceSourceOriginalResult(
    val filename: String,
    val mimeType: String?,
    /** MIME type of the file as stored (may differ from [mimeType] when format conversion occurred). */
    val storedMimeType: String? = null,
    /** How the original was preserved. Defaults to [PRESERVATION_BYTE_COPY]. */
    val preservation: String = PRESERVATION_BYTE_COPY
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
    internal const val FILE_BRANDING_HANDLE = "branding-handle.png"
    private const val FILE_BRANDING_HANDLE_TMP = "branding-handle-new.png"
    private const val METADATA_VERSION = 6
    private const val JPEG_QUALITY = 90
    private const val JPEG_QUALITY_PRIVACY = 95

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
        captureMediaStoreUri: Uri,
        stripMetadata: Boolean = false,
        globalBrandingRepository: GlobalBrandingRepository? = null
    ) = saveSession(
        context = context,
        sessionsRoot = File(context.filesDir, SESSIONS_DIR),
        capturedBitmap = capturedBitmap,
        snapshot = snapshot,
        captureMediaStoreUri = captureMediaStoreUri,
        stripMetadata = stripMetadata,
        globalBranding = globalBrandingRepository?.getBranding()
    )

    internal fun saveSession(
        context: Context,
        sessionsRoot: File,
        capturedBitmap: Bitmap,
        snapshot: CaptureSessionSnapshot,
        captureMediaStoreUri: Uri,
        stripMetadata: Boolean = false,
        globalBranding: GlobalBranding? = null
    ): SavedSessionRef? {
        val sessionTimestampMs = System.currentTimeMillis()
        val baseName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(sessionTimestampMs))
        val sessionDir = resolveUniqueDir(sessionsRoot, baseName)
        try {
            if (!sessionDir.mkdirs()) {
                throw IOException("Could not create session directory: $sessionDir")
            }
            val captureOriginalFilename = writeCaptureOriginal(context, captureMediaStoreUri, sessionDir, stripMetadata)
            val referenceSourceOriginalResult = writeReferenceSourceOriginal(context, snapshot.referenceImageUri, sessionDir, stripMetadata)
            writeCapture(capturedBitmap, sessionDir, snapshot.gpsSnapshot)
            writeReferenceOriginalAndReference(context, snapshot, sessionDir)
            // Branding copy: fail-soft — a failure here never aborts the session save.
            val brandingCopied = globalBranding?.let { branding ->
                writeBrandingHandleToSession(sessionDir, branding)
            } ?: false
            writeMetadata(
                sessionDir, sessionTimestampMs, snapshot, captureMediaStoreUri,
                captureOriginalFilename, referenceSourceOriginalResult, stripMetadata,
                brandingData = if (brandingCopied) globalBranding else null
            )
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
     * Updates [location.displayName], [location.city], [location.country], [location.countryCode],
     * and [location.userEdited] in the session's metadata.json.
     *
     * Each string parameter is trimmed; blank strings are treated as null (field absent).
     * When at least one field is non-null after normalization: sets the respective fields and
     * sets [location.userEdited] to true. Fields that are null after normalization are removed.
     * When all four fields are null after normalization: removes the entire [location] block.
     *
     * [countryCode] has no default value and is not optional to omit: every caller must always pass
     * the full intended state of all four location values, exactly as already required for
     * [displayName]/[city]/[country]. This is a full-replacement API, not a partial patch — passing
     * `null` for [countryCode] removes it. A caller that wants to preserve an existing
     * [location.countryCode] while changing an unrelated location value (e.g. City) must read the
     * current value first and pass it through unchanged, exactly as already required for the other
     * three fields today.
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
        country: String?,
        countryCode: String?
    ): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false

            val normalizedDisplayName = displayName?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedCity = city?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedCountry = country?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }
            val normalizedCountryCode = countryCode?.let { MetadataTextSanitizer.sanitizeSingleLine(it) }

            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            val hasAnyField = normalizedDisplayName != null || normalizedCity != null ||
                normalizedCountry != null || normalizedCountryCode != null
            if (hasAnyField) {
                val location = json.optJSONObject("location") ?: JSONObject()
                if (normalizedDisplayName != null) location.put("displayName", normalizedDisplayName) else location.remove("displayName")
                if (normalizedCity != null) location.put("city", normalizedCity) else location.remove("city")
                if (normalizedCountry != null) location.put("country", normalizedCountry) else location.remove("country")
                if (normalizedCountryCode != null) location.put("countryCode", normalizedCountryCode) else location.remove("countryCode")
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

    // ── Branding update functions ──────────────────────────────────────────────

    /**
     * Builds the JSON object written to the `branding` block of metadata.json.
     *
     * @param handleFile  Filename of the branding handle PNG in the session directory.
     * @param meta        Type and optional built-in symbol ID.
     * @param updatedAtMs Timestamp for the update in milliseconds since Unix epoch.
     */
    private fun buildBrandingJson(handleFile: String, meta: SessionBrandingMeta, updatedAtMs: Long): JSONObject =
        JSONObject().apply {
            put("handleFile", handleFile)
            put("type", meta.type)
            if (meta.builtinId != null) put("builtinId", meta.builtinId)
            put("updatedAtMs", updatedAtMs)
        }

    /**
     * Writes [brandingPng] as the session branding handle PNG and updates metadata.json.
     *
     * Write order for atomicity:
     * 1. Write [brandingPng] to a temp file in the session directory.
     * 2. [Files.move] temp → [FILE_BRANDING_HANDLE] (atomic replace on Linux/Android).
     * 3. Only after the file is in place: update metadata.json.
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, or any
     * IO/security error. Existing session files and metadata are never corrupted on failure.
     */
    fun updateSessionBranding(
        sessionsRoot: File,
        sessionId: String,
        brandingPng: ByteArray,
        type: String,
        builtinId: String?
    ): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            // Write PNG atomically via temp file.
            val tmpFile = File(sessionDir, FILE_BRANDING_HANDLE_TMP)
            try {
                tmpFile.writeBytes(brandingPng)
                Files.move(tmpFile.toPath(), File(sessionDir, FILE_BRANDING_HANDLE).toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                tmpFile.delete()
                return false
            }

            // PNG is in place — update metadata.
            val filesObj = json.optJSONObject("files") ?: JSONObject()
            filesObj.put("brandingHandle", FILE_BRANDING_HANDLE)
            json.put("files", filesObj)

            val meta = SessionBrandingMeta(type = type, builtinId = builtinId)
            json.put("branding", buildBrandingJson(FILE_BRANDING_HANDLE, meta, System.currentTimeMillis()))

            metadataFile.writeText(json.toString())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Removes session branding from both metadata.json and the filesystem.
     *
     * Removal order for safety:
     * 1. Remove `files.brandingHandle` and the `branding` block from metadata.json first.
     *    This ensures the scanner never sees a reference to a missing file.
     * 2. Delete [FILE_BRANDING_HANDLE] from the session directory.
     *    If deletion fails, the orphan file is harmless (no metadata reference remains).
     *
     * Returns false on invalid [sessionId], path traversal, missing metadata.json, or any
     * IO/security error. Does NOT fall back to global branding — removal is permanent until
     * the user explicitly re-adds branding via [updateSessionBranding] or [copyGlobalBrandingToSession].
     */
    fun removeSessionBranding(sessionsRoot: File, sessionId: String): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            // Step 1: remove from metadata first (safety: no dangling file reference).
            val filesObj = json.optJSONObject("files")
            if (filesObj != null) {
                filesObj.remove("brandingHandle")
                json.put("files", filesObj)
            }
            json.remove("branding")
            metadataFile.writeText(json.toString())

            // Step 2: delete the branding file. Failure leaves an orphan — acceptable.
            File(sessionDir, FILE_BRANDING_HANDLE).delete()
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Copies the current global branding into an existing session.
     *
     * This is the "Copy from default branding" operation: an explicit one-time copy
     * with no live link. After this call the session branding is independent — future
     * changes to global branding do NOT affect this session.
     *
     * Returns false if [globalBranding] is not provided (null at call site), the session
     * directory or metadata.json is missing, or any IO/security error occurs.
     * Does NOT fall back to any other branding source.
     */
    fun copyGlobalBrandingToSession(
        sessionsRoot: File,
        sessionId: String,
        globalBranding: GlobalBranding
    ): Boolean {
        return try {
            val sessionDir = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            val metadataFile = File(sessionDir, "metadata.json")
            if (!metadataFile.exists()) return false

            val json = try {
                JSONObject(metadataFile.readText())
            } catch (e: Exception) {
                return false
            }

            // Write PNG atomically via temp file.
            val tmpFile = File(sessionDir, FILE_BRANDING_HANDLE_TMP)
            try {
                globalBranding.file.inputStream().use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                Files.move(tmpFile.toPath(), File(sessionDir, FILE_BRANDING_HANDLE).toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                tmpFile.delete()
                return false
            }

            // PNG is in place — update metadata.
            val filesObj = json.optJSONObject("files") ?: JSONObject()
            filesObj.put("brandingHandle", FILE_BRANDING_HANDLE)
            json.put("files", filesObj)
            json.put("branding", buildBrandingJson(FILE_BRANDING_HANDLE, globalBranding.meta, System.currentTimeMillis()))

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

    /**
     * Copies the global branding handle PNG into [sessionDir] as [FILE_BRANDING_HANDLE].
     *
     * Returns true on success, false on any IO failure (fail-soft: session creation continues
     * without branding when this returns false).
     */
    private fun writeBrandingHandleToSession(sessionDir: File, globalBranding: GlobalBranding): Boolean {
        return try {
            val dest = File(sessionDir, FILE_BRANDING_HANDLE)
            globalBranding.file.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "Branding copy failed (non-fatal): ${e.message}") }
            File(sessionDir, FILE_BRANDING_HANDLE).delete()
            false
        }
    }

    private fun writeMetadata(
        sessionDir: File,
        sessionTimestampMs: Long,
        snapshot: CaptureSessionSnapshot,
        captureMediaStoreUri: Uri,
        captureOriginalFilename: String,
        referenceSourceOriginalResult: ReferenceSourceOriginalResult,
        stripMetadata: Boolean = false,
        brandingData: GlobalBranding? = null
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
                if (brandingData != null) put("brandingHandle", FILE_BRANDING_HANDLE)
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
            // originals block — only written when privacy mode is ON.
            // Absent when stripMetadata = false (default). Informational only; not validated by scanner.
            if (stripMetadata) {
                put("originals", JSONObject().apply {
                    put("privacyMode", true)
                    put("capturePreservation", PRESERVATION_METADATA_STRIPPED)
                    put("referenceSourcePreservation", referenceSourceOriginalResult.preservation)
                    val storedMime = referenceSourceOriginalResult.storedMimeType
                    if (!storedMime.isNullOrBlank() && storedMime != referenceSourceOriginalResult.mimeType) {
                        put("referenceSourceStoredMimeType", storedMime)
                    }
                })
            }
            // branding block — only written when global branding was successfully copied.
            if (brandingData != null) {
                put("branding", buildBrandingJson(FILE_BRANDING_HANDLE, brandingData.meta, sessionTimestampMs))
            }
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
     * Strips EXIF, XMP, and IPTC metadata from [source] by byte-level JPEG segment manipulation
     * and writes the result to [dest].
     *
     * Segments removed: APP1 (EXIF and XMP), APP3–APP15 (including APP13/IPTC).
     * Segments kept: SOI, APP0 (JFIF), APP2 (ICC color profile only), all image data, EOI.
     *
     * After the SOS (start of scan) marker all remaining bytes (entropy-coded data through EOI)
     * are copied verbatim — no scan parsing is required.
     *
     * @return true on success; false if [source] does not appear to be a valid JPEG, in which
     *   case [dest] is deleted and the caller should fall back to decode→re-encode.
     */
    internal fun stripJpegMetadata(source: File, dest: File): Boolean {
        val ok = stripJpegMetadataInternal(source, dest)
        if (!ok) dest.delete()  // Ensure no partial or empty dest file is left on failure
        return ok
    }

    private fun stripJpegMetadataInternal(source: File, dest: File): Boolean {
        return try {
            source.inputStream().buffered(65536).use { input ->
                dest.outputStream().buffered(65536).use { output ->
                    // Verify SOI (FF D8)
                    if (input.read() != 0xFF || input.read() != 0xD8) return false
                    output.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

                    segmentLoop@ while (true) {
                        // Read next marker, skipping 0xFF padding bytes
                        var b = input.read()
                        if (b == -1) break
                        if (b != 0xFF) return false
                        while (b == 0xFF) {
                            b = input.read()
                            if (b == -1) break@segmentLoop
                        }
                        val marker = b

                        // EOI — write and finish
                        if (marker == 0xD9) {
                            output.write(0xFF); output.write(0xD9)
                            return true
                        }

                        // SOS — write marker, then copy all remaining bytes (scan data + EOI)
                        if (marker == 0xDA) {
                            output.write(0xFF); output.write(0xDA)
                            input.copyTo(output, bufferSize = 65536)
                            return true
                        }

                        // All other segments have a 2-byte big-endian length (includes itself)
                        val lh = input.read(); val ll = input.read()
                        if (lh == -1 || ll == -1) return false
                        val dataLen = ((lh shl 8) or ll) - 2  // excludes the 2 length bytes

                        when (marker) {
                            0xE0 -> {
                                // APP0 (JFIF) — always keep
                                writeJpegSegment(output, marker, lh, ll, input, dataLen)
                            }
                            0xE2 -> {
                                // APP2 — keep only if it is an ICC_PROFILE block
                                keepIccSegmentOrSkip(output, marker, lh, ll, input, dataLen)
                            }
                            in 0xE1..0xEF -> {
                                // APP1 (EXIF / XMP) and APP3–APP15 (incl. APP13 IPTC) — skip
                                if (dataLen > 0) skipJpegBytes(input, dataLen)
                            }
                            else -> {
                                // SOF, DHT, DQT, DRI, COM, RST, etc. — keep
                                writeJpegSegment(output, marker, lh, ll, input, dataLen)
                            }
                        }
                    }
                    true
                }
            }
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }

    /** Writes a JPEG segment (marker + length bytes + data) to [output]. */
    private fun writeJpegSegment(
        output: OutputStream, marker: Int, lh: Int, ll: Int,
        input: InputStream, dataLen: Int
    ) {
        output.write(0xFF); output.write(marker)
        output.write(lh); output.write(ll)
        if (dataLen > 0) copyJpegBytes(input, output, dataLen)
    }

    /**
     * Keeps the APP2 segment if it begins with "ICC_PROFILE", otherwise discards it.
     * The check is based on the first 11 bytes of segment data ("ICC_PROFILE" in US-ASCII).
     */
    private fun keepIccSegmentOrSkip(
        output: OutputStream, marker: Int, lh: Int, ll: Int,
        input: InputStream, dataLen: Int
    ) {
        val headerLen = minOf(11, dataLen)
        val header = if (headerLen > 0) readJpegBytes(input, headerLen) else ByteArray(0)
        val remaining = dataLen - header.size
        val isIcc = header.size >= 11 &&
            header[0] == 'I'.code.toByte() && header[1] == 'C'.code.toByte() &&
            header[2] == 'C'.code.toByte() && header[3] == '_'.code.toByte() &&
            header[4] == 'P'.code.toByte()
        if (isIcc) {
            output.write(0xFF); output.write(marker)
            output.write(lh); output.write(ll)
            output.write(header)
            if (remaining > 0) copyJpegBytes(input, output, remaining)
        } else {
            if (remaining > 0) skipJpegBytes(input, remaining)
        }
    }

    /** Copies exactly [count] bytes from [input] to [output]. Throws [IOException] on EOF. */
    private fun copyJpegBytes(input: InputStream, output: OutputStream, count: Int) {
        val buf = ByteArray(minOf(count, 65536))
        var remaining = count
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(remaining, buf.size))
            if (n == -1) throw IOException("Unexpected EOF copying $count JPEG bytes")
            output.write(buf, 0, n)
            remaining -= n
        }
    }

    /** Reads exactly [count] bytes from [input] into a new array. Throws [IOException] on EOF. */
    private fun readJpegBytes(input: InputStream, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(bytes, offset, count - offset)
            if (n == -1) throw IOException("Unexpected EOF reading $count JPEG bytes")
            offset += n
        }
        return bytes
    }

    /** Skips exactly [count] bytes from [input]. Throws [IOException] on EOF. */
    private fun skipJpegBytes(input: InputStream, count: Int) {
        val buf = ByteArray(minOf(count, 8192))
        var remaining = count
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(remaining, buf.size))
            if (n == -1) throw IOException("Unexpected EOF skipping $count JPEG bytes")
            remaining -= n
        }
    }

    /**
     * Decodes [source] as a JPEG, applies any EXIF orientation, and re-encodes to [dest]
     * at JPEG quality 95 without writing any EXIF or metadata tags.
     *
     * Used as the fallback path when byte-level stripping is not possible (malformed JPEG or
     * non-trivial EXIF orientation).
     */
    private fun reencodeJpegWithoutMetadata(source: File, dest: File) {
        var raw: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            val orientation = try {
                ExifInterface(source.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                )
            } catch (_: Exception) { ExifInterface.ORIENTATION_UNDEFINED }

            raw = BitmapFactory.decodeFile(source.absolutePath)
                ?: throw IOException("BitmapFactory could not decode ${source.name}")
            oriented = applyExifOrientation(raw, orientation)

            FileOutputStream(dest).use { stream ->
                if (!oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_PRIVACY, stream)) {
                    throw IOException("Bitmap.compress failed writing to ${dest.name}")
                }
            }
        } finally {
            if (oriented !== raw) oriented?.recycle()
            raw?.recycle()
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
     * Writes `capture-original.jpg` to [sessionDir].
     *
     * When [stripMetadata] is false: byte-for-byte copy of the committed MediaStore file.
     * When [stripMetadata] is true: copies then strips all EXIF/GPS/XMP metadata; falls back
     * to decode → rotate → JPEG-95 re-encode if byte-level strip is not possible.
     *
     * [MediaStore.setRequireOriginal] is never applied — [captureMediaStoreUri] is our own entry.
     *
     * @return [FILE_CAPTURE_ORIGINAL] (the fixed filename).
     * @throws IOException if the stream cannot be opened, copy fails, or strip/re-encode fails.
     */
    private fun writeCaptureOriginal(
        context: Context,
        captureMediaStoreUri: Uri,
        sessionDir: File,
        stripMetadata: Boolean
    ): String {
        val destFile = File(sessionDir, FILE_CAPTURE_ORIGINAL)
        if (!stripMetadata) {
            copyStreamToFile(context, captureMediaStoreUri, destFile)
        } else {
            writeCaptureOriginalStripped(context, captureMediaStoreUri, destFile)
        }
        return FILE_CAPTURE_ORIGINAL
    }

    /**
     * Privacy path for capture-original.jpg.
     *
     * Copies the MediaStore JPEG to a temp file, reads EXIF orientation, then:
     * - Trivial orientation (NORMAL / UNDEFINED): byte-level JPEG metadata strip.
     * - Non-trivial orientation OR strip failure: decode → apply rotation → JPEG-95 re-encode.
     *
     * The temp file is always deleted in the finally block. If this function throws, the caller's
     * try/catch in saveSession() will deleteRecursively() the session directory.
     */
    private fun writeCaptureOriginalStripped(
        context: Context,
        captureMediaStoreUri: Uri,
        destFile: File
    ) {
        val tempFile = File(destFile.parent!!, "${destFile.name}.tmp")
        try {
            copyStreamToFile(context, captureMediaStoreUri, tempFile)

            val orientation = try {
                ExifInterface(tempFile.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                )
            } catch (_: Exception) { ExifInterface.ORIENTATION_UNDEFINED }

            val isTrivialOrientation = orientation == ExifInterface.ORIENTATION_UNDEFINED ||
                orientation == ExifInterface.ORIENTATION_NORMAL

            val stripped = isTrivialOrientation && stripJpegMetadata(tempFile, destFile)
            if (!stripped) {
                destFile.delete()
                reencodeJpegWithoutMetadata(tempFile, destFile)
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Writes `reference-source-original.[ext]` to [sessionDir].
     *
     * When [stripMetadata] is false: byte-for-byte copy of the source URI stream (existing behavior).
     * When [stripMetadata] is true: decodes and re-encodes the source to remove all metadata.
     * Formats that cannot be decoded receive [PRESERVATION_NOT_POSSIBLE] and are stored as-is —
     * the session is still created successfully (best-effort privacy for these formats).
     *
     * @return [ReferenceSourceOriginalResult] with the actual filename, MIME types, and preservation mode.
     * @throws IOException if the source stream cannot be opened or a required decode/encode step fails.
     */
    private fun writeReferenceSourceOriginal(
        context: Context,
        referenceUri: Uri,
        sessionDir: File,
        stripMetadata: Boolean
    ): ReferenceSourceOriginalResult {
        val resolvedUri = resolveSourceUri(referenceUri)
        val mimeType = context.contentResolver.getType(resolvedUri)
            ?: context.contentResolver.getType(referenceUri)
        if (!stripMetadata) {
            val extension = resolveExtensionForMimeType(mimeType)
            val filename = "$FILE_REFERENCE_SOURCE_ORIGINAL_BASE$extension"
            copyStreamToFile(context, resolvedUri, File(sessionDir, filename))
            return ReferenceSourceOriginalResult(filename = filename, mimeType = mimeType)
        }
        return writeReferenceSourceOriginalStripped(context, resolvedUri, mimeType, sessionDir)
    }

    /**
     * Infers an image MIME type from the file extension of [uri]'s path when [ContentResolver.getType]
     * cannot determine the type (e.g., for `file://` URIs with no registered MIME mapping).
     * Returns null if the extension is not recognized as a supported image format.
     */
    private fun inferMimeTypeFromUri(uri: Uri): String? = when (
        uri.path?.substringAfterLast('.', "")?.lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        else -> null
    }

    /**
     * Privacy path for reference-source-original.
     *
     * Routes by MIME type to the appropriate decode → re-encode pipeline.
     * All decodeable formats go through a full decode cycle that eliminates metadata completely.
     * Formats that cannot be decoded (unknown MIME, AVIF on API < 31) are stored as-is with
     * [PRESERVATION_NOT_POSSIBLE]; the session is still saved successfully.
     *
     * When [ContentResolver.getType] returns null (common for `file://` URIs), the MIME type
     * is inferred from the URI path extension so that file:// sources are handled correctly.
     *
     * No byte-level stripping is attempted — the decode → re-encode cycle is the correct and
     * reliable metadata removal approach for reference sources.
     */
    private fun writeReferenceSourceOriginalStripped(
        context: Context,
        resolvedUri: Uri,
        mimeType: String?,
        sessionDir: File
    ): ReferenceSourceOriginalResult {
        val effectiveMimeType = mimeType ?: inferMimeTypeFromUri(resolvedUri)
        return writeReferenceSourceOriginalStrippedByMime(context, resolvedUri, mimeType, effectiveMimeType, sessionDir)
    }

    private fun writeReferenceSourceOriginalStrippedByMime(
        context: Context,
        resolvedUri: Uri,
        originalMimeType: String?,
        effectiveMimeType: String?,
        sessionDir: File
    ): ReferenceSourceOriginalResult = when (effectiveMimeType?.lowercase()) {
        "image/png" ->
            reencodeReferenceSourceAsPng(context, resolvedUri, originalMimeType, sessionDir)
        "image/jpeg", "image/heic", "image/heif", "image/webp" ->
            reencodeReferenceSourceAsJpeg(context, resolvedUri, originalMimeType, sessionDir, useImageDecoder = true)
        "image/gif", "image/bmp" ->
            reencodeReferenceSourceAsJpeg(context, resolvedUri, originalMimeType, sessionDir, useImageDecoder = false)
        "image/avif" ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                reencodeReferenceSourceAsJpeg(context, resolvedUri, originalMimeType, sessionDir, useImageDecoder = true)
            } else {
                // AVIF requires ImageDecoder (API 31+); not available on API 29–30
                copyReferenceSourceAsIs(context, resolvedUri, originalMimeType, sessionDir)
            }
        else -> copyReferenceSourceAsIs(context, resolvedUri, originalMimeType, sessionDir)
    }

    /**
     * Decodes the reference source and re-encodes as JPEG quality 95.
     *
     * [ImageDecoder] is preferred when [useImageDecoder] is true: it supports HEIC, HEIF, WebP,
     * and JPEG, and applies EXIF orientation automatically. [BitmapFactory] is used for GIF
     * (first frame) and BMP, which [ImageDecoder] may not support on all devices.
     *
     * The resulting file always has no EXIF, GPS, or other metadata.
     */
    private fun reencodeReferenceSourceAsJpeg(
        context: Context,
        resolvedUri: Uri,
        sourceMimeType: String?,
        sessionDir: File,
        useImageDecoder: Boolean
    ): ReferenceSourceOriginalResult {
        val destFile = File(sessionDir, "$FILE_REFERENCE_SOURCE_ORIGINAL_BASE.jpg")
        var bitmap: Bitmap? = null
        try {
            bitmap = if (useImageDecoder) {
                val source = ImageDecoder.createSource(context.contentResolver, resolvedUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // Software allocator required so the bitmap can be passed to compress()
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(resolvedUri)
                    ?.use { stream -> BitmapFactory.decodeStream(stream) }
                    ?: throw IOException("Cannot open stream for reference source: $resolvedUri")
            }
            FileOutputStream(destFile).use { out ->
                if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_PRIVACY, out)) {
                    throw IOException("Bitmap.compress to JPEG failed for reference source")
                }
            }
        } finally {
            bitmap?.recycle()
        }
        return ReferenceSourceOriginalResult(
            filename = destFile.name,
            mimeType = sourceMimeType,
            storedMimeType = "image/jpeg",
            preservation = PRESERVATION_METADATA_STRIPPED
        )
    }

    /**
     * Decodes the PNG reference source and re-encodes as PNG (lossless).
     * The decode → compress cycle removes all metadata (tEXt, eXIf chunks, etc.).
     * No chunk-level parsing is required.
     */
    private fun reencodeReferenceSourceAsPng(
        context: Context,
        resolvedUri: Uri,
        sourceMimeType: String?,
        sessionDir: File
    ): ReferenceSourceOriginalResult {
        val destFile = File(sessionDir, "$FILE_REFERENCE_SOURCE_ORIGINAL_BASE.png")
        var bitmap: Bitmap? = null
        try {
            bitmap = context.contentResolver.openInputStream(resolvedUri)
                ?.use { stream -> BitmapFactory.decodeStream(stream) }
                ?: throw IOException("Cannot open stream for PNG reference source: $resolvedUri")
            FileOutputStream(destFile).use { out ->
                // PNG quality parameter is ignored; compress() always produces lossless output
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 0, out)) {
                    throw IOException("Bitmap.compress to PNG failed for reference source")
                }
            }
        } finally {
            bitmap?.recycle()
        }
        return ReferenceSourceOriginalResult(
            filename = destFile.name,
            mimeType = sourceMimeType,
            storedMimeType = "image/png",
            preservation = PRESERVATION_METADATA_STRIPPED
        )
    }

    /**
     * Stores the reference source byte-for-byte without decoding when privacy stripping is not
     * possible (unknown MIME type, or AVIF on API < 31). The session is still created successfully.
     */
    private fun copyReferenceSourceAsIs(
        context: Context,
        resolvedUri: Uri,
        sourceMimeType: String?,
        sessionDir: File
    ): ReferenceSourceOriginalResult {
        val extension = resolveExtensionForMimeType(sourceMimeType)
        val filename = "$FILE_REFERENCE_SOURCE_ORIGINAL_BASE$extension"
        copyStreamToFile(context, resolvedUri, File(sessionDir, filename))
        return ReferenceSourceOriginalResult(
            filename = filename,
            mimeType = sourceMimeType,
            storedMimeType = null,
            preservation = PRESERVATION_NOT_POSSIBLE
        )
    }
}
