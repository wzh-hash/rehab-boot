package com.dfrobot.rehab.presentation.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.SessionTracker
import com.dfrobot.rehab.domain.ThresholdCalculator
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val telemetryDataSource: TelemetryDataSource,
    private val connectionGateway: ConnectionGateway,
    private val settingsRepository: DeviceSettingsRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
) : ViewModel() {

    private val tracker = SessionTracker()

    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private val _effects = Channel<MonitorEffect>(Channel.BUFFERED)
    val effects: Flow<MonitorEffect> = _effects.receiveAsFlow()

    init {
        observeConnectionState()
        observeInvalidFrames()
        observeErrors()
        observeSamples()
        observeWeightPercentages()
        tickElapsed()
    }

    fun accept(intent: MonitorIntent) {
        when (intent) {
            MonitorIntent.StartSession -> tracker.start()
            MonitorIntent.PauseSession -> tracker.pause()
            MonitorIntent.ResumeSession -> tracker.resume()
            MonitorIntent.FinishSession -> finishSession()
            MonitorIntent.RetryConnect -> retryConnect()
        }
        if (intent != MonitorIntent.RetryConnect) {
            syncTrackerState()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionGateway.connectionState.collect { conn ->
                _state.update { it.copy(connectionState = conn) }
            }
        }
    }

    private fun observeInvalidFrames() {
        viewModelScope.launch {
            connectionGateway.invalidFrameCount.collect { count ->
                _state.update { it.copy(invalidFrameCount = count) }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            connectionGateway.errorEvents.collect { message ->
                emitEffect(MonitorEffect.ShowMessage(message))
            }
        }
    }

    private var lastUiUpdateMillis = 0L

    private fun observeSamples() {
        // 会话统计:全量样本
        viewModelScope.launch {
            telemetryDataSource.observeSamples().collect { sample ->
                tracker.ingest(sample)
                _state.update { it.copy(stats = tracker.stats) }
            }
        }
        // UI 实时压力:按样本时间戳 150ms 节流
        // (不用 sample() 操作符:其内部 ticker 会在测试调度器上无限重排,导致 runTest 死循环)
        viewModelScope.launch {
            telemetryDataSource.observeSamples().collect { sample ->
                if (sample.timestampMillis - lastUiUpdateMillis >= 150) {
                    lastUiUpdateMillis = sample.timestampMillis
                    _state.update {
                        it.copy(
                            livePressureKg = sample.valueKg,
                            livePressureAtMillis = sample.timestampMillis,
                        )
                    }
                }
            }
        }
    }

    private fun observeWeightPercentages() {
        viewModelScope.launch {
            settingsRepository.weightPercentages.collect { (weight, p) ->
                val thresholds = runCatching {
                    ThresholdCalculator.fromPercentages(weight, p.first, p.second, p.third)
                }.getOrNull()
                _state.update {
                    it.copy(
                        bodyWeightKg = weight,
                        thresholdPercentages = p,
                        thresholds = thresholds,
                    )
                }
                if (thresholds != null &&
                    connectionGateway.connectionState.value == ConnectionState.Connected
                ) {
                    // 保留位:连接后自动下发最新阈值(失败静默,不影响会话)
                    runCatching { telemetryDataSource.publishThresholds(thresholds) }
                }
            }
        }
    }

    private fun tickElapsed() {
        // 真实调度器上的周期计时:避免测试调度器因无限 delay 重排而死循环
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1_000)
                tracker.tick()
                _state.update { it.copy(stats = tracker.stats) }
            }
        }
    }

    private fun syncTrackerState() {
        _state.update { it.copy(phase = tracker.phase, stats = tracker.stats) }
    }

    private fun finishSession() {
        if (tracker.phase == com.dfrobot.rehab.domain.SessionPhase.Idle) return
        val session = tracker.finish()
        viewModelScope.launch {
            runCatching { trainingSessionRepository.saveSession(session) }
            emitEffect(MonitorEffect.ShowMessage("训练完成,已保存"))
        }
        syncTrackerState()
    }

    private fun retryConnect() {
        if (_state.value.connectionState == ConnectionState.Connecting) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.isComplete()) {
                emitEffect(MonitorEffect.ShowMessage("请先在「设置」页填写平台连接信息"))
                return@launch
            }
            try {
                connectionGateway.connect(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEffect(MonitorEffect.ShowMessage(e.message ?: "连接失败"))
            }
        }
    }

    private fun emitEffect(effect: MonitorEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun MutableStateFlow<MonitorUiState>.update(transform: (MonitorUiState) -> MonitorUiState) {
        value = transform(value)
    }
}
