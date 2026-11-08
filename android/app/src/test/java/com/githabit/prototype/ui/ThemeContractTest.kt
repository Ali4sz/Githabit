package com.githabit.prototype.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.githabit.prototype.domain.HabitColor
import com.githabit.prototype.domain.ThemePreference
import org.junit.Assert.*
import org.junit.Test

class ThemeContractTest {
    @Test fun themeResolutionHonorsExplicitOverrides() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemePreference.SYSTEM.isDark(systemDark))
            assertFalse(ThemePreference.LIGHT.isDark(systemDark))
            assertTrue(ThemePreference.DARK.isDark(systemDark))
        }
    }

    @Test fun materialTextAndControlPairsMeetContrastInBothThemes() {
        for (scheme in listOf(LightColors, DarkColors)) with(scheme) {
            val pairs = listOf(
                "primary" to (onPrimary to primary), "primaryContainer" to (onPrimaryContainer to primaryContainer),
                "secondary" to (onSecondary to secondary), "secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                "tertiary" to (onTertiary to tertiary), "tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                "error" to (onError to error), "errorContainer" to (onErrorContainer to errorContainer),
                "snackbar" to (inverseOnSurface to inverseSurface), "snackbar action" to (inversePrimary to inverseSurface),
                "brand" to (primary to primaryContainer),
                "tonal action focus" to (primary to secondaryContainer),
            )
            pairs.forEach { (name, pair) -> checkContrast(name, pair.first, pair.second, 4.5) }
            surfaces().forEach { surface ->
                for ((name, foreground) in listOf("body" to onSurface, "secondary copy" to onSurfaceVariant,
                    "action/focus" to primary, "error text/focus" to error)) {
                    checkContrast(name, foreground, surface, 4.5)
                }
                checkContrast("essential boundary", outline, surface, 3.0)
            }
        }
    }

    @Test fun accentsMeetIconAndProgressContrastIncludingTranslucentGlyphs() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) DarkColors else LightColors
            for (habit in HabitColor.entries) {
                val accent = HabitAccentPalette.color(habit, dark)
                checkContrast("$habit progress", accent, scheme.surfaceContainerHighest, 3.0)
                // Selected accent chips use secondaryContainer, glyphs also appear outside cards.
                for (surface in scheme.surfaces() + scheme.secondaryContainer) {
                    val glyph = accent.copy(alpha = HabitAccentPalette.glyphContainerAlpha).compositeOver(surface)
                    checkContrast("$habit glyph dark=$dark", accent, glyph, 3.0)
                }
            }
        }
    }

    @Test fun chartPalettesHaveFiveOrderedLevelsAndAreNotErrorPalettes() {
        for (dark in listOf(false, true)) {
            val levels = HeatmapPalette.levels(dark)
            assertEquals(5, levels.distinct().size)
            // Level zero is neutral; only the four activity levels carry ordered intensity.
            levels.drop(1).zipWithNext().forEach { (first, next) ->
                assertTrue(if (dark) first.luminance() < next.luminance() else first.luminance() > next.luminance())
            }
            assertFalse(levels.contains((if (dark) DarkColors else LightColors).error))
        }
    }

    private fun ColorScheme.surfaces() = listOf(background, surface, surfaceVariant,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh,
        surfaceContainerHighest, surfaceDim, surfaceBright)

    private fun checkContrast(label: String, foreground: Color, background: Color, minimum: Double) {
        val first = foreground.compositeOver(background).luminance().toDouble()
        val second = background.luminance().toDouble()
        val ratio = (maxOf(first, second) + .05) / (minOf(first, second) + .05)
        assertTrue("$label: $ratio < $minimum ($foreground on $background)", ratio >= minimum)
    }
}
