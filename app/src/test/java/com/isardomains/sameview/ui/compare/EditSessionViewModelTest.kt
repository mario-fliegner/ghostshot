// path: app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt
package com.isardomains.sameview.ui.compare

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.Calendar
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.io.IOException

/**
 * Unit tests for [EditSessionViewModel] — Block B: initial state loading.
 *
 * Uses [StandardTestDispatcher] so the init coroutine is queued but not started during
 * ViewModel construction. This lets tests override [EditSessionViewModel.ioDispatcher] and
 * [EditSessionViewModel.metadataReader] before [advanceUntilIdle] triggers the load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditSessionViewModelTest {

    private companion object {
        const val TEST_SESSION_ID = "2026-06-01_12-00-00"
    }

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Creates a ViewModel with [sessionId] and overrides [ioDispatcher], [metadataReader], and
     * storage updater lambdas before the init coroutine has a chance to run.
     *
     * The init coroutine is queued on [testDispatcher] (via viewModelScope / Dispatchers.Main)
     * but not started yet. Property reads inside the coroutine pick up the overridden values
     * when [advanceUntilIdle] eventually runs it.
     */
    private fun createViewModel(
        sessionId: String = TEST_SESSION_ID,
        titleUpdater: (File, String, String?) -> Boolean = { _, _, _ -> true },
        referenceDateUpdater: (File, String, String?) -> Boolean = { _, _, _ -> true },
        locationUpdater: (File, String, String?, String?, String?) -> Boolean = { _, _, _, _, _ -> true },
        reader: (File, String) -> InitialSessionFields = { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
    ): EditSessionViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val context: Context = mock { on { filesDir } doReturn File("/fake/files") }
        val vm = EditSessionViewModel(savedStateHandle, context)
        vm.ioDispatcher = testDispatcher
        vm.metadataReader = reader
        vm.sessionTitleUpdater = titleUpdater
        vm.sessionReferenceDateUpdater = referenceDateUpdater
        vm.sessionLocationUpdater = locationUpdater
        return vm
    }

    // ── Field loading ──────────────────────────────────────────────────────────

    @Test
    fun initialState_titleLoaded_fromMetadata() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields(
                title = "Zugspitze 2026",
                referenceDate = "",
                locationDisplayName = "",
                locationCity = "",
                locationCountry = ""
            )
        }
        advanceUntilIdle()
        assertEquals("Zugspitze 2026", vm.titleField.value)
    }

    @Test
    fun initialState_referenceDate_loaded_fromMetadata() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields(
                title = "",
                referenceDate = "2008-06",
                locationDisplayName = "",
                locationCity = "",
                locationCountry = ""
            )
        }
        advanceUntilIdle()
        assertEquals("2008-06", vm.referenceDateField.value)
    }

    @Test
    fun initialState_locationFields_loaded_fromMetadata() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields(
                title = "",
                referenceDate = "",
                locationDisplayName = "Zugspitze Summit",
                locationCity = "Garmisch-Partenkirchen",
                locationCountry = "Deutschland"
            )
        }
        advanceUntilIdle()
        assertEquals("Zugspitze Summit", vm.locationDisplayNameField.value)
        assertEquals("Garmisch-Partenkirchen", vm.locationCityField.value)
        assertEquals("Deutschland", vm.locationCountryField.value)
    }

    // ── Error / absence handling ───────────────────────────────────────────────

    @Test
    fun initialState_allFieldsEmpty_whenMetadataAbsent() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            throw IOException("metadata.json not found")
        }
        advanceUntilIdle()
        assertEquals("", vm.titleField.value)
        assertEquals("", vm.referenceDateField.value)
        assertEquals("", vm.locationDisplayNameField.value)
        assertEquals("", vm.locationCityField.value)
        assertEquals("", vm.locationCountryField.value)
        // isLoading must be false even when an exception is thrown (finally block)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun initialState_allFieldsEmpty_whenBlocksAbsent() = runTest(testDispatcher) {
        // Reader returns empty strings — simulates a metadata.json with no content,
        // reference, or location blocks.
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        assertEquals("", vm.titleField.value)
        assertEquals("", vm.referenceDateField.value)
        assertEquals("", vm.locationDisplayNameField.value)
        assertEquals("", vm.locationCityField.value)
        assertEquals("", vm.locationCountryField.value)
    }

    // ── Loading state ──────────────────────────────────────────────────────────

    @Test
    fun initialState_isLoading_trueInitially_falseAfterLoad() = runTest(testDispatcher) {
        val vm = createViewModel()

        // With StandardTestDispatcher, viewModelScope.launch{} is queued but not yet started.
        assertTrue("isLoading must be true before the init coroutine runs", vm.isLoading.value)

        advanceUntilIdle()

        assertFalse("isLoading must be false once the metadata read completes", vm.isLoading.value)
    }

    // ── Block C: title field mutation ─────────────────────────────────────────

    @Test
    fun onTitleChanged_updatesState() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onTitleChanged("Zugspitze 2026")
        assertEquals("Zugspitze 2026", vm.titleField.value)
    }

    // ── Block D: reference date field mutation and validation ─────────────────

    @Test
    fun onReferenceDateChanged_clearsPreviousError() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm._referenceDateError.value = "previous error"
        assertEquals("previous error", vm.referenceDateError.value)
        vm.onReferenceDateChanged("2008")
        assertNull(vm.referenceDateError.value)
    }

    @Test
    fun validateReferenceDate_emptyString_isValid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertTrue(vm.isValidReferenceDateInput(""))
    }

    @Test
    fun validateReferenceDate_blankString_isValid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertTrue(vm.isValidReferenceDateInput("   "))
    }

    @Test
    fun validateReferenceDate_yearOnly_isValid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertTrue(vm.isValidReferenceDateInput("2008"))
    }

    @Test
    fun validateReferenceDate_yearMonth_isValid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertTrue(vm.isValidReferenceDateInput("2008-06"))
    }

    @Test
    fun validateReferenceDate_fullDate_isValid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertTrue(vm.isValidReferenceDateInput("2008-06-15"))
    }

    @Test
    fun validateReferenceDate_invalidMonth_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(vm.isValidReferenceDateInput("2008-13"))
    }

    @Test
    fun validateReferenceDate_invalidCalendarDay_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(vm.isValidReferenceDateInput("2008-02-31"))
    }

    @Test
    fun validateReferenceDate_yearBefore1826_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(vm.isValidReferenceDateInput("1825"))
    }

    @Test
    fun validateReferenceDate_yearAfterCurrentYear_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
        assertFalse(vm.isValidReferenceDateInput("$nextYear"))
    }

    @Test
    fun validateReferenceDate_wrongFormat_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(vm.isValidReferenceDateInput("2008/06/15"))
    }

    @Test
    fun validateReferenceDate_singleDigitMonth_isInvalid() = runTest(testDispatcher) {
        val vm = createViewModel()
        assertFalse(vm.isValidReferenceDateInput("2008-6"))
    }

    // ── Block E: location field mutations ────────────────────────────────────

    @Test
    fun onLocationDisplayNameChanged_updatesState() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onLocationDisplayNameChanged("Zugspitze Summit")
        assertEquals("Zugspitze Summit", vm.locationDisplayNameField.value)
    }

    @Test
    fun onLocationCityChanged_updatesState() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onLocationCityChanged("Garmisch-Partenkirchen")
        assertEquals("Garmisch-Partenkirchen", vm.locationCityField.value)
    }

    @Test
    fun onLocationCountryChanged_updatesState() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onLocationCountryChanged("Deutschland")
        assertEquals("Deutschland", vm.locationCountryField.value)
    }

    // ── Block F: isDirty tracking ─────────────────────────────────────────────

    @Test
    fun isDirty_falseInitially() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("Zugspitze 2026", "2008-06", "Summit", "Garmisch", "Deutschland")
        }
        advanceUntilIdle()
        assertFalse(vm.isDirty.value)
    }

    @Test
    fun isDirty_trueAfterTitleChanged() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("initial title", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onTitleChanged("new title")
        assertTrue(vm.isDirty.value)
    }

    @Test
    fun isDirty_trueAfterReferenceDateChanged() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "2008", "", "", "")
        }
        advanceUntilIdle()
        vm.onReferenceDateChanged("2009")
        assertTrue(vm.isDirty.value)
    }

    @Test
    fun isDirty_trueAfterLocationFieldChanged() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "München", "")
        }
        advanceUntilIdle()
        vm.onLocationCityChanged("Berlin")
        assertTrue(vm.isDirty.value)
    }

    @Test
    fun isDirty_falseAfterRevertingToInitialValue() = runTest(testDispatcher) {
        val vm = createViewModel { _, _ ->
            InitialSessionFields("original", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onTitleChanged("changed")
        assertTrue(vm.isDirty.value)
        vm.onTitleChanged("original")
        assertFalse(vm.isDirty.value)
    }

    @Test
    fun isDirty_falseWhenInitialAndCurrentBothBlank() = runTest(testDispatcher) {
        // Initial absent (empty string) + current blank → normalized both null → not dirty.
        val vm = createViewModel { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
        advanceUntilIdle()
        vm.onTitleChanged("   ")
        assertFalse(vm.isDirty.value)
    }

    // ── Block F: onSave — title ───────────────────────────────────────────────

    @Test
    fun onSave_withValidTitle_callsTitleUpdater() = runTest(testDispatcher) {
        var capturedTitle: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old title", "", "", "", "") },
            titleUpdater = { _, _, title -> capturedTitle = title; true }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new title")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("new title", capturedTitle)
    }

    @Test
    fun onSave_withUnchangedTitle_doesNotCallTitleUpdater() = runTest(testDispatcher) {
        var titleUpdaterCalled = false
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("same title", "", "", "", "") },
            titleUpdater = { _, _, _ -> titleUpdaterCalled = true; true }
        )
        advanceUntilIdle()
        // Title unchanged — onSave should not call titleUpdater.
        vm.onSave()
        advanceUntilIdle()
        assertFalse(titleUpdaterCalled)
    }

    @Test
    fun onSave_withBlankTitle_callsTitleUpdaterWithNull() = runTest(testDispatcher) {
        var capturedTitle: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("existing title", "", "", "", "") },
            titleUpdater = { _, _, title -> capturedTitle = title; true }
        )
        advanceUntilIdle()
        vm.onTitleChanged("   ")  // blank → normalized null
        vm.onSave()
        advanceUntilIdle()
        assertNull(capturedTitle)
    }

    // ── Block F: onSave — reference date ──────────────────────────────────────

    @Test
    fun onSave_withValidReferenceDate_callsReferenceDateUpdater() = runTest(testDispatcher) {
        var capturedDate: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "", "", "") },
            referenceDateUpdater = { _, _, date -> capturedDate = date; true }
        )
        advanceUntilIdle()
        vm.onReferenceDateChanged("2008-06")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("2008-06", capturedDate)
    }

    @Test
    fun onSave_withBlankReferenceDate_callsReferenceDateUpdaterWithNull() = runTest(testDispatcher) {
        var capturedDate: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "2008", "", "", "") },
            referenceDateUpdater = { _, _, date -> capturedDate = date; true }
        )
        advanceUntilIdle()
        vm.onReferenceDateChanged("")  // blank → normalized null
        vm.onSave()
        advanceUntilIdle()
        assertNull(capturedDate)
    }

    @Test
    fun onSave_withInvalidReferenceDate_setsError_doesNotCallUpdater() = runTest(testDispatcher) {
        var referenceDateUpdaterCalled = false
        val vm = createViewModel(
            referenceDateUpdater = { _, _, _ -> referenceDateUpdaterCalled = true; true }
        )
        advanceUntilIdle()
        vm.onReferenceDateChanged("not-a-date")
        vm.onSave()
        advanceUntilIdle()
        assertNotNull(vm.referenceDateError.value)
        assertFalse(referenceDateUpdaterCalled)
    }

    // ── Block F: onSave — location ────────────────────────────────────────────

    @Test
    fun onSave_withLocationFields_callsLocationUpdater_withTrimmedValues() = runTest(testDispatcher) {
        var capturedDisplayName: String? = "SENTINEL"
        var capturedCity: String? = "SENTINEL"
        var capturedCountry: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "", "", "") },
            locationUpdater = { _, _, dn, city, country ->
                capturedDisplayName = dn
                capturedCity = city
                capturedCountry = country
                true
            }
        )
        advanceUntilIdle()
        vm.onLocationDisplayNameChanged("  Summit  ")
        vm.onLocationCityChanged("  Garmisch  ")
        vm.onLocationCountryChanged("  Deutschland  ")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("Summit", capturedDisplayName)
        assertEquals("Garmisch", capturedCity)
        assertEquals("Deutschland", capturedCountry)
    }

    @Test
    fun onSave_withAllLocationFieldsBlank_callsLocationUpdater_withNulls() = runTest(testDispatcher) {
        var capturedDisplayName: String? = "SENTINEL"
        var capturedCity: String? = "SENTINEL"
        var capturedCountry: String? = "SENTINEL"
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "existing", "München", "Deutschland") },
            locationUpdater = { _, _, dn, city, country ->
                capturedDisplayName = dn
                capturedCity = city
                capturedCountry = country
                true
            }
        )
        advanceUntilIdle()
        vm.onLocationDisplayNameChanged("")
        vm.onLocationCityChanged("")
        vm.onLocationCountryChanged("")
        vm.onSave()
        advanceUntilIdle()
        assertNull(capturedDisplayName)
        assertNull(capturedCity)
        assertNull(capturedCountry)
    }

    // ── Block F: onSave — events ──────────────────────────────────────────────

    @Test
    fun onSave_success_emitsSaveComplete() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old", "", "", "", "") }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new")
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, events.size)
        assertTrue(events[0] is EditSessionEvent.SaveComplete)
    }

    @Test
    fun onSave_noFieldChanged_emitsSaveComplete_withoutCallingAnyUpdater() = runTest(testDispatcher) {
        var anyUpdaterCalled = false
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("same", "2008", "Place", "City", "Country") },
            titleUpdater = { _, _, _ -> anyUpdaterCalled = true; true },
            referenceDateUpdater = { _, _, _ -> anyUpdaterCalled = true; true },
            locationUpdater = { _, _, _, _, _ -> anyUpdaterCalled = true; true }
        )
        advanceUntilIdle()
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertFalse(anyUpdaterCalled)
        assertEquals(1, events.size)
        assertTrue(events[0] is EditSessionEvent.SaveComplete)
    }

    @Test
    fun onSave_titleUpdaterFails_emitsSaveFailed() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old", "", "", "", "") },
            titleUpdater = { _, _, _ -> false }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new")
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, events.size)
        assertTrue(events[0] is EditSessionEvent.SaveFailed)
    }

    @Test
    fun onSave_referenceDateUpdaterFails_emitsSaveFailed() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "", "", "") },
            referenceDateUpdater = { _, _, _ -> false }
        )
        advanceUntilIdle()
        vm.onReferenceDateChanged("2008")
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, events.size)
        assertTrue(events[0] is EditSessionEvent.SaveFailed)
    }

    @Test
    fun onSave_locationUpdaterFails_emitsSaveFailed() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "", "", "") },
            locationUpdater = { _, _, _, _, _ -> false }
        )
        advanceUntilIdle()
        vm.onLocationCityChanged("München")
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, events.size)
        assertTrue(events[0] is EditSessionEvent.SaveFailed)
    }

    // ── Block F: isDirty after save ───────────────────────────────────────────

    @Test
    fun isDirty_falseAfterSuccessfulSave() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old", "", "", "", "") }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new")
        assertTrue(vm.isDirty.value)
        val job = launch { vm.events.collect { } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertFalse(vm.isDirty.value)
    }

    // ── Block F: storage order ────────────────────────────────────────────────

    @Test
    fun onSave_storageOrderIsTitleThenReferenceDateThenLocation() = runTest(testDispatcher) {
        val callOrder = mutableListOf<String>()
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("", "", "", "", "") },
            titleUpdater = { _, _, _ -> callOrder.add("title"); true },
            referenceDateUpdater = { _, _, _ -> callOrder.add("referenceDate"); true },
            locationUpdater = { _, _, _, _, _ -> callOrder.add("location"); true }
        )
        advanceUntilIdle()
        vm.onTitleChanged("T")
        vm.onReferenceDateChanged("2008")
        vm.onLocationCityChanged("München")
        val job = launch { vm.events.collect { } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("title", "referenceDate", "location"), callOrder)
    }

    // ── Block F: isSaving state ───────────────────────────────────────────────

    @Test
    fun isSaving_falseBeforeAndAfterSave() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old", "", "", "", "") }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new")
        assertFalse(vm.isSaving.value)
        val job = launch { vm.events.collect { } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertFalse(vm.isSaving.value)
    }

    // ── Block F: refreshSavedSessions not called on failure ───────────────────
    // (refreshSavedSessions is a CameraViewModel concern called in MainActivity on
    // SaveComplete. Verified here that SaveFailed is emitted — not SaveComplete — on failure,
    // so the caller knows not to refresh.)

    @Test
    fun onSave_titleUpdaterFails_doesNotEmitSaveComplete() = runTest(testDispatcher) {
        val vm = createViewModel(
            reader = { _, _ -> InitialSessionFields("old", "", "", "", "") },
            titleUpdater = { _, _, _ -> false }
        )
        advanceUntilIdle()
        vm.onTitleChanged("new")
        val events = mutableListOf<EditSessionEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        vm.onSave()
        advanceUntilIdle()
        job.cancel()
        assertFalse(events.any { it is EditSessionEvent.SaveComplete })
    }
}
