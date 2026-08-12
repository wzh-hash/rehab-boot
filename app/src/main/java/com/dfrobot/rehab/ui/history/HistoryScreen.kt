package com.dfrobot.rehab.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfrobot.rehab.R
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.presentation.history.HistoryEffect
import com.dfrobot.rehab.presentation.history.HistoryIntent
import com.dfrobot.rehab.presentation.history.HistoryViewModel
import com.dfrobot.rehab.ui.monitor.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var detailSession by remember { mutableStateOf<TrainingSession?>(null) }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HistoryScreen(
        state = state,
        onSessionClick = { detailSession = it },
        onDeleteClick = { confirmDeleteId = it.id },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.history_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.accept(HistoryIntent.DeleteSession(id))
                    confirmDeleteId = null
                }) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }

    detailSession?.let { session ->
        AlertDialog(
            onDismissRequest = { detailSession = null },
            title = { Text(stringResource(R.string.history_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDate(session.startTimeMillis))
                    Text(stringResource(R.string.history_duration, formatDuration(session.durationMillis)))
                    Text(stringResource(R.string.history_avg, session.avgPressureKg))
                    Text(stringResource(R.string.history_peak, session.peakPressureKg))
                }
            },
            confirmButton = {
                TextButton(onClick = { detailSession = null }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

@Composable
fun HistoryScreen(
    state: com.dfrobot.rehab.presentation.history.HistoryUiState,
    onSessionClick: (TrainingSession) -> Unit,
    onDeleteClick: (TrainingSession) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    SessionCard(session, onClick = { onSessionClick(session) }, onDeleteClick = { onDeleteClick(session) })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: TrainingSession,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    formatDate(session.startTimeMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.history_duration, formatDuration(session.durationMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.history_peak, session.peakPressureKg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.history_avg, session.avgPressureKg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
