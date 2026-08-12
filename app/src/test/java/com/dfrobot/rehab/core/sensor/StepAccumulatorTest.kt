package com.dfrobot.rehab.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class StepAccumulatorTest {

    @Test
    fun `计数器模式取与基线差值`() {
        val acc = StepAccumulator()
        acc.reset()
        acc.onCounterValue(1000) // 基线
        assertEquals(0, acc.total)
        acc.onCounterValue(1005)
        assertEquals(5, acc.total)
        acc.onCounterValue(1012)
        assertEquals(12, acc.total)
    }

    @Test
    fun `检测器模式逐事件累加`() {
        val acc = StepAccumulator()
        acc.reset()
        acc.onDetectorStep()
        acc.onDetectorStep()
        acc.onDetectorStep()
        assertEquals(3, acc.total)
    }

    @Test
    fun `reset 后重新计基线`() {
        val acc = StepAccumulator()
        acc.onCounterValue(100)
        acc.onCounterValue(110)
        assertEquals(10, acc.total)
        acc.reset()
        acc.onCounterValue(200)
        assertEquals(0, acc.total)
        acc.onCounterValue(203)
        assertEquals(3, acc.total)
    }

    @Test
    fun `计数器回退不产生负步数`() {
        val acc = StepAccumulator()
        acc.onCounterValue(100)
        acc.onCounterValue(95)
        assertEquals(0, acc.total)
    }
}
