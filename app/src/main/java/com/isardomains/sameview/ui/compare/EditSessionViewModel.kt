// path: app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt
package com.isardomains.sameview.ui.compare

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}

/**
 * Carries the five user-editable metadata fields read from metadata.json at editor open time.
 *
 * All fields default to an empty string when the corresponding JSON block or field is absent.
 */
internal data class InitialSessionFields(
    val title: String,
    val referenceDate: String,
    val locationDisplayName: String,
    val locationCity: String,
    val locationCountry: String
)

/**
 * ViewModel for [EditSessionScreen].
 *
 * On init, reads the five user-editable metadata fields (title, reference date, and the three
 * location fields) from the session's metadata.json on the IO dispatcher and exposes them as
 * [StateFlow]s. The read is best-effort: any IO or parse error leaves all fields at their empty
 * initial values without crashing.
 *
 * [isDirty] is true when any current field value differs from the loaded initial value (after
 * blank normalization). The Save button is enabled when [isDirty] is true and [isSaving] is false.
 *
 * [onSave] validates the reference date field first. On validation failure, sets [referenceDateError]
 * and returns without writing. On validation success, writes changed field groups in order:
 * title → referenceDate → location. Emits [EditSessionEvent.SaveComplete] when all writes succeed
 * (or no writes were needed); emits [EditSessionEvent.SaveFailed] if any storage write returns false.
 *
 * [sessionId] is provided by Navigation Compose via [SavedStateHandle].
 */
@HiltViewModel
class EditSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    /** Replaceable for unit tests; defaults to [Dispatchers.IO]. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // ── Initial field values (set before corresponding StateFlows to keep isDirty stable) ──

    private var initialTitle = ""
    private var initialReferenceDate = ""
    private var initialLocationDisplayName = ""
    private var initialLocationCity = ""
    private var initialLocationCountry = ""

    // ── Loading state ───────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(true)
    /** True while the initial metadata.json read is in progress, false once complete. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Field StateFlows ────────────────────────────────────────────────────────

    private val _titleField = MutableStateFlow("")
    /** Current value of content.title; empty string when absent. */
    val titleField: StateFlow<String> = _titleField.asStateFlow()

    /** Updates the title field as the user types. */
    fun onTitleChanged(value: String) {
        _titleField.value = value
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

    // ── Dirty and saving state ──────────────────────────────────────────────────

    private val _isDirty = MutableStateFlow(false)
    /**
     * True when any current field value differs from its loaded initial value (blank-normalized).
     * The Save button is enabled when this is true and [isSaving] is false.
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

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateTitle]. */
    internal var sessionTitleUpdater: (File, String, String?) -> Boolean =
        { root, id, title -> SessionStorage.updateTitle(root, id, title) }

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateReferenceDate]. */
    internal var sessionReferenceDateUpdater: (File, String, String?) -> Boolean =
        { root, id, date -> SessionStorage.updateReferenceDate(root, id, date) }

    /** Replaceable for unit tests. Defaults to [SessionStorage.updateLocation]. */
    internal var sessionLocationUpdater: (File, String, String?, String?, String?) -> Boolean =
        { root, id, dn, city, country -> SessionStorage.updateLocation(root, id, dn, city, country) }

    // ── Metadata reader ─────────────────────────────────────────────────────

    /**
     * Reads the five editor fields from metadata.json.
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
            val locationObj = json.optJSONObject("location")
            InitialSessionFields(
                title = contentObj?.optString("title", "") ?: "",
                referenceDate = referenceObj?.optString("date", "") ?: "",
                locationDisplayName = locationObj?.optString("displayName", "") ?: "",
                locationCity = locationObj?.optString("city", "") ?: "",
                locationCountry = locationObj?.optString("country", "") ?: ""
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
                initialReferenceDate = fields.referenceDate
                initialLocationDisplayName = fields.locationDisplayName
                initialLocationCity = fields.locationCity
                initialLocationCountry = fields.locationCountry
                _titleField.value = fields.title
                _referenceDateField.value = fields.referenceDate
                _locationDisplayNameField.value = fields.locationDisplayName
                _locationCityField.value = fields.locationCity
                _locationCountryField.value = fields.locationCountry
            } catch (e: Exception) {
                // All fields remain at their initial empty-string values.
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Validates the reference date field, then writes all changed fields to storage in order:
     * title → referenceDate → location. Emits [EditSessionEvent.SaveComplete] on success or when
     * no fields changed; emits [EditSessionEvent.SaveFailed] if any storage write returns false.
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
                val normalizedRefDate = normalizeField(_referenceDateField.value)
                val normalizedDisplayName = normalizeField(_locationDisplayNameField.value)
                val normalizedCity = normalizeField(_locationCityField.value)
                val normalizedCountry = normalizeField(_locationCountryField.value)

                // 1. Write title if changed.
                if (normalizedTitle != normalizeField(initialTitle)) {
                    val ok = withContext(ioDispatcher) {
                        sessionTitleUpdater(sessionsRoot, sessionId, normalizedTitle)
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
}
