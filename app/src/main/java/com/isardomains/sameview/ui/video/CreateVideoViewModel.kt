// path: app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt
package com.isardomains.sameview.ui.video

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.video.VideoExportFormat
import com.isardomains.sameview.video.VideoExportPipeline
import com.isardomains.sameview.video.VideoMode
import com.isardomains.sameview.video.VideoQuality
import com.isardomains.sameview.video.VideoRenderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

sealed class CreateVideoState {

    /** User is configuring mode, format, duration, quality, and branding. */
    data class Configuring(
        val mode: VideoMode = VideoMode.COMPARE_SLIDER,
        val format: VideoExportFormat = VideoExportFormat.ORIGINAL,
        val durationMs: Int = 6000,
        val quality: VideoQuality = VideoQuality.STANDARD_1080P,
        val brandingEnabled: Boolean = true
    ) : CreateVideoState()

    /**
     * Export is in progress.
     * [currentFrame] is updated on each rendered frame; [totalFrames] is fixed for the export.
     */
    data class Rendering(
        val totalFrames: Int,
        val currentFrame: Int = 0
    ) : CreateVideoState()

    /** Export completed. [videoUri] is the MediaStore URI of the created MP4. */
    data class Preview(val videoUri: Uri) : CreateVideoState()
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed class CreateVideoEvent {
    data class ShowSnackbar(@StringRes val messageResId: Int) : CreateVideoEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages the state machine for [CreateVideoScreen].
 *
 * State flow: Configuring → Rendering → Preview.
 * Error during rendering returns to Configuring and emits a [CreateVideoEvent.ShowSnackbar].
 *
 * [sessionId] is read from [SavedStateHandle] via the Navigation Compose nav argument.
 * [pipelineRunner] is replaceable for unit tests; defaults to [VideoExportPipeline.run].
 */
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

    private var exportJob: Job? = null

    /** Last Configuring state; restored on export failure or cancel. */
    private var lastConfiguringState = CreateVideoState.Configuring()

    /**
     * Replaceable pipeline runner for unit tests.
     * Production code delegates to [VideoExportPipeline.run].
     * The fourth parameter [onQualityFallback] is invoked when the export silently falls back
     * to Standard 1080p because the device cannot encode the requested High Quality resolution.
     */
    internal var pipelineRunner: suspend (VideoRenderConfig, File, (Float) -> Unit, suspend () -> Unit) -> Result<Uri> =
        { config, sessionDir, onProgress, onQualityFallback ->
            VideoExportPipeline(context.contentResolver).run(config, sessionDir, onProgress, onQualityFallback)
        }

    init {
        // Seed initial brandingEnabled from persisted DataStore preference.
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

    /** Updates branding toggle and persists the choice to DataStore. */
    fun updateBrandingEnabled(enabled: Boolean) {
        val current = _state.value as? CreateVideoState.Configuring ?: return
        val updated = current.copy(brandingEnabled = enabled)
        _state.value = updated
        lastConfiguringState = updated
        viewModelScope.launch { settingsRepository.setBrandingEnabled(enabled) }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Starts the video export.
     * Transitions state to [CreateVideoState.Rendering], then to [CreateVideoState.Preview]
     * on success, or back to [CreateVideoState.Configuring] with an error event on failure.
     */
    fun startExport() {
        val configState = _state.value as? CreateVideoState.Configuring ?: return
        lastConfiguringState = configState

        val config = VideoRenderConfig(
            videoMode = configState.mode,
            format = configState.format,
            quality = configState.quality,
            durationMs = configState.durationMs,
            brandingEnabled = configState.brandingEnabled
        )
        val sessionDir = File(context.filesDir, "sessions/$sessionId")
        val totalFrames = config.animationFrameCount

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

            // If the job was cancelled, do not treat it as a render failure.
            if (!isActive) return@launch

            if (result.isSuccess) {
                _state.value = CreateVideoState.Preview(videoUri = result.getOrThrow())
            } else {
                _state.value = lastConfiguringState
                _events.send(CreateVideoEvent.ShowSnackbar(R.string.create_video_error_render_failed))
            }
        }
    }

    /**
     * Cancels an in-progress export and returns to [CreateVideoState.Configuring].
     * Called from the cancel dialog's "Stop export" action.
     */
    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.value = lastConfiguringState
    }

    /**
     * Replaceable delete runner for unit tests.
     * Default: calls [ContentResolver.delete] on the IO dispatcher.
     * Returns true if at least one MediaStore row was deleted.
     */
    internal var videoDeleteRunner: suspend (android.net.Uri) -> Boolean = { uri ->
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }
    }

    /**
     * Deletes the video from MediaStore and returns to [CreateVideoState.Configuring].
     * On failure emits [CreateVideoEvent.ShowSnackbar] with [R.string.create_video_delete_failed].
     */
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
}
