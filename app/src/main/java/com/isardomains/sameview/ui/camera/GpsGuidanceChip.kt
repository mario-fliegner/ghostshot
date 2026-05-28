package com.isardomains.sameview.ui.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewOverlayScrim
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import com.isardomains.sameview.ui.theme.SameViewTextSecondary

private val GpsChipShape = RoundedCornerShape(20.dp)
private val GpsChipGreenColor = Color(0xFF4CAF50)
private val GpsChipOrangeColor = Color(0xFFFF9800)
private val GpsChipRedColor = Color(0xFFF44336)

@Composable
internal fun GpsGuidanceChip(
    state: GpsGuidanceState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is GpsGuidanceState.Hidden,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        // contentKey = class reference: animates only when type changes (Hidden↔Neutral↔Informative),
        // not when Informative data fields update (distance, bearing). Prevents per-GPS-update fade.
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "gps_chip_state"
        ) { currentState ->
            when (currentState) {
                is GpsGuidanceState.Hidden -> Box(Modifier.size(1.dp))
                is GpsGuidanceState.Neutral -> GpsChipNeutralContent()
                is GpsGuidanceState.Informative -> GpsChipInformativeContent(currentState)
            }
        }
    }
}

@Composable
private fun GpsChipNeutralContent() {
    val description = stringResource(R.string.gps_guidance_chip_description)
    Box(
        modifier = Modifier
            .testTag("gps_guidance_chip")
            .semantics { contentDescription = description }
            .background(SameViewOverlayScrim, GpsChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            drawDirectionChevron(color = SameViewTextSecondary.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun GpsChipInformativeContent(state: GpsGuidanceState.Informative) {
    val description = stringResource(R.string.gps_guidance_chip_description)
    val accentColor = state.proximityColor.toColor()
    Row(
        modifier = Modifier
            .testTag("gps_guidance_chip")
            .semantics { contentDescription = description }
            .background(SameViewOverlayScrim, GpsChipShape)
            .then(
                if (accentColor != null) {
                    Modifier.border(width = 1.dp, color = accentColor.copy(alpha = 0.8f), shape = GpsChipShape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.bearingDegrees != null) {
            val bearing = state.bearingDegrees
            Canvas(modifier = Modifier.size(14.dp)) {
                rotate(degrees = bearing, pivot = center) {
                    drawDirectionChevron(color = SameViewTextPrimary)
                }
            }
        }
        Text(
            text = GuidanceComputer.formatDistance(state.distanceMeters),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor ?: SameViewTextPrimary
        )
    }
}

private fun ProximityColor.toColor(): Color? = when (this) {
    ProximityColor.GREEN -> GpsChipGreenColor
    ProximityColor.ORANGE -> GpsChipOrangeColor
    ProximityColor.RED -> GpsChipRedColor
    ProximityColor.NEUTRAL -> null
}

private fun DrawScope.drawDirectionChevron(color: Color) {
    val cx = size.width / 2f
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(cx, 0f)
        lineTo(cx + w * 0.38f, h * 0.88f)
        lineTo(cx + w * 0.13f, h * 0.66f)
        lineTo(cx, h * 0.78f)
        lineTo(cx - w * 0.13f, h * 0.66f)
        lineTo(cx - w * 0.38f, h * 0.88f)
        close()
    }
    drawPath(path = path, color = color)
}
