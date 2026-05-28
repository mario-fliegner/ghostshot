package com.isardomains.sameview.storage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.GpsSnapshot
import com.isardomains.sameview.ui.camera.MediaStoreWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // openInputStream is unreliable on Samsung (Android 14+) immediately after IS_PENDING is
    // cleared: the media scanner moves the file, causing a brief ENOENT via the ContentProvider
    // path.  Reading via the DATA column bypasses that path and goes straight to the file system.
    @Suppress("DEPRECATION")
    private fun readExif(uri: Uri): ExifInterface {
        val path = resolver.query(
            uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).takeUnless { it.isNullOrEmpty() }
            else null
        }
        return if (path != null) {
            ExifInterface(path)
        } else {
            resolver.openFileDescriptor(uri, "r")!!.use { pfd ->
                ExifInterface(pfd.fileDescriptor)
            }
        }
    }

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
        val latLong = FloatArray(2)
        val hasLatLong = readExif(uri).getLatLong(latLong)
        assertTrue("LatLong not present in saved image", hasLatLong)
        assertEquals(gps.latitude, latLong[0].toDouble(), 0.0001)
        assertEquals(gps.longitude, latLong[1].toDouble(), 0.0001)
    }
}
