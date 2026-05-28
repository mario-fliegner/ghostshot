package com.isardomains.sameview.storage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.GpsSnapshot
import com.isardomains.sameview.ui.camera.MediaStoreWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreWriterGpsTest {

    private val resolver: ContentResolver =
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    private val savedUris = mutableListOf<Uri>()

    @After fun cleanup() {
        savedUris.forEach { uri ->
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
        }
        savedUris.clear()
    }

    private fun saveBitmap(gpsSnapshot: GpsSnapshot?): Uri {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val result = MediaStoreWriter.save(resolver, bitmap, gpsSnapshot)
        bitmap.recycle()
        val uri = result.getOrThrow()
        savedUris.add(uri)
        return uri
    }

    private fun readExif(uri: Uri): ExifInterface =
        resolver.openInputStream(uri)!!.use { ExifInterface(it) }

    // ── Recreation Guidance OFF — Privacy-critical: NO GPS in gallery image ──

    @Test
    fun save_noGps_whenGpsSnapshotNull() {
        // Recreation Guidance OFF: currentLocation may exist but gpsSnapshot is null.
        // The gallery image must contain no GPS EXIF tags whatsoever.
        val uri = saveBitmap(null)
        val exif = readExif(uri)
        assertNull("GPS latitude must be absent", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must be absent", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("GPS latitude ref must be absent", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertNull("GPS longitude ref must be absent", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
    }

    // ── Recreation Guidance ON — GPS present in gallery image ────────────────

    @Test
    fun save_hasGpsTags_whenGpsSnapshotPresent() {
        val gps = GpsSnapshot(48.137108, 11.575382, 520.0, 5.0f)
        val uri = saveBitmap(gps)
        val exif = readExif(uri)
        assertNotNull("GPS latitude must be present", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNotNull("GPS longitude must be present", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("N", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertEquals("E", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
    }

    @Test
    fun save_gpsCoordinates_matchSnapshot() {
        val gps = GpsSnapshot(48.137108, 11.575382, null, null)
        val uri = saveBitmap(gps)
        val latLon = readExif(uri).latLong
        assertNotNull(latLon)
        assertEquals(gps.latitude, latLon!![0], 0.0001)
        assertEquals(gps.longitude, latLon[1], 0.0001)
    }
}
