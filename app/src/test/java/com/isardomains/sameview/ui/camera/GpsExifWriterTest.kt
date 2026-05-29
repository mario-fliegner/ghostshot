package com.isardomains.sameview.ui.camera

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class GpsExifWriterTest {

    // ── toDmsRationalString ──────────────────────────────────────────────────

    @Test
    fun toDmsRationalString_zero_returnsAllZero() {
        assertEquals("0/1,0/1,0/1000000", GpsExifWriter.toDmsRationalString(0.0))
    }

    @Test
    fun toDmsRationalString_wholeDegreesOnly() {
        assertEquals("48/1,0/1,0/1000000", GpsExifWriter.toDmsRationalString(48.0))
    }

    @Test
    fun toDmsRationalString_halfDegree_returns30Minutes() {
        // 48.5° = 48°30'0"
        assertEquals("48/1,30/1,0/1000000", GpsExifWriter.toDmsRationalString(48.5))
    }

    @Test
    fun toDmsRationalString_exactSeconds() {
        // 48°0'30" = 48 + 30/3600
        val deg = 48.0 + 30.0 / 3600.0
        assertEquals("48/1,0/1,30000000/1000000", GpsExifWriter.toDmsRationalString(deg))
    }

    @Test
    fun toDmsRationalString_nullIsland() {
        assertEquals("0/1,0/1,0/1000000", GpsExifWriter.toDmsRationalString(0.0))
    }

    @Test
    fun toDmsRationalString_northPole() {
        assertEquals("90/1,0/1,0/1000000", GpsExifWriter.toDmsRationalString(90.0))
    }

    @Test
    fun toDmsRationalString_roundingAtSecondBoundary_normalizesUpward() {
        // A value that rounds to exactly 60 seconds must become 1 minute 0 seconds
        // 59.9999999... seconds scenario: use a value extremely close to the next minute
        val almostOneMinute = 48.0 + (1.0 / 60.0) - 1e-12
        val result = GpsExifWriter.toDmsRationalString(almostOneMinute)
        // Should not contain "60000000/1000000" — seconds must be < 60
        val parts = result.split(",")
        val secNumerator = parts[2].split("/")[0].toLong()
        assert(secNumerator < 60_000_000L) {
            "Seconds numerator $secNumerator must be < 60000000; got: $result"
        }
    }

    @Test
    fun toDmsRationalString_sydney_degreesCorrect() {
        // Sydney approx -33.8688° lat, but we pass abs value 33.8688
        val result = GpsExifWriter.toDmsRationalString(33.8688)
        val parts = result.split(",")
        assertEquals("33", parts[0].split("/")[0])
        // 0.8688 * 60 = 52.128 → minutes = 52
        assertEquals("52", parts[1].split("/")[0])
    }

    // ── writeGps — latitude ref ──────────────────────────────────────────────

    @Test
    fun writeGps_positiveLatitude_setsLatRefN() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
    }

    @Test
    fun writeGps_negativeLatitude_setsLatRefS() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(-33.8688, 151.0, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
    }

    @Test
    fun writeGps_zeroLatitude_setsLatRefN() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
    }

    // ── writeGps — longitude ref ─────────────────────────────────────────────

    @Test
    fun writeGps_positiveLongitude_setsLonRefE() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
    }

    @Test
    fun writeGps_negativeLongitude_setsLonRefW() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(40.7128, -74.0060, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
    }

    @Test
    fun writeGps_zeroLongitude_setsLonRefE() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
    }

    // ── writeGps — altitude ──────────────────────────────────────────────────

    @Test
    fun writeGps_positiveAltitude_setsAltRefZero() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, 520.0, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
        verify(exif).setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "520000/1000")
    }

    @Test
    fun writeGps_negativeAltitude_setsAltRefOne() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, -50.0, null))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "1")
        verify(exif).setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "50000/1000")
    }

    @Test
    fun writeGps_nullAltitude_doesNotSetAltitudeTags() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_ALTITUDE), any())
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_ALTITUDE_REF), any())
    }

    // ── writeGps — ORIENTATION invariant ────────────────────────────────────

    @Test
    fun writeGps_doesNotTouchOrientationTag() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, 500.0, 5.0f))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_ORIENTATION), any())
    }

    @Test
    fun writeGps_withNullAltitude_doesNotTouchOrientationTag() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_ORIENTATION), any())
    }

    // ── writeGps — GPSDateStamp ──────────────────────────────────────────────

    @Test
    fun writeGps_setsGpsDateStamp_whenFixTimestampPresent() {
        val exif = mock<ExifInterface>()
        // 2024-01-01T13:13:20Z = 1704114800000 ms
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, fixTimestampMs = 1704114800000L))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_DATESTAMP, "2024:01:01")
    }

    @Test
    fun writeGps_setsGpsDateStamp_midnight_utc() {
        val exif = mock<ExifInterface>()
        // 2024-01-01T00:00:00Z = 1704067200000 ms
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null, fixTimestampMs = 1704067200000L))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_DATESTAMP, "2024:01:01")
    }

    @Test
    fun writeGps_setsGpsDateStamp_yearBoundary() {
        val exif = mock<ExifInterface>()
        // 2023-12-31T23:59:59Z = 1704067199000 ms
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null, fixTimestampMs = 1704067199000L))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_DATESTAMP, "2023:12:31")
    }

    @Test
    fun writeGps_omitsDateStamp_whenFixTimestampNull() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, fixTimestampMs = null))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_DATESTAMP), any())
    }

    // ── writeGps — GPSTimeStamp ──────────────────────────────────────────────

    @Test
    fun writeGps_setsGpsTimeStamp_whenFixTimestampPresent() {
        val exif = mock<ExifInterface>()
        // 2024-01-01T13:13:20Z = 1704114800000 ms
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, fixTimestampMs = 1704114800000L))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, "13/1,13/1,20/1")
    }

    @Test
    fun writeGps_setsGpsTimeStamp_midnight_utc() {
        val exif = mock<ExifInterface>()
        // 2024-01-01T00:00:00Z = 1704067200000 ms
        GpsExifWriter.writeGps(exif, GpsSnapshot(0.0, 0.0, null, null, fixTimestampMs = 1704067200000L))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, "0/1,0/1,0/1")
    }

    @Test
    fun writeGps_omitsTimeStamp_whenFixTimestampNull() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, fixTimestampMs = null))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_TIMESTAMP), any())
    }

    // ── writeGps — GPSProcessingMethod ───────────────────────────────────────

    @Test
    fun writeGps_setsGpsProcessingMethod_fromGpsProvider() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, provider = "gps"))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")
    }

    @Test
    fun writeGps_setsGpsProcessingMethod_fromNetworkProvider() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, provider = "network"))
        verify(exif).setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "NETWORK")
    }

    @Test
    fun writeGps_omitsProcessingMethod_forPassiveProvider() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, provider = "passive"))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_PROCESSING_METHOD), any())
    }

    @Test
    fun writeGps_omitsProcessingMethod_whenProviderNull() {
        val exif = mock<ExifInterface>()
        GpsExifWriter.writeGps(exif, GpsSnapshot(48.0, 11.0, null, null, provider = null))
        verify(exif, never()).setAttribute(eq(ExifInterface.TAG_GPS_PROCESSING_METHOD), any())
    }
}
