package com.isardomains.ghostshot.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotOverlayScrim
import com.isardomains.ghostshot.ui.theme.GhostShotTextPrimary
import com.isardomains.ghostshot.ui.theme.GhostShotTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private val CameraShutterButtonSize = 96.dp
private val CameraBottomControlGap = 16.dp
private val CameraOpacitySliderPortraitBottom = 128.dp
private val CameraOpacitySliderHeight = 56.dp
private const val CaptureSuccessSnackbarStateKey =
    "com.isardomains.ghostshot.ui.camera.CaptureSuccessSnackbar"
private const val CaptureSuccessSnackbarLastShownGenerationKey = "lastShownGeneration"

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
    viewModel: CameraViewModel = hiltViewModel(),
    onCompareImages: (CompareInput) -> Unit = {},
    onOpenCompareLibrary: () -> Unit = {}
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
            val removeSnackbarMessage = stringResource(R.string.reference_removed_snackbar)
            val removeSnackbarUndo = stringResource(R.string.reference_removed_undo)
            val captureSavedMessage = stringResource(R.string.capture_saved)
            val captureCompareAction = stringResource(R.string.capture_saved_compare_action)
            val compareInput = uiState.compareInput
            val hasSavedSessions = uiState.savedSessions.isNotEmpty()
            val onCompareClick: () -> Unit = {
                if (compareInput != null) {
                    onCompareImages(compareInput)
                } else if (hasSavedSessions) {
                    onOpenCompareLibrary()
                }
            }

            LaunchedEffect(Unit) {
                viewModel.refreshSavedSessions()
            }

            LaunchedEffect(viewModel) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is UiEvent.ShowSnackbar -> {
                            pendingSnackbarEvent = event
                        }
                        is UiEvent.UndoInvalidated -> {
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    }
                }
            }

            ReferenceRemovalUndoSnackbarEffect(
                canUndoReferenceRemoval = uiState.canUndoReferenceRemoval,
                undoGeneration = uiState.referenceRemovalUndoGeneration,
                hostState = snackbarHostState,
                message = removeSnackbarMessage,
                actionLabel = removeSnackbarUndo,
                onUndo = { viewModel.onReferenceImageRemoveUndo() }
            )

            CaptureSuccessSnackbarEffect(
                captureSuccessGeneration = uiState.captureSuccessGeneration,
                captureSuccessHadReference = uiState.captureSuccessHadReference,
                hostState = snackbarHostState,
                message = captureSavedMessage,
                actionLabel = captureCompareAction,
                onCompare = {
                    compareInput?.let(onCompareImages)
                }
            )

            val pendingMessage = pendingSnackbarEvent?.let { stringResource(it.messageResId) }

            LaunchedEffect(pendingSnackbarEvent) {
                if (pendingMessage != null) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(pendingMessage)
                    pendingSnackbarEvent = null
                }
            }

            val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.onCaptureInterrupted()
                    executor.shutdown()
                }
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
                    compareInput = compareInput,
                    hasSavedSessions = hasSavedSessions,
                    onCompareClick = onCompareClick,
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
                    onRemoveReferenceImage = { viewModel.onReferenceImageRemoveConfirmed() },
                    displayMode = uiState.referenceImageDisplayMode,
                    hasViewportMismatch = uiState.referenceImageHasViewportMismatch,
                    onToggleDisplayMode = { viewModel.onReferenceImageDisplayModeToggle() },
                    onCapture = onCapture,
                    isLandscape = isLandscape,
                    modifier = Modifier.fillMaxSize()
                )

                // ── Layer 4: Snackbar ─────────────────────────────────────────────────
                CameraSnackbarHost(
                    hostState = snackbarHostState,
                    isLandscape = isLandscape,
                    modifier = Modifier.align(Alignment.BottomCenter)
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

@Composable
internal fun CameraSnackbarHost(
    hostState: SnackbarHostState,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = cameraSnackbarBottomPadding(isLandscape))
            .testTag("camera_snackbar_host"),
        snackbar = { data -> Snackbar(snackbarData = data) }
    )
}

