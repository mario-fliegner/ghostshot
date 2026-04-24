package com.isardomains.ghostshot.ui.compare

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.camera.ScannedSession
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation tests for the Compare Library screen.
 *
 * These tests use a minimal NavHost with stubs for all destinations to avoid Hilt
 * and keep the scope focused on backstack behavior. The real CompareLibraryScreen
 * composable is used; camera and compare destinations are lightweight stubs.
 *
 * Backstack under test: camera → compare_library → compare → back → compare_library → back → camera
 */
@RunWith(AndroidJUnit4::class)
class CompareLibraryNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    private val testSessionId = "nav_test_session"

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun navigatingToLibraryRoute_showsLibraryScreen() {
        setNavigationContent()

        composeRule.onNodeWithTag("open_library_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_screen").assertIsDisplayed()
    }

    @Test
    fun tappingSessionTile_navigatesToCompareScreen() {
        setNavigationContent()

        composeRule.onNodeWithTag("open_library_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("compare_library_session_tile_$testSessionId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
    }

    @Test
    fun backFromCompareScreen_returnsToLibrary() {
        setNavigationContent()

        composeRule.onNodeWithTag("open_library_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_session_tile_$testSessionId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_screen").assertIsDisplayed()
    }

    @Test
    fun backFromLibrary_returnsToCameraMarker() {
        setNavigationContent()

        composeRule.onNodeWithTag("open_library_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
    }

    @Test
    fun systemBackFromLibrary_returnsToCameraMarker() {
        setNavigationContent()

        composeRule.onNodeWithTag("open_library_button").performClick()
        composeRule.waitForIdle()
        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
    }

    private fun setNavigationContent() {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val session = createTestSession()
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                GhostShotTheme {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "camera"
                    ) {
                        composable("camera") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "camera-session",
                                    modifier = Modifier.testTag("camera_session_marker")
                                )
                                Button(
                                    onClick = { navController.navigate("compare_library") },
                                    modifier = Modifier.testTag("open_library_button")
                                ) {
                                    Text("Open Library")
                                }
                            }
                        }
                        composable("compare_library") {
                            CompareLibraryScreen(
                                sessions = listOf(session),
                                onRefresh = {},
                                onSessionClick = { navController.navigate("compare") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("compare") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("compare_screen_root")
                            ) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("compare_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createTestSession() = ScannedSession(
        sessionId = testSessionId,
        timestamp = 1705312200000L,
        referenceFileUri = Uri.parse("file:///fake/reference.jpg"),
        captureFileUri = Uri.parse("file:///fake/capture.jpg")
    )

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
