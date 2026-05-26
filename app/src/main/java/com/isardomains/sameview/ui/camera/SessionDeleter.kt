package com.isardomains.sameview.ui.camera

import java.io.File
import java.io.IOException

internal object SessionDeleter {

    fun delete(sessionsRoot: File, sessionId: String): Boolean {
        return try {
            val target = resolveDirectSessionDir(sessionsRoot, sessionId) ?: return false
            if (!target.exists()) {
                return true
            }
            target.deleteRecursively()
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
}
