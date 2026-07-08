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
            id = GuideTopicId.EXPORT,
            titleKey = "guide_topic_export_title",
            summaryKey = "guide_topic_export_summary"
        )
    )

    private val topicsById: Map<GuideTopicId, GuideTopic> = topics.associateBy { it.id }

    fun topicFor(id: GuideTopicId): GuideTopic? = topicsById[id]
}
