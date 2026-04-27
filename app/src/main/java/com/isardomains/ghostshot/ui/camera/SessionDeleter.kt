package com.isardomains.ghostshot.ui.camera

import java.io.File
import java.io.IOException

internal object SessionDeleter {

    fun delete(sessionsRoot: File, sessionId: String): Boolean {
        val target = File(sessionsRoot, sessionId)
        return try {
            val rootCanonical = sessionsRoot.canonicalPath + File.separator
            val targetCanonical = target.canonicalPath
            if (!targetCanonical.startsWith(rootCanonical)) {
                return false
            }
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
}
