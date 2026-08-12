package com.dfrobot.rehab.presentation.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfrobot.rehab.core.sensor.StepCounter
import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.SessionPhase
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
        tickElapsed()
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
                emitEffect(MonitorEffect.ShowMessage(message))
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            telemetryDataSource.observeEvents().collect { event ->
                when (event) {
                    DeviceEvent.Hello -> _state.update { it.copy(deviceOnline = true) }

                    DeviceEvent.RepReached -> appendEvent("已达到目标重量")

                    DeviceEvent.RepCompleted -> onRepCompleted()
                }
            }
        }
    }

    private fun onRepCompleted() {
        if (tracker.phase != com.dfrobot.rehab.domain.SessionPhase.Training) return
        tracker.onRepCompleted()
        appendEvent("完成一次重复")
        if (tracker.repsCompleted >= 3) {
            finishSession("训练完成,已记录")
        }
    }

    private fun startTraining(ratio: TrainingRatio) {
        if (!_state.value.canSendCommand) {
            emitEffect(
                MonitorEffect.ShowMessage(
                    when {
                        _state.value.connectionState != ConnectionState.Connected -> "未连接,请先连接平台"
                        else -> "设备训练中,请等待当前训练结束"
                    },
                ),
            )
            return
        }
        viewModelScope.launch {
            runCatching { telemetryDataSource.publishCommand(ratio) }
                .onFailure { emitEffect(MonitorEffect.ShowMessage(it.message ?: "指令发送失败")) }
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
            emitEffect(MonitorEffect.ShowMessage("未连接,请先连接平台"))
            return
        }
        viewModelScope.launch {
            runCatching { telemetryDataSource.publishHelloTest() }
                .onFailure { emitEffect(MonitorEffect.ShowMessage(it.message ?: "指令发送失败")) }
        }
    }

    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(SESSION_TIMEOUT_MS)
            if (tracker.phase == com.dfrobot.rehab.domain.SessionPhase.Training) {
                finishSession("训练超时,已记录(未完成)")
            }
        }
    }

    private fun finishSession(message: String) {
        timeoutJob?.cancel()
        timeoutJob = null
        stepCounter.stop()
        val session = tracker.finish()
        viewModelScope.launch {
            runCatching { trainingSessionRepository.saveSession(session) }
            emitEffect(MonitorEffect.ShowMessage(message))
        }
        _state.update {
            it.copy(
                phase = tracker.phase,
                stats = tracker.stats,
                repsCompleted = tracker.repsCompleted,
            )
        }
    }

    private fun appendEvent(text: String) {
        _state.update {
            val now = System.currentTimeMillis()
            it.copy(
                recentEvents = (it.recentEvents + EventUi(now, text)).takeLast(MAX_EVENTS),
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
