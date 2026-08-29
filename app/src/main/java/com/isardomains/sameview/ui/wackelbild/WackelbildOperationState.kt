// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildOperationState.kt
package com.isardomains.sameview.ui.wackelbild

import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildErrorClassification
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildSlot

/**
 * Observable phase of the current DeinWackelbild handoff operation, exposed by
 * [WackelbildViewModel] for a later UI block to consume. UI-independent by design: no user-facing
 * strings, no secrets, no handoff token, no upload URL, no raw server message anywhere in this
 * model. Retry attempts and handoff restarts happen silently within [CreatingHandoff]/
 * [UploadingSlot] -- there is no separate "retrying"/"restarting" phase, matching the product
 * requirement that automatic retry stays invisible behind one undifferentiated spinner state.
 */
sealed interface WackelbildOperationState {
    data object Idle : WackelbildOperationState
    data object Preparing : WackelbildOperationState

    /** Entered only when the renderer used the lower-quality fallback source. No network call is
     * made until [WackelbildViewModel.confirmFallbackAndContinue] is called explicitly. */
    data object AwaitingFallbackConfirmation : WackelbildOperationState

    data object CreatingHandoff : WackelbildOperationState
    data class UploadingSlot(val slot: DeinWackelbildSlot) : WackelbildOperationState

    /** Terminal success. [checkoutUrl] is the exact, unmodified value returned by the API. */
    data class Ready(val checkoutUrl: String, val usedFallback: Boolean) : WackelbildOperationState

    data class Failed(val failure: WackelbildOperationFailure) : WackelbildOperationState
}

/** Operation-level failure categories -- distinct from the raw [DeinWackelbildErrorClassification]
 * layer, deliberately minimal, intended for a later block's UX mapping. Never carries a raw server
 * message. */
enum class WackelbildOperationFailureCategory {
    PREPARATION_FAILED,
    NETWORK_UNAVAILABLE,
    SERVER_TEMPORARY,
    INTEGRATION_UNAVAILABLE,
    HANDOFF_FAILED,
    INVALID_LOCAL_OUTPUT
}

/** [classification] is retained only for internal diagnostics/logging -- never surfaced as a
 * user-facing string. */
data class WackelbildOperationFailure(
    val category: WackelbildOperationFailureCategory,
    val classification: DeinWackelbildErrorClassification? = null
)
