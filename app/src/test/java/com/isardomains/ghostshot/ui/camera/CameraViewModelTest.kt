package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        viewModel = CameraViewModel()
    }

    // --- onReferenceImageSelected ---

    @Test
    fun onReferenceImageSelected_setsUri() {
        val uri = mock<Uri>()
        viewModel.onReferenceImageSelected(uri)
        assertEquals(uri, viewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onReferenceImageSelected_null_preservesExistingUri() {
        val uri = mock<Uri>()
        viewModel.onReferenceImageSelected(uri)
        viewModel.onReferenceImageSelected(null)
        assertEquals(uri, viewModel.uiState.value.referenceImageUri)
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
        viewModel.onOverlayReset()
        assertEquals(0f, viewModel.uiState.value.overlayOffsetX)
        assertEquals(0f, viewModel.uiState.value.overlayOffsetY)
        assertEquals(1f, viewModel.uiState.value.overlayScale)
    }

    @Test
    fun onOverlayReset_preservesReferenceImageUri() {
        val uri = mock<Uri>()
        viewModel.onReferenceImageSelected(uri)
        viewModel.onOverlayDragged(0.1f, 0.1f)
        viewModel.onOverlayReset()
        assertEquals(uri, viewModel.uiState.value.referenceImageUri)
    }

    @Test
    fun onOverlayReset_preservesOverlayAlpha() {
        viewModel.onOverlayAlphaChanged(0.8f)
        viewModel.onOverlayDragged(0.1f, 0.1f)
        viewModel.onOverlayReset()
        assertEquals(0.8f, viewModel.uiState.value.overlayAlpha)
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
}
