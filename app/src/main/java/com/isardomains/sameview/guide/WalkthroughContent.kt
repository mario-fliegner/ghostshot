package com.isardomains.sameview.guide

import androidx.annotation.StringRes
import com.isardomains.sameview.R

enum class WalkthroughPageId {
    THEN_AND_NOW,
    ALIGN_OVERLAY,
    TAKE_SHOT,
    SEE_WHAT_CHANGED
}

data class WalkthroughPage(
    val id: WalkthroughPageId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int
)

object WalkthroughContent {
    val pages: List<WalkthroughPage> = listOf(
        WalkthroughPage(
            id = WalkthroughPageId.THEN_AND_NOW,
            titleRes = R.string.walkthrough_page_then_and_now_title,
            bodyRes = R.string.walkthrough_page_then_and_now_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.ALIGN_OVERLAY,
            titleRes = R.string.walkthrough_page_align_overlay_title,
            bodyRes = R.string.walkthrough_page_align_overlay_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.TAKE_SHOT,
            titleRes = R.string.walkthrough_page_take_shot_title,
            bodyRes = R.string.walkthrough_page_take_shot_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.SEE_WHAT_CHANGED,
            titleRes = R.string.walkthrough_page_see_what_changed_title,
            bodyRes = R.string.walkthrough_page_see_what_changed_body
        )
    )
}
