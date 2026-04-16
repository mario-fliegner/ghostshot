package com.isardomains.ghostshot.ui.camera

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceImageMetadataReaderTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun exifNone_usesRawDimensions() {
        assertOrientedDimensions("exif_none.jpg", expectedWidth = 100, expectedHeight = 60)
    }

    @Test
    fun exif90_swapsDimensions() {
        assertOrientedDimensions("exif_90.jpg", expectedWidth = 60, expectedHeight = 100)
    }

    @Test
    fun exif180_usesRawDimensions() {
        assertOrientedDimensions("exif_180.jpg", expectedWidth = 100, expectedHeight = 60)
    }

    @Test
    fun exif270_swapsDimensions() {
        assertOrientedDimensions("exif_270.jpg", expectedWidth = 60, expectedHeight = 100)
    }

    private fun assertOrientedDimensions(
        assetName: String,
        expectedWidth: Int,
        expectedHeight: Int
    ) {
        val metadata = ReferenceImageMetadataReader.read {
            assets.open(assetName)
        }

        requireNotNull(metadata)
        assertEquals(expectedWidth, metadata.orientedWidth)
        assertEquals(expectedHeight, metadata.orientedHeight)
    }
}
