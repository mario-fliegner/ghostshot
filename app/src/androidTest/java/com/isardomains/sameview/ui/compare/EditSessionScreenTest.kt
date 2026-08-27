package com.isardomains.sameview.ui.compare

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
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
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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

    // ── Group 2.1: Country Picker (Issue #2) ──────────────────────────────────

    @Test
    fun countryPicker_tapField_opensPicker() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_country_field").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_picker_list").assertIsDisplayed()
    }

    @Test
    fun countryPicker_selectCountry_updatesTriggerField_andEnablesSave() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_country_field").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_search")
            .performTextInput("Germany")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Germany"))
        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
    }

    @Test
    fun countryPicker_cancelViaBackPress_preservesOriginalValue() {
        setEditSessionContent(
            createSession(locationCountry = "Germany", locationCountryCode = "DE")
        )

        composeRule.onNodeWithTag("edit_session_country_field").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_list").assertIsDisplayed()

        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Germany"))
        composeRule.onNodeWithTag("edit_session_save_button").assertIsNotEnabled()
    }

    @Test
    fun countryPicker_legacyNonCanonicalValue_remainsVisible_untouched() {
        setEditSessionContent(createSession(locationCountry = "Östereich"))

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Östereich"))
    }

    @Test
    fun countryPicker_unrelatedTitleEdit_preservesLegacyCountry_afterSave() {
        val sessionId = createSession(locationCountry = "Östereich")
        setEditSessionContent(sessionId)

        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("New Title")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        val location = readSavedLocation(sessionId)
        assertEquals("Östereich", location.optString("country", ""))
        assertEquals(false, location.has("countryCode"))
    }

    @Test
    fun countryPicker_explicitSelection_overwritesLegacyValue_andPersistsCode_afterSave() {
        val sessionId = createSession(locationCountry = "Östereich")
        setEditSessionContent(sessionId)

        composeRule.onNodeWithTag("edit_session_country_field").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("Austria")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_row_AT").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        val location = readSavedLocation(sessionId)
        assertEquals("Austria", location.optString("country", ""))
        assertEquals("AT", location.optString("countryCode", ""))
    }

    @Test
    fun countryPicker_explicitClear_removesBothFields_afterSave() {
        val sessionId = createSession(locationCountry = "Germany", locationCountryCode = "DE")
        setEditSessionContent(sessionId)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.edit_session_country_clear_content_description)
        ).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        val location = readSavedLocation(sessionId)
        assertEquals(false, location.has("country"))
        assertEquals(false, location.has("countryCode"))
    }

    // ── Issue #2: locale-aware Country display in the trigger field ──────────

    @Test
    fun countryTrigger_validCode_de_showsLocalizedName() {
        val sessionId = createSession(locationCountry = "Germany", locationCountryCode = "DE")
        setEditSessionContent(sessionId, locale = Locale.GERMANY)

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Deutschland"))
    }

    @Test
    fun countryTrigger_validCode_en_showsLocalizedName() {
        val sessionId = createSession(locationCountry = "Deutschland", locationCountryCode = "DE")
        setEditSessionContent(sessionId, locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Germany"))
    }

    @Test
    fun countryTrigger_localeDisplayChange_doesNotEnableSave() {
        // A locale-driven re-render of the trigger's visible text is not a user edit — Save must
        // stay disabled, and no metadata write occurs (SESSION_METADATA_V1.md §6.9.7).
        val sessionId = createSession(locationCountry = "Germany", locationCountryCode = "DE")
        setEditSessionContent(sessionId, locale = Locale.GERMANY)

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Deutschland"))
        composeRule.onNodeWithTag("edit_session_save_button").assertIsNotEnabled()

        val location = readSavedLocation(sessionId)
        assertEquals("Germany", location.optString("country", ""))
        assertEquals("DE", location.optString("countryCode", ""))
    }

    @Test
    fun countryTrigger_legacyNoCode_showsExactStoredValue_regardlessOfLocale() {
        val sessionId = createSession(locationCountry = "Östereich")
        setEditSessionContent(sessionId, locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_field")
            .performScrollTo()
            .assert(hasText("Östereich"))
    }

    @Test
    fun countryPickerSheet_de_showsGermanNames_listVisibleImmediately() {
        setCountryPickerSheetContent(locale = Locale.GERMANY)

        // The full list renders immediately with no search required (asserted here); "Deutschland"
        // is then located via search purely because the LazyColumn only composes rows within its
        // viewport, and it sorts well past the initial screen in an unfiltered ~195-country list.
        composeRule.onNodeWithTag("edit_session_country_picker_list").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("Deutschland")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE")
            .assert(hasText("Deutschland"))
    }

    @Test
    fun countryPickerSheet_en_showsEnglishNames_listVisibleImmediately() {
        setCountryPickerSheetContent(locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_picker_list").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("Germany")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE")
            .assert(hasText("Germany"))
    }

    @Test
    fun countryPickerSheet_filtersFromFirstCharacter() {
        setCountryPickerSheetContent(locale = Locale.GERMANY)

        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("d")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_picker_row_DE").assertIsDisplayed()
    }

    @Test
    fun countryPickerSheet_isoCodeSearchMatch() {
        // "JP" is chosen because no English country display name starts with or contains "jp",
        // so a match can only come from the code tier — an unambiguous test of the ISO-code
        // secondary-match rule (CountryCatalog.filter ranks it after any name match; "DE" would be
        // a poor choice here since several English names start with "De", outranking the code match).
        setCountryPickerSheetContent(locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("JP")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_picker_row_JP").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_session_country_picker_row_JP").assert(hasText("Japan"))
    }

    @Test
    fun countryPickerSheet_noResults_showsEmptyState() {
        setCountryPickerSheetContent(locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_picker_search")
            .performTextInput("zzzzznotacountryzzzzz")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_picker_no_results").assertIsDisplayed()
    }

    @Test
    fun countryPickerSheet_selectingRow_invokesCallback_withMatchingCodeAndName() {
        var selected: CountryEntry? = null
        setCountryPickerSheetContent(
            locale = Locale.US,
            onCountrySelected = { selected = it }
        )

        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("Germany")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_country_picker_row_DE").performClick()
        composeRule.waitForIdle()

        assertEquals("DE", selected?.code)
        assertEquals("Germany", selected?.displayName)
    }

    @Test
    fun countryPickerSheet_rows_haveAccessibleContentDescription() {
        setCountryPickerSheetContent(locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_country_picker_search").performTextInput("Germany")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_country_picker_row_DE")
            .assert(androidx.compose.ui.test.hasContentDescription("Germany"))
    }

    /** Reads the `location` block from a session's saved metadata.json, or an empty JSONObject if absent. */
    private fun readSavedLocation(sessionId: String): JSONObject {
        val metadataFile = File(File(context.filesDir, "sessions/$sessionId"), "metadata.json")
        val json = JSONObject(metadataFile.readText())
        return json.optJSONObject("location") ?: JSONObject()
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

    // ── Issue #3: Reference Date must not be after Capture Date ──────────────

    @Test
    fun referenceDate_laterThanCapture_showsOrderErrorText_en() {
        val sessionId = createSession(captureTimestampMs = captureTimestampFor(2026, Calendar.AUGUST, 27))
        setEditSessionContent(sessionId, locale = Locale.US)

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("2026-08-28")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reference date can't be later than the capture date.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun referenceDate_laterThanCapture_showsOrderErrorText_de() {
        val sessionId = createSession(captureTimestampMs = captureTimestampFor(2026, Calendar.AUGUST, 27))
        setEditSessionContent(sessionId, locale = Locale.GERMANY)

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("2026-08-28")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Das Referenzdatum darf nicht nach dem Aufnahmedatum liegen.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun referenceDate_sameAsCapture_isAccepted_noOrderError() {
        val sessionId = createSession(captureTimestampMs = captureTimestampFor(2026, Calendar.AUGUST, 27))
        setEditSessionContent(sessionId)

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .performTextInput("2026-08-27")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reference date can't be later than the capture date.")
            .assertDoesNotExist()
    }

    @Test
    fun referenceDate_legacyInvalidValue_visibleOnOpen() {
        val sessionId = createSession(
            referenceDate = "2026-09-01",
            captureTimestampMs = captureTimestampFor(2026, Calendar.AUGUST, 27)
        )
        setEditSessionContent(sessionId)

        composeRule.onNodeWithTag("edit_session_reference_date_field")
            .performScrollTo()
            .assert(hasText("2026-09-01"))
        // No error is shown merely for having opened the editor — the order rule has not run yet.
        composeRule.onNodeWithText("Reference date can't be later than the capture date.")
            .assertDoesNotExist()
    }

    @Test
    fun referenceDate_legacyInvalidValueUntouched_unrelatedTitleEdit_savesSuccessfully() {
        val sessionId = createSession(
            referenceDate = "2026-09-01",
            captureTimestampMs = captureTimestampFor(2026, Calendar.AUGUST, 27)
        )
        setEditSessionContent(sessionId)

        composeRule.onNodeWithTag("edit_session_title_field").performTextInput("New Title")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reference date can't be later than the capture date.")
            .assertDoesNotExist()
        val json = JSONObject(
            File(File(context.filesDir, "sessions/$sessionId"), "metadata.json").readText()
        )
        assertEquals("2026-09-01", json.optJSONObject("reference")?.optString("date", ""))
    }

    /** Returns a local-timezone epoch millisecond timestamp for the given calendar date, noon local time. */
    private fun captureTimestampFor(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── Group 6: Privacy Mode Disclosure ──────────────────────────────────────

    @Test
    fun referenceMetadataPreservedHint_visible_whenPreservationNotPossible() {
        setEditSessionContent(createSession(referenceSourcePreservation = "not_possible"))

        composeRule.onNodeWithTag("edit_session_reference_metadata_preserved_hint")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun referenceMetadataPreservedHint_notVisible_whenOriginalsBlockAbsent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_reference_metadata_preserved_hint").assertDoesNotExist()
    }

    // ── Block F.3: Favourite star tests ──────────────────────────────────────

    @Test
    fun favoriteButton_isVisible() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_favorite_button").assertIsDisplayed()
    }

    @Test
    fun favoriteButton_showsOutlineIcon_whenNotFavorited() {
        setEditSessionContent(createSession(isFavorite = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_screen_favorite_mark)
        ).assertIsDisplayed()
    }

    @Test
    fun favoriteButton_showsFilledIcon_whenFavorited() {
        setEditSessionContent(createSession(isFavorite = true))
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_screen_favorite_remove)
        ).assertIsDisplayed()
    }

    @Test
    fun favoriteButton_tap_togglesImmediately() {
        setEditSessionContent(createSession(isFavorite = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_session_favorite_button").performClick()
        composeRule.waitForIdle()

        // After toggle: filled star (favorited) content description visible
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_screen_favorite_remove)
        ).assertIsDisplayed()
    }

    @Test
    fun favoriteButton_doesNotAffectDirtyState() {
        setEditSessionContent(createEmptySession())
        composeRule.waitForIdle()

        // Tap star — no form changes
        composeRule.onNodeWithTag("edit_session_favorite_button").performClick()
        composeRule.waitForIdle()

        // Save button must still be disabled (isDirty unchanged)
        composeRule.onNodeWithTag("edit_session_save_button").assertIsNotEnabled()
    }

    @Test
    fun favoriteButton_doesNotAffectSaveButton() {
        setEditSessionContent(createEmptySession())
        composeRule.waitForIdle()

        // Make form dirty via title change → Save enables
        composeRule.onNodeWithTag("edit_session_title_field")
            .performScrollTo().performTextInput("A")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()

        // Tap star → Save button must remain enabled (star toggle does not reset dirty)
        composeRule.onNodeWithTag("edit_session_favorite_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_session_save_button").assertIsEnabled()
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
        locationCountryCode: String = "",
        captureTimestampMs: Long = 0L,
        isFavorite: Boolean = false,
        referenceSourcePreservation: String? = null
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
        if (locationDisplayName.isNotEmpty() || locationCity.isNotEmpty() || locationCountry.isNotEmpty() ||
            locationCountryCode.isNotEmpty()
        ) {
            json.put("location", JSONObject().apply {
                if (locationDisplayName.isNotEmpty()) put("displayName", locationDisplayName)
                if (locationCity.isNotEmpty()) put("city", locationCity)
                if (locationCountry.isNotEmpty()) put("country", locationCountry)
                if (locationCountryCode.isNotEmpty()) put("countryCode", locationCountryCode)
            })
        }
        json.put("additional", org.json.JSONObject().apply {
            put("isFavorite", isFavorite)
            put("visibility", "private")
            put("source", "sameview")
        })
        if (referenceSourcePreservation != null) {
            json.put("originals", JSONObject().apply {
                put("privacyMode", true)
                put("capturePreservation", "metadata_stripped")
                put("referenceSourcePreservation", referenceSourcePreservation)
            })
        }
        File(sessionDir, "metadata.json").writeText(json.toString())

        return sessionId
    }

    private fun setEditSessionContent(
        sessionId: String,
        onBack: () -> Unit = {},
        locale: Locale? = null
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
                    val screenContent = @androidx.compose.runtime.Composable {
                        EditSessionScreen(
                            sessionId = sessionId,
                            onBack = onBack,
                            viewModel = vm
                        )
                    }
                    if (locale != null) {
                        val localizedConfiguration = Configuration(LocalConfiguration.current).apply {
                            setLocale(locale)
                        }
                        CompositionLocalProvider(LocalConfiguration provides localizedConfiguration) {
                            screenContent()
                        }
                    } else {
                        screenContent()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel?.isLoading?.value == false
        }
        composeRule.waitForIdle()
    }

    /**
     * Renders [CountryPickerSheet] standalone with an explicit [locale], independent of the
     * device/emulator's own locale — [CountryPickerSheet] takes [Locale] as a parameter rather
     * than reading it internally, so this gives deterministic DE/EN test coverage regardless of
     * the test environment's configured locale.
     */
    private fun setCountryPickerSheetContent(
        locale: Locale,
        onCountrySelected: (CountryEntry) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                SameViewTheme {
                    CountryPickerSheet(
                        currentLocale = locale,
                        onCountrySelected = onCountrySelected,
                        onDismiss = onDismiss
                    )
                }
            }
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

    // ── Regression guard — logo card removed from Edit Session ───────────────

    @Test
    fun editSession_logoCard_absent() {
        setEditSessionContent(createEmptySession())

        composeRule.onNodeWithTag("edit_session_logo_placeholder").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_session_logo_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_session_logo_choose_photo").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_session_logo_use_symbol").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_session_logo_remove").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_session_logo_use_default").assertDoesNotExist()
    }
}
