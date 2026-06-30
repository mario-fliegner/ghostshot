package com.isardomains.sameview.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setOverlayContent(markersState: ReferenceMarkersState) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    ReferenceMarkerOverlay(
                        markersState = markersState,
                        metadata = null,
                        displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
                        overlayOffsetX = 0f,
                        overlayOffsetY = 0f,
                        overlayScale = 1f,
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
                        metadata = ReferenceImageMetadata(
                            rawWidth = 1000,
                            rawHeight = 1000,
                            orientedWidth = 1000,
                            orientedHeight = 1000,
                            exifOrientation = null
                        ),
                        displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE,
                        overlayOffsetX = 0f,
                        overlayOffsetY = 0f,
                        overlayScale = 1f,
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

    private fun setBorderContent(isEditModeActive: Boolean) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MarkerEditBorder(
                            isEditModeActive = isEditModeActive,
                            isLandscape = false
                        )
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
