// path: app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt
package com.isardomains.sameview.ui.compare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.branding.BrandingNormalizer
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.branding.BuiltinSymbolRenderer
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.ui.camera.SessionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Single-shot events emitted by [EditSessionViewModel] to drive navigation and error display. */
sealed interface EditSessionEvent {
    data object SaveComplete : EditSessionEvent
    data object SaveFailed : EditSessionEvent
    /** Emitted after a successful favourite toggle write. Does NOT trigger navigation. */
    data object FavoriteToggleComplete : EditSessionEvent
}

/**
 * Single-shot branding error event emitted to [EditSessionScreen] for snackbar display.
 * Separate from [EditSessionEvent] so it does not interfere with MainActivity navigation.
 */
internal data object BrandingOperationFailed

/**
 * Carries the user-editable metadata fields read from metadata.json at editor open time.
 *
 * All fields default to an empty string / zero when the corresponding JSON block or field is
 * absent. New fields use default values so existing test call sites using positional constructor
 * syntax continue to compile.
 */
internal data class InitialSessionFields(
    val title: String,
    val referenceDate: String,
    val locationDisplayName: String,
    val locationCity: String,
    val locationCountry: String,
    val description: String = "",
    val captureTimestampMs: Long = 0L,
    val referenceSourceDisplayName: String = "",
    val isFavorite: Boolean = false,
    val hasBranding: Boolean = false
)

/**
 * ViewModel for [EditSessionScreen].
 *
 * On init, reads the editable metadata fields (title, description, reference date, location, and
 * read-only reference metadata) from the session's metadata.json on the IO dispatcher. The read
 * is best-effort: any IO or parse error leaves all fields at their empty initial values.
 *
 * [isDirty] is true when any current editable field value differs from the loaded initial value
 * (after blank normalization). The Save button is enabled when [isDirty] is true and [isSaving]
 * is false.
 *
 * [onSave] validates the reference date field first. On validation failure, sets [referenceDateError]
 * and returns without writing. On validation success, writes changed field groups in order:
 * content (title+description) → referenceDate → location. Emits [EditSessionEvent.SaveComplete]
 * when all writes succeed (or no writes were needed); emits [EditSessionEvent.SaveFailed] if any
 * storage write returns false.
 *
 * [sessionId] is provided by Navigation Compose via [SavedStateHandle].
 */
