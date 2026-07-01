package com.isardomains.sameview.ui.camera

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.isardomains.sameview.BuildConfig
import com.isardomains.sameview.R
import com.isardomains.sameview.guide.FirstRunWalkthroughGateState
import com.isardomains.sameview.guide.GuideTip
import com.isardomains.sameview.guide.GuideTipAnchor
import com.isardomains.sameview.guide.GuideTipAnchorKey
import com.isardomains.sameview.guide.GuideTipController
import com.isardomains.sameview.guide.GuideTipDismissReason
import com.isardomains.sameview.guide.GuideTipEvaluationContext
import com.isardomains.sameview.guide.GuideTipHost
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTipRegistry
import com.isardomains.sameview.guide.GuideTipScope
import com.isardomains.sameview.guide.GuideTopicId
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewAppSurface
import com.isardomains.sameview.ui.theme.SameViewAppSurfaceElevated
import com.isardomains.sameview.ui.theme.SameViewGridLine
import com.isardomains.sameview.ui.theme.SameViewOverlayScrim
import com.isardomains.sameview.ui.theme.SameViewPreviewFrameScrim
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import com.isardomains.sameview.ui.theme.SameViewTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "SameView"
private val CameraShutterButtonSize = 96.dp
private val CameraSecondaryActionMinWidth = 96.dp
private val CameraBottomControlGap = 16.dp
private val CameraSliderButtonGap = 4.dp
private val CameraOpacitySliderPortraitBottom = 128.dp
private val CameraOpacitySliderHeight = 56.dp
private val CameraOpacitySliderLandscapeMaxWidth = 320.dp
private val CameraGridLineWidth = 1.dp
private val LandscapeTopActionsTopDistance = 20.dp
private val LandscapeOverflowMenuGap = 8.dp
private val CameraPortraitPreviewTopOffset = 20.dp
private const val CaptureSuccessSnackbarStateKey =
    "com.isardomains.sameview.ui.camera.CaptureSuccessSnackbar"
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
    onOpenCompareLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    firstRunWalkthroughGateState: FirstRunWalkthroughGateState = FirstRunWalkthroughGateState.Complete,
    onCameraPermissionGrantedForFirstRunGate: () -> Unit = {},
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    guideTipController: GuideTipController? = null,
    onOpenGuideTopic: (GuideTopicId) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activity = context as? android.app.Activity

    val uiState by viewModel.uiState.collectAsState()

    val view = LocalView.current
    DisposableEffect(view, lifecycleOwner, uiState.keepScreenOn) {
        val lifecycle = lifecycleOwner.lifecycle

        fun applyKeepScreenOn() {
            view.keepScreenOn = uiState.keepScreenOn &&
                lifecycle.currentState == Lifecycle.State.RESUMED
        }

        applyKeepScreenOn()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.keepScreenOn = uiState.keepScreenOn
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> view.keepScreenOn = false
                else -> applyKeepScreenOn()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            view.keepScreenOn = false
        }
    }

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
    val currentPermissionState = rememberUpdatedState(permissionState)
    var isReferencePickerActive by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                permissionState = CameraPermissionState.GRANTED
                return@LifecycleEventObserver
            }
            if (currentPermissionState.value == CameraPermissionState.CHECKING) {
                return@LifecycleEventObserver
            }
            permissionState = if (
                activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
            ) {
                CameraPermissionState.SHOW_RATIONALE
            } else {
                CameraPermissionState.PERMANENTLY_DENIED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        isReferencePickerActive = false
        // uri is null when the picker is dismissed without a selection; ViewModel handles null.
        viewModel.onReferenceImageSelected(uri)
    }

    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        isReferencePickerActive = false
        viewModel.onReferenceImageSelectedViaSaf(uri)
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
            LaunchedEffect(Unit) {
                onCameraPermissionGrantedForFirstRunGate()
            }
            if (firstRunWalkthroughGateState != FirstRunWalkthroughGateState.Complete) {
                FirstRunWalkthroughGateSurface()
                return@CameraScreen
            }

            val referenceUri = uiState.referenceImageUri
            val isLandscape =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }
            val snackbarHostState = remember { SnackbarHostState() }
            var pendingSnackbarEvent by remember { mutableStateOf<UiEvent.ShowSnackbar?>(null) }
            var showGpsFallbackDialog by remember { mutableStateOf(false) }
            val guideTipScope = rememberCoroutineScope()
            var guideTipAnchors by remember { mutableStateOf<Map<GuideTipAnchorKey, GuideTipAnchor>>(emptyMap()) }
            var activeGuideTip by remember { mutableStateOf<GuideTip?>(null) }
            var overlayInteractionGeneration by remember { mutableStateOf(0L) }
            var isOverlayInteractionActive by remember { mutableStateOf(false) }
            var isAlignTipIdleReady by remember { mutableStateOf(false) }
            var isMarkerTipEligible by remember { mutableStateOf(false) }
            val onGuideTipAnchor: (GuideTipAnchor) -> Unit = { anchor ->
                guideTipAnchors = guideTipAnchors + (anchor.key to anchor)
            }
            val removeSnackbarMessage = stringResource(R.string.reference_removed_snackbar)
            val removeSnackbarUndo = stringResource(R.string.reference_removed_undo)
            val captureSavedMessage = stringResource(R.string.capture_saved)
            val captureCompareAction = stringResource(R.string.capture_saved_compare_action)
            val compareInput = uiState.compareInput
            val density = LocalDensity.current
            var frameLeftDp by remember { mutableStateOf(0.dp) }
            var frameTopDp by remember { mutableStateOf(0.dp) }
            val captureFlashAlpha = remember { Animatable(0f) }
            var captureFlashVisible by remember { mutableStateOf(false) }
            val hapticFeedback = LocalHapticFeedback.current
            val onCompareClick: () -> Unit = {
                guideTipController?.onUserAction()
                if (compareInput != null) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Compare opened") }
                    onCompareImages(compareInput)
                } else {
                    viewModel.onCompareDisabledTapped(referenceUri)
                }
            }

            LaunchedEffect(overlayInteractionGeneration) {
                if (overlayInteractionGeneration > 0L) {
                    isOverlayInteractionActive = true
                    delay(700)
                    isOverlayInteractionActive = false
                }
            }

            LaunchedEffect(referenceUri, isOverlayInteractionActive, uiState.referenceMarkersState.isEditModeActive) {
                isAlignTipIdleReady = false
                if (referenceUri != null && !isOverlayInteractionActive && !uiState.referenceMarkersState.isEditModeActive) {
                    delay(2500)
                    isAlignTipIdleReady = true
                }
            }

            val cameraTipBlocked = isReferencePickerActive ||
                uiState.isCaptureInProgress ||
                showGpsFallbackDialog ||
                pendingSnackbarEvent != null ||
                snackbarHostState.currentSnackbarData != null ||
                isOverlayInteractionActive ||
                uiState.referenceMarkersState.isEditModeActive
            val cameraEligibleTipIds = buildSet {
                if (referenceUri == null) add(GuideTipId.REFERENCE)
                if (referenceUri != null && isAlignTipIdleReady) add(GuideTipId.ALIGN)
                if (compareInput != null && !uiState.autoOpenCompareAfterCapture) add(GuideTipId.COMPARE)
                if (uiState.savedSessions.isNotEmpty()) add(GuideTipId.HISTORY)
                if (isMarkerTipEligible) add(GuideTipId.MARKER)
                if (uiState.gpsGuidanceState !is GpsGuidanceState.Hidden) add(GuideTipId.GPS)
            }.filter { tipId ->
                val tip = GuideTipRegistry.tipFor(tipId)
                tip == null || guideTipAnchors.containsKey(tip.anchorKey)
            }.toSet()
            LaunchedEffect(guideTipController, cameraEligibleTipIds, cameraTipBlocked, activeGuideTip?.id) {
                val controller = guideTipController ?: return@LaunchedEffect
                val currentTip = activeGuideTip
                if (currentTip != null && (cameraTipBlocked || currentTip.id !in cameraEligibleTipIds)) {
                    controller.clearActiveTipWithoutMarkingSeen()
                    activeGuideTip = null
                    return@LaunchedEffect
                }
                if (currentTip == null) {
                    activeGuideTip = controller.evaluate(
                        GuideTipEvaluationContext(
                            scope = GuideTipScope.CAMERA,
                            eligibleTipIds = cameraEligibleTipIds,
                            isBlockedByTransientUi = cameraTipBlocked
                        )
                    )
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
                        is UiEvent.NavigateToCompare -> {
                            onCompareImages(event.input)
                        }
                        is UiEvent.ShowGpsFallbackDialog -> {
                            showGpsFallbackDialog = true
                        }
                        is UiEvent.BackupSucceeded -> {}
                    }
                }
            }

            ReferenceRemovalUndoSnackbarEffect(
                canUndoReferenceRemoval = uiState.canUndoReferenceRemoval,
                undoGeneration = uiState.referenceRemovalUndoGeneration,
                undoExpiresAtMillis = uiState.undoExpiresAtMillis,
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

            val pendingMessage = pendingSnackbarEvent?.let { event ->
                if (event.count != null) stringResource(event.messageResId, event.count)
                else stringResource(event.messageResId)
            }

            LaunchedEffect(pendingSnackbarEvent) {
                val event = pendingSnackbarEvent ?: return@LaunchedEffect
                val message = pendingMessage ?: return@LaunchedEffect
                snackbarHostState.currentSnackbarData?.dismiss()
                val durationMs = event.durationMs
                if (durationMs != null) {
                    launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
                    delay(durationMs)
                    snackbarHostState.currentSnackbarData?.dismiss()
                } else {
                    snackbarHostState.showSnackbar(message)
                }
                pendingSnackbarEvent = null
            }

            LaunchedEffect(captureFlashVisible) {
                if (captureFlashVisible) {
                    captureFlashAlpha.snapTo(1f)
                    delay(30)
                    captureFlashAlpha.animateTo(0f, tween(150))
                    captureFlashVisible = false
                }
            }

            val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.onCaptureInterrupted()
                    executor.shutdown()
                }
            }
            // GPS lifecycle: active when screen is visible and foregrounded
            DisposableEffect(Unit) {
                onDispose { viewModel.onCameraScreenInactive() }
            }
            DisposableEffect(lifecycleOwner) {
                val gpsObserver = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.onCameraScreenActive()
                        Lifecycle.Event.ON_PAUSE -> viewModel.onCameraScreenInactive()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(gpsObserver)
                onDispose { lifecycleOwner.lifecycle.removeObserver(gpsObserver) }
            }
            val onCapture: () -> Unit = onCapture@{
                val imageCapture = imageCaptureState.value ?: return@onCapture
                val captureToken = viewModel.tryStartCapture() ?: return@onCapture
                captureFlashVisible = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                try {
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bitmap = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    viewModel.onPhotoCaptured(captureToken, bitmap, rotation)
                                } catch (_: Exception) {
                                    viewModel.onPhotoCaptureError(captureToken)
                                } catch (_: OutOfMemoryError) {
                                    viewModel.onPhotoCaptureError(captureToken)
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.onPhotoCaptureError(captureToken)
                            }
                        }
                    )
                } catch (_: Exception) {
                    viewModel.onPhotoCaptureError(captureToken)
                } catch (_: OutOfMemoryError) {
                    viewModel.onPhotoCaptureError(captureToken)
                }
            }

            BackHandler(enabled = uiState.referenceMarkersState.isEditModeActive) {
                viewModel.exitMarkerEditMode()
            }

            if (showGpsFallbackDialog) {
                AlertDialog(
                    onDismissRequest = { showGpsFallbackDialog = false },
                    title = { Text(stringResource(R.string.gps_fallback_dialog_title)) },
                    text = { Text(stringResource(R.string.gps_fallback_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showGpsFallbackDialog = false
                            safPickerLauncher.launch(arrayOf("image/*"))
                        }) {
                            Text(stringResource(R.string.gps_fallback_dialog_choose_file_manager))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGpsFallbackDialog = false }) {
                            Text(stringResource(R.string.gps_fallback_dialog_continue_without_gps))
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SameViewPreviewFrameScrim)
                    .onSizeChanged { size ->
                        if (!isLandscape) {
                            val offsetPx = with(density) { CameraPortraitPreviewTopOffset.roundToPx() }
                            val availableHeight = (size.height - offsetPx).coerceAtLeast(0)
                            val effectiveHeight = minOf(availableHeight, size.width * 16 / 9)
                            viewModel.onReferenceViewportChanged(size.width, effectiveHeight)
                            frameLeftDp = 0.dp
                            frameTopDp = with(density) { (offsetPx + (availableHeight - effectiveHeight) / 2).toDp() }
                        } else {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w / h >= 16f / 9f) {
                                val effectiveWidth = (h * 16f / 9f).toInt()
                                viewModel.onReferenceViewportChanged(effectiveWidth, size.height)
                                frameLeftDp = with(density) { ((size.width - effectiveWidth) / 2).toDp() }
                                frameTopDp = 0.dp
                            } else {
                                val effectiveHeight = (w * 9f / 16f).toInt()
                                viewModel.onReferenceViewportChanged(size.width, effectiveHeight)
                                frameLeftDp = 0.dp
                                frameTopDp = with(density) { ((size.height - effectiveHeight) / 2).toDp() }
                            }
                        }
                    }
            ) {

                // ── Camera viewport (Layer 1 + Layer 2) ──────────────────────────────
                // Keyed by isLandscape only — the reference image must never influence
                // CameraX sensor/capture mode selection.
                key(isLandscape) {
                    var isCameraBindingReleased by remember { mutableStateOf(false) }
                    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
                    var boundPreview by remember { mutableStateOf<Preview?>(null) }
                    val imageCapture = remember {
                        ImageCapture.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .build()
                            .also { imageCaptureState.value = it }
                    }
                    DisposableEffect(imageCapture) {
                        onDispose {
                            isCameraBindingReleased = true
                            imageCaptureState.value = null
                            val cameraProvider = boundCameraProvider
                            val preview = boundPreview
                            if (cameraProvider != null && preview != null) {
                                cameraProvider.unbind(preview, imageCapture)
                            }
                            boundCameraProvider = null
                            boundPreview = null
                        }
                    }
                    Box(modifier = if (!isLandscape) Modifier.fillMaxSize().padding(top = CameraPortraitPreviewTopOffset) else Modifier.fillMaxSize()) {
                        // ── Layer 1: Camera preview ───────────────────────────────────────
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener(
                                    {
                                        try {
                                            if (isCameraBindingReleased) return@addListener
                                            val cameraProvider = cameraProviderFuture.get()
                                            if (isCameraBindingReleased) return@addListener
                                            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                                imageCaptureState.value = null
                                                viewModel.onCameraStartError()
                                                return@addListener
                                            }

                                            val preview = Preview.Builder()
                                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                                .build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val viewPort = ViewPort.Builder(
                                                if (isLandscape) Rational(16, 9) else Rational(9, 16),
                                                previewView.display?.rotation ?: Surface.ROTATION_0
                                            ).build()
                                            val useCaseGroup = UseCaseGroup.Builder()
                                                .setViewPort(viewPort)
                                                .addUseCase(preview)
                                                .addUseCase(imageCapture)
                                                .build()
                                            // Unbind all use cases before rebinding to avoid conflicts.
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                useCaseGroup
                                            )
                                            boundCameraProvider = cameraProvider
                                            boundPreview = preview
                                        } catch (_: Exception) {
                                            imageCaptureState.value = null
                                            viewModel.onCameraStartError()
                                        }
                                    },
                                    ContextCompat.getMainExecutor(ctx)
                                )
                                previewView
                            },
                            update = { view ->
                                view.scaleType = PreviewView.ScaleType.FIT_CENTER
                                view.setBackgroundColor(SameViewPreviewFrameScrim.toArgb())
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
                                    overlayInteractionGeneration++
                                    viewModel.onOverlayDragged(dx = dx, dy = dy)
                                },
                                onScaled = { zoom ->
                                    overlayInteractionGeneration++
                                    viewModel.onOverlayScaled(zoom)
                                },
                                modifier = if (!isLandscape) {
                                    Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)
                                } else {
                                    Modifier.fillMaxHeight().aspectRatio(16f / 9f).align(Alignment.Center)
                                }
                            )
                        }

                        // ── Layer 2.5: Reference marker overlay ───────────────────────────
                        val markersState = uiState.referenceMarkersState
                        if (referenceUri != null &&
                            (markersState.markersVisible || markersState.isEditModeActive)
                        ) {
                            ReferenceMarkerOverlay(
                                markersState = markersState,
                                metadata = uiState.referenceImageMetadata,
                                displayMode = uiState.referenceImageDisplayMode,
                                overlayOffsetX = uiState.overlayOffsetX,
                                overlayOffsetY = uiState.overlayOffsetY,
                                overlayScale = uiState.overlayScale,
                                referenceUri = referenceUri,
                                onAddMarker = { nx, ny -> viewModel.addMarker(nx, ny) },
                                onMoveMarker = { id, nx, ny -> viewModel.moveMarker(id, nx, ny) },
                                onRemoveMarker = { id -> viewModel.removeMarker(id) },
                                onOverlayDragged = { dx, dy ->
                                    overlayInteractionGeneration++
                                    viewModel.onOverlayDragged(dx = dx, dy = dy)
                                },
                                onOverlayScaled = { zoom ->
                                    overlayInteractionGeneration++
                                    viewModel.onOverlayScaled(zoom)
                                },
                                modifier = if (!isLandscape) {
                                    Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)
                                } else {
                                    Modifier.fillMaxHeight().aspectRatio(16f / 9f).align(Alignment.Center)
                                }
                            )
                        }

                        // ── Layer 2.7: Edit-mode image rect border ────────────────────────
                        MarkerEditBorder(
                            isEditModeActive = uiState.referenceMarkersState.isEditModeActive,
                            metadata = uiState.referenceImageMetadata,
                            displayMode = uiState.referenceImageDisplayMode,
                            overlayOffsetX = uiState.overlayOffsetX,
                            overlayOffsetY = uiState.overlayOffsetY,
                            overlayScale = uiState.overlayScale,
                            modifier = if (!isLandscape) {
                                Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)
                            } else {
                                Modifier.fillMaxHeight().aspectRatio(16f / 9f).align(Alignment.Center)
                            }
                        )
                    }
                }

                // ── Layer 3: Camera grid overlay ─────────────────────────────────────
                if (uiState.gridType != GridType.NONE) {
                    CameraGridOverlay(
                        gridType = uiState.gridType,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = frameLeftDp, vertical = frameTopDp)
                    )
                }

                // ── Layer 4: Capture flash ────────────────────────────────────────────
                if (captureFlashAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = frameLeftDp, vertical = frameTopDp)
                            .graphicsLayer { alpha = captureFlashAlpha.value }
                            .background(SameViewTextPrimary)
                    )
                }

                // ── Layer 5: Camera controls overlay ─────────────────────────────────
                CameraControlsOverlay(
                    referenceUri = referenceUri,
                    compareInput = compareInput,
                    onCompareClick = onCompareClick,
                    alpha = uiState.overlayAlpha,
                    onAlphaChange = { viewModel.onOverlayAlphaChanged(it) },
                    onSelectReferenceImage = {
                        isReferencePickerActive = true
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
                    isOverlayNearlyInvisible = uiState.isOverlayNearlyInvisible,
                    onToggleDisplayMode = { viewModel.onReferenceImageDisplayModeToggle() },
                    onCapture = onCapture,
                    isLandscape = isLandscape,
                    frameLeft = frameLeftDp,
                    frameTop = frameTopDp,
                    gpsGuidanceState = uiState.gpsGuidanceState,
                    referenceMarkersState = uiState.referenceMarkersState,
                    onEnterMarkerEditMode = { viewModel.enterMarkerEditMode() },
                    onDoneMarkerEditMode = { viewModel.exitMarkerEditMode() },
                    onHideMarkers = { viewModel.hideMarkers() },
                    onShowMarkers = { viewModel.showMarkers() },
                    isMarkerEditModeActive = uiState.referenceMarkersState.isEditModeActive,
                    onGuideTipAnchor = onGuideTipAnchor,
                    onMarkerTipEligibilityChanged = { isMarkerTipEligible = it },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Layer 6: Snackbar ─────────────────────────────────────────────────
                CameraSnackbarHost(
                    hostState = snackbarHostState,
                    isLandscape = isLandscape,
                    hasOverlay = uiState.referenceImageUri != null,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // ── Layer 7: Top-right navigation ─────────────────────────────────────
                if (isLandscape) {
                    CameraLandscapeTopActions(
                        onOpenHistory = onOpenCompareLibrary,
                        onOpenSettings = onOpenSettings,
                        onOpenGuide = onOpenGuide,
                        onOpenAbout = onOpenAbout,
                        frameLeft = frameLeftDp,
                        onGuideTipAnchor = onGuideTipAnchor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CameraTopRightActions(
                        onOpenHistory = onOpenCompareLibrary,
                        onOpenSettings = onOpenSettings,
                        onOpenGuide = onOpenGuide,
                        onOpenAbout = onOpenAbout,
                        iconSize = 22.dp,
                        onGuideTipAnchor = onGuideTipAnchor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(end = 4.dp, top = 0.dp)
                    )
                }

                GuideTipHost(
                    activeTip = activeGuideTip,
                    anchors = guideTipAnchors.values.toList(),
                    windowWidthSizeClass = windowWidthSizeClass,
                    onGotIt = { _ ->
                        guideTipScope.launch {
                            guideTipController?.dismissActiveTip(GuideTipDismissReason.GOT_IT)
                            activeGuideTip = null
                        }
                    },
                    onLearnMore = { _, topicId ->
                        guideTipScope.launch {
                            guideTipController?.dismissActiveTip(GuideTipDismissReason.LEARN_MORE)
                            activeGuideTip = null
                            onOpenGuideTopic(topicId)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

            }
        }

        CameraPermissionState.SHOW_RATIONALE -> {
            // Permission denied once; Android still allows a direct re-request.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = SameViewAppSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SameViewAppSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = SameViewTextPrimary.copy(alpha = 0.88f)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.camera_permission_rationale_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = SameViewTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.camera_permission_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SameViewTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = SameViewTextPrimary
                            )
                        ) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }
        }

        CameraPermissionState.PERMANENTLY_DENIED -> {
            // Permission permanently blocked; only the system settings page can unblock it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = SameViewAppSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SameViewAppSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = SameViewTextPrimary.copy(alpha = 0.88f)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.camera_permission_blocked_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = SameViewTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.camera_permission_blocked_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SameViewTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = SameViewTextPrimary
                            )
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReferenceImageOverlay(
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
            .testTag("compare_reference_image")
            .fillMaxSize()
            .graphicsLayer {
                this.translationX = translationX
                this.translationY = translationY
                scaleX = scale
                scaleY = scale
            },
        contentScale = ContentScale.Crop,
        alpha = alpha,
    )
}

@Composable
internal fun CameraSnackbarHost(
    hostState: SnackbarHostState,
    isLandscape: Boolean,
    hasOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .widthIn(max = CameraOpacitySliderLandscapeMaxWidth)
            .padding(bottom = cameraSnackbarBottomPadding(isLandscape, hasOverlay))
            .testTag("camera_snackbar_host"),
        snackbar = { data -> Snackbar(snackbarData = data) }
    )
}

