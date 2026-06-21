// path: app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt
package com.isardomains.sameview.ui.compare

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.R
import com.isardomains.sameview.image.ShareCaptionData
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareImageRenderer
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.image.ShareRenderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── Events ─────────────────────────────────────────────────────────────────────

sealed class ShareComparisonEvent {
    data class ShowSnackbar(@StringRes val messageResId: Int) : ShareComparisonEvent()
    /** Signals that the JPEG has been written; host launches the Android Share Sheet. */
    data class ShareReady(val uri: Uri) : ShareComparisonEvent()
}

// ── Metadata snapshot (internal; injectable for tests) ─────────────────────────

internal data class ShareMetadataSnapshot(
    val title: String?,
    val referenceDate: String?,
    val captureTimestampMs: Long,
    val locationDisplayName: String?,
    val locationCity: String?,
    val locationCountry: String?,
    /** Width ÷ height of the session viewport; defaults to portrait 9:16. */
    val viewportRatio: Float = 9f / 16f
)

// ── ViewModel ──────────────────────────────────────────────────────────────────

/**
 * ViewModel for [ShareComparisonScreen].
 *
 * Manages style/quality selection, Information toggle state, metadata loading,
 * and orchestrates the Share action via [ShareImageRenderer].
 *
 * Defaults: Slider, Standard, Title ON, Date ON, Location OFF.
 */
