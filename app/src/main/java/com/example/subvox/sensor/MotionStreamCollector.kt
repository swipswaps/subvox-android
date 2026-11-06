
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

data class MotionFrame(
    val timestamp: Long,
    val acceleration: FloatArray,
    val rotationRate: FloatArray,
    val rotationVector: FloatArray
)

/**
 * Registers the phone IMU and, when available, the Android Head Tracker HID
 * sensor. SensorManager is the AOSP entry point for hardware sensors.
 * TYPE_HEAD_TRACKER is API 33+ and is exposed only when a compatible HID
 * device is present (Head Tracker HID protocol, Android 13+).
 */
class MotionStreamCollector(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyro  = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rot   = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val headTracker: Sensor? =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            sm.getDefaultSensor(Sensor.TYPE_HEAD_TRACKER) else null

    val hasHeadTracker: Boolean get() = headTracker != null

    private val _flow = MutableSharedFlow<MotionFrame>(0, 1024, BufferOverflow.DROP_OLDEST)
    val motionFlow: SharedFlow<MotionFrame> = _flow.asSharedFlow()

    private var a = FloatArray(3)
    private var g = FloatArray(3)
    private var r = FloatArray(4)
    private var last = 0L

    fun startListening() {
        accel?.let { sm.registerListener(this, it, 10_000) }
        gyro?.let  { sm.registerListener(this, it, 10_000) }
        rot?.let   { sm.registerListener(this, it, 10_000) }
        headTracker?.let { sm.registerListener(this, it, 10_000) }
    }
    fun stopListening() = sm.unregisterListener(this)

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
