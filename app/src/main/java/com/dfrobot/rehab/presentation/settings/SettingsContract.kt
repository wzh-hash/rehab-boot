package com.dfrobot.rehab.presentation.settings

import com.dfrobot.rehab.domain.model.DeviceSettings

data class SettingsUiState(
    val settings: DeviceSettings = DeviceSettings(),
    val bodyWeightKg: String = "60",
    val p25: String = "25",
    val p50: String = "50",
    val p75: String = "75",
    val connectionEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: String? = null,
)

sealed interface SettingsIntent {
    data class ConnectionFieldChanged(val field: ConnectionField, val value: String) : SettingsIntent
    data class WeightFieldChanged(val field: WeightField, val value: String) : SettingsIntent
    data object Save : SettingsIntent
    data object ToggleConnection : SettingsIntent
    data object TestConnection : SettingsIntent
}

enum class ConnectionField { HOST, PORT, IOT_ID, IOT_PWD, TOPIC }

enum class WeightField { BODY_WEIGHT, P25, P50, P75 }

sealed interface SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect
}
