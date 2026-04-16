package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.ghostshot.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * The two supported target aspect ratios for the camera viewport, capture, and overlay alignment.
 *
 * Maps reference image proportions to one of two well-supported CameraX sensor ratios.
 * Orientation (portrait vs landscape) is determined by the device at render time, not stored here.
 */
enum class TargetAspectRatio { RATIO_4_3, RATIO_16_9 }

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

data class ReferenceImageMetadata(
    val rawWidth: Int,
    val rawHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val exifOrientation: Int?
)

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
 * @param overlayOffsetY Vertical position as a normalised fraction of the container height.
 *   Same semantics as [overlayOffsetX].
 * @param overlayScale Scale factor applied to the overlay. 1.0 represents the default fit size.
 *   Clamped to [CameraViewModel.MIN_SCALE, CameraViewModel.MAX_SCALE].
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
    val interactionMode: InteractionMode = InteractionMode.OVERLAY_ADJUST,
    val activeAspectRatio: TargetAspectRatio = TargetAspectRatio.RATIO_16_9,
    val referenceImageDisplayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
    val referenceImageHasViewportMismatch: Boolean = false,
    val referenceImageMetadata: ReferenceImageMetadata? = null
)

/**
 * One-time UI events emitted by [CameraViewModel] for consumption by [CameraScreen].
 *
 * Using a [SharedFlow] ensures each event is delivered exactly once to active collectors
 * and is not retained in [CameraUiState], keeping ephemeral feedback separate from
 * persistent UI state.
 */
sealed interface UiEvent {
    /** Display a Snackbar with the given message. [isSuccess] controls visual style. */
    data class ShowSnackbar(@StringRes val messageResId: Int, val isSuccess: Boolean = false) : UiEvent
}

