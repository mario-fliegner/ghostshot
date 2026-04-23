package com.isardomains.ghostshot.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    fun compareEntry_withoutReference_isVisibleButDisabled() {
        setEntryContent(isCompareEnabled = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun compareEntry_withoutCapture_isVisibleButDisabled() {
        setEntryContent(isCompareEnabled = false)

        composeRule.onNodeWithTag("compare_images_entry")
            .assertIsDisplayed()
            .assertIsNotEnabled()
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
            .assertIsEnabled()
            .performClick()

        composeRule.waitForIdle()
        assertEquals(1, compareClickCount)
    }

    private fun setEntryContent(
        isCompareEnabled: Boolean,
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
                    CompareImagesEntry(
                        isEnabled = isCompareEnabled,
                        onClick = onCompareImages,
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
}
