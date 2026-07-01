package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideTipHostTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun host_rendersSameViewTipCardWithActionsAndPointer() {
        setHostContent()

        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_title").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_body").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_learn_more").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_got_it").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_pointer").assertIsDisplayed()
    }

    @Test
    fun gotIt_invokesCallbackForActiveTip() {
        var dismissedTip: GuideTip? = null
        setHostContent(onGotIt = { dismissedTip = it })

        composeRule.onNodeWithTag("guide_tip_got_it").performClick()
        composeRule.waitForIdle()

        assertEquals(GuideTipId.REFERENCE, dismissedTip?.id)
    }

    @Test
    fun learnMore_invokesCallbackWithGuideTopic() {
        var openedTip: GuideTip? = null
        var openedTopic: GuideTopicId? = null
        setHostContent(
            onLearnMore = { tip, topicId ->
                openedTip = tip
                openedTopic = topicId
            }
        )

        composeRule.onNodeWithTag("guide_tip_learn_more").performClick()
        composeRule.waitForIdle()

        assertEquals(GuideTipId.REFERENCE, openedTip?.id)
        assertEquals(GuideTopicId.REFERENCE_PHOTOS, openedTopic)
    }

    @Test
    fun missingAnchor_defersTipWithoutRenderingCard() {
        setHostContent(anchors = emptyList())

        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()
    }

    @Test
    fun unsafeAnchor_defersTipWithoutRenderingCard() {
        setHostContent(
            anchors = listOf(
                GuideTipAnchor(
                    key = GuideTipAnchorKey.REFERENCE_BUTTON,
                    bounds = Rect(100f, 100f, 100f, 120f)
                )
            )
        )

        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()
    }

    @Test
    fun compactPlacement_prefersAboveThenBelow() {
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(420, 640),
                cardSize = IntSize(220, 110),
                anchorBounds = Rect(180f, 260f, 240f, 320f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                marginPx = 16f,
                gapPx = 8f
            )
        )

        assertEquals(GuideTipPlacementSide.ABOVE, (result as GuideTipPlacementResult.Placed).side)
    }

    @Test
    fun mediumPlacement_prefersSideWhenSafe() {
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(720, 420),
                cardSize = IntSize(220, 120),
                anchorBounds = Rect(200f, 180f, 260f, 240f),
                windowWidthSizeClass = WindowWidthSizeClass.Medium,
                marginPx = 16f,
                gapPx = 8f
            )
        )

        assertEquals(GuideTipPlacementSide.END, (result as GuideTipPlacementResult.Placed).side)
    }

    @Test
    fun expandedPlacement_staysCloseToTarget() {
        val anchor = Rect(320f, 260f, 380f, 320f)
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(1200, 800),
                cardSize = IntSize(280, 140),
                anchorBounds = anchor,
                windowWidthSizeClass = WindowWidthSizeClass.Expanded,
                marginPx = 16f,
                gapPx = 8f
            )
        ) as GuideTipPlacementResult.Placed

        assertEquals(GuideTipPlacementSide.END, result.side)
        assertEquals(8, result.offset.x - anchor.right.toInt())
    }

    @Test
    fun unsafePlacement_defersInsteadOfCoveringTarget() {
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(240, 240),
                cardSize = IntSize(210, 210),
                anchorBounds = Rect(100f, 100f, 140f, 140f),
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                marginPx = 16f,
                gapPx = 8f
            )
        )

        assertEquals(GuideTipPlacementResult.Deferred, result)
    }

    @Test
    fun placedCardDoesNotOverlapTarget() {
        val anchor = Rect(200f, 180f, 260f, 240f)
        val cardSize = IntSize(220, 120)
        val result = calculateGuideTipPlacement(
            GuideTipPlacementInput(
                containerSize = IntSize(720, 420),
                cardSize = cardSize,
                anchorBounds = anchor,
                windowWidthSizeClass = WindowWidthSizeClass.Medium,
                marginPx = 16f,
                gapPx = 8f
            )
        ) as GuideTipPlacementResult.Placed
        val cardRect = Rect(
            result.offset.x.toFloat(),
            result.offset.y.toFloat(),
            result.offset.x + cardSize.width.toFloat(),
            result.offset.y + cardSize.height.toFloat()
        )

        assertFalse(cardRect.left < anchor.right && cardRect.right > anchor.left && cardRect.top < anchor.bottom && cardRect.bottom > anchor.top)
        assertTrue(anchor.width > 0f && anchor.height > 0f)
    }

    private fun setHostContent(
        activeTip: GuideTip? = GuideTipRegistry.tipFor(GuideTipId.REFERENCE),
        anchors: List<GuideTipAnchor> = listOf(
            GuideTipAnchor(
                key = GuideTipAnchorKey.REFERENCE_BUTTON,
                bounds = Rect(140f, 240f, 220f, 300f)
            )
        ),
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onGotIt: (GuideTip) -> Unit = {},
        onLearnMore: (GuideTip, GuideTopicId) -> Unit = { _, _ -> }
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
                SameViewTheme {
                    Box(modifier = Modifier.size(420.dp)) {
                        GuideTipHost(
                            activeTip = activeTip,
                            anchors = anchors,
                            windowWidthSizeClass = windowWidthSizeClass,
                            onGotIt = onGotIt,
                            onLearnMore = onLearnMore
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}


