// path: app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt
package com.isardomains.sameview.ui.video

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SameViewSegmentControl
import com.isardomains.sameview.ui.settings.SameViewSegmentItem
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.settings.SettingsSwitchRow
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import com.isardomains.sameview.video.VideoExportFormat
import com.isardomains.sameview.video.VideoMode
import com.isardomains.sameview.video.VideoQuality

/**
 * Fullscreen wizard screen for creating a video from a compare session.
 *
 * State flow: Configuring → Rendering → Preview.
 *
 * @param onBack called when the user navigates back (Configuring back or Preview Done/Back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVideoScreen(
    onBack: () -> Unit,
    viewModel: CreateVideoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
                    snackbarHostState.showSnackbar(context.getString(event.messageResId))
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
                    title = { Text(stringResource(R.string.create_video_rendering_title)) }
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
                onModeChange = viewModel::updateMode,
                onFormatChange = viewModel::updateFormat,
                onDurationChange = viewModel::updateDurationMs,
                onQualityChange = viewModel::updateQuality,
                onBrandingChange = viewModel::updateBrandingEnabled,
                onCreateVideo = viewModel::startExport,
                modifier = Modifier.padding(paddingValues)
            )
            is CreateVideoState.Rendering -> RenderingContent(
                state = s,
                progress = progress,
                modifier = Modifier.padding(paddingValues)
            )
            is CreateVideoState.Preview -> PreviewContent(
                state = s,
                onDelete = viewModel::deleteVideo,
                onDone = onBack,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// ── Configuring ───────────────────────────────────────────────────────────────

@Composable
private fun ConfiguringContent(
    state: CreateVideoState.Configuring,
    onModeChange: (VideoMode) -> Unit,
    onFormatChange: (VideoExportFormat) -> Unit,
    onDurationChange: (Int) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onBrandingChange: (Boolean) -> Unit,
    onCreateVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Mode ──────────────────────────────────────────────────────────
        val modes = listOf(VideoMode.COMPARE_SLIDER, VideoMode.BEFORE_AFTER)
        val modeItems = listOf(
            SameViewSegmentItem(stringResource(R.string.create_video_mode_compare_slider)),
            SameViewSegmentItem(stringResource(R.string.create_video_mode_before_after))
        )
        SettingsCard(title = stringResource(R.string.create_video_mode_label)) {
            SameViewSegmentControl(
                items = modeItems,
                selectedIndex = modes.indexOf(state.mode),
                onItemSelected = { onModeChange(modes[it]) }
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

        // ── Branding ──────────────────────────────────────────────────────
        SettingsCard {
            SettingsSwitchRow(
                label = stringResource(R.string.create_video_branding_label),
                checked = state.brandingEnabled,
                onCheckedChange = onBrandingChange
            )
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
    }
}

// ── Rendering ─────────────────────────────────────────────────────────────────

@Composable
private fun RenderingContent(
    state: CreateVideoState.Rendering,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    R.string.create_video_rendering_progress,
                    state.currentFrame,
                    state.totalFrames
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

/**
 * Preview state: auto-playing, looping, muted Media3 ExoPlayer with Share/Delete/Done actions.
 *
 * Player lifecycle is tied to composition via [DisposableEffect] — released on exit.
 * Rotation is safe: [state.videoUri] lives in ViewModel and survives configuration change.
 */
@OptIn(UnstableApi::class)
@Composable
private fun PreviewContent(
    state: CreateVideoState.Preview,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

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

    Column(modifier = modifier.fillMaxSize()) {
        // Video player occupies all remaining space above the buttons
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Action buttons at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, state.videoUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(shareIntent, null)) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.create_video_action_share))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.create_video_action_delete))
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.create_video_action_done))
                }
            }
        }
    }
}
