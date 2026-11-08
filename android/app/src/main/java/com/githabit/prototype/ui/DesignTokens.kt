package com.githabit.prototype.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Layout space, not fixed text/control heights. Content must remain free to grow. */
object GitHabitSpace {
    val micro = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val page = 20.dp
    val section = 24.dp
}

object GitHabitSize {
    val minimumTouch = 48.dp
    val icon = 24.dp
    val supportingIcon = 20.dp
    val glyph = 40.dp
    val compactBrand = 34.dp
    val border = 1.dp
    val focusBorder = 2.dp
    val progressTrack = 4.dp
}

internal val GitHabitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Keep the compact, slightly tightened brand headings; use scalable M3 roles for copy.
internal val GitHabitTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,
        fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

/** Brand/plot roles are deliberately separate from general-purpose Material text. */
object GitHabitType {
    val wordmark = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-1).sp)
    val compactWordmark = wordmark.copy(fontSize = 23.sp, lineHeight = 28.sp)
    val eyebrow = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 1.sp)
    val statistic = TextStyle(fontWeight = FontWeight.Bold,
        fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp)
    val chartLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
}
