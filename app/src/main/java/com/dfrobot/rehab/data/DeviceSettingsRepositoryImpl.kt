package com.dfrobot.rehab.data

import com.dfrobot.rehab.data.local.DeviceSettingsStore
import com.dfrobot.rehab.domain.ThresholdCalculator
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSettingsRepositoryImpl @Inject constructor(
    private val store: DeviceSettingsStore,
) : DeviceSettingsRepository {

    override val settings: Flow<DeviceSettings> = store.settingsFlow

    override suspend fun saveSettings(settings: DeviceSettings) {
        require(settings.isComplete()) { "连接配置不完整" }
        store.saveSettings(settings)
    }

    override val weightPercentages: Flow<Pair<Double, Triple<Int, Int, Int>>> =
        store.weightPercentagesFlow

    override suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) {
        // 校验失败抛 IllegalArgumentException,由调用层转中文提示
        ThresholdCalculator.fromPercentages(bodyWeightKg, p25, p50, p75)
        store.saveWeightPercentages(bodyWeightKg, p25, p50, p75)
    }
}
