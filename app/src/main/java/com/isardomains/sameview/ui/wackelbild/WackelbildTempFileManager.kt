// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt
package com.isardomains.sameview.ui.wackelbild

import java.io.File
import java.util.UUID

/**
 * Disposable-cache lifecycle for the DeinWackelbild temp-file directory
 * (`DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §11 Correction L, Block 6 correction). Provides the
 * per-operation temp directory and deterministic candidate file handles that
 * `WackelbildPrintRenderer`'s pair-size retry loop writes to and overwrites in place, plus the
 * cleanup/sweep primitives that keep `cacheDir/wackelbild/` from accumulating leftovers.
 *
 * Block 6 scope is intentionally narrow: targeted operation-directory deletion and an
 * unconditional orphan sweep of the dedicated root. It does **not** implement active-operation
 * cancellation, `onCleared()` wiring, or success/cancel/final-error cleanup — those require a real
 * operation to exist and remain deferred to the block that introduces one. Test-only teardown
 * (deleting a test's own operation directory in `@After`) is not product code and is not part of
 * this class.
 */
open class WackelbildTempFileManager(private val cacheDir: File) {

    /** The dedicated root all disposable Wackelbild operation files live under. Nothing outside
     * this directory is ever read or deleted by this class. */
    private val wackelbildRoot: File = File(cacheDir, "wackelbild")

    /** Creates and returns `cacheDir/wackelbild/<operationId>/`. */
    fun createOperationDir(operationId: String = UUID.randomUUID().toString()): File {
        val dir = File(wackelbildRoot, operationId)
        dir.mkdirs()
        return dir
    }

    /** Deterministic candidate path for the Reference output — overwritten in place on each
     * pair-level retry attempt, never given a fresh per-attempt name. */
    fun referenceCandidateFile(operationDir: File): File = File(operationDir, "image_one.jpg")

    /** Deterministic candidate path for the Capture output — overwritten in place on each
     * pair-level retry attempt, never given a fresh per-attempt name. */
    fun captureCandidateFile(operationDir: File): File = File(operationDir, "image_two.jpg")

    /**
     * Recursively deletes [dir], but only when [dir] is a genuine direct child of the dedicated
     * `cacheDir/wackelbild/` root — never the root itself, never a sibling/outside path, never a
     * path that escapes the root via `..`/symlink traversal. Containment is checked against the
     * canonical (symlink-resolved, `..`-collapsed) filesystem path, not the literal path string.
     *
     * A missing [dir] is a safe no-op, so repeated calls are idempotent. Deletion is best-effort:
     * a single file that cannot be removed does not throw and does not abort deletion of its
     * siblings.
     */
    fun deleteOperationDir(dir: File) {
        if (!isContainedOperationDir(dir)) return
        if (!dir.exists()) return
        deleteRecursivelyBestEffort(dir)
    }

    /**
     * Deletes every direct child currently present under `cacheDir/wackelbild/`. Every such child
     * is orphaned by construction at the point this is called (Block 6 wiring: once, at fresh
     * [WackelbildViewModel] creation, before any operation directory exists for that instance) —
     * there is no age threshold and no exemption. An unknown/non-directory child is removed the
     * same as a proper operation directory, since nothing else is ever expected to live under this
     * dedicated root. A missing root is a safe no-op; the root itself is never deleted, only its
     * children.
     */
    open fun sweepStaleOperationDirs() {
        val children = wackelbildRoot.listFiles() ?: return
        for (child in children) {
            deleteOperationDir(child)
        }
    }

    /** True only when [dir]'s canonical path is a direct child of the canonical
     * `cacheDir/wackelbild/` root — i.e. `canonicalRoot/<name>` with no further nesting and no
     * `..`/symlink escape. Rejects the root itself and anything outside it. */
    private fun isContainedOperationDir(dir: File): Boolean {
        val canonicalRoot = wackelbildRoot.canonicalFile
        val canonicalDir = dir.canonicalFile
        return canonicalDir.parentFile == canonicalRoot
    }

    private fun deleteRecursivelyBestEffort(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursivelyBestEffort(it) }
        }
        file.delete()
    }
}
