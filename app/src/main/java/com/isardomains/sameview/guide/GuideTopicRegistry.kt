package com.isardomains.sameview.guide

object GuideTopicRegistry {
    val topics: List<GuideTopic> = listOf(
        GuideTopic(
            id = GuideTopicId.GETTING_STARTED,
            titleKey = "guide_topic_getting_started_title",
            summaryKey = "guide_topic_getting_started_summary"
        ),
        GuideTopic(
            id = GuideTopicId.REFERENCE_PHOTOS,
            titleKey = "guide_topic_reference_photos_title",
            summaryKey = "guide_topic_reference_photos_summary"
        ),
        GuideTopic(
            id = GuideTopicId.MARKERS,
            titleKey = "guide_topic_markers_title",
            summaryKey = "guide_topic_markers_summary"
        ),
        GuideTopic(
            id = GuideTopicId.GPS_GUIDANCE,
            titleKey = "guide_topic_gps_guidance_title",
            summaryKey = "guide_topic_gps_guidance_summary"
        ),
        GuideTopic(
            id = GuideTopicId.COMPARE,
            titleKey = "guide_topic_compare_title",
            summaryKey = "guide_topic_compare_summary"
        ),
        GuideTopic(
            id = GuideTopicId.SHARE_COMPARISON_IMAGE,
            titleKey = "guide_topic_share_comparison_image_title",
            summaryKey = "guide_topic_share_comparison_image_summary"
        ),
        GuideTopic(
            id = GuideTopicId.CREATE_VIDEO,
            titleKey = "guide_topic_create_video_title",
            summaryKey = "guide_topic_create_video_summary"
        ),
        GuideTopic(
            id = GuideTopicId.FAVORITES,
            titleKey = "guide_topic_favorites_title",
            summaryKey = "guide_topic_favorites_summary"
        ),
        GuideTopic(
            id = GuideTopicId.BACKUPS,
            titleKey = "guide_topic_backups_title",
            summaryKey = "guide_topic_backups_summary"
        )
    )

    private val topicsById: Map<GuideTopicId, GuideTopic> = topics.associateBy { it.id }

    fun topicFor(id: GuideTopicId): GuideTopic? = topicsById[id]
}
