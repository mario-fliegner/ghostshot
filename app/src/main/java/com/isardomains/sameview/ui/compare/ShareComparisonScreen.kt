// path: app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt
package com.isardomains.sameview.ui.compare

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.isardomains.sameview.ui.branding.BrandingPreviewCircle
import com.isardomains.sameview.ui.branding.BrandingSymbolPickerSheet
import com.isardomains.sameview.ui.settings.SameViewSegmentControl
import com.isardomains.sameview.ui.settings.SameViewSegmentItem
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.settings.SettingsSwitchRow
import com.isardomains.sameview.ui.theme.SameViewAccent
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
    val titleDateEnabled by viewModel.titleDateEnabled.collectAsStateWithLifecycle()
    val locationEnabled by viewModel.locationEnabled.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val sessionViewportRatio by viewModel.sessionViewportRatio.collectAsStateWithLifecycle()
    val hasBranding by viewModel.hasBranding.collectAsStateWithLifecycle()
    val useBranding by viewModel.useBranding.collectAsStateWithLifecycle()
    val brandingVersion by viewModel.brandingVersion.collectAsStateWithLifecycle()

    val isTitleDateAvailable by viewModel.isTitleDateAvailable.collectAsStateWithLifecycle()
    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()

    val titleDatePreviewText by viewModel.titleDatePreviewText.collectAsStateWithLifecycle()
    val locationPreviewText by viewModel.locationPreviewText.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val brandingErrorMessage = stringResource(R.string.share_comparison_logo_error)

    val sessionDir = remember(viewModel.sessionId) {
        File(context.filesDir, "sessions/${viewModel.sessionId}")
    }
    // brandingVersion is included so recomposition is forced after every successful file write,
    // allowing Coil to receive a new ImageRequest with a fresh memoryCacheKey.
    val sessionBrandingFile = remember(sessionDir, brandingVersion) {
        File(sessionDir, "branding-handle.png")
    }

    // ── Branding photo picker launcher ─────────────────────────────────────────
    val brandingImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImageUriSelectedForBranding(uri)
    }

    // ── Branding symbol picker sheet ───────────────────────────────────────────
    var showBrandingSymbolSheet by remember { mutableStateOf(false) }
    if (showBrandingSymbolSheet) {
        BrandingSymbolPickerSheet(
            onSymbolSelected = { symbol ->
                showBrandingSymbolSheet = false
                viewModel.onSetSessionBrandingFromSymbol(symbol)
            },
            onDismiss = { showBrandingSymbolSheet = false }
        )
    }

    // Caption data for live preview — mirrors what will be passed to the renderer
    val previewCaptionData = remember(
        titleDateEnabled, locationEnabled,
        isTitleDateAvailable, isLocationAvailable,
        titleDatePreviewText, locationPreviewText
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

    // Collect branding errors
    LaunchedEffect(Unit) {
        viewModel.brandingError.collect {
            snackbarHostState.showSnackbar(brandingErrorMessage)
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ShareComparisonPreview(
                        style = style,
                        captionData = previewCaptionData,
                        sessionDir = sessionDir,
                        viewportRatio = sessionViewportRatio,
                        useBranding = useBranding && hasBranding,
                        brandingVersion = brandingVersion
                    )
                }

                // ── Logo on handle card (V2 — Slider only) ───────────────────
                if (style == ShareComparisonStyle.SLIDER) {
                    SettingsCard(title = stringResource(R.string.share_comparison_logo_card_title)) {
                        if (!hasBranding) {
                            // ── EMPTY state ───────────────────────────────────
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF5F7FA))
                                        .border(2.dp, SameViewAccent, CircleShape)
                                        .testTag("share_comparison_logo_placeholder"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = SameViewSettingsSecondaryText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.share_comparison_logo_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = SameViewSettingsSecondaryText,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            if (viewModel.hasGlobalBranding) {
                                TextButton(
                                    onClick = { viewModel.onUseDefaultLogo() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("share_comparison_logo_use_default")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_use_default))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        brandingImageLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_comparison_logo_choose_photo")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_choose_photo))
                                }
                                TextButton(
                                    onClick = { showBrandingSymbolSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_comparison_logo_use_symbol")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_use_symbol))
                                }
                            }
                        } else {
                            // ── POPULATED state ───────────────────────────────
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(modifier = Modifier.alpha(if (useBranding) 1f else 0.4f)) {
                                    BrandingPreviewCircle(
                                        brandingFile = sessionBrandingFile,
                                        modifier = Modifier.testTag("share_comparison_logo_preview"),
                                        brandingVersion = brandingVersion
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    SettingsSwitchRow(
                                        label = stringResource(R.string.share_comparison_logo_show),
                                        checked = useBranding,
                                        onCheckedChange = { viewModel.onToggleUseBranding() },
                                        testTag = "share_comparison_toggle_logo"
                                    )
                                }
                            }
                            if (viewModel.hasGlobalBranding) {
                                TextButton(
                                    onClick = { viewModel.onUseDefaultLogo() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("share_comparison_logo_use_default")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_use_default))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        brandingImageLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_comparison_logo_choose_photo")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_choose_photo))
                                }
                                TextButton(
                                    onClick = { showBrandingSymbolSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_comparison_logo_use_symbol")
                                ) {
                                    Text(stringResource(R.string.share_comparison_logo_use_symbol))
                                }
                            }
                            TextButton(
                                onClick = { viewModel.onRemoveSessionBranding() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_comparison_logo_remove")
                            ) {
                                Text(stringResource(R.string.share_comparison_logo_remove))
                            }
                        }
                    }
                }

                // ── Extras card ───────────────────────────────────────────────
                SettingsCard(
                    title = stringResource(R.string.share_comparison_extras_label),
                ) {
                    InfoToggleRow(
                        label = stringResource(R.string.create_video_overlay_title_date_label),
                        checked = titleDateEnabled && isTitleDateAvailable,
                        enabled = isTitleDateAvailable,
                        onCheckedChange = viewModel::onTitleDateToggled,
                        previewText = titleDatePreviewText,
                        hintText = stringResource(R.string.create_video_overlay_no_data_hint),
                        testTag = "share_comparison_toggle_title_date"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoToggleRow(
                        label = stringResource(R.string.create_video_overlay_location_label),
                        checked = locationEnabled && isLocationAvailable,
                        enabled = isLocationAvailable,
                        onCheckedChange = viewModel::onLocationToggled,
                        previewText = locationPreviewText,
                        hintText = stringResource(R.string.create_video_overlay_location_no_data_hint),
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
