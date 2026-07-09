package com.isardomains.sameview.video

import android.content.ContentResolver
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * Verifies [VideoExportPipeline.buildDisplayName] uses the export timestamp — not the session's
 * historical capture timestamp encoded in the session directory name — consistent with
 * [com.isardomains.sameview.image.ShareRenderConfig]'s image export naming.
 */
class VideoExportPipelineFilenameTest {

    private val mockContext: Context = mock {
        on { contentResolver } doReturn mock<ContentResolver>()
    }
    private val pipeline = VideoExportPipeline(mockContext)

    // A session directory named after a historical capture timestamp — must NOT appear in the
    // exported filename.
    private val sessionDir = File("/fake/sessions/2026-06-15_09-42-10")

    @Test
    fun buildDisplayName_matchesExportTimestampFormat() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 3000,
            brandingEnabled = false
        )

        val name = pipeline.buildDisplayName(config, sessionDir)

        // "SameView_" + yyyyMMdd_HHmmss (15 chars) + "_compare_slider.mp4"
        val timestampRegex = Regex("""SameView_\d{8}_\d{6}_compare_slider\.mp4""")
        assertTrue("Expected export-timestamp filename format, got: $name", timestampRegex.matches(name))
    }

    @Test
    fun buildDisplayName_preservesModeSuffix_forEachMode() {
        val baseConfig = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 3000,
            brandingEnabled = false
        )

        assertTrue(pipeline.buildDisplayName(baseConfig.copy(videoMode = VideoMode.COMPARE_SLIDER), sessionDir)
            .endsWith("_compare_slider.mp4"))
        assertTrue(pipeline.buildDisplayName(baseConfig.copy(videoMode = VideoMode.BEFORE_AFTER), sessionDir)
            .endsWith("_before_after.mp4"))
        assertTrue(pipeline.buildDisplayName(baseConfig.copy(videoMode = VideoMode.FLASH), sessionDir)
            .endsWith("_flash.mp4"))
    }

    @Test
    fun buildDisplayName_doesNotContainSessionId() {
        val config = VideoRenderConfig(
            videoMode = VideoMode.BEFORE_AFTER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 3000,
            brandingEnabled = false
        )

        val name = pipeline.buildDisplayName(config, sessionDir)

        assertFalse("Filename must not contain the session's historical capture timestamp",
            name.contains(sessionDir.name))
    }
}
