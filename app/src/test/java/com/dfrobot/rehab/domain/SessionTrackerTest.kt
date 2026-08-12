package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.TrainingRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionTrackerTest {

    private class Clock(var t: Long = 0L) {
        fun advance(ms: Long) { t += ms }
    }

    @Test
    fun `三次重复完成则会话 completed`() {
        val clock = Clock(1_000L)
        val tracker = SessionTracker(nowMillis = { clock.t })
        tracker.start(TrainingRatio.T25)
        clock.advance(2_000)
        tracker.onRepCompleted()
        tracker.onRepCompleted()
        tracker.onRepCompleted()
        clock.advance(1_000)

        val session = tracker.finish()
        assertEquals(SessionPhase.Idle, tracker.phase)
        assertEquals(1_000L, session.startTimeMillis)
        assertEquals(4_000L, session.endTimeMillis)
        assertEquals(3_000L, session.durationMillis)
        assertEquals(TrainingRatio.T25, session.ratio)
        assertEquals(3, session.repsCompleted)
        assertEquals(true, session.completed)
    }

    @Test
    fun `中途结束则 completed 为 false`() {
        val tracker = SessionTracker(nowMillis = { 0L })
        tracker.start(TrainingRatio.T75)
        tracker.onRepCompleted()
        val session = tracker.finish()
        assertEquals(1, session.repsCompleted)
        assertEquals(false, session.completed)
        assertEquals(TrainingRatio.T75, session.ratio)
    }

    @Test
    fun `Idle 时 finish 抛异常`() {
        assertThrows(IllegalStateException::class.java) {
            SessionTracker().finish()
        }
    }

    @Test
    fun `Training 中重复 start 抛异常`() {
        val tracker = SessionTracker()
        tracker.start(TrainingRatio.T50)
        assertThrows(IllegalStateException::class.java) {
            tracker.start(TrainingRatio.T50)
        }
    }

    @Test
    fun `Idle 时 onRepCompleted 抛异常`() {
        assertThrows(IllegalStateException::class.java) {
            SessionTracker().onRepCompleted()
        }
    }

    @Test
    fun `会话步数为结束与开始之差`() {
        var steps = 0
        val tracker = SessionTracker(stepProvider = { steps })
        tracker.start(TrainingRatio.T50)
        steps = 12
        tracker.onRepCompleted()
        steps = 35
        val session = tracker.finish()
        assertEquals(35, session.steps)
    }

    @Test
    fun `步数回退不为负`() {
        var steps = 100
        val tracker = SessionTracker(stepProvider = { steps })
        tracker.start(TrainingRatio.T50)
        steps = 80
        val session = tracker.finish()
        assertEquals(0, session.steps)
    }

    @Test
    fun `tick 推进时长`() {
        val clock = Clock(0L)
        val tracker = SessionTracker(nowMillis = { clock.t })
        tracker.start(TrainingRatio.T100)
        clock.advance(1_000)
        tracker.tick()
        assertEquals(1_000L, tracker.stats.elapsedMillis)
        assertEquals(TrainingRatio.T100, tracker.stats.ratio)
        tracker.finish()
        clock.advance(5_000)
        tracker.tick() // Idle 时不动
        assertEquals(SessionPhase.Idle, tracker.phase)
    }
}
