package com.isardomains.sameview.ui.camera

import android.graphics.BlurMaskFilter
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewOverlayScrim
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ── Loupe constants (NOT part of ReferenceMarkerDefaults) ──────────────────
private val LOUPE_DIAMETER_DP = 120.dp
private val LOUPE_BORDER_STROKE_DP = 1.5.dp
private val LOUPE_FINGER_OFFSET_DP = 16.dp
private val LOUPE_DONE_AREA_HEIGHT_DP = 88.dp
private val LOUPE_INDICATOR_RING_DP = 16.dp
private val LOUPE_INDICATOR_STROKE_DP = 1.5.dp
private val LOUPE_INDICATOR_DOT_DP = 3.dp
private val LOUPE_SHADOW_BLUR_DP = 6.dp
private const val LOUPE_BITMAP_MAX_DIM = 1024

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
    referenceUri: Uri? = null,
    onAddMarker: (Float, Float) -> Unit,
    onMoveMarker: (String, Float, Float) -> Unit,
    onRemoveMarker: (String) -> Unit,
    onOverlayDragged: (Float, Float) -> Unit,
    onOverlayScaled: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val isEditModeActive = markersState.isEditModeActive
    val markers = markersState.markers
    val shouldRenderMarkers = markersState.markersVisible || isEditModeActive

    val viewportSizeState = remember { mutableStateOf(IntSize.Zero) }
    var markerBeingDeleted by remember { mutableStateOf<String?>(null) }

    // ── Loupe: local drag state (Snapshot state, scoped to this composable) ──
    var isDragging by remember { mutableStateOf(false) }
    var draggingMarkerNormalizedPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // ── Loupe: cached bitmap for image crop rendering ─────────────────────────
    var loupeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Load/reload bitmap when referenceUri changes
    LaunchedEffect(referenceUri) {
        val prevBitmap = loupeBitmap
        loupeBitmap = null
        prevBitmap?.recycle()

        if (referenceUri == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                // Read raw dimensions without decoding
                val boundsOpts = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(referenceUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                val sampleSize = if (maxDim <= 0) 1 else {
                    var s = 1
                    while ((maxDim / (s * 2)) >= LOUPE_BITMAP_MAX_DIM) s *= 2
                    s
                }

                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val raw = context.contentResolver.openInputStream(referenceUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext

                // Apply EXIF orientation so crop geometry matches metadata.orientedWidth/Height
                val exifOrientation = context.contentResolver.openInputStream(referenceUri)
                    ?.use { stream ->
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL

                val matrix = android.graphics.Matrix()
                when (exifOrientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f); matrix.preScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f); matrix.preScale(-1f, 1f)
                    }
                }
                val oriented = if (matrix.isIdentity) raw else {
                    android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                        .also { if (it !== raw) raw.recycle() }
                }
                loupeBitmap = oriented
            } catch (_: Exception) {
                // null bitmap — loupe renders background-only (graceful fallback per spec §10)
            }
        }
    }

    // Recycle bitmap on Edit Mode exit (OQ-3)
    LaunchedEffect(isEditModeActive) {
        if (!isEditModeActive) {
            val bitmap = loupeBitmap
            loupeBitmap = null
            bitmap?.recycle()
        }
    }

    // ── Loupe: pre-allocated Rects for per-frame crop draw (avoids allocation) ──
    // srcRect uses android.graphics.Rect (Canvas.drawBitmap requires integer src rect)
    val srcRect = remember { android.graphics.Rect() }
    val dstRectF = remember { RectF() }

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

    // Loupe pixel sizes
    val loupeDiameterPx = with(density) { LOUPE_DIAMETER_DP.toPx() }
    val loupeRadiusPx = loupeDiameterPx / 2f
    val loupeBorderPx = with(density) { LOUPE_BORDER_STROKE_DP.toPx() }
    val loupeFingerOffsetPx = with(density) { LOUPE_FINGER_OFFSET_DP.toPx() }
    val loupeDoneAreaPx = with(density) { LOUPE_DONE_AREA_HEIGHT_DP.toPx() }
    val loupeShadowBlurPx = with(density) { LOUPE_SHADOW_BLUR_DP.toPx() }
    val loupeIndicatorRingPx = with(density) { LOUPE_INDICATOR_RING_DP.toPx() / 2f }
    val loupeIndicatorStrokePx = with(density) { LOUPE_INDICATOR_STROKE_DP.toPx() }
    val loupeIndicatorDotPx = with(density) { LOUPE_INDICATOR_DOT_DP.toPx() / 2f }

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
                                        // ── Marker drag ───────────────────────────────────
                                        isDragging = true
                                        // Pre-initialize to marker's current position so the loupe
                                        // appears immediately: the classification loop already
                                        // consumed the slop-qualifying move event, so the inner
                                        // loop starts waiting with no queued events to process.
                                        draggingMarkerNormalizedPos = Pair(
                                            nearestMarker.normalizedX,
                                            nearestMarker.normalizedY
                                        )
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
                                                    // Update loupe drag position (same guard as onMoveMarker)
                                                    draggingMarkerNormalizedPos = Pair(clamped.first, clamped.second)
                                                }
                                            }
                                        }
                                        // Drag ended — clear loupe state
                                        isDragging = false
                                        draggingMarkerNormalizedPos = null
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
        // ── Marker rendering via Canvas ───────────────────────────────────────
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

        // ── Drag loupe ────────────────────────────────────────────────────────
        // Visible only during marker drag in Edit Mode.
        // Pointer-transparent — the Box has no interaction modifier.
        val dragPos = draggingMarkerNormalizedPos
        if (isDragging && dragPos != null && metadata != null) {
            val vSize = viewportSizeState.value
            val vW = vSize.width.toFloat()
            val vH = vSize.height.toFloat()

            if (vW > 0f && vH > 0f) {
                val iW = metadata.orientedWidth.toFloat()
                val iH = metadata.orientedHeight.toFloat()
                val (normX, normY) = dragPos

                // Compute marker screen position
                val markerScreen = normalizedToScreen(
                    normX, normY, vW, vH, iW, iH,
                    displayMode, overlayOffsetX, overlayOffsetY, overlayScale
                )

                // Default loupe center: above the marker
                val defaultCenterX = markerScreen.x
                val defaultCenterY = markerScreen.y - loupeRadiusPx - loupeFingerOffsetPx

                // Clamp to viewport (spec §7)
                val clampMinX = loupeRadiusPx
                val clampMaxX = vW - loupeRadiusPx
                val clampMinY = loupeRadiusPx
                val clampMaxY = vH - loupeRadiusPx - loupeDoneAreaPx

                val clampedX = defaultCenterX.coerceIn(clampMinX, clampMaxX)
                val aboveCenterY = defaultCenterY.coerceIn(clampMinY, clampMaxY)

                // Fallback below if above position overlaps the marker
                val minGap = loupeRadiusPx
                val aboveOverlaps = aboveCenterY + loupeRadiusPx > markerScreen.y - minGap
                val loupeCenterY = if (aboveOverlaps) {
                    val belowCenterY = markerScreen.y + loupeRadiusPx + loupeFingerOffsetPx
                    belowCenterY.coerceIn(clampMinY, clampMaxY)
                } else {
                    aboveCenterY
                }

                val loupeTopLeftX = (clampedX - loupeRadiusPx).roundToInt()
                val loupeTopLeftY = (loupeCenterY - loupeRadiusPx).roundToInt()

                Box(
                    modifier = Modifier
                        .absoluteOffset { IntOffset(loupeTopLeftX, loupeTopLeftY) }
                        .size(LOUPE_DIAMETER_DP)
                        .testTag("marker_drag_loupe")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.width / 2f
                        val cx = radius
                        val cy = radius

                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas

                            // ── 1. Background fill + image crop, clipped to circle ──
                            nc.save()
                            nc.clipPath(android.graphics.Path().apply {
                                addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
                            })

                            // Background
                            nc.drawPaint(android.graphics.Paint().apply {
                                color = SameViewOverlayScrim.toArgb()
                            })

                            // Reference image crop
                            val bm = loupeBitmap
                            if (bm != null && !bm.isRecycled) {
                                val bW = bm.width.toFloat()
                                val bH = bm.height.toFloat()
                                if (iW > 0f && iH > 0f && bW > 0f && bH > 0f) {
                                    // effectiveScale uses original image dimensions (spec §6)
                                    val baseScale = when (displayMode) {
                                        ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW ->
                                            max(vW / iW, vH / iH)
                                        ReferenceImageDisplayMode.SHOW_FULL_IMAGE ->
                                            min(vW / iW, vH / iH)
                                    }
                                    val effectiveScale = (baseScale * overlayScale * 2.0f).coerceIn(1.0f, 6.0f)

                                    // Crop half in bitmap pixels, accounting for downsampling
                                    val cropHalfX = (radius / effectiveScale) * (bW / iW)
                                    val cropHalfY = (radius / effectiveScale) * (bH / iH)

                                    // Crop region in bitmap pixel space
                                    val markerBX = normX * bW
                                    val markerBY = normY * bH
                                    val cropL = markerBX - cropHalfX
                                    val cropT = markerBY - cropHalfY
                                    val cropR = markerBX + cropHalfX
                                    val cropB = markerBY + cropHalfY

                                    // Clamp src to bitmap bounds (spec §8: fill out-of-bounds with background)
                                    val srcL = cropL.coerceIn(0f, bW)
                                    val srcT = cropT.coerceIn(0f, bH)
                                    val srcR = cropR.coerceIn(0f, bW)
                                    val srcB = cropB.coerceIn(0f, bH)

                                    val cropW = cropR - cropL
                                    val cropH = cropB - cropT

                                    if (cropW > 0f && cropH > 0f && srcR > srcL && srcB > srcT) {
                                        // Map visible src fraction to dst in loupe space
                                        val dstL = ((srcL - cropL) / cropW) * size.width
                                        val dstT = ((srcT - cropT) / cropH) * size.height
                                        val dstR = ((srcR - cropL) / cropW) * size.width
                                        val dstB = ((srcB - cropT) / cropH) * size.height

                                        // drawBitmap requires integer src rect
                                        srcRect.set(srcL.toInt(), srcT.toInt(), srcR.roundToInt(), srcB.roundToInt())
                                        dstRectF.set(dstL, dstT, dstR, dstB)
                                        nc.drawBitmap(bm, srcRect, dstRectF, null)
                                    }
                                }
                            }

                            nc.restore()

                            // ── 2. Drop shadow on border ring (spec §9) ─────────────
                            nc.drawCircle(
                                cx, cy, radius - loupeBorderPx / 2f,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = loupeBorderPx
                                    color = android.graphics.Color.argb(127, 0, 0, 0)
                                    maskFilter = BlurMaskFilter(
                                        loupeShadowBlurPx,
                                        BlurMaskFilter.Blur.NORMAL
                                    )
                                }
                            )

                            // ── 3. White border ring ─────────────────────────────────
                            nc.drawCircle(
                                cx, cy, radius - loupeBorderPx / 2f,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = loupeBorderPx
                                    color = android.graphics.Color.argb(229, 255, 255, 255)
                                }
                            )

                            // ── 4. Marker indicator: ring ────────────────────────────
                            nc.drawCircle(
                                cx, cy, loupeIndicatorRingPx,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = loupeIndicatorStrokePx
                                    color = android.graphics.Color.WHITE
                                }
                            )

                            // ── 5. Marker indicator: center dot ──────────────────────
                            nc.drawCircle(
                                cx, cy, loupeIndicatorDotPx,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.FILL
                                    color = SameViewAccent.toArgb()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Empty-state hint: centered in the overlay when in edit mode with no markers ──
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
