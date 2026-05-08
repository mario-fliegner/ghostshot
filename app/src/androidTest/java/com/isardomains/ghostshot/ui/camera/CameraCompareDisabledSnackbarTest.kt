package com.isardomains.ghostshot.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that Compare-disabled hint snackbars auto-dismiss after ~2000 ms
 * and that unrelated snackbar events are unaffected.
 *
 * Uses the same launch+delay+dismiss pattern as CaptureSuccessSnackbarEffect.
 */
@RunWith(AndroidJUnit4::class)
class CameraCompareDisabledSnackbarTest {

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
    fun compareDisabled_snackbar_isVisibleImmediatelyAfterTrigger() {
        val message = context.getString(R.string.compare_disabled_no_reference)
        composeRule.mainClock.autoAdvance = false
        setTimedSnackbarContent(message = message, durationMs = 2000L)

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun compareDisabled_snackbar_dismissesAfter2000ms() {
        val message = context.getString(R.string.compare_disabled_no_capture)
        composeRule.mainClock.autoAdvance = false
        setTimedSnackbarContent(message = message, durationMs = 2000L)

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(message).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(2_200)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(message).assertCountEquals(0)
    }

    @Test
    fun defaultSnackbar_withNullDuration_remainsVisibleAfter2000ms() {
        val message = context.getString(R.string.capture_failed)
        composeRule.mainClock.autoAdvance = false
        setTimedSnackbarContent(message = message, durationMs = null)

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(message).assertIsDisplayed()

        // SnackbarDuration.Short is ~4 s; still visible at 2 s
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    /**
     * Renders a bare SnackbarHost driven by the same launch+delay+dismiss pattern
     * used in CameraScreen for durationMs-controlled snackbars.
     *
     * [durationMs] null → SnackbarDuration.Short (default, ~4 s)
     * [durationMs] non-null → Indefinite + dismiss after [durationMs] ms
     */
    private fun setTimedSnackbarContent(message: String, durationMs: Long?) {
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
                        val snackbarHostState = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) {
                            if (durationMs != null) {
                                launch {
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = SnackbarDuration.Indefinite
                                    )
                                }
                                delay(durationMs)
                                snackbarHostState.currentSnackbarData?.dismiss()
                            } else {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                        CameraSnackbarHost(
                            hostState = snackbarHostState,
                            isLandscape = false,
                            hasOverlay = false,
                            modifier = Modifier.align(Alignment.BottomCenter)
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
