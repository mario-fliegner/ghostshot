package com.isardomains.sameview.guide

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

enum class GuideTipPlacementSide {
    ABOVE,
    BELOW,
    START,
    END
}

sealed interface GuideTipPlacementResult {
    data class Placed(
        val offset: IntOffset,
        val side: GuideTipPlacementSide
    ) : GuideTipPlacementResult

    object Deferred : GuideTipPlacementResult
}

data class GuideTipPlacementInput(
    val containerSize: IntSize,
    val cardSize: IntSize,
    val anchorBounds: Rect?,
    val windowWidthSizeClass: WindowWidthSizeClass,
    val marginPx: Float,
    val gapPx: Float,
    val isLandscape: Boolean = false,
    val exclusionZones: List<Rect> = emptyList()
)

fun calculateGuideTipPlacement(input: GuideTipPlacementInput): GuideTipPlacementResult {
    val anchor = input.anchorBounds
    if (anchor == null ||
        anchor.width <= 0f ||
        anchor.height <= 0f ||
        input.containerSize.width <= 0 ||
        input.containerSize.height <= 0 ||
        input.cardSize.width <= 0 ||
        input.cardSize.height <= 0 ||
        !anchor.intersectsContainer(input.containerSize)
    ) {
        return GuideTipPlacementResult.Deferred
    }

    return candidateSides(input.windowWidthSizeClass, input.isLandscape)
        .asSequence()
        .mapNotNull { side -> placedCandidate(input, anchor, side) }
        .firstOrNull()
        ?: GuideTipPlacementResult.Deferred
}

private fun candidateSides(
    windowWidthSizeClass: WindowWidthSizeClass,
    isLandscape: Boolean
): List<GuideTipPlacementSide> = when {
    // Phones frequently cross into Medium width class once rotated to landscape (the portrait
    // height becomes the landscape width), so this must key on isLandscape alone rather than
    // Compact && isLandscape — otherwise a landscape phone falls through to the Medium/Expanded
    // side-placement order below, which is meant for wide tablet-style layouts, not rotated phones.
    isLandscape ->
        listOf(
            GuideTipPlacementSide.ABOVE,
            GuideTipPlacementSide.BELOW,
            GuideTipPlacementSide.START,
            GuideTipPlacementSide.END
        )
    windowWidthSizeClass == WindowWidthSizeClass.Compact ->
        listOf(GuideTipPlacementSide.ABOVE, GuideTipPlacementSide.BELOW)
    else ->
        listOf(
            GuideTipPlacementSide.END,
            GuideTipPlacementSide.START,
            GuideTipPlacementSide.ABOVE,
            GuideTipPlacementSide.BELOW
        )
}

private fun placedCandidate(
    input: GuideTipPlacementInput,
    anchor: Rect,
    side: GuideTipPlacementSide
): GuideTipPlacementResult.Placed? {
    val margin = input.marginPx
    val cardWidth = input.cardSize.width.toFloat()
    val cardHeight = input.cardSize.height.toFloat()
    val containerWidth = input.containerSize.width.toFloat()
    val containerHeight = input.containerSize.height.toFloat()
    if (cardWidth > containerWidth - margin * 2f || cardHeight > containerHeight - margin * 2f) {
        return null
    }

    val rawX = when (side) {
        GuideTipPlacementSide.START -> anchor.left - input.gapPx - cardWidth
        GuideTipPlacementSide.END -> anchor.right + input.gapPx
        GuideTipPlacementSide.ABOVE,
        GuideTipPlacementSide.BELOW -> anchor.center.x - cardWidth / 2f
    }
    val rawY = when (side) {
        GuideTipPlacementSide.ABOVE -> anchor.top - input.gapPx - cardHeight
        GuideTipPlacementSide.BELOW -> anchor.bottom + input.gapPx
        GuideTipPlacementSide.START,
        GuideTipPlacementSide.END -> anchor.center.y - cardHeight / 2f
    }

    val x = if (side == GuideTipPlacementSide.ABOVE || side == GuideTipPlacementSide.BELOW) {
        rawX.coerceIn(margin, containerWidth - margin - cardWidth)
    } else {
        rawX
    }
    val y = if (side == GuideTipPlacementSide.START || side == GuideTipPlacementSide.END) {
        rawY.coerceIn(margin, containerHeight - margin - cardHeight)
    } else {
        rawY
    }

    val cardRect = Rect(x, y, x + cardWidth, y + cardHeight)
    val safeRect = Rect(margin, margin, containerWidth - margin, containerHeight - margin)
    if (!safeRect.containsRect(cardRect) || cardRect.overlapsRect(anchor)) {
        return null
    }
    if (input.exclusionZones.any { zone -> cardRect.overlapsRect(zone) }) {
        return null
    }

    return GuideTipPlacementResult.Placed(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        side = side
    )
}

private fun Rect.intersectsContainer(containerSize: IntSize): Boolean =
    right > 0f && bottom > 0f && left < containerSize.width && top < containerSize.height

private fun Rect.containsRect(other: Rect): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun Rect.overlapsRect(other: Rect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top



