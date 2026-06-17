// path: app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt
package com.isardomains.sameview.ui.video

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.compare.computeCompareLabels
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.video.VideoExportFormat
import com.isardomains.sameview.video.VideoExportPipeline
import com.isardomains.sameview.video.VideoMode
import com.isardomains.sameview.video.VideoOverlay
import com.isardomains.sameview.video.VideoQuality
import com.isardomains.sameview.video.VideoRenderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

sealed class CreateVideoState {

    data class Configuring(
        val mode: VideoMode = VideoMode.COMPARE_SLIDER,
        val format: VideoExportFormat = VideoExportFormat.ORIGINAL,
        val durationMs: Int = 6000,
        val quality: VideoQuality = VideoQuality.STANDARD_1080P,
        val brandingEnabled: Boolean = true,
        val overlayEnabled: Boolean = false,
        val locationEnabled: Boolean = false
    ) : CreateVideoState()

    data class Rendering(
        val totalFrames: Int,
        val currentFrame: Int = 0
    ) : CreateVideoState()

    data class Preview(val videoUri: Uri) : CreateVideoState()
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed class CreateVideoEvent {
    data class ShowSnackbar(@StringRes val messageResId: Int) : CreateVideoEvent()
}

// ── Metadata snapshot (internal; injectable for tests) ────────────────────────

internal data class OverlayMetadataSnapshot(
    val title: String?,
    val referenceDate: String?,
    val captureTimestampMs: Long,
    val locationCity: String?,
    val locationCountry: String?
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CreateVideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<CreateVideoState>(CreateVideoState.Configuring())
    val state: StateFlow<CreateVideoState> = _state.asStateFlow()

    private val _events = Channel<CreateVideoEvent>(Channel.BUFFERED)
    val events: Flow<CreateVideoEvent> = _events.receiveAsFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // ── Overlay metadata StateFlows ────────────────────────────────────────────

    private val _overlayPreviewText = MutableStateFlow<String?>(null)
    val overlayPreviewText: StateFlow<String?> = _overlayPreviewText.asStateFlow()

    private val _locationPreviewText = MutableStateFlow<String?>(null)
    val locationPreviewText: StateFlow<String?> = _locationPreviewText.asStateFlow()

    private val _isOverlayAvailable = MutableStateFlow(false)
    val isOverlayAvailable: StateFlow<Boolean> = _isOverlayAvailable.asStateFlow()

    private val _isLocationAvailable = MutableStateFlow(false)
    val isLocationAvailable: StateFlow<Boolean> = _isLocationAvailable.asStateFlow()

    // Stored separately for VideoOverlay construction in startExport()
    private var computedTitle: String? = null
    private var computedDateLine: String? = null

    private var exportJob: Job? = null
    private var lastConfiguringState = CreateVideoState.Configuring()

    /**
     * Replaceable pipeline runner for unit tests.
     */
    internal var pipelineRunner: suspend (VideoRenderConfig, File, (Float) -> Unit, suspend () -> Unit) -> Result<Uri> =
        { config, sessionDir, onProgress, onQualityFallback ->
            VideoExportPipeline(context).run(config, sessionDir, onProgress, onQualityFallback)
        }

    /**
     * Replaceable metadata reader for unit tests.
     * The default implementation reads metadata.json synchronously.
     * Tests override this with a simple lambda returning a fixed snapshot.
     * Note: [loadOverlayMetadata] is called from a [viewModelScope] coroutine; callers
     * should ensure the coroutine runs on a suitable dispatcher for file IO.
     */
    internal var overlayMetadataReader: suspend (File) -> OverlayMetadataSnapshot = { sessionDir ->
        readOverlayMetadata(sessionDir)
    }

    /** Injectable dispatcher for the overlay metadata IO read; override in tests. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    init {
        // Seed brandingEnabled from persisted DataStore preference.
        viewModelScope.launch {
            settingsRepository.brandingEnabled.collect { enabled ->
                val current = _state.value
                if (current is CreateVideoState.Configuring) {
                    val updated = current.copy(brandingEnabled = enabled)
                    _state.value = updated
                    lastConfiguringState = updated
                }
            }
        }
        loadOverlayMetadata()
    }

    /**
     * Loads overlay metadata from disk and populates the overlay StateFlows.
     * Separated from init so tests can set [overlayMetadataReader] before calling this.
     * Called automatically from [init] in production; tests call it explicitly after setup.
     */
    internal fun loadOverlayMetadata() {
        viewModelScope.launch {
            val sessionDir = File(context.filesDir, "sessions/$sessionId")
            val snapshot = overlayMetadataReader(sessionDir)
            val title = snapshot.title?.trim()?.takeIf { it.isNotEmpty() }
            val dateLine = computeDateLine(snapshot.referenceDate, snapshot.captureTimestampMs)
            val locationLine = computeLocationLine(snapshot.locationCity, snapshot.locationCountry)

            computedTitle = title
            computedDateLine = dateLine

            val combinedPreview = when {
                title != null && dateLine != null -> "$title · $dateLine"
                dateLine != null -> dateLine
                title != null -> title
                else -> null
            }
            _overlayPreviewText.value = combinedPreview
            _locationPreviewText.value = locationLine
            _isOverlayAvailable.value = combinedPreview != null
            _isLocationAvailable.value = locationLine != null
        }
    }

    // ── Config update helpers ──────────────────────────────────────────────────

    fun updateMode(mode: VideoMode) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(mode = mode)
        _state.value = updated
        lastConfiguringState = updated
    }

