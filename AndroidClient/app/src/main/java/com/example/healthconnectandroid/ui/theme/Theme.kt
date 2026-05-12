package com.example.healthconnectandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = HealthBlueDark,
    onPrimary = ColorOnDarkPrimary,
    secondary = CalmMint,
    tertiary = WarmAmber,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedText,
    outline = DarkOutline,
    error = SoftRed
)

private val LightColorScheme = lightColorScheme(
    primary = HealthBlue,
    onPrimary = ColorOnLightPrimary,
    secondary = CalmMint,
    tertiary = WarmAmber,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    outline = LightOutline,
    error = SoftRed
)

@Composable
fun HealthConnectAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
