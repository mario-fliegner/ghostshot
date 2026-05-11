package com.isardomains.ghostshot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isardomains.ghostshot.ui.camera.GridType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val gridType: StateFlow<GridType> = repository.gridType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridType.RULE_OF_THIRDS)

    fun onGridTypeSelected(type: GridType) {
        viewModelScope.launch { repository.setGridType(type) }
    }

    val keepScreenOn: StateFlow<Boolean> = repository.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onKeepScreenOnChanged(enabled: Boolean) {
        viewModelScope.launch { repository.setKeepScreenOn(enabled) }
    }

    val resetOverlayAfterCapture: StateFlow<Boolean> = repository.resetOverlayAfterCapture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onResetOverlayAfterCaptureChanged(enabled: Boolean) {
        viewModelScope.launch { repository.setResetOverlayAfterCapture(enabled) }
    }
}
