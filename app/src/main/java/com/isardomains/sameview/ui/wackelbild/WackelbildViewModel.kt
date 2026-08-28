// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Which of the two persisted preview images is currently visible on the Wackelbild screen. */
enum class WackelbildImageSide {
    REFERENCE,
    CAPTURE
}

/**
 * Narrow date-only metadata read for the Wackelbild date overlay. Nothing else from
 * `metadata.json` is read — no title, location, branding, GPS, visibility, or favorite state.
 */
internal data class WackelbildDateMetadata(val referenceDate: String?, val captureTimestampMs: Long)

/**
 * Reads only `reference.date` and `capture.timestampMs` from `sessionDir/metadata.json`, using
 * the same `JSONObject`/`optJSONObject` style and defensive-failure behavior already established
 * by `ShareComparisonViewModel.readMetadata`. Missing/corrupt metadata never throws — it simply
 * yields an "unavailable" result.
 */
internal fun readWackelbildDateMetadata(sessionDir: File): WackelbildDateMetadata {
    val file = File(sessionDir, "metadata.json")
    if (!file.exists()) return WackelbildDateMetadata(null, 0L)
    return try {
        val json = JSONObject(file.readText())
        val referenceDate = json.optJSONObject("reference")?.optString("date", null)
            ?.trim()?.takeIf { it.isNotEmpty() }
        val captureTimestampMs = json.optJSONObject("capture")?.optLong("timestampMs", 0L) ?: 0L
        WackelbildDateMetadata(referenceDate, captureTimestampMs)
    } catch (_: Exception) {
        WackelbildDateMetadata(null, 0L)
    }
}

/**
 * ViewModel for [WackelbildScreen].
 *
 * Block 3 scope: resolves both persisted preview images and owns the local tilt/swipe
 * interaction (which image is visible, the tilt sensor, neutral calibration, hysteresis, and
 * swipe/sensor arbitration). No date/HQ/network/order state belongs here — those are later
 * DeinWackelbild implementation blocks.
 *
 * This class intentionally does not depend on [androidx.lifecycle.Lifecycle],
 * [androidx.lifecycle.LifecycleOwner], or any lifecycle-observer type — the screen/composable
 * layer owns lifecycle observation and calls [onScreenActive]/[onScreenInactive]/[onScreenLeft]
 * directly.
 */
