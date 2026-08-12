package com.dfrobot.rehab.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfrobot.rehab.R
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.presentation.monitor.EventUi
import com.dfrobot.rehab.presentation.monitor.MonitorEffect
import com.dfrobot.rehab.presentation.monitor.MonitorIntent
import com.dfrobot.rehab.presentation.monitor.MonitorUiState
import com.dfrobot.rehab.presentation.monitor.MonitorViewModel
import com.dfrobot.rehab.ui.formatDuration
import com.dfrobot.rehab.ui.theme.LocalRatioGradients
import com.dfrobot.rehab.ui.theme.LocalStatusColors
import com.dfrobot.rehab.ui.theme.RatioGradient
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
    var pendingTrainingIntent by remember { mutableStateOf<MonitorIntent.StartTraining?>(null) }

    // Android 10+ 计步需 ACTIVITY_RECOGNITION 运行时权限;拒绝仅计步不可用,训练照常
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> pendingTrainingIntent?.let { viewModel.accept(it) }; pendingTrainingIntent = null }
    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MonitorEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    MonitorScreen(
        state = state,
        onIntent = { intent ->
            if (intent is MonitorIntent.StartTraining &&
                Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 先请求计步权限,回调后再下发训练指令
                pendingTrainingIntent = intent
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                viewModel.accept(intent)
            }
        },
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionStatusBar(state, onIntent)
            RatioSelectionGrid(state, onIntent)
            VoiceTestRow(state, onIntent)
            TrainingProgressCard(state)
            EventTimelineCard(state.recentEvents)
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

// ---- 合并状态栏 ----
@Composable
private fun ConnectionStatusBar(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    val online = state.deviceOnline && state.connectionState == ConnectionState.Connected
    val status = LocalStatusColors.current
    val statusText = when (state.connectionState) {
        ConnectionState.Connected ->
            stringResource(
                if (online) R.string.monitor_status_connected
                else R.string.monitor_status_connected_no_device,
            )
        ConnectionState.Connecting -> stringResource(R.string.monitor_status_connecting)
        ConnectionState.Disconnected -> stringResource(R.string.monitor_status_disconnected)
    }
    val accent = when (state.connectionState) {
        ConnectionState.Connected -> status.success
        ConnectionState.Connecting -> MaterialTheme.colorScheme.primary
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
    }
    val containerBg = when (state.connectionState) {
        ConnectionState.Connected ->
            if (online) status.successContainer else MaterialTheme.colorScheme.surfaceVariant
        ConnectionState.Connecting -> MaterialTheme.colorScheme.surfaceVariant
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (state.connectionState) {
        ConnectionState.Connected ->
            if (online) status.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg)
            .clickable(enabled = state.connectionState != ConnectionState.Connecting) {
                onIntent(MonitorIntent.RetryConnect)
            }
            .semantics { contentDescription = statusText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .padding(end = 12.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (online) status.success
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ),
        )
    }
}

// ---- 比例选择区:2×2 渐变按钮 ----
@Composable
private fun RatioSelectionGrid(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.monitor_select_ratio),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ratios = listOf(TrainingRatio.T25, TrainingRatio.T50, TrainingRatio.T75, TrainingRatio.T100)
        ratios.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ratio ->
                    RatioButton(
                        ratio = ratio,
                        state = state,
                        onClick = { onIntent(MonitorIntent.StartTraining(ratio)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = state.phase == SessionPhase.Training,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                stringResource(R.string.monitor_training_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RatioButton(
    ratio: TrainingRatio,
    state: MonitorUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradientMap = LocalRatioGradients.current
    val gradient: RatioGradient = gradientMap[ratio.percent] ?: return
    val isActive = state.activeRatio == ratio
    val isDimmed = state.phase == SessionPhase.Training && !isActive
    val enabled = state.canSendCommand && !isDimmed
    val contentColor = if (enabled) gradient.content else gradient.content.copy(alpha = 0.6f)
    val subtitle = ratioSubtitle(ratio)

    val targetScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.97f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "ratioButtonScale",
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (enabled) 4f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "ratioButtonShadow",
    )

    Box(
        modifier = modifier
            .height(88.dp)
            .graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
                this.shadowElevation = shadowElevation
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(gradient.start, gradient.end),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (isActive) Modifier.border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                ) else Modifier,
            )
            .semantics {
                contentDescription = "${ratio.percent}% / $subtitle"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${ratio.percent}%",
                style = MaterialTheme.typography.displayLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ratioSubtitle(ratio: TrainingRatio): String = stringResource(
    when (ratio) {
        TrainingRatio.T25 -> R.string.monitor_ratio_subtitle_25
        TrainingRatio.T50 -> R.string.monitor_ratio_subtitle_50
        TrainingRatio.T75 -> R.string.monitor_ratio_subtitle_75
        TrainingRatio.T100 -> R.string.monitor_ratio_subtitle_100
    },
)

// ---- 语音测试 ----
@Composable
private fun VoiceTestRow(
    state: MonitorUiState,
    onIntent: (MonitorIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = { onIntent(MonitorIntent.HelloTest) },
            enabled = state.connectionState == ConnectionState.Connected,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.monitor_hello_test))
        }
    }
}

// ---- 训练进度卡 ----
@Composable
private fun TrainingProgressCard(state: MonitorUiState) {
    val status = LocalStatusColors.current
    val inTraining = state.phase == SessionPhase.Training
    val accent = if (inTraining) status.success else MaterialTheme.colorScheme.primary
    val highlightAlpha by animateFloatAsState(
        targetValue = if (inTraining) 1f else 0.6f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "progressHighlight",
    )
    val animatedReps by animateFloatAsState(
        targetValue = state.repsCompleted.toFloat(),
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "progressReps",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, accent),
                        ),
                    )
                    .alpha(highlightAlpha),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inTraining) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "第 ${animatedReps.toInt()}",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "/3 次",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    RepetitionDots(completed = state.repsCompleted, total = 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(
                                R.string.monitor_session_stats,
                                formatDuration(state.stats.elapsedMillis),
                                state.activeRatio?.percent ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.stepsSupported) {
                            Text(
                                stringResource(R.string.monitor_steps_label, state.steps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.monitor_steps_unsupported),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.monitor_progress_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepetitionDots(completed: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val filled = i < completed
            val color = if (filled) LocalStatusColors.current.success
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ---- 事件时间线 ----
@Composable
private fun EventTimelineCard(events: List<EventUi>) {
    val reachedText = stringResource(R.string.monitor_event_reached)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.monitor_timeline_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.monitor_timeline_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val latestIndex = events.lastIndex
                events.forEachIndexed { index, event ->
                    val isLatest = index == latestIndex
                    val reachedTarget = event.text == reachedText
                    if (isLatest) {
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 2 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 240)),
                        ) {
                            TimelineRow(event, reachedTarget)
                        }
                    } else {
                        TimelineRow(event, reachedTarget)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(event: EventUi, reachedTarget: Boolean) {
    val dotColor = if (reachedTarget) LocalStatusColors.current.success
        else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            event.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            eventTimeFormat.format(Date(event.timeMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val eventTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
