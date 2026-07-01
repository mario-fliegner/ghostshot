package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun firstPage_rendersApprovedContentAndDots() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_screen_root").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_choose_photo_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_mockup_choose_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_progress_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun nextAdvancesThroughPagesAndPageFourShowsBackAndStart() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_reference_overlay").assertIsDisplayed()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_capture_title))
            .assertIsDisplayed()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_compare_title))
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

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_capture_title))
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
