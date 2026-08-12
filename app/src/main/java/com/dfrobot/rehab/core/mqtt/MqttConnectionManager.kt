package com.dfrobot.rehab.core.mqtt

import com.dfrobot.rehab.data.protocol.ProtocolCodec
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.model.Thresholds
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 应用内唯一持有 MQTT 客户端的地方(深模块):
 * 连接生命周期、指数退避自动重连、入站消息解码分发、配置发布。
 *
 * 前台服务只是保活载体,UI 经 [connectionState]/[inboundMessages]/[errorEvents] 直接观察本单例。
 * 注意:以新配置重连前须先 [disconnect](设置页保存通过重启服务完成)。
 */
@Singleton
class MqttConnectionManager @Inject constructor() : ConnectionGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _inboundMessages = MutableSharedFlow<ProtocolCodec.MqttMessage>(
        extraBufferCapacity = 64,
    )
    val inboundMessages: SharedFlow<ProtocolCodec.MqttMessage> = _inboundMessages.asSharedFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _invalidFrameCount = MutableStateFlow(0)
    override val invalidFrameCount: StateFlow<Int> = _invalidFrameCount.asStateFlow()

    private var client: Mqtt3AsyncClient? = null
    private var shouldStayConnected = false
    private var reconnectJob: Job? = null
    private var currentTopic: String? = null
    private var lastSettings: DeviceSettings? = null

    /** 平台不校验 ClientID,固定即可。HiveMQ 1.3.x 的断连监听在 builder 上注册。 */
    private fun buildClient(settings: DeviceSettings): Mqtt3AsyncClient {
        val builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier("rehab-boot-app")
            .serverHost(settings.host)
            .serverPort(settings.port)
        builder.addDisconnectedListener { _ ->
            // 被动断开(网络波动/broker 重启):保持 shouldStayConnected 则自动重连
            if (shouldStayConnected) {
                _connectionState.value = ConnectionState.Disconnected
                scheduleReconnect()
            }
        }
        return builder.buildAsync()
    }

    override suspend fun connect(settings: DeviceSettings) {
        if (_connectionState.value == ConnectionState.Connected && client != null) return
        disconnectQuietly()
        lastSettings = settings
        currentTopic = settings.topic
        val newClient = buildClient(settings)
        client = newClient
        _connectionState.value = ConnectionState.Connecting
        shouldStayConnected = true

        try {
            await(newClient.connectWith()
                .cleanSession(true)
                .keepAlive(30)
                .simpleAuth()
                .username(settings.iotId)
                .password(ByteBuffer.wrap(settings.iotPwd.toByteArray(StandardCharsets.UTF_8)))
                .applySimpleAuth()
                .send())
            _connectionState.value = ConnectionState.Connected
            subscribeAndAwait(newClient, settings.topic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MqttConnectionException) {
            // 主动连接失败:不进入自动重连(由调用方决定是否重试),回滚状态
            shouldStayConnected = false
            _connectionState.value = ConnectionState.Disconnected
            client = null
            throw e
        } catch (e: Exception) {
            shouldStayConnected = false
            _connectionState.value = ConnectionState.Disconnected
            client = null
            throw MqttConnectionException(e.toUserMessage())
        }
    }

    private suspend fun subscribeAndAwait(client: Mqtt3AsyncClient, topic: String) {
        await(client.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                val message = ProtocolCodec.decodeIncoming(publish.payloadAsBytes)
                if (message != null) {
                    _inboundMessages.tryEmit(message)
                } else {
                    _invalidFrameCount.value = _invalidFrameCount.value + 1
                }
            }
            .send())
    }

    override suspend fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectQuietly()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun disconnectQuietly() {
        val old = client
        client = null
        if (old != null) {
            runCatching {
                old.disconnect().get(2, TimeUnit.SECONDS)
            }
        }
    }

    /** 发布阈值配置:QoS 1 + retained,设备重连/订阅即收到最新阈值。 */
    suspend fun publishConfig(topic: String, thresholds: Thresholds) {
        val c = client ?: throw MqttConnectionException("尚未连接,无法发送配置")
        await(c.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .payload(ProtocolCodec.encodeConfig(thresholds))
            .send())
    }

    /** 网络恢复时调用,提前触发重连。 */
    fun setNetworkRetryFlag() {
        if (!shouldStayConnected) return
        val job = reconnectJob
        if (job != null && job.isActive) {
            job.cancel()
            reconnectJob = null
        }
        if (_connectionState.value != ConnectionState.Connected) {
            scope.launch {
                delay(500)
                if (shouldStayConnected && _connectionState.value != ConnectionState.Connected) {
                    attemptReconnect()
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var backoffMs = 1_000L
            while (shouldStayConnected && _connectionState.value != ConnectionState.Connected) {
                delay(backoffMs)
                if (!shouldStayConnected || _connectionState.value == ConnectionState.Connected) break
                attemptReconnect()
                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
            }
        }
    }

    private suspend fun attemptReconnect() {
        val settings = lastSettings ?: return
        if (!shouldStayConnected) return
        val newClient = buildClient(settings)
        client = newClient
        _connectionState.value = ConnectionState.Connecting
        try {
            await(newClient.connectWith()
                .cleanSession(true)
                .keepAlive(30)
                .simpleAuth()
                .username(settings.iotId)
                .password(ByteBuffer.wrap(settings.iotPwd.toByteArray(StandardCharsets.UTF_8)))
                .applySimpleAuth()
                .send())
            _connectionState.value = ConnectionState.Connected
            subscribeAndAwait(newClient, settings.topic)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 重连失败静默,由循环继续退避
            runCatching { newClient.disconnect() }
            if (client === newClient) client = null
        }
    }

    private suspend fun <T> await(future: CompletableFuture<T>): T =
        suspendCancellableCoroutine { cont ->
            future.whenComplete { value, error ->
                if (error != null) {
                    cont.resumeWithException(error.toConnectionFailure())
                } else {
                    cont.resume(value)
                }
            }
            cont.invokeOnCancellation { future.cancel(true) }
        }

    private fun Throwable.toUserMessage(): String = when (this) {
        is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            "网络不可达,请检查网络或服务器地址"

        else -> message?.takeIf { it.isNotBlank() } ?: "连接失败"
    }

    private fun Throwable.toConnectionFailure(): Throwable = when {
        this is ExecutionException && cause != null -> cause!!.toConnectionFailure()
        this is MqttConnectionException -> this
        this is ConnectException || this is NoRouteToHostException || this is UnknownHostException ->
            MqttConnectionException("网络不可达,请检查网络或服务器地址")

        this is com.hivemq.client.mqtt.exceptions.ConnectionClosedException ||
            this is com.hivemq.client.mqtt.exceptions.ConnectionFailedException ->
            MqttConnectionException("连接被拒绝,请检查 Iot_id / Iot_pwd 与服务器")

        this is TimeoutException -> MqttConnectionException("连接超时,请检查网络")
        else -> MqttConnectionException(message ?: "连接失败")
    }

    /** 仅测试钩子:断开底层连接以验证自动重连(生产代码不使用)。 */
    internal suspend fun killConnectionForTest() {
        client?.let {
            runCatching { it.disconnect().get(2, TimeUnit.SECONDS) }
        }
    }
}
