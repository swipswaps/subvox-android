
package com.example.subvox.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One fused motion frame: three linear-acceleration axes, three angular-rate axes,
 * and a four-element rotation vector. Mirrors the three CoreMotion channels used
 * upstream (userAcceleration, rotationRate, attitude).
 */
data class MotionFrame(
    val timestamp: Long,
    val acceleration: FloatArray,
    val rotationRate: FloatArray,
    val rotationVector: FloatArray
)

/**
 * Registers the phone IMU and, when available, the Android Head Tracker HID
 * sensor. SensorManager is the AOSP entry point for hardware sensors.
 *
 * TYPE_HEAD_TRACKER is API 33+ and is “typically not available for apps to use”
 * unless a compatible HID device is present [reference:2]. The Head Tracker HID
 * protocol is available on Android 13+ and exposes a head-tracking device over
 * USB or Bluetooth through the sensor framework [reference:3]. getDefaultSensor
 * returns null when no compatible device is present, so registration is guarded.
 */
class MotionStreamCollector(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyro  = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rot   = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Head Tracker HID is available from API 33; null means no compatible device
    // or the OEM did not enable the dynamic-sensor module [reference:4].
    private val headTracker: Sensor? =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            sm.getDefaultSensor(Sensor.TYPE_HEAD_TRACKER) else null

    val hasHeadTracker: Boolean get() = headTracker != null

    // SharedFlow with DROP_OLDEST keeps the UI responsive under sensor back-pressure.
    private val _flow = MutableSharedFlow<MotionFrame>(0, 1024, BufferOverflow.DROP_OLDEST)
    val motionFlow: SharedFlow<MotionFrame> = _flow.asSharedFlow()

    private var a = FloatArray(3)
    private var g = FloatArray(3)
    private var r = FloatArray(4)
    private var last = 0L

    /**
     * Register all available channels at 10 ms (100 Hz requested). Above 200 Hz
     * HIGH_SAMPLING_RATE_SENSORS would be mandatory; at 100 Hz it is not
     * strictly required but is declared.
     */
    fun startListening() {
        accel?.let { sm.registerListener(this, it, 10_000) }
        gyro?.let  { sm.registerListener(this, it, 10_000) }
        rot?.let   { sm.registerListener(this, it, 10_000) }
        headTracker?.let { sm.registerListener(this, it, 10_000) }
    }
    fun stopListening() = sm.unregisterListener(this)

    /**
     * Fuse events into one frame every ~20 ms. TYPE_HEAD_TRACKER values[] layout
     * is device-dependent; the first six elements are assumed to be
     * [axis-angle xyz, angular velocity xyz] per the HID Custom Value fields
     * [reference:5]. This assumption must be validated on hardware.
     */
    override fun onSensorChanged(e: SensorEvent?) {
        e ?: return
        when (e.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> a = e.values.clone()
            Sensor.TYPE_GYROSCOPE            -> g = e.values.clone()
            Sensor.TYPE_ROTATION_VECTOR      -> r = e.values.clone()
            Sensor.TYPE_HEAD_TRACKER -> {
                if (e.values.size >= 6) {
                    r = floatArrayOf(e.values[0], e.values[1], e.values[2], 0f)
                    g = floatArrayOf(e.values[3], e.values[4], e.values[5])
                }
            }
        }
        if (e.timestamp - last < 20_000_000L) return
        last = e.timestamp
        _flow.tryEmit(MotionFrame(e.timestamp, a, g, r))
    }
    override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
}
