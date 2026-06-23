// path: app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt
package com.isardomains.sameview.ui.camera

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import java.io.File
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.BuildConfig
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.storage.SessionBackupExporter
import com.isardomains.sameview.ui.settings.LibraryFilter
import com.isardomains.sameview.ui.settings.LibrarySortOrder
import com.isardomains.sameview.ui.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
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
import kotlin.math.max
import kotlin.math.min

/**
 * The two supported target aspect ratios for the camera viewport, capture, and overlay alignment.
 *
 * Maps reference image proportions to one of two well-supported CameraX sensor ratios.
 * Orientation (portrait vs landscape) is determined by the device at render time, not stored here.
 */
private const val UNDO_TIMEOUT_MS = 2500L
private const val CAPTURE_CALLBACK_TIMEOUT_MS = 15_000L

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
    val exifOrientation: Int?,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAltitude: Double? = null,
    val exifDateTimeOriginal: String? = null
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
 * @param gridType The type of grid to draw; [GridType.NONE] means no grid is shown.
 * @param keepScreenOn Whether the display should stay on while [CameraScreen] is visible.
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
    val gridType: GridType = GridType.RULE_OF_THIRDS,
    val keepScreenOn: Boolean = true,
    val interactionMode: InteractionMode = InteractionMode.OVERLAY_ADJUST,
    val activeAspectRatio: TargetAspectRatio = TargetAspectRatio.RATIO_16_9,
    val referenceImageDisplayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
    val referenceImageHasViewportMismatch: Boolean = false,
    val referenceImageMetadata: ReferenceImageMetadata? = null,
    val isCaptureInProgress: Boolean = false,
    val resetOverlayAfterCapture: Boolean = false,
    val autoOpenCompareAfterCapture: Boolean = false,
    val canUndoReferenceRemoval: Boolean = false,
    val referenceRemovalUndoGeneration: Long = 0L,
    val undoExpiresAtMillis: Long = 0L,
    val captureSuccessGeneration: Long = 0L,
    val captureSuccessHadReference: Boolean = false,
    val compareInput: CompareInput? = null,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0,
    val savedSessions: List<ScannedSession> = emptyList(),
    val isOverlayNearlyInvisible: Boolean = false,
    val gpsGuidanceState: GpsGuidanceState = GpsGuidanceState.Hidden,
    val isBackupInProgress: Boolean = false,
    val isDeletionInProgress: Boolean = false
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
    val timestamp: Long? = null,
    val referenceDate: String? = null
)

/**
 * One-time UI events emitted by [CameraViewModel] for consumption by [CameraScreen].
 *
 * Using a [SharedFlow] ensures each event is delivered exactly once to active collectors
 * and is not retained in [CameraUiState], keeping ephemeral feedback separate from
 * persistent UI state.
 */
