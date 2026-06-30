package com.isardomains.sameview.ui.camera

import java.util.UUID

const val MAX_MARKERS = 5

data class ReferenceMarker(
    val id: String = UUID.randomUUID().toString(),
    val normalizedX: Float,
    val normalizedY: Float
)

data class ReferenceMarkersState(
    val markers: List<ReferenceMarker> = emptyList(),
    val markersVisible: Boolean = true,
    val isEditModeActive: Boolean = false
) {
    val markersExist: Boolean get() = markers.isNotEmpty()
}
