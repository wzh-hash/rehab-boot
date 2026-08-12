package com.dfrobot.rehab.core.mqtt

import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.testutil.TestBroker
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import kotlinx.coroutines.async
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
    fun `publishCommand 后设备端收到指令且 retained 为 false`() = runBlocking {
        manager.connect(settings)
        val device = deviceClient()
        device.subscribeWith()
            .topicFilter(settings.topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
        val publishes = device.publishes(MqttGlobalPublishFilter.ALL)

        manager.publishCommand(settings.topic, "A")

        val publish = publishes.receive(5, TimeUnit.SECONDS).orElseThrow()
        assertEquals("A", publish.payloadAsBytes.toString(Charsets.UTF_8))
        assertEquals(false, publish.isRetain)
        publishes.close()
        device.disconnect()
        manager.disconnect()
    }

    @Test(timeout = 15_000)
    fun `设备发布 hello 帧后 inboundMessages 收到`() = runBlocking {
        manager.connect(settings)
        val device = deviceClient()
        // 先订阅再发布,避免消息在订阅前被 SharedFlow 丢弃
        val received = async { manager.inboundMessages.first() }
        kotlinx.coroutines.yield() // 让收集器完成订阅
        device.publishWith()
            .topic(settings.topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload("hello".toByteArray())
            .send()

        val message = withTimeout(5_000) { received.await() }
        assertEquals(com.dfrobot.rehab.domain.model.DeviceEvent.Hello, message)
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
            .payload("wa".toByteArray())
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