sealed interface UiEvent {
    /** Display a Snackbar with the given message. [isSuccess] controls visual style.
     *  [durationMs] overrides the default duration: null = SnackbarDuration.Short (~4 s),
     *  non-null = Indefinite display auto-dismissed after [durationMs] milliseconds.
     *  [count] is an optional integer used to format strings with a %d placeholder (e.g. multi-session backup). */
    data class ShowSnackbar(
        @StringRes val messageResId: Int,
        val isSuccess: Boolean = false,
        val durationMs: Long? = null,
        val count: Int? = null
    ) : UiEvent
    /** Notifies the UI that the pending undo snapshot has been invalidated by a new reference load. */
    data object UndoInvalidated : UiEvent
    /** Triggers automatic navigation to CompareScreen after a successful capture when the
     *  auto-open setting is enabled. Contains a pre-built [CompareInput] from the session that
     *  was just saved, so the receiver does not need to read state after the event is emitted. */
    data class NavigateToCompare(val input: CompareInput) : UiEvent
    /** Emitted when a photo picker result has no GPS and recreation guidance is active.
     *  Prompts the user to retry via the system file manager to get unredacted GPS data. */
    data object ShowGpsFallbackDialog : UiEvent
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

internal data class CaptureSessionSnapshot(
    val referenceImageUri: Uri,
    val referenceImageMetadata: ReferenceImageMetadata,
    val overlayScale: Float,
    val overlayOffsetX: Float,
    val overlayOffsetY: Float,
    val referenceImageDisplayMode: ReferenceImageDisplayMode,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val gpsSnapshot: GpsSnapshot? = null,
    val recreationGuidanceEnabled: Boolean = false,
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

internal data class CaptureToken(
    val id: Long
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var referenceImageMetadataReader: (Uri) -> ReferenceImageMetadata? = { uri ->
        ReferenceImageMetadataReader.read(uri, context.contentResolver)
    }

    private var sessionScanner: (Context) -> List<ScannedSession> =
        { ctx -> SessionScanner.scan(ctx) }

    private var sessionTitleUpdater: (File, String, String?) -> Boolean =
        { root, id, title -> SessionStorage.updateTitle(root, id, title) }

    private var sessionDeleter: (File, String) -> Boolean =
        { root, id -> SessionDeleter.delete(root, id) }

    private var sessionFavoriteUpdater: (File, String, Boolean) -> Boolean =
        { root, id, fav -> SessionStorage.updateFavorite(root, id, fav) }

    /** Injectable for unit tests; default resolves global branding from filesDir at call time. */
    internal var globalBrandingRepository: GlobalBrandingRepository =
        GlobalBrandingRepository(File(context.filesDir, "branding"))

    private var sessionBackupExporter: (File, List<String>, Uri, ContentResolver?) -> SessionBackupExporter.BackupResult = { root, ids, uri, cr ->
        if (cr == null) {
            SessionBackupExporter.BackupResult.Failure("ContentResolver not available", null)
        } else {
            val outputStream = cr.openOutputStream(uri)
            if (outputStream == null) {
                SessionBackupExporter.BackupResult.Failure("Cannot open output stream", null)
            } else {
                outputStream.use { SessionBackupExporter.exportSessions(root, ids, it) }
            }
        }
    }

    private var displayModeChangedByUser = false
    private var undoSnapshot: ReferenceUndoSnapshot? = null
    private var undoTimeoutJob: Job? = null
    private val clock: () -> Long = { System.currentTimeMillis() }
    private var referenceImageSelectionJob: Job? = null
    private var referenceImageSelectionRequestId = 0L
    private var captureWatchdogJob: Job? = null
    private val nextCaptureTokenId = AtomicLong(0L)
    private val activeCaptureTokenId = AtomicLong(NO_CAPTURE_TOKEN_ID)

    // Privacy setting — whether to strip EXIF/GPS metadata from stored session originals.
    // Updated from DataStore via the init block; frozen to a local val at capture time.
    // Internal visibility enables direct observation in unit tests.
    @Volatile
    internal var stripOriginalsMetadata = false
        private set

    // GPS state — foreground-only, active only when all four conditions are met
    private var recreationGuidanceEnabled = false
    private var liveDirectionArrowEnabled = false
    private var cameraScreenActive = false
    private var hysteresisPendingColor: ProximityColor? = null
    private var hysteresisPendingCount: Int = 0

    // Visible for testing — true when GPS updates are currently active
    internal var isGpsActive = false
        private set

    // Visible for testing — most recent location from LocationManager, null when GPS is inactive
    @Volatile
    internal var currentLocation: Location? = null
        private set

    private var locationProvider: LocationProvider = LocationProvider(context)

    // Compass sensor state — active only when all six conditions are met (see updateSensorActivation)
    private var compassProvider: CompassProvider = CompassProvider(context)

    @Suppress("DEPRECATION")
    private var displayRotationProvider: () -> Int = {
        (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
            ?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
    }

    // Visible for testing — true when compass sensor is currently active
    internal var isSensorActive = false
        private set

    // Visible for testing — most recent smoothed azimuth from sensor, null when sensor is inactive
    @Volatile
    internal var currentAzimuth: Float? = null
        private set

    // Geographic bearing from GuidanceComputer; null when distance < suppression threshold
    private var currentGeoBearing: Float? = null
    private var smoothedAzimuth: Float? = null

    // LocationManager updates require only ACCESS_FINE_LOCATION. ACCESS_MEDIA_LOCATION is
    // separately required by the Settings permission flow to unredact GPS from MediaStore photos.
    // If ACCESS_MEDIA_LOCATION is missing, referenceHasGps() will return false (GPS was
    // redacted during photo selection), so GPS will not start even if this check passes.
    private var locationPermissionChecker: () -> Boolean = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (BuildConfig.DEBUG) {
                val ageMs = System.currentTimeMillis() - location.time
                Log.d("SameView.GPS", "LocationUpdate: lat=${location.latitude} lon=${location.longitude} acc=${location.accuracy} provider=${location.provider} ageMs=$ageMs")
            }
            currentLocation = location
            recomputeGuidanceState(location)
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onProviderEnabled(provider: String) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onProviderDisabled(provider: String) {}
    }

    // Visible for testing — holds the result of the most recent successfully completed capture.
    // Null until the first successful save, and reset to null on every new capture attempt
    // (success, failure, error, or interrupt). Never reflects a failed or incomplete capture.
    @Volatile
    internal var lastCaptureResult: CaptureResult? = null

    // Visible for testing — holds the capture-time state snapshot frozen at the start of
    // onPhotoCaptured(), before any IO work begins. Reset to null at the start of every new
    // capture attempt and set only when a reference and its metadata are both present.
    // Must not be used for production flow decisions.
    @Volatile
    internal var lastCaptureSnapshot: CaptureSessionSnapshot? = null

    /** Used in unit tests to inject a controlled dispatcher and metadata reader. */
    internal constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
        referenceImageMetadataReader: (Uri) -> ReferenceImageMetadata?,
        settingsRepository: SettingsRepository,
        sessionScanner: (Context) -> List<ScannedSession> = { ctx -> SessionScanner.scan(ctx) },
        sessionTitleUpdater: ((File, String, String?) -> Boolean)? = null,
        sessionDeleter: ((File, String) -> Boolean)? = null,
        locationProvider: LocationProvider? = null,
        locationPermissionChecker: (() -> Boolean)? = null,
        sessionBackupExporter: ((File, List<String>, Uri, ContentResolver?) -> SessionBackupExporter.BackupResult)? = null,
        compassProvider: CompassProvider? = null,
        displayRotationProvider: (() -> Int)? = null,
        sessionFavoriteUpdater: ((File, String, Boolean) -> Boolean)? = null
    ) : this(context, settingsRepository) {
        this.ioDispatcher = ioDispatcher
        this.referenceImageMetadataReader = referenceImageMetadataReader
        this.sessionScanner = sessionScanner
        if (sessionTitleUpdater != null) {
            this.sessionTitleUpdater = sessionTitleUpdater
        }
        if (sessionDeleter != null) {
            this.sessionDeleter = sessionDeleter
        }
        if (locationProvider != null) {
            this.locationProvider = locationProvider
        }
        if (locationPermissionChecker != null) {
            this.locationPermissionChecker = locationPermissionChecker
        }
        if (sessionBackupExporter != null) {
            this.sessionBackupExporter = sessionBackupExporter
        }
        if (compassProvider != null) {
            this.compassProvider = compassProvider
        }
        if (displayRotationProvider != null) {
            this.displayRotationProvider = displayRotationProvider
        }
        if (sessionFavoriteUpdater != null) {
            this.sessionFavoriteUpdater = sessionFavoriteUpdater
        }
    }

    private val _uiState = MutableStateFlow(CameraUiState())

    init {
        viewModelScope.launch {
            settingsRepository.gridType.collect { type ->
                _uiState.update { it.copy(gridType = type) }
            }
        }
        viewModelScope.launch {
            settingsRepository.keepScreenOn.collect { enabled ->
                _uiState.update { it.copy(keepScreenOn = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.resetOverlayAfterCapture.collect { enabled ->
                _uiState.update { it.copy(resetOverlayAfterCapture = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoOpenCompareAfterCapture.collect { enabled ->
                _uiState.update { it.copy(autoOpenCompareAfterCapture = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.recreationGuidance.collect { enabled ->
                recreationGuidanceEnabled = enabled
                updateGpsActivation()
                updateSensorActivation()
            }
        }
        viewModelScope.launch {
            settingsRepository.liveDirectionArrow.collect { enabled ->
                liveDirectionArrowEnabled = enabled
                updateSensorActivation()
            }
        }
        viewModelScope.launch {
            settingsRepository.stripOriginalsMetadata.collect { enabled ->
                stripOriginalsMetadata = enabled
            }
        }
    }

    /** Observed by [CameraScreen] to render the current UI state. */
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()

    /** One-time events collected by [CameraScreen] to trigger Snackbar messages. */
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    companion object {
        /** Minimum allowed scale for the reference image overlay. */
        const val MIN_SCALE = 0.5f
        /** Maximum allowed scale for the reference image overlay.
         *  Must be ≥ (16/9)² ≈ 3.16, the scale required to fill a 16:9 landscape viewport
         *  with a 9:16 portrait reference (or vice versa) in SHOW_FULL_IMAGE mode. */
        const val MAX_SCALE = 4.0f

        private const val LOG_TAG = "SameView"
        private const val NO_CAPTURE_TOKEN_ID = 0L

        // Low-pass filter alpha for azimuth smoothing: fraction of new value to apply each update.
        // Value 0.15 retains 85% of previous reading, resulting in ~1s convergence at ~10 Hz.
        private const val SENSOR_AZIMUTH_SMOOTHING_ALPHA = 0.15f
    }

    override fun onCleared() {
        super.onCleared()
        if (isSensorActive) {
            compassProvider.stopUpdates()
        }
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
        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Undo expired") }
        undoSnapshot = null
        undoTimeoutJob = null
        _uiState.update { it.copy(canUndoReferenceRemoval = false, undoExpiresAtMillis = 0L) }
    }

    fun onReferenceImageSelected(uri: Uri?) = loadReferenceImage(uri, emitGpsFallbackDialog = true)

    /**
     * Called when the user selects a reference image via the system file manager (SAF).
     *
     * Identical to [onReferenceImageSelected] but never emits [UiEvent.ShowGpsFallbackDialog],
     * preventing a dialog loop when the SAF result itself has no GPS data.
     */
    fun onReferenceImageSelectedViaSaf(uri: Uri?) = loadReferenceImage(uri, emitGpsFallbackDialog = false)

    private fun loadReferenceImage(uri: Uri?, emitGpsFallbackDialog: Boolean) {
        if (uri == null) return
        undoTimeoutJob?.cancel()
        undoTimeoutJob = null
        referenceImageSelectionJob?.cancel()
        val requestId = ++referenceImageSelectionRequestId
        referenceImageSelectionJob = viewModelScope.launch {
            val metadata = withContext(ioDispatcher) {
                referenceImageMetadataReader(uri)
            } ?: run {
                if (requestId == referenceImageSelectionRequestId) {
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.reference_image_load_failed))
                }
                return@launch
            }
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
                val updated = current.copy(
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
                updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
            }
            updateGpsActivation()
            updateSensorActivation()
            if (emitGpsFallbackDialog && recreationGuidanceEnabled && !referenceHasGps()) {
                _uiEvent.emit(UiEvent.ShowGpsFallbackDialog)
            }
            if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Overlay loaded") }
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
                isOverlayNearlyInvisible = false,
                canUndoReferenceRemoval = if (hasReference) true else it.canUndoReferenceRemoval,
                referenceRemovalUndoGeneration = if (hasReference) {
                    it.referenceRemovalUndoGeneration + 1L
                } else {
                    it.referenceRemovalUndoGeneration
                },
                undoExpiresAtMillis = if (hasReference) expiresAt else it.undoExpiresAtMillis
            )
        }
        updateGpsActivation()
        updateSensorActivation()
        if (hasReference) {
            if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Overlay removed") }
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
            val updated = current.copy(
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
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
        }
        updateGpsActivation()
        updateSensorActivation()
        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Undo triggered") }
    }

    fun onReferenceViewportChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _uiState.update { current ->
            val metadata = current.referenceImageMetadata
            val recommendation = if (metadata != null) {
                getDisplayRecommendation(metadata, width, height)
            } else null
            val updated = current.copy(
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
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
        }
    }

    fun onReferenceImageDisplayModeChanged(displayMode: ReferenceImageDisplayMode) {
        displayModeChangedByUser = true
        _uiState.update {
            val updated = it.copy(referenceImageDisplayMode = displayMode)
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
        }
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
            val updated = it.copy(
                overlayOffsetX = (it.overlayOffsetX + dx).coerceIn(-0.5f, 0.5f),
                overlayOffsetY = (it.overlayOffsetY + dy).coerceIn(-0.5f, 0.5f)
            )
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
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
            val updated = it.copy(overlayScale = (it.overlayScale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE))
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
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
            val updated = current.copy(
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                overlayScale = 1f,
                referenceImageDisplayMode = recommendation?.startMode
                    ?: current.referenceImageDisplayMode,
                referenceImageHasViewportMismatch = recommendation?.hasStrongMismatch
                    ?: current.referenceImageHasViewportMismatch
            )
            updated.copy(isOverlayNearlyInvisible = computeIsOverlayNearlyInvisible(updated))
        }
    }

    /**
     * Atomically acquires the capture lock.
     *
     * @return a token if the lock was acquired, null if a capture is already in progress.
     */
    internal fun tryStartCapture(): CaptureToken? {
        while (true) {
            val current = _uiState.value
            if (current.isCaptureInProgress) return null
            if (_uiState.compareAndSet(
                    current,
                    current.copy(isCaptureInProgress = true)
                )
            ) {
                val token = CaptureToken(nextCaptureTokenId.incrementAndGet())
                activeCaptureTokenId.set(token.id)
                startCaptureWatchdog(token)
                if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Capture started") }
                return token
            }
        }
    }

    private fun startCaptureWatchdog(token: CaptureToken) {
        captureWatchdogJob?.cancel()
        captureWatchdogJob = viewModelScope.launch {
            delay(CAPTURE_CALLBACK_TIMEOUT_MS)
            failCapture(token, cancelWatchdog = false)
        }
    }

    private fun cancelCaptureWatchdog(token: CaptureToken) {
        if (activeCaptureTokenId.get() == token.id) {
            captureWatchdogJob?.cancel()
            captureWatchdogJob = null
        }
    }

    /**
     * Releases an in-flight capture lock when the UI/camera composition is torn down
     * before CameraX can reliably deliver its success or error callback.
     */
    fun onCaptureInterrupted() {
        captureWatchdogJob?.cancel()
        captureWatchdogJob = null
        activeCaptureTokenId.set(NO_CAPTURE_TOKEN_ID)
        lastCaptureResult = null
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
    internal fun onPhotoCaptured(token: CaptureToken, bitmap: Bitmap, rotationDegrees: Int) {
        if (!isActiveCaptureToken(token)) {
            bitmap.recycle()
            return
        }
        cancelCaptureWatchdog(token)
        val currentState = _uiState.value
        // Freeze GPS at the instant the shutter fires, before any IO work begins.
        // null when recreation guidance is OFF or no location fix is available.
        val gpsSnapshot: GpsSnapshot? = if (recreationGuidanceEnabled) {
            currentLocation?.let { GpsSnapshot.from(it) }
        } else null
        // Freeze privacy setting at capture time so a Settings change mid-save cannot
        // produce a session that is half byte-copy and half stripped.
        val stripMetadataForSession = stripOriginalsMetadata
        val snapshot: CaptureSessionSnapshot? = if (
            currentState.referenceImageUri != null &&
            currentState.referenceImageMetadata != null
        ) {
            CaptureSessionSnapshot(
                referenceImageUri = currentState.referenceImageUri,
                referenceImageMetadata = currentState.referenceImageMetadata,
                overlayScale = currentState.overlayScale,
                overlayOffsetX = currentState.overlayOffsetX,
                overlayOffsetY = currentState.overlayOffsetY,
                referenceImageDisplayMode = currentState.referenceImageDisplayMode,
                viewportWidth = currentState.viewportWidth,
                viewportHeight = currentState.viewportHeight,
                gpsSnapshot = gpsSnapshot,
                recreationGuidanceEnabled = recreationGuidanceEnabled,
            )
        } else null
        lastCaptureSnapshot = null
        if (snapshot != null) lastCaptureSnapshot = snapshot
        viewModelScope.launch(ioDispatcher) {
            var corrected: Bitmap? = null
            try {
                corrected = rotateBitmap(bitmap, rotationDegrees)
                if (corrected !== bitmap) {
                    bitmap.recycle()
                }

                val saveResult = MediaStoreWriter.save(context.contentResolver, corrected, gpsSnapshot)
                val savedUri = saveResult.getOrNull()
                lastCaptureResult = if (savedUri != null) {
                    CaptureResult(savedUri = savedUri)
                } else {
                    null
                }

                if (savedUri != null) {
                    // Session storage: persists capture + reference as a matched pair in app-internal
                    // storage for later comparison. Only written when the main save succeeded and a
                    // reference image is present. Best-effort — failure here never affects the main save.
                    var sessionRef: SavedSessionRef? = null
                    if (snapshot != null) {
                        sessionRef = SessionStorage.saveSession(
                            context = context,
                            capturedBitmap = corrected,
                            snapshot = snapshot,
                            captureMediaStoreUri = savedUri,
                            stripMetadata = stripMetadataForSession,
                            globalBrandingRepository = globalBrandingRepository
                        )
                        val sessions = scanSavedSessionsSafely()
                        _uiState.update { it.copy(savedSessions = sessions) }
                    }
                    onCaptureSaved(savedUri, sessionRef, hadSnapshotButNoSession = snapshot != null && sessionRef == null)
                } else {
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
                }

            } catch (e: Exception) {
                lastCaptureResult = null
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
            } catch (e: OutOfMemoryError) {
                lastCaptureResult = null
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
            } finally {
                if (corrected != null) {
                    corrected.recycle()
                } else {
                    bitmap.recycle()
                }
                finishCapture(token)
            }
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        val tokenId = activeCaptureTokenId.get()
        if (tokenId == NO_CAPTURE_TOKEN_ID) {
            bitmap.recycle()
            return
        }
        onPhotoCaptured(CaptureToken(tokenId), bitmap, rotationDegrees)
    }

    internal fun onCaptureSaved(savedUri: Uri, sessionRef: SavedSessionRef? = null, hadSnapshotButNoSession: Boolean = false) {
        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "Capture completed") }
        val newCompareInput: CompareInput? = sessionRef?.let {
            CompareInput(
                referenceImageUri = it.referenceFileUri,
                captureImageUri = it.captureFileUri,
                sessionId = it.sessionId,
                timestamp = it.timestamp,
                referenceDate = it.referenceDate
            )
        }
        val autoOpen = _uiState.value.autoOpenCompareAfterCapture
        val doReset = _uiState.value.resetOverlayAfterCapture
        if (doReset) displayModeChangedByUser = false
        _uiState.update { current ->
            current.copy(
                captureSuccessGeneration = current.captureSuccessGeneration + 1L,
                captureSuccessHadReference = sessionRef != null || hadSnapshotButNoSession,
                compareInput = newCompareInput,
                referenceImageUri = if (doReset) null else current.referenceImageUri,
                referenceImageMetadata = if (doReset) null else current.referenceImageMetadata,
                referenceImageHasViewportMismatch = if (doReset) false else current.referenceImageHasViewportMismatch,
                referenceImageDisplayMode = if (doReset) ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW else current.referenceImageDisplayMode,
                overlayOffsetX = if (doReset) 0f else current.overlayOffsetX,
                overlayOffsetY = if (doReset) 0f else current.overlayOffsetY,
                overlayScale = if (doReset) 1f else current.overlayScale,
                isOverlayNearlyInvisible = if (doReset) false else current.isOverlayNearlyInvisible,
            )
        }
        if (autoOpen && newCompareInput != null) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.NavigateToCompare(newCompareInput))
            }
        }
        if (hadSnapshotButNoSession) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_saved_compare_failed))
            }
        }
    }

