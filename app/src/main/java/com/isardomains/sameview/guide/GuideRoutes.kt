package com.isardomains.sameview.guide

import androidx.navigation.NavBackStackEntry

const val ROUTE_GUIDE = "guide"
const val ROUTE_GUIDE_DETAIL = "guide_detail"
const val ARG_GUIDE_TOPIC_ID = "topicId"
const val ROUTE_GUIDE_DETAIL_WITH_ARGS = "$ROUTE_GUIDE_DETAIL/{$ARG_GUIDE_TOPIC_ID}"

fun guideDetailRoute(topicId: GuideTopicId): String = "$ROUTE_GUIDE_DETAIL/${topicId.storedValue}"

fun NavBackStackEntry.guideTopicIdArgument(): GuideTopicId? =
    arguments?.getString(ARG_GUIDE_TOPIC_ID)?.let(GuideTopicId::fromStoredValue)
const val ROUTE_WALKTHROUGH = "walkthrough"
const val ARG_WALKTHROUGH_ENTRY_MODE = "entryMode"
const val ROUTE_WALKTHROUGH_WITH_ARGS = "$ROUTE_WALKTHROUGH/{$ARG_WALKTHROUGH_ENTRY_MODE}"

enum class WalkthroughEntryMode(val routeValue: String) {
    FIRST_RUN("first_run"),
    REPLAY("replay");

    companion object {
        fun fromRouteValue(value: String): WalkthroughEntryMode? =
            values().firstOrNull { it.routeValue == value }
    }
}

enum class FirstRunWalkthroughGateState {
    Loading,
    WaitingForWalkthrough,
    Complete
}

fun walkthroughRoute(entryMode: WalkthroughEntryMode): String =
    "$ROUTE_WALKTHROUGH/${entryMode.routeValue}"

fun NavBackStackEntry.walkthroughEntryModeArgument(): WalkthroughEntryMode? =
    arguments?.getString(ARG_WALKTHROUGH_ENTRY_MODE)?.let(WalkthroughEntryMode::fromRouteValue)

