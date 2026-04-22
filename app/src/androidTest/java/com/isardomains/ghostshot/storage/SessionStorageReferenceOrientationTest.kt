// path: app/src/androidTest/java/com/isardomains/ghostshot/storage/SessionStorageReferenceOrientationTest.kt
package com.isardomains.ghostshot.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.core.image.CenterCropNormalizer
import com.isardomains.ghostshot.ui.camera.SessionStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionStorageReferenceOrientationTest {

    // Test-APK context: owns androidTest/assets.
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    // App-under-test context: owns the filesDir/cacheDir where SessionStorage writes.
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val sessionsDir = File(appContext.filesDir, "sessions")

    @Before
    fun clearSessions() {
        sessionsDir.deleteRecursively()
    }

    @After
    fun cleanup() {
        sessionsDir.deleteRecursively()
    }

    /**
     * Copies [assetName] to a temp file, saves it via the real SessionStorage reference path
     * (including EXIF orientation read from the asset), then decodes and returns the stored
     * reference.jpg. The caller receives the fully EXIF-corrected, JPEG-re-encoded bitmap.
     */
    private fun storeAndLoadReference(assetName: String): Bitmap {
        val tempFile = File(appContext.cacheDir, assetName)
        testContext.assets.open(assetName).use { input ->
            tempFile.outputStream().use { input.copyTo(it) }
        }
        val exifOrientation = testContext.assets.open(assetName).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
            )
        }
        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(appContext, captureBitmap, Uri.fromFile(tempFile), exifOrientation)
        captureBitmap.recycle()

        val sessionDir = sessionsDir.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
        return BitmapFactory.decodeFile(File(sessionDir, "reference.jpg").absolutePath)
            ?: error("reference.jpg missing or unreadable in $sessionDir")
    }

    // exif_90.jpg raw is 100×60; SessionStorage must store it as 60×100 (dimensions swapped by 90° rotation).
    @Test
    fun referenceImage_exif90_isStoredCorrectlyOriented() {
        val raw = testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it) }
        assertEquals(100, raw.width)
        assertEquals(60, raw.height)

        val stored = storeAndLoadReference("exif_90.jpg")
        assertEquals(60, stored.width)
        assertEquals(100, stored.height)
    }

    // exif_270.jpg raw is 100×60; SessionStorage must store it as 60×100 (dimensions swapped by 270° rotation).
    @Test
    fun referenceImage_exif270_isStoredCorrectlyOriented() {
        val raw = testContext.assets.open("exif_270.jpg").use { BitmapFactory.decodeStream(it) }
        assertEquals(100, raw.width)
        assertEquals(60, raw.height)

        val stored = storeAndLoadReference("exif_270.jpg")
        assertEquals(60, stored.width)
        assertEquals(100, stored.height)
    }

    // The EXIF-corrected, JPEG-re-encoded reference (60×100) normalizes deterministically via Variant B.
    @Test
    fun storedReference_fromExif90_canBeNormalizedDeterministically() {
        val stored = storeAndLoadReference("exif_90.jpg")
        val first = CenterCropNormalizer.scaleTo(
            CenterCropNormalizer.centerCrop(stored, CenterCropNormalizer.TARGET_RATIO),
            CenterCropNormalizer.TARGET_WIDTH, CenterCropNormalizer.TARGET_HEIGHT
        )
        val second = CenterCropNormalizer.scaleTo(
            CenterCropNormalizer.centerCrop(stored, CenterCropNormalizer.TARGET_RATIO),
            CenterCropNormalizer.TARGET_WIDTH, CenterCropNormalizer.TARGET_HEIGHT
        )
        assertEquals(CenterCropNormalizer.TARGET_WIDTH, first.width)
        assertEquals(CenterCropNormalizer.TARGET_HEIGHT, first.height)
        assertTrue(first.sameAs(second))
    }
}
