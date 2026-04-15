package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @param overlayOffsetX Horizontal offset of the overlay in dp relative to its centered default position.
 * @param overlayOffsetY Vertical offset of the overlay in dp relative to its centered default position.
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
}
