package com.dfrobot.rehab.core.mqtt

import com.dfrobot.rehab.data.protocol.ProtocolCodec
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.Thresholds
import com.dfrobot.rehab.testutil.TestBroker
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class MqttConnectionManagerTest {

    private lateinit var manager: MqttConnectionManager

    private val settings = DeviceSettings(
        host = "127.0.0.1",
        port = brokerPort,
        iotId = "test-user",
        iotPwd = "test-pwd",
        topic = "BJpHJt1VW",
    )

    @Before
    fun setUp() {
        manager = MqttConnectionManager()
    }

    private fun deviceClient(): Mqtt3BlockingClient =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier("device-test")
            .serverHost("127.0.0.1")
            .serverPort(brokerPort)
            .buildBlocking()
            .also { it.connect() }

    @Test(timeout = 15_000)
    fun `连接成功后状态为 Connected`() = runBlocking {
        manager.connect(settings)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
        manager.disconnect()
    }

    @Test(timeout = 15_000)
    fun `publishConfig 后设备端收到 retained 配置帧`() = runBlocking {
        manager.connect(settings)
        val device = deviceClient()
        device.subscribeWith()
            .topicFilter(settings.topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
        val publishes = device.publishes(MqttGlobalPublishFilter.ALL)

        manager.publishConfig(settings.topic, Thresholds(15.0, 30.0, 45.0))

        val publish = publishes.receive(5, TimeUnit.SECONDS).orElseThrow()
        val actual = publish.payloadAsBytes.toString(Charsets.UTF_8)
        val expected = ProtocolCodec.encodeConfig(Thresholds(15.0, 30.0, 45.0))
            .toString(Charsets.UTF_8)
        assertEquals(expected, actual)
        publishes.close()
        device.disconnect()
        manager.disconnect()
    }

    @Test(timeout = 15_000)
    fun `设备发布 data 帧后 inboundMessages 收到`() = runBlocking {
        manager.connect(settings)
        val device = deviceClient()
        device.publishWith()
            .topic(settings.topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload("""{"type":"data","p":9.5,"ts":0}""".toByteArray())
            .send()

        val message = withTimeout(5_000) { manager.inboundMessages.first() }
        val data = message as ProtocolCodec.MqttMessage.Data
        assertEquals(9.5, data.sample.valueKg, 0.0)
        device.disconnect()
        manager.disconnect()
    }

    @Test(timeout = 15_000)
    fun `设备发布乱帧被丢弃并计数`() = runBlocking {
        manager.connect(settings)
        val device = deviceClient()
        device.publishWith()
            .topic(settings.topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload("garbage".toByteArray())
            .send()
        device.publishWith()
            .topic(settings.topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload("""{"type":"nope"}""".toByteArray())
            .send()

        withTimeout(5_000) {
            while (manager.invalidFrameCount.value < 2) {
                kotlinx.coroutines.delay(50)
            }
        }
        assertEquals(2, manager.invalidFrameCount.value)
        device.disconnect()
        manager.disconnect()
    }

    @Test(timeout = 15_000)
    fun `连接不存在的端口抛 MqttConnectionException`() = runBlocking {
        val freePort = ServerSocket(0).use { it.localPort }
        val bad = settings.copy(port = freePort)
        val error = try {
            manager.connect(bad)
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(error is MqttConnectionException)
        assertTrue(error!!.message!!.isNotBlank())
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test(timeout = 30_000)
    fun `被动断开后自动重连`() = runBlocking {
        manager.connect(settings)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)

        // 模拟网络波动:断开底层连接(不调用 disconnect,shouldStayConnected 仍为 true)
        manager.killConnectionForTest()

        withTimeout(15_000) {
            while (manager.connectionState.value != ConnectionState.Connected) {
                kotlinx.coroutines.delay(100)
            }
        }
        manager.disconnect()
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)

        // disconnect 后不再自动重连
        kotlinx.coroutines.delay(2_500)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
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
