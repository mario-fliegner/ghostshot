// path: app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt
package com.isardomains.sameview.ui.compare

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.BrandingNormalizer
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.branding.BuiltinSymbolRenderer
import com.isardomains.sameview.branding.GlobalBranding
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.image.ShareCaptionData
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareImageRenderer
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.image.ShareRenderConfig
import com.isardomains.sameview.ui.camera.SessionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * Manages style/quality selection, Extras toggle state, metadata loading,
 * and orchestrates the Share action via [ShareImageRenderer].
 *
 * Defaults: Slider, Standard, Title and date ON, Location OFF.
 */
@HiltViewModel
class ShareComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val globalBrandingRepository: GlobalBrandingRepository
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    // ── User-configurable state ────────────────────────────────────────────────

    private val _style = MutableStateFlow(ShareComparisonStyle.SLIDER)
    val style: StateFlow<ShareComparisonStyle> = _style.asStateFlow()

    private val _quality = MutableStateFlow(ShareQuality.STANDARD)
    val quality: StateFlow<ShareQuality> = _quality.asStateFlow()

    private val _titleDateEnabled = MutableStateFlow(true)
    val titleDateEnabled: StateFlow<Boolean> = _titleDateEnabled.asStateFlow()

    private val _locationEnabled = MutableStateFlow(false)
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    // ── Rendering state ────────────────────────────────────────────────────────

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    // ── Branding state ─────────────────────────────────────────────────────────

    /** True when branding-handle.png exists in this session's directory. */
    private val _hasBranding = MutableStateFlow(false)
    val hasBranding: StateFlow<Boolean> = _hasBranding.asStateFlow()

    /**
     * Controls whether the branding handle is drawn in Slider exports.
     * Default: ON when [hasBranding] is true; OFF when no branding.
     * Not persisted — resets to the default each time the screen is opened.
     */
    private val _useBranding = MutableStateFlow(false)
    val useBranding: StateFlow<Boolean> = _useBranding.asStateFlow()

    /** Toggles [useBranding]. Only meaningful when [hasBranding] is true. */
    fun onToggleUseBranding() {
        _useBranding.value = !_useBranding.value
    }

    /**
     * True when global branding exists. Read once at init — plain val, not reactive StateFlow.
     */
    val hasGlobalBranding: Boolean = globalBrandingRepository.hasBranding()

    private val _brandingError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val brandingError: SharedFlow<Unit> = _brandingError.asSharedFlow()

    // ── Metadata-derived state ─────────────────────────────────────────────────

    /** Width ÷ height ratio of the session viewport; used for preview sizing. */
    private val _sessionViewportRatio = MutableStateFlow(9f / 16f)
    val sessionViewportRatio: StateFlow<Float> = _sessionViewportRatio.asStateFlow()

    /** True when at least one of title or date is available. */
    private val _isTitleDateAvailable = MutableStateFlow(false)
    val isTitleDateAvailable: StateFlow<Boolean> = _isTitleDateAvailable.asStateFlow()

    private val _isLocationAvailable = MutableStateFlow(false)
    val isLocationAvailable: StateFlow<Boolean> = _isLocationAvailable.asStateFlow()

    /** Combined preview text: "title · date", "title", or "date" depending on availability. */
    private val _titleDatePreviewText = MutableStateFlow<String?>(null)
    val titleDatePreviewText: StateFlow<String?> = _titleDatePreviewText.asStateFlow()

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

    /**
     * Override in tests to check branding file existence without a real filesystem.
     * Defaults to checking whether branding-handle.png is a regular file in [sessionDir].
     */
    internal var brandingFileChecker: (File) -> Boolean = { sessionDir ->
        java.io.File(sessionDir, "branding-handle.png").isFile
    }

    /** Override in tests with an unconfined/test dispatcher. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // ── Injectable branding lambdas (replaceable for unit tests) ─────────────

    /** Injectable for unit tests: decodes a URI to a Bitmap. */
    internal var imageDecoder: (Uri) -> Bitmap = { uri ->
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Injectable for unit tests: normalizes a Bitmap to a metadata-clean PNG ByteArray. */
    internal var brandingNormalizer: (Bitmap) -> ByteArray = { bitmap ->
        BrandingNormalizer.normalize(bitmap)
    }

    /** Injectable for unit tests: renders a built-in symbol to a PNG ByteArray. */
    internal var builtinSymbolRenderer: (BuiltinBrandingSymbol) -> ByteArray = { symbol ->
        BuiltinSymbolRenderer.render(context, symbol)
    }

    /** Injectable for unit tests. Defaults to [SessionStorage.updateSessionBranding]. */
    internal var sessionBrandingUpdater: (File, String, ByteArray, String, String?) -> Boolean =
        { root, id, png, type, builtinId ->
            SessionStorage.updateSessionBranding(root, id, png, type, builtinId)
        }

    /** Injectable for unit tests. Defaults to [SessionStorage.removeSessionBranding]. */
    internal var sessionBrandingRemover: (File, String) -> Boolean =
        { root, id -> SessionStorage.removeSessionBranding(root, id) }

    /** Injectable for unit tests. Defaults to [SessionStorage.copyGlobalBrandingToSession]. */
    internal var sessionBrandingCopier: (File, String, GlobalBranding) -> Boolean =
        { root, id, branding -> SessionStorage.copyGlobalBrandingToSession(root, id, branding) }

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
            val hasBranding = withContext(ioDispatcher) { brandingFileChecker(sessionDir) }
            _hasBranding.value = hasBranding
            _useBranding.value = hasBranding  // default: ON when branding present

            // Auto-copy global default when session has no branding
            if (!hasBranding) {
                val globalBranding = withContext(ioDispatcher) { globalBrandingRepository.getBranding() }
                if (globalBranding != null) {
                    val sessionsRoot = File(context.filesDir, "sessions")
                    val copied = withContext(ioDispatcher) {
                        sessionBrandingCopier(sessionsRoot, sessionId, globalBranding)
                    }
                    if (copied) {
                        _hasBranding.value = true
                        _useBranding.value = true
                    }
                }
            }

            val title = snapshot.title?.trim()?.takeIf { it.isNotEmpty() }
            val dateLine = computeDateLine(snapshot.referenceDate, snapshot.captureTimestampMs)
            val locationLine = computeLocationLine(
                snapshot.locationDisplayName, snapshot.locationCity, snapshot.locationCountry
            )

            computedTitle = title
            computedDateLine = dateLine
            computedLocationLine = locationLine

            val combinedPreview = when {
                title != null && dateLine != null -> "$title · $dateLine"
                title != null -> title
                dateLine != null -> dateLine
                else -> null
            }

            _sessionViewportRatio.value = snapshot.viewportRatio
            _titleDatePreviewText.value = combinedPreview
            _locationPreviewText.value = locationLine
            _isTitleDateAvailable.value = combinedPreview != null
            _isLocationAvailable.value = locationLine != null
        }
    }

    // ── Toggle handlers ────────────────────────────────────────────────────────

    fun onStyleChanged(style: ShareComparisonStyle) { _style.value = style }
    fun onQualityChanged(quality: ShareQuality) { _quality.value = quality }
    fun onTitleDateToggled(enabled: Boolean) { _titleDateEnabled.value = enabled }
    fun onLocationToggled(enabled: Boolean) { _locationEnabled.value = enabled }

    // ── Branding operations ────────────────────────────────────────────────────

    /**
     * Decodes [uri] from the Photo Picker, normalizes it, and stores it as session branding.
     * Writes immediately. Emits to [brandingError] on any failure.
     */
    fun onImageUriSelectedForBranding(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bitmap = imageDecoder(uri)
                val bytes = brandingNormalizer(bitmap)
                bitmap.recycle()
                val sessionsRoot = File(context.filesDir, "sessions")
                val ok = sessionBrandingUpdater(sessionsRoot, sessionId, bytes, "image", null)
                if (ok) { _hasBranding.value = true; _useBranding.value = true }
                else _brandingError.emit(Unit)
            } catch (_: Exception) { _brandingError.emit(Unit) }
        }
    }

    /**
     * Renders [symbol] and stores it as session branding.
     * Writes immediately. Emits to [brandingError] on any failure.
     */
    fun onSetSessionBrandingFromSymbol(symbol: BuiltinBrandingSymbol) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bytes = builtinSymbolRenderer(symbol)
                val sessionsRoot = File(context.filesDir, "sessions")
                val ok = sessionBrandingUpdater(sessionsRoot, sessionId, bytes, "builtin", symbol.id)
                if (ok) { _hasBranding.value = true; _useBranding.value = true }
                else _brandingError.emit(Unit)
            } catch (_: Exception) { _brandingError.emit(Unit) }
        }
    }

    /**
     * Removes session branding. After this call [hasBranding] becomes false.
     * Writes immediately. No automatic fallback to global branding.
     */
    fun onRemoveSessionBranding() {
        viewModelScope.launch(ioDispatcher) {
            val sessionsRoot = File(context.filesDir, "sessions")
            val ok = sessionBrandingRemover(sessionsRoot, sessionId)
            if (ok) { _hasBranding.value = false; _useBranding.value = false }
            else _brandingError.emit(Unit)
        }
    }

    /**
     * Copies the current global branding into this session.
     * After a successful copy the session owns an independent copy; future global changes
     * do NOT affect this session.
     */
    fun onUseDefaultLogo() {
        viewModelScope.launch(ioDispatcher) {
            val globalBranding = globalBrandingRepository.getBranding()
            if (globalBranding == null) { _brandingError.emit(Unit); return@launch }
            val sessionsRoot = File(context.filesDir, "sessions")
            val ok = sessionBrandingCopier(sessionsRoot, sessionId, globalBranding)
            if (ok) { _hasBranding.value = true; _useBranding.value = true }
            else _brandingError.emit(Unit)
        }
    }

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
                    exportTimestamp = ts,
                    useBranding = _useBranding.value && _hasBranding.value
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
        val showTitleDate = _titleDateEnabled.value && _isTitleDateAvailable.value
        val titleLine = if (showTitleDate) computedTitle else null
        val dateLine = if (showTitleDate) computedDateLine else null
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
