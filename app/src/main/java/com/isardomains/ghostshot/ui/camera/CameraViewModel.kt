// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/CameraViewModel.kt
package com.isardomains.ghostshot.ui.camera

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import android.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.core.image.CenterCropNormalizer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/**
 * The two supported target aspect ratios for the camera viewport, capture, and overlay alignment.
 *
 * Maps reference image proportions to one of two well-supported CameraX sensor ratios.
 * Orientation (portrait vs landscape) is determined by the device at render time, not stored here.
 */
private const val UNDO_TIMEOUT_MS = 2500L

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
 * @param viewportWidth Width of the camera preview viewport in pixels. 0 until first layout.
 * @param viewportHeight Height of the camera preview viewport in pixels. 0 until first layout.
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
    val referenceImageMetadata: ReferenceImageMetadata? = null,
    val isCaptureInProgress: Boolean = false,
    val canUndoReferenceRemoval: Boolean = false,
    val referenceRemovalUndoGeneration: Long = 0L,
    val undoExpiresAtMillis: Long = 0L,
    val captureSuccessGeneration: Long = 0L,
    val captureSuccessHadReference: Boolean = false,
    val compareInput: CompareInput? = null,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0,
    val savedSessions: List<ScannedSession> = emptyList()
)

/**
 * The current valid input pair for the fullscreen compare flow.
 *
 * The pair is only valid when both URIs belong to the same successful capture moment.
 * [sessionId] and [timestamp] are populated when the capture produced a persisted session,
 * enabling Delete and timestamp display in CompareScreen.
 */
data class CompareInput(
    val referenceImageUri: Uri,
    val captureImageUri: Uri,
    val sessionId: String? = null,
    val timestamp: Long? = null
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
    /** Notifies the UI that the pending undo snapshot has been invalidated by a new reference load. */
    data object UndoInvalidated : UiEvent
}

private data class ReferenceUndoSnapshot(
    val referenceImageUri: Uri,
    val referenceImageMetadata: ReferenceImageMetadata?,
    val referenceImageDisplayMode: ReferenceImageDisplayMode,
    val overlayOffsetX: Float,
    val overlayOffsetY: Float,
    val overlayScale: Float,
    val overlayAlpha: Float,
    val displayModeChangedByUser: Boolean
)

/**
 * The internal result of a successfully completed capture pipeline run.
 *
 * Only produced when [MediaStoreWriter.save] succeeds.
 *
 * @param savedUri URI of the image written to MediaStore.
 */
