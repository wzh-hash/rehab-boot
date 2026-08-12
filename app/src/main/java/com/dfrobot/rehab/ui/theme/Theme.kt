package com.dfrobot.rehab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = SlateSecondary,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = OnSlateContainer,
    background = PageBackground,
    onBackground = OnPageBackground,
    surface = PageBackground,
    onSurface = OnPageBackground,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = OnSlateContainer,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkBackground,
    primaryContainer = OnTealContainer,
    onPrimaryContainer = TealContainer,
    secondary = SlateSecondary,
    secondaryContainer = OnSlateContainer,
    onSecondaryContainer = SlateContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = OnSlateContainer,
    onSurfaceVariant = SlateContainer,
    error = ErrorRed,
)

@Composable
fun RehabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RehabTypography,
        content = content,
    )
}
