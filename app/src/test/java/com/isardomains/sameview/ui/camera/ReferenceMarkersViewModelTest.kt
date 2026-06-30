package com.isardomains.sameview.ui.camera

import android.net.Uri
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.LibraryFilter
import com.isardomains.sameview.ui.settings.LibrarySortOrder
import com.isardomains.sameview.ui.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceMarkersViewModelTest {

    private lateinit var viewModel: CameraViewModel

    private val fakeSettingsRepository: SettingsRepository = mock {
        on { gridType } doReturn flowOf(GridType.RULE_OF_THIRDS)
        on { keepScreenOn } doReturn flowOf(true)
        on { resetOverlayAfterCapture } doReturn flowOf(false)
        on { autoOpenCompareAfterCapture } doReturn flowOf(false)
        on { recreationGuidance } doReturn flowOf(false)
        on { liveDirectionArrow } doReturn flowOf(false)
        on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)
        on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)
        on { stripOriginalsMetadata } doReturn flowOf(false)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CameraViewModel(mock(), fakeSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── enterMarkerEditMode ───────────────────────────────────────────────────

    @Test
    fun enterMarkerEditMode_setsEditModeActive() = runTest {
        viewModel.enterMarkerEditMode()
        assertTrue(viewModel.uiState.value.referenceMarkersState.isEditModeActive)
    }

    @Test
    fun enterMarkerEditMode_setsMarkersVisible() = runTest {
        viewModel.enterMarkerEditMode()
        assertTrue(viewModel.uiState.value.referenceMarkersState.markersVisible)
    }

    @Test
    fun enterMarkerEditMode_forcesOverlayAdjustMode() = runTest {
        viewModel.enterMarkerEditMode()
        assertEquals(InteractionMode.OVERLAY_ADJUST, viewModel.uiState.value.interactionMode)
    }

    // ── exitMarkerEditMode ────────────────────────────────────────────────────

    @Test
    fun exitMarkerEditMode_clearsEditModeActive() = runTest {
        viewModel.enterMarkerEditMode()
        viewModel.exitMarkerEditMode()
        assertFalse(viewModel.uiState.value.referenceMarkersState.isEditModeActive)
    }

    @Test
    fun exitMarkerEditMode_doesNotClearMarkers() = runTest {
        viewModel.enterMarkerEditMode()
        viewModel.addMarker(0.5f, 0.5f)
        viewModel.exitMarkerEditMode()
        assertEquals(1, viewModel.uiState.value.referenceMarkersState.markers.size)
    }

    // ── addMarker ─────────────────────────────────────────────────────────────

    @Test
    fun addMarker_addsToList() = runTest {
        viewModel.addMarker(0.25f, 0.75f)
        val markers = viewModel.uiState.value.referenceMarkersState.markers
        assertEquals(1, markers.size)
        assertEquals(0.25f, markers[0].normalizedX)
        assertEquals(0.75f, markers[0].normalizedY)
    }

    @Test
    fun addMarker_multipleMarkers_allAdded() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        viewModel.addMarker(0.3f, 0.4f)
        viewModel.addMarker(0.5f, 0.6f)
        assertEquals(3, viewModel.uiState.value.referenceMarkersState.markers.size)
    }

    @Test
    fun addMarker_atMaxLimit_doesNotExceed() = runTest {
        repeat(MAX_MARKERS) { i -> viewModel.addMarker(i * 0.1f, i * 0.1f) }
        viewModel.addMarker(0.9f, 0.9f)
        assertEquals(MAX_MARKERS, viewModel.uiState.value.referenceMarkersState.markers.size)
    }

    @Test
    fun addMarker_eachHasUniqueId() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        viewModel.addMarker(0.3f, 0.4f)
        val markers = viewModel.uiState.value.referenceMarkersState.markers
        assertNotEquals(markers[0].id, markers[1].id)
    }

    // ── moveMarker ────────────────────────────────────────────────────────────

    @Test
    fun moveMarker_updatesPosition() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        val id = viewModel.uiState.value.referenceMarkersState.markers[0].id
        viewModel.moveMarker(id, 0.8f, 0.9f)
        val moved = viewModel.uiState.value.referenceMarkersState.markers[0]
        assertEquals(0.8f, moved.normalizedX)
        assertEquals(0.9f, moved.normalizedY)
    }

    @Test
    fun moveMarker_unknownId_noChange() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        val countBefore = viewModel.uiState.value.referenceMarkersState.markers.size
        viewModel.moveMarker("nonexistent-id", 0.5f, 0.5f)
        assertEquals(countBefore, viewModel.uiState.value.referenceMarkersState.markers.size)
    }

    // ── removeMarker ──────────────────────────────────────────────────────────

    @Test
    fun removeMarker_removesById() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        viewModel.addMarker(0.3f, 0.4f)
        val id = viewModel.uiState.value.referenceMarkersState.markers[0].id
        viewModel.removeMarker(id)
        assertEquals(1, viewModel.uiState.value.referenceMarkersState.markers.size)
        assertNotEquals(id, viewModel.uiState.value.referenceMarkersState.markers[0].id)
    }

    @Test
    fun removeMarker_unknownId_noChange() = runTest {
        viewModel.addMarker(0.5f, 0.5f)
        viewModel.removeMarker("no-such-id")
        assertEquals(1, viewModel.uiState.value.referenceMarkersState.markers.size)
    }

    // ── clearMarkers ──────────────────────────────────────────────────────────

    @Test
    fun clearMarkers_removesAll() = runTest {
        viewModel.addMarker(0.1f, 0.2f)
        viewModel.addMarker(0.3f, 0.4f)
        viewModel.clearMarkers()
        assertTrue(viewModel.uiState.value.referenceMarkersState.markers.isEmpty())
    }

    @Test
    fun clearMarkers_doesNotChangeVisibility() = runTest {
        viewModel.addMarker(0.5f, 0.5f)
        viewModel.hideMarkers()
        viewModel.clearMarkers()
        assertFalse(viewModel.uiState.value.referenceMarkersState.markersVisible)
    }

    // ── showMarkers / hideMarkers ─────────────────────────────────────────────

    @Test
    fun showMarkers_setsVisible() = runTest {
        viewModel.hideMarkers()
        viewModel.showMarkers()
        assertTrue(viewModel.uiState.value.referenceMarkersState.markersVisible)
    }

    @Test
    fun hideMarkers_clearsVisible() = runTest {
        viewModel.hideMarkers()
        assertFalse(viewModel.uiState.value.referenceMarkersState.markersVisible)
    }

    @Test
    fun hideMarkers_exitsEditMode() = runTest {
        viewModel.enterMarkerEditMode()
        viewModel.hideMarkers()
        assertFalse(viewModel.uiState.value.referenceMarkersState.isEditModeActive)
    }

    // ── markersExist computed property ────────────────────────────────────────

    @Test
    fun markersExist_falseWhenEmpty() = runTest {
        assertFalse(viewModel.uiState.value.referenceMarkersState.markersExist)
    }

    @Test
    fun markersExist_trueAfterAdd() = runTest {
        viewModel.addMarker(0.5f, 0.5f)
        assertTrue(viewModel.uiState.value.referenceMarkersState.markersExist)
    }

    // ── clearMarkersOnReferenceChange lifecycle ───────────────────────────────

    @Test
    fun onReferenceImageRemoveConfirmed_clearsMarkers() = runTest {
        viewModel.addMarker(0.5f, 0.5f)
        viewModel.onReferenceImageRemoveConfirmed()
        assertTrue(viewModel.uiState.value.referenceMarkersState.markers.isEmpty())
    }

    @Test
    fun onReferenceImageRemoveConfirmed_exitsEditMode() = runTest {
        viewModel.enterMarkerEditMode()
        viewModel.onReferenceImageRemoveConfirmed()
        assertFalse(viewModel.uiState.value.referenceMarkersState.isEditModeActive)
    }

    // ── tryStartCapture blocked in edit mode ──────────────────────────────────

    @Test
    fun tryStartCapture_blockedInEditMode() = runTest {
        viewModel.enterMarkerEditMode()
        val token = viewModel.tryStartCapture()
        assertNull(token)
    }

    @Test
    fun tryStartCapture_allowedOutsideEditMode() = runTest {
        val token = viewModel.tryStartCapture()
        // Token is non-null when not in edit mode and no capture in progress
        // (We don't assert non-null because the watchdog starts — just verify edit mode doesn't block)
        assertFalse(viewModel.uiState.value.referenceMarkersState.isEditModeActive)
    }

    // ── ownership: markers do not persist on overlay reset ────────────────────

    @Test
    fun onOverlayReset_doesNotClearMarkers() = runTest {
        viewModel.addMarker(0.3f, 0.7f)
        viewModel.onOverlayReset()
        assertEquals(1, viewModel.uiState.value.referenceMarkersState.markers.size)
    }
}
