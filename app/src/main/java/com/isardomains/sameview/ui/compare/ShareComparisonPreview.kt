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
// Safety cap: comparison area never exceeds this height regardless of aspect ratio.
// Prevents extreme portrait ratios (e.g. 9:16) from spanning the full visible screen.
// Normal portrait formats (3:4, 1:1) are uncapped and render at their exact ratio.
private const val MAX_PREVIEW_COMPARISON_HEIGHT_DP = 500
// Absolute backstop for total preview height (comparison + caption).
private const val MAX_TOTAL_PREVIEW_HEIGHT_DP = 550
private val ComparisonCornerRadius = 6.dp

/**
 * Compose-rendered preview of the Share Comparison Image export canvas.
 *
 * Compose-rendered preview of the Share Comparison Image export canvas.
 *
 * The preview is **ratio-proportional**: height is derived from the session viewport aspect ratio
 * so that 3:4 portrait sessions show a tall portrait preview and 16:9 sessions show a flat
 * landscape preview — accurately reflecting what the exported JPEG will look like.
 *
 * Safety caps prevent extreme portrait ratios (e.g. 9:16) from making the screen unusable:
 * - Comparison height is capped at `availableWidth × 1.5` (max ~495dp at 330dp card width)
 * - Comparison height is capped at [MAX_PREVIEW_COMPARISON_HEIGHT_DP] (500dp absolute)
 * Normal formats (3:4, 1:1, 4:3, 16:9) render at their exact aspect ratio, uncapped.
 *
 * Comparison and caption share one unified rounded-rectangle dark background so the preview
 * reads as a single export object. The screen is scrollable; portrait previews push Quality
 * and Share controls below the fold, which is acceptable and expected.
 *
 * No bitmap rendering — decorative, excluded from accessibility tree via [clearAndSetSemantics].
 *
 * @param style Current comparison style (Slider or Side by side).
 * @param captionData Pre-computed caption lines; null = no caption area rendered.
 * @param sessionDir Directory containing reference.jpg and capture.jpg.
 * @param viewportRatio Width ÷ height of the session viewport; drives proportions.
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

        // Preview fills the full card width. Comparison height is style-dependent:
        // - Slider uses the full available width per image (crop-fill), so height follows
        //   the full-width viewport ratio.
        // - Side by side gives each image only half the width (fit-fill), so the natural
        //   image height is halfW / ratio. This avoids empty dark space above/below the images.
        val compW: Dp = availableW
        val compH: Dp = when (style) {
            ShareComparisonStyle.SLIDER ->
                (availableW / safeRatio)
                    .coerceAtMost(availableW * 1.5f)              // narrow-portrait safety
                    .coerceAtMost(MAX_PREVIEW_COMPARISON_HEIGHT_DP.dp)
            ShareComparisonStyle.SIDE_BY_SIDE ->
                ((availableW / 2) / safeRatio)                    // half width → half-width fit height
                    .coerceAtMost(MAX_PREVIEW_COMPARISON_HEIGHT_DP.dp)
        }

        val hasCaptionContent = captionData != null && captionData.hasContent

        // Caption overhead scales with the number of visible lines — no fixed 3-line reservation.
        // Each text line in the preview is approximately 14 dp tall; gap above the caption is 6 dp.
        // Bottom canvas padding is always provided by the trailing Spacer inside the Column.
        val captionLineCount = if (hasCaptionContent) (captionData?.lineCount ?: 0) else 0
        val captionTotalH: Dp = when (captionLineCount) {
            0    -> 0.dp
            1    -> 20.dp   // 6 dp gap + ~14 dp single text line
            2    -> 33.dp   // 6 dp gap + ~27 dp two text lines
            else -> 46.dp   // 6 dp gap + ~40 dp three text lines
        }

        // Uniform outer canvas padding on all four sides — makes the dark canvas visible
        // around the comparison area, consistent regardless of caption state.
        val outerPad = 4.dp

        val totalH: Dp = (outerPad + compH + captionTotalH + outerPad)
            .coerceAtMost(MAX_TOTAL_PREVIEW_HEIGHT_DP.dp) // 550dp absolute backstop

        // Preview canvas: dark background visible on all sides around the comparison area.
        // The Column padding creates consistent 4dp margins on top, left, and right.
        // A bottom Spacer mirrors the top padding to complete the canvas framing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalH)
                .clip(RoundedCornerShape(ComparisonCornerRadius))
                .background(CanvasBackground),
            contentAlignment = Alignment.TopStart
        ) {
            Column(modifier = Modifier.padding(start = outerPad, end = outerPad, top = outerPad)) {
                // Comparison area — border is visible against the surrounding dark canvas.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(compH)
                        .clip(RoundedCornerShape(ComparisonCornerRadius))
                        .border(
                            1.dp,
                            ComparisonBorder,
                            RoundedCornerShape(ComparisonCornerRadius)
                        )
                ) {
                    when (style) {
                        ShareComparisonStyle.SLIDER ->
                            SliderPreviewContent(sessionDir, compW, compH)
                        ShareComparisonStyle.SIDE_BY_SIDE ->
                            SideBySidePreviewContent(sessionDir)
                    }
                }

                // Caption area: gap above, left-aligned, no bottom padding (Spacer handles it).
                if (hasCaptionContent && captionData != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CaptionPreviewContent(captionData)
                }

                // Bottom canvas padding — always present, mirrors top outerPad.
                Spacer(modifier = Modifier.height(outerPad))
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
        // White divider at centre
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.5f, 0f),
                end = Offset(size.width * 0.5f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        // SameView handle — blue circle with white arrows, same geometry as export renderer
        val handleSize = minOf(compW.value * 0.15f, compH.value * 0.20f, 36f).dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(handleSize)
                .clip(CircleShape)
                .background(SameViewAccent),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(handleSize)) {
                val unit = size.width / 48f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val off = unit * 9f
                val depth = unit * 4f
                val halfH = unit * 7f
                val arrowStroke = Stroke(
                    width = unit * 2.5f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
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
                drawPath(leftPath, Color.White, style = arrowStroke)
                drawPath(rightPath, Color.White, style = arrowStroke)
            }
        }
    }
}

// ── Side by side preview ────────────────────────────────────────────────────────

@Composable
private fun SideBySidePreviewContent(sessionDir: File) {
    // Fit semantics: both images are fully visible within their respective halves.
    // Letterboxing may appear for aspect ratio mismatches, but both reference and capture
    // are always shown in their entirety — the defining characteristic of Side by side.
    // The dark canvas background fills any letterbox areas.
    Row(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(sessionDir, "reference.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(CanvasBackground)
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
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(CanvasBackground)
        )
    }
}

// ── Caption preview ─────────────────────────────────────────────────────────────

@Composable
private fun CaptionPreviewContent(captionData: ShareCaptionData) {
    // Left-aligned at the image edge (4 dp additional inset beyond the outer canvas padding).
    // Bottom breathing room is provided by the parent Column's bottom Spacer.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
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
