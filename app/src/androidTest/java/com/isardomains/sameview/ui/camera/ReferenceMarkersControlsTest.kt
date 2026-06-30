package com.isardomains.sameview.ui.camera

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceMarkersControlsTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        scenario?.close()
        scenario = null
    }

    // ── 1. MarkerDoneButton visible in edit mode ──────────────────────────────

    @Test
    fun markerDoneButton_visibleInEditMode() {
        setMarkersContent(isMarkerEditModeActive = true)

        composeRule.onNodeWithTag("marker_done_button").assertIsDisplayed()
    }

    // ── 2. ShutterButton absent in edit mode ──────────────────────────────────

    @Test
    fun shutterButton_absentInEditMode() {
        setMarkersContent(isMarkerEditModeActive = true)

        composeRule.onAllNodesWithContentDescription(captureDescription()).assertCountEquals(0)
    }

    // ── 3. Tap Done exits edit mode; shutter button returns ───────────────────

    @Test
    fun tapMarkerDoneButton_exitsEditMode_andShutterReturns() {
        var doneCount = 0
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    var editMode by remember { mutableStateOf(true) }
                    CameraControlsOverlay(
                        referenceUri = testUri(),
                        alpha = 0.5f,
                        onAlphaChange = {},
                        onSelectReferenceImage = {},
                        onResetOverlay = {},
                        onCapture = {},
                        isLandscape = false,
                        isMarkerEditModeActive = editMode,
                        onDoneMarkerEditMode = { doneCount++; editMode = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("marker_done_button").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(captureDescription()).assertCountEquals(0)

        composeRule.onNodeWithTag("marker_done_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, doneCount)
        composeRule.onAllNodesWithTag("marker_done_button").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
    }

    // ── 4. Done does not clear markers ────────────────────────────────────────

    @Test
    fun markerDoneButton_doesNotClearMarkers() {
        val marker = ReferenceMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        var markersAtDone: List<ReferenceMarker>? = null

        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    var state by remember {
                        mutableStateOf(
                            ReferenceMarkersState(
                                markers = listOf(marker),
                                isEditModeActive = true
                            )
                        )
                    }
                    CameraControlsOverlay(
                        referenceUri = testUri(),
                        alpha = 0.5f,
                        onAlphaChange = {},
                        onSelectReferenceImage = {},
                        onResetOverlay = {},
                        onCapture = {},
                        isLandscape = false,
                        isMarkerEditModeActive = state.isEditModeActive,
                        referenceMarkersState = state,
                        onDoneMarkerEditMode = {
                            markersAtDone = state.markers
                            state = state.copy(isEditModeActive = false)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("marker_done_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, markersAtDone?.size)
        assertEquals(marker.id, markersAtDone?.firstOrNull()?.id)
    }

    // ── 5. Done absent from portrait top-right ────────────────────────────────

    @Test
    fun markerDoneButton_inPortrait_isAtBottomCenter_notTopRight() {
        setMarkersContent(isMarkerEditModeActive = true, isLandscape = false)

        val rootBounds = composeRule.onNodeWithTag("camera_controls_root").getUnclippedBoundsInRoot()
        val doneBounds = composeRule.onNodeWithTag("marker_done_button").getUnclippedBoundsInRoot()

        val rootMidY = rootBounds.top + (rootBounds.bottom - rootBounds.top) / 2f
        val rootMidX = rootBounds.left + (rootBounds.right - rootBounds.left) / 2f
        val doneCenterX = doneBounds.left + (doneBounds.right - doneBounds.left) / 2f
        val offsetX = if (doneCenterX > rootMidX) doneCenterX - rootMidX else rootMidX - doneCenterX

        assert(doneBounds.top > rootMidY) { "Done button must be in bottom half, not in top-right" }
        assert(offsetX <= 60.dp) { "Done button must be near horizontal center in portrait (offset=$offsetX)" }
    }

    // ── 6. Done absent from landscape side rail ───────────────────────────────

    @Test
    fun markerDoneButton_inLandscape_isAtBottomCenter_notSideRail() {
        setMarkersContentLandscape(isMarkerEditModeActive = true)

        val rootBounds = composeRule.onNodeWithTag("camera_controls_root").getUnclippedBoundsInRoot()
        val doneBounds = composeRule.onNodeWithTag("marker_done_button").getUnclippedBoundsInRoot()
        val refSlotBounds = composeRule.onNodeWithTag("reference_action_slot").getUnclippedBoundsInRoot()

        val rootMidX = rootBounds.left + (rootBounds.right - rootBounds.left) / 2f
        val doneCenterX = doneBounds.left + (doneBounds.right - doneBounds.left) / 2f
        val offsetX = if (doneCenterX > rootMidX) doneCenterX - rootMidX else rootMidX - doneCenterX

        assert(offsetX <= 60.dp) { "Done button must be near horizontal center in landscape (offset=$offsetX)" }
        assertNoOverlap(doneBounds, refSlotBounds)
    }

    // ── 10. Reference menu marker states ─────────────────────────────────────

    @Test
    fun referenceMenu_noMarkers_showsAddMarkersOnly() {
        setMarkersContent(referenceMarkersState = ReferenceMarkersState())

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(addMarkersLabel()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(hideMarkersLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(showMarkersLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(editMarkersLabel()).assertCountEquals(0)
    }

    @Test
    fun referenceMenu_visibleMarkers_showsHideAndEdit() {
        val marker = ReferenceMarker(normalizedX = 0.3f, normalizedY = 0.4f)
        setMarkersContent(
            referenceMarkersState = ReferenceMarkersState(
                markers = listOf(marker),
                markersVisible = true
            )
        )

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(hideMarkersLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(editMarkersLabel()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(addMarkersLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(showMarkersLabel()).assertCountEquals(0)
    }

    @Test
    fun referenceMenu_hiddenMarkers_showsShowAndEdit() {
        val marker = ReferenceMarker(normalizedX = 0.3f, normalizedY = 0.4f)
        setMarkersContent(
            referenceMarkersState = ReferenceMarkersState(
                markers = listOf(marker),
                markersVisible = false
            )
        )

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(showMarkersLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(editMarkersLabel()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(addMarkersLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(hideMarkersLabel()).assertCountEquals(0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setMarkersContent(
        referenceUri: Uri? = testUri(),
        isLandscape: Boolean = false,
        isMarkerEditModeActive: Boolean = false,
        referenceMarkersState: ReferenceMarkersState = ReferenceMarkersState()
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    CameraControlsOverlay(
                        referenceUri = referenceUri,
                        alpha = 0.5f,
                        onAlphaChange = {},
                        onSelectReferenceImage = {},
                        onResetOverlay = {},
                        onCapture = {},
                        isLandscape = isLandscape,
                        isMarkerEditModeActive = isMarkerEditModeActive,
                        referenceMarkersState = referenceMarkersState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setMarkersContentLandscape(
        referenceUri: Uri? = testUri(),
        isMarkerEditModeActive: Boolean = false,
        referenceMarkersState: ReferenceMarkersState = ReferenceMarkersState()
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario?.onActivity { activity ->
            activity.keepScreenOn()
            activity.setContent {
                SameViewTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraControlsOverlay(
                            referenceUri = referenceUri,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onCapture = {},
                            isLandscape = true,
                            isMarkerEditModeActive = isMarkerEditModeActive,
                            referenceMarkersState = referenceMarkersState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openReferenceMenu() {
        composeRule.onNodeWithTag("reference_action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithContentDescription(resetLabel()).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithContentDescription(referenceLabel()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(resetLabel()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoOverlap(first: DpRect, second: DpRect) {
        assert(
            first.right <= second.left ||
                second.right <= first.left ||
                first.bottom <= second.top ||
                second.bottom <= first.top
        )
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

    private fun testUri() = Uri.parse("content://sameview/test-reference")
    private fun captureDescription() = context.getString(R.string.capture_button_content_description)
    private fun referenceLabel() = context.getString(R.string.select_reference_image)
    private fun resetLabel() = context.getString(R.string.reset_overlay_label)
    private fun addMarkersLabel() = context.getString(R.string.markers_add_action)
    private fun hideMarkersLabel() = context.getString(R.string.markers_hide_action)
    private fun showMarkersLabel() = context.getString(R.string.markers_show_action)
    private fun editMarkersLabel() = context.getString(R.string.markers_edit_action)
}
