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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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

/**
 * Animated 16:9 preview frame shown inside the Video Type settings card, directly below the
 * mode segment control. Demonstrates the selected [mode] by loading real session images from
 * [sessionDir] (reference.jpg / capture.jpg) and playing a looping Compose animation.
 *
 * This is a decorative selection aid — it is excluded from the accessibility tree via
 * [clearAndSetSemantics] and carries no semantic information.
 *
 * Respects the system Animator Duration Scale: when the scale is 0 (Reduce Motion enabled),
 * a static fallback is rendered instead of the looping animation.
 *
 * Layout: 16:9 aspect, max height 200 dp, horizontally centred within the available width.
 */
@Composable
fun VideoModePreview(
    mode: VideoMode,
    sessionDir: File,
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
                        CompareSliderAnimated(sessionDir)
                    }
                    VideoMode.BEFORE_AFTER -> if (reduceMotion) {
                        BeforeAfterStatic(sessionDir)
                    } else {
                        BeforeAfterAnimated(sessionDir)
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

/** Animated looping Compare Slider: hold → sweep left-to-right → hold → restart. */
@Composable
private fun CompareSliderAnimated(sessionDir: File) {
    val transition = rememberInfiniteTransition(label = "compareSliderAnim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sliderProgress"
    )
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
        // Divider line at slider position (always visible when sliderPos > 0)
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

/** Animated looping Before & After: hold reference → crossfade → hold capture → restart. */
@Composable
private fun BeforeAfterAnimated(sessionDir: File) {
    val transition = rememberInfiniteTransition(label = "beforeAfterAnim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "crossfadeProgress"
    )
    val (alphaRef, alphaCap) = alphasFromProgress(progress)
    BeforeAfterFrame(sessionDir = sessionDir, alphaRef = alphaRef, alphaCap = alphaCap)
}

/**
 * Renders a single Before & After frame with the given alpha values.
 * Both images use ContentScale.Fit so neither is cropped.
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
