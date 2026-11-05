package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressAnalyticsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val draft = HabitDraft("Read", "", 20.0, "pages")
    private fun workspace(schedule: HabitSchedule = HabitSchedule.Daily) =
        Workspace().createHabit(draft.copy(schedule = schedule), monday, "read")

    @Test fun partialExcessAndRemainderAreSeparateFromCappedDisplay() {
        val partial = workspace().addCommit("read", 5.0, monday).addCommit("read", 7.0, monday)
        val day = ProgressAnalytics(partial, monday).daily("read", monday)
        assertEquals(TargetProgress(12.0, 20.0), day.progress)
        assertEquals(0.6, day.progress.ratio!!, 0.0)
        assertEquals(8.0, day.progress.remaining!!, 0.0)
        assertEquals(2, day.commitCount)
        assertEquals(DayAssessment.PENDING, day.assessment)
        assertFalse(day.qualifies)
        val completed = partial.completeGoal("read", monday)
        assertEquals(8.0, completed.commits.last().amount, 0.0)
        assertSame(completed, completed.completeGoal("read", monday))
        val excess = ProgressAnalytics(completed.addCommit("read", 15.0, monday), monday).daily("read", monday)
        assertEquals(35.0, excess.progress.total, 0.0)
        assertEquals(1.0, excess.progress.ratio!!, 0.0)
        assertEquals(0.0, excess.progress.remaining!!, 0.0)
        assertEquals(DayAssessment.ACHIEVED, excess.assessment)
        assertTrue(excess.qualifies)
    }

    @Test fun threeDecimalAccumulationDoesNotMissTargets() {
        val state = Workspace().createHabit(draft.copy(target = 0.003), monday, "read")
            .addCommit("read", 0.001, monday).addCommit("read", 0.002, monday)
        val day = ProgressAnalytics(state, monday).daily("read", monday)
        assertEquals(0.003, day.progress.total, 0.0)
        assertTrue(day.qualifies)
    }

    @Test fun selectedWeekdaysAllowOptionalActivityWithoutBorrowingCredit() {
        val state = workspace(HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
            .addCommit("read", 25.0, monday.plusDays(1))
        val analytics = ProgressAnalytics(state, monday.plusDays(3))
        val optional = analytics.daily("read", monday.plusDays(1))
        assertEquals(DateEligibility.NOT_SCHEDULED, optional.eligibility)
        assertEquals(DayAssessment.NOT_APPLICABLE, optional.assessment)
        assertEquals(25.0, optional.progress.total, 0.0)
        assertTrue(optional.progress.targetReached)
        assertFalse(optional.qualifies)
        assertEquals(DayAssessment.MISSED, analytics.daily("read", monday.plusDays(2)).assessment)
        assertEquals(1, analytics.commits.activityCount(monday.plusDays(1)))
    }

    @Test fun weeklyOpportunityNeverBecomesADailyObligation() {
        val state = workspace(HabitSchedule.WeeklyQuota(3)).addCommit("read", 20.0, monday)
        val analytics = ProgressAnalytics(state, monday.plusDays(2))
        val completed = analytics.daily("read", monday)
        assertEquals(DateEligibility.WEEKLY_OPPORTUNITY, completed.eligibility)
        assertEquals(DayAssessment.NOT_APPLICABLE, completed.assessment)
        assertTrue(completed.qualifies)
        assertEquals(DayAssessment.NOT_APPLICABLE, analytics.daily("read", monday.plusDays(1)).assessment)
    }

    @Test fun pendingTodayAndFutureNeverFailOrCreditFutureRecords() {
        val state = workspace().addCommit("read", 20.0, monday.plusDays(2))
        val analytics = ProgressAnalytics(state, monday.plusDays(1))
        assertEquals(DayAssessment.MISSED, analytics.daily("read", monday).assessment)
        assertEquals(DayAssessment.PENDING, analytics.daily("read", monday.plusDays(1)).assessment)
        val future = analytics.daily("read", monday.plusDays(2))
        assertEquals(DayAssessment.PENDING, future.assessment)
        assertEquals(20.0, future.progress.total, 0.0)
        assertFalse(future.qualifies)
        assertEquals(ActivityStreaks(0, 0), analytics.commits.activityStreaks(analytics.today))
    }

    @Test fun preCreationPausedAndArchivedDatesAreNotApplicableButRetainActuals() {
        val state = workspace().addCommit("read", 20.0, monday, id = "log")
            .changeStatus("read", RepoStatus.PAUSED, monday)
            .changeStatus("read", RepoStatus.ARCHIVED, monday.plusDays(1))
        val analytics = ProgressAnalytics(state, monday.plusDays(2))
        val before = analytics.daily("read", monday.minusDays(1))
        assertEquals(DateEligibility.BEFORE_CREATION, before.eligibility)
        assertEquals(TargetProgress(0.0, null), before.progress)
        assertNull(before.progress.ratio)
        assertNull(before.progress.remaining)
        val paused = analytics.daily("read", monday)
        assertEquals(DateEligibility.PAUSED, paused.eligibility)
        assertEquals(20.0, paused.progress.total, 0.0)
        assertFalse(paused.qualifies)
        assertEquals(DayAssessment.NOT_APPLICABLE, paused.assessment)
        assertEquals(DateEligibility.ARCHIVED, analytics.daily("read", monday.plusDays(1)).eligibility)
        assertThrows(IllegalArgumentException::class.java) { state.addCommit("read", 1.0, monday) }
        assertThrows(IllegalArgumentException::class.java) { state.addCommit("read", 1.0, monday.plusDays(1)) }
        val corrected = ProgressAnalytics(state.editCommit("log", 5.0, "correction"), analytics.today)
        assertEquals(5.0, corrected.daily("read", monday).progress.total, 0.0)
        assertFalse(corrected.daily("read", monday).qualifies)
    }

    @Test fun datedTargetsAndSchedulesAreUsedEvenForUnsortedLegacyVersions() {
        val first = GoalVersion(monday, 20.0)
        val later = first.copy(date = monday.plusDays(1), target = 40.0,
            schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.TUESDAY)))
        val state = workspace().let { it.copy(habits = listOf(it.habits.single().copy(versions = listOf(later, first)))) }
            .addCommit("read", 20.0, monday).addCommit("read", 20.0, monday.plusDays(1))
        val analytics = ProgressAnalytics(state, monday.plusDays(3))
        assertTrue(analytics.daily("read", monday).qualifies)
        assertEquals(0.5, analytics.daily("read", monday.plusDays(1)).progress.ratio!!, 0.0)
        assertEquals(DayAssessment.MISSED, analytics.daily("read", monday.plusDays(1)).assessment)
        assertEquals(DateEligibility.NOT_SCHEDULED, analytics.daily("read", monday.plusDays(2)).eligibility)
        assertEquals(listOf(later, first), state.habits.single().versions)
    }

    @Test fun correctionAndDeletionRequireANewIndexWithoutChangingOldSnapshotOrExpectations() {
        val state = workspace().addCommit("read", 20.0, monday, id = "log")
        val original = ProgressAnalytics(state, monday.plusDays(1))
        val correctedState = state.editCommit("log", 19.999, "correction")
        val corrected = ProgressAnalytics(correctedState, original.today)
        assertEquals(DayAssessment.ACHIEVED, original.daily("read", monday).assessment)
        assertEquals(DayAssessment.MISSED, corrected.daily("read", monday).assessment)
        assertEquals(state.habits, correctedState.habits)
        val deleted = ProgressAnalytics(correctedState.removeCommit("log"), original.today)
        assertEquals(RecordedProgress(), deleted.commits.recorded("read", monday))
        assertEquals(0, deleted.commits.activityCount(monday))
        assertEquals(20.0, original.daily("read", monday).progress.total, 0.0)
    }

    @Test fun storedDatesAreAuthoritativeRegardlessOfTimestampTimezoneOrFutureInstant() {
        val state = workspace().addCommit("read", 20.0, monday,
            timestamp = Instant.parse("2030-01-01T23:59:59Z"))
        val analytics = ProgressAnalytics(state, monday)
        assertTrue(analytics.daily("read", monday).qualifies)
        assertEquals(1, analytics.commits.activityCount(monday))
        assertEquals(0, analytics.commits.activityCount(LocalDate.of(2030, 1, 1)))
    }

    @Test fun unlikeUnitsOnlyShareCountsNeverAmountTotals() {
        val state = workspace().createHabit(draft.copy(target = 60.0, unit = "minutes"), monday, "move")
            .addCommit("read", 5.0, monday).addCommit("read", 7.0, monday).addCommit("move", 45.0, monday)
        val index = CommitIndex(state.commits)
        assertEquals(RecordedProgress(12.0, 2), index.recorded("read", monday))
        assertEquals(RecordedProgress(45.0, 1), index.recorded("move", monday))
        assertEquals(3, index.activityCount(monday))
        assertEquals(2, index.activityCount(monday, "read"))
        assertEquals(0, index.activityCount(monday, "unknown"))
    }

    @Test fun snapshotDoesNotRetainCallerOwnedMutableLists() {
        val versions = mutableListOf(GoalVersion(monday, 20.0))
        val habits = mutableListOf(workspace().habits.single().copy(versions = versions))
        val commits = mutableListOf(Commit("log", "read", 20.0, monday))
        val analytics = ProgressAnalytics(Workspace(habits = habits, commits = commits), monday)
        versions.clear()
        habits.clear()
        commits.clear()
        assertTrue(analytics.daily("read", monday).qualifies)
        assertEquals(1, analytics.commits.activityCount(monday))
        assertThrows(UnsupportedOperationException::class.java) { (analytics.habits as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (analytics.habits[0].versions as MutableList).clear() }
    }

    @Test fun activityStreakAllowsUnfinishedTodayButNotAGapAndIgnoresGoalEligibility() {
        val state = workspace(HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY)))
            .addCommit("read", 0.001, monday).addCommit("read", 0.001, monday.plusDays(1))
            .addCommit("read", 0.001, monday.plusDays(1))
            .addCommit("read", 0.001, monday.plusDays(3))
        val index = CommitIndex(state.commits)
        assertEquals(ActivityStreaks(2, 2), index.activityStreaks(monday.plusDays(2)))
        assertEquals(ActivityStreaks(1, 2), index.activityStreaks(monday.plusDays(3), "read"))
        assertEquals(ActivityStreaks(1, 2), index.activityStreaks(monday.plusDays(4)))
        assertEquals(ActivityStreaks(0, 2), index.activityStreaks(monday.plusDays(5)))
        assertEquals(ActivityStreaks(0, 0), index.activityStreaks(monday, "unknown"))
    }

    @Test fun streakCalendarArithmeticSpansLeapDayYearAndDstWithoutTimestamps() {
        listOf(LocalDate.of(2024, 2, 28), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 31)).forEach { start ->
            val index = CommitIndex((0L..2L).map { Commit("$it", "read", 1.0, start.plusDays(it)) })
            assertEquals(ActivityStreaks(3, 3), index.activityStreaks(start.plusDays(2)))
        }
    }

    @Test fun emptyHistoryHasNoInventedProgressOrStreak() {
        val analytics = ProgressAnalytics(Workspace(), monday)
        assertTrue(analytics.habits.isEmpty())
        assertEquals(0, analytics.commits.activityCount(monday))
        assertEquals(ActivityStreaks(0, 0), analytics.commits.activityStreaks(monday))
        assertThrows(IllegalArgumentException::class.java) { analytics.daily("missing", monday) }
    }
}
