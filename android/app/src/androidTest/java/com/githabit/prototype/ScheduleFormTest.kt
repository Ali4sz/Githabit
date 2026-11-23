package com.githabit.prototype

import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.activity.compose.setContent
import com.githabit.prototype.ui.GitHabitApp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.AuthorizedTestTarget
import com.githabit.prototype.fixtures.verifyDisposableDevice
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Only a freshly opted-in disposable emulator may change display settings or fixture files. */
class ScheduleFormTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GitHabitViewModel
    private var authorized = false
    private var originalFont = "1.0"

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Before fun launch() {
        check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
        authorized = true
        originalFont = shell("settings get system font_scale")
        shell("wm density 320"); shell("wm size 720x1600")
        context.filesDir.listFiles()?.filter { it.name.startsWith("githabit-workspace.json") ||
            it.name.startsWith("githabit-recovery-") || it.name == "githabit-last-good.json" }?.forEach { check(it.delete()) }
        context.getFileStreamPath("githabit-workspace.json").writeText(WorkspaceJson.encode(Workspace(onboarded = true)))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        bind()
        waitFor("page-TODAY")
    }

    @After fun cleanup() {
        scenario?.close()
        if (authorized) {
            check(verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR)
            if (originalFont == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $originalFont")
            shell("wm size 720x1600"); shell("wm density 320")
        }
    }

    private fun bind() { scenario!!.onActivity { vm = ViewModelProvider(it, GitHabitViewModel.Factory)[GitHabitViewModel::class.java] } }
    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(tag: String) { closeSoftKeyboard(); compose.onNodeWithTag(tag).performScrollTo().performClick(); compose.waitForIdle() }
    private fun open(id: String? = null) { scenario!!.onActivity { vm.overlay = Overlay.RepoForm(id) }; waitFor("repo-name") }
    private fun fill(name: String = "Explicit practice") {
        compose.onNodeWithTag("repo-name").performTextReplacement(name)
        compose.onNodeWithTag("repo-target").performScrollTo().performTextReplacement("12.5")
        compose.onNodeWithTag("repo-unit").performScrollTo().performTextReplacement("minutes")
        closeSoftKeyboard()
    }
    private fun save() { click("save-repo"); compose.waitUntil(15_000) { !vm.busy }; compose.waitForIdle() }

    @Test fun requiredFieldsStartBlankAndInvalidSubmissionCreatesNothing() {
        open()
        for (tag in listOf("repo-name", "repo-target", "repo-unit")) {
            assertEquals("", compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        }
        save()
        compose.onNodeWithTag("form-error").assertExists()
        scenario!!.onActivity { assertTrue(vm.workspace.habits.isEmpty()); assertTrue(vm.workspace.commits.isEmpty()) }
        fill()
        click("schedule-WEEKDAYS")
        save()
        compose.onNodeWithText("Choose at least one weekday.", substring = true).assertExists()
        scenario!!.onActivity { assertTrue(vm.workspace.habits.isEmpty()) }
        click("close-sheet")
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Discard draft").performClick()
        waitFor("page-TODAY")
        scenario!!.onActivity { assertNull(vm.overlay); assertTrue(vm.workspace.habits.isEmpty()) }
    }

    @Test fun allScheduleKindsSaveReopenAndEditWithoutChangingEarlierExpectations() {
        for (choice in ScheduleChoice.entries) {
            open(); fill("Practice ${choice.name}")
            click("schedule-${choice.name}")
            compose.onNodeWithTag("schedule-${choice.name}").assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            when (choice) {
                ScheduleChoice.DAILY -> Unit
                ScheduleChoice.WEEKDAYS -> {
                    click("weekday-MONDAY"); click("weekday-SUNDAY")
                    compose.onNodeWithTag("weekday-SUNDAY").assertIsOn()
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                }
                ScheduleChoice.WEEKLY -> { click("weekly-days-3"); compose.onNodeWithTag("weekly-explanation").assertTextContains("3 different days", substring = true) }
            }
            val expected = (vm.formState as FormState.Repo).values.schedule()
            save()
            scenario!!.onActivity { assertNull(vm.overlay) }
            val habit = vm.workspace.habits.last()
            assertEquals(expected, habit.versionOn(vm.today)!!.schedule)
            open(habit.id)
            compose.onNodeWithTag("schedule-${choice.name}").assertIsSelected()
            val writesBefore = context.getFileStreamPath("githabit-workspace.json").readText()
            save() // No-op Save must close without a configuration version or write.
            assertEquals(writesBefore, context.getFileStreamPath("githabit-workspace.json").readText())
            open(habit.id)
            click("schedule-WEEKLY"); click("weekly-days-7"); save()
            assertEquals(HabitSchedule.WeeklyQuota(7), vm.workspace.habits.last().versionOn(vm.today)!!.schedule)
            scenario!!.onActivity { vm.navigate(Destination.DETAIL, habit.id) }
            waitFor("repo-detail-pane")
            compose.onNodeWithTag("schedule-summary-${habit.id}").assertTextContains("7 different days", substring = true)
            scenario!!.onActivity { vm.navigate(Destination.TODAY, mainTab = true) }
            waitFor("page-TODAY")
        }
    }

    @Test fun weekdayOrderUsesEffectiveConventionAndSwitchingKindsPreservesSelections() {
        scenario!!.onActivity { vm.setWeekStart(DayOfWeek.SUNDAY) }
        compose.waitUntil(15_000) { !vm.busy }
        open(); fill(); click("schedule-WEEKDAYS")
        val sunday = compose.onNodeWithTag("weekday-SUNDAY").fetchSemanticsNode().positionInRoot.y
        val monday = compose.onNodeWithTag("weekday-MONDAY").fetchSemanticsNode().positionInRoot.y
        assertTrue(sunday < monday)
        click("weekday-SUNDAY"); click("weekday-TUESDAY")
        click("schedule-WEEKLY"); click("weekly-days-4")
        click("schedule-WEEKDAYS")
        compose.onNodeWithTag("weekday-SUNDAY").assertIsOn()
        compose.onNodeWithTag("weekday-TUESDAY").assertIsOn()
        click("schedule-WEEKLY")
        compose.onNodeWithTag("weekly-days-4").assertIsSelected()
        scenario!!.recreate(); bind(); waitFor("repo-name")
        compose.onNodeWithTag("repo-name").assertTextContains("Explicit practice")
        compose.onNodeWithTag("weekly-days-4").assertIsSelected()
        scenario!!.onActivity { assertTrue(vm.workspace.habits.isEmpty()) }
    }

    @Test fun backCloseNavigationScrimAndDragRequireDiscardAndCancelKeepsDraft() {
        open(); fill()
        pressBack()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("repo-name").assertTextContains("Explicit practice")
        click("close-sheet")
        compose.onNodeWithText("Cancel").performClick()
        scenario!!.onActivity { vm.navigate(Destination.SETTINGS) }
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000) { compose.onNodeWithTag("workspace-sheet").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription] == context.getString(R.string.sheet_expanded) }
        // The scrim has its own semantic close action; exclude the form's close IconButton.
        compose.onNode(hasContentDescription("Close sheet") and !hasAnyAncestor(hasTestTag("close-sheet")),
            useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000) { compose.onNodeWithTag("workspace-sheet").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription] == context.getString(R.string.sheet_expanded) }
        // Drag the sheet's handle rather than the scrollable form.
        compose.onNodeWithTag("sheet-drag-handle", useUnmergedTree = true).performTouchInput { swipeDown(endY = height + 1000f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        scenario!!.onActivity { vm.requestDismissForm() }
        compose.onNodeWithText("Discard draft").performClick()
        scenario!!.onActivity { assertNull(vm.overlay); assertTrue(vm.workspace.habits.isEmpty()) }
    }

    @Test fun unsavedLogAndCorrectionRequireDiscardAndRevertedRepoDoesNot() {
        open(); fill(); save()
        val habit = vm.workspace.habits.single()
        open(habit.id)
        compose.onNodeWithTag("repo-name").performTextReplacement("Changed")
        compose.onNodeWithTag("repo-name").performTextReplacement(habit.name)
        click("close-sheet")
        compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        scenario!!.onActivity { vm.overlay = Overlay.LogForm(habit.id) }; waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextInput("5")
        compose.onNodeWithTag("commit-note").performTextInput("Unsaved")
        closeSoftKeyboard(); pressBack()
        compose.onNodeWithText("Cancel").performClick()
        scenario!!.recreate(); bind(); waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").assertTextContains("5")
        compose.onNodeWithTag("commit-note").assertTextContains("Unsaved")
        click("close-sheet"); compose.onNodeWithText("Discard draft").performClick()
        assertTrue(vm.workspace.commits.isEmpty())
        scenario!!.onActivity { vm.saveCommit(habit.id, null, 2.0, "Saved note") }
        compose.waitUntil(15_000) { !vm.busy }
        val commit = vm.workspace.commits.single()
        scenario!!.onActivity { vm.overlay = Overlay.LogForm(habit.id, commit.id) }; waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextClearance()
        closeSoftKeyboard(); click("close-sheet")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("", compose.onNodeWithTag("commit-amount").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        click("close-sheet"); compose.onNodeWithText("Discard draft").performClick()
        assertEquals(commit, vm.workspace.commits.single())
    }

    @Test fun weeklySummaryCapsQuotaAndIdentifiesCalendarBridge() {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        var state = Workspace(onboarded = true).createHabit(HabitDraft("Weekly practice", "", 1.0, "minutes",
            schedule = HabitSchedule.WeeklyQuota(1)), monday, "weekly")
        state = state.addCommit("weekly", 1.0, monday)
        state = state.addCommit("weekly", 1.0, monday.plusDays(1))
        scenario!!.onActivity { vm.replaceWorkspace(state) }
        compose.waitUntil(15_000) { !vm.busy }
        scenario!!.onActivity { vm.navigate(Destination.DETAIL, "weekly") }; waitFor("repo-detail-pane")
        compose.onNodeWithText("Target-completed days: 1 of 1", substring = true).assertExists()
        compose.onNodeWithText("Target-completed days: 2 of 1", substring = true).assertDoesNotExist()
        val end = today.plusDays(3)
        val bridge = state.copy(calendar = WeekCalendar(today.dayOfWeek,
            transitions = listOf(WeekTransition(today, end.dayOfWeek))))
        scenario!!.onActivity { vm.replaceWorkspace(bridge) }
        compose.waitUntil(15_000) { !vm.busy }
        scenario!!.onActivity { vm.navigate(Destination.DETAIL, "weekly") }; waitFor("repo-detail-pane")
        compose.onNodeWithText("Calendar bridge:", substring = true).assertExists()
        compose.onNodeWithText("transitional period, unscored", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun labeledScheduleRowsSupportFocusAndEnterActivation() {
        open(); fill()
        lateinit var inputMode: InputModeManager
        scenario!!.onActivity { activity -> activity.setContent {
            inputMode = LocalInputModeManager.current
            GitHabitApp(vm)
        } }
        waitFor("repo-name")
        // The sheet window owns a separate input mode manager; request keyboard mode in its node action.
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val weekly = compose.onNodeWithTag("schedule-WEEKLY")
        weekly.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        weekly.assertIsFocused()
        weekly.performKeyInput { pressKey(Key.Enter) }
        weekly.assertIsSelected()
        val three = compose.onNodeWithTag("weekly-days-3")
        three.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        three.performKeyInput { pressKey(Key.Enter) }; three.assertIsSelected()
        click("schedule-WEEKDAYS")
        val monday = compose.onNodeWithTag("weekday-MONDAY")
        monday.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        monday.performKeyInput { pressKey(Key.Enter) }; monday.assertIsOn()
    }

    @Test fun narrowSystem200PercentFormRemainsOperableWithImeAndErrors() {
        shell("wm size 640x1600")
        shell("settings put system font_scale 2.0")
        compose.waitUntil(15_000) { context.resources.configuration.fontScale >= 1.99f }
        scenario!!.recreate(); bind(); waitFor("page-TODAY")
        open(); save()
        compose.onNodeWithTag("form-error").performScrollTo().assertIsDisplayed()
        fill(); click("schedule-WEEKLY"); click("weekly-days-3")
        compose.onNodeWithTag("weekly-explanation").performScrollTo().assertIsDisplayed()
        // Real IME stays open as the scrollable form brings submission into view.
        compose.onNodeWithTag("repo-target").performScrollTo().performClick().performTextReplacement("13")
        compose.onNodeWithTag("save-repo").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { !vm.busy && vm.overlay == null }
        assertEquals(13.0, vm.workspace.habits.single().versionOn(vm.today)!!.target, 0.0)
        assertEquals(HabitSchedule.WeeklyQuota(3), vm.workspace.habits.single().versionOn(vm.today)!!.schedule)
    }
}
