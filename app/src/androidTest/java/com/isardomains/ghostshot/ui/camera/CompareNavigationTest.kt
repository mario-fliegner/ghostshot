package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.isardomains.ghostshot.ui.compare.CompareScreen
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompareNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        scenario?.close()
        scenario = null
    }

    @Test
    fun activatingCompareEntryNavigatesToCompareScreen() {
        setNavigationContent()

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
    }

    @Test
    fun explicitBackReturnsToCameraAndKeepsSessionContext() {
        setNavigationContent()

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
        composeRule.onNodeWithTag("reference_action_active_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsToCameraAndKeepsSessionContext() {
        setNavigationContent()

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()
        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
        composeRule.onNodeWithTag("reference_action_active_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun captureSuccessSnackbarDoesNotReplayAfterCompareReturn() {
        composeRule.mainClock.autoAdvance = false
        setNavigationContent(showCaptureSuccessSnackbar = true)
        val savedText = context.getString(R.string.capture_saved)
        val compareText = context.getString(R.string.capture_saved_compare_action)

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(savedText).assertIsDisplayed()
        composeRule.onNodeWithText(compareText).performClick()
        composeRule.waitForIdle()

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("camera-session")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
        composeRule.onAllNodesWithText(savedText).assertCountEquals(0)
    }

    private fun setNavigationContent(showCaptureSuccessSnackbar: Boolean = false) {
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
                    val referenceUri = remember { Uri.parse("content://ghostshot/reference") }
                    val captureUri = remember { Uri.parse("content://ghostshot/capture") }
                    NavHost(
                        navController = navController,
                        startDestination = "camera"
                    ) {
                        composable("camera") {
                            CameraNavigationTestContent(
                                showCaptureSuccessSnackbar = showCaptureSuccessSnackbar,
                                onCompare = { navController.navigate("compare") }
                            )
                        }
                        composable("compare") {
                            CompareScreen(
                                referenceImageUri = referenceUri,
                                captureImageUri = captureUri,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun CameraNavigationTestContent(
        showCaptureSuccessSnackbar: Boolean,
        onCompare: () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "camera-session",
                modifier = Modifier.testTag("camera_session_marker")
            )
            CameraControlsOverlay(
                referenceUri = Uri.parse("content://ghostshot/reference"),
                alpha = 0.5f,
                onAlphaChange = {},
                onSelectReferenceImage = {},
                onResetOverlay = {},
                onCapture = {},
                isLandscape = false,
                modifier = Modifier.fillMaxSize()
            )
            CompareImagesEntry(
                isEnabled = true,
                onClick = onCompare,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            if (showCaptureSuccessSnackbar) {
                val snackbarHostState = remember { SnackbarHostState() }
                CaptureSuccessSnackbarEffect(
                    captureSuccessGeneration = 1L,
                    captureSuccessHadReference = true,
                    hostState = snackbarHostState,
                    message = context.getString(R.string.capture_saved),
                    actionLabel = context.getString(R.string.capture_saved_compare_action),
                    onCompare = onCompare
                )
                CameraSnackbarHost(
                    hostState = snackbarHostState,
                    isLandscape = false,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
