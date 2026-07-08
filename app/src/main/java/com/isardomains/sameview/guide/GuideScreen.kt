package com.isardomains.sameview.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewAboutActionText
import com.isardomains.sameview.ui.theme.SameViewAboutBodyText
import com.isardomains.sameview.ui.theme.SameViewAboutCardSurface
import com.isardomains.sameview.ui.theme.SameViewAboutIconSurface
import com.isardomains.sameview.ui.theme.SameViewAboutTitleText
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewAppDivider
import com.isardomains.sameview.ui.theme.SameViewSettingsCardSurface
import com.isardomains.sameview.ui.theme.SameViewTextSecondary

private val GUIDE_MAX_CONTENT_WIDTH = 680.dp

@Composable
fun GuideRoute(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onOpenTopic: (GuideTopicId) -> Unit,
    onShowWalkthroughAgain: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GuideScreen(
        windowWidthSizeClass = windowWidthSizeClass,
        showResetTipsConfirmation = uiState.showResetTipsConfirmation,
        onBack = onBack,
        onOpenTopic = onOpenTopic,
        onShowTipsAgain = viewModel::onShowTipsAgainClick,
        onDismissResetTips = viewModel::onResetTipsDismissed,
        onConfirmResetTips = viewModel::onResetTipsConfirmed,
        onShowWalkthroughAgain = onShowWalkthroughAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    showResetTipsConfirmation: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (GuideTopicId) -> Unit,
    onShowTipsAgain: () -> Unit,
    onDismissResetTips: () -> Unit,
    onConfirmResetTips: () -> Unit,
    onShowWalkthroughAgain: () -> Unit
) {
    if (showResetTipsConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissResetTips,
            title = { Text(stringResource(R.string.guide_show_tips_again_dialog_title)) },
            text = { Text(stringResource(R.string.guide_show_tips_again_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = onConfirmResetTips,
                    modifier = Modifier.testTag("guide_show_tips_confirm")
                ) {
                    Text(stringResource(R.string.guide_show_tips_again_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissResetTips,
                    modifier = Modifier.testTag("guide_show_tips_cancel")
                ) {
                    Text(stringResource(R.string.guide_show_tips_again_cancel))
                }
            },
            modifier = Modifier.testTag("guide_show_tips_dialog")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("guide_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.guide_back)
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("guide_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag("guide_topic_list"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
                            Modifier.widthIn(max = GUIDE_MAX_CONTENT_WIDTH)
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val gettingStarted = GuideTopicRegistry.topicFor(GuideTopicId.GETTING_STARTED)
                if (gettingStarted != null) {
                    GuideGettingStartedHeroCard(
                        topic = gettingStarted,
                        onClick = { onOpenTopic(gettingStarted.id) }
                    )
                }
                GuideTopicRegistry.topics
                    .filter { it.id != GuideTopicId.GETTING_STARTED }
                    .forEach { topic ->
                        GuideTopicRow(
                            topic = topic,
                            onClick = { onOpenTopic(topic.id) }
                        )
                    }
                GuideBottomActions(
                    onShowTipsAgain = onShowTipsAgain,
                    onShowWalkthroughAgain = onShowWalkthroughAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("guide_bottom_actions")
                )
            }
        }
    }
}

/**
 * Getting started is not a normal feature topic - it is the Guide's orientation and
 * walkthrough-replay entry point, so it is rendered as a true centered hero (icon, title,
 * subtitle, workflow glyph row) rather than a topic row, while still reusing existing
 * SameView theme tokens and the AboutScreen hero-card visual language rather than
 * introducing a new card system. The 0.5dp accent border reuses the exact treatment
 * already used by the Guide Tip card (see GuideTipHost.kt).
 */
@Composable
private fun GuideGettingStartedHeroCard(
    topic: GuideTopic,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guide_topic_${topic.id.storedValue}"),
        shape = MaterialTheme.shapes.medium,
        color = SameViewAboutCardSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, SameViewAccent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SameViewAboutIconSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SameViewAboutActionText
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(topic.id.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = SameViewAboutTitleText,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(topic.id.summaryRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = SameViewAboutBodyText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideGettingStartedWorkflowGlyphRow()
        }
    }
}

/**
 * Subtle, unlabeled workflow glyph row (reference photo -> align -> compare) reinforcing
 * the hero subtitle. Decorative only - contentDescription is null on every icon because the
 * subtitle text already conveys the same three steps for TalkBack.
 */
@Composable
private fun GuideGettingStartedWorkflowGlyphRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = SameViewTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SameViewTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = Icons.Default.FilterCenterFocus,
            contentDescription = null,
            tint = SameViewTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SameViewTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            tint = SameViewTextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Standard Guide topic row, reusing the SettingsCard family's surface/shape/elevation conventions. */
@Composable
private fun GuideTopicRow(
    topic: GuideTopic,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guide_topic_${topic.id.storedValue}"),
        shape = MaterialTheme.shapes.medium,
        color = SameViewSettingsCardSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = topic.id.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(topic.id.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(topic.id.summaryRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuideBottomActions(
    onShowTipsAgain: () -> Unit,
    onShowWalkthroughAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = SameViewAppDivider)
        Text(
            text = stringResource(R.string.guide_replay_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedButton(
            onClick = onShowTipsAgain,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("guide_show_tips_again")
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.guide_show_tips_again))
        }
        OutlinedButton(
            onClick = onShowWalkthroughAgain,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("guide_show_walkthrough_again")
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.guide_show_walkthrough_again))
        }
    }
}

private fun GuideTopicId.icon(): ImageVector = when (this) {
    GuideTopicId.GETTING_STARTED -> Icons.Default.Info
    GuideTopicId.REFERENCE_PHOTOS -> Icons.Default.Image
    GuideTopicId.GPS_GUIDANCE -> Icons.Default.LocationOn
    GuideTopicId.EXPORT -> Icons.Default.Share
}

@StringRes
fun GuideTopicId.titleRes(): Int = when (this) {
    GuideTopicId.GETTING_STARTED -> R.string.guide_topic_getting_started_title
    GuideTopicId.REFERENCE_PHOTOS -> R.string.guide_topic_reference_photos_title
    GuideTopicId.GPS_GUIDANCE -> R.string.guide_topic_gps_guidance_title
    GuideTopicId.EXPORT -> R.string.guide_topic_export_title
}

@StringRes
fun GuideTopicId.summaryRes(): Int = when (this) {
    GuideTopicId.GETTING_STARTED -> R.string.guide_topic_getting_started_summary
    GuideTopicId.REFERENCE_PHOTOS -> R.string.guide_topic_reference_photos_summary
    GuideTopicId.GPS_GUIDANCE -> R.string.guide_topic_gps_guidance_summary
    GuideTopicId.EXPORT -> R.string.guide_topic_export_summary
}
