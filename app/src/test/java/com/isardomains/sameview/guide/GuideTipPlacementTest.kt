package com.isardomains.sameview.guide

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTipPlacementTest {

    /**
     * Compact portrait: anchor near top of screen so ABOVE exits the safe zone.
     * BELOW would be placed but the exclusion zone (simulating the Capture button) covers
     * the card position. Both candidates rejected → Deferred.
     */
    @Test
    fun exclusionZone_captureButton_defers_whenAllSidesBlocked() {
        val captureButtonBounds = Rect(150f, 140f, 270f, 260f)
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(420, 900),
                cardSize = IntSize(220, 100),
                anchorBounds = Rect(180f, 100f, 240f, 140f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                exclusionZones = listOf(captureButtonBounds),
                marginPx = 16f,
                gapPx = 8f
            )
        )
        assertEquals(GuideTipPlacementResult.Deferred, result)
    }

    /**
     * Medium: anchor in the top bar. ABOVE exits the safe zone. START, BELOW, and END all
     * land on the exclusion zone (simulating the compare viewport). All candidates rejected → Deferred.
     */
    @Test
    fun exclusionZone_compareViewport_defers_whenAllSidesBlocked() {
        val compareViewportBounds = Rect(0f, 90f, 720f, 900f)
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(720, 900),
                cardSize = IntSize(220, 100),
                anchorBounds = Rect(600f, 50f, 670f, 90f),
                windowWidthSizeClass = WindowWidthSizeClass.Medium,
                exclusionZones = listOf(compareViewportBounds),
                marginPx = 16f,
                gapPx = 8f
            )
        )
        assertEquals(GuideTipPlacementResult.Deferred, result)
    }

    /**
     * Compact landscape: container is very short, so ABOVE and BELOW both fail the safe-zone
     * check. With isLandscape=true, START is tried next and succeeds → Placed(START).
     * With isLandscape=false (portrait), only ABOVE and BELOW are candidates → Deferred.
     */
    @Test
    fun compactLandscape_placesSide_whenAboveAndBelowFail_portraitDefers() {
        val input = GuideTipPlacementInput(
            containerSize = IntSize(900, 200),
            cardSize = IntSize(220, 120),
            anchorBounds = Rect(400f, 40f, 500f, 160f),
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            marginPx = 16f,
            gapPx = 8f
        )

        val landscapeResult = calculateGuideTipPlacement(input.copy(isLandscape = true))
        assertEquals(
            GuideTipPlacementSide.START,
            (landscapeResult as GuideTipPlacementResult.Placed).side
        )

        val portraitResult = calculateGuideTipPlacement(input.copy(isLandscape = false))
        assertEquals(GuideTipPlacementResult.Deferred, portraitResult)
    }

    /**
     * Compact portrait: a 280 px wide card in a 360 px wide container fits within the safe zone
     * (360 - 2×16 = 328 ≥ 280). Placement must not be deferred due to width constraints.
     */
    @Test
    fun compact_280pxCard_in360pxContainer_places_notDeferred() {
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(360, 640),
                cardSize = IntSize(280, 100),
                anchorBounds = Rect(160f, 200f, 200f, 240f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                marginPx = 16f,
                gapPx = 8f
            )
        )
        assertTrue("Expected Placed but got Deferred", result is GuideTipPlacementResult.Placed)
    }

    /**
     * An exclusion zone covering the entire container surface blocks every candidate side.
     * The algorithm must return Deferred rather than place the card over the exclusion zone.
     */
    @Test
    fun allSidesBlocked_byExclusionZone_returnsDeferred() {
        val fullContainerExclusion = Rect(0f, 0f, 800f, 600f)
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(800, 600),
                cardSize = IntSize(200, 100),
                anchorBounds = Rect(350f, 250f, 450f, 350f),
                windowWidthSizeClass = WindowWidthSizeClass.Medium,
                exclusionZones = listOf(fullContainerExclusion),
                marginPx = 16f,
                gapPx = 8f
            )
        )
        assertEquals(GuideTipPlacementResult.Deferred, result)
    }

    /**
     * Landscape, wide/tall-enough container, anchor near the right edge with a non-zero
     * safeInsetRightPx simulating a horizontal system bar/cutout (e.g. status bar shifted to the
     * side in landscape). ABOVE is chosen (landscape prefers ABOVE first) and its horizontal
     * centering on the anchor would overshoot the raw container edge, forcing a clamp. The placed
     * card must stay fully inside the inset-adjusted safe zone, not just inside the raw container
     * width.
     */
    @Test
    fun landscapeAnchorNearRightEdge_withSafeInsetRight_clampsInsideInsetAdjustedZone() {
        val safeInsetRightPx = 80f
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(900, 900),
                cardSize = IntSize(220, 100),
                anchorBounds = Rect(820f, 400f, 880f, 460f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                marginPx = 16f,
                gapPx = 8f,
                isLandscape = true,
                safeInsetRightPx = safeInsetRightPx
            )
        )

        val placed = result as GuideTipPlacementResult.Placed
        assertEquals(GuideTipPlacementSide.ABOVE, placed.side)
        assertTrue(
            "Card right edge ${placed.offset.x + 220} must stay within inset-adjusted safe zone ${900 - 16 - safeInsetRightPx}",
            placed.offset.x + 220 <= 900 - 16 - safeInsetRightPx
        )
    }

    /**
     * Same geometry as above but with safeInsetRightPx = 0f (the default). This pins down that
     * the default/unset behavior is unchanged by the new inset parameters — the card clamps to
     * the raw container edge instead of the (now absent) inset-adjusted one.
     */
    @Test
    fun landscapeAnchorNearRightEdge_withoutSafeInset_matchesDefaultBehavior() {
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(900, 900),
                cardSize = IntSize(220, 100),
                anchorBounds = Rect(820f, 400f, 880f, 460f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                marginPx = 16f,
                gapPx = 8f,
                isLandscape = true,
                safeInsetRightPx = 0f
            )
        )

        val placed = result as GuideTipPlacementResult.Placed
        assertEquals(GuideTipPlacementSide.ABOVE, placed.side)
        assertEquals(664, placed.offset.x)
        assertTrue(
            "Card right edge ${placed.offset.x + 220} must stay within default safe zone ${900 - 16}",
            placed.offset.x + 220 <= 900 - 16
        )
    }
}
