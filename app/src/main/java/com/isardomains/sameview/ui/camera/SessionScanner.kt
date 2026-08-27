// path: app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt
package com.isardomains.sameview.ui.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import com.isardomains.sameview.BuildConfig
import org.json.JSONException
import org.json.JSONObject
import java.io.File

data class ScannedSession(
    val sessionId: String,
    val timestamp: Long,
    val referenceFileUri: Uri,
    val captureFileUri: Uri,
    val title: String? = null,
    val referenceDate: String? = null,
    val locationDisplayName: String? = null,
    val locationCity: String? = null,
    val locationCountry: String? = null,
    val locationCountryCode: String? = null,
    val isFavorite: Boolean = false,
    val branding: SessionBranding? = null
)

internal object SessionScanner {

    private const val TAG = "SessionScanner"
    private const val SESSIONS_DIR = "sessions"
    private const val METADATA_FILE = "metadata.json"
    private val SUPPORTED_VERSIONS = setOf(2, 3, 4, 5, 6)

    fun scan(context: Context): List<ScannedSession> = scan(File(context.filesDir, SESSIONS_DIR))

    internal fun scan(sessionsRoot: File): List<ScannedSession> {
        if (!sessionsRoot.exists() || !sessionsRoot.isDirectory) {
            return emptyList()
        }
        val entries = sessionsRoot.listFiles() ?: return emptyList()
        val result = mutableListOf<ScannedSession>()
        for (entry in entries) {
            if (!entry.isDirectory) continue
            val session = validate(entry)
            if (session != null) result.add(session)
        }
        return result.sortedWith(
            compareByDescending<ScannedSession> { it.timestamp }
                .thenByDescending { it.sessionId }
        )
    }

    private fun validate(sessionDir: File): ScannedSession? {
        val id = sessionDir.name
        return try {
            validateUnsafe(sessionDir, id)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: unexpected error — ${e.message}") }
            null
        }
    }

    private fun validateUnsafe(sessionDir: File, id: String): ScannedSession? {
        val metadataFile = File(sessionDir, METADATA_FILE)
        if (!metadataFile.exists() || !metadataFile.isFile) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: metadata.json missing") }
            return null
        }

        val json: JSONObject = try {
            JSONObject(metadataFile.readText())
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: metadata.json not valid JSON — ${e.message}") }
            return null
        }

