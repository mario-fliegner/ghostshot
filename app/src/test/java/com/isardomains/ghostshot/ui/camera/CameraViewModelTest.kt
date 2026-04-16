package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

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
