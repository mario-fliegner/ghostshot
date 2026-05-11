package com.isardomains.ghostshot.ui.about

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun aboutContent_showsCoreV1Information() {
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
                    AboutScreenContent(
                        versionName = "9.9",
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("GhostShot").assertIsDisplayed()
        composeRule.onNodeWithText("Then & Now Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Recreate photos with a reference overlay.").assertIsDisplayed()
        composeRule.onNodeWithText("No tracking").assertIsDisplayed()
        composeRule.onNodeWithText("No cloud sync").assertIsDisplayed()
        composeRule.onNodeWithText("Photos stay on your device").assertIsDisplayed()
        composeRule.onNodeWithText("Version 9.9").assertIsDisplayed()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
