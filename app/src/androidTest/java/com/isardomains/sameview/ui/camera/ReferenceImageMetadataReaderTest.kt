package com.isardomains.sameview.ui.camera

import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ── GPS EXIF tests ────────────────────────────────────────────────────────

    @Test
    fun read_imageWithGpsExif_returnsCoordinates() {
        val gpsFile = File(appContext.cacheDir, "test_gps_reader.jpg")
        try {
            assets.open("exif_none.jpg").use { input ->
                gpsFile.outputStream().use { input.copyTo(it) }
            }
            ExifInterface(gpsFile.absolutePath).apply {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "520/1")
                setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
                saveAttributes()
            }

            val metadata = ReferenceImageMetadataReader.read { gpsFile.inputStream() }

            requireNotNull(metadata)
            assertEquals(48.0, metadata.gpsLatitude!!, 0.0001)
            assertEquals(11.0, metadata.gpsLongitude!!, 0.0001)
            assertEquals(520.0, metadata.gpsAltitude!!, 0.1)
        } finally {
            gpsFile.delete()
        }
    }

    @Test
    fun read_imageWithoutGpsExif_gpsFieldsAreNull() {
        val metadata = ReferenceImageMetadataReader.read {
            assets.open("exif_90.jpg") // has EXIF orientation, no GPS
        }

        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
        assertNull(metadata.gpsLongitude)
        assertNull(metadata.gpsAltitude)
    }

    @Test
    fun read_imageFromSocialMedia_gpsFieldsAreNull() {
        val metadata = ReferenceImageMetadataReader.read {
            assets.open("exif_none.jpg") // no EXIF at all
        }

        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
    }

    @Test
    fun read_screenshotWithNoExif_gpsFieldsAreNull() {
        val metadata = ReferenceImageMetadataReader.read {
            assets.open("normal_landscape.jpg")
        }

        requireNotNull(metadata)
        assertNull(metadata.gpsLatitude)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun assertOrientedDimensions(
        assetName: String,
        expectedWidth: Int,
        expectedHeight: Int
    ) {
        val metadata = ReferenceImageMetadataReader.read {
            assets.open(assetName)
        }

        requireNotNull(metadata)
        assertEquals(expectedWidth, metadata.orientedWidth)
        assertEquals(expectedHeight, metadata.orientedHeight)
    }
}
