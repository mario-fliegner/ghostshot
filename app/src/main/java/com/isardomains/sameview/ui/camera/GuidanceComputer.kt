package com.isardomains.sameview.ui.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GuidanceComputer {

    const val BEARING_SUPPRESS_DISTANCE_M = 15f
    const val DISTANCE_UPDATE_THRESHOLD_M = 2f
    const val BEARING_UPDATE_THRESHOLD_DEG = 5f
    const val NEUTRAL_ACCURACY_THRESHOLD_M = 100f
    const val HYSTERESIS_REQUIRED_COUNT = 2

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun computeDistance(
        currentLat: Double, currentLon: Double,
        refLat: Double, refLon: Double
    ): Float {
        val φ1 = Math.toRadians(currentLat)
        val φ2 = Math.toRadians(refLat)
        val Δφ = Math.toRadians(refLat - currentLat)
        val Δλ = Math.toRadians(refLon - currentLon)
        val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_M * c).toFloat()
    }

    fun computeBearing(
        currentLat: Double, currentLon: Double,
        refLat: Double, refLon: Double
    ): Float {
        val φ1 = Math.toRadians(currentLat)
        val φ2 = Math.toRadians(refLat)
        val Δλ = Math.toRadians(refLon - currentLon)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    fun computeProximityColor(distanceMeters: Float, accuracyMeters: Float): ProximityColor {
        if (accuracyMeters > 100f) return ProximityColor.NEUTRAL
        if (distanceMeters <= maxOf(20f, 2f * accuracyMeters)) return ProximityColor.GREEN
        if (distanceMeters <= 100f && accuracyMeters <= 50f) return ProximityColor.ORANGE
        if (distanceMeters > 100f) return ProximityColor.RED
        return ProximityColor.NEUTRAL
    }

    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            "%.1fkm".format(distanceMeters / 1000f)
        } else {
            "${distanceMeters.toInt()}m"
        }
    }

    /**
     * Applies color hysteresis: a color transition requires [HYSTERESIS_REQUIRED_COUNT]
     * consecutive updates confirming the new color before the stable color changes.
     *
     * @return Triple(stableColor, newPendingColor, newPendingCount)
     */
    fun applyColorHysteresis(
        rawColor: ProximityColor,
        stableColor: ProximityColor,
        pendingColor: ProximityColor?,
        pendingCount: Int
    ): Triple<ProximityColor, ProximityColor?, Int> {
        if (rawColor == stableColor) return Triple(stableColor, null, 0)
        return if (rawColor == pendingColor) {
            val newCount = pendingCount + 1
            if (newCount >= HYSTERESIS_REQUIRED_COUNT) {
                Triple(rawColor, null, 0)
            } else {
                Triple(stableColor, pendingColor, newCount)
            }
        } else {
            Triple(stableColor, rawColor, 1)
        }
    }

    data class GuidanceComputeResult(
        val state: GpsGuidanceState,
        val pendingColor: ProximityColor?,
        val pendingCount: Int
    )

    fun computeGuidanceState(
        currentLat: Double,
        currentLon: Double,
        accuracyMeters: Float,
        refLat: Double,
        refLon: Double,
        previousState: GpsGuidanceState,
        pendingColor: ProximityColor?,
        pendingCount: Int
    ): GuidanceComputeResult {
        if (accuracyMeters > NEUTRAL_ACCURACY_THRESHOLD_M) {
            return GuidanceComputeResult(GpsGuidanceState.Neutral, null, 0)
        }

        val rawDistance = computeDistance(currentLat, currentLon, refLat, refLon)
        val rawBearing = if (rawDistance >= BEARING_SUPPRESS_DISTANCE_M) {
            computeBearing(currentLat, currentLon, refLat, refLon)
        } else null
        val rawColor = computeProximityColor(rawDistance, accuracyMeters)

        val prevInformative = previousState as? GpsGuidanceState.Informative
        val stableColor = prevInformative?.proximityColor ?: rawColor
        val (effectiveColor, newPendingColor, newPendingCount) = applyColorHysteresis(
            rawColor, stableColor, pendingColor, pendingCount
        )

        val displayDistance = if (prevInformative != null &&
            abs(rawDistance - prevInformative.distanceMeters) < DISTANCE_UPDATE_THRESHOLD_M
        ) {
            prevInformative.distanceMeters
        } else {
            rawDistance
        }

        val displayBearing = when {
            rawBearing == null -> null
            prevInformative?.bearingDegrees != null &&
                bearingDelta(rawBearing, prevInformative.bearingDegrees) < BEARING_UPDATE_THRESHOLD_DEG ->
                prevInformative.bearingDegrees
            else -> rawBearing
        }

        return GuidanceComputeResult(
            state = GpsGuidanceState.Informative(displayDistance, displayBearing, effectiveColor),
            pendingColor = newPendingColor,
            pendingCount = newPendingCount
        )
    }

    private fun bearingDelta(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
