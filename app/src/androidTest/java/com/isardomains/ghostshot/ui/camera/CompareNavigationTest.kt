package com.isardomains.ghostshot.ui.camera

import android.graphics.Bitmap
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompareNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        scenario?.close()
        scenario = null
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
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
        composeRule.onNode(
            hasText(compareText) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        ).performClick()
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

    @Test
    fun backAfterSliderInteractionReturnsToCamera() {
        setNavigationContent()

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("compare_slider").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(150f, 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_session_marker").assertIsDisplayed()
        composeRule.onNodeWithTag("reference_action_active_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun setNavigationContent(showCaptureSuccessSnackbar: Boolean = false) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val compareInput = createCompareInput()
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
                            CameraNavigationTestContent(
                                showCaptureSuccessSnackbar = showCaptureSuccessSnackbar,
                                onCompare = { navController.navigate("compare") }
                            )
                        }
                        composable("compare") {
                            CompareScreen(
                                referenceImageUri = compareInput.referenceUri,
                                captureImageUri = compareInput.captureUri,
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
                label = context.getString(R.string.compare_entry_label),
                onClick = onCompare,
                modifier = Modifier.align(Alignment.BottomEnd)
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

    private fun createCompareInput(): TestNavigationInput {
        return TestNavigationInput(
            referenceUri = createImageUri("navigation_reference", android.graphics.Color.RED),
            captureUri = createImageUri("navigation_capture", android.graphics.Color.BLUE)
        )
    }

    private fun createImageUri(fileNamePrefix: String, color: Int): Uri {
        val file = File.createTempFile(fileNamePrefix, ".png", context.cacheDir)
        tempFiles += file
        val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }

    @Composable
    private fun CameraRoutingTestContent(
        compareInput: CompareInput? = null,
        hasSavedSessions: Boolean = false,
        onNavigateToCompare: () -> Unit = {},
        onNavigateToLibrary: () -> Unit = {}
    ) {
        val onCompareClick: () -> Unit = {
            if (compareInput != null) {
                onNavigateToCompare()
            } else if (hasSavedSessions) {
                onNavigateToLibrary()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "camera-session",
                modifier = Modifier.testTag("camera_session_marker")
            )
            CameraControlsOverlay(
                referenceUri = null,
                compareInput = compareInput,
                hasSavedSessions = hasSavedSessions,
                onCompareClick = onCompareClick,
                alpha = 0.5f,
                onAlphaChange = {},
                onSelectReferenceImage = {},
                onResetOverlay = {},
                onCapture = {},
                isLandscape = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun setRoutingNavigationContent(
        compareInput: CompareInput? = null,
        hasSavedSessions: Boolean = false
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
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "camera"
                    ) {
                        composable("camera") {
                            CameraRoutingTestContent(
                                compareInput = compareInput,
                                hasSavedSessions = hasSavedSessions,
                                onNavigateToCompare = { navController.navigate("compare") },
                                onNavigateToLibrary = { navController.navigate("library") }
                            )
                        }
                        composable("compare") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("compare_screen_stub")
                            ) {}
                        }
                        composable("library") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("compare_library_stub")
                            ) {}
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun clickWithCompareInput_opensCompareScreen() {
        setRoutingNavigationContent(
            compareInput = CompareInput(
                referenceImageUri = Uri.parse("file:///fake/reference.jpg"),
                captureImageUri = Uri.parse("file:///fake/capture.jpg")
            )
        )

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_stub").assertIsDisplayed()
    }

    @Test
    fun clickWithOnlySavedSessions_opensCompareLibrary() {
        setRoutingNavigationContent(compareInput = null, hasSavedSessions = true)

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_stub").assertIsDisplayed()
    }

    @Test
    fun clickWithCompareInputAndSavedSessions_opensCompareScreen() {
        setRoutingNavigationContent(
            compareInput = CompareInput(
                referenceImageUri = Uri.parse("file:///fake/reference.jpg"),
                captureImageUri = Uri.parse("file:///fake/capture.jpg")
            ),
            hasSavedSessions = true
        )

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_stub").assertIsDisplayed()
    }

    @Test
    fun initialSavedSessionsState_showsEntryWithoutCapture() {
        setRoutingNavigationContent(compareInput = null, hasSavedSessions = true)

        composeRule.onNodeWithTag("compare_images_entry").assertIsDisplayed()
    }

    private data class TestNavigationInput(
        val referenceUri: Uri,
        val captureUri: Uri
    )
}
