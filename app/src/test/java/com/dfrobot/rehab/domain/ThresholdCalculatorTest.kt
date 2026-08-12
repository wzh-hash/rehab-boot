package com.dfrobot.rehab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThresholdCalculatorTest {

    @Test
    fun `60kg 按 25 50 75 换算为 15 30 45`() {
        val t = ThresholdCalculator.fromPercentages(60.0, 25, 50, 75)
        assertEquals(15.0, t.p25Kg, 0.0)
        assertEquals(30.0, t.p50Kg, 0.0)
        assertEquals(45.0, t.p75Kg, 0.0)
    }

    @Test
    fun `保留一位小数`() {
        val t = ThresholdCalculator.fromPercentages(57.5, 25, 50, 75)
        assertEquals(14.4, t.p25Kg, 0.0) // 14.375 -> 14.4
        assertEquals(28.8, t.p50Kg, 0.0) // 28.75  -> 28.8
    }

    @Test
    fun `自定义百分比可用`() {
        val t = ThresholdCalculator.fromPercentages(70.0, 10, 30, 80)
        assertEquals(7.0, t.p25Kg, 0.0)
        assertEquals(21.0, t.p50Kg, 0.0)
        assertEquals(56.0, t.p75Kg, 0.0)
    }

    @Test
    fun `百分比非单调时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(60.0, 50, 25, 75)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(60.0, 25, 75, 50)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(60.0, 0, 50, 75)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(60.0, 25, 50, 101)
        }
    }

    @Test
    fun `体重越界时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(9.0, 25, 50, 75)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdCalculator.fromPercentages(301.0, 25, 50, 75)
        }
    }

    @Test
    fun `边界合法值通过`() {
        val t = ThresholdCalculator.fromPercentages(10.0, 1, 1, 100)
        assertEquals(0.1, t.p25Kg, 0.0)
        assertEquals(10.0, t.p75Kg, 0.0)
    }
}
