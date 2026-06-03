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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.create_video_action_done))
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp) // space reserved for the fixed CTA button
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Mode ──────────────────────────────────────────────────────────
            val modes = listOf(VideoMode.COMPARE_SLIDER, VideoMode.BEFORE_AFTER)
            val modeLabels = listOf(
                stringResource(R.string.create_video_mode_compare_slider),
                stringResource(R.string.create_video_mode_before_after)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(modeLabels[index]) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Format ────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.create_video_format_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val formats = listOf(
                VideoExportFormat.ORIGINAL,
                VideoExportFormat.PORTRAIT_9_16,
                VideoExportFormat.LANDSCAPE_16_9
            )
            val formatLabels = listOf(
                stringResource(R.string.create_video_format_original),
                stringResource(R.string.create_video_format_portrait),
                stringResource(R.string.create_video_format_landscape)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                formats.forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = state.format == format,
                        onClick = { onFormatChange(format) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                        label = { Text(formatLabels[index]) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Duration ──────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.create_video_duration_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val durations = listOf(4000, 6000, 8000)
            val durationLabels = listOf(
                stringResource(R.string.create_video_duration_short),
                stringResource(R.string.create_video_duration_medium),
                stringResource(R.string.create_video_duration_long)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                durations.forEachIndexed { index, durationMs ->
                    SegmentedButton(
                        selected = state.durationMs == durationMs,
                        onClick = { onDurationChange(durationMs) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = durations.size),
                        label = { Text(durationLabels[index]) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Quality ───────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.create_video_quality_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = state.quality == VideoQuality.STANDARD_1080P,
                    onClick = { onQualityChange(VideoQuality.STANDARD_1080P) },
                    label = { Text(stringResource(R.string.create_video_quality_standard)) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.quality == VideoQuality.HIGH_QUALITY,
                    onClick = { onQualityChange(VideoQuality.HIGH_QUALITY) },
                    label = { Text(stringResource(R.string.create_video_quality_high)) }
                )
            }
            Text(
                text = stringResource(R.string.create_video_quality_high_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // ── Branding toggle ───────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = state.brandingEnabled,
                    onCheckedChange = onBrandingChange
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.create_video_branding_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        // Fixed CTA button at the bottom
        Button(
            onClick = onCreateVideo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
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
