package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.PressureSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionTrackerTest {

    private class Clock(var t: Long = 0L) {
        fun advance(ms: Long) { t += ms }
    }

    private fun sample(v: Double) = PressureSample(v, 0L)

    @Test
    fun `完整会话统计正确`() {
        val clock = Clock(1_000L)
        val tracker = SessionTracker { clock.t }
        tracker.start() // t=1000
        clock.advance(500)
        tracker.ingest(sample(10.0)) // 累计 10
        tracker.ingest(sample(20.0)) // 累计 30
        tracker.ingest(sample(15.0)) // 累计 45
        clock.advance(500)
        tracker.pause() // 冻结 1000ms
        clock.advance(2_000) // 暂停 2s 不计
        tracker.resume() // baseStart 平移
        clock.advance(1_000)
        tracker.ingest(sample(30.0)) // 累计 75,峰值 30

        val session = tracker.finish() // t = 1000+500+500+2000+1000 = 5000

        assertEquals(SessionPhase.Idle, tracker.phase)
        // 恢复时 baseStart 平移:1000 + (500+500) 暂停前训练时长 = 2000 → baseStart=3000
        assertEquals(3_000L, session.startTimeMillis)
        assertEquals(5_000L, session.endTimeMillis)
        assertEquals(2_000L, session.durationMillis) // 1000 训练 + 1000 恢复后
        assertEquals(18.75, session.avgPressureKg, 0.0) // 75/4
        assertEquals(30.0, session.peakPressureKg, 0.0)
    }

    @Test
    fun `暂停期间样本不计入`() {
        val clock = Clock(0L)
        val tracker = SessionTracker { clock.t }
        tracker.start()
        tracker.ingest(sample(10.0))
        tracker.pause()
        tracker.ingest(sample(100.0)) // 暂停中,应被忽略
        tracker.resume()
        clock.advance(100)
        val session = tracker.finish()
        assertEquals(10.0, session.avgPressureKg, 0.0)
        assertEquals(10.0, session.peakPressureKg, 0.0)
        assertEquals(100L, session.durationMillis)
    }

    @Test
    fun `Idle 时 finish 抛异常`() {
        assertThrows(IllegalStateException::class.java) {
            SessionTracker().finish()
        }
    }

    @Test
    fun `Idle 时重复 start 抛异常`() {
        val tracker = SessionTracker()
        tracker.start()
        assertThrows(IllegalStateException::class.java) { tracker.start() }
    }

    @Test
    fun `空会话 finish 得零值`() {
        val tracker = SessionTracker { 0L }
        tracker.start()
        val session = tracker.finish()
        assertEquals(0L, session.durationMillis)
        assertEquals(0.0, session.avgPressureKg, 0.0)
        assertEquals(0.0, session.peakPressureKg, 0.0)
    }

    @Test
    fun `stats 实时反映时长与峰值`() {
        val clock = Clock(0L)
        val tracker = SessionTracker { clock.t }
        tracker.start()
        clock.advance(300)
        tracker.ingest(sample(5.0))
        tracker.ingest(sample(9.0))
        assertEquals(SessionPhase.Running, tracker.phase)
        assertEquals(300L, tracker.stats.elapsedMillis)
        assertEquals(2, tracker.stats.sampleCount)
        assertEquals(7.0, tracker.stats.avgPressureKg, 0.0)
        assertEquals(9.0, tracker.stats.peakPressureKg, 0.0)
    }

    @Test
    fun `tick 在 Running 时推进时长,暂停时不动`() {
        val clock = Clock(0L)
        val tracker = SessionTracker { clock.t }
        tracker.start()
        clock.advance(1_000)
        tracker.tick()
        assertEquals(1_000L, tracker.stats.elapsedMillis)
        tracker.pause()
        clock.advance(5_000)
        tracker.tick()
        assertEquals(1_000L, tracker.stats.elapsedMillis)
    }
}
