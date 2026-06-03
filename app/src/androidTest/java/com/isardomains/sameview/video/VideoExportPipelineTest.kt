package com.isardomains.sameview.video

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * T-I-01: End-to-end video export pipeline test.
 *
 * Canvas size note: computeCanvasDimensions always scales STANDARD_1080P/ORIGINAL to a
 * longest edge of 1920 px regardless of session viewport size. To keep this test fast,
 * LANDSCAPE_16_9 format is used (1920 × 1080, fixed — no metadata.json required) combined
 * with a 1-second duration (30 frames only). Real-quality smoke tests are handled manually
 * on device (see Block 7).
 */
@RunWith(AndroidJUnit4::class)
class VideoExportPipelineTest {

    private lateinit var resolver: ContentResolver
    private lateinit var sessionDir: File
    private var createdVideoUri: Uri? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        sessionDir = File(context.filesDir, "test_session_${System.currentTimeMillis()}")
        sessionDir.mkdirs()
        writeSyntheticImages(sessionDir)
    }

    @After
    fun tearDown() {
        sessionDir.deleteRecursively()
        createdVideoUri?.let { uri ->
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    /**
     * T-I-01: Compare Slider, 1 s, Standard, Landscape 16:9, branding OFF →
     * valid MP4 in Movies/SameView with IS_PENDING = 0.
     *
     * 1-second duration = 30 frames at 1920 × 1080. Fast but sufficient to verify the
     * complete encode→mux→MediaStore pipeline.
     */
    @Test
    fun t_i_01_compareSlider_standard_landscape_brandingOff_producesValidMp4() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 1_000,   // 30 frames — keeps test fast
            brandingEnabled = false
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context.contentResolver)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val uri = result.getOrThrow()
        createdVideoUri = uri

        // Query MediaStore for the created entry.
        val cursor = resolver.query(
            uri,
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.IS_PENDING
            ),
            null, null, null
        )
        assertNotNull("MediaStore cursor must not be null", cursor)
        cursor!!.use { c ->
            assertTrue("MediaStore entry must exist", c.moveToFirst())

            val displayName = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
            val mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
            val isPending = c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PENDING))

            assertEquals("IS_PENDING must be 0 after successful export", 0, isPending)
            assertEquals("MIME type must be video/mp4", "video/mp4", mimeType)
            assertTrue(
                "Display name must start with 'SameView_', was: $displayName",
                displayName.startsWith("SameView_")
            )
            assertTrue(
                "Display name must end with '.mp4', was: $displayName",
                displayName.endsWith(".mp4")
            )
        }
    }

    /**
     * T-I-02: Before & After, 2 s, Standard, Portrait 9:16, branding ON →
     * valid MP4 in Movies/SameView with IS_PENDING = 0.
     *
     * Duration is intentionally shortened to 2 s (30 animation frames with branding ON,
     * since animationFrameCount = (2000 - 1000) * 30 / 1000 = 30) for test speed.
     * Branding endcard rendering is deferred to Block 6; this test validates the pipeline
     * accepts brandingEnabled = true without error.
     * Full 6 s / branding endcard verification is covered in Block 6 T-I-02 re-run.
     */
    @Test
    fun t_i_02_beforeAfter_standard_portrait_brandingOn_producesValidMp4() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.BEFORE_AFTER,
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 2_000,   // 30 animation frames — fast; endcard adds 0 frames until Block 6
            brandingEnabled = true
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context.contentResolver)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val uri = result.getOrThrow()
        // Track for tearDown cleanup
        val secondUri = uri
        if (createdVideoUri == null) createdVideoUri = uri

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.IS_PENDING
            ),
            null, null, null
        )
        assertNotNull("MediaStore cursor must not be null", cursor)
        cursor!!.use { c ->
            assertTrue("MediaStore entry must exist", c.moveToFirst())

            val displayName = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
            val mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
            val isPending = c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PENDING))

            assertEquals("IS_PENDING must be 0 after successful export", 0, isPending)
            assertEquals("MIME type must be video/mp4", "video/mp4", mimeType)
            assertTrue(
                "Display name must start with 'SameView_', was: $displayName",
                displayName.startsWith("SameView_")
            )
            assertTrue(
                "Display name must end with '.mp4', was: $displayName",
                displayName.endsWith(".mp4")
            )
        }

        // Clean up the second video created in this test
        runCatching { context.contentResolver.delete(secondUri, null, null) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /**
     * Creates minimal synthetic reference.jpg (blue) and capture.jpg (green) in [dir].
     * 320 × 240 px — small enough for fast decode, large enough to be valid JPEG input.
     * No metadata.json is needed because LANDSCAPE_16_9 format ignores session viewport.
     */
    private fun writeSyntheticImages(dir: File) {
        writeSolidColorJpeg(dir, "reference.jpg", Color.BLUE)
        writeSolidColorJpeg(dir, "capture.jpg", Color.GREEN)
    }

    private fun writeSolidColorJpeg(dir: File, filename: String, color: Int) {
        val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        FileOutputStream(File(dir, filename)).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
    }
}
