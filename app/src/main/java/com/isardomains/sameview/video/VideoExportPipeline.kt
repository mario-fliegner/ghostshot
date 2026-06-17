package com.isardomains.sameview.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaFormat
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
 * Orchestrates the full video export: session images → rendered frames → encoded video → MP4 in MediaStore.
 *
 * The pipeline renders [VideoRenderConfig.animationFrameCount] animation frames followed by
 * [VideoRenderConfig.BRANDING_FRAME_COUNT] endcard frames when [VideoRenderConfig.brandingEnabled]
 * is true. When branding is disabled, only animation frames are rendered.
 *
 * Usage:
 *   val pipeline = VideoExportPipeline(context)
 *   val result = pipeline.run(config, sessionDir) { progress -> ... }
 */
class VideoExportPipeline(private val context: Context) {

    private val contentResolver = context.contentResolver
    private val writer = MediaStoreVideoWriter(contentResolver)

    suspend fun run(
        config: VideoRenderConfig,
        sessionDir: File,
        onProgress: (Float) -> Unit = {},
        onQualityFallback: suspend () -> Unit = {}
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
        var brandingRenderer: BrandingEndcardRenderer? = null
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
            val encoderParams = resolveEncoderParams(config.quality, config.format, vw, vh)
            val canvasW = encoderParams.width
            val canvasH = encoderParams.height

            // ── Phase 3: Frame bitmap + renderers ────────────────────────────────────
            frameBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap!!)
            val renderer = createRenderer(config, refBitmap!!, capBitmap!!)

            if (config.brandingEnabled) {
                brandingRenderer = BrandingEndcardRenderer(context, canvasW, canvasH)
            }

            val overlayRenderer: TitleDateOverlayRenderer? = config.overlay?.let { overlay ->
                TitleDateOverlayRenderer(canvasW, canvasH, overlay)
            }
            val holdFrameCount = computeHoldFrameCount(config)

            // ── Phase 4: MediaStore insert (IO) ──────────────────────────────────────
            val displayName = buildDisplayName(config, sessionDir)
            val entry = withContext(Dispatchers.IO) {
                writer.insertPending(displayName)
            }
            pendingUri = entry.uri
            pfd = entry.pfd

            // ── Phase 5: Encoding loop (Default / CPU-bound) ─────────────────────────
            withContext(Dispatchers.Default) {
                val animationFrames = config.animationFrameCount
                val endcardFrames = if (config.brandingEnabled) VideoRenderConfig.BRANDING_FRAME_COUNT else 0
                val totalFrames = config.totalFrameCount

                val encoder = VideoEncoder(
                    entry.pfd, canvasW, canvasH, config.frameRate,
                    encoderParams.bitRate, encoderParams.mimeType
                )
                try {
                    encoder.start()

                    // Animation frames
                    for (i in 0 until animationFrames) {
                        ensureActive()
                        renderer.renderFrame(i, frameCanvas)
                        if (overlayRenderer != null && i < holdFrameCount) {
                            overlayRenderer.renderOnCanvas(i, holdFrameCount, frameCanvas)
                        }
                        encoder.encodeFrame(frameBitmap!!)
                        onProgress(i.toFloat() / totalFrames)
                    }

                    // Branding endcard frames
                    val br = brandingRenderer
                    if (br != null) {
                        for (i in 0 until endcardFrames) {
                            ensureActive()
                            br.renderFrame(i, frameCanvas)
                            encoder.encodeFrame(frameBitmap!!)
                            onProgress((animationFrames + i).toFloat() / totalFrames)
                        }
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
            if (encoderParams.qualityFallbackApplied) onQualityFallback()
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
                brandingRenderer?.release()
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
        VideoMode.FLASH -> FlashRenderEngine(config, refBitmap, capBitmap)
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
            VideoMode.FLASH -> "flash"
        }
        return "SameView_${sessionId}_${modeSuffix}.mp4"
    }

    /**
     * Selects the codec MIME type, final canvas dimensions, and bitrate for the export.
     *
     * HIGH_QUALITY prefers HEVC; falls back silently to AVC if no ByteBuffer-capable HEVC
     * encoder is found. If the chosen encoder cannot handle the requested resolution
     * (typically 4K), dimensions are reduced to Standard 1080p and
     * [EncoderParams.qualityFallbackApplied] is set to true so the caller can notify the user.
     * STANDARD_1080P always uses AVC at standard dimensions.
     */
    private fun resolveEncoderParams(
        quality: VideoQuality,
        format: VideoExportFormat,
        vpW: Int,
        vpH: Int
    ): EncoderParams {
        val (requestedW, requestedH) = computeCanvasDimensions(format, quality, vpW, vpH)

        if (quality == VideoQuality.STANDARD_1080P) {
            return EncoderParams(requestedW, requestedH, MediaFormat.MIMETYPE_VIDEO_AVC, BITRATE_STANDARD_BPS, false)
        }

        // HIGH_QUALITY: prefer HEVC; silently use AVC if no ByteBuffer-capable HEVC encoder found.
        val mimeType = if (VideoEncoder.findHevcEncoder() != null) {
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC
        }

        // If the chosen encoder supports the requested resolution, proceed at HIGH_QUALITY bitrate.
        if (VideoEncoder.isResolutionSupported(mimeType, requestedW, requestedH)) {
            return EncoderParams(requestedW, requestedH, mimeType, BITRATE_HIGH_QUALITY_BPS, false)
        }

        // Resolution not supported: fall back to Standard 1080p dimensions and notify the caller.
        val (fallbackW, fallbackH) = computeCanvasDimensions(format, VideoQuality.STANDARD_1080P, vpW, vpH)
        return EncoderParams(fallbackW, fallbackH, mimeType, BITRATE_STANDARD_BPS, qualityFallbackApplied = true)
    }

    private fun computeHoldFrameCount(config: VideoRenderConfig): Int = when (config.videoMode) {
        VideoMode.COMPARE_SLIDER -> (config.animationFrameCount * 0.15f).toInt()
        VideoMode.BEFORE_AFTER -> {
            val crossfade = config.frameRate / 2
            (config.animationFrameCount - crossfade) / 2
        }
        VideoMode.FLASH -> FlashRenderEngine.FLASH_HOLD_FRAMES
    }

    companion object {
        private const val BITRATE_STANDARD_BPS = 7_000_000
        private const val BITRATE_HIGH_QUALITY_BPS = 20_000_000
    }
}

// File-level private: avoids nested-class DEX fragmentation in the androidTest APK build.
private data class EncoderParams(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val bitRate: Int,
    val qualityFallbackApplied: Boolean
)
