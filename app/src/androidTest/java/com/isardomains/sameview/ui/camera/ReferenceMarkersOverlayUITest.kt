package com.isardomains.sameview.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
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
