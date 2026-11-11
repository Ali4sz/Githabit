package com.githabit.prototype

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.AuthorizedTestTarget
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.BackPreviewProgress
import com.githabit.prototype.ui.GitHabitApp
import com.githabit.prototype.ui.LightColors
import com.githabit.prototype.ui.DarkColors
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Emulator-only: window changes and synthetic workspaces must never touch a phone. */
class AdaptiveNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var authorized = false
    private lateinit var vm: GitHabitViewModel

    @Before fun authorize() {
        check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
        authorized = true
        resize("720x1600")
    }

    @After fun cleanup() {
        scenario?.close()
        if (authorized) resize("720x1600")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun resize(size: String) {
        check(authorized && verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
        shell("wm density 320")
        shell("wm size $size")
    }

    private fun launch(empty: Boolean = false) {
        check(authorized)
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("githabit-workspace.json") || it.name.startsWith("githabit-recovery-") ||
                it.name == "githabit-last-good.json"
        }?.forEach { check(it.delete()) }
        var workspace = Workspace(onboarded = true)
        if (!empty) repeat(12) { index ->
            workspace = workspace.createHabit(HabitDraft("Practice $index", "", 10.0, "minutes"),
                LocalDate.now().minusDays(10), "adaptive-$index")
        }
        context.getFileStreamPath("githabit-workspace.json").writeText(WorkspaceJson.encode(workspace))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { vm = ViewModelProvider(it, GitHabitViewModel.Factory)[GitHabitViewModel::class.java] }
        waitFor("page-TODAY")
    }

    private fun waitFor(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun repos() { compose.onNodeWithTag("tab-REPOS").performClick(); waitFor("repo-list-pane") }
    private fun open(index: Int) {
        compose.onNodeWithTag("repo-list-pane").performScrollToNode(hasTestTag("habit-adaptive-$index"))
        compose.onNodeWithTag("habit-adaptive-$index").performClick()
        waitFor("repo-detail-pane")
    }
    private fun listPosition(): Float = compose.onNodeWithTag("repo-list-pane").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private val verticalDetail = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        hasAnyAncestor(hasTestTag("repo-detail-pane"))
    private fun detailScroll(): Float = compose.onNode(verticalDetail)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun scrollDetail() {
        compose.onNode(verticalDetail)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
        compose.waitForIdle()
    }

    @Test fun compactRecreationRetainsRouteListContextSelectionAndDetailScroll() {
        launch()
        repos()
        compose.onNodeWithTag("repo-search").performTextInput("Practice")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("repo-filter-All").performClick()
        open(7)
        scrollDetail()
        val scroll = detailScroll()
        assertTrue(scroll > 0f)
        scenario!!.recreate()
        waitFor("repo-detail-pane")
        assertEquals(scroll, detailScroll(), 1f)
        compose.onNodeWithTag("navigate-back").performClick()
        waitFor("repo-list-pane")
        assertTrue(listPosition() >= 3f)
        compose.onNodeWithTag("repo-list-pane").performScrollToIndex(0)
        compose.onNodeWithTag("repo-search").assertTextContains("Practice")
        compose.onNodeWithTag("repo-filter-All").assertIsSelected()
        compose.runOnIdle { assertEquals("adaptive-7", vm.selectedRepoId) }
    }

    @Test fun expandedHasNoAutoSelectionAndOnlyOneDetailInstance() {
        resize("2400x1800")
        launch()
        repos()
        waitFor("repos-two-pane")
        compose.onNodeWithTag("navigation-rail").assertExists()
        compose.onNodeWithTag("repo-selection-prompt").assertIsDisplayed()
        compose.onNodeWithTag("repo-detail-pane").assertDoesNotExist()
        compose.runOnIdle { assertNull(vm.selectedRepoId) }
        open(0)
        compose.onNodeWithTag("repo-list-pane").assertExists()
        compose.onAllNodesWithTag("detail-total").assertCountEquals(1)
        compose.onAllNodesWithTag("habit-adaptive-0").assertCountEquals(1)
        compose.onNodeWithTag("habit-adaptive-0").assertIsSelected()
        compose.onNodeWithTag("tab-INSIGHTS").performClick()
        compose.onNodeWithTag("tab-REPOS").performClick()
        waitFor("repo-selection-prompt")
        compose.onNodeWithTag("repo-detail-pane").assertDoesNotExist()
        compose.runOnIdle { assertEquals("adaptive-0", vm.selectedRepoId) }
    }

    @Test fun resizeActiveDetailPreservesRouteAndBothPaneScrollStates() {
        resize("2400x1800")
        launch()
        repos()
        open(7)
        val position = listPosition()
        scrollDetail()
        val scroll = detailScroll()
        resize("720x1600")
        waitFor("repos-single-pane")
        compose.onNodeWithTag("repo-list-pane").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Destination.DETAIL, vm.destination); assertEquals("adaptive-7", vm.selectedRepoId) }
        assertEquals(scroll, detailScroll(), 1f)
        resize("2400x1800")
        waitFor("repos-two-pane")
        assertEquals(position.toInt(), listPosition().toInt())
        assertEquals(scroll, detailScroll(), 1f)
        compose.onAllNodesWithTag("detail-total").assertCountEquals(1)
    }

    @Test fun shortLandscapeAndNarrowSplitWindowKeepVisibleBarLabels() {
        launch(empty = true)
        resize("1600x720")
        waitFor("navigation-bar")
        compose.onNodeWithTag("tab-TODAY").assertIsDisplayed()
        compose.onNodeWithTag("tab-REPOS").assertIsDisplayed().performClick()
        waitFor("repos-single-pane")
        compose.onNodeWithTag("tab-INSIGHTS").assertIsDisplayed()
        resize("640x1200")
        waitFor("navigation-bar")
        compose.onNodeWithTag("tab-REPOS").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Repos").assertIsDisplayed()
    }

    @Test fun modalBackDismissesSheetAndConfirmationBeforeChangingRoute() {
        launch()
        repos()
        open(0)
        compose.onNodeWithText("Edit repo").performScrollTo().performClick()
        waitFor("repo-name")
        compose.onNodeWithTag("repo-name").performTextReplacement("Unsaved draft")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scenario!!.recreate()
        waitFor("repo-name")
        compose.onNodeWithTag("repo-name").assertTextContains("Unsaved draft")
        pressBack()
        compose.waitUntil { compose.onAllNodesWithTag("repo-name").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(Destination.DETAIL, vm.destination) }
        compose.onNodeWithText("Archive", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Close this chapter?").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Close this chapter?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Destination.DETAIL, vm.destination) }
        pressBack()
        waitFor("page-REPOS")
    }

    @Test fun predictiveCallbacksCancelWithoutPopAndCompleteExactlyOnce() {
        launch()
        repos()
        open(0)
        val before = vm.navigation
        scenario!!.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitUntil { compose.onNodeWithTag("page-DETAIL").fetchSemanticsNode().config[BackPreviewProgress] > .5f }
        scenario!!.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitUntil { compose.onNodeWithTag("page-DETAIL").fetchSemanticsNode().config[BackPreviewProgress] == 0f }
        compose.runOnIdle { assertEquals(before, vm.navigation) }
        scenario!!.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 100f, .9f, BackEventCompat.EDGE_LEFT))
            it.onBackPressedDispatcher.onBackPressed()
        }
        waitFor("page-REPOS")
        compose.runOnIdle { assertEquals(before.history.dropLast(1), vm.navigation.history) }
        pressBack()
        waitFor("page-TODAY")
        scenario!!.onActivity { assertFalse(it.onBackPressedDispatcher.hasEnabledCallbacks()) }
    }

    @Test fun systemEdgeGesturesPreviewCancelAndCommitWithoutDuplicatePop() {
        check(shell("settings get secure navigation_mode") == "2") { "This test requires system gesture navigation." }
        launch()
        repos()
        open(0)
        val before = vm.navigation
        fun gesture(commit: Boolean) {
            val downTime = SystemClock.uptimeMillis()
            fun event(action: Int, x: Float) {
                val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, 800f, 0)
                motion.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) }
                finally { motion.recycle() }
                SystemClock.sleep(35) // Input cadence, not a route/state polling substitute.
            }
            event(MotionEvent.ACTION_DOWN, 1f)
            repeat(12) { event(MotionEvent.ACTION_MOVE, 1f + (it + 1) * 25f) }
            compose.waitUntil(5_000) {
                compose.onNodeWithTag("page-DETAIL").fetchSemanticsNode().config[BackPreviewProgress] > 0f
            }
            compose.runOnIdle { assertEquals(before, vm.navigation) }
            if (!commit) repeat(12) { event(MotionEvent.ACTION_MOVE, 301f - (it + 1) * 25f) }
            event(MotionEvent.ACTION_UP, if (commit) 301f else 1f)
        }
        gesture(commit = false)
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("page-DETAIL").fetchSemanticsNode().config[BackPreviewProgress] == 0f
        }
        compose.runOnIdle { assertEquals(before, vm.navigation) }
        gesture(commit = true)
        waitFor("page-REPOS")
        compose.runOnIdle { assertEquals(before.history.dropLast(1), vm.navigation.history) }
        compose.onNodeWithTag("tab-TODAY").performClick()
        waitFor("page-TODAY")
    }

    @Test fun systemTwoHundredPercentNavigationLabelsRemainReadableAcrossWindows() {
        val original = shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 2.0")
            resize("640x1600")
            launch(empty = true)
            fun labels() {
                listOf("Today", "Repos", "Insights").forEach { label ->
                    val node = compose.onNodeWithText(label)
                    node.assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertTrue("Missing layout for $label", layouts.isNotEmpty())
                    val layout = layouts.single()
                    // In 1.9.2, paragraph allocation can exceed a wrap-content Text's
                    // measured width even when every glyph fits. Check rendered lines,
                    // not didOverflowWidth's paragraph-allocation comparison.
                    assertFalse("Clipped label height: $label", layout.didOverflowHeight)
                    repeat(layout.lineCount) { line ->
                        assertFalse("Ellipsized label: $label", layout.isLineEllipsized(line))
                        assertTrue("Clipped label start: $label", layout.getLineLeft(line) >= -1f)
                        assertTrue("Clipped label end: $label", layout.getLineRight(line) <= layout.size.width + 1f)
                    }
                }
            }
            waitFor("navigation-bar")
            labels()
            resize("1600x720")
            waitFor("navigation-bar")
            labels()
            resize("2400x1800")
            waitFor("navigation-rail")
            labels()
        } finally {
            if (original == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $original")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardFocusOutlineBelongsOnlyToTheFocusedNavigationItem() {
        launch(empty = true)
        lateinit var inputMode: InputModeManager
        scenario!!.onActivity { activity ->
            activity.setContent {
                inputMode = LocalInputModeManager.current
                GitHabitApp(vm)
            }
        }
        waitFor("page-TODAY")
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val primary = (if (dark) DarkColors else LightColors).primary.toArgb()
        for (focused in listOf("REPOS", "INSIGHTS")) {
            val node = compose.onNodeWithTag("tab-$focused")
            node.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            node.assertIsFocused()
            listOf("TODAY", "REPOS", "INSIGHTS").forEach { tab ->
                val target = compose.onNodeWithTag("tab-$tab")
                val pixels = target.captureToImage().toPixelMap()
                val hasOutline = pixels[pixels.width / 2, 1].toArgb() == primary
                assertEquals("Focus outline leaked to $tab", tab == focused, hasOutline)
            }
            node.performKeyInput { pressKey(Key.Enter) }
            waitFor("page-$focused")
        }
    }
}
