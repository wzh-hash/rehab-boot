package com.dfrobot.rehab.e2e

import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.data.mqtt.MqttTelemetryDataSource
import com.dfrobot.rehab.data.protocol.ProtocolCodec
import com.dfrobot.rehab.domain.SessionTracker
import com.dfrobot.rehab.domain.ThresholdCalculator
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.PressureSample
import com.dfrobot.rehab.domain.model.Thresholds
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import com.dfrobot.rehab.testutil.TestBroker
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import kotlinx.coroutines.channels.Channel
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

/**
 * 端到端闭环:嵌入式 broker 模拟设备,验证
 * 连接 → 设备上报 → app 实时接收 → 会话统计 → 汇总落库 → 阈值下发(retained)全链路。
 */
class EndToEndFlowTest {

    private class FakeSettingsRepo : DeviceSettingsRepository {
        val settingsFlow = MutableStateFlow(
            DeviceSettings(
                host = "127.0.0.1",
                port = brokerPort,
                iotId = "test-user",
                iotPwd = "test-pwd",
                topic = "BJpHJt1VW",
            ),
        )

        override val settings: Flow<DeviceSettings> = settingsFlow.asStateFlow()
        override suspend fun saveSettings(settings: DeviceSettings) {
            settingsFlow.value = settings
        }

        override val weightPercentages: Flow<Pair<Double, Triple<Int, Int, Int>>> =
            MutableStateFlow(60.0 to Triple(25, 50, 75)).asStateFlow()

        override suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) {
            // no-op
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
    fun `完整闭环_设备上报到会话落库与阈值下发`() = runBlocking {
        val settings = DeviceSettings(
            host = "127.0.0.1",
            port = brokerPort,
            iotId = "test-user",
            iotPwd = "test-pwd",
            topic = "BJpHJt1VW",
        )
        val manager = MqttConnectionManager()
        val settingsRepo = FakeSettingsRepo()
        val dataSource = MqttTelemetryDataSource(manager, settingsRepo)
        val tracker = SessionTracker()
        val sessionRepo = FakeSessionRepo()

        // 1. 连接
        manager.connect(settings)
        awaitCondition { manager.connectionState.value == ConnectionState.Connected }

        // 2. 设备端客户端:订阅 topic,准备接收配置
        val device = deviceClient()
        device.subscribeWith()
            .topicFilter(settings.topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
        val publishes = device.publishes(MqttGlobalPublishFilter.ALL)

        // 3. 训练会话:开始,设备上报 20 帧(2Hz 模拟)
        tracker.start()
        val collected = mutableListOf<PressureSample>()
        val collectJob = launch {
            dataSource.observeSamples().take(20).toList(collected)
        }
        for (i in 1..20) {
            val value = 5.0 + i * 1.5 // 5.0, 6.5, ..., 33.5
            device.publishWith()
                .topic(settings.topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .payload(
                    """{"type":"data","p":$value,"ts":${1_000L * i}}"""
                        .toByteArray(Charsets.UTF_8),
                )
                .send()
            kotlinx.coroutines.delay(10)
        }
        collectJob.join()

        // 4. 会话统计正确
        assertEquals(20, collected.size)
        for (sample in collected) tracker.ingest(sample)
        val session = tracker.finish()
        assertEquals(35.0, session.peakPressureKg, 0.0) // i=20 → 5.0+20*1.5=35.0
        assertEquals(20.75, session.avgPressureKg, 0.01) // (6.5+35.0)/2
        sessionRepo.saveSession(session)
        assertEquals(1, sessionRepo.saved.size)

        // 5. 阈值下发:设备端收到 retained config 帧
        // (publishes 流会先收到 20 条 data 帧,循环丢弃直到 config 帧)
        val thresholds = ThresholdCalculator.fromPercentages(60.0, 25, 50, 75)
        dataSource.publishThresholds(thresholds)
        val expected = ProtocolCodec.encodeConfig(thresholds).toString(Charsets.UTF_8)
        val actual = withTimeout(10_000) {
            var payload: String? = null
            while (payload == null || !payload.contains("\"type\":\"config\"")) {
                val publish = publishes.receive(2, java.util.concurrent.TimeUnit.SECONDS)
                    .orElse(null) ?: break
                payload = publish.payloadAsBytes.toString(Charsets.UTF_8)
            }
            payload
        }
        assertEquals(expected, actual)

        // 6. 清理
        publishes.close()
        device.disconnect()
        manager.disconnect()
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test(timeout = 60_000)
    fun `会话统计平均值与峰值精确`() {
        val tracker = SessionTracker()
        tracker.start()
        tracker.ingest(PressureSample(5.0, 1L))
        tracker.ingest(PressureSample(10.0, 2L))
        tracker.ingest(PressureSample(15.0, 3L))
        val session = tracker.finish()
        assertEquals(10.0, session.avgPressureKg, 0.0)
        assertEquals(15.0, session.peakPressureKg, 0.0)
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
