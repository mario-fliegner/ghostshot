package com.isardomains.sameview.ui.compare

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.isardomains.sameview.R
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
import com.isardomains.sameview.ui.theme.SameViewStarFavorited
import com.isardomains.sameview.ui.theme.SameViewAppSurface
import com.isardomains.sameview.ui.theme.SameViewAppSurfaceElevated
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalBadgeBackground
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalBadgeBackgroundActive
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalBadgeContent
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalBadgeContentActive
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalLabelBackground
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalLabelContent
import com.isardomains.sameview.ui.theme.SameViewCompareOriginalLetterboxBackground
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import com.isardomains.sameview.ui.theme.SameViewTextSecondary
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

private const val InitialSliderFraction = 0.5f
private const val ReferenceFileName = "reference.jpg"
private const val ReferenceOriginalFileName = "reference-original.jpg"
private const val TransformEpsilon = 0.01f
private const val AspectRatioMismatchThreshold = 0.05f
private val CompareViewportCornerRadius = 8.dp
private const val CompareSliderRingGapAngle = 12f
private val CompareSliderTouchWidth = 56.dp
private val CompareSliderHandleSize = 48.dp
private val CompareSliderRingThickness = 2.dp
private val CompareSliderRingGap = 1.dp
private val CompareBadgeHeight = 32.dp
private val CompareHandleLabelGap = 8.dp

