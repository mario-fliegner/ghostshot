package com.isardomains.sameview.guide

enum class GuideTipId(val storedValue: String) {
    REFERENCE("reference"),
    SHARE("share"),
    EDIT_SESSION("edit_session"),
    OPEN_COMPARISON("open_comparison");

    companion object {
        fun fromStoredValue(value: String): GuideTipId? =
            values().firstOrNull { it.storedValue == value }
    }
}
