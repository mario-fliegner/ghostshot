// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/SessionScanner.kt
package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.File

data class ScannedSession(
    val sessionId: String,
    val timestamp: Long,
    val referenceFileUri: Uri,
    val captureFileUri: Uri
)

internal object SessionScanner {

    private const val TAG = "SessionScanner"
    private const val SESSIONS_DIR = "sessions"
    private const val METADATA_FILE = "metadata.json"
    private const val EXPECTED_VERSION = 1

    fun scan(context: Context): List<ScannedSession> {
        val sessionsRoot = File(context.filesDir, SESSIONS_DIR)
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
            Log.d(TAG, "Session $id: unexpected error — ${e.message}")
            null
        }
    }

    private fun validateUnsafe(sessionDir: File, id: String): ScannedSession? {
        val metadataFile = File(sessionDir, METADATA_FILE)
        if (!metadataFile.exists() || !metadataFile.isFile) {
            Log.d(TAG, "Session $id: metadata.json missing")
            return null
        }

        val json: JSONObject = try {
            JSONObject(metadataFile.readText())
        } catch (e: JSONException) {
            Log.d(TAG, "Session $id: metadata.json not valid JSON — ${e.message}")
            return null
        }

        val version: Int = try {
            json.getInt("version")
        } catch (e: JSONException) {
            Log.d(TAG, "Session $id: version field missing or not an Int")
            return null
        }
        if (version != EXPECTED_VERSION) {
            Log.d(TAG, "Session $id: unsupported version $version")
            return null
        }

        val timestamp: Long = try {
            json.getLong("sessionTimestampMs")
        } catch (e: JSONException) {
            Log.d(TAG, "Session $id: sessionTimestampMs field missing or not a Long")
            return null
        }
        if (timestamp <= 0L) {
            Log.d(TAG, "Session $id: sessionTimestampMs is <= 0")
            return null
        }

        val referenceFile: String = try {
            json.getString("referenceFile")
        } catch (e: JSONException) {
            Log.d(TAG, "Session $id: referenceFile field missing")
            return null
        }
        if (!isSafeFilename(referenceFile)) {
            Log.d(TAG, "Session $id: referenceFile is unsafe — $referenceFile")
            return null
        }

        val captureFile: String = try {
            json.getString("captureFile")
        } catch (e: JSONException) {
            Log.d(TAG, "Session $id: captureFile field missing")
            return null
        }
        if (!isSafeFilename(captureFile)) {
            Log.d(TAG, "Session $id: captureFile is unsafe — $captureFile")
            return null
        }

        val refFile = File(sessionDir, referenceFile)
        if (!refFile.exists() || !refFile.isFile) {
            Log.d(TAG, "Session $id: referenceFile $referenceFile not found on disk")
            return null
        }

        val capFile = File(sessionDir, captureFile)
        if (!capFile.exists() || !capFile.isFile) {
            Log.d(TAG, "Session $id: captureFile $captureFile not found on disk")
            return null
        }

        return ScannedSession(
            sessionId = id,
            timestamp = timestamp,
            referenceFileUri = Uri.fromFile(refFile),
            captureFileUri = Uri.fromFile(capFile)
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
