package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.settings.SettingsRepository
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
        val prefs = PreferenceDataStoreFactory.create {
            File(appContext.cacheDir, "settings_test_${System.nanoTime()}.preferences_pb")
        }
        val viewModel = CameraViewModel(appContext, SettingsRepository(prefs))
        val referenceUri = Uri.fromFile(referenceFile)

        viewModel.onReferenceImageSelected(referenceUri)

        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.referenceImageUri == referenceUri
        }
        assertEquals(referenceUri, viewModel.uiState.value.referenceImageUri)

        val captureToken = viewModel.tryStartCapture()
        assertTrue(captureToken != null)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        viewModel.onPhotoCaptured(captureToken!!, bitmap, rotationDegrees = 0)

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
