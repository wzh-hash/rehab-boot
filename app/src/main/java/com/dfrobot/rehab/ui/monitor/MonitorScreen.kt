package com.dfrobot.rehab.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.presentation.monitor.MonitorEffect
import com.dfrobot.rehab.presentation.monitor.MonitorIntent
import com.dfrobot.rehab.presentation.monitor.MonitorUiState
import com.dfrobot.rehab.presentation.monitor.MonitorViewModel
import com.dfrobot.rehab.ui.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
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
            ConnectionBanner(state, onIntent)
            DeviceStatusBadge(state)
            TrainingButtons(state, onIntent)
            TrainingProgressCard(state)
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
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    val (text, color) = when (state.connectionState) {
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
private fun DeviceStatusBadge(state: MonitorUiState) {
    val online = state.deviceOnline && state.connectionState == ConnectionState.Connected
    val color = if (online) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                if (online) Color(0xFF16A34A).copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            stringResource(
                if (online) R.string.monitor_device_online else R.string.monitor_device_unknown,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun TrainingButtons(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    val ratios = listOf(
        TrainingRatio.T25 to R.string.monitor_ratio_25,
        TrainingRatio.T50 to R.string.monitor_ratio_50,
        TrainingRatio.T75 to R.string.monitor_ratio_75,
        TrainingRatio.T100 to R.string.monitor_ratio_100,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ratios.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (ratio, labelRes) ->
                    Button(
                        onClick = { onIntent(MonitorIntent.StartTraining(ratio)) },
                        enabled = state.canSendCommand,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onIntent(MonitorIntent.HelloTest) },
            enabled = state.connectionState == ConnectionState.Connected,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Text(stringResource(R.string.monitor_hello_test))
        }
        if (state.phase == SessionPhase.Training) {
            Text(
                stringResource(R.string.monitor_training_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun TrainingProgressCard(state: MonitorUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.phase == SessionPhase.Training) {
                Text(
                    stringResource(R.string.monitor_rep_progress, state.repsCompleted),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        R.string.monitor_session_stats,
                        formatDuration(state.stats.elapsedMillis),
                        state.activeRatio?.percent ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.monitor_training_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.recentEvents.isNotEmpty()) {
                state.recentEvents.takeLast(6).forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            event.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            eventTimeFormat.format(Date(event.timeMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val eventTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