@Composable
internal fun ReferenceRemovalUndoSnackbarEffect(
    canUndoReferenceRemoval: Boolean,
    undoGeneration: Long,
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String,
    onUndo: () -> Unit
) {
    LaunchedEffect(canUndoReferenceRemoval, undoGeneration) {
        if (canUndoReferenceRemoval) {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }
}

@Composable
internal fun CaptureSuccessSnackbarEffect(
    captureSuccessGeneration: Long,
    captureSuccessHadReference: Boolean,
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String,
    onCompare: () -> Unit
) {
    val savedStateRegistry = LocalSavedStateRegistryOwner.current.savedStateRegistry
    var lastShownGeneration by remember(savedStateRegistry) {
        mutableStateOf(
            savedStateRegistry
                .consumeRestoredStateForKey(CaptureSuccessSnackbarStateKey)
                ?.getLong(CaptureSuccessSnackbarLastShownGenerationKey, 0L)
                ?: 0L
        )
    }
    val currentLastShownGeneration = rememberUpdatedState(lastShownGeneration)
    DisposableEffect(savedStateRegistry) {
        savedStateRegistry.registerSavedStateProvider(CaptureSuccessSnackbarStateKey) {
            Bundle().apply {
                putLong(
                    CaptureSuccessSnackbarLastShownGenerationKey,
                    currentLastShownGeneration.value
                )
            }
        }
        onDispose {
            savedStateRegistry.unregisterSavedStateProvider(CaptureSuccessSnackbarStateKey)
        }
    }
    LaunchedEffect(captureSuccessGeneration) {
        if (captureSuccessGeneration > 0L && captureSuccessGeneration != lastShownGeneration) {
            lastShownGeneration = captureSuccessGeneration
            hostState.currentSnackbarData?.dismiss()
            val durationMs = if (captureSuccessHadReference) 2500L else 2000L
            launch {
                val result = hostState.showSnackbar(
                    message = message,
                    actionLabel = if (captureSuccessHadReference) actionLabel else null,
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onCompare()
                }
            }
            delay(durationMs)
            hostState.currentSnackbarData?.dismiss()
        }
    }
}

private fun cameraBottomPadding(isLandscape: Boolean): Dp =
    if (isLandscape) 18.dp else 24.dp

private fun cameraSnackbarBottomPadding(isLandscape: Boolean): Dp =
    if (isLandscape)
        cameraBottomPadding(true) + CameraShutterButtonSize + CameraBottomControlGap
    else
        CameraOpacitySliderPortraitBottom + CameraOpacitySliderHeight + 8.dp

/**
 * Camera-style controls layered over the fullscreen preview.
 */
@Composable
internal fun CameraControlsOverlay(
    referenceUri: Uri?,
    compareInput: CompareInput? = null,
    hasSavedSessions: Boolean = false,
    onCompareClick: () -> Unit = {},
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
    val referenceStartPadding = 16.dp
    val bottomPadding = cameraBottomPadding(isLandscape)
    var isStackVisible by remember { mutableStateOf(false) }

    LaunchedEffect(referenceUri) {
        if (referenceUri == null) isStackVisible = false
    }

    Box(modifier = modifier.testTag("camera_controls_root")) {
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
                FormatMismatchHint(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding()
                        .padding(top = 12.dp, start = horizontalPadding)
                )
            }

            // Opacity slider — fixed position above bottom controls, hidden when stack is open
            AnimatedVisibility(
                visible = isLandscape || !isStackVisible,
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = horizontalPadding, bottom = bottomPadding)
                        .fillMaxWidth(0.3f)
                        .widthIn(max = 280.dp)
                        .height(CameraShutterButtonSize)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = horizontalPadding, end = horizontalPadding, bottom = 128.dp)
                },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (isLandscape) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingOpacitySlider(alpha = alpha, onAlphaChange = onAlphaChange)
                    }
                } else {
                    FloatingOpacitySlider(alpha = alpha, onAlphaChange = onAlphaChange)
                }
            }
        }

        val referenceClick = {
            if (referenceUri == null) {
                onSelectReferenceImage()
            } else {
                isStackVisible = !isStackVisible
            }
        }
        val referenceStack = @Composable {
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
                },
                isCompact = isLandscape
            )
        }

        // Bottom-left: reference button with action stack anchored to it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = referenceStartPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AnimatedVisibility(
                visible = isStackVisible && referenceUri != null,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                referenceStack()
            }
            Box(
                modifier = Modifier
                    .height(CameraShutterButtonSize)
                    .testTag("reference_action_slot"),
                contentAlignment = Alignment.Center
            ) {
                ReferenceAction(
                    isActive = referenceUri != null,
                    onClick = referenceClick
                )
            }
        }

        if (compareInput != null || hasSavedSessions) {
            CompareImagesEntry(
                onClick = onCompareClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = horizontalPadding,
                        bottom = if (isLandscape)
                            bottomPadding + CameraShutterButtonSize + 8.dp
                        else
                            bottomPadding
                    )
                    .height(CameraShutterButtonSize)
                    .wrapContentHeight(align = Alignment.CenterVertically)
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

@Composable
internal fun CompareImagesEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.compare_entry_label)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .testTag("compare_images_entry")
            .semantics { contentDescription = label }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = GhostShotTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = GhostShotTextPrimary
            )
        }
    }
}

