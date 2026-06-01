package com.isardomains.sameview.storage

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports one or more compare sessions as a standard ZIP file.
 *
 * Each session is written as a subdirectory named after its session ID containing
 * all four required files: capture.jpg, reference.jpg, reference-original.jpg, metadata.json.
 * All files are copied byte-for-byte without any transformation.
 *
 * Export is all-or-nothing: any validation or IO failure aborts the entire operation
 * before writing begins. Cleanup of the destination is the caller's responsibility.
 *
 * Threading: performs blocking IO; call on an IO dispatcher.
 */
internal object SessionBackupExporter {

    private const val BUFFER_SIZE = 8192

    private val REQUIRED_FILES = listOf(
        "capture.jpg",
        "reference.jpg",
        "reference-original.jpg",
        "metadata.json"
    )

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
     * ZIP structure per session:
     * ```
     * <sessionId>/capture.jpg
     * <sessionId>/reference.jpg
     * <sessionId>/reference-original.jpg
     * <sessionId>/metadata.json
     * ```
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

        // Pre-validate all session IDs and verify all required files exist before writing anything.
        // This guarantees nothing is written to outputStream if any session is invalid.
        for (sessionId in sessionIds) {
            validateSessionId(sessionsRoot, sessionId)?.let { return it }

            val sessionDir = File(sessionsRoot, sessionId)
            if (!sessionDir.exists() || !sessionDir.isDirectory) {
                return BackupResult.Failure("Session directory not found: $sessionId")
            }
            for (filename in REQUIRED_FILES) {
                val file = File(sessionDir, filename)
                if (!file.exists() || !file.isFile) {
                    return BackupResult.Failure("Required file missing in session '$sessionId': $filename")
                }
            }
        }

        val zip = ZipOutputStream(outputStream)
        zip.setMethod(ZipOutputStream.DEFLATED)
        return try {
            for (sessionId in sessionIds) {
                val sessionDir = File(sessionsRoot, sessionId)
                for (filename in REQUIRED_FILES) {
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
