package com.dfrobot.rehab.data.mqtt

import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.data.protocol.FirmwareCodec
import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 设备事件数据源接口:供 presentation 层依赖,便于 fake 测试。 */
interface TelemetryDataSource {
    fun observeEvents(): Flow<DeviceEvent>
    suspend fun publishCommand(ratio: TrainingRatio)
    suspend fun publishHelloTest()
}

@Singleton
class MqttTelemetryDataSource @Inject constructor(
    private val manager: MqttConnectionManager,
    private val settingsRepository: DeviceSettingsRepository,
) : TelemetryDataSource {

    override fun observeEvents(): Flow<DeviceEvent> = manager.inboundMessages

    override suspend fun publishCommand(ratio: TrainingRatio) {
        val settings = settingsRepository.settings.first()
        manager.publishCommand(settings.topic, FirmwareCodec.encodeCommand(ratio).toString(Charsets.UTF_8))
    }

    override suspend fun publishHelloTest() {
        val settings = settingsRepository.settings.first()
        manager.publishCommand(settings.topic, FirmwareCodec.encodeHelloTest().toString(Charsets.UTF_8))
    }
}
