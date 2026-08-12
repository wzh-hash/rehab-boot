package com.dfrobot.rehab.domain.repository

import com.dfrobot.rehab.domain.model.DeviceSettings
import kotlinx.coroutines.flow.Flow

interface DeviceSettingsRepository {

    /** 连接配置流(默认值即 DFRobot 平台默认参数)。 */
    val settings: Flow<DeviceSettings>

    suspend fun saveSettings(settings: DeviceSettings)
}
