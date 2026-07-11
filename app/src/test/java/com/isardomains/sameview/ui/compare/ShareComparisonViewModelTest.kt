package com.isardomains.sameview.ui.compare

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.GlobalBrandingRepository
import com.isardomains.sameview.image.ShareCaptionData
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.image.ShareRenderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ShareComparisonViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    // Shared mock bitmaps — distinct references allow tests to assert identity changes.
    private val mockBitmapA: android.graphics.Bitmap = mock()
    private val mockBitmapB: android.graphics.Bitmap = mock()

    private fun createViewModel(
        globalBrandingRepository: GlobalBrandingRepository = GlobalBrandingRepository(File(tempFolder.root, "branding")),
        metadataReader: suspend (File) -> ShareMetadataSnapshot = { emptySnapshot },
        hqSourceChecker: (File) -> Boolean = { false },
        captureFileResolver: (File) -> File? = { null }
    ): ShareComparisonViewModel {
        val handle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val vm = ShareComparisonViewModel(handle, context, globalBrandingRepository)
        vm.ioDispatcher = Dispatchers.Main
        vm.metadataReader = metadataReader
        vm.hqSourceChecker = hqSourceChecker
        vm.captureFileResolver = captureFileResolver
        // Default: no session branding, no global branding auto-copy.
        vm.previewBitmapFromFile = { null }
        vm.previewBitmapFromBytes = { null }
        vm.sessionBrandingCopier = { _, _, _ -> false }
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("My title", null, 0L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, "München", "Deutschland")
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("Title", "2008", 1000L, null, "City", null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("Grünwald Rathaus", "1958", 1748000000000L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("My Title", null, 0L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            // title = null, but referenceDate + captureTimestampMs → date line computable
            ShareMetadataSnapshot(null, "1958", 1748000000000L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("My Title", "1958", 1748000000000L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            // no title, no referenceDate, captureTimestampMs = 0 → no date either
            ShareMetadataSnapshot(null, null, 0L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot("My Shot", null, 0L, null, null, null)
        })
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
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, "München", "Deutschland")
        })
        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue(vm.isLocationAvailable.value)
        assertNotNull(vm.locationPreviewText.value)
    }

    @Test
    fun loadMetadata_setsViewportRatio() = runTest {
        val vm = createViewModel(metadataReader = { _ ->
            ShareMetadataSnapshot(null, null, 0L, null, null, null, viewportRatio = 1.5f)
        })
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

    // ── P0-2: OutOfMemoryError must not crash — same fallback as any other failure ──

    @Test
    fun onShare_outOfMemoryError_emitsSnackbarEvent() = runTest {
        viewModel.shareRunner = { _, _ -> throw OutOfMemoryError("simulated OOM") }

        viewModel.onShare()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue("OutOfMemoryError must be handled identically to any other render failure — ShowSnackbar, not a crash",
            event is ShareComparisonEvent.ShowSnackbar)
        assertFalse("isRendering must reset to false after an OutOfMemoryError failure", viewModel.isRendering.value)
    }

    @Test
    fun onShare_cancellationException_isRethrown_notReportedAsFailure() = runTest {
        viewModel.shareRunner = { _, _ -> throw kotlinx.coroutines.CancellationException("cancelled") }

        val receivedEvents = mutableListOf<ShareComparisonEvent>()
        val job = launch { viewModel.events.collect { receivedEvents.add(it) } }

        viewModel.onShare()
        advanceUntilIdle()

        assertTrue("CancellationException must be rethrown, not surfaced as a render-failure Snackbar",
            receivedEvents.isEmpty())
        assertFalse("isRendering must still reset to false when the coroutine is cancelled",
            viewModel.isRendering.value)
        job.cancel()
    }

    // ── Branding state — driven by previewBrandingBitmap (single source of truth) ─

    @Test
    fun hasBranding_false_whenNoBitmapLoaded() = runTest {
        viewModel = createViewModel()  // previewBitmapFromFile = { null } by default
        advanceUntilIdle()
        assertFalse(viewModel.hasBranding.value)
        assertNull(viewModel.previewBrandingBitmap.value)
    }

    @Test
    fun hasBranding_true_whenBitmapLoaded() = runTest {
        viewModel = createViewModel()
        viewModel.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        assertTrue(viewModel.hasBranding.value)
        assertNotNull(viewModel.previewBrandingBitmap.value)
    }

    @Test
    fun useBranding_defaultFalse_whenNoBitmapLoaded() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse("useBranding default must be false when no bitmap", viewModel.useBranding.value)
    }

    @Test
    fun useBranding_defaultTrue_whenBitmapLoaded() = runTest {
        viewModel = createViewModel()
        viewModel.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        assertTrue("useBranding must be true when bitmap is loaded", viewModel.useBranding.value)
    }

    @Test
    fun onToggleUseBranding_flipsUseBranding() = runTest {
        viewModel = createViewModel()
        viewModel.previewBitmapFromFile = { mockBitmapA }
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
        viewModel.previewBitmapFromFile = { mockBitmapA }
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
        // previewBitmapFromFile = { null } by default — no bitmap loaded
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
        viewModel.previewBitmapFromFile = { mockBitmapA }
        viewModel.shareRunner = { config, _ ->
            capturedConfig = config
            fakeUri
        }
        advanceUntilIdle()

        viewModel.onToggleUseBranding()
        viewModel.onShare()
        advanceUntilIdle()

        assertFalse("useBranding must be false in config after toggle OFF", capturedConfig!!.useBranding)
    }

    @Test
    fun useBranding_notPersisted_resetsToDefaultOnLoadMetadata() = runTest {
        viewModel = createViewModel()
        viewModel.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        assertTrue(viewModel.useBranding.value)

        viewModel.onToggleUseBranding()
        assertFalse(viewModel.useBranding.value)

        viewModel.loadMetadata()
        advanceUntilIdle()
        assertTrue("useBranding must reset to true after reload when bitmap is present",
            viewModel.useBranding.value)
    }

    // ── Branding × Style regression test ──────────────────────────────────────

    @Test
    fun useBranding_survivesSwitchToSideBySideAndBack() = runTest {
        viewModel = createViewModel()
        viewModel.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        assertTrue("useBranding default true when bitmap present", viewModel.useBranding.value)

        viewModel.onStyleChanged(ShareComparisonStyle.SIDE_BY_SIDE)
        assertTrue("useBranding must survive style switch to Side by side", viewModel.useBranding.value)

        viewModel.onStyleChanged(ShareComparisonStyle.SLIDER)
        assertTrue("useBranding must survive style switch back to Slider", viewModel.useBranding.value)
    }

    // ── brandingVersion — In-Screen Coil Cache Buster ─────────────────────────
    // Sichert ab, dass brandingVersion nach jedem erfolgreichen Schreibvorgang erhöht wird.
    // Ohne diesen Zähler löst das Überschreiben von Logo A durch Logo B keine StateFlow-
    // Emission aus (_hasBranding/_useBranding bleiben true), und Compose recomposiert nicht.
    // Auch wenn Recomposition eintreten würde, liefert Coil den gecachten Bitmap für den
    // unveränderten Dateipfad zurück. brandingVersion dient als memoryCacheKey-Suffix.

    @Test
    fun brandingVersion_initiallyZero() = runTest {
        val vm = createViewModel()
        assertEquals(0, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_increments_afterSuccessfulSymbolOverride() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x01 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        advanceUntilIdle()
        val versionBefore = vm.brandingVersion.value

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.HEART)
        advanceUntilIdle()

        assertEquals("brandingVersion must increment after symbol write", versionBefore + 1, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_increments_afterSuccessfulPhotoOverride() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x02 }
        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        advanceUntilIdle()
        val versionBefore = vm.brandingVersion.value

        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()

        assertEquals("brandingVersion must increment after photo write", versionBefore + 1, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_increments_afterAutoCopyFromGlobal() = runTest {
        val brandingDir = java.io.File(tempFolder.root, "branding").also { it.mkdirs() }
        java.io.File(brandingDir, "handle.png").writeBytes(ByteArray(32))
        java.io.File(brandingDir, "handle-meta.json").writeText("""{"type":"image"}""")
        val globalRepo = com.isardomains.sameview.branding.GlobalBrandingRepository(brandingDir)
        val vm = createViewModel(globalBrandingRepository = globalRepo)
        var copierHasRun2 = false
        vm.sessionBrandingCopier = { _, _, _ -> copierHasRun2 = true; true }
        vm.previewBitmapFromFile = { if (copierHasRun2) mockBitmapA else null }

        advanceUntilIdle()

        assertEquals("brandingVersion must be 1 after auto-copy on first open", 1, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_doesNotIncrement_onWriteFailure() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x03 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> false }
        advanceUntilIdle()
        val versionBefore = vm.brandingVersion.value

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()

        assertEquals("brandingVersion must NOT increment on write failure", versionBefore, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_doesNotIncrement_afterRemove() = runTest {
        // Remove sets previewBrandingBitmap=null; no version bump needed.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> true }
        advanceUntilIdle()
        val versionBefore = vm.brandingVersion.value

        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertEquals("brandingVersion must NOT increment on remove",
            versionBefore, vm.brandingVersion.value)
    }

    @Test
    fun brandingVersion_multipleChanges_emitEachTime() = runTest {
        // Fall 5: Mehrfache Änderungen hintereinander — jede muss die Version erhöhen.
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x04 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        advanceUntilIdle()
        val start = vm.brandingVersion.value

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()
        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()

        assertEquals("brandingVersion must increment once per successful write (3 writes = +3)",
            start + 3, vm.brandingVersion.value)
    }

    // ── sessionBrandingChanged — Library-Refresh-Signal ────────────────────────
    // Sichert ab, dass nach jeder erfolgreichen Branding-Operation das Signal emittiert
    // wird, damit CameraViewModel.refreshSavedSessions() ausgelöst werden kann.

    @Test
    fun sessionBrandingChanged_emitted_afterSuccessfulPhotoSelection() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x01 }
        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()

        assertEquals("sessionBrandingChanged must fire once after photo selection", 1, signals.size)
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_emitted_afterSuccessfulSymbolSelection() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x02 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.HEART)
        advanceUntilIdle()

        assertEquals("sessionBrandingChanged must fire once after symbol selection", 1, signals.size)
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_emitted_afterSuccessfulRemove() = runTest {
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> true }
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertEquals("sessionBrandingChanged must fire once after remove", 1, signals.size)
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_notEmitted_onBrandingWriteFailure() = runTest {
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x03 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> false }  // write fails
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()

        assertTrue("sessionBrandingChanged must NOT fire on write failure", signals.isEmpty())
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_notEmitted_onRemoveFailure() = runTest {
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> false }  // remove fails
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertTrue("sessionBrandingChanged must NOT fire on remove failure", signals.isEmpty())
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_emitted_onAutoCopyFromGlobal_whenSessionHasNoBranding() = runTest {
        // Simulates first-open: session has no branding, global branding exists.
        // GlobalBrandingRepository.getBranding() requires both handle.png AND handle-meta.json.
        val brandingDir = java.io.File(tempFolder.root, "branding").also { it.mkdirs() }
        java.io.File(brandingDir, "handle.png").writeBytes(ByteArray(32))
        java.io.File(brandingDir, "handle-meta.json").writeText("""{"type":"image"}""")
        val globalRepo = com.isardomains.sameview.branding.GlobalBrandingRepository(brandingDir)
        val vm = createViewModel(globalBrandingRepository = globalRepo)
        // First call: session has no branding (null). Second call after copy: mockBitmapA.
        var copierHasRun = false
        vm.sessionBrandingCopier = { _, _, _ -> copierHasRun = true; true }
        vm.previewBitmapFromFile = { if (copierHasRun) mockBitmapA else null }

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        // replay=1 on _sessionBrandingChanged means the collector receives the emission
        // from init's loadMetadata even though the init coroutine ran before this collector.
        advanceUntilIdle()

        assertEquals("sessionBrandingChanged must fire once after auto-copy from global", 1, signals.size)
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_notEmitted_whenNoBrandingAndNoGlobal() = runTest {
        // Session has no branding, no global branding — auto-copy never runs.
        val vm = createViewModel()  // globalRepo has no handle.png in tempFolder
        vm.sessionBrandingCopier = { _, _, _ -> false }

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        vm.loadMetadata()
        advanceUntilIdle()

        assertTrue("sessionBrandingChanged must NOT fire when neither session nor global branding exists",
            signals.isEmpty())
        job.cancel()
    }

    @Test
    fun sessionBrandingChanged_emitsMultipleTimes_forMultipleChanges() = runTest {
        // Fall 5: Mehrfache Änderungen hintereinander — jede muss ein Signal emittieren.
        val vm = createViewModel()
        val fakePng = ByteArray(32) { 0x04 }
        vm.builtinSymbolRenderer = { _ -> fakePng }
        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> fakePng }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.sessionBrandingRemover = { _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapA }
        advanceUntilIdle()

        val signals = mutableListOf<Unit>()
        val job = launch { vm.sessionBrandingChanged.collect { signals.add(it) } }

        // Symbol A
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()
        // Symbol B
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()
        // Photo
        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()
        // Remove
        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertEquals("sessionBrandingChanged must fire once per successful write — 4 writes = 4 signals",
            4, signals.size)
        job.cancel()
    }

    // ── Robust branding state scenarios (previewBrandingBitmap as source of truth) ──
    // These tests verify the full broken sequence described in the bug report.
    // All assertions target previewBrandingBitmap — the single in-memory state that drives
    // both Logo card preview and Share image preview without Coil file-path caching.

    @Test
    fun globalDefault_thenOverride_thenDeleteGlobal_reopenSession_showsSessionOverride() = runTest {
        // Step 1: Open session — auto-copy from global gives bitmapA.
        val vm1 = createViewModel()
        vm1.sessionBrandingCopier = { _, _, _ -> true }
        vm1.previewBitmapFromFile = { mockBitmapA }  // global was copied; load it
        advanceUntilIdle()
        assertEquals("After auto-copy: bitmap must be mockBitmapA", mockBitmapA, vm1.previewBrandingBitmap.value)

        // Step 2: Override with symbol → bitmapB.
        vm1.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm1.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm1.previewBitmapFromBytes = { mockBitmapB }
        vm1.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()
        assertEquals("After override: bitmap must be mockBitmapB", mockBitmapB, vm1.previewBrandingBitmap.value)

        // Step 3: Simulate reopen (new VM). Global is now gone; session file contains override.
        val vm2 = createViewModel()
        vm2.previewBitmapFromFile = { mockBitmapB }  // session file exists with override
        vm2.sessionBrandingCopier = { _, _, _ -> false }  // global gone — copier not called
        vm2.loadMetadata()
        advanceUntilIdle()
        assertEquals("After reopen: must show session override (bitmapB), not stale global",
            mockBitmapB, vm2.previewBrandingBitmap.value)
    }

    @Test
    fun removeSessionLogo_afterGlobalDeleted_resultsInNoLogo() = runTest {
        // Session has a logo (bitmapA). Global is gone. User removes the session logo.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> true }
        advanceUntilIdle()
        assertNotNull("Before remove: must have bitmap", vm.previewBrandingBitmap.value)

        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertNull("After remove: previewBrandingBitmap must be null", vm.previewBrandingBitmap.value)
        assertFalse("After remove: hasBranding must be false", vm.hasBranding.value)
        assertFalse("After remove: useBranding must be false", vm.useBranding.value)
    }

    @Test
    fun chooseNewLogo_afterRemove_updatesLogoCardAndSharePreviewSameState() = runTest {
        // Remove → null → choose symbol → bitmap present again.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> true }
        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapB }
        advanceUntilIdle()

        vm.onRemoveSessionBranding()
        advanceUntilIdle()
        assertNull(vm.previewBrandingBitmap.value)

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()

        assertEquals("After choosing new logo: must show bitmapB", mockBitmapB, vm.previewBrandingBitmap.value)
        assertTrue("hasBranding must be true", vm.hasBranding.value)
        // Both Logo card and Share preview read the same previewBrandingBitmap — no split state.
        assertSame("Single state source: same bitmap object for both surfaces",
            mockBitmapB, vm.previewBrandingBitmap.value)
    }

    @Test
    fun toggleOffOn_afterNewLogo_doesNotRestoreOldLogo() = runTest {
        // Set bitmapA, then override with bitmapB. Toggle OFF/ON must still show bitmapB.
        val vm = createViewModel()
        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapA }
        advanceUntilIdle()

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.HEART)
        advanceUntilIdle()
        assertEquals(mockBitmapA, vm.previewBrandingBitmap.value)

        // Override with bitmapB
        vm.previewBitmapFromBytes = { mockBitmapB }
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()
        assertEquals("After override: bitmapB must be the state", mockBitmapB, vm.previewBrandingBitmap.value)

        // Toggle OFF / ON
        vm.onToggleUseBranding()
        assertFalse(vm.useBranding.value)
        vm.onToggleUseBranding()
        assertTrue(vm.useBranding.value)

        // previewBrandingBitmap unchanged — toggle never modifies the bitmap state
        assertEquals("After toggle cycle: must still show bitmapB, not bitmapA",
            mockBitmapB, vm.previewBrandingBitmap.value)
    }

    @Test
    fun logoCardAndSharePreview_useSameBrandingState() = runTest {
        // Both surfaces receive their branding from previewBrandingBitmap only.
        // This test verifies there is only one state, not two independent sources.
        val vm = createViewModel()
        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapA }
        advanceUntilIdle()

        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.CAMERA)
        advanceUntilIdle()

        val stateForLogoCard = vm.previewBrandingBitmap.value
        val stateForSharePreview = vm.previewBrandingBitmap.value  // same StateFlow
        assertSame("Logo card and Share preview must read from the exact same Bitmap object",
            stateForLogoCard, stateForSharePreview)
        assertNotNull("Both must see non-null bitmap", stateForLogoCard)
    }

    // ── Issue 2: Logo replacement must not modify Show logo toggle ────────────────────────────
    // "Show logo" represents "export with a logo", not "owns a logo".
    // Replacing the logo must not override an explicit user decision to turn the toggle off.

    @Test
    fun choosePhoto_setsUseBrandingTrue_whenFirstLogoAdded() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse("Pre-condition: no branding, useBranding is false", vm.useBranding.value)

        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapA }
        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()

        assertTrue("choosePhoto must set useBranding=true when adding the first logo",
            vm.useBranding.value)
    }

    @Test
    fun choosePhoto_doesNotModifyUseBranding_whenAlreadyEnabled() = runTest {
        // Existing logo loaded; useBranding=true from init. Toggle OFF. Then choose photo.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        assertTrue("Pre-condition: useBranding true from init", vm.useBranding.value)

        vm.onToggleUseBranding()
        assertFalse("Pre-condition: useBranding now false after toggle", vm.useBranding.value)

        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapB }
        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()

        assertFalse("choosePhoto must NOT re-enable Show logo when replacing an existing logo",
            vm.useBranding.value)
    }

    @Test
    fun chooseSymbol_setsUseBrandingTrue_whenFirstLogoAdded() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.useBranding.value)

        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapA }
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()

        assertTrue("chooseSymbol must set useBranding=true when adding the first logo",
            vm.useBranding.value)
    }

    @Test
    fun chooseSymbol_doesNotModifyUseBranding_whenAlreadyEnabled() = runTest {
        // Existing logo loaded; toggle OFF; then choose symbol.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        advanceUntilIdle()
        vm.onToggleUseBranding()
        assertFalse(vm.useBranding.value)

        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.previewBitmapFromBytes = { mockBitmapB }
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()

        assertFalse("chooseSymbol must NOT re-enable Show logo when replacing an existing logo",
            vm.useBranding.value)
    }

    @Test
    fun autoCopyFromGlobal_setsUseBrandingTrue_whenFirstLogoAdded() = runTest {
        // Session starts with no branding; global exists → auto-copy fires at init.
        val brandingDir = java.io.File(tempFolder.root, "branding-ud2").also { it.mkdirs() }
        java.io.File(brandingDir, "handle.png").writeBytes(ByteArray(32))
        java.io.File(brandingDir, "handle-meta.json").writeText("""{"type":"image"}""")
        val globalRepo = com.isardomains.sameview.branding.GlobalBrandingRepository(brandingDir)
        val vm = createViewModel(globalBrandingRepository = globalRepo)
        var copierHasRun = false
        vm.sessionBrandingCopier = { _, _, _ -> copierHasRun = true; true }
        vm.previewBitmapFromFile = { if (copierHasRun) mockBitmapA else null }

        advanceUntilIdle()

        assertTrue("Auto-copy from global must set useBranding=true when adding the first logo",
            vm.useBranding.value)
    }

    // ── Issue 3: Remove logo available when Show logo is OFF (no change; regression guard) ───

    @Test
    fun removeLogoAvailable_whenShowLogoOff() = runTest {
        // Existence check: onRemoveSessionBranding executes correctly when useBranding = false.
        val vm = createViewModel()
        vm.previewBitmapFromFile = { mockBitmapA }
        vm.sessionBrandingRemover = { _, _ -> true }
        advanceUntilIdle()
        assertTrue(vm.hasBranding.value)

        vm.onToggleUseBranding()
        assertFalse("Show logo OFF", vm.useBranding.value)

        vm.onRemoveSessionBranding()
        advanceUntilIdle()

        assertFalse("Remove logo must work when Show logo is OFF: hasBranding must be false",
            vm.hasBranding.value)
        assertNull("previewBrandingBitmap must be null after remove",
            vm.previewBrandingBitmap.value)
    }

    @Test
    fun multipleChanges_neverShowStaleBrandingState() = runTest {
        // Full scenario: A → B → remove → photo.
        val vm = createViewModel()
        vm.builtinSymbolRenderer = { _ -> ByteArray(32) }
        vm.sessionBrandingUpdater = { _, _, _, _, _ -> true }
        vm.sessionBrandingRemover = { _, _ -> true }
        vm.sessionBrandingCopier = { _, _, _ -> true }
        vm.imageDecoder = { _ -> mock() }
        vm.brandingNormalizer = { _ -> ByteArray(32) }

        // Symbol A → bitmapA
        vm.previewBitmapFromBytes = { mockBitmapA }
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.STAR)
        advanceUntilIdle()
        assertEquals("Step A: bitmapA", mockBitmapA, vm.previewBrandingBitmap.value)

        // Symbol B → bitmapB (override A)
        vm.previewBitmapFromBytes = { mockBitmapB }
        vm.onSetSessionBrandingFromSymbol(com.isardomains.sameview.branding.BuiltinBrandingSymbol.FIRE)
        advanceUntilIdle()
        assertEquals("Step B: bitmapB, not stale bitmapA", mockBitmapB, vm.previewBrandingBitmap.value)

        // Remove → null
        vm.onRemoveSessionBranding()
        advanceUntilIdle()
        assertNull("Step remove: null", vm.previewBrandingBitmap.value)

        // Photo → bitmapB
        vm.previewBitmapFromBytes = { mockBitmapB }
        vm.onImageUriSelectedForBranding(fakeUri)
        advanceUntilIdle()
        assertEquals("Step photo: bitmapB, no stale state", mockBitmapB, vm.previewBrandingBitmap.value)
    }

    // ── T-HQ-U-13/14: hqAvailable StateFlow ──────────────────────────────────

    @Test
    fun hqAvailable_trueWhenHqSourceCheckerReturnsTrue() = runTest {
        val vm = createViewModel(hqSourceChecker = { true })
        vm.loadMetadata()
        advanceUntilIdle()
        assertTrue("hqAvailable must be true when checker returns true", vm.hqAvailable.value)
    }

    @Test
    fun hqAvailable_falseWhenHqSourceCheckerReturnsFalse() = runTest {
        val vm = createViewModel(hqSourceChecker = { false })
        vm.loadMetadata()
        advanceUntilIdle()
        assertFalse("hqAvailable must be false when checker returns false", vm.hqAvailable.value)
    }

    @Test
    fun hqAvailable_defaultsFalseBeforeLoadMetadata() {
        val vm = createViewModel(hqSourceChecker = { true })
        // init coroutine is queued but not yet run → should be false
        assertFalse("hqAvailable must default to false before loadMetadata completes",
            vm.hqAvailable.value)
    }

    // ── T-HQ-U-15/16: captureOriginalFile wiring in onShare() ────────────────

    @Test
    fun onShare_withHqAvailable_captureOriginalFileIsNonNull() = runTest {
        val fakeCapOriginal = File(tempFolder.root, "capture-original.jpg")
        fakeCapOriginal.createNewFile()

        var capturedConfig: ShareRenderConfig? = null
        val vm = createViewModel(
            hqSourceChecker = { true },
            captureFileResolver = { fakeCapOriginal }
        )
        vm.loadMetadata()
        advanceUntilIdle()

        vm.shareRunner = { config, _ -> capturedConfig = config; fakeUri }
        vm.onQualityChanged(ShareQuality.ORIGINAL)
        vm.onShare()
        advanceUntilIdle()

        assertNotNull("captureOriginalFile must be non-null when HQ is available",
            capturedConfig?.captureOriginalFile)
        assertEquals("captureOriginalFile must match the resolved file",
            fakeCapOriginal, capturedConfig?.captureOriginalFile)
    }

    @Test
    fun onShare_withHqNotAvailable_captureOriginalFileIsNull() = runTest {
        var capturedConfig: ShareRenderConfig? = null
        val vm = createViewModel(
            hqSourceChecker = { false },
            captureFileResolver = { File(tempFolder.root, "should-not-be-called.jpg") }
        )
        vm.loadMetadata()
        advanceUntilIdle()

        vm.shareRunner = { config, _ -> capturedConfig = config; fakeUri }
        vm.onQualityChanged(ShareQuality.ORIGINAL)
        vm.onShare()
        advanceUntilIdle()

        assertNull("captureOriginalFile must be null when HQ is not available",
            capturedConfig?.captureOriginalFile)
    }

}
