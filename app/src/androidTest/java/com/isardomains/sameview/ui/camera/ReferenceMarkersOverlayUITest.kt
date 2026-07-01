package com.isardomains.sameview.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceMarkersOverlayUITest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    // ── 7. Empty-state hint visible with zero markers ─────────────────────────

    @Test
    fun emptyStateHint_visibleWithZeroMarkers_inEditMode() {
        setOverlayContent(markersState = ReferenceMarkersState(isEditModeActive = true))

        composeRule.onNodeWithText(emptyHintText()).assertIsDisplayed()
    }

    // ── 8. Empty-state hint hidden after first marker ─────────────────────────

    @Test
    fun emptyStateHint_hiddenAfterFirstMarker() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        composeRule.onAllNodesWithText(emptyHintText()).assertCountEquals(0)
    }

    // ── 9. Edit-mode border visible only in edit mode ─────────────────────────

    @Test
    fun editModeBorder_visibleInEditMode() {
        setBorderContent(isEditModeActive = true)

        composeRule.onNodeWithTag("marker_edit_border").assertIsDisplayed()
    }

    @Test
    fun editModeBorder_absentWhenNotInEditMode() {
        setBorderContent(isEditModeActive = false)

        composeRule.onAllNodesWithTag("marker_edit_border").assertCountEquals(0)
    }

    // ── Loupe appearance tests ────────────────────────────────────────────────

    @Test
    fun loupe_appearsWhileDraggingMarker() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val centerX = rootBounds.width / 2f
        val centerY = rootBounds.height / 2f

        // Perform a drag from the marker center — keep finger down
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, centerY))
            moveTo(Offset(centerX, centerY - 20f))
            moveTo(Offset(centerX, centerY - 40f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("marker_drag_loupe").assertIsDisplayed()

        // Clean up — lift finger
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun loupe_disappears_afterDragEnds() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val centerX = rootBounds.width / 2f
        val centerY = rootBounds.height / 2f

        // Drag then lift
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, centerY))
            moveTo(Offset(centerX, centerY - 40f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("marker_drag_loupe").assertCountEquals(0)
    }

    @Test
    fun loupe_notVisible_whenNotDragging() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        // No gesture — loupe must not be present
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("marker_drag_loupe").assertCountEquals(0)
    }

    @Test
    fun loupe_notVisible_outsideEditMode() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = false  // Edit Mode off
            )
        )

        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("marker_drag_loupe").assertCountEquals(0)
    }

    // ── Loupe position / clamping tests ───────────────────────────────────────

    @Test
    fun loupe_clamped_nearTopEdge() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.05f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val vW = rootBounds.width
        val vH = rootBounds.height
        // With a 1000×1000 image in SHOW_FULL_IMAGE mode the displayed size is
        // min(vW,vH) × min(vW,vH), centered in the viewport.
        val displayed = minOf(vW, vH)
        val markerScreenX = vW / 2f  // normX = 0.5 → centered
        val markerScreenY = vH / 2f + displayed * (0.05f - 0.5f)  // near image top

        // Move down by 50px — exceeds touch slop so the gesture is classified as a drag
        composeRule.onRoot().performTouchInput {
            down(Offset(markerScreenX, markerScreenY))
            moveTo(Offset(markerScreenX, markerScreenY + 50f))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Loupe top must be >= viewport top (0)", loupeBounds.top >= 0f)

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun loupe_clamped_nearBottomEdge() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.95f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val vW = rootBounds.width
        val vH = rootBounds.height
        // With a 1000×1000 image in SHOW_FULL_IMAGE mode the displayed size is
        // min(vW,vH) × min(vW,vH), centered in the viewport.
        val displayed = minOf(vW, vH)
        val markerScreenX = vW / 2f  // normX = 0.5 → centered
        val markerScreenY = vH / 2f + displayed * (0.95f - 0.5f)  // near image bottom

        // Move up by 50px — exceeds touch slop so the gesture is classified as a drag
        composeRule.onRoot().performTouchInput {
            down(Offset(markerScreenX, markerScreenY))
            moveTo(Offset(markerScreenX, markerScreenY - 50f))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot
        // 88 dp done-area reservation; check loupe bottom does not exceed viewport
        assertTrue("Loupe bottom must be <= viewport height", loupeBounds.bottom <= rootBounds.height)

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun loupe_clamped_nearLeftEdge() {
        val marker = ReferenceMarker(normalizedX = 0.02f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val markerScreenX = rootBounds.width * 0.02f
        val markerScreenY = rootBounds.height / 2f

        // Move right by 50px — exceeds touch slop so the gesture is classified as a drag
        composeRule.onRoot().performTouchInput {
            down(Offset(markerScreenX, markerScreenY))
            moveTo(Offset(markerScreenX + 50f, markerScreenY))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Loupe left must be >= viewport left (0)", loupeBounds.left >= 0f)

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun loupe_clamped_nearRightEdge() {
        val marker = ReferenceMarker(normalizedX = 0.98f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val markerScreenX = rootBounds.width * 0.98f
        val markerScreenY = rootBounds.height / 2f

        // Move left by 50px — exceeds touch slop so the gesture is classified as a drag
        composeRule.onRoot().performTouchInput {
            down(Offset(markerScreenX, markerScreenY))
            moveTo(Offset(markerScreenX - 50f, markerScreenY))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Loupe right must be <= viewport right", loupeBounds.right <= rootBounds.right)

        composeRule.onRoot().performTouchInput { up() }
    }

    // ── Marker coordinate integrity ───────────────────────────────────────────

    @Test
    fun loupe_doesNotModifyMarkerCoordinate() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        val capturedCoords = mutableListOf<Pair<Float, Float>>()

        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            ),
            onMoveMarker = { _, nx, ny -> capturedCoords.add(Pair(nx, ny)) }
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val startX = rootBounds.width / 2f
        val startY = rootBounds.height / 2f
        // Slop-qualifying move: 57px diagonal (40² + 40²)½ >> typical touchSlop (~8dp)
        val slopX = startX + 40f
        val slopY = startY - 40f
        // Final target sent to the inner drag loop (after classification is done)
        val targetX = startX + 60f
        val targetY = startY - 60f

        // Step 1: down + move that exceeds touch slop → drag classified, inner loop starts waiting
        composeRule.onRoot().performTouchInput {
            down(Offset(startX, startY))
            moveTo(Offset(slopX, slopY))
        }
        composeRule.waitForIdle()

        // Step 2: second move processed by the inner drag loop → triggers onMoveMarker
        composeRule.onRoot().performTouchInput {
            moveTo(Offset(targetX, targetY))
        }
        composeRule.waitForIdle()

        // Loupe must be visible
        composeRule.onNodeWithTag("marker_drag_loupe").assertIsDisplayed()

        // onMoveMarker must have been called with the drag position, not the loupe center
        assertTrue("onMoveMarker must have been called at least once", capturedCoords.isNotEmpty())

        val lastCoord = capturedCoords.last()
        // With a 1000×1000 image in SHOW_FULL_IMAGE mode the normalised position of the
        // drag target should be near targetX/vW, targetY/vH (within ±5% tolerance).
        // The loupe center is ~120dp above the marker — if that were used instead, the
        // reported normY would be off by ~0.4, far outside the 0.05 tolerance.
        val expectedNormX = targetX / rootBounds.width
        val expectedNormY = targetY / rootBounds.height
        assertEquals("normX must match drag position (not loupe center)",
            expectedNormX, lastCoord.first, 0.05f)
        assertEquals("normY must match drag position (not loupe center)",
            expectedNormY, lastCoord.second, 0.05f)

        composeRule.onRoot().performTouchInput { up() }
    }

    // ── Border geometry tests ─────────────────────────────────────────────────

    @Test
    fun border_framesVisibleImageRect_whenLetterboxed() {
        // Wide image (2:1) in SHOW_FULL_IMAGE, portrait viewport, non-zero overlayOffsetY.
        // The border top must be computed using the constrained viewport height, not the full
        // screen height — a non-zero offset would have caught the coordinate-space bug.
        val overlayOffsetY = 0.05f
        setBorderContent(
            isEditModeActive = true,
            metadata = ReferenceImageMetadata(
                rawWidth = 1000, rawHeight = 500,
                orientedWidth = 1000, orientedHeight = 500,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            overlayOffsetY = overlayOffsetY
        )

        val viewportBounds = composeRule.onNodeWithTag("test_viewport")
            .fetchSemanticsNode().boundsInRoot
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border")
            .fetchSemanticsNode().boundsInRoot

        val vW = viewportBounds.width
        val vH = viewportBounds.height
        val baseScale = minOf(vW / 1000f, vH / 500f)
        val displayedH = 500f * baseScale
        val imageTop = vH / 2f - displayedH / 2f + overlayOffsetY * vH
        val expectedTop = viewportBounds.top + imageTop.coerceIn(0f, vH)

        // Basic: image is letterboxed and shifted, so border is within the viewport bounds
        assertTrue("Border top > viewport top when letterboxed",
            borderBounds.top > viewportBounds.top + 1f)
        assertTrue("Border bottom < viewport bottom when letterboxed",
            borderBounds.bottom < viewportBounds.bottom - 1f)
        // Tight: border top must reflect offset computed from the constrained viewport height
        assertEquals("Border top uses constrained viewport height for offset",
            expectedTop, borderBounds.top, 4f)
    }

    @Test
    fun border_matchesViewport_whenImageFillsViewport() {
        // Square image in COMPARE_WITH_PREVIEW → image fills/overflows the constrained viewport → rect = viewport.
        setBorderContent(
            isEditModeActive = true,
            metadata = ReferenceImageMetadata(
                rawWidth = 1000, rawHeight = 1000,
                orientedWidth = 1000, orientedHeight = 1000,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )

        val viewportBounds = composeRule.onNodeWithTag("test_viewport")
            .fetchSemanticsNode().boundsInRoot
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border")
            .fetchSemanticsNode().boundsInRoot

        assertEquals("Border left matches viewport", viewportBounds.left, borderBounds.left, 2f)
        assertEquals("Border top matches viewport", viewportBounds.top, borderBounds.top, 2f)
        assertEquals("Border right matches viewport", viewportBounds.right, borderBounds.right, 2f)
        assertEquals("Border bottom matches viewport", viewportBounds.bottom, borderBounds.bottom, 2f)
    }

    @Test
    fun border_neverExceedsViewport_whenImageLargerThanViewport() {
        // Very large square image in COMPARE_WITH_PREVIEW overflows all sides.
        // The border must be clamped to the constrained marker viewport, never outside it.
        setBorderContent(
            isEditModeActive = true,
            metadata = ReferenceImageMetadata(
                rawWidth = 4000, rawHeight = 4000,
                orientedWidth = 4000, orientedHeight = 4000,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )

        val viewportBounds = composeRule.onNodeWithTag("test_viewport")
            .fetchSemanticsNode().boundsInRoot
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border")
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Border left >= viewport left", borderBounds.left >= viewportBounds.left - 2f)
        assertTrue("Border top >= viewport top", borderBounds.top >= viewportBounds.top - 2f)
        assertTrue("Border right <= viewport right", borderBounds.right <= viewportBounds.right + 2f)
        assertTrue("Border bottom <= viewport bottom", borderBounds.bottom <= viewportBounds.bottom + 2f)
    }

    @Test
    fun border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY() {
        // Landscape image (16:9) in SHOW_FULL_IMAGE portrait viewport, offset moves image up.
        // Border top/bottom must be computed from the constrained viewport height —
        // using full screen height instead would give the wrong translation magnitude.
        val overlayOffsetY = -0.1f
        setBorderContent(
            isEditModeActive = true,
            metadata = ReferenceImageMetadata(
                rawWidth = 1600, rawHeight = 900,
                orientedWidth = 1600, orientedHeight = 900,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
            overlayOffsetY = overlayOffsetY
        )

        val viewportBounds = composeRule.onNodeWithTag("test_viewport")
            .fetchSemanticsNode().boundsInRoot
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border")
            .fetchSemanticsNode().boundsInRoot

        val vW = viewportBounds.width
        val vH = viewportBounds.height
        val baseScale = minOf(vW / 1600f, vH / 900f)
        val displayedH = 900f * baseScale
        val imageTop = vH / 2f - displayedH / 2f + overlayOffsetY * vH
        val imageBottom = imageTop + displayedH
        val expectedTop = viewportBounds.top + imageTop.coerceIn(0f, vH)
        val expectedBottom = viewportBounds.top + imageBottom.coerceIn(0f, vH)

        assertEquals("Border top from constrained viewport height", expectedTop, borderBounds.top, 4f)
        assertEquals("Border bottom from constrained viewport height", expectedBottom, borderBounds.bottom, 4f)
    }

    @Test
    fun border_recomputesCorrectly_inLandscapeViewport() {
        // A landscape image (16:9) in COMPARE_WITH_PREVIEW fills a 16:9 landscape viewport
        // end-to-end. The same image in portrait would be letterboxed. This verifies the rect
        // is computed against the constrained landscape viewport, not a portrait one.
        setBorderContent(
            isEditModeActive = true,
            metadata = ReferenceImageMetadata(
                rawWidth = 1920, rawHeight = 1080,
                orientedWidth = 1920, orientedHeight = 1080,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
            isLandscape = true
        )

        val viewportBounds = composeRule.onNodeWithTag("test_viewport")
            .fetchSemanticsNode().boundsInRoot
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border")
            .fetchSemanticsNode().boundsInRoot

        assertEquals("Border left matches landscape viewport", viewportBounds.left, borderBounds.left, 2f)
        assertEquals("Border top matches landscape viewport", viewportBounds.top, borderBounds.top, 2f)
        assertEquals("Border right matches landscape viewport", viewportBounds.right, borderBounds.right, 2f)
        assertEquals("Border bottom matches landscape viewport", viewportBounds.bottom, borderBounds.bottom, 2f)
    }

    // ── Empty-state hint placement test ───────────────────────────────────────

    @Test
    fun hint_centeredInsideVisibleImageRect() {
        // Wide image (2:1) in SHOW_FULL_IMAGE → letterboxed → hint must be within image rect
        setOverlayContent(
            markersState = ReferenceMarkersState(isEditModeActive = true),
            metadata = ReferenceImageMetadata(
                rawWidth = 1000, rawHeight = 500,
                orientedWidth = 1000, orientedHeight = 500,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val vW = rootBounds.width
        val vH = rootBounds.height

        // Compute expected image rect for 1000×500 in SHOW_FULL_IMAGE
        val baseScale = minOf(vW / 1000f, vH / 500f)
        val displayedH = 500f * baseScale
        val imageRectTop = vH / 2f - displayedH / 2f
        val imageRectBottom = vH / 2f + displayedH / 2f

        val hintBounds = composeRule.onNodeWithText(emptyHintText())
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Hint top >= image rect top", hintBounds.top >= imageRectTop - 2f)
        assertTrue("Hint bottom <= image rect bottom", hintBounds.bottom <= imageRectBottom + 2f)
    }

    // ── Loupe: visible image rect clamping tests ──────────────────────────────

    @Test
    fun loupe_clampedToVisibleImageRect() {
        // Square image: in SHOW_FULL_IMAGE portrait the image is letterboxed top/bottom.
        // Drag near top of the image → loupe must clamp to imageRect.top, not viewport top (0).
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.05f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            )
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val vW = rootBounds.width
        val vH = rootBounds.height

        // For 1000×1000 square image in SHOW_FULL_IMAGE portrait: baseScale = min(vW,vH)/1000
        val baseScale = minOf(vW, vH) / 1000f
        val displayedSize = 1000f * baseScale
        val imageRectTop = vH / 2f - displayedSize / 2f

        val markerScreenX = vW / 2f
        val markerScreenY = vH / 2f + displayedSize * (0.05f - 0.5f)

        composeRule.onRoot().performTouchInput {
            down(Offset(markerScreenX, markerScreenY))
            moveTo(Offset(markerScreenX, markerScreenY + 50f))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot

        // The loupe must be clamped at or below imageRect.top (not at viewport top = 0)
        assertTrue("Loupe top >= imageRect.top when letterboxed",
            loupeBounds.top >= imageRectTop - 2f)
        // Loupe must also stay within viewport
        assertTrue("Loupe top >= viewport top", loupeBounds.top >= 0f)

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun loupe_fallsBackToViewport_whenImageRectSmallerThanLoupe() {
        // Extreme zoom-out (overlayScale=0.05) → image rect is tiny, smaller than loupe diameter.
        // Loupe must fall back to viewport clamping and remain inside the viewport.
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        setLoupeOverlayContent(
            markersState = ReferenceMarkersState(
                markers = listOf(marker),
                isEditModeActive = true
            ),
            overlayScale = 0.05f
        )

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val centerX = rootBounds.width / 2f
        val centerY = rootBounds.height / 2f

        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, centerY))
            moveTo(Offset(centerX - 50f, centerY - 50f))
        }
        composeRule.waitForIdle()

        val loupeBounds = composeRule.onNodeWithTag("marker_drag_loupe")
            .fetchSemanticsNode().boundsInRoot

        // Loupe must remain inside the viewport (viewport fallback active)
        assertTrue("Loupe left >= 0", loupeBounds.left >= 0f)
        assertTrue("Loupe top >= 0", loupeBounds.top >= 0f)
        assertTrue("Loupe right <= viewport right", loupeBounds.right <= rootBounds.right)
        assertTrue("Loupe bottom <= viewport bottom", loupeBounds.bottom <= rootBounds.bottom)

        composeRule.onRoot().performTouchInput { up() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setOverlayContent(
        markersState: ReferenceMarkersState,
        metadata: ReferenceImageMetadata? = null,
        displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
        overlayOffsetX: Float = 0f,
        overlayOffsetY: Float = 0f,
        overlayScale: Float = 1f
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    ReferenceMarkerOverlay(
                        markersState = markersState,
                        metadata = metadata,
                        displayMode = displayMode,
                        overlayOffsetX = overlayOffsetX,
                        overlayOffsetY = overlayOffsetY,
                        overlayScale = overlayScale,
                        onAddMarker = { _, _ -> },
                        onMoveMarker = { _, _, _ -> },
                        onRemoveMarker = {},
                        onOverlayDragged = { _, _ -> },
                        onOverlayScaled = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setLoupeOverlayContent(
        markersState: ReferenceMarkersState,
        metadata: ReferenceImageMetadata = ReferenceImageMetadata(
            rawWidth = 1000,
            rawHeight = 1000,
            orientedWidth = 1000,
            orientedHeight = 1000,
            exifOrientation = null
        ),
        displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
        overlayOffsetX: Float = 0f,
        overlayOffsetY: Float = 0f,
        overlayScale: Float = 1f,
        onMoveMarker: (String, Float, Float) -> Unit = { _, _, _ -> }
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    ReferenceMarkerOverlay(
                        markersState = markersState,
                        metadata = metadata,
                        displayMode = displayMode,
                        overlayOffsetX = overlayOffsetX,
                        overlayOffsetY = overlayOffsetY,
                        overlayScale = overlayScale,
                        onAddMarker = { _, _ -> },
                        onMoveMarker = onMoveMarker,
                        onRemoveMarker = {},
                        onOverlayDragged = { _, _ -> },
                        onOverlayScaled = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setBorderContent(
        isEditModeActive: Boolean,
        metadata: ReferenceImageMetadata? = null,
        displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
        overlayOffsetX: Float = 0f,
        overlayOffsetY: Float = 0f,
        overlayScale: Float = 1f,
        isLandscape: Boolean = false
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = if (!isLandscape)
                                Modifier.fillMaxWidth().aspectRatio(9f / 16f).testTag("test_viewport")
                            else
                                Modifier.fillMaxHeight().aspectRatio(16f / 9f).testTag("test_viewport")
                        ) {
                            MarkerEditBorder(
                                isEditModeActive = isEditModeActive,
                                metadata = metadata,
                                displayMode = displayMode,
                                overlayOffsetX = overlayOffsetX,
                                overlayOffsetY = overlayOffsetY,
                                overlayScale = overlayScale
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun ComponentActivity.keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }

    private fun emptyHintText() = context.getString(R.string.markers_empty_hint)
}
