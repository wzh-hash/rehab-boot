package com.dfrobot.rehab.domain.model

/** 一次训练会话的汇总记录(Room 持久化)。 */
data class TrainingSession(
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val avgPressureKg: Double,
    val peakPressureKg: Double,
)
