package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCompareEntryTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun compareEntry_withoutReference_doesNotExist() {
        setEntryContent(isCompareEnabled = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertDoesNotExist()
    }

    @Test
    fun compareEntry_withoutCapture_doesNotExist() {
        setEntryContent(isCompareEnabled = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertDoesNotExist()
    }

    @Test
    fun compareEntry_withReferenceAndCapture_isEnabledAndInvokesCallback() {
        var compareClickCount = 0
        setEntryContent(
            isCompareEnabled = true,
            onCompareImages = { compareClickCount++ }
        )

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.waitForIdle()
        assertEquals(1, compareClickCount)
    }

    private fun setEntryContent(
        isCompareEnabled: Boolean,
        label: String = "Compare",
        onCompareImages: () -> Unit = {}
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
                    if (isCompareEnabled) {
                        CompareImagesEntry(
                            label = label,
                            onClick = onCompareImages,
                            modifier = Modifier
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

    @Test
    fun compareEntry_showsCompareLabel_forCurrentCompare() {
        setEntryContent(isCompareEnabled = true, label = "Compare")

        composeRule.onNodeWithTag("compare_images_entry").assertIsDisplayed()
        composeRule.onNodeWithText("Compare").assertIsDisplayed()
    }

    @Test
    fun compareEntry_showsComparisonsLabel_forLibrary() {
        setEntryContent(isCompareEnabled = true, label = "Comparisons")

        composeRule.onNodeWithTag("compare_images_entry").assertIsDisplayed()
        composeRule.onNodeWithText("Comparisons").assertIsDisplayed()
    }

    private val fakeCompareInput = CompareInput(
        referenceImageUri = Uri.parse("file:///fake/reference.jpg"),
        captureImageUri = Uri.parse("file:///fake/capture.jpg")
    )

    private fun setOverlayContent(
        compareInput: CompareInput? = null,
        hasSavedSessions: Boolean = false,
        onCompareClick: () -> Unit = {}
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
                        isLandscape = false
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun compareEntry_notVisible_whenNoCompareInputAndNoSavedSessions() {
        setOverlayContent(compareInput = null, hasSavedSessions = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertDoesNotExist()
    }

    @Test
    fun compareEntry_visible_whenCompareInputExists() {
        setOverlayContent(compareInput = fakeCompareInput, hasSavedSessions = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compareEntry_visible_whenOnlySavedSessionsExist() {
        setOverlayContent(compareInput = null, hasSavedSessions = true)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compareEntry_clickInvokesCallback() {
        var callbackCount = 0
        setOverlayContent(compareInput = fakeCompareInput, onCompareClick = { callbackCount++ })

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        assertEquals(1, callbackCount)
    }
}
