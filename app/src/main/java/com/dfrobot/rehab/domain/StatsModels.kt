package com.dfrobot.rehab.domain

/** 今日训练统计(监测页概览卡)。 */
data class TodayStats(
    val sessionCount: Int,
    val totalDurationMillis: Long,
    val totalSteps: Int,
)

/** 某天的训练统计(近 7 天柱状图)。 */
data class DailyStat(
    val dateMillis: Long,
    val dayLabel: String,
    val sessionCount: Int,
)
