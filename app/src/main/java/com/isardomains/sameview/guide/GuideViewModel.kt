package com.isardomains.sameview.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuideUiState(
    val showResetTipsConfirmation: Boolean = false
)

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val guideRepository: GuideRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    fun onShowTipsAgainClick() {
        _uiState.value = _uiState.value.copy(showResetTipsConfirmation = true)
    }

    fun onResetTipsDismissed() {
        _uiState.value = _uiState.value.copy(showResetTipsConfirmation = false)
    }

    fun onResetTipsConfirmed() {
        viewModelScope.launch {
            guideRepository.resetContextualTips()
            _uiState.value = _uiState.value.copy(showResetTipsConfirmation = false)
        }
    }
}
