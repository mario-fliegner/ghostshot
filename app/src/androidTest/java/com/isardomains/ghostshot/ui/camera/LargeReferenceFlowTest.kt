package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LargeReferenceFlowTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var referenceFile: File

    @Before
    fun setUp() {
        referenceFile = File(appContext.cacheDir, "large_reference_portrait_tall.jpg")
        testContext.assets.open("portrait_tall.jpg").use { input ->
            referenceFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    @After
    fun tearDown() {
        if (::referenceFile.isInitialized) {
            referenceFile.delete()
        }
    }

    @Test
    fun largeReferenceImage_capturePipelineCompletesWithoutStuckLock() {
        val viewModel = CameraViewModel(appContext)
        val referenceUri = Uri.fromFile(referenceFile)

        viewModel.onReferenceImageSelected(referenceUri)

        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.referenceImageUri == referenceUri
        }
        assertEquals(referenceUri, viewModel.uiState.value.referenceImageUri)

        assertTrue(viewModel.tryStartCapture())
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        viewModel.onPhotoCaptured(bitmap, rotationDegrees = 0)

        waitUntil(timeoutMillis = 10_000) {
            !viewModel.uiState.value.isCaptureInProgress
        }
        assertFalse(viewModel.uiState.value.isCaptureInProgress)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }
}
