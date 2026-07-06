package com.isardomains.sameview.guide

import com.isardomains.sameview.R

object GuideTipRegistry {
    val tips: List<GuideTip> = listOf(
        GuideTip(
            id = GuideTipId.REFERENCE,
            titleRes = R.string.guide_tip_reference_title,
            bodyRes = R.string.guide_tip_reference_body,
            anchorKey = GuideTipAnchorKey.REFERENCE_BUTTON,
            topicId = GuideTopicId.REFERENCE_PHOTOS,
            scope = GuideTipScope.CAMERA,
            priority = 1
        ),
        GuideTip(
            id = GuideTipId.SHARE,
            titleRes = R.string.guide_tip_share_title,
            bodyRes = R.string.guide_tip_share_body,
            anchorKey = GuideTipAnchorKey.SHARE_ACTION,
            topicId = GuideTopicId.SHARE_COMPARISON_IMAGE,
            scope = GuideTipScope.COMPARE,
            priority = 1
        ),
        GuideTip(
            id = GuideTipId.EDIT_SESSION,
            titleRes = R.string.guide_tip_edit_session_title,
            bodyRes = R.string.guide_tip_edit_session_body,
            anchorKey = GuideTipAnchorKey.OVERFLOW_ACTION,
            topicId = GuideTopicId.GETTING_STARTED,
            scope = GuideTipScope.COMPARE,
            priority = 2,
            prerequisiteTipId = GuideTipId.SHARE
        ),
        GuideTip(
            id = GuideTipId.OPEN_COMPARISON,
            titleRes = R.string.guide_tip_open_comparison_title,
            bodyRes = R.string.guide_tip_open_comparison_body,
            anchorKey = GuideTipAnchorKey.LIBRARY_GRID_AREA,
            topicId = null,
            scope = GuideTipScope.LIBRARY,
            priority = 1
        ),
        GuideTip(
            id = GuideTipId.MULTI_SELECT,
            titleRes = R.string.guide_tip_multi_select_title,
            bodyRes = R.string.guide_tip_multi_select_body,
            anchorKey = GuideTipAnchorKey.LIBRARY_GRID_AREA,
            topicId = null,
            scope = GuideTipScope.LIBRARY,
            priority = 2,
            prerequisiteTipId = GuideTipId.OPEN_COMPARISON
        )
    )

    private val tipsById: Map<GuideTipId, GuideTip> = tips.associateBy { it.id }

    fun tipFor(id: GuideTipId): GuideTip? = tipsById[id]
}