@Composable
private fun FormatMismatchHint(
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.reference_format_mismatch_description)
    val bubbleText = stringResource(R.string.reference_format_mismatch_bubble)
    val view = LocalView.current
    var isBubbleVisible by remember { mutableStateOf(false) }
    var hintRequest by remember { mutableStateOf(0) }

    LaunchedEffect(hintRequest) {
        if (hintRequest > 0) {
            isBubbleVisible = true
            view.announceForAccessibility(bubbleText)
            delay(1800)
            isBubbleVisible = false
        }
    }

    Box(
        modifier = modifier
            .width(180.dp)
            .height(92.dp)
            .testTag("format_mismatch_hint_container")
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { hintRequest++ }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    testTag = "format_mismatch_hint"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(GhostShotOverlayScrim.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .testTag("format_mismatch_hint_icon"),
                    tint = GhostShotTextPrimary.copy(alpha = 0.82f)
                )
            }
        }

        AnimatedVisibility(
            visible = isBubbleVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 56.dp)
                .testTag("format_mismatch_hint_bubble"),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GhostShotOverlayScrim.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostShotTextPrimary,
                    maxLines = 1
                )
            }
        }
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
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val referenceLabel = stringResource(R.string.select_reference_image)
    val optionsBadgeLabel = stringResource(R.string.reference_options_badge)
    val optionsBadgeText = stringResource(R.string.reference_options_badge_text)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .then(
                if (isActive) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .testTag("reference_action")
            .semantics {
                contentDescription = referenceLabel
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Check else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.testTag(
                    if (isActive) "reference_action_active_indicator" else "reference_action_add_indicator"
                ),
                tint = GhostShotTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.select_reference_image_label),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) GhostShotTextPrimary else GhostShotTextSecondary
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("reference_action_options_badge")
                    .semantics { contentDescription = optionsBadgeLabel },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionsBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostShotTextPrimary
                )
            }
        }
    }
}

@Composable
private fun ReferenceActionStack(
    onReset: () -> Unit,
    displayMode: ReferenceImageDisplayMode,
    onToggleDisplayMode: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val resetLabel = stringResource(R.string.reset_overlay_label)
    val displayModeLabel = stringResource(R.string.toggle_reference_display_mode)
    val replaceLabel = stringResource(R.string.replace_reference_image)
    val removeLabel = stringResource(R.string.remove_reference_image)
    val displayModeText = stringResource(
        when (displayMode) {
            ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW -> R.string.action_stack_display_mode_compare_label
            ReferenceImageDisplayMode.SHOW_FULL_IMAGE -> R.string.action_stack_display_mode_fit_label
        }
    )
    val rowVerticalPadding = if (isCompact) 6.dp else 10.dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GhostShotOverlayScrim)
            .testTag("reference_action_menu")
            .widthIn(min = 176.dp, max = 216.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReset)
                .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
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
                .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
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
                modifier = Modifier.testTag("reference_display_mode_icon"),
                tint = GhostShotTextPrimary
            )
            Text(
                text = displayModeText,
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotTextPrimary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReplace)
                .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                .semantics(mergeDescendants = true) { contentDescription = replaceLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
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
                .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
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
            .size(CameraShutterButtonSize)
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
