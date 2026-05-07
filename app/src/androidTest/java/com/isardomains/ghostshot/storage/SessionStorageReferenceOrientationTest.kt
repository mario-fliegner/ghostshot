package com.isardomains.ghostshot.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.core.image.CenterCropNormalizer
import com.isardomains.ghostshot.ui.camera.CaptureSessionSnapshot
import com.isardomains.ghostshot.ui.camera.ReferenceImageDisplayMode
import com.isardomains.ghostshot.ui.camera.ReferenceImageMetadata
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

    private val testRoot = File(appContext.filesDir, "session-tests/SessionStorageReferenceOrientationTest")

    @Before
    fun clearSessions() {
        cleanTestRoot()
    }

    @After
    fun cleanup() {
        cleanTestRoot()
    }

    private fun cleanTestRoot() {
        require(testRoot.absolutePath.contains("session-tests")) {
            "Refusing to delete non-test session root: ${testRoot.absolutePath}"
        }
        testRoot.deleteRecursively()
    }

    /**
     * Copies [assetName] to a temp file, saves it via SessionStorage (including EXIF orientation
     * read from the asset), then decodes and returns the stored reference-original.jpg.
     * The caller receives the fully EXIF-corrected, JPEG-re-encoded bitmap.
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
        val options = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        testContext.assets.open(assetName).use { BitmapFactory.decodeStream(it, null, options) }
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
        val orientedWidth = if (isRotated) rawHeight else rawWidth
        val orientedHeight = if (isRotated) rawWidth else rawHeight

        val snapshot = CaptureSessionSnapshot(
            referenceImageUri = Uri.fromFile(tempFile),
            referenceImageMetadata = ReferenceImageMetadata(
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
                exifOrientation = exifOrientation,
            ),
            overlayScale = 1.0f,
            overlayOffsetX = 0.0f,
            overlayOffsetY = 0.0f,
            referenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            viewportWidth = 80,
            viewportHeight = 120,
        )

        val captureBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        SessionStorage.saveSession(
            context = appContext,
            sessionsRoot = testRoot,
            capturedBitmap = captureBitmap,
            snapshot = snapshot,
            captureMediaStoreUri = Uri.parse("content://test/capture"),
        )
        captureBitmap.recycle()

        val sessionDir = testRoot.listFiles()?.firstOrNull()
            ?: error("SessionStorage did not create a session directory")
        return BitmapFactory.decodeFile(File(sessionDir, "reference-original.jpg").absolutePath)
            ?: error("reference-original.jpg missing or unreadable in $sessionDir")
    }

    // exif_90.jpg raw is 100×60; reference-original.jpg must be 60×100 (dimensions swapped by 90° rotation).
    @Test
    fun referenceImage_exif90_isStoredCorrectlyOriented() {
        val raw = testContext.assets.open("exif_90.jpg").use { BitmapFactory.decodeStream(it) }
        assertEquals(100, raw.width)
        assertEquals(60, raw.height)

        val stored = storeAndLoadReference("exif_90.jpg")
        assertEquals(60, stored.width)
        assertEquals(100, stored.height)
    }

    // exif_270.jpg raw is 100×60; reference-original.jpg must be 60×100 (dimensions swapped by 270° rotation).
    @Test
    fun referenceImage_exif270_isStoredCorrectlyOriented() {
        val raw = testContext.assets.open("exif_270.jpg").use { BitmapFactory.decodeStream(it) }
        assertEquals(100, raw.width)
        assertEquals(60, raw.height)

        val stored = storeAndLoadReference("exif_270.jpg")
        assertEquals(60, stored.width)
        assertEquals(100, stored.height)
    }

    // The EXIF-corrected, JPEG-re-encoded reference-original (60×100) normalizes deterministically.
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
