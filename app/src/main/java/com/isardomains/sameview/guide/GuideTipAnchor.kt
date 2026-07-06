package com.isardomains.sameview.guide

import androidx.compose.ui.geometry.Rect

enum class GuideTipAnchorKey {
    REFERENCE_BUTTON,
    SHARE_ACTION,
    OVERFLOW_ACTION,
    LIBRARY_GRID_AREA
}

data class GuideTipAnchor(
    val key: GuideTipAnchorKey,
    val bounds: Rect
) {
    val isUsable: Boolean
        get() = bounds.width > 0f && bounds.height > 0f
}
