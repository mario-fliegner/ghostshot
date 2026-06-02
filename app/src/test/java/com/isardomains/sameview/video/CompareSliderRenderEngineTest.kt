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

    @Test fun animationFrameCount_4s_brandingOn_is90() =
        assertEquals(90, CompareSliderRenderEngine.animationFrameCount(cfg(4000, true)))

    @Test fun animationFrameCount_6s_brandingOn_is150() =
        assertEquals(150, CompareSliderRenderEngine.animationFrameCount(cfg(6000, true)))

    @Test fun animationFrameCount_8s_brandingOn_is210() =
        assertEquals(210, CompareSliderRenderEngine.animationFrameCount(cfg(8000, true)))

    // T-U-02 — slider starts at 0

    @Test fun sliderPosition_frame0_isZero() {
        val config = cfg(4000, false)
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(0, config), 0.001f)
    }

    @Test fun sliderPosition_frame0_brandingOn_isZero() {
        val config = cfg(6000, true)
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(0, config), 0.001f)
    }

    // T-U-03 — slider reaches 1.0 at hold-mid-start (t = 0.44)

    @Test fun sliderPosition_atHoldMidStart_isOne() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount // 120
        // First frame where t >= 0.44: ceil(0.44 * 120) = ceil(52.8) = 53
        val holdMidStartFrame = ceil(0.44 * n).toInt()
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(holdMidStartFrame, config), 0.001f)
    }

    @Test fun sliderPosition_atHoldMidStart_6s_brandingOn_isOne() {
        val config = cfg(6000, true)
        val n = config.animationFrameCount // 150
        val holdMidStartFrame = ceil(0.44 * n).toInt()
        assertEquals(1.0f, CompareSliderRenderEngine.sliderPositionAt(holdMidStartFrame, config), 0.001f)
    }

    // T-U-04 — slider returns to 0 after slide-back phase (t >= 0.88)

    @Test fun sliderPosition_afterSlideBack_isZero() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount // 120
        // First frame where t >= 0.88: ceil(0.88 * 120) = ceil(105.6) = 106
        val slideBackEndFrame = ceil(0.88 * n).toInt()
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(slideBackEndFrame, config), 0.001f)
    }

    @Test fun sliderPosition_lastAnimationFrame_isZero() {
        val config = cfg(8000, false)
        val n = config.animationFrameCount // 240
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(n - 1, config), 0.001f)
    }

    @Test fun sliderPosition_lastFrame_brandingOn_isZero() {
        val config = cfg(8000, true)
        val n = config.animationFrameCount // 210
        assertEquals(0.0f, CompareSliderRenderEngine.sliderPositionAt(n - 1, config), 0.001f)
    }
}
