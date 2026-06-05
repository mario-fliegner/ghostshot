package com.isardomains.sameview.video

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil

class CompareSliderRenderEngineTest {

    private fun cfg(durationMs: Int, brandingEnabled: Boolean) = VideoRenderConfig(
        videoMode = VideoMode.COMPARE_SLIDER,
        format = VideoExportFormat.ORIGINAL,
        quality = VideoQuality.STANDARD_1080P,
        durationMs = durationMs,
        brandingEnabled = brandingEnabled
    )

    // T-U-01 — animationFrameCount for all 3 presets × branding ON/OFF

    @Test fun animationFrameCount_4s_brandingOff_is120() =
        assertEquals(120, CompareSliderRenderEngine.animationFrameCount(cfg(4000, false)))

    @Test fun animationFrameCount_6s_brandingOff_is180() =
        assertEquals(180, CompareSliderRenderEngine.animationFrameCount(cfg(6000, false)))

    @Test fun animationFrameCount_8s_brandingOff_is240() =
        assertEquals(240, CompareSliderRenderEngine.animationFrameCount(cfg(8000, false)))

    @Test fun animationFrameCount_4s_brandingOn_is75() =
        assertEquals(75, CompareSliderRenderEngine.animationFrameCount(cfg(4000, true)))

    @Test fun animationFrameCount_6s_brandingOn_is135() =
        assertEquals(135, CompareSliderRenderEngine.animationFrameCount(cfg(6000, true)))

    @Test fun animationFrameCount_8s_brandingOn_is195() =
        assertEquals(195, CompareSliderRenderEngine.animationFrameCount(cfg(8000, true)))

    // T-U-02 — slider starts at 0

    @Test fun sliderPosition_frame0_isZero() {
        val config = cfg(4000, false)
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(0, config), 0.001f)
    }

    @Test fun sliderPosition_frame0_brandingOn_isZero() {
        val config = cfg(6000, true)
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(0, config), 0.001f)
    }

    // T-U-03 — slider reaches 1.0 at hold-capture start (t = 0.60)

    @Test fun sliderPosition_atHoldCaptureStart_isOne() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount // 120
        // First frame where t >= 0.60: ceil(0.60 * 120) = 72
        val holdCaptureStartFrame = ceil(0.60 * n).toInt()
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(holdCaptureStartFrame, config), 0.001f)
    }

    @Test fun sliderPosition_atHoldCaptureStart_6s_brandingOn_isOne() {
        val config = cfg(6000, true)
        val n = config.animationFrameCount // 135
        val holdCaptureStartFrame = ceil(0.60 * n).toInt()
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(holdCaptureStartFrame, config), 0.001f)
    }

    // T-U-04 — single-pass ends on hold capture; last animation frame is 1.0, no reversal

    @Test fun sliderPosition_duringHoldCapture_isOne() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount // 120
        // Frame at 80 % of animation: well within hold-capture zone (60 %–100 %)
        val holdCaptureFrame = (0.80 * n).toInt()
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(holdCaptureFrame, config), 0.001f)
    }

    @Test fun sliderPosition_lastAnimationFrame_isOne() {
        val config = cfg(8000, false)
        val n = config.animationFrameCount // 240
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(n - 1, config), 0.001f)
    }

    @Test fun sliderPosition_lastFrame_brandingOn_isOne() {
        val config = cfg(8000, true)
        val n = config.animationFrameCount // 195
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(n - 1, config), 0.001f)
    }
}
