// path: app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt
package com.isardomains.sameview.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import com.isardomains.sameview.ui.camera.ReferenceRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Orchestrates the full Share Comparison Image export pipeline:
 *   decode → compute canvas → render → compress → write to MediaStore.
 *
 * All rendering is CPU-bound (Dispatchers.Default); the MediaStore write is I/O-bound
 * (Dispatchers.IO). No EXIF, GPS, XMP, or IPTC metadata is written to the output JPEG:
 * Bitmap.compress() produces only pixel data, and ExifInterface is never called on the result.
 *
 * Quality paths:
 *  - STANDARD: capture.jpg + reference.jpg, max 2048 px longest edge.
 *  - ORIGINAL:
 *      Capture side: capture-original.jpg decoded via ImageDecoder.setTargetSize (HQ),
 *        or capture.jpg when captureOriginalFile is null in config.
 *      Reference side: reference-original.jpg re-rendered via ReferenceRenderer.render()
 *        using overlay parameters from metadata.json, or reference.jpg on any fallback.
 */
class ShareImageRenderer {

    /**
     * Renders the comparison image and writes it to MediaStore.
     *
     * @param config All rendering parameters including style, quality, caption, and session dir.
     * @param contentResolver Required for MediaStore insert and commit.
     * @return The MediaStore URI of the committed image; usable directly in Intent.ACTION_SEND.
     * @throws IOException if session images cannot be decoded or MediaStore operations fail.
     */
    suspend fun render(config: ShareRenderConfig, contentResolver: ContentResolver): Uri {
        // --- Rendering phase (CPU-bound) ---
        val (canvasBitmap, displayName) = withContext(Dispatchers.Default) {
            val viewport = readSessionViewport(config.sessionDir)

            // HQ path: only for ORIGINAL quality with a declared captureOriginalFile.
            // STANDARD always uses null → computeCanvasDimensions applies 2048 px cap.
            val captureOriginalDims: Pair<Int, Int>? =
                if (config.quality == ShareQuality.ORIGINAL && config.captureOriginalFile != null) {
                    readExifOrientedDimensions(config.captureOriginalFile)
                } else null

            val dims = computeCanvasDimensions(
                viewport.first, viewport.second,
                config.quality, config.captionData, config.style,
                captureOriginalDims
            )

            val capFile = File(config.sessionDir, "capture.jpg")
            val refFile = File(config.sessionDir, "reference.jpg")

            // Style-aware bitmap target dimensions.
            // Slider:       bitmaps prepared at full comparison-area dimensions (compW × compH).
            // Side-by-side: bitmaps prepared at slot dimensions (compW/2 × compH).
            //               SideBySideRenderStrategy receives slot-sized bitmaps; Fit at scale=1.0
            //               fills the slot exactly — no letterboxing, no strategy-side HQ logic.
            val imgW = if (config.style == ShareComparisonStyle.SIDE_BY_SIDE) dims.compW / 2 else dims.compW
            val imgH = dims.compH

            var capBitmap: Bitmap? = null
            var refBitmap: Bitmap? = null
            var brandingBitmap: Bitmap? = null
            val canvas = Bitmap.createBitmap(dims.canvasW, dims.canvasH, Bitmap.Config.ARGB_8888)
            try {
                capBitmap = if (config.quality == ShareQuality.ORIGINAL && config.captureOriginalFile != null) {
                    if (config.style == ShareComparisonStyle.SIDE_BY_SIDE) {
                        // SbS HQ: center-crop capture-original to viewport ratio, scale to slot.
                        // capture-original may have a different native aspect ratio than the
                        // viewport (e.g., 3:4 camera sensor vs 9:16 preview). Center-crop avoids
                        // both letterboxing (Fit) and distortion (non-uniform setTargetSize).
                        prepareHqCaptureForSbs(config.captureOriginalFile, imgW, imgH)
                            ?: BitmapFactory.decodeFile(capFile.absolutePath)
                            ?: throw IOException("Cannot decode capture source in ${config.sessionDir.name}")
                    } else {
                        // Slider HQ: decode to full comparison-area dimensions.
                        decodeHqCapture(config.captureOriginalFile, imgW, imgH)
                            ?: BitmapFactory.decodeFile(capFile.absolutePath)
                            ?: throw IOException("Cannot decode capture source in ${config.sessionDir.name}")
                    }
                } else {
                    BitmapFactory.decodeFile(capFile.absolutePath)
                        ?: throw IOException("Cannot decode capture.jpg in ${config.sessionDir.name}")
                }

                // Reference bitmap: HQ re-render at imgW × imgH for ORIGINAL when overlay params
                // are readable. For SbS, imgW = compW/2 (slot width); the composition proof
                // from spec §5.3 holds since the slot has the same viewport aspect ratio.
                // Falls back to reference.jpg when overlay block is absent or unreadable.
                refBitmap = if (config.quality == ShareQuality.ORIGINAL) {
                    val overlayParams = readOverlayParams(config.sessionDir)
                    if (overlayParams != null) {
                        renderHqReference(config.sessionDir, imgW, imgH, overlayParams)
                    } else {
                        BitmapFactory.decodeFile(refFile.absolutePath)
                            ?: throw IOException("Cannot decode reference.jpg in ${config.sessionDir.name}")
                    }
                } else {
                    BitmapFactory.decodeFile(refFile.absolutePath)
                        ?: throw IOException("Cannot decode reference.jpg in ${config.sessionDir.name}")
                }

                // Branding bitmap: only for Slider style and when useBranding=true.
                // Loaded from session-local branding-handle.png — never from global branding.
                // On decode failure: null → standard handle rendered; no crash, no user error.
                brandingBitmap = if (config.style == ShareComparisonStyle.SLIDER && config.useBranding) {
                    val brandingFile = File(config.sessionDir, "branding-handle.png")
                    if (brandingFile.isFile) {
                        try { BitmapFactory.decodeFile(brandingFile.absolutePath) }
                        catch (_: Exception) { null }
                    } else null
                } else null

                when (config.style) {
                    ShareComparisonStyle.SLIDER ->
                        SliderRenderStrategy(dims, refBitmap, capBitmap, brandingBitmap)
                            .render(Canvas(canvas), config.captionData)
                    ShareComparisonStyle.SIDE_BY_SIDE ->
                        SideBySideRenderStrategy(dims, refBitmap, capBitmap)
                            .render(Canvas(canvas), config.captionData)
                }
            } finally {
                capBitmap?.recycle()
                refBitmap?.recycle()
                brandingBitmap?.recycle()
            }

            Pair(canvas, buildDisplayName(config.exportTimestamp, config.style))
        }

        // --- MediaStore write phase (I/O-bound) ---
        return try {
            withContext(Dispatchers.IO) {
                val writer = ShareMediaStoreWriter(contentResolver)
                val pending = writer.insertPending(displayName)
                try {
                    FileOutputStream(pending.pfd.fileDescriptor).use { out ->
                        canvasBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    writer.commit(pending)
                    pending.uri
                } catch (e: Exception) {
                    writer.abort(pending)
                    throw e
                }
            }
        } finally {
            canvasBitmap.recycle()
        }
    }

    // ── HQ source helpers ────────────────────────────────────────────────────

    /**
     * Returns true when a HQ capture source (capture-original.jpg) is declared in
     * metadata.json and exists on disk. Used by the ViewModel to populate
     * [ShareRenderConfig.captureOriginalFile].
     */
    internal fun hasHqCaptureSource(sessionDir: File): Boolean =
        resolveHqCaptureFile(sessionDir) != null

    /**
     * Returns the [File] for the HQ capture source when it is declared in metadata.json
     * (`files.captureOriginal`) and exists on disk. Returns null otherwise.
     */
    internal fun resolveHqCaptureFile(sessionDir: File): File? {
        return try {
            val metaFile = File(sessionDir, "metadata.json")
            if (!metaFile.exists()) return null
            val json = JSONObject(metaFile.readText())
            val filename = json.optJSONObject("files")
                ?.optString("captureOriginal", null)
                ?: return null
            val file = File(sessionDir, filename)
            if (file.isFile) file else null
        } catch (_: Exception) { null }
    }

    // ── HQ decode helpers ────────────────────────────────────────────────────

    /**
     * Decodes [file] (capture-original.jpg) to a [Bitmap] scaled to approximately
     * [targetW] × [targetH]. EXIF orientation is applied automatically by [ImageDecoder].
     *
     * Uses [ImageDecoder.setTargetSize] only when the source dimensions exceed the target
     * in at least one axis (downsampling). Never upscales via setTargetSize.
     *
     * Returns null on any failure; callers fall back to capture.jpg.
     */
    private fun decodeHqCapture(file: File, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcW = info.size.width
                val srcH = info.size.height
                // Downsample only; never upscale — upscaling wastes memory without quality gain.
                if (targetW < srcW || targetH < srcH) {
                    decoder.setTargetSize(targetW, targetH)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) { null }
    }

    /**
     * Prepares a HQ capture bitmap for Side-by-side at slot dimensions [slotW] × [slotH].
     *
     * The source [captureOriginalFile] may have a native aspect ratio that differs from the
     * slot's viewport-ratio (e.g., 3:4 camera sensor in a 9:16 portrait slot). To fill the
     * slot without letterboxing (Fit) and without distortion (non-uniform setTargetSize),
     * this function:
     *
     *  1. Reads the EXIF-oriented source dimensions.
     *  2. Computes the minimum natural-ratio decode size that still covers the slot.
     *     (scale = max(slotW/srcW, slotH/srcH); decode at fillScale × source dims)
     *  3. Decodes via ImageDecoder with downsampling; EXIF orientation applied automatically.
     *  4. Renders to a [slotW] × [slotH] Bitmap using Fill semantics (center-crop + scale),
     *     which produces the correct viewport-ratio crop with no distortion.
     *
     * Returns null on any failure; callers fall back to capture.jpg.
     */
    private fun prepareHqCaptureForSbs(captureOriginalFile: File, slotW: Int, slotH: Int): Bitmap? {
        return try {
            val (srcW, srcH) = readExifOrientedDimensions(captureOriginalFile) ?: return null

            // Minimum natural-ratio decode size that covers the slot in both dimensions.
            // Fill semantics require: decoded_w ≥ slotW OR decoded_h ≥ slotH (max scale).
            // fillScale = max(slotW/srcW, slotH/srcH) → decW = srcW × fillScale ≥ slotW.
            val fillScale = maxOf(slotW.toFloat() / srcW, slotH.toFloat() / srcH)
            val decW: Int
            val decH: Int
            if (fillScale >= 1f) {
                // Source smaller than slot in at least one dimension — decode at full size.
                decW = srcW; decH = srcH
            } else {
                // Source larger — decode at minimum fill-quality size.
                decW = (srcW * fillScale).toInt().coerceAtLeast(slotW)
                decH = (srcH * fillScale).toInt().coerceAtLeast(slotH)
            }

            // Decode at natural (source) aspect ratio. setTargetSize(decW, decH) is a uniform
            // scale when fillScale is uniform for both axes (which it is by construction here:
            // decW/srcW = decH/srcH = fillScale). EXIF orientation is applied automatically.
            val source = ImageDecoder.createSource(captureOriginalFile)
            val naturalBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                if (decW < info.size.width || decH < info.size.height) {
                    decoder.setTargetSize(decW, decH)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            // Render to slot via Fill (center-crop + scale). drawBitmapFill is the same
            // function used by SliderRenderStrategy, extracted as internal for reuse.
            val slotBitmap = Bitmap.createBitmap(slotW, slotH, Bitmap.Config.ARGB_8888)
            try {
                Canvas(slotBitmap).also { c ->
                    c.drawColor(COMPARISON_BG_COLOR)
                    drawBitmapFill(c, naturalBitmap, RectF(0f, 0f, slotW.toFloat(), slotH.toFloat()))
                }
            } finally {
                naturalBitmap.recycle()
            }
            slotBitmap
        } catch (_: Exception) { null }
    }

    /**
     * Decodes reference-original.jpg and re-renders it at [compW] × [compH] using
     * [ReferenceRenderer.render] with [overlayParams] from metadata.json.
     *
     * The overlay offsets stored in metadata.json are normalized viewport fractions
     * (multiplied by viewportWidth/Height in ReferenceRenderer). Passing the HQ
     * target dimensions as viewportWidth/viewportHeight preserves the composition
     * exactly at the new resolution — the same source pixel is visible at the same
     * relative position in both the original and HQ render.
     *
     * Falls back to reference.jpg on any failure (missing file, decode error, OOM).
     * reference.jpg failure propagates as IOException to abort the render entirely.
     */
    private fun renderHqReference(
        sessionDir: File,
        compW: Int,
        compH: Int,
        overlayParams: OverlayParams
    ): Bitmap {
        val refOrigFile = File(sessionDir, "reference-original.jpg")
        if (!refOrigFile.exists()) return decodeReferenceFallback(sessionDir)
        val refOrigBitmap = BitmapFactory.decodeFile(refOrigFile.absolutePath)
            ?: return decodeReferenceFallback(sessionDir)
        return try {
            ReferenceRenderer.render(
                sourceBitmap   = refOrigBitmap,
                viewportWidth  = compW,
                viewportHeight = compH,
                overlayScale   = overlayParams.scale,
                overlayOffsetX = overlayParams.offsetX,
                overlayOffsetY = overlayParams.offsetY,
                displayMode    = overlayParams.displayMode
            )
        } finally {
            refOrigBitmap.recycle()
        }
    }

    /** Decodes reference.jpg as a fallback. Throws [IOException] if the file cannot be decoded. */
    private fun decodeReferenceFallback(sessionDir: File): Bitmap =
        BitmapFactory.decodeFile(File(sessionDir, "reference.jpg").absolutePath)
            ?: throw IOException("Cannot decode reference source in ${sessionDir.name}")

    // ── Viewport helpers ─────────────────────────────────────────────────────

    /**
     * Reads viewport dimensions from metadata.json. Falls back to capture.jpg pixel dimensions
     * if metadata is unavailable or viewport fields are missing/zero.
     */
    internal fun readSessionViewport(sessionDir: File): Pair<Int, Int> {
        try {
            val metaFile = File(sessionDir, "metadata.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                val vp = json.optJSONObject("viewport")
                if (vp != null) {
                    val w = vp.optInt("width", 0)
                    val h = vp.optInt("height", 0)
                    if (w > 0 && h > 0) return Pair(w, h)
                }
            }
        } catch (_: Exception) { /* fall through to capture.jpg fallback */ }

        return readCaptureDimensions(sessionDir)
    }

    private fun readCaptureDimensions(sessionDir: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(sessionDir, "capture.jpg").absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) {
            Pair(opts.outWidth, opts.outHeight)
        } else {
            Pair(DEFAULT_VIEWPORT_W, DEFAULT_VIEWPORT_H)
        }
    }

    companion object {
        private const val JPEG_QUALITY = 92
        private const val DEFAULT_VIEWPORT_W = 1080
        private const val DEFAULT_VIEWPORT_H = 1920
    }
}
