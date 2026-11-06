
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

data class MotionFrame(val timestamp: Long, val acceleration: FloatArray,
                       val rotationRate: FloatArray, val rotationVector: FloatArray)

class MotionStreamCollector(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyro  = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rot   = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _flow = MutableSharedFlow<MotionFrame>(0, 1024, BufferOverflow.DROP_OLDEST)
    val motionFlow: SharedFlow<MotionFrame> = _flow.asSharedFlow()

    private var a = FloatArray(3); private var g = FloatArray(3)
    private var r = FloatArray(4); private var last = 0L

    fun startListening() {
        accel?.let { sm.registerListener(this, it, 10_000) }
        gyro?.let  { sm.registerListener(this, it, 10_000) }
        rot?.let   { sm.registerListener(this, it, 10_000) }
    }
    fun stopListening() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent?) {
        e ?: return
        when (e.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> a = e.values.clone()
            Sensor.TYPE_GYROSCOPE            -> g = e.values.clone()
            Sensor.TYPE_ROTATION_VECTOR      -> r = e.values.clone()
        }
        if (e.timestamp - last < 20_000_000L) return
        last = e.timestamp
        _flow.tryEmit(MotionFrame(e.timestamp, a, g, r))
    }
    override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
}