internal data class CaptureResult(
    val savedUri: Uri
)

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

    private var sessionScanner: (Context) -> List<ScannedSession> =
        { ctx -> SessionScanner.scan(ctx) }

    private var displayModeChangedByUser = false
    private var undoSnapshot: ReferenceUndoSnapshot? = null
    private var undoTimeoutJob: Job? = null
    private val clock: () -> Long = { System.currentTimeMillis() }
    private var referenceImageSelectionJob: Job? = null
    private var referenceImageSelectionRequestId = 0L

    // Visible for testing — holds the result of the most recent successfully completed capture.
    // Null until the first successful save, and reset to null on every new capture attempt
    // (success, failure, error, or interrupt). Never reflects a failed or incomplete capture.
    @Volatile
    internal var lastCaptureResult: CaptureResult? = null

    /** Used in unit tests to inject a controlled dispatcher and metadata reader. */
    internal constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
        referenceImageMetadataReader: (Uri) -> ReferenceImageMetadata?,
        sessionScanner: (Context) -> List<ScannedSession> = { ctx -> SessionScanner.scan(ctx) }
    ) : this(context) {
        this.ioDispatcher = ioDispatcher
        this.referenceImageMetadataReader = referenceImageMetadataReader
        this.sessionScanner = sessionScanner
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

        private const val DEBUG_TAG = "ComparisonCropDebug"
    }

    /**
     * Called when the user selects a reference image via the photo picker.
     *
     * A null [uri] means the picker was dismissed without a selection; in that case
     * the existing [CameraUiState.referenceImageUri] is preserved unchanged.
     *
     * @param uri The URI returned by the system photo picker, or null if dismissed.
     */
    private fun clearUndoState() {
        undoSnapshot = null
        undoTimeoutJob = null
        _uiState.update { it.copy(canUndoReferenceRemoval = false, undoExpiresAtMillis = 0L) }
    }

    fun onReferenceImageSelected(uri: Uri?) {
        if (uri == null) return
        undoTimeoutJob?.cancel()
        undoTimeoutJob = null
        referenceImageSelectionJob?.cancel()
        val requestId = ++referenceImageSelectionRequestId
        referenceImageSelectionJob = viewModelScope.launch {
            val metadata = withContext(ioDispatcher) {
                referenceImageMetadataReader(uri)
            } ?: return@launch
            if (requestId != referenceImageSelectionRequestId) return@launch

            val longer = maxOf(metadata.orientedWidth, metadata.orientedHeight).toFloat()
            val shorter = minOf(metadata.orientedWidth, metadata.orientedHeight).toFloat()
            val ratio = longer / shorter
            val newAspectRatio = if (abs(ratio - 4f / 3f) <= abs(ratio - 16f / 9f)) {
                TargetAspectRatio.RATIO_4_3
            } else {
                TargetAspectRatio.RATIO_16_9
            }
            val hadUndo = undoSnapshot != null
            undoSnapshot = null
            displayModeChangedByUser = false
            _uiState.update { current ->
                val recommendation = getDisplayRecommendation(metadata, current.viewportWidth, current.viewportHeight)
                val formatChanged = current.activeAspectRatio != newAspectRatio
                current.copy(
                    referenceImageUri = uri,
                    activeAspectRatio = newAspectRatio,
                    referenceImageMetadata = metadata,
                    referenceImageHasViewportMismatch = recommendation.hasStrongMismatch,
                    referenceImageDisplayMode = recommendation.startMode,
                    overlayOffsetX = if (formatChanged) 0f else current.overlayOffsetX,
                    overlayOffsetY = if (formatChanged) 0f else current.overlayOffsetY,
                    overlayScale = if (formatChanged) 1f else current.overlayScale,
                    canUndoReferenceRemoval = false,
                    undoExpiresAtMillis = 0L,
                    compareInput = null
                )
            }
            if (hadUndo) {
                _uiEvent.emit(UiEvent.UndoInvalidated)
            }
        }
    }

    fun onReferenceImageRemoveConfirmed() {
        val current = _uiState.value
        val hasReference = current.referenceImageUri != null
        if (hasReference) {
            undoSnapshot = ReferenceUndoSnapshot(
                referenceImageUri = current.referenceImageUri,
                referenceImageMetadata = current.referenceImageMetadata,
                referenceImageDisplayMode = current.referenceImageDisplayMode,
                overlayOffsetX = current.overlayOffsetX,
                overlayOffsetY = current.overlayOffsetY,
                overlayScale = current.overlayScale,
                overlayAlpha = current.overlayAlpha,
                displayModeChangedByUser = displayModeChangedByUser
            )
        }
        referenceImageSelectionJob?.cancel()
        referenceImageSelectionJob = null
        referenceImageSelectionRequestId++
        displayModeChangedByUser = false
        undoTimeoutJob?.cancel()
        val expiresAt = if (hasReference) clock() + UNDO_TIMEOUT_MS else 0L
        _uiState.update {
            it.copy(
                referenceImageUri = null,
                referenceImageMetadata = null,
                referenceImageHasViewportMismatch = false,
                referenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                overlayScale = 1f,
                compareInput = null,
                canUndoReferenceRemoval = if (hasReference) true else it.canUndoReferenceRemoval,
                referenceRemovalUndoGeneration = if (hasReference) {
                    it.referenceRemovalUndoGeneration + 1L
                } else {
                    it.referenceRemovalUndoGeneration
                },
                undoExpiresAtMillis = if (hasReference) expiresAt else it.undoExpiresAtMillis
            )
        }
        if (hasReference) {
            undoTimeoutJob = viewModelScope.launch {
                delay(UNDO_TIMEOUT_MS)
                clearUndoState()
            }
        }
    }

    fun onReferenceImageRemoveUndo() {
        val snapshot = undoSnapshot ?: run {
            _uiState.update { it.copy(canUndoReferenceRemoval = false) }
            return
        }
        undoTimeoutJob?.cancel()
        undoTimeoutJob = null
        undoSnapshot = null
        displayModeChangedByUser = snapshot.displayModeChangedByUser
        _uiState.update { current ->
            val recommendation = snapshot.referenceImageMetadata?.let { metadata ->
                getDisplayRecommendation(metadata, current.viewportWidth, current.viewportHeight)
            }
            current.copy(
                referenceImageUri = snapshot.referenceImageUri,
                referenceImageMetadata = snapshot.referenceImageMetadata,
                referenceImageDisplayMode = snapshot.referenceImageDisplayMode,
                referenceImageHasViewportMismatch = recommendation?.hasStrongMismatch ?: false,
                overlayOffsetX = snapshot.overlayOffsetX,
                overlayOffsetY = snapshot.overlayOffsetY,
                overlayScale = snapshot.overlayScale,
                overlayAlpha = snapshot.overlayAlpha,
                canUndoReferenceRemoval = false,
                undoExpiresAtMillis = 0L
            )
        }
    }

    fun onReferenceViewportChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _uiState.update { current ->
            val metadata = current.referenceImageMetadata
            val recommendation = if (metadata != null) {
                getDisplayRecommendation(metadata, width, height)
            } else null
            current.copy(
                viewportWidth = width,
                viewportHeight = height,
                referenceImageHasViewportMismatch = recommendation?.hasStrongMismatch
                    ?: current.referenceImageHasViewportMismatch,
                referenceImageDisplayMode = if (recommendation != null && !displayModeChangedByUser) {
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
                getDisplayRecommendation(metadata, current.viewportWidth, current.viewportHeight)
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
     * Atomically acquires the capture lock.
     *
     * @return true if the lock was acquired, false if a capture is already in progress.
     */
    fun tryStartCapture(): Boolean {
        while (true) {
            val current = _uiState.value
            if (current.isCaptureInProgress) return false
            if (_uiState.compareAndSet(
                    current,
                    current.copy(isCaptureInProgress = true, compareInput = null)
                )
            ) {
                return true
            }
        }
    }

    /**
     * Releases an in-flight capture lock when the UI/camera composition is torn down
     * before CameraX can reliably deliver its success or error callback.
     */
    fun onCaptureInterrupted() {
        lastCaptureResult = null
        _uiState.update { it.copy(compareInput = null) }
        finishCapture()
    }

    /**
     * Called by [CameraScreen] when [ImageCapture] delivers a captured frame successfully.
     *
     * Runs the full pipeline on [Dispatchers.IO]:
     * rotation correction → MediaStore save → Variant B comparison crop normalization.
     * Sets [lastCaptureResult] only on successful save. Emits a [UiEvent.ShowSnackbar]
     * with the outcome.
     *
     * @param bitmap Raw bitmap from ImageProxy.toBitmap(), may require rotation correction.
     * @param rotationDegrees Clockwise degrees to apply, from ImageInfo.rotationDegrees.
     */
    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        viewModelScope.launch(ioDispatcher) {
            var corrected: Bitmap? = null
            try {
                corrected = rotateBitmap(bitmap, rotationDegrees)
                if (corrected !== bitmap) {
                    bitmap.recycle()
                }

                val saveResult = MediaStoreWriter.save(context.contentResolver, corrected)
                val savedUri = saveResult.getOrNull()
                lastCaptureResult = if (savedUri != null) {
                    CaptureResult(savedUri = savedUri)
                } else {
                    null
                }

                if (savedUri != null) {
                    val referenceUri = _uiState.value.referenceImageUri
                    // Session storage: persists capture + reference as a matched pair in app-internal
                    // storage for later comparison. Only written when the main save succeeded and a
                    // reference image is present. Best-effort — failure here never affects the main save.
                    var sessionRef: SavedSessionRef? = null
                    if (referenceUri != null) {
                        sessionRef = SessionStorage.saveSession(
                            context = context,
                            capturedBitmap = corrected,
                            referenceUri = referenceUri,
                            exifOrientation = _uiState.value.referenceImageMetadata?.exifOrientation,
                            captureMediaStoreUri = savedUri,
                            referencePickerUri = referenceUri
                        )
                        val sessions = scanSavedSessionsSafely()
                        _uiState.update { it.copy(savedSessions = sessions) }
                    }
                    onCaptureSaved(savedUri, sessionRef)
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
                }

                // --- Variant B: comparison crop normalization ---
                var variantBCaptureCropped: Bitmap? = null
                var variantBCaptureNormalized: Bitmap? = null
                var variantBRawReference: Bitmap? = null
                var variantBOrientedReference: Bitmap? = null
                var variantBReferenceCropped: Bitmap? = null
                var variantBReferenceNormalized: Bitmap? = null
                try {
                    variantBCaptureCropped = CenterCropNormalizer.centerCrop(corrected, CenterCropNormalizer.TARGET_RATIO)
                    variantBCaptureNormalized = CenterCropNormalizer.scaleTo(
                        variantBCaptureCropped, CenterCropNormalizer.TARGET_WIDTH, CenterCropNormalizer.TARGET_HEIGHT
                    )

                    val referenceUri = _uiState.value.referenceImageUri
                    if (referenceUri != null) {
                        val exifOrientation = _uiState.value.referenceImageMetadata?.exifOrientation
                        variantBRawReference = context.contentResolver
                            .openInputStream(referenceUri)
                            ?.use { BitmapFactory.decodeStream(it) }
                        if (variantBRawReference != null) {
                            variantBOrientedReference = applyExifOrientation(variantBRawReference, exifOrientation)
                            variantBReferenceCropped = CenterCropNormalizer.centerCrop(
                                variantBOrientedReference, CenterCropNormalizer.TARGET_RATIO
                            )
                            variantBReferenceNormalized = CenterCropNormalizer.scaleTo(
                                variantBReferenceCropped, CenterCropNormalizer.TARGET_WIDTH, CenterCropNormalizer.TARGET_HEIGHT
                            )
                            Log.d(DEBUG_TAG, "variantB capture:   ${variantBCaptureNormalized.width}×${variantBCaptureNormalized.height}")
                            Log.d(DEBUG_TAG, "variantB reference: ${variantBReferenceNormalized.width}×${variantBReferenceNormalized.height}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(DEBUG_TAG, "variantB error: ${e.message}")
                } catch (e: OutOfMemoryError) {
                    Log.d(DEBUG_TAG, "variantB OOM")
                } finally {
                    // corrected is owned by the outer scope — never recycle it here.
                    if (variantBCaptureCropped !== corrected) variantBCaptureCropped?.recycle()
                    variantBCaptureNormalized?.recycle()
                    // Walk the reference chain: each level is only recycled if it is a distinct instance.
                    if (variantBReferenceCropped !== variantBOrientedReference) variantBReferenceCropped?.recycle()
                    if (variantBOrientedReference !== variantBRawReference) variantBOrientedReference?.recycle()
                    variantBRawReference?.recycle()
                    variantBReferenceNormalized?.recycle()
                }
                // --- End Variant B comparison crop normalization ---

            } catch (e: Exception) {
                lastCaptureResult = null
                _uiState.update { it.copy(compareInput = null) }
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
            } catch (e: OutOfMemoryError) {
                lastCaptureResult = null
                _uiState.update { it.copy(compareInput = null) }
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
            } finally {
                if (corrected != null) {
                    corrected.recycle()
                } else {
                    bitmap.recycle()
                }
                finishCapture()
            }
        }
    }

    internal fun onCaptureSaved(savedUri: Uri, sessionRef: SavedSessionRef? = null) {
        val referenceUri = _uiState.value.referenceImageUri
        _uiState.update { current ->
            current.copy(
                captureSuccessGeneration = current.captureSuccessGeneration + 1L,
                captureSuccessHadReference = referenceUri != null,
                compareInput = referenceUri?.let {
                    CompareInput(
                        referenceImageUri = it,
                        captureImageUri = savedUri,
                        sessionId = sessionRef?.sessionId,
                        timestamp = sessionRef?.timestamp
                    )
                }
            )
        }
    }

    /**
     * Called by [CameraScreen] when [ImageCapture] reports a hardware or session error
     * before a frame could be delivered.
     */
    fun onPhotoCaptureError() {
        lastCaptureResult = null
        _uiState.update { it.copy(compareInput = null) }
        finishCapture()
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
        }
    }

    private fun finishCapture() {
        _uiState.update { it.copy(isCaptureInProgress = false) }
    }

    fun refreshSavedSessions() {
        viewModelScope.launch(ioDispatcher) {
            val sessions = scanSavedSessionsSafely()
            _uiState.update { it.copy(savedSessions = sessions) }
        }
    }

    fun deleteSessions(sessionIds: List<String>) {
        viewModelScope.launch(ioDispatcher) {
            val sessionsRoot = File(context.filesDir, "sessions")
            for (sessionId in sessionIds) {
                SessionDeleter.delete(sessionsRoot, sessionId)
            }
            val sessions = scanSavedSessionsSafely()
            _uiState.update { it.copy(savedSessions = sessions) }
        }
    }

    private fun scanSavedSessionsSafely(): List<ScannedSession> {
        return try {
            sessionScanner(context)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Applies EXIF orientation to [source] and returns the correctly oriented bitmap.
     *
     * Returns [source] unchanged if no transform is needed. Never recycles the input.
     */
    private fun applyExifOrientation(source: Bitmap, exifOrientation: Int?): Bitmap {
        val matrix = Matrix()
        val needsTransform = when (exifOrientation) {
            null,
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL -> false
            ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); true }
            ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); true }
            ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); true }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f); true
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f, source.width / 2f, source.height / 2f); true
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f); true
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f); matrix.postScale(-1f, 1f); true
            }
            else -> false
        }
        return if (needsTransform) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }
    }

    private fun getDisplayRecommendation(
        metadata: ReferenceImageMetadata,
        viewportWidth: Int,
        viewportHeight: Int
    ): ReferenceImageDisplayRecommendation {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return ReferenceImageDisplayRecommendation(
                cropLoss = 0f,
                hasStrongMismatch = false,
                startMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
            )
        }
        return ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = metadata.orientedWidth,
            orientedImageHeight = metadata.orientedHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
    }
}
