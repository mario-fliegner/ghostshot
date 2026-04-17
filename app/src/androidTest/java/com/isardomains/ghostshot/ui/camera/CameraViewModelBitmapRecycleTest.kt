package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val eventReceived = CountDownLatch(1)

        // Register the collector before starting the capture so the SharedFlow emit
        // is never missed (replay = 0).
        CoroutineScope(Dispatchers.Default).launch {
            viewModel.uiEvent.first()
            eventReceived.countDown()
        }

        viewModel.onPhotoCaptured(bitmap, rotationDegrees = 0)

        assertTrue(
            "UiEvent not received within timeout — capture coroutine may have stalled",
            eventReceived.await(5, TimeUnit.SECONDS)
        )

        // recycle() runs in the finally block, which executes after emit() returns in the IO
        // coroutine. The test thread may resume before the finally block completes; poll briefly.
        val deadline = System.currentTimeMillis() + 2_000
        while (!bitmap.isRecycled && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("bitmap must be recycled after a no-rotation capture", bitmap.isRecycled)
    }

    /**
     * rotationDegrees != 0: rotateBitmap creates a new Bitmap (corrected !== bitmap).
     * The original bitmap must be recycled immediately after rotation (before save),
     * and therefore before the UiEvent is emitted.
     */
    @Test
    fun onPhotoCaptured_withRotation_recyclesOriginalBitmap() {
        // 4×3 source; 90° rotation produces a distinct 3×4 corrected Bitmap object.
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        val eventReceived = CountDownLatch(1)

        CoroutineScope(Dispatchers.Default).launch {
            viewModel.uiEvent.first()
            eventReceived.countDown()
        }

        viewModel.onPhotoCaptured(bitmap, rotationDegrees = 90)

        assertTrue(
            "UiEvent not received within timeout — capture coroutine may have stalled",
            eventReceived.await(5, TimeUnit.SECONDS)
        )

        // The original bitmap is recycled immediately after rotateBitmap returns, before save()
        // and emit(). It is guaranteed to be recycled by the time the event arrives.
        assertTrue(
            "original bitmap must be recycled after a rotation capture",
            bitmap.isRecycled
        )
    }
}
