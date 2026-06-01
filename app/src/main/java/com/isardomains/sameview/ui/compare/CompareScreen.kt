package com.isardomains.sameview.ui.compare

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewAccent
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
private val CompareSliderTouchWidth = 48.dp
private val CompareSliderHandleSize = 32.dp
private val CompareBadgeHeight = 32.dp

/**
 * Fullscreen compare screen for the V1 slider compare flow.
 *
 * Uses a single shared viewport for both images so reference and capture always
 * render with the same container, alignment, and scaling logic.
 */
@Composable
fun CompareScreen(
    referenceImageUri: Uri?,
    captureImageUri: Uri?,
    onBack: () -> Unit,
    timestamp: Long? = null,
    onDelete: (() -> Unit)? = null,
    sessionTitle: String? = null,
    onSaveTitle: ((String?) -> Unit)? = null,
    sessionId: String? = null,
    onBackupSession: ((Uri) -> Unit)? = null,
    isBackupInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasValidInput = referenceImageUri != null && captureImageUri != null
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf(sessionTitle) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val compareContentScale = if (isFullscreen) ContentScale.Crop else ContentScale.Fit

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onBackupSession?.invoke(uri)
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text(stringResource(R.string.compare_screen_edit_title_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { if (it.length <= 60) titleInput = it },
                    label = { Text(stringResource(R.string.compare_screen_edit_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.testTag("compare_screen_title_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = titleInput.trim().ifEmpty { null }
                        currentTitle = trimmed
                        onSaveTitle?.invoke(trimmed)
                        showTitleDialog = false
                    }
                ) {
                    Text(stringResource(R.string.compare_screen_edit_title_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) {
                    Text(stringResource(R.string.compare_screen_edit_title_cancel))
                }
            }
        )
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
                    Text(
                        text = stringResource(R.string.compare_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (onSaveTitle != null || sessionId != null || onDelete != null) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (onSaveTitle != null || sessionId != null) {
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("compare_screen_more_menu_button")
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
                                if (onSaveTitle != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.compare_screen_edit_title)) },
                                        onClick = {
                                            titleInput = currentTitle ?: ""
                                            showMoreMenu = false
                                            showTitleDialog = true
                                        }
                                    )
                                    if (!currentTitle.isNullOrEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.compare_screen_remove_title)) },
                                            onClick = {
                                                showMoreMenu = false
                                                currentTitle = null
                                                onSaveTitle.invoke(null)
                                            },
                                            modifier = Modifier.testTag("compare_screen_remove_title_item")
                                        )
                                    }
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
                }
            }

            if (isLandscape) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val density = LocalDensity.current
                    val maxWPx = with(density) { maxWidth.toPx() }
                    val maxHPx = with(density) { maxHeight.toPx() }
                    val reservedHeightPx = if (!isFullscreen && timestamp != null) with(density) { 48.dp.toPx() } else 0f
                    val effectiveMaxH = maxHPx - reservedHeightPx
                    val targetWidthPx = minOf(maxWPx, effectiveMaxH * (16f / 9f))
                    val targetHeightPx = targetWidthPx * (9f / 16f)
                    val targetWidth = with(density) { targetWidthPx.toDp() }
                    val targetHeight = with(density) { targetHeightPx.toDp() }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.width(targetWidth)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = targetWidth, height = targetHeight)
                                    .then(
                                        if (hasValidInput) Modifier.pointerInput(Unit) {
                                            detectTapGestures { isFullscreen = !isFullscreen }
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
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("compare_screen_shell_content")
                                    )
                                }
                            }
                            if (!isFullscreen && timestamp != null) {
                                val formatted = remember(timestamp) {
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(timestamp))
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    if (!currentTitle.isNullOrEmpty()) {
                                        Text(
                                            text = currentTitle!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("compare_screen_session_title")
                                        )
                                    }
                                    Text(
                                        text = formatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("compare_screen_timestamp")
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            start = if (isFullscreen) 0.dp else 24.dp,
                            end = if (isFullscreen) 0.dp else 24.dp,
                            top = if (isFullscreen) 0.dp else 24.dp,
                            bottom = if (isFullscreen) 0.dp else if (timestamp != null) 0.dp else 24.dp
                        )
                        .then(
                            if (hasValidInput) Modifier.pointerInput(Unit) {
                                detectTapGestures { isFullscreen = !isFullscreen }
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
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("compare_screen_shell_content")
                        )
                    }
                }

                if (!isFullscreen && timestamp != null) {
                    val formatted = remember(timestamp) {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(timestamp))
                    }
                    Column(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                    ) {
                        if (!currentTitle.isNullOrEmpty()) {
                            Text(
                                text = currentTitle!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("compare_screen_session_title")
                            )
                        }
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("compare_screen_timestamp")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareSliderViewport(
    referenceImageUri: Uri,
    captureImageUri: Uri,
    contentScale: ContentScale = ContentScale.Fit,
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
    var sliderFraction by rememberSaveable { mutableFloatStateOf(InitialSliderFraction) }
    var viewportWidthPx by remember { mutableFloatStateOf(1f) }
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
            val viewportAspect = if (maxWidth >= maxHeight) {
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
                    .onSizeChanged { size ->
                        viewportWidthPx = size.width.coerceAtLeast(1).toFloat()
                    }
                    .pointerInput(viewportWidthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            sliderFraction = (sliderFraction + (dragAmount.x / viewportWidthPx))
                                .coerceIn(0f, 1f)
                        }
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
                    revealLeftFraction = sliderFraction,
                    modifier = Modifier.matchParentSize()
                )
                CompareViewportImage(
                    painter = capturePainter,
                    imageContentDescription = stringResource(R.string.compare_label_capture),
                    imageTestTag = "compare_capture_image",
                    renderSurfaceTestTag = "compare_capture_surface",
                    contentScale = contentScale,
                    revealRightFraction = sliderFraction,
                    modifier = Modifier.matchParentSize()
                )

                if (sliderFraction > 0f) {
                    ReferenceBadgeGroup(
                        hasOriginalReference = showOriginalReferenceBadge,
                        isOriginalPeekActive = isOriginalPeekActive,
                        onOriginalPeekActiveChange = { isOriginalPeekActive = it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .testTag("compare_reference_badge_group")
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
                if (sliderFraction < 1f && !isOriginalPeekActive) {
                    CompareLabelBadge(
                        text = stringResource(R.string.compare_label_capture),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .testTag("compare_capture_label")
                    )
                }

                if (!isOriginalPeekActive) {
                    CompareDivider(
                        sliderFraction = sliderFraction,
                        viewportWidthPx = viewportWidthPx,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxHeight()
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
private fun ReferenceBadgeGroup(
    hasOriginalReference: Boolean,
    isOriginalPeekActive: Boolean,
    onOriginalPeekActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .then(modifier)
            .height(CompareBadgeHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompareLabelBadge(
            text = stringResource(R.string.compare_label_reference),
            modifier = Modifier
                .fillMaxHeight()
                .testTag("compare_reference_label")
        )
        if (hasOriginalReference) {
            OriginalReferenceBadge(
                isActive = isOriginalPeekActive,
                contentDescription = stringResource(R.string.compare_show_original_reference),
                activeStateDescription = stringResource(R.string.compare_original_reference_active),
                onActiveChange = onOriginalPeekActiveChange,
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag("compare_original_reference_badge")
            )
        }
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
private fun CompareLabelBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CompareDivider(
    sliderFraction: Float,
    viewportWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val dividerOffsetPx = (viewportWidthPx * sliderFraction).roundToInt()
    val sliderDescription = stringResource(R.string.compare_slider_content_description)

    Box(
        modifier = modifier
            .width(CompareSliderTouchWidth)
            .offset {
                IntOffset(
                    x = dividerOffsetPx - (CompareSliderTouchWidth.roundToPx() / 2),
                    y = 0
                )
            }
            .testTag("compare_slider")
            .semantics {
                contentDescription = sliderDescription
                stateDescription = "${(sliderFraction * 100).roundToInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(sliderFraction, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.9f))
                .testTag("compare_divider_line")
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(CompareSliderHandleSize)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .testTag("compare_divider_handle"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(SameViewTextPrimary.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(SameViewAccent)
                )
            }
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
