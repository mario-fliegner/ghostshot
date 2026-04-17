package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
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
        composeRule.onNodeWithTag("reference_action_add_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("reference_action_active_indicator", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("reference_action_options_badge", useUnmergedTree = true).assertCountEquals(0)
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
        composeRule.onNodeWithTag("reference_action_active_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("reference_action_options_badge", useUnmergedTree = true).assertIsDisplayed()
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
        composeRule.onNodeWithTag("reference_action_active_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("reference_action_options_badge", useUnmergedTree = true).assertIsDisplayed()
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
        assertCenterYWithin(sliderBounds, captureBounds, 48.dp)
    }

    @Test
    fun bottomControls_keepClearSpacingInPortraitAndLandscape() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        var referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        var referenceZoneBounds = composeRule
            .onNodeWithTag("reference_action_slot")
            .getUnclippedBoundsInRoot()
        var captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assert(captureBounds.left - referenceBounds.right > 24.dp)
        assertCenterYWithin(referenceZoneBounds, captureBounds, 12.dp)

        scenario?.close()
        scenario = null
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        referenceZoneBounds = composeRule
            .onNodeWithTag("reference_action_slot")
            .getUnclippedBoundsInRoot()
        captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assert(captureBounds.left - referenceBounds.right > 24.dp)
        assertCenterYWithin(referenceZoneBounds, captureBounds, 12.dp)
    }

    @Test
    fun referenceButton_withoutReference_invokesSelectAndDoesNotOpenMenu() {
        var selectCount = 0
        setControlsContent(
            referenceUri = null,
            isLandscape = false,
            onSelectReferenceImage = { selectCount++ }
        )

        composeRule.onNodeWithTag("reference_action", useUnmergedTree = true).performClick()
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
        composeRule.onNodeWithText(modeCompareText()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(replaceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(removeDescription()).assertIsDisplayed()
    }

    @Test
    fun menu_togglesClosedOnSecondReferenceTap_whenReferenceActive() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()
        composeRule.onNodeWithTag("reference_action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertMenuHidden()
    }

    @Test
    fun menuModeEntry_showsCurrentFitMode() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            displayMode = ReferenceImageDisplayMode.SHOW_FULL_IMAGE
        )

        openReferenceMenu()

        composeRule.onNodeWithText(modeFitText()).assertIsDisplayed()
    }

    @Test
    fun menu_whenOpen_isCompactAndAnchoredToReferenceButton() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        openReferenceMenu()

        val menuBounds = composeRule
            .onNodeWithTag("reference_action_menu")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithTag("reference_action")
            .getUnclippedBoundsInRoot()

        assert(menuBounds.right - menuBounds.left < 260.dp)
        assert(menuBounds.left == referenceBounds.left)
        assertMenuAboveReference(menuBounds, referenceBounds)
    }

    @Test
    fun menuInLandscape_opensAboveReferenceAndDoesNotOverlapCapture() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        openReferenceMenu()

        val menuBounds = composeRule
            .onNodeWithTag("reference_action_menu")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithTag("reference_action")
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assertMenuAboveReference(menuBounds, referenceBounds)
        assertNoOverlap(menuBounds, captureBounds)
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
    fun slider_hiddenWhenMenuOpen_inPortrait() {
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
    fun slider_visibleWhenMenuOpen_inLandscape() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val menuBounds = composeRule
            .onNodeWithTag("reference_action_menu")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assertNoOverlap(sliderBounds, menuBounds)
        assertNoOverlap(sliderBounds, referenceBounds)
        assertNoOverlap(sliderBounds, captureBounds)
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

    @Test
    fun menuWithMismatch_inLandscape_doesNotOverlapWarning() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasViewportMismatch = true
        )

        openReferenceMenu()

        val menuBounds = composeRule
            .onNodeWithTag("reference_action_menu")
            .getUnclippedBoundsInRoot()
        val warningBounds = composeRule
            .onNodeWithContentDescription(mismatchDescription())
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithTag("reference_action")
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assertMenuAboveReference(menuBounds, referenceBounds)
        assertNoOverlap(menuBounds, warningBounds)
        assertNoOverlap(menuBounds, captureBounds)
    }

    @Test
    fun snackbar_doesNotOverlapBottomControls() {
        setScreenControlsContentWithSnackbar(referenceUri = Uri.parse("content://ghostshot/test-reference"))

        composeRule.onNodeWithTag("camera_snackbar_host").assertIsDisplayed()

        val snackbarBounds = composeRule
            .onNodeWithTag("camera_snackbar_host")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        assertNoOverlap(snackbarBounds, referenceBounds)
        assertNoOverlap(snackbarBounds, captureBounds)
    }

    private fun assertMenuHidden() {
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(displayModeDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(replaceDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(removeDescription()).assertCountEquals(0)
    }

    private fun assertNoOverlap(first: DpRect, second: DpRect) {
        assert(
            first.right <= second.left ||
                second.right <= first.left ||
                first.bottom <= second.top ||
                second.bottom <= first.top
        )
    }

    private fun assertMenuAboveReference(menuBounds: DpRect, referenceBounds: DpRect) {
        assert(menuBounds.bottom <= referenceBounds.top)
    }

    private fun assertCenterYWithin(first: DpRect, second: DpRect, tolerance: Dp) {
        val firstCenter = first.top + (first.bottom - first.top) / 2f
        val secondCenter = second.top + (second.bottom - second.top) / 2f
        val delta = if (firstCenter > secondCenter) {
            firstCenter - secondCenter
        } else {
            secondCenter - firstCenter
        }
        assert(delta <= tolerance)
    }

    private fun openReferenceMenu() {
        composeRule.onNodeWithTag("reference_action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithContentDescription(resetDescription()).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithContentDescription(referenceDescription()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(resetDescription())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun setScreenControlsContentWithSnackbar(referenceUri: Uri?) {
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraControlsOverlay(
                            referenceUri = referenceUri,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onRemoveReferenceImage = {},
                            onCapture = {},
                            isLandscape = false,
                            modifier = Modifier.fillMaxSize()
                        )
                        val snackbarHostState = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) {
                            snackbarHostState.showSnackbar("Reference removed")
                        }
                        CameraSnackbarHost(
                            hostState = snackbarHostState,
                            isLandscape = false,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("camera_snackbar_host")
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
        onToggleDisplayMode: () -> Unit = {},
        displayMode: ReferenceImageDisplayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
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
                        displayMode = displayMode,
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

    private fun modeCompareText() = context.getString(R.string.action_stack_display_mode_compare_label)

    private fun modeFitText() = context.getString(R.string.action_stack_display_mode_fit_label)

    private fun mismatchDescription() = context.getString(R.string.reference_viewport_mismatch)
}
