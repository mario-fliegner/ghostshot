// path: app/src/main/java/com/isardomains/sameview/branding/GlobalBrandingRepository.kt
package com.isardomains.sameview.branding

import com.isardomains.sameview.ui.camera.SessionBrandingMeta
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages the global branding template stored on-device under [brandingDir].
 *
 * Storage layout:
 *   [brandingDir]/handle.png          — normalized 512×512 RGBA PNG
 *   [brandingDir]/handle-meta.json    — type + builtinId (documentary only)
 *
 * The global branding is a template copied into new sessions at creation time
 * (Block 3). Existing sessions are never affected by global branding changes.
 * Session branding remains the single source of truth for all exports.
 *
 * **Atomicity contract (best-effort two-file):**
 * [setBranding] writes both files to temporary paths before replacing the finals.
 * If temp writes fail, existing finals are untouched. If a final rename fails, the
 * repository may be left in an inconsistent state, but [hasBranding] guards against
 * this by requiring both files to be present and the meta JSON to be parseable.
 * An inconsistent state is treated as "no branding" — this is acceptable because
 * global branding is only a template; no session data or export is affected.
 *
 * **Privacy:** only normalized PNG bytes and [SessionBrandingMeta] are stored.
 * No source URI, filename, EXIF, GPS, XMP, IPTC, or MakerNotes are written.
 *
 * **Threading:** [setBranding] and [removeBranding] are `suspend` but perform
 * **no internal dispatching**. The caller is responsible for dispatching to an IO
 * coroutine context before calling these functions (e.g. via an injectable
 * `ioDispatcher` in the ViewModel). Read functions ([hasBranding], [getBrandingFile],
 * [getBrandingMeta]) are synchronous and should also be called from an IO context.
 *
 * Hilt wiring is added in Block 4 via a module that provides:
 *   GlobalBrandingRepository(File(context.filesDir, "branding"))
 */
class GlobalBrandingRepository(val brandingDir: File) {

    private val handleFile: File get() = File(brandingDir, "handle.png")
    private val metaFile: File get() = File(brandingDir, "handle-meta.json")
    private val tmpHandleFile: File get() = File(brandingDir, "handle-new.png")
    private val tmpMetaFile: File get() = File(brandingDir, "handle-meta-new.json")

    /**
     * Returns true only when ALL three conditions hold:
     *   1. handle.png exists and is a file
     *   2. handle-meta.json exists and is a file
     *   3. handle-meta.json can be parsed into a valid [SessionBrandingMeta]
     *
     * Any inconsistent state (one file missing or meta unparseable) returns false,
     * which the repository treats as "no branding set."
     */
    fun hasBranding(): Boolean {
        if (!handleFile.isFile) return false
        if (!metaFile.isFile) return false
        return getBrandingMeta() != null
    }

    /**
     * Returns the [File] for handle.png if it exists, null otherwise.
     * Does not check meta consistency — use [hasBranding] for a full validity check.
     */
    fun getBrandingFile(): File? = handleFile.takeIf { it.isFile }

    /**
     * Parses and returns [SessionBrandingMeta] from handle-meta.json.
     * Returns null if the file does not exist, cannot be read, or contains
     * invalid JSON (including a missing or empty "type" field).
     */
    fun getBrandingMeta(): SessionBrandingMeta? {
        val file = metaFile.takeIf { it.isFile } ?: return null
        return try {
            val json = JSONObject(file.readText())
            val type = json.getString("type").takeIf { it.isNotEmpty() } ?: return null
            val builtinId = json.optString("builtinId", "").ifEmpty { null }
            SessionBrandingMeta(type = type, builtinId = builtinId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stores [normalizedPng] (a 512×512 RGBA PNG [ByteArray] produced by
     * [BrandingNormalizer]) as the new global branding, along with [type] and
     * optional [builtinId].
     *
     * Write strategy:
     * 1. Write [normalizedPng] to handle-new.png (temp)
     * 2. Write meta JSON to handle-meta-new.json (temp)
     * 3. If either write fails → delete both temps; existing finals are untouched; throw
     * 4. Rename handle-new.png → handle.png (atomically replaces existing on Linux)
     * 5. Rename handle-meta-new.json → handle-meta.json
     * 6. If a rename in step 4–5 fails → delete any remaining temps; throw.
     *    [hasBranding] will return false for an inconsistent final state.
     *
     * Threading: the caller is responsible for dispatching to an IO coroutine context.
     * This function does not dispatch internally.
     *
     * @throws IOException if writing or renaming fails.
     */
    suspend fun setBranding(normalizedPng: ByteArray, type: String, builtinId: String?) {
        brandingDir.mkdirs()
        val tmpPng = tmpHandleFile
        val tmpMeta = tmpMetaFile
        try {
            // Step 1 & 2: write both temp files first
            tmpPng.writeBytes(normalizedPng)
            val json = JSONObject().apply {
                put("type", type)
                if (builtinId != null) put("builtinId", builtinId)
            }
            tmpMeta.writeText(json.toString())

            // Step 3 & 4: move (rename) temp files to their final paths.
            // Files.move with REPLACE_EXISTING is atomic on Linux (Android) via rename(2)
            // and cross-platform safe on JVM (unlike File.renameTo which fails on Windows
            // when the destination already exists).
            Files.move(tmpPng.toPath(), handleFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            // tmpPng no longer exists after a successful move.
            Files.move(tmpMeta.toPath(), metaFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            // If the meta move throws, handle.png now contains the new PNG while meta
            // may be missing or stale. hasBranding() guards against this: it returns
            // false when meta is absent or unparseable.
        } catch (e: Exception) {
            // Best-effort cleanup of any remaining temp files.
            // delete() is a no-op if the file was already renamed or never created.
            tmpPng.delete()
            tmpMeta.delete()
            throw e
        }
    }

    /**
     * Removes all global branding files (handle.png and handle-meta.json).
     * After this call [hasBranding] returns false. No-op if neither file exists.
     *
     * Threading: the caller is responsible for dispatching to an IO coroutine context.
     */
    suspend fun removeBranding() {
        handleFile.delete()
        metaFile.delete()
    }

    /**
     * Returns a [GlobalBranding] snapshot if both files exist and meta is parseable,
     * or null when no consistent global branding is present.
     *
     * This is a convenience reader that atomically resolves file + meta in a single call,
     * avoiding three separate null-checked calls at each use site.
     * No new persistence logic — delegates to [getBrandingFile] and [getBrandingMeta].
     */
    fun getBranding(): GlobalBranding? {
        val file = getBrandingFile() ?: return null
        val meta = getBrandingMeta() ?: return null
        return GlobalBranding(file = file, meta = meta)
    }
}

/**
 * A resolved snapshot of the current global branding: the normalized PNG [file] and its
 * documentary [meta] (type + builtinId).
 *
 * Obtained via [GlobalBrandingRepository.getBranding]. Used as a value object at Session
 * creation time (Block 3) and by Edit Session "Copy from default branding" (Block 5).
 */
data class GlobalBranding(
    val file: File,
    val meta: SessionBrandingMeta
)
