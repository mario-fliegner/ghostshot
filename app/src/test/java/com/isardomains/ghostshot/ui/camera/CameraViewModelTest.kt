// path: app/src/test/java/com/isardomains/ghostshot/ui/camera/CameraViewModelTest.kt
package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CameraViewModel(mock())
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
            }
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
            }
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

        assertEquals(true, viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        assertEquals(false, viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)
    }

    @Test
    fun onCaptureInterrupted_releasesCaptureLock_whenCallbackDoesNotArrive() {
        assertEquals(true, viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onCaptureInterrupted()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
        assertEquals(true, viewModel.tryStartCapture())
    }

    @Test
    fun onCaptureInterrupted_isHarmlessWhenNoCaptureIsActive() {
        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onCaptureInterrupted()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
        assertEquals(true, viewModel.tryStartCapture())
    }

    @Test
    fun onPhotoCaptureError_resetsCaptureInProgress() {
        viewModel.tryStartCapture()

        viewModel.onPhotoCaptureError()

        assertEquals(false, viewModel.uiState.value.isCaptureInProgress)
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
            }
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
        assertEquals(true, viewModel.tryStartCapture())
        assertEquals(true, viewModel.uiState.value.isCaptureInProgress)

        viewModel.onPhotoCaptureError()

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
    fun compareInput_isSetWhenCaptureSavedWithReference() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val referenceUri = mock<Uri>()
        val captureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(referenceUri)

        testViewModel.onCaptureSaved(captureUri)

        assertEquals(referenceUri, testViewModel.uiState.value.compareInput?.referenceImageUri)
        assertEquals(captureUri, testViewModel.uiState.value.compareInput?.captureImageUri)
    }

    @Test
    fun compareInput_sessionIdAndTimestampAreNullWhenNoSessionRef() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val captureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(captureUri)

        assertNull(testViewModel.uiState.value.compareInput?.sessionId)
        assertNull(testViewModel.uiState.value.compareInput?.timestamp)
    }

    @Test
    fun compareInput_hasSessionIdAndTimestampWhenSessionRefProvided() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        val captureUri = mock<Uri>()
        testViewModel.onReferenceImageSelected(mock())

        testViewModel.onCaptureSaved(captureUri, SavedSessionRef("session-abc", 9876543210L))

        assertEquals("session-abc", testViewModel.uiState.value.compareInput?.sessionId)
        assertEquals(9876543210L, testViewModel.uiState.value.compareInput?.timestamp)
    }

    @Test
    fun compareInput_isClearedWhenReferenceChanges() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock())

        testViewModel.onReferenceImageSelected(mock())

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_isClearedWhenReferenceIsRemoved() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock())

        testViewModel.onReferenceImageRemoveConfirmed()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_isClearedWhenNewCaptureStarts() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock())

        testViewModel.tryStartCapture()

        assertNull(testViewModel.uiState.value.compareInput)
    }

    @Test
    fun compareInput_isClearedAfterCaptureInterrupt() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock())

        testViewModel.onCaptureInterrupted()

        assertNull(testViewModel.uiState.value.compareInput)
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
        testViewModel.tryStartCapture()

        testViewModel.onPhotoCaptureError()

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
            { _ -> listOf(fakeSession) }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        testViewModel.onReferenceImageSelected(mock())
        testViewModel.onCaptureSaved(mock())

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

    // --- helpers ---

    private fun testViewModelWithScanner(
        scanner: (Context) -> List<ScannedSession>
    ): CameraViewModel {
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { null },
            scanner
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
            }
        )
    }
}
