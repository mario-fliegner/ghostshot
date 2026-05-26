package com.isardomains.sameview.ui.camera

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
import com.isardomains.sameview.ui.theme.SameViewTheme
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
    fun compareEntry_withReferenceAndCapture_isEnabledAndInvokesCallback() {
        var compareClickCount = 0
        setEntryContent(
            enabled = true,
            onCompareImages = { compareClickCount++ }
        )

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.waitForIdle()
        assertEquals(1, compareClickCount)
    }

    @Test
    fun compareEntry_alwaysPresent_whenDisabled() {
        setEntryContent(enabled = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun setEntryContent(
        enabled: Boolean = true,
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
                SameViewTheme {
                    CompareImagesEntry(
                        label = label,
                        onClick = onCompareImages,
                        enabled = enabled,
                        modifier = Modifier
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

    @Test
    fun compareEntry_showsCompareLabel_forCurrentCompare() {
        setEntryContent(enabled = true, label = "Compare")

        composeRule.onNodeWithTag("compare_images_entry").assertIsDisplayed()
        composeRule.onNodeWithText("Compare").assertIsDisplayed()
    }

    private val fakeCompareInput = CompareInput(
        referenceImageUri = Uri.parse("file:///fake/reference.jpg"),
        captureImageUri = Uri.parse("file:///fake/capture.jpg")
    )

    private fun setOverlayContent(
        compareInput: CompareInput? = null,
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
                SameViewTheme {
                    CameraControlsOverlay(
                        referenceUri = null,
                        compareInput = compareInput,
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
    fun compareEntry_alwaysVisible_whenNoCompareInput() {
        setOverlayContent(compareInput = null)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compareEntry_visible_whenCompareInputExists() {
        setOverlayContent(compareInput = fakeCompareInput)

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

    @Test
    fun compareEntry_clickInvokesCallback_whenDisabled() {
        var callbackCount = 0
        setOverlayContent(compareInput = null, onCompareClick = { callbackCount++ })

        composeRule.onNodeWithTag("compare_images_entry").performClick()
        composeRule.waitForIdle()

        assertEquals(1, callbackCount)
    }
}
