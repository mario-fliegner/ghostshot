package com.isardomains.ghostshot.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
class CameraTopRightNavigationTest {

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
    fun historyButton_isAlwaysVisible() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_history_button")
            .assertIsDisplayed()
    }

    @Test
    fun overflowButton_isAlwaysVisible() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button")
            .assertIsDisplayed()
    }

    @Test
    fun historyButton_invokesCallback() {
        var historyOpenCount = 0
        setTopRightContent(onOpenHistory = { historyOpenCount++ })

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, historyOpenCount)
    }

    @Test
    fun overflowButton_opensMenu() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_overflow_menu").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_containsSettings() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_containsAbout() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_about))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_settingsTap_closesMenu() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_overflow_button").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_history_button").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_aboutTap_invokesAboutCallback() {
        var aboutOpenCount = 0
        setTopRightContent(onOpenAbout = { aboutOpenCount++ })

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_about))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, aboutOpenCount)
    }

    @Test
    fun historyButton_navigatesViaNavController() {
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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "camera") {
                        composable("camera") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CameraTopRightActions(
                                    onOpenHistory = { navController.navigate("history") }
                                )
                            }
                        }
                        composable("history") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("history_screen_stub")
                            ) {}
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history_screen_stub").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_settingsTap_invokesSettingsCallback() {
        var settingsOpenCount = 0
        setTopRightContent(onOpenSettings = { settingsOpenCount++ })

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, settingsOpenCount)
    }

    private fun setTopRightContent(
        onOpenHistory: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenAbout: () -> Unit = {}
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraTopRightActions(
                            onOpenHistory = onOpenHistory,
                            onOpenSettings = onOpenSettings,
                            onOpenAbout = onOpenAbout
                        )
                    }
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
