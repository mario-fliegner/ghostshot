package com.isardomains.sameview.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.guide.GuideTipAnchor
import com.isardomains.sameview.guide.GuideTipAnchorKey
import com.isardomains.sameview.guide.GuideTipHost
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTipRegistry
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraGuideTipIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun referenceTip_anchorsToReferenceButton() {
        setCameraControlsContent(activeTipId = GuideTipId.REFERENCE)

        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
    }

    @Test
    fun referenceTip_notDisplayed_whenActiveTipIsNull() {
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        GuideTipHost(
                            activeTip = null,
                            anchors = emptyList(),
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            onGotIt = {},
                            onLearnMore = { _, _ -> },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()
    }

    @Test
    fun captureButtonBounds_reportedViaCallback() {
        var capturedBounds: Rect? = null
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraControlsOverlay(
                            referenceUri = null,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onCapture = {},
                            isLandscape = false,
                            onCaptureButtonBoundsChanged = { capturedBounds = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assert(capturedBounds != null) { "onCaptureButtonBoundsChanged never fired" }
        assert(capturedBounds!!.width > 0f) { "captureButtonBounds width was zero" }
        assert(capturedBounds!!.height > 0f) { "captureButtonBounds height was zero" }
    }

    @Test
    fun referenceTip_displayedWithExclusionZone() {
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
                    var anchors by remember { mutableStateOf<Map<GuideTipAnchorKey, GuideTipAnchor>>(emptyMap()) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraControlsOverlay(
                            referenceUri = null,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onCapture = {},
                            isLandscape = false,
                            onGuideTipAnchor = { anchor -> anchors = anchors + (anchor.key to anchor) },
                            modifier = Modifier.fillMaxSize()
                        )
                        GuideTipHost(
                            activeTip = GuideTipRegistry.tipFor(GuideTipId.REFERENCE),
                            anchors = anchors.values.toList(),
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            onGotIt = {},
                            onLearnMore = { _, _ -> },
                            exclusionZones = listOf(Rect(0f, 0f, 1f, 1f)),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
    }

    private fun setCameraControlsContent(
        activeTipId: GuideTipId,
        referenceUri: Uri? = null,
        compareInput: CompareInput? = null,
        gpsGuidanceState: GpsGuidanceState = GpsGuidanceState.Hidden
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
                    var anchors by remember { mutableStateOf<Map<GuideTipAnchorKey, GuideTipAnchor>>(emptyMap()) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraControlsOverlay(
                            referenceUri = referenceUri,
                            compareInput = compareInput,
                            alpha = 0.5f,
                            onAlphaChange = {},
                            onSelectReferenceImage = {},
                            onResetOverlay = {},
                            onCapture = {},
                            isLandscape = false,
                            gpsGuidanceState = gpsGuidanceState,
                            onGuideTipAnchor = { anchor -> anchors = anchors + (anchor.key to anchor) },
                            modifier = Modifier.fillMaxSize()
                        )
                        GuideTipHost(
                            activeTip = GuideTipRegistry.tipFor(activeTipId),
                            anchors = anchors.values.toList(),
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            onGotIt = {},
                            onLearnMore = { _, _ -> },
                            modifier = Modifier.fillMaxSize()
                        )
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

