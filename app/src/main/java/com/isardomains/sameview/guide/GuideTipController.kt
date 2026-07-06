package com.isardomains.sameview.guide

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class GuideTipEvaluationContext(
    val scope: GuideTipScope,
    val eligibleTipIds: Set<GuideTipId>,
    val isBlockedByTransientUi: Boolean = false
)

enum class GuideTipDismissReason {
    GOT_IT,
    LEARN_MORE
}

@Singleton
class GuideTipController @Inject constructor(
    private val repository: GuideRepository
) {
    private val _activeTipId = MutableStateFlow<GuideTipId?>(null)
    val activeTipId: StateFlow<GuideTipId?> = _activeTipId.asStateFlow()

    private var waitingForUserActionAfterDismissal = false

    suspend fun evaluate(context: GuideTipEvaluationContext): GuideTip? {
        if (_activeTipId.value != null ||
            context.isBlockedByTransientUi ||
            waitingForUserActionAfterDismissal
        ) {
            return null
        }

        val seenTipIds = repository.observeSeenTipIds().first()
        val selectedTip = GuideTipRegistry.tips
            .asSequence()
            .filter { tip -> tip.scope == context.scope }
            .filter { tip -> tip.id in context.eligibleTipIds }
            .filterNot { tip -> tip.id in seenTipIds }
            .filterNot { tip -> tip.prerequisiteTipId != null && tip.prerequisiteTipId !in seenTipIds }
            .sortedBy { tip -> tip.priority }
            .firstOrNull()

        _activeTipId.value = selectedTip?.id
        return selectedTip
    }

    suspend fun dismissActiveTip(reason: GuideTipDismissReason) {
        val tipId = _activeTipId.value ?: return
        if (reason == GuideTipDismissReason.LEARN_MORE) {
            repository.markTipSeen(tipId)
        }
        _activeTipId.value = null
        waitingForUserActionAfterDismissal = true
    }

    fun clearActiveTipWithoutMarkingSeen() {
        _activeTipId.value = null
    }

    fun onUserAction() {
        waitingForUserActionAfterDismissal = false
    }

    suspend fun completeTip(tipId: GuideTipId) {
        repository.markTipSeen(tipId)
        if (_activeTipId.value == tipId) {
            _activeTipId.value = null
        }
        waitingForUserActionAfterDismissal = true
    }

    fun observeTipSeen(tipId: GuideTipId): Flow<Boolean> = repository.observeTipSeen(tipId)
}
