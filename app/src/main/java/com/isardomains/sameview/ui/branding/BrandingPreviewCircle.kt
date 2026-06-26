// path: app/src/main/java/com/isardomains/sameview/ui/branding/BrandingPreviewCircle.kt
package com.isardomains.sameview.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * 64 dp circle preview of a branding asset (handle.png or branding-handle.png).
 *
 * Renders: white outer ring (2 dp, two arcs with 12° gaps) · 1 dp gap · white background
 * circle (58 dp) · [brandingFile] logo centered at 46 dp (Fit, no crop).
 *
 * Matches the branding handle appearance in Share Comparison Image exports: the branding
 * handle uses the same visual language as the standard SameView handle (white ring, white
 * circle). Only the inner content changes — logo instead of arrows.
 *
 * Used in [com.isardomains.sameview.ui.settings.SettingsScreen] (global branding)
 * and [com.isardomains.sameview.ui.compare.ShareComparisonScreen] (session branding).
 */
@Composable
fun BrandingPreviewCircle(
    brandingFile: File,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // Fill circle: white background with logo.
        // 58 dp = 64 dp total minus ring gap (1 dp) and ring half-stroke (1 dp) on each side.
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = brandingFile,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(46.dp)
            )
        }
        // Ring drawn on top: white, two arcs with 12° gaps.
        // Geometry matches ShareComparisonPreview.kt and BrandingHandleRenderer.kt.
        Canvas(modifier = Modifier.size(64.dp)) {
            val strokePx = 2.dp.toPx()
            val inset = strokePx / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(Color.White, 102f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
            drawArc(Color.White, 282f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
        }
    }
}
