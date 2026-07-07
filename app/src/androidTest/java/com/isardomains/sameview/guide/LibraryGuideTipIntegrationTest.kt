package com.isardomains.sameview.guide

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
    fun openComparisonTip_completesOnTileTap() {
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

        // Tap the session tile — triggers completeTip(OPEN_COMPARISON).
        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId").performClick()
        Thread.sleep(300)
        composeRule.waitForIdle()

        assertTrue(runBlocking { repository.observeTipSeen(GuideTipId.OPEN_COMPARISON).first() })
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