@HiltViewModel
class WackelbildViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    /**
     * The session's persisted reference/capture preview images. Read-only — this feature never
     * writes to or mutates any persisted session file. Neither original nor HQ files are used
     * for this local preview.
     */
    val referenceFile: File = File(context.filesDir, "sessions/$sessionId/reference.jpg")
    val captureFile: File = File(context.filesDir, "sessions/$sessionId/capture.jpg")

    private var tiltProvider: TiltProvider = TiltProvider(context)

    @Suppress("DEPRECATION")
    private var displayRotationProvider: () -> Int = {
        (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
            ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

    private var hysteresisStateMachine: TiltHysteresisStateMachine = TiltHysteresisStateMachine()

    private val sessionDir: File = File(context.filesDir, "sessions/$sessionId")

    /** Overridable in unit tests; production default performs the narrow metadata.json read. */
    internal var metadataReader: (File) -> WackelbildDateMetadata = ::readWackelbildDateMetadata

    /** Overridable in unit tests to avoid real disk IO. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * The app/UI locale — never `Locale.getDefault()` — mirroring
     * `ShareComparisonViewModel.currentUiLocale`'s explicit, testable seam.
     */
    internal var currentUiLocale: () -> Locale = { context.resources.configuration.locales.get(0) }

    /** Used in unit tests to inject fakes without a real SensorManager/WindowManager. */
    internal constructor(
        savedStateHandle: SavedStateHandle,
        context: Context,
        tiltProvider: TiltProvider? = null,
        displayRotationProvider: (() -> Int)? = null,
        hysteresisStateMachine: TiltHysteresisStateMachine? = null
    ) : this(savedStateHandle, context) {
        if (tiltProvider != null) this.tiltProvider = tiltProvider
        if (displayRotationProvider != null) this.displayRotationProvider = displayRotationProvider
        if (hysteresisStateMachine != null) this.hysteresisStateMachine = hysteresisStateMachine
    }

    /**
     * True when suitable tilt-sensor hardware exists on this device. A static hardware fact for
     * the lifetime of the ViewModel, but computed via the (possibly test-overridden) provider
     * rather than cached at construction time.
     */
    val isSensorAvailable: Boolean
        get() = tiltProvider.isAvailable()

    private val _visibleImage = MutableStateFlow(WackelbildImageSide.REFERENCE)
    val visibleImage: StateFlow<WackelbildImageSide> = _visibleImage.asStateFlow()

    // --- Date overlay (Block 4) ---
    // Temporary, in-memory only: no DataStore, no SavedStateHandle, no metadata.json write.
    // A fresh screen visit/ViewModel instance always starts with the overlay OFF.

    private val _dateOverlayEnabled = MutableStateFlow(false)
    val dateOverlayEnabled: StateFlow<Boolean> = _dateOverlayEnabled.asStateFlow()

    // True only when a usable Reference date exists. capture.timestampMs never gates this.
    private val _isDateOverlayAvailable = MutableStateFlow(false)
    val isDateOverlayAvailable: StateFlow<Boolean> = _isDateOverlayAvailable.asStateFlow()

    private val _referenceDateBadgeText = MutableStateFlow<String?>(null)
    val referenceDateBadgeText: StateFlow<String?> = _referenceDateBadgeText.asStateFlow()

    // Null when capture.timestampMs is missing/unusable — never an invented value.
    private val _captureDateBadgeText = MutableStateFlow<String?>(null)
    val captureDateBadgeText: StateFlow<String?> = _captureDateBadgeText.asStateFlow()

    init {
        viewModelScope.launch {
            val metadata = try {
                withContext(ioDispatcher) { metadataReader(sessionDir) }
            } catch (_: Exception) {
                WackelbildDateMetadata(null, 0L)
            }
            val locale = currentUiLocale()
            _referenceDateBadgeText.value = DateBadgeFormatter.formatReferenceDate(metadata.referenceDate, locale)
            _captureDateBadgeText.value = DateBadgeFormatter.formatCaptureDate(metadata.captureTimestampMs, locale)
            // Availability depends only on whether the Reference date is usable — a missing
            // Capture date must never disable the toggle.
            _isDateOverlayAvailable.value = _referenceDateBadgeText.value != null
        }
    }

    /**
     * Called by the screen's date toggle. Enabling is a no-op while [isDateOverlayAvailable] is
     * false, so an attempt to enable during unavailability can never leave the overlay ON.
     */
    fun onDateOverlayToggled(enabled: Boolean) {
        if (enabled && !_isDateOverlayAvailable.value) return
        _dateOverlayEnabled.value = enabled
    }

    // Visible for testing — true while the tilt sensor is actively registered.
    internal var isSensorActive = false
        private set

    // Visible for testing — null whenever no neutral posture has been calibrated yet (sensor
    // inactive, or active but no reading received yet).
    @Volatile
    internal var neutralRoll: Float? = null
        private set

    // Visible for testing — true immediately after a manual swipe/accessibility toggle, until
    // the arbitration sequence below clears it.
    internal var swipeOverrideActive = false
        private set

    // Visible for testing — true once the hysteresis machine has reported a return to NEUTRAL
    // since the override began; only then may the next threshold crossing resume sensor control.
    internal var neutralObservedSinceOverride = false
        private set

    /** Called by the screen on ON_RESUME. No-op if already active or no sensor exists. */
    fun onScreenActive() {
        if (isSensorActive || !tiltProvider.isAvailable()) return
        isSensorActive = true
        neutralRoll = null
        tiltProvider.startUpdates(displayRotationProvider) { rawRollDegrees ->
            onRawRollChanged(rawRollDegrees)
        }
    }

    /** Called by the screen on ON_PAUSE. Stops the sensor and clears the calibrated neutral. */
    fun onScreenInactive() {
        if (!isSensorActive) return
        isSensorActive = false
        tiltProvider.stopUpdates()
        neutralRoll = null
    }

    /** Called by the screen when it leaves composition. Fully releases the sensor. */
    fun onScreenLeft() {
        isSensorActive = false
        tiltProvider.stopUpdates()
        neutralRoll = null
    }

    /** Called by the screen when a deliberate horizontal swipe is detected on the preview. */
    fun onSwipeDetected() {
        manualToggle()
    }

    /** Called by the screen's accessibility toggle action on the preview. */
    fun onAccessibilityToggle() {
        manualToggle()
    }

    private fun manualToggle() {
        _visibleImage.value = when (_visibleImage.value) {
            WackelbildImageSide.REFERENCE -> WackelbildImageSide.CAPTURE
            WackelbildImageSide.CAPTURE -> WackelbildImageSide.REFERENCE
        }
        swipeOverrideActive = true
        neutralObservedSinceOverride = false
    }

    private fun onRawRollChanged(rawRollDegrees: Float) {
        val neutral = neutralRoll
        if (neutral == null) {
            // First reading after activation calibrates neutral only — no switch is emitted.
            neutralRoll = rawRollDegrees
            return
        }
        when (val result = hysteresisStateMachine.onDeltaDegrees(rawRollDegrees - neutral)) {
            is TiltHysteresisResult.NoChange -> Unit
            is TiltHysteresisResult.Transitioned -> handleHysteresisTransition(result.newState)
        }
    }

    private fun handleHysteresisTransition(newState: TiltHysteresisState) {
        if (swipeOverrideActive) {
            when {
                newState == TiltHysteresisState.NEUTRAL -> {
                    // SWIPE -> neutral/re-arm observed. Sensor is not yet eligible: a new
                    // threshold crossing (below) is still required before it may act.
                    neutralObservedSinceOverride = true
                }
                neutralObservedSinceOverride -> {
                    // neutral/re-arm observed -> new threshold crossing: sensor control resumes.
                    swipeOverrideActive = false
                    neutralObservedSinceOverride = false
                    applySensorState(newState)
                }
                else -> {
                    // A TOWARD_* transition observed before a NEUTRAL return was observed since
                    // the override began — e.g. the device was already resting near neutral at
                    // swipe time, so the very next threshold crossing arrives without a prior
                    // "return to neutral" transition. Per the required exact rule (SWIPE ->
                    // neutral/re-arm observed -> new threshold crossing -> sensor resumes), this
                    // crossing does not yet count: it is ignored and the override stays active
                    // until an explicit NEUTRAL transition is observed first.
                }
            }
            return
        }
        applySensorState(newState)
    }

    private fun applySensorState(state: TiltHysteresisState) {
        _visibleImage.value = when (state) {
            TiltHysteresisState.TOWARD_CAPTURE -> WackelbildImageSide.CAPTURE
            TiltHysteresisState.TOWARD_REFERENCE -> WackelbildImageSide.REFERENCE
            TiltHysteresisState.NEUTRAL -> return
        }
    }
}