/**
 * Fullscreen compare screen for the V1 slider compare flow.
 *
 * Uses a single shared viewport for both images so reference and capture always
 * render with the same container, alignment, and scaling logic.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CompareScreen(
    referenceImageUri: Uri?,
    captureImageUri: Uri?,
    onBack: () -> Unit,
    timestamp: Long? = null,
    onDelete: (() -> Unit)? = null,
    sessionTitle: String? = null,
    onEditSession: (() -> Unit)? = null,
    sessionId: String? = null,
    onBackupSession: ((Uri) -> Unit)? = null,
    isBackupInProgress: Boolean = false,
    onCreateVideo: (() -> Unit)? = null,
    isCreateVideoAvailable: Boolean = false,
    onShareComparisonImage: (() -> Unit)? = null,
    isShareComparisonAvailable: Boolean = false,
    referenceDate: String? = null,
    locationDisplayName: String? = null,
    locationCity: String? = null,
    locationCountry: String? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    guideTipController: GuideTipController? = null,
    onOpenGuideTopic: (GuideTopicId) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasValidInput = referenceImageUri != null && captureImageUri != null
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val compareContentScale = if (isFullscreen) ContentScale.Crop else ContentScale.Fit
    val guideTipScope = rememberCoroutineScope()
    var guideTipAnchors by remember { mutableStateOf<Map<GuideTipAnchorKey, GuideTipAnchor>>(emptyMap()) }
    var activeGuideTip by remember { mutableStateOf<GuideTip?>(null) }
    val onGuideTipAnchor: (GuideTipAnchor) -> Unit = { anchor ->
        guideTipAnchors = guideTipAnchors + (anchor.key to anchor)
    }

    val shareTipCompleted by remember(guideTipController) {
        guideTipController?.observeTipSeen(GuideTipId.SHARE) ?: flowOf(false)
    }.collectAsState(initial = false)
    var sliderInteractionDetected by remember { mutableStateOf(false) }
    var isSliderInteractionReady by remember { mutableStateOf(false) }
    var isEditSessionTipDelayReady by remember { mutableStateOf(false) }
    var compareViewportBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(sliderInteractionDetected) {
        if (sliderInteractionDetected) {
            delay(1000L)
            isSliderInteractionReady = true
        }
    }
    LaunchedEffect(Unit) {
        delay(1200L)
        isEditSessionTipDelayReady = true
    }

    val compareTipBlocked = isFullscreen || showExportMenu || showMoreMenu || showDeleteDialog || isBackupInProgress
    val compareEligibleTipIds = buildSet<GuideTipId> {
        if (sessionId != null && isSliderInteractionReady && !shareTipCompleted) add(GuideTipId.SHARE)
        if (sessionId != null && shareTipCompleted && isEditSessionTipDelayReady) add(GuideTipId.EDIT_SESSION)
    }.filter { tipId ->
        val tip = GuideTipRegistry.tipFor(tipId)
        tip == null || guideTipAnchors.containsKey(tip.anchorKey)
    }.toSet()
    LaunchedEffect(guideTipController, compareEligibleTipIds, compareTipBlocked, activeGuideTip?.id) {
        val controller = guideTipController ?: return@LaunchedEffect
        val currentTip = activeGuideTip
        if (currentTip != null && (compareTipBlocked || currentTip.id !in compareEligibleTipIds)) {
            controller.clearActiveTipWithoutMarkingSeen()
            activeGuideTip = null
            return@LaunchedEffect
        }
        if (currentTip == null) {
            activeGuideTip = controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.COMPARE,
                    eligibleTipIds = compareEligibleTipIds,
                    isBlockedByTransientUi = compareTipBlocked
                )
            )
        }
    }

    DisposableEffect(guideTipController) {
        onDispose {
            guideTipController?.clearActiveTipWithoutMarkingSeen()
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onBackupSession?.invoke(uri)
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.compare_screen_delete_dialog_title)) },
            text = { Text(stringResource(R.string.compare_screen_delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete?.invoke()
                    }
                ) {
                    Text(stringResource(R.string.compare_library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.compare_library_delete_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("compare_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isFullscreen) Modifier.systemBarsPadding() else Modifier)
        ) {
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("compare_screen_top_bar"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("compare_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.compare_back)
                        )
                    }
                    if (isLandscape) {
                        LandscapeTopBarMetadata(
                            title = sessionTitle,
                            locationDisplayName = locationDisplayName,
                            locationCity = locationCity,
                            locationCountry = locationCountry,
                            timestamp = timestamp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.compare_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        if (onToggleFavorite != null || onCreateVideo != null || onEditSession != null || sessionId != null || onDelete != null) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    // Favourite star — only when session context is present (onToggleFavorite != null)
                    if (onToggleFavorite != null) {
                        val starDescription = stringResource(
                            if (isFavorite) R.string.compare_screen_favorite_remove
                            else R.string.compare_screen_favorite_mark
                        )
                        IconButton(
                            onClick = { onToggleFavorite() },
                            modifier = Modifier.testTag("compare_screen_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = starDescription,
                                tint = if (isFavorite) SameViewStarFavorited
                                       else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    // Export dropdown — only when sessionId context is present
                    if (sessionId != null) {
                        Box {
                            IconButton(
                                onClick = {
                                    showExportMenu = true
                                    guideTipScope.launch { guideTipController?.completeTip(GuideTipId.SHARE) }
                                },
                                modifier = Modifier
                                    .testTag("compare_screen_export_button")
                                    .guideTipAnchor(GuideTipAnchorKey.SHARE_ACTION, onGuideTipAnchor)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.export_entry_content_description)
                                )
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                // 1. Share image
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_menu_share_comparison_image)) },
                                    enabled = isShareComparisonAvailable,
                                    onClick = {
                                        showExportMenu = false
                                        onShareComparisonImage?.invoke()
                                    },
                                    modifier = Modifier.testTag("compare_screen_export_share_item")
                                )
                                // 2. Create video
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_menu_create_video)) },
                                    enabled = isCreateVideoAvailable,
                                    onClick = {
                                        showExportMenu = false
                                        onCreateVideo?.invoke()
                                    },
                                    modifier = Modifier.testTag("compare_screen_export_create_video_item")
                                )
                            }
                        }
                    }
                    // Delete button — dedicated icon, not in overflow
                    if (onDelete != null) {
                        val deleteDescription = stringResource(R.string.compare_screen_delete)
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("compare_screen_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = deleteDescription
                            )
                        }
                    }
                    // Overflow menu (⋮) — Edit Session, Backup Session
                    if (onEditSession != null || sessionId != null) {
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .testTag("compare_screen_more_menu_button")
                                    .guideTipAnchor(GuideTipAnchorKey.OVERFLOW_ACTION, onGuideTipAnchor)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.compare_screen_more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                if (onEditSession != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_session_overflow_item)) },
                                        onClick = {
                                            showMoreMenu = false
                                            onEditSession.invoke()
                                        },
                                        modifier = Modifier.testTag("compare_screen_edit_session_item")
                                    )
                                }
                                if (sessionId != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.compare_screen_overflow_backup_session)) },
                                        enabled = !isBackupInProgress,
                                        onClick = {
                                            showMoreMenu = false
                                            createDocumentLauncher.launch("SameView_${sessionId}.zip")
                                        },
                                        modifier = Modifier.testTag("compare_screen_backup_session_item")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
                        Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxHeight()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    }
                ) {
                    if (!isFullscreen && !isLandscape) {
                        CompareMetadataHeader(
                            isLandscape = isLandscape,
                            title = sessionTitle,
                            locationDisplayName = locationDisplayName,
                            locationCity = locationCity,
                            locationCountry = locationCountry,
                            timestamp = timestamp
                        )
                    }

                    if (isLandscape) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = if (isFullscreen) 0.dp else 8.dp)
                        ) {
                            val density = LocalDensity.current
                            val maxWPx = with(density) { maxWidth.toPx() }
                            val maxHPx = with(density) { maxHeight.toPx() }
                            // Header is above BoxWithConstraints; no footer reserved inside.
                            val targetWidthPx = minOf(maxWPx, maxHPx * (16f / 9f))
                            val targetHeightPx = targetWidthPx * (9f / 16f)
                            val targetWidth = with(density) { targetWidthPx.toDp() }
                            val targetHeight = with(density) { targetHeightPx.toDp() }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = targetWidth, height = targetHeight)
                                        .onGloballyPositioned { compareViewportBounds = it.boundsInRoot() }
                                        .then(
                                            if (hasValidInput) Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    isFullscreen = !isFullscreen
                                                    guideTipController?.onUserAction()
                                                }
                                            } else Modifier
                                        )
                                ) {
                                    when {
                                        !hasValidInput -> CompareMessageFallback(
                                            title = stringResource(R.string.compare_error_missing_images),
                                            body = stringResource(R.string.compare_error_missing_images_body),
                                            testTag = "compare_missing_input_fallback"
                                        )
                                        else -> CompareSliderViewport(
                                            referenceImageUri = referenceImageUri!!,
                                            captureImageUri = captureImageUri!!,
                                            contentScale = compareContentScale,
                                            referenceDate = referenceDate,
                                            captureTimestampMs = timestamp ?: 0L,
                                            onMeaningfulSliderInteraction = {
                                                sliderInteractionDetected = true
                                                guideTipController?.onUserAction()
                                            },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("compare_screen_shell_content")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onGloballyPositioned { compareViewportBounds = it.boundsInRoot() }
                                .padding(
                                    start = if (isFullscreen) 0.dp else 24.dp,
                                    end = if (isFullscreen) 0.dp else 24.dp,
                                    top = if (isFullscreen) 0.dp else 24.dp,
                                    bottom = if (isFullscreen) 0.dp else 24.dp
                                )
                                .then(
                                    if (hasValidInput) Modifier.pointerInput(Unit) {
                                        detectTapGestures {
                                            isFullscreen = !isFullscreen
                                            guideTipController?.onUserAction()
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !hasValidInput -> CompareMessageFallback(
                                    title = stringResource(R.string.compare_error_missing_images),
                                    body = stringResource(R.string.compare_error_missing_images_body),
                                    testTag = "compare_missing_input_fallback"
                                )

                                else -> CompareSliderViewport(
                                    referenceImageUri = referenceImageUri!!,
                                    captureImageUri = captureImageUri!!,
                                    contentScale = compareContentScale,
                                    referenceDate = referenceDate,
                                    captureTimestampMs = timestamp ?: 0L,
                                    onMeaningfulSliderInteraction = {
                                        sliderInteractionDetected = true
                                        guideTipController?.onUserAction()
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("compare_screen_shell_content")
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = activeGuideTip != null,
            enter = fadeIn(animationSpec = tween(200, easing = FastOutLinearInEasing)),
            exit = fadeOut(animationSpec = tween(150, easing = LinearOutSlowInEasing))
        ) {
            GuideTipHost(
                activeTip = activeGuideTip,
                anchors = guideTipAnchors.values.toList(),
                windowWidthSizeClass = windowWidthSizeClass,
                isLandscape = isLandscape,
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
}

/**
 * Displays session metadata above the compare slider.
 *
 * Portrait: title (up to 2 lines) + location line; or "Created <date>" fallback.
 * Landscape: single line — title, or location, or "Created <date>" fallback.
 * Returns immediately with no layout contribution when there is nothing to show.
 */
