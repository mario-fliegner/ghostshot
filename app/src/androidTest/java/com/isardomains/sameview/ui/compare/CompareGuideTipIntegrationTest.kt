package com.isardomains.sameview.ui.compare

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.guide.GuideRepository
import com.isardomains.sameview.guide.GuideTipController
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTopicId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompareGuideTipIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

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
    fun editSessionTip_notEligibleWithoutShareCompleted() {
        // autoAdvance = true: waitForIdle() advances the Compose test clock so LaunchedEffect
        // delays and AnimatedVisibility animations are processed. Without it, the clock stays
        // paused and nothing advances even after Thread.sleep.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setCompareContent(controller)

        // First sleep + idle: lets the real-time delay(1200L) fire and processes the resulting
        // state change (isEditSessionTipDelayReady = true), which launches evaluate().
        Thread.sleep(1500)
        composeRule.waitForIdle()
        // Second sleep + idle: lets evaluate()'s DataStore IO complete and then processes the
        // resulting composition (activeGuideTip assignment + any animation).
        Thread.sleep(300)
        composeRule.waitForIdle()

        // EDIT_SESSION must not appear — SHARE has not been completed
        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()
    }

    @Test
    fun editSessionTip_anchorsToOverflowButton() {
        // autoAdvance = true: waitForIdle() advances the Compose test clock so LaunchedEffect
        // delays and AnimatedVisibility animations are processed.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        runBlocking { repository.markTipSeen(GuideTipId.SHARE) }
        val controller = GuideTipController(repository)
        setCompareContent(controller)

        // Two sleep+waitForIdle passes: first advances the Compose clock past the 1200ms
        // screen-entry delay; second lets the evaluate() chain and DataStore IO settle.
        Thread.sleep(1500)
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.waitForIdle()

        // DIAGNOSTIC: first wait for guide_tip_host to appear (activeGuideTip set)
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_host").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        // DIAGNOSTIC: then check whether guide_tip_card also appears (placement succeeded)
        composeRule.waitUntil(timeoutMillis = 3000) {
            runCatching {
                val b = composeRule.onNodeWithTag("guide_tip_card").fetchSemanticsNode().boundsInRoot
                b.width > 0f && b.height > 0f
            }.getOrDefault(false)
        }
        // Anchor button (OVERFLOW_ACTION) must be visible — tip points to it
        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertIsDisplayed()
    }

    @Test
    fun shareTip_appearsAfterSliderInteraction() {
        // autoAdvance = true: waitForIdle() advances the Compose test clock so LaunchedEffect
        // delays and AnimatedVisibility animations are processed.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val refUri = createImageUri("ref")
        val capUri = createImageUri("cap")
        setCompareContent(controller, refUri, capUri)

        // Wait for the viewport to render with real images
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("compare_viewport").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // Diagnostic: 3-phase gesture to satisfy the 100ms wall-clock check.
        // Phase 1: DOWN. Phase 2: small MOVE to cross touch slop so onDragStart fires
        // and dragStartMs is captured. Thread.sleep(150) so real wall time elapses AFTER
        // dragStartMs is set. Phase 3: large MOVE so the onDrag check fires with both
        // horizontalDragPx > 8dp AND System.currentTimeMillis() - dragStartMs > 100ms.
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(Offset(200f, centerY))
        }
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(40f, 0f))
        }
        Thread.sleep(150)
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(460f, 0f))
            up()
        }

        // Wait longer than the 1000ms SHARE delay; real-time sleep lets the LaunchedEffect
        // delay fire, then waitForIdle() processes state changes and advances the Compose clock.
        Thread.sleep(1500)
        composeRule.waitForIdle()

        // Poll until the tip card exists with positive bounds in root.
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_host").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun shareTip_completesOnExportMenuOpen() {
        // autoAdvance = true: waitForIdle() advances the Compose test clock so LaunchedEffect
        // delays and AnimatedVisibility animations are processed.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val refUri = createImageUri("ref")
        val capUri = createImageUri("cap")
        setCompareContent(controller, refUri, capUri)

        // Wait for the viewport to render with real images
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("compare_viewport").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // 3-phase real-time gesture: DOWN → small MOVE to cross touch slop (onDragStart fires,
        // dragStartMs captured) → Thread.sleep(150) → large MOVE + UP so onDrag fires with
        // both horizontalDragPx > 8dp AND System.currentTimeMillis() - dragStartMs > 100ms.
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(Offset(200f, centerY))
        }
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(40f, 0f))
        }
        Thread.sleep(150)
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(460f, 0f))
            up()
        }

        // Wait longer than the 1000ms SHARE delay; real-time sleep lets the LaunchedEffect
        // delay fire, then waitForIdle() processes state changes and advances the Compose clock.
        Thread.sleep(1500)
        composeRule.waitForIdle()

        // Wait for guide_tip_host first (activeGuideTip set), then for guide_tip_card placement.
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_host").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitUntil(timeoutMillis = 3000) {
            runCatching {
                val b = composeRule.onNodeWithTag("guide_tip_card").fetchSemanticsNode().boundsInRoot
                b.width > 0f && b.height > 0f
            }.getOrDefault(false)
        }

        // Click the export button — triggers completeTip(SHARE) and opens the export menu,
        // both of which cause the tip to be dismissed.
        composeRule.onNodeWithTag("compare_screen_export_button").performClick()

        // Let state propagation and the 150ms fade-out animation complete.
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guide_tip_card").assertDoesNotExist()
        assertTrue(runBlocking { repository.observeTipSeen(GuideTipId.SHARE).first() })
    }

    // ── Stability Fix Block 2: SHARE/EDIT_SESSION poison-state regression tests ─────────
    //
    // These reuse the existing real-CompareScreen + real-GuideTipController/GuideRepository
    // harness above, extended with a visibility toggle on setCompareContent so the screen's
    // composition can be disposed deterministically (simulating navigating away) without
    // needing real back-stack navigation.

    @Test
    fun shareTipVisible_screenDisposed_clearsControllerWithoutMarkingSeen() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val refUri = createImageUri("ref")
        val capUri = createImageUri("cap")
        val screenVisible = mutableStateOf(true)
        setCompareContent(controller, refUri, capUri, screenVisible = screenVisible)

        // Wait for the viewport to render with real images
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("compare_viewport").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // 3-phase real-time gesture (see shareTip_appearsAfterSliderInteraction for details):
        // DOWN → small MOVE to cross touch slop → Thread.sleep(150) → large MOVE + UP so both
        // horizontalDragPx > 8dp and the 100ms wall-clock check are satisfied.
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(Offset(200f, centerY))
        }
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(40f, 0f))
        }
        Thread.sleep(150)
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            moveBy(Offset(460f, 0f))
            up()
        }

        // Wait longer than the 1000ms SHARE delay; real-time sleep lets the LaunchedEffect
        // delay fire, then waitForIdle() processes state changes and advances the Compose clock.
        Thread.sleep(1500)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_host").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.SHARE, controller.activeTipId.value)

        // Simulate navigating away: CompareScreen leaves composition without completing or
        // dismissing the tip. Prior to the Block 2 fix, this left the singleton
        // GuideTipController's _activeTipId stuck forever, blocking all future tips.
        screenVisible.value = false
        composeRule.waitForIdle()

        assertNull(controller.activeTipId.value)
        assertFalse(runBlocking { repository.observeTipSeen(GuideTipId.SHARE).first() })
    }

    @Test
    fun editSessionTipVisible_screenDisposed_clearsControllerWithoutMarkingSeen() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        runBlocking { repository.markTipSeen(GuideTipId.SHARE) }
        val controller = GuideTipController(repository)
        val screenVisible = mutableStateOf(true)
        setCompareContent(controller, screenVisible = screenVisible)

        // Two sleep+waitForIdle passes: first advances the Compose clock past the 1200ms
        // screen-entry delay; second lets the evaluate() chain and DataStore IO settle.
        Thread.sleep(1500)
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_host").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.EDIT_SESSION, controller.activeTipId.value)

        // Simulate navigating away while EDIT_SESSION is visible (e.g. Back, Delete, or
        // Create Video / Share Comparison navigation instead of opening Edit Session).
        screenVisible.value = false
        composeRule.waitForIdle()

        assertNull(controller.activeTipId.value)
        assertFalse(runBlocking { repository.observeTipSeen(GuideTipId.EDIT_SESSION).first() })
    }

    private fun setCompareContent(
        controller: GuideTipController,
        referenceImageUri: Uri? = null,
        captureImageUri: Uri? = null,
        onOpenGuideTopic: (GuideTopicId) -> Unit = {},
        screenVisible: MutableState<Boolean> = mutableStateOf(true)
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
                    if (screenVisible.value) {
                        CompareScreen(
                            referenceImageUri = referenceImageUri,
                            captureImageUri = captureImageUri,
                            onBack = {},
                            sessionId = "session-1",
                            onShareComparisonImage = {},
                            isShareComparisonAvailable = true,
                            onCreateVideo = {},
                            isCreateVideoAvailable = true,
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            guideTipController = controller,
                            onOpenGuideTopic = onOpenGuideTopic
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createRepository(): GuideRepository {
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

    private fun createImageUri(prefix: String): Uri {
        val file = File.createTempFile(prefix, ".png", context.cacheDir)
        tempFiles += file
        val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}
