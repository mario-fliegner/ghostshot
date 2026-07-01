package com.isardomains.sameview.guide

data class GuideTipSignals(
    val scope: GuideTipScope,
    val eligibleTipIds: Set<GuideTipId> = emptySet(),
    val isBlockedByTransientUi: Boolean = false
) {
    fun toEvaluationContext(): GuideTipEvaluationContext =
        GuideTipEvaluationContext(
            scope = scope,
            eligibleTipIds = eligibleTipIds,
            isBlockedByTransientUi = isBlockedByTransientUi
        )
}
