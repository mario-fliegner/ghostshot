package com.isardomains.sameview.ui.camera

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewOverlayScrim
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

object ReferenceMarkerDefaults {
    val ringDiameterDp = 20.dp
    val strokeWidthDp = 2.dp
    val centerDotDiameterDp = 4.dp
    val ringColor: Color = Color.White
    val centerDotColor: Color = SameViewAccent
    val warningColor: Color = Color.Red
    val shadowRadiusDp = 4.dp
    val touchTargetDp = 48.dp
    val dragPriorityRadiusDp = 15.dp
}

/**
 * Converts a normalized image coordinate (0.0–1.0) to screen pixels within the overlay viewport.
 *
 * Reproduces the transform from CompareReferenceImage (COMPARE_WITH_PREVIEW) and the SHOW_FULL_IMAGE
 * AsyncImage with graphicsLayer, locally — no shared utility, no modification of existing paths.
 */
internal fun normalizedToScreen(
    normalizedX: Float,
    normalizedY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    displayMode: ReferenceImageDisplayMode,
    overlayOffsetX: Float,
    overlayOffsetY: Float,
    overlayScale: Float
): Offset {
    val baseScale = when (displayMode) {
        ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW ->
            max(viewportWidth / imageWidth, viewportHeight / imageHeight)
        ReferenceImageDisplayMode.SHOW_FULL_IMAGE ->
            min(viewportWidth / imageWidth, viewportHeight / imageHeight)
    }
    val displayedWidth = imageWidth * baseScale
    val displayedHeight = imageHeight * baseScale

    val translationX: Float
    val translationY: Float
    if (displayMode == ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW) {
        val scaledWidth = displayedWidth * overlayScale
        val scaledHeight = displayedHeight * overlayScale
        val maxTX = max(0f, (scaledWidth - viewportWidth) / 2f)
        val maxTY = max(0f, (scaledHeight - viewportHeight) / 2f)
        translationX = (overlayOffsetX * viewportWidth).coerceIn(-maxTX, maxTX)
        translationY = (overlayOffsetY * viewportHeight).coerceIn(-maxTY, maxTY)
    } else {
        translationX = overlayOffsetX * viewportWidth
        translationY = overlayOffsetY * viewportHeight
    }

    val screenX = viewportWidth / 2f + displayedWidth * (normalizedX - 0.5f) * overlayScale + translationX
    val screenY = viewportHeight / 2f + displayedHeight * (normalizedY - 0.5f) * overlayScale + translationY
    return Offset(screenX, screenY)
}

/**
 * Converts a screen position to normalized image coordinates (0.0–1.0).
 *
 * Returns null if the position falls outside the image bounds.
 */
internal fun screenToNormalized(
    screenPos: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    displayMode: ReferenceImageDisplayMode,
    overlayOffsetX: Float,
    overlayOffsetY: Float,
    overlayScale: Float
): Pair<Float, Float>? {
    val baseScale = when (displayMode) {
        ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW ->
            max(viewportWidth / imageWidth, viewportHeight / imageHeight)
        ReferenceImageDisplayMode.SHOW_FULL_IMAGE ->
            min(viewportWidth / imageWidth, viewportHeight / imageHeight)
    }
    val displayedWidth = imageWidth * baseScale
    val displayedHeight = imageHeight * baseScale

    val translationX: Float
    val translationY: Float
    if (displayMode == ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW) {
        val scaledWidth = displayedWidth * overlayScale
        val scaledHeight = displayedHeight * overlayScale
        val maxTX = max(0f, (scaledWidth - viewportWidth) / 2f)
        val maxTY = max(0f, (scaledHeight - viewportHeight) / 2f)
        translationX = (overlayOffsetX * viewportWidth).coerceIn(-maxTX, maxTX)
        translationY = (overlayOffsetY * viewportHeight).coerceIn(-maxTY, maxTY)
    } else {
        translationX = overlayOffsetX * viewportWidth
        translationY = overlayOffsetY * viewportHeight
    }

    val effWidth = displayedWidth * overlayScale
    val effHeight = displayedHeight * overlayScale
    if (effWidth <= 0f || effHeight <= 0f) return null

    val normalizedX = (screenPos.x - viewportWidth / 2f - translationX) / effWidth + 0.5f
    val normalizedY = (screenPos.y - viewportHeight / 2f - translationY) / effHeight + 0.5f

    if (normalizedX < 0f || normalizedX > 1f || normalizedY < 0f || normalizedY > 1f) return null
    return Pair(normalizedX, normalizedY)
}

