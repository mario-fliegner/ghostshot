package com.isardomains.sameview.ui.camera

import android.location.Location

internal data class GpsSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val provider: String? = null,
    val fixTimestampMs: Long? = null
) {
    companion object {
        fun from(location: Location): GpsSnapshot = GpsSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            provider = location.provider,
            fixTimestampMs = location.time.takeIf { it > 0 }
        )
    }
}
