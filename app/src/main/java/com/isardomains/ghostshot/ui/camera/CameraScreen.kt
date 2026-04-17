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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.max

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

    val activity = context as? android.app.Activity

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
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
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
            var undoAvailable by remember { mutableStateOf(false) }
            var pendingUndoSnackbarTrigger by remember { mutableStateOf(0) }
            val removeSnackbarMessage = stringResource(R.string.reference_removed_snackbar)
            val removeSnackbarUndo = stringResource(R.string.reference_removed_undo)

            LaunchedEffect(viewModel) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is UiEvent.ShowSnackbar -> {
                            if (event.isSuccess) {
                                successMessageResId = event.messageResId
                            } else {
                                pendingSnackbarEvent = event
                            }
                        }
                        is UiEvent.UndoInvalidated -> {
                            undoAvailable = false
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    }
                }
            }

            LaunchedEffect(pendingUndoSnackbarTrigger) {
                if (pendingUndoSnackbarTrigger > 0) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = removeSnackbarMessage,
                        actionLabel = removeSnackbarUndo,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && undoAvailable) {
                        viewModel.onReferenceImageRemoveUndo()
                    }
                    undoAvailable = false
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
            DisposableEffect(Unit) {
                onDispose { executor.shutdown() }
            }
            val onCapture: () -> Unit = onCapture@{
                val imageCapture = imageCaptureState.value ?: return@onCapture
                if (!viewModel.tryStartCapture()) return@onCapture
                try {
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bitmap = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    viewModel.onPhotoCaptured(bitmap, rotation)
                                } catch (_: Exception) {
                                    viewModel.onPhotoCaptureError()
                                } catch (_: OutOfMemoryError) {
                                    viewModel.onPhotoCaptureError()
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.onPhotoCaptureError()
                            }
                        }
                    )
                } catch (_: Exception) {
                    viewModel.onPhotoCaptureError()
                } catch (_: OutOfMemoryError) {
                    viewModel.onPhotoCaptureError()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        viewModel.onReferenceViewportChanged(size.width, size.height)
                    }
            ) {

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
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                                imageCaptureState.value = null
                                                viewModel.onPhotoCaptureError()
                                                return@addListener
                                            }

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
                                        } catch (_: Exception) {
                                            imageCaptureState.value = null
                                            viewModel.onPhotoCaptureError()
                                        }
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
                            ReferenceImageOverlay(
                                referenceUri = referenceUri,
                                metadata = uiState.referenceImageMetadata,
                                displayMode = uiState.referenceImageDisplayMode,
                                offsetX = uiState.overlayOffsetX,
                                offsetY = uiState.overlayOffsetY,
                                scale = uiState.overlayScale,
                                alpha = uiState.overlayAlpha,
                                onDragged = { dx, dy ->
                                    viewModel.onOverlayDragged(dx = dx, dy = dy)
                                },
                                onScaled = { zoom -> viewModel.onOverlayScaled(zoom) },
                                modifier = Modifier.fillMaxSize()
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
                    onRemoveReferenceImage = {
                        viewModel.onReferenceImageRemoveConfirmed()
                        undoAvailable = true
                        pendingUndoSnackbarTrigger++
                    },
                    displayMode = uiState.referenceImageDisplayMode,
                    hasViewportMismatch = uiState.referenceImageHasViewportMismatch,
                    onToggleDisplayMode = { viewModel.onReferenceImageDisplayModeToggle() },
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

@Composable
private fun ReferenceImageOverlay(
    referenceUri: Uri,
    metadata: ReferenceImageMetadata?,
    displayMode: ReferenceImageDisplayMode,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    alpha: Float,
    onDragged: (dx: Float, dy: Float) -> Unit,
    onScaled: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val overlayDescription = stringResource(R.string.overlay_content_description)
    val currentOnDragged by rememberUpdatedState(onDragged)
    val currentOnScaled by rememberUpdatedState(onScaled)

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (size.width > 0 && size.height > 0) {
                        currentOnDragged(
                            pan.x / size.width,
                            pan.y / size.height
                        )
                        currentOnScaled(zoom)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            displayMode == ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW && metadata != null -> {
                CompareReferenceImage(
                    referenceUri = referenceUri,
                    metadata = metadata,
                    viewportSize = viewportSize,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    scale = scale,
                    alpha = alpha,
                    contentDescription = overlayDescription
                )
            }

            displayMode == ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> {
                AsyncImage(
                    model = referenceUri,
                    contentDescription = overlayDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetX * size.width
                            translationY = offsetY * size.height
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Crop,
                    alpha = alpha,
                )
            }

            else -> {
                AsyncImage(
                    model = referenceUri,
                    contentDescription = overlayDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetX * size.width
                            translationY = offsetY * size.height
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Fit,
                    alpha = alpha,
                )
            }
        }
    }
}

