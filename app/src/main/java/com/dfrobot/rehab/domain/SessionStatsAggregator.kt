package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.TrainingSession
import java.time.LocalDate
import java.time.ZoneId

/**
 * 训练历史聚合(纯函数,可单测):
 * 会话量小(每次训练一条记录),直接在内存按本地时区日界聚合,不动 DAO。
 */
object SessionStatsAggregator {

    /** 今日(本地时区 0 点起)训练次数/总时长/总步数。 */
    fun todayStats(
        sessions: List<TrainingSession>,
        nowMillis: Long,
        zoneId: ZoneId,
    ): TodayStats {
        val dayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val today = sessions.filter { it.startTimeMillis >= dayStart }
        return TodayStats(
            sessionCount = today.size,
            totalDurationMillis = today.sumOf { it.durationMillis },
            totalSteps = today.sumOf { it.steps },
        )
    }

    /** 近 7 天(含今天)每天训练次数;[dayLabel] 由调用方注入日期标签格式化。 */
    fun weeklyStats(
        sessions: List<TrainingSession>,
        nowMillis: Long,
        zoneId: ZoneId,
        dayLabel: (Long) -> String,
    ): List<DailyStat> {
        val today = LocalDate.now(zoneId)
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val count = sessions.count { it.startTimeMillis in start until end }
            DailyStat(
                dateMillis = start,
                dayLabel = dayLabel(start),
                sessionCount = count,
            )
        }
    }
}
