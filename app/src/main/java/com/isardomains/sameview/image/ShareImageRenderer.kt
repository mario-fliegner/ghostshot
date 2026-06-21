// path: app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt
package com.isardomains.sameview.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
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
            val dims = computeCanvasDimensions(viewport.first, viewport.second, config.quality, config.captionData)

            val refFile = File(config.sessionDir, "reference.jpg")
            val capFile = File(config.sessionDir, "capture.jpg")
            val refBitmap = BitmapFactory.decodeFile(refFile.absolutePath)
                ?: throw IOException("Cannot decode reference.jpg in ${config.sessionDir.name}")
            val capBitmap = BitmapFactory.decodeFile(capFile.absolutePath)
                ?: throw IOException("Cannot decode capture.jpg in ${config.sessionDir.name}")

            val canvas = Bitmap.createBitmap(dims.canvasW, dims.canvasH, Bitmap.Config.ARGB_8888)
            try {
                when (config.style) {
                    ShareComparisonStyle.SLIDER ->
                        SliderRenderStrategy(dims, refBitmap, capBitmap).render(Canvas(canvas), config.captionData)
                    ShareComparisonStyle.SIDE_BY_SIDE ->
                        SideBySideRenderStrategy(dims, refBitmap, capBitmap).render(Canvas(canvas), config.captionData)
                }
            } finally {
                refBitmap.recycle()
                capBitmap.recycle()
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
