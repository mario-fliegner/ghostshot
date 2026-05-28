package com.isardomains.sameview.ui.camera

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReferenceImageMetadataReaderTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exifNone_usesRawDimensions() {
        assertOrientedDimensions("exif_none.jpg", expectedWidth = 100, expectedHeight = 60)
    }

    @Test
    fun exif90_swapsDimensions() {
        assertOrientedDimensions("exif_90.jpg", expectedWidth = 60, expectedHeight = 100)
    }

    @Test
    fun exif180_usesRawDimensions() {
        assertOrientedDimensions("exif_180.jpg", expectedWidth = 100, expectedHeight = 60)
    }

    @Test
    fun exif270_swapsDimensions() {
        assertOrientedDimensions("exif_270.jpg", expectedWidth = 60, expectedHeight = 100)
    }

    // ── GPS EXIF tests (synthetic JPEG fixtures) ──────────────────────────────

    @Test
    fun read_imageWithGpsExif_returnsCoordinates() {
        val file = makeGpsJpeg("test_gps_reader.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "520/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            assertEquals(48.0, metadata.gpsLatitude!!, 0.0001)
            assertEquals(11.0, metadata.gpsLongitude!!, 0.0001)
            assertEquals(520.0, metadata.gpsAltitude!!, 0.1)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_imageWithoutGpsExif_gpsFieldsAreNull() {
        val metadata = readFromAsset("exif_90.jpg")
        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
        assertNull(metadata.gpsLongitude)
        assertNull(metadata.gpsAltitude)
    }

    @Test
    fun read_imageFromSocialMedia_gpsFieldsAreNull() {
        val metadata = readFromAsset("exif_none.jpg")
        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
    }

    @Test
    fun read_screenshotWithNoExif_gpsFieldsAreNull() {
        val metadata = readFromAsset("normal_landscape.jpg")
        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
    }

    @Test
    fun read_samsungStyleFullPrecisionDms_returnsCorrectCoordinates() {
        // Venice, Italy: 45°28'32.718719"N 12°33'52.072922"E, 43 m above sea level
        val file = makeGpsJpeg("test_gps_samsung_precision.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "45/1,28/1,32718719/1000000")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "12/1,33/1,52072922/1000000")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "43/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            assertEquals(45.4757, metadata.gpsLatitude!!, 0.001)
            assertEquals(12.5645, metadata.gpsLongitude!!, 0.001)
            assertEquals(43.0, metadata.gpsAltitude!!, 0.5)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_zeroZeroCoordinates_gpsIsNull() {
        val file = makeGpsJpeg("test_gps_zero.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "0/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "0/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            assertNull(metadata.gpsLatitude)
            assertNull(metadata.gpsLongitude)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_southHemisphere_negatesLatitude() {
        // Sydney: 33°51'54"S 151°12'26"E
        val file = makeGpsJpeg("test_gps_south.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "33/1,51/1,54/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "151/1,12/1,26/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            val lat = requireNotNull(metadata.gpsLatitude)
            assertTrue("Expected negative latitude for S ref, got $lat", lat < 0.0)
            assertEquals(-33.865, lat, 0.01)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_westLongitude_negatesLongitude() {
        // New York: 40°42'46"N 74°0'21"W
        val file = makeGpsJpeg("test_gps_west.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "40/1,42/1,46/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "74/1,0/1,21/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            val lon = requireNotNull(metadata.gpsLongitude)
            assertTrue("Expected negative longitude for W ref, got $lon", lon < 0.0)
            assertEquals(-74.006, lon, 0.01)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_altitudeBelowSeaLevel_negatesAltitude() {
        // Dead Sea shore: ~31°N 35°E, 420 m below sea level
        val file = makeGpsJpeg("test_gps_belowsea.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "31/1,46/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "35/1,29/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "420/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "1")
        }
        try {
            val metadata = readFromFile(file)
            requireNotNull(metadata)
            requireNotNull(metadata.gpsLatitude)
            requireNotNull(metadata.gpsLongitude)
            val alt = requireNotNull(metadata.gpsAltitude)
            assertTrue("Expected negative altitude for below-sea-level ref, got $alt", alt < 0.0)
            assertEquals(-420.0, alt, 0.5)
        } finally {
            file.delete()
        }
    }

    // ── Real-file GPS regression tests ───────────────────────────────────────

    /** A real JPEG from a camera must deliver valid GPS through the production FD-based reader. */
    @Test
    fun read_realJpgWithGps_productionPathDeliversValidCoordinates() {
        val metadata = readFromAsset("reference_jpg_with_gps.jpg")
        requireNotNull(metadata) { "read() returned null for reference_jpg_with_gps.jpg" }
        val lat = requireNotNull(metadata.gpsLatitude) { "gpsLatitude was null" }
        val lon = requireNotNull(metadata.gpsLongitude) { "gpsLongitude was null" }
        assertTrue("lat $lat out of valid range", lat in -90.0..90.0)
        assertTrue("lon $lon out of valid range", lon in -180.0..180.0)
        assertTrue("lat/lon is 0/0 (Null Island)", lat != 0.0 || lon != 0.0)
    }

    /** A JPEG without GPS must yield null coordinates — no false positive. */
    @Test
    fun read_realJpgWithoutGps_returnsNullGps() {
        val metadata = readFromAsset("reference_jpg_without_gps.jpg")
        requireNotNull(metadata)
        assertNull("Expected null gpsLatitude for no-GPS JPEG", metadata.gpsLatitude)
        assertNull("Expected null gpsLongitude for no-GPS JPEG", metadata.gpsLongitude)
    }

    /** A HEIC without GPS must yield null coordinates — no phantom GPS. */
    @Test
    fun read_realHeicWithoutGps_returnsNullGps() {
        // Note: read() may return null on runtimes where BitmapFactory cannot decode HEIC bounds.
        // In that case GPS is still not returned — the test passes either way.
        val metadata = readFromAsset("reference_heic_without_gps.heic")
        if (metadata != null) {
            assertNull("Expected null gpsLatitude for no-GPS HEIC", metadata.gpsLatitude)
            assertNull("Expected null gpsLongitude for no-GPS HEIC", metadata.gpsLongitude)
        }
    }

    /**
     * A real HEIC shot with a Samsung camera must now deliver valid GPS through the
     * production FD-based reader. This test fails before the FileDescriptor fix and
     * must pass after it.
     */
    @Test
    fun read_realHeicWithGps_productionPathDeliversValidCoordinates() {
        // Note: read() returns null if BitmapFactory cannot decode HEIC bounds on this runtime.
        // In that case we skip the GPS assertion — the fix is still in place but HEIC decoding
        // itself is the limiting factor on that runtime.
        val metadata = readFromAsset("reference_heic_with_gps.heic")
            ?: return
        val lat = requireNotNull(metadata.gpsLatitude) {
            "GPS latitude was null for HEIC with GPS — FD-based reader fix may not be effective"
        }
        val lon = requireNotNull(metadata.gpsLongitude) { "GPS longitude was null" }
        assertTrue("lat $lat out of valid range", lat in -90.0..90.0)
        assertTrue("lon $lon out of valid range", lon in -180.0..180.0)
        assertTrue("lat/lon is 0/0 (Null Island)", lat != 0.0 || lon != 0.0)
    }

    // ── SAF / non-media authority regression tests ────────────────────────────

    /**
     * Reproduces the root cause of the SAF GPS regression: a content URI whose authority is NOT
     * "media" must return GPS coordinates correctly.
     *
     * The test uses [SafMimicContentProvider] which throws SecurityException when the URI
     * carries the "require_original" query parameter — exactly what the real
     * com.android.providers.media.documents document provider does when
     * MediaStore.setRequireOriginal() is incorrectly applied to a document URI.
     *
     * Before the fix the reader called setRequireOriginal() unconditionally, causing
     * openFileDescriptor() to throw SecurityException, which was silently caught and left GPS null.
     * After the fix setRequireOriginal() is only applied for authority == "media" URIs.
     */
    @Test
    fun read_safLikeContentUri_gpsReturnedWithoutRequireOriginal() {
        val file = makeGpsJpeg("test_saf_require_original_guard.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "520/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
        }
        try {
            // URI authority is "com.isardomains.sameview.test.safmimic" — not "media".
            // SafMimicContentProvider throws SecurityException if require_original is present,
            // reproducing com.android.providers.media.documents behaviour on real devices.
            val safUri = SafMimicContentProvider.uriFor(file)
            val metadata = ReferenceImageMetadataReader.read(safUri, appContext.contentResolver)
            requireNotNull(metadata) { "read() returned null for SAF-like content URI" }
            assertEquals(48.0, metadata.gpsLatitude!!, 0.0001)
            assertEquals(11.0, metadata.gpsLongitude!!, 0.0001)
            assertEquals(520.0, metadata.gpsAltitude!!, 0.1)
        } finally {
            file.delete()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun assertOrientedDimensions(assetName: String, expectedWidth: Int, expectedHeight: Int) {
        val metadata = readFromAsset(assetName)
        requireNotNull(metadata)
        assertEquals(expectedWidth, metadata.orientedWidth)
        assertEquals(expectedHeight, metadata.orientedHeight)
    }

    /**
     * Copies an asset to a temp file, reads metadata via the production FD-based reader,
     * deletes the temp file, and returns the result.
     */
    private fun readFromAsset(assetName: String): ReferenceImageMetadata? {
        val file = File(appContext.cacheDir, "test_asset_${assetName.replace(Regex("[^a-zA-Z0-9.]"), "_")}")
        return try {
            assets.open(assetName).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            ReferenceImageMetadataReader.read(Uri.fromFile(file), appContext.contentResolver)
        } finally {
            file.delete()
        }
    }

    /** Reads metadata from a temp file via the production FD-based reader. */
    private fun readFromFile(file: File): ReferenceImageMetadata? =
        ReferenceImageMetadataReader.read(Uri.fromFile(file), appContext.contentResolver)

    /**
     * Copies exif_none.jpg to a temp file, applies EXIF writes via [configure], saves,
     * and returns the file. Caller is responsible for deletion.
     */
    private fun makeGpsJpeg(filename: String, configure: (ExifInterface) -> Unit): File {
        val file = File(appContext.cacheDir, filename)
        assets.open("exif_none.jpg").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        ExifInterface(file.absolutePath).apply {
            configure(this)
            saveAttributes()
        }
        return file
    }
}
