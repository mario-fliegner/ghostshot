package com.isardomains.ghostshot.ui.compare

import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalReferencePeekContentScaleTest {

    @Test
    fun portraitImage_portraitViewport_returnsCrop() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 1080f,
            imageHeight = 1920f
        )
        assertEquals(ContentScale.Crop, result)
    }

    @Test
    fun landscapeImage_landscapeViewport_returnsCrop() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1920f,
            viewportHeight = 1080f,
            imageWidth = 1920f,
            imageHeight = 1080f
        )
        assertEquals(ContentScale.Crop, result)
    }

    @Test
    fun landscapeImage_portraitViewport_returnsFit() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 1920f,
            imageHeight = 1080f
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun portraitImage_landscapeViewport_returnsFit() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1920f,
            viewportHeight = 1080f,
            imageWidth = 1080f,
            imageHeight = 1920f
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun unknownImageSize_nanDimensions_returnsFit() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = Float.NaN,
            imageHeight = Float.NaN
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun unknownImageSize_zeroDimensions_returnsFit() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 0f,
            imageHeight = 0f
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun squareImage_portraitViewport_returnsCrop() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 1080f,
            imageHeight = 1080f
        )
        assertEquals(ContentScale.Crop, result)
    }

    @Test
    fun squareImage_landscapeViewport_returnsCrop() {
        val result = resolveOriginalReferencePeekContentScale(
            viewportWidth = 1920f,
            viewportHeight = 1080f,
            imageWidth = 1080f,
            imageHeight = 1080f
        )
        assertEquals(ContentScale.Crop, result)
    }
}
