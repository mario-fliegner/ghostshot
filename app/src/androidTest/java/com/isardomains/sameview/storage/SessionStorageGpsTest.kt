package com.isardomains.sameview.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.CaptureSessionSnapshot
import com.isardomains.sameview.ui.camera.GpsSnapshot
import com.isardomains.sameview.ui.camera.ReferenceImageDisplayMode
import com.isardomains.sameview.ui.camera.ReferenceImageMetadata
import com.isardomains.sameview.ui.camera.SessionStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionStorageGpsTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "session-tests/SessionStorageGpsTest")
    private val captureMediaStoreUri = Uri.parse("content://test/capture/gps-test")

    private val testGps = GpsSnapshot(
        latitude = 48.137108,
        longitude = 11.575382,
        altitude = 520.0,
        accuracyMeters = 5.0f
    )

    private val referenceGps = GpsSnapshot(
        latitude = 48.100000,
        longitude = 11.500000,
        altitude = 490.0,
        accuracyMeters = null
    )

    @Before fun clearSessions() { cleanTestRoot() }
    @After fun cleanup() { cleanTestRoot() }

    private fun cleanTestRoot() {
        require(testRoot.absolutePath.contains("session-tests")) {
            "Refusing to delete non-test session root: ${testRoot.absolutePath}"
        }
        testRoot.deleteRecursively()
    }

    private fun buildSnapshot(
        gpsSnapshot: GpsSnapshot?,
        recreationGuidanceEnabled: Boolean,
        refLat: Double? = null,
        refLon: Double? = null,
        refAlt: Double? = null
    ): CaptureSessionSnapshot {
        val exifOrientation = testContext.assets.open("exif_90.jpg").use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
            )
        }
        val options = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it, null, options) }
        return CaptureSessionSnapshot(
            referenceImageUri = Uri.parse("content://test/reference"),
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = options.outWidth,
                rawHeight = options.outHeight,
                orientedWidth = options.outHeight,
                orientedHeight = options.outWidth,
                exifOrientation = exifOrientation,
                gpsLatitude = refLat,
                gpsLongitude = refLon,
                gpsAltitude = refAlt
            ),
            overlayScale = 1.0f,
            overlayOffsetX = 0f,
            overlayOffsetY = 0f,
            referenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            viewportWidth = 80,
            viewportHeight = 120,
            gpsSnapshot = gpsSnapshot,
            recreationGuidanceEnabled = recreationGuidanceEnabled
        )
    }

    private fun saveSession(
        gpsSnapshot: GpsSnapshot?,
        recreationGuidanceEnabled: Boolean = true,
        refLat: Double? = null,
        refLon: Double? = null,
        refAlt: Double? = null
    ): File {
        val tempFile = File(appContext.cacheDir, "test_ref_gps.jpg")
        testContext.assets.open("exif_90.jpg").use { it.copyTo(tempFile.outputStream()) }
        val snapshot = buildSnapshot(gpsSnapshot, recreationGuidanceEnabled, refLat, refLon, refAlt)
        // Override referenceImageUri to use real file so bitmap decode works
        val snapshotWithFile = snapshot.copy(referenceImageUri = Uri.fromFile(tempFile))
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshotWithFile,
            captureMediaStoreUri = captureMediaStoreUri
        )
        captureBitmap.recycle()
        return testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
    }

    private fun readMetadata(sessionDir: File): JSONObject =
        JSONObject(File(sessionDir, "metadata.json").readText())

    // ── capture.jpg GPS ──────────────────────────────────────────────────────

    @Test
    fun captureJpeg_hasGpsTags_whenGpsSnapshotPresent() {
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("N", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertEquals("E", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
    }

    @Test
    fun captureJpeg_hasNoGpsTags_whenGpsSnapshotNull() {
        val sessionDir = saveSession(gpsSnapshot = null)
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
    }

    @Test
    fun captureJpeg_gpsCoordinates_matchSnapshot() {
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        val latLong = FloatArray(2)
        val hasLatLong = exif.getLatLong(latLong)
        assertTrue("LatLong not present in capture.jpg", hasLatLong)
        assertEquals(testGps.latitude, latLong[0].toDouble(), 0.0001)
        assertEquals(testGps.longitude, latLong[1].toDouble(), 0.0001)
    }

    @Test
    fun captureJpeg_orientationPreserved_afterGpsWrite() {
        // capture.jpg is written from a bitmap (no EXIF orientation).
        // After GPS write, no spurious ORIENTATION tag must appear.
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val exif = ExifInterface(File(sessionDir, "capture.jpg").absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        // Bitmap-derived JPEG has no orientation; any value is acceptable EXCEPT
        // one that differs from what was there before the GPS write.
        // The key invariant: GPS write must not SET orientation to a non-default value
        // when no orientation existed. ORIENTATION_UNDEFINED or ORIENTATION_NORMAL both OK.
        assertTrue(
            "Unexpected orientation $orientation",
            orientation == ExifInterface.ORIENTATION_UNDEFINED ||
                    orientation == ExifInterface.ORIENTATION_NORMAL
        )
    }

    // ── reference.jpg — never GPS ────────────────────────────────────────────

    @Test
    fun referenceJpeg_neverHasGpsTags_whenGpsOn() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude
        )
        val exif = ExifInterface(File(sessionDir, "reference.jpg").absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test
    fun referenceJpeg_neverHasGpsTags_whenGpsOff() {
        val sessionDir = saveSession(gpsSnapshot = null, recreationGuidanceEnabled = false)
        val exif = ExifInterface(File(sessionDir, "reference.jpg").absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    // ── reference-original.jpg GPS preservation ──────────────────────────────

    @Test
    fun referenceOriginalJpeg_hasGpsTags_whenGuidanceOnAndRefHasGps() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude,
            refAlt = referenceGps.altitude
        )
        val exif = ExifInterface(File(sessionDir, "reference-original.jpg").absolutePath)
        assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test
    fun referenceOriginalJpeg_gpsMatchesReferenceMetadata_notCaptureGps() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude
        )
        val exif = ExifInterface(File(sessionDir, "reference-original.jpg").absolutePath)
        val latLong = FloatArray(2)
        val hasLatLong = exif.getLatLong(latLong)
        assertTrue("LatLong not present in reference-original.jpg", hasLatLong)
        // Must match reference GPS, not the capture GPS
        assertEquals(referenceGps.latitude, latLong[0].toDouble(), 0.0001)
        assertEquals(referenceGps.longitude, latLong[1].toDouble(), 0.0001)
    }

    @Test
    fun referenceOriginalJpeg_noGps_whenGuidanceOff() {
        val sessionDir = saveSession(
            gpsSnapshot = null,
            recreationGuidanceEnabled = false,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude
        )
        val exif = ExifInterface(File(sessionDir, "reference-original.jpg").absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test
    fun referenceOriginalJpeg_noGps_whenRefHasNoGps() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = null,
            refLon = null
        )
        val exif = ExifInterface(File(sessionDir, "reference-original.jpg").absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    // ── metadata.json captureLocation block ─────────────────────────────────

    @Test
    fun metadata_hasCaptureLocation_whenGpsSnapshotPresent() {
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val json = readMetadata(sessionDir)
        assertTrue("captureLocation block missing", json.has("captureLocation"))
        val loc = json.getJSONObject("captureLocation")
        assertEquals(testGps.latitude, loc.getDouble("latitude"), 0.0001)
        assertEquals(testGps.longitude, loc.getDouble("longitude"), 0.0001)
    }

    @Test
    fun metadata_captureLocation_hasAltitude_whenSnapshotHasAltitude() {
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertEquals(testGps.altitude!!, loc.getDouble("altitude"), 0.001)
    }

    @Test
    fun metadata_captureLocation_hasAccuracyMeters_whenSnapshotHasAccuracy() {
        val sessionDir = saveSession(gpsSnapshot = testGps)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertEquals(testGps.accuracyMeters!!.toDouble(), loc.getDouble("accuracyMeters"), 0.001)
    }

    @Test
    fun metadata_noCaptureLocation_whenGpsSnapshotNull() {
        val sessionDir = saveSession(gpsSnapshot = null)
        val json = readMetadata(sessionDir)
        assertFalse("captureLocation must not be present when GPS is off", json.has("captureLocation"))
    }

    @Test
    fun metadata_captureLocation_omitsAltitude_whenSnapshotAltitudeNull() {
        val snapshotNoAlt = GpsSnapshot(48.0, 11.0, null, null)
        val sessionDir = saveSession(gpsSnapshot = snapshotNoAlt)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertFalse("altitude must not be present when null", loc.has("altitude"))
    }

    @Test
    fun metadata_captureLocation_hasProvider_whenSet() {
        val withProvider = GpsSnapshot(48.0, 11.0, null, null, provider = "gps")
        val sessionDir = saveSession(gpsSnapshot = withProvider)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertEquals("gps", loc.getString("provider"))
    }

    @Test
    fun metadata_captureLocation_omitsProvider_whenNull() {
        val noProvider = GpsSnapshot(48.0, 11.0, null, null)
        val sessionDir = saveSession(gpsSnapshot = noProvider)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertFalse("provider must not be present when null", loc.has("provider"))
    }

    @Test
    fun metadata_captureLocation_hasFixTimestampMs_whenSet() {
        val ts = 1_700_000_000_000L
        val withTs = GpsSnapshot(48.0, 11.0, null, null, fixTimestampMs = ts)
        val sessionDir = saveSession(gpsSnapshot = withTs)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertEquals(ts, loc.getLong("fixTimestampMs"))
    }

    @Test
    fun metadata_captureLocation_omitsFixTimestampMs_whenNull() {
        val noTs = GpsSnapshot(48.0, 11.0, null, null)
        val sessionDir = saveSession(gpsSnapshot = noTs)
        val loc = readMetadata(sessionDir).getJSONObject("captureLocation")
        assertFalse("fixTimestampMs must not be present when null", loc.has("fixTimestampMs"))
    }

    // ── metadata.json referenceLocation block ────────────────────────────────

    @Test
    fun metadata_hasReferenceLocation_whenGuidanceOnAndRefHasGps() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude,
            refAlt = referenceGps.altitude
        )
        val json = readMetadata(sessionDir)
        assertTrue("referenceLocation block missing", json.has("referenceLocation"))
        val ref = json.getJSONObject("referenceLocation")
        assertEquals(referenceGps.latitude, ref.getDouble("latitude"), 0.0001)
        assertEquals(referenceGps.longitude, ref.getDouble("longitude"), 0.0001)
        assertEquals(referenceGps.altitude!!, ref.getDouble("altitude"), 0.001)
        assertEquals("exif", ref.getString("source"))
    }

    @Test
    fun metadata_noReferenceLocation_whenGuidanceOff() {
        val sessionDir = saveSession(
            gpsSnapshot = null,
            recreationGuidanceEnabled = false,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude
        )
        assertFalse("referenceLocation must be absent when guidance is OFF",
            readMetadata(sessionDir).has("referenceLocation"))
    }

    @Test
    fun metadata_noReferenceLocation_whenRefHasNoGps() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = null,
            refLon = null
        )
        assertFalse("referenceLocation must be absent when ref has no GPS",
            readMetadata(sessionDir).has("referenceLocation"))
    }

    @Test
    fun metadata_referenceLocation_omitsAltitude_whenRefAltitudeNull() {
        val sessionDir = saveSession(
            gpsSnapshot = testGps,
            recreationGuidanceEnabled = true,
            refLat = referenceGps.latitude,
            refLon = referenceGps.longitude,
            refAlt = null
        )
        val ref = readMetadata(sessionDir).getJSONObject("referenceLocation")
        assertFalse("altitude must not be present when null", ref.has("altitude"))
    }

    // ── Session stays valid when GPS EXIF write fails ────────────────────────

    @Test
    fun session_remainsValid_whenGpsSnapshotPresent() {
        // GPS write is fail-soft; session must exist regardless
        val sessionDir = saveSession(gpsSnapshot = testGps)
        assertTrue(File(sessionDir, "capture.jpg").exists())
        assertTrue(File(sessionDir, "reference.jpg").exists())
        assertTrue(File(sessionDir, "reference-original.jpg").exists())
        assertTrue(File(sessionDir, "metadata.json").exists())
    }
}