@Composable
private fun CompareMetadataHeader(
    isLandscape: Boolean,
    title: String?,
    locationDisplayName: String?,
    locationCity: String?,
    locationCountry: String?,
    timestamp: Long?,
    modifier: Modifier = Modifier
) {
    val hasTitle = !title.isNullOrEmpty()
    val hasLocation = locationDisplayName != null || locationCity != null || locationCountry != null
    val showFallback = !hasTitle && !hasLocation && timestamp != null
    if (!hasTitle && !hasLocation && !showFallback) return

    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val titleStyle = MaterialTheme.typography.bodyLarge
    val secondaryStyle = MaterialTheme.typography.bodySmall

    val createdTemplate = stringResource(R.string.compare_screen_metadata_created)
    val formattedDate = remember(timestamp) {
        if (timestamp != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)) else ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 0.dp)
            .testTag("compare_screen_metadata_header")
    ) {
        if (isLandscape) {
            if (hasTitle) {
                Text(
                    text = title!!,
                    style = titleStyle,
                    color = onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_title")
                )
            }
            if (hasLocation) {
                PortraitLocationRow(
                    displayName = locationDisplayName,
                    city = locationCity,
                    country = locationCountry,
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = if (hasTitle) 2.dp else 0.dp)
                )
            }
            if (!hasTitle && !hasLocation && showFallback) {
                Text(
                    text = createdTemplate.format(formattedDate),
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_fallback")
                )
            }
        } else {
            if (hasTitle) {
                Text(
                    text = title!!,
                    style = titleStyle,
                    color = onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_title")
                )
            }
            if (hasLocation) {
                PortraitLocationRow(
                    displayName = locationDisplayName,
                    city = locationCity,
                    country = locationCountry,
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = if (hasTitle) 2.dp else 0.dp)
                )
            }
            if (!hasTitle && !hasLocation && showFallback) {
                Text(
                    text = createdTemplate.format(formattedDate),
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_fallback")
                )
            }
        }
    }
}

