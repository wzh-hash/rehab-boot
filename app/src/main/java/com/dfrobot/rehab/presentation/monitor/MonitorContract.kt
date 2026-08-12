package com.dfrobot.rehab.presentation.monitor

import com.dfrobot.rehab.domain.DailyStat
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.SessionStats
import com.dfrobot.rehab.domain.TodayStats
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.TrainingRatio

/** 事件时间线条目:文案以资源 ID 承载,由 UI 层渲染(支持本地化)。 */
data class EventUi(
    val timeMillis: Long,
    val textRes: Int,
)

data class MonitorUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** 收到过设备 "hello"(设备上线);连接断开时复位。 */
    val deviceOnline: Boolean = false,
    val phase: SessionPhase = SessionPhase.Idle,
    val repsCompleted: Int = 0,
    val activeRatio: TrainingRatio? = null,
    val stats: SessionStats = SessionStats(0),
    val recentEvents: List<EventUi> = emptyList(),
    val invalidFrameCount: Int = 0,
    /** 手机是否支持计步(无传感器时 UI 显示不可用)。 */
    val stepsSupported: Boolean = false,
    /** 训练期间实时步数。 */
    val steps: Int = 0,
    /** 今日训练统计(数据可视化)。 */
    val todayStats: TodayStats? = null,
    /** 近 7 天训练统计(数据可视化)。 */
    val weeklyStats: List<DailyStat> = emptyList(),
) {
    val isConnecting: Boolean get() = connectionState == ConnectionState.Connecting

    /** 训练中禁用发令(固件阻塞循环,重复指令会排队)。 */
    val canSendCommand: Boolean
        get() = phase == SessionPhase.Idle && connectionState == ConnectionState.Connected
}

sealed interface MonitorIntent {
    data class StartTraining(val ratio: TrainingRatio) : MonitorIntent
    data object HelloTest : MonitorIntent
    data object RetryConnect : MonitorIntent
}

sealed interface MonitorEffect {
    /** 静态文案(资源 ID)。 */
    data class ShowMessage(val messageRes: Int) : MonitorEffect

    /** 运行时动态文案(如连接错误详情)。 */
    data class ShowRawMessage(val message: String) : MonitorEffect
}