@Composable
internal fun ReferenceRemovalUndoSnackbarEffect(
    canUndoReferenceRemoval: Boolean,
    undoGeneration: Long,
    undoExpiresAtMillis: Long,
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String,
    onUndo: () -> Unit
) {
    LaunchedEffect(canUndoReferenceRemoval, undoGeneration, undoExpiresAtMillis) {
        if (canUndoReferenceRemoval) {
            val remaining = undoExpiresAtMillis - System.currentTimeMillis()
            if (remaining > 0) {
                hostState.currentSnackbarData?.dismiss()
                launch {
                    val result = hostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onUndo()
                    }
                }
                delay(remaining)
                hostState.currentSnackbarData?.dismiss()
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
            if (captureSuccessHadReference) return@LaunchedEffect
            launch {
                hostState.showSnackbar(
                    message = message,
                    actionLabel = null,
                    duration = SnackbarDuration.Indefinite
                )
            }
            delay(2000L)
            hostState.currentSnackbarData?.dismiss()
        }
    }
}

private fun cameraBottomPadding(isLandscape: Boolean): Dp =
    if (isLandscape) 10.dp else 24.dp

internal fun cameraSnackbarBottomPadding(isLandscape: Boolean, hasOverlay: Boolean): Dp =
    if (isLandscape && hasOverlay)
        cameraBottomPadding(true) + CameraShutterButtonSize + CameraSliderButtonGap + CameraOpacitySliderHeight + CameraSliderButtonGap
    else if (isLandscape)
        cameraBottomPadding(true) + CameraShutterButtonSize + CameraBottomControlGap
    else if (hasOverlay)
        CameraOpacitySliderPortraitBottom + CameraOpacitySliderHeight + 8.dp
    else
        cameraBottomPadding(false) + CameraShutterButtonSize + CameraBottomControlGap

