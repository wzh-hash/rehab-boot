package com.dfrobot.rehab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题:Light/Dark 配色方案。
 *
 * 提案 §4 关键改动:
 * - Light surface = #FFFFFF 与 background #F8FAFC 拉开,卡片层次更清晰
 * - Dark surface = #111A2C 与 background #0B1220 拉开
 * - 状态色:success #16A34A、warning #D97706、error #DC2626(含容器/暗色变体)
 * - 训练按钮渐变通过 [RatioGradients] 暴露(按系统主题切换)
 */
private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = OnSlateContainer,
    background = PageBackground,
    onBackground = OnPageBackground,
    surface = SurfaceLight,
    onSurface = OnPageBackground,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = OnSlateContainer,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkBackground,
    primaryContainer = OnTealContainer,
    onPrimaryContainer = TealContainer,
    secondary = SlateSecondary,
    onSecondary = DarkOnBackground,
    secondaryContainer = OnSlateContainer,
    onSecondaryContainer = SlateContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = OnSlateContainer,
    onSurfaceVariant = SlateContainer,
    error = DarkErrorRed,
    onError = DarkBackground,
)

/** 训练按钮渐变集合(按当前主题由 UI 读取)。 */
val LocalRatioGradients = compositionLocalOf { LightRatioGradients }
val LocalStatusColors = compositionLocalOf {
    StatusColors(
        success = SuccessGreen,
        onSuccess = Color.White,
        successContainer = SuccessGreenContainer,
        onSuccessContainer = OnSuccessGreenContainer,
        warning = WarningAmber,
        warningContainer = WarningAmberContainer,
        onWarningContainer = OnWarningAmberContainer,
    )
}

/** 状态色集合(不属 M3 ColorScheme 标准槽位,通过 CompositionLocal 暴露)。 */
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val DarkStatusColors = StatusColors(
    success = DarkSuccessGreen,
    onSuccess = DarkBackground,
    successContainer = OnSuccessGreenContainer,
    onSuccessContainer = SuccessGreenContainer,
    warning = DarkWarningAmber,
    warningContainer = OnWarningAmberContainer,
    onWarningContainer = WarningAmberContainer,
)

@Composable
fun RehabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val statusColors = if (darkTheme) DarkStatusColors else LocalStatusColors.current
    val ratioGradients = if (darkTheme) DarkRatioGradients else LightRatioGradients
    CompositionLocalProvider(
        LocalRatioGradients provides ratioGradients,
        LocalStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RehabTypography,
            content = content,
        )
    }
}