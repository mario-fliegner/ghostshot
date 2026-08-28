// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import android.hardware.SensorManager
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        context = mock {
            on { filesDir } doReturn File("/fake/files")
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
    }

    private fun createViewModel(
        tiltProvider: TestTiltProvider = TestTiltProvider(available = true),
        hysteresisStateMachine: TiltHysteresisStateMachine =
            TiltHysteresisStateMachine(thresholdDegrees = 12f, rearmDegrees = 6f),
        metadataReader: ((File) -> WackelbildDateMetadata)? = null,
        locale: Locale = Locale.US
    ): Pair<WackelbildViewModel, TestTiltProvider> {
        val handle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val vm = WackelbildViewModel(
            handle,
            context,
            tiltProvider = tiltProvider,
            displayRotationProvider = { Surface.ROTATION_0 },
            hysteresisStateMachine = hysteresisStateMachine
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
}
