package com.isardomains.ghostshot.ui.compare

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class CompareScreenTest {

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
    fun compareScreen_rendersDistinctShellWithTitleAndBack() {
        setCompareContent(
            referenceImageUri = Uri.parse("content://ghostshot/reference"),
            captureImageUri = Uri.parse("content://ghostshot/capture")
        )

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_shell_content").assertIsDisplayed()
    }

    @Test
    fun compareScreen_missingInputsShowsFallback() {
        setCompareContent(referenceImageUri = null, captureImageUri = null)

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_missing_input_fallback").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_missing_images))
            .assertIsDisplayed()
    }

    @Test
    fun compareScreen_explicitBackInvokesCallback() {
        var backCount = 0
        setCompareContent(
            referenceImageUri = Uri.parse("content://ghostshot/reference"),
            captureImageUri = Uri.parse("content://ghostshot/capture"),
            onBack = { backCount++ }
        )

        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    private fun setCompareContent(
        referenceImageUri: Uri?,
        captureImageUri: Uri?,
        onBack: () -> Unit = {}
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
                    CompareScreen(
                        referenceImageUri = referenceImageUri,
                        captureImageUri = captureImageUri,
                        onBack = onBack
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