/**
 * Renders session metadata inline in the TopAppBar center slot for landscape mode.
 *
 * In landscape the separate [CompareMetadataHeader] is not rendered; this composable
 * occupies the TopAppBar center slot via [Modifier.weight], returning the header's
 * vertical space to the compare viewport. When user-authored metadata is present
 * (title and/or location), it is displayed. When absent, shows the screen title
 * "Compare" (titleLarge) as the primary line and "Created <date>" (bodySmall) as
 * a secondary line below it, preserving session context without displacing the title.
 */
@Composable
private fun LandscapeTopBarMetadata(
    title: String?,
    locationDisplayName: String?,
    locationCity: String?,
    locationCountry: String?,
    timestamp: Long?,
    modifier: Modifier = Modifier
) {
    val hasTitle = !title.isNullOrEmpty()
    val hasLocation = locationDisplayName != null || locationCity != null || locationCountry != null
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val secondaryStyle = MaterialTheme.typography.bodySmall
    val createdTemplate = stringResource(R.string.compare_screen_metadata_created)
    val formattedDate = remember(timestamp) {
        if (timestamp != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)) else ""
    }

    Column(modifier = modifier) {
        if (!hasTitle && !hasLocation) {
            Text(
                text = stringResource(R.string.compare_screen_title),
                style = MaterialTheme.typography.titleLarge,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (timestamp != null) {
                Text(
                    text = createdTemplate.format(formattedDate),
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_fallback")
                )
            }
        } else {
            if (hasTitle) {
                Text(
                    text = title!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_screen_metadata_title")
                )
            }
            if (hasLocation) {
                PortraitLocationRow(
                    displayName = locationDisplayName,
                    city = locationCity,
                    country = locationCountry,
                    style = secondaryStyle,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = if (hasTitle) 2.dp else 0.dp)
                )
            }
        }
    }
}

/** Portrait location line with smart reduction: tries combinations in priority order,
 *  picks the first that fits the available width to avoid mid-word ellipsis. */
