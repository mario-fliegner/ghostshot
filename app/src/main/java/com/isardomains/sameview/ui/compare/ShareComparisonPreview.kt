// path: app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonPreview.kt
package com.isardomains.sameview.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.isardomains.sameview.image.ShareCaptionData
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.ui.theme.SameViewAccent
import java.io.File

private val CanvasBackground = Color(0xFF0D1424)
private val ComparisonBorder = Color(0xFF17202F)
private val SeparatorColor = Color(0xFF17202F)
private const val MAX_PREVIEW_COMPARISON_HEIGHT_DP = 160
private const val MAX_TOTAL_PREVIEW_HEIGHT_DP = 200
private val ComparisonCornerRadius = 6.dp

/**
 * Compose-rendered preview of the Share Comparison Image export canvas.
 *
 * Shows the full canvas layout: dark outer background, comparison area with subtle border,
 * and caption text below. No bitmap rendering — this is a visual selection aid only.
 *
 * Analogue to [com.isardomains.sameview.ui.video.VideoModePreview] for video exports.
 * Decorative — excluded from the accessibility tree via [clearAndSetSemantics].
 *
 * @param style Current comparison style (Slider or Side by side).
 * @param captionData Caption lines to show below the comparison; null = no caption area.
 * @param sessionDir Directory containing reference.jpg and capture.jpg.
 * @param viewportRatio Width ÷ height of the session viewport; drives comparison proportions.
 */
@Composable
fun ShareComparisonPreview(
    style: ShareComparisonStyle,
    captionData: ShareCaptionData?,
    sessionDir: File,
    viewportRatio: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
    ) {
        val availableW = maxWidth
        val safeRatio = if (viewportRatio > 0f) viewportRatio else (9f / 16f)

        // Comparison area: width is at most availableW; height from ratio, capped
        val compHeightFromWidth: Dp = availableW / safeRatio
        val compH: Dp = minOf(compHeightFromWidth, MAX_PREVIEW_COMPARISON_HEIGHT_DP.dp)
        val compW: Dp = compH * safeRatio

        // Caption area: fixed compact height when content is present
        val hasCaptionContent = captionData != null && captionData.hasContent
        val captionH: Dp = if (hasCaptionContent) 36.dp else 0.dp

        // Total preview height: comparison + small gap + caption
        val totalH: Dp = (compH + (if (hasCaptionContent) 8.dp else 0.dp) + captionH)
            .coerceAtMost(MAX_TOTAL_PREVIEW_HEIGHT_DP.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalH)
                .background(CanvasBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Comparison area
                Box(
                    modifier = Modifier
                        .size(width = compW, height = compH)
                        .clip(RoundedCornerShape(ComparisonCornerRadius))
                        .border(1.dp, ComparisonBorder, RoundedCornerShape(ComparisonCornerRadius))
                ) {
                    when (style) {
                        ShareComparisonStyle.SLIDER -> SliderPreviewContent(sessionDir, compW, compH)
                        ShareComparisonStyle.SIDE_BY_SIDE -> SideBySidePreviewContent(sessionDir)
                    }
                }

                // Caption area
                if (hasCaptionContent && captionData != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CaptionPreviewContent(captionData, compW)
                }
            }
        }
    }
}

// ── Slider preview ──────────────────────────────────────────────────────────────

@Composable
private fun SliderPreviewContent(sessionDir: File, compW: Dp, compH: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Reference fills full area (base layer)
        AsyncImage(
            model = File(sessionDir, "reference.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Capture clipped to right half
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(
                        size: androidx.compose.ui.geometry.Size,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        density: androidx.compose.ui.unit.Density
                    ) = androidx.compose.ui.graphics.Outline.Rectangle(
                        androidx.compose.ui.geometry.Rect(
                            left = size.width * 0.5f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height
                        )
                    )
                })
        ) {
            AsyncImage(
                model = File(sessionDir, "capture.jpg"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // White divider at center
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.5f, 0f),
                end = Offset(size.width * 0.5f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        // SameView handle — blue circle with white arrows
        val handleSize = minOf(compW.value * 0.15f, compH.value * 0.20f, 36f).dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(handleSize)
                .clip(CircleShape)
                .background(SameViewAccent),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(handleSize)
            ) {
                val unit = size.width / 48f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arrowPaint = androidx.compose.ui.graphics.Paint().apply {
                    color = Color.White
                    strokeWidth = unit * 2.5f
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    strokeJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                }
                val off = unit * 9f
                val depth = unit * 4f
                val halfH = unit * 7f

                val leftPath = Path().apply {
                    moveTo(cx - off + depth, cy - halfH)
                    lineTo(cx - off - depth, cy)
                    lineTo(cx - off + depth, cy + halfH)
                }
                val rightPath = Path().apply {
                    moveTo(cx + off - depth, cy - halfH)
                    lineTo(cx + off + depth, cy)
                    lineTo(cx + off - depth, cy + halfH)
                }
                drawPath(leftPath, Color.White, style = Stroke(width = unit * 2.5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawPath(rightPath, Color.White, style = Stroke(width = unit * 2.5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

// ── Side by side preview ────────────────────────────────────────────────────────

@Composable
private fun SideBySidePreviewContent(sessionDir: File) {
    Row(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(sessionDir, "reference.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(SeparatorColor)
        )
        AsyncImage(
            model = File(sessionDir, "capture.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

// ── Caption preview ─────────────────────────────────────────────────────────────

@Composable
private fun CaptionPreviewContent(captionData: ShareCaptionData, compW: Dp) {
    Column(
        modifier = Modifier
            .width(compW)
            .padding(horizontal = 2.dp)
    ) {
        captionData.titleLine?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        captionData.dateLine?.takeIf { it.isNotBlank() }?.let { date ->
            Text(
                text = date,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        captionData.locationLine?.takeIf { it.isNotBlank() }?.let { location ->
            Text(
                text = location,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
