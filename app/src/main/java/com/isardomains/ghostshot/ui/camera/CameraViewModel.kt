package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Controls how touch gestures are interpreted on the camera screen.
 *
 * [OVERLAY_ADJUST]: one-finger drag moves the overlay, two-finger pinch scales it.
 * [CAMERA_ZOOM]: two-finger pinch controls camera zoom only.
 */
enum class InteractionMode {
    OVERLAY_ADJUST,
    CAMERA_ZOOM
}

/**
 * Represents the complete UI state for the camera screen during an active session.
 *
 * This state is preserved across rotation and lifecycle recreation but is NOT
 * persisted across full app restarts. On restart the app begins with the defaults below.
 *
 * @param referenceImageUri URI of the currently selected reference image, or null if none is selected.
 * @param overlayOffsetX Horizontal position of the overlay as a normalised fraction of the
 *   container width. 0.0 = centred, 0.5 = shifted one full half-width to the right,
 *   -0.5 = shifted one full half-width to the left. Clamped to [-0.5, 0.5].
 *   Storing a fraction rather than absolute pixels makes the position rotation-invariant:
 *   after a configuration change the same fraction maps to a proportional pixel offset in
 *   the new container size, so the overlay never drifts off-screen on rotation.
 * @param overlayOffsetY Vertical position as a normalised fraction of the container height.
 *   Same semantics as [overlayOffsetX].
 * @param overlayScale Scale factor applied to the overlay. 1.0 represents the default fit size.
 * @param overlayAlpha Opacity of the overlay, clamped to [0.1, 0.9]. Default is 0.5.
 * @param isGridVisible Whether the 3x3 rule-of-thirds grid is currently shown.
 * @param interactionMode The currently active gesture interaction mode.
 */
data class CameraUiState(
    val referenceImageUri: Uri? = null,
    val overlayOffsetX: Float = 0f,
    val overlayOffsetY: Float = 0f,
    val overlayScale: Float = 1f,
    val overlayAlpha: Float = 0.5f,
    val isGridVisible: Boolean = false,
    val interactionMode: InteractionMode = InteractionMode.OVERLAY_ADJUST
)

/**
 * ViewModel for the camera screen.
 *
 * Owns and exposes [CameraUiState] as a [StateFlow]. Because it is a ViewModel,
 * state survives configuration changes (rotation) within the same session.
 * No state is written to persistent storage; all fields reset on app restart.
 */
@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())

    /** Observed by [CameraScreen] to render the current UI state. */
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /**
     * Called when the user selects a reference image via the photo picker.
     *
     * A null [uri] means the picker was dismissed without a selection; in that case
     * the existing [CameraUiState.referenceImageUri] is preserved unchanged.
     *
     * @param uri The URI returned by the system photo picker, or null if the picker was dismissed.
     */
    fun onReferenceImageSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(referenceImageUri = uri) }
    }

    /**
     * Called when the user moves the transparency slider for the reference image overlay.
     *
     * The value is clamped to [0.1, 0.9] to enforce the range documented on
     * [CameraUiState.overlayAlpha], regardless of the slider's own valueRange.
     *
     * @param alpha The new opacity value emitted by the slider.
     */
    fun onOverlayAlphaChanged(alpha: Float) {
        _uiState.update { it.copy(overlayAlpha = alpha.coerceIn(0.1f, 0.9f)) }
    }

    /**
     * Called on each drag event while the user repositions the reference image overlay.
     *
     * [dx] and [dy] are normalised fractions of the container size (pixel delta divided
     * by container width/height respectively), converted by the caller before this method
     * is invoked. Accumulating fractions instead of raw pixels keeps the stored position
     * meaningful across rotation: the same fraction produces a proportional pixel offset
     * in any container size.
     *
     * Offsets are clamped to [-0.5, 0.5] so the overlay centre can never move beyond the
     * container edge, guaranteeing it remains visible regardless of orientation.
     *
     * @param dx Normalised horizontal drag delta (dragPixels.x / containerWidth).
     * @param dy Normalised vertical drag delta (dragPixels.y / containerHeight).
     */
    fun onOverlayDragged(dx: Float, dy: Float) {
        _uiState.update {
            it.copy(
                overlayOffsetX = (it.overlayOffsetX + dx).coerceIn(-0.5f, 0.5f),
                overlayOffsetY = (it.overlayOffsetY + dy).coerceIn(-0.5f, 0.5f)
            )
        }
    }

    /**
     * Resets the overlay position and scale to their default values.
     *
     * Only [CameraUiState.overlayOffsetX], [CameraUiState.overlayOffsetY], and
     * [CameraUiState.overlayScale] are affected. The selected reference image and
     * current opacity are intentionally preserved.
     */
    fun onOverlayReset() {
        _uiState.update {
            it.copy(
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                overlayScale = 1f
            )
        }
    }
}
