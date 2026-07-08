package com.isardomains.sameview.guide

enum class GuideTopicId(val storedValue: String) {
    GETTING_STARTED("getting_started"),
    REFERENCE_PHOTOS("reference_photos"),
    GPS_GUIDANCE("gps_guidance"),
    COMPARE("compare"),
    EXPORT("export");

    companion object {
        fun fromStoredValue(value: String): GuideTopicId? =
            values().firstOrNull { it.storedValue == value }
    }
}
