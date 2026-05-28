package com.isardomains.sameview.ui.camera

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GpsSnapshotTest {

    private fun mockLocation(
        lat: Double = 48.0,
        lon: Double = 11.0,
        hasAlt: Boolean = false,
        alt: Double = 0.0,
        hasAcc: Boolean = false,
        acc: Float = 0f
    ): Location = mock<Location>().also {
        whenever(it.latitude).thenReturn(lat)
        whenever(it.longitude).thenReturn(lon)
        whenever(it.hasAltitude()).thenReturn(hasAlt)
        whenever(it.altitude).thenReturn(alt)
        whenever(it.hasAccuracy()).thenReturn(hasAcc)
        whenever(it.accuracy).thenReturn(acc)
    }

    @Test
    fun from_setsLatitudeAndLongitude() {
        val snapshot = GpsSnapshot.from(mockLocation(lat = 48.123, lon = 11.456))
        assertEquals(48.123, snapshot.latitude, 0.0)
        assertEquals(11.456, snapshot.longitude, 0.0)
    }

    @Test
    fun from_setsAltitude_whenHasAltitude() {
        val snapshot = GpsSnapshot.from(mockLocation(hasAlt = true, alt = 520.5))
        assertEquals(520.5, snapshot.altitude!!, 0.0)
    }

    @Test
    fun from_altitudeIsNull_whenNoAltitude() {
        val snapshot = GpsSnapshot.from(mockLocation(hasAlt = false))
        assertNull(snapshot.altitude)
    }

    @Test
    fun from_setsAccuracy_whenHasAccuracy() {
        val snapshot = GpsSnapshot.from(mockLocation(hasAcc = true, acc = 7.5f))
        assertEquals(7.5f, snapshot.accuracyMeters!!, 0f)
    }

    @Test
    fun from_accuracyIsNull_whenNoAccuracy() {
        val snapshot = GpsSnapshot.from(mockLocation(hasAcc = false))
        assertNull(snapshot.accuracyMeters)
    }

    @Test
    fun from_setsProvider() {
        val loc = mockLocation().also { whenever(it.provider).thenReturn("gps") }
        assertEquals("gps", GpsSnapshot.from(loc).provider)
    }

    @Test
    fun from_setsFixTimestampMs_whenTimePositive() {
        val loc = mockLocation().also { whenever(it.time).thenReturn(1_700_000_000_000L) }
        assertEquals(1_700_000_000_000L, GpsSnapshot.from(loc).fixTimestampMs)
    }

    @Test
    fun from_fixTimestampMs_isNull_whenTimeZero() {
        val loc = mockLocation().also { whenever(it.time).thenReturn(0L) }
        assertNull(GpsSnapshot.from(loc).fixTimestampMs)
    }

    @Test
    fun equalityBasedOnValues() {
        val a = GpsSnapshot(48.0, 11.0, 500.0, 5.0f)
        val b = GpsSnapshot(48.0, 11.0, 500.0, 5.0f)
        assertEquals(a, b)
    }

    @Test
    fun inequality_whenLatitudeDiffers() {
        val a = GpsSnapshot(48.0, 11.0, null, null)
        val b = GpsSnapshot(49.0, 11.0, null, null)
        assertNotEquals(a, b)
    }

    @Test
    fun inequality_whenLongitudeDiffers() {
        val a = GpsSnapshot(48.0, 11.0, null, null)
        val b = GpsSnapshot(48.0, 12.0, null, null)
        assertNotEquals(a, b)
    }

    @Test
    fun copy_producesIndependentInstance() {
        val original = GpsSnapshot(48.0, 11.0, 500.0, 5.0f)
        val copy = original.copy(latitude = 49.0)
        assertEquals(48.0, original.latitude, 0.0)
        assertEquals(49.0, copy.latitude, 0.0)
    }
}
