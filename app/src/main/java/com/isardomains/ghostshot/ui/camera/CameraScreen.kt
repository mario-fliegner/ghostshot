package com.isardomains.ghostshot.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotOverlayScrim
import com.isardomains.ghostshot.ui.theme.GhostShotTextPrimary
import com.isardomains.ghostshot.ui.theme.GhostShotTextSecondary

/**
 * Represents the four distinct states of the CAMERA permission lifecycle.
 *
 * CHECKING:           Initial state. The system dialog has not yet been shown in this
 *                     composition. No permission result is available yet.
 * GRANTED:            Permission is held. The camera preview and overlay are shown.
 * SHOW_RATIONALE:     Permission was denied but Android still allows re-requesting it.
 *                     shouldShowRequestPermissionRationale returned true after the denial.
 * PERMANENTLY_DENIED: Permission is permanently denied. The user must open system settings.
 *                     shouldShowRequestPermissionRationale returned false after the denial.
 */
private enum class CameraPermissionState {
    CHECKING,
    GRANTED,
    SHOW_RATIONALE,
    PERMANENTLY_DENIED,
}

/**
 * Main camera screen composable.
 *
 * Manages the CAMERA permission state machine and, once permission is granted,
 * renders a full-screen Box layout with four overlay layers:
 *  - Layer 1 (base):   Full-screen CameraX preview — never resized by UI.
 *  - Layer 2:          Reference image overlay (conditional).
 *  - Layer 3 (top):    Reserved for future secondary actions; currently provides
 *                      only the status bar inset.
 *  - Layer 4 (bottom): Orientation-aware bottom overlay with context controls
 *                      and the primary action bar, composited over the preview.
 *
 * @param viewModel The [CameraViewModel] provided by Hilt via the composition.
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Safe: LocalContext in a Compose tree hosted by ComponentActivity is always an Activity.
    val activity = context as android.app.Activity

    val uiState by viewModel.uiState.collectAsState()

    var permissionState by remember {
        val isGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(
            if (isGranted) CameraPermissionState.GRANTED else CameraPermissionState.CHECKING
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = when {
            granted -> CameraPermissionState.GRANTED
            // shouldShowRequestPermissionRationale is true only between the first denial
            // and a permanent denial. It is false both before any request AND after a
            // permanent denial, which is why CHECKING is needed to distinguish them.
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            ) -> CameraPermissionState.SHOW_RATIONALE
            else -> CameraPermissionState.PERMANENTLY_DENIED
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // uri is null when the picker is dismissed without a selection; ViewModel handles null.
        viewModel.onReferenceImageSelected(uri)
    }

    // Trigger the system dialog on the first composition.
    // When permanently denied, Android fires the launcher callback immediately
    // with granted=false without displaying any dialog.
    LaunchedEffect(Unit) {
        if (permissionState == CameraPermissionState.CHECKING) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (permissionState) {
        CameraPermissionState.CHECKING -> {
            // Blank while the system permission dialog is overlaid on screen.
            Box(modifier = Modifier.fillMaxSize())
        }

        CameraPermissionState.GRANTED -> {
            val referenceUri = uiState.referenceImageUri
            val isLandscape =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

            Box(modifier = Modifier.fillMaxSize()) {

                // ── Layer 1: Full-screen camera preview ───────────────────────────────
                // Always fills the entire screen. Never constrained by UI zones above it.
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener(
                            {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                // Unbind all use cases before rebinding to avoid conflicts.
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview
                                )
                            },
                            ContextCompat.getMainExecutor(ctx)
                        )
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Layer 2: Reference image overlay ──────────────────────────────────
                // Composited over the full preview. Only present when the user has
                // selected a reference image.
                //
                // Position is stored as normalised fractions in the ViewModel so that the
                // overlay survives rotation without drifting off-screen. The graphicsLayer
                // lambda multiplies each fraction by GraphicsLayerScope.size (the actual
                // draw size of this composable at the time of drawing) to produce pixel
                // translations. This is resolved per-frame, so the correct size is always
                // used after a configuration change without any external size tracking.
                //
                // Keeping graphicsLayer translation separate from layout bounds means the
                // full-screen touch area of the composable is unaffected by the current
                // overlay position, so drag detection works uniformly across the viewport.
                //
                // detectDragGestures checks whether a drag change was already consumed by a
                // higher-Z composable (Layer 4: Slider, IconButton). If consumed, the drag
                // is cancelled before onDrag fires, so UI controls and overlay drag never
                // interfere. Pixel deltas are divided by PointerInputScope.size before
                // being forwarded to the ViewModel, converting them to the same normalised
                // fraction space used for storage.
                if (referenceUri != null) {
                    AsyncImage(
                        model = referenceUri,
                        contentDescription = stringResource(R.string.overlay_content_description),
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                            .graphicsLayer {
                                translationX = uiState.overlayOffsetX * size.width
                                translationY = uiState.overlayOffsetY * size.height
                                scaleX = uiState.overlayScale
                                scaleY = uiState.overlayScale
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    viewModel.onOverlayDragged(
                                        dx = pan.x / size.width,
                                        dy = pan.y / size.height
                                    )
                                    viewModel.onOverlayScaled(zoom)
                                }
                            },
                        contentScale = ContentScale.Fit,
                        alpha = uiState.overlayAlpha,
                    )
                }

                // ── Layer 3: Top zone ─────────────────────────────────────────────────
                // Structurally reserved for future secondary actions (grid toggle,
                // interaction mode switch, etc.). Currently only provides the status
                // bar inset; zero visible height without content.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(GhostShotOverlayScrim)
                        .statusBarsPadding()
                )

                // ── Layer 4: Bottom overlay ───────────────────────────────────────────
                // Orientation-aware: portrait stacks controls vertically; landscape
                // collapses them into a single compact row to preserve preview area.
                CameraBottomOverlay(
                    referenceUri = referenceUri,
                    alpha = uiState.overlayAlpha,
                    onAlphaChange = { viewModel.onOverlayAlphaChanged(it) },
                    onSelectReferenceImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onResetOverlay = { viewModel.onOverlayReset() },
                    isLandscape = isLandscape,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(GhostShotOverlayScrim)
                        .navigationBarsPadding()
                )
            }
        }

        CameraPermissionState.SHOW_RATIONALE -> {
            // Permission denied once; Android still allows a direct re-request.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(stringResource(R.string.camera_permission_rationale))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }
        }

        CameraPermissionState.PERMANENTLY_DENIED -> {
            // Permission permanently blocked; only the system settings page can unblock it.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(stringResource(R.string.camera_permission_denied))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        ).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.open_settings))
                    }
                }
            }
        }
    }
}

/**
 * Orientation-aware bottom overlay combining context controls and the primary action bar.
 *
 * Portrait: vertical stack — opacity slider above the action bar, matching the natural
 * reading order and giving the slider full width for comfortable interaction.
 *
 * Landscape: single horizontal row — picker button, optional reset button, and opacity
 * slider sit side-by-side, keeping vertical height minimal so the preview area stays
 * as large as possible.
 *
 * The [modifier] supplied by the caller carries background, alignment, and inset
 * handling so both branches inherit identical surface treatment.
 */
