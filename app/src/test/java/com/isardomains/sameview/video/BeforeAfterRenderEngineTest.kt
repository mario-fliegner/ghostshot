package com.isardomains.sameview.video

import org.junit.Assert.assertEquals
import org.junit.Test

class BeforeAfterRenderEngineTest {

    private fun cfg(durationMs: Int, brandingEnabled: Boolean) = VideoRenderConfig(
        videoMode = VideoMode.BEFORE_AFTER,
        format = VideoExportFormat.ORIGINAL,
        quality = VideoQuality.STANDARD_1080P,
        durationMs = durationMs,
        brandingEnabled = brandingEnabled
    )

    // T-U-05 — animationFrameCount for all 3 presets × branding ON/OFF

    @Test fun animationFrameCount_4s_brandingOff_is120() =
        assertEquals(120, BeforeAfterRenderEngine.animationFrameCount(cfg(4000, false)))

    @Test fun animationFrameCount_6s_brandingOff_is180() =
        assertEquals(180, BeforeAfterRenderEngine.animationFrameCount(cfg(6000, false)))

    @Test fun animationFrameCount_8s_brandingOff_is240() =
        assertEquals(240, BeforeAfterRenderEngine.animationFrameCount(cfg(8000, false)))

    @Test fun animationFrameCount_4s_brandingOn_is90() =
        assertEquals(90, BeforeAfterRenderEngine.animationFrameCount(cfg(4000, true)))

    @Test fun animationFrameCount_6s_brandingOn_is150() =
        assertEquals(150, BeforeAfterRenderEngine.animationFrameCount(cfg(6000, true)))

    @Test fun animationFrameCount_8s_brandingOn_is210() =
        assertEquals(210, BeforeAfterRenderEngine.animationFrameCount(cfg(8000, true)))

    // T-U-06 — reference shown first: frame 0 = reference fully visible, capture hidden

    @Test fun alphaAt_frame0_referenceOne_captureZero() {
        val config = cfg(4000, false)
        val (ref, cap) = BeforeAfterRenderEngine.alphaAt(0, config)
        assertEquals(1.0f, ref, 0.001f)
        assertEquals(0.0f, cap, 0.001f)
    }

    @Test fun alphaAt_frame0_brandingOn_referenceOne_captureZero() {
        val config = cfg(6000, true)
        val (ref, cap) = BeforeAfterRenderEngine.alphaAt(0, config)
        assertEquals(1.0f, ref, 0.001f)
        assertEquals(0.0f, cap, 0.001f)
    }

    // T-U-07 — crossfade midpoint: both alphas ≈ 0.5

    @Test fun alphaAt_crossfadeMidpoint_bothApproximatelyHalf() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount  // 120
        val crossfade = config.frameRate / 2 // 15
        val holdEachSide = (n - crossfade) / 2 // 52
        val midFrame = holdEachSide + crossfade / 2 // 59
        val (ref, cap) = BeforeAfterRenderEngine.alphaAt(midFrame, config)
        assertEquals(0.5f, ref, 0.1f)
        assertEquals(0.5f, cap, 0.1f)
    }

    @Test fun alphaAt_refPlusCaptureEqualsOne_duringCrossfade() {
        val config = cfg(6000, false)
        val n = config.animationFrameCount  // 180
        val crossfade = config.frameRate / 2 // 15
        val holdEachSide = (n - crossfade) / 2
        // Check every frame in the crossfade zone
        for (f in holdEachSide until holdEachSide + crossfade) {
            val (ref, cap) = BeforeAfterRenderEngine.alphaAt(f, config)
            assertEquals("ref + cap must equal 1.0 at frame $f", 1.0f, ref + cap, 0.001f)
        }
    }

    // T-U-08 — last animation frame: capture fully visible, reference hidden

    @Test fun alphaAt_lastAnimationFrame_captureOne_referenceZero() {
        val config = cfg(4000, false)
        val n = config.animationFrameCount
        val (ref, cap) = BeforeAfterRenderEngine.alphaAt(n - 1, config)
        assertEquals(0.0f, ref, 0.001f)
        assertEquals(1.0f, cap, 0.001f)
    }

    @Test fun alphaAt_lastFrame_brandingOn_captureOne_referenceZero() {
        val config = cfg(8000, true)
        val n = config.animationFrameCount
        val (ref, cap) = BeforeAfterRenderEngine.alphaAt(n - 1, config)
        assertEquals(0.0f, ref, 0.001f)
        assertEquals(1.0f, cap, 0.001f)
    }
}
