package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TodayOverviewTest {
    private val monday = LocalDate.parse("2026-11-02")
    private val wednesday = monday.plusDays(2)
    private val weekly = HabitDraft("Practice", "", 10.0, "minutes", schedule = HabitSchedule.WeeklyQuota(3))
    private fun Workspace.groups(date: LocalDate = wednesday) = ProgressAnalytics(this, date).todayGroups()
    private fun status(state: Workspace) = todayStatus(state.habits.size, state.groups())

    @Test fun distinctDaysPartialAmountsAndQuotaRemovalRemainCoherent() {
        val state = Workspace().createHabit(weekly, monday, "weekly")
            .addCommit("weekly", 4.0, monday, id = "m1")
            .addCommit("weekly", 6.0, monday, id = "m2")
            .addCommit("weekly", 3.0, monday.plusDays(1), id = "t")
        val row = state.groups().single { it.isRemaining && it.habits.isNotEmpty() }.habits.single()
        assertEquals(1, row.weekly!!.quota!!.displayDays)
        assertFalse(row.daily.qualifies)
        val two = state.completeGoal("weekly", wednesday, "w")
        assertEquals(2, two.groups().flatMap { it.habits }.single().weekly!!.quota!!.displayDays)
        val met = two.completeGoal("weekly", monday.plusDays(1), "t2")
        assertEquals(TodayStatus.QUOTA_MET, status(met))
        assertTrue(met.groups().filter { it.isRemaining }.all { it.habits.isEmpty() })
        assertEquals(5, met.commits.size)
        val corrected = met.editCommit("m2", 1.0, "correction")
        assertEquals(TodayStatus.REMAINING, status(corrected))
        assertEquals(2, corrected.groups().flatMap { it.habits }.single().weekly!!.quota!!.displayDays)
        assertEquals(TodayStatus.REMAINING, status(met.removeCommit("w")))
    }

    @Test fun pendingConventionPreservesQuotaAndBridgeOnlyNeutralizesWeeklyWork() {
        val state = Workspace().createHabit(weekly, monday, "weekly")
            .createHabit(weekly.copy(schedule = HabitSchedule.Daily), monday, "daily")
            .createHabit(weekly.copy(schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))), monday, "selected")
            .addCommit("weekly", 10.0, monday, id = "m")
        val pending = state.requestWeekStart(DayOfWeek.SUNDAY, wednesday)
        assertEquals(state.groups(), pending.groups())
        val bridge = pending.groups(monday.plusWeeks(1))
        assertEquals(listOf("daily", "selected"), bridge.single { it.kind == TodayGroupKind.SCHEDULED }.habits.map { it.habitId })
        val practice = bridge.single { it.kind == TodayGroupKind.PARTIAL_WEEK_PRACTICE }.habits.single()
        assertNull(practice.weekly!!.quota)
        assertEquals(WeekPeriodKind.BRIDGE, practice.weekly.period.kind)
        assertFalse(bridge.single { it.kind == TodayGroupKind.PARTIAL_WEEK_PRACTICE }.isRemaining)
    }

    @Test fun emptyInactiveUnscheduledPracticeAndDueCompleteHaveDistinctStatuses() {
        assertEquals(TodayStatus.NO_REPOS, status(Workspace()))
        val state = Workspace().createHabit(weekly.copy(schedule = HabitSchedule.Daily), monday, "daily")
        assertEquals(TodayStatus.REMAINING, status(state))
        assertEquals(TodayStatus.NO_ACTIVE_REPOS, status(state.changeStatus("daily", RepoStatus.PAUSED, wednesday)))
        assertEquals(TodayStatus.DUE_COMPLETE, status(state.completeGoal("daily", wednesday)))
        assertEquals(TodayStatus.NO_SCHEDULE, status(Workspace().createHabit(weekly.copy(
            schedule = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))), monday, "selected")))
        val practice = Workspace().createHabit(weekly, wednesday, "weekly").completeGoal("weekly", wednesday)
        assertEquals(TodayStatus.NO_SCHEDULE, status(practice))
        assertNull(practice.groups().flatMap { it.habits }.single().weekly!!.quota)
    }
}
