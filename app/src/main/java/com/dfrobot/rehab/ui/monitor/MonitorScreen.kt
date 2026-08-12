package com.dfrobot.rehab.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfrobot.rehab.R
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.presentation.monitor.MonitorEffect
import com.dfrobot.rehab.presentation.monitor.MonitorIntent
import com.dfrobot.rehab.presentation.monitor.MonitorUiState
import com.dfrobot.rehab.presentation.monitor.MonitorViewModel
import java.util.Locale

@Composable
fun MonitorRoute(
    viewModel: MonitorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MonitorEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    MonitorScreen(
        state = state,
        onIntent = viewModel::accept,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun MonitorScreen(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
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
            ConnectionBanner(state.connectionState, onIntent)
            WeightDisplay(state)
            state.thresholds?.let { thresholds ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.monitor_thresholds),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ThresholdProgress(
                            thresholds = thresholds,
                            currentValueKg = state.livePressureKg,
                        )
                    }
                }
            }
            TrendCard(state)
            SessionControls(state, onIntent)
            if (state.invalidFrameCount > 0) {
                Text(
                    stringResource(R.string.monitor_invalid_frames, state.invalidFrameCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    connectionState: ConnectionState,
    onIntent: (MonitorIntent) -> Unit,
) {
    val (text, color) = when (connectionState) {
        ConnectionState.Connected ->
            stringResource(R.string.monitor_connected) to Color(0xFF16A34A)

        ConnectionState.Connecting ->
            stringResource(R.string.monitor_connecting) to Color(0xFFD97706)

        ConnectionState.Disconnected ->
            stringResource(R.string.monitor_disconnected) to Color(0xFFDC2626)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable { onIntent(MonitorIntent.RetryConnect) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WeightDisplay(state: MonitorUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.monitor_current_weight),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.livePressureKg != null) {
                String.format(Locale.US, "%.1f kg", state.livePressureKg)
            } else {
                stringResource(R.string.monitor_no_data)
            },
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (state.livePressureKg != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (state.livePressureKg == null) {
            Text(
                stringResource(R.string.monitor_waiting_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendCard(
    state: MonitorUiState,
) {
    val buffer = PressureChart.rememberBuffer()
    val now = state.livePressureAtMillis ?: System.currentTimeMillis()
    state.livePressureKg?.let { buffer.add(now, it.toFloat()) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.monitor_trend_30s),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PressureChart(
                values = buffer.values(now),
                maxValue = state.thresholds?.p75Kg?.toFloat()?.times(1.2f) ?: 10f,
            )
        }
    }
}

@Composable
private fun SessionControls(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.phase) {
            SessionPhase.Idle -> Button(
                onClick = { onIntent(MonitorIntent.StartSession) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.monitor_start))
            }

            SessionPhase.Running -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onIntent(MonitorIntent.PauseSession) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(R.string.monitor_pause))
                }
                Button(
                    onClick = { onIntent(MonitorIntent.FinishSession) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(R.string.monitor_finish))
                }
            }

            SessionPhase.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onIntent(MonitorIntent.ResumeSession) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(R.string.monitor_resume))
                }
                Button(
                    onClick = { onIntent(MonitorIntent.FinishSession) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(R.string.monitor_finish))
                }
            }
        }
        if (state.phase != SessionPhase.Idle) {
            val elapsed = formatDuration(state.stats.elapsedMillis)
            Text(
                stringResource(
                    R.string.monitor_session_stats,
                    elapsed,
                    state.stats.avgPressureKg,
                    state.stats.peakPressureKg,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** 格式化时长:分:秒 */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
