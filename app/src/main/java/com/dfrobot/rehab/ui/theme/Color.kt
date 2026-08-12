package com.dfrobot.rehab.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色 token —— 提案 §4 v3 设计。
 *
 * 主色:医疗康复冷静基调(深青);Light 表面 #FFFFFF 与背景 #F8FAFC 拉开层次。
 * 状态色:success 翠绿、warning 琥珀、error 红;深色模式整体提亮一档。
 * 训练按钮渐变(135°):25% 浅青→青、50% 青→深青、75% 深青→更深、100% 更深→墨青。
 */

// ---- 基础色(共用) ----
val TealPrimary = Color(0xFF0E7490)
val TealContainer = Color(0xFFA5F3FC)
val OnTealContainer = Color(0xFF164E63)

// ---- 中性(灰阶) ----
val SlateSecondary = Color(0xFF64748B)
val SlateContainer = Color(0xFFE2E8F0)
val OnSlateContainer = Color(0xFF1E293B)

// ---- 状态色(提案 P0) ----
val SuccessGreen = Color(0xFF16A34A)
val SuccessGreenContainer = Color(0xFFDCFCE7)
val OnSuccessGreenContainer = Color(0xFF14532D)
val WarningAmber = Color(0xFFD97706)
val WarningAmberContainer = Color(0xFFFEF3C7)
val OnWarningAmberContainer = Color(0xFF78350F)
val ErrorRed = Color(0xFFDC2626)
val ErrorContainer = Color(0xFFFEE2E2)
val OnErrorContainer = Color(0xFF991B1B)

// ---- Light 主题 ----
val PageBackground = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val OnPageBackground = Color(0xFF0F172A)

// ---- Dark 主题(整体提亮一档) ----
val DarkTealPrimary = Color(0xFF67E8F9)
val DarkBackground = Color(0xFF0B1220)
val DarkSurface = Color(0xFF111A2C)
val DarkOnBackground = Color(0xFFE2E8F0)
val DarkSuccessGreen = Color(0xFF4ADE80)
val DarkWarningAmber = Color(0xFFFBBF24)
val DarkErrorRed = Color(0xFFF87171)

// ---- 训练按钮渐变(135°) ----
data class RatioGradient(
    val start: Color,
    val end: Color,
    val content: Color,
)

/** Light 模式渐变 —— 颜色由浅到深表达负重从小到大。 */
val LightRatioGradients: Map<Int, RatioGradient> = mapOf(
    25 to RatioGradient(Color(0xFFA5F3FC), Color(0xFF67E8F9), Color(0xFF164E63)),
    50 to RatioGradient(Color(0xFF22D3EE), Color(0xFF0891B2), Color.White),
    75 to RatioGradient(Color(0xFF0891B2), Color(0xFF0E7490), Color.White),
    100 to RatioGradient(Color(0xFF155E75), Color(0xFF082F49), Color.White),
)

/** Dark 模式渐变 —— 整体提亮一档,在深底色上保持可读对比。 */
val DarkRatioGradients: Map<Int, RatioGradient> = mapOf(
    25 to RatioGradient(Color(0xFFCFFAFE), Color(0xFF67E8F9), Color(0xFF164E63)),
    50 to RatioGradient(Color(0xFF67E8F9), Color(0xFF22D3EE), Color(0xFF0F172A)),
    75 to RatioGradient(Color(0xFF22D3EE), Color(0xFF0891B2), Color(0xFF0F172A)),
    100 to RatioGradient(Color(0xFF0891B2), Color(0xFF155E75), Color.White),
)
