package com.isardomains.sameview.ui.compare

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.isardomains.sameview.R
import com.isardomains.sameview.image.ShareCaptionData
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.image.ShareRenderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ShareComparisonViewModelTest {

    private val testSessionId = "2026-06-21_10-00-00"
    private val fakeUri: Uri = mock()
    private lateinit var viewModel: ShareComparisonViewModel
    private lateinit var context: Context

    private val emptySnapshot = ShareMetadataSnapshot(null, null, 0L, null, null, null)

    @Before
    fun setUp() {
        // StandardTestDispatcher: init coroutine is queued but not run immediately.
        // This allows ioDispatcher and metadataReader to be overridden before execution.
        Dispatchers.setMain(StandardTestDispatcher())
        context = mock {
            on { filesDir } doReturn File("/fake/files")
            on { contentResolver } doReturn mock<ContentResolver>()
            // getString() must return non-null for non-nullable Kotlin parameters in computeDateLine
            on { getString(any()) } doReturn ""
        }
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        metadataReader: suspend (File) -> ShareMetadataSnapshot = { emptySnapshot }
    ): ShareComparisonViewModel {
        val handle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val vm = ShareComparisonViewModel(handle, context)
        // Set dispatcher and reader BEFORE advanceUntilIdle so the queued init coroutine
        // picks up these overrides when it runs (StandardTestDispatcher pattern).
        vm.ioDispatcher = Dispatchers.Main
        vm.metadataReader = metadataReader
        return vm
    }

    // ── T-B3-01: style state updates ──────────────────────────────────────────

    @Test
    fun onStyleChanged_updatesStyleState() = runTest {
        assertEquals(ShareComparisonStyle.SLIDER, viewModel.style.value)
        viewModel.onStyleChanged(ShareComparisonStyle.SIDE_BY_SIDE)
        assertEquals(ShareComparisonStyle.SIDE_BY_SIDE, viewModel.style.value)
    }

    // ── T-B3-02: quality state updates ────────────────────────────────────────

    @Test
    fun onQualityChanged_updatesQualityState() = runTest {
        assertEquals(ShareQuality.STANDARD, viewModel.quality.value)
        viewModel.onQualityChanged(ShareQuality.ORIGINAL)
        assertEquals(ShareQuality.ORIGINAL, viewModel.quality.value)
    }

    // ── Default values ─────────────────────────────────────────────────────────

    @Test
    fun defaults_style_isSlider() {
        assertEquals(ShareComparisonStyle.SLIDER, viewModel.style.value)
    }

    @Test
    fun defaults_quality_isStandard() {
        assertEquals(ShareQuality.STANDARD, viewModel.quality.value)
    }

    @Test
    fun defaults_titleEnabled_isTrue() {
        assertTrue(viewModel.titleEnabled.value)
    }

    @Test
    fun defaults_dateEnabled_isTrue() {
        assertTrue(viewModel.dateEnabled.value)
    }

    @Test
    fun defaults_locationEnabled_isFalse() {
        assertFalse(viewModel.locationEnabled.value)
    }

    // ── T-B3-03: title toggle off → titleLine null ─────────────────────────────

    @Test
    fun titleToggle_offWithAvailableTitle_titleLineIsNull() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My title", null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("Title should be available", vm.isTitleAvailable.value)

        vm.onTitleToggled(false)
        val captionData = vm.buildCaptionData()
        assertNull("titleLine should be null when toggle is off", captionData?.titleLine)
    }

    // ── T-B3-04: location toggle true → locationLine present ──────────────────

    @Test
    fun locationToggle_onWithAvailableLocation_locationLinePresent() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, "München", "Deutschland")
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("Location should be available", vm.isLocationAvailable.value)

        vm.onLocationToggled(true)
        val captionData = vm.buildCaptionData()
        assertNotNull("locationLine should be present when toggle is on and location available",
            captionData?.locationLine)
    }

    // ── T-B3-05: all toggles off → captionData null ────────────────────────────

    @Test
    fun allTogglesOff_captionDataIsNull() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("Title", "2008", 1000L, null, "City", null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        vm.onTitleToggled(false)
        vm.onDateToggled(false)
        vm.onLocationToggled(false)

        val captionData = vm.buildCaptionData()
        assertNull("captionData should be null when all toggles are off", captionData)
    }

    // ── T-B3-06: isRendering transitions true → false ──────────────────────────

    @Test
    fun onShare_renderingTransitionsTrueToFalse() = runTest {
        viewModel.shareRunner = { _, _ ->
            // Simulate successful render
            fakeUri
        }
        viewModel.ioDispatcher = Dispatchers.Main

        assertFalse(viewModel.isRendering.value)
        viewModel.onShare()
        advanceUntilIdle()
        assertFalse("isRendering should return to false after share completes",
            viewModel.isRendering.value)
    }

    // ── Caption data building ──────────────────────────────────────────────────

    @Test
    fun buildCaptionData_noMetadata_returnsNull() = runTest {
        val vm = createViewModel()
        vm.loadMetadata()
        advanceUntilIdle()
        // No metadata: title/date/location all unavailable → caption null
        assertNull(vm.buildCaptionData())
    }

    @Test
    fun buildCaptionData_titleAndDateAvailable_buildsBothLines() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("Grünwald Rathaus", "1958", 1748000000000L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        val captionData = vm.buildCaptionData()
        assertNotNull(captionData)
        assertEquals("Grünwald Rathaus", captionData!!.titleLine)
        assertNotNull(captionData.dateLine)
    }

    // ── sessionId ──────────────────────────────────────────────────────────────

    @Test
    fun sessionId_matchesSavedStateHandle() {
        assertEquals(testSessionId, viewModel.sessionId)
    }

    // ── Metadata loading ───────────────────────────────────────────────────────

    @Test
    fun loadMetadata_withTitle_titleAvailableAndPreviewTextSet() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My Shot", null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue(vm.isTitleAvailable.value)
        assertEquals("My Shot", vm.titlePreviewText.value)
    }

    @Test
    fun loadMetadata_withoutTitle_titleNotAvailable() = runTest {
        val vm = createViewModel()
        vm.loadMetadata()
        advanceUntilIdle()

        assertFalse(vm.isTitleAvailable.value)
        assertNull(vm.titlePreviewText.value)
    }

    @Test
    fun loadMetadata_withLocation_locationAvailable() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, "München", "Deutschland")
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue(vm.isLocationAvailable.value)
        assertNotNull(vm.locationPreviewText.value)
    }

    @Test
    fun loadMetadata_setsViewportRatio() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, null, null, viewportRatio = 1.5f)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertEquals(1.5f, vm.sessionViewportRatio.value, 0.001f)
    }

    // ── Share event emission ───────────────────────────────────────────────────

    @Test
    fun onShare_success_emitsShareReadyEvent() = runTest {
        viewModel.shareRunner = { _, _ -> fakeUri }

        viewModel.onShare()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue("Should emit ShareReady", event is ShareComparisonEvent.ShareReady)
        assertEquals(fakeUri, (event as ShareComparisonEvent.ShareReady).uri)
    }

    @Test
    fun onShare_failure_emitsSnackbarEvent() = runTest {
        viewModel.shareRunner = { _, _ -> throw RuntimeException("render failed") }

        viewModel.onShare()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue("Should emit ShowSnackbar", event is ShareComparisonEvent.ShowSnackbar)
    }
}
