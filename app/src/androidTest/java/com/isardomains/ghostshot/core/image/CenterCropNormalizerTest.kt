// path: app/src/androidTest/java/com/isardomains/ghostshot/core/image/CenterCropNormalizerTest.kt
package com.isardomains.ghostshot.core.image

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CenterCropNormalizerTest {

    private val ratio9to16 = 9f / 16f

    // Source 1080×1440 (3:4 portrait): crop width → 810×1440.
    @Test
    fun centerCrop_portraitSource_produces9to16Dimensions() {
        val source = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val result = CenterCropNormalizer.centerCrop(source, ratio9to16)
        assertEquals(810, result.width)
        assertEquals(1440, result.height)
    }

    // Source 2560×1600 (16:10 landscape): crop width → 900×1600.
    @Test
    fun centerCrop_landscapeSource_produces9to16Dimensions() {
        val source = Bitmap.createBitmap(2560, 1600, Bitmap.Config.ARGB_8888)
        val result = CenterCropNormalizer.centerCrop(source, ratio9to16)
        assertEquals(900, result.width)
        assertEquals(1600, result.height)
    }

    // Source 1600×1600 (1:1 square): crop width → 900×1600.
    @Test
    fun centerCrop_squareSource_produces9to16Dimensions() {
        val source = Bitmap.createBitmap(1600, 1600, Bitmap.Config.ARGB_8888)
        val result = CenterCropNormalizer.centerCrop(source, ratio9to16)
        assertEquals(900, result.width)
        assertEquals(1600, result.height)
    }

    // Source 810×1440 already 9:16: output dimensions are identical.
    @Test
    fun centerCrop_alreadyCorrectRatio_returnsIdenticalDimensions() {
        val source = Bitmap.createBitmap(810, 1440, Bitmap.Config.ARGB_8888)
        val result = CenterCropNormalizer.centerCrop(source, ratio9to16)
        assertEquals(810, result.width)
        assertEquals(1440, result.height)
    }

    @Test
    fun scaleTo_arbitrarySource_producesExactTargetDimensions() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = CenterCropNormalizer.scaleTo(source, 1080, 1920)
        assertEquals(1080, result.width)
        assertEquals(1920, result.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun centerCrop_zeroRatio_throwsIllegalArgumentException() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        CenterCropNormalizer.centerCrop(source, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scaleTo_zeroWidth_throwsIllegalArgumentException() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        CenterCropNormalizer.scaleTo(source, 0, 1920)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scaleTo_zeroHeight_throwsIllegalArgumentException() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        CenterCropNormalizer.scaleTo(source, 1080, 0)
    }
}
