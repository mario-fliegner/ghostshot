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
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
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
import kotlinx.coroutines.delay

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

            val activeAspectRatio = uiState.activeAspectRatio
            val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }
            val snackbarHostState = remember { SnackbarHostState() }
            var pendingSnackbarEvent by remember { mutableStateOf<UiEvent.ShowSnackbar?>(null) }
            var successMessageResId by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(viewModel) {
                viewModel.uiEvent.collect { event ->
                    if (event is UiEvent.ShowSnackbar) {
                        if (event.isSuccess) {
                            successMessageResId = event.messageResId
                        } else {
                            pendingSnackbarEvent = event
                        }
                    }
                }
            }

            val pendingMessage = pendingSnackbarEvent?.let { stringResource(it.messageResId) }
            val successMessage = successMessageResId?.let { stringResource(it) }

            LaunchedEffect(pendingSnackbarEvent) {
                if (pendingMessage != null) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(pendingMessage)
                    pendingSnackbarEvent = null
                }
            }

            LaunchedEffect(successMessageResId) {
                if (successMessageResId != null) {
                    delay(1200)
                    successMessageResId = null
                }
            }

            val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
            val onCapture: () -> Unit = {
                imageCaptureState.value?.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val bitmap = image.toBitmap()
                                val rotation = image.imageInfo.rotationDegrees
                                viewModel.onPhotoCaptured(bitmap, rotation)
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            viewModel.onPhotoCaptureError()
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {

                // ── Camera viewport (Layer 1 + Layer 2) ──────────────────────────────
                // Keyed by activeAspectRatio so that the ImageCapture use case and
                // the CameraX binding are fully recreated when the target ratio changes.
                key(activeAspectRatio) {
                    val cameraXRatio = when (activeAspectRatio) {
                        TargetAspectRatio.RATIO_4_3 -> AspectRatio.RATIO_4_3
                        TargetAspectRatio.RATIO_16_9 -> AspectRatio.RATIO_16_9
                    }
                    val imageCapture = remember {
                        ImageCapture.Builder()
                            .setTargetAspectRatio(cameraXRatio)
                            .build()
                            .also { imageCaptureState.value = it }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Layer 1: Camera preview ───────────────────────────────────────
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener(
                                    {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder()
                                            .setTargetAspectRatio(cameraXRatio)
                                            .build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        // Unbind all use cases before rebinding to avoid conflicts.
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    },
                                    ContextCompat.getMainExecutor(ctx)
                                )
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // ── Layer 2: Reference image overlay ──────────────────────────────
                        // Shares the same viewport container as the preview. Position and scale
                        // use normalised fractions so gesture math in the ViewModel is unchanged.
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
                    }
                }

                // ── Layer 3: Camera controls overlay ─────────────────────────────────
                CameraControlsOverlay(
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
                    onCapture = onCapture,
                    isLandscape = isLandscape,
                    modifier = Modifier.fillMaxSize()
                )

                // ── Layer 4: Snackbar ─────────────────────────────────────────────────
                successMessage?.let { message ->
                    SaveSuccessOverlay(
                        message = message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                    snackbar = { data -> Snackbar(snackbarData = data) }
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
 * Short save confirmation shown away from the camera controls.
 */
@Composable
private fun SaveSuccessOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = GhostShotTextPrimary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = GhostShotTextPrimary
        )
    }
}

/**
 * Camera-style controls layered over the fullscreen preview.
 */
@Composable
internal fun CameraControlsOverlay(
    referenceUri: Uri?,
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    onSelectReferenceImage: () -> Unit,
    onResetOverlay: () -> Unit,
    onCapture: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = if (isLandscape) 28.dp else 24.dp
    val bottomPadding = if (isLandscape) 18.dp else 24.dp
    val referenceBottomPadding = if (isLandscape) bottomPadding else 38.dp
    val sliderBottomPadding = if (isLandscape) bottomPadding else 128.dp

    Box(modifier = modifier) {
        if (referenceUri != null) {
            IconButton(
                onClick = onResetOverlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 12.dp, end = horizontalPadding)
                    .background(GhostShotOverlayScrim, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.reset_overlay_label),
                    tint = GhostShotTextPrimary
                )
            }

            FloatingOpacitySlider(
                alpha = alpha,
                onAlphaChange = onAlphaChange,
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = horizontalPadding, bottom = sliderBottomPadding)
                        .fillMaxWidth(0.3f)
                        .widthIn(max = 280.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            bottom = sliderBottomPadding
                        )
                }
            )
        }

        ReferenceAction(
            onSelectReferenceImage = onSelectReferenceImage,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = horizontalPadding, bottom = referenceBottomPadding)
        )

        ShutterButton(
            onCapture = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomPadding)
        )
    }
}

/**
 * Floating opacity control shown above the shutter when a reference overlay is active.
 */
@Composable
private fun FloatingOpacitySlider(
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val opacityLabel = stringResource(R.string.overlay_opacity_label)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Slider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = 0.1f..0.9f,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = opacityLabel }
        )
    }
}

/**
 * Larger reference picker target placed at the bottom-left of the camera overlay.
 */
@Composable
private fun ReferenceAction(
    onSelectReferenceImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .clickable(onClick = onSelectReferenceImage)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.select_reference_image),
            tint = GhostShotTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.select_reference_image_label),
            style = MaterialTheme.typography.labelSmall,
            color = GhostShotTextSecondary
        )
    }
}

/**
 * Large centered shutter target for the primary capture action.
 */
@Composable
private fun ShutterButton(
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(onClick = onCapture),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(Color.White, CircleShape)
                .border(width = 4.dp, color = Color.White.copy(alpha = 0.45f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.capture_button_content_description),
                tint = Color.Black
            )
        }
    }
}

