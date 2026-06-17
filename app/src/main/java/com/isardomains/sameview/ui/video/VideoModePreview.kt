// path: app/src/main/java/com/isardomains/sameview/ui/video/VideoModePreview.kt
package com.isardomains.sameview.ui.video

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.isardomains.sameview.video.VideoMode
import java.io.File

private val VideoPreviewBackground = Color(0xFF17202F)
private const val LOOP_DURATION_MS = 4000

// Compare Slider phase boundaries (fraction of total loop)
private const val CS_HOLD_REF_END = 0.15f
private const val CS_SWEEP_END = 0.60f

// Before & After phase boundaries (fraction of total loop)
// 500 ms crossfade / 4000 ms total = 0.125
private const val BA_HOLD_REF_END = 0.15f
private const val BA_CROSSFADE_FRACTION = 0.125f
private val BA_CROSSFADE_END = BA_HOLD_REF_END + BA_CROSSFADE_FRACTION

// Preview overlay text timing — more generous than the export spec to give users
// time to read the text. [0, 0.20) = 800 ms full opacity; [0.20, 0.25) = 200 ms fade-out.
private const val PREVIEW_OVERLAY_FULL_END = 0.20f
private const val PREVIEW_OVERLAY_FADE_END = 0.25f

/**
 * Animated 16:9 preview frame shown inside the Video Type settings card, directly below the
 * mode segment control. Demonstrates the selected [mode] by loading real session images from
 * [sessionDir] (reference.jpg / capture.jpg) and playing a looping Compose animation.
 *
 * When [previewLines] is non-empty, overlay text lines are shown at the very start of each
 * loop cycle using a compact readable size, then fade out before the main animation begins.
 * This is a UX preview — not a pixel-accurate export simulation.
 *
 * A single [InfiniteTransition] drives both the mode animation and the overlay so they are
 * always in sync regardless of mode switches or toggle state changes.
 *
 * This is a decorative selection aid — excluded from the accessibility tree via
 * [clearAndSetSemantics] and carries no semantic information.
 *
 * Layout: 16:9 aspect, max height 200 dp, horizontally centred within the available width.
 */
@Composable
fun VideoModePreview(
    mode: VideoMode,
    sessionDir: File,
    previewLines: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    // Single shared InfiniteTransition — drives both the mode animation and the overlay text,
    // keeping them permanently in sync. Eliminates phase drift from independent transitions.
    val mainTransition = rememberInfiniteTransition(label = "previewAnim")
    val progress by mainTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "previewProgress"
    )

    // Overlay alpha: visible from frame 0, fades before the sweep begins.
    val overlayAlpha = when {
        previewLines.isEmpty() -> 0f
        reduceMotion -> 1f
        progress < PREVIEW_OVERLAY_FULL_END -> 1f
        progress < PREVIEW_OVERLAY_FADE_END -> {
            1f - (progress - PREVIEW_OVERLAY_FULL_END) / (PREVIEW_OVERLAY_FADE_END - PREVIEW_OVERLAY_FULL_END)
        }
        else -> 0f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
    ) {
        val heightFromWidth = maxWidth * (9f / 16f)
        val effectiveHeight = heightFromWidth.coerceAtMost(200.dp)
        val effectiveWidth = if (heightFromWidth <= 200.dp) maxWidth else 200.dp * (16f / 9f)

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = mode,
                animationSpec = tween(durationMillis = 175),
                label = "videoModePreview",
                modifier = Modifier
                    .size(width = effectiveWidth, height = effectiveHeight)
                    .background(VideoPreviewBackground)
            ) { currentMode ->
                when (currentMode) {
                    VideoMode.COMPARE_SLIDER -> if (reduceMotion) {
                        CompareSliderStatic(sessionDir)
                    } else {
                        CompareSliderAnimated(sessionDir, progress)
                    }
                    VideoMode.BEFORE_AFTER -> if (reduceMotion) {
                        BeforeAfterStatic(sessionDir)
                    } else {
                        BeforeAfterAnimated(sessionDir, progress)
                    }
                    VideoMode.FLASH -> if (reduceMotion) {
                        FlashStatic(sessionDir)
                    } else {
                        FlashAnimated(sessionDir, progress)
                    }
                }
            }

            // Overlay text: UX preview of active Extras — fixed readable size, compact spacing.
            if (previewLines.isNotEmpty() && overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(width = effectiveWidth, height = effectiveHeight)
                        .alpha(overlayAlpha),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = effectiveWidth * 0.04f,
                            bottom = effectiveHeight * 0.04f
                        )
                    ) {
                        previewLines.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Compare Slider ──────────────────────────────────────────────────────────────

/** Static fallback for Compare Slider: 50 % split with a visible divider line. */
@Composable
private fun CompareSliderStatic(sessionDir: File) {
    CompareSliderFrame(sessionDir = sessionDir, sliderPos = 0.5f)
}

/**
 * Renders the Compare Slider animation for the given loop [progress].
 * [progress] is provided by [VideoModePreview]'s single shared InfiniteTransition.
 */
@Composable
private fun CompareSliderAnimated(sessionDir: File, progress: Float) {
    CompareSliderFrame(sessionDir = sessionDir, sliderPos = sliderPosFromProgress(progress))
}

private class SliderClipShape(private val startFraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(left = size.width * startFraction, top = 0f, right = size.width, bottom = size.height))
}