private fun findNearestMarker(
    position: Offset,
    markers: List<ReferenceMarker>,
    viewportWidth: Float,
    viewportHeight: Float,
    metadata: ReferenceImageMetadata,
    displayMode: ReferenceImageDisplayMode,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    thresholdPx: Float
): ReferenceMarker? {
    var nearest: ReferenceMarker? = null
    var minDist = Float.MAX_VALUE
    for (marker in markers) {
        val screenPos = normalizedToScreen(
            marker.normalizedX, marker.normalizedY,
            viewportWidth, viewportHeight,
            metadata.orientedWidth.toFloat(), metadata.orientedHeight.toFloat(),
            displayMode, offsetX, offsetY, scale
        )
        val dist = (position - screenPos).getDistance()
        if (dist < thresholdPx && dist < minDist) {
            nearest = marker
            minDist = dist
        }
    }
    return nearest
}

@Composable
internal fun ReferenceMarkerOverlay(
    markersState: ReferenceMarkersState,
    metadata: ReferenceImageMetadata?,
    displayMode: ReferenceImageDisplayMode,
    overlayOffsetX: Float,
    overlayOffsetY: Float,
    overlayScale: Float,
    onAddMarker: (Float, Float) -> Unit,
    onMoveMarker: (String, Float, Float) -> Unit,
    onRemoveMarker: (String) -> Unit,
    onOverlayDragged: (Float, Float) -> Unit,
    onOverlayScaled: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isEditModeActive = markersState.isEditModeActive
    val markers = markersState.markers
    val shouldRenderMarkers = markersState.markersVisible || isEditModeActive

    val viewportSizeState = remember { mutableStateOf(IntSize.Zero) }
    var markerBeingDeleted by remember { mutableStateOf<String?>(null) }

    // Stable state references for use inside pointerInput coroutines
    val currentMarkersState = rememberUpdatedState(markers)
    val currentMetadataState = rememberUpdatedState(metadata)
    val currentDisplayModeState = rememberUpdatedState(displayMode)
    val currentOffsetXState = rememberUpdatedState(overlayOffsetX)
    val currentOffsetYState = rememberUpdatedState(overlayOffsetY)
    val currentScaleState = rememberUpdatedState(overlayScale)
    val onAddMarkerState = rememberUpdatedState(onAddMarker)
    val onMoveMarkerState = rememberUpdatedState(onMoveMarker)
    val onRemoveMarkerState = rememberUpdatedState(onRemoveMarker)
    val onOverlayDraggedState = rememberUpdatedState(onOverlayDragged)
    val onOverlayScaledState = rememberUpdatedState(onOverlayScaled)

    // Pre-compute pixel sizes from dp — recalculated on density change via recomposition
    val ringRadiusPx = with(density) { (ReferenceMarkerDefaults.ringDiameterDp / 2f).toPx() }
    val strokeWidthPx = with(density) { ReferenceMarkerDefaults.strokeWidthDp.toPx() }
    val centerDotRadiusPx = with(density) { (ReferenceMarkerDefaults.centerDotDiameterDp / 2f).toPx() }
    val shadowRadiusPx = with(density) { ReferenceMarkerDefaults.shadowRadiusDp.toPx() }
    val dragPriorityPx = with(density) { ReferenceMarkerDefaults.dragPriorityRadiusDp.toPx() }

    val emptyHintText = stringResource(R.string.markers_empty_hint)
    val editModeDescription = stringResource(R.string.markers_edit_mode_description)

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewportSizeState.value = it }
            .then(
                if (isEditModeActive) {
                    Modifier.pointerInput(Unit) {
                        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                        val touchSlopPx = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val downPos = down.position
                            val downPointerId: PointerId = down.id

                            val vSize = viewportSizeState.value
                            val meta = currentMetadataState.value
                            val dm = currentDisplayModeState.value
                            val ox = currentOffsetXState.value
                            val oy = currentOffsetYState.value
                            val sc = currentScaleState.value
                            val vW = vSize.width.toFloat()
                            val vH = vSize.height.toFloat()

                            val nearestMarker = if (meta != null && vW > 0f && vH > 0f) {
                                findNearestMarker(
                                    downPos, currentMarkersState.value,
                                    vW, vH,
                                    meta, dm, ox, oy, sc,
                                    dragPriorityPx
                                )
                            } else null

                            // Detect long-press vs. drag/lift
                            val gestureResult = withTimeoutOrNull(longPressTimeoutMs) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        return@withTimeoutOrNull "dragged"
                                    }
                                    val change = event.changes.firstOrNull()
                                        ?: return@withTimeoutOrNull "lifted"
                                    if (!change.pressed) return@withTimeoutOrNull "lifted"
                                    val moved =
                                        (change.position - downPos).getDistance() > touchSlopPx
                                    if (moved) return@withTimeoutOrNull "dragged"
                                    change.consume()
                                }
                                @Suppress("UNREACHABLE_CODE")
                                "impossible"
                            }

                            when (gestureResult) {
                                null -> {
                                    // Long-press confirmed
                                    if (nearestMarker != null) {
                                        // Show warning state, wait for lift, then delete
                                        markerBeingDeleted = nearestMarker.id
                                        loop@ while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull()
                                                ?: break@loop
                                            change.consume()
                                            if (!change.pressed) break@loop
                                        }
                                        onRemoveMarkerState.value(nearestMarker.id)
                                        markerBeingDeleted = null
                                    } else {
                                        // Add marker at long-press position — wait for lift first
                                        val latestMeta = currentMetadataState.value
                                        val latestVSize = viewportSizeState.value
                                        val normalized = if (latestMeta != null && latestVSize.width > 0) {
                                            screenToNormalized(
                                                downPos,
                                                latestVSize.width.toFloat(),
                                                latestVSize.height.toFloat(),
                                                latestMeta.orientedWidth.toFloat(),
                                                latestMeta.orientedHeight.toFloat(),
                                                currentDisplayModeState.value,
                                                currentOffsetXState.value,
                                                currentOffsetYState.value,
                                                currentScaleState.value
                                            )
                                        } else null
                                        if (normalized != null) {
                                            onAddMarkerState.value(normalized.first, normalized.second)
                                        }
                                        // Drain events until lift
                                        loop@ while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull()
                                                ?: break@loop
                                            change.consume()
                                            if (!change.pressed) break@loop
                                        }
                                    }
                                }

                                "lifted" -> {
                                    // Tap — no action
                                }

                                else -> {
                                    // Drag — either marker drag or overlay pan/zoom
                                    if (nearestMarker != null) {
                                        // Marker drag: track the original pointer only
                                        loop@ while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull {
                                                it.id == downPointerId
                                            } ?: break@loop
                                            change.consume()
                                            if (!change.pressed) break@loop
                                            val latestMeta = currentMetadataState.value
                                            val latestVSize = viewportSizeState.value
                                            if (latestMeta != null && latestVSize.width > 0) {
                                                val normalized = screenToNormalized(
                                                    change.position,
                                                    latestVSize.width.toFloat(),
                                                    latestVSize.height.toFloat(),
                                                    latestMeta.orientedWidth.toFloat(),
                                                    latestMeta.orientedHeight.toFloat(),
                                                    currentDisplayModeState.value,
                                                    currentOffsetXState.value,
                                                    currentOffsetYState.value,
                                                    currentScaleState.value
                                                )
                                                // Clamp to image bounds during drag
                                                val clamped = normalized ?: Pair(
                                                    change.position.x
                                                        .coerceIn(0f, latestVSize.width.toFloat())
                                                        .let { px ->
                                                            screenToNormalized(
                                                                Offset(px, change.position.y.coerceIn(0f, latestVSize.height.toFloat())),
                                                                latestVSize.width.toFloat(),
                                                                latestVSize.height.toFloat(),
                                                                latestMeta.orientedWidth.toFloat(),
                                                                latestMeta.orientedHeight.toFloat(),
                                                                currentDisplayModeState.value,
                                                                currentOffsetXState.value,
                                                                currentOffsetYState.value,
                                                                currentScaleState.value
                                                            )?.first ?: 0.5f
                                                        },
                                                    0.5f  // fallback — marker stays in place
                                                )
                                                if (normalized != null) {
                                                    onMoveMarkerState.value(
                                                        nearestMarker.id,
                                                        clamped.first,
                                                        clamped.second
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Overlay pan / pinch — forward to ViewModel callbacks
                                        loop@ while (true) {
                                            val event = awaitPointerEvent()
                                            if (!event.changes.any { it.pressed }) break@loop
                                            val latestVSize = viewportSizeState.value
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            if (zoom != 1f) {
                                                onOverlayScaledState.value(zoom)
                                            }
                                            if (latestVSize.width > 0 && (pan.x != 0f || pan.y != 0f)) {
                                                onOverlayDraggedState.value(
                                                    pan.x / latestVSize.width,
                                                    pan.y / latestVSize.height
                                                )
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        // Marker rendering via Canvas
        if (shouldRenderMarkers && metadata != null) {
            val iW = metadata.orientedWidth.toFloat()
            val iH = metadata.orientedHeight.toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val vW = size.width
                val vH = size.height
                if (vW <= 0f || vH <= 0f || iW <= 0f || iH <= 0f) return@Canvas

                for ((index, marker) in markers.withIndex()) {
                    val isDeleting = marker.id == markerBeingDeleted
                    val ringColor = if (isDeleting) ReferenceMarkerDefaults.warningColor
                    else ReferenceMarkerDefaults.ringColor
                    val dotColor = if (isDeleting) ReferenceMarkerDefaults.warningColor
                    else ReferenceMarkerDefaults.centerDotColor

                    val screenPos = normalizedToScreen(
                        marker.normalizedX, marker.normalizedY,
                        vW, vH, iW, iH,
                        displayMode, overlayOffsetX, overlayOffsetY, overlayScale
                    )

                    // Shadow ring using BlurMaskFilter (hardware-accelerated on API 29+)
                    drawIntoCanvas { canvas ->
                        val shadowPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokeWidthPx
                            color = android.graphics.Color.argb(180, 0, 0, 0)
                            maskFilter = BlurMaskFilter(
                                shadowRadiusPx,
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        canvas.nativeCanvas.drawCircle(
                            screenPos.x, screenPos.y, ringRadiusPx, shadowPaint
                        )
                    }

                    // Marker ring
                    drawCircle(
                        color = ringColor,
                        radius = ringRadiusPx,
                        center = screenPos,
                        style = Stroke(width = strokeWidthPx)
                    )

                    // Center dot
                    drawCircle(
                        color = dotColor,
                        radius = centerDotRadiusPx,
                        center = screenPos
                    )
                }
            }
        }

        // Empty-state hint: centered in the overlay when in edit mode with no markers
        if (isEditModeActive && markers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyHintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SameViewTextPrimary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}
