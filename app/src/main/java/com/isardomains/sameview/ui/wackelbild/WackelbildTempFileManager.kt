// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt
package com.isardomains.sameview.ui.wackelbild

import java.io.File
import java.util.UUID

/**
 * Block 5 creation-side surface only (`DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §11 Correction
 * L). Provides the per-operation temp directory and deterministic candidate file handles that
 * `WackelbildPrintRenderer`'s pair-size retry loop writes to and overwrites in place.
 *
 * Does **not** implement any cleanup lifecycle — sweep-on-screen-entry, cleanup on
 * upload-success/cancel/final-error, or `onCleared()` teardown are all Block 6 scope. Test-only
 * teardown (deleting a test's own operation directory in `@After`) is not product code and is not
 * part of this class.
 */
class WackelbildTempFileManager(private val cacheDir: File) {

    /** Creates and returns `cacheDir/wackelbild/<operationId>/`. */
    fun createOperationDir(operationId: String = UUID.randomUUID().toString()): File {
        val dir = File(cacheDir, "wackelbild/$operationId")
        dir.mkdirs()
        return dir
    }

    /** Deterministic candidate path for the Reference output — overwritten in place on each
     * pair-level retry attempt, never given a fresh per-attempt name. */
    fun referenceCandidateFile(operationDir: File): File = File(operationDir, "image_one.jpg")

    /** Deterministic candidate path for the Capture output — overwritten in place on each
     * pair-level retry attempt, never given a fresh per-attempt name. */
    fun captureCandidateFile(operationDir: File): File = File(operationDir, "image_two.jpg")
}