/**
 * Camera-style controls layered over the fullscreen preview.
 */
@Composable
internal fun CameraControlsOverlay(
    referenceUri: Uri?,
    compareInput: CompareInput? = null,
    onCompareClick: () -> Unit = {},
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    onSelectReferenceImage: () -> Unit,
    onResetOverlay: () -> Unit,
    onRemoveReferenceImage: () -> Unit = {},
    displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
    hasViewportMismatch: Boolean = false,
    isOverlayNearlyInvisible: Boolean = false,
    onToggleDisplayMode: () -> Unit = {},
    onCapture: () -> Unit,
    isLandscape: Boolean,
    frameLeft: Dp = 0.dp,
    frameTop: Dp = 0.dp,
    gpsGuidanceState: GpsGuidanceState = GpsGuidanceState.Hidden,
    referenceMarkersState: ReferenceMarkersState = ReferenceMarkersState(),
    onEnterMarkerEditMode: () -> Unit = {},
    onDoneMarkerEditMode: () -> Unit = {},
    onHideMarkers: () -> Unit = {},
    onShowMarkers: () -> Unit = {},
    isMarkerEditModeActive: Boolean = false,
    onGuideTipAnchor: (GuideTipAnchor) -> Unit = {},
    onMarkerTipEligibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val horizontalPadding = if (isLandscape) 28.dp else 24.dp
    val bottomPadding = cameraBottomPadding(isLandscape)
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var isStackVisible by remember { mutableStateOf(false) }

    LaunchedEffect(referenceUri) {
        if (referenceUri == null) isStackVisible = false
    }
    LaunchedEffect(isMarkerEditModeActive) {
        if (isMarkerEditModeActive) isStackVisible = false
    }
    LaunchedEffect(isStackVisible, referenceUri, isMarkerEditModeActive) {
        onMarkerTipEligibilityChanged(isStackVisible && referenceUri != null && !isMarkerEditModeActive)
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
            when {
                isOverlayNearlyInvisible -> {
                    OverlayVisibilityWarning(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = maxOf(frameTop, statusBarTopInset) + 12.dp,
                                start = frameLeft + horizontalPadding
                            )
                    )
                }
                hasViewportMismatch -> {
                    FormatMismatchHint(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = maxOf(frameTop, statusBarTopInset) + 12.dp,
                                start = frameLeft + horizontalPadding
                            )
                    )
                }
            }

            if (!isLandscape) {
                AnimatedVisibility(
                    visible = !isStackVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = horizontalPadding, end = horizontalPadding, bottom = 128.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.guideTipAnchor(GuideTipAnchorKey.ALIGN_CONTROLS, onGuideTipAnchor)) {
                        FloatingOpacitySlider(alpha = alpha, onAlphaChange = onAlphaChange)
                    }
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
                isCompact = isLandscape,
                referenceMarkersState = referenceMarkersState,
                onEnterMarkerEditMode = {
                    isStackVisible = false
                    onEnterMarkerEditMode()
                },
                onHideMarkers = {
                    isStackVisible = false
                    onHideMarkers()
                },
                onShowMarkers = {
                    isStackVisible = false
                    onShowMarkers()
                },
                onGuideTipAnchor = onGuideTipAnchor
            )
        }

        if (isLandscape) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val controlGap = 16.dp
                val sliderButtonGap = CameraSliderButtonGap
                val sideControlsPadding = maxWidth / 2 + CameraShutterButtonSize / 2 + controlGap
                val sliderGroupWidth = CameraSecondaryActionMinWidth +
                    controlGap +
                    CameraShutterButtonSize +
                    controlGap +
                    CameraSecondaryActionMinWidth

                val navigationBottomPadding =
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val effectiveBottomPadding = bottomPadding + navigationBottomPadding

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(1f)
                        .widthIn(min = CameraSecondaryActionMinWidth)
                        .padding(end = sideControlsPadding, bottom = effectiveBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = isStackVisible && referenceUri != null,
                        modifier = Modifier.wrapContentWidth(
                            align = Alignment.End
                        ),
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                    ) {
                        referenceStack()
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = CameraSecondaryActionMinWidth)
                            .height(CameraShutterButtonSize)
                            .testTag("reference_action_slot")
                            .guideTipAnchor(GuideTipAnchorKey.REFERENCE_BUTTON, onGuideTipAnchor),
                        contentAlignment = Alignment.Center
                    ) {
                        ReferenceAction(
                            isActive = referenceUri != null,
                            onClick = referenceClick
                        )
                    }
                }

                if (isMarkerEditModeActive) {
                    MarkerDoneButton(
                        onClick = onDoneMarkerEditMode,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = effectiveBottomPadding)
                    )
                } else {
                    ShutterButton(
                        onCapture = onCapture,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = effectiveBottomPadding)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = sideControlsPadding, bottom = effectiveBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(controlGap),
                    verticalAlignment = Alignment.Bottom
                ) {
                    CompareImagesEntry(
                        label = stringResource(R.string.compare_entry_label),
                        onClick = onCompareClick,
                        enabled = compareInput != null,
                        modifier = Modifier
                            .guideTipAnchor(GuideTipAnchorKey.COMPARE_ACTION, onGuideTipAnchor)
                            .height(CameraShutterButtonSize)
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                }

                if (referenceUri != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = effectiveBottomPadding + CameraShutterButtonSize + sliderButtonGap
                            )
                            .width(sliderGroupWidth.coerceAtMost(CameraOpacitySliderLandscapeMaxWidth))
                            .height(CameraOpacitySliderHeight)
                            .guideTipAnchor(GuideTipAnchorKey.ALIGN_CONTROLS, onGuideTipAnchor),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingOpacitySlider(alpha = alpha, onAlphaChange = onAlphaChange)
                    }
                }
            }
        } else {
            // Bottom-left: reference button with action stack anchored to it.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = horizontalPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        .testTag("reference_action_slot")
                            .guideTipAnchor(GuideTipAnchorKey.REFERENCE_BUTTON, onGuideTipAnchor),
                    contentAlignment = Alignment.Center
                ) {
                    ReferenceAction(
                        isActive = referenceUri != null,
                        onClick = referenceClick
                    )
                }
            }

            CompareImagesEntry(
                label = stringResource(R.string.compare_entry_label),
                onClick = onCompareClick,
                enabled = compareInput != null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .guideTipAnchor(GuideTipAnchorKey.COMPARE_ACTION, onGuideTipAnchor)
                    .padding(
                        end = horizontalPadding,
                        bottom = bottomPadding
                    )
                    .height(CameraShutterButtonSize)
                    .wrapContentHeight(align = Alignment.CenterVertically)
            )

            if (isMarkerEditModeActive) {
                MarkerDoneButton(
                    onClick = onDoneMarkerEditMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                )
            } else {
                ShutterButton(
                    onCapture = onCapture,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                )
            }
        }

        GpsGuidanceChip(
            state = gpsGuidanceState,
            modifier = Modifier
                .guideTipAnchor(GuideTipAnchorKey.GPS_CHIP, onGuideTipAnchor)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        )
    }
}

