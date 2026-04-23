package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that [CameraViewModel.onPhotoCaptured] recycles all Bitmap objects it receives or
 * creates, regardless of rotation and regardless of whether the MediaStore save succeeds.
 *
 * These tests must run as instrumentation tests because [Bitmap.isRecycled] and
 * [Bitmap.createBitmap] require a real Android runtime; JVM stubs do not support them.
 */
@RunWith(AndroidJUnit4::class)
class CameraViewModelBitmapRecycleTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        viewModel = CameraViewModel(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
    }

    /**
     * rotationDegrees == 0: rotateBitmap returns the original reference unchanged.
     * The single bitmap object must be recycled via the finally block.
     */
    @Test
    fun onPhotoCaptured_noRotation_recyclesBitmap() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        viewModel.onPhotoCaptured(bitmap, rotationDegrees = 0)

        val deadline = System.currentTimeMillis() + 2_000
        while (!bitmap.isRecycled && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("bitmap must be recycled after a no-rotation capture", bitmap.isRecycled)
    }

    /**
     * rotationDegrees != 0: rotateBitmap creates a new Bitmap (corrected !== bitmap).
     * The original bitmap must be recycled after rotation.
     */
    @Test
    fun onPhotoCaptured_withRotation_recyclesOriginalBitmap() {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)

        viewModel.onPhotoCaptured(bitmap, rotationDegrees = 90)

        val deadline = System.currentTimeMillis() + 2_000
        while (!bitmap.isRecycled && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(
            "original bitmap must be recycled after a rotation capture",
            bitmap.isRecycled
        )
    }
}
