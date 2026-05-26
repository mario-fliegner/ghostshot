package com.isardomains.sameview.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreWriterTest {

    @Test
    fun generatedDisplayName_startsWithSameViewPrefix() {
        val displayName = MediaStoreWriter.generateDisplayName(timestampMillis = 0L)

        assertTrue(displayName.startsWith("SameView_"))
    }

    @Test
    fun generatedDisplayName_matchesSameViewTimestampFormat() {
        val displayName = MediaStoreWriter.generateDisplayName(timestampMillis = 0L)

        assertTrue(
            Regex("""^SameView_\d{8}_\d{6}_\d{3}\.jpg$""").matches(displayName)
        )
    }

    @Test
    fun relativePath_equalsSameViewPicturesFolder() {
        assertEquals("Pictures/SameView", MediaStoreWriter.relativePath())
    }
}
