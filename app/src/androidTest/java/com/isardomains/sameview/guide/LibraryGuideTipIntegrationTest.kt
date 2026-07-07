package com.isardomains.sameview.guide

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.ScannedSession
import com.isardomains.sameview.ui.compare.CompareLibraryScreen
import com.isardomains.sameview.ui.settings.LibraryFilter
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
class LibraryGuideTipIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var dataStoreFile: File? = null
    private var dataStoreScope: CoroutineScope? = null

    private val fakeSessionId = "2024-01-15_10-30-00"
    private val fakeTimestamp = 1705312200000L
    private val fakeReferenceUri = Uri.parse("file:///fake/reference.jpg")
    private val fakeCaptureUri = Uri.parse("file:///fake/capture.jpg")

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        dataStoreScope?.cancel()
        dataStoreScope = null
        dataStoreFile?.delete()
        dataStoreFile = null
    }

    // ── Open Comparison tip ───────────────────────────────────────────────────

    @Test
    fun openComparisonTip_showsWhenSessionExists() {
        // autoAdvance = true: waitForIdle() advances the Compose test clock so LaunchedEffect
        // delays and AnimatedVisibility animations are processed.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession())
        )

        // Real-time sleep lets the 600ms screen-entry delay fire.
        Thread.sleep(900)
        composeRule.waitForIdle()
        // Second pass: DataStore IO + composition settle.
        Thread.sleep(300)
        composeRule.waitForIdle()

        // OPEN_COMPARISON renders as an inline card above the grid, not via GuideTipHost.
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitUntil(timeoutMillis = 3000) {
            runCatching {
                val b = composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNode().boundsInRoot
                b.width > 0f && b.height > 0f
            }.getOrDefault(false)
        }
    }

    @Test
    fun openComparisonTip_hiddenInMultiSelectMode() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession())
        )

        // Enter multi-select mode before the 600ms delay fires.
        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        // Multi-select is a blocker — tip must not appear.
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()
    }

    @Test
    fun openComparisonTip_notShownWhenNoSessions() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = emptyList()
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        // Empty session list → eligibility empty → no tip.
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()
    }

    @Test
    fun openComparisonTip_notShownOnEmptyFavoritesFilter() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        // sessions list has one non-favorited session; Favorites filter shows nothing.
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession(isFavorite = false)),
            libraryFilter = LibraryFilter.FAVORITES
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        // displayedSessions is empty → eligibility empty → no tip.
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()
    }

    @Test
    fun openComparisonTip_tileTapDoesNotMarkSeen() {
        // Product decision: OPEN_COMPARISON completes via Dismiss only (GUIDE_TIPS_UX_V1.md
        // §6.2/§7.4/§15.3). Tapping a comparison tile navigates as normal but must not mark
        // the tip seen.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession())
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId").performClick()
        Thread.sleep(300)
        composeRule.waitForIdle()

        assertFalse(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })
    }

    @Test
    fun openComparisonTip_reappearsAfterTileTapWithoutDismiss() {
        // Tile tap without Dismiss must leave OPEN_COMPARISON eligible: leaving the screen
        // clears the active tip via the existing dispose cleanup
        // (clearActiveTipWithoutMarkingSeen()), not via completion, so it appears again on
        // the next Library visit.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val screenVisible = mutableStateOf(true)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession()),
            screenVisible = screenVisible
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // Tile tap (no Dismiss) — navigates away without completing.
        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId").performClick()
        composeRule.waitForIdle()

        // Leave the screen (simulating navigation to CompareScreen).
        screenVisible.value = false
        composeRule.waitForIdle()
        assertFalse(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })

        // Re-open the Library.
        screenVisible.value = true
        composeRule.waitForIdle()
        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
    }

    @Test
    fun openComparisonTip_dismissMarksSeenAndPreventsReappearance() {
        // Dismiss is OPEN_COMPARISON's only completion path: it must mark the tip seen and
        // permanently prevent reappearance on subsequent Library visits.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val screenVisible = mutableStateOf(true)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession()),
            screenVisible = screenVisible
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_dismiss").performClick()
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        assertTrue(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()

        // Leave and re-open the Library — must not reappear.
        screenVisible.value = false
        composeRule.waitForIdle()
        screenVisible.value = true
        composeRule.waitForIdle()
        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()
    }

    @Test
    fun openComparisonTipVisible_screenDisposed_clearsControllerWithoutMarkingSeen() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val screenVisible = mutableStateOf(true)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession()),
            screenVisible = screenVisible
        )

        // Real-time sleep lets the 600ms screen-entry delay fire (same pattern as
        // openComparisonTip_showsWhenSessionExists above).
        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)

        // Simulate navigating away: CompareLibraryScreen leaves composition without
        // completing or dismissing the tip. The existing DisposableEffect(Unit) in
        // CompareLibraryScreen.kt must clear the controller's active tip without
        // marking it seen.
        screenVisible.value = false
        composeRule.waitForIdle()

        assertNull(controller.activeTipId.value)
        assertFalse(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })
    }

    @Test
    fun openComparisonTip_survivesLateForeignClearFromAnotherScreen() {
        // Regression guard for the cross-screen singleton race: Compose Navigation may dispose
        // an outgoing screen (e.g. CameraScreen) well after CompareLibraryScreen has already
        // mounted and made OPEN_COMPARISON active. That screen's own dispose cleanup — now
        // scoped via clearActiveTipWithoutMarkingSeen(expectedTipId) — must not wipe out
        // OPEN_COMPARISON when it targets a different tip id.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession())
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)

        // Simulate CameraScreen's late dispose firing after Library already made its own tip
        // active — it only ever targets its own tip (REFERENCE), never Library's.
        controller.clearActiveTipWithoutMarkingSeen(GuideTipId.REFERENCE)
        composeRule.waitForIdle()

        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertIsDisplayed()
    }

    @Test
    fun openComparisonTip_reappearsAfterShowTipsAgain_evenIfAnotherTipWasDismissedFirst() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)

        // Simulate a completely unrelated tip already dismissed earlier in the same app
        // session (e.g. REFERENCE in CameraScreen). This sets waitingForUserActionAfterDismissal
        // on the controller, which OPEN_COMPARISON must not remain blocked by forever.
        runBlocking {
            controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.CAMERA,
                    eligibleTipIds = setOf(GuideTipId.REFERENCE)
                )
            )
            controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)
        }

        val screenVisible = mutableStateOf(true)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession()),
            screenVisible = screenVisible
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        // Bug reproduction: without a reset, OPEN_COMPARISON stays blocked by the leftover
        // anti-spam flag from the unrelated REFERENCE dismissal above.
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertDoesNotExist()

        // "Show tips again": clears persisted seen ids AND the controller's in-memory state.
        runBlocking { repository.resetContextualTips() }
        controller.resetInMemoryState()

        // Re-open the Library (dispose + remount), matching a real navigate-away-and-back.
        screenVisible.value = false
        composeRule.waitForIdle()
        screenVisible.value = true
        composeRule.waitForIdle()

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun openComparisonTip_survivesInstantaneousTouchOnGrid_notClearedByBriefPulse() {
        // Regression guard: a smoke test reported OPEN_COMPARISON appearing then disappearing
        // after ~200ms on a real device. Root cause (confirmed via logcat): any scroll delta,
        // however brief/incidental, immediately set isScrollInProgress=true and instantly
        // cleared the tip. This test drives a single instantaneous touch (down+move+up
        // delivered back-to-back with no real wall-clock time in between) on the grid,
        // simulating an incidental touch settling right as the tip is visible — it must not
        // be cleared by such a brief pulse.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val manySessions = (1..30).map { i ->
            createFakeSession(id = "session-$i", timestamp = fakeTimestamp + i)
        }
        setLibraryContent(controller = controller, sessions = manySessions)

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)

        composeRule.onNodeWithTag("compare_library_grid").performTouchInput {
            down(Offset(centerX, centerY))
            moveBy(Offset(0f, -20f))
            up()
        }
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_card").assertIsDisplayed()
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
    }

    @Test
    fun openComparisonTip_recoversAfterGenuineScrollSettles_notPermanentlyRemoved() {
        // A real, sustained scroll must still correctly block the tip while scrolling is in
        // progress — that part of §8.3 is unchanged. Once the scroll fully settles, the block
        // must be temporary: after the normal re-entry delay the tip must reappear rather than
        // staying gone for the rest of the session.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val manySessions = (1..30).map { i ->
            createFakeSession(id = "session-$i", timestamp = fakeTimestamp + i)
        }
        setLibraryContent(controller = controller, sessions = manySessions)

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // A single continuous swipe (interpolated move events with no gap in between,
        // unlike a paused multi-step gesture) so isScrollInProgress stays true throughout,
        // well past the 150ms debounce, instead of flickering between discrete move() calls.
        composeRule.onNodeWithTag("compare_library_grid").performTouchInput {
            swipe(
                start = Offset(centerX, centerY),
                end = Offset(centerX, centerY - 400f),
                durationMillis = 400
            )
        }

        // Gone while genuinely scrolling (checked immediately after the swipe; the fling
        // settle following a swipe keeps isScrollInProgress true briefly afterward too).
        assertNull(controller.activeTipId.value)

        // Not permanently gone: reappears once settled + the normal re-entry delay.
        Thread.sleep(1500)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
    }

    @Test
    fun openComparisonTip_reappearsAfterShowTipsAgain_followingDismissInSameSession() {
        // Regression guard: root cause was a combined delay(600)+evaluate() LaunchedEffect
        // that got cancelled and restarted by transient eligibility churn (e.g. the
        // openComparisonTipCompleted collectAsState briefly reporting its initial false on
        // every re-mount), so evaluate() could go unreached indefinitely. Reproduces: Dismiss
        // completes OPEN_COMPARISON (the only completion path) -> leave Library -> "Show
        // tips again" (repository reset + controller in-memory reset) -> re-open Library ->
        // OPEN_COMPARISON must become eligible and appear again.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val screenVisible = mutableStateOf(true)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession()),
            screenVisible = screenVisible
        )

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // Dismiss completes OPEN_COMPARISON in this same session.
        composeRule.onNodeWithTag("guide_tip_open_comparison_inline_dismiss").performClick()
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        assertTrue(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })

        // Leave Library, then "Show tips again" (Guide screen action).
        screenVisible.value = false
        composeRule.waitForIdle()
        runBlocking { repository.resetContextualTips() }
        controller.resetInMemoryState()

        // Re-open Library after the reset.
        screenVisible.value = true
        composeRule.waitForIdle()
        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
    }

    @Test
    fun openComparisonTip_notPermanentlyBlockedByTransientEligibilityChurn() {
        // Rapid, repeated blocked-state churn (selection mode on/off) right after mount,
        // simulating transient recomposition churn during the entry-delay window. Before the
        // fix, each such churn cancelled the combined delay+evaluate effect and restarted its
        // 600ms delay from zero — if churn kept recurring, evaluate() could go unreached
        // indefinitely. The decoupled entry-delay effect must be immune to this: it keeps
        // ticking regardless of how often libraryTipBlocked flips during the window.
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        val controller = GuideTipController(repository)
        setLibraryContent(controller = controller, sessions = listOf(createFakeSession()))

        repeat(5) {
            composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
                .performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("compare_library_cancel_button").performClick()
            composeRule.waitForIdle()
        }

        Thread.sleep(900)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeRule.onAllNodesWithTag("guide_tip_open_comparison_inline_card")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)
    }

    // ── Long-press / multi-select (no guide tip involved) ──────────────────────

    @Test
    fun longPress_activatesSelectionMode_regardlessOfGuideTipState() {
        composeRule.mainClock.autoAdvance = true
        val repository = createRepository()
        // MULTI_SELECT was removed as a guide tip; long-press must still enter selection
        // mode on its own, with no tip completion involved and no prerequisite tip state.
        val controller = GuideTipController(repository)
        setLibraryContent(
            controller = controller,
            sessions = listOf(createFakeSession())
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setLibraryContent(
        controller: GuideTipController,
        sessions: List<ScannedSession>,
        libraryFilter: LibraryFilter = LibraryFilter.ALL,
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
                        CompareLibraryScreen(
                            sessions = sessions,
                            onRefresh = {},
                            onSessionClick = {},
                            onBack = {},
                            libraryFilter = libraryFilter,
                            windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            guideTipController = controller
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createRepository(): GuideRepository {
        val file = File(context.cacheDir, "library-guide-tip-${UUID.randomUUID()}.preferences_pb")
        dataStoreFile = file
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStoreScope = scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return GuideRepository(dataStore)
    }

    private fun createFakeSession(
        id: String = fakeSessionId,
        isFavorite: Boolean = false,
        timestamp: Long = fakeTimestamp
    ) = ScannedSession(
        sessionId = id,
        timestamp = timestamp,
        referenceFileUri = fakeReferenceUri,
        captureFileUri = fakeCaptureUri,
        title = null,
        locationDisplayName = null,
        locationCity = null,
        locationCountry = null,
        isFavorite = isFavorite
    )

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}
