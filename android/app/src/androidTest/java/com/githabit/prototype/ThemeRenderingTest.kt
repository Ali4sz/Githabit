package com.githabit.prototype

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.AuthorizedTestTarget
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.*
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Rendering-only host: no workspace IO or runtime seeding. Pixel checks require an emulator. */
class ThemeRenderingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @Before fun authorizeThenLaunch() {
        check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR) {
            "Pixel capture tests run only on an authorized disposable emulator."
        }
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After fun close() { scenario?.close() }

    @Test fun effectiveThemeUpdatesContentAndSystemBarIconsTogether() {
        var preference by mutableStateOf(ThemePreference.SYSTEM)
        var systemDark by mutableStateOf(false)
        scenario!!.onActivity { activity ->
            activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config) {
                    val dark = preference.isDark(androidx.compose.foundation.isSystemInDarkTheme())
                    GitHabitSystemBars(dark)
                    GitHabitTheme(dark) {
                        Surface(Modifier.fillMaxSize().testTag("theme-surface")) { Text("Theme check") }
                    }
                }
            }
        }
        for (mode in ThemePreference.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { preference = mode; systemDark = night }
            compose.waitForIdle()
            val dark = mode.isDark(night)
            val pixels = compose.onNodeWithTag("theme-surface").captureToImage().toPixelMap()
            assertEquals((if (dark) DarkColors else LightColors).surface.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
            scenario!!.onActivity {
                val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
                assertEquals(!dark, controller.isAppearanceLightStatusBars)
                assertEquals(!dark, controller.isAppearanceLightNavigationBars)
            }
        }
    }

    @Suppress("DEPRECATION") // Inspect legacy startup resources still used on API 26–34.
    @Test fun startupResourcesMatchSystemAndHaveExplicitBarBackgrounds() {
        scenario!!.onActivity { activity ->
            for (night in listOf(false, true)) {
                val configuration = Configuration(activity.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = activity.createConfigurationContext(configuration)
                val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_GitHabit, true) }
                fun attribute(id: Int): Int {
                    val value = android.util.TypedValue()
                    assertTrue(theme.resolveAttribute(id, value, true))
                    return value.data
                }
                val colors = if (night) DarkColors else LightColors
                assertEquals(colors.background.toArgb(), attribute(android.R.attr.colorBackground))
                assertEquals(colors.background.toArgb(), attribute(android.R.attr.statusBarColor))
                assertEquals(!night, attribute(android.R.attr.windowLightStatusBar) != 0)
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    assertEquals(!night, attribute(android.R.attr.windowLightNavigationBar) != 0)
                    assertEquals(colors.surface.toArgb(), attribute(android.R.attr.navigationBarColor))
                }
            }
        }
    }

    @Test fun sharedCardScalesToTwoHundredPercentWithReachable48dpActions() {
        val day = LocalDate.of(2026, 11, 8)
        val workspace = Workspace().createHabit(HabitDraft("Practice", "Personal routine", 10.0, "minutes"), day, "render")
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var logs = 0
        scenario!!.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    GitHabitTheme(dark) {
                        Surface {
                            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                                HabitCard(workspace, workspace.habits.single(), day, false, {}, { logs++ }, {}, {})
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { dark = night; scale = fontScale }
            compose.onNodeWithTag("log-render").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("complete-render").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            assertNoOverflowingText()
        }
        compose.runOnIdle { assertEquals(4, logs) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sharedControlFocusHasVisibleInkAndKeyboardActivation() {
        val day = LocalDate.of(2026, 11, 8)
        val workspace = Workspace().createHabit(HabitDraft("Practice", "", 10.0, "minutes"), day, "focus")
        var dark by mutableStateOf(false)
        var logs = 0
        lateinit var inputMode: InputModeManager
        scenario!!.onActivity { activity ->
            activity.setContent {
                inputMode = LocalInputModeManager.current
                GitHabitTheme(dark) {
                    Surface {
                        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                            HabitCard(workspace, workspace.habits.single(), day, false, {}, { logs++ }, {}, {})
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle {
                dark = night
                assertTrue(inputMode.requestInputMode(InputMode.Keyboard))
            }
            val log = compose.onNodeWithTag("log-focus").performScrollTo()
            log.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            log.assertIsFocused()
            val pixels = log.captureToImage().toPixelMap()
            val primary = (if (night) DarkColors else LightColors).primary.toArgb()
            // Top-center border, inside the rounded shape and away from text/ripple edges.
            assertEquals(primary, pixels[pixels.width / 2, 1].toArgb())
            log.performKeyInput { pressKey(Key.Enter) }
        }
        compose.runOnIdle { assertEquals(2, logs) }
    }

    @Test fun renderedSecondaryBadgeAndErrorColorsUseContractRoles() {
        var dark by mutableStateOf(false)
        scenario!!.onActivity { activity ->
            activity.setContent {
                GitHabitTheme(dark) {
                    Surface {
                        Column(Modifier.width(320.dp)) {
                            Panel(Modifier.testTag("pair-panel")) {
                                Subtitle("Secondary copy")
                                StatusBadge("Achieved", positive = true)
                                OutlinedTextField("", {}, isError = true, label = { Text("Target") },
                                    supportingText = { Text("Enter a positive amount") }, modifier = Modifier.testTag("error-field"))
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            compose.onNodeWithTag("error-field").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("pair-panel").captureToImage().toPixelMap()
            val colors = if (night) DarkColors else LightColors
            val actual = buildSet {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) add(pixels[x, y].toArgb())
            }
            // Solid interior glyph pixels; antialiased edges are not contrast-reference colors.
            for (expected in listOf(colors.surface, colors.onSurfaceVariant, colors.primaryContainer,
                colors.onPrimaryContainer, colors.error)) assertTrue("Missing rendered role $expected", expected.toArgb() in actual)
            assertNoOverflowingText()
        }
    }

    private fun assertNoOverflowingText() {
        compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val action = node.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action
            val layouts = mutableListOf<TextLayoutResult>()
            action?.invoke(layouts)
            layouts.forEach {
                // The String Text semantics path reconstructs a paragraph at maxWidth,
                // even for a much narrower wrap-content node. Check occupied line bounds,
                // not hasVisualOverflow's comparison to that empty paragraph width.
                val message = "Clipped text: ${it.layoutInput.text}; size=${it.size}"
                assertFalse(message, it.multiParagraph.didExceedMaxLines)
                for (line in 0 until it.lineCount) {
                    assertFalse(message, it.isLineEllipsized(line))
                    assertTrue(message, it.getLineLeft(line) >= -1f)
                    assertTrue(message, it.getLineRight(line) <= it.size.width + 1f)
                    assertTrue(message, it.getLineBottom(line) <= it.size.height + 1f)
                }
            }
        }
    }

    @Test fun textBoundsCheckRejectsRealClipping() {
        scenario!!.onActivity { activity ->
            activity.setContent {
                GitHabitTheme {
                    Text("This label cannot fit", modifier = Modifier.width(16.dp), maxLines = 1)
                }
            }
        }
        compose.waitForIdle()
        assertThrows(AssertionError::class.java) { assertNoOverflowingText() }
    }
}
