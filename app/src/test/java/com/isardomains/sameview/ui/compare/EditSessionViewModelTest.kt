// path: app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt
package com.isardomains.sameview.ui.compare

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * Creates a ViewModel with [sessionId] and overrides [ioDispatcher] and [metadataReader]
     * before the init coroutine has a chance to run.
     *
     * The init coroutine is queued on [testDispatcher] (via viewModelScope / Dispatchers.Main)
     * but not started yet. Property reads inside the coroutine pick up the overridden values
     * when [advanceUntilIdle] eventually runs it.
     */
    private fun createViewModel(
        sessionId: String = TEST_SESSION_ID,
        reader: (File, String) -> InitialSessionFields = { _, _ ->
            InitialSessionFields("", "", "", "", "")
        }
    ): EditSessionViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val context: Context = mock { on { filesDir } doReturn File("/fake/files") }
        val vm = EditSessionViewModel(savedStateHandle, context)
        vm.ioDispatcher = testDispatcher
        vm.metadataReader = reader
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
}
