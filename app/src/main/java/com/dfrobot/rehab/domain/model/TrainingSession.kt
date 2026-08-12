package com.dfrobot.rehab.domain.model

/** 一次训练会话的汇总记录(Room 持久化)。固件无压力上报,记录事件驱动数据。 */
data class TrainingSession(
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val ratio: TrainingRatio,
    val repsCompleted: Int,
    /** 3 次重复全部完成则为 true;超时/中断为 false。 */
    val completed: Boolean,
)
