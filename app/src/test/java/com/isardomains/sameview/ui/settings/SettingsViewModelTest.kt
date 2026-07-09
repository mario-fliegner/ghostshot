package com.isardomains.sameview.ui.settings

import android.content.Context
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.branding.GlobalBrandingRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: SettingsRepository
    private lateinit var locationPermissionChecker: LocationPermissionChecker
    private lateinit var gridTypeFlow: MutableStateFlow<GridType>
    private lateinit var keepScreenOnFlow: MutableStateFlow<Boolean>
    private lateinit var recreationGuidanceFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: SettingsViewModel
    private lateinit var brandingRepository: GlobalBrandingRepository
    private val mockContext: Context = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mock()
        locationPermissionChecker = mock()
        brandingRepository = GlobalBrandingRepository(File(tempFolder.root, "branding"))
        gridTypeFlow = MutableStateFlow(GridType.RULE_OF_THIRDS)
        keepScreenOnFlow = MutableStateFlow(true)
        recreationGuidanceFlow = MutableStateFlow(false)
        whenever(repository.gridType).thenReturn(gridTypeFlow)
        whenever(repository.keepScreenOn).thenReturn(keepScreenOnFlow)
        whenever(repository.resetOverlayAfterCapture).thenReturn(MutableStateFlow(false))
        whenever(repository.autoOpenCompareAfterCapture).thenReturn(MutableStateFlow(false))
        whenever(repository.recreationGuidance).thenReturn(recreationGuidanceFlow)
        whenever(repository.liveDirectionArrow).thenReturn(MutableStateFlow(false))
        whenever(repository.stripOriginalsMetadata).thenReturn(MutableStateFlow(false))
        whenever(locationPermissionChecker.isGranted()).thenReturn(false)
        viewModel = SettingsViewModel(repository, locationPermissionChecker, brandingRepository, mockContext)
            .also { it.ioDispatcher = UnconfinedTestDispatcher() }
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

    @Test
    fun isLocationPermissionGranted_true_whenCheckerReturnsTrue() {
        whenever(locationPermissionChecker.isGranted()).thenReturn(true)

        assertTrue(viewModel.isLocationPermissionGranted())
    }

    @Test
    fun isLocationPermissionGranted_false_whenCheckerReturnsFalse() {
        whenever(locationPermissionChecker.isGranted()).thenReturn(false)

        assertFalse(viewModel.isLocationPermissionGranted())
    }

    @Test
    fun stripOriginalsMetadata_defaultIsFalse() {
        assertEquals(false, viewModel.stripOriginalsMetadata.value)
    }

    @Test
    fun onStripOriginalsMetadataChanged_true_callsRepository() = runTest {
        viewModel.onStripOriginalsMetadataChanged(true)
        advanceUntilIdle()
        verify(repository).setStripOriginalsMetadata(true)
    }

    @Test
    fun onStripOriginalsMetadataChanged_false_callsRepository() = runTest {
        viewModel.onStripOriginalsMetadataChanged(false)
        advanceUntilIdle()
        verify(repository).setStripOriginalsMetadata(false)
    }

    @Test
    fun stripOriginalsMetadata_updatesWhenRepositoryFlowChanges() = runTest {
        val stripFlow = MutableStateFlow(false)
        whenever(repository.stripOriginalsMetadata).thenReturn(stripFlow)
        viewModel = SettingsViewModel(repository, locationPermissionChecker, brandingRepository, mockContext)
            .also { it.ioDispatcher = UnconfinedTestDispatcher() }

        val collected = mutableListOf<Boolean>()
        val job = launch { viewModel.stripOriginalsMetadata.collect { collected.add(it) } }

        stripFlow.value = true
        advanceUntilIdle()

        assertEquals(true, collected.last())
        job.cancel()
    }

    // ── Branding tests ────────────────────────────────────────────────────────

    @Test
    fun hasBranding_false_initiallyWhenNoBrandingSet() {
        assertFalse(viewModel.hasBranding.value)
    }

    @Test
    fun onSetBrandingFromSymbol_updatesBrandingAndSetsHasBrandingTrue() = runTest {
        val fakePng = ByteArray(64) { 0x01 }
        viewModel.builtinSymbolRenderer = { fakePng }

        viewModel.onSetBrandingFromSymbol(BuiltinBrandingSymbol.HEART)
        advanceUntilIdle()

        assertTrue("hasBranding must be true after setting branding", viewModel.hasBranding.value)
        assertTrue("Global branding repository must have branding set", brandingRepository.hasBranding())
        assertEquals("builtin", brandingRepository.getBrandingMeta()?.type)
        assertEquals("heart", brandingRepository.getBrandingMeta()?.builtinId)
    }

    @Test
    fun onSetBrandingFromImage_updatesBrandingAndSetsHasBrandingTrue() = runTest {
        val fakePng = ByteArray(64) { 0x02 }
        viewModel.imageDecoder = { mock() }
        viewModel.brandingNormalizer = { fakePng }

        viewModel.onImageUriSelected(mock())
        advanceUntilIdle()

        assertTrue("hasBranding must be true after image branding", viewModel.hasBranding.value)
        assertTrue(brandingRepository.hasBranding())
        assertEquals("image", brandingRepository.getBrandingMeta()?.type)
    }

    @Test
    fun onSetBrandingFromSymbol_rendererThrows_emitsBrandingLoadFailed() = runTest {
        viewModel.builtinSymbolRenderer = { throw RuntimeException("render failed") }

        val events = mutableListOf<SettingsUiEvent>()
        val job = launch(Dispatchers.Main.immediate) { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.onSetBrandingFromSymbol(BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()

        assertTrue("BrandingLoadFailed event must be emitted", events.any { it is SettingsUiEvent.BrandingLoadFailed })
        assertFalse("hasBranding must remain false on error", viewModel.hasBranding.value)
        job.cancel()
    }

    @Test
    fun onRemoveBranding_setsHasBrandingFalse() = runTest {
        val fakePng = ByteArray(64) { 0x03 }
        viewModel.builtinSymbolRenderer = { fakePng }
        viewModel.onSetBrandingFromSymbol(BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()
        assertTrue(viewModel.hasBranding.value)

        viewModel.onRemoveBranding()
        advanceUntilIdle()

        assertFalse("hasBranding must be false after removeBranding", viewModel.hasBranding.value)
        assertFalse("Global branding repository must have no branding", brandingRepository.hasBranding())
    }
}
