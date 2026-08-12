package com.dfrobot.rehab.presentation.monitor

import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.PressureSample
import com.dfrobot.rehab.domain.model.Thresholds
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val samples = MutableSharedFlow<PressureSample>(extraBufferCapacity = 64)
        val publishedThresholds = mutableListOf<Thresholds>()

        override fun observeSamples(): Flow<PressureSample> = samples

        override suspend fun publishThresholds(thresholds: Thresholds) {
            publishedThresholds.add(thresholds)
        }
    }

    private class FakeGateway : ConnectionGateway {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        private val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val invalidFlow = MutableStateFlow(0)
        var connectCallCount = 0
        var failWith: Exception? = null
        var lastSettings: DeviceSettings? = null

        override val connectionState: StateFlow<ConnectionState> = stateFlow.asStateFlow()
        override val errorEvents: SharedFlow<String> = errors.asSharedFlow()
        override val invalidFrameCount: StateFlow<Int> = invalidFlow.asStateFlow()

        override suspend fun connect(settings: DeviceSettings) {
            connectCallCount++
            lastSettings = settings
            failWith?.let { throw it }
            stateFlow.value = ConnectionState.Connected
        }

        override suspend fun disconnect() {
            stateFlow.value = ConnectionState.Disconnected
        }
    }

    private class FakeSettingsRepo : DeviceSettingsRepository {
        val settingsFlow = MutableStateFlow(
            DeviceSettings(iotId = "abc", iotPwd = "pwd", topic = "BJpHJt1VW"),
        )
        val weightsFlow = MutableStateFlow(60.0 to Triple(25, 50, 75))

        override val settings: Flow<DeviceSettings> = settingsFlow.asStateFlow()
        override suspend fun saveSettings(settings: DeviceSettings) {
            settingsFlow.value = settings
        }

        override val weightPercentages: Flow<Pair<Double, Triple<Int, Int, Int>>> =
            weightsFlow.asStateFlow()

        override suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) {
            weightsFlow.value = bodyWeightKg to Triple(p25, p50, p75)
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
    private lateinit var viewModel: MonitorViewModel

    @Before
    fun setUp() {
        // Unconfined:VM init 的收集器立即执行,emit 同步传播;
        // tickElapsed 的无限循环调度在独立 scheduler 上,不会阻塞 runTest 结束
        Dispatchers.setMain(UnconfinedTestDispatcher())
        telemetry = FakeTelemetry()
        gateway = FakeGateway()
        settingsRepo = FakeSettingsRepo()
        sessionRepo = FakeSessionRepo()
        viewModel = MonitorViewModel(telemetry, gateway, settingsRepo, sessionRepo)
    }

    @Test
    fun `样本到达后实时压力与统计更新`() = runTest {
        telemetry.samples.emit(PressureSample(12.5, 1000L))

        assertEquals(12.5, viewModel.state.value.livePressureKg!!, 0.0)
        assertEquals(1000L, viewModel.state.value.livePressureAtMillis!!)
    }

    @Test
    fun `开始会话后进入 Running`() = runTest {
        viewModel.accept(MonitorIntent.StartSession)
        assertEquals(SessionPhase.Running, viewModel.state.value.phase)
    }

    @Test
    fun `训练中样本计入统计`() = runTest {
        viewModel.accept(MonitorIntent.StartSession)
        telemetry.samples.emit(PressureSample(10.0, 1L))
        telemetry.samples.emit(PressureSample(20.0, 2L))

        assertEquals(2, viewModel.state.value.stats.sampleCount)
        assertEquals(15.0, viewModel.state.value.stats.avgPressureKg, 0.0)
        assertEquals(20.0, viewModel.state.value.stats.peakPressureKg, 0.0)
    }

    @Test
    fun `结束会话保存并提示`() = runTest {
        viewModel.accept(MonitorIntent.StartSession)
        telemetry.samples.emit(PressureSample(30.0, 1L))

        viewModel.accept(MonitorIntent.FinishSession)


        assertEquals(SessionPhase.Idle, viewModel.state.value.phase)
        assertEquals(1, sessionRepo.saved.size)
        assertEquals(30.0, sessionRepo.saved[0].peakPressureKg, 0.0)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("训练完成,已保存"), awaitItem())
        }
    }

    @Test
    fun `设置变化后自动换算并下发新阈值`() = runTest {
        gateway.stateFlow.value = ConnectionState.Connected
        settingsRepo.weightsFlow.value = 70.0 to Triple(25, 50, 75)

        assertEquals(17.5, viewModel.state.value.thresholds!!.p25Kg, 0.0) // 70*0.25
        assertEquals(Thresholds(17.5, 35.0, 52.5), telemetry.publishedThresholds.last())
    }

    @Test
    fun `未连接时设置变化不发布`() = runTest {
        settingsRepo.weightsFlow.value = 70.0 to Triple(25, 50, 75)

        assertEquals(0, telemetry.publishedThresholds.size)
        assertEquals(17.5, viewModel.state.value.thresholds!!.p25Kg, 0.0)
    }

    @Test
    fun `配置不完整时重连提示去设置页`() = runTest {
        settingsRepo.settingsFlow.value = DeviceSettings(iotId = "", iotPwd = "", topic = "")
        viewModel.accept(MonitorIntent.RetryConnect)

        assertEquals(0, gateway.connectCallCount)
        viewModel.effects.test {
            assertEquals(MonitorEffect.ShowMessage("请先在「设置」页填写平台连接信息"), awaitItem())
        }
    }

    @Test
    fun `重连失败提示错误`() = runTest {
        gateway.failWith = com.dfrobot.rehab.core.mqtt.MqttConnectionException("网络不可达,请检查网络或服务器地址")
        viewModel.accept(MonitorIntent.RetryConnect)

        assertEquals(1, gateway.connectCallCount)
        viewModel.effects.test {
            val effect = awaitItem() as MonitorEffect.ShowMessage
            assertTrue(effect.message.contains("网络不可达"))
        }
    }

    @Test
    fun `连接中时忽略重复重连`() = runTest {
        gateway.stateFlow.value = ConnectionState.Connecting
        viewModel.accept(MonitorIntent.RetryConnect)

        assertEquals(0, gateway.connectCallCount)
    }

    @Test
    fun `连接成功后重连会建立连接`() = runTest {
        viewModel.accept(MonitorIntent.RetryConnect)

        assertEquals(1, gateway.connectCallCount)
        assertEquals(ConnectionState.Connected, viewModel.state.value.connectionState)
    }
}
