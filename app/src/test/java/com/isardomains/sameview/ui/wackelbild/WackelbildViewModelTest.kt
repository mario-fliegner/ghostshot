// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import android.hardware.SensorManager
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

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
    }

    private fun createViewModel(
        tiltProvider: TestTiltProvider = TestTiltProvider(available = true),
        hysteresisStateMachine: TiltHysteresisStateMachine =
            TiltHysteresisStateMachine(thresholdDegrees = 12f, rearmDegrees = 6f)
    ): Pair<WackelbildViewModel, TestTiltProvider> {
        val handle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val vm = WackelbildViewModel(
            handle,
            context,
            tiltProvider = tiltProvider,
            displayRotationProvider = { Surface.ROTATION_0 },
            hysteresisStateMachine = hysteresisStateMachine
        )
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
}
