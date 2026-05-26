package com.isardomains.ghostshot.ui.about

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {

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
    fun aboutContent_showsCoreV2Information() {
        setAboutContent()

        composeRule.onNodeWithTag("about_app_icon").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_description)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_local_device)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_no_account_required)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_version, "9.9", 99)).assertIsDisplayed()
        composeRule.onNodeWithTag("about_send_feedback")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun feedbackClick_invokesFeedbackAction() {
        var launchCount = 0
        setAboutContent(
            feedbackIntentLauncher = {
                launchCount++
                true
            }
        )

        composeRule.onNodeWithTag("about_send_feedback").performClick()
        composeRule.waitForIdle()

        assertEquals(1, launchCount)
    }

    @Test
    fun feedbackClick_whenNoEmailApp_showsFallbackMessage() {
        setAboutContent(feedbackIntentLauncher = { false })

        composeRule.onNodeWithTag("about_send_feedback").performClick()

        composeRule.onNodeWithText(context.getString(R.string.about_feedback_no_email_app))
            .assertIsDisplayed()
    }

    private fun setAboutContent(
        feedbackIntentLauncher: ((android.content.Intent) -> Boolean)? = null
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
                    AboutScreenContent(
                        versionName = "9.9",
                        versionCode = 99,
                        onBack = {},
                        feedbackIntentLauncher = feedbackIntentLauncher
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
}
