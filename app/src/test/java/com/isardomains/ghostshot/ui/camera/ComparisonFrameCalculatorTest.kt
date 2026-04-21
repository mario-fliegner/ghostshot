// path: app/src/test/java/com/isardomains/ghostshot/ui/camera/ComparisonFrameCalculatorTest.kt
package com.isardomains.ghostshot.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Unit tests for [ComparisonFrameCalculator].
 *
 * Geometry fixture values are derived directly from the rendering logic in CameraScreen:
 * - COMPARE_WITH_PREVIEW uses CompareReferenceImage (fillScale = max(vw/iw, vh/ih), with Render-Clamp)
 * - SHOW_FULL_IMAGE uses ContentScale.Fit (fitScale = min(vw/iw, vh/ih), no clamp)
 */
class ComparisonFrameCalculatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun input(
        viewportWidth: Int = 1080,
        viewportHeight: Int = 1920,
        captureWidth: Int = 1080,
        captureHeight: Int = 1920,
        referenceOrientedWidth: Int = 1080,
        referenceOrientedHeight: Int = 1920,
        overlayOffsetX: Float = 0f,
        overlayOffsetY: Float = 0f,
        overlayScale: Float = 1f,
        displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
    ) = CalculatorInput(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        captureWidth = captureWidth,
        captureHeight = captureHeight,
        referenceOrientedWidth = referenceOrientedWidth,
        referenceOrientedHeight = referenceOrientedHeight,
        overlayOffsetX = overlayOffsetX,
        overlayOffsetY = overlayOffsetY,
        overlayScale = overlayScale,
        displayMode = displayMode
    )

    private fun NormalizedRect.assertValid() {
        assertTrue("left ($left) must be >= 0", left >= 0f)
        assertTrue("top ($top) must be >= 0", top >= 0f)
        assertTrue("right ($right) must be <= 1", right <= 1f)
        assertTrue("bottom ($bottom) must be <= 1", bottom <= 1f)
        assertTrue("left ($left) must be < right ($right)", left < right)
        assertTrue("top ($top) must be < bottom ($bottom)", top < bottom)
    }

    private fun assertFloat(expected: Double, actual: Float, delta: Float = 0.0001f) {
        assertEquals(expected.toFloat(), actual, delta)
    }

    // ── COMPARE_WITH_PREVIEW — Standard case ─────────────────────────────────────────────────

    @Test
    fun compareWithPreview_standardCase_viewport1080x1920_refMatchesViewport() {
        // Reference matches viewport aspect ratio exactly. fillScale = 1.
        // No translation, no clamp needed.
        val result = ComparisonFrameCalculator.calculate(input())
        assertNotNull(result)
        result!!

        // CaptureRect: capture same size as viewport, previewScale=1, full frame visible.
        result.captureRect.assertValid()
        assertFloat(0.0, result.captureRect.left)
        assertFloat(0.0, result.captureRect.top)
        assertFloat(1.0, result.captureRect.right)
        assertFloat(1.0, result.captureRect.bottom)

        // ReferenceRect: reference fills viewport exactly, all visible.
        result.referenceRect.assertValid()
        assertFloat(0.0, result.referenceRect.left)
        assertFloat(0.0, result.referenceRect.top)
        assertFloat(1.0, result.referenceRect.right)
        assertFloat(1.0, result.referenceRect.bottom)
    }

    @Test
    fun compareWithPreview_standardCase_widerReference_cropsTopBottom() {
        // Viewport 1080x1920 (portrait), reference 1920x1080 (landscape).
        // fillScale = max(1080/1920, 1920/1080) = max(0.5625, 1.7778) = 1.7778
        // displayedWidth=1920*1.7778=3413.3, displayedHeight=1080*1.7778=1920
        // scaledW=3413.3, scaledH=1920 (scale=1)
        // maxTranslationX = (3413.3-1080)/2 = 1166.7, maxTranslationY = 0
        // No offset → translationX=0, translationY=0
        // imgLeft = 1080/2 - 3413.3/2 = -1166.7, imgRight = 1080/2 + 3413.3/2 = 2246.7
        // imgTop = 0, imgBottom = 1920
        // visLeft=0, visTop=0, visRight=1080, visBottom=1920
        // refLeft=(0-(-1166.7))/3413.3=0.3417, refRight=(1080+1166.7)/3413.3=0.6583
        // refTop=0, refBottom=1
        val vw = 1080.0; val vh = 1920.0
        val iw = 1920.0; val ih = 1080.0
        val fillScale = max(vw / iw, vh / ih) // 1.7778
        val scaledW = iw * fillScale
        val imgLeft = vw / 2.0 - scaledW / 2.0
        val refLeft = (0.0 - imgLeft) / scaledW
        val refRight = (vw - imgLeft) / scaledW

        val result = ComparisonFrameCalculator.calculate(
            input(
                viewportWidth = 1080, viewportHeight = 1920,
                captureWidth = 1080, captureHeight = 1920,
                referenceOrientedWidth = 1920, referenceOrientedHeight = 1080,
                overlayOffsetX = 0f, overlayOffsetY = 0f, overlayScale = 1f
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        assertFloat(refLeft, result.referenceRect.left)
        assertFloat(0.0, result.referenceRect.top)
        assertFloat(refRight, result.referenceRect.right)
        assertFloat(1.0, result.referenceRect.bottom)
    }

    @Test
    fun compareWithPreview_clampActive_offsetClamped() {
        // Viewport 1080x1920, reference 1080x1920, scale=2.
        // scaledW=2160, scaledH=3840
        // maxTransX=(2160-1080)/2=540, maxTransY=(3840-1920)/2=960
        // offsetX=0.5 → requestedTransX=0.5*1080=540 → clamped to 540 (exactly at boundary)
        // offsetY=0.5 → requestedTransY=0.5*1920=960 → clamped to 960
        val vw = 1080.0; val vh = 1920.0
        val iw = 1080.0; val ih = 1920.0
        val scale = 2.0
        val fillScale = max(vw / iw, vh / ih) // 1.0
        val scaledW = iw * fillScale * scale
        val scaledH = ih * fillScale * scale
        val maxTX = max(0.0, (scaledW - vw) / 2.0)
        val maxTY = max(0.0, (scaledH - vh) / 2.0)
        val tX = (0.5 * vw).coerceIn(-maxTX, maxTX)
        val tY = (0.5 * vh).coerceIn(-maxTY, maxTY)
        val imgLeft = vw / 2.0 + tX - scaledW / 2.0
        val imgTop = vh / 2.0 + tY - scaledH / 2.0
        val refLeft = (0.0 - imgLeft) / scaledW
        val refTop = (0.0 - imgTop) / scaledH
        val refRight = (vw - imgLeft) / scaledW
        val refBottom = (vh - imgTop) / scaledH

        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayOffsetX = 0.5f, overlayOffsetY = 0.5f, overlayScale = 2f
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        assertFloat(refLeft.coerceIn(0.0, 1.0), result.referenceRect.left)
        assertFloat(refTop.coerceIn(0.0, 1.0), result.referenceRect.top)
        assertFloat(refRight.coerceIn(0.0, 1.0), result.referenceRect.right)
        assertFloat(refBottom.coerceIn(0.0, 1.0), result.referenceRect.bottom)
    }

    @Test
    fun compareWithPreview_scaleGreaterThanOne_zoomedIn_refsSubregion() {
        val result = ComparisonFrameCalculator.calculate(
            input(overlayScale = 1.5f)
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
        // At scale>1 with no offset, we see the center portion of the reference.
        assertTrue("refLeft should be > 0 when zoomed in", result.referenceRect.left > 0f)
        assertTrue("refTop should be > 0 when zoomed in", result.referenceRect.top > 0f)
        assertTrue("refRight should be < 1 when zoomed in", result.referenceRect.right < 1f)
        assertTrue("refBottom should be < 1 when zoomed in", result.referenceRect.bottom < 1f)
    }

    @Test
    fun compareWithPreview_rectsRemainInBounds() {
        val result = ComparisonFrameCalculator.calculate(
            input(overlayOffsetX = 0.3f, overlayOffsetY = -0.2f, overlayScale = 1.8f)
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
    }

    // ── SHOW_FULL_IMAGE — Standard case ──────────────────────────────────────────────────────

    @Test
    fun showFullImage_standardCase_matchingAspect_fullReferenceVisible() {
        // Viewport 1080x1920, reference 1080x1920, scale=1, offset=0.
        // fitScale=1, image fills viewport exactly, fully visible.
        val result = ComparisonFrameCalculator.calculate(
            input(displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
        assertFloat(0.0, result.referenceRect.left)
        assertFloat(0.0, result.referenceRect.top)
        assertFloat(1.0, result.referenceRect.right)
        assertFloat(1.0, result.referenceRect.bottom)
    }

    @Test
    fun showFullImage_partiallyVisible_offsetPushesPartOutOfViewport() {
        // Viewport 1080x1920, reference 1080x1920, scale=1, offsetX=0.3 (no clamp in SHOW_FULL_IMAGE).
        // translationX = 0.3*1080=324
        // imgLeft=1080/2+324-1080/2=324, imgRight=324+1080=1404
        // visLeft=324, visRight=min(1080,1404)=1080
        // refLeft=(324-324)/1080=0, refRight=(1080-324)/1080=0.7
        val vw = 1080.0; val vh = 1920.0
        val iw = 1080.0; val ih = 1920.0
        val scale = 1.0
        val fitScale = min(vw / iw, vh / ih) // 1.0
        val scaledW = iw * fitScale * scale
        val scaledH = ih * fitScale * scale
        val tX = 0.3 * vw  // no clamp
        val tY = 0.0
        val imgLeft = vw / 2.0 + tX - scaledW / 2.0
        val imgTop = vh / 2.0 + tY - scaledH / 2.0
        val visLeft = max(0.0, imgLeft)
        val visTop = max(0.0, imgTop)
        val visRight = min(vw, imgLeft + scaledW)
        val visBottom = min(vh, imgTop + scaledH)
        val refLeft = (visLeft - imgLeft) / scaledW
        val refTop = (visTop - imgTop) / scaledH
        val refRight = (visRight - imgLeft) / scaledW
        val refBottom = (visBottom - imgTop) / scaledH

        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayOffsetX = 0.3f, overlayOffsetY = 0f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
        assertFloat(refLeft, result.referenceRect.left)
        assertFloat(refTop, result.referenceRect.top)
        assertFloat(refRight, result.referenceRect.right)
        assertFloat(refBottom, result.referenceRect.bottom)
    }

    @Test
    fun showFullImage_completelyOutsideViewport_returnsNull() {
        // offsetX=1.5 → translationX=1.5*1080=1620 → image entirely to the right of viewport
        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayOffsetX = 1.5f, overlayOffsetY = 0f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNull(result)
    }

    @Test
    fun showFullImage_completelyAboveViewport_returnsNull() {
        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayOffsetX = 0f, overlayOffsetY = -1.5f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNull(result)
    }

    @Test
    fun showFullImage_scaleGreaterThanOne_zoomedIn_subregion() {
        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayScale = 2f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
        // Zoomed in: only center portion of reference visible
        assertTrue(result.referenceRect.left > 0f)
        assertTrue(result.referenceRect.right < 1f)
    }

    @Test
    fun showFullImage_noClamp_offsetBeyondCompareClampBoundary() {
        // Verify SHOW_FULL_IMAGE does NOT clamp: apply same offset that would be clamped in COMPARE mode.
        // In COMPARE: offsetX=0.5 → clamp to maxTranslation boundary.
        // In SHOW_FULL: offsetX=0.5 → unclamped translation = 0.5*1080=540.
        val compareResult = ComparisonFrameCalculator.calculate(
            input(overlayOffsetX = 0.5f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        )
        val showResult = ComparisonFrameCalculator.calculate(
            input(overlayOffsetX = 0.5f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        )
        // COMPARE_WITH_PREVIEW scale=1 → maxTranslationX=0 → translationX=0 → ref centered
        // SHOW_FULL_IMAGE → unclamped shift → partially visible, refLeft=0
        assertNotNull(compareResult)
        assertNotNull(showResult)
        // The two results should differ because clamp behavior differs
        assertTrue(
            compareResult!!.referenceRect.left != showResult!!.referenceRect.left ||
            compareResult.referenceRect.right != showResult.referenceRect.right
        )
    }

    @Test
    fun showFullImage_rectsRemainInBounds() {
        val result = ComparisonFrameCalculator.calculate(
            input(
                overlayOffsetX = -0.2f, overlayOffsetY = 0.1f, overlayScale = 1.3f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
    }

    // ── EDGE CASES ────────────────────────────────────────────────────────────────────────────

    @Test
    fun edgeCase_viewportWidthZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(viewportWidth = 0)))
    }

    @Test
    fun edgeCase_viewportHeightZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(viewportHeight = 0)))
    }

    @Test
    fun edgeCase_viewportBothZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(viewportWidth = 0, viewportHeight = 0)))
    }

    @Test
    fun edgeCase_captureWidthZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(captureWidth = 0)))
    }

    @Test
    fun edgeCase_captureHeightZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(captureHeight = 0)))
    }

    @Test
    fun edgeCase_referenceWidthZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(referenceOrientedWidth = 0)))
    }

    @Test
    fun edgeCase_referenceHeightZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(referenceOrientedHeight = 0)))
    }

    @Test
    fun edgeCase_overlayScaleZero_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(overlayScale = 0f)))
    }

    @Test
    fun edgeCase_overlayScaleNegative_returnsNull() {
        assertNull(ComparisonFrameCalculator.calculate(input(overlayScale = -1f)))
    }

    @Test
    fun edgeCase_compareWithPreview_referenceCompletelyOutsideViewport_returnsNull() {
        // Scale=1, reference larger than viewport in both dims → fillScale > 1.
        // With a very large offset the clamp will push image back into viewport for COMPARE mode,
        // so we test SHOW_FULL_IMAGE with extreme offset instead:
        assertNull(
            ComparisonFrameCalculator.calculate(
                input(
                    overlayOffsetX = 2.0f, overlayOffsetY = 2.0f,
                    displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
                )
            )
        )
    }

    @Test
    fun edgeCase_showFullImage_verySmallReference_fitScaleTiny() {
        // Reference 10x10 in 1080x1920 viewport → fitScale=min(108,192)=108
        // Scaled display: 1080x1080, centered. With no offset, visible area is the full ref image.
        val result = ComparisonFrameCalculator.calculate(
            input(
                referenceOrientedWidth = 10, referenceOrientedHeight = 10,
                overlayOffsetX = 0f, overlayOffsetY = 0f, overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNotNull(result)
        result!!
        result.referenceRect.assertValid()
        result.captureRect.assertValid()
        assertFloat(0.0, result.referenceRect.left)
        assertFloat(0.0, result.referenceRect.top)
        assertFloat(1.0, result.referenceRect.right)
        assertFloat(1.0, result.referenceRect.bottom)
    }

    @Test
    fun showFullImage_narrowReference_captureRectFixedValues() {
        // Viewport 1000x1000, capture 1000x1000, reference 500x1000, offset=0, scale=1.
        //
        // fitScale = min(1000/500, 1000/1000) = min(2.0, 1.0) = 1.0
        // displayedWidth=500, displayedHeight=1000
        // translationX=0, translationY=0  (no clamp in SHOW_FULL_IMAGE)
        // imageCenterX=500, imageCenterY=500
        // imgLeft=250, imgTop=0, imgRight=750, imgBottom=1000
        // visLeft=250, visTop=0, visRight=750, visBottom=1000
        //
        // captureRect (previewScale = max(1000/1000, 1000/1000) = 1.0, capOriginX=0, capOriginY=0):
        //   left   = (250-0)/1.0/1000 = 0.25
        //   top    = (0  -0)/1.0/1000 = 0.0
        //   right  = (750-0)/1.0/1000 = 0.75
        //   bottom = (1000-0)/1.0/1000= 1.0
        val result = ComparisonFrameCalculator.calculate(
            CalculatorInput(
                viewportWidth = 1000,
                viewportHeight = 1000,
                captureWidth = 1000,
                captureHeight = 1000,
                referenceOrientedWidth = 500,
                referenceOrientedHeight = 1000,
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                overlayScale = 1f,
                displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            )
        )
        assertNotNull(result)
        result!!
        result.captureRect.assertValid()
        assertEquals(0.25f, result.captureRect.left,   0.0001f)
        assertEquals(0.0f,  result.captureRect.top,    0.0001f)
        assertEquals(0.75f, result.captureRect.right,  0.0001f)
        assertEquals(1.0f,  result.captureRect.bottom, 0.0001f)
    }

    // ── PRECISION / ROBUSTNESS ────────────────────────────────────────────────────────────────

    @Test
    fun precision_normalValidInputDoesNotProduceZeroAreaRect() {
        // Verifies that Double-based math does not collapse to zero-area through float rounding.
        val inputs = listOf(
            input(overlayScale = 1.0000001f),
            input(overlayOffsetX = 0.00001f, overlayOffsetY = 0.00001f),
            input(overlayScale = 0.9999999f),
            input(
                viewportWidth = 1079, viewportHeight = 1919,
                captureWidth = 1080, captureHeight = 1920
            )
        )
        for (i in inputs) {
            val result = ComparisonFrameCalculator.calculate(i)
            if (result != null) {
                result.captureRect.assertValid()
                result.referenceRect.assertValid()
            }
        }
    }

    @Test
    fun precision_doubleArithmeticDoesNotProduceOutOfBoundsRect() {
        // Stress test with many offset/scale combinations, none should produce out-of-[0,1] values.
        val offsets = listOf(-0.5f, -0.25f, 0f, 0.25f, 0.5f)
        val scales = listOf(0.5f, 1f, 1.5f, 2f, 3f)
        val modes = ReferenceImageDisplayMode.entries
        for (ox in offsets) {
            for (oy in offsets) {
                for (s in scales) {
                    for (mode in modes) {
                        val result = ComparisonFrameCalculator.calculate(
                            input(overlayOffsetX = ox, overlayOffsetY = oy, overlayScale = s, displayMode = mode)
                        )
                        if (result != null) {
                            result.captureRect.assertValid()
                            result.referenceRect.assertValid()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun precision_captureRectIdenticalForBothModesWhenFullViewportVisible() {
        // When the full viewport is used as the capture rect source (COMPARE_WITH_PREVIEW),
        // previewScale=1 and the full frame is captured → captureRect should be [0,0,1,1].
        val result = ComparisonFrameCalculator.calculate(
            input(
                viewportWidth = 1080, viewportHeight = 1920,
                captureWidth = 1080, captureHeight = 1920,
                overlayScale = 1f, overlayOffsetX = 0f, overlayOffsetY = 0f,
                displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
            )
        )
        assertNotNull(result)
        assertFloat(0.0, result!!.captureRect.left)
        assertFloat(0.0, result.captureRect.top)
        assertFloat(1.0, result.captureRect.right)
        assertFloat(1.0, result.captureRect.bottom)
    }

    @Test
    fun precision_captureRectReflectsCenterCropWhenCaptureWiderThanViewport() {
        // Viewport 1080x1920, capture 1440x1920 (wider).
        // previewScale = max(1080/1440, 1920/1920) = max(0.75, 1.0) = 1.0
        // capOriginX = (1080 - 1440*1.0)/2 = -180
        // Full viewport → capLeft=(0-(-180))/1.0/1440=180/1440=0.125
        //                  capRight=(1080+180)/1440=0.875
        val result = ComparisonFrameCalculator.calculate(
            input(
                viewportWidth = 1080, viewportHeight = 1920,
                captureWidth = 1440, captureHeight = 1920,
                overlayOffsetX = 0f, overlayOffsetY = 0f, overlayScale = 1f,
                referenceOrientedWidth = 1080, referenceOrientedHeight = 1920
            )
        )
        assertNotNull(result)
        result!!
        result.captureRect.assertValid()
        assertFloat(180.0 / 1440.0, result.captureRect.left)
        assertFloat(0.0, result.captureRect.top)
        assertFloat((1080.0 + 180.0) / 1440.0, result.captureRect.right)
        assertFloat(1.0, result.captureRect.bottom)
    }
}
