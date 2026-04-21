// path: app/src/androidTest/java/com/isardomains/ghostshot/core/image/ComparisonCropProcessorTest.kt
package com.isardomains.ghostshot.core.image

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isardomains.ghostshot.ui.camera.ComparisonFrame
import com.isardomains.ghostshot.ui.camera.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComparisonCropProcessorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun rect(left: Float, top: Float, right: Float, bottom: Float): NormalizedRect =
        NormalizedRect(left = left, top = top, right = right, bottom = bottom)

    private fun frame(
        cL: Float, cT: Float, cR: Float, cB: Float,
        rL: Float, rT: Float, rR: Float, rB: Float
    ): ComparisonFrame = ComparisonFrame(
        captureRect   = rect(cL, cT, cR, cB),
        referenceRect = rect(rL, rT, rR, rB)
    )

    // ── Standard cases ────────────────────────────────────────────────────────────────────────

    /** Full-image rects: captureCrop must be scaled to match the reference bitmap's full size. */
    @Test
    fun fullImageCrop_outputMatchesReferenceDimensions() {
        val captureBitmap   = bitmap(400, 300)
        val referenceBitmap = bitmap(800, 600)
        val frame = frame(0f, 0f, 1f, 1f,  0f, 0f, 1f, 1f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(referenceBitmap.width,  result!!.captureCrop.width)
            assertEquals(referenceBitmap.height, result.captureCrop.height)
            assertEquals(referenceBitmap.width,  result.referenceCrop.width)
            assertEquals(referenceBitmap.height, result.referenceCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** Symmetric partial rects on equal-sized bitmaps: output dimensions must match. */
    @Test
    fun symmetricPartialCrop_outputDimensionsMatch() {
        val captureBitmap   = bitmap(200, 200)
        val referenceBitmap = bitmap(200, 200)
        val frame = frame(0.25f, 0.25f, 0.75f, 0.75f,  0.25f, 0.25f, 0.75f, 0.75f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /**
     * captureRect is wide (full width, half height), referenceRect is tall (half width, full height).
     * The capture crop is scaled to the reference crop size — intentional distortion.
     */
    @Test
    fun differentAspectRatios_resultNotNullAndOutputSizesMatch() {
        val captureBitmap   = bitmap(400, 400)
        val referenceBitmap = bitmap(400, 400)
        val frame = frame(
            cL = 0f, cT = 0f, cR = 1f,   cB = 0.5f,
            rL = 0f, rT = 0f, rR = 0.5f, rB = 1f
        )

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** Large capture bitmap vs. small reference bitmap: captureCrop scaled down to reference crop size. */
    @Test
    fun differentBitmapSizes_captureCropScaledToReferenceCropSize() {
        val captureBitmap   = bitmap(4000, 3000)
        val referenceBitmap = bitmap(400, 300)
        val frame = frame(0.1f, 0.1f, 0.9f, 0.9f,  0.1f, 0.1f, 0.9f, 0.9f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** The size invariant captureCrop == referenceCrop must hold across a range of configurations. */
    @Test
    fun outputSizeInvariant_holdsForMultipleConfigurations() {
        data class Case(
            val captureW: Int, val captureH: Int,
            val refW: Int,     val refH: Int,
            val captureRect: NormalizedRect,
            val referenceRect: NormalizedRect
        )

        val cases = listOf(
            Case(200, 150, 100, 75,
                rect(0f, 0f, 1f, 1f),                 rect(0f, 0f, 1f, 1f)),
            Case(300, 400, 600, 800,
                rect(0.1f, 0.1f, 0.9f, 0.9f),         rect(0.2f, 0.2f, 0.8f, 0.8f)),
            Case(1920, 1080, 1024, 768,
                rect(0.25f, 0.25f, 0.75f, 0.75f),     rect(0f, 0f, 0.5f, 0.5f)),
            Case(100, 100, 200, 150,
                rect(0f, 0f, 0.5f, 1f),               rect(0f, 0f, 1f, 0.5f))
        )

        for (case in cases) {
            val captureBitmap   = bitmap(case.captureW, case.captureH)
            val referenceBitmap = bitmap(case.refW, case.refH)
            val frame = ComparisonFrame(case.captureRect, case.referenceRect)

            val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
            try {
                assertNotNull("Expected non-null result for case $case", result)
                assertEquals(
                    "captureCrop.width must equal referenceCrop.width for case $case",
                    result!!.referenceCrop.width, result.captureCrop.width
                )
                assertEquals(
                    "captureCrop.height must equal referenceCrop.height for case $case",
                    result.referenceCrop.height, result.captureCrop.height
                )
            } finally {
                result?.captureCrop?.recycle()
                result?.referenceCrop?.recycle()
                captureBitmap.recycle()
                referenceBitmap.recycle()
            }
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────────────────────

    /** Rects with exact 0.0 / 1.0 boundaries must not cause out-of-bounds errors. */
    @Test
    fun rectAtExactBounds_noOutOfBoundsError() {
        val captureBitmap   = bitmap(100, 80)
        val referenceBitmap = bitmap(100, 80)
        val frame = frame(0.0f, 0.0f, 1.0f, 1.0f,  0.0f, 0.0f, 1.0f, 1.0f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /**
     * rect.right = 0.995f on a 100px bitmap:
     * 0.995f stored as ≈0.99499994, * 100 ≈ 99.499994 → ceil = 100 → clamped to 100.
     * Result must not be null; no IllegalArgumentException due to out-of-bounds access.
     */
    @Test
    fun floatRoundingJustBelowBoundary_ceilClampedCorrectly() {
        val captureBitmap   = bitmap(100, 100)
        val referenceBitmap = bitmap(100, 100)
        val frame = frame(0f, 0f, 0.995f, 0.995f,  0f, 0f, 0.995f, 0.995f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /**
     * rect.left=0.1f, rect.right=0.5f on a 100px bitmap:
     * floor(0.1 * 100)=10, ceil(0.5 * 100)=50 → width=40.
     * Validates exact floor/ceil semantics at integral pixel boundaries.
     */
    @Test
    fun floatExactlyOnPixelBoundary_floorAndCeilProduceCorrectPixelCoords() {
        val captureBitmap   = bitmap(100, 100)
        val referenceBitmap = bitmap(100, 100)
        val frame = frame(0.1f, 0.1f, 0.5f, 0.5f,  0.1f, 0.1f, 0.5f, 0.5f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(40, result!!.referenceCrop.width)
            assertEquals(40, result.referenceCrop.height)
            assertEquals(result.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /**
     * referenceRect spans exactly one pixel:
     * left=0.3f, right=0.4f on 10px → floor(3.0)=3, ceil(4.0)=4 → width=1.
     * A 1×1 reference crop is valid; captureCrop is scaled to 1×1.
     */
    @Test
    fun minimalCrop_oneByOne_resultIsNotNull() {
        val captureBitmap   = bitmap(20, 20)
        val referenceBitmap = bitmap(10, 10)
        val frame = ComparisonFrame(
            captureRect   = rect(0f, 0f, 1f, 1f),
            referenceRect = rect(0.3f, 0.3f, 0.4f, 0.4f)
        )

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(1, result!!.referenceCrop.width)
            assertEquals(1, result.referenceCrop.height)
            assertEquals(1, result.captureCrop.width)
            assertEquals(1, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** Irregular (non-round) bitmap dimensions must not cause a crash or degenerate output. */
    @Test
    fun irregularBitmapDimensions_noCrashAndSizesMatch() {
        val captureBitmap   = bitmap(101, 73)
        val referenceBitmap = bitmap(101, 73)
        val frame = frame(0.1f, 0.1f, 0.9f, 0.9f,  0.1f, 0.1f, 0.9f, 0.9f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNotNull(result)
            assertEquals(result!!.referenceCrop.width,  result.captureCrop.width)
            assertEquals(result.referenceCrop.height, result.captureCrop.height)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    // ── Error cases ───────────────────────────────────────────────────────────────────────────

    /**
     * referenceRect with left == right: floor(0.5*100)=50, ceil(0.5*100)=50 → width=0.
     * Process must return null.
     */
    @Test
    fun degenerateReferenceRect_returnsNull() {
        val captureBitmap   = bitmap(100, 100)
        val referenceBitmap = bitmap(100, 100)
        val frame = ComparisonFrame(
            captureRect   = rect(0f, 0f, 1f, 1f),
            referenceRect = NormalizedRect(left = 0.5f, top = 0.0f, right = 0.5f, bottom = 1.0f)
        )

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNull(result)
        } finally {
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /**
     * captureRect with top == bottom: floor(0.5*100)=50, ceil(0.5*100)=50 → height=0.
     * Process must return null.
     */
    @Test
    fun degenerateCaptureRect_returnsNull() {
        val captureBitmap   = bitmap(100, 100)
        val referenceBitmap = bitmap(100, 100)
        val frame = ComparisonFrame(
            captureRect   = NormalizedRect(left = 0.0f, top = 0.5f, right = 1.0f, bottom = 0.5f),
            referenceRect = rect(0f, 0f, 1f, 1f)
        )

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNull(result)
        } finally {
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** Input bitmaps must not be recycled by the processor after a successful call. */
    @Test
    fun inputBitmapsNotRecycledAfterSuccessfulCall() {
        val captureBitmap   = bitmap(200, 150)
        val referenceBitmap = bitmap(200, 150)
        val frame = frame(0f, 0f, 1f, 1f,  0f, 0f, 1f, 1f)

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertFalse("captureBitmap must not be recycled by the processor",   captureBitmap.isRecycled)
            assertFalse("referenceBitmap must not be recycled by the processor", referenceBitmap.isRecycled)
        } finally {
            result?.captureCrop?.recycle()
            result?.referenceCrop?.recycle()
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }

    /** Input bitmaps must not be recycled by the processor even when the call returns null. */
    @Test
    fun inputBitmapsNotRecycledAfterNullResult() {
        val captureBitmap   = bitmap(100, 100)
        val referenceBitmap = bitmap(100, 100)
        val frame = ComparisonFrame(
            captureRect   = rect(0f, 0f, 1f, 1f),
            referenceRect = NormalizedRect(left = 0.5f, top = 0.5f, right = 0.5f, bottom = 0.5f)
        )

        val result = ComparisonCropProcessor.process(captureBitmap, referenceBitmap, frame)
        try {
            assertNull(result)
            assertFalse("captureBitmap must not be recycled by the processor",   captureBitmap.isRecycled)
            assertFalse("referenceBitmap must not be recycled by the processor", referenceBitmap.isRecycled)
        } finally {
            captureBitmap.recycle()
            referenceBitmap.recycle()
        }
    }
}
