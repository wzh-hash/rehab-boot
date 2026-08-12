package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SessionStatsAggregatorTest {

    // 固定"现在":2026-08-12 15:00 北京时间(UTC+8)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val nowMillis: Long = ZonedDateTime.of(2026, 8, 12, 15, 0, 0, 0, zone)
        .toInstant().toEpochMilli()
    // 2026-08-12 00:00 北京时间
    private val todayStart: Long = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, zone)
        .toInstant().toEpochMilli()
    // 2026-08-11 00:00 北京时间(昨天)
    private val yesterdayStart: Long = ZonedDateTime.of(2026, 8, 11, 0, 0, 0, 0, zone)
        .toInstant().toEpochMilli()

    private fun session(
        start: Long,
        duration: Long = 60_000,
        steps: Int = 10,
        ratio: TrainingRatio = TrainingRatio.T25,
    ) = TrainingSession(
        startTimeMillis = start, endTimeMillis = start + duration,
        durationMillis = duration, ratio = ratio,
        repsCompleted = 3, completed = true, steps = steps,
    )

    @Test
    fun `今日统计只含今天的数据`() {
        val sessions = listOf(
            session(todayStart + 1_000, duration = 120_000, steps = 30),
            session(todayStart + 60_000, duration = 60_000, steps = 20),
            session(yesterdayStart + 1_000, duration = 999_000, steps = 999), // 昨天,不应计入
        )
        val stats = SessionStatsAggregator.todayStats(sessions, nowMillis, zone)
        assertEquals(2, stats.sessionCount)
        assertEquals(180_000L, stats.totalDurationMillis)
        assertEquals(50, stats.totalSteps)
    }

    @Test
    fun `无数据时今日统计为零`() {
        val stats = SessionStatsAggregator.todayStats(emptyList(), nowMillis, zone)
        assertEquals(0, stats.sessionCount)
        assertEquals(0L, stats.totalDurationMillis)
        assertEquals(0, stats.totalSteps)
    }

    @Test
    fun `今天零点前的会话不计入今日`() {
        val stats = SessionStatsAggregator.todayStats(
            listOf(session(todayStart - 1)),
            nowMillis, zone,
        )
        assertEquals(0, stats.sessionCount)
    }

    @Test
    fun `近7天统计按日分组且含今天`() {
        val sessions = listOf(
            session(todayStart + 1_000),                       // 今天
            session(yesterdayStart + 1_000),                   // 昨天
            session(yesterdayStart + 60_000),                  // 昨天第 2 条
        )
        val stats = SessionStatsAggregator.weeklyStats(
            sessions, nowMillis, zone,
            dayLabel = { "L${it}" },
        )
        assertEquals(7, stats.size)
        // 最后一项 = 今天
        assertEquals(todayStart, stats[6].dateMillis)
        assertEquals(1, stats[6].sessionCount)
        // 倒数第二项 = 昨天
        assertEquals(yesterdayStart, stats[5].dateMillis)
        assertEquals(2, stats[5].sessionCount)
        // 其余为 0
        assertEquals(0, stats[0].sessionCount)
        assertEquals(0, stats[4].sessionCount)
        // 日期标签按注入的格式化器生成
        assertEquals("L${stats[6].dateMillis}", stats[6].dayLabel)
    }

    @Test
    fun `7天窗口外的会话不计入`() {
        val oldStart = ZonedDateTime.of(2026, 7, 30, 12, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        val stats = SessionStatsAggregator.weeklyStats(
            listOf(session(oldStart)),
            nowMillis, zone,
            dayLabel = { "" },
        )
        assertEquals(7, stats.size)
        assertEquals(0, stats.sumOf { it.sessionCount })
    }
}
