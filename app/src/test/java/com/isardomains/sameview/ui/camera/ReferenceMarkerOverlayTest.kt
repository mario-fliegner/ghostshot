package com.isardomains.sameview.ui.camera

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the coordinate transform math in ReferenceMarkerOverlay.
 *
 * These verify that normalizedToScreen and screenToNormalized are consistent
 * inverses of each other, that boundary rejection works, and that the math
 * matches the transform applied in CompareReferenceImage.
 */
class ReferenceMarkerOverlayTest {

    private val epsilon = 0.001f

    // ── COMPARE_WITH_PREVIEW mode ─────────────────────────────────────────────

    @Test
    fun compareMode_centerMarker_mapsToViewportCenter_whenNoOffset() {
        val result = normalizedToScreen(
            normalizedX = 0.5f, normalizedY = 0.5f,
            viewportWidth = 1080f, viewportHeight = 1920f,
            imageWidth = 1080f, imageHeight = 1920f,
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            overlayOffsetX = 0f, overlayOffsetY = 0f, overlayScale = 1f
        )
        assertOffset(result, 540f, 960f)
    }

    @Test
    fun compareMode_topLeftMarker_calculatesCorrectly() {
        // fillScale = max(1080/1080, 1920/1920) = 1
        // displayedWidth=1080, displayedHeight=1920, scaledW=1080, scaledH=1920
        // maxTX=0, maxTY=0, tx=0, ty=0
        // screenX = 1080/2 + 1080*(0-0.5)*1 + 0 = 540 - 540 = 0
        // screenY = 1920/2 + 1920*(0-0.5)*1 + 0 = 960 - 960 = 0
        val result = normalizedToScreen(
            0f, 0f, 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertOffset(result, 0f, 0f)
    }

    @Test
    fun compareMode_bottomRightMarker_calculatesCorrectly() {
        val result = normalizedToScreen(
            1f, 1f, 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertOffset(result, 1080f, 1920f)
    }

    @Test
    fun compareMode_fillScaleApplied_wideImageOnNarrowViewport() {
        // 4:3 image (1920x1440) on 9:16 viewport (1080x1920)
        // fillScale = max(1080/1920, 1920/1440) = max(0.5625, 1.333) = 1.333
        // displayedWidth = 1920*1.333 = 2560, displayedHeight = 1440*1.333 = 1920
        // scale=1, scaledW=2560, scaledH=1920, maxTX=max(0,(2560-1080)/2)=740, maxTY=0
        // tx = clamp(0*1080, -740, 740) = 0
        // center marker: screenX = 1080/2 + 2560*(0.5-0.5)*1 + 0 = 540
        val result = normalizedToScreen(
            0.5f, 0.5f, 1080f, 1920f, 1920f, 1440f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertOffset(result, 540f, 960f)
    }

    @Test
    fun compareMode_offsetClamped_doesNotExceedMaxTranslation() {
        // 1920x1920 image on 1080x1920 viewport, scale=1
        // fillScale = max(1080/1920, 1920/1920) = max(0.5625, 1.0) = 1.0
        // displayedWidth=1920*1=1920, scaledW=1920, maxTX=(1920-1080)/2=420
        // Requesting offset of 1.0 (i.e. translationX = 1.0*1080 = 1080), clamped to 420
        val result = normalizedToScreen(
            0.5f, 0.5f, 1080f, 1920f, 1920f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 1.0f, 0f, 1f
        )
        // screenX = 540 + 0 + 420 = 960
        assertOffset(result, 960f, 960f)
    }

    @Test
    fun compareMode_roundTrip_normalizedToScreenToNormalized() {
        val nx = 0.3f
        val ny = 0.7f
        val screenPos = normalizedToScreen(
            nx, ny, 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0.1f, -0.1f, 1.2f
        )
        val roundTrip = screenToNormalized(
            screenPos, 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0.1f, -0.1f, 1.2f
        )
        assertNotNull(roundTrip)
        assertEquals(nx, roundTrip!!.first, epsilon)
        assertEquals(ny, roundTrip.second, epsilon)
    }

    // ── SHOW_FULL_IMAGE mode ──────────────────────────────────────────────────

    @Test
    fun fitMode_centerMarker_mapsToViewportCenter_whenNoOffset() {
        val result = normalizedToScreen(
            0.5f, 0.5f, 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE, 0f, 0f, 1f
        )
        assertOffset(result, 540f, 960f)
    }

    @Test
    fun fitMode_fitScaleApplied_wideImageOnNarrowViewport() {
        // 4:3 image (1920x1440) on 9:16 viewport (1080x1920)
        // fitScale = min(1080/1920, 1920/1440) = min(0.5625, 1.333) = 0.5625
        // displayedWidth = 1920*0.5625 = 1080, displayedHeight = 1440*0.5625 = 810
        // tx = 0, ty = 0
        // center marker: screenX = 540 + 1080*(0.5-0.5)*1 + 0 = 540
        //                screenY = 960 + 810*(0.5-0.5)*1 + 0 = 960
        val result = normalizedToScreen(
            0.5f, 0.5f, 1080f, 1920f, 1920f, 1440f,
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE, 0f, 0f, 1f
        )
        assertOffset(result, 540f, 960f)
    }

    @Test
    fun fitMode_roundTrip_normalizedToScreenToNormalized() {
        val nx = 0.6f
        val ny = 0.4f
        val screenPos = normalizedToScreen(
            nx, ny, 1080f, 1920f, 1920f, 1080f,
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE, 0.05f, -0.05f, 1.1f
        )
        val roundTrip = screenToNormalized(
            screenPos, 1080f, 1920f, 1920f, 1080f,
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE, 0.05f, -0.05f, 1.1f
        )
        assertNotNull(roundTrip)
        assertEquals(nx, roundTrip!!.first, epsilon)
        assertEquals(ny, roundTrip.second, epsilon)
    }

    // ── screenToNormalized boundary rejection ─────────────────────────────────

    @Test
    fun screenToNormalized_outsideTopLeft_returnsNull() {
        // Screen position clearly outside the image (top-left corner, before image starts)
        // With fitScale on wide viewport: image is smaller than viewport, so edges are letterboxed
        // fitScale = min(1080/2000, 1920/1000) = min(0.54, 1.92) = 0.54
        // displayedWidth=2000*0.54=1080, displayedHeight=1000*0.54=540
        // Image occupies center: x=[0,1080], y=[960-270, 960+270]=[690,1230]
        // Point (0, 0) is above the image → null
        val result = screenToNormalized(
            Offset(-10f, -10f), 1080f, 1920f, 2000f, 1000f,
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE, 0f, 0f, 1f
        )
        assertNull(result)
    }

    @Test
    fun screenToNormalized_outsideRight_returnsNull() {
        // Point far to the right of the image
        val result = screenToNormalized(
            Offset(2000f, 960f), 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertNull(result)
    }

    @Test
    fun screenToNormalized_insideImage_returnsValidCoords() {
        val result = screenToNormalized(
            Offset(540f, 960f), 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertNotNull(result)
        assertEquals(0.5f, result!!.first, epsilon)
        assertEquals(0.5f, result.second, epsilon)
    }

    @Test
    fun screenToNormalized_cornerPixel_isAccepted() {
        // The exact pixel at (0, 0) is the image top-left
        val result = screenToNormalized(
            Offset(0f, 0f), 1080f, 1920f, 1080f, 1920f,
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, 0f, 0f, 1f
        )
        assertNotNull(result)
        assertEquals(0.0f, result!!.first, epsilon)
        assertEquals(0.0f, result.second, epsilon)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun assertOffset(actual: Offset, expectedX: Float, expectedY: Float) {
        assertEquals("x", expectedX, actual.x, epsilon)
        assertEquals("y", expectedY, actual.y, epsilon)
    }
}