        val version: Int = try {
            json.getInt("version")
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: version field missing or not an Int") }
            return null
        }
        if (version !in SUPPORTED_VERSIONS) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: unsupported version $version") }
            return null
        }

        val sessionObj: JSONObject = try {
            json.getJSONObject("session")
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: session block missing") }
            return null
        }

        // Prefer capture.timestampMs (v4 canonical); fall back to session.createdAtMs for v2/v3.
        val captureObj: JSONObject? = json.optJSONObject("capture")
        val captureTs = captureObj?.optLong("timestampMs", 0L) ?: 0L
        val timestamp: Long = if (captureTs > 0L) {
            captureTs
        } else {
            val fallbackTs = try {
                sessionObj.getLong("createdAtMs")
            } catch (e: JSONException) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: session.createdAtMs field missing or not a Long") }
                return null
            }
            if (fallbackTs <= 0L) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: session.createdAtMs is <= 0") }
                return null
            }
            fallbackTs
        }

        val filesObj: JSONObject = try {
            json.getJSONObject("files")
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files block missing") }
            return null
        }

        val referenceFile: String = try {
            filesObj.getString("reference")
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.reference field missing") }
            return null
        }
        if (!isSafeFilename(referenceFile)) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.reference is unsafe — $referenceFile") }
            return null
        }

        val captureFile: String = try {
            filesObj.getString("capture")
        } catch (e: JSONException) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.capture field missing") }
            return null
        }
        if (!isSafeFilename(captureFile)) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.capture is unsafe — $captureFile") }
            return null
        }

        val refFile = File(sessionDir, referenceFile)
        if (!refFile.exists() || !refFile.isFile) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.reference $referenceFile not found on disk") }
            return null
        }

        val capFile = File(sessionDir, captureFile)
        if (!capFile.exists() || !capFile.isFile) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.capture $captureFile not found on disk") }
            return null
        }

        // v5: additionally validate the two original files declared in the files block.
        // Versions 2, 3, and 4 are not affected by these checks.
        if (version == 5 || version == 6) {
            val captureOriginalFile = filesObj.optString("captureOriginal", "")
            if (captureOriginalFile.isEmpty()) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.captureOriginal missing or empty in v$version") }
                return null
            }
            if (!isSafeFilename(captureOriginalFile)) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.captureOriginal is unsafe — $captureOriginalFile") }
                return null
            }
            if (!File(sessionDir, captureOriginalFile).let { it.exists() && it.isFile }) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.captureOriginal $captureOriginalFile not found on disk") }
                return null
            }

            val referenceSourceOriginalFile = filesObj.optString("referenceSourceOriginal", "")
            if (referenceSourceOriginalFile.isEmpty()) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.referenceSourceOriginal missing or empty in v$version") }
                return null
            }
            if (!isSafeFilename(referenceSourceOriginalFile)) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.referenceSourceOriginal is unsafe — $referenceSourceOriginalFile") }
                return null
            }
            if (!File(sessionDir, referenceSourceOriginalFile).let { it.exists() && it.isFile }) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.referenceSourceOriginal $referenceSourceOriginalFile not found on disk") }
                return null
            }
        }

        // v6: validate optional files.brandingHandle when present.
        if (version == 6) {
            val brandingHandleFile = filesObj.optString("brandingHandle", "")
            if (brandingHandleFile.isNotEmpty()) {
                if (!isSafeFilename(brandingHandleFile)) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.brandingHandle is unsafe — $brandingHandleFile") }
                    return null
                }
                if (!File(sessionDir, brandingHandleFile).let { it.exists() && it.isFile }) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: files.brandingHandle $brandingHandleFile not found on disk") }
                    return null
                }
            }
        }

        val contentObj: JSONObject? = json.optJSONObject("content")
        val rawTitle = contentObj?.optString("title", "")?.trim() ?: ""
        val title = rawTitle.ifEmpty { null }

        val referenceDate = json.optJSONObject("reference")?.optString("date", "")?.takeIf { it.isNotEmpty() }

        val locationObj: JSONObject? = json.optJSONObject("location")
        val locationDisplayName = locationObj?.optString("displayName", "")?.takeIf { it.isNotEmpty() }
        val locationCity = locationObj?.optString("city", "")?.takeIf { it.isNotEmpty() }
        val locationCountry = locationObj?.optString("country", "")?.takeIf { it.isNotEmpty() }
        // Preserved exactly as read, valid or not — the display-time resolver (CountryCatalog),
        // not the scanner, decides validity (SESSION_METADATA_V1.md §6.9.8).
        val locationCountryCode = locationObj?.optString("countryCode", "")?.takeIf { it.isNotEmpty() }

        val additionalObj: JSONObject? = json.optJSONObject("additional")
        val isFavorite: Boolean = additionalObj?.optBoolean("isFavorite", false) ?: false

        // Read branding block (v6 sessions only; null for v2–v5).
        // Inconsistency (branding block present but files.brandingHandle absent, or vice versa)
        // is tolerated: treated as no branding without rejecting the session.
        val branding: SessionBranding? = if (version >= 6) {
            val brandingObj = json.optJSONObject("branding")
            val hasBrandingFile = filesObj.optString("brandingHandle", "").isNotEmpty()
            if (brandingObj != null && hasBrandingFile) {
                try {
                    val handleFile = brandingObj.getString("handleFile")
                    val type = brandingObj.getString("type")
                    val builtinId = brandingObj.optString("builtinId", "").ifEmpty { null }
                    val updatedAtMs = brandingObj.getLong("updatedAtMs")
                    SessionBranding(handleFile = handleFile, type = type, builtinId = builtinId, updatedAtMs = updatedAtMs)
                } catch (_: Exception) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: branding block malformed — treating as no branding") }
                    null
                }
            } else {
                if (brandingObj != null || hasBrandingFile) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Session $id: branding block/file inconsistency — treating as no branding") }
                }
                null
            }
        } else {
            null
        }

        return ScannedSession(
            sessionId = id,
            timestamp = timestamp,
            referenceFileUri = Uri.fromFile(refFile),
            captureFileUri = Uri.fromFile(capFile),
            title = title,
            referenceDate = referenceDate,
            locationDisplayName = locationDisplayName,
            locationCity = locationCity,
            locationCountry = locationCountry,
            locationCountryCode = locationCountryCode,
            isFavorite = isFavorite,
            branding = branding
        )
    }

    private fun isSafeFilename(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.contains('/') || name.contains('\\')) return false
        if (name.contains("..")) return false
        if (File(name).isAbsolute) return false
        return true
    }
}
