package com.dfrobot.rehab.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: DeviceSettingsRepository,
    private val connectionGateway: ConnectionGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    private var isTestingConnection = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun accept(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ConnectionFieldChanged -> {
                val current = _state.value.settings
                val updated = when (intent.field) {
                    ConnectionField.HOST -> current.copy(host = intent.value.trim())
                    ConnectionField.PORT -> current.copy(port = intent.value.trim().toIntOrNull() ?: 0)
                    ConnectionField.IOT_ID -> current.copy(iotId = intent.value.trim())
                    ConnectionField.IOT_PWD -> current.copy(iotPwd = intent.value)
                    ConnectionField.TOPIC -> current.copy(topic = intent.value.trim())
                }
                _state.update { it.copy(settings = updated, validationError = null) }
            }

            SettingsIntent.Save -> save()
            SettingsIntent.ToggleConnection -> toggleConnection()
            SettingsIntent.TestConnection -> testConnection()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.isSaving) return
        val validation = validate(s)
        if (validation != null) {
            _state.update { it.copy(validationError = validation) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                settingsRepository.saveSettings(s.settings)
            }.onFailure { e ->
                _state.update {
                    it.copy(isSaving = false, validationError = e.message ?: "保存失败")
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false) }
            _effects.send(SettingsEffect.ShowMessage("已保存"))
        }
    }

    private fun toggleConnection() {
        val enabled = !_state.value.connectionEnabled
        if (enabled) {
            val validation = validate(_state.value)
            if (validation != null) {
                _state.update { it.copy(validationError = validation) }
                return
            }
        }
        viewModelScope.launch {
            settingsRepository.saveSettings(_state.value.settings)
            _state.update { it.copy(connectionEnabled = enabled) }
            _effects.send(
                SettingsEffect.ShowMessage(
                    if (enabled) "连接已启用,后台服务已启动" else "连接已关闭,后台服务已停止",
                ),
            )
        }
    }

    private fun testConnection() {
        if (isTestingConnection) return
        val s = _state.value
        val validation = validate(s)
        if (validation != null) {
            _state.update { it.copy(validationError = validation) }
            return
        }
        viewModelScope.launch {
            isTestingConnection = true
            try {
                connectionGateway.connect(s.settings)
                connectionGateway.disconnect()
                _effects.send(SettingsEffect.ShowMessage("连接成功"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(SettingsEffect.ShowMessage(e.message ?: "连接失败"))
            } finally {
                isTestingConnection = false
            }
        }
    }

    private fun validate(s: SettingsUiState): String? {
        if (!s.settings.isComplete()) {
            return "请填写完整的连接信息(服务器/端口/账号/密码/Topic)"
        }
        return null
    }

    private fun MutableStateFlow<SettingsUiState>.update(transform: (SettingsUiState) -> SettingsUiState) {
        value = transform(value)
    }
}
