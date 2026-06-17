// path: app/src/main/java/com/isardomains/sameview/ui/video/VideoLoadingPreview.kt
package com.isardomains.sameview.ui.video

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.isardomains.sameview.video.VideoMode
import java.io.File

private val LoadingPreviewBackground = Color(0xFF17202F)
private const val LOADING_LOOP_DURATION_MS = 6000

// Compare Slider timing fractions (§14 of VIDEO_EXPORT_V1.md)
private const val CS_HOLD_REF_END_L = 0.15f
private const val CS_SWEEP_END_L = 0.60f

// Before & After timing: 500 ms crossfade / 6000 ms loop = 0.0833 (§15)
private const val BA_HOLD_REF_END_L = 0.15f
private const val BA_CROSSFADE_FRACTION_L = 0.0833f
private val BA_CROSSFADE_END_L = BA_HOLD_REF_END_L + BA_CROSSFADE_FRACTION_L

// Overlay text: 0–20 % = 1200 ms full opacity; 20–25 % = 300 ms fade-out
private const val LOADING_OVERLAY_FULL_END = 0.20f
private const val LOADING_OVERLAY_FADE_END = 0.25f

/**
 * Animated preview card shown in the Rendering state of the Create Video flow.
 *
 * Represents the video currently being encoded. Unlike [VideoModePreview], this composable:
 * - Uses ContentScale.Fit for Before & After (full image visible, matching export behavior)
 * - Uses ContentScale.Crop for Compare Slider (canvas covered, matching export fill semantics)
 * - Runs a dedicated 6-second loop independent of the actual export duration
 * - Has no Crossfade transition on mode switch (mode is fixed once export starts)
 * - Does not include the branding endcard in its loop
 *
 * The caller sizes this composable via [modifier] (typically [androidx.compose.ui.unit.Dp] size).
 * The card fills the provided size, applies 4 dp elevation, and clips to the app's medium shape.
 *
 * When [previewLines] is non-empty, overlay text lines appear bottom-left during the initial
 * hold phase and fade before the main animation begins, matching the export overlay timing.
 *
 * This is a UX preview — not a pixel-accurate export simulation.
 */
@Composable
fun VideoLoadingPreview(
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

    val mainTransition = rememberInfiniteTransition(label = "loadingPreviewAnim")
    val progress by mainTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOADING_LOOP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingProgress"
    )

    val overlayAlpha = when {
        previewLines.isEmpty() -> 0f
        reduceMotion -> 1f
        progress < LOADING_OVERLAY_FULL_END -> 1f
        progress < LOADING_OVERLAY_FADE_END ->
            1f - (progress - LOADING_OVERLAY_FULL_END) /
                    (LOADING_OVERLAY_FADE_END - LOADING_OVERLAY_FULL_END)
        else -> 0f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = LoadingPreviewBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.clearAndSetSemantics {}
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                VideoMode.COMPARE_SLIDER -> if (reduceMotion) {
                    CompareSliderLoadingStatic(sessionDir)
                } else {
                    CompareSliderLoadingAnimated(sessionDir, progress)
                }
                VideoMode.BEFORE_AFTER -> if (reduceMotion) {
                    BeforeAfterLoadingStatic(sessionDir)
                } else {
                    BeforeAfterLoadingAnimated(sessionDir, progress)
                }
            }

            if (previewLines.isNotEmpty() && overlayAlpha > 0f) {
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(overlayAlpha)
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)) {
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

@Composable
private fun CompareSliderLoadingStatic(sessionDir: File) {
    CompareSliderLoadingFrame(sessionDir = sessionDir, sliderPos = 0.5f)
}

@Composable
private fun CompareSliderLoadingAnimated(sessionDir: File, progress: Float) {
    CompareSliderLoadingFrame(
        sessionDir = sessionDir,
        sliderPos = loadingSliderPosFromProgress(progress)
    )
}

/**
 * Single Compare Slider frame. Both images use ContentScale.Crop (fill / cover semantics),
 * matching the export behavior of CompareSliderRenderEngine which uses maxOf() scaling.
 */
@Composable
private fun CompareSliderLoadingFrame(sessionDir: File, sliderPos: Float) {
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
                    .clip(LoadingSliderClipShape(sliderPos))
            )
        }
        if (sliderPos > 0f && sliderPos < 1f) {
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

/** Cubic smoothstep easing — same formula as CompareSliderRenderEngine (§14.2). */
private fun loadingSliderPosFromProgress(progress: Float): Float = when {
    progress < CS_HOLD_REF_END_L -> 0f
    progress < CS_SWEEP_END_L -> {
        val t = (progress - CS_HOLD_REF_END_L) / (CS_SWEEP_END_L - CS_HOLD_REF_END_L)
        3f * t * t - 2f * t * t * t
    }
    else -> 1f
}

private class LoadingSliderClipShape(private val startFraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Rectangle(
        Rect(
            left = size.width * startFraction,
            top = 0f,
            right = size.width,
            bottom = size.height
        )
    )
}

// ── Before & After ──────────────────────────────────────────────────────────────

@Composable
private fun BeforeAfterLoadingStatic(sessionDir: File) {
    BeforeAfterLoadingFrame(sessionDir = sessionDir, alphaRef = 0.5f, alphaCap = 0.5f)
}

@Composable
private fun BeforeAfterLoadingAnimated(sessionDir: File, progress: Float) {
    val (alphaRef, alphaCap) = loadingAlphasFromProgress(progress)
    BeforeAfterLoadingFrame(sessionDir = sessionDir, alphaRef = alphaRef, alphaCap = alphaCap)
}

/**
 * Single Before & After frame. Both images use ContentScale.Fit (fit semantics),
 * matching the export behavior of BeforeAfterRenderEngine which uses minOf() scaling.
 * Free areas show the LoadingPreviewBackground (#17202F) set on the Card container.
 */
@Composable
private fun BeforeAfterLoadingFrame(sessionDir: File, alphaRef: Float, alphaCap: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (alphaRef > 0f) {
            AsyncImage(
                model = File(sessionDir, "reference.jpg"),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(alphaRef)
            )
        }
        if (alphaCap > 0f) {
            AsyncImage(
                model = File(sessionDir, "capture.jpg"),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(alphaCap)
            )
        }
    }
}

private fun loadingAlphasFromProgress(progress: Float): Pair<Float, Float> = when {
    progress < BA_HOLD_REF_END_L -> 1f to 0f
    progress < BA_CROSSFADE_END_L -> {
        val cf = (progress - BA_HOLD_REF_END_L) / BA_CROSSFADE_FRACTION_L
        (1f - cf) to cf
    }
    else -> 0f to 1f
}
