package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class FirstRunWalkthroughGateTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun loadingCompletionStateShowsNeutralSurfaceBeforeWalkthroughNavigation() {
        setGateHarness(initialCompletionState = WalkthroughCompletionState.Loading)

        composeRule.onNodeWithTag("first_run_gate_neutral_surface").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_screen_root").assertDoesNotExist()
    }

    @Test
    fun completedWalkthroughDoesNotReopenAfterRecreate() {
        setGateHarness(initialCompletionState = WalkthroughCompletionState.Loaded(true))

        waitUntilNodeExists("camera_route_stub")
        composeRule.onNodeWithTag("walkthrough_screen_root").assertDoesNotExist()

        scenario?.recreate()
        setGateHarnessContent(initialCompletionState = WalkthroughCompletionState.Loaded(true))

        waitUntilNodeExists("camera_route_stub")
        composeRule.onNodeWithTag("camera_route_stub").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_screen_root").assertDoesNotExist()
    }

    @Test
    fun permissionGrantedAndIncompleteCompletionOpensWalkthroughOnce() {
        var completionState by mutableStateOf<WalkthroughCompletionState>(WalkthroughCompletionState.Loading)
        setGateHarness(
            completionStateProvider = { completionState },
            onMarkComplete = { completionState = WalkthroughCompletionState.Loaded(true) }
        )

        composeRule.runOnIdle {
            completionState = WalkthroughCompletionState.Loaded(false)
        }
        waitUntilNodeExists("walkthrough_screen_root")

        composeRule.onNodeWithTag("walkthrough_skip").performClick()
        composeRule.waitForIdle()
        waitUntilNodeDoesNotExist("walkthrough_screen_root")
        composeRule.onNodeWithTag("camera_route_stub").assertIsDisplayed()
    }

    private fun setGateHarness(
        initialCompletionState: WalkthroughCompletionState = WalkthroughCompletionState.Loaded(false),
        completionStateProvider: (() -> WalkthroughCompletionState)? = null,
        onMarkComplete: () -> Unit = {}
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        setGateHarnessContent(initialCompletionState, completionStateProvider, onMarkComplete)
    }

    /**
     * Re-invokable content setter, split out from [setGateHarness] so recreate-survival tests can
     * call it again after [ActivityScenario.recreate] — a bare [ComponentActivity] does not
     * automatically re-run a previous [android.app.Activity.setContent] call on its new instance.
     */
    private fun setGateHarnessContent(
        initialCompletionState: WalkthroughCompletionState = WalkthroughCompletionState.Loaded(false),
        completionStateProvider: (() -> WalkthroughCompletionState)? = null,
        onMarkComplete: () -> Unit = {}
    ) {
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    val navController = rememberNavController()
                    val completionState = completionStateProvider?.invoke() ?: initialCompletionState
                    var cameraPermissionGranted by rememberSaveable { mutableStateOf(false) }
                    var firstRunNavigationRequested by rememberSaveable { mutableStateOf(false) }
                    val gateState = when (val state = completionState) {
                        WalkthroughCompletionState.Loading -> FirstRunWalkthroughGateState.Loading
                        is WalkthroughCompletionState.Loaded -> if (state.isCompleted) {
                            FirstRunWalkthroughGateState.Complete
                        } else {
                            FirstRunWalkthroughGateState.WaitingForWalkthrough
                        }
                    }
                    LaunchedEffect(cameraPermissionGranted, completionState) {
                        val loaded = completionState as? WalkthroughCompletionState.Loaded
                        if (cameraPermissionGranted && loaded?.isCompleted == false && !firstRunNavigationRequested) {
                            firstRunNavigationRequested = true
                            navController.navigate(walkthroughRoute(WalkthroughEntryMode.FIRST_RUN)) {
                                launchSingleTop = true
                            }
                        }
                    }
                    NavHost(navController = navController, startDestination = "camera") {
                        composable("camera") {
                            LaunchedEffect(Unit) {
                                cameraPermissionGranted = true
                            }
                            if (gateState == FirstRunWalkthroughGateState.Complete) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("camera_route_stub")
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("first_run_gate_neutral_surface")
                                )
                            }
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
                                    onMarkComplete()
                                    navController.popBackStack("camera", inclusive = false)
                                },
                                onStart = {
                                    onMarkComplete()
                                    navController.popBackStack("camera", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitUntilNodeExists(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilNodeDoesNotExist(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}
