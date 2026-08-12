package com.dfrobot.rehab.presentation.settings

import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeSettingsRepo : DeviceSettingsRepository {
        val settingsFlow = MutableStateFlow(DeviceSettings())
        val weightsFlow = MutableStateFlow(60.0 to Triple(25, 50, 75))
        var savedCount = 0

        override val settings: Flow<DeviceSettings> = settingsFlow.asStateFlow()
        override suspend fun saveSettings(settings: DeviceSettings) {
            settingsFlow.value = settings
            savedCount++
        }

        override val weightPercentages: Flow<Pair<Double, Triple<Int, Int, Int>>> =
            weightsFlow.asStateFlow()

        override suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) {
            weightsFlow.value = bodyWeightKg to Triple(p25, p50, p75)
            savedCount++
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

    private lateinit var settingsRepo: FakeSettingsRepo
    private lateinit var gateway: FakeGateway
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        settingsRepo = FakeSettingsRepo()
        gateway = FakeGateway()
        viewModel = SettingsViewModel(settingsRepo, gateway)
    }

    private fun fillValidForm() {
        viewModel.accept(
            SettingsIntent.ConnectionFieldChanged(ConnectionField.HOST, "iot.dfrobot.com.cn"),
        )
        viewModel.accept(SettingsIntent.ConnectionFieldChanged(ConnectionField.PORT, "1883"))
        viewModel.accept(SettingsIntent.ConnectionFieldChanged(ConnectionField.IOT_ID, "abc"))
        viewModel.accept(SettingsIntent.ConnectionFieldChanged(ConnectionField.IOT_PWD, "secret"))
        viewModel.accept(SettingsIntent.ConnectionFieldChanged(ConnectionField.TOPIC, "BJpHJt1VW"))
    }

    @Test
    fun `iotId 为空保存报校验错误`() = runTest {
        fillValidForm()
        viewModel.accept(SettingsIntent.ConnectionFieldChanged(ConnectionField.IOT_ID, ""))
        viewModel.accept(SettingsIntent.Save)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.validationError)
        assertEquals(0, settingsRepo.savedCount)
    }

    @Test
    fun `体重越界保存报校验错误`() = runTest {
        fillValidForm()
        viewModel.accept(SettingsIntent.WeightFieldChanged(WeightField.BODY_WEIGHT, "500"))
        viewModel.accept(SettingsIntent.Save)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.validationError)
    }

    @Test
    fun `百分比非单调保存报校验错误`() = runTest {
        fillValidForm()
        viewModel.accept(SettingsIntent.WeightFieldChanged(WeightField.P25, "75"))
        viewModel.accept(SettingsIntent.WeightFieldChanged(WeightField.P50, "25"))
        viewModel.accept(SettingsIntent.Save)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.validationError)
    }

    @Test
    fun `合法表单保存成功并提示`() = runTest {
        fillValidForm()
        viewModel.accept(SettingsIntent.WeightFieldChanged(WeightField.BODY_WEIGHT, "70"))
        viewModel.accept(SettingsIntent.Save)
        advanceUntilIdle()
        assertNull(viewModel.state.value.validationError)
        assertEquals(2, settingsRepo.savedCount) // settings + weightPercentages
        assertEquals(70.0, settingsRepo.weightsFlow.value.first, 0.0)
        viewModel.effects.test {
            assertEquals(SettingsEffect.ShowMessage("已保存"), awaitItem())
        }
    }

    @Test
    fun `测试连接成功提示`() = runTest {
        fillValidForm()
        viewModel.accept(SettingsIntent.TestConnection)
        advanceUntilIdle()
        assertEquals(1, gateway.connectCallCount)
        assertEquals(ConnectionState.Disconnected, gateway.connectionState.value)
        viewModel.effects.test {
            assertEquals(SettingsEffect.ShowMessage("连接成功"), awaitItem())
        }
    }

    @Test
    fun `测试连接失败提示错误`() = runTest {
        fillValidForm()
        gateway.failWith =
            com.dfrobot.rehab.core.mqtt.MqttConnectionException("网络不可达,请检查网络或服务器地址")
        viewModel.accept(SettingsIntent.TestConnection)
        advanceUntilIdle()
        viewModel.effects.test {
            val effect = awaitItem() as SettingsEffect.ShowMessage
            assertTrue(effect.message.contains("网络不可达"))
        }
    }

    @Test
    fun `表单不完整时测试连接直接报校验错误`() = runTest {
        viewModel.accept(SettingsIntent.TestConnection)
        advanceUntilIdle()
        assertEquals(0, gateway.connectCallCount)
        assertNotNull(viewModel.state.value.validationError)
    }
}
