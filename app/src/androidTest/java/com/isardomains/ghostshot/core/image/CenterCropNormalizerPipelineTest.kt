// path: app/src/androidTest/java/com/isardomains/ghostshot/core/image/CenterCropNormalizerPipelineTest.kt
package com.isardomains.ghostshot.core.image

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CenterCropNormalizerPipelineTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun runPipeline(assetName: String) =
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }.let { src ->
            val cropped = CenterCropNormalizer.centerCrop(src, CenterCropNormalizer.TARGET_RATIO)
            CenterCropNormalizer.scaleTo(cropped, CenterCropNormalizer.TARGET_WIDTH, CenterCropNormalizer.TARGET_HEIGHT)
        }

    // decode → centerCrop → scaleTo on normal_landscape.jpg is deterministic.
    @Test
    fun pipeline_landscape_producesStable9to16Output() {
        val first = runPipeline("normal_landscape.jpg")
        val second = runPipeline("normal_landscape.jpg")

        assertEquals(CenterCropNormalizer.TARGET_WIDTH, first.width)
        assertEquals(CenterCropNormalizer.TARGET_HEIGHT, first.height)
        assertEquals(CenterCropNormalizer.TARGET_WIDTH, second.width)
        assertEquals(CenterCropNormalizer.TARGET_HEIGHT, second.height)
        assertTrue(first.sameAs(second))
    }

    // decode → centerCrop → scaleTo on portrait_tall.jpg is deterministic.
    @Test
    fun pipeline_portrait_producesStable9to16Output() {
        val first = runPipeline("portrait_tall.jpg")
        val second = runPipeline("portrait_tall.jpg")

        assertEquals(CenterCropNormalizer.TARGET_WIDTH, first.width)
        assertEquals(CenterCropNormalizer.TARGET_HEIGHT, first.height)
        assertEquals(CenterCropNormalizer.TARGET_WIDTH, second.width)
        assertEquals(CenterCropNormalizer.TARGET_HEIGHT, second.height)
        assertTrue(first.sameAs(second))
    }

    // normal_landscape.jpg (3840×2160): centerCrop to 9:16 keeps height and reduces width to 1215.
    // Expected: cropWidth = (2160 * 9 / 16).roundToInt() = 1215
    @Test
    fun centerCrop_landscape_producesExpectedDimensions_beforeScaling() {
        val source = context.assets.open("normal_landscape.jpg").use { BitmapFactory.decodeStream(it) }
        assertEquals(3840, source.width)
        assertEquals(2160, source.height)

        val cropped = CenterCropNormalizer.centerCrop(source, CenterCropNormalizer.TARGET_RATIO)
        assertEquals(1215, cropped.width)
        assertEquals(2160, cropped.height)
    }
}
