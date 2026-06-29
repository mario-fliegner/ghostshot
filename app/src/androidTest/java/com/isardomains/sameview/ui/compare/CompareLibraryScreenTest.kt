package com.isardomains.sameview.ui.compare

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.camera.ScannedSession
import com.isardomains.sameview.ui.settings.LibraryFilter
import com.isardomains.sameview.ui.settings.LibrarySortOrder
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

    // ── Block D: Favourite star tests ────────────────────────────────────────

    @Test
    fun tile_starVisible_whenNotInSelectionMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        // Star is a child of combinedClickable → only accessible in unmerged tree
        composeRule.onNodeWithTag(
            "compare_library_tile_favorite_star_$fakeSessionId",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun tile_starHidden_whenInSelectionMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(
            "compare_library_tile_favorite_star_$fakeSessionId",
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun tile_starTap_doesNotOpenSession() {
        var sessionClickCount = 0
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            onSessionClick = { sessionClickCount++ }
        )

        // performClick() invokes the semantics onClick action on the star node only
        composeRule.onNodeWithTag(
            "compare_library_tile_favorite_star_$fakeSessionId",
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(0, sessionClickCount)
    }

    @Test
    fun tile_starTap_doesNotActivateMultiSelect() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag(
            "compare_library_tile_favorite_star_$fakeSessionId",
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()

        // Cancel button only appears when multi-select is active
        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()
    }

    @Test
    fun tile_starTap_invokesToggleFavorite() {
        var toggledSessionId: String? = null
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            onToggleFavorite = { sessionId -> toggledSessionId = sessionId }
        )

        composeRule.onNodeWithTag(
            "compare_library_tile_favorite_star_$fakeSessionId",
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(fakeSessionId, toggledSessionId)
    }

    @Test
    fun longPress_stillActivatesMultiSelect_withStarPresent() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()
    }

    @Test
    fun starContentDescription_markAsFavorite_whenNotFavorited() {
        setLibraryContent(sessions = listOf(createFakeSession(isFavorite = false)))

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_library_tile_favorite_mark),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun starContentDescription_removeFromFavorites_whenFavorited() {
        setLibraryContent(sessions = listOf(createFakeSession(isFavorite = true)))

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_library_tile_favorite_remove),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    // ── Block F: Filter + Sort UI tests ──────────────────────────────────────

    @Test
    fun overflowMenu_isVisibleInNormalMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_overflow_button").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_isNotVisibleInSelectionMode() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_library_overflow_button").assertDoesNotExist()
    }

    @Test
    fun overflowMenu_containsFilterAndSortSections() {
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_library_filter_header)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_sort_header)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_filter_all)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_sort_newest_first)).assertIsDisplayed()
    }

    @Test
    fun filter_favorites_showsOnlyFavoritedSessions() {
        val favSession = createFakeSession(id = "fav", isFavorite = true)
        val notFavSession = createFakeSession(id = "not_fav", isFavorite = false)
        setLibraryContent(
            sessions = listOf(favSession, notFavSession),
            libraryFilter = LibraryFilter.FAVORITES
        )

        composeRule.onNodeWithTag("compare_library_session_tile_fav").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_session_tile_not_fav").assertDoesNotExist()
    }

    @Test
    fun filter_all_showsAllSessions() {
        val favSession = createFakeSession(id = "fav", isFavorite = true)
        val notFavSession = createFakeSession(id = "not_fav", isFavorite = false)
        setLibraryContent(
            sessions = listOf(favSession, notFavSession),
            libraryFilter = LibraryFilter.ALL
        )

        composeRule.onNodeWithTag("compare_library_session_tile_fav").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_session_tile_not_fav").assertIsDisplayed()
    }

    @Test
    fun sort_newestFirst_correctOrder() {
        val newer = createFakeSession(id = "newer", timestamp = 3000L)
        val middle = createFakeSession(id = "middle", timestamp = 2000L)
        val older = createFakeSession(id = "older", timestamp = 1000L)
        setLibraryContent(
            sessions = listOf(newer, middle, older),
            librarySortOrder = LibrarySortOrder.NEWEST_FIRST
        )

        val topNewer = composeRule.onNodeWithTag("compare_library_session_tile_newer")
            .fetchSemanticsNode().boundsInRoot.top
        val topOlder = composeRule.onNodeWithTag("compare_library_session_tile_older")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Newer session should appear above older in NEWEST_FIRST order", topNewer < topOlder)
    }

    @Test
    fun sort_oldestFirst_correctOrder() {
        val newer = createFakeSession(id = "newer", timestamp = 3000L)
        val middle = createFakeSession(id = "middle", timestamp = 2000L)
        val older = createFakeSession(id = "older", timestamp = 1000L)
        setLibraryContent(
            sessions = listOf(newer, middle, older),
            librarySortOrder = LibrarySortOrder.OLDEST_FIRST
        )

        val topNewer = composeRule.onNodeWithTag("compare_library_session_tile_newer")
            .fetchSemanticsNode().boundsInRoot.top
        val topOlder = composeRule.onNodeWithTag("compare_library_session_tile_older")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Older session should appear above newer in OLDEST_FIRST order", topOlder < topNewer)
    }

    @Test
    fun filterAndSort_combined_correct() {
        val favOld = createFakeSession(id = "fav_old", isFavorite = true, timestamp = 1000L)
        val favNew = createFakeSession(id = "fav_new", isFavorite = true, timestamp = 3000L)
        val notFav = createFakeSession(id = "not_fav", isFavorite = false, timestamp = 2000L)
        setLibraryContent(
            sessions = listOf(favOld, favNew, notFav),
            libraryFilter = LibraryFilter.FAVORITES,
            librarySortOrder = LibrarySortOrder.OLDEST_FIRST
        )

        // Non-favorited session must not appear
        composeRule.onNodeWithTag("compare_library_session_tile_not_fav").assertDoesNotExist()
        // Both favorited sessions are visible
        composeRule.onNodeWithTag("compare_library_session_tile_fav_old").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_session_tile_fav_new").assertIsDisplayed()
        // Oldest favorited session appears above newest
        val topOld = composeRule.onNodeWithTag("compare_library_session_tile_fav_old")
            .fetchSemanticsNode().boundsInRoot.top
        val topNew = composeRule.onNodeWithTag("compare_library_session_tile_fav_new")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Oldest favorite should appear above newest in OLDEST_FIRST order", topOld <= topNew)
    }

    @Test
    fun emptyState_favorites_shownWhenNoFavorites() {
        val notFav = createFakeSession(isFavorite = false)
        setLibraryContent(
            sessions = listOf(notFav),
            libraryFilter = LibraryFilter.FAVORITES
        )

        composeRule.onNodeWithTag("compare_library_empty_favorites_state").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_empty_favorites_title))
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_favorites_disappears_whenFavoriteAdded() {
        // Start with no favorites → favorites empty state shown
        var sessions = listOf(createFakeSession(id = "s1", isFavorite = false))
        var currentSessions = sessions
        setLibraryContent(
            sessions = currentSessions,
            libraryFilter = LibraryFilter.FAVORITES
        )

        composeRule.onNodeWithTag("compare_library_empty_favorites_state").assertIsDisplayed()

        // Simulate favorite added by recomposing with a favorited session
        composeRule.runOnUiThread {
            // Content is already set; we verify the logic works when sessions have isFavorite=true
        }

        // Re-set content with a favorited session
        setLibraryContent(
            sessions = listOf(createFakeSession(id = "s1", isFavorite = true)),
            libraryFilter = LibraryFilter.FAVORITES
        )

        composeRule.onNodeWithTag("compare_library_empty_favorites_state").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_library_session_tile_s1").assertIsDisplayed()
    }

    @Test
    fun selectAll_inFavoritesFilter_selectsOnlyFavorites() {
        val fav1 = createFakeSession(id = "fav1", isFavorite = true)
        val fav2 = createFakeSession(id = "fav2", isFavorite = true)
        val notFav = createFakeSession(id = "not_fav", isFavorite = false)
        setLibraryContent(
            sessions = listOf(fav1, fav2, notFav),
            libraryFilter = LibraryFilter.FAVORITES
        )

        // Enter selection mode via long press on visible tile
        composeRule.onNodeWithTag("compare_library_session_tile_fav1")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Select all (visible = only the 2 favorites)
        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()

        // Should show 2 selected (only the 2 visible favorites)
        composeRule.onNodeWithText(context.getString(R.string.compare_library_selection_count, 2))
            .assertIsDisplayed()
    }

    private fun setLibraryContent(
        sessions: List<ScannedSession>,
        onRefresh: () -> Unit = {},
        onSessionClick: (ScannedSession) -> Unit = {},
        onBack: () -> Unit = {},
        onDeleteSessions: (List<String>) -> Unit = {},
        onBackupSessions: (List<String>, Uri) -> Unit = { _, _ -> },
        onToggleFavorite: (String) -> Unit = {},
        libraryFilter: LibraryFilter = LibraryFilter.ALL,
        librarySortOrder: LibrarySortOrder = LibrarySortOrder.NEWEST_FIRST,
        onSetLibraryFilter: (LibraryFilter) -> Unit = {},
        onSetLibrarySortOrder: (LibrarySortOrder) -> Unit = {},
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
                        onToggleFavorite = onToggleFavorite,
                        libraryFilter = libraryFilter,
                        librarySortOrder = librarySortOrder,
                        onSetLibraryFilter = onSetLibraryFilter,
                        onSetLibrarySortOrder = onSetLibrarySortOrder,
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
        locationCountry: String? = null,
        isFavorite: Boolean = false,
        timestamp: Long = fakeTimestamp
    ) = ScannedSession(
        sessionId = id,
        timestamp = timestamp,
        referenceFileUri = fakeReferenceUri,
        captureFileUri = fakeCaptureUri,
        title = title,
        locationDisplayName = locationDisplayName,
        locationCity = locationCity,
        locationCountry = locationCountry,
        isFavorite = isFavorite
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

    // ── Block G: Backup state-ownership regression ────────────────────────────

    /**
     * Regression test for the empty-ZIP bug:
     *
     * Before the fix, the SAF callback read [selectedSessionIds] at callback time.
     * If the selection was cleared between the backup tap and the SAF result (e.g.
     * due to process death or the user pressing cancel while the picker was open),
     * [onBackupSessions] received an empty list and the exporter produced a 0-byte file.
     *
     * After the fix, the IDs are snapshotted into [pendingBackupSessionIds] at tap time.
     * The callback reads that snapshot, so it is immune to later selection changes.
     */
    @Test
    fun backupCallback_usesSnapshotIds_notCurrentSelectionState() {
        // Fake registry intercepts the SAF launcher without showing the real picker.
        var launchRequestCode: Int? = null
        val fakeUri = Uri.parse("content://test/SameView_Backup_test.zip")
        val testRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launchRequestCode = requestCode
            }
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = testRegistry
        }

        val capturedArgs = mutableListOf<Pair<List<String>, Uri>>()
        val sessions = (1..11).map { i -> createFakeSession(id = "sess_$i") }

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
                    CompositionLocalProvider(
                        LocalActivityResultRegistryOwner provides registryOwner
                    ) {
                        CompareLibraryScreen(
                            sessions = sessions,
                            onRefresh = {},
                            onSessionClick = {},
                            onBack = {},
                            onBackupSessions = { ids, uri -> capturedArgs.add(ids to uri) }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Enter multi-select and select all 11 sessions.
        composeRule.onNodeWithTag("compare_library_session_tile_sess_1")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_library_selection_count, 11))
            .assertIsDisplayed()

        // Tap backup — pendingBackupSessionIds is now [sess_1..sess_11];
        // our fake registry records the request code without showing the real picker.
        composeRule.onNodeWithTag("compare_library_backup_button").performClick()
        composeRule.waitForIdle()
        val requestCode = checkNotNull(launchRequestCode) {
            "SAF launcher was not invoked after tapping the backup button"
        }

        // Simulate selection loss: press cancel so selectedSessionIds becomes emptySet().
        // This mirrors what happens after process death (Activity recreation resets remember state).
        // With the old code the callback would now send [] to onBackupSessions.
        composeRule.onNodeWithTag("compare_library_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()

        // Deliver the fake SAF result (as if the user confirmed the save location).
        scenario?.onActivity {
            testRegistry.dispatchResult(requestCode, fakeUri)
        }
        composeRule.waitForIdle()

        // onBackupSessions must have been called exactly once with the 11 originally
        // selected IDs — NOT with the empty list that selectedSessionIds now holds.
        assertEquals("onBackupSessions must be called exactly once", 1, capturedArgs.size)
        val (receivedIds, receivedUri) = capturedArgs.first()
        assertEquals("SAF URI must be forwarded unchanged", fakeUri, receivedUri)
        assertEquals(
            "All 11 originally captured session IDs must reach the exporter",
            11,
            receivedIds.size
        )
        assertTrue(
            "Every originally selected ID must be present in the delivered list",
            receivedIds.containsAll((1..11).map { "sess_$it" })
        )
    }

    // ── Block H: Backup UX — progress indicator and selection lifecycle ──────

    @Test
    fun backupInProgress_showsLinearProgressIndicator() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isBackupInProgress = true
        )
        composeRule.onNodeWithTag("compare_library_progress_indicator").assertIsDisplayed()
    }

    @Test
    fun backupNotInProgress_hidesLinearProgressIndicator() {
        setLibraryContent(
            sessions = listOf(createFakeSession()),
            isBackupInProgress = false
        )
        composeRule.onNodeWithTag("compare_library_progress_indicator").assertDoesNotExist()
    }

    @Test
    fun backupSuccessGeneration_increase_exitsSelectionMode() {
        val successGen = androidx.compose.runtime.mutableStateOf(0L)

        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                com.isardomains.sameview.ui.theme.SameViewTheme {
                    CompareLibraryScreen(
                        sessions = listOf(createFakeSession()),
                        onRefresh = {},
                        onSessionClick = {},
                        onBack = {},
                        backupSuccessGeneration = successGen.value
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Enter selection mode.
        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()

        // Simulate successful backup: increment the success generation counter.
        composeRule.runOnUiThread { successGen.value++ }
        composeRule.waitForIdle()

        // Selection mode must have exited.
        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_library_back_button").assertIsDisplayed()
    }

    @Test
    fun backupSuccessGeneration_unchanged_keepsSelectionMode() {
        // Simulates a failed backup: no generation increment → selection must remain active.
        setLibraryContent(sessions = listOf(createFakeSession()))

        composeRule.onNodeWithTag("compare_library_session_tile_$fakeSessionId")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()

        // No backupSuccessGeneration change — selection mode must remain.
        composeRule.onNodeWithTag("compare_library_cancel_button").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.compare_library_selection_count, 1)
        ).assertIsDisplayed()
    }

    @Test
    fun backupSuccessGeneration_increase_clearsSselectedIds() {
        val successGen = androidx.compose.runtime.mutableStateOf(0L)

        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                com.isardomains.sameview.ui.theme.SameViewTheme {
                    CompareLibraryScreen(
                        sessions = listOf(
                            createFakeSession(id = "a"),
                            createFakeSession(id = "b"),
                            createFakeSession(id = "c")
                        ),
                        onRefresh = {},
                        onSessionClick = {},
                        onBack = {},
                        backupSuccessGeneration = successGen.value
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Select all 3 sessions.
        composeRule.onNodeWithTag("compare_library_session_tile_a").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_library_select_all_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            context.getString(R.string.compare_library_selection_count, 3)
        ).assertIsDisplayed()

        // Simulate successful backup.
        composeRule.runOnUiThread { successGen.value++ }
        composeRule.waitForIdle()

        // Normal mode — selection count label must be gone, back button visible.
        composeRule.onNodeWithTag("compare_library_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_library_cancel_button").assertDoesNotExist()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
