// path: app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt
package com.isardomains.sameview.ui.compare

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.ui.settings.SameViewSegmentControl
import com.isardomains.sameview.ui.settings.SameViewSegmentItem
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.settings.SettingsSwitchRow
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import java.io.File

/**
 * Fullscreen screen for configuring and triggering Share Comparison Image exports.
 *
 * Visually matches [com.isardomains.sameview.ui.video.CreateVideoScreen]: same card structure,
 * same segment controls, same spacing, same CTA pattern. No new design language.
 *
 * @param onBack Called when the user navigates back to CompareScreen.
 * @param viewModel Hilt ViewModel; owns all state and the share action.
 * @param windowWidthSizeClass Used for Expanded (≥ 840 dp) max-width constraint.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ShareComparisonScreen(
    onBack: () -> Unit,
    viewModel: ShareComparisonViewModel = hiltViewModel(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val style by viewModel.style.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val titleEnabled by viewModel.titleEnabled.collectAsStateWithLifecycle()
    val dateEnabled by viewModel.dateEnabled.collectAsStateWithLifecycle()
    val locationEnabled by viewModel.locationEnabled.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val sessionViewportRatio by viewModel.sessionViewportRatio.collectAsStateWithLifecycle()

    val isTitleAvailable by viewModel.isTitleAvailable.collectAsStateWithLifecycle()
    val isDateAvailable by viewModel.isDateAvailable.collectAsStateWithLifecycle()
    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()

    val titlePreviewText by viewModel.titlePreviewText.collectAsStateWithLifecycle()
    val datePreviewText by viewModel.datePreviewText.collectAsStateWithLifecycle()
    val locationPreviewText by viewModel.locationPreviewText.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionDir = remember(viewModel.sessionId) {
        File(context.filesDir, "sessions/${viewModel.sessionId}")
    }

    // Caption data for live preview — mirrors what will be passed to the renderer
    val previewCaptionData = remember(
        titleEnabled, dateEnabled, locationEnabled,
        isTitleAvailable, isDateAvailable, isLocationAvailable,
        titlePreviewText, datePreviewText, locationPreviewText
    ) {
        viewModel.buildCaptionData()
    }

    // Collect events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShareComparisonEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))

                is ShareComparisonEvent.ShareReady -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_comparison_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("share_comparison_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 680.dp else Dp.Unspecified)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Style card ────────────────────────────────────────────────
                val styles = listOf(ShareComparisonStyle.SLIDER, ShareComparisonStyle.SIDE_BY_SIDE)
                val styleItems = listOf(
                    SameViewSegmentItem(stringResource(R.string.share_comparison_style_slider)),
                    SameViewSegmentItem(stringResource(R.string.share_comparison_style_side_by_side))
                )
                SettingsCard(title = stringResource(R.string.share_comparison_style_label)) {
                    SameViewSegmentControl(
                        items = styleItems,
                        selectedIndex = styles.indexOf(style),
                        onItemSelected = { viewModel.onStyleChanged(styles[it]) },
                        modifier = Modifier.testTag("share_comparison_style_control")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ShareComparisonPreview(
                        style = style,
                        captionData = previewCaptionData,
                        sessionDir = sessionDir,
                        viewportRatio = sessionViewportRatio
                    )
                }

                // ── Information card ──────────────────────────────────────────
                SettingsCard(
                    title = stringResource(R.string.share_comparison_info_label),
                ) {
                    InfoToggleRow(
                        label = stringResource(R.string.share_comparison_toggle_title),
                        checked = titleEnabled && isTitleAvailable,
                        enabled = isTitleAvailable,
                        onCheckedChange = viewModel::onTitleToggled,
                        previewText = titlePreviewText,
                        hintText = stringResource(R.string.share_comparison_no_title_hint),
                        testTag = "share_comparison_toggle_title"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoToggleRow(
                        label = stringResource(R.string.share_comparison_toggle_date),
                        checked = dateEnabled && isDateAvailable,
                        enabled = isDateAvailable,
                        onCheckedChange = viewModel::onDateToggled,
                        previewText = datePreviewText,
                        hintText = stringResource(R.string.share_comparison_no_date_hint),
                        testTag = "share_comparison_toggle_date"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoToggleRow(
                        label = stringResource(R.string.share_comparison_toggle_location),
                        checked = locationEnabled && isLocationAvailable,
                        enabled = isLocationAvailable,
                        onCheckedChange = viewModel::onLocationToggled,
                        previewText = locationPreviewText,
                        hintText = stringResource(R.string.share_comparison_no_location_hint),
                        testTag = "share_comparison_toggle_location"
                    )
                }

                // ── Quality card ──────────────────────────────────────────────
                val qualities = listOf(ShareQuality.STANDARD, ShareQuality.ORIGINAL)
                val qualityItems = listOf(
                    SameViewSegmentItem(stringResource(R.string.share_comparison_quality_standard)),
                    SameViewSegmentItem(stringResource(R.string.share_comparison_quality_original))
                )
                SettingsCard(title = stringResource(R.string.share_comparison_quality_label)) {
                    SameViewSegmentControl(
                        items = qualityItems,
                        selectedIndex = qualities.indexOf(quality),
                        onItemSelected = { viewModel.onQualityChanged(qualities[it]) },
                        modifier = Modifier.testTag("share_comparison_quality_control")
                    )
                    if (quality == ShareQuality.ORIGINAL) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.share_comparison_quality_original_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = SameViewSettingsSecondaryText
                        )
                    }
                }

                // ── Share CTA ─────────────────────────────────────────────────
                Button(
                    onClick = viewModel::onShare,
                    enabled = !isRendering,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .testTag("share_comparison_share_button")
                ) {
                    Text(stringResource(R.string.share_comparison_action_share))
                }
            }
        }
    }
}

// ── InfoToggleRow ───────────────────────────────────────────────────────────────

/**
 * A toggle row with a preview/hint text line below. Matches the CreateVideoScreen
 * OverlayToggleItem pattern without duplicating that private composable.
 */
@Composable
private fun InfoToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    previewText: String?,
    hintText: String,
    testTag: String? = null
) {
    Column {
        SettingsSwitchRow(
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            testTag = testTag
        )
        Text(
            text = if (enabled && previewText != null) previewText else hintText,
            style = MaterialTheme.typography.bodySmall,
            color = SameViewSettingsSecondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
