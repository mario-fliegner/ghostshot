package com.isardomains.ghostshot.ui.compare

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.camera.ScannedSession
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Date

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
        composeRule.onNodeWithText(context.getString(R.string.compare_library_empty_state))
            .assertIsDisplayed()
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
    fun emptyState_ctaInvokesOnBackCallback() {
        var backCount = 0
        setLibraryContent(sessions = emptyList(), onBack = { backCount++ })

        composeRule.onNodeWithTag("compare_library_empty_cta").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
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
        onDeleteSessions: (List<String>) -> Unit = {}
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
                    CompareLibraryScreen(
                        sessions = sessions,
                        onRefresh = onRefresh,
                        onSessionClick = onSessionClick,
                        onBack = onBack,
                        onDeleteSessions = onDeleteSessions
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createFakeSession(id: String = fakeSessionId) = ScannedSession(
        sessionId = id,
        timestamp = fakeTimestamp,
        referenceFileUri = fakeReferenceUri,
        captureFileUri = fakeCaptureUri
    )

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
