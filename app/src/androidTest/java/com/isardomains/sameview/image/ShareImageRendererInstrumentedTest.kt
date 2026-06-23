package com.isardomains.sameview.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ShareImageRendererInstrumentedTest {

    private lateinit var resolver: ContentResolver
    private lateinit var sessionDir: File
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        sessionDir = File(context.filesDir, "share_test_${System.currentTimeMillis()}")
        sessionDir.mkdirs()
        writeSyntheticSession(sessionDir)
    }

    @After
    fun tearDown() {
        sessionDir.deleteRecursively()
        createdUris.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
    }

    // --- T-B2-09: Slider style, Standard quality, no caption → valid JPEG in Pictures/SameView ---

    @Test
    fun t_b2_09_sliderStandard_noCaptionCreatesJpeg() {
        val ts = nowTimestamp()
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    // --- T-B2-10: Side by side, Original quality, caption ON ---

    @Test
    fun t_b2_10_sideBySideOriginal_withCaption_createsJpeg() {
        val ts = nowTimestamp()
        val caption = ShareCaptionData(
            titleLine = "Grünwald Rathaus",
            dateLine = "1958 → 2026",
            locationLine = "Grünwald"
        )
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE,
            quality = ShareQuality.ORIGINAL,
            captionData = caption,
            sessionDir = sessionDir,
            exportTimestamp = ts
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    // --- T-B2-11: Output JPEG contains no GPS EXIF tags ---

    @Test
    fun t_b2_11_output_hasNoGpsExif() {
        val ts = nowTimestamp()
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts
        )
        val uri = render(config)

        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            assertTrue("GPS latitude must not be present in exported JPEG", lat.isNullOrEmpty())
            assertTrue("GPS longitude must not be present in exported JPEG", lon.isNullOrEmpty())
        } ?: error("Could not open exported JPEG for EXIF check")
    }

    // --- T-B2-12: DISPLAY_NAME contains timestamp and style suffix ---

    @Test
    fun t_b2_12_displayName_containsTimestampAndStyleSuffix() {
        val ts = "20260621_120000"
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts
        )
        val uri = render(config)

        val displayName = queryDisplayName(uri)
        assertNotNull("DISPLAY_NAME must not be null", displayName)
        assertTrue("DISPLAY_NAME must contain timestamp", displayName!!.contains(ts))
        assertTrue("DISPLAY_NAME must contain 'slider'", displayName.contains("slider"))
        assertTrue("DISPLAY_NAME must end with .jpg", displayName.endsWith(".jpg"))
    }

    // --- T-B2-13: sidebyside style produces 'sidebyside' in filename ---

    @Test
    fun t_b2_13_sideBySideStyle_displayNameContainsSidebyside() {
        val ts = "20260621_120001"
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts
        )
        val uri = render(config)
        val displayName = queryDisplayName(uri)
        assertTrue("DISPLAY_NAME must contain 'sidebyside'", displayName?.contains("sidebyside") == true)
    }

    // --- T-B2-14: Output image is in Pictures/SameView ---

    @Test
    fun t_b2_14_output_relativePath_isPicturesSameView() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp()
        )
        val uri = render(config)

        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH),
            null, null, null
        )
        val relativePath = cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        assertNotNull("RELATIVE_PATH must not be null", relativePath)
        assertTrue(
            "RELATIVE_PATH must be Pictures/SameView, was: $relativePath",
            relativePath!!.startsWith("Pictures/SameView")
        )
    }

    // --- Helpers ---

    private fun render(config: ShareRenderConfig): Uri {
        val uri = runBlocking { ShareImageRenderer().render(config, resolver) }
        createdUris.add(uri)
        return uri
    }

    private fun assertJpegExistsInMediaStore(uri: Uri) {
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.IS_PENDING),
            null, null, null
        )
        cursor?.use {
            assertTrue("MediaStore entry must exist", it.moveToFirst())
            assertEquals("image/jpeg", it.getString(0))
            assertEquals(0, it.getInt(1)) // IS_PENDING must be 0 after commit
        } ?: error("MediaStore query returned null for: $uri")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun nowTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // ── Branding tests ────────────────────────────────────────────────────────

    @Test
    fun branding_useBrandingTrue_withBrandingFile_rendersWithoutCrash() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(),
            useBranding = true
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    /**
     * I-12: Verifies that branding ON produces a visually different result from branding OFF.
     * Samples a 10×10 pixel region at the handle centre (mid-image) from both exports.
     * At least one pixel must differ — the branding logo (#CC3232 fill) vs the standard white
     * circle will always differ in this region.
     */
    @Test
    fun i12_branding_useBrandingTrue_renderedOutputDiffersFromStandardHandle() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        val ts1 = "20260701_100001"
        val ts2 = "20260701_100002"

        val uriWithBranding = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts1,
            useBranding = true
        ))
        val uriWithoutBranding = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = ts2,
            useBranding = false
        ))

        // Decode both exports back to Bitmaps for comparison.
        val bmpWith = resolver.openInputStream(uriWithBranding)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: error("Cannot decode branding export")
        val bmpWithout = resolver.openInputStream(uriWithoutBranding)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: error("Cannot decode standard export")

        // Sample a 10×10 region at the image centre where the handle is drawn.
        val cx = bmpWith.width / 2
        val cy = bmpWith.height / 2
        val sampleSize = 10
        var differenceFound = false
        outer@ for (dy in 0 until sampleSize) {
            for (dx in 0 until sampleSize) {
                if (bmpWith.getPixel(cx + dx, cy + dy) != bmpWithout.getPixel(cx + dx, cy + dy)) {
                    differenceFound = true
                    break@outer
                }
            }
        }

        bmpWith.recycle()
        bmpWithout.recycle()

        assertTrue(
            "Branding ON export must differ from branding OFF export in handle region",
            differenceFound
        )
    }

    @Test
    fun branding_useBrandingFalse_rendersStandardHandle_withoutCrash() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(),
            useBranding = false
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    @Test
    fun branding_useBrandingTrue_missingBrandingFile_fallsBackToStandardHandle() {
        // branding-handle.png intentionally absent — must not crash
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(),
            useBranding = true
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    @Test
    fun branding_sideBySide_useBrandingTrue_noHandleRendered() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(),
            useBranding = true  // no effect on Side by side — must not crash
        )
        val uri = render(config)
        assertJpegExistsInMediaStore(uri)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeSyntheticSession(dir: File) {
        writeSyntheticJpeg(File(dir, "reference.jpg"), Color.rgb(80, 120, 180))
        writeSyntheticJpeg(File(dir, "capture.jpg"), Color.rgb(180, 120, 80))
        File(dir, "metadata.json").writeText(
            """{"version":4,"session":{"id":"test","createdAtMs":1000},"viewport":{"width":1080,"height":1920,"orientation":"PORTRAIT"},"overlay":{"scale":1.0,"offsetX":0,"offsetY":0,"displayMode":"COMPARE_WITH_PREVIEW"},"files":{"capture":"capture.jpg","reference":"reference.jpg","referenceOriginal":"reference-original.jpg"},"capture":{"timestampMs":1000}}"""
        )
    }

    private fun writeSyntheticJpeg(file: File, fillColor: Int) {
        val bmp = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(fillColor) }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    private fun writeSyntheticBrandingPng(file: File) {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.rgb(200, 50, 50)) }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
}
