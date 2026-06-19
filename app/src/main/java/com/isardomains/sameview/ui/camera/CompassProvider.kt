package com.isardomains.sameview.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Wrapper around [SensorManager] that delivers normalized compass azimuth values
 * via a callback. Handles TYPE_ROTATION_VECTOR, rotation matrix extraction, display-rotation
 * remapping, and azimuth normalization internally.
 *
 * Lifecycle: call [startUpdates] when the compass should be active, [stopUpdates] to
 * deactivate. Both are exception-safe and no-op when the sensor is unavailable.
 */
open class CompassProvider internal constructor(
    private val sensorManager: SensorManager?
) {

    constructor(context: Context) : this(
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    )

    private var azimuthCallback: ((Float) -> Unit)? = null
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
            val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalized = ((azimuthDegrees + 360f) % 360f)
            azimuthCallback?.invoke(normalized)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /** Returns true when TYPE_ROTATION_VECTOR is available on this device. */
    open fun isAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    /**
     * Registers for sensor updates. Each update delivers a normalized azimuth (0–360°) to
     * [onAzimuthChanged]. Display rotation is read from [displayRotationProvider] on each event
     * to account for device rotation while the sensor is active.
     *
     * No-op if the sensor is unavailable or [sensorManager] is null.
     */
    open fun startUpdates(displayRotationProvider: () -> Int, onAzimuthChanged: (Float) -> Unit) {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        this.displayRotationProvider = displayRotationProvider
        this.azimuthCallback = onAzimuthChanged
        try {
            sensorManager.registerListener(internalListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Exception) { }
    }

    /** Unregisters sensor updates and clears the azimuth callback. Exception-safe. */
    open fun stopUpdates() {
        azimuthCallback = null
        displayRotationProvider = null
        try {
            sensorManager?.unregisterListener(internalListener)
        } catch (_: Exception) { }
    }
}
