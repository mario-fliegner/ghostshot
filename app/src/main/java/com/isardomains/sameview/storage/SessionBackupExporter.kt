package com.isardomains.sameview.storage

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports one or more compare sessions as a standard ZIP file.
 *
 * The file list per session is determined at export time from the `files.*` block in
 * `metadata.json` plus `metadata.json` itself, so v5 sessions with variable-extension
 * original files (e.g. `reference-source-original.heic`) are handled automatically.
 *
 * All files are copied byte-for-byte without any transformation.
 *
 * Export is all-or-nothing: any validation or IO failure aborts the entire operation
 * before writing begins. Cleanup of the destination is the caller's responsibility.
 *
 * Threading: performs blocking IO; call on an IO dispatcher.
 */
internal object SessionBackupExporter {

    private const val BUFFER_SIZE = 8192

    /**
     * Result of an [exportSessions] call.
     */
    sealed class BackupResult {
        /** All sessions were written successfully. */
        data class Success(val sessionCount: Int) : BackupResult()
        /** Export was aborted. [reason] is a non-user-facing diagnostic description. */
        data class Failure(val reason: String, val cause: Throwable? = null) : BackupResult()
    }

    /**
     * Writes all sessions identified by [sessionIds] as a ZIP into [outputStream].
     *
     * The file set per session is discovered from `metadata.json`'s `files.*` block
     * plus `metadata.json` itself. This handles sessions at any schema version, including
     * v5 sessions with variable-extension original files.
     *
     * All files are copied byte-for-byte. [outputStream] is left open on return;
     * the caller is responsible for closing it.
     *
     * @param sessionsRoot the root directory containing session subdirectories
     * @param sessionIds the IDs to export; must not be empty
     * @param outputStream destination stream; caller owns its lifecycle
     * @return [BackupResult.Success] if all sessions were written, [BackupResult.Failure] otherwise
     */
    fun exportSessions(
        sessionsRoot: File,
        sessionIds: List<String>,
        outputStream: OutputStream
    ): BackupResult {
        if (sessionIds.isEmpty()) {
            return BackupResult.Failure("Session list must not be empty")
        }

        // Pre-validate all session IDs and collect per-session file lists before writing anything.
        // This guarantees nothing is written to outputStream if any session is invalid.
        val sessionFileLists = mutableMapOf<String, List<String>>()
        for (sessionId in sessionIds) {
            validateSessionId(sessionsRoot, sessionId)?.let { return it }

            val sessionDir = File(sessionsRoot, sessionId)
            if (!sessionDir.exists() || !sessionDir.isDirectory) {
                return BackupResult.Failure("Session directory not found: $sessionId")
            }

            val fileList = try {
                collectSessionFiles(sessionDir)
            } catch (e: IOException) {
                return BackupResult.Failure("Cannot read session manifest for '$sessionId': ${e.message}", e)
            }

            for (filename in fileList) {
                val file = File(sessionDir, filename)
                if (!file.exists() || !file.isFile) {
                    return BackupResult.Failure("Required file missing in session '$sessionId': $filename")
                }
            }
            sessionFileLists[sessionId] = fileList
        }

        val zip = ZipOutputStream(outputStream)
        zip.setMethod(ZipOutputStream.DEFLATED)
        return try {
            for (sessionId in sessionIds) {
                val sessionDir = File(sessionsRoot, sessionId)
                val fileList = sessionFileLists.getValue(sessionId)
                for (filename in fileList) {
                    val sourceFile = File(sessionDir, filename)
                    zip.putNextEntry(ZipEntry("$sessionId/$filename"))
                    FileInputStream(sourceFile).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            zip.write(buffer, 0, bytesRead)
                        }
                    }
                    zip.closeEntry()
                }
            }
            zip.finish()
            BackupResult.Success(sessionIds.size)
        } catch (e: IOException) {
            BackupResult.Failure("IO error during export: ${e.message}", e)
        } catch (e: Exception) {
            BackupResult.Failure("Unexpected error during export: ${e.message}", e)
        }
    }

    /**
     * Reads `metadata.json` from [sessionDir] and returns the list of files to export:
     * all filenames declared as String values in the `files.*` block, followed by
     * `"metadata.json"`. The list is deduplicated while preserving order.
     *
     * @throws IOException if `metadata.json` is missing, not parseable, lacks a `files` block,
     *   or contains a value that is empty or fails the safe-filename check.
     */
    internal fun collectSessionFiles(sessionDir: File): List<String> {
        val metadataFile = File(sessionDir, "metadata.json")
        if (!metadataFile.exists() || !metadataFile.isFile) {
            throw IOException("metadata.json missing in ${sessionDir.name}")
        }
        val json = try {
            JSONObject(metadataFile.readText())
        } catch (e: JSONException) {
            throw IOException("metadata.json is not valid JSON in ${sessionDir.name}: ${e.message}", e)
        }
        val filesObj = try {
            json.getJSONObject("files")
        } catch (e: JSONException) {
            throw IOException("metadata.json has no 'files' block in ${sessionDir.name}")
        }
        val seen = linkedSetOf<String>()
        val keys = filesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = filesObj.optString(key, "")
            if (value.isEmpty()) {
                throw IOException("files.$key is empty in session ${sessionDir.name}")
            }
            if (!isSafeFilename(value)) {
                throw IOException("files.$key has unsafe filename '$value' in session ${sessionDir.name}")
            }
            seen.add(value)
        }
        seen.add("metadata.json")
        return seen.toList()
    }

    private fun isSafeFilename(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.contains('/') || name.contains('\\')) return false
        if (name.contains("..")) return false
        if (File(name).isAbsolute) return false
        return true
    }

    /**
     * Validates that [sessionId] is a safe, direct child name of [sessionsRoot].
     *
     * Rejects empty strings, "." and "..", path separators, absolute paths,
     * and any ID that resolves outside [sessionsRoot] via canonical path resolution.
     *
     * @return a [BackupResult.Failure] if the ID is invalid, null if it is safe
     */
    private fun validateSessionId(sessionsRoot: File, sessionId: String): BackupResult.Failure? {
        if (sessionId.isEmpty()) {
            return BackupResult.Failure("Session ID must not be empty")
        }
        if (sessionId == "." || sessionId == "..") {
            return BackupResult.Failure("Session ID must not be '.' or '..'")
        }
        if (sessionId.contains('/') || sessionId.contains('\\')) {
            return BackupResult.Failure("Session ID must not contain path separators: $sessionId")
        }
        if (File(sessionId).isAbsolute) {
            return BackupResult.Failure("Session ID must not be an absolute path: $sessionId")
        }

        val rootCanonical = try {
            sessionsRoot.canonicalFile
        } catch (e: IOException) {
            return BackupResult.Failure("Cannot resolve sessions root: ${e.message}", e)
        }
        val targetCanonical = try {
            File(sessionsRoot, sessionId).canonicalFile
        } catch (e: IOException) {
            return BackupResult.Failure("Cannot resolve session directory for '$sessionId': ${e.message}", e)
        }
        val parentCanonical = targetCanonical.parentFile?.canonicalFile
            ?: return BackupResult.Failure("Cannot determine parent directory for session ID: $sessionId")

        if (parentCanonical.path != rootCanonical.path) {
            return BackupResult.Failure("Session ID resolves outside sessions root: $sessionId")
        }
        if (targetCanonical.name != sessionId) {
            return BackupResult.Failure("Session ID canonical name mismatch: $sessionId")
        }
        return null
    }
}