@Composable
private fun CameraBottomOverlay(
    referenceUri: Uri?,
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    onSelectReferenceImage: () -> Unit,
    onResetOverlay: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        // Single row: picker icon on the left, reset + slider when overlay is active.
        // padding(vertical = 8.dp) gives natural breathing room without forcing a height.
        Row(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onSelectReferenceImage) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.select_reference_image),
                    tint = GhostShotTextPrimary
                )
            }
            if (referenceUri != null) {
                IconButton(onClick = onResetOverlay) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset_overlay_label),
                        tint = GhostShotTextPrimary
                    )
                }
                val opacityLabel = stringResource(R.string.overlay_opacity_label)
                Slider(
                    value = alpha,
                    onValueChange = onAlphaChange,
                    valueRange = 0.1f..0.9f,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = opacityLabel }
                )
            }
        }
    } else {
        // Vertical stack: opacity controls above the action bar.
        Column(modifier = modifier) {
            if (referenceUri != null) {
                OverlayContextControls(
                    alpha = alpha,
                    onAlphaChange = onAlphaChange
                )
            }
            CameraBottomBar(
                onSelectReferenceImage = onSelectReferenceImage,
                isOverlayActive = referenceUri != null,
                onResetOverlay = onResetOverlay
            )
        }
    }
}

/**
 * Context-sensitive controls shown above the bottom bar in portrait when a reference
 * overlay is active.
 *
 * Currently contains only the opacity slider. Additional per-overlay controls
 * (e.g. scale indicator) can be added to the Row in future steps.
 */
@Composable
private fun OverlayContextControls(
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val opacityLabel = stringResource(R.string.overlay_opacity_label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = 0.1f..0.9f,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = opacityLabel }
        )
    }
}

/**
 * Primary action bar used in portrait layout.
 *
 * Uses [Arrangement.SpaceEvenly] so that additional actions (grid toggle,
 * zoom mode, capture) can be inserted alongside the existing items without
 * layout changes.
 *
 * The reset button is only shown when an overlay is active ([isOverlayActive] is true).
 */
@Composable
private fun CameraBottomBar(
    onSelectReferenceImage: () -> Unit,
    isOverlayActive: Boolean,
    onResetOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSelectReferenceImage) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.select_reference_image),
                    tint = GhostShotTextPrimary
                )
            }
            Text(
                text = stringResource(R.string.select_reference_image_label),
                style = MaterialTheme.typography.labelSmall,
                color = GhostShotTextSecondary
            )
        }
        if (isOverlayActive) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onResetOverlay) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset_overlay_label),
                        tint = GhostShotTextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.reset_overlay_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostShotTextSecondary
                )
            }
        }
    }
}
