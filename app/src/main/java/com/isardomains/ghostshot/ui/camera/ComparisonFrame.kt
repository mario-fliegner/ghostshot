// path: app/src/main/java/com/isardomains/ghostshot/ui/camera/ComparisonFrame.kt
package com.isardomains.ghostshot.ui.camera

/**
 * A normalized rectangle in a 2D coordinate space, with all values clamped to [0, 1].
 *
 * Represents a rectangular region as fractions of the total width/height of the containing space.
 * [left] < [right] and [top] < [bottom] are invariants — any calculation producing a degenerate
 * rect must return null instead of constructing this type.
 *
 * @param left   Normalized left edge in [0, 1].
 * @param top    Normalized top edge in [0, 1].
 * @param right  Normalized right edge in [0, 1], must be > [left].
 * @param bottom Normalized bottom edge in [0, 1], must be > [top].
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Defines the visible comparison area produced at capture time.
 *
 * Both rects are normalized to [0, 1] within their respective image spaces.
 *
 * @param captureRect   The sub-region of the captured (final rotated) bitmap that is compared.
 * @param referenceRect The sub-region of the reference image that is compared.
 */
data class ComparisonFrame(
    val captureRect: NormalizedRect,
    val referenceRect: NormalizedRect
)