@HiltViewModel
class EditSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val globalBrandingRepository: GlobalBrandingRepository
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    /** Replaceable for unit tests; defaults to [Dispatchers.IO]. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // ── Initial field values (set before corresponding StateFlows to keep isDirty stable) ──

    private var initialTitle = ""
    private var initialDescription = ""
    private var initialReferenceDate = ""
    private var initialLocationDisplayName = ""
    private var initialLocationCity = ""
    private var initialLocationCountry = ""

    // ── Favourite state (independent of form dirty tracking) ──────────────────

    private val _isFavorite = MutableStateFlow(false)
    /**
     * Current favourite status for this session. Updated optimistically when [toggleFavorite]
     * is called. Does NOT influence [isDirty] or the Save button.
     */
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ── Loading state ───────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(true)
    /** True while the initial metadata.json read is in progress, false once complete. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Editable field StateFlows ───────────────────────────────────────────────

    private val _titleField = MutableStateFlow("")
    /** Current value of content.title; empty string when absent. */
    val titleField: StateFlow<String> = _titleField.asStateFlow()

    /** Updates the title field as the user types. */
    fun onTitleChanged(value: String) {
        _titleField.value = value
        updateIsDirty()
    }

    private val _descriptionField = MutableStateFlow("")
    /** Current value of content.description; empty string when absent. */
    val descriptionField: StateFlow<String> = _descriptionField.asStateFlow()

    /** Updates the description field as the user types. */
    fun onDescriptionChanged(value: String) {
        _descriptionField.value = value
        updateIsDirty()
    }

    private val _referenceDateField = MutableStateFlow("")
    /** Current value of reference.date; empty string when absent. */
    val referenceDateField: StateFlow<String> = _referenceDateField.asStateFlow()

    internal val _referenceDateError = MutableStateFlow<String?>(null)
    /** Current validation error for the reference date field; null when no error is present. */
    val referenceDateError: StateFlow<String?> = _referenceDateError.asStateFlow()

    /** Updates the reference date field as the user types and clears any existing validation error. */
    fun onReferenceDateChanged(value: String) {
        _referenceDateField.value = value
        _referenceDateError.value = null
        updateIsDirty()
    }

    /**
     * Returns true if [value] is a valid reference date input.
     *
     * An empty or blank value is valid in the UI context (means "remove date" on save).
     * A non-empty trimmed value is delegated to [SessionStorage.isValidReferenceDate].
     */
    internal fun isValidReferenceDateInput(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isEmpty() || SessionStorage.isValidReferenceDate(trimmed)
    }

    private val _locationDisplayNameField = MutableStateFlow("")
    /** Current value of location.displayName; empty string when absent. */
    val locationDisplayNameField: StateFlow<String> = _locationDisplayNameField.asStateFlow()

    private val _locationCityField = MutableStateFlow("")
    /** Current value of location.city; empty string when absent. */
    val locationCityField: StateFlow<String> = _locationCityField.asStateFlow()

    private val _locationCountryField = MutableStateFlow("")
    /** Current value of location.country; empty string when absent. */
    val locationCountryField: StateFlow<String> = _locationCountryField.asStateFlow()

    /** Updates the location display name field as the user types. */
    fun onLocationDisplayNameChanged(value: String) {
        _locationDisplayNameField.value = value
        updateIsDirty()
    }

    /** Updates the city field as the user types. */
    fun onLocationCityChanged(value: String) {
        _locationCityField.value = value
        updateIsDirty()
    }

    /** Updates the country field as the user types. */
    fun onLocationCountryChanged(value: String) {
        _locationCountryField.value = value
        updateIsDirty()
    }

    // ── Read-only reference metadata StateFlows ─────────────────────────────────

    private val _captureTimestampMs = MutableStateFlow(0L)
    /** Epoch-millisecond capture timestamp from capture.timestampMs; 0 when absent. */
    val captureTimestampMs: StateFlow<Long> = _captureTimestampMs.asStateFlow()

    private val _referenceSourceDisplayName = MutableStateFlow("")
    /** Raw reference.sourceDisplayName value from metadata.json; empty when absent. */
    val referenceSourceDisplayName: StateFlow<String> = _referenceSourceDisplayName.asStateFlow()

    // ── Dirty and saving state ──────────────────────────────────────────────────

    private val _isDirty = MutableStateFlow(false)
    /**
     * True when any current editable field value differs from its loaded initial value
     * (blank-normalized). The Save button is enabled when this is true and [isSaving] is false.
     */
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    /** True while a save operation is in progress. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Normalizes a field value: trims whitespace; empty string becomes null (absent). */
    private fun normalizeField(s: String): String? = s.trim().ifEmpty { null }

    /** Recomputes [isDirty] from the current field values against the loaded initial values. */
    private fun updateIsDirty() {
        _isDirty.value =
            normalizeField(_titleField.value) != normalizeField(initialTitle) ||
            normalizeField(_descriptionField.value) != normalizeField(initialDescription) ||
            normalizeField(_referenceDateField.value) != normalizeField(initialReferenceDate) ||
            normalizeField(_locationDisplayNameField.value) != normalizeField(initialLocationDisplayName) ||
            normalizeField(_locationCityField.value) != normalizeField(initialLocationCity) ||
            normalizeField(_locationCountryField.value) != normalizeField(initialLocationCountry)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private val _events = MutableSharedFlow<EditSessionEvent>()
    /** Single-shot navigation and error events consumed by the host (MainActivity). */
    val events: SharedFlow<EditSessionEvent> = _events.asSharedFlow()

    // ── Storage lambdas (replaceable for unit tests) ─────────────────────────

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateContent]. */
    internal var sessionContentUpdater: (File, String, String?, String?) -> Boolean =
        { root, id, title, description -> SessionStorage.updateContent(root, id, title, description) }

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateReferenceDate]. */
    internal var sessionReferenceDateUpdater: (File, String, String?) -> Boolean =
        { root, id, date -> SessionStorage.updateReferenceDate(root, id, date) }

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateLocation]. */
    internal var sessionLocationUpdater: (File, String, String?, String?, String?) -> Boolean =
        { root, id, dn, city, country -> SessionStorage.updateLocation(root, id, dn, city, country) }

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateFavorite]. */
    internal var sessionFavoriteUpdater: (File, String, Boolean) -> Boolean =
        { root, id, fav -> SessionStorage.updateFavorite(root, id, fav) }

    // ── Metadata reader ─────────────────────────────────────────────────────

    /**
     * Reads all editor fields from metadata.json.
     *
     * Replaceable for unit tests. The default implementation reads the file synchronously;
     * call sites always dispatch to [ioDispatcher].
     */
    internal var metadataReader: (sessionsRoot: File, sessionId: String) -> InitialSessionFields =
        { sessionsRoot, sId ->
            val metadataFile = File(File(sessionsRoot, sId), "metadata.json")
            val json = JSONObject(metadataFile.readText())
            val contentObj = json.optJSONObject("content")
            val referenceObj = json.optJSONObject("reference")
            val captureObj = json.optJSONObject("capture")
            val locationObj = json.optJSONObject("location")
            val additionalObj = json.optJSONObject("additional")
            InitialSessionFields(
                title = contentObj?.optString("title", "") ?: "",
                description = contentObj?.optString("description", "") ?: "",
                referenceDate = referenceObj?.optString("date", "") ?: "",
                locationDisplayName = locationObj?.optString("displayName", "") ?: "",
                locationCity = locationObj?.optString("city", "") ?: "",
                locationCountry = locationObj?.optString("country", "") ?: "",
                captureTimestampMs = captureObj?.optLong("timestampMs", 0L) ?: 0L,
                referenceSourceDisplayName = referenceObj?.run {
                    // v5 sessions write "sourceUri"; v2–v4 sessions write "sourceDisplayName".
                    optString("sourceUri", "").ifEmpty { null }
                        ?: optString("sourceDisplayName", "").ifEmpty { null }
                } ?: "",
                isFavorite = additionalObj?.optBoolean("isFavorite", false) ?: false,
                hasBranding = run {
                    val filesObj = json.optJSONObject("files")
                    val brandingHandle = filesObj?.optString("brandingHandle", "")?.takeIf { it.isNotEmpty() }
                    brandingHandle != null &&
                        File(File(sessionsRoot, sId), brandingHandle).let { it.exists() && it.isFile }
                }
            )
        }

    init {
        viewModelScope.launch {
            try {
                val sessionsRoot = File(context.filesDir, "sessions")
                val fields = withContext(ioDispatcher) {
                    metadataReader(sessionsRoot, sessionId)
                }
                // Set initial values BEFORE field StateFlows so isDirty stays false after load.
                initialTitle = fields.title
                initialDescription = fields.description
                initialReferenceDate = fields.referenceDate
                initialLocationDisplayName = fields.locationDisplayName
                initialLocationCity = fields.locationCity
                initialLocationCountry = fields.locationCountry
                _titleField.value = fields.title
                _descriptionField.value = fields.description
                _referenceDateField.value = fields.referenceDate
                _locationDisplayNameField.value = fields.locationDisplayName
                _locationCityField.value = fields.locationCity
                _locationCountryField.value = fields.locationCountry
                _captureTimestampMs.value = fields.captureTimestampMs
                _referenceSourceDisplayName.value = fields.referenceSourceDisplayName
                _isFavorite.value = fields.isFavorite
                _hasBranding.value = fields.hasBranding
                _hasGlobalBranding.value = withContext(ioDispatcher) {
                    globalBrandingRepository.hasBranding()
                }
            } catch (e: Exception) {
                // All fields remain at their initial empty-string / zero values.
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Validates the reference date field, then writes all changed fields to storage in order:
     * content (title+description) → referenceDate → location. Emits [EditSessionEvent.SaveComplete]
     * on success or when no fields changed; emits [EditSessionEvent.SaveFailed] if any storage
     * write returns false.
     *
     * If reference date validation fails, sets [referenceDateError] and returns without writing.
     * Blank normalization (trim + empty → null) is applied at save time only; field display values
     * are not altered.
     */
    fun onSave() {
        viewModelScope.launch {
            // Validate first — before setting isSaving — so a validation failure is not treated
            // as a save attempt.
            if (!isValidReferenceDateInput(_referenceDateField.value)) {
                _referenceDateError.value = "error"
                return@launch
            }

            _isSaving.value = true
            try {
                val sessionsRoot = File(context.filesDir, "sessions")

                val normalizedTitle = normalizeField(_titleField.value)
                val normalizedDescription = normalizeField(_descriptionField.value)
                val normalizedRefDate = normalizeField(_referenceDateField.value)
                val normalizedDisplayName = normalizeField(_locationDisplayNameField.value)
                val normalizedCity = normalizeField(_locationCityField.value)
                val normalizedCountry = normalizeField(_locationCountryField.value)

                // 1. Write content (title + description) if either changed.
                val contentChanged =
                    normalizedTitle != normalizeField(initialTitle) ||
                    normalizedDescription != normalizeField(initialDescription)
                if (contentChanged) {
                    val ok = withContext(ioDispatcher) {
                        sessionContentUpdater(sessionsRoot, sessionId, normalizedTitle, normalizedDescription)
                    }
                    if (!ok) { _events.emit(EditSessionEvent.SaveFailed); return@launch }
                }

                // 2. Write reference date if changed.
                if (normalizedRefDate != normalizeField(initialReferenceDate)) {
                    val ok = withContext(ioDispatcher) {
                        sessionReferenceDateUpdater(sessionsRoot, sessionId, normalizedRefDate)
                    }
                    if (!ok) { _events.emit(EditSessionEvent.SaveFailed); return@launch }
                }

                // 3. Write location if any location field changed.
                val locationChanged =
                    normalizedDisplayName != normalizeField(initialLocationDisplayName) ||
                    normalizedCity != normalizeField(initialLocationCity) ||
                    normalizedCountry != normalizeField(initialLocationCountry)
                if (locationChanged) {
                    val ok = withContext(ioDispatcher) {
                        sessionLocationUpdater(
                            sessionsRoot, sessionId,
                            normalizedDisplayName, normalizedCity, normalizedCountry
                        )
                    }
                    if (!ok) { _events.emit(EditSessionEvent.SaveFailed); return@launch }
                }

                // All writes succeeded (or no writes were needed).
                // Update initial fields so isDirty resets to false.
                initialTitle = normalizedTitle ?: ""
                initialDescription = normalizedDescription ?: ""
                initialReferenceDate = normalizedRefDate ?: ""
                initialLocationDisplayName = normalizedDisplayName ?: ""
                initialLocationCity = normalizedCity ?: ""
                initialLocationCountry = normalizedCountry ?: ""
                updateIsDirty()

                _events.emit(EditSessionEvent.SaveComplete)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Toggles [isFavorite] for this session.
     *
     * Uses an optimistic-update strategy: [_isFavorite] is flipped immediately so the UI
     * reflects the new state without visible latency. The write to metadata.json happens
     * asynchronously on the IO dispatcher. On write failure, the value is reverted and
     * [EditSessionEvent.SaveFailed] is emitted.
     *
     * This function does NOT affect [isDirty] and does NOT interact with the form Save flow.
     */
    fun toggleFavorite() {
        val newValue = !_isFavorite.value
        _isFavorite.value = newValue // optimistic flip
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
                _events.emit(EditSessionEvent.FavoriteToggleComplete)
            } else {
                _isFavorite.value = !newValue // revert
                _events.emit(EditSessionEvent.SaveFailed)
            }
        }
    }

    // ── Session branding (immediate write; independent of Save flow) ──────────

    private val _hasBranding = MutableStateFlow(false)
    /**
     * True when [SessionStorage.FILE_BRANDING_HANDLE] is referenced in metadata.json and
     * the file exists on disk. Updated at editor open time and after each branding operation.
     * Does NOT affect [isDirty] and is never reverted by "Discard changes".
     */
    val hasBranding: StateFlow<Boolean> = _hasBranding.asStateFlow()

    private val _hasGlobalBranding = MutableStateFlow(false)
    /**
     * True when global branding is currently set in [GlobalBrandingRepository].
     * Used to determine whether "Copy from default branding" is visible.
     */
    val hasGlobalBranding: StateFlow<Boolean> = _hasGlobalBranding.asStateFlow()

    /** Emitted (as a [Unit]) when a branding operation fails. Collected by [EditSessionScreen]. */
    private val _brandingError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val brandingError: SharedFlow<Unit> = _brandingError.asSharedFlow()

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
    internal var sessionBrandingCopier: (File, String, com.isardomains.sameview.branding.GlobalBranding) -> Boolean =
        { root, id, branding -> SessionStorage.copyGlobalBrandingToSession(root, id, branding) }

    /**
     * Decodes [uri] from the Photo Picker, normalizes it, and stores it as session branding.
     * Writes immediately; does NOT affect [isDirty].
     * Emits to [brandingError] on any failure; existing branding is preserved.
     */
    fun onImageUriSelectedForBranding(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bitmap = imageDecoder(uri)
                val bytes = brandingNormalizer(bitmap)
                bitmap.recycle()
                val sessionsRoot = File(context.filesDir, "sessions")
                val ok = sessionBrandingUpdater(sessionsRoot, sessionId, bytes, "image", null)
                if (ok) {
                    _hasBranding.value = true
                } else {
                    _brandingError.emit(Unit)
                }
            } catch (_: Exception) {
                _brandingError.emit(Unit)
            }
        }
    }

    /**
     * Renders [symbol] and stores it as session branding.
     * Writes immediately; does NOT affect [isDirty].
     * Emits to [brandingError] on any failure.
     */
    fun onSetSessionBrandingFromSymbol(symbol: BuiltinBrandingSymbol) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bytes = builtinSymbolRenderer(symbol)
                val sessionsRoot = File(context.filesDir, "sessions")
                val ok = sessionBrandingUpdater(sessionsRoot, sessionId, bytes, "builtin", symbol.id)
                if (ok) {
                    _hasBranding.value = true
                } else {
                    _brandingError.emit(Unit)
                }
            } catch (_: Exception) {
                _brandingError.emit(Unit)
            }
        }
    }

    /**
     * Removes session branding. After this call [hasBranding] becomes false.
     * Writes immediately; does NOT affect [isDirty].
     * No automatic fallback to global branding.
     */
    fun onRemoveSessionBranding() {
        viewModelScope.launch(ioDispatcher) {
            val sessionsRoot = File(context.filesDir, "sessions")
            val ok = sessionBrandingRemover(sessionsRoot, sessionId)
            if (ok) {
                _hasBranding.value = false
            } else {
                _brandingError.emit(Unit)
            }
        }
    }

    /**
     * Copies the current global branding into this session.
     * Only callable when [hasBranding] is false and [hasGlobalBranding] is true.
     * After a successful copy the session owns an independent copy; future global changes
     * do NOT affect this session.
     */
    fun onCopyFromGlobalBranding() {
        viewModelScope.launch(ioDispatcher) {
            val globalBranding = globalBrandingRepository.getBranding()
            if (globalBranding == null) {
                _brandingError.emit(Unit)
                return@launch
            }
            val sessionsRoot = File(context.filesDir, "sessions")
            val ok = sessionBrandingCopier(sessionsRoot, sessionId, globalBranding)
            if (ok) {
                _hasBranding.value = true
            } else {
                _brandingError.emit(Unit)
            }
        }
    }
}
