package com.dfrobot.rehab.data

import com.dfrobot.rehab.data.local.DeviceSettingsStore
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
}
