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
        // Dismiss (GOT_IT) and Learn more (LEARN_MORE) both mark the tip permanently seen —
        // dismissing is completion, not a temporary hide. `reason` is kept for callers/analytics
        // to distinguish which action the user took, even though persistence is now identical.
        repository.markTipSeen(tipId)
        _activeTipId.value = null
        waitingForUserActionAfterDismissal = true
    }

    /**
     * Clears the active tip without marking it seen. When [expectedTipId] is non-null, the
     * clear only takes effect if that tip is the one currently active — this makes the call
     * ownership-safe for screens whose dispose/cleanup may run late (after Compose Navigation
     * has already moved on to a different screen that has since made its own tip active).
     * Passing `null` preserves the original unconditional-clear behavior.
     */
    fun clearActiveTipWithoutMarkingSeen(expectedTipId: GuideTipId? = null) {
        if (expectedTipId == null || _activeTipId.value == expectedTipId) {
            _activeTipId.value = null
        }
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

    /**
     * Resets all in-memory guide tip state, so the controller behaves like a freshly
     * constructed instance (equivalent to a real app start). Used by "Show tips again" —
     * without this, [waitingForUserActionAfterDismissal] or [_activeTipId] could survive
     * a reset even though the persisted seen_tip_ids were cleared.
     */
    fun resetInMemoryState() {
        _activeTipId.value = null
        waitingForUserActionAfterDismissal = false
    }
}
