package com.isardomains.sameview.guide

import androidx.annotation.StringRes
import com.isardomains.sameview.R

enum class WalkthroughPageId {
    CHOOSE_PHOTO,
    ALIGN_OVERLAY,
    CAPTURE,
    COMPARE
}

data class WalkthroughPage(
    val id: WalkthroughPageId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int
)

object WalkthroughContent {
    val pages: List<WalkthroughPage> = listOf(
        WalkthroughPage(
            id = WalkthroughPageId.CHOOSE_PHOTO,
            titleRes = R.string.walkthrough_page_choose_photo_title,
            bodyRes = R.string.walkthrough_page_choose_photo_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.ALIGN_OVERLAY,
            titleRes = R.string.walkthrough_page_align_overlay_title,
            bodyRes = R.string.walkthrough_page_align_overlay_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.CAPTURE,
            titleRes = R.string.walkthrough_page_capture_title,
            bodyRes = R.string.walkthrough_page_capture_body
        ),
        WalkthroughPage(
            id = WalkthroughPageId.COMPARE,
            titleRes = R.string.walkthrough_page_compare_title,
            bodyRes = R.string.walkthrough_page_compare_body
        )
    )
}
