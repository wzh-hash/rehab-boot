package com.dfrobot.rehab.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfrobot.rehab.R
import com.dfrobot.rehab.domain.DailyStat
import com.dfrobot.rehab.domain.TodayStats
import com.dfrobot.rehab.ui.localized
import com.dfrobot.rehab.ui.parseTotalDuration

/**
 * 今日训练概览卡:训练次数 / 总时长 / 总步数,横向均分,28sp Bold 主色。
 * 调用方负责在 [todayStats] 非空时渲染。
 */
@Composable
internal fun TodayStatsCard(todayStats: TodayStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.today_stats_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatColumn(
                    value = todayStats.sessionCount.toString(),
                    label = stringResource(R.string.today_sessions),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = parseTotalDuration(todayStats.totalDurationMillis).localized(),
                    label = stringResource(R.string.today_duration),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = todayStats.totalSteps.toString(),
                    label = stringResource(R.string.today_steps),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 近 7 天训练柱状图卡:Canvas 手绘柱状 + 今天高亮 + 日期标签 + 柱顶次数。
 * 全 0 时显示空态文案(stats_empty)。
 */
@Composable
internal fun WeeklyChartCard(
    weekly: List<DailyStat>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.weekly_chart_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val totalCount = weekly.sumOf { it.sessionCount }
            if (totalCount == 0) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                WeeklyBars(weekly = weekly)
            }
        }
    }
}

@Composable
private fun WeeklyBars(weekly: List<DailyStat>) {
    val textMeasurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    val heightDp = 120.dp
    val barSpacing = 4.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        drawWeeklyBars(
            weekly = weekly,
            barColor = primary,
            onSurfaceVariant = onSurfaceVariant,
            textMeasurer = textMeasurer,
            valueStyle = valueStyle,
            labelStyle = labelStyle,
            barSpacingPx = barSpacing.toPx(),
        )
    }
}

private fun DrawScope.drawWeeklyBars(
    weekly: List<DailyStat>,
    barColor: Color,
    onSurfaceVariant: Color,
    textMeasurer: TextMeasurer,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
    barSpacingPx: Float,
) {
    if (weekly.isEmpty()) return
    val n = weekly.size
    val maxValue = weekly.maxOf { it.sessionCount }.coerceAtLeast(1)
    // 顶部预留画柱顶次数,底部预留画日期标签
    val topReserved = 18.sp.toPx()
    val bottomReserved = 16.sp.toPx()
    val chartHeight = size.height - topReserved - bottomReserved
    val columnWidth = size.width / n
    val barWidth = (columnWidth - barSpacingPx * 2f).coerceAtLeast(2f)

    weekly.forEachIndexed { index, stat ->
        val centerX = columnWidth * index + columnWidth / 2f
        val fraction = stat.sessionCount.toFloat() / maxValue
        val barHeight = (chartHeight * fraction).coerceAtLeast(if (stat.sessionCount > 0) 4f else 0f)
        val barTop = topReserved + (chartHeight - barHeight)
        val barLeft = centerX - barWidth / 2f
        val isToday = index == n - 1
        val highlight = isToday && stat.sessionCount > 0

        if (stat.sessionCount > 0) {
            // 柱体(圆角)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )
            // 柱顶次数
            val valueText = stat.sessionCount.toString()
            val valueLayout = textMeasurer.measure(
                AnnotatedString(valueText),
                style = valueStyle,
            )
            val valueX = centerX - valueLayout.size.width / 2f
            val valueY = (barTop - valueLayout.size.height - 2f).coerceAtLeast(0f)
            drawText(valueLayout, color = barColor, topLeft = Offset(valueX, valueY))
        }
        // 日期标签(底部)
        val labelLayout = textMeasurer.measure(
            AnnotatedString(stat.dayLabel),
            style = labelStyle,
        )
        val labelX = centerX - labelLayout.size.width / 2f
        val labelY = size.height - bottomReserved + 2f
        val labelColor = if (highlight) barColor else onSurfaceVariant
        drawText(labelLayout, color = labelColor, topLeft = Offset(labelX, labelY))
    }
}