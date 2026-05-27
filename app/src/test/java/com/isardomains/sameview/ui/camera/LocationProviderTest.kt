package com.isardomains.sameview.ui.camera

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocationProviderTest {

    private lateinit var locationManager: LocationManager
    private lateinit var provider: LocationProvider
    private lateinit var listener: LocationListener
    private lateinit var looper: Looper

    @Before
    fun setUp() {
        locationManager = mock()
        looper = mock()
        provider = LocationProvider(locationManager, looperProvider = { looper })
        listener = mock()
    }

    // --- startUpdates ---

    @Test
    fun startUpdates_requestsGpsProvider() {
        provider.startUpdates(listener)

        verify(locationManager).requestLocationUpdates(
            eq(LocationManager.GPS_PROVIDER),
            eq(LocationProvider.MIN_INTERVAL_MS),
            eq(LocationProvider.MIN_DISTANCE_M),
            eq(listener),
            eq(looper)
        )
    }

    @Test
    fun startUpdates_requestsNetworkProvider() {
        provider.startUpdates(listener)

        verify(locationManager).requestLocationUpdates(
            eq(LocationManager.NETWORK_PROVIDER),
            eq(LocationProvider.MIN_INTERVAL_MS),
            eq(LocationProvider.MIN_DISTANCE_M),
            eq(listener),
            eq(looper)
        )
    }

    @Test
    fun startUpdates_doesNotThrow_whenGpsProviderThrowsSecurityException() {
        whenever(
            locationManager.requestLocationUpdates(
                eq(LocationManager.GPS_PROVIDER), any(), any<Float>(), any<LocationListener>(), any<Looper>()
            )
        ).doThrow(SecurityException())

        // must not throw
        provider.startUpdates(listener)

        // network provider should still be requested
        verify(locationManager).requestLocationUpdates(
            eq(LocationManager.NETWORK_PROVIDER), any(), any<Float>(), eq(listener), eq(looper)
        )
    }

    @Test
    fun startUpdates_doesNotThrow_whenNetworkProviderThrowsIllegalArgumentException() {
        whenever(
            locationManager.requestLocationUpdates(
                eq(LocationManager.NETWORK_PROVIDER), any(), any<Float>(), any<LocationListener>(), any<Looper>()
            )
        ).doThrow(IllegalArgumentException())

        // must not throw
        provider.startUpdates(listener)
    }

    // --- stopUpdates ---

    @Test
    fun stopUpdates_callsRemoveUpdates() {
        provider.stopUpdates(listener)

        verify(locationManager).removeUpdates(listener)
    }

    @Test
    fun stopUpdates_doesNotThrow_whenRemoveUpdatesThrows() {
        whenever(locationManager.removeUpdates(any<LocationListener>())).doThrow(RuntimeException())

        // must not throw
        provider.stopUpdates(listener)
    }

    @Test
    fun stopUpdates_whenNullManager_isHarmless() {
        val providerWithNull = LocationProvider(null as LocationManager?)
        // must not throw
        providerWithNull.stopUpdates(listener)
        verify(locationManager, never()).removeUpdates(any<LocationListener>())
    }

    // --- getLastKnown ---

    @Test
    fun getLastKnown_returnsGps_whenGpsIsFresher() {
        val gpsLoc = mock<Location>().also { whenever(it.time).thenReturn(2000L) }
        val netLoc = mock<Location>().also { whenever(it.time).thenReturn(1000L) }
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(gpsLoc)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(netLoc)

        assertEquals(gpsLoc, provider.getLastKnown())
    }

    @Test
    fun getLastKnown_returnsNetwork_whenNetworkIsFresher() {
        val gpsLoc = mock<Location>().also { whenever(it.time).thenReturn(1000L) }
        val netLoc = mock<Location>().also { whenever(it.time).thenReturn(2000L) }
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(gpsLoc)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(netLoc)

        assertEquals(netLoc, provider.getLastKnown())
    }

    @Test
    fun getLastKnown_returnsGps_whenNetworkIsNull() {
        val gpsLoc = mock<Location>()
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(gpsLoc)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(null)

        assertEquals(gpsLoc, provider.getLastKnown())
    }

    @Test
    fun getLastKnown_returnsNetwork_whenGpsIsNull() {
        val netLoc = mock<Location>()
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(null)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(netLoc)

        assertEquals(netLoc, provider.getLastKnown())
    }

    @Test
    fun getLastKnown_returnsNull_whenBothProvidersReturnNull() {
        whenever(locationManager.getLastKnownLocation(any())).thenReturn(null)

        assertNull(provider.getLastKnown())
    }

    @Test
    fun getLastKnown_doesNotThrow_whenGpsThrowsSecurityException() {
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER))
            .doThrow(SecurityException())
        val netLoc = mock<Location>()
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(netLoc)

        assertEquals(netLoc, provider.getLastKnown())
    }

    @Test
    fun getLastKnown_returnsGpsEqualTime_preferredOverNetwork() {
        val gpsLoc = mock<Location>().also { whenever(it.time).thenReturn(1000L) }
        val netLoc = mock<Location>().also { whenever(it.time).thenReturn(1000L) }
        whenever(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)).thenReturn(gpsLoc)
        whenever(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(netLoc)

        // equal timestamps → GPS wins (gps.time >= net.time)
        assertEquals(gpsLoc, provider.getLastKnown())
    }
}