@Composable
private fun PortraitLocationRow(
    displayName: String?,
    city: String?,
    country: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // Reserve 20dp for icon (16dp) + gap (4dp)
        val iconReservePx = with(density) { 20.dp.toPx() }
        val availableForTextPx = with(density) { maxWidth.toPx() } - iconReservePx
        val chosenText = remember(displayName, city, country, availableForTextPx, style) {
            pickBestLocationText(displayName, city, country, availableForTextPx, textMeasurer, style)
        }
        if (chosenText.isNotEmpty()) {
            MetadataLocationRow(
                text = chosenText,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** Renders a location icon + text row. */
@Composable
private fun MetadataLocationRow(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag("compare_screen_metadata_location")
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Returns location candidates in priority order (most informative first).
 * Used by [PortraitLocationRow] to pick the best-fitting string.
 */
private fun buildLocationCandidates(
    displayName: String?,
    city: String?,
    country: String?
): List<String> {
    val result = mutableListOf<String>()
    if (displayName != null) {
        if (city != null && country != null) result.add("$displayName · $city, $country")
        if (city != null) result.add("$displayName · $city")
        if (country != null && city == null) result.add("$displayName · $country")
        result.add(displayName)
    } else {
        if (city != null && country != null) result.add("$city, $country")
        if (city != null) result.add(city)
        if (country != null) result.add(country)
    }
    return result
}

/** Picks the first candidate whose rendered width fits within [availableWidthPx]. */
private fun pickBestLocationText(
    displayName: String?,
    city: String?,
    country: String?,
    availableWidthPx: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle
): String {
    val candidates = buildLocationCandidates(displayName, city, country)
    if (candidates.isEmpty()) return ""
    return candidates.firstOrNull { candidate ->
        textMeasurer.measure(candidate, style).size.width.toFloat() <= availableWidthPx
    } ?: candidates.last()
}

@Composable
private fun CompareSliderViewport(
    referenceImageUri: Uri,
    captureImageUri: Uri,
    contentScale: ContentScale = ContentScale.Fit,
    referenceDate: String? = null,
    captureTimestampMs: Long = 0L,
    onMeaningfulSliderInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val referencePainter = rememberAsyncImagePainter(
        model = referenceImageUri,
        imageLoader = imageLoader
    )
    val capturePainter = rememberAsyncImagePainter(
        model = captureImageUri,
        imageLoader = imageLoader
    )
    val originalReferenceUri = remember(referenceImageUri, context.filesDir) {
        resolveOriginalReferenceUri(context.filesDir, referenceImageUri)
    }
    val showOriginalReferenceBadge = remember(referenceImageUri, context.filesDir) {
        resolveOriginalReferenceBadgeEligible(context.filesDir, referenceImageUri)
    }
    val originalReferencePainter = rememberAsyncImagePainter(
        model = originalReferenceUri,
        imageLoader = imageLoader
    )
    val labelLocale = LocalConfiguration.current.locales.get(0)
    val labelPast = stringResource(R.string.compare_label_past)
    val labelPresent = stringResource(R.string.compare_label_present)
    val labelReference = stringResource(R.string.compare_label_reference)
    val labelCurrent = stringResource(R.string.compare_label_current)
    val compareLabels = remember(referenceDate, captureTimestampMs, labelLocale) {
        computeCompareLabels(
            referenceDate = referenceDate,
            captureTimestampMs = captureTimestampMs,
            locale = labelLocale,
            labelPast = labelPast,
            labelPresent = labelPresent,
            labelReference = labelReference,
            labelCurrent = labelCurrent
        )
    }

    var sliderFraction by rememberSaveable { mutableFloatStateOf(InitialSliderFraction) }
    var isOriginalPeekActive by remember { mutableStateOf(false) }

    val loadFailed =
        referencePainter.state is AsyncImagePainter.State.Error ||
            capturePainter.state is AsyncImagePainter.State.Error

    if (loadFailed) {
        CompareMessageFallback(
            title = stringResource(R.string.compare_error_load_failed),
            body = stringResource(R.string.compare_error_load_failed_body),
            testTag = "compare_load_failed_fallback"
        )
        return
    }

    Box(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val refIntrinsicSize = referencePainter.intrinsicSize
            val viewportAspect = if (
                refIntrinsicSize.width > 0f && refIntrinsicSize.height > 0f &&
                refIntrinsicSize.width.isFinite() && refIntrinsicSize.height.isFinite()
            ) {
                refIntrinsicSize.width / refIntrinsicSize.height
            } else if (maxWidth >= maxHeight) {
                16f / 9f
            } else {
                9f / 16f
            }
            val targetHeightFromWidth = maxWidth / viewportAspect
            val targetWidth = if (targetHeightFromWidth > maxHeight) {
                maxHeight * viewportAspect
            } else {
                maxWidth
            }
            val targetHeight = if (targetHeightFromWidth > maxHeight) {
                maxHeight
            } else {
                targetHeightFromWidth
            }

            val density = LocalDensity.current
            val targetWPx = with(density) { targetWidth.toPx() }
            val targetHPx = with(density) { targetHeight.toPx() }
            val imageBounds = computeFitBounds(
                containerWidthPx = targetWPx,
                containerHeightPx = targetHPx,
                imageWidthPx = refIntrinsicSize.width,
                imageHeightPx = refIntrinsicSize.height,
                contentScale = contentScale
            )
            val clipFraction = if (targetWPx > 0f) {
                (imageBounds.offsetXPx + imageBounds.widthPx * sliderFraction) / targetWPx
            } else sliderFraction

            val intrinsicSize = originalReferencePainter.intrinsicSize
            val peekContentScale = resolveOriginalReferencePeekContentScale(
                viewportWidth = maxWidth.value,
                viewportHeight = maxHeight.value,
                imageWidth = intrinsicSize.width,
                imageHeight = intrinsicSize.height
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = targetWidth, height = targetHeight)
                    .clip(RoundedCornerShape(CompareViewportCornerRadius))
                    .background(SameViewAppSurface)
                    .pointerInput(imageBounds) {
                        var dragStartMs = 0L
                        var horizontalDragPx = 0f
                        var meaningfulFired = false
                        detectDragGestures(
                            onDragStart = {
                                dragStartMs = System.currentTimeMillis()
                                horizontalDragPx = 0f
                                meaningfulFired = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                horizontalDragPx += abs(dragAmount.x)
                                sliderFraction = (sliderFraction + (dragAmount.x / imageBounds.widthPx))
                                    .coerceIn(0f, 1f)
                                if (!meaningfulFired &&
                                    horizontalDragPx > 8.dp.toPx() &&
                                    System.currentTimeMillis() - dragStartMs > 100L
                                ) {
                                    meaningfulFired = true
                                    onMeaningfulSliderInteraction()
                                }
                            }
                        )
                    }
                    .testTag("compare_viewport")
                    .semantics {
                        testTag = "compare_viewport"
                    }
            ) {
                CompareViewportImage(
                    painter = referencePainter,
                    imageContentDescription = stringResource(R.string.compare_label_reference),
                    imageTestTag = "compare_reference_image",
                    renderSurfaceTestTag = "compare_reference_surface",
                    contentScale = contentScale,
                    revealLeftFraction = clipFraction,
                    modifier = Modifier.matchParentSize()
                )
                CompareViewportImage(
                    painter = capturePainter,
                    imageContentDescription = stringResource(R.string.compare_label_capture),
                    imageTestTag = "compare_capture_image",
                    renderSurfaceTestTag = "compare_capture_surface",
                    contentScale = contentScale,
                    revealRightFraction = clipFraction,
                    modifier = Modifier.matchParentSize()
                )

                if (sliderFraction > 0f && showOriginalReferenceBadge) {
                    OriginalReferenceBadge(
                        isActive = isOriginalPeekActive,
                        contentDescription = stringResource(R.string.compare_show_original_reference),
                        activeStateDescription = stringResource(R.string.compare_original_reference_active),
                        onActiveChange = { isOriginalPeekActive = it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .height(CompareBadgeHeight)
                            .testTag("compare_original_reference_badge")
                    )
                }

                if (originalReferenceUri != null) {
                    OriginalReferencePeekOverlay(
                        painter = originalReferencePainter,
                        isActive = isOriginalPeekActive,
                        contentScale = peekContentScale,
                        modifier = Modifier.matchParentSize()
                    )
                }

                if (!isOriginalPeekActive) {
                    CompareDivider(
                        sliderFraction = sliderFraction,
                        imageOffsetXPx = imageBounds.offsetXPx,
                        imageRenderedWPx = imageBounds.widthPx,
                        imageOffsetYPx = imageBounds.offsetYPx,
                        imageRenderedHPx = imageBounds.heightPx,
                        viewportWidthPx = targetWPx,
                        leftLabel = compareLabels.left,
                        rightLabel = compareLabels.right,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareViewportImage(
    painter: AsyncImagePainter,
    imageContentDescription: String,
    imageTestTag: String,
    renderSurfaceTestTag: String,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
    revealLeftFraction: Float? = null,
    revealRightFraction: Float? = null,
    overlayContent: @Composable () -> Unit = {}
) {
    val revealModifier = when {
        revealLeftFraction != null -> Modifier.drawWithContent {
            clipRect(right = size.width * revealLeftFraction) {
                this@drawWithContent.drawContent()
            }
        }

        revealRightFraction != null -> Modifier.drawWithContent {
            clipRect(left = size.width * revealRightFraction) {
                this@drawWithContent.drawContent()
            }
        }

        else -> Modifier
    }

    Box(
        modifier = modifier
            .then(revealModifier)
            .testTag(renderSurfaceTestTag)
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = imageContentDescription,
            contentScale = contentScale,
            alignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .testTag(imageTestTag)
        )
        overlayContent()
    }
}

@Composable
private fun BoxScope.OriginalReferencePeekOverlay(
    painter: AsyncImagePainter,
    isActive: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.compare_original_reference)

    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(CompareViewportCornerRadius))
                .background(SameViewCompareOriginalLetterboxBackground)
                .testTag("compare_original_reference_image")
        ) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = label,
                contentScale = contentScale,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        }
    }

    if (isActive) {
        CompareOriginalReferenceLabel(
            text = label,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .testTag("compare_original_reference_label")
        )
    }
}

@Composable
private fun OriginalReferenceBadge(
    isActive: Boolean,
    contentDescription: String,
    activeStateDescription: String,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isActive) {
        SameViewCompareOriginalBadgeBackgroundActive
    } else {
        SameViewCompareOriginalBadgeBackground
    }
    val content = if (isActive) {
        SameViewCompareOriginalBadgeContentActive
    } else {
        SameViewCompareOriginalBadgeContent
    }

    Surface(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (isActive) {
                    stateDescription = activeStateDescription
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onActiveChange(true)
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    onActiveChange(false)
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = background
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompareOriginalReferenceLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = SameViewCompareOriginalLabelBackground
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = SameViewCompareOriginalLabelContent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CompareDivider(
    sliderFraction: Float,
    imageOffsetXPx: Float,
    imageRenderedWPx: Float,
    imageOffsetYPx: Float,
    imageRenderedHPx: Float,
    viewportWidthPx: Float,
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dividerXPx = imageOffsetXPx + imageRenderedWPx * sliderFraction
    val dividerXInt = dividerXPx.roundToInt()
    val imageHeightDp = with(density) { imageRenderedHPx.toDp() }
    val handleRadiusPx = with(density) {
        (CompareSliderHandleSize / 2 + CompareSliderRingGap + CompareSliderRingThickness).toPx()
    }
    val labelGapPx = with(density) { CompareHandleLabelGap.toPx() }

    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = MaterialTheme.typography.labelLarge.copy(
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.75f),
            offset = Offset(0f, 1f),
            blurRadius = 4f
        )
    )

    val leftMeasured = remember(leftLabel, labelTextStyle) {
        textMeasurer.measure(leftLabel, labelTextStyle)
    }
    val rightMeasured = remember(rightLabel, labelTextStyle) {
        textMeasurer.measure(rightLabel, labelTextStyle)
    }
    val leftLabelWidthPx = leftMeasured.size.width.toFloat()
    val rightLabelWidthPx = rightMeasured.size.width.toFloat()
    val labelHeightPx = leftMeasured.size.height.toFloat()

    val showLeftLabel = (dividerXPx - handleRadiusPx - labelGapPx - leftLabelWidthPx) >= 0f
    val showRightLabel = (dividerXPx + handleRadiusPx + labelGapPx + rightLabelWidthPx) <= viewportWidthPx

    val labelsDescription = stringResource(
        R.string.compare_slider_labels_content_description,
        leftLabel,
        rightLabel
    )

    // Outer Box: same width/height/offset as before so compare_slider bounds remain stable for tests.
    Box(
        modifier = modifier
            .width(CompareSliderTouchWidth)
            .height(imageHeightDp)
            .offset {
                IntOffset(
                    x = dividerXInt - (CompareSliderTouchWidth.roundToPx() / 2),
                    y = imageOffsetYPx.roundToInt()
                )
            }
            .testTag("compare_slider")
            .semantics {
                contentDescription = labelsDescription
                stateDescription = "${(sliderFraction * 100).roundToInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(sliderFraction, 0f..1f)
            }
    ) {
        // 1. Outer ring — two arcs with genuine gaps at top and bottom where the divider line runs through.
        val ringCanvasSize = CompareSliderHandleSize + CompareSliderRingGap * 2 + CompareSliderRingThickness * 2
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(ringCanvasSize)
        ) {
            val strokePx = CompareSliderRingThickness.toPx()
            val inset = strokePx / 2
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val gapDeg = CompareSliderRingGapAngle
            // Left half: clockwise from just past bottom (90°+gap) through left (180°) to just before top (270°-gap).
            drawArc(
                color = Color.White,
                startAngle = 90f + gapDeg,
                sweepAngle = 180f - 2 * gapDeg,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )
            // Right half: clockwise from just past top (270°+gap) through right (0°/360°) to just before bottom (360°+90°-gap).
            drawArc(
                color = Color.White,
                startAngle = 270f + gapDeg,
                sweepAngle = 180f - 2 * gapDeg,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )
        }

        // 2. Handle — white filled circle with blue ◀ ▶ arrows
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(CompareSliderHandleSize)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .testTag("compare_divider_handle"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(CompareSliderHandleSize)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                // All geometry expressed as fractions of the 48dp circle so density
                // scaling is automatic. Each arrow is centered 9dp from the midpoint,
                // extends 4dp toward its tip/base, and spans 7dp above/below center.
                val unit = size.width / 48f
                val arrowCenterOffset = unit * 9f
                val halfDepth = unit * 4f
                val halfH = unit * 7f
                val arrowStroke = Stroke(
                    width = unit * 2.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                // Left < : base-top → tip → base-bottom
                val leftPath = Path().apply {
                    moveTo(cx - arrowCenterOffset + halfDepth, cy - halfH)
                    lineTo(cx - arrowCenterOffset - halfDepth, cy)
                    lineTo(cx - arrowCenterOffset + halfDepth, cy + halfH)
                }
                // Right > : exact mirror of left arrow
                val rightPath = Path().apply {
                    moveTo(cx + arrowCenterOffset - halfDepth, cy - halfH)
                    lineTo(cx + arrowCenterOffset + halfDepth, cy)
                    lineTo(cx + arrowCenterOffset - halfDepth, cy + halfH)
                }
                drawPath(path = leftPath, color = SameViewAccent, style = arrowStroke)
                drawPath(path = rightPath, color = SameViewAccent, style = arrowStroke)
            }
        }

        // 3. Vertical divider line — rendered last so it sits on top of the ring, flowing through the open gaps.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.9f))
                .testTag("compare_divider_line")
        )

        // Left label — rendered outside the 56dp touch Box (no clipToBounds on this Box)
        if (showLeftLabel) {
            val touchHalfWidthPx = with(density) { CompareSliderTouchWidth.toPx() / 2 }
            val imageCenterRelY = imageRenderedHPx / 2f
            val labelXRelative = touchHalfWidthPx - handleRadiusPx - labelGapPx - leftLabelWidthPx
            val labelYRelative = imageCenterRelY - labelHeightPx / 2f
            Text(
                text = leftLabel,
                style = labelTextStyle,
                overflow = TextOverflow.Visible,
                softWrap = false,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = labelXRelative.roundToInt(),
                            y = labelYRelative.roundToInt()
                        )
                    }
                    .wrapContentHeight()
                    .testTag("compare_handle_label_left")
            )
        }

        // Right label
        if (showRightLabel) {
            val touchHalfWidthPx = with(density) { CompareSliderTouchWidth.toPx() / 2 }
            val imageCenterRelY = imageRenderedHPx / 2f
            val labelXRelative = touchHalfWidthPx + handleRadiusPx + labelGapPx
            val labelYRelative = imageCenterRelY - labelHeightPx / 2f
            Text(
                text = rightLabel,
                style = labelTextStyle,
                overflow = TextOverflow.Visible,
                softWrap = false,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = labelXRelative.roundToInt(),
                            y = labelYRelative.roundToInt()
                        )
                    }
                    .wrapContentHeight()
                    .testTag("compare_handle_label_right")
            )
        }
    }
}

