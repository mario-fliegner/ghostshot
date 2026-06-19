package com.isardomains.sameview.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionArrowCalculatorTest {

    private fun bearing(geo: Float, azimuth: Float): Float =
        DirectionArrowCalculator.computeDisplayBearing(geo, azimuth)

    @Test
    fun deviceFacingTarget_returns0() {
        assertEquals(0f, bearing(90f, 90f), 0.001f)
    }

    @Test
    fun bothZero_returns0() {
        assertEquals(0f, bearing(0f, 0f), 0.001f)
    }

    @Test
    fun targetToRight_returns90() {
        // Facing North (azimuth 0°), target is East (geo 90°) → 90° to the right
        assertEquals(90f, bearing(90f, 0f), 0.001f)
    }

    @Test
    fun targetBehind_returns180() {
        // Facing North (azimuth 0°), target is South (geo 180°) → behind
        assertEquals(180f, bearing(180f, 0f), 0.001f)
    }

    @Test
    fun targetToLeft_returns270() {
        // Facing North (azimuth 0°), target is West (geo 270°) → 270° (or 90° to the left)
        assertEquals(270f, bearing(270f, 0f), 0.001f)
    }

    @Test
    fun facingSouthTargetNorth_returns180() {
        // Facing South (azimuth 180°), target is North (geo 0°) → behind (180°)
        assertEquals(180f, bearing(0f, 180f), 0.001f)
    }

    @Test
    fun wraparound_geoNearZero_azimuthNear360() {
        // Target almost North (geo 5°), device almost North (azimuth 355°) → slightly right
        assertEquals(10f, bearing(5f, 355f), 0.001f)
    }

    @Test
    fun wraparound_geoNear360_azimuthNearZero() {
        // Target almost North clockwise (geo 355°), device facing North (azimuth 5°) → slightly left
        assertEquals(350f, bearing(355f, 5f), 0.001f)
    }

    @Test
    fun result_alwaysInRange0to360() {
        // geo 10°, azimuth 350° → (10 - 350 + 360) % 360 = 20°
        val result = bearing(10f, 350f)
        assertEquals(20f, result, 0.001f)
        assertTrue(result >= 0f && result < 360f)
    }

    @Test
    fun result_alwaysInRange_negativeIntermediate() {
        // geo 0°, azimuth 90° → (0 - 90 + 360) % 360 = 270°
        val result = bearing(0f, 90f)
        assertEquals(270f, result, 0.001f)
        assertTrue(result >= 0f && result < 360f)
    }

    @Test
    fun symmetry_targetLeft_vs_targetRight() {
        // Target 30° right and 30° left should give symmetric results
        val right = bearing(30f, 0f)
        val left = bearing(330f, 0f)
        assertEquals(30f, right, 0.001f)
        assertEquals(330f, left, 0.001f)
    }
}
