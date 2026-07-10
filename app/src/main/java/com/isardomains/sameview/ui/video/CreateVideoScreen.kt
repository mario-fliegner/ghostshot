// path: app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt
package com.isardomains.sameview.ui.video

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SameViewSegmentControl
import com.isardomains.sameview.ui.settings.SameViewSegmentItem
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.settings.SettingsSwitchRow
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import com.isardomains.sameview.video.VideoExportFormat
import com.isardomains.sameview.video.VideoMode
import com.isardomains.sameview.video.VideoQuality
import java.io.File

/**
 * Fullscreen wizard screen for creating a video from a compare session.
 *
 * State flow: Configuring → Rendering → Preview.
 *
 * @param onBack called when the user navigates back (Configuring back or Preview Done/Back).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CreateVideoScreen(
    onBack: () -> Unit,
    viewModel: CreateVideoViewModel = hiltViewModel(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val sessionDir = remember { File(context.filesDir, "sessions/${viewModel.sessionId}") }
    val isOverlayAvailable by viewModel.isOverlayAvailable.collectAsStateWithLifecycle()
    val overlayPreviewText by viewModel.overlayPreviewText.collectAsStateWithLifecycle()
    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()
    val locationPreviewText by viewModel.locationPreviewText.collectAsStateWithLifecycle()
    val overlayTitleText by viewModel.overlayTitleText.collectAsStateWithLifecycle()
    val overlayDateText by viewModel.overlayDateText.collectAsStateWithLifecycle()
    val sessionViewportRatio by viewModel.sessionViewportRatio.collectAsStateWithLifecycle()

    // Cancel dialog state (visible when user presses Back during Rendering)
    var showCancelDialog by remember { mutableStateOf(false) }

    // Back from Preview is equivalent to Done — video stays saved, screen closes.
    BackHandler(enabled = state is CreateVideoState.Preview) {
        onBack()
    }

    // Back during Rendering shows confirmation dialog instead of immediate cancel.
    BackHandler(enabled = state is CreateVideoState.Rendering) {
        showCancelDialog = true
    }

    // Dismiss cancel dialog if state transitions away from Rendering (e.g. export finishes).
    LaunchedEffect(state) {
        if (state !is CreateVideoState.Rendering) {
            showCancelDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateVideoEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.create_video_cancel_export_dialog_title)) },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.create_video_cancel_export_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.cancelExport()
                }) {
                    Text(stringResource(R.string.create_video_cancel_export_stop))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            when (val s = state) {
                is CreateVideoState.Configuring -> TopAppBar(
                    title = { Text(stringResource(R.string.create_video_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
                is CreateVideoState.Rendering -> TopAppBar(
                    title = { Text(stringResource(R.string.create_video_rendering_title)) },
                    navigationIcon = {
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
                is CreateVideoState.Preview -> TopAppBar(
                    title = { Text(stringResource(R.string.create_video_preview_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when (val s = state) {
            is CreateVideoState.Configuring -> ConfiguringContent(
                state = s,
                sessionDir = sessionDir,
                onModeChange = viewModel::updateMode,
                onFormatChange = viewModel::updateFormat,
                onDurationChange = viewModel::updateDurationMs,
                onQualityChange = viewModel::updateQuality,
                onBrandingChange = viewModel::updateBrandingEnabled,
                onOverlayChange = viewModel::updateOverlayEnabled,
                onLocationChange = viewModel::updateLocationEnabled,
                isOverlayAvailable = isOverlayAvailable,
                overlayPreviewText = overlayPreviewText,
                isLocationAvailable = isLocationAvailable,
                locationPreviewText = locationPreviewText,
                overlayTitleText = overlayTitleText,
                overlayDateText = overlayDateText,
                onCreateVideo = viewModel::startExport,
                windowWidthSizeClass = windowWidthSizeClass,
                modifier = Modifier.padding(paddingValues)
            )
            is CreateVideoState.Rendering -> RenderingContent(
                state = s,
                progress = progress,
                sessionDir = sessionDir,
                sessionViewportRatio = sessionViewportRatio,
                overlayTitleText = overlayTitleText,
                overlayDateText = overlayDateText,
                locationPreviewText = locationPreviewText,
                isOverlayAvailable = isOverlayAvailable,
                isLocationAvailable = isLocationAvailable,
                modifier = Modifier.padding(paddingValues)
            )
            is CreateVideoState.Preview -> PreviewContent(
                state = s,
                onDelete = viewModel::deleteVideo,
                onDone = onBack,
                windowWidthSizeClass = windowWidthSizeClass,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// ── Configuring ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun ConfiguringContent(
    state: CreateVideoState.Configuring,
    sessionDir: File,
    onModeChange: (VideoMode) -> Unit,
    onFormatChange: (VideoExportFormat) -> Unit,
    onDurationChange: (Int) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onBrandingChange: (Boolean) -> Unit,
    onOverlayChange: (Boolean) -> Unit,
    onLocationChange: (Boolean) -> Unit,
    isOverlayAvailable: Boolean,
    overlayPreviewText: String?,
    isLocationAvailable: Boolean,
    locationPreviewText: String?,
    overlayTitleText: String?,
    overlayDateText: String?,
    onCreateVideo: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
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
        // ── Mode ──────────────────────────────────────────────────────────
        val modes = listOf(VideoMode.COMPARE_SLIDER, VideoMode.BEFORE_AFTER, VideoMode.FLASH)
        val modeItems = listOf(
            SameViewSegmentItem(stringResource(R.string.create_video_mode_compare_slider)),
            SameViewSegmentItem(stringResource(R.string.create_video_mode_before_after)),
            SameViewSegmentItem(stringResource(R.string.create_video_mode_flash))
        )
        SettingsCard(title = stringResource(R.string.create_video_mode_label)) {
            SameViewSegmentControl(
                items = modeItems,
                selectedIndex = modes.indexOf(state.mode),
                onItemSelected = { onModeChange(modes[it]) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val previewLines = buildList {
                if (state.overlayEnabled && isOverlayAvailable) {
                    overlayTitleText?.let { add(it) }
                    overlayDateText?.let { add(it) }
                }
                if (state.locationEnabled && isLocationAvailable) {
                    locationPreviewText?.let { add(it) }
                }
            }
            VideoModePreview(
                mode = state.mode,
                sessionDir = sessionDir,
                previewLines = previewLines
            )
        }

        // ── Extras ───────────────────────────────────────────────────────
        SettingsCard(title = stringResource(R.string.create_video_extras_section_title)) {
            // 1. Show title and date
            OverlayToggleItem(
                label = stringResource(R.string.create_video_overlay_title_date_label),
                checked = state.overlayEnabled && isOverlayAvailable,
                enabled = isOverlayAvailable,
                onCheckedChange = onOverlayChange,
                previewText = overlayPreviewText,
                hintText = stringResource(R.string.create_video_overlay_no_data_hint)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // 2. Show location
            OverlayToggleItem(
                label = stringResource(R.string.create_video_overlay_location_label),
                checked = state.locationEnabled && isLocationAvailable,
                enabled = isLocationAvailable,
                onCheckedChange = onLocationChange,
                previewText = locationPreviewText,
                hintText = stringResource(R.string.create_video_overlay_location_no_data_hint)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // 3. Add #MadeWithSameView card
            SettingsSwitchRow(
                label = stringResource(R.string.create_video_branding_label),
                checked = state.brandingEnabled,
                onCheckedChange = onBrandingChange
            )
        }

        // ── Format ────────────────────────────────────────────────────────
        val formats = listOf(
            VideoExportFormat.ORIGINAL,
            VideoExportFormat.PORTRAIT_9_16,
            VideoExportFormat.LANDSCAPE_16_9
        )
        val formatItems = listOf(
            SameViewSegmentItem(stringResource(R.string.create_video_format_original)),
            SameViewSegmentItem(stringResource(R.string.create_video_format_portrait)),
            SameViewSegmentItem(stringResource(R.string.create_video_format_landscape))
        )
        SettingsCard(title = stringResource(R.string.create_video_format_label)) {
            SameViewSegmentControl(
                items = formatItems,
                selectedIndex = formats.indexOf(state.format),
                onItemSelected = { onFormatChange(formats[it]) }
            )
        }

        // ── Duration ──────────────────────────────────────────────────────
        val durations = listOf(4000, 6000, 8000)
        val durationItems = listOf(
            SameViewSegmentItem(stringResource(R.string.create_video_duration_short)),
            SameViewSegmentItem(stringResource(R.string.create_video_duration_medium)),
            SameViewSegmentItem(stringResource(R.string.create_video_duration_long))
        )
        SettingsCard(title = stringResource(R.string.create_video_duration_label)) {
            SameViewSegmentControl(
                items = durationItems,
                selectedIndex = durations.indexOf(state.durationMs),
                onItemSelected = { onDurationChange(durations[it]) }
            )
        }

        // ── Quality ───────────────────────────────────────────────────────
        val qualities = listOf(VideoQuality.STANDARD_1080P, VideoQuality.HIGH_QUALITY)
        val qualityItems = listOf(
            SameViewSegmentItem(stringResource(R.string.create_video_quality_standard)),
            SameViewSegmentItem(stringResource(R.string.create_video_quality_high))
        )
        SettingsCard(title = stringResource(R.string.create_video_quality_label)) {
            SameViewSegmentControl(
                items = qualityItems,
                selectedIndex = qualities.indexOf(state.quality),
                onItemSelected = { onQualityChange(qualities[it]) }
            )
            // Note is shown only when High Quality is selected, associating it
            // visually with that specific option as specified in VIDEO_EXPORT_V1.md §10.2.
            if (state.quality == VideoQuality.HIGH_QUALITY) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_video_quality_high_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = SameViewSettingsSecondaryText
                )
            }
        }

        // ── Create Video CTA ──────────────────────────────────────────────
        Button(
            onClick = onCreateVideo,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.create_video_action_create))
        }
        } // inner Column
    } // outer Column
}

// ── Rendering ─────────────────────────────────────────────────────────────────

@Composable
private fun RenderingContent(
    state: CreateVideoState.Rendering,
    progress: Float,
    sessionDir: File,
    sessionViewportRatio: Float,
    overlayTitleText: String?,
    overlayDateText: String?,
    locationPreviewText: String?,
    isOverlayAvailable: Boolean,
    isLocationAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    // BoxWithConstraints provides bounded maxWidth/maxHeight so card dimensions
    // can be computed as a fraction of the available Scaffold content area.
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        val safeViewportRatio = sessionViewportRatio.takeIf { it > 0f } ?: (4f / 3f)
        val cardAspectRatioW2H = when (state.format) {
            VideoExportFormat.PORTRAIT_9_16 -> 9f / 16f
            VideoExportFormat.LANDSCAPE_16_9 -> 16f / 9f
            VideoExportFormat.ORIGINAL -> safeViewportRatio
        }
        val horizontalPadding = 16.dp
        val cardContainerWidth = maxWidth - horizontalPadding * 2
        // Cap card height at 62 % of the available content area so progress remains visible.
        val maxCardHeight = maxHeight * 0.62f
        val cardHeightFromWidth = cardContainerWidth / cardAspectRatioW2H
        val effectiveCardHeight = cardHeightFromWidth.coerceAtMost(maxCardHeight)
        val effectiveCardWidth = effectiveCardHeight * cardAspectRatioW2H

        val previewLines = buildList {
            if (state.overlayEnabled && isOverlayAvailable) {
                overlayTitleText?.let { add(it) }
                overlayDateText?.let { add(it) }
            }
            if (state.locationEnabled && isLocationAvailable) {
                locationPreviewText?.let { add(it) }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        ) {
            VideoLoadingPreview(
                mode = state.mode,
                sessionDir = sessionDir,
                previewLines = previewLines,
                modifier = Modifier.size(
                    width = effectiveCardWidth,
                    height = effectiveCardHeight
                )
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.create_video_rendering_status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.create_video_rendering_progress,
                    state.currentFrame,
                    state.totalFrames
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

/**
 * Preview state: auto-playing, looping, muted Media3 ExoPlayer with Share/Delete/Done actions.
 *
 * The player area is a `weight(1f)` region measured *after* the Actions column has already
 * claimed its true natural height (Actions remain a non-weighted sibling, unchanged) — so the
 * area this composable has to work with is already a safe, exact remainder, never an estimate.
 * Within that safe remainder, a format-correct player card is sized (same 62%-height-cap
 * principle as [RenderingContent]'s loading card) and centered, rather than stretching the
 * player to fill the whole remainder — this aligns the finished-preview layout language with
 * the rendering-preview layout language.
 *
 * Player lifecycle is tied to composition via [DisposableEffect] — released on exit.
 * Rotation is safe: [state.videoUri] lives in ViewModel and survives configuration change.
 *
 * Internal (not private) so [CreateVideoScreenTest] can compose it directly without driving
 * the full Hilt-backed [CreateVideoViewModel] state machine into the Preview state.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PreviewContent(
    state: CreateVideoState.Preview,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.create_video_delete_dialog_title)) },
            text = { Text(stringResource(R.string.create_video_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.create_video_delete_dialog_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.create_video_delete_dialog_confirm))
                }
            }
        )
    }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    // Real aspect ratio of the exported MP4, read from the player once its container
    // metadata is parsed (see the DisposableEffect below). A neutral fallback is used
    // until then; a single layout reflow is accepted once the real value is known.
    var videoAspectRatio by remember { mutableStateOf(4f / 3f) }

    LaunchedEffect(state.videoUri) {
        player.setMediaItem(MediaItem.fromUri(state.videoUri))
        player.prepare()
        player.playWhenReady = true
        player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
        player.volume = 0f
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Separate from the release effect above (left untouched) so listener cleanup always
    // runs before release, regardless of effect ordering.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rotated = videoSize.unappliedRotationDegrees == 90 ||
                        videoSize.unappliedRotationDegrees == 270
                    val width = if (rotated) videoSize.height else videoSize.width
                    val height = if (rotated) videoSize.width else videoSize.height
                    videoAspectRatio = width.toFloat() / height.toFloat()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
                    Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxHeight()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                }
            ) {
                // Player area: weighted, so Actions below still get their true natural
                // height first (unchanged). The player itself no longer stretches to fill
                // this area — a format-correct card is centered within it instead.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("create_video_preview_player_area"),
                    contentAlignment = Alignment.Center
                ) {
                    // BoxWithConstraints reads the true, already-safe remaining area — Actions
                    // were already reserved by the Column's weight mechanism above, so no
                    // Actions height is estimated or subtracted here.
                    BoxWithConstraints {
                        // Same visual height-cap principle as RenderingContent's loading card
                        // (§7.4 mode preview / §7.5 alignment), calibrated differently: this
                        // maxHeight is already the safe remainder after Actions' true height
                        // was reserved by the weight(1f) mechanism above, whereas Rendering's
                        // 62% applies to the full content area. Applying 62% again here would
                        // shrink the card a second time — 90% is the correct calibration for
                        // this already-reduced base. Purely a visual size limit for centering,
                        // not a stand-in for a measured Actions height.
                        val maxCardHeight = maxHeight * 0.90f
                        val cardHeightFromWidth = maxWidth / videoAspectRatio
                        val effectiveCardHeight = cardHeightFromWidth.coerceAtMost(maxCardHeight)
                        val effectiveCardWidth = (effectiveCardHeight * videoAspectRatio)
                            .coerceAtMost(maxWidth)

                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    // Explicit FIT so no future default change can silently crop.
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            update = { playerView ->
                                playerView.player = player
                            },
                            modifier = Modifier
                                .size(width = effectiveCardWidth, height = effectiveCardHeight)
                                .testTag("create_video_preview_player_card")
                        )
                    }
                }

                // Action buttons at the bottom — structurally unchanged.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary action — same visual style as Create Video button
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, state.videoUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(shareIntent, null)) }
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_video_action_share")
                    ) {
                        Text(stringResource(R.string.create_video_action_share))
                    }
                    // Secondary action — full-width outlined, clearly below Share in hierarchy
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_video_action_done")
                    ) {
                        Text(stringResource(R.string.create_video_action_done))
                    }
                    // Destructive / tertiary — text-only, right-aligned; tap opens confirmation dialog
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("create_video_action_delete")
                        ) {
                            Text(stringResource(R.string.create_video_action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayToggleItem(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    previewText: String?,
    hintText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable { onCheckedChange(!checked) }
                else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) SameViewSettingsLabelText
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
    }
    Text(
        text = previewText ?: hintText,
        style = MaterialTheme.typography.bodySmall,
        color = SameViewSettingsSecondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
