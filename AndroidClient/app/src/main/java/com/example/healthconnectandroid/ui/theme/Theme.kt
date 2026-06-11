package com.example.healthconnectandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.healthconnectandroid.AppThemePalette

private data class AppPaletteTokens(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val primary2: Color,
    val accent: Color,
    val azuki: Color,
    val danger: Color,
    val success: Color,
    val info: Color
)

@Composable
fun HealthConnectAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppThemePalette = AppThemePalette.PAPER,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = appColorScheme(palette = palette, darkTheme = darkTheme),
        typography = Typography,
        content = content
    )
}

private fun appColorScheme(
    palette: AppThemePalette,
    darkTheme: Boolean
): ColorScheme {
    val tokens = paletteTokens(palette = palette, darkTheme = darkTheme)
    return if (darkTheme) tokens.toDarkColorScheme() else tokens.toLightColorScheme()
}

private fun AppPaletteTokens.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primary,
    onPrimaryContainer = Color.White,
    inversePrimary = primary2,
    secondary = success,
    onSecondary = Color.White,
    secondaryContainer = success.copy(alpha = 0.18f),
    onSecondaryContainer = text,
    tertiary = azuki,
    onTertiary = Color.White,
    tertiaryContainer = accent.copy(alpha = 0.34f),
    onTertiaryContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = muted,
    surfaceTint = primary,
    inverseSurface = text,
    inverseOnSurface = surface,
    outline = border,
    outlineVariant = surface3,
    error = danger,
    onError = Color.White,
    errorContainer = danger.copy(alpha = 0.18f),
    onErrorContainer = text,
    scrim = Color(0xFF000000)
)

private fun AppPaletteTokens.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF17131A),
    primaryContainer = primary2,
    onPrimaryContainer = Color(0xFF151219),
    inversePrimary = primary,
    secondary = success,
    onSecondary = Color(0xFF151914),
    secondaryContainer = success.copy(alpha = 0.24f),
    onSecondaryContainer = text,
    tertiary = azuki,
    onTertiary = Color(0xFF20120F),
    tertiaryContainer = accent.copy(alpha = 0.28f),
    onTertiaryContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = muted,
    surfaceTint = primary,
    inverseSurface = text,
    inverseOnSurface = background,
    outline = border,
    outlineVariant = surface3,
    error = danger,
    onError = Color(0xFF230D0B),
    errorContainer = surface3,
    onErrorContainer = text,
    scrim = Color(0xFF000000)
)

