package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
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
        composeRule.mainClock.autoAdvance = true
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

        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val buttonGroupWidth = captureBounds.right - referenceBounds.left

        assertCenterXWithin(sliderBounds, captureBounds, 1.dp)
        assert(sliderBounds.right - sliderBounds.left <= buttonGroupWidth)
        assert(sliderBounds.left >= rootBounds.left)
        assert(sliderBounds.right <= rootBounds.right)
        assertNoOverlap(sliderBounds, referenceBounds)
        assertNoOverlap(sliderBounds, captureBounds)
    }

    @Test
    fun captureButton_inLandscape_staysHorizontallyCenteredWithReferenceCompareAndSlider() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasSavedSessions = true
        )

        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()

        val rootCenterX = rootBounds.left + (rootBounds.right - rootBounds.left) / 2f
        val captureCenterX = captureBounds.left + (captureBounds.right - captureBounds.left) / 2f
        val delta = if (captureCenterX > rootCenterX) {
            captureCenterX - rootCenterX
        } else {
            rootCenterX - captureCenterX
        }

        assert(delta <= 1.dp)
    }

    @Test
    fun referenceAction_inLandscape_keepsMinimumWidth() {
        setLandscapeControlsContent()

        val referenceSlotBounds = composeRule
            .onNodeWithTag("reference_action_slot")
            .getUnclippedBoundsInRoot()
        val referenceActionBounds = composeRule
            .onNodeWithTag("reference_action", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assert(referenceSlotBounds.right - referenceSlotBounds.left >= 96.dp)
        assert(referenceActionBounds.right - referenceActionBounds.left >= 96.dp)
    }

    @Test
    fun bottomButtons_inLandscape_stayCompactAroundCapture() {
        setLandscapeControlsContent()

        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val compareBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()

        val leftGap = captureBounds.left - referenceBounds.right
        val rightGap = compareBounds.left - captureBounds.right
        val gapDelta = if (leftGap > rightGap) leftGap - rightGap else rightGap - leftGap

        assert(referenceBounds.right < captureBounds.left)
        assert(compareBounds.left > captureBounds.right)
        assert(leftGap in 8.dp..64.dp)
        assert(rightGap in 8.dp..64.dp)
        assert(gapDelta <= 24.dp)
    }

    @Test
    fun opacitySlider_inLandscape_isCenteredAboveButtonGroupWithCompare() {
        setLandscapeControlsContent()

        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val compareBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val buttonGroupWidth = compareBounds.right - referenceBounds.left

        assertCenterXWithin(sliderBounds, captureBounds, 1.dp)
        assert(sliderBounds.right - sliderBounds.left <= buttonGroupWidth)
        assert(sliderBounds.left >= rootBounds.left)
        assert(sliderBounds.right <= rootBounds.right)
        assertNoOverlap(sliderBounds, captureBounds)
        assertNoOverlap(sliderBounds, referenceBounds)
        assertNoOverlap(sliderBounds, compareBounds)
    }

    @Test
    fun opacitySlider_inLandscape_keepsUsableWidthWithinButtonGroup() {
        setLandscapeControlsContent()

        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val compareBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val buttonGroupWidth = compareBounds.right - referenceBounds.left

        assert(sliderBounds.right - sliderBounds.left >= 180.dp)
        assert(sliderBounds.right - sliderBounds.left <= buttonGroupWidth)
        assert(sliderBounds.left >= rootBounds.left)
        assert(sliderBounds.right <= rootBounds.right)
        assertNoOverlap(sliderBounds, captureBounds)
        assertNoOverlap(sliderBounds, referenceBounds)
        assertNoOverlap(sliderBounds, compareBounds)
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

        assert(captureBounds.left - referenceBounds.right > 8.dp)
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

        assert(captureBounds.left - referenceBounds.right > 8.dp)
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
        setLandscapeControlsContent()

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
        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()

        assertMenuAboveReference(menuBounds, referenceBounds)
        assertNoOverlap(menuBounds, captureBounds)
        assert(menuBounds.left >= rootBounds.left)
        assert(menuBounds.right <= rootBounds.right)
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
        setLandscapeControlsContent()

        openReferenceMenu()

        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()
        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
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

        assertCenterXWithin(sliderBounds, captureBounds, 1.dp)
        assert(sliderBounds.left >= rootBounds.left)
        assert(sliderBounds.right <= rootBounds.right)
        assert(menuBounds.left >= rootBounds.left)
        assert(menuBounds.right <= rootBounds.right)
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
        composeRule.onNodeWithContentDescription(mismatchDescription()).assertHasClickAction()
        composeRule.onAllNodesWithText(formatMismatchBubble()).assertCountEquals(0)
    }

    @Test
    fun formatMismatchBadge_usesSeparateIconFromDisplayModeMenu() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        openReferenceMenu()

        composeRule.onNodeWithTag("format_mismatch_hint_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("reference_display_mode_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("format_mismatch_hint_icon", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("reference_display_mode_icon", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun controls_withMismatchInLandscape_showHintInsideTopStartBounds() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasViewportMismatch = true
        )

        val hintBounds = composeRule
            .onNodeWithContentDescription(mismatchDescription())
            .getUnclippedBoundsInRoot()
        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()

        assert(hintBounds.left >= rootBounds.left)
        assert(hintBounds.top >= rootBounds.top)
        assert(hintBounds.right <= rootBounds.right)
        assert(hintBounds.bottom <= rootBounds.bottom)
    }

    @Test
    fun formatMismatchBubble_showsWhenBadgeTapped() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(formatMismatchBubble()).assertIsDisplayed()
    }

    @Test
    fun formatMismatchBubble_disappearsAutomatically() {
        composeRule.mainClock.autoAdvance = false
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(formatMismatchBubble()).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(2_200)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(formatMismatchBubble()).assertCountEquals(0)
    }

    @Test
    fun formatMismatchBubble_repeatedTapResetsTimer() {
        composeRule.mainClock.autoAdvance = false
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(formatMismatchBubble()).assertCountEquals(1)

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(formatMismatchBubble()).assertCountEquals(1)

        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(formatMismatchBubble()).assertCountEquals(0)
    }

    @Test
    fun formatMismatchBubble_staysInsideScreenBounds() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.waitForIdle()

        val bubbleBounds = composeRule
            .onNodeWithTag("format_mismatch_hint_bubble")
            .getUnclippedBoundsInRoot()
        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()

        assert(bubbleBounds.left >= rootBounds.left)
        assert(bubbleBounds.top >= rootBounds.top)
        assert(bubbleBounds.right <= rootBounds.right)
        assert(bubbleBounds.bottom <= rootBounds.bottom)
    }

    @Test
    fun formatMismatchBubble_isBelowAndRightOfBadge() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = false,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.waitForIdle()

        val badgeBounds = composeRule
            .onNodeWithContentDescription(mismatchDescription())
            .getUnclippedBoundsInRoot()
        val bubbleBounds = composeRule
            .onNodeWithTag("format_mismatch_hint_bubble")
            .getUnclippedBoundsInRoot()

        assert(bubbleBounds.top > badgeBounds.bottom)
        assert(bubbleBounds.left > badgeBounds.left)
    }

    @Test
    fun formatMismatchBubble_inLandscape_staysInsideScreenBounds() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasViewportMismatch = true
        )

        composeRule.onNodeWithContentDescription(mismatchDescription()).performClick()
        composeRule.waitForIdle()

        val bubbleBounds = composeRule
            .onNodeWithTag("format_mismatch_hint_bubble")
            .getUnclippedBoundsInRoot()
        val rootBounds = composeRule
            .onNodeWithTag("camera_controls_root")
            .getUnclippedBoundsInRoot()

        assert(bubbleBounds.left >= rootBounds.left)
        assert(bubbleBounds.top >= rootBounds.top)
        assert(bubbleBounds.right <= rootBounds.right)
        assert(bubbleBounds.bottom <= rootBounds.bottom)
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

    @Test
    fun snackbar_inPortrait_doesNotOverlapOpacitySlider() {
        val snackbarMessage = captureSavedText()
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
                            referenceUri = Uri.parse("content://ghostshot/test-reference"),
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
                            snackbarHostState.showSnackbar(
                                message = snackbarMessage,
                                duration = SnackbarDuration.Indefinite
                            )
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
            composeRule.onAllNodesWithText(snackbarMessage).fetchSemanticsNodes().isNotEmpty()
        }

        val snackbarBounds = composeRule
            .onNodeWithTag("camera_snackbar_host")
            .getUnclippedBoundsInRoot()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()

        assertNoOverlap(snackbarBounds, sliderBounds)
    }

    @Test
    fun captureSuccess_doesNotReplayAfterRecreation() {
        val savedText = context.getString(R.string.capture_saved)
        val compareText = context.getString(R.string.capture_saved_compare_action)

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
                    CaptureSuccessSnackbarTestHost(
                        captureSuccessGeneration = 1L,
                        captureSuccessHadReference = false,
                        message = savedText,
                        actionLabel = compareText
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(savedText).fetchSemanticsNodes().isNotEmpty()
        }

        scenario?.recreate()
        composeRule.waitForIdle()

        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                GhostShotTheme {
                    CaptureSuccessSnackbarTestHost(
                        captureSuccessGeneration = 1L,
                        captureSuccessHadReference = false,
                        message = savedText,
                        actionLabel = compareText
                    )
                }
            }
        }
        // Advance clock past any initial frame delays to give the LaunchedEffect a chance to run
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(savedText).assertCountEquals(0)
    }

    @Test
    fun captureSuccess_withoutReference_dismissesAfter2000ms() {
        val savedText = context.getString(R.string.capture_saved)
        val compareText = context.getString(R.string.capture_saved_compare_action)

        composeRule.mainClock.autoAdvance = false
        setCaptureSuccessTestContent {
            CaptureSuccessSnackbarTestHost(
                captureSuccessGeneration = 1L,
                captureSuccessHadReference = false,
                message = savedText,
                actionLabel = compareText
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(savedText).assertIsDisplayed()

        // Still visible just before the 2000 ms mark
        composeRule.mainClock.advanceTimeBy(1_850) // t = 1950 ms
        composeRule.waitForIdle()
        composeRule.onNodeWithText(savedText).assertIsDisplayed()

        // Past 2000 ms + exit animation buffer
        composeRule.mainClock.advanceTimeBy(500) // t = 2450 ms
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(savedText).assertCountEquals(0)
    }

    @Test
    fun captureSuccess_withReference_dismissesAfter2500ms() {
        val savedText = context.getString(R.string.capture_saved)
        val compareText = context.getString(R.string.capture_saved_compare_action)

        composeRule.mainClock.autoAdvance = false
        setCaptureSuccessTestContent {
            CaptureSuccessSnackbarTestHost(
                captureSuccessGeneration = 1L,
                captureSuccessHadReference = true,
                message = savedText,
                actionLabel = compareText
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(savedText).assertIsDisplayed()

        // Still visible just before the 2500 ms mark
        composeRule.mainClock.advanceTimeBy(2_350) // t = 2450 ms
        composeRule.waitForIdle()
        composeRule.onNodeWithText(savedText).assertIsDisplayed()

        // Past 2500 ms + exit animation buffer
        composeRule.mainClock.advanceTimeBy(500) // t = 2950 ms
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(savedText).assertCountEquals(0)
    }

    @Test
    fun undoSnackbar_showsFromUndoState() {
        setUndoSnackbarContent(canUndoReferenceRemoval = true, undoGeneration = 1L)

        composeRule.onNodeWithText(referenceRemovedSnackbar()).assertIsDisplayed()
        composeRule.onNodeWithText(referenceRemovedUndo()).assertIsDisplayed()
    }

    @Test
    fun undoSnackbar_reappearsAfterContentRecreation_whenUndoStateStillAvailable() {
        setUndoSnackbarContent(canUndoReferenceRemoval = true, undoGeneration = 1L)
        composeRule.onNodeWithText(referenceRemovedSnackbar()).assertIsDisplayed()

        setUndoSnackbarContent(canUndoReferenceRemoval = true, undoGeneration = 1L, reuseScenario = true)

        composeRule.onNodeWithText(referenceRemovedSnackbar()).assertIsDisplayed()
        composeRule.onNodeWithText(referenceRemovedUndo()).assertIsDisplayed()
    }

    @Test
    fun undoSnackbar_doesNotDuplicateOnRecompositionWithSameGeneration() {
        var forceRecompose: (() -> Unit)? = null
        setUndoSnackbarTestContent {
            var nonce by remember { mutableStateOf(0) }
            forceRecompose = { nonce++ }
            UndoSnackbarTestHost(
                canUndoReferenceRemoval = true,
                undoGeneration = 1L,
                message = referenceRemovedSnackbar(),
                actionLabel = referenceRemovedUndo(),
                nonce = nonce
            )
        }
        composeRule.onNodeWithText(referenceRemovedSnackbar()).assertIsDisplayed()

        composeRule.runOnIdle { forceRecompose?.invoke() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(referenceRemovedSnackbar()).assertCountEquals(1)
    }

    @Test
    fun undoSnackbar_newGenerationShowsNewSnackbar() {
        var showNextGeneration: (() -> Unit)? = null
        setUndoSnackbarTestContent {
            var generation by remember { mutableStateOf(1L) }
            showNextGeneration = { generation = 2L }
            UndoSnackbarTestHost(
                canUndoReferenceRemoval = true,
                undoGeneration = generation,
                message = "${referenceRemovedSnackbar()} $generation",
                actionLabel = referenceRemovedUndo()
            )
        }
        composeRule.onNodeWithText("${referenceRemovedSnackbar()} 1").assertIsDisplayed()

        composeRule.runOnIdle { showNextGeneration?.invoke() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("${referenceRemovedSnackbar()} 2")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("${referenceRemovedSnackbar()} 2").assertIsDisplayed()
    }

    @Test
    fun undoSnackbar_doesNotShowWhenUndoUnavailable() {
        setUndoSnackbarContent(canUndoReferenceRemoval = false, undoGeneration = 0L)

        composeRule.onAllNodesWithText(referenceRemovedSnackbar()).assertCountEquals(0)
        composeRule.onAllNodesWithText(referenceRemovedUndo()).assertCountEquals(0)
    }

    @Test
    fun captureSuccess_withoutReference_showsSnackbarWithoutAction() {
        setCaptureSuccessContent(captureSuccessGeneration = 1L, captureSuccessHadReference = false)

        composeRule.onNodeWithText(captureSavedText()).assertIsDisplayed()
        composeRule.onAllNodesWithText(captureCompareActionText()).assertCountEquals(0)
    }

    @Test
    fun captureSuccess_withReference_showsSnackbarWithCompareAction() {
        setCaptureSuccessContent(captureSuccessGeneration = 1L, captureSuccessHadReference = true)

        composeRule.onNodeWithText(captureSavedText()).assertIsDisplayed()
        composeRule.onNodeWithText(captureCompareActionText()).assertIsDisplayed()
    }

    @Test
    fun compareEntry_inLandscape_isInBottomZone() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasSavedSessions = true
        )

        val compareEntryBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val referenceZoneBounds = composeRule
            .onNodeWithTag("reference_action_slot")
            .getUnclippedBoundsInRoot()

        assertCenterYWithin(compareEntryBounds, captureBounds, 80.dp)
        assertNoOverlap(compareEntryBounds, captureBounds)
        assertNoOverlap(compareEntryBounds, referenceZoneBounds)
    }

    @Test
    fun compareEntry_doesNotOverlapControls() {
        setControlsContent(
            referenceUri = Uri.parse("content://ghostshot/test-reference"),
            isLandscape = true,
            hasSavedSessions = true
        )

        val compareEntryBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()
        val captureBounds = composeRule
            .onNodeWithContentDescription(captureDescription())
            .getUnclippedBoundsInRoot()
        val referenceBounds = composeRule
            .onNodeWithContentDescription(referenceDescription())
            .getUnclippedBoundsInRoot()
        val sliderBounds = composeRule
            .onNodeWithContentDescription(opacityDescription())
            .getUnclippedBoundsInRoot()

        assertNoOverlap(compareEntryBounds, captureBounds)
        assertNoOverlap(compareEntryBounds, referenceBounds)
        assertNoOverlap(compareEntryBounds, sliderBounds)
    }

    @Test
    fun compareEntry_inLandscape_doesNotOverlapSnackbar() {
        val snackbarMessage = captureSavedText()
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
                            referenceUri = Uri.parse("content://ghostshot/test-reference"),
                            hasSavedSessions = true,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onRemoveReferenceImage = {},
                            onCapture = {},
                            isLandscape = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        val snackbarHostState = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) {
                            snackbarHostState.showSnackbar(
                                message = snackbarMessage,
                                duration = SnackbarDuration.Indefinite
                            )
                        }
                        CameraSnackbarHost(
                            hostState = snackbarHostState,
                            isLandscape = true,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(snackbarMessage).fetchSemanticsNodes().isNotEmpty()
        }

        val snackbarBounds = composeRule
            .onNodeWithTag("camera_snackbar_host")
            .getUnclippedBoundsInRoot()
        val compareEntryBounds = composeRule
            .onNodeWithTag("compare_images_entry")
            .getUnclippedBoundsInRoot()

        assertNoOverlap(snackbarBounds, compareEntryBounds)
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

    private fun assertCenterXWithin(first: DpRect, second: DpRect, tolerance: Dp) {
        val firstCenter = first.left + (first.right - first.left) / 2f
        val secondCenter = second.left + (second.right - second.left) / 2f
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

    private fun setUndoSnackbarContent(
        canUndoReferenceRemoval: Boolean,
        undoGeneration: Long,
        reuseScenario: Boolean = false
    ) {
        setUndoSnackbarTestContent(reuseScenario = reuseScenario) {
            UndoSnackbarTestHost(
                canUndoReferenceRemoval = canUndoReferenceRemoval,
                undoGeneration = undoGeneration,
                message = referenceRemovedSnackbar(),
                actionLabel = referenceRemovedUndo()
            )
        }
    }

    private fun setUndoSnackbarTestContent(
        reuseScenario: Boolean = false,
        content: @Composable () -> Unit
    ) {
        wakeTestDevice()
        if (!reuseScenario || scenario == null) {
            scenario?.close()
            scenario = ActivityScenario.launch(ComponentActivity::class.java)
        }
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                GhostShotTheme {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setCaptureSuccessTestContent(content: @Composable () -> Unit) {
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
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun UndoSnackbarTestHost(
        canUndoReferenceRemoval: Boolean,
        undoGeneration: Long,
        message: String,
        actionLabel: String,
        nonce: Int = 0
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val snackbarHostState = remember { SnackbarHostState() }
            ReferenceRemovalUndoSnackbarEffect(
                canUndoReferenceRemoval = canUndoReferenceRemoval,
                undoGeneration = undoGeneration,
                hostState = snackbarHostState,
                message = message,
                actionLabel = actionLabel,
                onUndo = {}
            )
            CameraSnackbarHost(
                hostState = snackbarHostState,
                isLandscape = false,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            Box(modifier = Modifier.testTag("undo_snackbar_recompose_$nonce"))
        }
    }

    private fun setCaptureSuccessContent(
        captureSuccessGeneration: Long,
        captureSuccessHadReference: Boolean
    ) {
        val savedText = context.getString(R.string.capture_saved)
        val compareText = context.getString(R.string.capture_saved_compare_action)
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
                    CaptureSuccessSnackbarTestHost(
                        captureSuccessGeneration = captureSuccessGeneration,
                        captureSuccessHadReference = captureSuccessHadReference,
                        message = savedText,
                        actionLabel = compareText
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(savedText)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Composable
    private fun CaptureSuccessSnackbarTestHost(
        captureSuccessGeneration: Long,
        captureSuccessHadReference: Boolean,
        message: String,
        actionLabel: String
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val snackbarHostState = remember { SnackbarHostState() }
            CaptureSuccessSnackbarEffect(
                captureSuccessGeneration = captureSuccessGeneration,
                captureSuccessHadReference = captureSuccessHadReference,
                hostState = snackbarHostState,
                message = message,
                actionLabel = actionLabel,
                onCompare = {}
            )
            CameraSnackbarHost(
                hostState = snackbarHostState,
                isLandscape = false,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    private fun setLandscapeControlsContent() {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
                            referenceUri = Uri.parse("content://ghostshot/test-reference"),
                            hasSavedSessions = true,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onRemoveReferenceImage = {},
                            onCapture = {},
                            isLandscape = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setControlsContent(
        referenceUri: Uri?,
        isLandscape: Boolean,
        hasViewportMismatch: Boolean = false,
        hasSavedSessions: Boolean = false,
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
                        hasSavedSessions = hasSavedSessions,
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

    private fun mismatchDescription() = context.getString(R.string.reference_format_mismatch_description)

    private fun formatMismatchBubble() = context.getString(R.string.reference_format_mismatch_bubble)

    private fun referenceRemovedSnackbar() = context.getString(R.string.reference_removed_snackbar)

    private fun referenceRemovedUndo() = context.getString(R.string.reference_removed_undo)

    private fun captureSavedText() = context.getString(R.string.capture_saved)

    private fun captureCompareActionText() = context.getString(R.string.capture_saved_compare_action)
}
