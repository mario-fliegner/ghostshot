package com.isardomains.sameview.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.sameview.ui.camera.GridType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun interface LocationPermissionChecker {
    fun isGranted(): Boolean
}

sealed interface SettingsUiEvent {
    data object RequestLocationPermission : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val locationPermissionChecker: LocationPermissionChecker
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
}
