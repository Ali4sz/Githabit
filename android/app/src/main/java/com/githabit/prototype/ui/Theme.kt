package com.githabit.prototype.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githabit.prototype.domain.HabitColor

val LocalDarkTheme = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BE5AC), onPrimary = Color(0xFF10291A),
    primaryContainer = Color(0xFF193426), onPrimaryContainer = Color(0xFF9FE8B8),
    secondary = Color(0xFF96BFA4), secondaryContainer = Color(0xFF233A2C),
    onSecondaryContainer = Color(0xFFB8E0C5),
    background = Color(0xFF0D1117), onBackground = Color(0xFFEDF2F6),
    surface = Color(0xFF131920), onSurface = Color(0xFFEDF2F6),
    surfaceVariant = Color(0xFF1B222C), onSurfaceVariant = Color(0xFF99A6B4),
    surfaceContainer = Color(0xFF131920), surfaceContainerLow = Color(0xFF10161D),
    surfaceContainerHigh = Color(0xFF1B222C), surfaceContainerHighest = Color(0xFF242D36),
    outline = Color(0xFF586774), outlineVariant = Color(0xFF28313B),
    error = Color(0xFFFFA3A3), onError = Color(0xFF53151B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF256B3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F1E4), onPrimaryContainer = Color(0xFF1A5730),
    secondary = Color(0xFF486A51), secondaryContainer = Color(0xFFDDEADB),
    onSecondaryContainer = Color(0xFF25452E),
    background = Color(0xFFF7F9F8), onBackground = Color(0xFF18251F),
    surface = Color.White, onSurface = Color(0xFF18251F),
    surfaceVariant = Color(0xFFF0F4F1), onSurfaceVariant = Color(0xFF5A6B61),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F7F4),
    surfaceContainerHigh = Color(0xFFF0F4F1), surfaceContainerHighest = Color(0xFFE7ECE8),
    outline = Color(0xFF6C7B71), outlineVariant = Color(0xFFD8E1DA),
    error = Color(0xFFAC303B), onError = Color.White,
)

@Composable
fun GitHabitTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography(
                displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                    fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1.5).sp),
                headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp),
                titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
                titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp),
                labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 15.sp),
            ),
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
            content = content,
        )
    }
}

@Composable
fun habitColor(color: HabitColor): Color {
    val dark = LocalDarkTheme.current
    return when (color) {
        HabitColor.GREEN -> if (dark) Color(0xFF8BE5AC) else Color(0xFF286E41)
        HabitColor.ORANGE -> if (dark) Color(0xFFEFB784) else Color(0xFF93551F)
        HabitColor.BLUE -> if (dark) Color(0xFF8EBAFF) else Color(0xFF3266A4)
        HabitColor.TEAL -> if (dark) Color(0xFF80D5CE) else Color(0xFF26726D)
        HabitColor.PURPLE -> if (dark) Color(0xFFC3A5F5) else Color(0xFF7950A6)
        HabitColor.PINK -> if (dark) Color(0xFFEEA1C4) else Color(0xFFAA4174)
    }
}

@Composable
fun heatColors(): List<Color> = if (LocalDarkTheme.current)
    listOf(0xFF242D36, 0xFF163E2B, 0xFF226B40, 0xFF39A35F, 0xFF85DE9D).map { Color(it) }
else listOf(0xFFE7ECE8, 0xFFC5E8CB, 0xFF83C990, 0xFF459755, 0xFF256B3E).map { Color(it) }
