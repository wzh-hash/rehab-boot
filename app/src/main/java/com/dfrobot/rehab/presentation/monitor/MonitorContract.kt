package com.dfrobot.rehab.presentation.monitor

import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.SessionStats
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.Thresholds

data class MonitorUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val livePressureKg: Double? = null,
    val livePressureAtMillis: Long? = null,
    val thresholds: Thresholds? = null,
    val thresholdPercentages: Triple<Int, Int, Int> = Triple(25, 50, 75),
    val bodyWeightKg: Double = 60.0,
    val phase: SessionPhase = SessionPhase.Idle,
    val stats: SessionStats = SessionStats(0, 0, 0.0, 0.0),
    val invalidFrameCount: Int = 0,
) {
    val isConnecting: Boolean get() = connectionState == ConnectionState.Connecting
}

sealed interface MonitorIntent {
    data object StartSession : MonitorIntent
    data object PauseSession : MonitorIntent
    data object ResumeSession : MonitorIntent
    data object FinishSession : MonitorIntent
    data object RetryConnect : MonitorIntent
}

sealed interface MonitorEffect {
    data class ShowMessage(val message: String) : MonitorEffect
}
