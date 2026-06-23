package com.isardomains.sameview.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.branding.BrandingNormalizer
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.branding.BuiltinSymbolRenderer
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.ui.camera.GridType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

fun interface LocationPermissionChecker {
    fun isGranted(): Boolean
}

sealed interface SettingsUiEvent {
    data object RequestLocationPermission : SettingsUiEvent
    data object BrandingLoadFailed : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val locationPermissionChecker: LocationPermissionChecker,
    private val globalBrandingRepository: GlobalBrandingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val gridType: StateFlow<GridType> = repository.gridType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridType.RULE_OF_THIRDS)

    fun onGridTypeSelected(type: GridType) {
        viewModelScope.launch {
            runCatching { repository.setGridType(type) }
        }
    }

    val keepScreenOn: StateFlow<Boolean> = repository.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onKeepScreenOnChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setKeepScreenOn(enabled) }
        }
    }

    val resetOverlayAfterCapture: StateFlow<Boolean> = repository.resetOverlayAfterCapture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onResetOverlayAfterCaptureChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setResetOverlayAfterCapture(enabled) }
        }
    }

    val autoOpenCompareAfterCapture: StateFlow<Boolean> = repository.autoOpenCompareAfterCapture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onAutoOpenCompareAfterCaptureChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setAutoOpenCompareAfterCapture(enabled) }
        }
    }

    val recreationGuidance: StateFlow<Boolean> = repository.recreationGuidance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val liveDirectionArrow: StateFlow<Boolean> = repository.liveDirectionArrow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onLiveDirectionArrowChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setLiveDirectionArrow(enabled) }
        }
    }

    private val _uiEvents = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<SettingsUiEvent> = _uiEvents.asSharedFlow()

    fun onRecreationGuidanceChanged(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                runCatching { repository.setRecreationGuidance(false) }
            }
            return
        }
        if (locationPermissionChecker.isGranted()) {
            viewModelScope.launch {
                runCatching { repository.setRecreationGuidance(true) }
            }
        } else {
            viewModelScope.launch {
                _uiEvents.emit(SettingsUiEvent.RequestLocationPermission)
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setRecreationGuidance(granted) }
        }
    }

    val stripOriginalsMetadata: StateFlow<Boolean> = repository.stripOriginalsMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onStripOriginalsMetadataChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setStripOriginalsMetadata(enabled) }
        }
    }

    // ── Global branding ───────────────────────────────────────────────────────

    /** Injectable for unit tests; defaults to [Dispatchers.IO]. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _hasBranding = MutableStateFlow(globalBrandingRepository.hasBranding())
    val hasBranding: StateFlow<Boolean> = _hasBranding.asStateFlow()

    /** Injectable for unit tests: decodes a URI to a Bitmap using the app ContentResolver. */
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

    /** Injectable for unit tests: renders a built-in symbol to a metadata-clean PNG ByteArray. */
    internal var builtinSymbolRenderer: (BuiltinBrandingSymbol) -> ByteArray = { symbol ->
        BuiltinSymbolRenderer.render(context, symbol)
    }

    /**
     * Decodes [uri] from the Photo Picker, normalizes the image, and stores it as global branding.
     * Emits [SettingsUiEvent.BrandingLoadFailed] on any error; existing branding is preserved.
     * The URI is used only for a single read and is never persisted.
     */
    fun onImageUriSelected(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bitmap = imageDecoder(uri)
                val bytes = brandingNormalizer(bitmap)
                bitmap.recycle()
                globalBrandingRepository.setBranding(bytes, "image", null)
                _hasBranding.value = true
            } catch (_: Exception) {
                _uiEvents.emit(SettingsUiEvent.BrandingLoadFailed)
            }
        }
    }

    /**
     * Renders [symbol] from the built-in symbol set and stores it as global branding.
     * Emits [SettingsUiEvent.BrandingLoadFailed] on any rendering error.
     */
    fun onSetBrandingFromSymbol(symbol: BuiltinBrandingSymbol) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val bytes = builtinSymbolRenderer(symbol)
                globalBrandingRepository.setBranding(bytes, "builtin", symbol.id)
                _hasBranding.value = true
            } catch (_: Exception) {
                _uiEvents.emit(SettingsUiEvent.BrandingLoadFailed)
            }
        }
    }

    /** Removes global branding. After this call, new sessions will have no default branding. */
    fun onRemoveBranding() {
        viewModelScope.launch(ioDispatcher) {
            globalBrandingRepository.removeBranding()
            _hasBranding.value = false
        }
    }
}
