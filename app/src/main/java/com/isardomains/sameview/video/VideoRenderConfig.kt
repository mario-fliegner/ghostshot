package com.isardomains.sameview.video

enum class VideoMode {
    COMPARE_SLIDER,
    BEFORE_AFTER
}

enum class VideoExportFormat {
    ORIGINAL,
    PORTRAIT_9_16,
    LANDSCAPE_16_9
}

enum class VideoQuality {
    STANDARD_1080P,
    HIGH_QUALITY
}

data class VideoRenderConfig(
    val videoMode: VideoMode,
    val format: VideoExportFormat,
    val quality: VideoQuality,
    val durationMs: Int,
    val brandingEnabled: Boolean,
    val frameRate: Int = 30
) {
    val animationFrameCount: Int
        get() = if (brandingEnabled) {
            (durationMs - 1000) * frameRate / 1000
        } else {
            durationMs * frameRate / 1000
        }

    val totalFrameCount: Int
        get() = animationFrameCount + if (brandingEnabled) 30 else 0
}

fun computeCanvasDimensions(
    format: VideoExportFormat,
    quality: VideoQuality,
    viewportWidth: Int,
    viewportHeight: Int
): Pair<Int, Int> = when (quality) {
    VideoQuality.STANDARD_1080P -> when (format) {
        VideoExportFormat.PORTRAIT_9_16 -> Pair(1080, 1920)
        VideoExportFormat.LANDSCAPE_16_9 -> Pair(1920, 1080)
        VideoExportFormat.ORIGINAL -> scaleToLongestEdge(viewportWidth, viewportHeight, 1920)
    }
    VideoQuality.HIGH_QUALITY -> when (format) {
        VideoExportFormat.PORTRAIT_9_16 -> Pair(makeEven(2160), makeEven(3840))
        VideoExportFormat.LANDSCAPE_16_9 -> Pair(makeEven(3840), makeEven(2160))
        VideoExportFormat.ORIGINAL -> scaleToLongestEdge(viewportWidth, viewportHeight, 3840)
    }
}

private fun scaleToLongestEdge(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> =
    if (width >= height) {
        val w = makeEven(maxEdge)
        val h = makeEven((maxEdge.toLong() * height / width).toInt())
        Pair(w, h)
    } else {
        val h = makeEven(maxEdge)
        val w = makeEven((maxEdge.toLong() * width / height).toInt())
        Pair(w, h)
    }

internal fun makeEven(n: Int): Int = if (n % 2 == 0) n else n - 1
