package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraControlsOverlayTest {

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
    fun controls_withoutReference_showPrimaryActionsOnly() {
        setControlsContent(referenceUri = null, isLandscape = false)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(resetDescription()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(opacityDescription()).assertCountEquals(0)
    }

    @Test
    fun controls_withReferenceInPortrait_showResetAndOpacity() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = false)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(resetDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
    }

    @Test
    fun controls_withReferenceInLandscape_showSameStableControls() {
        setControlsContent(referenceUri = Uri.parse("content://ghostshot/test-reference"), isLandscape = true)

        composeRule.onNodeWithContentDescription(referenceDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(captureDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(resetDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(opacityDescription()).assertIsDisplayed()
    }

    private fun setControlsContent(referenceUri: Uri?, isLandscape: Boolean) {
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
                        referenceUri = referenceUri,
                        alpha = 0.5f,
                        onAlphaChange = {},
                        onSelectReferenceImage = {},
                        onResetOverlay = {},
                        onCapture = {},
                        isLandscape = isLandscape,
                        modifier = Modifier.fillMaxSize()
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

    private fun referenceDescription() = context.getString(R.string.select_reference_image)

    private fun captureDescription() = context.getString(R.string.capture_button_content_description)

    private fun resetDescription() = context.getString(R.string.reset_overlay_label)

    private fun opacityDescription() = context.getString(R.string.overlay_opacity_label)
}
