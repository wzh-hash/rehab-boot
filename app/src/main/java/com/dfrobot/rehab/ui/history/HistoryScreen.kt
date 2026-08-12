package com.dfrobot.rehab.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.dfrobot.rehab.ui.feedback.performClickHaptic
import com.dfrobot.rehab.ui.feedback.pressFeedback
import com.dfrobot.rehab.ui.formatDuration
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

    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(context.getString(effect.messageRes))
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
        val hapticFeedback = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.history_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    hapticFeedback.performClickHaptic()
                    viewModel.accept(HistoryIntent.DeleteSession(id))
                    confirmDeleteId = null
                }) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    hapticFeedback.performClickHaptic()
                    confirmDeleteId = null
                }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }

    detailSession?.let { session ->
        val hapticFeedback = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { detailSession = null },
            title = { Text(stringResource(R.string.history_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDate(session.startTimeMillis))
                    Text(stringResource(R.string.history_ratio, session.ratio.percent))
                    Text(stringResource(R.string.history_reps, session.repsCompleted))
                    Text(stringResource(R.string.history_duration, formatDuration(session.durationMillis)))
                    Text(
                        if (session.steps > 0) {
                            stringResource(R.string.history_steps, session.steps)
                        } else {
                            stringResource(R.string.history_steps_unsupported)
                        },
                    )
                    Text(
                        stringResource(
                            if (session.completed) R.string.history_status_done
                            else R.string.history_status_incomplete,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    hapticFeedback.performClickHaptic()
                    detailSession = null
                }) {
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback(
                enabled = true,
                haptic = false,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDate(session.startTimeMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.history_ratio, session.ratio.percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.steps > 0) {
                    Text(
                        stringResource(R.string.history_steps, session.steps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            stringResource(
                                if (session.completed) R.string.history_status_done
                                else R.string.history_status_incomplete,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (session.completed) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        labelColor = if (session.completed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    ),
                )
                Text(
                    stringResource(R.string.history_reps, session.repsCompleted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
