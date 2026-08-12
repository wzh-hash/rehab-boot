package com.dfrobot.rehab.domain.repository

import com.dfrobot.rehab.domain.model.DeviceSettings
import kotlinx.coroutines.flow.Flow

interface DeviceSettingsRepository {

    /** 连接配置流(默认值即 DFRobot 平台默认参数)。 */
    val settings: Flow<DeviceSettings>

    suspend fun saveSettings(settings: DeviceSettings)

    /** 体重(kg)与三档百分比,emit 时已经 ThresholdCalculator 校验。 */
    val weightPercentages: Flow<Pair<Double, Triple<Int, Int, Int>>>

    suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int)
}
