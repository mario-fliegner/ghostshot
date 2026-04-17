package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraControlsOverlayTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun controls_withoutReference_showPrimaryActionsOnly() {
        setControlsContent(referenceUri = null, isLandscape = false)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(opacityDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(mismatchDescription()).assertCountEquals(0)
    }

    @Test
    fun controls_withReferenceInPortrait_showOpacityAndNoTopDisplayMode() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(mismatchDescription()).assertCountEquals(0)
    }

    @Test
    fun controls_withReferenceInLandscape_showOpacityAndNoTopDisplayMode() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
    }

    @Test
    fun opacitySlider_inLandscape_isConstrainedAndDoesNotOverlapBottomControls() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assert(sliderBounds.right - sliderBounds.left < 360.dp)
        assert(sliderBounds.left > referenceBounds.right)
        assert(sliderBounds.left > captureBounds.right)
    }

    @Test
    fun referenceButton_withoutReference_invokesSelectAndDoesNotOpenMenu() {
        var selectCount = 0
        setControlsContent(
            referenceUri = null,
            isLandscape = false,
            onSelectReferenceImage = { selectCount++ }
        )

        composeRule.onNodeWithTag("reference_action").performClick()
        composeRule.waitForIdle()

        assertEquals(1, selectCount)
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
    }

    @Test
    fun menu_opensOnTap_whenReferenceActive() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(resetDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(displayModeDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(replaceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(removeDescription()).assertIsDisplayed()
    }

    @Test
    fun menuActions_invokeExistingCallbacks() {
        var resetCount = 0
        var modeCount = 0
        var replaceCount = 0
        var removeCount = 0
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            onSelectReferenceImage = { replaceCount++ },
            onResetOverlay = { resetCount++ },
            onRemoveReferenceImage = { removeCount++ },
            onToggleDisplayMode = { modeCount++ }
        )

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(resetDescription()).performClick()
        composeRule.waitForIdle()
        openReferenceMenu()
        composeRule.onNodeWithContentDescription(displayModeDescription()).performClick()
        composeRule.waitForIdle()
        openReferenceMenu()
        composeRule.onNodeWithContentDescription(replaceDescription()).performClick()
        composeRule.waitForIdle()
        openReferenceMenu()
        composeRule.onNodeWithContentDescription(removeDescription()).performClick()
        composeRule.waitForIdle()

        assertEquals(1, resetCount)
        assertEquals(1, modeCount)
        assertEquals(1, replaceCount)
        assertEquals(1, removeCount)
    }

    @Test
    fun menu_closesAfterResetModeReplaceAndRemove() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(resetDescription()).performClick()
        composeRule.waitForIdle()
        assertMenuHidden()

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(displayModeDescription()).performClick()
        composeRule.waitForIdle()
        assertMenuHidden()

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(replaceDescription()).performClick()
        composeRule.waitForIdle()
        assertMenuHidden()

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(removeDescription()).performClick()
        composeRule.waitForIdle()
        assertMenuHidden()
    }

    @Test
    fun menu_closesOnOutsideTap() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(resetDescription()).assertIsDisplayed()

        composeRule.onNodeWithTag("reference_menu_backdrop").performClick()
        composeRule.waitForIdle()

        assertMenuHidden()
    }

    @Test
    fun slider_hiddenWhenMenuOpen() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
        openReferenceMenu()

        composeRule.onAllNodesWithContentDescription(opacityDescription()).assertCountEquals(0)
    }

    @Test
    fun slider_visibleWhenMenuClosed() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()
        composeRule.onNodeWithContentDescription(resetDescription()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
    }

    @Test
    fun controls_withMismatch_showMismatchHint() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).assertIsDisplayed()
    }

    private fun assertMenuHidden() {
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
    }

    private fun openReferenceMenu() {
        composeRule.onNodeWithTag("reference_action").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(resetDescription())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun setControlsContent(
        referenceUri: Uri?,
        isLandscape: Boolean,
        hasViewportMismatch: Boolean = false,
        onSelectReferenceImage: () -> Unit = {},
        onResetOverlay: () -> Unit = {},
        onRemoveReferenceImage: () -> Unit = {},
        onToggleDisplayMode: () -> Unit = {}
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                GhostShotTheme {
                    CameraControlsOverlay(
                        referenceUri = referenceUri,
                        alpha = 0.5f,
                        onAlphaChange = {},
                        onSelectReferenceImage = onSelectReferenceImage,
                        onResetOverlay = onResetOverlay,
                        onRemoveReferenceImage = onRemoveReferenceImage,
                        displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW,
                        hasViewportMismatch = hasViewportMismatch,
                        onToggleDisplayMode = onToggleDisplayMode,
                        onCapture = {},
                        isLandscape = isLandscape,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }

    private fun referenceDescription() = context.getString(R.string.select_reference_image)

    private fun captureDescription() = context.getString(R.string.capture_button_content_description)

    private fun resetDescription() = context.getString(R.string.reset_overlay_label)

    private fun removeDescription() = context.getString(R.string.remove_reference_image)

    private fun opacityDescription() = context.getString(R.string.overlay_opacity_label)

    private fun displayModeDescription() = context.getString(R.string.toggle_reference_display_mode)

    private fun replaceDescription() = context.getString(R.string.replace_reference_image)

    private fun mismatchDescription() = context.getString(R.string.reference_viewport_mismatch)
}
