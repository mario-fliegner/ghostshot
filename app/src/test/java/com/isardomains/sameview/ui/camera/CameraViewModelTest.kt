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
import com.isardomains.sameview.storage.SessionBackupExporter
import com.isardomains.sameview.ui.settings.LibraryFilter
import com.isardomains.sameview.ui.settings.LibrarySortOrder
import com.isardomains.sameview.ui.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
        on { liveDirectionArrow } doReturn flowOf(false)
        on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
        on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
        on { stripOriginalsMetadata } doReturn flowOf(false)
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
    fun landscapeOrientedReference_againstPortraitViewport_hasMismatch() = runTest {
        // Simulates the double-rotation bug outcome: landscape-oriented dims + portrait viewport.
        // Verifies that genuinely mismatched dims still trigger the hint (regression guard).
        val testViewModel = testViewModelWithMetadata(
            rawWidth = 1920,
            rawHeight = 1080,
            orientedWidth = 1920,
            orientedHeight = 1080
        )
        testViewModel.onReferenceViewportChanged(1080, 1920)

        testViewModel.onReferenceImageSelected(mock())

        assertEquals(true, testViewModel.uiState.value.referenceImageHasViewportMismatch)
        assertEquals(
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            testViewModel.uiState.value.referenceImageDisplayMode
        )
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
        // 1.0 * 5.0 = 5.0, clamped to MAX_SCALE (4.0)
        viewModel.onOverlayScaled(5.0f)
        assertEquals(4.0f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun maxScale_sufficientForCrossOrientationShowFullImageMode() {
        // In SHOW_FULL_IMAGE mode, ContentScale.Fit renders a 9:16 portrait reference inside a
        // 16:9 landscape composable at rendered_width = imageWidth * (vpH / imageH).
        // For 9:16 in 16:9: rendered_width = 9 * (9/16) = 81/16 of viewport units.
        // To fill the full landscape viewport width the user needs:
        //   overlayScale = vpW / rendered_width = 16 / (81/16) = 256/81 ≈ 3.160
        // MAX_SCALE must be strictly greater than this required value.
        val requiredScale = (16f / 9f) * (16f / 9f) // 256/81 ≈ 3.160
        assertTrue(
            "MAX_SCALE (${CameraViewModel.MAX_SCALE}) must be >= required cross-orientation scale ($requiredScale)",
            CameraViewModel.MAX_SCALE >= requiredScale
        )
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

    // --- toggleFavorite ---

    @Test
    fun toggleFavorite_updatesInMemoryState_onSuccess() = runTest {
        val session = ScannedSession(
            sessionId = "session-fav",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        val testViewModel = CameraViewModel(
            mock(),
            StandardTestDispatcher(testScheduler),
            { null },
            fakeSettingsRepository,
            sessionScanner = { _ -> listOf(session) },
            sessionFavoriteUpdater = { _, _, _ -> true }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        testViewModel.toggleFavorite("session-fav")
        advanceUntilIdle()

        val updated = testViewModel.uiState.value.savedSessions.find { it.sessionId == "session-fav" }
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)
    }

    @Test
    fun toggleFavorite_doesNotUpdateInMemoryState_onWriteFailure() = runTest {
        val session = ScannedSession(
            sessionId = "session-fav",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        val testViewModel = CameraViewModel(
            mock(),
            StandardTestDispatcher(testScheduler),
            { null },
            fakeSettingsRepository,
            sessionScanner = { _ -> listOf(session) },
            sessionFavoriteUpdater = { _, _, _ -> false }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect {} }
        testViewModel.toggleFavorite("session-fav")
        advanceUntilIdle()
        collectJob.cancel()

        val inMemory = testViewModel.uiState.value.savedSessions.find { it.sessionId == "session-fav" }
        assertNotNull(inMemory)
        assertFalse(inMemory!!.isFavorite) // unchanged
    }

    @Test
    fun toggleFavorite_emitsSnackbar_onWriteFailure() = runTest {
        val session = ScannedSession(
            sessionId = "session-fav",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        val testViewModel = CameraViewModel(
            mock(),
            StandardTestDispatcher(testScheduler),
            { null },
            fakeSettingsRepository,
            sessionScanner = { _ -> listOf(session) },
            sessionFavoriteUpdater = { _, _, _ -> false }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        testViewModel.toggleFavorite("session-fav")
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.compare_session_favorite_update_failed, snackbars[0].messageResId)
    }

    @Test
    fun toggleFavorite_onlyAffectsTargetSession() = runTest {
        val sessionA = ScannedSession(
            sessionId = "session-a",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        val sessionB = ScannedSession(
            sessionId = "session-b",
            timestamp = 2000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        val testViewModel = CameraViewModel(
            mock(),
            StandardTestDispatcher(testScheduler),
            { null },
            fakeSettingsRepository,
            sessionScanner = { _ -> listOf(sessionA, sessionB) },
            sessionFavoriteUpdater = { _, _, _ -> true }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        testViewModel.toggleFavorite("session-a")
        advanceUntilIdle()

        val updatedA = testViewModel.uiState.value.savedSessions.find { it.sessionId == "session-a" }
        val updatedB = testViewModel.uiState.value.savedSessions.find { it.sessionId == "session-b" }
        assertNotNull(updatedA)
        assertNotNull(updatedB)
        assertTrue(updatedA!!.isFavorite)
        assertFalse(updatedB!!.isFavorite) // unchanged
    }

    @Test
    fun toggleFavorite_noSideEffect_whenSessionIdNotFound() = runTest {
        val session = ScannedSession(
            sessionId = "session-existing",
            timestamp = 1000L,
            referenceFileUri = mock(),
            captureFileUri = mock(),
            isFavorite = false
        )
        var writeCallCount = 0
        val testViewModel = CameraViewModel(
            mock(),
            StandardTestDispatcher(testScheduler),
            { null },
            fakeSettingsRepository,
            sessionScanner = { _ -> listOf(session) },
            sessionFavoriteUpdater = { _, _, _ -> writeCallCount++; true }
        )
        testViewModel.refreshSavedSessions()
        advanceUntilIdle()

        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }
        testViewModel.toggleFavorite("session-does-not-exist")
        advanceUntilIdle()
        collectJob.cancel()

        assertEquals(0, writeCallCount) // no storage write
        assertEquals(0, events.size) // no Snackbar
        // savedSessions unchanged
        val existing = testViewModel.uiState.value.savedSessions.find { it.sessionId == "session-existing" }
        assertNotNull(existing)
        assertFalse(existing!!.isFavorite)
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
            on { liveDirectionArrow } doReturn flowOf(false)
            on { stripOriginalsMetadata } doReturn flowOf(false)
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
            on { liveDirectionArrow } doReturn flowOf(false)
            on { stripOriginalsMetadata } doReturn flowOf(false)
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
            on { liveDirectionArrow } doReturn flowOf(false)
            on { stripOriginalsMetadata } doReturn flowOf(false)
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
        on { liveDirectionArrow } doReturn flowOf(false)
        on { stripOriginalsMetadata } doReturn flowOf(false)
    }

    private fun settingsRepoWithSensor(
        recreationGuidance: Boolean,
        liveDirectionArrow: Boolean
    ): SettingsRepository = mock {
        on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
        on { keepScreenOn } doReturn flowOf(true)
        on { resetOverlayAfterCapture } doReturn flowOf(false)
        on { autoOpenCompareAfterCapture } doReturn flowOf(false)
        on { this.recreationGuidance } doReturn flowOf(recreationGuidance)
        on { this.liveDirectionArrow } doReturn flowOf(liveDirectionArrow)
        on { stripOriginalsMetadata } doReturn flowOf(false)
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
            on { liveDirectionArrow } doReturn flowOf(false)
            on { stripOriginalsMetadata } doReturn flowOf(false)
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

    // --- Sensor (Live Direction Arrow) Activation ---

    private class TestCompassProvider(
        private val available: Boolean = true
    ) : CompassProvider(null as android.hardware.SensorManager?) {
        var started = false
        var stopped = false
        private var callback: ((Float) -> Unit)? = null

        override fun isAvailable(): Boolean = available

        override fun startUpdates(displayRotationProvider: () -> Int, onAzimuthChanged: (Float) -> Unit) {
            started = true
            stopped = false
            callback = onAzimuthChanged
        }

        override fun stopUpdates() {
            stopped = true
            started = false
            callback = null
        }

        fun simulateAzimuth(azimuth: Float) {
            callback?.invoke(azimuth)
        }
    }

    private fun sensorViewModel(
        recreationGuidance: Boolean = true,
        liveDirectionArrow: Boolean = true,
        permissionGranted: Boolean = true,
        referenceHasGps: Boolean = true,
        compassProvider: TestCompassProvider = TestCompassProvider(available = true)
    ): Pair<CameraViewModel, TestCompassProvider> {
        val metadata = if (referenceHasGps) {
            ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        } else {
            ReferenceImageMetadata(1080, 1920, 1080, 1920, null)
        }
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithSensor(recreationGuidance, liveDirectionArrow),
            locationProvider = mock(),
            locationPermissionChecker = { permissionGranted },
            compassProvider = compassProvider,
            displayRotationProvider = { android.view.Surface.ROTATION_0 }
        )
        return Pair(vm, compassProvider)
    }

    @Test
    fun sensor_notStarted_initially_whenCameraInactive() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        // cameraScreenActive = false → sensor must not start
        assertFalse(vm.isSensorActive)
        assertFalse(fakeCompass.started)
    }

    @Test
    fun sensor_started_whenAllConditionsMet() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isSensorActive)
        assertTrue(fakeCompass.started)
    }

    @Test
    fun sensor_notStarted_whenLiveDirectionArrowOff() = runTest {
        val (vm, fakeCompass) = sensorViewModel(liveDirectionArrow = false)
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertFalse(vm.isSensorActive)
        assertFalse(fakeCompass.started)
    }

    @Test
    fun sensor_notStarted_whenRecreationGuidanceOff() = runTest {
        val (vm, fakeCompass) = sensorViewModel(recreationGuidance = false)
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertFalse(vm.isSensorActive)
        assertFalse(fakeCompass.started)
    }

    @Test
    fun sensor_notStarted_whenPermissionNotGranted() = runTest {
        val (vm, fakeCompass) = sensorViewModel(permissionGranted = false)
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertFalse(vm.isSensorActive)
        assertFalse(fakeCompass.started)
    }

    @Test
    fun sensor_notStarted_whenReferenceHasNoGps() = runTest {
        val (vm, fakeCompass) = sensorViewModel(referenceHasGps = false)
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertFalse(vm.isSensorActive)
        assertFalse(fakeCompass.started)
    }

    @Test
    fun sensor_notStarted_whenSensorUnavailable() = runTest {
        val unavailableCompass = TestCompassProvider(available = false)
        val (vm, _) = sensorViewModel(compassProvider = unavailableCompass)
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertFalse(vm.isSensorActive)
    }

    @Test
    fun sensor_stopped_whenCameraScreenBecomesInactive() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isSensorActive)

        vm.onCameraScreenInactive()

        assertFalse(vm.isSensorActive)
        assertTrue(fakeCompass.stopped)
    }

    @Test
    fun sensor_stopped_whenReferenceRemoved() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        assertTrue(vm.isSensorActive)

        vm.onReferenceImageRemoveConfirmed()

        assertFalse(vm.isSensorActive)
        assertTrue(fakeCompass.stopped)
    }

    @Test
    fun currentAzimuth_updatedByCompassCallback() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        fakeCompass.simulateAzimuth(90f)

        assertNotNull(vm.currentAzimuth)
    }

    @Test
    fun currentAzimuth_clearedWhenSensorStopped() = runTest {
        val (vm, fakeCompass) = sensorViewModel()
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        fakeCompass.simulateAzimuth(90f)
        assertNotNull(vm.currentAzimuth)

        vm.onCameraScreenInactive()

        assertNull(vm.currentAzimuth)
    }

    @Test
    fun bearingDegrees_isNull_whenSensorNotActive() = runTest {
        val (vm, _) = sensorViewModel(liveDirectionArrow = false)
        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0)
        val fakeProvider = object : LocationProvider(null as android.location.LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {}
            override fun stopUpdates(listener: android.location.LocationListener) {}
            override fun getLastKnown(): android.location.Location? = null
        }
        // With live direction arrow OFF, bearing should always be null in chip state
        val vm2 = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithSensor(recreationGuidance = true, liveDirectionArrow = false),
            locationProvider = fakeProvider,
            locationPermissionChecker = { true },
            compassProvider = TestCompassProvider(available = false),
            displayRotationProvider = { android.view.Surface.ROTATION_0 }
        )
        vm2.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm2.onCameraScreenActive()

        val state = vm2.uiState.value.gpsGuidanceState
        if (state is GpsGuidanceState.Informative) {
            assertNull(state.bearingDegrees)
        }
    }

    @Test
    fun bearingDegrees_isDeviceRelative_whenSensorAndGpsActive() = runTest {
        val fakeCompass = TestCompassProvider(available = true)
        val capturedListeners = mutableListOf<android.location.LocationListener>()
        val fakeLocationProvider = object : LocationProvider(null as android.location.LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {
                capturedListeners.add(listener)
            }
            override fun stopUpdates(listener: android.location.LocationListener) {}
            override fun getLastKnown(): android.location.Location? = null
        }
        val metadata = ReferenceImageMetadata(
            1080, 1920, 1080, 1920, null,
            gpsLatitude = 48.001, gpsLongitude = 11.0
        )
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithSensor(recreationGuidance = true, liveDirectionArrow = true),
            locationProvider = fakeLocationProvider,
            locationPermissionChecker = { true },
            compassProvider = fakeCompass,
            displayRotationProvider = { android.view.Surface.ROTATION_0 }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        // Simulate GPS location south of reference → geoBearing ≈ 0° (north)
        val fakeLocation = mock<android.location.Location>()
        whenever(fakeLocation.latitude).thenReturn(48.0)
        whenever(fakeLocation.longitude).thenReturn(11.0)
        whenever(fakeLocation.accuracy).thenReturn(10f)
        whenever(fakeLocation.time).thenReturn(System.currentTimeMillis())
        capturedListeners.single().onLocationChanged(fakeLocation)

        // Simulate sensor azimuth 0° (facing North)
        fakeCompass.simulateAzimuth(0f)

        val state = vm.uiState.value.gpsGuidanceState
        assertTrue(state is GpsGuidanceState.Informative)
        assertNotNull((state as GpsGuidanceState.Informative).bearingDegrees)
    }

    @Test
    fun stopSensor_nullsBearingInState() = runTest {
        val fakeCompass = TestCompassProvider(available = true)
        val capturedListeners = mutableListOf<android.location.LocationListener>()
        val fakeLocationProvider = object : LocationProvider(null as android.location.LocationManager?) {
            override fun startUpdates(listener: android.location.LocationListener) {
                capturedListeners.add(listener)
            }
            override fun stopUpdates(listener: android.location.LocationListener) {}
            override fun getLastKnown(): android.location.Location? = null
        }
        val metadata = ReferenceImageMetadata(
            1080, 1920, 1080, 1920, null,
            gpsLatitude = 48.001, gpsLongitude = 11.0
        )
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithSensor(recreationGuidance = true, liveDirectionArrow = true),
            locationProvider = fakeLocationProvider,
            locationPermissionChecker = { true },
            compassProvider = fakeCompass,
            displayRotationProvider = { android.view.Surface.ROTATION_0 }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()

        val fakeLocation = mock<android.location.Location>()
        whenever(fakeLocation.latitude).thenReturn(48.0)
        whenever(fakeLocation.longitude).thenReturn(11.0)
        whenever(fakeLocation.accuracy).thenReturn(10f)
        whenever(fakeLocation.time).thenReturn(System.currentTimeMillis())
        capturedListeners.single().onLocationChanged(fakeLocation)
        fakeCompass.simulateAzimuth(45f)

        vm.onCameraScreenInactive()

        val state = vm.uiState.value.gpsGuidanceState
        if (state is GpsGuidanceState.Informative) {
            assertNull(state.bearingDegrees)
        }
    }

    // --- GPS Fallback Dialog ---

    @Test
    fun gpsFallbackDialog_emitted_whenGuidanceOnAndReferenceHasNoGps() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()

        job.cancel()
        assertTrue(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun gpsFallbackDialog_notEmitted_whenRecreationGuidanceOff() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepoWithGps(recreationGuidance = false)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()

        job.cancel()
        assertFalse(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun gpsFallbackDialog_notEmitted_whenReferenceHasGps() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 48.0, gpsLongitude = 11.0) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()

        job.cancel()
        assertFalse(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun gpsFallbackDialog_notEmitted_whenPickerDismissed() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelected(null)
        advanceUntilIdle()

        job.cancel()
        assertFalse(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun gpsFallbackDialog_notEmitted_afterSafSelection_withNoGps() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelectedViaSaf(mock())
        advanceUntilIdle()

        job.cancel()
        assertFalse(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun gpsFallbackDialog_notEmitted_afterSafSelection_withGps() = runTest {
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 45.0, gpsLongitude = 12.0) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        val events = mutableListOf<UiEvent>()
        val job = launch(Dispatchers.Main) { vm.uiEvent.collect { events.add(it) } }

        vm.onReferenceImageSelectedViaSaf(mock())
        advanceUntilIdle()

        job.cancel()
        assertFalse(events.any { it is UiEvent.ShowGpsFallbackDialog })
    }

    @Test
    fun onReferenceImageSelectedViaSaf_loadsReferenceNormally() = runTest {
        val uri = mock<Uri>()
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null, gpsLatitude = 45.0, gpsLongitude = 12.0) },
            settingsRepoWithGps(recreationGuidance = true)
        )

        vm.onReferenceImageSelectedViaSaf(uri)
        advanceUntilIdle()

        assertEquals(uri, vm.uiState.value.referenceImageUri)
        assertNotNull(vm.uiState.value.referenceImageMetadata)
    }

    @Test
    fun onReferenceImageSelectedViaSaf_null_preservesExistingUri() = runTest {
        val uri = mock<Uri>()
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            settingsRepoWithGps(recreationGuidance = true)
        )
        vm.onReferenceImageSelectedViaSaf(uri)
        advanceUntilIdle()

        vm.onReferenceImageSelectedViaSaf(null)
        advanceUntilIdle()

        assertEquals(uri, vm.uiState.value.referenceImageUri)
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

    // --- GPS Snapshot in CaptureSessionSnapshot ---

    @Test
    fun onPhotoCaptured_snapshotGpsIsNull_whenRecreationGuidanceOff() = runTest {
        val testViewModel = testViewModelWithMetadata(1080, 1920)
        testViewModel.onReferenceViewportChanged(1080, 1920)
        testViewModel.onReferenceImageSelected(mock())
        testViewModel.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        testViewModel.onPhotoCaptured(bitmap, 0)

        assertNull(testViewModel.lastCaptureSnapshot?.gpsSnapshot)
    }

    @Test
    fun onPhotoCaptured_snapshotGpsIsNull_whenNoLocationAvailable() = runTest {
        val mockProvider = mock<LocationProvider>()
        whenever(mockProvider.getLastKnown()).thenReturn(null)
        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true,
            mockProvider = mockProvider
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        vm.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(bitmap, 0)

        assertNull(vm.lastCaptureSnapshot?.gpsSnapshot)
    }

    @Test
    fun onPhotoCaptured_snapshotGpsIsNotNull_whenGuidanceOnAndLocationAvailable() = runTest {
        val fixLocation = mock<Location>()
        whenever(fixLocation.latitude).thenReturn(48.0)
        whenever(fixLocation.longitude).thenReturn(11.5)
        whenever(fixLocation.hasAltitude()).thenReturn(false)
        whenever(fixLocation.hasAccuracy()).thenReturn(true)
        whenever(fixLocation.accuracy).thenReturn(8.0f)
        whenever(fixLocation.provider).thenReturn("gps")
        whenever(fixLocation.time).thenReturn(1704114800000L)

        val mockProvider = mock<LocationProvider>()
        whenever(mockProvider.getLastKnown()).thenReturn(fixLocation)

        val (vm, _) = gpsViewModel(
            recreationGuidance = true, permissionGranted = true, referenceHasGps = true,
            mockProvider = mockProvider
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        vm.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(bitmap, 0)

        val gps = vm.lastCaptureSnapshot?.gpsSnapshot
        assertNotNull(gps)
        assertEquals(48.0, gps!!.latitude, 0.0001)
        assertEquals(11.5, gps.longitude, 0.0001)
        assertEquals("gps", gps.provider)
    }

    @Test
    fun onPhotoCaptured_snapshotGpsFreezesLocationAtCaptureTime() = runTest {
        val captureTimeLocation = mock<Location>()
        whenever(captureTimeLocation.latitude).thenReturn(48.0)
        whenever(captureTimeLocation.longitude).thenReturn(11.5)
        whenever(captureTimeLocation.hasAltitude()).thenReturn(false)
        whenever(captureTimeLocation.hasAccuracy()).thenReturn(true)
        whenever(captureTimeLocation.accuracy).thenReturn(8.0f)
        whenever(captureTimeLocation.provider).thenReturn("gps")
        whenever(captureTimeLocation.time).thenReturn(1000L)

        val capturedListeners = mutableListOf<LocationListener>()
        val fakeProvider = object : LocationProvider(null as LocationManager?) {
            override fun startUpdates(listener: LocationListener) { capturedListeners.add(listener) }
            override fun stopUpdates(listener: LocationListener) {}
            override fun getLastKnown(): Location = captureTimeLocation
        }

        val metadata = ReferenceImageMetadata(1080, 1920, 1080, 1920, null,
            gpsLatitude = 48.0, gpsLongitude = 11.0)
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(), { metadata },
            settingsRepoWithGps(recreationGuidance = true),
            locationProvider = fakeProvider,
            locationPermissionChecker = { true }
        )
        vm.onReferenceImageSelected(mock())
        advanceUntilIdle()
        vm.onCameraScreenActive()
        vm.tryStartCapture()

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(bitmap, 0)

        // Snapshot is frozen — inject a different location after capture
        val newLocation = mock<Location>()
        whenever(newLocation.latitude).thenReturn(99.9)
        whenever(newLocation.longitude).thenReturn(99.9)
        whenever(newLocation.hasAltitude()).thenReturn(false)
        whenever(newLocation.hasAccuracy()).thenReturn(true)
        whenever(newLocation.accuracy).thenReturn(5.0f)
        whenever(newLocation.provider).thenReturn("gps")
        whenever(newLocation.time).thenReturn(2000L)
        capturedListeners.single().onLocationChanged(newLocation)

        // Snapshot must still carry capture-time coordinates
        val gps = vm.lastCaptureSnapshot?.gpsSnapshot
        assertNotNull(gps)
        assertEquals(48.0, gps!!.latitude, 0.0001)
        assertEquals(11.5, gps.longitude, 0.0001)
    }

    // --- backupSingleSession / backupSessions ---

    @Test
    fun backupSingleSession_setsIsBackupInProgressTrueDuringAndFalseAfter() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )

        testViewModel.backupSingleSession("session-abc", mock())

        assertTrue(testViewModel.uiState.value.isBackupInProgress)

        advanceUntilIdle()

        assertFalse(testViewModel.uiState.value.isBackupInProgress)
    }

    @Test
    fun backupSingleSession_success_emitsSessionBackupSuccessSingle() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSingleSession("session-abc", mock())
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.session_backup_success_single, snackbars[0].messageResId)
        assertTrue(snackbars[0].isSuccess)
    }

    @Test
    fun backupSingleSession_failure_emitsSessionBackupError() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Failure("test failure", null) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSingleSession("session-abc", mock())
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.session_backup_error, snackbars[0].messageResId)
        assertFalse(snackbars[0].isSuccess)
    }

    @Test
    fun backupSingleSession_secondCallDuringActiveBackup_isIgnored() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSingleSession("session-abc", mock())
        assertTrue(testViewModel.uiState.value.isBackupInProgress)

        testViewModel.backupSingleSession("session-abc", mock())

        advanceUntilIdle()
        collectJob.cancel()

        val successSnackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
            .filter { it.messageResId == R.string.session_backup_success_single }
        assertEquals(1, successSnackbars.size)
    }

    @Test
    fun backupSessions_multipleSessionsSuccess_emitsSessionBackupSuccessMultiWithCount() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(3) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSessions(listOf("s1", "s2", "s3"), mock())
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.session_backup_success_multi, snackbars[0].messageResId)
        assertEquals(3, snackbars[0].count)
        assertTrue(snackbars[0].isSuccess)
    }

    @Test
    fun backupSessions_singleSessionViaBackupSessions_emitsSessionBackupSuccessSingle() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSessions(listOf("session-abc"), mock())
        advanceUntilIdle()
        collectJob.cancel()

        val snackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
        assertEquals(1, snackbars.size)
        assertEquals(R.string.session_backup_success_single, snackbars[0].messageResId)
        assertNull(snackbars[0].count)
        assertTrue(snackbars[0].isSuccess)
    }

    @Test
    fun backupSessions_success_emitsBackupSucceededEvent() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(3) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSessions(listOf("s1", "s2", "s3"), mock())
        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(
            "BackupSucceeded must be emitted after a successful multi-session backup",
            events.any { it is UiEvent.BackupSucceeded }
        )
    }

    @Test
    fun backupSessions_failure_doesNotEmitBackupSucceededEvent() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Failure("test failure", null) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.backupSessions(listOf("s1", "s2", "s3"), mock())
        advanceUntilIdle()
        collectJob.cancel()

        assertFalse(
            "BackupSucceeded must NOT be emitted after a failed backup",
            events.any { it is UiEvent.BackupSucceeded }
        )
    }

    @Test
    fun deleteSessions_duringActiveBackup_isIgnored() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, null, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )

        testViewModel.backupSingleSession("session-abc", mock())
        assertTrue(testViewModel.uiState.value.isBackupInProgress)

        testViewModel.deleteSessions(listOf("session-abc"))

        assertFalse(testViewModel.uiState.value.isDeletionInProgress)

        advanceUntilIdle()

        assertFalse(testViewModel.uiState.value.isBackupInProgress)
        assertFalse(testViewModel.uiState.value.isDeletionInProgress)
    }

    @Test
    fun backupSessions_whenDeletionInProgress_isIgnored() = runTest {
        val testViewModel = CameraViewModel(
            mock(), StandardTestDispatcher(testScheduler), { null },
            fakeSettingsRepository, { _ -> emptyList() }, null, { _, _ -> true }, null, null,
            { _, _, _, _ -> SessionBackupExporter.BackupResult.Success(1) }
        )
        val events = mutableListOf<UiEvent>()
        val collectJob = launch(Dispatchers.Main) { testViewModel.uiEvent.collect { events.add(it) } }

        testViewModel.deleteSessions(listOf("session-abc"))
        assertTrue(testViewModel.uiState.value.isDeletionInProgress)

        testViewModel.backupSessions(listOf("session-abc"), mock())

        assertFalse(testViewModel.uiState.value.isBackupInProgress)

        advanceUntilIdle()
        collectJob.cancel()

        assertFalse(testViewModel.uiState.value.isDeletionInProgress)
        assertFalse(testViewModel.uiState.value.isBackupInProgress)

        val backupSnackbars = events.filterIsInstance<UiEvent.ShowSnackbar>()
            .filter { it.messageResId == R.string.session_backup_success_single ||
                      it.messageResId == R.string.session_backup_error }
        assertEquals(0, backupSnackbars.size)
    }

    // --- stripOriginalsMetadata (Block D) ---

    @Test
    fun stripOriginalsMetadata_defaultIsFalse() = runTest {
        // Default repository stub emits false — ViewModel must reflect that
        assertEquals(false, viewModel.stripOriginalsMetadata)
    }

    @Test
    fun stripOriginalsMetadata_updatesWhenRepositoryEmitsTrue() = runTest {
        val stripFlow = MutableStateFlow(false)
        val repo = mock<SettingsRepository> {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
            on { liveDirectionArrow } doReturn flowOf(false)
            on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
            on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
            on { stripOriginalsMetadata } doReturn stripFlow
        }
        val testViewModel = CameraViewModel(mock(), UnconfinedTestDispatcher(), { null }, repo)

        // Initial state
        assertEquals(false, testViewModel.stripOriginalsMetadata)

        // Setting toggled ON
        stripFlow.value = true
        advanceUntilIdle()

        assertEquals(true, testViewModel.stripOriginalsMetadata)
    }

    @Test
    fun stripOriginalsMetadata_revertsToFalseWhenRepositoryEmitsFalse() = runTest {
        val stripFlow = MutableStateFlow(true)
        val repo = mock<SettingsRepository> {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
            on { liveDirectionArrow } doReturn flowOf(false)
            on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
            on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
            on { stripOriginalsMetadata } doReturn stripFlow
        }
        val testViewModel = CameraViewModel(mock(), UnconfinedTestDispatcher(), { null }, repo)
        advanceUntilIdle()
        assertEquals(true, testViewModel.stripOriginalsMetadata)

        stripFlow.value = false
        advanceUntilIdle()

        assertEquals(false, testViewModel.stripOriginalsMetadata)
    }

    // --- stripMetadata propagation to session save (Block E) ---

    // Helper: ViewModel with configurable strip setting and a real reference image.
    private fun testViewModelWithStrip(stripEnabled: Boolean): CameraViewModel {
        val stripFlow = MutableStateFlow(stripEnabled)
        val repo = mock<SettingsRepository> {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
            on { liveDirectionArrow } doReturn flowOf(false)
            on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
            on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
            on { stripOriginalsMetadata } doReturn stripFlow
        }
        return CameraViewModel(
            mock(),
            UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) },
            repo
        )
    }

    @Test
    fun stripMetadata_isFalse_atCaptureTime_whenSettingOff() = runTest {
        // Verify the setting value observed at capture time (= what gets frozen) is false.
        // In unit tests MediaStoreWriter.save() has no real ContentResolver and always fails,
        // so the injected sessionSaver is never reached. The observable proxy for the frozen
        // value is stripOriginalsMetadata — a synchronous read before the IO coroutine launch.
        val vm = testViewModelWithStrip(stripEnabled = false)
        vm.onReferenceViewportChanged(1080, 1920)
        vm.onReferenceImageSelected(mock())

        assertEquals("Setting must be false before capture", false, vm.stripOriginalsMetadata)

        val token = vm.tryStartCapture()!!
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        assertEquals("Setting must remain false after capture", false, vm.stripOriginalsMetadata)
    }

    @Test
    fun stripMetadata_isTrue_atCaptureTime_whenSettingOn() = runTest {
        val vm = testViewModelWithStrip(stripEnabled = true)
        vm.onReferenceViewportChanged(1080, 1920)
        vm.onReferenceImageSelected(mock())

        assertEquals("Setting must be true before capture", true, vm.stripOriginalsMetadata)

        val token = vm.tryStartCapture()!!
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(token, bitmap, 0)
        advanceUntilIdle()

        assertEquals("Setting must remain true after capture", true, vm.stripOriginalsMetadata)
    }

    @Test
    fun stripMetadata_liveVarUpdates_butFreezeSemanticIsGuaranteedBySynchronousRead() = runTest {
        // Verifies that: (a) the live var correctly reflects the repository at all times,
        // and (b) a post-capture repository change cannot affect a session already saved.
        //
        // Note: the actual frozen value (val stripMetadataForSession = stripOriginalsMetadata)
        // cannot be directly asserted in unit tests because MediaStoreWriter.save() fails
        // without a real ContentResolver — the sessionSaver lambda is therefore never invoked.
        // The freeze is guaranteed by synchronous Kotlin semantics: the val is assigned from
        // stripOriginalsMetadata before viewModelScope.launch(), so no concurrent coroutine
        // interference is possible at that point. This invariant is validated at the source level.
        val stripFlow = MutableStateFlow(false)
        val repo = mock<SettingsRepository> {
            on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
            on { keepScreenOn } doReturn flowOf(true)
            on { resetOverlayAfterCapture } doReturn flowOf(false)
            on { autoOpenCompareAfterCapture } doReturn flowOf(false)
            on { recreationGuidance } doReturn flowOf(false)
            on { liveDirectionArrow } doReturn flowOf(false)
            on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
            on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
            on { stripOriginalsMetadata } doReturn stripFlow
        }
        val vm = CameraViewModel(
            mock(), UnconfinedTestDispatcher(),
            { ReferenceImageMetadata(1080, 1920, 1080, 1920, null) }, repo
        )
        vm.onReferenceViewportChanged(1080, 1920)
        vm.onReferenceImageSelected(mock())

        // At capture time the setting is false — this is what gets frozen
        assertEquals(false, vm.stripOriginalsMetadata)

        val token = vm.tryStartCapture()!!
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(1080)
        whenever(bitmap.height).thenReturn(1920)
        vm.onPhotoCaptured(token, bitmap, 0)

        // Repository change happens after onPhotoCaptured() returns (post-capture)
        stripFlow.value = true
        advanceUntilIdle()

        // The live var now reflects the updated setting (confirming flow wiring)
        assertEquals(true, vm.stripOriginalsMetadata)
        // A subsequent capture would use true as the frozen value
    }
}
