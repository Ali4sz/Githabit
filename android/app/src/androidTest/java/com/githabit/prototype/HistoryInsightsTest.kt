package com.githabit.prototype

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
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
import com.githabit.prototype.ui.dateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Synthetic fixtures and clock are owned by the separate test APK, never the app. */
class HistoryInsightsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val models = ViewModelStore()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GitHabitViewModel
    private var authorized = false
    private var originalFont = "1.0"
    private val monday = LocalDate.parse("2026-11-02")
    private val today = monday.plusDays(9)
    private val clock = object : AppClock {
        override fun now(): Instant = today.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC)
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today(): LocalDate = today
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
        scenario?.onActivity { models.clear() }; scenario?.close()
        if (authorized) {
            check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
            if (originalFont == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $originalFont")
            shell("wm size reset"); shell("wm density reset")
        }
    }
    private fun launch(state: Workspace, fixedClock: Boolean = true) {
        check(authorized && scenario == null)
        WorkspaceStore(context).let { it.load(); assertEquals(WriteOutcome.Committed, it.replaceVerified(state)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("page-TODAY")
        scenario!!.onActivity { activity ->
            val real = ViewModelProvider(activity, GitHabitViewModel.Factory)[GitHabitViewModel::class.java]
            real.stopForegroundRefresh()
            vm = if (!fixedClock) real else GitHabitViewModel(context, WorkspaceStore(context), clock).also {
                models.put("history", it); activity.setContent { GitHabitApp(it) }
            }
        }
        compose.waitUntil(15_000) { vm.load is WorkspaceLoad.Ready }; compose.waitForIdle()
    }
    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; compose.waitForIdle()
    }
    private fun reveal(tag: String, list: String) { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String, list: String? = null) {
        closeSoftKeyboard()
        compose.onAllNodesWithContentDescription("Dismiss").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodesWithContentDescription("Dismiss").onFirst().performClick() }
        if (list != null) reveal(tag, list) else compose.onNodeWithTag(tag).performScrollTo()
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(15_000) { !vm.busy }; compose.waitForIdle()
    }
    private fun inspect(date: LocalDate, repo: String? = null) {
        scenario!!.onActivity { vm.overlay = Overlay.Day(date, repo) }; waitFor("date-inspector")
    }
    private fun state() = WorkspaceJson.decode(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).readText())
    private fun weekly() = Workspace(onboarded = true).createHabit(HabitDraft("Weekly repo", "", 10.0, "units", schedule = HabitSchedule.WeeklyQuota(3)), monday, "w")
        .addCommit("w", 4.0, monday, "", "a").addCommit("w", 6.0, monday, "", "b")
        .addCommit("w", 2.0, monday.plusDays(1), "", "partial")
        .addCommit("w", 10.0, monday.plusDays(3), "", "d").addCommit("w", 10.0, monday.plusDays(5), "", "s")

    @Test fun closedQuotaIsOneAssessedWeekAndCorrectionDeletionUndoReturnToDate() {
        launch(weekly())
        scenario!!.onActivity { vm.navigate(Destination.INSIGHTS, mainTab = true) }; waitFor("insights-list")
        click("previous-period", "insights-list")
        compose.onNodeWithText("Week adherence: achieved 1 / assessed 1 habit-weeks").assertExists()
        compose.onAllNodesWithText("Day adherence unavailable: no assessed habit-dates.").onFirst().assertExists()
        click("inspect-repo-w", "insights-list")
        compose.onNodeWithTag("inspected-date").assertTextEquals(dateLabel(monday))
        click("edit-commit-b", "date-inspector")
        compose.onNodeWithTag("commit-amount").performTextReplacement("1")
        click("save-commit")
        waitFor("date-inspector")
        assertEquals(monday, (vm.overlay as Overlay.Day).date)
        assertEquals(2, vm.analytics.weekly("w", monday).quota!!.qualifyingDays)
        reveal("week-summary-w", "date-inspector")
        compose.onNode(hasText("Target-completed days: 2 of 3 · closed week below quota") and hasAnyAncestor(hasTestTag("date-inspector"))).assertExists()
        click("edit-commit-b", "date-inspector")
        compose.onNodeWithText("Delete this commit").performScrollTo().performClick()
        compose.onNodeWithText("Delete commit").performClick()
        compose.waitUntil(15_000) { !vm.busy }; waitFor("date-inspector")
        assertFalse(state().commits.any { it.id == "b" })
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { !vm.busy }; compose.waitForIdle()
        assertTrue(state().commits.any { it.id == "b" && it.amount == 1.0 })
        assertEquals(monday, (vm.overlay as Overlay.Day).date)
        assertEquals(weekly().habits, state().habits)
    }

    @Test fun unscheduledMissedWeeklyPartialAndBridgeAreDistinctWithoutLostRecords() {
        val state = weekly().createHabit(HabitDraft("Weekdays", "", 10.0, "units", schedule = HabitSchedule.SelectedWeekdays(setOf(java.time.DayOfWeek.MONDAY))), monday, "days")
            .createHabit(HabitDraft("Daily", "", 10.0, "units"), monday, "daily")
            .requestWeekStart(java.time.DayOfWeek.SUNDAY, monday)
        launch(state)
        inspect(monday.plusDays(1))
        reveal("date-summary-days", "date-inspector")
        compose.onNodeWithText("Not scheduled: no obligation on this weekday.").assertExists()
        reveal("date-summary-daily", "date-inspector")
        compose.onNodeWithText("No activity: eligible daily target missed.").assertExists()
        inspect(monday.plusDays(1), "w")
        reveal("date-summary-w", "date-inspector")
        compose.onNodeWithText("Partial daily target progress: not yet a qualifying date. No daily obligation.").assertExists()
        inspect(today, "w")
        reveal("week-summary-w", "date-inspector")
        compose.onNodeWithText("Partial period: weekly practice, unscored.").assertExists()
        assertEquals(6, vm.analytics.calendar.periodOn(today).dayCount)
        assertEquals(state.commits, this.state().commits)
    }

    @Test fun futureRecordsAreInspectableAndDirtyCancellationKeepsOriginAtTwoHundredPercent() {
        shell("wm size 640x1600"); shell("settings put system font_scale 2.0")
        val future = today.plusDays(2)
        launch(weekly().addCommit("w", 10.0, future, "future retained", "future"))
        inspect(today, "w")
        reveal("explore-date", "date-inspector")
        compose.onNodeWithTag("explore-date").performTextReplacement(future.toString())
        click("explore-go", "date-inspector")
        assertEquals(future, (vm.overlay as Overlay.Day).date)
        click("edit-commit-future", "date-inspector")
        compose.onNodeWithTag("commit-note").performScrollTo().performTextReplacement("unsaved")
        click("close-sheet")
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(vm.overlay is Overlay.LogForm)
        click("close-sheet")
        compose.onNodeWithText("Discard draft").performClick()
        waitFor("date-inspector")
        assertEquals(future, (vm.overlay as Overlay.Day).date)
        assertEquals("future retained", state().commits.first { it.id == "future" }.note)
        reveal("explore-date", "date-inspector")
        compose.onNodeWithTag("explore-date").performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        compose.onNodeWithTag("explore-date").assertIsFocused()
    }

    @Test fun emptyInsightsIsZeroAndHeatmapHasNoCellClickActions() {
        launch(Workspace(onboarded = true))
        scenario!!.onActivity { vm.navigate(Destination.INSIGHTS, mainTab = true) }; waitFor("insights-list")
        compose.onNodeWithTag("period-activity").assertTextEquals("0 recorded commits")
        compose.onNodeWithText("Week adherence unavailable: no assessed habit-weeks.").assertExists()
        reveal("heatmap-canvas", "insights-list")
        assertFalse(compose.onNodeWithTag("heatmap-canvas").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        click("heatmap-explore", "insights-list")
        compose.onNodeWithTag("date-inspector").performScrollToNode(hasText("No stored commits on this date."))
        compose.onNodeWithText("No stored commits on this date.").assertIsDisplayed()
        assertTrue(state().habits.isEmpty()); assertTrue(state().commits.isEmpty())
    }

    @Test fun activityRecreationRetainsInspectionOriginAndCorrectionDraft() {
        val actualToday = LocalDate.now()
        val date = actualToday.minusDays(1)
        val state = Workspace(onboarded = true).createHabit(HabitDraft("Real lifecycle test", "", 10.0, "units"), date, "r")
            .addCommit("r", 10.0, date, "original", "c")
        launch(state, fixedClock = false)
        inspect(date, "r")
        click("edit-commit-c", "date-inspector")
        compose.onNodeWithTag("commit-amount").performTextReplacement("7")
        closeSoftKeyboard()
        scenario!!.recreate()
        waitFor("commit-amount")
        scenario!!.onActivity { activity -> vm = ViewModelProvider(activity, GitHabitViewModel.Factory)[GitHabitViewModel::class.java] }
        compose.onNodeWithTag("commit-amount").assertTextContains("7")
        click("save-commit")
        waitFor("date-inspector")
        assertEquals(date, (vm.overlay as Overlay.Day).date)
        assertEquals(7.0, state().commits.single().amount, 0.0)
        assertEquals(state.habits, state().habits)
        scenario!!.recreate()
        waitFor("date-inspector")
        compose.onNodeWithTag("inspected-date").assertTextEquals(dateLabel(date))
    }
}