private fun paletteTokens(
    palette: AppThemePalette,
    darkTheme: Boolean
): AppPaletteTokens = when (palette) {
    AppThemePalette.PAPER -> if (darkTheme) {
        AppPaletteTokens(
            background = StudioPaperDark,
            surface = Color(0xFF111A28),
            surface2 = Color(0xFF1F2B3D),
            surface3 = Color(0xFF121D2B),
            text = StudioInkDark,
            muted = Color(0xFFA6B0C0),
            border = Color(0xFF334154),
            primary = StudioTealDark,
            primary2 = StudioPurpleDark,
            accent = StudioWarmDark,
            azuki = Color(0xFFB9806B),
            danger = StudioDangerDark,
            success = Color(0xFF72E3A8),
            info = Color(0xFF75B7FF)
        )
    } else {
        AppPaletteTokens(
            background = StudioPaper,
            surface = Color(0xFFFBFAF7),
            surface2 = Color(0xFFE2EAEE),
            surface3 = Color(0xFFEEF4F6),
            text = StudioInk,
            muted = Color(0xFF667085),
            border = Color(0xFFD3D8DE),
            primary = StudioTeal,
            primary2 = StudioPurple,
            accent = StudioWarm,
            azuki = Color(0xFF9B6258),
            danger = StudioDanger,
            success = Color(0xFF087348),
            info = Color(0xFF1677FF)
        )
    }

    AppThemePalette.RAIN -> if (darkTheme) {
        AppPaletteTokens(
            background = Color(0xFF0A121A),
            surface = Color(0xFF121E2A),
            surface2 = Color(0xFF1C2B37),
            surface3 = Color(0xFF243846),
            text = Color(0xFFEAF3F5),
            muted = Color(0xFFA9B8C0),
            border = Color(0xFF3B505B),
            primary = Color(0xFF7BD3DA),
            primary2 = Color(0xFFA9A0F2),
            accent = Color(0xFFF2A57B),
            azuki = Color(0xFFD28D80),
            danger = Color(0xFFFB7185),
            success = Color(0xFF82E3B5),
            info = Color(0xFF94C9FF)
        )
    } else {
        AppPaletteTokens(
            background = Color(0xFFEEF4F6),
            surface = Color(0xFFFBFDFD),
            surface2 = Color(0xFFDDE7EA),
            surface3 = Color(0xFFECF4F5),
            text = Color(0xFF13202A),
            muted = Color(0xFF63717A),
            border = Color(0xFFC9D5DA),
            primary = Color(0xFF0E7C86),
            primary2 = Color(0xFF6E56CF),
            accent = Color(0xFFD86F45),
            azuki = Color(0xFF8D5C55),
            danger = Color(0xFFB42342),
            success = Color(0xFF087348),
            info = Color(0xFF1677FF)
        )
    }

    AppThemePalette.MILK -> if (darkTheme) {
        AppPaletteTokens(
            background = Color(0xFF1C1512),
            surface = Color(0xFF2A211D),
            surface2 = Color(0xFF3A2B25),
            surface3 = Color(0xFF49372F),
            text = Color(0xFFF2E5D7),
            muted = Color(0xFFC5B2A7),
            border = Color(0xFF5B4A41),
            primary = Color(0xFF65CDD5),
            primary2 = Color(0xFFA78BFA),
            accent = Color(0xFFF49B6E),
            azuki = Color(0xFFC08073),
            danger = Color(0xFFFB7185),
            success = Color(0xFFA1D69B),
            info = Color(0xFF9FB4C8)
        )
    } else {
        AppPaletteTokens(
            background = Color(0xFFF4E9DD),
            surface = Color(0xFFFFF8EF),
            surface2 = Color(0xFFEBD9CA),
            surface3 = Color(0xFFF1E4D8),
            text = Color(0xFF473A35),
            muted = Color(0xFF7A6961),
            border = Color(0xFFD9C8BA),
            primary = StudioTeal,
            primary2 = StudioPurple,
            accent = Color(0xFFD86F45),
            azuki = Color(0xFF9B6258),
            danger = StudioDanger,
            success = Color(0xFF087348),
            info = Color(0xFF1677FF)
        )
    }

    AppThemePalette.HOODIE -> if (darkTheme) {
        AppPaletteTokens(
            background = Color(0xFF171719),
            surface = Color(0xFF232326),
            surface2 = Color(0xFF303034),
            surface3 = Color(0xFF3B383B),
            text = Color(0xFFE7E1DA),
            muted = Color(0xFFB7ADA5),
            border = Color(0xFF4D4A4D),
            primary = Color(0xFF72CCD4),
            primary2 = Color(0xFFA78BFA),
            accent = Color(0xFFF49B6E),
            azuki = Color(0xFFB87670),
            danger = Color(0xFFFB7185),
            success = Color(0xFF94C991),
            info = Color(0xFF96B0BF)
        )
    } else {
        AppPaletteTokens(
            background = Color(0xFFE7E1DA),
            surface = Color(0xFFF2EEE8),
            surface2 = Color(0xFFD8D1C9),
            surface3 = Color(0xFFE0D9D1),
            text = Color(0xFF342F2E),
            muted = Color(0xFF706A68),
            border = Color(0xFFCBC2B8),
            primary = StudioTeal,
            primary2 = StudioPurple,
            accent = StudioWarm,
            azuki = Color(0xFF875B54),
            danger = StudioDanger,
            success = Color(0xFF087348),
            info = Color(0xFF1677FF)
        )
    }

    AppThemePalette.SAGE -> if (darkTheme) {
        AppPaletteTokens(
            background = Color(0xFF1A1F19),
            surface = Color(0xFF273027),
            surface2 = Color(0xFF334032),
            surface3 = Color(0xFF3D4B3B),
            text = Color(0xFFE9E4D8),
            muted = Color(0xFFB8B8A8),
            border = Color(0xFF536450),
            primary = Color(0xFF72CCD4),
            primary2 = Color(0xFFA78BFA),
            accent = Color(0xFFF49B6E),
            azuki = Color(0xFFBC7A70),
            danger = Color(0xFFFB7185),
            success = Color(0xFF9ECA8A),
            info = Color(0xFF94ACB6)
        )
    } else {
        AppPaletteTokens(
            background = Color(0xFFECE9DF),
            surface = Color(0xFFFAF7EF),
            surface2 = Color(0xFFDDE7D4),
            surface3 = Color(0xFFE6E7D9),
            text = Color(0xFF423B35),
            muted = Color(0xFF6F7165),
            border = Color(0xFFD1D2C3),
            primary = StudioTeal,
            primary2 = StudioPurple,
            accent = StudioWarm,
            azuki = Color(0xFF8D5D53),
            danger = StudioDanger,
            success = Color(0xFF087348),
            info = Color(0xFF1677FF)
        )
    }
}
