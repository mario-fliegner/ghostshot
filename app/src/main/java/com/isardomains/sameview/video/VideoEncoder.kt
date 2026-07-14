package com.isardomains.sameview.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Encodes Bitmap frames to H.264/AVC or H.265/HEVC using MediaCodec in ByteBuffer input mode.
 *
 * Before constructing, call [findAvcEncoder] (or [findHevcEncoder] for HEVC) to verify a
 * suitable encoder exists. Use [isResolutionSupported] to confirm the target resolution is
 * within the encoder's capability before inserting a MediaStore entry.
 *
 * Lifecycle: start() → encodeFrame() × N → finish() → (always) release()
 * The caller must guarantee release() runs even on exception (use try/finally).
 */
internal class VideoEncoder(
    pfd: ParcelFileDescriptor,
    private val width: Int,
    private val height: Int,
    private val frameRateFps: Int = 30,
    private val bitRateBps: Int = 7_000_000,
    private val codecMimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
) {
    private val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val encoderName: String
    private val colorFormat: Int

    private var codec: MediaCodec? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0
    private var codecReleased = false
    private var muxerReleased = false

    // Pre-allocated in start(); reused across all frames.
    private var argbPixels = IntArray(0)
    private var yuvByteCount = 0

    init {
        val info = when (codecMimeType) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> findHevcEncoder()
            else -> findAvcEncoder()
        } ?: throw IOException(
            "No encoder with YUV420 ByteBuffer support found for $codecMimeType"
        )
        encoderName = info.first
        colorFormat = info.second
    }

    fun start() {
        val pixelCount = width * height
        argbPixels = IntArray(width * minOf(BAND_ROW_COUNT, height))
        yuvByteCount = pixelCount * 3 / 2

        val format = MediaFormat.createVideoFormat(codecMimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRateFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        codec = MediaCodec.createByCodecName(encoderName).also { c ->
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
        }
    }

    fun encodeFrame(bitmap: Bitmap) {
        val c = codec ?: return

        val pts = frameIndex++ * 1_000_000L / frameRateFps

        val inputIdx = c.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIdx >= 0) {
            val buf = c.getInputBuffer(inputIdx)!!
            buf.clear()
            if (buf.remaining() < yuvByteCount) {
                c.releaseOutputBuffer(inputIdx, false)
                throw IOException(
                    "Encoder input buffer too small: ${buf.remaining()} < $yuvByteCount"
                )
            }

            var bandStartRow = 0
            while (bandStartRow < height) {
                val bandRowCount = minOf(BAND_ROW_COUNT, height - bandStartRow)
                bitmap.getPixels(argbPixels, 0, width, 0, bandStartRow, width, bandRowCount)

                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    argbToNv12(argbPixels, buf, width, height, bandStartRow, bandRowCount)
                } else {
                    argbToI420(argbPixels, buf, width, height, bandStartRow, bandRowCount)
                }

                bandStartRow += bandRowCount
            }

            c.queueInputBuffer(inputIdx, 0, yuvByteCount, pts, 0)
        }

        drainOutput(drainUntilEos = false)
    }

    fun finish() {
        val c = codec ?: return

        // Signal end-of-stream via a zero-size input buffer.
        var eosQueued = false
        repeat(EOS_RETRY_LIMIT) {
            if (!eosQueued) {
                val idx = c.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (idx >= 0) {
                    c.queueInputBuffer(
                        idx, 0, 0,
                        frameIndex * 1_000_000L / frameRateFps,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eosQueued = true
                }
            }
        }

        drainOutput(drainUntilEos = true)
        releaseCodec()

        if (muxerStarted) {
            runCatching { muxer.stop() }
        }
        runCatching { muxer.release() }
        muxerReleased = true
    }

    fun release() {
        releaseCodec()
        if (!muxerReleased) {
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            muxerReleased = true
        }
    }

    private fun releaseCodec() {
        if (codecReleased) return
        codecReleased = true
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    private fun drainOutput(drainUntilEos: Boolean) {
        val c = codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var iterations = if (drainUntilEos) DRAIN_EOS_MAX_ITERATIONS else DRAIN_NORMAL_MAX_ITERATIONS

        while (iterations-- > 0) {
            val outputIdx = c.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!drainUntilEos) return
                }
                outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(c.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputIdx >= 0 -> {
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isCodecConfig && muxerStarted && bufferInfo.size > 0) {
                        muxer.writeSampleData(
                            videoTrackIndex,
                            c.getOutputBuffer(outputIdx)!!,
                            bufferInfo
                        )
                    }
                    c.releaseOutputBuffer(outputIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_NORMAL_MAX_ITERATIONS = 50
        private const val DRAIN_EOS_MAX_ITERATIONS = 2_000
        private const val EOS_RETRY_LIMIT = 200
        private const val BAND_ROW_COUNT = 128

        private val PREFERRED_COLOR_FORMATS = intArrayOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, // NV12
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar      // I420
        )

        /**
         * Scans all registered AVC encoders for one that supports a YUV420 ByteBuffer
         * color format. NV12 is preferred; I420 is the fallback.
         *
         * Returns (encoderName, colorFormat), or null if no suitable encoder is found.
         */
        internal fun findAvcEncoder(): Pair<String, Int>? {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (preferredFormat in PREFERRED_COLOR_FORMATS) {
                for (info in list.codecInfos) {
                    if (!info.isEncoder) continue
                    if (MediaFormat.MIMETYPE_VIDEO_AVC !in info.supportedTypes) continue
                    val caps = try {
                        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                    if (preferredFormat in caps.colorFormats) {
                        return Pair(info.name, preferredFormat)
                    }
                }
            }
            return null
        }

        /**
         * Scans all registered HEVC encoders for one that supports a YUV420 ByteBuffer
         * color format. NV12 is preferred; I420 is the fallback.
         *
         * Returns (encoderName, colorFormat), or null if no HEVC encoder with ByteBuffer
         * YUV420 support is found on this device.
         */
        internal fun findHevcEncoder(): Pair<String, Int>? {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (preferredFormat in PREFERRED_COLOR_FORMATS) {
                for (info in list.codecInfos) {
                    if (!info.isEncoder) continue
                    if (MediaFormat.MIMETYPE_VIDEO_HEVC !in info.supportedTypes) continue
                    val caps = try {
                        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                    if (preferredFormat in caps.colorFormats) {
                        return Pair(info.name, preferredFormat)
                    }
                }
            }
            return null
        }

        /**
         * Returns true if any registered encoder for [mimeType] reports that it supports
         * [width] × [height] via [MediaCodecInfo.VideoCapabilities.isSizeSupported].
         */
        internal fun isResolutionSupported(mimeType: String, width: Int, height: Int): Boolean {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                if (mimeType !in info.supportedTypes) continue
                val caps = try {
                    info.getCapabilitiesForType(mimeType)
                } catch (_: IllegalArgumentException) {
                    continue
                }
                val videoCaps = caps.videoCapabilities ?: continue
                if (videoCaps.isSizeSupported(width, height)) return true
            }
            return false
        }

        // ARGB → NV12 (YUV420SemiPlanar): Y plane, then interleaved UV plane (U first).
        // src holds only the current band (bandRowCount rows starting at bandStartRow);
        // h is the full frame height, needed to compute the total Y-plane size.
        private fun argbToNv12(
            src: IntArray,
            dst: ByteBuffer,
            w: Int,
            h: Int,
            bandStartRow: Int,
            bandRowCount: Int
        ) {
            val ySize = w * h
            for (localRow in 0 until bandRowCount) {
                val globalRow = bandStartRow + localRow
                for (col in 0 until w) {
                    val p = src[localRow * w + col]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    dst.put(
                        globalRow * w + col,
                        (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
                    )
                }
            }
            val blocksPerRowPair = w / 2
            for (localRow in 0 until bandRowCount step 2) {
                val globalRow = bandStartRow + localRow
                for (col in 0 until w step 2) {
                    val (u, v) = chromaAvg(src, w, bandRowCount, localRow, col)
                    val blockIndex = (globalRow / 2) * blocksPerRowPair + (col / 2)
                    val uvOffset = ySize + blockIndex * 2
                    dst.put(uvOffset, u)
                    dst.put(uvOffset + 1, v)
                }
            }
        }

        // ARGB → I420 (YUV420Planar): Y plane, then U plane, then V plane.
        // src holds only the current band (bandRowCount rows starting at bandStartRow);
        // h is the full frame height, needed to compute the total Y-plane and U/V-plane sizes.
        private fun argbToI420(
            src: IntArray,
            dst: ByteBuffer,
            w: Int,
            h: Int,
            bandStartRow: Int,
            bandRowCount: Int
        ) {
            val ySize = w * h
            val uvSize = ySize / 4
            for (localRow in 0 until bandRowCount) {
                val globalRow = bandStartRow + localRow
                for (col in 0 until w) {
                    val p = src[localRow * w + col]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    dst.put(
                        globalRow * w + col,
                        (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
                    )
                }
            }
            val blocksPerRowPair = w / 2
            for (localRow in 0 until bandRowCount step 2) {
                val globalRow = bandStartRow + localRow
                for (col in 0 until w step 2) {
                    val (u, v) = chromaAvg(src, w, bandRowCount, localRow, col)
                    val blockIndex = (globalRow / 2) * blocksPerRowPair + (col / 2)
                    dst.put(ySize + blockIndex, u)
                    dst.put(ySize + uvSize + blockIndex, v)
                }
            }
        }

        /**
         * Computes the average U and V values for a 2×2 pixel block.
         * Since both canvas dimensions are always even (guaranteed by makeEven), the
         * out-of-bounds checks are hit only at the theoretical edge; they cost one
         * comparison per pixel and keep the code safe for all canvas sizes.
         */
        private fun chromaAvg(src: IntArray, w: Int, h: Int, row: Int, col: Int): Pair<Byte, Byte> {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (dr in 0..1) {
                for (dc in 0..1) {
                    val r2 = row + dr
                    val c2 = col + dc
                    if (r2 < h && c2 < w) {
                        val p = src[r2 * w + c2]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                }
            }
            val r = sumR / count; val g = sumG / count; val b = sumB / count
            val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
            val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
            return Pair(u, v)
        }
    }
}