    /**
     * Called by [CameraScreen] when [ImageCapture] reports a hardware or session error
     * before a frame could be delivered.
     */
    internal fun onPhotoCaptureError(token: CaptureToken) {
        failCapture(token, cancelWatchdog = true)
    }

    private fun failCapture(token: CaptureToken, cancelWatchdog: Boolean) {
        if (!activeCaptureTokenId.compareAndSet(token.id, NO_CAPTURE_TOKEN_ID)) return
        if (cancelWatchdog) {
            captureWatchdogJob?.cancel()
            captureWatchdogJob = null
        }
        lastCaptureResult = null
        finishCapture()
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar(R.string.capture_failed))
        }
    }

    fun onPhotoCaptureError() {
        val tokenId = activeCaptureTokenId.get()
        if (tokenId == NO_CAPTURE_TOKEN_ID) return
        onPhotoCaptureError(CaptureToken(tokenId))
    }

    fun onCameraStartError() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSnackbar(R.string.camera_start_failed))
        }
    }

    // Called by CameraScreen when the composable enters the active foreground state (ON_RESUME).
    fun onCameraScreenActive() {
        cameraScreenActive = true
        updateGpsActivation()
        updateSensorActivation()
    }

    // Called by CameraScreen on ON_PAUSE and on dispose to stop GPS when screen is not visible.
    fun onCameraScreenInactive() {
        cameraScreenActive = false
        updateGpsActivation()
        updateSensorActivation()
    }

    private fun referenceHasGps(): Boolean =
        _uiState.value.referenceImageMetadata?.gpsLatitude != null

    private fun updateGpsActivation() {
        val guidance = recreationGuidanceEnabled
        val location = locationPermissionChecker()
        val hasGps = referenceHasGps()
        val screen = cameraScreenActive
        val shouldBeActive = guidance && location && hasGps && screen
        if (BuildConfig.DEBUG) {
            Log.d("SameView.GPS", "updateGpsActivation: guidance=$guidance location=$location refGps=$hasGps screen=$screen -> shouldBeActive=$shouldBeActive isGpsActive=$isGpsActive")
        }
        if (shouldBeActive && !isGpsActive) {
            startGps()
        } else if (!shouldBeActive && isGpsActive) {
            stopGps()
        }
    }

    private fun startGps() {
        isGpsActive = true
        hysteresisPendingColor = null
        hysteresisPendingCount = 0
        _uiState.update { it.copy(gpsGuidanceState = GpsGuidanceState.Neutral) }
        val lastKnown = try { locationProvider.getLastKnown() } catch (_: Exception) { null }
        if (BuildConfig.DEBUG) {
            if (lastKnown != null) {
                val ageMs = System.currentTimeMillis() - lastKnown.time
                Log.d("SameView.GPS", "LastKnown: lat=${lastKnown.latitude} lon=${lastKnown.longitude} acc=${lastKnown.accuracy} provider=${lastKnown.provider} ageMs=$ageMs")
            } else {
                Log.d("SameView.GPS", "LastKnown: null")
            }
        }
        if (lastKnown != null) {
            currentLocation = lastKnown
            recomputeGuidanceState(lastKnown)
        }
        locationProvider.startUpdates(locationListener)
    }

    private fun stopGps() {
        isGpsActive = false
        locationProvider.stopUpdates(locationListener)
        currentLocation = null
        hysteresisPendingColor = null
        hysteresisPendingCount = 0
        _uiState.update { it.copy(gpsGuidanceState = GpsGuidanceState.Hidden) }
    }

    private fun updateSensorActivation() {
        val shouldBeActive = recreationGuidanceEnabled
            && liveDirectionArrowEnabled
            && locationPermissionChecker()
            && referenceHasGps()
            && cameraScreenActive
            && compassProvider.isAvailable()
        if (shouldBeActive && !isSensorActive) {
            startSensor()
        } else if (!shouldBeActive && isSensorActive) {
            stopSensor()
        }
    }

    private fun startSensor() {
        isSensorActive = true
        smoothedAzimuth = null
        compassProvider.startUpdates(displayRotationProvider) { rawAzimuth ->
            val smoothed = smoothAzimuth(rawAzimuth)
            currentAzimuth = smoothed
            updateBearingInState(smoothed)
        }
    }

    private fun stopSensor() {
        isSensorActive = false
        compassProvider.stopUpdates()
        currentAzimuth = null
        smoothedAzimuth = null
        currentGeoBearing = null
        _uiState.update { state ->
            val gps = state.gpsGuidanceState
            if (gps is GpsGuidanceState.Informative) {
                state.copy(gpsGuidanceState = gps.copy(bearingDegrees = null))
            } else state
        }
    }

    /**
     * Applies a low-pass filter with shortest-path interpolation to avoid the 359°↔1° boundary
     * artifact that a naive LERP would produce.
     */
    private fun smoothAzimuth(raw: Float): Float {
        val prev = smoothedAzimuth
        if (prev == null) {
            smoothedAzimuth = raw
            return raw
        }
        val delta = ((raw - prev + 540f) % 360f) - 180f
        val smoothed = (prev + delta * SENSOR_AZIMUTH_SMOOTHING_ALPHA + 360f) % 360f
        smoothedAzimuth = smoothed
        return smoothed
    }

    private fun updateBearingInState(azimuth: Float) {
        val geoBearing = currentGeoBearing ?: return
        if (!liveDirectionArrowEnabled) return
        val displayBearing = DirectionArrowCalculator.computeDisplayBearing(geoBearing, azimuth)
        _uiState.update { state ->
            val gps = state.gpsGuidanceState
            if (gps is GpsGuidanceState.Informative) {
                state.copy(gpsGuidanceState = gps.copy(bearingDegrees = displayBearing))
            } else state
        }
    }

    private fun recomputeGuidanceState(location: Location) {
        val metadata = _uiState.value.referenceImageMetadata ?: return
        val refLat = metadata.gpsLatitude ?: return
        val refLon = metadata.gpsLongitude ?: return
        if (BuildConfig.DEBUG) {
            Log.d("SameView.GPS", "Compute input: currentLat=${location.latitude} currentLon=${location.longitude} refLat=$refLat refLon=$refLon acc=${location.accuracy}")
        }
        val result = GuidanceComputer.computeGuidanceState(
            currentLat = location.latitude,
            currentLon = location.longitude,
            accuracyMeters = location.accuracy,
            refLat = refLat,
            refLon = refLon,
            previousState = _uiState.value.gpsGuidanceState,
            pendingColor = hysteresisPendingColor,
            pendingCount = hysteresisPendingCount
        )
        if (BuildConfig.DEBUG) {
            val s = result.state
            if (s is GpsGuidanceState.Informative) {
                Log.d("SameView.GPS", "Compute result: Informative distM=${s.distanceMeters} bearing=${s.bearingDegrees} color=${s.proximityColor}")
            } else {
                Log.d("SameView.GPS", "Compute result: ${s::class.simpleName}")
            }
        }
        hysteresisPendingColor = result.pendingColor
        hysteresisPendingCount = result.pendingCount

        // Extract geographic bearing. Note: prevState.bearingDegrees may carry a device-relative
        // value from a prior update, so the bearing threshold in GuidanceComputer is effectively
        // inactive. This is an accepted trade-off for keeping GuidanceComputer unchanged.
        val geoBearing = (result.state as? GpsGuidanceState.Informative)?.bearingDegrees
        currentGeoBearing = geoBearing

        val displayBearing = if (liveDirectionArrowEnabled && geoBearing != null) {
            currentAzimuth?.let { azimuth ->
                DirectionArrowCalculator.computeDisplayBearing(geoBearing, azimuth)
            }
        } else null

        val displayState = when (val s = result.state) {
            is GpsGuidanceState.Informative -> s.copy(bearingDegrees = displayBearing)
            else -> s
        }
        _uiState.update { it.copy(gpsGuidanceState = displayState) }
    }

    private fun finishCapture() {
        _uiState.update { it.copy(isCaptureInProgress = false) }
    }

    private fun finishCapture(token: CaptureToken) {
        if (activeCaptureTokenId.compareAndSet(token.id, NO_CAPTURE_TOKEN_ID)) {
            finishCapture()
        }
    }

    private fun isActiveCaptureToken(token: CaptureToken): Boolean {
        return token.id != NO_CAPTURE_TOKEN_ID && activeCaptureTokenId.get() == token.id
    }

    fun updateSessionTitle(sessionId: String, title: String?) {
        viewModelScope.launch {
            val success = try {
                withContext(ioDispatcher) {
                    val sessionsRoot = File(context.filesDir, "sessions")
                    sessionTitleUpdater(sessionsRoot, sessionId, title?.trim()?.ifEmpty { null })
                }
            } catch (e: Exception) {
                false
            }
            if (!success) {
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.compare_screen_title_save_failed))
            }
            refreshSavedSessions()
        }
    }

    /**
     * Toggles [isFavorite] for the session identified by [sessionId].
     *
     * Write-First strategy: writes metadata.json first; only on success is the in-memory
     * [CameraUiState.savedSessions] entry updated (targeted, single entry). On failure,
     * [savedSessions] is not modified and a Snackbar event is emitted. No full rescan.
     */
    fun toggleFavorite(sessionId: String) {
        val currentSession = _uiState.value.savedSessions.find { it.sessionId == sessionId }
            ?: return
        val newValue = !currentSession.isFavorite
        viewModelScope.launch {
            val sessionsRoot = File(context.filesDir, "sessions")
            val success = try {
                withContext(ioDispatcher) {
                    sessionFavoriteUpdater(sessionsRoot, sessionId, newValue)
                }
            } catch (e: Exception) {
                false
            }
            if (success) {
                _uiState.update { current ->
                    current.copy(
                        savedSessions = current.savedSessions.map { session ->
                            if (session.sessionId == sessionId) session.copy(isFavorite = newValue)
                            else session
                        }
                    )
                }
            } else {
                _uiEvent.emit(UiEvent.ShowSnackbar(R.string.compare_session_favorite_update_failed))
            }
        }
    }

    /** Pass-through to [SettingsRepository.libraryFilter]; does not update [CameraUiState]. */
    val libraryFilter: Flow<LibraryFilter> = settingsRepository.libraryFilter

    /** Pass-through to [SettingsRepository.librarySortOrder]; does not update [CameraUiState]. */
    val librarySortOrder: Flow<LibrarySortOrder> = settingsRepository.librarySortOrder

    fun setLibraryFilter(filter: LibraryFilter) {
        viewModelScope.launch { settingsRepository.setLibraryFilter(filter) }
    }

    fun setLibrarySortOrder(order: LibrarySortOrder) {
        viewModelScope.launch { settingsRepository.setLibrarySortOrder(order) }
    }

    fun refreshSavedSessions() {
        viewModelScope.launch(ioDispatcher) {
            val sessions = scanSavedSessionsSafely()
            _uiState.update { it.copy(savedSessions = sessions) }
        }
    }

    fun onCompareDisabledTapped(referenceUri: Uri?) {
        viewModelScope.launch {
            val messageResId = if (referenceUri == null) {
                R.string.compare_disabled_no_reference
            } else {
                R.string.compare_disabled_no_capture
            }
            _uiEvent.emit(UiEvent.ShowSnackbar(messageResId, durationMs = 2000L))
        }
    }

    fun deleteSessions(sessionIds: List<String>) {
        if (_uiState.value.isBackupInProgress) return
        _uiState.update { it.copy(isDeletionInProgress = true) }
        viewModelScope.launch(ioDispatcher) {
            try {
                val sessionsRoot = File(context.filesDir, "sessions")
                val failedIds = mutableSetOf<String>()
                for (sessionId in sessionIds) {
                    if (!sessionDeleter(sessionsRoot, sessionId)) {
                        failedIds.add(sessionId)
                    }
                }
                val succeededIds = sessionIds.toSet() - failedIds
                val sessions = scanSavedSessionsSafely()
                _uiState.update { current ->
                    val activeSessionDeleted = current.compareInput?.sessionId
                        ?.let { it in succeededIds } ?: false
                    current.copy(
                        savedSessions = sessions,
                        compareInput = if (activeSessionDeleted) null else current.compareInput
                    )
                }
                if (failedIds.isNotEmpty()) {
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.delete_failed))
                }
            } finally {
                _uiState.update { it.copy(isDeletionInProgress = false) }
            }
        }
    }

    suspend fun deleteSession(sessionId: String): Boolean {
        if (_uiState.value.isBackupInProgress) return false
        _uiState.update { it.copy(isDeletionInProgress = true) }
        return try {
            withContext(ioDispatcher) {
                val sessionsRoot = File(context.filesDir, "sessions")
                val succeeded = sessionDeleter(sessionsRoot, sessionId)
                val sessions = scanSavedSessionsSafely()
                _uiState.update { current ->
                    val activeSessionDeleted = succeeded && current.compareInput?.sessionId == sessionId
                    current.copy(
                        savedSessions = sessions,
                        compareInput = if (activeSessionDeleted) null else current.compareInput
                    )
                }
                if (!succeeded) {
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.delete_failed))
                }
                succeeded
            }
        } finally {
            _uiState.update { it.copy(isDeletionInProgress = false) }
        }
    }

    fun backupSessions(sessionIds: List<String>, destinationUri: Uri) {
        val current = _uiState.value
        if (current.isBackupInProgress || current.isDeletionInProgress) return
        _uiState.update { it.copy(isBackupInProgress = true) }
        viewModelScope.launch(ioDispatcher) {
            val sessionsRoot = File(context.filesDir, "sessions")
            val result = try {
                sessionBackupExporter(sessionsRoot, sessionIds, destinationUri, context.contentResolver)
            } catch (e: Exception) {
                SessionBackupExporter.BackupResult.Failure("Unexpected error during backup", e)
            }
            if (result is SessionBackupExporter.BackupResult.Failure) {
                try { context.contentResolver.delete(destinationUri, null, null) } catch (_: Exception) {}
            }
            _uiState.update { it.copy(isBackupInProgress = false) }
            when (result) {
                is SessionBackupExporter.BackupResult.Success -> {
                    val msgRes = if (sessionIds.size == 1) R.string.session_backup_success_single
                                 else R.string.session_backup_success_multi
                    _uiEvent.emit(UiEvent.ShowSnackbar(
                        messageResId = msgRes,
                        isSuccess = true,
                        count = if (sessionIds.size > 1) sessionIds.size else null
                    ))
                }
                is SessionBackupExporter.BackupResult.Failure ->
                    _uiEvent.emit(UiEvent.ShowSnackbar(R.string.session_backup_error, isSuccess = false))
            }
        }
    }

    fun backupSingleSession(sessionId: String, destinationUri: Uri) =
        backupSessions(listOf(sessionId), destinationUri)

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

    private fun computeIsOverlayNearlyInvisible(state: CameraUiState): Boolean {
        if (state.referenceImageUri == null) return false
        if (state.viewportWidth <= 0 || state.viewportHeight <= 0) return false
        val metadata = state.referenceImageMetadata ?: return false

        val iW = metadata.orientedWidth.toFloat()
        val iH = metadata.orientedHeight.toFloat()
        val vW = state.viewportWidth.toFloat()
        val vH = state.viewportHeight.toFloat()

        val displayedWidth: Float
        val displayedHeight: Float
        val actualTX: Float
        val actualTY: Float

        if (state.referenceImageDisplayMode == ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW) {
            val fillScale = max(vW / iW, vH / iH)
            displayedWidth = iW * fillScale
            displayedHeight = iH * fillScale
            val scaledW = displayedWidth * state.overlayScale
            val scaledH = displayedHeight * state.overlayScale
            val maxTX = max(0f, (scaledW - vW) / 2f)
            val maxTY = max(0f, (scaledH - vH) / 2f)
            actualTX = (state.overlayOffsetX * vW).coerceIn(-maxTX, maxTX)
            actualTY = (state.overlayOffsetY * vH).coerceIn(-maxTY, maxTY)
        } else {
            val fitScale = min(vW / iW, vH / iH)
            displayedWidth = iW * fitScale
            displayedHeight = iH * fitScale
            actualTX = state.overlayOffsetX * vW
            actualTY = state.overlayOffsetY * vH
        }

        val scaledWidth = displayedWidth * state.overlayScale
        val scaledHeight = displayedHeight * state.overlayScale
        val centerX = vW / 2f + actualTX
        val centerY = vH / 2f + actualTY
        val visLeft = max(0f, centerX - scaledWidth / 2f)
        val visRight = min(vW, centerX + scaledWidth / 2f)
        val visTop = max(0f, centerY - scaledHeight / 2f)
        val visBottom = min(vH, centerY + scaledHeight / 2f)
        val visWidth = max(0f, visRight - visLeft)
        val visHeight = max(0f, visBottom - visTop)
        val coverage = (visWidth * visHeight) / (vW * vH)
        return coverage < 0.20f
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
