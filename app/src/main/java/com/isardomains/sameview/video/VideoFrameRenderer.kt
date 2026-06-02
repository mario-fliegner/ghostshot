package com.isardomains.sameview.video

import android.graphics.Canvas

internal const val VIDEO_BACKGROUND_COLOR: Int = 0xFF17202F.toInt()

interface VideoFrameRenderer {
    fun renderFrame(frameIndex: Int, canvas: Canvas)
}
