package com.dfrobot.rehab.data.mqtt

import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.data.protocol.ProtocolCodec
import com.dfrobot.rehab.domain.model.PressureSample
import com.dfrobot.rehab.domain.model.Thresholds
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 遥测数据源接口:供 presentation 层依赖,便于 fake 测试。 */
interface TelemetryDataSource {
    fun observeSamples(): Flow<PressureSample>
    suspend fun publishThresholds(thresholds: Thresholds)
}

@Singleton
class MqttTelemetryDataSource @Inject constructor(
    private val manager: MqttConnectionManager,
    private val settingsRepository: DeviceSettingsRepository,
) : TelemetryDataSource {

    override fun observeSamples(): Flow<PressureSample> =
        manager.inboundMessages
            .filterIsInstance<ProtocolCodec.MqttMessage.Data>()
            .map { it.sample }

    override suspend fun publishThresholds(thresholds: Thresholds) {
        val settings = settingsRepository.settings.first()
        manager.publishConfig(settings.topic, thresholds)
    }
}
