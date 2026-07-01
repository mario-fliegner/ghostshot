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
            priority = 10
        ),
        GuideTip(
            id = GuideTipId.ALIGN,
            titleRes = R.string.guide_tip_align_title,
            bodyRes = R.string.guide_tip_align_body,
            anchorKey = GuideTipAnchorKey.ALIGN_CONTROLS,
            topicId = GuideTopicId.GETTING_STARTED,
            scope = GuideTipScope.CAMERA,
            priority = 20
        ),
        GuideTip(
            id = GuideTipId.MARKER,
            titleRes = R.string.guide_tip_marker_title,
            bodyRes = R.string.guide_tip_marker_body,
            anchorKey = GuideTipAnchorKey.MARKER_ACTION,
            topicId = GuideTopicId.MARKERS,
            scope = GuideTipScope.CAMERA,
            priority = 30
        ),
        GuideTip(
            id = GuideTipId.GPS,
            titleRes = R.string.guide_tip_gps_title,
            bodyRes = R.string.guide_tip_gps_body,
            anchorKey = GuideTipAnchorKey.GPS_CHIP,
            topicId = GuideTopicId.GPS_GUIDANCE,
            scope = GuideTipScope.CAMERA,
            priority = 40
        ),
        GuideTip(
            id = GuideTipId.COMPARE,
            titleRes = R.string.guide_tip_compare_title,
            bodyRes = R.string.guide_tip_compare_body,
            anchorKey = GuideTipAnchorKey.COMPARE_ACTION,
            topicId = GuideTopicId.COMPARE,
            scope = GuideTipScope.CAMERA,
            priority = 10
        ),
        GuideTip(
            id = GuideTipId.HISTORY,
            titleRes = R.string.guide_tip_history_title,
            bodyRes = R.string.guide_tip_history_body,
            anchorKey = GuideTipAnchorKey.HISTORY_ACTION,
            topicId = GuideTopicId.FAVORITES,
            scope = GuideTipScope.CAMERA,
            priority = 20
        ),
        GuideTip(
            id = GuideTipId.EXPORT,
            titleRes = R.string.guide_tip_export_title,
            bodyRes = R.string.guide_tip_export_body,
            anchorKey = GuideTipAnchorKey.EXPORT_ACTION,
            topicId = GuideTopicId.SHARE_COMPARISON_IMAGE,
            scope = GuideTipScope.COMPARE,
            priority = 30
        )
    )

    private val tipsById: Map<GuideTipId, GuideTip> = tips.associateBy { it.id }

    fun tipFor(id: GuideTipId): GuideTip? = tipsById[id]
}


