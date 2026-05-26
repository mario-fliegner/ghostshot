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

private val SameViewDarkColorScheme = darkColorScheme(
    primary = SameViewAccent,
    background = SameViewAppBackground,
    surface = SameViewAppSurface,
    surfaceVariant = SameViewSliderInactive,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = SameViewTextSecondary,
    outlineVariant = SameViewAppDivider,
)

private val SameViewLightColorScheme = lightColorScheme(
    primary = SameViewAccent,
    surfaceVariant = SameViewSliderInactive,
    background = Color.White,
    surface = Color.White,
)

@Composable
fun SameViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = SameViewDarkColorScheme

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
