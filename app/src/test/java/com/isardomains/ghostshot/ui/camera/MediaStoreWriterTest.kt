package com.isardomains.ghostshot.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreWriterTest {

    @Test
    fun generatedDisplayName_startsWithThenAndNowCameraPrefix() {
        val displayName = MediaStoreWriter.generateDisplayName(timestampMillis = 0L)

        assertTrue(displayName.startsWith("ThenAndNowCamera_"))
    }

    @Test
    fun generatedDisplayName_matchesThenAndNowCameraTimestampFormat() {
        val displayName = MediaStoreWriter.generateDisplayName(timestampMillis = 0L)

        assertTrue(
            Regex("""^ThenAndNowCamera_\d{8}_\d{6}_\d{3}\.jpg$""").matches(displayName)
        )
    }

    @Test
    fun relativePath_equalsThenAndNowCameraPicturesFolder() {
        assertEquals("Pictures/ThenAndNowCamera", MediaStoreWriter.relativePath())
    }
}