@Composable
private fun CompareReferenceImage(
    referenceUri: Uri,
    metadata: ReferenceImageMetadata,
    viewportSize: IntSize,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    alpha: Float,
    contentDescription: String
) {
    if (
        viewportSize.width <= 0 ||
        viewportSize.height <= 0 ||
        metadata.orientedWidth <= 0 ||
        metadata.orientedHeight <= 0
    ) {
        AsyncImage(
            model = referenceUri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = alpha,
        )
        return
    }

    val density = LocalDensity.current
    val imageWidth = metadata.orientedWidth.toFloat()
    val imageHeight = metadata.orientedHeight.toFloat()
    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    val fillScale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val displayedWidth = imageWidth * fillScale
    val displayedHeight = imageHeight * fillScale
    val scaledWidth = displayedWidth * scale
    val scaledHeight = displayedHeight * scale
    val maxTranslationX = max(0f, (scaledWidth - viewportWidth) / 2f)
    val maxTranslationY = max(0f, (scaledHeight - viewportHeight) / 2f)
    val translationX = (offsetX * viewportWidth).coerceIn(-maxTranslationX, maxTranslationX)
    val translationY = (offsetY * viewportHeight).coerceIn(-maxTranslationY, maxTranslationY)

    AsyncImage(
        model = referenceUri,
        contentDescription = contentDescription,
        modifier = Modifier
            .requiredSize(
                width = with(density) { displayedWidth.toDp() },
                height = with(density) { displayedHeight.toDp() }
            )
            .graphicsLayer {
                this.translationX = translationX
                this.translationY = translationY
                scaleX = scale
                scaleY = scale
            },
        contentScale = ContentScale.FillBounds,
        alpha = alpha,
    )
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
    onRemoveReferenceImage: () -> Unit = {},
    displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
    hasViewportMismatch: Boolean = false,
    onToggleDisplayMode: () -> Unit = {},
    onCapture: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = if (isLandscape) 28.dp else 24.dp
    val bottomPadding = if (isLandscape) 18.dp else 24.dp
    val referenceBottomPadding = if (isLandscape) bottomPadding else 38.dp
    val mismatchDescription = stringResource(R.string.reference_viewport_mismatch)

    var isStackVisible by remember { mutableStateOf(false) }

    LaunchedEffect(referenceUri) {
        if (referenceUri == null) isStackVisible = false
    }

    Box(modifier = modifier) {
        // Backdrop — lowest z, catches taps outside the stack to dismiss it
        if (isStackVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTag = "reference_menu_backdrop" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isStackVisible = false }
            )
        }

        if (referenceUri != null) {
            if (hasViewportMismatch) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding()
                        .padding(top = 12.dp, start = horizontalPadding)
                        .size(48.dp)
                        .background(GhostShotOverlayScrim, CircleShape)
                        .semantics { contentDescription = mismatchDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = GhostShotTextPrimary
                    )
                }
            }

            // Opacity slider — fixed position above bottom controls, hidden when stack is open
            AnimatedVisibility(
                visible = !isStackVisible,
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = horizontalPadding, bottom = bottomPadding)
                        .fillMaxWidth(0.3f)
                        .widthIn(max = 280.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = horizontalPadding, end = horizontalPadding, bottom = 128.dp)
                },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingOpacitySlider(alpha = alpha, onAlphaChange = onAlphaChange)
            }
        }

        // Bottom-left: reference button with action stack opening above it
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = horizontalPadding, bottom = referenceBottomPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AnimatedVisibility(
                visible = isStackVisible && referenceUri != null,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                ReferenceActionStack(
                    onReset = {
                        isStackVisible = false
                        onResetOverlay()
                    },
                    displayMode = displayMode,
                    onToggleDisplayMode = {
                        isStackVisible = false
                        onToggleDisplayMode()
                    },
                    onReplace = {
                        isStackVisible = false
                        onSelectReferenceImage()
                    },
                    onRemove = {
                        isStackVisible = false
                        onRemoveReferenceImage()
                    }
                )
            }
            ReferenceAction(
                onClick = {
                    if (referenceUri == null) {
                        onSelectReferenceImage()
                    } else {
                        isStackVisible = true
                    }
                }
            )
        }

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
    onAlphaChange: (Float) -> Unit
) {
    val opacityLabel = stringResource(R.string.overlay_opacity_label)
    Box(
        modifier = Modifier
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val referenceLabel = stringResource(R.string.select_reference_image)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .testTag("reference_action")
            .semantics(mergeDescendants = true) {
                contentDescription = referenceLabel
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
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

@Composable
private fun ReferenceActionStack(
    onReset: () -> Unit,
    displayMode: ReferenceImageDisplayMode,
    onToggleDisplayMode: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resetLabel = stringResource(R.string.reset_overlay_label)
    val displayModeLabel = stringResource(R.string.toggle_reference_display_mode)
    val replaceLabel = stringResource(R.string.replace_reference_image)
    val removeLabel = stringResource(R.string.remove_reference_image)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .widthIn(min = 160.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReset)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = resetLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = GhostShotTextPrimary
            )
            Text(
                text = stringResource(R.string.action_stack_reset_label),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotTextPrimary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleDisplayMode)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = displayModeLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (displayMode) {
                    ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> Icons.Default.CropFree
                    ReferenceImageDisplayMode.SHOW_FULL_IMAGE -> Icons.Default.AspectRatio
                },
                contentDescription = null,
                tint = GhostShotTextPrimary
            )
            Text(
                text = stringResource(R.string.action_stack_display_mode_label),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotTextPrimary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReplace)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = replaceLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = GhostShotTextPrimary
            )
            Text(
                text = stringResource(R.string.action_stack_replace_label),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotTextPrimary
            )
        }
        HorizontalDivider(color = GhostShotTextPrimary.copy(alpha = 0.2f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemove)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = removeLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.action_stack_remove_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
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
