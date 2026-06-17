package com.isardomains.sameview.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRenderConfigTest {

    // T-U-09 — branding ON: totalFrameCount = animationFrameCount + 45 (1.5 s endcard at 30 FPS)

    @Test fun totalFrameCount_brandingOn_4s_animationPlusFortyFive() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 4000,
            brandingEnabled = true
        )
        // New branding model: animation = 4000 * 30 / 1000 = 120 frames; endcard: 45; total: 165.
        assertEquals(config.animationFrameCount + VideoRenderConfig.BRANDING_FRAME_COUNT, config.totalFrameCount)
        assertEquals(120 + 45, config.totalFrameCount)
    }

    @Test fun totalFrameCount_brandingOn_6s_animationPlusFortyFive() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.BEFORE_AFTER,
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 6000,
            brandingEnabled = true
        )
        // New branding model: animation = 6000 * 30 / 1000 = 180 frames; endcard: 45; total: 225.
        assertEquals(config.animationFrameCount + VideoRenderConfig.BRANDING_FRAME_COUNT, config.totalFrameCount)
        assertEquals(180 + 45, config.totalFrameCount)
    }

    @Test fun totalFrameCount_brandingOn_8s_animationPlusFortyFive() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.HIGH_QUALITY,
            durationMs = 8000,
            brandingEnabled = true
        )
        // New branding model: animation = 8000 * 30 / 1000 = 240 frames; endcard: 45; total: 285.
        assertEquals(config.animationFrameCount + VideoRenderConfig.BRANDING_FRAME_COUNT, config.totalFrameCount)
        assertEquals(240 + 45, config.totalFrameCount)
    }

    // T-U-10 — branding OFF: totalFrameCount = animationFrameCount

    @Test fun totalFrameCount_brandingOff_4s_equalsAnimationFrames() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 4000,
            brandingEnabled = false
        )
        assertEquals(config.animationFrameCount, config.totalFrameCount)
        assertEquals(120, config.totalFrameCount)
    }

    @Test fun totalFrameCount_brandingOff_6s_equalsAnimationFrames() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.BEFORE_AFTER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 6000,
            brandingEnabled = false
        )
        assertEquals(config.animationFrameCount, config.totalFrameCount)
        assertEquals(180, config.totalFrameCount)
    }

    @Test fun totalFrameCount_brandingOff_8s_equalsAnimationFrames() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 8000,
            brandingEnabled = false
        )
        assertEquals(config.animationFrameCount, config.totalFrameCount)
        assertEquals(240, config.totalFrameCount)
    }

    // T-U-11 — High Quality + Original: dimensions derived from viewport, longest edge = 3840

    @Test fun computeCanvasDimensions_highQuality_original_portraitViewport() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.HIGH_QUALITY,
            viewportWidth = 1080,
            viewportHeight = 1920
        )
        assertEquals(2160, w)
        assertEquals(3840, h)
    }

    @Test fun computeCanvasDimensions_highQuality_original_landscapeViewport() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.HIGH_QUALITY,
            viewportWidth = 1920,
            viewportHeight = 1080
        )
        assertEquals(3840, w)
        assertEquals(2160, h)
    }

    @Test fun computeCanvasDimensions_standard_original_portraitViewport_longestEdge1920() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1080,
            viewportHeight = 1920
        )
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    // T-U-12 — Standard + Portrait 9:16: exactly 1080 × 1920

    @Test fun computeCanvasDimensions_standard_portrait_is1080x1920() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1080,
            viewportHeight = 1920
        )
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    // T-U-13 — Standard + Landscape 16:9: exactly 1920 × 1080

    @Test fun computeCanvasDimensions_standard_landscape_is1920x1080() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1920,
            viewportHeight = 1080
        )
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    // T-U-14 — All canvas dimensions must be even numbers

    @Test fun computeCanvasDimensions_standard_original_oddViewport_widthIsEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1001,
            viewportHeight = 1800
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
    }

    @Test fun computeCanvasDimensions_highQuality_original_oddViewport_bothEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.HIGH_QUALITY,
            viewportWidth = 999,
            viewportHeight = 1777
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
    }

    @Test fun computeCanvasDimensions_highQuality_portrait_isEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.HIGH_QUALITY,
            viewportWidth = 1080,
            viewportHeight = 1920
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
        assertEquals(2160, w)
        assertEquals(3840, h)
    }

    @Test fun computeCanvasDimensions_highQuality_landscape_isEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.HIGH_QUALITY,
            viewportWidth = 1920,
            viewportHeight = 1080
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
        assertEquals(3840, w)
        assertEquals(2160, h)
    }

    @Test fun computeCanvasDimensions_standard_landscape_isEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1920,
            viewportHeight = 1080
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
    }

    @Test fun computeCanvasDimensions_standard_portrait_isEven() {
        val (w, h) = computeCanvasDimensions(
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            viewportWidth = 1080,
            viewportHeight = 1920
        )
        assertTrue("Width must be even, got $w", w % 2 == 0)
        assertTrue("Height must be even, got $h", h % 2 == 0)
    }
}
