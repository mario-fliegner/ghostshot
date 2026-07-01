package com.isardomains.sameview.guide

import androidx.annotation.StringRes

enum class GuideTipScope {
    CAMERA,
    COMPARE
}

data class GuideTip(
    val id: GuideTipId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val anchorKey: GuideTipAnchorKey,
    val topicId: GuideTopicId?,
    val scope: GuideTipScope,
    val priority: Int
)
