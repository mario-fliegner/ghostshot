package com.isardomains.ghostshot.ui.compare

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotAccent
import com.isardomains.ghostshot.ui.theme.GhostShotAppSurface
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val InitialSliderFraction = 0.5f
private val CompareSliderTouchWidth = 48.dp
private val CompareSliderHandleSize = 32.dp

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
    modifier: Modifier = Modifier
) {
    val hasValidInput = referenceImageUri != null && captureImageUri != null
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val compareContentScale = if (isFullscreen) ContentScale.Crop else ContentScale.Fit

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
                    Text(
                        text = stringResource(R.string.compare_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.weight(1f))
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
                                        text = stringResource(R.string.compare_error_missing_images),
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
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .testTag("compare_screen_timestamp")
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
                            text = stringResource(R.string.compare_error_missing_images),
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
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                            .testTag("compare_screen_timestamp")
                    )
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
    var sliderFraction by rememberSaveable { mutableFloatStateOf(InitialSliderFraction) }
    var viewportWidthPx by remember { mutableFloatStateOf(1f) }

    val loadFailed =
        referencePainter.state is AsyncImagePainter.State.Error ||
            capturePainter.state is AsyncImagePainter.State.Error

    if (loadFailed) {
        CompareMessageFallback(
            text = stringResource(R.string.compare_error_load_failed),
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

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = targetWidth, height = targetHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GhostShotAppSurface)
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
                    CompareLabelBadge(
                        text = stringResource(R.string.compare_label_reference),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .testTag("compare_reference_label")
                    )
                }
                if (sliderFraction < 1f) {
                    CompareLabelBadge(
                        text = stringResource(R.string.compare_label_capture),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .testTag("compare_capture_label")
                    )
                }

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

@Composable
private fun CompareViewportImage(
    painter: AsyncImagePainter,
    imageContentDescription: String,
    imageTestTag: String,
    renderSurfaceTestTag: String,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
    revealLeftFraction: Float? = null,
    revealRightFraction: Float? = null
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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(GhostShotAccent)
            )
        }
    }
}

@Composable
private fun CompareMessageFallback(
    text: String,
    testTag: String
) {
    Column(
        modifier = Modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
