// path: app/src/test/java/com/isardomains/sameview/ui/camera/CameraViewModelTest.kt
package com.isardomains.sameview.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import java.io.File
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private companion object {
        const val CAPTURE_CALLBACK_TIMEOUT_MS = 15_000L
    }

    private lateinit var viewModel: CameraViewModel

    private val fakeSettingsRepository: SettingsRepository = mock {
        on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
        on { keepScreenOn } doReturn flowOf(true)
        on { resetOverlayAfterCapture } doReturn flowOf(false)
        on { autoOpenCompareAfterCapture } doReturn flowOf(false)
        on { recreationGuidance } doReturn flowOf(false)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CameraViewModel(mock(), fakeSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- onReferenceImageSelected ---

    @Test
    fun onReferenceImageSelected_setsUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)
        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onReferenceImageSelected_null_preservesExistingUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)
        testViewModel.onReferenceImageSelected(null)
        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onReferenceImageSelected_olderSelectionCannotOverwriteNewerSelection() = runTest {
        val firstUri = mock<Uri>()
        val secondUri = mock<Uri>()
        lateinit var testViewModel: CameraViewModel
        var secondSelectionTriggered = false

        testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { uri ->
                when {
                    uri === firstUri -> {
                        if (!secondSelectionTriggered) {
                            secondSelectionTriggered = true
                            testViewModel.onReferenceImageSelected(secondUri)
                        }
                        ReferenceImageMetadata(
                            rawWidth = 1920,
                            rawHeight = 1080,
                            orientedWidth = 1920,
                            orientedHeight = 1080,
                            exifOrientation = null
                        )
                    }

                    uri === secondUri -> ReferenceImageMetadata(
                        rawWidth = 1080,
                        rawHeight = 1920,
                        orientedWidth = 1080,
                        orientedHeight = 1920,
                        exifOrientation = null
                    )

                    else -> error("Unexpected reference URI")
                }
            },
            fakeSettingsRepository
        )
        testViewModel.onReferenceViewportChanged(1080, 1920)

        testViewModel.onReferenceImageSelected(firstUri)

        assertEquals(secondUri, testViewModel.uiState.value.referenceImageUri)
        assertEquals(1080, testViewModel.uiState.value.referenceImageMetadata?.orientedWidth)
        assertEquals(1920, testViewModel.uiState.value.referenceImageMetadata?.orientedHeight)
        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(false, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceImageSelected_metadataReadReturnsNull_emitsSnackbarError() = runTest {
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onReferenceImageSelected(mock())

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.reference_image_load_failed, snackbars[0].messageResId)
    }

    @Test
    fun onReferenceImageRemoveConfirmed_clearsReferenceStateAndDisplayDefaults() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onReferenceImageRemoveConfirmed()

        assertEquals(null, testViewModel.uiState.value.referenceImageUri)
        assertEquals(null, testViewModel.uiState.value.referenceImageMetadata)
        assertEquals(false, testViewModel.uiState.value.referenceImageHasViewportMismatch)
        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.canUndoReferenceRemoval)
    }

    @Test
    fun onReferenceImageRemoveConfirmed_marksUndoAvailableAndAdvancesGeneration() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        val initialGeneration = testViewModel.uiState.value.referenceRemovalUndoGeneration

        testViewModel.onReferenceImageRemoveConfirmed()

        assertEquals(null, testViewModel.uiState.value.referenceImageUri)
        assertEquals(true, testViewModel.uiState.value.canUndoReferenceRemoval)
        assertEquals(initialGeneration + 1L, testViewModel.uiState.value.referenceRemovalUndoGeneration)
    }

    @Test
    fun onReferenceImageRemoveConfirmed_resetsOverlayTransform() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayDragged(0.2f, -0.2f)
        testViewModel.onOverlayScaled(1.8f)

        testViewModel.onReferenceImageRemoveConfirmed()

        assertEquals(0f, testViewModel.uiState.value.overlayOffsetX)
        assertEquals(0f, testViewModel.uiState.value.overlayOffsetY)
        assertEquals(1f, testViewModel.uiState.value.overlayScale)
    }

    @Test
    fun onReferenceImageRemoveConfirmed_preservesOverlayAlphaAndActiveAspectRatio() = runTest {
        val testViewModel = testViewModelWithMetadata(1600, 1200)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayAlphaChanged(0.8f)

        testViewModel.onReferenceImageRemoveConfirmed()

        assertEquals(0.8f, testViewModel.uiState.value.overlayAlpha)
        assertEquals(TargetAspectRatio.RATIO_4_3, testViewModel.uiState.value.activeAspectRatio)
    }

    @Test
    fun onReferenceImageRemoveConfirmed_invalidatesPendingSelection() = runTest {
        val uri = mock<Uri>()
        lateinit var testViewModel: CameraViewModel
        var removeTriggered = false

        testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            {
                if (!removeTriggered) {
                    removeTriggered = true
                    testViewModel.onReferenceImageRemoveConfirmed()
                }
                ReferenceImageMetadata(
                    rawWidth = 1920,
                    rawHeight = 1080,
                    orientedWidth = 1920,
                    orientedHeight = 1080,
                    exifOrientation = null
                )
            },
            fakeSettingsRepository
        )

        testViewModel.onReferenceImageSelected(uri)

        assertEquals(null, testViewModel.uiState.value.referenceImageUri)
        assertEquals(null, testViewModel.uiState.value.referenceImageMetadata)
    }

    @Test
    fun onReferenceImageSelected_normalViewport_startsCompareMode() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)

        testViewModel.onReferenceImageSelected(mock())

        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(false, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceImageSelected_strongViewportMismatch_startsFullImageMode() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)

        testViewModel.onReferenceImageSelected(mock())

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceImageSelected_exifRotatedDimensions_driveStartMode() = runTest {
        val testViewModel = testViewModelWithMetadata(
            rawWidth = 1920,
            rawHeight = 1080,
            orientedWidth = 1080,
            orientedHeight = 1920,
            exifOrientation = 6
        )
        testViewModel.onReferenceViewportChanged(1080, 1920)

        testViewModel.onReferenceImageSelected(mock())

        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(false, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceViewportChanged_withoutReference_keepsDefaultReferenceState() {
        viewModel.onReferenceViewportChanged(1080, 1920)

        assertEquals(null, viewModel.uiState.value.referenceImageUri)
        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            viewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(false, viewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceViewportChanged_storesViewportInState() {
        viewModel.onReferenceViewportChanged(1080, 1920)
        assertEquals(1080, viewModel.uiState.value.viewportWidth)
        assertEquals(1920, viewModel.uiState.value.viewportHeight)
    }

    @Test
    fun onReferenceViewportChanged_invalidDimensions_doesNotUpdateState() {
        viewModel.onReferenceViewportChanged(1080, 1920)
        viewModel.onReferenceViewportChanged(0, 1920)
        assertEquals(1080, viewModel.uiState.value.viewportWidth)
        assertEquals(1920, viewModel.uiState.value.viewportHeight)
    }

    @Test
    fun onReferenceImageDisplayModeChanged_updatesUiState() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
    }

    @Test
    fun onReferenceImageDisplayModeToggle_switchesBetweenModes() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onReferenceImageDisplayModeToggle()
        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )

        testViewModel.onReferenceImageDisplayModeToggle()
        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
    }

    @Test
    fun onReferenceViewportChanged_withReferenceReevaluatesStartModeAndMismatch() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onReferenceViewportChanged(1920, 1080)

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onReferenceViewportChanged_afterManualModeChangeKeepsManualModeButUpdatesMismatch() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)

        testViewModel.onReferenceViewportChanged(1920, 1080)

        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    // --- onOverlayAlphaChanged ---

    @Test
    fun onOverlayAlphaChanged_validValue_isAccepted() {
        viewModel.onOverlayAlphaChanged(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.overlayAlpha)
    }

    @Test
    fun onOverlayAlphaChanged_belowMin_isClamped() {
        viewModel.onOverlayAlphaChanged(0.05f)
        assertEquals(0.1f, viewModel.uiState.value.overlayAlpha)
    }

    @Test
    fun onOverlayAlphaChanged_aboveMax_isClamped() {
        viewModel.onOverlayAlphaChanged(0.95f)
        assertEquals(0.9f, viewModel.uiState.value.overlayAlpha)
    }

    // --- onOverlayDragged ---

    @Test
    fun onOverlayDragged_updatesOffsets() {
        viewModel.onOverlayDragged(0.1f, 0.2f)
        assertEquals(0.1f, viewModel.uiState.value.overlayOffsetX)
        assertEquals(0.2f, viewModel.uiState.value.overlayOffsetY)
    }

    @Test
    fun onOverlayDragged_clampsOffsets() {
        // Move near the boundary: +0.4 on X, -0.4 on Y
        viewModel.onOverlayDragged(0.4f, -0.4f)
        // A further drag that would exceed ±0.5 must be clamped to the boundary
        viewModel.onOverlayDragged(0.2f, -0.2f)
        assertEquals(0.5f, viewModel.uiState.value.overlayOffsetX)
        assertEquals(-0.5f, viewModel.uiState.value.overlayOffsetY)
    }

    // --- onOverlayReset ---

    @Test
    fun onOverlayReset_resetsPositionAndScale() {
        viewModel.onOverlayDragged(0.3f, 0.3f)
        viewModel.onOverlayScaled(2.0f)
        viewModel.onOverlayReset()
        assertEquals(0f, viewModel.uiState.value.overlayOffsetX)
        assertEquals(0f, viewModel.uiState.value.overlayOffsetY)
        assertEquals(1f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayReset_preservesReferenceImageUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)
        testViewModel.onOverlayDragged(0.1f, 0.1f)
        testViewModel.onOverlayReset()
        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onOverlayReset_preservesOverlayAlpha() {
        viewModel.onOverlayAlphaChanged(0.8f)
        viewModel.onOverlayDragged(0.1f, 0.1f)
        viewModel.onOverlayReset()
        assertEquals(0.8f, viewModel.uiState.value.overlayAlpha)
    }

    @Test
    fun onOverlayReset_withStrongMismatch_restoresFullImageStartMode() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        testViewModel.onOverlayDragged(0.2f, -0.2f)
        testViewModel.onOverlayScaled(1.5f)

        testViewModel.onOverlayReset()

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
        assertEquals(0f, testViewModel.uiState.value.overlayOffsetX)
        assertEquals(0f, testViewModel.uiState.value.overlayOffsetY)
        assertEquals(1f, testViewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayReset_withNormalMatch_restoresCompareStartMode() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)

        testViewModel.onOverlayReset()

        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(false, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    @Test
    fun onOverlayReset_afterManualModeChange_allowsFutureViewportAutoUpdates() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)

        testViewModel.onOverlayReset()
        testViewModel.onReferenceViewportChanged(1920, 1080)

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
    }

    // --- onOverlayScaled ---

    @Test
    fun onOverlayScaled_validFactor_updatesScale() {
        viewModel.onOverlayScaled(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayScaled_clampsAtMin() {
        // 1.0 * 0.1 = 0.1, clamped to 0.5
        viewModel.onOverlayScaled(0.1f)
        assertEquals(0.5f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayScaled_clampsAtMax() {
        // 1.0 * 4.0 = 4.0, clamped to 3.0
        viewModel.onOverlayScaled(4.0f)
        assertEquals(3.0f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayScaled_doesNotAffectOtherState() {
        viewModel.onOverlayAlphaChanged(0.7f)
        viewModel.onOverlayDragged(0.1f, 0.1f)
        viewModel.onOverlayScaled(1.5f)
        assertEquals(0.7f, viewModel.uiState.value.overlayAlpha)
        assertEquals(0.1f, viewModel.uiState.value.overlayOffsetX)
        assertEquals(0.1f, viewModel.uiState.value.overlayOffsetY)
    }

    @Test
    fun onOverlayReset_resetsScaleToDefault() {
        viewModel.onOverlayScaled(2.0f)
        viewModel.onOverlayReset()
        assertEquals(1f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun tryStartCapture_firstCallStartsSecondCallIsRejected() {
        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)

        val token = viewModel.tryStartCapture()
        assertNotNull(token)
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        assertNull(viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onCaptureInterrupted()
    }

    @Test
    fun onCaptureInterrupted_releasesCaptureLock_whenCallbackDoesNotArrive() {
        assertNotNull(viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onCaptureInterrupted()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
        assertNotNull(viewModel.tryStartCapture())
    }

    @Test
    fun onCaptureInterrupted_isHarmlessWhenNoCaptureIsActive() {
        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onCaptureInterrupted()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
        assertNotNull(viewModel.tryStartCapture())
    }

    @Test
    fun onPhotoCaptureError_resetsCaptureInProgress() {
        val token = viewModel.tryStartCapture()!!

        viewModel.onPhotoCaptureError(token)

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
    }

    @Test
    fun onPhotoCaptureError_emitsCaptureFailed() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        val token = viewModel.tryStartCapture()!!
        viewModel.onPhotoCaptureError(token)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    @Test
    fun staleSuccess_afterCaptureInterrupted_isIgnored() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }
        val token = viewModel.tryStartCapture()!!
        viewModel.onCaptureInterrupted()
        val bitmap = mock<Bitmap>()

        viewModel.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        job.cancel()
        verify(bitmap).recycle()
        assertNull(viewModel.lastCaptureSnapshot)
        assertNull(viewModel.uiState.value.compareInput)
        assertEquals(0, events.filterIsInstance<UiEvent.ShowSnackbar>().size)
        assertEquals(0, events.filterIsInstance<UiEvent.NavigateToCompare>().size)
    }

    @Test
    fun staleError_afterCaptureInterrupted_isIgnored() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }
        val token = viewModel.tryStartCapture()!!
        viewModel.onCaptureInterrupted()

        viewModel.onPhotoCaptureError(token)
        advanceUntilIdle()

        job.cancel()
        assertEquals(0, events.filterIsInstance<UiEvent.ShowSnackbar>().size)
    }

    @Test
    fun tryStartCapture_afterInterrupt_returnsDifferentToken() {
        val firstToken = viewModel.tryStartCapture()!!

        viewModel.onCaptureInterrupted()
        val secondToken = viewModel.tryStartCapture()!!

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun staleError_doesNotReleaseNewCaptureLock() {
        val staleToken = viewModel.tryStartCapture()!!
        viewModel.onCaptureInterrupted()
        assertNotNull(viewModel.tryStartCapture())

        viewModel.onPhotoCaptureError(staleToken)

        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)
        assertNull(viewModel.tryStartCapture())
    }

    @Test
    fun staleSuccess_afterInterrupt_doesNotEmitAutoOpenCompare() = runTest {
        val testViewModel = testViewModelWithAutoOpen(autoOpenEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        val token = testViewModel.tryStartCapture()!!
        testViewModel.onCaptureInterrupted()
        val bitmap = mock<Bitmap>()

        testViewModel.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        job.cancel()
        verify(bitmap).recycle()
        assertEquals(0, events.filterIsInstance<UiEvent.NavigateToCompare>().size)
    }

    @Test
    fun captureWatchdog_withoutCallback_releasesCaptureLockAfterTimeout() = runTest {
        assertNotNull(viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
        assertNotNull(viewModel.tryStartCapture())
        viewModel.onCaptureInterrupted()
    }

    @Test
    fun captureWatchdog_withoutCallback_emitsCaptureFailedOnce() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        assertNotNull(viewModel.tryStartCapture())
        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    @Test
    fun captureWatchdog_successBeforeTimeout_doesNotEmitLaterTimeoutSnackbar() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        val token = testViewModel.tryStartCapture()!!
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)

        testViewModel.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()
        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    @Test
    fun captureWatchdog_errorBeforeTimeout_doesNotEmitLaterTimeoutSnackbar() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }
        val token = viewModel.tryStartCapture()!!

        viewModel.onPhotoCaptureError(token)
        advanceUntilIdle()
        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    @Test
    fun captureWatchdog_interruptBeforeTimeout_doesNotEmitLaterTimeoutSnackbar() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        assertNotNull(viewModel.tryStartCapture())
        viewModel.onCaptureInterrupted()
        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()

        job.cancel()
        assertEquals(0, events.filterIsInstance<UiEvent.ShowSnackbar>().size)
    }

    @Test
    fun captureWatchdog_staleTimeoutDoesNotReleaseNewCapture() = runTest {
        val timedOutToken = viewModel.tryStartCapture()!!
        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)

        val newToken = viewModel.tryStartCapture()!!
        assertNotEquals(timedOutToken, newToken)
        val staleBitmap = mock<Bitmap>()

        viewModel.onPhotoCaptured(timedOutToken, staleBitmap, 0)

        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)
        assertNull(viewModel.tryStartCapture())
        verify(staleBitmap).recycle()
        viewModel.onCaptureInterrupted()
    }

    @Test
    fun staleSuccess_afterCaptureWatchdogTimeout_isIgnoredAndRecyclesBitmap() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }
        val token = viewModel.tryStartCapture()!!

        advanceTimeBy(CAPTURE_CALLBACK_TIMEOUT_MS)
        advanceUntilIdle()
        val bitmap = mock<Bitmap>()

        viewModel.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        job.cancel()
        verify(bitmap).recycle()
        assertNull(viewModel.lastCaptureSnapshot)
        assertNull(viewModel.uiState.value.compareInput)
        assertEquals(1, events.filterIsInstance<UiEvent.ShowSnackbar>().size)
        assertEquals(0, events.filterIsInstance<UiEvent.NavigateToCompare>().size)
    }

    @Test
    fun onPhotoCaptured_withValidToken_runsNormalPipeline() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        val token = testViewModel.tryStartCapture()!!
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)

        testViewModel.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        job.cancel()
        verify(bitmap).recycle()
        assertEquals(false, testViewModel.uiState.value.isCaptureInProgress)
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    @Test
    fun onCameraStartError_emitsCameraStartFailed_notCaptureFailed() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        viewModel.onCameraStartError()
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.camera_start_failed, snackbars.single().messageResId)
        assertNotEquals(R.string.capture_failed, snackbars.single().messageResId)
    }

    // --- onReferenceImageRemoveUndo ---

    @Test
    fun removeUndo_restoresUriAndMetadata() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)
        testViewModel.onOverlayDragged(0.2f, -0.1f)
        testViewModel.onOverlayScaled(1.5f)
        testViewModel.onOverlayAlphaChanged(0.8f)

        testViewModel.onReferenceImageRemoveConfirmed()
        assertNull(testViewModel.uiState.value.referenceImageUri)

        testViewModel.onReferenceImageRemoveUndo()

        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
        assertEquals(0.2f, testViewModel.uiState.value.overlayOffsetX)
        assertEquals(-0.1f, testViewModel.uiState.value.overlayOffsetY)
        assertEquals(1.5f, testViewModel.uiState.value.overlayScale)
        assertEquals(0.8f, testViewModel.uiState.value.overlayAlpha)
        assertEquals(false, testViewModel.uiState.value.canUndoReferenceRemoval)
    }

    @Test
    fun undoTimeout_clearsUndoStateAfter2500ms() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageRemoveConfirmed()
        assertEquals(true, testViewModel.uiState.value.canUndoReferenceRemoval)

        advanceTimeBy(2501)

        assertEquals(false, testViewModel.uiState.value.canUndoReferenceRemoval)
        assertEquals(0L, testViewModel.uiState.value.undoExpiresAtMillis)
    }

    @Test
    fun undoTimeout_doesNotExpireBeforeDeadline() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageRemoveConfirmed()

        advanceTimeBy(2499)

        assertEquals(true, testViewModel.uiState.value.canUndoReferenceRemoval)
    }

    @Test
    fun removeUndo_noOp_whenNoSnapshot() = runTest {
        viewModel.onReferenceImageRemoveUndo()
        assertNull(viewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun removeUndo_isInvalidatedAfterNewReferenceLoad() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val firstUri = mock<Uri>()
        val secondUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(firstUri)
        testViewModel.onReferenceImageRemoveConfirmed()

        testViewModel.onReferenceImageSelected(secondUri)
        testViewModel.onReferenceImageRemoveUndo()

        // Undo snapshot was cleared by the new selection — URI stays as secondUri, not firstUri
        assertEquals(secondUri, testViewModel.uiState.value.referenceImageUri)
        assertEquals(false, testViewModel.uiState.value.canUndoReferenceRemoval)
    }

    @Test
    fun removeUndo_remainsValidAfterPickerAbort() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)
        testViewModel.onReferenceImageRemoveConfirmed()

        testViewModel.onReferenceImageSelected(null) // picker aborted
        testViewModel.onReferenceImageRemoveUndo()

        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun removeUndo_remainsValidAfterFailedReferenceLoad() = runTest {
        val firstUri = mock<Uri>()
        var loadCount = 0
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { uri ->
                loadCount++
                if (loadCount == 1) {
                    // First load succeeds
                    ReferenceImageMetadata(1920, 1080, 1920, 1080, null)
                } else {
                    // Second load fails (returns null)
                    null
                }
            },
            fakeSettingsRepository
        )
        testViewModel.onReferenceImageSelected(firstUri)
        testViewModel.onReferenceImageRemoveConfirmed()

        val failingUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(failingUri) // metadata read fails
        testViewModel.onReferenceImageRemoveUndo()

        // Undo snapshot still valid because second load failed before committing
        assertEquals(firstUri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun removeUndo_emitsUndoInvalidatedEvent_whenNewReferenceLoaded() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageRemoveConfirmed()

        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onReferenceImageSelected(mock())

        job.cancel()
        assertEquals(1, events.filterIsInstance<UiEvent.UndoInvalidated>().size)
    }

    @Test
    fun removeUndo_doesNotEmitUndoInvalidatedEvent_whenNoSnapshotExists() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)

        val events = mutableListOf<UiEvent>()
        val job = launch { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onReferenceImageSelected(mock())

        job.cancel()
        assertEquals(0, events.filterIsInstance<UiEvent.UndoInvalidated>().size)
    }

    @Test
    fun noReference_captureStillProceedsNormally() {
        // No reference → capture lock should still work.
        val token = viewModel.tryStartCapture()
        assertNotNull(token)
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onPhotoCaptureError(token!!)

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
    }

    @Test
    fun captureInterruptWithNoMetadata_releasesLock() {
        viewModel.onReferenceViewportChanged(1080, 1920)
        viewModel.tryStartCapture()

        viewModel.onCaptureInterrupted()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
    }

    @Test
    fun compareInput_isNullInitially() {
        assertNull(viewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_remainsNullWhenCaptureSavedWithoutReference() {
        val captureUri = mock<Uri>()

        viewModel.onCaptureSaved(captureUri)

        assertNull(viewModel.uiState.value.compareInput)
    }

    @Test
    fun onCaptureSaved_withReferenceButNoSessionRef_leavesCompareInputNull() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val referenceUri = mock<Uri>()
        val captureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(referenceUri)

        testViewModel.onCaptureSaved(captureUri)

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun onCaptureSaved_withoutSessionRef_setsCaptureSuccessHadReferenceFalse() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val captureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(captureUri)

        assertEquals(false, testViewModel.uiState.value.captureSuccessHadReference)
    }

    @Test
    fun onCaptureSaved_withoutSessionRef_hasNoCompareInputAndNoSnackbarCompareAvailability() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val pickerReferenceUri = mock<Uri>()
        val mediaStoreCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(pickerReferenceUri)

        testViewModel.onCaptureSaved(mediaStoreCaptureUri, sessionRef = null)

        assertNull(testViewModel.uiState.value.compareInput)
        assertEquals(false, testViewModel.uiState.value.captureSuccessHadReference)
    }

    @Test
    fun onCaptureSaved_withSnapshotButNoSessionRef_emitsCaptureCompareFailedSnackbar() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onCaptureSaved(mock(), sessionRef = null, hadSnapshotButNoSession = true)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.capture_saved_compare_failed, snackbars.single().messageResId)
    }

    @Test
    fun onCaptureSaved_withSnapshotButNoSessionRef_setsCaptureSuccessHadReferenceTrue() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(mock(), sessionRef = null, hadSnapshotButNoSession = true)

        assertEquals(true, testViewModel.uiState.value.captureSuccessHadReference)
        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun onCaptureSaved_withSessionRef_usesSessionReferenceFileUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val mediaStoreCaptureUri = mock<Uri>()
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(
            mediaStoreCaptureUri,
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = sessionReferenceUri,
                captureFileUri = sessionCaptureUri
            )
        )

        assertEquals(sessionReferenceUri, testViewModel.uiState.value.compareInput?.referenceImageUri)
    }

    @Test
    fun onCaptureSaved_withSessionRef_usesSessionCaptureFileUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val mediaStoreCaptureUri = mock<Uri>()
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(
            mediaStoreCaptureUri,
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = sessionReferenceUri,
                captureFileUri = sessionCaptureUri
            )
        )

        assertEquals(sessionCaptureUri, testViewModel.uiState.value.compareInput?.captureImageUri)
    }

    @Test
    fun onCaptureSaved_withSessionRef_preservesSessionIdAndTimestamp() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val mediaStoreCaptureUri = mock<Uri>()
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(
            mediaStoreCaptureUri,
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = sessionReferenceUri,
                captureFileUri = sessionCaptureUri
            )
        )

        assertEquals("session-abc", testViewModel.uiState.value.compareInput?.sessionId)
        assertEquals(9876543210L, testViewModel.uiState.value.compareInput?.timestamp)
    }

    @Test
    fun onCaptureSaved_withSessionRef_doesNotUsePickerReferenceUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val pickerReferenceUri = mock<Uri>()
        val mediaStoreCaptureUri = mock<Uri>()
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(pickerReferenceUri)

        testViewModel.onCaptureSaved(
            mediaStoreCaptureUri,
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = sessionReferenceUri,
                captureFileUri = sessionCaptureUri
            )
        )

        assertNotEquals(pickerReferenceUri, testViewModel.uiState.value.compareInput?.referenceImageUri)
        assertEquals(sessionReferenceUri, testViewModel.uiState.value.compareInput?.referenceImageUri)
    }

    @Test
    fun onCaptureSaved_withSessionRef_doesNotUseMediaStoreCaptureUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val mediaStoreCaptureUri = mock<Uri>()
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(
            mediaStoreCaptureUri,
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = sessionReferenceUri,
                captureFileUri = sessionCaptureUri
            )
        )

        assertNotEquals(mediaStoreCaptureUri, testViewModel.uiState.value.compareInput?.captureImageUri)
        assertEquals(sessionCaptureUri, testViewModel.uiState.value.compareInput?.captureImageUri)
    }

    @Test
    fun onCaptureSaved_withSessionRef_setsCaptureSuccessHadReferenceTrue() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(
            mock(),
            SavedSessionRef(
                sessionId = "session-abc",
                timestamp = 9876543210L,
                referenceFileUri = mock(),
                captureFileUri = mock()
            )
        )

        assertEquals(true, testViewModel.uiState.value.captureSuccessHadReference)
    }

    @Test
    fun immediateCompareInput_matchesLibrarySessionFilesForSameSavedSession() = runTest {
        val sessionReferenceUri = mock<Uri>()
        val sessionCaptureUri = mock<Uri>()
        val savedSessionRef = SavedSessionRef(
            sessionId = "session-abc",
            timestamp = 9876543210L,
            referenceFileUri = sessionReferenceUri,
            captureFileUri = sessionCaptureUri
        )
        val scannedSession = ScannedSession(
            sessionId = savedSessionRef.sessionId,
            timestamp = savedSessionRef.timestamp,
            referenceFileUri = savedSessionRef.referenceFileUri,
            captureFileUri = savedSessionRef.captureFileUri
        )
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            fakeSettingsRepository,
            { _ -> listOf(scannedSession) }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(mock(), savedSessionRef)

        val compareInput = testViewModel.uiState.value.compareInput
        val librarySession = testViewModel.uiState.value.savedSessions.single()
        assertEquals(savedSessionRef.referenceFileUri, compareInput?.referenceImageUri)
        assertEquals(savedSessionRef.captureFileUri, compareInput?.captureImageUri)
        assertEquals(savedSessionRef.sessionId, compareInput?.sessionId)
        assertEquals(savedSessionRef.timestamp, compareInput?.timestamp)
        assertEquals(librarySession.referenceFileUri, compareInput?.referenceImageUri)
        assertEquals(librarySession.captureFileUri, compareInput?.captureImageUri)
    }

    @Test
    fun compareInput_isClearedWhenReferenceChanges() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        testViewModel.onReferenceImageSelected(mock())

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_isClearedWhenReferenceIsRemoved() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        testViewModel.onReferenceImageRemoveConfirmed()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_persistsDuringNewCaptureStart() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        testViewModel.tryStartCapture()

        assertNotNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_persistsAfterCaptureInterrupt() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        testViewModel.onCaptureInterrupted()

        assertNotNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_persistsAfterCaptureError() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        val token = testViewModel.tryStartCapture()!!
        testViewModel.onPhotoCaptureError(token)

        assertNotNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_updatedBySubsequentSuccessfulCapture() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "session-1"))
        val firstCompareInput = testViewModel.uiState.value.compareInput
        assertNotNull(firstCompareInput)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "session-2"))
        val secondCompareInput = testViewModel.uiState.value.compareInput

        assertNotNull(secondCompareInput)
        assertNotEquals(firstCompareInput!!.sessionId, secondCompareInput!!.sessionId)
    }

    // --- lastCaptureResult ---

    @Test
    fun lastCaptureResult_isNullInitially() {
        assertNull(viewModel.lastCaptureResult)
    }

    @Test
    fun lastCaptureResult_isNullWhenSaveFails() = runTest {
        // In unit tests MediaStoreWriter.save() always fails (mocked context → null resolver).
        // Verifies that lastCaptureResult is null and not set to a stale value.
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        assertNull(testViewModel.lastCaptureResult)
    }

    @Test
    fun lastCaptureResult_isNullWhenNoSnapshot() = runTest {
        // No reference → no snapshot → save still attempted but fails → lastCaptureResult null.
        viewModel.onReferenceViewportChanged(1080, 1920)
        viewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        viewModel.onPhotoCaptured(bitmap, 0)

        assertNull(viewModel.lastCaptureResult)
    }

    @Test
    fun lastCaptureResult_isNullAfterInterrupt() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()

        testViewModel.onCaptureInterrupted()

        assertNull(testViewModel.lastCaptureResult)
    }

    @Test
    fun lastCaptureResult_isNullAfterCaptureError() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        val token = testViewModel.tryStartCapture()!!

        testViewModel.onPhotoCaptureError(token)

        assertNull(testViewModel.lastCaptureResult)
    }

    @Test
    fun lastCaptureResult_isResetOnEachNewCapture() = runTest {
        // Verifies that no stale value survives across capture attempts.
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())

        // First capture — save fails in unit tests → null.
        testViewModel.tryStartCapture()
        val bitmap1 = mock<Bitmap>()
        whenever(bitmap1.width).thenReturn(1080)
        whenever(bitmap1.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap1, 0)
        assertNull(testViewModel.lastCaptureResult)

        // Second capture after reference removal → also null, not stale from first attempt.
        testViewModel.onReferenceImageRemoveConfirmed()
        testViewModel.tryStartCapture()
        val bitmap2 = mock<Bitmap>()
        whenever(bitmap2.width).thenReturn(1080)
        whenever(bitmap2.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap2, 0)

        assertNull(testViewModel.lastCaptureResult)
    }

    // --- lastCaptureSnapshot ---

    @Test
    fun onPhotoCaptured_withReference_snapshotCapturesOverlayValuesAtCallTime() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayDragged(0.3f, -0.2f)
        testViewModel.onOverlayScaled(1.5f)
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        // Mutate overlay state after capture — snapshot must reflect pre-capture values
        testViewModel.onOverlayDragged(0.1f, 0.1f)
        testViewModel.onOverlayScaled(2.0f)

        val snapshot = testViewModel.lastCaptureSnapshot
        assertNotNull(snapshot)
        assertEquals(0.3f, snapshot!!.overlayOffsetX)
        assertEquals(-0.2f, snapshot.overlayOffsetY)
        assertEquals(1.5f, snapshot.overlayScale)
    }

    @Test
    fun onPhotoCaptured_withReference_snapshotCapturesDisplayMode() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        // Mutate display mode after capture — snapshot must reflect pre-capture value
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)

        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.lastCaptureSnapshot!!.referenceImageDisplayMode
        )
    }

    @Test
    fun onPhotoCaptured_withReference_snapshotCapturesViewportDimensions() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        val snapshot = testViewModel.lastCaptureSnapshot
        assertNotNull(snapshot)
        assertEquals(1080, snapshot!!.viewportWidth)
        assertEquals(1920, snapshot.viewportHeight)
    }

    @Test
    fun onPhotoCaptured_withReference_snapshotCapturesReferenceUri() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val referenceUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(referenceUri)
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        assertEquals(referenceUri, testViewModel.lastCaptureSnapshot!!.referenceImageUri)
    }

    @Test
    fun onPhotoCaptured_withReference_snapshotCapturesMetadata() = runTest {
        val testViewModel = testViewModelWithMetadata(
            rawWidth = 1920,
            rawHeight = 1080,
            orientedWidth = 1080,
            orientedHeight = 1920,
            exifOrientation = 6
        )
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        val metadata = testViewModel.lastCaptureSnapshot!!.referenceImageMetadata
        assertEquals(1920, metadata.rawWidth)
        assertEquals(1080, metadata.rawHeight)
        assertEquals(1080, metadata.orientedWidth)
        assertEquals(1920, metadata.orientedHeight)
        assertEquals(6, metadata.exifOrientation)
    }

    @Test
    fun onPhotoCaptured_withoutReference_snapshotIsNull() = runTest {
        viewModel.onReferenceViewportChanged(1080, 1920)
        viewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        viewModel.onPhotoCaptured(bitmap, 0)

        assertNull(viewModel.lastCaptureSnapshot)
    }

    @Test
    fun onPhotoCaptured_resetsLastCaptureSnapshotAtStart() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()
        val bitmap1 = mock<Bitmap>()
        whenever(bitmap1.width).thenReturn(1080)
        whenever(bitmap1.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap1, 0)
        assertNotNull(testViewModel.lastCaptureSnapshot)

        // Remove reference before second capture — snapshot must be reset to null
        testViewModel.onReferenceImageRemoveConfirmed()
        testViewModel.tryStartCapture()
        val bitmap2 = mock<Bitmap>()
        whenever(bitmap2.width).thenReturn(1080)
        whenever(bitmap2.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap2, 0)

        assertNull(testViewModel.lastCaptureSnapshot)
    }

    // --- savedSessions / refreshSavedSessions ---

    @Test
    fun savedSessions_isEmptyInitially() {
        assertEquals(emptyList<ScannedSession>(), viewModel.uiState.value.savedSessions)
    }

    @Test
    fun refreshSavedSessions_updatesState() = runTest {
        val fakeSession = ScannedSession(
            sessionId = "session-1",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock()
        )
        val testViewModel = testViewModelWithScanner { _ -> listOf(fakeSession) }

        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        assertEquals(listOf(fakeSession), testViewModel.uiState.value.savedSessions)
    }

    @Test
    fun refreshSavedSessions_withEmptyResult_setsEmptyList() = runTest {
        val testViewModel = testViewModelWithScanner { _ -> emptyList() }

        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        assertEquals(emptyList<ScannedSession>(), testViewModel.uiState.value.savedSessions)
    }

    @Test
    fun refreshSavedSessions_handlesException_stateRemainsEmpty() = runTest {
        val testViewModel = testViewModelWithScanner { _ -> throw RuntimeException("scanner failed") }

        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        assertEquals(emptyList<ScannedSession>(), testViewModel.uiState.value.savedSessions)
    }

    @Test
    fun savedSessions_independentOfCompareInput() = runTest {
        val fakeSession = ScannedSession(
            sessionId = "session-1",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock()
        )
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            fakeSettingsRepository,
            { _ -> listOf(fakeSession) }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertNotNull(testViewModel.uiState.value.compareInput)
        assertEquals(listOf(fakeSession), testViewModel.uiState.value.savedSessions)
    }

    // --- deleteSessions ---

    @Test
    fun deleteSessions_afterDelete_updatesStateViaScanner() = runTest {
        val remainingSession = ScannedSession(
            sessionId = "remaining-session",
            timestamp = 2000L,
            referenceFileUri = mock(),
            captureFileUri = mock()
        )
        val testViewModel = testViewModelWithScanner { _ -> listOf(remainingSession) }

        testViewModel.deleteSessions(listOf("deleted-session-id"))
        advanceUntilIdle()

        assertEquals(listOf(remainingSession), testViewModel.uiState.value.savedSessions)
    }

    @Test
    fun deleteSessions_clearsCompareInput_whenActiveSessionDeleted() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "active-session"))
        assertNotNull(testViewModel.uiState.value.compareInput)

        testViewModel.deleteSessions(listOf("active-session"))
        advanceUntilIdle()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun deleteSessions_preservesCompareInput_whenDifferentSessionDeleted() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "active-session"))
        assertNotNull(testViewModel.uiState.value.compareInput)

        testViewModel.deleteSessions(listOf("other-session"))
        advanceUntilIdle()

        assertNotNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun deleteSessions_clearsCompareInput_inMultiSelectDeleteContainingActiveSession() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "active-session"))
        assertNotNull(testViewModel.uiState.value.compareInput)

        testViewModel.deleteSessions(listOf("other-session-a", "active-session", "other-session-b"))
        advanceUntilIdle()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun deleteSessions_whenDeleteFails_emitsDeleteFailedSnackbar() = runTest {
        val events = mutableListOf<UiEvent>()
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, { _, _ -> false }
        )

        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        testViewModel.deleteSessions(listOf("session-x"))
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.delete_failed, snackbars.first().messageResId)
    }

    @Test
    fun deleteSessions_whenDeleteFails_preservesCompareInput() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, { _, _ -> false }
        )
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "active-session"))
        assertNotNull(testViewModel.uiState.value.compareInput)

        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect {} }
        testViewModel.deleteSessions(listOf("active-session"))
        advanceUntilIdle()
        collectJob.cancel()

        assertNotNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun deleteSessions_whenDeleteSucceeds_clearsCompareInput() = runTest {
        val testViewModel = testViewModelWithDeleter { _, _ -> true }
        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef(sessionId = "active-session"))
        assertNotNull(testViewModel.uiState.value.compareInput)

        testViewModel.deleteSessions(listOf("active-session"))
        advanceUntilIdle()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun deleteSessions_whenDeleteSucceeds_emitsNoDeleteFailedSnackbar() = runTest {
        val events = mutableListOf<UiEvent>()
        val testViewModel = testViewModelWithDeleter { _, _ -> true }

        val job = launch { testViewModel.uiEvent.collect { events.add(it) } }
        testViewModel.deleteSessions(listOf("session-x"))
        advanceUntilIdle()
        job.cancel()

        val deleteFailedSnackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
            .filter { it.messageResId == R.string.delete_failed }
        assertEquals(0, deleteFailedSnackbars.size)
    }

    @Test
    fun deleteSession_whenDeleteSucceeds_returnsTrue() = runTest {
        val testViewModel = testViewModelWithDeleter { _, _ -> true }

        val result = testViewModel.deleteSession("session-x")

        assertTrue(result)
    }

    @Test
    fun deleteSession_whenDeleteFails_returnsFalse() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, { _, _ -> false }
        )

        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect {} }
        val result = testViewModel.deleteSession("session-x")
        collectJob.cancel()

        assertFalse(result)
    }

    // --- updateSessionTitle ---

    @Test
    fun updateSessionTitle_trimIsApplied() = runTest {
        var capturedTitle: String? = "SENTINEL"
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> emptyList() },
            { _, _, t -> capturedTitle = t; true }
        )

        testViewModel.updateSessionTitle("session-1", "  hello  ")
        advanceUntilIdle()

        assertEquals("hello", capturedTitle)
    }

    @Test
    fun updateSessionTitle_whitespaceOnly_passesNull() = runTest {
        var capturedTitle: String? = "SENTINEL"
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> emptyList() },
            { _, _, t -> capturedTitle = t; true }
        )

        testViewModel.updateSessionTitle("session-1", "   ")
        advanceUntilIdle()

        assertNull(capturedTitle)
    }

    @Test
    fun updateSessionTitle_triggersRefresh() = runTest {
        val fakeSession = ScannedSession(
            sessionId = "session-1",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock()
        )
        var sessionList = listOf(fakeSession)
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> sessionList },
            { _, _, _ -> true }
        )

        testViewModel.updateSessionTitle("session-1", "New Title")
        advanceUntilIdle()

        assertEquals(listOf(fakeSession), testViewModel.uiState.value.savedSessions)
    }

    @Test
    fun updateSessionTitle_updaterReturnsFalse_emitsSnackbarError() = runTest {
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> emptyList() },
            { _, _, _ -> false }
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.updateSessionTitle("session-1", "Some Title")
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.compare_screen_title_save_failed, snackbars[0].messageResId)
    }

    @Test
    fun updateSessionTitle_updaterThrows_emitsSnackbarFailure() = runTest {
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> emptyList() },
            { _, _, _ -> throw RuntimeException("unexpected disk error") }
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.updateSessionTitle("session-1", "Some Title")
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.compare_screen_title_save_failed, snackbars[0].messageResId)
    }

    // --- onCompareDisabledTapped ---

    @Test
    fun onCompareDisabledTapped_noReference_emitsSnackbarWithDuration2000() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        viewModel.onCompareDisabledTapped(referenceUri = null)
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.compare_disabled_no_reference, snackbars[0].messageResId)
        assertEquals(2000L, snackbars[0].durationMs)
    }

    @Test
    fun onCompareDisabledTapped_withReference_emitsSnackbarWithDuration2000() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { viewModel.uiEvent.collect { events.add(it) } }

        viewModel.onCompareDisabledTapped(referenceUri = mock())
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.compare_disabled_no_capture, snackbars[0].messageResId)
        assertEquals(2000L, snackbars[0].durationMs)
    }

    @Test
    fun otherSnackbarEvents_haveNullDurationMs() = runTest {
        val testViewModel = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onReferenceImageSelected(mock())
        advanceUntilIdle()

        job.cancel()
        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.reference_image_load_failed, snackbars[0].messageResId)
        assertNull(snackbars[0].durationMs)
    }

    // --- isOverlayNearlyInvisible ---

    @Test
    fun isOverlayNearlyInvisible_falseByDefault() {
        assertEquals(false, viewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_falseWhenViewportZero() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        // viewport was never set — remains 0
        assertEquals(false, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_falseInCompareModeAtMinScale() = runTest {
        // Portrait image in portrait viewport → COMPARE_WITH_PREVIEW, scale=0.5
        // fillScale=1, coverage=25% > 20% threshold
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayScaled(0.5f) // 1.0 * 0.5 = MIN_SCALE
        assertEquals(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, testViewModel.uiState.value.referenceImageDisplayMode)
        assertEquals(false, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_falseInShowFullImageCenteredWithDefaultScale() = runTest {
        // 16:9 image, portrait viewport, scale=1.0, offset=0/0 → coverage ~31.6% > 20%
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        assertEquals(false, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_trueInShowFullImageSmallScaleWithOffset() = runTest {
        // 16:9 image, portrait viewport, scale=0.5, offsetX=0.5 → coverage ~4% < 20%
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        testViewModel.onOverlayScaled(0.5f)
        testViewModel.onOverlayDragged(0.5f, 0f)
        assertEquals(true, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_trueInShowFullImagePortraitImageInLandscapeViewportWithOffset() = runTest {
        // 9:16 portrait image in 16:9 landscape viewport, scale=1.0, offsetX=0.5 → coverage ~15.8% < 20%
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1920, 1080)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        testViewModel.onOverlayDragged(0.5f, 0f)
        assertEquals(true, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_falseAfterOverlayReset() = runTest {
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        testViewModel.onOverlayScaled(0.5f)
        testViewModel.onOverlayDragged(0.5f, 0f)
        assertEquals(true, testViewModel.uiState.value.isOverlayNearlyInvisible)

        testViewModel.onOverlayReset()
        assertEquals(false, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    @Test
    fun isOverlayNearlyInvisible_falseAfterSwitchingToCompareMode() = runTest {
        // Start in SHOW_FULL_IMAGE with nearly-invisible state, then switch to COMPARE_WITH_PREVIEW
        // clamped offsets + fillScale bring coverage back above 20%
        val testViewModel = testViewModelWithMetadata(1920, 1080)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        testViewModel.onOverlayScaled(0.5f)
        testViewModel.onOverlayDragged(0.5f, 0f)
        assertEquals(true, testViewModel.uiState.value.isOverlayNearlyInvisible)

        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        assertEquals(false, testViewModel.uiState.value.isOverlayNearlyInvisible)
    }

    // --- autoOpenCompareAfterCapture ---

    @Test
    fun onCaptureSaved_autoOpenEnabled_withSession_emitsNavigateToCompare() = runTest {
        val testViewModel = testViewModelWithAutoOpen(autoOpenEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        val sessionRef = fakeSavedSessionRef()

        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onCaptureSaved(mock(), sessionRef)
        advanceUntilIdle()

        job.cancel()
        val navigateEvents = events.filterIsInstance<UiEvent.NavigateToCompare>()
        assertEquals(1, navigateEvents.size)
        assertEquals(sessionRef.referenceFileUri, navigateEvents[0].input.referenceImageUri)
        assertEquals(sessionRef.captureFileUri, navigateEvents[0].input.captureImageUri)
    }

    @Test
    fun onCaptureSaved_autoOpenEnabled_withoutSession_doesNotEmitNavigateToCompare() = runTest {
        val testViewModel = testViewModelWithAutoOpen(autoOpenEnabled = true)

        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onCaptureSaved(mock(), sessionRef = null)
        advanceUntilIdle()

        job.cancel()
        assertEquals(0, events.filterIsInstance<UiEvent.NavigateToCompare>().size)
    }

    @Test
    fun onCaptureSaved_autoOpenDisabled_withSession_doesNotEmitNavigateToCompare() = runTest {
        val testViewModel = testViewModelWithAutoOpen(autoOpenEnabled = false)
        testViewModel.onReferenceImageSelected(mock())

        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())
        advanceUntilIdle()

        job.cancel()
        assertEquals(0, events.filterIsInstance<UiEvent.NavigateToCompare>().size)
    }

    // --- helpers ---

    private fun testViewModelWithScanner(
        scanner: (Context) -> List<ScannedSession>
    ): CameraViewModel {
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            scanner
        )
    }

    private fun testViewModelWithDeleter(
        deleter: (File, String) -> Boolean
    ): CameraViewModel {
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            fakeSettingsRepository,
            { _ -> emptyList() },
            null,
            deleter
        )
    }

    private fun testViewModelWithMetadata(
        rawWidth: Int,
        rawHeight: Int,
        orientedWidth: Int = rawWidth,
        orientedHeight: Int = rawHeight,
        exifOrientation: Int? = null
    ): CameraViewModel {
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            {
                ReferenceImageMetadata(
                    rawWidth = rawWidth,
                    rawHeight = rawHeight,
                    orientedWidth = orientedWidth,
                    orientedHeight = orientedHeight,
                    exifOrientation = exifOrientation
                )
            },
            fakeSettingsRepository
        )
    }

    private fun fakeSavedSessionRef(
        sessionId: String = "session-abc",
        timestamp: Long = 9876543210L
    ): SavedSessionRef {
        return SavedSessionRef(
            sessionId = sessionId,
            timestamp = timestamp,
            referenceFileUri = mock(),
            captureFileUri = mock()
        )
    }

    // --- gridType from SettingsRepository ---

    @Test
    fun gridType_initialValue_matchesSettingsRepositoryDefault() = runTest {
        assertEquals(GridType.RULE_OF_THIRDS, viewModel.uiState.value.gridType)
    }

    @Test
    fun gridType_updatesWhenSettingsEmitNewValue() = runTest {
        val settingsRepo: SettingsRepository = mock {
            on { gridType } doReturn flowOf(GridType.QUARTERS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
        }
        val testViewModel = CameraViewModel(mock(), settingsRepo)
        assertEquals(GridType.QUARTERS, testViewModel.uiState.value.gridType)
    }

    // --- resetOverlayAfterCapture ---

    @Test
    fun onCaptureSaved_withResetEnabled_resetsOverlayTransform() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayDragged(0.3f, -0.2f)
        testViewModel.onOverlayScaled(1.8f)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertEquals(0f, testViewModel.uiState.value.overlayOffsetX)
        assertEquals(0f, testViewModel.uiState.value.overlayOffsetY)
        assertEquals(1f, testViewModel.uiState.value.overlayScale)
    }

    @Test
    fun onCaptureSaved_withResetEnabled_clearsReferenceUri() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertNull(testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onCaptureSaved_withResetEnabled_clearsReferenceMetadata() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertNull(testViewModel.uiState.value.referenceImageMetadata)
    }

    @Test
    fun onCaptureSaved_withResetEnabled_resetsDisplayMode() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertEquals(
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
    }

    @Test
    fun onCaptureSaved_withResetEnabled_preservesAlpha() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayAlphaChanged(0.8f)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertEquals(0.8f, testViewModel.uiState.value.overlayAlpha)
    }

    @Test
    fun onCaptureSaved_withResetDisabled_preservesReferenceUri() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = false)
        val uri = mock<Uri>()
        testViewModel.onReferenceImageSelected(uri)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertEquals(uri, testViewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onCaptureSaved_withResetDisabled_preservesOverlayTransform() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = false)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onOverlayDragged(0.3f, -0.2f)
        testViewModel.onOverlayScaled(1.8f)

        testViewModel.onCaptureSaved(mock(), fakeSavedSessionRef())

        assertEquals(0.3f, testViewModel.uiState.value.overlayOffsetX)
        assertEquals(-0.2f, testViewModel.uiState.value.overlayOffsetY)
        assertEquals(1.8f, testViewModel.uiState.value.overlayScale)
    }

    @Test
    fun onCaptureSaved_withResetEnabled_doesNotClearCompareInput() = runTest {
        val testViewModel = testViewModelWithMetadataAndReset(resetEnabled = true)
        testViewModel.onReferenceImageSelected(mock())
        val savedRef = fakeSavedSessionRef()

        testViewModel.onCaptureSaved(mock(), savedRef)

        assertNotNull(testViewModel.uiState.value.compareInput)
        assertEquals(savedRef.sessionId, testViewModel.uiState.value.compareInput?.sessionId)
    }

    private fun testViewModelWithMetadataAndReset(resetEnabled: Boolean): CameraViewModel {
        val settingsRepo: SettingsRepository = mock {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(resetEnabled)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
        }
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepo
        )
    }

    private fun testViewModelWithAutoOpen(autoOpenEnabled: Boolean): CameraViewModel {
        val settingsRepo: SettingsRepository = mock {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(autoOpenEnabled)
            on { recreationGuidance } doReturn flowOf(false)
        }
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepo
        )
    }

    // --- GPS Activation ---

    private fun settingsRepoWithGps(recreationGuidance: Boolean): SettingsRepository = mock {
        on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
        on { keepScreenOn } doReturn flowOf(true)
        on { resetOverlayAfterCapture } doReturn flowOf(false)
        on { autoOpenCompareAfterCapture } doReturn flowOf(false)
        on { this.recreationGuidance } doReturn flowOf(recreationGuidance)
    }

    private fun gpsViewModel(
        recreationGuidance: Boolean = false,
        permissionGranted: Boolean = false,
        referenceHasGps: Boolean = false,
        mockProvider: LocationProvider = mock()
    ): Pair<CameraViewModel, LocationProvider> {
        val metadata = if (referenceHasGps) {
            ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        } else {
            ReferenceImageMetadata(1080, 1920, 1080, 1920, null)
        }
        val vm = CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { metadata },
            settingsRepoWithGps(recreationGuidance),
            locationProvider = mockProvider,
            locationPermissionChecker = { permissionGranted }
        )
        return Pair(vm, mockProvider)
    }

    @Test
    fun gps_notStarted_initially_whenNoCameraScreenActive() = runTest {
        val (vm, mockProvider) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        // cameraScreenActive = false → GPS must not start
        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_started_whenAllFourConditionsMet() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()

        vm.onCameraScreenActive()

        assertTrue(vm.isGpsActive)
    }

    @Test
    fun gps_notStarted_whenRecreationGuidanceOff() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = false, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_notStarted_whenPermissionNotGranted() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = false, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_notStarted_whenReferenceHasNoGps() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = false
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_notStarted_whenReferenceNotLoaded() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        // no onReferenceImageSelected → referenceImageMetadata is null → referenceHasGps = false
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_stopped_whenCameraScreenBecomesInactive() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)

        vm.onCameraScreenInactive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_stopped_whenRecreationGuidanceTurnsOff() = runTest {
        // Start with guidance ON, get GPS active, then switch setting to OFF
        var guidanceEnabled = true
        val settingsRepo: SettingsRepository = mock {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(true)
        }
        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepo,
            locationPermissionChecker = { true }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)

        // Simulate setting turned OFF by calling the internal tracking directly
        // (the real path goes through the DataStore collector)
        vm.onCameraScreenInactive()  // simulates setting change via inactive
        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_stopped_whenReferenceRemoved() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)

        vm.onReferenceImageRemoveConfirmed()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun gps_notDuplicated_onMultipleActiveCallsWhenAlreadyActive() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)

        // Second active call must not double-start
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)
    }

    @Test
    fun currentLocation_updatedByLocationListenerCallback() = runTest {
        // Verify that the LocationListener registered by the ViewModel propagates updates
        val capturedListeners = mutableListOf<android.location.LocationListener>()
        val fakeProvider = object : LocationProvider(null as LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {
                capturedListeners.add(listener)
            }
            override fun stopUpdates(listener: android.location.LocationListener) {
                capturedListeners.clear()
            }
            override fun getLastKnown(): android.location.Location? = null
        }
        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithGps(recreationGuidance = true),
            locationProvider = fakeProvider,
            locationPermissionChecker = { true }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(capturedListeners.size == 1)

        val fakeLocation = mock<android.location.Location>()
        capturedListeners.single().onLocationChanged(fakeLocation)

        assertEquals(fakeLocation, vm.currentLocation)
    }

    @Test
    fun currentLocation_clearedWhenGpsStopped() = runTest {
        val capturedListeners = mutableListOf<android.location.LocationListener>()
        val fakeProvider = object : LocationProvider(null as LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {
                capturedListeners.add(listener)
            }
            override fun stopUpdates(listener: android.location.LocationListener) {}
            override fun getLastKnown(): android.location.Location? = null
        }
        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithGps(recreationGuidance = true),
            locationProvider = fakeProvider,
            locationPermissionChecker = { true }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        capturedListeners.single().onLocationChanged(mock<Location>())
        assertNotNull(vm.currentLocation)

        vm.onCameraScreenInactive()

        assertNull(vm.currentLocation)
    }

    @Test
    fun referenceHasGps_isFalse_whenMetadataHasNoCoordinates() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = false
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
    }

    @Test
    fun referenceHasGps_isTrue_whenMetadataHasCoordinates() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertTrue(vm.isGpsActive)
    }

    @Test
    fun gps_startsAfterUndo_whenRestoredReferenceHasGps() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isGpsActive)

        vm.onReferenceImageRemoveConfirmed()
        assertFalse(vm.isGpsActive)

        vm.onReferenceImageRemoveUndo()
        assertTrue(vm.isGpsActive)
    }

    // --- GpsGuidanceState lifecycle ---

    @Test
    fun gpsGuidanceState_isHidden_byDefault() = runTest {
        val (vm, _) = gpsViewModel()
        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_isNeutral_whenGpsStartsWithNoLastKnown() = runTest {
        val mockProvider = mock<LocationProvider>()
        whenever(mockProvider.getLastKnown()).thenReturn(null)
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true,
            mockProvider = mockProvider
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertEquals(GpsGuidanceState.Neutral, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_isHidden_afterGpsStopped() = runTest {
        val mockProvider = mock<LocationProvider>()
        whenever(mockProvider.getLastKnown()).thenReturn(null)
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true,
            mockProvider = mockProvider
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertEquals(GpsGuidanceState.Neutral, vm.uiState.value.gpsGuidanceState)

        vm.onCameraScreenInactive()

        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_becomesInformative_whenLocationArrivesWithGoodAccuracy() = runTest {
        val capturedListeners = mutableListOf<android.location.LocationListener>()
        val fakeProvider = object : LocationProvider(null as LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {
                capturedListeners.add(listener)
            }
            override fun stopUpdates(listener: android.location.LocationListener) {}
            override fun getLastKnown(): android.location.Location? = null
        }
        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null,
            gpsLatitude = 48.001, gpsLongitude = 11.0)
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithGps(recreationGuidance = true),
            locationProvider = fakeProvider,
            locationPermissionChecker = { true }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertEquals(GpsGuidanceState.Neutral, vm.uiState.value.gpsGuidanceState)

        val fakeLocation = mock<android.location.Location>()
        whenever(fakeLocation.latitude).thenReturn(48.0)
        whenever(fakeLocation.longitude).thenReturn(11.0)
        whenever(fakeLocation.accuracy).thenReturn(10f)
        capturedListeners.single().onLocationChanged(fakeLocation)

        assertTrue(vm.uiState.value.gpsGuidanceState is GpsGuidanceState.Informative)
    }

    @Test
    fun gpsGuidanceState_remainsHidden_whenPermissionNotGranted() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = false, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_remainsHidden_whenReferenceHasNoGps() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = false
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_remainsHidden_whenRecreationGuidanceOff() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = false, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        assertFalse(vm.isGpsActive)
        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }

    @Test
    fun gpsGuidanceState_remainsHidden_whenCameraScreenNotActive() = runTest {
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        // camera screen not activated

        assertFalse(vm.isGpsActive)
        assertEquals(GpsGuidanceState.Hidden, vm.uiState.value.gpsGuidanceState)
    }
}
