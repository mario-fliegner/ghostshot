// path: app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt
package com.isardomains.sameview.ui.video

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.video.VideoRenderConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CreateVideoViewModelTest {

    private val testSessionId = "2026-06-03_10-00-00"
    private val fakeUri: Uri = mock()
    private lateinit var viewModel: CreateVideoViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mock {
            on { filesDir } doReturn File("/fake/files")
            on { contentResolver } doReturn mock<ContentResolver>()
        }
        settingsRepository = mock {
            on { brandingEnabled } doReturn flowOf(true)
        }
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        viewModel = CreateVideoViewModel(savedStateHandle, context, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // T-U-15: startExport() transitions from Configuring to Rendering

    @Test
    fun startExport_transitionsToRendering() = runTest {
        // Gate suspends the pipeline so state stays in Rendering during the assertion.
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()

        // State must be Rendering while the pipeline is suspended.
        assertTrue(
            "Expected Rendering, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Rendering
        )

        // Allow the pipeline to complete so the test scope ends cleanly.
        gate.complete(Unit)
        advanceUntilIdle()
    }

    // T-U-16: Pipeline success transitions to Preview with the MediaStore URI

    @Test
    fun startExport_pipelineSuccess_transitionsToPreview() = runTest {
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }

        viewModel.startExport()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Preview, got $state", state is CreateVideoState.Preview)
        assertEquals(fakeUri, (state as CreateVideoState.Preview).videoUri)
    }

    // T-U-17: Pipeline failure returns to Configuring and emits error Snackbar event

    @Test
    fun startExport_pipelineFailure_returnsToConfiguringWithErrorEvent() = runTest {
        viewModel.pipelineRunner = { _, _, _, _ ->
            Result.failure(RuntimeException("encode failed"))
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent() // Subscribe collector before startExport emits.

        viewModel.startExport()
        advanceUntilIdle()

        assertTrue(
            "Expected Configuring, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
        assertTrue("Expected at least one event", collectedEvents.isNotEmpty())
        val event = collectedEvents.first()
        assertTrue("Expected ShowSnackbar event, got $event", event is CreateVideoEvent.ShowSnackbar)
        assertEquals(
            R.string.create_video_error_render_failed,
            (event as CreateVideoEvent.ShowSnackbar).messageResId
        )

        collectJob.cancel()
    }

    // Additional: brandingEnabled default is loaded from SettingsRepository (default true)

    @Test
    fun initialState_brandingEnabledIsTrue() {
        val state = viewModel.state.value
        assertTrue("Expected Configuring", state is CreateVideoState.Configuring)
        assertTrue(
            "Expected brandingEnabled = true",
            (state as CreateVideoState.Configuring).brandingEnabled
        )
    }

    // Additional: sessionId is read from SavedStateHandle

    @Test
    fun sessionId_isReadFromSavedStateHandle() {
        assertEquals(testSessionId, viewModel.sessionId)
    }

    // Additional: branding toggle writes to SettingsRepository

    @Test
    fun updateBrandingEnabled_false_callsRepository() = runTest {
        viewModel.updateBrandingEnabled(false)
        advanceUntilIdle()
        verify(settingsRepository).setBrandingEnabled(false)
    }

    // Additional: pipelineRunner receives the correct sessionDir derived from sessionId

    @Test
    fun startExport_pipelineReceivedCorrectSessionDir() = runTest {
        var capturedDir: File? = null
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _: VideoRenderConfig, dir: File, _: (Float) -> Unit, _ ->
            capturedDir = dir
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()

        assertEquals(
            File("/fake/files/sessions/$testSessionId"),
            capturedDir
        )

        gate.complete(Unit)
        advanceUntilIdle()
    }

    // T-U-18: deleteVideo success — state transitions from Preview to Configuring

    @Test
    fun deleteVideo_success_transitionsToConfiguring() = runTest {
        // Arrange: put ViewModel in Preview state
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }
        viewModel.startExport()
        advanceUntilIdle()
        assertTrue("Expected Preview state before delete", viewModel.state.value is CreateVideoState.Preview)

        // Replace delete runner with success stub
        viewModel.videoDeleteRunner = { _ -> true }

        // Act
        viewModel.deleteVideo()
        advanceUntilIdle()

        // Assert
        assertTrue(
            "Expected Configuring after successful delete, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
    }

    // T-U-19: deleteVideo failure — Preview state unchanged, create_video_delete_failed emitted

    @Test
    fun deleteVideo_failure_staysInPreviewAndEmitsSnackbar() = runTest {
        // Arrange: put ViewModel in Preview state
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }
        viewModel.startExport()
        advanceUntilIdle()
        assertTrue("Expected Preview state before delete", viewModel.state.value is CreateVideoState.Preview)

        // Replace delete runner with failure stub
        viewModel.videoDeleteRunner = { _ -> false }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        // Act
        viewModel.deleteVideo()
        advanceUntilIdle()

        // Assert: state stays Preview
        assertTrue(
            "Expected Preview state after failed delete, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Preview
        )

        // Assert: snackbar event emitted
        assertTrue("Expected at least one event after delete failure", collectedEvents.isNotEmpty())
        val event = collectedEvents.first()
        assertTrue("Expected ShowSnackbar", event is CreateVideoEvent.ShowSnackbar)
        assertEquals(
            R.string.create_video_delete_failed,
            (event as CreateVideoEvent.ShowSnackbar).messageResId
        )

        collectJob.cancel()
    }

    // Additional: cancelExport during Rendering returns to Configuring

    @Test
    fun cancelExport_fromRendering_returnsToConfiguring() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()
        assertTrue("Expected Rendering before cancel", viewModel.state.value is CreateVideoState.Rendering)

        viewModel.cancelExport()
        advanceUntilIdle()

        assertTrue(
            "Expected Configuring after cancelExport, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
    }

    // Additional: cancellation must not emit create_video_error_render_failed snackbar

    @Test
    fun cancellation_doesNotEmitRenderFailedSnackbar() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        viewModel.startExport()
        viewModel.cancelExport()
        advanceUntilIdle()

        assertTrue(
            "No snackbar events should be emitted on cancel, got: $collectedEvents",
            collectedEvents.isEmpty()
        )

        collectJob.cancel()
    }

    // T-U-20: quality fallback emits create_video_quality_fallback_notice Snackbar

    @Test
    fun startExport_qualityFallback_emitsFallbackNoticeSnackbar() = runTest {
        viewModel.pipelineRunner = { _, _, _, onQualityFallback ->
            onQualityFallback()
            Result.success(fakeUri)
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        viewModel.startExport()
        advanceUntilIdle()

        val snackbarEvents = collectedEvents.filterIsInstance<CreateVideoEvent.ShowSnackbar>()
        assertTrue(
            "Expected create_video_quality_fallback_notice snackbar, got: $collectedEvents",
            snackbarEvents.any { it.messageResId == R.string.create_video_quality_fallback_notice }
        )

        collectJob.cancel()
    }
}
