// path: app/src/main/java/com/isardomains/sameview/ui/branding/BrandingPreviewCircle.kt
package com.isardomains.sameview.ui.branding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.isardomains.sameview.ui.theme.SameViewAccent
import java.io.File

/**
 * 64 dp circle preview of a branding asset (handle.png or branding-handle.png).
 *
 * Renders: [SameViewAccent] outer ring (2 dp) · [#F5F7FA] background circle ·
 * [brandingFile] logo centered at ≈72 % of diameter (Fit, no crop).
 *
 * Matches the branding handle appearance in Share Comparison Image exports
 * (SESSION_BRANDING_V1.md §8.3).
 *
 * Used in [com.isardomains.sameview.ui.settings.SettingsScreen] (global branding)
 * and [com.isardomains.sameview.ui.compare.EditSessionScreen] (session branding).
 */
@Composable
fun BrandingPreviewCircle(
    brandingFile: File,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5F7FA))
            .border(2.dp, SameViewAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = brandingFile,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(46.dp)  // ≈ 72 % of 64 dp
        )
    }
}
