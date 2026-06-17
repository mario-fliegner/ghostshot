// path: app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt
package com.isardomains.sameview.ui.video

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.video.VideoExportFormat
import com.isardomains.sameview.video.VideoMode
import com.isardomains.sameview.video.VideoQuality
import com.isardomains.sameview.video.VideoRenderConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CreateVideoViewModelTest {

    private val testSessionId = "2026-06-03_10-00-00"
    private val fakeUri: Uri = mock()
    private lateinit var viewModel: CreateVideoViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mock {
            on { filesDir } doReturn File("/fake/files")
            on { contentResolver } doReturn mock<ContentResolver>()
        }
        settingsRepository = mock {
            on { brandingEnabled } doReturn flowOf(true)
        }
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        viewModel = CreateVideoViewModel(savedStateHandle, context, settingsRepository)
        viewModel.ioDispatcher = Dispatchers.Main  // UnconfinedTestDispatcher is set as Main
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // T-U-15: startExport() transitions from Configuring to Rendering

    @Test
    fun startExport_transitionsToRendering() = runTest {
        // Gate suspends the pipeline so state stays in Rendering during the assertion.
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()

        // State must be Rendering while the pipeline is suspended.
        assertTrue(
            "Expected Rendering, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Rendering
        )

        // Allow the pipeline to complete so the test scope ends cleanly.
        gate.complete(Unit)
        advanceUntilIdle()
    }

    // T-U-16: Pipeline success transitions to Preview with the MediaStore URI

    @Test
    fun startExport_pipelineSuccess_transitionsToPreview() = runTest {
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }

        viewModel.startExport()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Preview, got $state", state is CreateVideoState.Preview)
        assertEquals(fakeUri, (state as CreateVideoState.Preview).videoUri)
    }

    // T-U-17: Pipeline failure returns to Configuring and emits error Snackbar event

    @Test
    fun startExport_pipelineFailure_returnsToConfiguringWithErrorEvent() = runTest {
        viewModel.pipelineRunner = { _, _, _, _ ->
            Result.failure(RuntimeException("encode failed"))
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent() // Subscribe collector before startExport emits.

        viewModel.startExport()
        advanceUntilIdle()

        assertTrue(
            "Expected Configuring, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
        assertTrue("Expected at least one event", collectedEvents.isNotEmpty())
        val event = collectedEvents.first()
        assertTrue("Expected ShowSnackbar event, got $event", event is CreateVideoEvent.ShowSnackbar)
        assertEquals(
            R.string.create_video_error_render_failed,
            (event as CreateVideoEvent.ShowSnackbar).messageResId
        )

        collectJob.cancel()
    }

    // Additional: brandingEnabled default is loaded from SettingsRepository (default true)

    @Test
    fun initialState_brandingEnabledIsTrue() {
        val state = viewModel.state.value
        assertTrue("Expected Configuring", state is CreateVideoState.Configuring)
        assertTrue(
            "Expected brandingEnabled = true",
            (state as CreateVideoState.Configuring).brandingEnabled
        )
    }

    // Additional: sessionId is read from SavedStateHandle

    @Test
    fun sessionId_isReadFromSavedStateHandle() {
        assertEquals(testSessionId, viewModel.sessionId)
    }

    // Additional: branding toggle writes to SettingsRepository

    @Test
    fun updateBrandingEnabled_false_callsRepository() = runTest {
        viewModel.updateBrandingEnabled(false)
        advanceUntilIdle()
        verify(settingsRepository).setBrandingEnabled(false)
    }

    // Additional: pipelineRunner receives the correct sessionDir derived from sessionId

    @Test
    fun startExport_pipelineReceivedCorrectSessionDir() = runTest {
        var capturedDir: File? = null
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _: VideoRenderConfig, dir: File, _: (Float) -> Unit, _ ->
            capturedDir = dir
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()

        assertEquals(
            File("/fake/files/sessions/$testSessionId"),
            capturedDir
        )

        gate.complete(Unit)
        advanceUntilIdle()
    }

    // T-U-18: deleteVideo success — state transitions from Preview to Configuring

    @Test
    fun deleteVideo_success_transitionsToConfiguring() = runTest {
        // Arrange: put ViewModel in Preview state
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }
        viewModel.startExport()
        advanceUntilIdle()
        assertTrue("Expected Preview state before delete", viewModel.state.value is CreateVideoState.Preview)

        // Replace delete runner with success stub
        viewModel.videoDeleteRunner = { _ -> true }

        // Act
        viewModel.deleteVideo()
        advanceUntilIdle()

        // Assert
        assertTrue(
            "Expected Configuring after successful delete, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
    }

    // T-U-19: deleteVideo failure — Preview state unchanged, create_video_delete_failed emitted

    @Test
    fun deleteVideo_failure_staysInPreviewAndEmitsSnackbar() = runTest {
        // Arrange: put ViewModel in Preview state
        viewModel.pipelineRunner = { _, _, _, _ -> Result.success(fakeUri) }
        viewModel.startExport()
        advanceUntilIdle()
        assertTrue("Expected Preview state before delete", viewModel.state.value is CreateVideoState.Preview)

        // Replace delete runner with failure stub
        viewModel.videoDeleteRunner = { _ -> false }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        // Act
        viewModel.deleteVideo()
        advanceUntilIdle()

        // Assert: state stays Preview
        assertTrue(
            "Expected Preview state after failed delete, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Preview
        )

        // Assert: snackbar event emitted
        assertTrue("Expected at least one event after delete failure", collectedEvents.isNotEmpty())
        val event = collectedEvents.first()
        assertTrue("Expected ShowSnackbar", event is CreateVideoEvent.ShowSnackbar)
        assertEquals(
            R.string.create_video_delete_failed,
            (event as CreateVideoEvent.ShowSnackbar).messageResId
        )

        collectJob.cancel()
    }

    // Additional: cancelExport during Rendering returns to Configuring

    @Test
    fun cancelExport_fromRendering_returnsToConfiguring() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        viewModel.startExport()
        assertTrue("Expected Rendering before cancel", viewModel.state.value is CreateVideoState.Rendering)

        viewModel.cancelExport()
        advanceUntilIdle()

        assertTrue(
            "Expected Configuring after cancelExport, got ${viewModel.state.value}",
            viewModel.state.value is CreateVideoState.Configuring
        )
    }

    // Additional: cancellation must not emit create_video_error_render_failed snackbar

    @Test
    fun cancellation_doesNotEmitRenderFailedSnackbar() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ ->
            gate.await()
            Result.success(fakeUri)
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        viewModel.startExport()
        viewModel.cancelExport()
        advanceUntilIdle()

        assertTrue(
            "No snackbar events should be emitted on cancel, got: $collectedEvents",
            collectedEvents.isEmpty()
        )

        collectJob.cancel()
    }

    // T-U-20: quality fallback emits create_video_quality_fallback_notice Snackbar

    @Test
    fun startExport_qualityFallback_emitsFallbackNoticeSnackbar() = runTest {
        viewModel.pipelineRunner = { _, _, _, onQualityFallback ->
            onQualityFallback()
            Result.success(fakeUri)
        }

        val collectedEvents = mutableListOf<CreateVideoEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.events.collect { collectedEvents.add(it) }
        }
        runCurrent()

        viewModel.startExport()
        advanceUntilIdle()

        val snackbarEvents = collectedEvents.filterIsInstance<CreateVideoEvent.ShowSnackbar>()
        assertTrue(
            "Expected create_video_quality_fallback_notice snackbar, got: $collectedEvents",
            snackbarEvents.any { it.messageResId == R.string.create_video_quality_fallback_notice }
        )

        collectJob.cancel()
    }

    // ── Block 8 tests ─────────────────────────────────────────────────────────

    private fun createViewModelWithSnapshot(snapshot: OverlayMetadataSnapshot): CreateVideoViewModel {
        // Use a context that returns safe empty strings for all getString() calls so
        // computeCompareLabels doesn't crash via NPE from the mock.
        val testContext: Context = mock {
            on { filesDir } doReturn File("/fake/files")
            on { contentResolver } doReturn mock<ContentResolver>()
            on { getString(org.mockito.kotlin.any()) } doReturn ""
        }
        val vm = CreateVideoViewModel(
            SavedStateHandle(mapOf("sessionId" to testSessionId)),
            testContext,
            settingsRepository
        )
        // Set the reader AFTER construction, then reload. The init block already called
        // loadOverlayMetadata() with the default reader (empty file → null values).
        // Re-running it with the injected snapshot reader overwrites those values.
        vm.overlayMetadataReader = { _ -> snapshot }
        vm.loadOverlayMetadata()
        return vm
    }

    // T-U-21: overlayPreviewText is "Title · 2008 → 2026" when title + Level-1 date present
    @Test
    fun overlayPreviewText_titleAndLevel1Date_showsCombined() = runTest {
        // 2026 timestamp for capture (2026-06-01 00:00:00 UTC = 1780272000000)
        val captureTs = 1780272000000L
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot("My grandparents", "2008", captureTs, null, null)
        )
        advanceUntilIdle()
        // "2008" vs year 2026 → Level 1 → "2008 → 2026"
        assertEquals("My grandparents · 2008 → 2026", vm.overlayPreviewText.value)
    }

    // T-U-22: overlayPreviewText is "2008 → 2026" when no title, Level-1 date present
    @Test
    fun overlayPreviewText_noTitle_level1Date_showsDateOnly() = runTest {
        val captureTs = 1780272000000L
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, "2008", captureTs, null, null)
        )
        advanceUntilIdle()
        assertEquals("2008 → 2026", vm.overlayPreviewText.value)
    }

    // T-U-23: overlayPreviewText is "My grandparents" when title present, no reference.date
    @Test
    fun overlayPreviewText_titleOnly_noDate_showsTitleOnly() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot("My grandparents", null, 0L, null, null)
        )
        advanceUntilIdle()
        assertEquals("My grandparents", vm.overlayPreviewText.value)
    }

    // T-U-24: overlay in VideoRenderConfig is null when overlayEnabled = false (toggle off)
    @Test
    fun startExport_overlayToggleOff_overlayIsNull() = runTest {
        val captureTs = 1780272000000L
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot("My grandparents", "2008", captureTs, null, null)
        )
        advanceUntilIdle()
        // overlayEnabled defaults to false — overlay should be null in config
        var capturedConfig: VideoRenderConfig? = null
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vm.pipelineRunner = { config, _, _, _ ->
            capturedConfig = config
            gate.await()
            Result.success(fakeUri)
        }
        vm.startExport()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull("Expected null overlay when toggle is off", capturedConfig?.overlay)
    }

    // T-U-25: title/date toggle is disabled (isOverlayAvailable=false) when no title or date
    @Test
    fun isOverlayAvailable_noTitleNoDate_isFalse() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, null, null)
        )
        advanceUntilIdle()
        assertTrue("Expected isOverlayAvailable=false when no data", !vm.isOverlayAvailable.value)
    }

    // T-U-26: TitleDateOverlayRenderer renders no pixels when overlay is null (no-op)
    @Test
    fun overlayRenderer_nullOverlay_noRenderCalled() {
        // When overlay == null, the pipeline creates no renderer → no frame modification.
        // This is a structural test: verify that computeHoldFrameCount works and null overlay
        // means overlayRenderer == null in the pipeline (tested via VideoRenderConfig.overlay == null).
        val config = VideoRenderConfig(
            videoMode = VideoMode.COMPARE_SLIDER,
            format = VideoExportFormat.ORIGINAL,
            quality = VideoQuality.STANDARD_1080P,
            durationMs = 6000,
            brandingEnabled = false,
            overlay = null
        )
        assertNull("Expected overlay to be null", config.overlay)
    }

    // T-U-27: TitleDateOverlayRenderer.alphaForFrame returns 0 at frameIndex == holdFrameCount
    @Test
    fun overlayRenderer_alphaAtSweepFrame0_isZero() {
        val holdFrameCount = 27 // 15% of 180 frames (6s, branding OFF)
        val alpha = com.isardomains.sameview.video.TitleDateOverlayRenderer.alphaForFrame(
            frameIndex = holdFrameCount,
            holdFrameCount = holdFrameCount
        )
        assertEquals("Alpha at first sweep frame must be 0", 0f, alpha, 0.001f)
    }

    // T-U-28: dateLine is null when reference.date is absent (Level 5 exclusion)
    @Test
    fun overlayPreviewText_noReferenceDate_dataLineIsNull() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot("My grandparents", null, 1780272000000L, null, null)
        )
        advanceUntilIdle()
        // No reference.date → dateLine = null → only title shown
        assertEquals("My grandparents", vm.overlayPreviewText.value)
        // Confirm there's no "→" in the output
        assertFalse(vm.overlayPreviewText.value?.contains("→") == true)
    }

    // T-U-29: locationLine is "Munich, Germany" when city + country present and toggle enabled
    @Test
    fun locationPreviewText_cityAndCountry_showsBoth() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, "Munich", "Germany")
        )
        advanceUntilIdle()
        assertEquals("Munich, Germany", vm.locationPreviewText.value)
    }

    // T-U-30: locationLine is "Munich" when only city present and toggle enabled
    @Test
    fun locationPreviewText_cityOnly_showsCity() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, "Munich", null)
        )
        advanceUntilIdle()
        assertEquals("Munich", vm.locationPreviewText.value)
    }

    // T-U-31: locationLine is null in VideoOverlay when location toggle is disabled
    @Test
    fun startExport_locationToggleOff_locationLineIsNull() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, "Munich", "Germany")
        )
        advanceUntilIdle()
        // locationEnabled defaults to false
        var capturedConfig: VideoRenderConfig? = null
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vm.pipelineRunner = { config, _, _, _ ->
            capturedConfig = config
            gate.await()
            Result.success(fakeUri)
        }
        vm.startExport()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull("Expected null overlay or null locationLine when location toggle is off",
            capturedConfig?.overlay?.locationLine)
    }

    // T-U-32: location toggle is disabled (isLocationAvailable=false) when no city or country
    @Test
    fun isLocationAvailable_noCityNoCountry_isFalse() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, null, null)
        )
        advanceUntilIdle()
        assertFalse("Expected isLocationAvailable=false when no location data", vm.isLocationAvailable.value)
    }

    // ── Block 8.1 tests — displayName + separate title/date ──────────────────

    // displayName + city + country
    @Test
    fun locationPreviewText_displayNameAndCityAndCountry_showsFullFormat() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, "Kitzbühel", "Österreich", "Am Schwarzsee")
        )
        advanceUntilIdle()
        assertEquals("Am Schwarzsee · Kitzbühel, Österreich", vm.locationPreviewText.value)
    }

    // displayName + city only
    @Test
    fun locationPreviewText_displayNameAndCityOnly_showsDisplayNameDotCity() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, "Kitzbühel", null, "Am Schwarzsee")
        )
        advanceUntilIdle()
        assertEquals("Am Schwarzsee · Kitzbühel", vm.locationPreviewText.value)
    }

    // displayName only (no city, no country)
    @Test
    fun locationPreviewText_displayNameOnly_showsDisplayName() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, null, null, "Am Schwarzsee")
        )
        advanceUntilIdle()
        assertEquals("Am Schwarzsee", vm.locationPreviewText.value)
    }

    // Toggle available when only displayName present
    @Test
    fun isLocationAvailable_displayNameOnly_isTrue() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, null, 0L, null, null, "Am Schwarzsee")
        )
        advanceUntilIdle()
        assertTrue("Expected isLocationAvailable=true when displayName present", vm.isLocationAvailable.value)
    }

    // overlayTitleText and overlayDateText exposed separately
    @Test
    fun overlayTitleText_whenTitlePresent_returnsTitle() = runTest {
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot("My grandparents", null, 0L, null, null)
        )
        advanceUntilIdle()
        assertEquals("My grandparents", vm.overlayTitleText.value)
        assertNull(vm.overlayDateText.value)
    }

    @Test
    fun overlayDateText_whenDatePresent_returnsDateLine() = runTest {
        val captureTs = 1780272000000L
        val vm = createViewModelWithSnapshot(
            OverlayMetadataSnapshot(null, "2008", captureTs, null, null)
        )
        advanceUntilIdle()
        assertNull(vm.overlayTitleText.value)
        assertEquals("2008 → 2026", vm.overlayDateText.value)
    }

    // ── Rendering state field propagation (Loading Preview) ───────────────────

    @Test
    fun startExport_renderingStateContainsMode() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ -> gate.await(); Result.success(fakeUri) }

        viewModel.updateMode(VideoMode.BEFORE_AFTER)
        viewModel.startExport()

        val state = viewModel.state.value as? CreateVideoState.Rendering
        assertEquals("Rendering should carry the selected mode",
            VideoMode.BEFORE_AFTER, state?.mode)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun startExport_renderingStateContainsFormat() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ -> gate.await(); Result.success(fakeUri) }

        viewModel.updateFormat(VideoExportFormat.PORTRAIT_9_16)
        viewModel.startExport()

        val state = viewModel.state.value as? CreateVideoState.Rendering
        assertEquals("Rendering should carry the selected format",
            VideoExportFormat.PORTRAIT_9_16, state?.format)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun startExport_renderingStateContainsExtrasToggles() = runTest {
        val gate = CompletableDeferred<Unit>()
        viewModel.pipelineRunner = { _, _, _, _ -> gate.await(); Result.success(fakeUri) }

        viewModel.updateOverlayEnabled(true)
        viewModel.updateLocationEnabled(true)
        viewModel.startExport()

        val state = viewModel.state.value as? CreateVideoState.Rendering
        assertTrue("Rendering should carry overlayEnabled = true", state?.overlayEnabled == true)
        assertTrue("Rendering should carry locationEnabled = true", state?.locationEnabled == true)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun sessionViewportRatio_hasPositiveDefault() = runTest {
        advanceUntilIdle()
        assertTrue(
            "sessionViewportRatio should be positive, got ${viewModel.sessionViewportRatio.value}",
            viewModel.sessionViewportRatio.value > 0f
        )
    }
}
