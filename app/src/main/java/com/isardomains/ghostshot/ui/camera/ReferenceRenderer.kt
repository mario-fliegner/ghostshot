package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

object ReferenceRenderer {

    fun render(
        sourceBitmap: Bitmap,
        viewportWidth: Int,
        viewportHeight: Int,
        overlayScale: Float,
        overlayOffsetX: Float,
        overlayOffsetY: Float,
        displayMode: ReferenceImageDisplayMode,
    ): Bitmap {
        require(viewportWidth > 0) { "viewportWidth must be > 0, was $viewportWidth" }
        require(viewportHeight > 0) { "viewportHeight must be > 0, was $viewportHeight" }

        val iW = sourceBitmap.width.toFloat()
        val iH = sourceBitmap.height.toFloat()
        val vW = viewportWidth.toFloat()
        val vH = viewportHeight.toFloat()
        val matrix = Matrix()

        when (displayMode) {
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> {
                // Mirrors ContentScale.Crop + graphicsLayer in CompareReferenceImage.
                val fillScale = maxOf(vW / iW, vH / iH)
                val scaledW = iW * fillScale * overlayScale
                val scaledH = iH * fillScale * overlayScale
                val maxTX = maxOf(0f, (scaledW - vW) / 2f)
                val maxTY = maxOf(0f, (scaledH - vH) / 2f)
                val tX = (overlayOffsetX * vW).coerceIn(-maxTX, maxTX)
                val tY = (overlayOffsetY * vH).coerceIn(-maxTY, maxTY)
                val imgX = (vW - scaledW) / 2f + tX
                val imgY = (vH - scaledH) / 2f + tY
                matrix.setScale(fillScale * overlayScale, fillScale * overlayScale)
                matrix.postTranslate(imgX, imgY)
            }
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE -> {
                // Mirrors ContentScale.Fit + graphicsLayer in ReferenceImageOverlay (else branch).
                // No clamp: offset is applied as-is.
                val fitScale = minOf(vW / iW, vH / iH)
                val scaledW = iW * fitScale * overlayScale
                val scaledH = iH * fitScale * overlayScale
                val tX = overlayOffsetX * vW
                val tY = overlayOffsetY * vH
                val imgX = (vW - scaledW) / 2f + tX
                val imgY = (vH - scaledH) / 2f + tY
                matrix.setScale(fitScale * overlayScale, fitScale * overlayScale)
                matrix.postTranslate(imgX, imgY)
            }
        }

        val output = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)
        return output
    }
}
