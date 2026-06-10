package com.isardomains.sameview.ui.compare

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditSessionScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    // ── Group 1: Screen Structure ─────────────────────────────────────────────

    @Test
    fun screenStructure_topBarWithBackAndSave() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_save_button").assertIsDisplayed()
    }

    @Test
    fun screenStructure_allSixEditableFieldsPresent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_title_field").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_description_field").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_reference_date_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_place_name_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_city_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_field").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun screenStructure_createdHeaderPresent_whenCaptureTimestampAvailable() {
        // 1749386200000 = 2026-06-08 (arbitrary past timestamp)
        setEditSessionContent(createSession(captureTimestampMs = 1749386200000L))

        // R.string.edit_session_created = "Created %s"; look for the localized prefix as substring.
        val prefix = context.getString(R.string.edit_session_created, "").trimEnd()
        composeRule.onNodeWithText(prefix, substring = true).assertIsDisplayed()
    }

    @Test
    fun screenStructure_noStandaloneSessionHeader() {
        setEditSessionContent(createEmptySession())

        // The Session card no longer has a static "Session" title — it uses "Created <date>" or no title.
        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_card_session)
        ).assertDoesNotExist()
    }

    @Test
    fun screenStructure_referencePhotoCardPresent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_card_reference_photo)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun screenStructure_currentPhotoCardPresent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_card_current_photo)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun screenStructure_capturedOnLabelPresent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_label_captured_on)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun screenStructure_locationCardPresent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_card_location)
        ).performScrollTo().assertIsDisplayed()
    }

    // ── Group 2: Pre-Population ───────────────────────────────────────────────

    @Test
    fun prePopulation_titlePrePopulated() {
        setEditSessionContent(createSession(title = "Zugspitze 2026"))

        composeRule.onNodeWithTag("edit_session_title_field")
            .assert(hasText("Zugspitze 2026"))
    }

    @Test
    fun prePopulation_referenceDatePrePopulated() {
        setEditSessionContent(createSession(referenceDate = "2008-06"))

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .assert(hasText("2008-06"))
    }

    @Test
    fun prePopulation_locationPrePopulated() {
        setEditSessionContent(
            createSession(
                locationDisplayName = "Zugspitze Summit",
                locationCity = "Garmisch-Partenkirchen",
                locationCountry = "Deutschland"
            )
        )

        composeRule.onNodeWithTag("edit_session_place_name_field")
            .performScrollTo()
            .assert(hasText("Zugspitze Summit"))
        composeRule.onNodeWithTag("edit_session_city_field")
            .performScrollTo()
            .assert(hasText("Garmisch-Partenkirchen"))
        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Deutschland"))
    }

    // ── Group 3: Save-Button-State ────────────────────────────────────────────

    @Test
    fun saveButton_disabledInitially() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_save_button").assertIsNotEnabled()
    }

    @Test
    fun saveButton_enabledAfterTitleChange() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("New Title")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
    }

    @Test
    fun saveButton_enabledAfterDescriptionChange() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_description_field").performTextInput("Some description")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
    }

    @Test
    fun saveButton_enabledAfterReferenceDateChange() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("2008")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
    }

    @Test
    fun saveButton_enabledAfterLocationChange() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_place_name_field")
            .performScrollTo()
            .performTextInput("Summit")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
    }

    // ── Group 4: Dialoge / Navigation ─────────────────────────────────────────

    @Test
    fun back_withNoChanges_invokesBackCallback() {
        var backCount = 0
        setEditSessionContent(createEmptySession(), onBack = { backCount++ })

        composeRule.onNodeWithTag("edit_session_back_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    @Test
    fun back_withChanges_showsDiscardDialog() {
        setEditSessionContent(createEmptySession())
        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("Changed")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_discard_dialog_title)
        ).assertIsDisplayed()
    }

    @Test
    fun discardDialog_confirmNavigatesBack() {
        var backCount = 0
        setEditSessionContent(createEmptySession(), onBack = { backCount++ })
        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("Changed")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_discard_confirm)
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    @Test
    fun discardDialog_cancelKeepsEditorOpen() {
        var backCount = 0
        setEditSessionContent(createEmptySession(), onBack = { backCount++ })
        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("Changed")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_discard_cancel)
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(0, backCount)
        composeRule.onNodeWithTag("edit_session_screen_root").assertIsDisplayed()
    }

    // ── Group 5: Validation UI ────────────────────────────────────────────────

    @Test
    fun invalidDate_showsErrorText() {
        setEditSessionContent(createEmptySession())
        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("not-a-date")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_reference_date_error)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun validDate_noErrorText() {
        setEditSessionContent(createEmptySession())
        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("2008-06-15")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.edit_session_reference_date_error)
        ).assertDoesNotExist()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createEmptySession(): String = createSession()

    private fun createSession(
        title: String = "",
        description: String = "",
        referenceDate: String = "",
        locationDisplayName: String = "",
        locationCity: String = "",
        locationCountry: String = "",
        captureTimestampMs: Long = 0L
    ): String {
        val sessionId = "edit_test_${System.nanoTime()}"
        val sessionsRoot = File(context.filesDir, "sessions")
        val sessionDir = File(sessionsRoot, sessionId)
        sessionDir.mkdirs()
        tempDirs += sessionDir

        createImageFile(File(sessionDir, "reference.jpg"), Color.rgb(220, 40, 40))
        createImageFile(File(sessionDir, "capture.jpg"), Color.rgb(40, 120, 220))

        val json = JSONObject()
        json.put("version", 4)
        json.put("session", JSONObject().apply {
            put("id", sessionId)
            put("createdAtMs", captureTimestampMs)
        })
        if (captureTimestampMs > 0L) {
            json.put("capture", JSONObject().apply { put("timestampMs", captureTimestampMs) })
        }
        json.put("reference", JSONObject().apply {
            put("sourceDisplayName", "test://reference")
            if (referenceDate.isNotEmpty()) put("date", referenceDate)
        })
        if (title.isNotEmpty() || description.isNotEmpty()) {
            json.put("content", JSONObject().apply {
                if (title.isNotEmpty()) put("title", title)
                if (description.isNotEmpty()) put("description", description)
            })
        }
        if (locationDisplayName.isNotEmpty() || locationCity.isNotEmpty() || locationCountry.isNotEmpty()) {
            json.put("location", JSONObject().apply {
                if (locationDisplayName.isNotEmpty()) put("displayName", locationDisplayName)
                if (locationCity.isNotEmpty()) put("city", locationCity)
                if (locationCountry.isNotEmpty()) put("country", locationCountry)
            })
        }
        File(sessionDir, "metadata.json").writeText(json.toString())

        return sessionId
    }

    private fun setEditSessionContent(
        sessionId: String,
        onBack: () -> Unit = {}
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var viewModel: EditSessionViewModel? = null
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId))
            viewModel = EditSessionViewModel(savedStateHandle, activity.applicationContext)
            val vm = viewModel!!
            activity.setContent {
                SameViewTheme {
                    EditSessionScreen(
                        sessionId = sessionId,
                        onBack = onBack,
                        viewModel = vm
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel?.isLoading?.value == false
        }
        composeRule.waitForIdle()
    }

    private fun createImageFile(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        }
        bitmap.recycle()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
