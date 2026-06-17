package com.isardomains.sameview.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class FlashRenderEngineTest {

    private fun cfg(durationMs: Int, brandingEnabled: Boolean = false) = VideoRenderConfig(
        videoMode = VideoMode.FLASH,
        format = VideoExportFormat.ORIGINAL,
        quality = VideoQuality.STANDARD_1080P,
        durationMs = durationMs,
        brandingEnabled = brandingEnabled
    )

    // T-F-U-01 — animationFrameCount equals selected duration in frames (branding-invariant)

    @Test fun animationFrameCount_4s_brandingOff_is120() =
        assertEquals(120, cfg(4000, false).animationFrameCount)

    @Test fun animationFrameCount_4s_brandingOn_is120() =
        assertEquals(120, cfg(4000, true).animationFrameCount)

    @Test fun animationFrameCount_6s_brandingOff_is180() =
        assertEquals(180, cfg(6000, false).animationFrameCount)

    @Test fun animationFrameCount_6s_brandingOn_is180() =
        assertEquals(180, cfg(6000, true).animationFrameCount)

    @Test fun animationFrameCount_8s_brandingOff_is240() =
        assertEquals(240, cfg(8000, false).animationFrameCount)

    @Test fun animationFrameCount_8s_brandingOn_is240() =
        assertEquals(240, cfg(8000, true).animationFrameCount)

    // T-F-U-02 — frame 0 is always Reference (Phase 1 hold)

    @Test fun showCapture_frame0_isFalse() =
        assertFalse(FlashRenderEngine.showCaptureAt(0, cfg(6000)))

    // T-F-U-03 — frame 44 (last Phase-1 frame) is still Reference

    @Test fun showCapture_frame44_isFalse() =
        assertFalse(FlashRenderEngine.showCaptureAt(44, cfg(6000)))

    // T-F-U-04 — frame 45 (first Phase-2 frame) maps to flashFrameIndex 0 → Reference

    @Test fun showCapture_firstPhase2Frame_isFalse() =
        assertFalse(FlashRenderEngine.showCaptureAt(FlashRenderEngine.FLASH_HOLD_FRAMES, cfg(6000)))

    // T-F-U-05 — second flash-frame group is Capture (flashFrameIndex 1)

    @Test fun showCapture_secondFlashGroup_isTrue() {
        val config = cfg(6000)
        // 6 s: phase2Frames=135, totalFlashFrames=8.
        // Second group starts at phase2FrameIndex = ceil(135/8) = 17 → absolute frame 62.
        val phase2Frames = config.animationFrameCount - FlashRenderEngine.FLASH_HOLD_FRAMES
        val totalFlashFrames = FlashRenderEngine.cycleCount(config.durationMs) * 2
        val secondGroupStart = FlashRenderEngine.FLASH_HOLD_FRAMES +
                ceil(phase2Frames.toFloat() / totalFlashFrames).toInt()
        assertTrue(FlashRenderEngine.showCaptureAt(secondGroupStart, config))
    }

    // T-F-U-06 — last animation frame is always Capture (all 6 preset/branding combinations)

    @Test fun showCapture_lastFrame_4s_brandingOff_isTrue() {
        val config = cfg(4000, false)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    @Test fun showCapture_lastFrame_4s_brandingOn_isTrue() {
        val config = cfg(4000, true)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    @Test fun showCapture_lastFrame_6s_brandingOff_isTrue() {
        val config = cfg(6000, false)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    @Test fun showCapture_lastFrame_6s_brandingOn_isTrue() {
        val config = cfg(6000, true)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    @Test fun showCapture_lastFrame_8s_brandingOff_isTrue() {
        val config = cfg(8000, false)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    @Test fun showCapture_lastFrame_8s_brandingOn_isTrue() {
        val config = cfg(8000, true)
        assertTrue(FlashRenderEngine.showCaptureAt(config.animationFrameCount - 1, config))
    }

    // T-F-U-07 — cycle counts

    @Test fun cycleCount_4000ms_is2() = assertEquals(2, FlashRenderEngine.cycleCount(4000))
    @Test fun cycleCount_6000ms_is4() = assertEquals(4, FlashRenderEngine.cycleCount(6000))
    @Test fun cycleCount_8000ms_is6() = assertEquals(6, FlashRenderEngine.cycleCount(8000))

    // T-F-U-08 — Phase 2 contains exactly cycleCount × 2 distinct flash-frame groups

    @Test fun flashFrameGroups_6s_are8Distinct() {
        val config = cfg(6000)
        val phase2Frames = config.animationFrameCount - FlashRenderEngine.FLASH_HOLD_FRAMES
        val totalFlashFrames = FlashRenderEngine.cycleCount(config.durationMs) * 2
        // Verify that the last phase2 frame maps to flashFrameIndex = totalFlashFrames - 1
        val lastPhase2FrameIndex = phase2Frames - 1
        val lastFlashIndex = (lastPhase2FrameIndex * totalFlashFrames) / phase2Frames
        assertEquals(totalFlashFrames - 1, lastFlashIndex)
    }

    // T-F-U-09 — no flash-frame group is 0 frames wide

    @Test fun flashFrameGroups_noGroupIsEmpty_4s() {
        val config = cfg(4000)
        val phase2Frames = config.animationFrameCount - FlashRenderEngine.FLASH_HOLD_FRAMES
        val totalFlashFrames = FlashRenderEngine.cycleCount(config.durationMs) * 2
        val groupSizes = IntArray(totalFlashFrames)
        for (p2 in 0 until phase2Frames) {
            val idx = (p2 * totalFlashFrames) / phase2Frames
            groupSizes[idx]++
        }
        groupSizes.forEachIndexed { i, size ->
            assertTrue("Flash group $i must not be empty (4 s preset)", size > 0)
        }
    }

    @Test fun flashFrameGroups_noGroupIsEmpty_6s() {
        val config = cfg(6000)
        val phase2Frames = config.animationFrameCount - FlashRenderEngine.FLASH_HOLD_FRAMES
        val totalFlashFrames = FlashRenderEngine.cycleCount(config.durationMs) * 2
        val groupSizes = IntArray(totalFlashFrames)
        for (p2 in 0 until phase2Frames) {
            val idx = (p2 * totalFlashFrames) / phase2Frames
            groupSizes[idx]++
        }
        groupSizes.forEachIndexed { i, size ->
            assertTrue("Flash group $i must not be empty (6 s preset)", size > 0)
        }
    }

    @Test fun flashFrameGroups_noGroupIsEmpty_8s() {
        val config = cfg(8000)
        val phase2Frames = config.animationFrameCount - FlashRenderEngine.FLASH_HOLD_FRAMES
        val totalFlashFrames = FlashRenderEngine.cycleCount(config.durationMs) * 2
        val groupSizes = IntArray(totalFlashFrames)
        for (p2 in 0 until phase2Frames) {
            val idx = (p2 * totalFlashFrames) / phase2Frames
            groupSizes[idx]++
        }
        groupSizes.forEachIndexed { i, size ->
            assertTrue("Flash group $i must not be empty (8 s preset)", size > 0)
        }
    }
}
