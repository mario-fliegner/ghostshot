package com.isardomains.sameview.ui.camera

sealed interface GpsGuidanceState {
    data object Hidden : GpsGuidanceState
    data object Neutral : GpsGuidanceState
    data class Informative(
        val distanceMeters: Float,
        val bearingDegrees: Float?,
        val proximityColor: ProximityColor
    ) : GpsGuidanceState
}

enum class ProximityColor { GREEN, ORANGE, RED, NEUTRAL }
