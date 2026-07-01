package com.isardomains.sameview.guide

enum class GuideTopicId(val storedValue: String) {
    GETTING_STARTED("getting_started"),
    REFERENCE_PHOTOS("reference_photos"),
    MARKERS("markers"),
    GPS_GUIDANCE("gps_guidance"),
    COMPARE("compare"),
    SHARE_COMPARISON_IMAGE("share_comparison_image"),
    CREATE_VIDEO("create_video"),
    FAVORITES("favorites"),
    BACKUPS("backups");

    companion object {
        fun fromStoredValue(value: String): GuideTopicId? =
            values().firstOrNull { it.storedValue == value }
    }
}
