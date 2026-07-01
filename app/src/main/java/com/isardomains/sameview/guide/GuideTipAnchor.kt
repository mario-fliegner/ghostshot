package com.isardomains.sameview.guide

import androidx.compose.ui.geometry.Rect

enum class GuideTipAnchorKey {
    REFERENCE_BUTTON,
    ALIGN_CONTROLS,
    COMPARE_ACTION,
    HISTORY_ACTION,
    EXPORT_ACTION,
    MARKER_ACTION,
    GPS_CHIP
}

data class GuideTipAnchor(
    val key: GuideTipAnchorKey,
    val bounds: Rect
) {
    val isUsable: Boolean
        get() = bounds.width > 0f && bounds.height > 0f
}
