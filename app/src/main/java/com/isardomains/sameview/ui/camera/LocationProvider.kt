package com.isardomains.sameview.ui.camera

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

open class LocationProvider internal constructor(
    private val locationManager: LocationManager?,
    // Injects main looper; overridable in tests to avoid Android stub null returns.
    private val looperProvider: () -> Looper? = {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val l: Looper? = Looper.getMainLooper()
        l
    }
) {
    constructor(context: Context) : this(
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    )

    open fun startUpdates(listener: LocationListener) {
        val looper = looperProvider() ?: return
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, looper
            )
        } catch (_: SecurityException) { }
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, looper
            )
        } catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
    }

    open fun stopUpdates(listener: LocationListener) {
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: Exception) { }
    }

    open fun getLastKnown(): Location? {
        val gps = try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) { null }
        val net = try {
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        catch (_: IllegalArgumentException) { null }
        return when {
            gps != null && net != null -> if (gps.time >= net.time) gps else net
            gps != null -> gps
            else -> net
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 8000L
        const val MIN_DISTANCE_M = 3f
    }
}
