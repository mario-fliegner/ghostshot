package com.isardomains.sameview.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded || maxWidth >= 600.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("guide_topic_grid"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(GuideTopicRegistry.topics, key = { topic -> topic.id.storedValue }) { topic ->
                    GuideTopicRow(
                        topic = topic,
                        onClick = { onOpenTopic(topic.id) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
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
}

@Composable
private fun GuideTopicRow(
    topic: GuideTopic,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guide_topic_${topic.id.storedValue}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
        modifier = modifier.widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    GuideTopicId.MARKERS -> Icons.Outlined.RadioButtonChecked
    GuideTopicId.GPS_GUIDANCE -> Icons.Default.LocationOn
    GuideTopicId.COMPARE -> Icons.AutoMirrored.Filled.CompareArrows
    GuideTopicId.SHARE_COMPARISON_IMAGE -> Icons.Default.Share
    GuideTopicId.CREATE_VIDEO -> Icons.Default.VideoLibrary
    GuideTopicId.FAVORITES -> Icons.Default.Favorite
    GuideTopicId.BACKUPS -> Icons.Default.SaveAlt
}

@StringRes
fun GuideTopicId.titleRes(): Int = when (this) {
    GuideTopicId.GETTING_STARTED -> R.string.guide_topic_getting_started_title
    GuideTopicId.REFERENCE_PHOTOS -> R.string.guide_topic_reference_photos_title
    GuideTopicId.MARKERS -> R.string.guide_topic_markers_title
    GuideTopicId.GPS_GUIDANCE -> R.string.guide_topic_gps_guidance_title
    GuideTopicId.COMPARE -> R.string.guide_topic_compare_title
    GuideTopicId.SHARE_COMPARISON_IMAGE -> R.string.guide_topic_share_comparison_image_title
    GuideTopicId.CREATE_VIDEO -> R.string.guide_topic_create_video_title
    GuideTopicId.FAVORITES -> R.string.guide_topic_favorites_title
    GuideTopicId.BACKUPS -> R.string.guide_topic_backups_title
}

@StringRes
fun GuideTopicId.summaryRes(): Int = when (this) {
    GuideTopicId.GETTING_STARTED -> R.string.guide_topic_getting_started_summary
    GuideTopicId.REFERENCE_PHOTOS -> R.string.guide_topic_reference_photos_summary
    GuideTopicId.MARKERS -> R.string.guide_topic_markers_summary
    GuideTopicId.GPS_GUIDANCE -> R.string.guide_topic_gps_guidance_summary
    GuideTopicId.COMPARE -> R.string.guide_topic_compare_summary
    GuideTopicId.SHARE_COMPARISON_IMAGE -> R.string.guide_topic_share_comparison_image_summary
    GuideTopicId.CREATE_VIDEO -> R.string.guide_topic_create_video_summary
    GuideTopicId.FAVORITES -> R.string.guide_topic_favorites_summary
    GuideTopicId.BACKUPS -> R.string.guide_topic_backups_summary
}

