package com.isardomains.sameview.video

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
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

@RunWith(AndroidJUnit4::class)
class VideoExportPipelineFlashTest {

    private lateinit var resolver: ContentResolver
    private lateinit var sessionDir: File
    private var createdVideoUri: Uri? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        sessionDir = File(context.filesDir, "test_flash_${System.currentTimeMillis()}")
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
     * T-F-I-01: Flash, 1 s animation, Standard, Original, branding OFF.
     * Verifies a valid MP4 is created in Movies/SameView with correct metadata.
     * Uses 1 s duration for fast test execution; cycle count falls back to default (4).
     */
    @Test
    fun t_f_i_01_flash_standard_original_brandingOff_producesValidMp4() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.FLASH,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 1_000,
            brandingEnabled = false
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Flash pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val uri = result.getOrThrow()
        createdVideoUri = uri

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

            assertEquals("IS_PENDING must be 0", 0, isPending)
            assertEquals("MIME type must be video/mp4", "video/mp4", mimeType)
            assertTrue("Display name must start with SameView_", displayName.startsWith("SameView_"))
            assertTrue("Display name must end with _flash.mp4", displayName.endsWith("_flash.mp4"))
        }
    }

    /**
     * T-F-I-02: Flash, 2 s animation, Standard, Portrait 9:16, branding ON.
     * New branding model: animation = 60 frames (2000 ms), endcard = 45 frames (1500 ms),
     * total = 105 frames (3500 ms). Verifies valid MP4 and correct total duration.
     */
    @Test
    fun t_f_i_02_flash_standard_portrait_brandingOn_producesValidMp4WithCorrectDuration() {
        val durationMs = 2_000
        val config = VideoRenderConfig(
            videoMode = VideoMode.FLASH,
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = durationMs,
            brandingEnabled = true
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Flash pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val uri = result.getOrThrow()
        createdVideoUri = uri

        val cursor = resolver.query(
            uri,
            arrayOf(
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.IS_PENDING
            ),
            null, null, null
        )
        assertNotNull("MediaStore cursor must not be null", cursor)
        cursor!!.use { c ->
            assertTrue("MediaStore entry must exist", c.moveToFirst())
            assertEquals("MIME type must be video/mp4", "video/mp4",
                c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)))
            assertEquals("IS_PENDING must be 0", 0,
                c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PENDING)))
        }

        // Verify total duration = animation + branding endcard.
        val expectedTotalMs = durationMs + VideoRenderConfig.BRANDING_DURATION_MS  // 3500 ms
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val actualDurationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            assertTrue(
                "Duration must be approximately ${expectedTotalMs}ms (±500ms), was ${actualDurationMs}ms",
                actualDurationMs in (expectedTotalMs - 500).toLong()..(expectedTotalMs + 500).toLong()
            )
        } finally {
            retriever.release()
        }
    }

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
