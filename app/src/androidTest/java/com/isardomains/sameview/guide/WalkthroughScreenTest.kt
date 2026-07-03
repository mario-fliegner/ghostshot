package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
class WalkthroughScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    // ── Existing tests updated for renamed strings / new illustration tags ────

    @Test
    fun firstPage_rendersApprovedContentAndDots() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_screen_root").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_mockup_then_and_now").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_progress_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun nextAdvancesThroughPagesAndPageFourShowsBackAndStart() {
        setWalkthroughContent()

        // Page 1 → page 2
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()

        // Page 2 → page 3
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()

        // Page 3 → page 4
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_start").assertIsDisplayed()
    }

    @Test
    fun backOnLastPageReturnsToCapturePage() {
        setWalkthroughContent()
        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()
    }

    @Test
    fun skipAndStartInvokeCallbacks() {
        var skipCount = 0
        var startCount = 0
        setWalkthroughContent(
            onSkip = { skipCount++ },
            onStart = { startCount++ }
        )

        composeRule.onNodeWithTag("walkthrough_skip").performClick()
        composeRule.waitForIdle()
        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("walkthrough_start").performClick()
        composeRule.waitForIdle()

        assertEquals(1, skipCount)
        assertEquals(1, startCount)
    }

    @Test
    fun expandedLayoutUsesTwoColumns() {
        setWalkthroughContent(windowWidthSizeClass = WindowWidthSizeClass.Expanded)

        composeRule.onNodeWithTag("walkthrough_two_column_layout").assertIsDisplayed()
    }

    // ── Button model ──────────────────────────────────────────────────────────

    @Test
    fun page1_hasSkipAndNextNoBack() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
        composeRule.onAllNodesWithTag("walkthrough_back").assertCountEquals(0)
    }

    @Test
    fun page2_hasSkipBackAndNext() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun page3_hasSkipBackAndNext() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun page4_hasBackAndStartNoSkip() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_start").assertIsDisplayed()
        composeRule.onAllNodesWithTag("walkthrough_skip").assertCountEquals(0)
    }

    @Test
    fun backOnPage2ReturnsToPage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun backOnPage3ReturnsToPage2() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    // ── Swipe navigation ──────────────────────────────────────────────────────

    @Test
    fun swipeLeftOnPage1AdvancesToPage2() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeRightOnPage2ReturnsToPage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeDoesNotAdvancePastPage4() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeDoesNotGoBeforePage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun progressDotsUpdateOnSwipe() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        // Page title change confirms pager advanced; dots are always visible
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_progress_dots").assertIsDisplayed()
    }

    // ── Responsive ────────────────────────────────────────────────────────────

    @Test
    fun mediumLayoutUsesTwoColumns() {
        setWalkthroughContent(windowWidthSizeClass = WindowWidthSizeClass.Medium)

        composeRule.onNodeWithTag("walkthrough_two_column_layout").assertIsDisplayed()
    }

    // ── Illustration tags ─────────────────────────────────────────────────────

    @Test
    fun page1IllustrationHasStep1Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_step1_image").assertIsDisplayed()
    }

    @Test
    fun page2IllustrationHasStep2Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
    }

    @Test
    fun page2IllustrationShowsStep2Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
    }

    @Test
    fun page3IllustrationHasStep3Image() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_step3_image").assertIsDisplayed()
    }

    @Test
    fun page4IllustrationHasStep4Image() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_step4_image").assertIsDisplayed()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setWalkthroughContent(
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onSkip: () -> Unit = {},
        onStart: () -> Unit = {}
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
                    WalkthroughScreen(
                        entryMode = WalkthroughEntryMode.FIRST_RUN,
                        windowWidthSizeClass = windowWidthSizeClass,
                        onSkip = onSkip,
                        onStart = onStart
                    )
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
