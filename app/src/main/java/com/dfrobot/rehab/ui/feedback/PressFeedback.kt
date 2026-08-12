package com.dfrobot.rehab.ui.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 统一点击反馈(v4 设计):
 * - 涟漪(沿用 M3 默认指示器 LocalIndication,主色)
 * - 按压态:透明度 0.85 + 缩小 0.97
 * - 关键操作附加震动
 *
 * 用法:在原有 .clickable(...) 处替换为
 * ```kotlin
 * Modifier.pressFeedback(enabled = true, haptic = false, onClick = { ... })
 * ```
 *
 * 训练比例按钮自带缩放+阴影反馈,**不**直接套此 modifier,
 * 而是在按下时通过 [HapticFeedback.performClickHaptic] 触发震动(参见 RatioButton)。
 *
 * 震动 API 使用 Compose 自带的 [LocalHapticFeedback] / [HapticFeedback.performHapticFeedback],
 * 无新增依赖。
 */
fun Modifier.pressFeedback(
    enabled: Boolean = true,
    haptic: Boolean = false,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按下→轻震(短促,适合按钮反馈)。仅关键操作(haptic=true)启用。
    if (haptic) {
        LaunchedEffect(isPressed, enabled) {
            if (isPressed && enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // 使用 M3 默认 Indication(LocalIndication) - 即 m3 ripple,无需手动构造 rememberRipple
    this.clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
        .graphicsLayer {
            val scale = if (isPressed && enabled) 0.97f else 1f
            scaleX = scale
            scaleY = scale
        }
        .alpha(if (isPressed && enabled) 0.85f else 1f)
}

/**
 * 触发一次轻震动;供训练比例按钮 / 语音测试按钮在按下时调用。
 * (TextButton 等 M3 组件自带按压视觉反馈,无需 pressFeedback;
 * 仅在 onClick 中追加震动即可。)
 */
fun HapticFeedback.performClickHaptic() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

/** 取得当前 CompositionLocal 中的 HapticFeedback(用于在 TextButton 等 M3 组件中触发震动)。 */
@Composable
fun rememberHapticFeedback(): HapticFeedback = LocalHapticFeedback.current