// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import android.hardware.SensorManager
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import com.isardomains.sameview.image.wackelbild.WackelbildDateOverlay
import com.isardomains.sameview.image.wackelbild.WackelbildPrintPair
import com.isardomains.sameview.image.wackelbild.WackelbildPrintResult
import com.isardomains.sameview.net.deinwackelbild.CreateHandoffRequest
import com.isardomains.sameview.net.deinwackelbild.CreateHandoffResponse
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildApiClient
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildResult
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildSlot
import com.isardomains.sameview.net.deinwackelbild.UploadResponse
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class WackelbildViewModelTest {

    private val testSessionId = "2026-06-21_10-00-00"
    private lateinit var context: Context
    private lateinit var tempCacheDir: File

    /** Mirrors CameraViewModelTest's TestCompassProvider pattern for the sibling TiltProvider. */
    private class TestTiltProvider(
        private val available: Boolean = true
    ) : TiltProvider(null as SensorManager?) {
        var started = false
        var stopped = false
        private var callback: ((Float) -> Unit)? = null

        override fun isAvailable(): Boolean = available

        override fun startUpdates(displayRotationProvider: () -> Int, onRollChanged: (Float) -> Unit) {
            started = true
            stopped = false
            callback = onRollChanged
        }

        override fun stopUpdates() {
            stopped = true
            started = false
            callback = null
        }

        fun simulateRoll(rollDegrees: Float) {
            callback?.invoke(rollDegrees)
        }
    }

    /** Counts sweep invocations while delegating to the real implementation, so tests can prove
     * both "sweep ran" (via real filesystem side effects) and "sweep ran exactly once". */
    private class TestTempFileManager(cacheDir: File) : WackelbildTempFileManager(cacheDir) {
        var sweepCallCount = 0
            private set

        override fun sweepStaleOperationDirs() {
            sweepCallCount++
            super.sweepStaleOperationDirs()
        }
    }

    @Before
    fun setUp() {
        tempCacheDir = Files.createTempDirectory("wb-viewmodel-test-").toFile()
        context = mock {
            on { filesDir } doReturn File("/fake/files")
            on { cacheDir } doReturn tempCacheDir
        }
        // StandardTestDispatcher: the date-metadata coroutine launched from WackelbildViewModel's
        // init is queued but not run immediately, mirroring ShareComparisonViewModelTest's own
        // precedent — this lets a test override metadataReader/currentUiLocale before the queued
        // coroutine actually executes via advanceUntilIdle(). Tests that don't care about date
        // state never advance it, so it simply never runs — harmless.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempCacheDir.deleteRecursively()
    }

    private fun createViewModel(
        tiltProvider: TestTiltProvider = TestTiltProvider(available = true),
        hysteresisStateMachine: TiltHysteresisStateMachine =
            TiltHysteresisStateMachine(thresholdDegrees = 12f, rearmDegrees = 6f),
        metadataReader: ((File) -> WackelbildDateMetadata)? = null,
        locale: Locale = Locale.US,
        tempFileManager: WackelbildTempFileManager? = null,
        apiClient: DeinWackelbildApiClient? = null,
        renderPrintPair: (suspend (File, File, WackelbildDateOverlay?) -> WackelbildPrintResult)? = null
    ): Pair<WackelbildViewModel, TestTiltProvider> {
        val handle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val vm = WackelbildViewModel(
            handle,
            context,
            tiltProvider = tiltProvider,
            displayRotationProvider = { Surface.ROTATION_0 },
            hysteresisStateMachine = hysteresisStateMachine,
            tempFileManager = tempFileManager,
            apiClient = apiClient,
            renderPrintPair = renderPrintPair
        )
        vm.currentUiLocale = { locale }
        // Route the metadata read through the same StandardTestDispatcher installed as Main
        // (see setUp()) rather than the real Dispatchers.IO thread pool, so advanceUntilIdle()
        // can deterministically wait for it -- mirrors ShareComparisonViewModelTest's identical
        // `vm.ioDispatcher = Dispatchers.Main` precedent.
        vm.ioDispatcher = Dispatchers.Main
        if (metadataReader != null) {
            vm.metadataReader = metadataReader
        }
        return Pair(vm, tiltProvider)
    }

    // --- initial state ---

    @Test
    fun initialImage_isReference() {
        val (vm, _) = createViewModel()
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    @Test
    fun captureFile_resolvesUnderSessionDir_besideReferenceFile() {
        val (vm, _) = createViewModel()
        assertEquals(File("/fake/files/sessions/$testSessionId/capture.jpg"), vm.captureFile)
        assertEquals(File("/fake/files/sessions/$testSessionId/reference.jpg"), vm.referenceFile)
    }

    // --- swipe / accessibility toggle ---

    @Test
    fun swipe_togglesReferenceToCapture() {
        val (vm, _) = createViewModel()
        vm.onSwipeDetected()
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)
    }

    @Test
    fun swipe_twice_returnsToReference() {
        val (vm, _) = createViewModel()
        vm.onSwipeDetected()
        vm.onSwipeDetected()
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    @Test
    fun accessibilityToggle_togglesImage() {
        val (vm, _) = createViewModel()
        vm.onAccessibilityToggle()
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)
    }

    // --- neutral calibration ---

    @Test
    fun firstSensorReading_afterScreenActive_calibratesNeutralOnly_noImageChange() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(40f)
        assertEquals(40f, vm.neutralRoll!!, 0.001f)
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    @Test
    fun thresholdCrossing_afterCalibration_changesImage() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f) // calibrates neutral
        tilt.simulateRoll(20f) // delta 20 > threshold 12
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)
    }

    @Test
    fun negativeThresholdCrossing_afterCalibration_switchesToReference() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // -> CAPTURE
        tilt.simulateRoll(0f) // back inside re-arm band -> NEUTRAL, no image change
        tilt.simulateRoll(-20f) // -> REFERENCE
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    // --- swipe / sensor arbitration (SWIPE -> neutral/re-arm observed -> new threshold crossing -> sensor resumes) ---

    @Test
    fun swipeOverride_unchangedSensorReading_doesNotUndoSwipe() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // sensor drives to CAPTURE
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)

        vm.onSwipeDetected() // manual swipe -> REFERENCE, override active
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)

        // The device has not moved (still the same tilted reading): no new hysteresis
        // transition fires at all, so the manual choice must not be undone.
        tilt.simulateRoll(20f)
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    @Test
    fun swipeOverride_doesNotClearBeforeNeutralIsReached() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // sensor -> CAPTURE, underlying hysteresis state TOWARD_CAPTURE

        vm.onSwipeDetected() // -> REFERENCE, override active
        assertTrue(vm.swipeOverrideActive)

        // Still no transition (delta stays past rearm band) -- override must remain active.
        tilt.simulateRoll(22f)
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
        assertTrue(vm.swipeOverrideActive)
        assertFalse(vm.neutralObservedSinceOverride)
    }

    @Test
    fun neutralReturn_isObserved_butDoesNotChangeImageYet() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // sensor -> CAPTURE
        vm.onSwipeDetected() // -> REFERENCE, override active

        tilt.simulateRoll(0f) // returns into the re-arm band -> Transitioned(NEUTRAL)
        assertTrue(vm.neutralObservedSinceOverride)
        assertTrue(vm.swipeOverrideActive)
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
    }

    @Test
    fun onlyNewThresholdCrossing_afterNeutralReturn_changesImage_andClearsOverride() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // sensor -> CAPTURE
        vm.onSwipeDetected() // -> REFERENCE, override active
        tilt.simulateRoll(0f) // neutral/re-arm observed
        tilt.simulateRoll(20f) // new threshold crossing -> sensor control resumes

        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)
        assertFalse(vm.swipeOverrideActive)
    }

    // --- lifecycle ---

    @Test
    fun pause_clearsNeutral_stopsSensor() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(10f)
        assertNotNull(vm.neutralRoll)

        vm.onScreenInactive()
        assertNull(vm.neutralRoll)
        assertTrue(tilt.stopped)
        assertFalse(vm.isSensorActive)
    }

    @Test
    fun resume_recalibratesNeutral_fromCurrentPosture() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        tilt.simulateRoll(10f)
        assertEquals(10f, vm.neutralRoll!!, 0.001f)

        vm.onScreenInactive()
        vm.onScreenActive()
        assertNull(vm.neutralRoll) // not yet recalibrated -- awaits the next reading

        tilt.simulateRoll(50f)
        assertEquals(50f, vm.neutralRoll!!, 0.001f)
    }

    @Test
    fun screenLeft_stopsSensor() {
        val (vm, tilt) = createViewModel()
        vm.onScreenActive()
        vm.onScreenLeft()
        assertTrue(tilt.stopped)
        assertFalse(vm.isSensorActive)
    }

    @Test
    fun unavailableSensor_neverStartsUpdates() {
        val (vm, tilt) = createViewModel(tiltProvider = TestTiltProvider(available = false))
        vm.onScreenActive()
        assertFalse(tilt.started)
        assertFalse(vm.isSensorActive)
    }

    @Test
    fun isSensorAvailable_reflectsInjectedProvider() {
        val (available, _) = createViewModel(tiltProvider = TestTiltProvider(available = true))
        val (unavailable, _) = createViewModel(tiltProvider = TestTiltProvider(available = false))
        assertTrue(available.isSensorAvailable)
        assertFalse(unavailable.isSensorAvailable)
    }

    // --- date overlay availability (Reference date only -- capture.timestampMs never gates it) ---

    @Test
    fun dateOverlay_referenceDatePresent_availableTrue() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 1751359200000L) }
        )
        advanceUntilIdle()
        assertTrue(vm.isDateOverlayAvailable.value)
    }

    @Test
    fun dateOverlay_referenceDateMissing_availableFalse() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata(null, 1751359200000L) }
        )
        advanceUntilIdle()
        assertFalse(vm.isDateOverlayAvailable.value)
    }

    @Test
    fun dateOverlay_malformedReferenceDate_availableFalse() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("not-a-date", 1751359200000L) }
        )
        advanceUntilIdle()
        assertFalse(vm.isDateOverlayAvailable.value)
    }

    @Test
    fun dateOverlay_captureTimestampMissing_withValidReference_availabilityRemainsTrue() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 0L) }
        )
        advanceUntilIdle()
        assertTrue(vm.isDateOverlayAvailable.value)
        assertNull(vm.captureDateBadgeText.value)
        assertNotNull(vm.referenceDateBadgeText.value)
    }

    @Test
    fun dateOverlay_defaultState_isOff() {
        val (vm, _) = createViewModel()
        assertFalse(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_enablingWhenAvailable_turnsOn() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 1751359200000L) }
        )
        advanceUntilIdle()
        vm.onDateOverlayToggled(true)
        assertTrue(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_disabling_turnsOff() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 1751359200000L) }
        )
        advanceUntilIdle()
        vm.onDateOverlayToggled(true)
        vm.onDateOverlayToggled(false)
        assertFalse(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_enablingWhenUnavailable_remainsOff() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata(null, 0L) }
        )
        advanceUntilIdle()
        vm.onDateOverlayToggled(true)
        assertFalse(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_doesNotInterfereWithVisibleImageState() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 1751359200000L) }
        )
        advanceUntilIdle()
        vm.onDateOverlayToggled(true)
        vm.onSwipeDetected()
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)
        assertTrue(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_doesNotInterfereWithSwipeTiltArbitration() = runTest {
        val (vm, tilt) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 1751359200000L) }
        )
        advanceUntilIdle()
        vm.onDateOverlayToggled(true)

        vm.onScreenActive()
        tilt.simulateRoll(0f)
        tilt.simulateRoll(20f) // sensor -> CAPTURE
        assertEquals(WackelbildImageSide.CAPTURE, vm.visibleImage.value)

        vm.onSwipeDetected() // -> REFERENCE, override active
        assertEquals(WackelbildImageSide.REFERENCE, vm.visibleImage.value)
        assertTrue(vm.swipeOverrideActive)
        // Date overlay state is untouched by the tilt/swipe arbitration sequence.
        assertTrue(vm.dateOverlayEnabled.value)
    }

    @Test
    fun dateOverlay_metadataReadFailure_doesNotCrash() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { throw RuntimeException("boom") }
        )
        advanceUntilIdle()
        assertFalse(vm.isDateOverlayAvailable.value)
        assertNull(vm.referenceDateBadgeText.value)
        assertNull(vm.captureDateBadgeText.value)
    }

    @Test
    fun dateOverlay_missingCaptureTimestamp_neverInventsCaptureDate() = runTest {
        val (vm, _) = createViewModel(
            metadataReader = { WackelbildDateMetadata("2008-06", 0L) }
        )
        advanceUntilIdle()
        assertNull(vm.captureDateBadgeText.value)
    }

    // --- Block 6: temp-file orphan sweep at fresh ViewModel creation ---

    @Test
    fun init_triggersSweepExactlyOnce() = runTest {
        val tempFileManager = TestTempFileManager(tempCacheDir)
        val (_, _) = createViewModel(tempFileManager = tempFileManager)
        advanceUntilIdle()
        assertEquals(1, tempFileManager.sweepCallCount)
    }

    @Test
    fun init_sweep_removesStaleOperationDirectory() = runTest {
        val realManager = WackelbildTempFileManager(tempCacheDir)
        val staleDir = realManager.createOperationDir("stale-op")
        realManager.referenceCandidateFile(staleDir).writeText("leftover")
        assertTrue(staleDir.exists())

        createViewModel(tempFileManager = TestTempFileManager(tempCacheDir))
        advanceUntilIdle()

        assertFalse(staleDir.exists())
    }

    @Test
    fun init_sweep_doesNotMutateSessionFiles() = runTest {
        // Sentinel placed directly under cacheDir, outside cacheDir/wackelbild/, mirroring the
        // real risk this test guards against: sweep must never escape the dedicated root even
        // for a sibling directory that happens to share a session-shaped name.
        val sessionsDir = File(tempCacheDir, "sessions/$testSessionId")
        sessionsDir.mkdirs()
        val referenceFile = File(sessionsDir, "reference.jpg")
        referenceFile.writeText("persisted session data")

        createViewModel(tempFileManager = TestTempFileManager(tempCacheDir))
        advanceUntilIdle()

        assertTrue(referenceFile.exists())
        assertEquals("persisted session data", referenceFile.readText())
    }

    // --- Block 8: handoff operation orchestration (thin ViewModel-level wiring only --
    // retry/restart/idempotency/semantic-validation matrix is exhaustively covered by
    // WackelbildHandoffOrchestratorTest and deliberately not duplicated here) ---

    private class FakeApiClient(
        private val createResult: DeinWackelbildResult<CreateHandoffResponse>,
        private val uploadOneResult: DeinWackelbildResult<UploadResponse>,
        private val uploadTwoResult: DeinWackelbildResult<UploadResponse>
    ) : DeinWackelbildApiClient {
        override suspend fun createHandoff(request: CreateHandoffRequest, idempotencyKey: String) = createResult

        override suspend fun uploadImage(uploadUrl: String, handoffToken: String, slot: DeinWackelbildSlot, file: File) =
            if (slot == DeinWackelbildSlot.ONE) uploadOneResult else uploadTwoResult
    }

    private fun successfulApiClient(checkoutUrl: String = "https://deinwackelbild.de/checkout/h1"): DeinWackelbildApiClient =
        FakeApiClient(
            createResult = DeinWackelbildResult.Success(
                CreateHandoffResponse(
                    handoffId = "h1", handoffToken = "tok", partner = "sameview", status = "awaiting_files",
                    expiresAt = "2026-08-30T12:00:00Z", maxFileBytes = 20_971_520L, acceptedTypes = listOf("image/jpeg"),
                    uploadedSlots = emptyList(), uploadUrl = "https://deinwackelbild.de/upload/h1", checkoutUrl = null
                )
            ),
            uploadOneResult = DeinWackelbildResult.Success(UploadResponse("h1", "awaiting_files", listOf("one"), null)),
            uploadTwoResult = DeinWackelbildResult.Success(UploadResponse("h1", "ready", listOf("one", "two"), checkoutUrl))
        )

    private fun fakeRenderer(usedFallback: Boolean = false): suspend (File, File, WackelbildDateOverlay?) -> WackelbildPrintResult =
        { _, outputDir, _ ->
            val ref = File(outputDir, "image_one.jpg").also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(1)) }
            val cap = File(outputDir, "image_two.jpg").also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(2)) }
            WackelbildPrintResult.Success(WackelbildPrintPair(ref, cap), usedFallback)
        }

    @Test
    fun operationState_initialValue_isIdle() {
        val (vm, _) = createViewModel()
        assertEquals(WackelbildOperationState.Idle, vm.operationState.value)
    }

    @Test
    fun startOperation_happyPath_updatesStateToReady() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(checkoutUrl = "https://deinwackelbild.de/checkout/h1"),
            renderPrintPair = fakeRenderer(usedFallback = false)
        )
        vm.startOperation()
        advanceUntilIdle()
        assertEquals(
            WackelbildOperationState.Ready("https://deinwackelbild.de/checkout/h1", usedFallback = false),
            vm.operationState.value
        )
    }

    @Test
    fun startOperation_secondStartWhileActive_ignored() = runTest {
        var renderCallCount = 0
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(),
            renderPrintPair = { _, _, _ ->
                renderCallCount++
                awaitCancellation() // hang, so the first operation stays active
            }
        )
        vm.startOperation()
        advanceUntilIdle() // let it reach Preparing and suspend inside the (hanging) renderer
        vm.startOperation() // second call while the first is still active/suspended in the renderer
        advanceUntilIdle()
        // No exception, no crash -- and critically, the renderer (and hence the whole downstream
        // flow) never runs a second time.
        assertEquals(1, renderCallCount)
        assertEquals(WackelbildOperationState.Preparing, vm.operationState.value)
        vm.cancelOperation()
    }

    @Test
    fun fallbackConfirmation_stateExposed_whenRendererUsesFallback() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(),
            renderPrintPair = fakeRenderer(usedFallback = true)
        )
        vm.startOperation()
        advanceUntilIdle()
        assertEquals(WackelbildOperationState.AwaitingFallbackConfirmation, vm.operationState.value)
    }

    @Test
    fun confirmFallbackAndContinue_resumesPendingConfirmation() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(checkoutUrl = "https://deinwackelbild.de/checkout/h1"),
            renderPrintPair = fakeRenderer(usedFallback = true)
        )
        vm.startOperation()
        advanceUntilIdle()
        assertEquals(WackelbildOperationState.AwaitingFallbackConfirmation, vm.operationState.value)

        vm.confirmFallbackAndContinue()
        advanceUntilIdle()

        assertEquals(
            WackelbildOperationState.Ready("https://deinwackelbild.de/checkout/h1", usedFallback = true),
            vm.operationState.value
        )
    }

    @Test
    fun confirmFallbackAndContinue_doubleCallSafe() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(),
            renderPrintPair = fakeRenderer(usedFallback = true)
        )
        vm.startOperation()
        advanceUntilIdle()

        vm.confirmFallbackAndContinue()
        vm.confirmFallbackAndContinue() // must not throw, must not start a second job
        advanceUntilIdle()

        assertTrue(vm.operationState.value is WackelbildOperationState.Ready)
    }

    @Test
    fun cancelOperation_whileAwaitingFallback_resetsToIdle() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(),
            renderPrintPair = fakeRenderer(usedFallback = true)
        )
        vm.startOperation()
        advanceUntilIdle()
        assertEquals(WackelbildOperationState.AwaitingFallbackConfirmation, vm.operationState.value)

        vm.cancelOperation()

        assertEquals(WackelbildOperationState.Idle, vm.operationState.value)
    }

    @Test
    fun cancelOperation_duringActiveOperation_resetsToIdle_noReadyEmittedAfter() = runTest {
        val (vm, _) = createViewModel(
            apiClient = successfulApiClient(),
            renderPrintPair = { _, _, _ -> awaitCancellation() }
        )
        vm.startOperation()
        advanceUntilIdle() // runs until it suspends inside the (hanging) renderer -- reaches Preparing

        vm.cancelOperation()
        advanceUntilIdle()

        assertEquals(WackelbildOperationState.Idle, vm.operationState.value)
    }

    // Note: onCleared() is `protected` (inherited from androidx.lifecycle.ViewModel) and cannot be
    // invoked directly from this unrelated test class -- no test in this codebase does so. Its body
    // is a single operationJob?.cancel() call, structurally identical to half of cancelOperation(),
    // which is exercised by cancelOperation_duringActiveOperation_resetsToIdle_noReadyEmittedAfter
    // and cancelOperation_whileAwaitingFallback_resetsToIdle above.
}
