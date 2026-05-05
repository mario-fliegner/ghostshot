package com.isardomains.ghostshot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dynamic color is intentionally disabled: a camera app requires deterministic colours
// so that overlay controls are always readable against the camera preview scrim.

private val GhostShotDarkColorScheme = darkColorScheme(
    primary = GhostShotAccent,
    background = GhostShotAppBackground,
    surface = GhostShotAppSurface,
    surfaceVariant = GhostShotSliderInactive,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = GhostShotTextSecondary,
    outlineVariant = GhostShotAppDivider,
)

private val GhostShotLightColorScheme = lightColorScheme(
    primary = GhostShotAccent,
    surfaceVariant = GhostShotSliderInactive,
    background = Color.White,
    surface = Color.White,
)

@Composable
fun GhostShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = GhostShotDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
            content = content
        )
    }
}
