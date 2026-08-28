// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt
package com.isardomains.sameview.ui.wackelbild

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.isardomains.sameview.R
import java.io.File
import kotlin.math.abs

private const val MAX_PREVIEW_HEIGHT_DP = 500
private const val SWIPE_THRESHOLD_DP = 24

/**
 * DeinWackelbild entry destination (Block 3 scope).
 *
 * Local tilt/swipe preview switching directly between the session's persisted `reference.jpg`
 * and `capture.jpg`. No date/HQ/network behavior exists yet — those are added in later blocks.
 *
 * @param viewModel Hilt ViewModel; owns file resolution and the tilt/swipe interaction state.
 * @param windowWidthSizeClass Used for the Expanded (>= 840 dp) max-width constraint.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WackelbildScreen(
    onBack: () -> Unit,
    viewModel: WackelbildViewModel = hiltViewModel(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val visibleImage by viewModel.visibleImage.collectAsState()
    WackelbildScreenContent(
        referenceFile = viewModel.referenceFile,
        captureFile = viewModel.captureFile,
        visibleImage = visibleImage,
        isSensorAvailable = viewModel.isSensorAvailable,
        onSwipeDetected = viewModel::onSwipeDetected,
        onAccessibilityToggle = viewModel::onAccessibilityToggle,
        onScreenActive = viewModel::onScreenActive,
        onScreenInactive = viewModel::onScreenInactive,
        onScreenLeft = viewModel::onScreenLeft,
        onBack = onBack,
        windowWidthSizeClass = windowWidthSizeClass
    )
}

/**
 * Stateless content for [WackelbildScreen], taking file/state/callbacks directly rather than a
 * Hilt ViewModel. Kept `internal` so instrumentation tests can exercise the exact production UI
 * without needing a Hilt-backed [WackelbildViewModel]/[androidx.lifecycle.SavedStateHandle].
 *
 * This composable owns lifecycle observation (`ON_RESUME`/`ON_PAUSE`/disposal) itself and calls
 * [onScreenActive]/[onScreenInactive]/[onScreenLeft] directly — [WackelbildViewModel] has no
 * dependency on any Android lifecycle type.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun WackelbildScreenContent(
    referenceFile: File,
    captureFile: File,
    visibleImage: WackelbildImageSide,
    isSensorAvailable: Boolean,
    onSwipeDetected: () -> Unit,
    onAccessibilityToggle: () -> Unit,
    onScreenActive: () -> Unit,
    onScreenInactive: () -> Unit,
    onScreenLeft: () -> Unit,
    onBack: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScreenActive by rememberUpdatedState(onScreenActive)
    val currentOnScreenInactive by rememberUpdatedState(onScreenInactive)
    val currentOnScreenLeft by rememberUpdatedState(onScreenLeft)

    // Screen/composable-owned lifecycle observation — active while resumed, stopped while
    // paused/backgrounded, fully released when this screen leaves composition.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentOnScreenActive()
                Lifecycle.Event.ON_PAUSE -> currentOnScreenInactive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(Unit) {
        onDispose { currentOnScreenLeft() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wackelbild_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("wackelbild_back_button")
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
        val contentMaxWidth =
            if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 680.dp else Dp.Unspecified

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("wackelbild_screen_root"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Structurally outside the scrollable content below (see WackelbildInteractionHint
            // Column) so the swipe gesture never contends with vertical scroll.
            WackelbildPreview(
                referenceFile = referenceFile,
                captureFile = captureFile,
                visibleImage = visibleImage,
                onSwipeDetected = onSwipeDetected,
                onAccessibilityToggle = onAccessibilityToggle,
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = contentMaxWidth)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WackelbildInteractionHint(isSensorAvailable = isSensorAvailable)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Local, direct-switch Reference/Capture preview. Both files are requested unconditionally (so
 * both are decoded/cached ahead of time and switching is instant, per spec: no fade, no
 * animation), but only the currently visible one is placed in the composition. Sized from the
 * Reference image's own intrinsic aspect ratio once successfully decoded — never from session
 * metadata and never from a hardcoded fallback ratio (Reference and Capture share the same
 * aspect ratio by construction of the capture pipeline, so this stays stable across a toggle).
 */
