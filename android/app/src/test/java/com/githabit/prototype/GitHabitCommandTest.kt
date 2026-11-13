package com.githabit.prototype

import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.FakeAccessForViewModel
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHabitCommandTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelId = 0
    private val clock = object : AppClock {
        var instant = Instant.parse("2026-11-04T12:00:00Z")
        var zoneId = ZoneId.of("UTC")
        override fun now() = instant
        override fun zone() = zoneId
        override fun today(): LocalDate = error("Commands must derive their date from one captured instant and zone")
    }
    private val day get() = clock.instant.atZone(clock.zoneId).toLocalDate()
    private val draft = HabitDraft("Practice", "", 10.0, "minutes", schedule = HabitSchedule.WeeklyQuota(2))
    private fun workspace() = Workspace().createHabit(draft, LocalDate.parse("2026-11-02"), "habit")
    private fun access(workspace: Workspace = workspace()) = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
    private fun TestScope.model(access: FakeAccessForViewModel): GitHabitViewModel =
        GitHabitViewModel(access, clock, dispatcher, backgroundScope).also { models.put("${modelId++}", it) }
    private fun TestScope.notices(vm: GitHabitViewModel) = mutableListOf<Notice>().also { events ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.collect { events += it } }
    }
    private fun move(date: String) { clock.instant = LocalDate.parse(date).atTime(12, 0).atZone(clock.zoneId).toInstant() }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun action(vm: GitHabitViewModel) = TodayAction(vm.workspaceSession, vm.today,
        vm.workspace.habits.single(), vm.analytics.todayGroups().flatMap { it.habits }.single())

    @Test fun todayActionsRejectMidnightAndTimezoneDateChangesBeforeExecution() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val events = notices(vm)
        advanceUntilIdle()
        vm.completeToday(action(vm))
        move("2026-11-05")
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertEquals(day, vm.today)
        assertTrue(events.single().message.contains("tap again"))
        vm.openTodayLog(action(vm))
        clock.zoneId = ZoneId.of("Pacific/Kiritimati")
        advanceUntilIdle()
        assertNull(vm.overlay)
        assertEquals(0, access.writes)
        vm.openTodayLog(action(vm))
        advanceUntilIdle()
        assertEquals(vm.today, (vm.formState as FormState.Log).intendedDate)
        assertEquals("", (vm.formState as FormState.Log).values.amount)
    }

    @Test fun todayActionsRejectChangedProgressConfigurationAndReplacementSession() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        advanceUntilIdle()
        val rendered = action(vm)
        vm.saveCommit("habit", null, 4.0, "")
        advanceUntilIdle()
        vm.completeToday(rendered)
        advanceUntilIdle()
        assertEquals(1, access.writes)
        val current = action(vm)
        vm.saveHabit("habit", draft.copy(target = 20.0))
        advanceUntilIdle()
        vm.openTodayLog(current)
        advanceUntilIdle()
        assertNull(vm.overlay)
        val beforeReplacement = action(vm)
        vm.replaceWorkspace(vm.workspace)
        advanceUntilIdle()
        val writes = access.writes
        vm.completeToday(beforeReplacement)
        advanceUntilIdle()
        assertEquals(writes, access.writes)
    }

    @Test fun todayCompleteUsesRemainderVerifiedFeedbackUndoAndSharedIndex() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 4.0, day, id = "partial"))
        val vm = model(access)
        val events = notices(vm)
        advanceUntilIdle()
        val index = vm.analytics
        assertSame(index, vm.analytics)
        vm.setTheme(ThemePreference.LIGHT)
        advanceUntilIdle()
        assertSame(index, vm.analytics)
        vm.completeToday(action(vm)); vm.completeToday(action(vm))
        advanceUntilIdle()
        assertNotSame(index, vm.analytics)
        assertEquals(6.0, vm.workspace.commits.last().amount, 0.0)
        assertEquals(1, vm.analytics.weekly("habit", day).quota!!.qualifyingDays)
        assertTrue(events.single().message.contains("remaining target saved"))
        vm.undo(events.single().undoId!!)
        advanceUntilIdle()
        assertEquals(4.0, vm.analytics.daily("habit", day).progress.total, 0.0)
        assertEquals(0, vm.analytics.weekly("habit", day).quota!!.qualifyingDays)
    }

    @Test fun todayCompleteRollbackNeverPublishesCompletionOrUndo() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val events = notices(vm)
        advanceUntilIdle()
        access.writeActions += { throw IOException("injected") }
        vm.completeToday(action(vm))
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
        assertTrue(events.none { it.undoId != null || it.message.contains("target saved") })
        vm.completeToday(action(vm))
        advanceUntilIdle()
        assertEquals(1, vm.workspace.commits.size)
    }

    @Test fun preparedTodayLogFeedbackAndCacheRemainDateConsistentAcrossMidnight() = runTest(dispatcher) {
        val access = access(workspace().copy(habits = workspace().habits.map { habit ->
            habit.copy(versions = habit.versions.map { it.copy(schedule = HabitSchedule.Daily) })
        }))
        val vm = model(access)
        val events = notices(vm)
        advanceUntilIdle()
        val intended = vm.today
        access.writeActions += { bytes -> access.stored = bytes; move("2026-11-05"); vm.refreshDate() }
        vm.saveCommit("habit", null, 10.0, "late", intended)
        advanceUntilIdle()
        assertEquals(intended, vm.workspace.commits.single().date)
        assertEquals(day, vm.analytics.today)
        assertEquals(0.0, vm.analytics.daily("habit", day).progress.total, 0.0)
        assertEquals(TodayGroupKind.SCHEDULED, vm.analytics.todayGroups().single { it.habits.isNotEmpty() }.kind)
        assertTrue(events.single().message.contains("Today’s target reached; moved out of Remaining."))
        assertTrue(events.single().message.contains("Date: $intended"))
        vm.undo(events.single().undoId!!)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
    }

    @Test fun midnightDuringSaveUsesCapturedDateInstantAndNotice() = runTest(dispatcher) {
        clock.instant = Instant.parse("2026-11-04T23:59:59Z")
        val captured = clock.capture()
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.writeActions += { bytes -> access.stored = bytes; move("2026-11-05"); vm.refreshDate() }
        vm.saveCommit("habit", null, 4.0, "late")
        advanceUntilIdle()
        val commit = vm.workspace.commits.single()
        assertEquals(captured.date, commit.date)
        assertEquals(captured.instant, commit.timestamp)
        assertEquals(LocalDate.parse("2026-11-05"), vm.today)
        assertTrue(notices.single().message.contains(captured.date.toString()))
        vm.undo(requireNotNull(notices.single().undoId))
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
    }

    @Test fun staleFormBetweenAdmissionAndExecutionRequiresResubmission() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.overlay = Overlay.LogForm("habit")
        var revised: LocalDate? = null
        vm.saveCommit("habit", null, 4.0, "draft", vm.today) { revised = it }
        move("2026-11-05")
        advanceUntilIdle()
        assertEquals(day, revised)
        assertEquals(0, access.writes)
        assertNotNull(vm.overlay)
        assertTrue(notices.single().message.contains("submit again"))
        vm.saveCommit("habit", null, 4.0, "draft", requireNotNull(revised))
        advanceUntilIdle()
        assertEquals(day, vm.workspace.commits.single().date)
        assertEquals("draft", vm.workspace.commits.single().note)
        assertNull(vm.overlay)
    }

    @Test fun staleRepoFormAndMissingCorrectionNeverCreateReplacementRecords() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.saveHabit("habit", draft.copy(target = 20.0))
        move("2026-11-05")
        advanceUntilIdle()
        assertEquals(0, access.writes)
        vm.saveHabit("missing", draft)
        advanceUntilIdle()
        vm.saveCommit("habit", "deleted-entry", 1.0, "correction")
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertEquals(1, vm.workspace.habits.size)
        assertTrue(vm.workspace.commits.isEmpty())
        assertTrue(notices.last().message.contains("could not be found"))
    }

    @Test fun completionCapturesTimestampAndRepeatedTapsOnlyAddRemainder() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 4.0, day, id = "partial", timestamp = clock.instant))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.complete("habit"); vm.complete("habit")
        advanceUntilIdle()
        vm.complete("habit")
        advanceUntilIdle()
        assertEquals(1, access.writes)
        assertEquals(6.0, vm.workspace.commits.last().amount, 0.0)
        assertEquals(clock.instant, vm.workspace.commits.last().timestamp)
        assertEquals(1, notices.size)
    }

    @Test fun calendarImmediateOnlyWithoutHabitsOrHistory() = runTest(dispatcher) {
        val empty = model(access(Workspace()))
        val withHabits = model(access())
        val history = model(access(Workspace(calendar = WeekCalendar(DayOfWeek.MONDAY,
            listOf(WeekTransition(LocalDate.parse("2026-10-26"), DayOfWeek.SUNDAY))))))
        advanceUntilIdle()
        empty.setWeekStart(DayOfWeek.SUNDAY)
        withHabits.setWeekStart(DayOfWeek.SUNDAY)
        history.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(DayOfWeek.SUNDAY), empty.workspace.calendar)
        assertTrue(empty.workspace.habits.isEmpty() && empty.workspace.commits.isEmpty())
        assertEquals(LocalDate.parse("2026-11-09"), withHabits.workspace.calendar.pending?.boundary)
        assertNotNull(history.workspace.calendar.pending)
    }

    @Test fun allWeekStartPairsPersistNoopOrOriginalBoundary() = runTest(dispatcher) {
        for (old in DayOfWeek.entries) for (new in DayOfWeek.entries) {
            val original = workspace().copy(calendar = WeekCalendar(old))
            val access = access(original)
            val vm = model(access)
            val notices = notices(vm)
            advanceUntilIdle()
            val period = vm.workspace.calendar.periodOn(day)
            vm.setWeekStart(new)
            advanceUntilIdle()
            assertEquals(period, vm.workspace.calendar.periodOn(day))
            assertEquals(if (new == old) 0 else 1, access.writes)
            assertTrue(notices.all { it.undoId == null })
            assertEquals(if (new == old) null else WeekTransition(period.endExclusive, new), vm.workspace.calendar.pending)
            assertEquals(vm.workspace, WorkspaceJson.decode(requireNotNull(access.stored).toString(Charsets.UTF_8)))
        }
    }

    @Test fun pendingReplacementCancellationAndOrdinaryCommandsKeepCurrentWeek() = runTest(dispatcher) {
        val vm = model(access())
        advanceUntilIdle()
        val period = vm.workspace.calendar.periodOn(day)
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        vm.saveCommit("habit", null, 1.0, "pending does not block")
        advanceUntilIdle()
        vm.saveHabit("habit", draft.copy(target = 20.0))
        advanceUntilIdle()
        vm.setWeekStart(DayOfWeek.TUESDAY)
        advanceUntilIdle()
        assertEquals(WeekTransition(period.endExclusive, DayOfWeek.TUESDAY), vm.workspace.calendar.pending)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertNull(vm.workspace.calendar.pending)
        assertEquals(period, vm.workspace.calendar.periodOn(day))
        assertEquals(1, vm.workspace.commits.size)
        assertEquals(20.0, vm.workspace.habits.single().versionOn(day)!!.target, 0.0)
    }

    @Test fun restartAcrossBoundaryRetainsOriginalBridgeAndAllowsOnlyQueuedChanges() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        move("2026-11-10")
        val vm = model(access)
        advanceUntilIdle()
        val bridge = vm.workspace.calendar.periodOn(day)
        assertEquals(LocalDate.parse("2026-11-09"), bridge.start)
        assertEquals(LocalDate.parse("2026-11-15"), bridge.endExclusive)
        assertEquals(WeekPeriodKind.BRIDGE, bridge.kind)
        assertNull(vm.workspace.calendar.pending)
        assertEquals(1, vm.workspace.calendar.transitions.size)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekTransition(bridge.endExclusive, DayOfWeek.MONDAY), vm.workspace.calendar.pending)
        assertEquals(LocalDate.parse("2026-11-16"), vm.workspace.calendar.status(day).firstFullWeekStart)
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertNull(vm.workspace.calendar.pending)
        assertEquals(bridge, vm.workspace.calendar.periodOn(day))
        assertEquals(vm.workspace, model(access).let { advanceUntilIdle(); it.workspace })
    }

    @Test fun observedActivationCannotBeCancelledAfterClockRollbackEvenIfWriteFailed() = runTest(dispatcher) {
        for (fail in listOf(false, true)) {
            move("2026-11-04")
            val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
            val vm = model(access)
            val notices = notices(vm)
            advanceUntilIdle()
            access.failAllWrites = fail
            move("2026-11-09")
            vm.refreshDate()
            advanceUntilIdle()
            access.failAllWrites = false
            move("2026-11-04")
            vm.refreshDate()
            vm.setWeekStart(DayOfWeek.MONDAY)
            advanceUntilIdle()
            assertTrue(notices.last().message.contains("newer effective change"))
            assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY)), vm.workspace.calendar.transitions)
        }
    }

    @Test fun failedCalendarSaveKeepsPriorValuesAndStaleBoundaryRequiresReview() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.failAllWrites = true
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
        assertTrue(notices.single().message.contains("previous file was verified"))
        access.failAllWrites = false
        vm.setWeekStart(DayOfWeek.SUNDAY)
        move("2026-11-09")
        advanceUntilIdle()
        assertNull(vm.workspace.calendar.pending)
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-11-16"), vm.workspace.calendar.pending?.boundary)
    }

    @Test fun leapDayDstTimezoneTravelAndYearBoundaryUseCapturedLocalDate() = runTest(dispatcher) {
        val instants = listOf(
            "2024-02-29T23:59:00Z" to "UTC",
            "2024-03-10T06:59:00Z" to "America/New_York",
            "2024-03-10T07:01:00Z" to "America/New_York",
            "2024-11-03T05:30:00Z" to "America/New_York",
            "2024-11-03T06:30:00Z" to "America/New_York",
            "2026-12-31T23:30:00Z" to "Pacific/Kiritimati",
            "2026-12-31T23:30:00Z" to "America/Los_Angeles",
        )
        val vm = model(access(Workspace().createHabit(draft, LocalDate.parse("2024-01-01"), "habit")))
        advanceUntilIdle()
        for ((instant, zone) in instants) {
            clock.instant = Instant.parse(instant); clock.zoneId = ZoneId.of(zone)
            vm.refreshDate()
            val previous = vm.workspace.commits
            vm.saveCommit("habit", null, 1.0, zone)
            advanceUntilIdle()
            assertEquals(previous, vm.workspace.commits.dropLast(1))
            assertEquals(day, vm.workspace.commits.last().date)
            assertEquals(clock.instant, vm.workspace.commits.last().timestamp)
            assertEquals(clock.zoneId, vm.zone)
        }
    }

    @Test fun addedCommitUndoRejectsLaterCorrectionButWorksAfterCorrectionUndo() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.saveCommit("habit", null, 4.0, "original")
        advanceUntilIdle()
        val addUndo = requireNotNull(notices.last().undoId)
        val id = vm.workspace.commits.single().id
        vm.saveCommit("habit", id, 5.0, "corrected")
        advanceUntilIdle()
        val correctionUndo = requireNotNull(notices.last().undoId)
        val writes = access.writes
        vm.undo(addUndo)
        advanceUntilIdle()
        assertEquals(writes, access.writes)
        assertEquals(5.0, vm.workspace.commits.single().amount, 0.0)
        assertTrue(notices.last().message.contains("Cannot undo"))
        assertNull(notices.last().undoId)
        vm.undo(correctionUndo)
        advanceUntilIdle()
        assertEquals("original", vm.workspace.commits.single().note)
        vm.undo(addUndo)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
    }

    @Test fun oldCorrectionUndoCannotOverwriteNewerCorrectionOrDeletion() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 2.0, day, id = "entry", timestamp = clock.instant))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.saveCommit("habit", "entry", 3.0, "first")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        vm.saveCommit("habit", "entry", 4.0, "second")
        advanceUntilIdle()
        vm.undo(token)
        advanceUntilIdle()
        assertEquals("second", vm.workspace.commits.single().note)
        vm.removeCommit("entry")
        advanceUntilIdle()
        val writes = access.writes
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(writes, access.writes)
        assertTrue(vm.workspace.commits.isEmpty())
        assertTrue(notices.last().message.contains("Cannot undo"))
    }

    @Test fun deletedLastCommitCannotBeRestoredUnderChangedUnit() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 2.0, day, id = "entry", timestamp = clock.instant))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("entry")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        vm.saveHabit("habit", draft.copy(unit = "pages"))
        advanceUntilIdle()
        vm.undo(token)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
        assertTrue(notices.last().message.contains("unit has changed"))
        assertNull(notices.last().undoId)
    }

    @Test fun statusUndoPreservesSameDayTargetScheduleAndCosmeticEdits() = runTest(dispatcher) {
        val vm = model(access())
        val notices = notices(vm)
        advanceUntilIdle()
        vm.status("habit", RepoStatus.PAUSED)
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        vm.saveHabit("habit", draft.copy(name = "Renamed", target = 20.0,
            schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))))
        advanceUntilIdle()
        vm.undo(token)
        advanceUntilIdle()
        val habit = vm.workspace.habits.single()
        val current = habit.versionOn(day)!!
        assertEquals("Renamed", habit.name)
        assertEquals(20.0, current.target, 0.0)
        assertEquals(HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY)), current.schedule)
        assertEquals(RepoStatus.ACTIVE, current.status)
    }

    @Test fun statusUndoPreservesLaterDatedVersionsWithoutLeavingFalseTransition() = runTest(dispatcher) {
        val vm = model(access())
        val notices = notices(vm)
        advanceUntilIdle()
        val original = vm.workspace
        vm.status("habit", RepoStatus.ARCHIVED)
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        move("2026-11-05"); vm.refreshDate()
        vm.saveHabit("habit", draft.copy(target = 20.0))
        advanceUntilIdle()
        val later = vm.workspace.habits.single().versions.last()
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(original.habits.single().versions + later, vm.workspace.habits.single().versions)
        assertEquals(RepoStatus.ARCHIVED, later.status)
    }

    @Test fun statusUndoSameDayRoundTripRestoresStableWeekAndRejectsLaterStatus() = runTest(dispatcher) {
        val vm = model(access())
        val notices = notices(vm)
        advanceUntilIdle()
        val original = vm.workspace
        vm.status("habit", RepoStatus.PAUSED)
        advanceUntilIdle()
        val pause = requireNotNull(notices.last().undoId)
        vm.status("habit", RepoStatus.ARCHIVED)
        advanceUntilIdle()
        val archive = requireNotNull(notices.last().undoId)
        vm.undo(pause)
        advanceUntilIdle()
        assertEquals(RepoStatus.ARCHIVED, vm.workspace.habits.single().versionOn(day)!!.status)
        vm.undo(archive)
        advanceUntilIdle()
        vm.undo(pause)
        advanceUntilIdle()
        assertEquals(original, vm.workspace)
        assertEquals(WeekAssessment.PENDING, ProgressAnalytics(vm.workspace, day).weekly("habit", day).assessment)
    }

    @Test fun statusUndoUsesOriginalDateWhenSaveCrossesMidnight() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        val original = vm.workspace
        access.writeActions += { bytes -> access.stored = bytes; move("2026-11-05"); vm.refreshDate() }
        vm.status("habit", RepoStatus.PAUSED)
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-11-04"), vm.workspace.habits.single().versions.last().date)
        vm.undo(requireNotNull(notices.last().undoId))
        advanceUntilIdle()
        assertEquals(original, vm.workspace)
    }

    @Test fun weeklyCorrectionDeletionAndUndoRecomputeClosedWeekWithoutChangingExpectations() = runTest(dispatcher) {
        val monday = LocalDate.parse("2026-11-02")
        val original = workspace().addCommit("habit", 10.0, monday, id = "monday", timestamp = clock.instant)
            .addCommit("habit", 10.0, monday.plusDays(1), id = "tuesday", timestamp = clock.instant)
        move("2026-11-09")
        val vm = model(access(original))
        val notices = notices(vm)
        advanceUntilIdle()
        fun assessment() = ProgressAnalytics(vm.workspace, day).weekly("habit", monday).assessment
        assertEquals(WeekAssessment.ACHIEVED, assessment())
        vm.saveCommit("habit", "monday", 9.0, "below target")
        advanceUntilIdle()
        assertEquals(WeekAssessment.MISSED, assessment())
        vm.undo(requireNotNull(notices.last().undoId))
        advanceUntilIdle()
        assertEquals(WeekAssessment.ACHIEVED, assessment())
        vm.removeCommit("tuesday")
        advanceUntilIdle()
        assertEquals(WeekAssessment.MISSED, assessment())
        vm.undo(requireNotNull(notices.last().undoId))
        advanceUntilIdle()
        assertEquals(WeekAssessment.ACHIEVED, assessment())
        assertEquals(original.habits, vm.workspace.habits)
        assertEquals(original.commits, vm.workspace.commits)
    }

    @Test fun indeterminateUndoBlocksBlindReplayAndReconcilesBothPossibleOutcomes() = runTest(dispatcher) {
        for (candidateCommitted in listOf(false, true)) {
            val original = workspace().addCommit("habit", 1.0, day, id = "entry", timestamp = clock.instant)
            val access = access(original)
            val vm = model(access)
            val notices = notices(vm)
            advanceUntilIdle()
            vm.removeCommit("entry")
            advanceUntilIdle()
            val token = requireNotNull(notices.last().undoId)
            access.writeActions += { bytes -> if (candidateCommitted) access.stored = bytes; access.failReads = true }
            access.writeActions += { throw IOException("rollback failed") }
            vm.undo(token)
            advanceUntilIdle()
            assertTrue(vm.load is WorkspaceLoad.Recovery)
            val writes = access.writes
            vm.undo(token)
            advanceUntilIdle()
            assertEquals(writes, access.writes)
            assertTrue(notices.last().message.contains("paused"))
            access.failReads = false
            vm.retryLoad()
            advanceUntilIdle()
            if (!candidateCommitted) {
                assertEquals(token, notices.last().undoId)
                assertTrue(notices.last().message.contains("Undo did not commit"))
            }
            vm.undo(if (candidateCommitted) token else requireNotNull(notices.last().undoId))
            advanceUntilIdle()
            assertEquals(original, vm.workspace)
            assertEquals(writes + if (candidateCommitted) 0 else 1, access.writes)
            if (candidateCommitted) assertTrue(notices.last().message.contains("expired"))
        }
    }

    @Test fun replacementEvenWithSameIdsOrEqualValuesInvalidatesUndoOnlyAfterVerification() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 1.0, day, id = "entry", timestamp = clock.instant))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("entry")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        val candidate = vm.workspace
        val session = vm.workspaceSession
        vm.overlay = Overlay.RepoForm("habit")
        access.failAllWrites = true
        vm.replaceWorkspace(candidate.copy(name = "Restored"))
        advanceUntilIdle()
        assertEquals(session, vm.workspaceSession)
        assertNotNull(vm.overlay)
        access.failAllWrites = false
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(1, vm.workspace.commits.size)
        vm.removeCommit("entry")
        advanceUntilIdle()
        val stale = requireNotNull(notices.last().undoId)
        vm.replaceWorkspace(candidate)
        advanceUntilIdle()
        assertEquals(session + 1, vm.workspaceSession)
        assertNull(vm.overlay)
        vm.undo(stale)
        advanceUntilIdle()
        assertEquals(candidate, vm.workspace)
        assertTrue(notices.last().message.contains("expired"))
    }

    @Test fun indeterminateEqualReplacementInvalidatesUndoOnVerifiedReconciliation() = runTest(dispatcher) {
        val access = access(workspace().addCommit("habit", 1.0, day, id = "entry", timestamp = clock.instant))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("entry")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        val session = vm.workspaceSession
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failed") }
        vm.replaceWorkspace(vm.workspace)
        advanceUntilIdle()
        assertEquals(session, vm.workspaceSession)
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        // A second failed replacement must not forget the earlier equal-value barrier.
        vm.startOverFromRecovery()
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertEquals(session + 1, vm.workspaceSession)
        vm.undo(token)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
        assertTrue(notices.last().message.contains("expired"))
    }

    @Test fun undoBoundExplainsExpiredTokensAndNoopRemovalCreatesNothing() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("missing")
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertTrue(notices.isEmpty())
        repeat(21) {
            vm.saveCommit("habit", null, 1.0, "$it")
            advanceUntilIdle()
        }
        val expired = requireNotNull(notices.first().undoId)
        val retained = requireNotNull(notices[1].undoId)
        vm.undo(expired)
        advanceUntilIdle()
        assertEquals(21, vm.workspace.commits.size)
        assertTrue(notices.last().message.contains("expired"))
        vm.undo(retained)
        advanceUntilIdle()
        assertEquals(20, vm.workspace.commits.size)
    }

    @Test fun foregroundTickerWakesAtMidnightAndStopsWhileHidden() = runTest(dispatcher) {
        clock.instant = Instant.parse("2026-11-04T23:59:59Z")
        val access = access(Workspace())
        val vm = model(access)
        advanceUntilIdle()
        val initial = vm.today
        vm.startForegroundRefresh()
        vm.startForegroundRefresh() // Repeated lifecycle signal must not start another ticker.
        runCurrent()
        clock.instant = clock.instant.plusSeconds(1)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(initial.plusDays(1), vm.today)
        vm.stopForegroundRefresh()
        move("2026-11-10")
        advanceTimeBy(60_000); runCurrent()
        assertEquals(initial.plusDays(1), vm.today)
        vm.startForegroundRefresh()
        assertEquals(day, vm.today)
        assertEquals(0, access.writes)
        assertEquals(Workspace(), vm.workspace)
    }

    @Test fun sameDateTimezoneRefreshChangesPresentationContextNotStoredDates() = runTest(dispatcher) {
        val original = workspace().addCommit("habit", 1.0, day, id = "entry", timestamp = clock.instant)
        val access = access(original)
        val vm = model(access)
        advanceUntilIdle()
        vm.startForegroundRefresh()
        runCurrent()
        val initial = vm.today
        clock.zoneId = ZoneId.of("Europe/Paris")
        vm.refreshDate() // Same path used by the Activity's timezone broadcast.
        assertEquals(initial, vm.today)
        assertEquals(clock.zoneId, vm.zone)
        assertEquals(original, vm.workspace)
        assertEquals(0, access.writes)
        clock.zoneId = ZoneId.of("Pacific/Kiritimati")
        vm.refreshDate()
        assertEquals(initial.plusDays(1), vm.today)
        assertEquals(original.commits, vm.workspace.commits)
    }

    @Test fun foregroundResumeAcrossSeveralDaysLocksOriginalCalendarBoundary() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        val vm = model(access)
        advanceUntilIdle()
        vm.startForegroundRefresh(); runCurrent()
        vm.stopForegroundRefresh()
        move("2026-11-20")
        vm.startForegroundRefresh()
        advanceUntilIdle()
        assertEquals(day, vm.today)
        assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY)), vm.workspace.calendar.transitions)
        assertEquals(LocalDate.parse("2026-11-15"), vm.workspace.calendar.periodOn(day).start)
    }

    @Test fun indeterminateCalendarSaveBlocksCommandsUntilReconciliation() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        advanceUntilIdle()
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failed") }
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        assertEquals(WeekCalendar(), vm.workspace.calendar)
        val writes = access.writes
        vm.setWeekStart(DayOfWeek.TUESDAY)
        vm.complete("habit")
        advanceUntilIdle()
        assertEquals(writes, access.writes)
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertEquals(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY), vm.workspace.calendar.pending)
        assertTrue(vm.workspace.commits.isEmpty())
    }

    @Test fun statusUndoBlocksClockRollbackAndCanBeRetriedAtOriginalDate() = runTest(dispatcher) {
        val original = workspace()
        val vm = model(access(original))
        val notices = notices(vm)
        advanceUntilIdle()
        vm.status("habit", RepoStatus.PAUSED)
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        move("2026-11-03"); vm.refreshDate()
        vm.undo(token)
        advanceUntilIdle()
        assertTrue(notices.last().message.contains("device date"))
        assertEquals(token, notices.last().undoId)
        assertEquals(RepoStatus.PAUSED, vm.workspace.habits.single().versions.last().status)
        move("2026-11-04"); vm.refreshDate()
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(original, vm.workspace)
    }

    @Test fun activationObservedWhileRevisingPendingDestinationSurvivesClockRollback() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.writeActions += { bytes ->
            access.stored = bytes
            move("2026-11-09"); vm.refreshDate()
            move("2026-11-04"); vm.refreshDate()
        }
        vm.setWeekStart(DayOfWeek.TUESDAY)
        advanceUntilIdle()
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.TUESDAY)), vm.workspace.calendar.transitions)
        assertTrue(notices.last().message.contains("newer effective change"))
    }

    @Test fun cancellationCapturedBeforeBoundaryDoesNotLockALaterNewRequestOnRollback() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        val vm = model(access)
        advanceUntilIdle()
        access.writeActions += { bytes ->
            access.stored = bytes
            move("2026-11-09"); vm.refreshDate()
            move("2026-11-04"); vm.refreshDate()
        }
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
        vm.setWeekStart(DayOfWeek.TUESDAY)
        advanceUntilIdle()
        assertNotNull(vm.workspace.calendar.pending)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
    }

    @Test fun activationObservationSurvivesOrdinaryChangedSnapshotReconciliation() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.writeActions += { bytes ->
            access.stored = bytes
            move("2026-11-09"); vm.refreshDate()
            move("2026-11-04"); vm.refreshDate()
            access.failReads = true
        }
        access.writeActions += { throw IOException("rollback failed") }
        vm.setTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        // This replacement attempt writes nothing and must not clear the ordinary
        // theme candidate's calendar observation when that candidate is reconciled.
        vm.startOverFromRecovery()
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, vm.workspace.theme)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY)), vm.workspace.calendar.transitions)
        assertTrue(notices.last().message.contains("newer effective change"))
    }

    @Test fun rolledBackFirstCalendarRequestDoesNotLockLaterRequest() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        advanceUntilIdle()
        access.writeActions += {
            move("2026-11-09"); vm.refreshDate()
            move("2026-11-04"); vm.refreshDate()
            throw IOException("no write")
        }
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
        vm.setWeekStart(DayOfWeek.TUESDAY)
        advanceUntilIdle()
        vm.refreshDate()
        advanceUntilIdle()
        assertNotNull(vm.workspace.calendar.pending)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
    }

    @Test fun reconciledCancellationDoesNotLockLaterRequest() = runTest(dispatcher) {
        val access = access(workspace().requestWeekStart(DayOfWeek.SUNDAY, day))
        val vm = model(access)
        advanceUntilIdle()
        access.writeActions += { bytes ->
            access.stored = bytes
            move("2026-11-09"); vm.refreshDate()
            move("2026-11-04"); vm.refreshDate()
            access.failReads = true
        }
        access.writeActions += { throw IOException("rollback failed") }
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
        vm.setWeekStart(DayOfWeek.TUESDAY)
        advanceUntilIdle()
        vm.refreshDate()
        advanceUntilIdle()
        assertNotNull(vm.workspace.calendar.pending)
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(WeekCalendar(), vm.workspace.calendar)
    }

    @Test fun boundaryCrossedWhileOrdinaryCandidateIsInRecoveryRemainsLocked() = runTest(dispatcher) {
        val access = access()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failed") }
        vm.setWeekStart(DayOfWeek.SUNDAY)
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        move("2026-11-09"); vm.refreshDate()
        move("2026-11-04"); vm.refreshDate()
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        vm.setWeekStart(DayOfWeek.MONDAY)
        advanceUntilIdle()
        assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY)), vm.workspace.calendar.transitions)
        assertTrue(notices.last().message.contains("newer effective change"))
    }

    @Test fun unsuccessfulRecoveryReplacementRetainsOrdinaryCandidateBoundaryEvidence() = runTest(dispatcher) {
        for (preservationRejected in listOf(false, true)) {
            move("2026-11-04")
            val access = access()
            val vm = model(access)
            val notices = notices(vm)
            advanceUntilIdle()
            access.writeActions += { bytes ->
                access.stored = bytes
                move("2026-11-09"); vm.refreshDate()
                move("2026-11-04"); vm.refreshDate()
                access.failReads = true
            }
            access.writeActions += { throw IOException("rollback failed") }
            vm.setWeekStart(DayOfWeek.SUNDAY)
            advanceUntilIdle()
            access.failReads = false
            access.failAllWrites = true
            access.failPreservation = preservationRejected
            vm.startOverFromRecovery()
            advanceUntilIdle()
            assertTrue(vm.load is WorkspaceLoad.Recovery)
            access.failAllWrites = false
            access.failPreservation = false
            vm.retryLoad()
            advanceUntilIdle()
            vm.setWeekStart(DayOfWeek.MONDAY)
            advanceUntilIdle()
            assertEquals(listOf(WeekTransition(LocalDate.parse("2026-11-09"), DayOfWeek.SUNDAY)), vm.workspace.calendar.transitions)
            assertTrue(notices.last().message.contains("newer effective change"))
        }
    }

    @Test fun clockRollbackBlocksConfigurationEditButNotHistoricalCorrection() = runTest(dispatcher) {
        val vm = model(access(workspace().addCommit("habit", 1.0, day, id = "entry", timestamp = clock.instant)))
        val notices = notices(vm)
        advanceUntilIdle()
        vm.saveHabit("habit", draft.copy(target = 20.0))
        advanceUntilIdle()
        move("2026-11-03"); vm.refreshDate()
        vm.saveHabit("habit", draft.copy(target = 30.0))
        advanceUntilIdle()
        assertTrue(notices.last().message.contains("newer dated configuration"))
        vm.saveCommit("habit", "entry", 5.0, "corrected")
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-11-04"), vm.workspace.commits.single().date)
        assertEquals(5.0, vm.workspace.commits.single().amount, 0.0)
    }
}
