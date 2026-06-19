package com.isardomains.sameview.ui.camera

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CompassProviderTest {

    private val mockSensorManager: SensorManager = mock()
    private val mockSensor: Sensor = mock()

    // --- isAvailable ---

    @Test
    fun isAvailable_returnsTrue_whenSensorExists() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = CompassProvider(mockSensorManager)
        assertTrue(provider.isAvailable())
    }

    @Test
    fun isAvailable_returnsFalse_whenSensorIsNull() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(null)
        val provider = CompassProvider(mockSensorManager)
        assertFalse(provider.isAvailable())
    }

    @Test
    fun isAvailable_returnsFalse_whenSensorManagerIsNull() {
        val provider = CompassProvider(null as SensorManager?)
        assertFalse(provider.isAvailable())
    }

    // --- startUpdates ---

    @Test
    fun startUpdates_registersListener_whenSensorAvailable() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = CompassProvider(mockSensorManager)

        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onAzimuthChanged = {})

        verify(mockSensorManager).registerListener(
            any<SensorEventListener>(),
            eq(mockSensor),
            eq(SensorManager.SENSOR_DELAY_UI)
        )
    }

    @Test
    fun startUpdates_doesNotRegister_whenSensorUnavailable() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(null)
        val provider = CompassProvider(mockSensorManager)

        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onAzimuthChanged = {})

        verify(mockSensorManager, never()).registerListener(
            any<SensorEventListener>(), any<Sensor>(), any<Int>()
        )
    }

    @Test
    fun startUpdates_doesNotCrash_whenSensorManagerIsNull() {
        val provider = CompassProvider(null as SensorManager?)
        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onAzimuthChanged = {})
    }

    // --- stopUpdates ---

    @Test
    fun stopUpdates_unregistersListener_afterStart() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = CompassProvider(mockSensorManager)
        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onAzimuthChanged = {})

        provider.stopUpdates()

        verify(mockSensorManager).unregisterListener(any<SensorEventListener>())
    }

    @Test
    fun stopUpdates_doesNotCrash_whenNeverStarted() {
        val provider = CompassProvider(mockSensorManager)
        provider.stopUpdates()
    }

    @Test
    fun stopUpdates_doesNotCrash_whenSensorManagerIsNull() {
        val provider = CompassProvider(null as SensorManager?)
        provider.stopUpdates()
    }
}
