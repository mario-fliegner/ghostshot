package com.isardomains.ghostshot.ui.theme

import androidx.compose.ui.graphics.Color

/** App-level dark background — deepest layer. */
val GhostShotAppBackground = Color(0xFF0D1424)

/** App-level dark surface — cards, tiles, viewports. */
val GhostShotAppSurface = Color(0xFF17202F)

/** App-level elevated surface — inner tile areas, headers above surface. */
val GhostShotAppSurfaceElevated = Color(0xFF1E2C40)

/** App-level dark surface variant (legacy, kept for compatibility). */
val GhostShotAppSurfaceVariant = Color(0xFF1A1D24)

/** Primary accent: Slider active track and thumb, interactive highlights. */
val GhostShotAccent = Color(0xFF4F8CFF)

/** Primary text and icon colour on all dark camera overlay surfaces. */
val GhostShotTextPrimary = Color(0xFFFFFFFF)

/** Secondary label colour on dark camera overlay surfaces (e.g. "Opacity", "Reference"). */
val GhostShotTextSecondary = Color(0xFFC7CCD6)

/** Divider / separator lines. */
val GhostShotAppDivider = Color(0xFF2A3445)

/** Slider inactive track. */
val GhostShotSliderInactive = Color(0xFF666666)

/** Subtle white tint applied over selected tiles (~10 % white). */
val GhostShotSelectionOverlay = Color(0x1AFFFFFF)

/** Semi-transparent scrim applied to overlay surfaces above the camera preview (~70 % black). */
val GhostShotOverlayScrim = Color(0xB3000000)

/** Solid dark fill for letterbox/pillarbox areas outside the active preview frame. */
val GhostShotPreviewFrameScrim = Color(0xFF17202F)

/** Semi-transparent white for camera grid overlay lines (~50 % white). */
val GhostShotGridLine = Color(0x80FFFFFF)

/** Original-reference peek badge background in compare. */
val GhostShotCompareOriginalBadgeBackground = Color(0xCC17202F)

/** Active original-reference peek badge background in compare. */
val GhostShotCompareOriginalBadgeBackgroundActive = Color(0xE64F8CFF)

/** Original-reference peek badge icon/text colour in compare. */
val GhostShotCompareOriginalBadgeContent = Color(0xFFFFFFFF)

/** Active original-reference peek badge icon/text colour in compare. */
val GhostShotCompareOriginalBadgeContentActive = Color(0xFFFFFFFF)

/** Original-reference peek label background in compare. */
val GhostShotCompareOriginalLabelBackground = Color(0xCC000000)

/** Original-reference peek label text colour in compare. */
val GhostShotCompareOriginalLabelContent = Color(0xFFFFFFFF)

/** Original-reference peek letterbox background in compare — matches app surface. */
val GhostShotCompareOriginalLetterboxBackground = Color(0xFF17202F)

/** Settings cards: quiet grouped surface, aligned with the app-level card tone. */
val GhostShotSettingsCardSurface = GhostShotAppSurface

/** Settings controls: slightly lifted inner surface for segmented controls. */
val GhostShotSettingsControlSurface = GhostShotAppSurfaceElevated

/** Settings card headings: calm, high-clarity header tone. */
val GhostShotSettingsHeaderText = Color(0xFFE8EEF8)

/** Settings primary row labels. */
val GhostShotSettingsLabelText = GhostShotTextPrimary

/** Settings secondary labels, such as field captions and inactive segments. */
val GhostShotSettingsSecondaryText = GhostShotTextSecondary

/** Settings control outline: softer than the general divider. */
val GhostShotSettingsControlOutline = Color(0x1FFFFFFF)

/** Settings selected segment fill, using the CI accent at a restrained dark-theme strength. */
val GhostShotSettingsSelectedSegment = Color(0x384F8CFF)

/** Settings unselected segment fill. */
val GhostShotSettingsUnselectedSegment = Color.Transparent
