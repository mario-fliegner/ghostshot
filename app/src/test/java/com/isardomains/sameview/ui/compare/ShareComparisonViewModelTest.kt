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
        // brandingFileChecker defaults to { false } on the VM — override per test if needed.
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
    fun defaults_titleDateEnabled_isTrue() {
        assertTrue(viewModel.titleDateEnabled.value)
    }

    @Test
    fun defaults_locationEnabled_isFalse() {
        assertFalse(viewModel.locationEnabled.value)
    }

    // ── T-B3-03: title+date toggle off → no title/date in caption ────────────

    @Test
    fun titleDateToggle_offWithAvailableData_captionHasNoTitleOrDate() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My title", null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("Title+date should be available", vm.isTitleDateAvailable.value)

        vm.onTitleDateToggled(false)
        val captionData = vm.buildCaptionData()
        // Title is the only content; toggle off → captionData is null
        assertNull("captionData should be null when title+date toggle is off and no location", captionData)
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

        vm.onTitleDateToggled(false)
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
    fun buildCaptionData_titleAndDateAvailable_buildsBothAsSeperateLines() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("Grünwald Rathaus", "1958", 1748000000000L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        val captionData = vm.buildCaptionData()
        assertNotNull(captionData)
        // Title and date must be separate fields — not merged into one string
        assertEquals("Grünwald Rathaus", captionData!!.titleLine)
        assertNotNull("dateLine must be non-null when date is available", captionData.dateLine)
        assertNull(captionData.locationLine)
    }

    // ── Title-only: no date available ─────────────────────────────────────────

    @Test
    fun titleDateToggle_onlyTitleAvailable_showsTitleInPreview() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My Title", null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("isTitleDateAvailable should be true when title present", vm.isTitleDateAvailable.value)
        assertEquals("My Title", vm.titleDatePreviewText.value)

        val captionData = vm.buildCaptionData()
        assertNotNull(captionData)
        assertEquals("My Title", captionData!!.titleLine)
        assertNull("dateLine should be null when no date available", captionData.dateLine)
    }

    // ── Date-only: no title available ─────────────────────────────────────────

    @Test
    fun titleDateToggle_onlyDateAvailable_showsDateInPreview() = runTest {
        val vm = createViewModel { _ ->
            // title = null, but referenceDate + captureTimestampMs → date line computable
            ShareMetadataSnapshot(null, "1958", 1748000000000L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("isTitleDateAvailable should be true when date computable", vm.isTitleDateAvailable.value)
        val preview = vm.titleDatePreviewText.value
        assertNotNull("Preview should be non-null when date available", preview)
        // The date preview must not contain a "·" separator (no title)
        assertFalse("Preview should not contain separator when only date present",
            preview!!.contains(" · "))

        val captionData = vm.buildCaptionData()
        assertNotNull(captionData)
        assertNull("titleLine should be null when no title available", captionData!!.titleLine)
        assertNotNull("dateLine should be non-null when date is available", captionData.dateLine)
    }

    // ── Both title and date available ─────────────────────────────────────────

    @Test
    fun titleDateToggle_bothAvailable_showsCombinedPreviewWithSeparator() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My Title", "1958", 1748000000000L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue(vm.isTitleDateAvailable.value)
        val preview = vm.titleDatePreviewText.value
        assertNotNull(preview)
        assertTrue("Preview should contain separator when both title and date present",
            preview!!.contains(" · "))
        assertTrue("Preview should start with title", preview.startsWith("My Title"))
    }

    // ── Neither title nor date → toggle unavailable ───────────────────────────

    @Test
    fun titleDateToggle_neitherAvailable_isTitleDateAvailableFalse() = runTest {
        val vm = createViewModel { _ ->
            // no title, no referenceDate, captureTimestampMs = 0 → no date either
            ShareMetadataSnapshot(null, null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertFalse("isTitleDateAvailable should be false when neither title nor date present",
            vm.isTitleDateAvailable.value)
        assertNull("titleDatePreviewText should be null", vm.titleDatePreviewText.value)
        // buildCaptionData with title+date toggle ON but unavailable → null caption
        assertNull("captionData should be null", vm.buildCaptionData())
    }

    // ── sessionId ──────────────────────────────────────────────────────────────

    @Test
    fun sessionId_matchesSavedStateHandle() {
        assertEquals(testSessionId, viewModel.sessionId)
    }

    // ── Metadata loading ───────────────────────────────────────────────────────

    @Test
    fun loadMetadata_withTitle_titleDateAvailableAndPreviewTextSet() = runTest {
        val vm = createViewModel { _ ->
            ShareMetadataSnapshot("My Shot", null, 0L, null, null, null)
        }
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue(vm.isTitleDateAvailable.value)
        assertEquals("My Shot", vm.titleDatePreviewText.value)
    }

    @Test
    fun loadMetadata_withoutTitleOrDate_titleDateNotAvailable() = runTest {
        val vm = createViewModel()
        vm.loadMetadata()
        advanceUntilIdle()

        assertFalse(vm.isTitleDateAvailable.value)
        assertNull(vm.titleDatePreviewText.value)
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

    // ── Branding state ────────────────────────────────────────────────────────

    @Test
    fun hasBranding_false_whenBrandingFileNotPresent() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { false }
        advanceUntilIdle()
        assertFalse(viewModel.hasBranding.value)
    }

    @Test
    fun hasBranding_true_whenBrandingFilePresent() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        advanceUntilIdle()
        assertTrue(viewModel.hasBranding.value)
    }

    @Test
    fun useBranding_defaultFalse_whenNoBrandingFile() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { false }
        advanceUntilIdle()
        assertFalse("useBranding default must be false when no branding", viewModel.useBranding.value)
    }

    @Test
    fun useBranding_defaultTrue_whenBrandingFilePresent() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        advanceUntilIdle()
        assertTrue("useBranding default must be true when branding is present", viewModel.useBranding.value)
    }

    @Test
    fun onToggleUseBranding_flipsUseBranding() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        advanceUntilIdle()
        assertTrue(viewModel.useBranding.value)

        viewModel.onToggleUseBranding()
        assertFalse("useBranding must flip to false", viewModel.useBranding.value)

        viewModel.onToggleUseBranding()
        assertTrue("useBranding must flip back to true", viewModel.useBranding.value)
    }

    @Test
    fun onShare_config_containsCorrectUseBranding_whenBrandingPresent() = runTest {
        var capturedConfig: ShareRenderConfig? = null
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        viewModel.shareRunner = { config, _ ->
            capturedConfig = config
            fakeUri
        }
        advanceUntilIdle()

        viewModel.onShare()
        advanceUntilIdle()

        assertNotNull("Config must be captured", capturedConfig)
        assertTrue("useBranding must be true in config", capturedConfig!!.useBranding)
    }

    @Test
    fun onShare_config_useBrandingFalse_whenNoBranding() = runTest {
        var capturedConfig: ShareRenderConfig? = null
        viewModel = createViewModel()
        // brandingFileChecker defaults to { false } — no change needed
        viewModel.shareRunner = { config, _ ->
            capturedConfig = config
            fakeUri
        }
        advanceUntilIdle()

        viewModel.onShare()
        advanceUntilIdle()

        assertNotNull(capturedConfig)
        assertFalse("useBranding must be false in config when no branding", capturedConfig!!.useBranding)
    }

    @Test
    fun onToggleUseBranding_thenOnShare_config_useBrandingFalse() = runTest {
        var capturedConfig: ShareRenderConfig? = null
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        viewModel.shareRunner = { config, _ ->
            capturedConfig = config
            fakeUri
        }
        advanceUntilIdle()

        // Toggle OFF
        viewModel.onToggleUseBranding()
        viewModel.onShare()
        advanceUntilIdle()

        assertFalse("useBranding must be false in config after toggle OFF", capturedConfig!!.useBranding)
    }

    @Test
    fun useBranding_notPersisted_resetsToDefaultOnLoadMetadata() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        advanceUntilIdle()
        assertTrue(viewModel.useBranding.value)

        viewModel.onToggleUseBranding()
        assertFalse(viewModel.useBranding.value)

        // Re-load metadata resets useBranding to hasBranding default
        viewModel.loadMetadata()
        advanceUntilIdle()
        assertTrue("useBranding must reset to hasBranding default after reload", viewModel.useBranding.value)
    }

    // ── Branding × Style regression test ──────────────────────────────────────

    @Test
    fun useBranding_survivesSwitchToSideBySideAndBack() = runTest {
        viewModel = createViewModel()
        viewModel.brandingFileChecker = { true }
        advanceUntilIdle()
        assertTrue("useBranding default true when branding present", viewModel.useBranding.value)

        // Switch to Side by side
        viewModel.onStyleChanged(ShareComparisonStyle.SIDE_BY_SIDE)
        assertTrue("useBranding must survive style switch to Side by side", viewModel.useBranding.value)

        // Switch back to Slider
        viewModel.onStyleChanged(ShareComparisonStyle.SLIDER)
        assertTrue("useBranding must survive style switch back to Slider", viewModel.useBranding.value)
    }
}
