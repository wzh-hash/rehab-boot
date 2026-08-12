package com.dfrobot.rehab.presentation.history

import androidx.compose.runtime.Immutable
import com.dfrobot.rehab.domain.model.TrainingSession

@Immutable
data class HistoryUiState(
    val sessions: List<TrainingSession> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface HistoryIntent {
    data class DeleteSession(val id: Long) : HistoryIntent
}

sealed interface HistoryEffect {
    data class ShowMessage(val messageRes: Int) : HistoryEffect
}
