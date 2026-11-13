package com.githabit.prototype

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.AuthorizedTestTarget
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.GitHabitApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test-owned fixtures/clocks only; all writes and display changes require a fresh emulator opt-in. */
class TodayFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val models = ViewModelStore()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GitHabitViewModel
    private var authorized = false
    private var originalFont = "1.0"
    private val monday = LocalDate.parse("2026-11-02")
    private val draft = HabitDraft("Explicit practice", "", 10.0, "minutes")
    private val clock = object : AppClock {
        var instant = Instant.parse("2026-11-04T12:00:00Z")
        override fun now() = instant
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today() = instant.atZone(zone()).toLocalDate()
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before fun authorize() {
        check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
        authorized = true
        originalFont = shell("settings get system font_scale")
        shell("wm density 320"); shell("wm size 720x1600"); shell("settings put system font_scale 1.0")
    }
    @After fun close() {
        scenario?.onActivity { models.clear() }
        scenario?.close()
        if (authorized) {
            check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
            if (originalFont == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $originalFont")
            shell("wm size reset"); shell("wm density reset")
        }
    }
    private fun launch(state: Workspace, fixedClock: Boolean = false) {
        check(authorized && scenario == null)
        val store = WorkspaceStore(context)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(state))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("page-TODAY") // Let production initialization finish before a second test-owned reader.
        scenario!!.onActivity { activity ->
            val real = ViewModelProvider(activity, GitHabitViewModel.Factory)[GitHabitViewModel::class.java]
            real.stopForegroundRefresh()
            vm = if (fixedClock) GitHabitViewModel(context, WorkspaceStore(context), clock).also {
                models.put("today", it)
                activity.setContent { GitHabitApp(it) }
            } else real
        }
        compose.waitUntil(15_000) { vm.load is WorkspaceLoad.Ready }
        compose.waitForIdle()
    }
    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun dismissNotice() {
        compose.onAllNodesWithContentDescription("Dismiss").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onAllNodesWithContentDescription("Dismiss").onFirst().performClick()
        }
    }
    private fun reveal(tag: String) {
        compose.onNodeWithTag("today-list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag))
    }
    private fun click(tag: String, today: Boolean = false) {
        dismissNotice(); closeSoftKeyboard()
        if (today) reveal(tag) else if (!tag.startsWith("tab-")) compose.onNodeWithTag(tag).performScrollTo()
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(15_000) { !vm.busy }
        compose.waitForIdle()
    }
    private fun replace(state: Workspace) {
        scenario!!.onActivity { vm.replaceWorkspace(state) }
        compose.waitUntil(15_000) { !vm.busy }
        compose.waitForIdle()
    }
    private fun undo() {
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { !vm.busy }
        compose.waitForIdle()
    }

    @Test fun firstActionIsInMeasuredInitialViewportAndOneExplicitLogIsExact() {
        val today = LocalDate.now()
        launch(Workspace(onboarded = true).createHabit(draft, today, "daily"))
        compose.onNodeWithTag("log-daily").assertIsDisplayed()
        compose.onNodeWithTag("complete-daily").assertIsDisplayed()
        val page = compose.onNodeWithTag("page-TODAY").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("complete-daily").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.bottom <= page.bottom && controls.top >= page.top)
        scenario!!.onActivity { activity ->
            val metrics = activity.resources.displayMetrics
            val window = activity.windowManager.currentWindowMetrics
            val insets = window.windowInsets.getInsets(android.view.WindowInsets.Type.systemBars())
            Log.i("Task10Viewport", "window=${window.bounds}; density=${metrics.density}; font=${activity.resources.configuration.fontScale}; systemInsets=$insets; usablePagePx=$page; completePx=$controls")
            assertEquals(360, window.bounds.width() / metrics.density.toInt())
            assertEquals(800, window.bounds.height() / metrics.density.toInt())
        }
        click("log-daily", today = true)
        compose.onNodeWithTag("commit-amount").performTextInput("4")
        click("commit-remaining")
        compose.onNodeWithTag("commit-amount").assertTextContains("10") // Opening total was zero, fills but never submits.
        assertTrue(vm.workspace.commits.isEmpty())
        compose.onNodeWithTag("commit-amount").performTextReplacement("4")
        click("save-commit")
        assertEquals(1, vm.workspace.habits.size)
        assertEquals(1, vm.workspace.commits.size)
        reveal("progress-daily")
        compose.onNodeWithTag("progress-daily", useUnmergedTree = true).assertTextEquals("Today: 4 / 10 minutes")
        reveal("today-activity-count")
        compose.onNodeWithTag("today-activity-count").assertTextEquals("1 commit recorded today")
    }

    @Test fun multipleMondayCommitsPartialTuesdayWednesdayCompletionAndQuotaUndo() {
        val state = Workspace(onboarded = true).createHabit(draft.copy(schedule = HabitSchedule.WeeklyQuota(3)), monday, "weekly")
            .addCommit("weekly", 4.0, monday, id = "m1").addCommit("weekly", 6.0, monday, id = "m2")
            .addCommit("weekly", 3.0, monday.plusDays(1), id = "t")
        launch(state, fixedClock = true)
        compose.onNodeWithTag("log-weekly").assertIsDisplayed()
        compose.onNodeWithTag("complete-weekly").assertIsDisplayed()
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 1/3")
        click("complete-weekly", today = true)
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 2/3")
        compose.onNodeWithTag("complete-weekly").assertIsNotEnabled()
        compose.onNodeWithText("Today already qualifies", substring = true).assertExists()
        reveal("today-remaining-filter")
        compose.onNodeWithTag("today-remaining-filter").performClick()
        reveal("habit-weekly") // Today reached its target, but the week's quota still remains.
        undo()
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 1/3")
        // Correct historical Tuesday, preserving its date. Wednesday completion then meets N.
        scenario!!.onActivity { vm.overlay = Overlay.LogForm("weekly", "t") }
        waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextReplacement("10")
        click("save-commit")
        click("complete-weekly", today = true)
        reveal("today-filtered-empty")
        compose.onNodeWithTag("today-status-QUOTA_MET").assertExists()
        undo()
        reveal("habit-weekly")
        click("today-remaining-filter", today = true)
        click("complete-weekly", today = true)
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 3/3")
        click("log-weekly", today = true)
        compose.onNodeWithTag("commit-amount").performTextInput("2")
        click("save-commit")
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 3/3")
        assertEquals(12.0, vm.analytics.daily("weekly", vm.today).progress.total, 0.0)
    }

    @Test fun pendingPreferencePreservesQuotaAndBridgeKeepsDailyObligations() {
        val state = Workspace(onboarded = true).createHabit(draft.copy(schedule = HabitSchedule.WeeklyQuota(3)), monday, "weekly")
            .createHabit(draft, monday, "daily")
            .createHabit(draft.copy(schedule = HabitSchedule.SelectedWeekdays(setOf(java.time.DayOfWeek.MONDAY))), monday, "selected")
            .addCommit("weekly", 10.0, monday, id = "m")
        launch(state, fixedClock = true)
        val period = vm.analytics.calendar.periodOn(vm.today)
        scenario!!.onActivity { vm.setWeekStart(java.time.DayOfWeek.SUNDAY) }
        compose.waitUntil(15_000) { !vm.busy }; compose.waitForIdle()
        assertEquals(period, vm.analytics.calendar.periodOn(vm.today))
        reveal("today-quota-weekly")
        compose.onNodeWithTag("today-quota-weekly", useUnmergedTree = true).assertTextEquals("Target-completed days: 1/3")
        scenario!!.onActivity { clock.instant = Instant.parse("2026-11-09T12:00:00Z"); vm.refreshDate() }
        compose.waitUntil(15_000) { vm.workspace.calendar.pending == null }; compose.waitForIdle()
        reveal("today-group-PARTIAL_WEEK_PRACTICE")
        compose.onNodeWithText("Calendar bridge: Nov 9, 2026 – Nov 14, 2026. This partial period is unscored.").assertExists()
        reveal("today-practice-weekly")
        compose.onNodeWithTag("today-practice-weekly", useUnmergedTree = true).assertTextEquals("0 target-completed practice dates")
        click("complete-weekly", today = true)
        click("today-remaining-filter", today = true)
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("habit-selected"))
        compose.onNodeWithTag("habit-weekly").assertDoesNotExist()
        reveal("habit-daily"); compose.onNodeWithTag("habit-daily").assertExists()
    }

    @Test fun unscheduledDatesEmptyStatusesAndCreationNeverInventHistory() {
        launch(Workspace(onboarded = true))
        compose.onNodeWithTag("today-status-NO_REPOS").assertExists()
        compose.onNodeWithTag("today-activity-count").assertTextEquals("0 commits recorded today")
        val today = vm.today
        replace(Workspace(onboarded = true).createHabit(draft.copy(schedule = HabitSchedule.SelectedWeekdays(
            setOf(today.dayOfWeek.plus(1)))), today, "optional"))
        compose.onNodeWithTag("today-status-NO_SCHEDULE").assertExists()
        reveal("habit-optional")
        compose.onNodeWithTag("log-optional").assertDoesNotExist()
        compose.onNodeWithTag("complete-optional").assertDoesNotExist()
        click("today-open-optional", today = true)
        waitFor("page-DETAIL")
        compose.onNodeWithText("Log progress").performScrollTo().performClick()
        waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextInput("2")
        click("save-commit")
        click("tab-TODAY")
        reveal("progress-optional")
        compose.onNodeWithTag("progress-optional", useUnmergedTree = true).assertTextEquals("Today: 2 / 10 minutes")
        assertEquals(1, vm.workspace.commits.size)
        assertFalse(vm.analytics.daily("optional", today).qualifies)
    }

    @Test fun remainingRemovalFeedbackUndoAndSavedScrollSurviveRecreation() {
        val today = LocalDate.now()
        var state = Workspace(onboarded = true)
        repeat(25) { state = state.createHabit(draft.copy(name = "Repo $it"), today, "daily-$it") }
        launch(state)
        click("today-remaining-filter", today = true)
        click("log-daily-15", today = true)
        compose.onNodeWithTag("commit-amount").performTextInput("4")
        click("save-commit")
        compose.onNodeWithTag("habit-daily-15").assertIsDisplayed()
        click("complete-daily-15", today = true)
        compose.onNodeWithText("Today’s remaining target saved for Repo 15.", substring = true).assertExists()
        undo()
        reveal("progress-daily-15")
        compose.onNodeWithTag("progress-daily-15", useUnmergedTree = true).assertTextEquals("Today: 4 / 10 minutes")
        click("log-daily-15", today = true)
        compose.onNodeWithTag("commit-amount").performTextInput("6")
        click("save-commit")
        compose.onNodeWithText("Today’s target reached; moved out of Remaining.", substring = true).assertExists()
        undo()
        reveal("habit-daily-15")
        scenario!!.recreate()
        scenario!!.onActivity { vm = ViewModelProvider(it, GitHabitViewModel.Factory)[GitHabitViewModel::class.java] }
        waitFor("page-TODAY")
        compose.onNodeWithTag("habit-daily-15").assertIsDisplayed()
        reveal("today-remaining-filter")
        compose.onNodeWithTag("today-remaining-filter").assertIsSelected()
    }

    @Test fun narrowSystem200PercentTextStillLogsAndCompletesWithoutUnrelatedNavigation() {
        shell("wm size 640x1600"); shell("settings put system font_scale 2.0")
        val today = LocalDate.now()
        launch(Workspace(onboarded = true).createHabit(draft.copy(name = "A long explicit habit name for wrapping"), today, "daily"))
        scenario!!.onActivity { assertEquals(2.0f, it.resources.configuration.fontScale, 0.0f) }
        click("log-daily", today = true)
        compose.onNodeWithTag("commit-amount").performTextInput("4")
        click("save-commit")
        click("complete-daily", today = true)
        assertEquals(10.0, vm.analytics.daily("daily", today).progress.total, 0.0)
        assertEquals(2, vm.workspace.commits.size)
        reveal("today-status-DUE_COMPLETE")
        compose.onNodeWithTag("today-status-DUE_COMPLETE").assertIsDisplayed()
    }
}
