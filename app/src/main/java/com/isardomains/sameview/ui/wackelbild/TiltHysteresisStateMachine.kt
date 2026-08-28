// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt
package com.isardomains.sameview.ui.wackelbild

/**
 * Discrete state reached by [TiltHysteresisStateMachine]. [NEUTRAL] means the device is close to
 * its calibrated neutral posture; [TOWARD_REFERENCE]/[TOWARD_CAPTURE] mean a deliberate tilt past
 * the switch threshold has been observed in that direction.
 */
enum class TiltHysteresisState {
    NEUTRAL,
    TOWARD_REFERENCE,
    TOWARD_CAPTURE
}

/** Result of feeding one new delta reading into [TiltHysteresisStateMachine]. */
sealed class TiltHysteresisResult {
    /** The state did not change for this reading (still stabilizing or inside a band). */
    data object NoChange : TiltHysteresisResult()

    /** The state changed to [newState] as a result of this reading. */
    data class Transitioned(val newState: TiltHysteresisState) : TiltHysteresisResult()
}

/**
 * Small pure state machine that turns a stream of angle-delta readings (device roll relative to
 * a calibrated neutral posture) into discrete direct-switch events, with hysteresis so normal
 * hand jitter does not cause repeated switching.
 *
 * No Android framework dependency — plain-JVM unit testable. No continuous progress/animation
 * value is ever produced, only discrete state transitions.
 *
 * From [TiltHysteresisState.TOWARD_REFERENCE] or [TiltHysteresisState.TOWARD_CAPTURE] the machine
 * can only ever transition back to [TiltHysteresisState.NEUTRAL] (the re-arm band) — it never
 * jumps directly to the opposite TOWARD_* state. A caller that needs "re-arm before a new
 * direction can fire" (e.g. after a manual swipe override) gets that guarantee for free from this
 * state shape.
 */
class TiltHysteresisStateMachine(
    private val thresholdDegrees: Float = THRESHOLD_DEGREES,
    private val rearmDegrees: Float = REARM_DEGREES
) {

    var state: TiltHysteresisState = TiltHysteresisState.NEUTRAL
        private set

    /**
     * Feeds one new delta reading (current roll minus calibrated neutral roll, in degrees;
     * any range is accepted — this function performs its own angle-wrap-safe normalization to
     * (-180, 180]).
     */
    fun onDeltaDegrees(rawDeltaDegrees: Float): TiltHysteresisResult {
        val delta = wrapAngleDegrees(rawDeltaDegrees)
        val newState = when (state) {
            TiltHysteresisState.NEUTRAL -> when {
                delta > thresholdDegrees -> TiltHysteresisState.TOWARD_CAPTURE
                delta < -thresholdDegrees -> TiltHysteresisState.TOWARD_REFERENCE
                else -> TiltHysteresisState.NEUTRAL
            }
            // Re-arm band: must return close enough to neutral before this direction can fire
            // again. Never transitions straight to the opposite TOWARD_* state.
            TiltHysteresisState.TOWARD_CAPTURE ->
                if (delta < rearmDegrees) TiltHysteresisState.NEUTRAL else TiltHysteresisState.TOWARD_CAPTURE
            TiltHysteresisState.TOWARD_REFERENCE ->
                if (delta > -rearmDegrees) TiltHysteresisState.NEUTRAL else TiltHysteresisState.TOWARD_REFERENCE
        }
        if (newState == state) return TiltHysteresisResult.NoChange
        state = newState
        return TiltHysteresisResult.Transitioned(newState)
    }

    companion object {
        // TODO(real-device tuning): placeholder switch threshold. Requires validation on
        // physical hardware before release — see DEINWACKELBILD_INTEGRATION_V1.md §8.3.
        const val THRESHOLD_DEGREES = 9f

        // TODO(real-device tuning): placeholder re-arm band. Requires validation on physical
        // hardware before release — see DEINWACKELBILD_INTEGRATION_V1.md §8.3.
        const val REARM_DEGREES = 6f

        /** Normalizes an arbitrary degree delta into (-180, 180]. */
        internal fun wrapAngleDegrees(degrees: Float): Float {
            var wrapped = degrees % 360f
            if (wrapped > 180f) wrapped -= 360f
            if (wrapped <= -180f) wrapped += 360f
            return wrapped
        }
    }
}
