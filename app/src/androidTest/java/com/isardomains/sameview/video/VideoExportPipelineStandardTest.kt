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

@RunWith(AndroidJUnit4::class)
class VideoExportPipelineStandardTest {

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

    @Test
    fun t_i_01_compareSlider_standard_landscape_brandingOff_producesValidMp4() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.LANDSCAPE_16_9,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 1_000,
            brandingEnabled = false
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context.contentResolver)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

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

    @Test
    fun t_i_02_beforeAfter_standard_portrait_brandingOn_producesValidMp4() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.BEFORE_AFTER,
            format = VideoExportFormat.PORTRAIT_9_16,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 2_000,
            brandingEnabled = true
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = VideoExportPipeline(context.contentResolver)

        val result = runBlocking { pipeline.run(config, sessionDir) }

        assertTrue("Pipeline must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

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