/**
 * Renders a single Compare Slider frame at [sliderPos] (0.0 = full reference, 1.0 = full capture).
 * Reference fills the entire frame as the base layer; capture is clipped to the right portion.
 */
@Composable
private fun CompareSliderFrame(sessionDir: File, sliderPos: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(sessionDir, "reference.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (sliderPos > 0f) {
            AsyncImage(
                model = File(sessionDir, "capture.jpg"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SliderClipShape(sliderPos))
            )
        }
        if (sliderPos > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width * sliderPos
                drawLine(
                    color = Color.White,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

/** Cubic smoothstep easing: maps loop progress to a slider position in [0, 1]. */
private fun sliderPosFromProgress(progress: Float): Float = when {
    progress < CS_HOLD_REF_END -> 0f
    progress < CS_SWEEP_END -> {
        val t = (progress - CS_HOLD_REF_END) / (CS_SWEEP_END - CS_HOLD_REF_END)
        3f * t * t - 2f * t * t * t
    }
    else -> 1f
}

// ── Before & After ──────────────────────────────────────────────────────────────

/** Static fallback for Before & After: both images at 0.5 alpha overlaid. */
@Composable
private fun BeforeAfterStatic(sessionDir: File) {
    BeforeAfterFrame(sessionDir = sessionDir, alphaRef = 0.5f, alphaCap = 0.5f)
}

/**
 * Renders the Before & After animation for the given loop [progress].
 * [progress] is provided by [VideoModePreview]'s single shared InfiniteTransition.
 */
@Composable
private fun BeforeAfterAnimated(sessionDir: File, progress: Float) {
    val (alphaRef, alphaCap) = alphasFromProgress(progress)
    BeforeAfterFrame(sessionDir = sessionDir, alphaRef = alphaRef, alphaCap = alphaCap)
}

/**
 * Renders a single Before & After frame with the given alpha values.
 */
@Composable
private fun BeforeAfterFrame(sessionDir: File, alphaRef: Float, alphaCap: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(sessionDir, "reference.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(alphaRef)
        )
        AsyncImage(
            model = File(sessionDir, "capture.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(alphaCap)
        )
    }
}

/** Maps loop progress to (alphaRef, alphaCap) for the Before & After crossfade. */
private fun alphasFromProgress(progress: Float): Pair<Float, Float> = when {
    progress < BA_HOLD_REF_END -> 1f to 0f
    progress < BA_CROSSFADE_END -> {
        val cf = (progress - BA_HOLD_REF_END) / BA_CROSSFADE_FRACTION
        (1f - cf) to cf
    }
    else -> 0f to 1f
}

// ── Flash ────────────────────────────────────────────────────────────────────────

/** Static fallback for Flash: both images at 0.5 alpha overlaid (Reduce Motion). */
@Composable
private fun FlashStatic(sessionDir: File) {
    BeforeAfterFrame(sessionDir = sessionDir, alphaRef = 0.5f, alphaCap = 0.5f)
}

/**
 * Renders the Flash animation for the given loop [progress].
 * Phase 1 (0–15 %): Reference hold. Phase 2 (15–100 %): rapid hard-cut alternation.
 * Uses the same [BeforeAfterFrame] with ContentScale.Crop (fill semantics).
 */
@Composable
private fun FlashAnimated(sessionDir: File, progress: Float) {
    val showCapture = flashShowCaptureFromProgress(progress)
    BeforeAfterFrame(
        sessionDir = sessionDir,
        alphaRef = if (showCapture) 0f else 1f,
        alphaCap = if (showCapture) 1f else 0f
    )
}

// 4 preview cycles in the mode preview (visual selection aid — not tied to export cycle counts).
private const val FL_PREVIEW_CYCLES = 4

/**
 * Returns true when [progress] is in Phase 2 and the current flash frame is Capture.
 * Hard-cut: no crossfade, integer quantisation only.
 */
private fun flashShowCaptureFromProgress(progress: Float): Boolean {
    if (progress < CS_HOLD_REF_END) return false
    val flashProgress = (progress - CS_HOLD_REF_END) / (1f - CS_HOLD_REF_END)
    val flashFrameIndex = (flashProgress * (FL_PREVIEW_CYCLES * 2))
        .toInt().coerceIn(0, FL_PREVIEW_CYCLES * 2 - 1)
    return flashFrameIndex % 2 == 1
}
