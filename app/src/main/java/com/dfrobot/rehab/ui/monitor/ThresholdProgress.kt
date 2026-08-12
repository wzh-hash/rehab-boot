package com.dfrobot.rehab.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dfrobot.rehab.domain.model.Thresholds
import java.util.Locale

/**
 * 三档阈值进度条:按 25/50/75% 分四段着色,当前值指针。
 * 视觉语义:安全区(主色)→ 过渡(灰)→ 目标区(琥珀)→ 超限(红)。
 */
@Composable
fun ThresholdProgress(
    thresholds: Thresholds,
    currentValueKg: Double?,
    modifier: Modifier = Modifier,
    maxScaleKg: Float = maxOf(thresholds.p75Kg.toFloat() * 1.2f, 10f),
) {
    val p25 = thresholds.p25Kg.toFloat()
    val p50 = thresholds.p50Kg.toFloat()
    val p75 = thresholds.p75Kg.toFloat()
    val scale = maxOf(maxScaleKg, p75 * 1.1f)

    val safeColor = MaterialTheme.colorScheme.primary
    val transitionColor = MaterialTheme.colorScheme.secondaryContainer
    val targetColor = Color(0xFFB45309)
    val overColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        ) {
            val total = maxWidth
            fun fraction(value: Float): Float = (value / scale).coerceIn(0f, 1f)

            // 四段着色
            segment(total, fraction(p25), 0f, safeColor)
            segment(total, fraction(p50) - fraction(p25), fraction(p25), transitionColor)
            segment(total, fraction(p75) - fraction(p50), fraction(p50), targetColor)
            segment(total, 1f - fraction(p75), fraction(p75), overColor.copy(alpha = 0.35f))

            // 当前值指针(竖线)
            if (currentValueKg != null) {
                val x = total * fraction(currentValueKg.toFloat())
                Box(
                    Modifier
                        .offset(x = x - 1.dp)
                        .width(2.dp)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
        // 阈值刻度标签
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                text = String.format(Locale.US, "25%%  %.1f", p25),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.33f),
            )
            Text(
                text = String.format(Locale.US, "50%%  %.1f", p50),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.34f),
            )
            Text(
                text = String.format(Locale.US, "75%%  %.1f", p75),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.33f),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.segment(
    totalWidth: androidx.compose.ui.unit.Dp,
    fraction: Float,
    offsetFraction: Float,
    color: Color,
) {
    if (fraction <= 0.001f) return
    Box(
        Modifier
            .offset(x = totalWidth * offsetFraction)
            .width(totalWidth * fraction)
            .height(12.dp)
            .background(color, RoundedCornerShape(2.dp)),
    )
}
