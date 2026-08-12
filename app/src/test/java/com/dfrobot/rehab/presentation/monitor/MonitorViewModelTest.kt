package com.dfrobot.rehab.presentation.monitor

import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModelTest {

    private class FakeTelemetry : TelemetryDataSource {
        val events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
        val publishedCommands = mutableListOf<TrainingRatio>()
        var helloTestCount = 0

        override fun observeEvents(): Flow<DeviceEvent> = events

        override suspend fun publishCommand(ratio: TrainingRatio) {
            publishedCommands.add(ratio)
        }

        override suspend fun publishHelloTest() {
            helloTestCount++
        }
    }

    private class FakeGateway : ConnectionGateway {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        private val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val invalidFlow = MutableStateFlow(0)
        var connectCallCount = 0
        var failWith: Exception? = null

        override val connectionState: StateFlow<ConnectionState> = stateFlow.asStateFlow()
        override val errorEvents: SharedFlow<String> = errors.asSharedFlow()
        override val invalidFrameCount: StateFlow<Int> = invalidFlow.asStateFlow()

        override suspend fun connect(settings: DeviceSettings) {
            connectCallCount++
            failWith?.let { throw it }
            stateFlow.value = ConnectionState.Connected
        }

        override suspend fun disconnect() {
            stateFlow.value = ConnectionState.Disconnected
        }
    }

    private class FakeStepCounter : com.dfrobot.rehab.core.sensor.StepCounter {
        var supported = true
        var steps = 0
        var started = false
        var stopped = false

        override val isSupported: Boolean get() = supported
        override fun readTotalSteps(): Int? = if (supported) steps else null
        override fun start() { started = true }
        override fun stop() { stopped = true }
    }

    private class FakeSettingsRepo : DeviceSettingsRepository {
        val settingsFlow = MutableStateFlow(
            DeviceSettings(iotId = "abc", iotPwd = "pwd", topic = "BJpHJt1VW"),
        )

        override val settings: Flow<DeviceSettings> = settingsFlow.asStateFlow()

        override suspend fun saveSettings(settings: DeviceSettings) {
            settingsFlow.value = settings
        }
    }

    private class FakeSessionRepo : TrainingSessionRepository {
        val saved = mutableListOf<TrainingSession>()
        override fun observeSessions(): Flow<List<TrainingSession>> =
            MutableStateFlow(saved.toList())

        override suspend fun saveSession(session: TrainingSession) {
            saved.add(session)
        }

        override suspend fun deleteSession(id: Long) {
            saved.removeAll { it.id == id }
        }
    }

    private lateinit var telemetry: FakeTelemetry
    private lateinit var gateway: FakeGateway
    private lateinit var settingsRepo: FakeSettingsRepo
    private lateinit var sessionRepo: FakeSessionRepo
    private lateinit var stepCounter: FakeStepCounter

    @Before
    fun setUp() {
        telemetry = FakeTelemetry()
        gateway = FakeGateway()
        settingsRepo = FakeSettingsRepo()
        sessionRepo = FakeSessionRepo()
        stepCounter = FakeStepCounter()
    }

    private fun createViewModel() =
        MonitorViewModel(telemetry, gateway, settingsRepo, sessionRepo, stepCounter)

    @Test
    fun `未连接时发令被拒且不下发`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T25))
        assertEquals(0, telemetry.publishedCommands.size)
        assertEquals(SessionPhase.Idle, viewModel.state.value.phase)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("未连接,请先连接平台"), awaitItem())
        }
    }

    @Test
    fun `连接后发令下发指令并进入训练`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T25))
        assertEquals(listOf(TrainingRatio.T25), telemetry.publishedCommands)
        assertEquals(SessionPhase.Training, viewModel.state.value.phase)
        assertEquals(TrainingRatio.T25, viewModel.state.value.activeRatio)
    }

    @Test
    fun `收到 hello 设备上线`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        telemetry.events.emit(DeviceEvent.Hello)
        assertEquals(true, viewModel.state.value.deviceOnline)
    }

    @Test
    fun `WA 事件进入时间线`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        telemetry.events.emit(DeviceEvent.RepReached)
        assertTrue(viewModel.state.value.recentEvents.any { it.text == "已达到目标重量" })
    }

    @Test
    fun `三次 plus 后会话落库并提示完成`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T75))
        telemetry.events.emit(DeviceEvent.RepCompleted)
        telemetry.events.emit(DeviceEvent.RepCompleted)
        telemetry.events.emit(DeviceEvent.RepCompleted)

        assertEquals(SessionPhase.Idle, viewModel.state.value.phase)
        assertEquals(1, sessionRepo.saved.size)
        val session = sessionRepo.saved[0]
        assertEquals(TrainingRatio.T75, session.ratio)
        assertEquals(3, session.repsCompleted)
        assertEquals(true, session.completed)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("训练完成,已记录"), awaitItem())
        }
    }

    @Test
    fun `训练中重复发令被拒`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T25))
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T50))
        assertEquals(listOf(TrainingRatio.T25), telemetry.publishedCommands)
        assertEquals(TrainingRatio.T25, viewModel.state.value.activeRatio)
    }

    @Test
    fun `训练开始启动计步_结束停止并写入步数`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T25))
        assertEquals(true, stepCounter.started)
        assertEquals(true, viewModel.state.value.stepsSupported)

        stepCounter.steps = 42
        telemetry.events.emit(DeviceEvent.RepCompleted)
        telemetry.events.emit(DeviceEvent.RepCompleted)
        telemetry.events.emit(DeviceEvent.RepCompleted)

        assertEquals(true, stepCounter.stopped)
        assertEquals(42, sessionRepo.saved[0].steps)
        assertEquals(0, viewModel.state.value.steps) // 会话结束后不保留实时步数
    }

    @Test
    fun `断开连接后设备在线状态复位`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        telemetry.events.emit(DeviceEvent.Hello)
        assertEquals(true, viewModel.state.value.deviceOnline)
        gateway.stateFlow.value = ConnectionState.Disconnected
        assertEquals(false, viewModel.state.value.deviceOnline)
    }

    @Test
    fun `十分钟无事件超时落库未完成`() = runTest {
        // 共享 testScheduler:launch 立即执行,delay 可用 advanceTimeBy 推进
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        gateway.stateFlow.value = ConnectionState.Connected
        viewModel.accept(MonitorIntent.StartTraining(TrainingRatio.T50))
        assertEquals(SessionPhase.Training, viewModel.state.value.phase)

        advanceTimeBy(10 * 60 * 1000L)
        runCurrent()

        assertEquals(SessionPhase.Idle, viewModel.state.value.phase)
        assertEquals(1, sessionRepo.saved.size)
        assertEquals(false, sessionRepo.saved[0].completed)
        assertEquals(0, sessionRepo.saved[0].repsCompleted)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("训练超时,已记录(未完成)"), awaitItem())
        }
    }

    @Test
    fun `配置不完整时重连提示去设置页`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = createViewModel()
        settingsRepo.settingsFlow.value = DeviceSettings(iotId = "", iotPwd = "", topic = "")
        viewModel.accept(MonitorIntent.RetryConnect)
        assertEquals(0, gateway.connectCallCount)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("请先在「设置」页填写平台连接信息"), awaitItem())
        }
    }
}
