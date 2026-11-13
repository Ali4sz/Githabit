package com.githabit.prototype

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.FakeAccessForViewModel
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var count = 0
    private val date = LocalDate.parse("2026-11-01")
    private val workspace = Workspace(onboarded = true).createHabit(HabitDraft("Practice", "", 10.0, "minutes"), date, "a")
    private fun TestScope.model(access: FakeAccessForViewModel, saved: SavedStateHandle = SavedStateHandle()) =
        GitHabitViewModel(access, ioDispatcher = dispatcher, tickerScope = backgroundScope, savedStateHandle = saved)
            .also { models.put("${count++}", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun openingDefaultsAreBlankAndScheduleChoicesRetainIdentity() {
        val blank = RepoFormValues.opening(null, date)
        assertEquals(listOf("", "", "", ""), blank.encode().take(4))
        assertEquals(HabitSchedule.Daily, blank.schedule())
        val selected = blank.copy(choice = ScheduleChoice.WEEKDAYS, weekdayMask = 65)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), (selected.schedule() as HabitSchedule.SelectedWeekdays).days)
        for (n in 1..7) assertEquals(HabitSchedule.WeeklyQuota(n), blank.copy(choice = ScheduleChoice.WEEKLY, weeklyDays = "$n").schedule())
        for (n in listOf("0", "8", "1.5", "")) assertThrows(IllegalArgumentException::class.java) {
            blank.copy(choice = ScheduleChoice.WEEKLY, weeklyDays = n).schedule()
        }
        assertThrows(IllegalArgumentException::class.java) { blank.copy(choice = ScheduleChoice.WEEKDAYS).schedule() }
    }

    @Test fun meaningfulComparisonIncludesInvalidAndClearedInputsButNotRevertedHiddenSubforms() {
        val baseline = RepoFormValues("Practice", target = "10", unit = "minutes")
        assertTrue(baseline.copy(target = "").meaningfulComparedWith(baseline))
        assertTrue(baseline.copy(target = "invalid").meaningfulComparedWith(baseline))
        assertTrue(baseline.copy(icon = HabitIcon.CODE).meaningfulComparedWith(baseline))
        assertTrue(baseline.copy(color = HabitColor.BLUE).meaningfulComparedWith(baseline))
        assertFalse(baseline.copy(weekdayMask = 65, weeklyDays = "3").meaningfulComparedWith(baseline))
        assertFalse(baseline.copy(name = "Changed").copy(name = baseline.name).meaningfulComparedWith(baseline))
    }

    @Test fun encodedDraftsRetainOpeningBaselineDateAndConfiguration() {
        val values = RepoFormValues("Practice", target = "10", unit = "minutes", choice = ScheduleChoice.WEEKLY, weeklyDays = "3")
        val repo = FormState.Repo("a", date, values, values.copy(target = ""), GoalVersion(date, 10.0,
            RepoStatus.PAUSED, HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.SUNDAY))))
        assertEquals(repo, FormState.decode(repo.encode()))
        val log = FormState.Log("a", "c", date, LogFormValues("2", "Old"), LogFormValues("", "New"))
        assertEquals(log, FormState.decode(log.encode()))
        assertNull(FormState.decode(listOf("repo")))
        assertNull(FormState.decode(repo.encode() + "extra"))
        assertNull(FormState.decode(log.encode().toMutableList().also { it[7] = "x".repeat(4097) }))
    }

    @Test fun transitionPreviewUsesEffectiveHistoryNotTouchedFields() {
        val monday = LocalDate.parse("2026-11-02")
        val thursday = monday.plusDays(3)
        val weekly = HabitDraft("Practice", "", 10.0, "minutes", schedule = HabitSchedule.WeeklyQuota(3))
        val initial = Workspace().createHabit(weekly, monday, "a")
        assertFalse(initial.proposedWeeklyTransition("a", weekly.copy(name = "Cosmetic"), thursday))
        assertFalse(initial.proposedWeeklyTransition("a", weekly, thursday))
        assertTrue(initial.proposedWeeklyTransition("a", weekly.copy(target = 20.0), thursday))
        assertFalse(initial.proposedWeeklyTransition("a", weekly.copy(target = 20.0), monday))
        assertTrue(Workspace().proposedWeeklyTransition(null, weekly, thursday))
        assertFalse(Workspace().proposedWeeklyTransition(null, weekly, monday))
        val changed = initial.editHabit("a", weekly.copy(target = 20.0), thursday)
        assertFalse(changed.proposedWeeklyTransition("a", weekly, thursday))
        assertTrue(initial.proposedWeeklyTransition("a", weekly.copy(schedule = HabitSchedule.Daily), thursday))
    }

    @Test fun backCloseAndNavigationUseSameConfirmationAndKeepHistoryUnchanged() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("a")
        vm.editRepoForm { it.copy(target = "") }
        vm.back()
        assertTrue(vm.discardConfirmation)
        assertEquals(Overlay.RepoForm("a"), vm.overlay)
        vm.cancelDiscard()
        vm.requestDismissForm()
        assertTrue(vm.discardConfirmation)
        vm.cancelDiscard()
        vm.navigate(Destination.SETTINGS)
        assertEquals(Destination.TODAY, vm.destination)
        vm.confirmDiscard()
        assertNull(vm.overlay)
        assertNull(vm.formState)
        assertEquals(Destination.SETTINGS, vm.destination)
        assertEquals(workspace, vm.workspace)
        assertEquals(0, access.writes)
    }

    @Test fun freshSavedStateRestoresDraftAndConfirmationWithoutPersistingRecords() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.RepoForm()
        first.editRepoForm { it.copy(name = "Unsubmitted", choice = ScheduleChoice.WEEKLY, weeklyDays = "3") }
        first.back()
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(first.formState, restored.formState)
        assertTrue(restored.discardConfirmation)
        restored.cancelDiscard()
        assertEquals("Unsubmitted", (restored.formState as FormState.Repo).values.name)
        assertEquals(workspace, restored.workspace)
        assertEquals(0, access.writes)
    }

    @Test fun revertedEditAndUntouchedCreationDismissWithoutConfirmation() = runTest(dispatcher) {
        val vm = model(FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray()))
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm()
        vm.back()
        assertNull(vm.overlay)
        vm.overlay = Overlay.RepoForm("a")
        val baseline = (vm.formState as FormState.Repo).baseline
        vm.editRepoForm { it.copy(name = "Different") }
        vm.editRepoForm { baseline }
        vm.back()
        assertNull(vm.overlay)
        assertFalse(vm.discardConfirmation)
    }

    @Test fun failedSaveKeepsDraftAndVerifiedReplacementClearsIt() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.LogForm("a")
        vm.editLogForm { it.copy(amount = "2", note = "Keep") }
        val draft = vm.formState
        access.writeActions += { throw java.io.IOException("test-only failure") }
        vm.saveCommit("a", null, 2.0, "Keep")
        advanceUntilIdle()
        val retained = vm.formState as FormState.Log
        assertEquals((draft as FormState.Log).values, retained.values)
        assertTrue(retained.submitted)
        vm.reset()
        advanceUntilIdle()
        assertNull(vm.formState)
        assertNull(vm.overlay)
    }

    @Test fun staleReviewedConfigurationCannotSilentlyOverwriteDatedChanges() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("a")
        val opening = vm.formState as FormState.Repo
        vm.editRepoForm { it.copy(target = "20") }
        vm.status("a", RepoStatus.PAUSED)
        advanceUntilIdle()
        var reviewRequested = false
        vm.saveHabit("a", (vm.formState as FormState.Repo).values.draft(), vm.today,
            reviewedConfiguration = opening.reviewedConfiguration,
            onConfigurationChanged = { reviewRequested = true; vm.reviewFormDate(vm.today) })
        advanceUntilIdle()
        assertTrue(reviewRequested)
        assertTrue(vm.formSaveFailed)
        assertEquals(10.0, vm.workspace.habits.single().versionOn(vm.today)!!.target, 0.0)
        val reviewed = vm.formState as FormState.Repo
        vm.saveHabit("a", reviewed.values.draft(), vm.today, reviewedConfiguration = reviewed.reviewedConfiguration)
        advanceUntilIdle()
        assertNull(vm.overlay)
        assertEquals(20.0, vm.workspace.habits.single().versionOn(vm.today)!!.target, 0.0)
        assertEquals(RepoStatus.PAUSED, vm.workspace.habits.single().versionOn(vm.today)!!.status)
    }

    @Test fun restorationOfPreCompletionSnapshotCannotReplayNewRepoOrLog() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.RepoForm()
        first.editRepoForm { it.copy(name = "New", target = "2", unit = "minutes") }
        first.saveHabit(null, (first.formState as FormState.Repo).values.draft())
        val beforeCommit = saved.keys().associateWith { saved.get<Any?>(it) }
        advanceUntilIdle()
        val restored = model(access, SavedStateHandle(beforeCommit))
        advanceUntilIdle()
        assertNull(restored.overlay)
        assertEquals(2, restored.workspace.habits.size)
        first.overlay = Overlay.LogForm("a")
        first.editLogForm { it.copy(amount = "2") }
        first.saveCommit("a", null, 2.0, "")
        val logBeforeCommit = saved.keys().associateWith { saved.get<Any?>(it) }
        advanceUntilIdle()
        val logRestored = model(access, SavedStateHandle(logBeforeCommit))
        advanceUntilIdle()
        assertNull(logRestored.overlay)
        assertEquals(1, logRestored.workspace.commits.size)
    }

    @Test fun largeUntouchedLegacyStringsAreReferencedNotCopiedIntoSavedState() = runTest(dispatcher) {
        val legacy = workspace.copy(habits = workspace.habits.map { it.copy(name = " ".repeat(20_000) + it.name,
            unit = it.unit + " ".repeat(20_000), description = " ".repeat(20_000)) })
        val access = FakeAccessForViewModel(WorkspaceJson.encode(legacy).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.RepoForm("a")
        first.editRepoForm { it.copy(target = "20") }
        val raw = saved.get<List<String>>(FORM_STATE_KEY)!!
        assertTrue(raw.sumOf { it.length } < 2000)
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(first.formState, restored.formState)
        val form = restored.formState as FormState.Repo
        restored.saveHabit("a", form.values.draft(), restored.today, reviewedConfiguration = form.reviewedConfiguration)
        advanceUntilIdle()
        assertEquals(legacy.habits.single().name, restored.workspace.habits.single().name)
        assertEquals(legacy.habits.single().unit, restored.workspace.habits.single().unit)
        assertEquals(legacy.habits.single().description, restored.workspace.habits.single().description)
    }

    @Test fun unrelatedIndeterminateWriteReconcilesWithoutDiscardingDirtyForm() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("a")
        vm.editRepoForm { it.copy(target = "20") }
        val draft = vm.formState
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true; access.failLastGoodReads = true }
        access.writeActions += { throw java.io.IOException("rollback unavailable") }
        vm.setTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        access.failReads = false; access.failLastGoodReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Ready)
        assertEquals(ThemePreference.DARK, vm.workspace.theme)
        assertEquals(draft, vm.formState)
        assertEquals(Overlay.RepoForm("a"), vm.overlay)
        assertEquals(0, vm.workspaceSession)
    }

    @Test fun literalReferenceLookingInputRestoresExactly() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.LogForm("a")
        first.editLogForm { it.copy(note = "\u0000reference:7:literal") }
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(first.formState, restored.formState)
    }

    @Test fun editingRejectedNoOpSubmissionInvalidatesOldCandidateBeforeRestoration() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.RepoForm("a")
        val opening = first.formState as FormState.Repo
        first.saveHabit("a", opening.values.draft(), first.today.minusDays(1)) { first.reviewFormDate(it) }
        advanceUntilIdle()
        first.editRepoForm { it.copy(target = "20") }
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(Overlay.RepoForm("a"), restored.overlay)
        assertEquals("20", (restored.formState as FormState.Repo).values.target)
        assertFalse((restored.formState as FormState.Repo).submitted)
    }

    @Test fun preCompletionEditAndCorrectionSnapshotsReconcileExactVerifiedCandidate() = runTest(dispatcher) {
        val initial = workspace.addCommit("a", 2.0, date, "Old", "c")
        val access = FakeAccessForViewModel(WorkspaceJson.encode(initial).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.overlay = Overlay.RepoForm("a")
        first.editRepoForm { it.copy(name = "Renamed") }
        first.saveHabit("a", (first.formState as FormState.Repo).values.draft())
        val editSnapshot = saved.keys().associateWith { saved.get<Any?>(it) }
        advanceUntilIdle()
        val editRestored = model(access, SavedStateHandle(editSnapshot))
        advanceUntilIdle()
        assertNull(editRestored.overlay)
        first.overlay = Overlay.LogForm("a", "c")
        first.editLogForm { it.copy(amount = "4", note = "Corrected") }
        first.saveCommit("a", "c", 4.0, "Corrected")
        val correctionSnapshot = saved.keys().associateWith { saved.get<Any?>(it) }
        advanceUntilIdle()
        val correctionRestored = model(access, SavedStateHandle(correctionSnapshot))
        advanceUntilIdle()
        assertNull(correctionRestored.overlay)
        assertEquals(4.0, correctionRestored.workspace.commits.single().amount, 0.0)
    }

    @Test fun recoveredCommittedLegacyRenameDoesNotLeaveUnencodableBaseline() = runTest(dispatcher) {
        val legacy = workspace.copy(habits = workspace.habits.map { it.copy(name = " ".repeat(20_000) + it.name) })
        val access = FakeAccessForViewModel(WorkspaceJson.encode(legacy).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("a")
        vm.editRepoForm { it.copy(name = "Short") }
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true; access.failLastGoodReads = true }
        access.writeActions += { throw java.io.IOException("rollback unavailable") }
        vm.saveHabit("a", (vm.formState as FormState.Repo).values.draft())
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        access.failReads = false; access.failLastGoodReads = false
        vm.retryLoad(); advanceUntilIdle()
        assertNull(vm.overlay)
        assertNull(vm.formState)
        assertEquals("Short", vm.workspace.habits.single().name)
    }
}
