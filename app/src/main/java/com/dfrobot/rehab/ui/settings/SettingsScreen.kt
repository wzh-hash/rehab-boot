package com.dfrobot.rehab.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfrobot.rehab.BuildConfig
import com.dfrobot.rehab.R
import com.dfrobot.rehab.core.service.MqttConnectionService
import com.dfrobot.rehab.presentation.settings.ConnectionField
import com.dfrobot.rehab.presentation.settings.SettingsEffect
import com.dfrobot.rehab.presentation.settings.SettingsIntent
import com.dfrobot.rehab.presentation.settings.SettingsUiState
import com.dfrobot.rehab.presentation.settings.SettingsViewModel
import com.dfrobot.rehab.presentation.settings.WeightField

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SettingsScreen(
        state = state,
        onIntent = viewModel::accept,
        snackbarHostState = snackbarHostState,
        onConnectionToggled = { enabled ->
            viewModel.accept(SettingsIntent.ToggleConnection)
            if (enabled) {
                // Android 13+ 前台服务通知需运行时权限
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                MqttConnectionService.start(context)
            } else {
                MqttConnectionService.stop(context)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onConnectionToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.validationError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---- 平台连接 ----
            SectionTitle(stringResource(R.string.settings_connection_section))
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.settings.host,
                        onValueChange = { onIntent(SettingsIntent.ConnectionFieldChanged(ConnectionField.HOST, it)) },
                        label = { Text(stringResource(R.string.settings_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.settings.port.toString(),
                        onValueChange = { onIntent(SettingsIntent.ConnectionFieldChanged(ConnectionField.PORT, it)) },
                        label = { Text(stringResource(R.string.settings_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.settings.iotId,
                        onValueChange = { onIntent(SettingsIntent.ConnectionFieldChanged(ConnectionField.IOT_ID, it)) },
                        label = { Text(stringResource(R.string.settings_iot_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.settings.iotPwd,
                        onValueChange = { onIntent(SettingsIntent.ConnectionFieldChanged(ConnectionField.IOT_PWD, it)) },
                        label = { Text(stringResource(R.string.settings_iot_pwd)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.settings.topic,
                        onValueChange = { onIntent(SettingsIntent.ConnectionFieldChanged(ConnectionField.TOPIC, it)) },
                        label = { Text(stringResource(R.string.settings_topic)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onIntent(SettingsIntent.TestConnection) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.settings_test_connection))
                        }
                        Button(
                            onClick = { onIntent(SettingsIntent.Save) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving,
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_enable_connection),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = state.connectionEnabled,
                            onCheckedChange = onConnectionToggled,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.settings_plaintext_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            // ---- 训练参数 ----
            SectionTitle(stringResource(R.string.settings_training_section))
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.bodyWeightKg,
                        onValueChange = { onIntent(SettingsIntent.WeightFieldChanged(WeightField.BODY_WEIGHT, it)) },
                        label = { Text(stringResource(R.string.settings_body_weight)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.p25,
                            onValueChange = { onIntent(SettingsIntent.WeightFieldChanged(WeightField.P25, it)) },
                            label = { Text(stringResource(R.string.settings_p25)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.p50,
                            onValueChange = { onIntent(SettingsIntent.WeightFieldChanged(WeightField.P50, it)) },
                            label = { Text(stringResource(R.string.settings_p50)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.p75,
                            onValueChange = { onIntent(SettingsIntent.WeightFieldChanged(WeightField.P75, it)) },
                            label = { Text(stringResource(R.string.settings_p75)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- 关于 ----
            SectionTitle(stringResource(R.string.settings_about_section))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}
