// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachineTest.kt
package com.isardomains.sameview.ui.wackelbild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltHysteresisStateMachineTest {

    private val machine = TiltHysteresisStateMachine(thresholdDegrees = 12f, rearmDegrees = 6f)

    // --- neutral start ---

    @Test
    fun initialState_isNeutral() {
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }

    @Test
    fun smallDelta_insideThreshold_staysNeutral_noChange() {
        val result = machine.onDeltaDegrees(5f)
        assertEquals(TiltHysteresisResult.NoChange, result)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }

    // --- positive threshold crossing ---

    @Test
    fun positiveDelta_pastThreshold_transitionsTowardCapture() {
        val result = machine.onDeltaDegrees(15f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_CAPTURE), result)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)
    }

    // --- negative threshold crossing ---

    @Test
    fun negativeDelta_pastThreshold_transitionsTowardReference() {
        val result = machine.onDeltaDegrees(-15f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_REFERENCE), result)
        assertEquals(TiltHysteresisState.TOWARD_REFERENCE, machine.state)
    }

    // --- no switch from jitter inside threshold ---

    @Test
    fun repeatedSmallJitter_neverLeavesNeutral() {
        repeat(20) {
            val result = machine.onDeltaDegrees(if (it % 2 == 0) 3f else -3f)
            assertEquals(TiltHysteresisResult.NoChange, result)
        }
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }

    @Test
    fun onceTowardCapture_smallJitterNearThreshold_doesNotRefire() {
        machine.onDeltaDegrees(15f)
        val result = machine.onDeltaDegrees(20f)
        assertEquals(TiltHysteresisResult.NoChange, result)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)
    }

    // --- re-arm behavior ---

    @Test
    fun towardCapture_returningInsideRearmBand_transitionsToNeutral() {
        machine.onDeltaDegrees(15f)
        val result = machine.onDeltaDegrees(2f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.NEUTRAL), result)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }

    @Test
    fun towardCapture_stillOutsideRearmBand_staysTowardCapture_noChange() {
        machine.onDeltaDegrees(15f)
        val result = machine.onDeltaDegrees(8f)
        assertEquals(TiltHysteresisResult.NoChange, result)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)
    }

    @Test
    fun towardReference_returningInsideRearmBand_transitionsToNeutral() {
        machine.onDeltaDegrees(-15f)
        val result = machine.onDeltaDegrees(-2f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.NEUTRAL), result)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }

    // --- transition only after re-arm (never jumps directly to the opposite TOWARD_* state) ---

    @Test
    fun towardCapture_thenLargeNegativeDelta_neverJumpsDirectlyToTowardReference() {
        machine.onDeltaDegrees(15f)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)

        // A single reading that swings hard the other way: the state machine's shape only
        // recognizes the re-arm-band condition (< rearmDegrees), so this transitions to NEUTRAL,
        // never straight to TOWARD_REFERENCE.
        val result = machine.onDeltaDegrees(-15f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.NEUTRAL), result)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)

        val second = machine.onDeltaDegrees(-15f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_REFERENCE), second)
    }

    @Test
    fun fullCycle_neutralToCaptureToNeutralToReference() {
        assertEquals(
            TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_CAPTURE),
            machine.onDeltaDegrees(15f)
        )
        assertEquals(
            TiltHysteresisResult.Transitioned(TiltHysteresisState.NEUTRAL),
            machine.onDeltaDegrees(0f)
        )
        assertEquals(
            TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_REFERENCE),
            machine.onDeltaDegrees(-15f)
        )
    }

    // --- angle wrap ---

    @Test
    fun wrapAngleDegrees_normalizesAboveOneEighty() {
        assertEquals(-170f, TiltHysteresisStateMachine.wrapAngleDegrees(190f), 0.001f)
        assertEquals(-1f, TiltHysteresisStateMachine.wrapAngleDegrees(359f), 0.001f)
        assertEquals(0f, TiltHysteresisStateMachine.wrapAngleDegrees(360f), 0.001f)
    }

    @Test
    fun wrapAngleDegrees_normalizesBelowNegativeOneEighty() {
        assertEquals(170f, TiltHysteresisStateMachine.wrapAngleDegrees(-190f), 0.001f)
        assertEquals(1f, TiltHysteresisStateMachine.wrapAngleDegrees(-359f), 0.001f)
    }

    @Test
    fun deltaCrossingViaWrap_stillCrossesThreshold_towardCapture() {
        // 350 degrees wraps to -10, which is inside the threshold band...
        val insideResult = machine.onDeltaDegrees(350f)
        assertTrue(insideResult is TiltHysteresisResult.NoChange)
        // ...but 190 degrees wraps to -170, clearly past the negative threshold and must fire
        // TOWARD_REFERENCE, proving the wrap is applied before threshold comparison rather than
        // compared on the raw unwrapped value.
        val crossingResult = machine.onDeltaDegrees(190f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_REFERENCE), crossingResult)
    }

    // --- production defaults (no-arg constructor: THRESHOLD_DEGREES = 9f, REARM_DEGREES = 6f) ---

    @Test
    fun productionDefaults_thresholdAndRearmMatchApprovedTuning() {
        val machine = TiltHysteresisStateMachine()

        // 1. Just below the 9-degree threshold: no transition.
        val belowThreshold = machine.onDeltaDegrees(8.9f)
        assertEquals(TiltHysteresisResult.NoChange, belowThreshold)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)

        // 2. Past the 9-degree threshold: transitions toward Capture.
        val pastThreshold = machine.onDeltaDegrees(9.1f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.TOWARD_CAPTURE), pastThreshold)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)

        // 3. Still outside the 6-degree re-arm band: stays TOWARD_CAPTURE, no change.
        val stillOutsideRearm = machine.onDeltaDegrees(7f)
        assertEquals(TiltHysteresisResult.NoChange, stillOutsideRearm)
        assertEquals(TiltHysteresisState.TOWARD_CAPTURE, machine.state)

        // 4. Inside the 6-degree re-arm band: returns to Neutral.
        val insideRearm = machine.onDeltaDegrees(5.9f)
        assertEquals(TiltHysteresisResult.Transitioned(TiltHysteresisState.NEUTRAL), insideRearm)
        assertEquals(TiltHysteresisState.NEUTRAL, machine.state)
    }
}
