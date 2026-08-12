package com.dfrobot.rehab.presentation.settings

import com.dfrobot.rehab.domain.model.DeviceSettings

data class SettingsUiState(
    val settings: DeviceSettings = DeviceSettings(),
    val connectionEnabled: Boolean = false,
    val isSaving: Boolean = false,
    /** 表单校验错误(资源 ID)。 */
    val validationErrorRes: Int? = null,
)

sealed interface SettingsIntent {
    data class ConnectionFieldChanged(val field: ConnectionField, val value: String) : SettingsIntent
    data object Save : SettingsIntent
    data object ToggleConnection : SettingsIntent
    data object TestConnection : SettingsIntent
}

enum class ConnectionField { HOST, PORT, IOT_ID, IOT_PWD, TOPIC }

sealed interface SettingsEffect {
    /** 静态文案(资源 ID)。 */
    data class ShowMessage(val messageRes: Int) : SettingsEffect

    /** 运行时动态文案(如连接错误详情)。 */
    data class ShowRawMessage(val message: String) : SettingsEffect
}
