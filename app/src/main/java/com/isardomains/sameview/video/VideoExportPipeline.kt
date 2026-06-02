package com.isardomains.sameview.video

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Orchestrates the full video export: session images → rendered frames → H.264 → MP4 in MediaStore.
 *
 * Usage:
 *   val pipeline = VideoExportPipeline(context.contentResolver)
 *   val result = pipeline.run(config, sessionDir) { progress -> ... }
 *
 * Branding (config.brandingEnabled) is intentionally ignored in this block.
 * Only config.animationFrameCount frames are rendered. Branding is added in a later block.
 */
class VideoExportPipeline(private val contentResolver: ContentResolver) {

    private val writer = MediaStoreVideoWriter(contentResolver)

    suspend fun run(
        config: VideoRenderConfig,
        sessionDir: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> {
        // Check encoder availability before touching MediaStore.
        if (VideoEncoder.findAvcEncoder() == null) {
            return Result.failure(
                IOException("No H.264 encoder with YUV420 ByteBuffer support found on this device")
            )
        }

        var pendingUri: Uri? = null
        var pfd: ParcelFileDescriptor? = null
        var refBitmap: Bitmap? = null
        var capBitmap: Bitmap? = null
        var frameBitmap: Bitmap? = null
        var committed = false

        return try {
            // ── Phase 1: Decode session images ──────────────────────────────────────
            val (rBmp, cBmp) = withContext(Dispatchers.Default) {
                val r = BitmapFactory.decodeFile(File(sessionDir, "reference.jpg").absolutePath)
                    ?: throw IOException("Cannot decode reference.jpg in $sessionDir")
                val c = BitmapFactory.decodeFile(File(sessionDir, "capture.jpg").absolutePath)
                    ?: throw IOException("Cannot decode capture.jpg in $sessionDir")
                Pair(r, c)
            }
            refBitmap = rBmp
            capBitmap = cBmp

            // ── Phase 2: Canvas dimensions ───────────────────────────────────────────
            val (vw, vh) = readViewport(sessionDir, capBitmap!!)
            val (canvasW, canvasH) = computeCanvasDimensions(
                config.format, config.quality, vw, vh
            )

            // ── Phase 3: Frame bitmap + renderer ────────────────────────────────────
            frameBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap!!)
            val renderer = createRenderer(config, refBitmap!!, capBitmap!!)

            // ── Phase 4: MediaStore insert (IO) ──────────────────────────────────────
            val displayName = buildDisplayName(config, sessionDir)
            val entry = withContext(Dispatchers.IO) {
                writer.insertPending(displayName)
            }
            pendingUri = entry.uri
            pfd = entry.pfd

            // ── Phase 5: Encoding loop (Default / CPU-bound) ─────────────────────────
            withContext(Dispatchers.Default) {
                val totalFrames = config.animationFrameCount  // branding ignored in Block 2
                val encoder = VideoEncoder(entry.pfd, canvasW, canvasH, config.frameRate)
                try {
                    encoder.start()
                    for (i in 0 until totalFrames) {
                        ensureActive()
                        renderer.renderFrame(i, frameCanvas)
                        encoder.encodeFrame(frameBitmap!!)
                        onProgress(i.toFloat() / totalFrames)
                    }
                    encoder.finish()
                } finally {
                    encoder.release()
                }
            }

            // ── Phase 6: Commit (IO) ─────────────────────────────────────────────────
            withContext(Dispatchers.IO) {
                pfd!!.close()
                pfd = null
                writer.commit(pendingUri!!)
            }
            committed = true
            onProgress(1.0f)
            Result.success(pendingUri!!)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // Never swallow CancellationException.
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            // NonCancellable guarantees cleanup runs even if the coroutine was cancelled.
            withContext(NonCancellable) {
                runCatching { pfd?.close() }
                if (!committed) {
                    pendingUri?.let { uri ->
                        withContext(Dispatchers.IO) { writer.abort(uri) }
                    }
                }
                refBitmap?.recycle()
                capBitmap?.recycle()
                frameBitmap?.recycle()
            }
        }
    }

    private fun createRenderer(
        config: VideoRenderConfig,
        refBitmap: Bitmap,
        capBitmap: Bitmap
    ): VideoFrameRenderer = when (config.videoMode) {
        VideoMode.COMPARE_SLIDER -> CompareSliderRenderEngine(config, refBitmap, capBitmap)
        VideoMode.BEFORE_AFTER -> BeforeAfterRenderEngine(config, refBitmap, capBitmap)
    }

    /**
     * Reads viewport dimensions from metadata.json (key: viewport.width / viewport.height).
     * Falls back to the decoded capture bitmap's dimensions if metadata is absent or unreadable.
     */
    private fun readViewport(sessionDir: File, captureBitmap: Bitmap): Pair<Int, Int> {
        val metadataFile = File(sessionDir, "metadata.json")
        if (metadataFile.exists()) {
            try {
                val json = JSONObject(metadataFile.readText())
                val viewport = json.optJSONObject("viewport")
                val vw = viewport?.optInt("width", 0) ?: 0
                val vh = viewport?.optInt("height", 0) ?: 0
                if (vw > 0 && vh > 0) return Pair(vw, vh)
            } catch (_: Exception) {
                // Fall through to bitmap-based fallback.
            }
        }
        return Pair(captureBitmap.width, captureBitmap.height)
    }

    /**
     * Derives the MediaStore display name from sessionDir.name (the session ID)
     * and the video mode, as specified in §18.1.
     */
    private fun buildDisplayName(config: VideoRenderConfig, sessionDir: File): String {
        val sessionId = sessionDir.name
        val modeSuffix = when (config.videoMode) {
            VideoMode.COMPARE_SLIDER -> "compare_slider"
            VideoMode.BEFORE_AFTER -> "before_after"
        }
        return "SameView_${sessionId}_${modeSuffix}.mp4"
    }
}
