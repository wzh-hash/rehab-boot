package com.dfrobot.rehab.e2e

import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.data.mqtt.MqttTelemetryDataSource
import com.dfrobot.rehab.domain.SessionTracker
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import com.dfrobot.rehab.testutil.TestBroker
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 端到端闭环(短码协议):嵌入式 broker 模拟掌控板固件,验证
 * 连接 → 指令下发("A")→ 设备事件上报(hello/WA/plus×3)→ 会话落库 全链路。
 */
class EndToEndFlowTest {

    private class FakeSettingsRepo : DeviceSettingsRepository {
        val settingsFlow = MutableStateFlow(
            DeviceSettings(
                host = "127.0.0.1",
                port = brokerPort,
                iotId = "test-user",
                iotPwd = "test-pwd",
                topic = "wIOqDXyDg",
            ),
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

    @Test(timeout = 60_000)
    fun `完整闭环_指令下发与事件驱动会话落库`() = runBlocking {
        val manager = MqttConnectionManager()
        val settingsRepo = FakeSettingsRepo()
        val dataSource = MqttTelemetryDataSource(manager, settingsRepo)
        val tracker = SessionTracker()
        val sessionRepo = FakeSessionRepo()

        // 1. 连接
        manager.connect(settingsRepo.settings.first())
        awaitCondition { manager.connectionState.value == ConnectionState.Connected }

        // 2. 设备端客户端:订阅 topic,准备接收指令
        val device = deviceClient()
        device.subscribeWith()
            .topicFilter("wIOqDXyDg")
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
        val publishes = device.publishes(MqttGlobalPublishFilter.ALL)

        // 3. App 下发 25% 训练指令
        dataSource.publishCommand(TrainingRatio.T25)
        val command = publishes.receive(10, TimeUnit.SECONDS).orElseThrow()
            .payloadAsBytes.toString(Charsets.UTF_8)
        assertEquals("A", command)
        // retained 语义由 manager 单测覆盖,此处仅断言指令内容

        // 4. 设备上报事件流:hello → WA → plus ×3
        val collected = mutableListOf<DeviceEvent>()
        val collectJob = launch {
            dataSource.observeEvents().take(5).toList(collected)
        }
        // 让收集器完成订阅,避免首帧在订阅前被丢弃
        kotlinx.coroutines.delay(100)
        for (payload in listOf("hello", "WA", "plus", "plus", "plus")) {
            device.publishWith()
                .topic("wIOqDXyDg")
                .qos(MqttQos.AT_MOST_ONCE)
                .payload(payload.toByteArray(Charsets.UTF_8))
                .send()
            kotlinx.coroutines.delay(10)
        }
        withTimeout(10_000) { collectJob.join() }

        // 5. 事件解析正确
        assertEquals(
            listOf(DeviceEvent.Hello, DeviceEvent.RepReached, DeviceEvent.RepCompleted,
                DeviceEvent.RepCompleted, DeviceEvent.RepCompleted),
            collected,
        )

        // 6. 事件驱动会话:3 次 plus 完成
        tracker.start(TrainingRatio.T25)
        for (event in collected) {
            when (event) {
                DeviceEvent.Hello, DeviceEvent.RepReached -> {}
                DeviceEvent.RepCompleted -> tracker.onRepCompleted()
            }
        }
        val session = tracker.finish()
        assertEquals(3, session.repsCompleted)
        assertEquals(true, session.completed)
        sessionRepo.saveSession(session)
        assertEquals(1, sessionRepo.saved.size)
        assertEquals(TrainingRatio.T25, sessionRepo.saved[0].ratio)

        // 7. 乱帧计数
        device.publishWith()
            .topic("wIOqDXyDg")
            .qos(MqttQos.AT_MOST_ONCE)
            .payload("garbage".toByteArray())
            .send()
        awaitCondition { manager.invalidFrameCount.value >= 1 }
        assertEquals(1, manager.invalidFrameCount.value)

        // 8. 清理
        publishes.close()
        device.disconnect()
        manager.disconnect()
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    private fun deviceClient(): Mqtt3BlockingClient =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier("device-e2e")
            .serverHost("127.0.0.1")
            .serverPort(brokerPort)
            .buildBlocking()
            .also { it.connect() }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) {
                kotlinx.coroutines.delay(50)
            }
        }
    }

    companion object {
        private var brokerPort: Int = 0

        @BeforeClass
        @JvmStatic
        fun startBroker() {
            TestBroker.start()
            brokerPort = TestBroker.port
        }

        @AfterClass
        @JvmStatic
        fun stopBroker() {
            TestBroker.stop()
        }
    }
}
