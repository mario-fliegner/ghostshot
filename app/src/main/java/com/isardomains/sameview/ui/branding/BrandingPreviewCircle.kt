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
import com.isardomains.sameview.ui.theme.SameViewAccent
import java.io.File

/**
 * 64 dp circle preview of a branding asset (handle.png or branding-handle.png).
 *
 * Renders: [SameViewAccent] outer ring (2 dp, two arcs with 12° gaps) · 1 dp gap ·
 * [#F5F7FA] background circle (58 dp) · [brandingFile] logo centered at 46 dp (Fit, no crop).
 *
 * The ring geometry matches the comparison handle ring in [ShareComparisonPreview] and
 * [BrandingHandleRenderer]: two 156° arcs starting at 102° and 282°, separated by 12° gaps
 * at top and bottom, with a 1 dp gap between the arc inner edge and the fill circle.
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
        // Fill circle with logo. 58 dp = 64 dp total − 2 × (1 dp gap + 1 dp stroke half).
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F7FA)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = brandingFile,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(46.dp)
            )
        }
        // Ring drawn on top: two arcs, 12° gaps at top/bottom.
        // Geometry matches ShareComparisonPreview.kt (ringThickness=2.dp, ringGap=1.dp,
        // angles 102°/282°, sweep 156°). Extracted constants would require touching
        // ShareComparisonPreview.kt (out of scope), so duplicated locally.
        Canvas(modifier = Modifier.size(64.dp)) {
            val strokePx = 2.dp.toPx()
            val inset = strokePx / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(SameViewAccent, 102f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
            drawArc(SameViewAccent, 282f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
        }
    }
}