@Composable
internal fun CompareImagesEntry(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SameViewOverlayScrim)
            .widthIn(min = CameraSecondaryActionMinWidth)
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
                tint = SameViewTextPrimary.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SameViewTextPrimary.copy(alpha = contentAlpha)
            )
        }
    }
}

@Composable
internal fun CameraLandscapeTopActions(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    frameLeft: Dp,
    onGuideTipAnchor: (GuideTipAnchor) -> Unit = {},
    modifier: Modifier = Modifier,
    navigationLeftInset: Dp = WindowInsets.navigationBars.asPaddingValues()
        .calculateLeftPadding(LayoutDirection.Ltr),
    navigationRightInset: Dp = WindowInsets.navigationBars.asPaddingValues()
        .calculateRightPadding(LayoutDirection.Ltr),
    safeTopInset: Dp = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
    safeBottomInset: Dp = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
) {
    val actionsOnStart = navigationRightInset > navigationLeftInset
    val railWidth = maxOf(frameLeft, 64.dp)
    val horizontalPadding = 8.dp
    val bottomReservedHeight = cameraBottomPadding(true) + CameraShutterButtonSize + CameraBottomControlGap
    val railAlignment = if (actionsOnStart) Alignment.CenterStart else Alignment.CenterEnd

    Box(modifier = modifier.testTag("camera_landscape_top_actions_root")) {
        Box(
            modifier = Modifier
                .align(railAlignment)
                .fillMaxHeight()
                .width(railWidth)
                .padding(
                    start = if (actionsOnStart) maxOf(navigationLeftInset, horizontalPadding) else horizontalPadding,
                    end = if (actionsOnStart) horizontalPadding else maxOf(navigationRightInset, horizontalPadding),
                    top = safeTopInset + 12.dp,
                    bottom = safeBottomInset + bottomReservedHeight
                )
                .testTag("camera_landscape_top_actions_rail"),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                CameraTopRightActions(
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    vertical = true,
                    overflowMenuPlacement = if (actionsOnStart) {
                        OverflowMenuPlacement.OpenToEnd
                    } else {
                        OverflowMenuPlacement.OpenToStart
                    },
                    overflowMenuGap = LandscapeOverflowMenuGap,
                    onGuideTipAnchor = onGuideTipAnchor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = LandscapeTopActionsTopDistance)
                        .testTag("camera_landscape_top_actions")
                )
            }
        }
    }
}


