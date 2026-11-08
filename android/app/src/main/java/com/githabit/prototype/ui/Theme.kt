package com.githabit.prototype.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.githabit.prototype.domain.HabitColor
import com.githabit.prototype.domain.ThemePreference

val LocalDarkTheme = staticCompositionLocalOf { false }

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8BE5AC), onPrimary = Color(0xFF10291A),
    primaryContainer = Color(0xFF193426), onPrimaryContainer = Color(0xFF9FE8B8),
    secondary = Color(0xFF96BFA4), onSecondary = Color(0xFF173323), secondaryContainer = Color(0xFF233A2C),
    onSecondaryContainer = Color(0xFFB8E0C5),
    tertiary = Color(0xFF9BCDD3), onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1B4147), onTertiaryContainer = Color(0xFFB7E9EF),
    background = Color(0xFF0D1117), onBackground = Color(0xFFEDF2F6),
    surface = Color(0xFF131920), onSurface = Color(0xFFEDF2F6),
    surfaceVariant = Color(0xFF1B222C), onSurfaceVariant = Color(0xFF99A6B4),
    surfaceContainer = Color(0xFF131920), surfaceContainerLow = Color(0xFF10161D),
    surfaceContainerHigh = Color(0xFF1B222C), surfaceContainerHighest = Color(0xFF242D36),
    surfaceContainerLowest = Color(0xFF0A0E13), surfaceDim = Color(0xFF131920), surfaceBright = Color(0xFF303942),
    outline = Color(0xFF81918F), outlineVariant = Color(0xFF28313B),
    inverseSurface = Color(0xFFE7ECE8), inverseOnSurface = Color(0xFF18251F), inversePrimary = Color(0xFF256B3E),
    surfaceTint = Color(0xFF8BE5AC), scrim = Color.Black,
    error = Color(0xFFFFA3A3), onError = Color(0xFF53151B),
    errorContainer = Color(0xFF67252C), onErrorContainer = Color(0xFFFFDADC),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF256B3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F1E4), onPrimaryContainer = Color(0xFF1A5730),
    secondary = Color(0xFF486A51), onSecondary = Color.White, secondaryContainer = Color(0xFFDDEADB),
    onSecondaryContainer = Color(0xFF25452E),
    tertiary = Color(0xFF29656D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCEAEF), onTertiaryContainer = Color(0xFF16464D),
    background = Color(0xFFF7F9F8), onBackground = Color(0xFF18251F),
    surface = Color.White, onSurface = Color(0xFF18251F),
    surfaceVariant = Color(0xFFF0F4F1), onSurfaceVariant = Color(0xFF536459),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F7F4),
    surfaceContainerHigh = Color(0xFFF0F4F1), surfaceContainerHighest = Color(0xFFE7ECE8),
    surfaceContainerLowest = Color.White, surfaceDim = Color(0xFFD8E1DA), surfaceBright = Color.White,
    outline = Color(0xFF6C7B71), outlineVariant = Color(0xFFD8E1DA),
    inverseSurface = Color(0xFF242D36), inverseOnSurface = Color(0xFFEDF2F6), inversePrimary = Color(0xFF8BE5AC),
    surfaceTint = Color(0xFF256B3E), scrim = Color.Black,
    error = Color(0xFFAC303B), onError = Color.White,
    errorContainer = Color(0xFFFFDADC), onErrorContainer = Color(0xFF67252C),
)

internal fun ThemePreference.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemePreference.SYSTEM -> systemDark
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun GitHabitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = GitHabitTypography,
            shapes = GitHabitShapes,
            content = content,
        )
    }
}

@Composable
fun habitColor(color: HabitColor): Color {
    return HabitAccentPalette.color(color, LocalDarkTheme.current)
}

/** Categorical habit identity; never used as an error/action role. */
internal object HabitAccentPalette {
    const val glyphContainerAlpha = .11f

    fun color(color: HabitColor, dark: Boolean): Color = when (color) {
        HabitColor.GREEN -> if (dark) Color(0xFF8BE5AC) else Color(0xFF286E41)
        HabitColor.ORANGE -> if (dark) Color(0xFFEFB784) else Color(0xFF93551F)
        HabitColor.BLUE -> if (dark) Color(0xFF8EBAFF) else Color(0xFF3266A4)
        HabitColor.TEAL -> if (dark) Color(0xFF80D5CE) else Color(0xFF26726D)
        HabitColor.PURPLE -> if (dark) Color(0xFFC3A5F5) else Color(0xFF7950A6)
        HabitColor.PINK -> if (dark) Color(0xFFEEA1C4) else Color(0xFFAA4174)
    }
}

/** Ordered data intensity, not status/failure. Exact meaning belongs to the chart legend. */
internal object HeatmapPalette {
    private val dark = listOf(0xFF242D36, 0xFF163E2B, 0xFF226B40, 0xFF39A35F, 0xFF85DE9D).map { Color(it) }
    private val light = listOf(0xFFE7ECE8, 0xFFC5E8CB, 0xFF83C990, 0xFF459755, 0xFF256B3E).map { Color(it) }
    fun levels(darkTheme: Boolean): List<Color> = if (darkTheme) dark else light
}

@Composable
fun heatColors(): List<Color> = HeatmapPalette.levels(LocalDarkTheme.current)