@Composable
private fun WackelbildPreview(
    referenceFile: File,
    captureFile: File,
    visibleImage: WackelbildImageSide,
    onSwipeDetected: () -> Unit,
    onAccessibilityToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var referenceLoadState by remember(referenceFile) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    var captureLoadState by remember(captureFile) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    // Size.ORIGINAL: this preview decides its own layout size *from* the decoded image, so the
    // request must not wait on Coil's default layout-constraints size resolver.
    val referenceRequest = remember(referenceFile) {
        ImageRequest.Builder(context).data(referenceFile).size(Size.ORIGINAL).build()
    }
    val captureRequest = remember(captureFile) {
        ImageRequest.Builder(context).data(captureFile).size(Size.ORIGINAL).build()
    }

    val referencePainter = rememberAsyncImagePainter(
        model = referenceRequest,
        onState = { referenceLoadState = it }
    )
    val capturePainter = rememberAsyncImagePainter(
        model = captureRequest,
        onState = { captureLoadState = it }
    )

    val referenceRatio = intrinsicRatioOf(referenceLoadState)
    val captureRatio = intrinsicRatioOf(captureLoadState)

    val referenceFailed = hasFailed(referenceLoadState, referenceRatio)
    val captureFailed = hasFailed(captureLoadState, captureRatio)

    if (referenceFailed || captureFailed) {
        WackelbildPreviewFallback(modifier = modifier)
        return
    }

    if (referenceRatio == null || captureRatio == null) {
        // Still loading local files — near-instant; no dedicated loading UI needed.
        return
    }

    val currentOnSwipeDetected by rememberUpdatedState(onSwipeDetected)
    val currentOnAccessibilityToggle by rememberUpdatedState(onAccessibilityToggle)
    val referenceLabel = stringResource(R.string.compare_label_reference)
    val captureLabel = stringResource(R.string.compare_label_capture)
    val toggleActionLabel = stringResource(R.string.wackelbild_accessibility_toggle_action)

    BoxWithConstraints(
        modifier = modifier.testTag("wackelbild_reference_preview_container")
    ) {
        val availableW = maxWidth
        val compH = (availableW / referenceRatio).coerceAtMost(MAX_PREVIEW_HEIGHT_DP.dp)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(availableW)
                .height(compH)
                .pointerInput(Unit) {
                    val thresholdPx = SWIPE_THRESHOLD_DP.dp.toPx()
                    var totalDx = 0f
                    var totalDy = 0f
                    var fired = false
                    detectDragGestures(
                        onDragStart = {
                            totalDx = 0f
                            totalDy = 0f
                            fired = false
                        },
                        onDrag = { change, dragAmount ->
                            totalDx += dragAmount.x
                            totalDy += dragAmount.y
                            // Only ever consume/act once the gesture is clearly horizontal —
                            // an ambiguous or clearly-vertical gesture is left unconsumed.
                            val isHorizontalIntent = abs(totalDx) > abs(totalDy)
                            if (isHorizontalIntent) {
                                if (!fired && abs(totalDx) > thresholdPx) {
                                    fired = true
                                    currentOnSwipeDetected()
                                }
                                change.consume()
                            }
                        }
                    )
                }
                // Not merged: the child Image already sets contentDescription = null (no
                // meaningful child semantics to fold in), and merging here would hide the
                // Image's own testTag from onNodeWithTag's default merged-tree queries.
                .semantics {
                    contentDescription =
                        if (visibleImage == WackelbildImageSide.REFERENCE) referenceLabel else captureLabel
                    customActions = listOf(
                        CustomAccessibilityAction(toggleActionLabel) {
                            currentOnAccessibilityToggle()
                            true
                        }
                    )
                }
                .testTag("wackelbild_preview_interactive_area"),
            contentAlignment = Alignment.Center
        ) {
            when (visibleImage) {
                WackelbildImageSide.REFERENCE -> Image(
                    painter = referencePainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wackelbild_reference_image")
                )
                WackelbildImageSide.CAPTURE -> Image(
                    painter = capturePainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wackelbild_capture_image")
                )
            }
        }
    }
}

private fun intrinsicRatioOf(state: AsyncImagePainter.State): Float? {
    val success = state as? AsyncImagePainter.State.Success ?: return null
    val w = success.result.drawable.intrinsicWidth
    val h = success.result.drawable.intrinsicHeight
    return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
}

private fun hasFailed(state: AsyncImagePainter.State, intrinsicRatio: Float?): Boolean =
    state is AsyncImagePainter.State.Error ||
        (state is AsyncImagePainter.State.Success && intrinsicRatio == null)

@Composable
private fun WackelbildPreviewFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(MAX_PREVIEW_HEIGHT_DP.dp)
            .testTag("wackelbild_preview_fallback"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wackelbild_preview_error_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wackelbild_preview_error_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Tilt hint when a suitable sensor exists, swipe hint otherwise. Same supporting text either way. */
@Composable
private fun WackelbildInteractionHint(isSensorAvailable: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(
                if (isSensorAvailable) {
                    R.string.wackelbild_hint_tilt_title
                } else {
                    R.string.wackelbild_hint_swipe_title
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("wackelbild_hint_title")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wackelbild_hint_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("wackelbild_hint_subtitle")
        )
    }
}
