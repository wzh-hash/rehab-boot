package com.dfrobot.rehab.presentation.settings

import com.dfrobot.rehab.domain.model.DeviceSettings

data class SettingsUiState(
    val settings: DeviceSettings = DeviceSettings(),
    val connectionEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: String? = null,
)

sealed interface SettingsIntent {
    data class ConnectionFieldChanged(val field: ConnectionField, val value: String) : SettingsIntent
    data object Save : SettingsIntent
    data object ToggleConnection : SettingsIntent
    data object TestConnection : SettingsIntent
}

enum class ConnectionField { HOST, PORT, IOT_ID, IOT_PWD, TOPIC }

sealed interface SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect
}
