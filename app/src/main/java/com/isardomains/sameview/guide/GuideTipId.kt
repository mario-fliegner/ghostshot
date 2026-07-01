package com.isardomains.sameview.guide

enum class GuideTipId(val storedValue: String) {
    REFERENCE("reference"),
    ALIGN("align"),
    COMPARE("compare"),
    HISTORY("history"),
    EXPORT("export"),
    MARKER("marker"),
    GPS("gps");

    companion object {
        fun fromStoredValue(value: String): GuideTipId? =
            values().firstOrNull { it.storedValue == value }
    }
}
