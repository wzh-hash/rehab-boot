package com.dfrobot.rehab.presentation.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfrobot.rehab.R
import com.dfrobot.rehab.core.sensor.StepCounter
import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.SessionStatsAggregator
import com.dfrobot.rehab.domain.SessionTracker
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 训练超时:10 分钟无 "plus" 事件视为未完成。 */
private const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L
private const val MAX_EVENTS = 8

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val telemetryDataSource: TelemetryDataSource,
    private val connectionGateway: ConnectionGateway,
    private val settingsRepository: DeviceSettingsRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val stepCounter: StepCounter,
) : ViewModel() {

    private val tracker = SessionTracker(stepProvider = { stepCounter.readTotalSteps() ?: 0 })

    private val _state = MutableStateFlow(MonitorUiState(stepsSupported = stepCounter.isSupported))
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private val _effects = Channel<MonitorEffect>(Channel.BUFFERED)
    val effects: Flow<MonitorEffect> = _effects.receiveAsFlow()

    private var timeoutJob: Job? = null

    init {
        observeConnectionState()
        observeInvalidFrames()
        observeErrors()
        observeEvents()
        observeStats()
        tickElapsed()
    }

    /** 训练历史 → 今日概览 + 近 7 天统计(数据可视化)。 */
    private fun observeStats() {
        val dayLabelFormat = SimpleDateFormat("M/d", Locale.CHINA)
        viewModelScope.launch {
            trainingSessionRepository.observeSessions().collect { sessions ->
                val now = System.currentTimeMillis()
                val zoneId = ZoneId.systemDefault()
                _state.update {
                    it.copy(
                        todayStats = SessionStatsAggregator.todayStats(sessions, now, zoneId),
                        weeklyStats = SessionStatsAggregator.weeklyStats(
                            sessions, now, zoneId,
                            dayLabel = { millis -> dayLabelFormat.format(Date(millis)) },
                        ),
                    )
                }
            }
        }
    }

    fun accept(intent: MonitorIntent) {
        when (intent) {
            is MonitorIntent.StartTraining -> startTraining(intent.ratio)
            MonitorIntent.HelloTest -> helloTest()
            MonitorIntent.RetryConnect -> retryConnect()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionGateway.connectionState.collect { conn ->
                _state.update {
                    it.copy(
                        connectionState = conn,
                        // 断开后设备在线状态复位(重连后设备会重新发 hello)
                        deviceOnline = if (conn == ConnectionState.Connected) it.deviceOnline else false,
                    )
                }
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
                // 连接错误为运行时动态文案(如"网络不可达…"),直接透传
                emitEffect(MonitorEffect.ShowRawMessage(message))
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            telemetryDataSource.observeEvents().collect { event ->
                when (event) {
                    DeviceEvent.Hello -> _state.update { it.copy(deviceOnline = true) }

                    DeviceEvent.RepReached -> appendEvent(R.string.monitor_event_reached)

                    DeviceEvent.RepCompleted -> onRepCompleted()
                }
            }
        }
    }

    private fun onRepCompleted() {
        if (tracker.phase != com.dfrobot.rehab.domain.SessionPhase.Training) return
        tracker.onRepCompleted()
        // 同步进度到 UI(state.repsCompleted 驱动"第 X/3 次"与圆点序列)
        _state.update { it.copy(repsCompleted = tracker.repsCompleted) }
        appendEvent(R.string.monitor_event_completed)
        if (tracker.repsCompleted >= 3) {
            finishSession(R.string.session_done)
        }
    }

    private fun startTraining(ratio: TrainingRatio) {
        if (!_state.value.canSendCommand) {
            emitEffect(
                MonitorEffect.ShowMessage(
                    if (_state.value.connectionState != ConnectionState.Connected) {
                        R.string.err_not_connected_cmd
                    } else {
                        R.string.err_training_busy
                    },
                ),
            )
            return
        }
        viewModelScope.launch {
            runCatching { telemetryDataSource.publishCommand(ratio) }
                .onFailure { emitEffect(MonitorEffect.ShowMessage(R.string.err_send_failed)) }
                .onSuccess {
                    stepCounter.start()
                    tracker.start(ratio)
                    _state.update {
                        it.copy(
                            phase = tracker.phase,
                            repsCompleted = 0,
                            activeRatio = ratio,
                            stats = tracker.stats,
                            recentEvents = emptyList(),
                            steps = stepCounter.readTotalSteps() ?: 0,
                        )
                    }
                    startTimeout()
                }
        }
    }

    private fun helloTest() {
        if (_state.value.connectionState != ConnectionState.Connected) {
            emitEffect(MonitorEffect.ShowMessage(R.string.err_not_connected_cmd))
            return
        }
        viewModelScope.launch {
            runCatching { telemetryDataSource.publishHelloTest() }
                .onFailure { emitEffect(MonitorEffect.ShowMessage(R.string.err_send_failed)) }
        }
    }

    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(SESSION_TIMEOUT_MS)
            if (tracker.phase == com.dfrobot.rehab.domain.SessionPhase.Training) {
                finishSession(R.string.session_timeout)
            }
        }
    }

    private fun finishSession(messageRes: Int) {
        timeoutJob?.cancel()
        timeoutJob = null
        stepCounter.stop()
        val session = tracker.finish()
        viewModelScope.launch {
            runCatching { trainingSessionRepository.saveSession(session) }
            emitEffect(MonitorEffect.ShowMessage(messageRes))
        }
        _state.update {
            it.copy(
                phase = tracker.phase,
                stats = tracker.stats,
                // 用会话值而非 tracker(其已 reset 清零)
                repsCompleted = session.repsCompleted,
            )
        }
    }

    private fun appendEvent(textRes: Int) {
        _state.update {
            val now = System.currentTimeMillis()
            it.copy(
                recentEvents = (it.recentEvents + EventUi(now, textRes)).takeLast(MAX_EVENTS),
            )
        }
    }

    private fun tickElapsed() {
        // 真实调度器上的周期计时:避免测试调度器因无限 delay 重排而死循环
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1_000)
                tracker.tick()
                _state.update {
                    it.copy(
                        stats = tracker.stats,
                        steps = if (it.phase == SessionPhase.Training) {
                            stepCounter.readTotalSteps() ?: it.steps
                        } else {
                            it.steps
                        },
                    )
                }
            }
        }
    }

    private fun retryConnect() {
        if (_state.value.connectionState == ConnectionState.Connecting) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.isComplete()) {
                emitEffect(MonitorEffect.ShowMessage(R.string.settings_needed_hint))
                return@launch
            }
            try {
                connectionGateway.connect(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 连接错误为运行时动态文案(如"网络不可达…"),直接透传
                emitEffect(MonitorEffect.ShowRawMessage(e.message ?: ""))
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