@Composable
private fun FirstRunWalkthroughGateSurface() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("first_run_gate_neutral_surface"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
internal enum class OverflowMenuPlacement {
    OpenToStart,
    OpenToEnd
}

@Composable
internal fun CameraTopRightActions(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    iconSize: Dp = 24.dp,
    vertical: Boolean = false,
    overflowMenuLayoutDirection: LayoutDirection = LayoutDirection.Ltr,
    overflowMenuOffset: DpOffset = DpOffset.Zero,
    overflowMenuPlacement: OverflowMenuPlacement? = null,
    overflowMenuGap: Dp = 0.dp,
    onGuideTipAnchor: (GuideTipAnchor) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val historyDescription = stringResource(R.string.camera_history_content_description)
    val overflowDescription = stringResource(R.string.camera_overflow_content_description)
    val settingsLabel = stringResource(R.string.camera_overflow_settings)
    val guideLabel = stringResource(R.string.camera_overflow_guide)
    val aboutLabel = stringResource(R.string.camera_overflow_about)
    val actions: @Composable () -> Unit = {
        IconButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .guideTipAnchor(GuideTipAnchorKey.HISTORY_ACTION, onGuideTipAnchor)
                .testTag("camera_history_button")
                .semantics { contentDescription = historyDescription }
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = historyDescription,
                tint = SameViewTextPrimary,
                modifier = Modifier.size(iconSize)
            )
        }
        Box {
            IconButton(
                onClick = { overflowExpanded = true },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("camera_overflow_button")
                    .semantics { contentDescription = overflowDescription }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = overflowDescription,
                    tint = SameViewTextPrimary,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (overflowMenuPlacement != null) {
                CameraSideAwareOverflowMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    placement = overflowMenuPlacement,
                    gap = overflowMenuGap,
                    settingsLabel = settingsLabel,
                    guideLabel = guideLabel,
                    aboutLabel = aboutLabel,
                    onOpenSettings = onOpenSettings,
                    onOpenGuide = onOpenGuide,
                    onOpenAbout = onOpenAbout
                )
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides overflowMenuLayoutDirection) {
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        offset = overflowMenuOffset,
                        modifier = Modifier.testTag("camera_overflow_menu")
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            DropdownMenuItem(
                                text = { Text(settingsLabel) },
                                onClick = { overflowExpanded = false; onOpenSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text(guideLabel) },
                                onClick = { overflowExpanded = false; onOpenGuide() }
                            )
                            DropdownMenuItem(
                                text = { Text(aboutLabel) },
                                onClick = { overflowExpanded = false; onOpenAbout() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            actions()
        }
    } else {
        Row(modifier = modifier) {
            actions()
        }
    }
}

@Composable
private fun CameraSideAwareOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    placement: OverflowMenuPlacement,
    gap: Dp,
    settingsLabel: String,
    guideLabel: String,
    aboutLabel: String,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAbout: () -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val positionProvider = remember(placement, gap, density) {
        SideAwareOverflowMenuPositionProvider(
            opensToEnd = placement == OverflowMenuPlacement.OpenToEnd,
            gapPx = with(density) { gap.roundToPx() },
            verticalMarginPx = with(density) { 8.dp.roundToPx() }
        )
    }

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .width(176.dp)
                .testTag("camera_overflow_menu"),
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation
        ) {
            Column {
                DropdownMenuItem(
                    text = { Text(settingsLabel) },
                    onClick = {
                        onDismissRequest()
                        onOpenSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text(guideLabel) },
                    onClick = {
                        onDismissRequest()
                        onOpenGuide()
                    }
                )
                DropdownMenuItem(
                    text = { Text(aboutLabel) },
                    onClick = {
                        onDismissRequest()
                        onOpenAbout()
                    }
                )
            }
        }
    }
}

