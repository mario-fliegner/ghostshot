package com.isardomains.sameview.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewAppSurface
import com.isardomains.sameview.ui.theme.SameViewTextSecondary
import kotlin.math.min

@Composable
fun GuideTipHost(
    activeTip: GuideTip?,
    anchors: List<GuideTipAnchor>,
    windowWidthSizeClass: WindowWidthSizeClass,
    onGotIt: (GuideTip) -> Unit,
    onLearnMore: (GuideTip, GuideTopicId) -> Unit,
    isLandscape: Boolean = false,
    exclusionZones: List<Rect> = emptyList(),
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(
        modifier = modifier
            .fillMaxSize()
            .testTag("guide_tip_host")
    ) { constraints ->
        val tip = activeTip
        val anchor = tip?.let { currentTip ->
            anchors.firstOrNull { anchor -> anchor.key == currentTip.anchorKey && anchor.isUsable }
        }
        val marginPx = 16.dp.roundToPx().toFloat()
        val gapPx = 8.dp.roundToPx().toFloat()
        val maxCardWidth = min(280.dp.roundToPx(), constraints.maxWidth - (marginPx * 2).toInt())

        if (tip == null || anchor == null || maxCardWidth <= 0) {
            return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        val measureConstraints = Constraints(
            minWidth = 0,
            maxWidth = maxCardWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )
        val measuringPlaceables = subcompose("guide_tip_measure") {
            GuideTipCard(
                tip = tip,
                placementSide = GuideTipPlacementSide.BELOW,
                onGotIt = { onGotIt(tip) },
                onLearnMore = { topicId -> onLearnMore(tip, topicId) },
                isMeasurement = true
            )
        }.map { measurable -> measurable.measure(measureConstraints) }

        val cardWidth = measuringPlaceables.maxOfOrNull { it.width } ?: 0
        val cardHeight = measuringPlaceables.maxOfOrNull { it.height } ?: 0
        val placement = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                cardSize = IntSize(cardWidth, cardHeight),
                anchorBounds = anchor.bounds,
                windowWidthSizeClass = windowWidthSizeClass,
                marginPx = marginPx,
                gapPx = gapPx,
                isLandscape = isLandscape,
                exclusionZones = exclusionZones
            )
        )

        val placedPlaceables = if (placement is GuideTipPlacementResult.Placed) {
            subcompose("guide_tip_${placement.side}") {
                GuideTipCard(
                    tip = tip,
                    placementSide = placement.side,
                    onGotIt = { onGotIt(tip) },
                    onLearnMore = { topicId -> onLearnMore(tip, topicId) }
                )
            }.map { measurable -> measurable.measure(measureConstraints) }
        } else {
            emptyList()
        }

        var pointerOffsetX = 0
        var pointerOffsetY = 0
        val pointerPlaceables = if (placement is GuideTipPlacementResult.Placed) {
            val isVertical = placement.side == GuideTipPlacementSide.ABOVE ||
                    placement.side == GuideTipPlacementSide.BELOW
            val pointerWidthPx = if (isVertical) 14.dp.roundToPx() else 8.dp.roundToPx()
            val pointerHeightPx = if (isVertical) 8.dp.roundToPx() else 14.dp.roundToPx()
            val cardX = placement.offset.x
            val cardY = placement.offset.y
            val cardCenterX = cardX + cardWidth / 2
            val cardCenterY = cardY + cardHeight / 2
            pointerOffsetX = when (placement.side) {
                GuideTipPlacementSide.ABOVE,
                GuideTipPlacementSide.BELOW -> cardCenterX - pointerWidthPx / 2
                GuideTipPlacementSide.START -> cardX + cardWidth
                GuideTipPlacementSide.END -> cardX - pointerWidthPx
            }
            pointerOffsetY = when (placement.side) {
                GuideTipPlacementSide.ABOVE -> cardY + cardHeight
                GuideTipPlacementSide.BELOW -> cardY - pointerHeightPx
                GuideTipPlacementSide.START,
                GuideTipPlacementSide.END -> cardCenterY - pointerHeightPx / 2
            }
            subcompose("guide_tip_pointer") {
                GuideTipPointer(side = placement.side)
            }.map {
                it.measure(
                    Constraints(
                        minWidth = pointerWidthPx,
                        maxWidth = pointerWidthPx,
                        minHeight = pointerHeightPx,
                        maxHeight = pointerHeightPx
                    )
                )
            }
        } else {
            emptyList()
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placement is GuideTipPlacementResult.Placed) {
                placedPlaceables.forEach { it.place(placement.offset.x, placement.offset.y) }
                pointerPlaceables.forEach { it.place(pointerOffsetX, pointerOffsetY) }
            }
        }
    }
}

@Composable
private fun GuideTipCard(
    tip: GuideTip,
    placementSide: GuideTipPlacementSide,
    onGotIt: () -> Unit,
    onLearnMore: (GuideTopicId) -> Unit,
    isMeasurement: Boolean = false
) {
    Card(
        modifier = if (isMeasurement) {
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
        } else {
            Modifier
                .fillMaxWidth()
                .testTag("guide_tip_card")
        },
        colors = CardDefaults.cardColors(containerColor = SameViewAppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isMeasurement) {
                Box(modifier = Modifier.testTag("guide_tip_placement_${placementSide.name.lowercase()}"))
            }
            Text(
                text = stringResource(tip.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_title")
            )
            Text(
                text = stringResource(tip.bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = SameViewTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_body")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (tip.topicId != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tip.topicId != null) {
                    TextButton(
                        onClick = { tip.topicId?.let(onLearnMore) },
                        modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_learn_more")
                    ) {
                        Text(stringResource(R.string.guide_tip_learn_more))
                    }
                }
                TextButton(
                    onClick = onGotIt,
                    modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_dismiss")
                ) {
                    Text(stringResource(R.string.guide_tip_dismiss))
                }
            }
        }
    }
}

/** Draws a filled directional triangle in [SameViewAppSurface], pointing toward the anchor. */
@Composable
private fun GuideTipPointer(side: GuideTipPlacementSide) {
    val color = SameViewAppSurface
    Canvas(modifier = Modifier.testTag("guide_tip_pointer")) {
        val path = Path()
        when (side) {
            GuideTipPlacementSide.ABOVE -> {
                // Card above anchor: apex points downward toward anchor.
                path.moveTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(size.width / 2f, size.height)
                path.close()
            }
            GuideTipPlacementSide.BELOW -> {
                // Card below anchor: apex points upward toward anchor.
                path.moveTo(0f, size.height)
                path.lineTo(size.width, size.height)
                path.lineTo(size.width / 2f, 0f)
                path.close()
            }
            GuideTipPlacementSide.START -> {
                // Card to the left of anchor: apex points rightward toward anchor.
                path.moveTo(0f, 0f)
                path.lineTo(0f, size.height)
                path.lineTo(size.width, size.height / 2f)
                path.close()
            }
            GuideTipPlacementSide.END -> {
                // Card to the right of anchor: apex points leftward toward anchor.
                path.moveTo(size.width, 0f)
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height / 2f)
                path.close()
            }
        }
        drawPath(path, color)
    }
}
