package com.isardomains.sameview.ui.settings

import com.isardomains.sameview.ui.camera.GridType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var repository: SettingsRepository
    private lateinit var locationPermissionChecker: LocationPermissionChecker
    private lateinit var gridTypeFlow: MutableStateFlow<GridType>
    private lateinit var keepScreenOnFlow: MutableStateFlow<Boolean>
    private lateinit var recreationGuidanceFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mock()
        locationPermissionChecker = mock()
        gridTypeFlow = MutableStateFlow(GridType.RULE_OF_THIRDS)
        keepScreenOnFlow = MutableStateFlow(true)
        recreationGuidanceFlow = MutableStateFlow(false)
        whenever(repository.gridType).thenReturn(gridTypeFlow)
        whenever(repository.keepScreenOn).thenReturn(keepScreenOnFlow)
        whenever(repository.resetOverlayAfterCapture).thenReturn(MutableStateFlow(false))
        whenever(repository.autoOpenCompareAfterCapture).thenReturn(MutableStateFlow(false))
        whenever(repository.recreationGuidance).thenReturn(recreationGuidanceFlow)
        whenever(locationPermissionChecker.isGranted()).thenReturn(false)
        viewModel = SettingsViewModel(repository, locationPermissionChecker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isRuleOfThirds() {
        assertEquals(GridType.RULE_OF_THIRDS, viewModel.gridType.value)
    }

    @Test
    fun gridType_updatesWhenRepositoryFlowChanges() = runTest {
        val collected = mutableListOf<GridType>()
        val job = launch { viewModel.gridType.collect { collected.add(it) } }

        gridTypeFlow.value = GridType.NONE
        advanceUntilIdle()

        assertEquals(GridType.NONE, collected.last())
        job.cancel()
    }

    @Test
    fun onGridTypeSelected_none_callsRepository() = runTest {
        viewModel.onGridTypeSelected(GridType.NONE)
        advanceUntilIdle()
        verify(repository).setGridType(GridType.NONE)
    }

    @Test
    fun onGridTypeSelected_quarters_callsRepository() = runTest {
        viewModel.onGridTypeSelected(GridType.QUARTERS)
        advanceUntilIdle()
        verify(repository).setGridType(GridType.QUARTERS)
    }

    @Test
    fun onGridTypeSelected_repositoryThrows_doesNotCrashScope() = runTest {
        doThrow(RuntimeException("write failed"))
            .whenever(repository)
            .setGridType(GridType.NONE)

        viewModel.onGridTypeSelected(GridType.NONE)
        advanceUntilIdle()

        assertEquals(GridType.RULE_OF_THIRDS, viewModel.gridType.value)
    }

    @Test
    fun initialKeepScreenOn_isTrue() {
        assertEquals(true, viewModel.keepScreenOn.value)
    }

    @Test
    fun onKeepScreenOnChanged_false_callsRepository() = runTest {
        viewModel.onKeepScreenOnChanged(false)
        advanceUntilIdle()
        verify(repository).setKeepScreenOn(false)
    }

    @Test
    fun onKeepScreenOnChanged_true_callsRepository() = runTest {
        viewModel.onKeepScreenOnChanged(true)
        advanceUntilIdle()
        verify(repository).setKeepScreenOn(true)
    }

    @Test
    fun initialResetOverlayAfterCapture_isFalse() {
        assertEquals(false, viewModel.resetOverlayAfterCapture.value)
    }

    @Test
    fun onResetOverlayAfterCaptureChanged_true_callsRepository() = runTest {
        viewModel.onResetOverlayAfterCaptureChanged(true)
        advanceUntilIdle()
        verify(repository).setResetOverlayAfterCapture(true)
    }

    @Test
    fun onResetOverlayAfterCaptureChanged_false_callsRepository() = runTest {
        viewModel.onResetOverlayAfterCaptureChanged(false)
        advanceUntilIdle()
        verify(repository).setResetOverlayAfterCapture(false)
    }

    @Test
    fun initialAutoOpenCompareAfterCapture_isFalse() {
        assertEquals(false, viewModel.autoOpenCompareAfterCapture.value)
    }

    @Test
    fun onAutoOpenCompareAfterCaptureChanged_true_callsRepository() = runTest {
        viewModel.onAutoOpenCompareAfterCaptureChanged(true)
        advanceUntilIdle()
        verify(repository).setAutoOpenCompareAfterCapture(true)
    }

    @Test
    fun onAutoOpenCompareAfterCaptureChanged_false_callsRepository() = runTest {
        viewModel.onAutoOpenCompareAfterCaptureChanged(false)
        advanceUntilIdle()
        verify(repository).setAutoOpenCompareAfterCapture(false)
    }

    @Test
    fun initialRecreationGuidance_isFalse() {
        assertEquals(false, viewModel.recreationGuidance.value)
    }

    @Test
    fun onRecreationGuidanceOn_withoutPermission_emitsRequestPermissionEvent() = runTest {
        whenever(locationPermissionChecker.isGranted()).thenReturn(false)
        val events = mutableListOf<SettingsUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events.add(it) } }
        runCurrent() // ensure collector is subscribed before event is emitted

        viewModel.onRecreationGuidanceChanged(true)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(SettingsUiEvent.RequestLocationPermission, events.first())
        job.cancel()
    }

    @Test
    fun onRecreationGuidanceOn_withPermissionGranted_savesSettingDirectly() = runTest {
        whenever(locationPermissionChecker.isGranted()).thenReturn(true)

        viewModel.onRecreationGuidanceChanged(true)
        advanceUntilIdle()

        verify(repository).setRecreationGuidance(true)
    }
}
