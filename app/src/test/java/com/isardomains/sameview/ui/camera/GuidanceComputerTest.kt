package com.isardomains.sameview.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceComputerTest {

    // --- proximityColor ---

    @Test
    fun proximityColor_green_whenDistanceBelowThreshold() {
        // distance 10m < max(20, 2*5) = 20 -> GREEN
        assertEquals(ProximityColor.GREEN, GuidanceComputer.computeProximityColor(10f, 5f))
    }

    @Test
    fun proximityColor_green_scalesWithAccuracy() {
        // distance 30m <= max(20, 2*20) = 40 -> GREEN (threshold scales with accuracy)
        assertEquals(ProximityColor.GREEN, GuidanceComputer.computeProximityColor(30f, 20f))
    }

    @Test
    fun proximityColor_orange_range() {
        // distance 50m, accuracy 10m: 50 > max(20,20)=20 and 50<=100 and accuracy<=50 -> ORANGE
        assertEquals(ProximityColor.ORANGE, GuidanceComputer.computeProximityColor(50f, 10f))
    }

    @Test
    fun proximityColor_red_range() {
        // distance 200m, accuracy 10m: > 100m and accuracy<=100 -> RED
        assertEquals(ProximityColor.RED, GuidanceComputer.computeProximityColor(200f, 10f))
    }

    @Test
    fun proximityColor_neutral_whenAccuracyPoor() {
        // accuracy > 100m -> NEUTRAL regardless of distance
        assertEquals(ProximityColor.NEUTRAL, GuidanceComputer.computeProximityColor(50f, 101f))
    }

    // --- bearing suppression ---

    @Test
    fun bearing_suppressedWhenDistanceLessThan15m() {
        // ~10m north of current position (10/111100 ≈ 0.000090°)
        val result = GuidanceComputer.computeGuidanceState(
            currentLat = 48.0, currentLon = 11.0, accuracyMeters = 5f,
            refLat = 48.000090, refLon = 11.0,
            previousState = GpsGuidanceState.Hidden,
            pendingColor = null, pendingCount = 0
        )
        val state = result.state as GpsGuidanceState.Informative
        assertNull("Bearing must be suppressed when distance < 15m", state.bearingDegrees)
    }

    @Test
    fun bearing_presentWhenDistanceAboveSuppressThreshold() {
        // ~50m north (50/111100 ≈ 0.000450°), well above 15m threshold
        val result = GuidanceComputer.computeGuidanceState(
            currentLat = 48.0, currentLon = 11.0, accuracyMeters = 5f,
            refLat = 48.000450, refLon = 11.0,
            previousState = GpsGuidanceState.Hidden,
            pendingColor = null, pendingCount = 0
        )
        val state = result.state as GpsGuidanceState.Informative
        assertNotNull("Bearing must be present when distance >= 15m", state.bearingDegrees)
    }

    @Test
    fun bearing_normalizedToPositiveRange() {
        // Point south of current -> bearing near 180°, always in [0, 360)
        val bearing = GuidanceComputer.computeBearing(48.001, 11.0, 48.0, 11.0)
        assertTrue("bearing $bearing should be in [0, 360)", bearing >= 0f && bearing < 360f)
        assertEquals(180f, bearing, 1f)
    }

    @Test
    fun bearing_north_isNearZero() {
        val bearing = GuidanceComputer.computeBearing(48.0, 11.0, 48.001, 11.0)
        // North = 0° (or very close)
        assertTrue("bearing $bearing should be near 0/360", bearing < 1f || bearing > 359f)
    }

    // --- hysteresis ---

    @Test
    fun hysteresis_colorDoesNotChangeOnSingleUpdate() {
        val (stable, pending, count) = GuidanceComputer.applyColorHysteresis(
            rawColor = ProximityColor.RED,
            stableColor = ProximityColor.GREEN,
            pendingColor = null,
            pendingCount = 0
        )
        assertEquals("Stable color must not change on first differing update",
            ProximityColor.GREEN, stable)
        assertEquals(ProximityColor.RED, pending)
        assertEquals(1, count)
    }

    @Test
    fun hysteresis_colorChangesAfterTwoConsecutiveUpdates() {
        // First update: RED differs from GREEN stable -> pending=RED, count=1
        val (s1, p1, c1) = GuidanceComputer.applyColorHysteresis(
            rawColor = ProximityColor.RED,
            stableColor = ProximityColor.GREEN,
            pendingColor = null,
            pendingCount = 0
        )
        assertEquals(ProximityColor.GREEN, s1)
        // Second consecutive RED -> count reaches 2 -> commit
        val (s2, p2, c2) = GuidanceComputer.applyColorHysteresis(
            rawColor = ProximityColor.RED,
            stableColor = s1,
            pendingColor = p1,
            pendingCount = c1
        )
        assertEquals("Stable color must commit after two consecutive updates",
            ProximityColor.RED, s2)
        assertNull(p2)
        assertEquals(0, c2)
    }

    @Test
    fun hysteresis_pendingResetWhenRawColorReverts() {
        // First update: pending RED
        val (s1, p1, c1) = GuidanceComputer.applyColorHysteresis(
            rawColor = ProximityColor.RED,
            stableColor = ProximityColor.GREEN,
            pendingColor = null,
            pendingCount = 0
        )
        // Second update: raw reverts to GREEN -> clears pending, stable stays GREEN
        val (s2, p2, c2) = GuidanceComputer.applyColorHysteresis(
            rawColor = ProximityColor.GREEN,
            stableColor = s1,
            pendingColor = p1,
            pendingCount = c1
        )
        assertEquals(ProximityColor.GREEN, s2)
        assertNull(p2)
        assertEquals(0, c2)
    }

    // --- update thresholds ---

    @Test
    fun updateThreshold_smallDistanceChangeIgnored() {
        val currentLat = 48.0; val currentLon = 11.0
        val refLat = 48.000450; val refLon = 11.0  // ~50m north

        // Compute actual raw distance so we can set previous to (rawDist - 1m)
        val rawDist = GuidanceComputer.computeDistance(currentLat, currentLon, refLat, refLon)
        val previousDist = rawDist - 1.0f  // 1m change — below 2m threshold
        val previous = GpsGuidanceState.Informative(previousDist, 0f, ProximityColor.ORANGE)

        val result = GuidanceComputer.computeGuidanceState(
            currentLat, currentLon, 10f, refLat, refLon, previous, null, 0
        ).state as GpsGuidanceState.Informative

        assertEquals("Distance display must not update when change < ${GuidanceComputer.DISTANCE_UPDATE_THRESHOLD_M}m",
            previousDist, result.distanceMeters, 0.001f)
    }

    @Test
    fun updateThreshold_distanceUpdatesWhenChangeExceedsThreshold() {
        val currentLat = 48.0; val currentLon = 11.0
        val refLat = 48.000450; val refLon = 11.0  // ~50m north

        val rawDist = GuidanceComputer.computeDistance(currentLat, currentLon, refLat, refLon)
        val previousDist = rawDist - 5.0f  // 5m change — above 2m threshold
        val previous = GpsGuidanceState.Informative(previousDist, 0f, ProximityColor.ORANGE)

        val result = GuidanceComputer.computeGuidanceState(
            currentLat, currentLon, 10f, refLat, refLon, previous, null, 0
        ).state as GpsGuidanceState.Informative

        assertEquals("Distance display must update when change >= ${GuidanceComputer.DISTANCE_UPDATE_THRESHOLD_M}m",
            rawDist, result.distanceMeters, 0.001f)
    }

    @Test
    fun updateThreshold_smallBearingChangeIgnored() {
        val currentLat = 48.0; val currentLon = 11.0
        val refLat = 48.000450; val refLon = 11.0  // ~50m north

        val rawBearing = GuidanceComputer.computeBearing(currentLat, currentLon, refLat, refLon)
        val previousBearing = rawBearing - 3f  // 3° less — below 5° threshold
        val previous = GpsGuidanceState.Informative(50f, previousBearing, ProximityColor.ORANGE)

        val result = GuidanceComputer.computeGuidanceState(
            currentLat, currentLon, 10f, refLat, refLon, previous, null, 0
        ).state as GpsGuidanceState.Informative

        assertEquals("Bearing display must not update when change < ${GuidanceComputer.BEARING_UPDATE_THRESHOLD_DEG}°",
            previousBearing, result.bearingDegrees!!, 0.001f)
    }

    @Test
    fun updateThreshold_bearingUpdatesWhenChangeExceedsThreshold() {
        val currentLat = 48.0; val currentLon = 11.0
        val refLat = 48.000450; val refLon = 11.0  // ~50m north

        val rawBearing = GuidanceComputer.computeBearing(currentLat, currentLon, refLat, refLon)
        val previousBearing = rawBearing - 10f  // 10° less — above 5° threshold
        val previous = GpsGuidanceState.Informative(50f, previousBearing, ProximityColor.ORANGE)

        val result = GuidanceComputer.computeGuidanceState(
            currentLat, currentLon, 10f, refLat, refLon, previous, null, 0
        ).state as GpsGuidanceState.Informative

        assertEquals("Bearing display must update when change >= ${GuidanceComputer.BEARING_UPDATE_THRESHOLD_DEG}°",
            rawBearing, result.bearingDegrees!!, 0.001f)
    }

    // --- Neutral when accuracy poor ---

    @Test
    fun guidanceState_isNeutral_whenAccuracyExceedsThreshold() {
        val result = GuidanceComputer.computeGuidanceState(
            currentLat = 48.0, currentLon = 11.0, accuracyMeters = 101f,
            refLat = 48.001, refLon = 11.0,
            previousState = GpsGuidanceState.Hidden,
            pendingColor = null, pendingCount = 0
        )
        assertEquals(GpsGuidanceState.Neutral, result.state)
        assertNull(result.pendingColor)
        assertEquals(0, result.pendingCount)
    }

    @Test
    fun guidanceState_isInformative_whenAccuracySufficient() {
        val result = GuidanceComputer.computeGuidanceState(
            currentLat = 48.0, currentLon = 11.0, accuracyMeters = 10f,
            refLat = 48.000450, refLon = 11.0,
            previousState = GpsGuidanceState.Hidden,
            pendingColor = null, pendingCount = 0
        )
        assertTrue(result.state is GpsGuidanceState.Informative)
    }

    // --- formatDistance ---

    @Test
    fun formatDistance_showsMeters_whenBelow1km() {
        assertEquals("47m", GuidanceComputer.formatDistance(47f))
    }

    @Test
    fun formatDistance_showsKm_whenAtLeast1km() {
        assertEquals("1.2km", GuidanceComputer.formatDistance(1200f))
    }

    @Test
    fun formatDistance_showsKm_atExactly1km() {
        assertEquals("1.0km", GuidanceComputer.formatDistance(1000f))
    }
}
