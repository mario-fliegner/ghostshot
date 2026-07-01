package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
class GuideScreenTest {

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
    fun guideScreen_rendersTopicList() {
        setGuideScreenContent()

        composeRule.onNodeWithTag("guide_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_topic_getting_started").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.guide_topic_reference_photos_title))
            .assertIsDisplayed()
    }

    @Test
    fun guideScreen_topicClickInvokesCallback() {
        var openedTopic: GuideTopicId? = null
        setGuideScreenContent(onOpenTopic = { openedTopic = it })

        composeRule.onNodeWithTag("guide_topic_getting_started").performClick()
        composeRule.waitForIdle()

        assertEquals(GuideTopicId.GETTING_STARTED, openedTopic)
    }

    @Test
    fun guideScreen_bottomActionsAreSeparateFromTopics() {
        setGuideScreenContent()

        scrollToBottomActions()
        composeRule.onNodeWithTag("guide_bottom_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_show_tips_again").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_show_walkthrough_again").assertIsDisplayed()
    }

    @Test
    fun showTipsAgain_opensConfirmationAndConfirmInvokesReset() {
        var showDialog by mutableStateOf(false)
        var resetCount = 0
        setActivityContent {
            GuideScreen(
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                showResetTipsConfirmation = showDialog,
                onBack = {},
                onOpenTopic = {},
                onShowTipsAgain = { showDialog = true },
                onDismissResetTips = { showDialog = false },
                onConfirmResetTips = {
                    resetCount++
                    showDialog = false
                },
                onShowWalkthroughAgain = {}
            )
        }

        scrollToBottomActions()
        composeRule.onNodeWithTag("guide_show_tips_again").performClick()
        composeRule.onNodeWithTag("guide_show_tips_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_show_tips_confirm").performClick()
        composeRule.waitForIdle()

        assertEquals(1, resetCount)
    }

    @Test
    fun showWalkthroughAgain_invokesCallbackOnly() {
        var replayCount = 0
        setGuideScreenContent(onShowWalkthroughAgain = { replayCount++ })

        scrollToBottomActions()
        composeRule.onNodeWithTag("guide_show_walkthrough_again").performClick()
        composeRule.waitForIdle()

        assertEquals(1, replayCount)
    }

    @Test
    fun guideDetail_usesVerticalReadingFlow() {
        setActivityContent {
            GuideDetailScreen(
                topicId = GuideTopicId.REFERENCE_PHOTOS,
                windowWidthSizeClass = WindowWidthSizeClass.Expanded,
                onBack = {}
            )
        }

        composeRule.onNodeWithTag("guide_detail_root").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_title").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_intro").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_visual_0").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_body_0").assertIsDisplayed()
    }

    private fun scrollToBottomActions() {
        composeRule.onNodeWithTag("guide_topic_grid").performScrollToIndex(GuideTopicRegistry.topics.size)
        composeRule.waitForIdle()
    }

    private fun setGuideScreenContent(
        onOpenTopic: (GuideTopicId) -> Unit = {},
        onShowWalkthroughAgain: () -> Unit = {}
    ) {
        setActivityContent {
            GuideScreen(
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                showResetTipsConfirmation = false,
                onBack = {},
                onOpenTopic = onOpenTopic,
                onShowTipsAgain = {},
                onDismissResetTips = {},
                onConfirmResetTips = {},
                onShowWalkthroughAgain = onShowWalkthroughAgain
            )
        }
    }

    private fun setActivityContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme { content() }
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

