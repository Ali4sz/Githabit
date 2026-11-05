package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeeklyAnalyticsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val draft = HabitDraft("Read", "", 20.0, "pages", schedule = HabitSchedule.WeeklyQuota(3))
    private fun workspace(created: LocalDate = monday, days: Int = 3) =
        Workspace().createHabit(draft.copy(schedule = HabitSchedule.WeeklyQuota(days)), created, "read")
    private fun Workspace.qualify(vararg offsets: Long): Workspace = offsets.fold(this) { state, offset ->
        state.addCommit("read", 20.0, monday.plusDays(offset), id = "log-$offset")
    }

    @Test fun repeatedCommitsAndPartialDaysCountOnlyDistinctQualifiedDates() {
        val state = workspace().addCommit("read", 5.0, monday).addCommit("read", 7.0, monday)
            .addCommit("read", 8.0, monday).addCommit("read", 19.999, monday.plusDays(1)).qualify(2)
        val analytics = ProgressAnalytics(state, monday.plusDays(2))
        val result = analytics.weekly("read", monday)
        assertEquals(listOf(monday, monday.plusDays(2)), result.qualifyingDates)
        assertEquals(WeeklyQuotaProgress(2, WeeklyGoal(3, 20.0)), result.quota)
        assertEquals(WeekAssessment.PENDING, result.assessment)
        assertEquals(19.999, result.days[1].progress.total, 0.0)
        assertTrue(result.days.all { it.assessment == DayAssessment.NOT_APPLICABLE })
        assertEquals(5, analytics.weekSummary(monday).activityCount)
    }

    @Test fun mondayThursdaySaturdayMeetsThreeDayQuotaWithoutFourFailedDays() {
        val analytics = ProgressAnalytics(workspace().qualify(0, 3, 5), monday.plusDays(7))
        val result = analytics.weekly("read", monday.plusDays(2))
        assertEquals(WeekAssessment.ACHIEVED, result.assessment)
        assertEquals(3, result.quota!!.displayDays)
        val adherence = analytics.adherence(monday, monday.plusDays(7))
        assertEquals(DayAdherence(0, 0), adherence.days)
        assertNull(adherence.days.rate)
        assertEquals(WeekAdherence(1, 0), adherence.weeks)
        assertEquals(1.0, adherence.weeks.rate!!, 0.0)
    }

    @Test fun quotaLimitsRetainSurplusButNeverCarryItIntoTheNextWeek() {
        for (n in listOf(1, 7)) {
            val state = workspace(days = n).qualify(0, 1, 2, 3, 4, 5, 6)
            val analytics = ProgressAnalytics(state, monday.plusDays(7))
            val result = analytics.weekly("read", monday)
            assertEquals(7, result.qualifyingDates.size)
            assertEquals(n, result.quota!!.displayDays)
            assertEquals(0, result.quota.remainingDays)
            assertEquals(WeekAssessment.ACHIEVED, result.assessment)
            val next = analytics.weekly("read", monday.plusDays(7))
            assertEquals(0, next.quota!!.qualifyingDays)
            assertEquals(WeekAssessment.PENDING, next.assessment)
        }
    }

    @Test fun openIncompleteWeekIsPendingAndClosedIncompleteWeekIsMissed() {
        val state = workspace().qualify(0)
        assertEquals(WeekAssessment.PENDING, ProgressAnalytics(state, monday.plusDays(6)).weekly("read", monday).assessment)
        val closed = ProgressAnalytics(state, monday.plusDays(7)).weekly("read", monday)
        assertEquals(WeekAssessment.MISSED, closed.assessment)
        assertEquals(2, closed.quota!!.remainingDays)
        assertEquals(WeekAdherence(0, 1), ProgressAnalytics(state, monday.plusDays(7)).adherence(monday, monday.plusDays(7)).weeks)
    }

    @Test fun futureRecordsAreVisibleButCannotMeetQuotaEarly() {
        val state = workspace(days = 1).qualify(3)
        val result = ProgressAnalytics(state, monday).weekly("read", monday)
        assertEquals(WeekAssessment.PENDING, result.assessment)
        assertTrue(result.qualifyingDates.isEmpty())
        assertEquals(20.0, result.days[3].progress.total, 0.0)
        assertEquals(WeekAssessment.ACHIEVED, ProgressAnalytics(state, monday.plusDays(3)).weekly("read", monday).assessment)
    }

    @Test fun creationMidweekIsTransitionalEvenWhenNominalQuotaIsMet() {
        val state = workspace(created = monday.plusDays(3), days = 1).qualify(3, 4)
        val analytics = ProgressAnalytics(state, monday.plusDays(4))
        val result = analytics.weekly("read", monday)
        assertEquals(WeekAssessment.TRANSITIONAL, result.assessment)
        assertEquals(setOf(WeekTransitionReason.CREATED_DURING_PERIOD), result.transitionReasons)
        assertEquals(2, result.qualifyingDates.size)
        assertNull(result.quota)
        assertEquals(WeeklyGoal(1, 20.0), result.endingWeeklyGoal)
        assertEquals(listOf("read"), analytics.todayGroups()[2].habits.map { it.habitId })
        assertEquals(WeekAdherence(0, 0), analytics.adherence(monday, monday.plusDays(7)).weeks)
    }

    @Test fun targetScheduleQuotaAndStatusChangesMidweekAreUnscored() {
        val state = workspace().qualify(0, 1)
        val changes = listOf(
            state.editHabit("read", draft.copy(target = 10.0), monday.plusDays(2)),
            state.editHabit("read", draft.copy(schedule = HabitSchedule.WeeklyQuota(1)), monday.plusDays(2)),
            state.editHabit("read", draft.copy(schedule = HabitSchedule.Daily), monday.plusDays(2)),
            state.changeStatus("read", RepoStatus.PAUSED, monday.plusDays(2)),
            state.changeStatus("read", RepoStatus.ARCHIVED, monday.plusDays(2)),
            state.changeStatus("read", RepoStatus.PAUSED, monday.plusDays(2))
                .changeStatus("read", RepoStatus.ACTIVE, monday.plusDays(3)),
        )
        changes.forEach { changed ->
            val analytics = ProgressAnalytics(changed, monday.plusDays(7))
            val result = analytics.weekly("read", monday)
            assertEquals(WeekAssessment.TRANSITIONAL, result.assessment)
            assertTrue(WeekTransitionReason.CONFIGURATION_CHANGED in result.transitionReasons)
            assertNull(result.quota)
            assertEquals(WeekAdherence(0, 0), analytics.adherence(monday, monday.plusDays(7)).weeks)
        }
    }

    @Test fun changesOnFirstDayEstablishAStableFullWeek() {
        val state = workspace(created = monday.minusDays(10))
            .changeStatus("read", RepoStatus.PAUSED, monday.minusDays(3))
            .changeStatus("read", RepoStatus.ACTIVE, monday)
            .editHabit("read", draft.copy(target = 10.0, schedule = HabitSchedule.WeeklyQuota(1)), monday)
            .addCommit("read", 10.0, monday)
        val result = ProgressAnalytics(state, monday).weekly("read", monday)
        assertEquals(WeekAssessment.ACHIEVED, result.assessment)
        assertEquals(WeeklyQuotaProgress(1, WeeklyGoal(1, 10.0)), result.quota)
        assertTrue(result.transitionReasons.isEmpty())
    }

    @Test fun cosmeticNoOpRedundantLegacyVersionsAndSameDayReversionsDoNotFakeTransitions() {
        val created = workspace().qualify(0, 1, 2)
        val day = monday.plusDays(3)
        val cosmetic = created.editHabit("read", draft.copy(name = "Renamed"), day)
        val noOp = created.editHabit("read", draft, day)
        val reverted = created.changeStatus("read", RepoStatus.PAUSED, day)
            .editHabit("read", draft.copy(target = 10.0, schedule = HabitSchedule.Daily), day)
            .changeStatus("read", RepoStatus.ACTIVE, day).editHabit("read", draft, day)
        val habit = created.habits.single()
        val legacy = created.copy(habits = listOf(habit.copy(versions =
            listOf(habit.versions.single().copy(date = day)) + habit.versions)))
        listOf(cosmetic, noOp, reverted, legacy).forEach {
            assertEquals(WeekAssessment.ACHIEVED, ProgressAnalytics(it, day).weekly("read", monday).assessment)
        }
    }

    @Test fun fullyPausedArchivedPreCreationAndNonweeklyPeriodsAreNotApplicable() {
        val inactive = listOf(
            workspace().changeStatus("read", RepoStatus.PAUSED, monday),
            workspace().changeStatus("read", RepoStatus.ARCHIVED, monday),
            workspace(created = monday.plusDays(7)),
            workspace().editHabit("read", draft.copy(schedule = HabitSchedule.Daily), monday),
        )
        inactive.forEach {
            val result = ProgressAnalytics(it, monday.plusDays(7)).weekly("read", monday)
            assertEquals(WeekAssessment.NOT_APPLICABLE, result.assessment)
            assertNull(result.quota)
        }
    }

    @Test fun partialSameDayReversionStillLeavesARealTargetTransition() {
        val state = workspace().qualify(0, 1, 2)
        val day = monday.plusDays(3)
        val changed = state.changeStatus("read", RepoStatus.PAUSED, day)
            .editHabit("read", draft.copy(target = 10.0, schedule = HabitSchedule.Daily), day)
            .changeStatus("read", RepoStatus.ACTIVE, day)
            .editHabit("read", draft.copy(target = 10.0), day)
        val analytics = ProgressAnalytics(changed, monday.plusDays(7))
        val week = analytics.weekly("read", monday)
        assertEquals(WeekAssessment.TRANSITIONAL, week.assessment)
        assertEquals(setOf(WeekTransitionReason.CONFIGURATION_CHANGED), week.transitionReasons)
        assertNull(week.quota)
        assertEquals(20.0, analytics.daily("read", day.minusDays(1)).progress.target!!, 0.0)
        assertEquals(10.0, analytics.daily("read", day).progress.target!!, 0.0)
        assertEquals(WeeklyGoal(3, 10.0), week.endingWeeklyGoal)
    }

    @Test fun pausesRetainLogsWithoutAwardingQualifiedDateCredit() {
        val state = workspace().qualify(0, 1, 2).changeStatus("read", RepoStatus.PAUSED, monday.plusDays(2))
        val result = ProgressAnalytics(state, monday.plusDays(7)).weekly("read", monday)
        assertEquals(WeekAssessment.TRANSITIONAL, result.assessment)
        assertEquals(listOf(monday, monday.plusDays(1)), result.qualifyingDates)
        assertEquals(20.0, result.days[2].progress.total, 0.0)
        assertEquals(1, result.days[2].commitCount)
    }

    @Test fun correctionAndDeletionRecomputeClosedWeekAndLeaveExpectationsUntouched() {
        val state = workspace().qualify(0, 3, 5)
        val original = ProgressAnalytics(state, monday.plusDays(7))
        val corrected = state.editCommit("log-3", 19.999, "Correction")
        val result = ProgressAnalytics(corrected, original.today).weekly("read", monday)
        assertEquals(WeekAssessment.MISSED, result.assessment)
        assertEquals(2, result.quota!!.qualifyingDays)
        val restored = corrected.editCommit("log-3", 20.0, "")
        assertEquals(WeekAssessment.ACHIEVED, ProgressAnalytics(restored, original.today).weekly("read", monday).assessment)
        val removed = state.removeCommit("log-5")
        assertEquals(WeekAssessment.MISSED, ProgressAnalytics(removed, original.today).weekly("read", monday).assessment)
        assertEquals(state.habits, removed.habits)
        assertEquals(state.habits, corrected.habits)
        assertEquals(WeekAssessment.ACHIEVED, original.weekly("read", monday).assessment)
    }

    @Test fun mixedDailyAndWeeklyHistoryKeepsEarlierDailyExpectationsSeparate() {
        val state = Workspace().createHabit(draft.copy(schedule = HabitSchedule.Daily), monday, "read")
            .qualify(0).editHabit("read", draft, monday.plusDays(2)).qualify(2, 3, 4)
        val analytics = ProgressAnalytics(state, monday.plusDays(7))
        val week = analytics.weekly("read", monday)
        assertEquals(WeekAssessment.TRANSITIONAL, week.assessment)
        assertNull(week.quota)
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(3), monday.plusDays(4)), week.qualifyingDates)
        val adherence = analytics.adherence(monday, monday.plusDays(7))
        assertEquals(DayAdherence(1, 1), adherence.days)
        assertEquals(WeekAdherence(0, 0), adherence.weeks)
    }

    @Test fun pendingCalendarChangeDoesNotAlterCurrentQuotaOrHistoricalAssessment() {
        val state = workspace().qualify(0, 3, 5)
        val changed = state.requestWeekStart(DayOfWeek.SUNDAY, monday.plusDays(3))
        val before = ProgressAnalytics(state, monday.plusDays(6))
        val after = ProgressAnalytics(changed, before.today)
        assertEquals(before.weekly("read", monday), after.weekly("read", monday))
        assertEquals(before.dateSummary(monday), after.dateSummary(monday))
    }

    @Test fun bridgesArePracticeEvenWhenQuotaMetWhileDailyAndWeekdayObligationsContinue() {
        val dailyDraft = draft.copy(schedule = HabitSchedule.Daily)
        val weekdays = draft.copy(schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY)))
        val state = workspace(days = 1).createHabit(dailyDraft, monday, "daily")
            .createHabit(weekdays, monday, "weekday").requestWeekStart(DayOfWeek.SUNDAY, monday)
            .qualify(7, 8).addCommit("daily", 20.0, monday.plusDays(7))
        val analytics = ProgressAnalytics(state, monday.plusDays(9))
        val week = analytics.weekly("read", monday.plusDays(7))
        assertEquals(WeekPeriodKind.BRIDGE, week.period.kind)
        assertEquals(WeekAssessment.TRANSITIONAL, week.assessment)
        assertEquals(setOf(WeekTransitionReason.CALENDAR_BRIDGE), week.transitionReasons)
        assertEquals(2, week.qualifyingDates.size)
        assertNull(week.quota)
        assertEquals(WeeklyGoal(1, 20.0), week.endingWeeklyGoal)
        assertEquals(DayAssessment.ACHIEVED, analytics.daily("daily", monday.plusDays(7)).assessment)
        assertEquals(DayAssessment.MISSED, analytics.daily("weekday", monday.plusDays(7)).assessment)
        assertEquals(DateEligibility.NOT_SCHEDULED, analytics.daily("weekday", monday.plusDays(8)).eligibility)
        val summary = analytics.weekSummary(monday.plusDays(8))
        assertEquals(6, summary.dates.size)
        assertEquals(3, summary.activityCount)
        assertEquals(week.period, summary.period)
        val adherence = analytics.adherence(week.period.start, week.period.endExclusive)
        assertEquals(DayAdherence(1, 2), adherence.days)
        assertEquals(WeekAdherence(0, 0), adherence.weeks)
        assertEquals(listOf("read"), analytics.todayGroups()[2].habits.map { it.habitId })
    }

    @Test fun chainedBridgesDoNotCarryPracticeIntoFirstFullWeek() {
        val state = workspace(days = 1).requestWeekStart(DayOfWeek.SUNDAY, monday)
            .requestWeekStart(DayOfWeek.MONDAY, monday.plusDays(9)).qualify(7, 13)
        val analytics = ProgressAnalytics(state, monday.plusDays(14))
        assertEquals(WeekAssessment.TRANSITIONAL, analytics.weekly("read", monday.plusDays(7)).assessment)
        assertEquals(WeekAssessment.TRANSITIONAL, analytics.weekly("read", monday.plusDays(13)).assessment)
        val full = analytics.weekly("read", monday.plusDays(14))
        assertEquals(WeekAssessment.PENDING, full.assessment)
        assertEquals(0, full.quota!!.qualifyingDays)
    }

    @Test fun everyWeekdayConventionDrivesWeeklyBoundsInsteadOfRollingSevenDays() {
        for (weekday in DayOfWeek.entries) {
            val calendar = WeekCalendar(weekday)
            val period = calendar.periodOn(monday)
            val state = workspace(created = period.start.minusDays(7), days = 1).copy(calendar = calendar).qualify(0)
            val summary = ProgressAnalytics(state, monday).weekSummary(monday)
            assertEquals(period, summary.period)
            assertEquals(period.start, summary.dates.first().date)
            assertEquals(period.endExclusive.minusDays(1), summary.dates.last().date)
            assertEquals(WeekAssessment.ACHIEVED, summary.habits.single().assessment)
        }
    }

    @Test fun weeklyPeriodAcrossNewYearUsesExplicitBounds() {
        val start = LocalDate.of(2025, 12, 29)
        val state = workspace(created = start, days = 1).addCommit("read", 20.0, LocalDate.of(2026, 1, 1))
        val summary = ProgressAnalytics(state, LocalDate.of(2026, 1, 4)).weekSummary(LocalDate.of(2026, 1, 1))
        assertEquals(start, summary.period.start)
        assertEquals(LocalDate.of(2026, 1, 5), summary.period.endExclusive)
        assertEquals(WeekAssessment.ACHIEVED, summary.habits.single().assessment)
    }

    @Test fun todayGroupsAreOrderedAndExcludePausedArchivedAndPreCreationHabits() {
        val due = draft.copy(schedule = HabitSchedule.Daily)
        var state = Workspace()
            .createHabit(due, monday, "due-first").createHabit(due, monday, "due-second")
            .createHabit(draft, monday, "weekly").createHabit(draft, monday.plusDays(1), "practice")
            .createHabit(due, monday, "complete")
            .createHabit(draft.copy(schedule = HabitSchedule.WeeklyQuota(1)), monday, "quota-met")
            .createHabit(due.copy(schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))), monday, "optional")
            .createHabit(due, monday, "paused").createHabit(due, monday, "archived")
            .createHabit(due, monday.plusDays(3), "future")
        state = state.changeStatus("paused", RepoStatus.PAUSED, monday)
            .changeStatus("archived", RepoStatus.ARCHIVED, monday)
            .addCommit("complete", 20.0, monday.plusDays(2)).addCommit("quota-met", 20.0, monday)
        val groups = ProgressAnalytics(state, monday.plusDays(2)).todayGroups()
        assertEquals(TodayGroupKind.entries, groups.map { it.kind })
        assertEquals(listOf("due-first", "due-second"), groups[0].habits.map { it.habitId })
        assertEquals(listOf("weekly"), groups[1].habits.map { it.habitId })
        assertEquals(listOf("practice"), groups[2].habits.map { it.habitId })
        assertEquals(listOf("complete", "quota-met", "optional"), groups[3].habits.map { it.habitId })
        assertEquals(listOf(true, true, false, false), groups.map { it.isRemaining })
        assertNull(groups[0].habits.first().weekly)
        assertNotNull(groups[1].habits.first().weekly)
    }

    @Test fun metQuotaMovesToOptionalWithoutHidingHistoryOrPreventingLogging() {
        val state = workspace(days = 1).qualify(0)
        val groups = ProgressAnalytics(state, monday.plusDays(1)).todayGroups()
        assertTrue(groups[1].habits.isEmpty())
        assertEquals(listOf("read"), groups[3].habits.map { it.habitId })
        val extra = state.addCommit("read", 5.0, monday.plusDays(1))
        assertEquals(2, extra.commits.size)
        assertEquals(1, ProgressAnalytics(extra, monday.plusDays(1)).weekly("read", monday).quota!!.qualifyingDays)
        val corrected = state.editCommit("log-0", 19.999, "")
        assertEquals(listOf("read"), ProgressAnalytics(corrected, monday.plusDays(1)).todayGroups()[1].habits.map { it.habitId })
    }

    @Test fun dayAndWeekAdherenceRemainSeparateAndExcludePendingAndTransitions() {
        val state = workspace().qualify(0, 1, 2).createHabit(draft.copy(schedule = HabitSchedule.Daily), monday, "daily")
            .addCommit("daily", 20.0, monday)
        val analytics = ProgressAnalytics(state, monday.plusDays(2))
        val result = analytics.adherence(monday, monday.plusDays(7))
        assertEquals(DayAdherence(1, 1), result.days) // Wednesday pending, not failed.
        assertEquals(WeekAdherence(1, 0), result.weeks) // Achieved early, not an average of day/week rates.
        assertEquals(0.5, result.days.rate!!, 0.0)
        assertEquals(1.0, result.weeks.rate!!, 0.0)
        assertEquals(WeekAdherence(0, 0), analytics.adherence(monday.plusDays(1), monday.plusDays(7)).weeks)
        assertEquals(WeekAdherence(0, 0), analytics.adherence(monday, monday.plusDays(6)).weeks)
        assertEquals(DayAdherence(0, 0), analytics.adherence(monday, monday.plusDays(7), "read").days)
    }

    @Test fun dateAndWeekSummariesKeepHabitUnitsSeparateAndUseSamePeriods() {
        val state = workspace().createHabit(draft.copy(unit = "minutes", schedule = HabitSchedule.Daily), monday, "move")
            .qualify(0).addCommit("move", 7.0, monday)
        val analytics = ProgressAnalytics(state, monday)
        val date = analytics.dateSummary(monday)
        assertEquals(2, date.activityCount)
        assertEquals(listOf(20.0, 7.0), date.habits.map { it.progress.total })
        val week = analytics.weekSummary(monday)
        assertEquals(date.period, week.period)
        assertEquals(date, week.dates.first())
        assertEquals(1, analytics.weekSummary(monday, "move").activityCount)
        assertEquals(listOf("move"), analytics.dateSummary(monday, "move").habits.map { it.habitId })
    }

    @Test fun adherenceTraversesMultipleFullWeeksAndChainedBridgesExactlyOnce() {
        val state = workspace().qualify(0, 3, 5).requestWeekStart(DayOfWeek.SUNDAY, monday)
            .requestWeekStart(DayOfWeek.MONDAY, monday.plusDays(9)).qualify(7, 8, 9, 13)
        val analytics = ProgressAnalytics(state, monday.plusDays(21))
        assertEquals(WeekAdherence(1, 1), analytics.adherence(monday, monday.plusDays(21)).weeks)
        assertEquals(WeekAdherence(1, 1), analytics.adherence(monday.minusDays(1), monday.plusDays(22)).weeks)
        assertEquals(WeekAdherence(0, 1), analytics.adherence(monday.plusDays(1), monday.plusDays(21)).weeks)
        assertEquals(WeekAdherence(0, 0), analytics.adherence(monday.plusDays(1), monday.plusDays(20)).weeks)
        assertEquals(0.5, analytics.adherence(monday, monday.plusDays(21)).weeks.rate!!, 0.0)
        assertEquals(DayAdherence(0, 0), analytics.adherence(monday, monday.plusDays(21)).days)
    }

    @Test fun repeatedQueriesReuseTheIndexForTwentyHabitsOverFiveYears() {
        val start = monday.minusYears(5)
        val habits = (0 until 20).map { index ->
            workspace(created = start).habits.single().copy(id = "habit-$index")
        }
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= monday }.toList()
        val records = habits.flatMap { habit -> dates.map { date ->
            Commit("${habit.id}-$date", habit.id, 20.0, date)
        } }
        var reads = 0
        val counted = object : AbstractList<Commit>() {
            override val size: Int get() = records.size
            override fun get(index: Int): Commit { reads++; return records[index] }
        }
        val analytics = ProgressAnalytics(Workspace(habits = habits, commits = counted), monday)
        assertEquals(records.size, reads)
        repeat(50) { offset ->
            val date = monday.minusDays(offset.toLong())
            assertEquals(20, analytics.dateSummary(date).activityCount)
            val period = analytics.calendar.periodOn(date)
            assertEquals(20 * dates.count { it in period }, analytics.weekSummary(date).activityCount)
            assertEquals(20.0, analytics.daily("habit-0", date).progress.total, 0.0)
        }
        assertEquals(ActivityStreaks(dates.size, dates.size), analytics.commits.activityStreaks(monday, "habit-0"))
        assertEquals(records.size, reads) // No per-cell or summary rescan of the input commit history.
    }

    @Test fun emptySummaryHasZeroActivityAndUnavailableAdherenceNotFakeSuccess() {
        val analytics = ProgressAnalytics(Workspace(), monday)
        assertEquals(0, analytics.dateSummary(monday).activityCount)
        assertEquals(0, analytics.weekSummary(monday).activityCount)
        assertTrue(analytics.weekSummary(monday).habits.isEmpty())
        assertTrue(analytics.todayGroups().all { it.habits.isEmpty() })
        val adherence = analytics.adherence(monday, monday.plusDays(7))
        assertEquals(0, adherence.days.assessedDates)
        assertEquals(0, adherence.weeks.assessedWeeks)
        assertNull(adherence.days.rate)
        assertNull(adherence.weeks.rate)
        assertEquals(adherence, analytics.adherence(monday, monday))
        assertThrows(IllegalArgumentException::class.java) { analytics.adherence(monday, monday.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { analytics.weekSummary(monday, "missing") }
    }
}