internal class SideAwareOverflowMenuPositionProvider(
    internal val opensToEnd: Boolean,
    internal val gapPx: Int,
    internal val verticalMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val minX = 0
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(minX)
        val preferredX = if (opensToEnd) {
            anchorBounds.right + gapPx
        } else {
            anchorBounds.left - popupContentSize.width - gapPx
        }
        val x = preferredX.coerceIn(minX, maxX)

        val minY = verticalMarginPx
        val maxY = (windowSize.height - verticalMarginPx - popupContentSize.height)
            .coerceAtLeast(minY)
        val preferredY = anchorBounds.top
        val y = preferredY.coerceIn(minY, maxY)

        return IntOffset(x, y)
    }
}

@Composable
private fun OverlayVisibilityWarning(
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.overlay_visibility_warning_description)
    val bubbleText = stringResource(R.string.overlay_visibility_warning_bubble)
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
            .width(220.dp)
            .wrapContentHeight()
            .testTag("overlay_visibility_warning_container")
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { hintRequest++ }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    testTag = "overlay_visibility_warning"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(SameViewOverlayScrim.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .testTag("overlay_visibility_warning_icon"),
                    tint = SameViewTextPrimary.copy(alpha = 0.82f)
                )
            }
        }

        AnimatedVisibility(
            visible = isBubbleVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 56.dp)
                .testTag("overlay_visibility_warning_bubble"),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SameViewOverlayScrim.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SameViewTextPrimary,
                    maxLines = 2
                )
            }
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
            .width(220.dp)
            .wrapContentHeight()
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
                    .background(SameViewOverlayScrim.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .testTag("format_mismatch_hint_icon"),
                    tint = SameViewTextPrimary.copy(alpha = 0.82f)
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
                    .background(SameViewOverlayScrim.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SameViewTextPrimary,
                    maxLines = 2
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
    val handleClick = onClick
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SameViewOverlayScrim)
            .widthIn(min = CameraSecondaryActionMinWidth)
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
            .semantics {
                testTag = "reference_action"
                contentDescription = referenceLabel
                this.onClick { handleClick(); true }
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
                tint = SameViewTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.select_reference_image_label),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) SameViewTextPrimary else SameViewTextSecondary
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
                    color = SameViewTextPrimary
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
    referenceMarkersState: ReferenceMarkersState = ReferenceMarkersState(),
    onEnterMarkerEditMode: () -> Unit = {},
    onHideMarkers: () -> Unit = {},
    onShowMarkers: () -> Unit = {},
    onGuideTipAnchor: (GuideTipAnchor) -> Unit = {},
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
    val addMarkersLabel = stringResource(R.string.markers_add_action)
    val hideMarkersLabel = stringResource(R.string.markers_hide_action)
    val showMarkersLabel = stringResource(R.string.markers_show_action)
    val editMarkersLabel = stringResource(R.string.markers_edit_action)
    val rowVerticalPadding = if (isCompact) 6.dp else 10.dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SameViewOverlayScrim)
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
                tint = SameViewTextPrimary
            )
            Text(
                text = stringResource(R.string.action_stack_reset_label),
                style = MaterialTheme.typography.bodyMedium,
                color = SameViewTextPrimary
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
                tint = SameViewTextPrimary
            )
            Text(
                text = displayModeText,
                style = MaterialTheme.typography.bodyMedium,
                color = SameViewTextPrimary
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
                tint = SameViewTextPrimary
            )
            Text(
                text = stringResource(R.string.action_stack_replace_label),
                style = MaterialTheme.typography.bodyMedium,
                color = SameViewTextPrimary
            )
        }
        // Marker menu items — state machine based on markersExist / markersVisible
        HorizontalDivider(color = SameViewTextPrimary.copy(alpha = 0.2f))
        when {
            !referenceMarkersState.markersExist -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .guideTipAnchor(GuideTipAnchorKey.MARKER_ACTION, onGuideTipAnchor)
                        .clickable(onClick = onEnterMarkerEditMode)
                        .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                        .semantics(mergeDescendants = true) {
                            contentDescription = addMarkersLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = SameViewTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.markers_add_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SameViewTextPrimary
                    )
                }
            }
            referenceMarkersState.markersVisible -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onHideMarkers)
                        .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                        .semantics(mergeDescendants = true) {
                            contentDescription = hideMarkersLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = SameViewTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.markers_hide_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SameViewTextPrimary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .guideTipAnchor(GuideTipAnchorKey.MARKER_ACTION, onGuideTipAnchor)
                        .clickable(onClick = onEnterMarkerEditMode)
                        .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                        .semantics(mergeDescendants = true) {
                            contentDescription = editMarkersLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = SameViewTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.markers_edit_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SameViewTextPrimary
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowMarkers)
                        .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                        .semantics(mergeDescendants = true) {
                            contentDescription = showMarkersLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = SameViewTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.markers_show_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SameViewTextPrimary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .guideTipAnchor(GuideTipAnchorKey.MARKER_ACTION, onGuideTipAnchor)
                        .clickable(onClick = onEnterMarkerEditMode)
                        .padding(horizontal = 14.dp, vertical = rowVerticalPadding)
                        .semantics(mergeDescendants = true) {
                            contentDescription = editMarkersLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = SameViewTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.markers_edit_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SameViewTextPrimary
                    )
                }
            }
        }
        HorizontalDivider(color = SameViewTextPrimary.copy(alpha = 0.2f))
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

