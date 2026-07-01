package com.isardomains.sameview.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.SubcomposeLayout
import com.isardomains.sameview.R
import kotlin.math.min

@Composable
fun GuideTipHost(
    activeTip: GuideTip?,
    anchors: List<GuideTipAnchor>,
    windowWidthSizeClass: WindowWidthSizeClass,
    onGotIt: (GuideTip) -> Unit,
    onLearnMore: (GuideTip, GuideTopicId) -> Unit,
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
        val maxCardWidth = min(320.dp.roundToPx(), constraints.maxWidth - (marginPx * 2).toInt())

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
                gapPx = gapPx
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

        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placement is GuideTipPlacementResult.Placed) {
                placedPlaceables.forEach { placeable ->
                    placeable.place(placement.offset.x, placement.offset.y)
                }
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
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isMeasurement) {
                    Box(modifier = Modifier.testTag("guide_tip_placement_${placementSide.name.lowercase()}"))
                }
                GuideTipPointer(placementSide, isMeasurement)
                Text(
                    text = stringResource(tip.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_title")
                )
                Text(
                    text = stringResource(tip.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_body")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { tip.topicId?.let(onLearnMore) },
                        modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_learn_more")
                    ) {
                        Text(stringResource(R.string.guide_tip_learn_more))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onGotIt,
                        modifier = if (isMeasurement) Modifier else Modifier.testTag("guide_tip_got_it")
                    ) {
                        Text(stringResource(R.string.guide_tip_got_it))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTipPointer(
    placementSide: GuideTipPlacementSide,
    isMeasurement: Boolean
) {
    val alignment = when (placementSide) {
        GuideTipPlacementSide.ABOVE -> Alignment.BottomCenter
        GuideTipPlacementSide.BELOW -> Alignment.TopCenter
        GuideTipPlacementSide.START -> Alignment.CenterEnd
        GuideTipPlacementSide.END -> Alignment.CenterStart
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = if (isMeasurement) {
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("guide_tip_pointer")
            }
        )
    }
}


