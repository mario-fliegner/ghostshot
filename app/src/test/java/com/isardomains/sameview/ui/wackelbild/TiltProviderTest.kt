// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltProviderTest.kt
package com.isardomains.sameview.ui.wackelbild

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

/** Mirrors CompassProviderTest's exact style for the sibling TiltProvider. */
class TiltProviderTest {

    private val mockSensorManager: SensorManager = mock()
    private val mockSensor: Sensor = mock()

    // --- isAvailable ---

    @Test
    fun isAvailable_returnsTrue_whenSensorExists() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = TiltProvider(mockSensorManager)
        assertTrue(provider.isAvailable())
    }

    @Test
    fun isAvailable_returnsFalse_whenSensorIsNull() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(null)
        val provider = TiltProvider(mockSensorManager)
        assertFalse(provider.isAvailable())
    }

    @Test
    fun isAvailable_returnsFalse_whenSensorManagerIsNull() {
        val provider = TiltProvider(null as SensorManager?)
        assertFalse(provider.isAvailable())
    }

    // --- startUpdates ---

    @Test
    fun startUpdates_registersListener_whenSensorAvailable() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = TiltProvider(mockSensorManager)

        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onRollChanged = {})

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
        val provider = TiltProvider(mockSensorManager)

        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onRollChanged = {})

        verify(mockSensorManager, never()).registerListener(
            any<SensorEventListener>(), any<Sensor>(), any<Int>()
        )
    }

    @Test
    fun startUpdates_doesNotCrash_whenSensorManagerIsNull() {
        val provider = TiltProvider(null as SensorManager?)
        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onRollChanged = {})
    }

    // --- stopUpdates ---

    @Test
    fun stopUpdates_unregistersListener_afterStart() {
        whenever(mockSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR))
            .thenReturn(mockSensor)
        val provider = TiltProvider(mockSensorManager)
        provider.startUpdates(displayRotationProvider = { Surface.ROTATION_0 }, onRollChanged = {})

        provider.stopUpdates()

        verify(mockSensorManager).unregisterListener(any<SensorEventListener>())
    }

    @Test
    fun stopUpdates_doesNotCrash_whenNeverStarted() {
        val provider = TiltProvider(mockSensorManager)
        provider.stopUpdates()
    }

    @Test
    fun stopUpdates_doesNotCrash_whenSensorManagerIsNull() {
        val provider = TiltProvider(null as SensorManager?)
        provider.stopUpdates()
    }
}
