package com.dfrobot.rehab.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: TrainingSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _effects = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeSessions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
                .collect { sessions ->
                    _state.value = HistoryUiState(sessions = sessions, isLoading = false)
                }
        }
    }

    fun accept(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.DeleteSession -> viewModelScope.launch {
                repository.deleteSession(intent.id)
                _effects.send(HistoryEffect.ShowMessage("已删除"))
            }
        }
    }
}
