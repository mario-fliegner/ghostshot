package com.isardomains.ghostshot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Dynamic color is intentionally disabled: a camera app requires deterministic colours
// so that overlay controls are always readable against the camera preview scrim.

private val GhostShotDarkColorScheme = darkColorScheme(
    primary = GhostShotAccent,
    surfaceVariant = GhostShotSliderInactive,
)

private val GhostShotLightColorScheme = lightColorScheme(
    primary = GhostShotAccent,
    surfaceVariant = GhostShotSliderInactive,
)

@Composable
fun GhostShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GhostShotDarkColorScheme else GhostShotLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
