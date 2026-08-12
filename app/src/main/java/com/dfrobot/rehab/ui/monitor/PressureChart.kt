package com.dfrobot.rehab.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 近 30 秒实时压力折线(零依赖 Canvas 手绘)。
 * 环形缓冲由 [values] 承载,UI 本地状态,不进 ViewModel。
 */
@Composable
fun PressureChart(
    values: List<Float>,
    maxValue: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val areaColor = lineColor.copy(alpha = 0.12f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val w = size.width
        val h = size.height

        // 网格:横线 + 纵线
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = h * i / gridSteps
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        for (i in 0..4) {
            val x = w * i / 4
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }

        if (values.isEmpty() || maxValue <= 0f) return@Canvas

        val scale = max(maxValue, 1f)
        val stepX = w / (PressureChart.WINDOW_POINTS - 1).toFloat()
        val visible = values.takeLast(PressureChart.WINDOW_POINTS)
        val baseY = h // 值 0 对应的 y
        val points = visible.mapIndexed { index, value ->
            val x = stepX * (PressureChart.WINDOW_POINTS - visible.size + index)
            val y = baseY - (value / scale).coerceIn(0f, 1f) * h
            Offset(x, y)
        }

        // 面积渐变
        val areaPath = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(areaColor, Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )

        // 折线
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.5f))

        // 最新点
        val last = points.last()
        drawCircle(lineColor, radius = 4f, center = last)
    }
}

object PressureChart {
    /** 30s 窗口,按 150ms 节流后约 200 点。 */
    const val WINDOW_POINTS = 200

    /** 由时间戳与实时值驱动的环形缓冲。 */
    class Buffer {
        private val deque = ArrayDeque<Pair<Long, Float>>()

        fun add(timestampMillis: Long, value: Float) {
            deque.addLast(timestampMillis to value)
            while (deque.size > WINDOW_POINTS) deque.removeFirst()
        }

        fun values(nowMillis: Long, windowMillis: Long = 30_000L): List<Float> =
            deque.filter { nowMillis - it.first <= windowMillis }.map { it.second }

        fun clear() {
            deque.clear()
        }

        fun isEmpty(): Boolean = deque.isEmpty()
    }

    @Composable
    fun rememberBuffer(): Buffer = remember { Buffer() }
}