@HiltViewModel
class ShareComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    // ── User-configurable state ────────────────────────────────────────────────

    private val _style = MutableStateFlow(ShareComparisonStyle.SLIDER)
    val style: StateFlow<ShareComparisonStyle> = _style.asStateFlow()

    private val _quality = MutableStateFlow(ShareQuality.STANDARD)
    val quality: StateFlow<ShareQuality> = _quality.asStateFlow()

    private val _titleEnabled = MutableStateFlow(true)
    val titleEnabled: StateFlow<Boolean> = _titleEnabled.asStateFlow()

    private val _dateEnabled = MutableStateFlow(true)
    val dateEnabled: StateFlow<Boolean> = _dateEnabled.asStateFlow()

    private val _locationEnabled = MutableStateFlow(false)
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    // ── Rendering state ────────────────────────────────────────────────────────

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    // ── Metadata-derived state ─────────────────────────────────────────────────

    /** Width ÷ height ratio of the session viewport; used for preview sizing. */
    private val _sessionViewportRatio = MutableStateFlow(9f / 16f)
    val sessionViewportRatio: StateFlow<Float> = _sessionViewportRatio.asStateFlow()

    private val _isTitleAvailable = MutableStateFlow(false)
    val isTitleAvailable: StateFlow<Boolean> = _isTitleAvailable.asStateFlow()

    private val _isDateAvailable = MutableStateFlow(false)
    val isDateAvailable: StateFlow<Boolean> = _isDateAvailable.asStateFlow()

    private val _isLocationAvailable = MutableStateFlow(false)
    val isLocationAvailable: StateFlow<Boolean> = _isLocationAvailable.asStateFlow()

    private val _titlePreviewText = MutableStateFlow<String?>(null)
    val titlePreviewText: StateFlow<String?> = _titlePreviewText.asStateFlow()

    private val _datePreviewText = MutableStateFlow<String?>(null)
    val datePreviewText: StateFlow<String?> = _datePreviewText.asStateFlow()

    private val _locationPreviewText = MutableStateFlow<String?>(null)
    val locationPreviewText: StateFlow<String?> = _locationPreviewText.asStateFlow()

    // ── Events ─────────────────────────────────────────────────────────────────

    private val _events = Channel<ShareComparisonEvent>(Channel.BUFFERED)
    val events: Flow<ShareComparisonEvent> = _events.receiveAsFlow()

    // ── Stored computed values for caption building ────────────────────────────

    private var computedTitle: String? = null
    private var computedDateLine: String? = null
    private var computedLocationLine: String? = null

    // ── Injectable for tests ───────────────────────────────────────────────────

    /** Override in tests to inject a fixed metadata snapshot without hitting disk. */
    internal var metadataReader: suspend (File) -> ShareMetadataSnapshot = { dir ->
        readMetadata(dir)
    }

    /** Override in tests to avoid real MediaStore writes and rendering. */
    internal var shareRunner: suspend (ShareRenderConfig, ContentResolver) -> Uri = { config, resolver ->
        ShareImageRenderer().render(config, resolver)
    }

    /** Override in tests with an unconfined/test dispatcher. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // ── Initialization ─────────────────────────────────────────────────────────

    init {
        loadMetadata()
    }

    /**
     * Loads session metadata and populates all metadata StateFlows.
     * Called automatically from [init]; callable explicitly in tests after setting
     * [metadataReader] and [ioDispatcher].
     */
    internal fun loadMetadata() {
        viewModelScope.launch {
            val sessionDir = File(context.filesDir, "sessions/$sessionId")
            val snapshot = withContext(ioDispatcher) { metadataReader(sessionDir) }

            val title = snapshot.title?.trim()?.takeIf { it.isNotEmpty() }
            val dateLine = computeDateLine(snapshot.referenceDate, snapshot.captureTimestampMs)
            val locationLine = computeLocationLine(
                snapshot.locationDisplayName, snapshot.locationCity, snapshot.locationCountry
            )

            computedTitle = title
            computedDateLine = dateLine
            computedLocationLine = locationLine

            _sessionViewportRatio.value = snapshot.viewportRatio
            _titlePreviewText.value = title
            _datePreviewText.value = dateLine
            _locationPreviewText.value = locationLine
            _isTitleAvailable.value = title != null
            _isDateAvailable.value = dateLine != null
            _isLocationAvailable.value = locationLine != null
        }
    }

    // ── Toggle handlers ────────────────────────────────────────────────────────

    fun onStyleChanged(style: ShareComparisonStyle) { _style.value = style }
    fun onQualityChanged(quality: ShareQuality) { _quality.value = quality }
    fun onTitleToggled(enabled: Boolean) { _titleEnabled.value = enabled }
    fun onDateToggled(enabled: Boolean) { _dateEnabled.value = enabled }
    fun onLocationToggled(enabled: Boolean) { _locationEnabled.value = enabled }

    // ── Share action ───────────────────────────────────────────────────────────

    /**
     * Renders the comparison image via [ShareImageRenderer] and emits a [ShareComparisonEvent.ShareReady]
     * event on success. The host observes the event and launches the Android Share Sheet.
     *
     * No-op while rendering is already in progress.
     */
    fun onShare() {
        if (_isRendering.value) return
        _isRendering.value = true
        viewModelScope.launch {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val config = ShareRenderConfig(
                    style = _style.value,
                    quality = _quality.value,
                    captionData = buildCaptionData(),
                    sessionDir = File(context.filesDir, "sessions/$sessionId"),
                    exportTimestamp = ts
                )
                val uri = withContext(ioDispatcher) {
                    shareRunner(config, context.contentResolver)
                }
                _events.send(ShareComparisonEvent.ShareReady(uri))
            } catch (_: Exception) {
                _events.send(ShareComparisonEvent.ShowSnackbar(R.string.share_comparison_error_render_failed))
            } finally {
                _isRendering.value = false
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Builds the current [ShareCaptionData] from toggle state and loaded metadata.
     * Returns null when no active caption content exists (triggers caption-less export).
     */
    internal fun buildCaptionData(): ShareCaptionData? {
        val titleLine = if (_titleEnabled.value && _isTitleAvailable.value) computedTitle else null
        val dateLine = if (_dateEnabled.value && _isDateAvailable.value) computedDateLine else null
        val locationLine = if (_locationEnabled.value && _isLocationAvailable.value) computedLocationLine else null
        if (titleLine == null && dateLine == null && locationLine == null) return null
        return ShareCaptionData(titleLine, dateLine, locationLine)
    }

    private fun computeDateLine(referenceDate: String?, captureTimestampMs: Long): String? {
        if (referenceDate == null || captureTimestampMs == 0L) return null
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

    private fun computeLocationLine(
        displayName: String?,
        city: String?,
        country: String?
    ): String? {
        val dn = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val c = city?.trim()?.takeIf { it.isNotEmpty() }
        val cn = country?.trim()?.takeIf { it.isNotEmpty() }
        val cityCountry = when {
            c != null && cn != null -> "$c, $cn"
            c != null -> c
            cn != null -> cn
            else -> null
        }
        return when {
            dn != null && cityCountry != null -> "$dn · $cityCountry"
            dn != null -> dn
            cityCountry != null -> cityCountry
            else -> null
        }
    }

    private fun readMetadata(sessionDir: File): ShareMetadataSnapshot {
        val file = File(sessionDir, "metadata.json")
        if (!file.exists()) return ShareMetadataSnapshot(null, null, 0L, null, null, null)
        return try {
            val json = JSONObject(file.readText())
            val title = json.optJSONObject("content")?.optString("title", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val referenceDate = json.optJSONObject("reference")?.optString("date", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val captureTimestampMs = json.optJSONObject("capture")?.optLong("timestampMs", 0L) ?: 0L
            val locationDisplayName = json.optJSONObject("location")?.optString("displayName", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val locationCity = json.optJSONObject("location")?.optString("city", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val locationCountry = json.optJSONObject("location")?.optString("country", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val vw = json.optJSONObject("viewport")?.optInt("width", 0) ?: 0
            val vh = json.optJSONObject("viewport")?.optInt("height", 0) ?: 0
            val viewportRatio = if (vw > 0 && vh > 0) vw.toFloat() / vh.toFloat() else 9f / 16f
            ShareMetadataSnapshot(
                title, referenceDate, captureTimestampMs,
                locationDisplayName, locationCity, locationCountry, viewportRatio
            )
        } catch (_: Exception) {
            ShareMetadataSnapshot(null, null, 0L, null, null, null)
        }
    }
}