@Composable
private fun CameraGridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier,
    lineColor: Color = SameViewGridLine,
    lineWidth: Dp = CameraGridLineWidth,
) {
    if (gridType == GridType.NONE) return
    Canvas(modifier = modifier) {
        val strokePx = lineWidth.toPx()
        val positions: List<Float> = when (gridType) {
            GridType.NONE -> emptyList()
            GridType.RULE_OF_THIRDS -> listOf(1f / 3f, 2f / 3f)
            GridType.QUARTERS -> listOf(1f / 4f, 2f / 4f, 3f / 4f)
        }
        for (fraction in positions) {
            val x = size.width * fraction
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokePx,
            )
            val y = size.height * fraction
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokePx,
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            .size(CameraShutterButtonSize)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onCapture) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(Color.White.copy(alpha = contentAlpha), CircleShape)
                .border(
                    width = 4.dp,
                    color = Color.White.copy(alpha = 0.45f * contentAlpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.capture_button_content_description),
                tint = Color.Black.copy(alpha = contentAlpha)
            )
        }
    }
}

/**
 * Draws the edit-mode border around the visible reference-image rectangle clipped to the viewport.
 *
 * When [metadata] is null (no reference image loaded yet), falls back to a full-viewport border.
 * The border follows [overlayScale] and [overlayOffsetX]/[overlayOffsetY] and applies in all
 * display modes. Letterbox and pillarbox areas are excluded.
 */
