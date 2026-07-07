package com.isardomains.sameview.ui.camera

import android.Manifest
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.MutableState
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.isardomains.sameview.guide.FirstRunWalkthroughGateState
import com.isardomains.sameview.guide.GuideRepository
import com.isardomains.sameview.guide.GuideTipAnchor
import com.isardomains.sameview.guide.GuideTipAnchorKey
import com.isardomains.sameview.guide.GuideTipController
import com.isardomains.sameview.guide.GuideTipDismissReason
import com.isardomains.sameview.guide.GuideTipEvaluationContext
import com.isardomains.sameview.guide.GuideTipHost
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTipRegistry
import com.isardomains.sameview.guide.GuideTipScope
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.ui.theme.SameViewTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraGuideTipIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val cameraPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var dataStoreFile: File? = null
    private var dataStoreScope: CoroutineScope? = null
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        dataStoreScope?.cancel()
        dataStoreScope = null
        dataStoreFile?.delete()
        dataStoreFile = null
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
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

    // ── Stability Fix Block 1: REFERENCE tip poison-state regression tests ──────────────
    //
    // These tests compose the real CameraScreen (not just CameraControlsOverlay/GuideTipHost
    // in isolation, as the tests above do) with a real GuideTipController + GuideRepository,
    // because the bug and its fix live in CameraScreen's guide-tip orchestration effects,
    // which the isolated-composable tests above never exercise.

    @Test
    fun referenceTipVisible_screenDisposed_clearsControllerWithoutMarkingSeen() {
        val harness = mountCameraScreenForGuideTipTest()
        waitForReferenceTipActive(harness.controller)
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()

        // Simulate navigating away: CameraScreen leaves composition without completing
        // or dismissing the tip. Prior to the Block 1 fix, this left the singleton
        // GuideTipController's _activeTipId stuck forever, blocking all future tips.
        harness.screenVisible.value = false
        composeRule.waitForIdle()

        assertNull(harness.controller.activeTipId.value)
        assertTrue(runBlocking { harness.repository.observeSeenTipIds().first() }.isEmpty())
    }

    @Test
    fun referenceTipVisible_transientBlockClears_notPoisonedAndReappearsUncompleted() {
        val harness = mountCameraScreenForGuideTipTest()
        waitForReferenceTipActive(harness.controller)
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()

        // Drives the same `cameraTipBlocked` gate in CameraScreen.kt that
        // isReferencePickerActive also feeds (isCaptureInProgress is one of the same
        // OR-ed flags), via a fully in-process, deterministic trigger. Actually clicking
        // the Reference button launches the real system photo picker, which on a real
        // device takes over the foreground (observed: it surfaced an unrelated app) and
        // is unsafe/unreliable to drive from an automated test here — this exercises the
        // identical block-then-recover code path (CameraScreen.kt:455-488) without it.
        val captureToken = harness.viewModel.tryStartCapture()
        assertTrue(captureToken != null)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()

        // Block lifts — isCaptureInProgress resets to false, REFERENCE becomes eligible again.
        harness.viewModel.onCaptureInterrupted()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            harness.controller.activeTipId.value == GuideTipId.REFERENCE
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
        assertTrue(runBlocking { harness.repository.observeSeenTipIds().first() }.isEmpty())
    }

    @Test
    fun referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear() {
        val harness = mountCameraScreenForGuideTipTest()
        waitForReferenceTipActive(harness.controller)
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()

        val referenceUri = createReferenceImageUri()
        harness.viewModel.onReferenceImageSelected(referenceUri)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            harness.controller.activeTipId.value == null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()

        val seenTipIds = runBlocking { harness.repository.observeSeenTipIds().first() }
        assertTrue(GuideTipId.REFERENCE in seenTipIds)
    }

    // ── History/Comparisons icon must release the anti-spam gate ────────────────────────
    //
    // Root cause: dismissing/completing any tip sets waitingForUserActionAfterDismissal on
    // the controller (a global, cross-scope flag). Every other navigation-triggering click in
    // this codebase (Compare button, Library tile taps, Compare viewport taps) already calls
    // onUserAction() to release it. The History/Comparisons icon did not, so OPEN_COMPARISON
    // (or any other tip) could stay blocked in Library even with empty seen_tip_ids and no
    // Library-side transient UI blocking it.

    @Test
    fun historyButtonClick_releasesAntiSpamGateAfterPriorTipDismissal() {
        val harness = mountCameraScreenForGuideTipTest()

        // Pre-mark REFERENCE as seen so CameraScreen's own (800ms-delayed) REFERENCE
        // eligibility effect can never select it and touch activeTipId in the background
        // while this test manipulates COMPARE/LIBRARY scope state directly below — keeps
        // the test deterministic regardless of exact timing.
        runBlocking { harness.repository.markTipSeen(GuideTipId.REFERENCE) }

        // Simulate an earlier tip dismissal elsewhere in the session (e.g. SHARE in
        // CompareScreen), which sets waitingForUserActionAfterDismissal on the controller.
        runBlocking {
            harness.controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.COMPARE,
                    eligibleTipIds = setOf(GuideTipId.SHARE)
                )
            )
            harness.controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)
        }

        // Confirm the gate is actually blocking a different, unrelated, still-unseen tip
        // before the click — otherwise this test would prove nothing.
        val blockedBeforeClick = runBlocking {
            harness.controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.LIBRARY,
                    eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
                )
            )
        }
        assertNull(blockedBeforeClick)

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()

        val tipAfterClick = runBlocking {
            harness.controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.LIBRARY,
                    eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
                )
            )
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, tipAfterClick?.id)
    }

    private class CameraGuideTipHarness(
        val controller: GuideTipController,
        val repository: GuideRepository,
        val viewModel: CameraViewModel,
        val screenVisible: MutableState<Boolean>
    )

    private fun mountCameraScreenForGuideTipTest(): CameraGuideTipHarness {
        wakeTestDevice()
        val repository = createGuideRepository()
        val controller = GuideTipController(repository)
        val settingsPrefs = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "camera_settings_${UUID.randomUUID()}.preferences_pb")
        }
        val viewModel = CameraViewModel(context, SettingsRepository(settingsPrefs))
        val screenVisible = mutableStateOf(true)

        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    if (screenVisible.value) {
                        CameraScreen(
                            viewModel = viewModel,
                            firstRunWalkthroughGateState = FirstRunWalkthroughGateState.Complete,
                            guideTipController = controller
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return CameraGuideTipHarness(controller, repository, viewModel, screenVisible)
    }

    /**
     * REFERENCE has an 800ms screen-entry delay (isCameraReady) before first evaluation, plus
     * real DataStore IO in evaluate(). A real-time sleep is required to let that wall-clock
     * delay fire (matches the established pattern in CompareGuideTipIntegrationTest), then
     * waitUntil polls the controller's actual state rather than a fixed additional sleep.
     */
    private fun waitForReferenceTipActive(controller: GuideTipController) {
        Thread.sleep(1_500)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            controller.activeTipId.value == GuideTipId.REFERENCE
        }
        composeRule.waitForIdle()
    }

    private fun createGuideRepository(): GuideRepository {
        val file = File(context.cacheDir, "guide-tip-${UUID.randomUUID()}.preferences_pb")
        dataStoreFile = file
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStoreScope = scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return GuideRepository(dataStore)
    }

    private fun createReferenceImageUri(): Uri {
        val file = File(context.cacheDir, "guide_tip_reference_${UUID.randomUUID()}.jpg")
        tempFiles += file
        InstrumentationRegistry.getInstrumentation().context.assets.open("portrait_tall.jpg").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(file)
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

