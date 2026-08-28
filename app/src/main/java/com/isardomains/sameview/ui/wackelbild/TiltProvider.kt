// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltProvider.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Wrapper around [SensorManager] that delivers device roll (left/right tilt) values via a
 * callback. Handles TYPE_ROTATION_VECTOR, rotation matrix extraction, and display-rotation
 * remapping internally — structurally identical to [com.isardomains.sameview.ui.camera.CompassProvider],
 * kept as a narrow, deliberate duplicate rather than a shared base class since the two providers
 * serve independent products/lifecycles. The only functional difference from CompassProvider is
 * which orientation-angle index is read after [SensorManager.getOrientation] (roll instead of
 * azimuth), and that roll is not normalized to a 0-360 compass heading since a signed left/right
 * value is what the tilt interaction needs.
 *
 * Lifecycle: call [startUpdates] when the tilt interaction should be active, [stopUpdates] to
 * deactivate. Both are exception-safe and no-op when the sensor is unavailable. No motion data is
 * persisted, logged, or transmitted anywhere in this class.
 */
open class TiltProvider internal constructor(
    private val sensorManager: SensorManager?
) {

    constructor(context: Context) : this(
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    )

    private var rollCallback: ((Float) -> Unit)? = null
    private var displayRotationProvider: (() -> Int)? = null

    private val internalListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // getRotationMatrixFromVector is void; fills rotationMatrix in-place
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val adjustedMatrix = FloatArray(9)
            val displayRotation = displayRotationProvider?.invoke() ?: Surface.ROTATION_0
            // remapCoordinateSystem returns boolean; skip rest if remap fails
            val remapped = when (displayRotation) {
                Surface.ROTATION_0 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, adjustedMatrix
                    )
                Surface.ROTATION_90 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, adjustedMatrix
                    )
                Surface.ROTATION_180 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, adjustedMatrix
                    )
                Surface.ROTATION_270 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, adjustedMatrix
                    )
                else ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, adjustedMatrix
                    )
            }
            if (remapped != true) return

            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(adjustedMatrix, orientationAngles)
            val rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            rollCallback?.invoke(rollDegrees)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /** Returns true when TYPE_ROTATION_VECTOR is available on this device. */
    open fun isAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    /**
     * Registers for sensor updates. Each update delivers the current signed roll in degrees to
     * [onRollChanged]. Display rotation is read from [displayRotationProvider] on each event to
     * account for device rotation while the sensor is active.
     *
     * No-op if the sensor is unavailable or [sensorManager] is null.
     */
    open fun startUpdates(displayRotationProvider: () -> Int, onRollChanged: (Float) -> Unit) {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        this.displayRotationProvider = displayRotationProvider
        this.rollCallback = onRollChanged
        try {
            sensorManager.registerListener(internalListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Exception) { }
    }

    /** Unregisters sensor updates and clears the roll callback. Exception-safe. */
    open fun stopUpdates() {
        rollCallback = null
        displayRotationProvider = null
        try {
            sensorManager?.unregisterListener(internalListener)
        } catch (_: Exception) { }
    }
}
