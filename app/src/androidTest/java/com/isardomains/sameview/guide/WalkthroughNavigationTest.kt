package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalkthroughNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun replayFromGuideOpensWalkthroughAndSkipReturnsToGuide() {
        setNavigationContent()

        composeRule.onNodeWithTag("guide_topic_grid").performScrollToIndex(GuideTopicRegistry.topics.size)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_show_walkthrough_again").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("walkthrough_screen_root").assertIsDisplayed()

        composeRule.onNodeWithTag("walkthrough_skip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guide_screen_root").assertIsDisplayed()
    }

    @Test
    fun firstRunStartReturnsToCameraRoute() {
        setNavigationContent(startDestination = walkthroughRoute(WalkthroughEntryMode.FIRST_RUN))

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("walkthrough_start").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_route_stub").assertIsDisplayed()
    }

    private fun setNavigationContent(
        startDestination: String = ROUTE_GUIDE
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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("camera") {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxSize()
                                    .testTag("camera_route_stub")
                            )
                        }
                        composable(ROUTE_GUIDE) {
                            var showResetDialog by remember { mutableStateOf(false) }
                            GuideScreen(
                                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                                showResetTipsConfirmation = showResetDialog,
                                onBack = {},
                                onOpenTopic = { topicId -> navController.navigate(guideDetailRoute(topicId)) },
                                onShowTipsAgain = { showResetDialog = true },
                                onDismissResetTips = { showResetDialog = false },
                                onConfirmResetTips = { showResetDialog = false },
                                onShowWalkthroughAgain = {
                                    navController.navigate(walkthroughRoute(WalkthroughEntryMode.REPLAY))
                                }
                            )
                        }
                        composable(
                            route = ROUTE_GUIDE_DETAIL_WITH_ARGS,
                            arguments = listOf(navArgument(ARG_GUIDE_TOPIC_ID) { type = NavType.StringType })
                        ) { backStackEntry ->
                            val topicId = backStackEntry.guideTopicIdArgument() ?: return@composable
                            GuideDetailScreen(
                                topicId = topicId,
                                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = ROUTE_WALKTHROUGH_WITH_ARGS,
                            arguments = listOf(navArgument(ARG_WALKTHROUGH_ENTRY_MODE) { type = NavType.StringType })
                        ) { backStackEntry ->
                            val entryMode = backStackEntry.walkthroughEntryModeArgument() ?: return@composable
                            WalkthroughScreen(
                                entryMode = entryMode,
                                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                                onSkip = {
                                    if (entryMode == WalkthroughEntryMode.REPLAY) {
                                        navController.popBackStack(ROUTE_GUIDE, inclusive = false)
                                    } else {
                                        navController.navigate("camera") {
                                            popUpTo(ROUTE_WALKTHROUGH_WITH_ARGS) { inclusive = true }
                                        }
                                    }
                                },
                                onStart = {
                                    navController.navigate("camera") {
                                        popUpTo(ROUTE_WALKTHROUGH_WITH_ARGS) { inclusive = true }
                                    }
                                }
                            )
                        }
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

