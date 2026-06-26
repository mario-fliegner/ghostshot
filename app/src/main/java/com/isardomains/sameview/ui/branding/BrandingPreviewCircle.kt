// path: app/src/main/java/com/isardomains/sameview/ui/branding/BrandingPreviewCircle.kt
package com.isardomains.sameview.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
 * @param brandingVersion Incremented by [ShareComparisonViewModel] after every successful
 * branding file write. Used as the Coil memory cache key suffix so that replacing
 * branding-handle.png does not serve the stale cached bitmap for the unchanged path.
 * Defaults to 0; callers that don't override (e.g. SettingsScreen) are unaffected because
 * the global logo file path never changes mid-session.
 *
 * Used in [com.isardomains.sameview.ui.settings.SettingsScreen] (global branding)
 * and [com.isardomains.sameview.ui.compare.ShareComparisonScreen] (session branding).
 */
@Composable
fun BrandingPreviewCircle(
    brandingFile: File,
    modifier: Modifier = Modifier,
    brandingVersion: Int = 0
) {
    val context = LocalContext.current
    // Build a fresh ImageRequest every time brandingVersion changes. The memoryCacheKey
    // includes the version so Coil bypasses its in-memory cache for the new file content.
    val imageRequest = remember(brandingFile, brandingVersion) {
        ImageRequest.Builder(context)
            .data(brandingFile)
            .memoryCacheKey("${brandingFile.absolutePath}-$brandingVersion")
            .build()
    }

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
                model = imageRequest,
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