@Composable
private fun CompareMessageFallback(
    title: String,
    body: String,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag(testTag),
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SameViewAppSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SameViewTextPrimary.copy(alpha = 0.88f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SameViewTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SameViewTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class FitBounds(
    val offsetXPx: Float,
    val offsetYPx: Float,
    val widthPx: Float,
    val heightPx: Float
)

private fun computeFitBounds(
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageWidthPx: Float,
    imageHeightPx: Float,
    contentScale: ContentScale
): FitBounds {
    if (contentScale == ContentScale.Crop ||
        imageWidthPx <= 0f || imageHeightPx <= 0f ||
        !imageWidthPx.isFinite() || !imageHeightPx.isFinite()) {
        return FitBounds(0f, 0f, containerWidthPx, containerHeightPx)
    }
    val scale = minOf(containerWidthPx / imageWidthPx, containerHeightPx / imageHeightPx)
    val renderedW = imageWidthPx * scale
    val renderedH = imageHeightPx * scale
    return FitBounds(
        offsetXPx = (containerWidthPx - renderedW) / 2f,
        offsetYPx = (containerHeightPx - renderedH) / 2f,
        widthPx = renderedW,
        heightPx = renderedH
    )
}

internal fun resolveOriginalReferencePeekContentScale(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): ContentScale {
    if (imageWidth <= 0f || imageHeight <= 0f || !imageWidth.isFinite() || !imageHeight.isFinite()) {
        return ContentScale.Fit
    }
    if (imageWidth == imageHeight) return ContentScale.Crop
    val viewportIsLandscape = viewportWidth >= viewportHeight
    val imageIsLandscape = imageWidth > imageHeight
    return if (viewportIsLandscape == imageIsLandscape) ContentScale.Crop else ContentScale.Fit
}

private fun resolveOriginalReferenceUri(filesDir: File, referenceImageUri: Uri): Uri? {
    if (referenceImageUri.scheme != "file") return null
    val referencePath = referenceImageUri.path ?: return null
    val referenceFile = File(referencePath)
    if (referenceFile.name != ReferenceFileName) return null

    val sessionDir = referenceFile.parentFile ?: return null
    val sessionsRoot = File(filesDir, "sessions")
    val isInsideSessions = runCatching {
        val sessionsRootPath = sessionsRoot.canonicalPath + File.separator
        val sessionDirPath = sessionDir.canonicalPath
        sessionDirPath.startsWith(sessionsRootPath)
    }.getOrDefault(false)
    if (!isInsideSessions) return null

    val originalFile = File(sessionDir, ReferenceOriginalFileName)
    return if (originalFile.exists() && originalFile.isFile) {
        Uri.fromFile(originalFile)
    } else {
        null
    }
}

private fun resolveOriginalReferenceBadgeEligible(filesDir: File, referenceImageUri: Uri): Boolean {
    if (referenceImageUri.scheme != "file") return false
    val referencePath = referenceImageUri.path ?: return false
    val referenceFile = File(referencePath)
    if (referenceFile.name != ReferenceFileName) return false
    val sessionDir = referenceFile.parentFile ?: return false
    val sessionsRoot = File(filesDir, "sessions")
    val isInsideSessions = runCatching {
        val sessionsRootPath = sessionsRoot.canonicalPath + File.separator
        val sessionDirPath = sessionDir.canonicalPath
        sessionDirPath.startsWith(sessionsRootPath)
    }.getOrDefault(false)
    if (!isInsideSessions) return false
    val originalFile = File(sessionDir, ReferenceOriginalFileName)
    if (!originalFile.exists() || !originalFile.isFile) return false
    val metadataFile = File(sessionDir, "metadata.json")
    if (!metadataFile.exists() || !metadataFile.isFile) return true
    return runCatching {
        val json = JSONObject(metadataFile.readText())
        val overlay = json.getJSONObject("overlay")
        val reference = json.getJSONObject("reference")
        val viewport = json.getJSONObject("viewport")
        hasOriginalReferenceTransform(
            scale = overlay.getDouble("scale").toFloat(),
            offsetX = overlay.getDouble("offsetX").toFloat(),
            offsetY = overlay.getDouble("offsetY").toFloat(),
            displayMode = overlay.getString("displayMode"),
            orientedWidth = reference.getInt("orientedWidth"),
            orientedHeight = reference.getInt("orientedHeight"),
            viewportWidth = viewport.getInt("width"),
            viewportHeight = viewport.getInt("height")
        )
    }.getOrDefault(true)
}

private fun hasOriginalReferenceTransform(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    displayMode: String,
    orientedWidth: Int,
    orientedHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int
): Boolean {
    if (abs(scale - 1f) > TransformEpsilon) return true
    if (abs(offsetX) > TransformEpsilon) return true
    if (abs(offsetY) > TransformEpsilon) return true
    if (displayMode == "COMPARE_WITH_PREVIEW" && orientedHeight > 0 && viewportHeight > 0) {
        val refAspect = orientedWidth.toFloat() / orientedHeight.toFloat()
        val vpAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        if (vpAspect > 0f) {
            val mismatch = abs(refAspect - vpAspect) / vpAspect
            if (mismatch > AspectRatioMismatchThreshold) return true
        }
    }
    return false
}

private fun Modifier.guideTipAnchor(
    key: GuideTipAnchorKey,
    onAnchor: (GuideTipAnchor) -> Unit
): Modifier = onGloballyPositioned { coordinates ->
    onAnchor(GuideTipAnchor(key = key, bounds = coordinates.boundsInRoot()))
}