/**
 * ViewModel for the camera screen.
 *
 * Owns and exposes [CameraUiState] as a [StateFlow]. Because it is a ViewModel,
 * state survives configuration changes (rotation) within the same session.
 * No state is written to persistent storage; all fields reset on app restart.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var referenceImageMetadataReader: (Uri) -> ReferenceImageMetadata? = { uri ->
        ReferenceImageMetadataReader.read {
            context.contentResolver.openInputStream(uri)
        }
    }

    private var viewportSize: Pair<Int, Int>? = null
    private var displayModeChangedByUser = false

    /** Used in unit tests to inject a controlled dispatcher and metadata reader. */
    internal constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
        referenceImageMetadataReader: (Uri) -> ReferenceImageMetadata?
    ) : this(context) {
        this.ioDispatcher = ioDispatcher
        this.referenceImageMetadataReader = referenceImageMetadataReader
    }

    private val _uiState = MutableStateFlow(CameraUiState())

    /** Observed by [CameraScreen] to render the current UI state. */
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()

    /** One-time events collected by [CameraScreen] to trigger Snackbar messages. */
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    companion object {
        /** Minimum allowed scale for the reference image overlay. */
        const val MIN_SCALE = 0.5f
        /** Maximum allowed scale for the reference image overlay. */
        const val MAX_SCALE = 3.0f
    }

    /**
     * Called when the user selects a reference image via the photo picker.
     *
     * A null [uri] means the picker was dismissed without a selection; in that case
     * the existing [CameraUiState.referenceImageUri] is preserved unchanged.
     *
     * @param uri The URI returned by the system photo picker, or null if dismissed.
     */
    fun onReferenceImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(ioDispatcher) {
            val metadata = referenceImageMetadataReader(uri) ?: return@launch
            val longer = maxOf(metadata.orientedWidth, metadata.orientedHeight).toFloat()
            val shorter = minOf(metadata.orientedWidth, metadata.orientedHeight).toFloat()
            val ratio = longer / shorter
            val newAspectRatio = if (abs(ratio - 4f / 3f) <= abs(ratio - 16f / 9f)) {
                TargetAspectRatio.RATIO_4_3
            } else {
                TargetAspectRatio.RATIO_16_9
            }
            val recommendation = getDisplayRecommendation(metadata, viewportSize)
            displayModeChangedByUser = false
            _uiState.update { current ->
                val formatChanged = current.activeAspectRatio != newAspectRatio
                current.copy(
                    referenceImageUri = uri,
                    activeAspectRatio = newAspectRatio,
                    referenceImageMetadata = metadata,
                    referenceImageHasViewportMismatch = recommendation.hasStrongMismatch,
                    referenceImageDisplayMode = recommendation.startMode,
                    overlayOffsetX = if (formatChanged) 0f else current.overlayOffsetX,
                    overlayOffsetY = if (formatChanged) 0f else current.overlayOffsetY,
                    overlayScale = if (formatChanged) 1f else current.overlayScale
                )
            }
        }
    }

    fun onReferenceViewportChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewportSize = Pair(width, height)
        _uiState.update { current ->
            val metadata = current.referenceImageMetadata ?: return@update current
            val recommendation = getDisplayRecommendation(metadata, viewportSize)
            current.copy(
                referenceImageHasViewportMismatch = recommendation.hasStrongMismatch,
                referenceImageDisplayMode = if (!displayModeChangedByUser) {
                    recommendation.startMode
                } else {
                    current.referenceImageDisplayMode
                }
            )
        }
    }

    fun onReferenceImageDisplayModeChanged(displayMode: ReferenceImageDisplayMode) {
        displayModeChangedByUser = true
        _uiState.update { it.copy(referenceImageDisplayMode = displayMode) }
    }

    fun onReferenceImageDisplayModeToggle() {
        val next = when (_uiState.value.referenceImageDisplayMode) {
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE -> ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        }
        onReferenceImageDisplayModeChanged(next)
    }

    /**
     * Called when the user moves the transparency slider for the reference image overlay.
     *
     * The value is clamped to [0.1, 0.9] regardless of the slider's own valueRange.
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
     * by container width/height respectively). Offsets are clamped to [-0.5, 0.5].
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
     * Called on each pinch event while the user scales the reference image overlay.
     *
     * [scaleFactor] is the multiplicative zoom step for this single event (e.g. 1.1 = 10%
     * larger). Applied cumulatively and clamped to [MIN_SCALE, MAX_SCALE].
     *
     * @param scaleFactor Multiplicative scale step from detectTransformGestures zoom field.
     */
    fun onOverlayScaled(scaleFactor: Float) {
        _uiState.update {
            it.copy(overlayScale = (it.overlayScale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE))
        }
    }

    /**
     * Resets the overlay position and scale to their default values.
     *
     * Resets position and scale, and restores the automatically recommended display
     * mode for the current reference image and viewport. The reference image URI and
     * opacity are intentionally preserved.
     */
    fun onOverlayReset() {
        displayModeChangedByUser = false
        _uiState.update { current ->
            val recommendation = current.referenceImageMetadata?.let { metadata ->
                getDisplayRecommendation(metadata, viewportSize)
            }
            current.copy(
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                overlayScale = 1f,
                referenceImageDisplayMode = recommendation?.startMode
                    ?: current.referenceImageDisplayMode,
                referenceImageHasViewportMismatch = recommendation?.hasStrongMismatch
                    ?: current.referenceImageHasViewportMismatch
            )
        }
    }

    /**
     * Called by [CameraScreen] when [ImageCapture] delivers a captured frame successfully.
     *
     * Runs the full pipeline on [Dispatchers.IO]:
     * rotation correction → compositing with overlay (if active) → MediaStore save.
     * Emits a [UiEvent.ShowSnackbar] with the outcome.
     *
     * @param bitmap Raw bitmap from ImageProxy.toBitmap(), may require rotation correction.
     * @param rotationDegrees Clockwise degrees to apply, from ImageInfo.rotationDegrees.
     */
    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val corrected = rotateBitmap(bitmap, rotationDegrees)
                val result = MediaStoreWriter.save(context.contentResolver, corrected)
                _uiEvent.emit(
                    UiEvent.ShowSnackbar(
                        messageResId = if (result.isSuccess) R.string.capture_saved else R.string.capture_failed,
                        isSuccess = result.isSuccess
                    )
                )
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
            }
        }
    }

    /**
     * Called by [CameraScreen] when [ImageCapture] reports a hardware or session error
     * before a frame could be delivered.
     */
    fun onPhotoCaptureError() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getDisplayRecommendation(
        metadata: ReferenceImageMetadata,
        viewportSize: Pair<Int, Int>?
    ): ReferenceImageDisplayRecommendation {
        val (viewportWidth, viewportHeight) = viewportSize
            ?: return ReferenceImageDisplayRecommendation(
                cropLoss = 0f,
                hasStrongMismatch = false,
                startMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
            )
        return ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = metadata.orientedWidth,
            orientedImageHeight = metadata.orientedHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
    }
}