@Composable
internal fun MarkerEditBorder(
    isEditModeActive: Boolean,
    metadata: ReferenceImageMetadata?,
    displayMode: ReferenceImageDisplayMode,
    overlayOffsetX: Float,
    overlayOffsetY: Float,
    overlayScale: Float,
    modifier: Modifier = Modifier
) {
    if (!isEditModeActive) return

    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
    ) {
        val vW = viewportSize.width.toFloat()
        val vH = viewportSize.height.toFloat()

        if (vW > 0f && vH > 0f) {
            val meta = metadata
            if (meta != null && meta.orientedWidth > 0 && meta.orientedHeight > 0) {
                val rect = computeVisibleImageRect(
                    viewportWidth = vW,
                    viewportHeight = vH,
                    imageWidth = meta.orientedWidth.toFloat(),
                    imageHeight = meta.orientedHeight.toFloat(),
                    displayMode = displayMode,
                    overlayOffsetX = overlayOffsetX,
                    overlayOffsetY = overlayOffsetY,
                    overlayScale = overlayScale
                )
                Box(
                    modifier = Modifier
                        .absoluteOffset {
                            IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                        }
                        .size(
                            width = with(density) { (rect.right - rect.left).toDp() },
                            height = with(density) { (rect.bottom - rect.top).toDp() }
                        )
                        .border(width = 2.dp, color = SameViewAccent)
                        .testTag("marker_edit_border")
                )
            } else {
                // Fallback: border around the full viewport when no image metadata available
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 2.dp, color = SameViewAccent)
                        .testTag("marker_edit_border")
                )
            }
        }
    }
}

@Composable
private fun MarkerDoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.markers_done_button)
    Box(
        modifier = modifier.height(CameraShutterButtonSize),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SameViewAccent)
                .testTag("marker_done_button")
                .semantics { contentDescription = label }
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}














private fun Modifier.guideTipAnchor(
    key: GuideTipAnchorKey,
    onAnchor: (GuideTipAnchor) -> Unit
): Modifier = onGloballyPositioned { coordinates ->
    onAnchor(GuideTipAnchor(key = key, bounds = coordinates.boundsInRoot()))
}