    fun updateFormat(format: VideoExportFormat) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(format = format)
        _state.value = updated
        lastConfiguringState = updated
    }

    fun updateDurationMs(durationMs: Int) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(durationMs = durationMs)
        _state.value = updated
        lastConfiguringState = updated
    }

    fun updateQuality(quality: VideoQuality) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(quality = quality)
        _state.value = updated
        lastConfiguringState = updated
    }

    fun updateBrandingEnabled(enabled: Boolean) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(brandingEnabled = enabled)
        _state.value = updated
        lastConfiguringState = updated
        viewModelScope.launch { settingsRepository.setBrandingEnabled(enabled) }
    }

    fun updateOverlayEnabled(enabled: Boolean) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(overlayEnabled = enabled)
        _state.value = updated
        lastConfiguringState = updated
    }

    fun updateLocationEnabled(enabled: Boolean) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(locationEnabled = enabled)
        _state.value = updated
        lastConfiguringState = updated
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun startExport() {
        val configState = _state.value as? CreateVideoState.Configuring ?: return
        lastConfiguringState = configState

        val config = VideoRenderConfig(
            videoMode = configState.mode,
            format = configState.format,
            quality = configState.quality,
            durationMs = configState.durationMs,
            brandingEnabled = configState.brandingEnabled,
            overlay = buildVideoOverlay(configState)
        )
        val sessionDir = File(context.filesDir, "sessions/$sessionId")
        val totalFrames = config.totalFrameCount

        _state.value = CreateVideoState.Rendering(totalFrames = totalFrames)
        _progress.value = 0f

        exportJob = viewModelScope.launch {
            val result = pipelineRunner(
                config,
                sessionDir,
                { p ->
                    _progress.value = p
                    val currentFrame = (p * totalFrames).toInt().coerceIn(0, totalFrames)
                    val rendering = _state.value as? CreateVideoState.Rendering
                    if (rendering != null) {
                        _state.value = rendering.copy(currentFrame = currentFrame)
                    }
                },
                {
                    _events.send(CreateVideoEvent.ShowSnackbar(R.string.create_video_quality_fallback_notice))
                }
            )

            if (!isActive) return@launch

            if (result.isSuccess) {
                _state.value = CreateVideoState.Preview(videoUri = result.getOrThrow())
            } else {
                _state.value = lastConfiguringState
                _events.send(CreateVideoEvent.ShowSnackbar(R.string.create_video_error_render_failed))
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.value = lastConfiguringState
    }

    internal var videoDeleteRunner: suspend (android.net.Uri) -> Boolean = { uri ->
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }
    }

    fun deleteVideo() {
        val preview = _state.value as? CreateVideoState.Preview ?: return
        viewModelScope.launch {
            val deleted = videoDeleteRunner(preview.videoUri)
            if (deleted) {
                _state.value = lastConfiguringState
            } else {
                _events.send(CreateVideoEvent.ShowSnackbar(R.string.create_video_delete_failed))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildVideoOverlay(state: CreateVideoState.Configuring): VideoOverlay? {
        val title = if (state.overlayEnabled) computedTitle else null
        val dateLine = if (state.overlayEnabled) computedDateLine else null
        val locationLine = if (state.locationEnabled) _locationPreviewText.value else null
        if (title == null && dateLine == null && locationLine == null) return null
        return VideoOverlay(title, dateLine, locationLine)
    }

    private fun computeDateLine(referenceDate: String?, captureTimestampMs: Long): String? {
        if (referenceDate == null) return null
        val labels = computeCompareLabels(
            referenceDate = referenceDate,
            captureTimestampMs = captureTimestampMs,
            locale = Locale.getDefault(),
            labelPast = context.getString(R.string.compare_label_past),
            labelPresent = context.getString(R.string.compare_label_present),
            labelReference = context.getString(R.string.compare_label_reference),
            labelCurrent = context.getString(R.string.compare_label_current)
        )
        return "${labels.left} → ${labels.right}"
    }

    private fun computeLocationLine(city: String?, country: String?): String? {
        val c = city?.trim()?.takeIf { it.isNotEmpty() }
        val cn = country?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            c != null && cn != null -> "$c, $cn"
            c != null -> c
            cn != null -> cn
            else -> null
        }
    }

    private fun readOverlayMetadata(sessionDir: File): OverlayMetadataSnapshot {
        val file = File(sessionDir, "metadata.json")
        if (!file.exists()) return OverlayMetadataSnapshot(null, null, 0L, null, null)
        return try {
            val json = JSONObject(file.readText())
            val title = json.optJSONObject("content")?.optString("title", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val referenceDate = json.optJSONObject("reference")?.optString("date", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val captureTimestampMs = json.optJSONObject("capture")?.optLong("timestampMs", 0L) ?: 0L
            val locationCity = json.optJSONObject("location")?.optString("city", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val locationCountry = json.optJSONObject("location")?.optString("country", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            OverlayMetadataSnapshot(title, referenceDate, captureTimestampMs, locationCity, locationCountry)
        } catch (_: Exception) {
            OverlayMetadataSnapshot(null, null, 0L, null, null)
        }
    }
}
