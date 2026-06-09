// path: app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt
package com.isardomains.sameview.ui.compare

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.isardomains.sameview.ui.camera.SessionStorage
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

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

    private val _isLoading = MutableStateFlow(true)
    /** True while the initial metadata.json read is in progress, false once complete. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _titleField = MutableStateFlow("")
    /** Current value of content.title; empty string when absent. */
    val titleField: StateFlow<String> = _titleField.asStateFlow()

    /** Updates the title field as the user types. */
    fun onTitleChanged(value: String) {
        _titleField.value = value
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
    }

    /** Updates the city field as the user types. */
    fun onLocationCityChanged(value: String) {
        _locationCityField.value = value
    }

    /** Updates the country field as the user types. */
    fun onLocationCountryChanged(value: String) {
        _locationCountryField.value = value
    }

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
}
