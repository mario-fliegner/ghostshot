package com.isardomains.sameview.ui.compare

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.camera.ScannedSession
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(AndroidJUnit4::class)
class CompareLibraryScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    private val fakeSessionId = "2024-01-15_10-30-00"
    private val fakeTimestamp = 1705312200000L
    private val fakeReferenceUri = Uri.parse("file:///fake/reference.jpg")
    private val fakeCaptureUri = Uri.parse("file:///fake/capture.jpg")

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun emptyState_isVisibleWhenSessionsIsEmpty() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_empty_title))
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_cardIsVisibleWhenSessionsIsEmpty() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_empty_card").assertIsDisplayed()
    }

    @Test
    fun emptyState_ctaIsVisibleWhenSessionsIsEmpty() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_empty_cta").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_empty_cta))
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_ctaInvokesOnBackCallback() {
        var backCount = 0
        setLibraryContent(sessions = emptyList(), onBack = { backCount++ })

        composeRule.onNodeWithTag("compare_library_empty_cta").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    @Test
    fun emptyState_bodyAndHintDoNotExistWhenSessionsIsEmpty() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithText(
            "Your saved comparisons will appear here after you capture a photo with a reference image."
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Photos and comparisons stay on this device.")
            .assertDoesNotExist()
        composeRule.onNodeWithText(
            "Ihre gespeicherten Vergleiche erscheinen hier, sobald Sie ein Foto mit Referenzbild aufnehmen."
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Fotos und Vergleiche bleiben auf diesem Gerät.")
            .assertDoesNotExist()
    }

    @Test
    fun emptyState_gridDoesNotExistWhenSessionsIsEmpty() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_grid").assertDoesNotExist()
    }

    @Test
    fun sessions_gridIsVisibleWhenSessionsNonEmpty() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_empty_state").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_library_empty_card").assertDoesNotExist()
    }

    @Test
    fun sessions_tileIsDisplayedWithCorrectTestTag() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .assertIsDisplayed()
    }

    @Test
    fun sessions_tileIsClickable() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .assertHasClickAction()
    }

    @Test
    fun sessions_tileClickInvokesOnSessionClickCallback() {
        var clickedSession: ScannedSession? = null
        val session = createFakeSession()
        setLibraryContent(
            sessions = listOf(session),
            onSessionClick = { clickedSession = it }
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId").performClick()
        composeRule.waitForIdle()

        assertEquals(session, clickedSession)
    }

    @Test
    fun sessions_referenceImageSlotExists() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag(
            "compare_library_reference_image_$fakeSessionId",
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun sessions_captureImageSlotExists() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag(
            "compare_library_capture_image_$fakeSessionId",
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun sessions_timestampIsDisplayedInTile() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun multipleSessions_allTilesAreDisplayed() {
        val session1 = createFakeSession(id = "session_a")
        val session2 = createFakeSession(id = "session_b")
        setLibraryContent(sessions = listOf(session1, session2))

        composeRule.onNodeWithTag("compare_library_session_tile_session_a").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_session_tile_session_b").assertIsDisplayed()
    }

    @Test
    fun screen_rootTestTagIsPresent() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_screen").assertIsDisplayed()
    }

    @Test
    fun screen_titleIsDisplayed() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithText(context.getString(R.string.compare_library_title))
            .assertIsDisplayed()
    }

    @Test
    fun screen_backButtonIsDisplayed() {
        setLibraryContent(sessions = emptyList())

        composeRule.onNodeWithTag("compare_library_back_button").assertIsDisplayed()
    }

    @Test
    fun screen_backButtonInvokesOnBackCallback() {
        var backCount = 0
        setLibraryContent(sessions = emptyList(), onBack = { backCount++ })

        composeRule.onNodeWithTag("compare_library_back_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    @Test
    fun selectAll_selectsAllSessions() {
        val sessions = listOf(
            createFakeSession(id = "s1"),
            createFakeSession(id = "s2"),
            createFakeSession(id = "s3")
        )
        setLibraryContent(sessions = sessions)

        composeRule.onNodeWithTag("compare_library_session_tile_s1")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_library_selection_count, 3))
            .assertIsDisplayed()
    }

    @Test
    fun deselectAll_clearsSelection_butStaysInSelectionMode() {
        val sessions = listOf(
            createFakeSession(id = "s1"),
            createFakeSession(id = "s2"),
            createFakeSession(id = "s3")
        )
        setLibraryContent(sessions = sessions)

        composeRule.onNodeWithTag("compare_library_session_tile_s1")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_selection_count, 0))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_delete_button").assertIsDisplayed()
    }

    @Test
    fun screen_refreshIsCalledOnLaunch() {
        var refreshCount = 0
        setLibraryContent(sessions = emptyList(), onRefresh = { refreshCount++ })

        composeRule.waitForIdle()

        assertEquals(1, refreshCount)
    }

    @Test
    fun longPress_activatesSelectionModeAndSelectsItem() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()
    }

    @Test
    fun tapInSelectionMode_deselectingLastItem_exitsSelectionMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_library_back_button").assertIsDisplayed()
    }

    @Test
    fun cancel_exitsSelectionModeWithoutDelete() {
        var deleteCallCount = 0
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            onDeleteSessions = { deleteCallCount++ }
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_library_back_button").assertIsDisplayed()
        assertEquals(0, deleteCallCount)
    }

    @Test
    fun deleteButton_opensConfirmDialog() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_library_delete_dialog_title))
            .assertIsDisplayed()
    }

    @Test
    fun confirmDelete_invokesOnDeleteSessionsWithCorrectIds() {
        var deletedIds: List<String>? = null
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            onDeleteSessions = { deletedIds = it }
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_library_delete_confirm))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(fakeSessionId), deletedIds)
    }

    @Test
    fun normalTap_afterExitingSelectionMode_stillInvokesOnSessionClick() {
        var clickedSession: ScannedSession? = null
        val session = createFakeSession()
        setLibraryContent(
            sessions = listOf(session),
            onSessionClick = { clickedSession = it }
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_cancel_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId").performClick()
        composeRule.waitForIdle()

        assertEquals(session, clickedSession)
    }

    private fun setLibraryContent(
        sessions: List<ScannedSession>,
        onRefresh: () -> Unit = {},
        onSessionClick: (ScannedSession) -> Unit = {},
        onBack: () -> Unit = {},
        onDeleteSessions: (List<String>) -> Unit = {},
        onBackupSessions: (List<String>, Uri) -> Unit = { _, _ -> },
        isBackupInProgress: Boolean = false,
        isDeletionInProgress: Boolean = false,
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
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
                    CompareLibraryScreen(
                        sessions = sessions,
                        onRefresh = onRefresh,
                        onSessionClick = onSessionClick,
                        onBack = onBack,
                        onDeleteSessions = onDeleteSessions,
                        onBackupSessions = onBackupSessions,
                        isBackupInProgress = isBackupInProgress,
                        isDeletionInProgress = isDeletionInProgress,
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun backupButton_isVisibleInMultiSelectMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_backup_button").assertIsDisplayed()
    }

    @Test
    fun backupButton_isEnabledWhenSessionSelectedAndNoProgressFlags() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isBackupInProgress = false,
            isDeletionInProgress = false
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_backup_button").assertIsEnabled()
    }

    @Test
    fun backupButton_isDisabledWhenIsBackupInProgressTrue() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isBackupInProgress = true
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_backup_button").assertIsNotEnabled()
    }

    @Test
    fun backupButton_isDisabledWhenIsDeletionInProgressTrue() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isDeletionInProgress = true
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_backup_button").assertIsNotEnabled()
    }

    @Test
    fun deleteButton_isDisabledWhenIsBackupInProgressTrue() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isBackupInProgress = true
        )

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_delete_button").assertIsNotEnabled()
    }

    @Test
    fun sessions_tileDisplaysTitleWhenPresent() {
        val session = createFakeSession(title = "My Test Title")
        setLibraryContent(sessions = listOf(session))

        composeRule.onNodeWithText("My Test Title").assertIsDisplayed()
    }

    @Test
    fun tile_withTitle_showsBothTitleAndTimestamp() {
        val session = createFakeSession(title = "Sunset Shot")
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText("Sunset Shot").assertIsDisplayed()
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun sessions_tileShowsTimestampWhenTitleIsNull() {
        val session = createFakeSession(title = null)
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun sessions_tileShowsNoPlaceholderWhenTitleIsNull() {
        val session = createFakeSession(title = null)
        setLibraryContent(sessions = listOf(session))

        composeRule.onNodeWithText("Untitled").assertDoesNotExist()
    }

    @Test
    fun sessions_tileDoesNotExposeLayoutAnchorText() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithText("X", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun grid_hasTwoColumns_whenWindowSizeCompact() {
        val sessions = (0..2).map { createFakeSession(id = "compact_col_$it") }
        setLibraryContent(sessions = sessions, windowWidthSizeClass = WindowWidthSizeClass.Compact)

        val top0 = composeRule.onNodeWithTag("compare_library_session_tile_compact_col_0")
            .fetchSemanticsNode().boundsInRoot.top
        val top1 = composeRule.onNodeWithTag("compare_library_session_tile_compact_col_1")
            .fetchSemanticsNode().boundsInRoot.top
        val top2 = composeRule.onNodeWithTag("compare_library_session_tile_compact_col_2")
            .fetchSemanticsNode().boundsInRoot.top

        // tiles 0 and 1 share row 1; tile 2 is in row 2
        assertTrue("Tiles 0 and 1 should be in the same row", kotlin.math.abs(top0 - top1) < 2f)
        assertTrue("Tile 2 should be in a different row than tile 0", kotlin.math.abs(top0 - top2) > 50f)
    }

    @Test
    fun grid_hasThreeColumns_whenWindowSizeMedium() {
        val sessions = (0..3).map { createFakeSession(id = "medium_col_$it") }
        setLibraryContent(sessions = sessions, windowWidthSizeClass = WindowWidthSizeClass.Medium)

        val top0 = composeRule.onNodeWithTag("compare_library_session_tile_medium_col_0")
            .fetchSemanticsNode().boundsInRoot.top
        val top1 = composeRule.onNodeWithTag("compare_library_session_tile_medium_col_1")
            .fetchSemanticsNode().boundsInRoot.top
        val top2 = composeRule.onNodeWithTag("compare_library_session_tile_medium_col_2")
            .fetchSemanticsNode().boundsInRoot.top
        val top3 = composeRule.onNodeWithTag("compare_library_session_tile_medium_col_3")
            .fetchSemanticsNode().boundsInRoot.top

        // tiles 0, 1, 2 share row 1; tile 3 is in row 2
        assertTrue("Tiles 0 and 1 should be in the same row", kotlin.math.abs(top0 - top1) < 2f)
        assertTrue("Tiles 0 and 2 should be in the same row", kotlin.math.abs(top0 - top2) < 2f)
        assertTrue("Tile 3 should be in a different row than tile 0", kotlin.math.abs(top0 - top3) > 50f)
    }

    @Test
    fun grid_hasFourColumns_whenWindowSizeExpanded() {
        val sessions = (0..4).map { createFakeSession(id = "expanded_col_$it") }
        setLibraryContent(sessions = sessions, windowWidthSizeClass = WindowWidthSizeClass.Expanded)

        val top0 = composeRule.onNodeWithTag("compare_library_session_tile_expanded_col_0")
            .fetchSemanticsNode().boundsInRoot.top
        val top1 = composeRule.onNodeWithTag("compare_library_session_tile_expanded_col_1")
            .fetchSemanticsNode().boundsInRoot.top
        val top2 = composeRule.onNodeWithTag("compare_library_session_tile_expanded_col_2")
            .fetchSemanticsNode().boundsInRoot.top
        val top3 = composeRule.onNodeWithTag("compare_library_session_tile_expanded_col_3")
            .fetchSemanticsNode().boundsInRoot.top
        val top4 = composeRule.onNodeWithTag("compare_library_session_tile_expanded_col_4")
            .fetchSemanticsNode().boundsInRoot.top

        // tiles 0, 1, 2, 3 share row 1; tile 4 is in row 2
        assertTrue("Tiles 0 and 1 should be in the same row", kotlin.math.abs(top0 - top1) < 2f)
        assertTrue("Tiles 0 and 2 should be in the same row", kotlin.math.abs(top0 - top2) < 2f)
        assertTrue("Tiles 0 and 3 should be in the same row", kotlin.math.abs(top0 - top3) < 2f)
        assertTrue("Tile 4 should be in a different row than tile 0", kotlin.math.abs(top0 - top4) > 50f)
    }

    private fun createFakeSession(
        id: String = fakeSessionId,
        title: String? = null,
        locationDisplayName: String? = null,
        locationCity: String? = null,
        locationCountry: String? = null
    ) = ScannedSession(
        sessionId = id,
        timestamp = fakeTimestamp,
        referenceFileUri = fakeReferenceUri,
        captureFileUri = fakeCaptureUri,
        title = title,
        locationDisplayName = locationDisplayName,
        locationCity = locationCity,
        locationCountry = locationCountry
    )

    // ── Location-Display-Tests ──────────────────────────────────────────────

    @Test
    fun tile_fallA_showsTitleAndLocation_whenBothPresent() {
        val session = createFakeSession(
            title = "Holla die Waldfee",
            locationDisplayName = "Cavallino",
            locationCountry = "Italien"
        )
        setLibraryContent(sessions = listOf(session))

        composeRule.onNodeWithText("Holla die Waldfee").assertIsDisplayed()
        composeRule.onNodeWithText("Cavallino · Italien").assertIsDisplayed()
    }

    @Test
    fun tile_fallA_doesNotShowTimestamp_whenTitleAndLocationPresent() {
        val session = createFakeSession(
            title = "Holla die Waldfee",
            locationCity = "München",
            locationCountry = "Deutschland"
        )
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText(expectedTimestamp).assertDoesNotExist()
    }

    @Test
    fun tile_fallA_allThreeLocationFields_displaysMostInformativeCandidate() {
        val session = createFakeSession(
            title = "Zugspitze",
            locationDisplayName = "Zugspitze Summit",
            locationCity = "Garmisch",
            locationCountry = "Deutschland"
        )
        setLibraryContent(sessions = listOf(session))

        composeRule.onNodeWithText("Zugspitze").assertIsDisplayed()
        composeRule.onNodeWithText("Zugspitze Summit · Garmisch, Deutschland").assertIsDisplayed()
    }

    @Test
    fun tile_fallB_showsTitleAndTimestamp_whenTitleButNoLocation() {
        val session = createFakeSession(title = "Goldener Herbst")
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText("Goldener Herbst").assertIsDisplayed()
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun tile_fallC_showsLocationAndTimestamp_whenLocationButNoTitle() {
        val session = createFakeSession(locationCity = "München", locationCountry = "Deutschland")
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText("München, Deutschland").assertIsDisplayed()
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun tile_fallC_displayNameOnly_whenCityAndCountryAbsent() {
        val session = createFakeSession(locationDisplayName = "Marienplatz")
        setLibraryContent(sessions = listOf(session))

        composeRule.onNodeWithText("Marienplatz").assertIsDisplayed()
    }

    @Test
    fun tile_fallD_showsOnlyTimestamp_whenNeitherTitleNorLocation() {
        val session = createFakeSession()
        setLibraryContent(sessions = listOf(session))

        val expectedTimestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText(expectedTimestamp).assertIsDisplayed()
    }

    @Test
    fun tile_heightStable_acrossFallAAndFallD() {
        // Fall D (kein Titel, keine Location) und Fall A (Titel + Location) müssen
        // dieselbe Tile-Höhe haben — reservedTextHeight-Spacer bleibt stabil.
        val sessionFallD = createFakeSession(id = "tile_d")
        val sessionFallA = createFakeSession(
            id = "tile_a",
            title = "Mein Titel",
            locationCity = "München"
        )
        // Beide Tiles landen in der ersten Zeile des 2-Spalten-Grids.
        setLibraryContent(sessions = listOf(sessionFallD, sessionFallA))

        val boundsD = composeRule.onNodeWithTag("compare_library_session_tile_tile_d")
            .fetchSemanticsNode().boundsInRoot
        val boundsA = composeRule.onNodeWithTag("compare_library_session_tile_tile_a")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Tiles müssen in derselben Zeile sein (top-Differenz < 2 px)",
            kotlin.math.abs(boundsD.top - boundsA.top) < 2f
        )
        assertTrue(
            "Tile-Höhen müssen identisch sein (Differenz < 2 px)",
            kotlin.math.abs(boundsD.height - boundsA.height) < 2f
        )
    }

    @Test
    fun tile_heightStable_acrossFallBAndFallC() {
        // Fall B (Titel, keine Location) und Fall C (Location, kein Titel) —
        // beide zeigen zwei Textzeilen und dürfen nicht unterschiedlich hoch sein.
        val sessionFallB = createFakeSession(id = "tile_b", title = "Mein Titel")
        val sessionFallC = createFakeSession(id = "tile_c", locationCity = "München")
        setLibraryContent(sessions = listOf(sessionFallB, sessionFallC))

        val boundsB = composeRule.onNodeWithTag("compare_library_session_tile_tile_b")
            .fetchSemanticsNode().boundsInRoot
        val boundsC = composeRule.onNodeWithTag("compare_library_session_tile_tile_c")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Tiles müssen in derselben Zeile sein (top-Differenz < 2 px)",
            kotlin.math.abs(boundsB.top - boundsC.top) < 2f
        )
        assertTrue(
            "Tile-Höhen müssen identisch sein (Differenz < 2 px)",
            kotlin.math.abs(boundsB.height - boundsC.height) < 2f
        )
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
