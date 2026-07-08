package com.isardomains.sameview.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(
    topicId: GuideTopicId,
    windowWidthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit
) {
    val content = topicId.detailContent()
    val maxWidth = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 760.dp else 620.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(topicId.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("guide_detail_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.guide_back)
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("guide_detail_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(topicId.titleRes()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("guide_detail_title")
                )
                Text(
                    text = stringResource(content.introRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("guide_detail_intro")
                )
                content.sections.forEachIndexed { index, section ->
                    GuideDetailVisual(
                        label = stringResource(section.visualLabelRes),
                        modifier = Modifier.testTag("guide_detail_visual_$index")
                    )
                    Text(
                        text = stringResource(section.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("guide_detail_body_$index")
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideDetailVisual(
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(172.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class GuideDetailContent(
    @param:StringRes val introRes: Int,
    val sections: List<GuideDetailSection>
)

private data class GuideDetailSection(
    @param:StringRes val visualLabelRes: Int,
    @param:StringRes val bodyRes: Int
)

private fun GuideTopicId.detailContent(): GuideDetailContent = when (this) {
    GuideTopicId.GETTING_STARTED -> GuideDetailContent(
        introRes = R.string.guide_detail_getting_started_intro,
        sections = listOf(
            GuideDetailSection(R.string.guide_detail_visual_camera, R.string.guide_detail_getting_started_body_1),
            GuideDetailSection(R.string.guide_detail_visual_compare, R.string.guide_detail_getting_started_body_2)
        )
    )
    GuideTopicId.REFERENCE_PHOTOS -> GuideDetailContent(
        introRes = R.string.guide_detail_reference_photos_intro,
        sections = listOf(
            GuideDetailSection(R.string.guide_detail_visual_reference, R.string.guide_detail_reference_photos_body_1),
            GuideDetailSection(R.string.guide_detail_visual_alignment, R.string.guide_detail_reference_photos_body_2),
            GuideDetailSection(R.string.guide_detail_visual_markers, R.string.guide_detail_markers_body_1)
        )
    )
    GuideTopicId.GPS_GUIDANCE -> GuideDetailContent(
        introRes = R.string.guide_detail_gps_intro,
        sections = listOf(
            GuideDetailSection(R.string.guide_detail_visual_gps, R.string.guide_detail_gps_body_1),
            GuideDetailSection(R.string.guide_detail_visual_camera, R.string.guide_detail_gps_body_2)
        )
    )
    GuideTopicId.COMPARE -> GuideDetailContent(
        introRes = R.string.guide_detail_compare_intro,
        sections = listOf(
            GuideDetailSection(R.string.guide_detail_visual_compare, R.string.guide_detail_compare_body_1),
            GuideDetailSection(R.string.guide_detail_visual_compare_tools, R.string.guide_detail_compare_body_2)
        )
    )
    GuideTopicId.EXPORT -> GuideDetailContent(
        introRes = R.string.guide_detail_export_intro,
        sections = listOf(
            GuideDetailSection(R.string.guide_detail_visual_share, R.string.guide_detail_share_body_1),
            GuideDetailSection(R.string.guide_detail_visual_video, R.string.guide_detail_video_body_1),
            GuideDetailSection(R.string.guide_detail_visual_backup, R.string.guide_detail_backups_body_1)
        )
    )
}

