package com.dfrobot.rehab.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 计步能力门面:供 presentation 层依赖,便于 fake 测试。 */
interface StepCounter {
    val isSupported: Boolean

    /** 自 [start] 起的累计步数;不支持时返回 null。 */
    fun readTotalSteps(): Int?

    fun start()

    fun stop()
}

/**
 * 手机传感器计步(训练会话内):TYPE_STEP_COUNTER 优先(硬件累计,
 * 差值计算),TYPE_STEP_DETECTOR 备选(逐事件累加);均无则不可用。
 * 监听仅在训练期间注册(省电);进程由前台服务保活。
 */
@Singleton
class StepSensor @Inject constructor(
    @ApplicationContext context: Context,
) : StepCounter {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accumulator = StepAccumulator()
    private var registered = false

    override val isSupported: Boolean
        get() = counterSensor() != null || detectorSensor() != null

    override fun readTotalSteps(): Int? = if (isSupported) accumulator.total else null

    override fun start() {
        val sensor = counterSensor() ?: detectorSensor() ?: return
        accumulator.reset()
        registered = sensorManager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_NORMAL,
        )
    }

    override fun stop() {
        if (registered) {
            sensorManager.unregisterListener(listener)
        }
        registered = false
    }

    private fun counterSensor(): Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private fun detectorSensor(): Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> accumulator.onCounterValue(event.values[0].toInt())
                Sensor.TYPE_STEP_DETECTOR -> accumulator.onDetectorStep()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}

/** 纯逻辑:计数器差值 / 检测器累加(可单测)。 */
class StepAccumulator {

    var total: Int = 0
        private set

    private var counterBaseline: Int? = null

    fun reset() {
        counterBaseline = null
        total = 0
    }

    /** STEP_COUNTER 上报的是自重启以来的累计值,取与基线的差值。 */
    fun onCounterValue(raw: Int) {
        val baseline = counterBaseline
        if (baseline == null) {
            counterBaseline = raw
        } else {
            total = (raw - baseline).coerceAtLeast(0)
        }
    }

    /** STEP_DETECTOR 每次事件 = 一步。 */
    fun onDetectorStep() {
        total += 1
    }
}
