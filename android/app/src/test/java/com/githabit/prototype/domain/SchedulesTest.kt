package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Schedule validation, dated configuration semantics, and no-op edit rules (contract 1, 4). */
class SchedulesTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val draft = HabitDraft("Read", "", 20.0, "pages")

    @Test fun selectedWeekdaysRequireAtLeastOneDay() {
        assertThrows(IllegalArgumentException::class.java) {
            draft.copy(schedule = HabitSchedule.SelectedWeekdays(emptySet())).normalized()
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSchedule.SelectedWeekdays(emptySet()).validated()
        }
        val weekdays = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).validated()
        assertEquals(HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), weekdays)
    }

    @Test fun weeklyQuotaBoundsAreEnforcedAtBothLimits() {
        listOf(0, 8, -1).forEach {
            assertThrows(IllegalArgumentException::class.java) { HabitSchedule.WeeklyQuota(it).validated() }
        }
        assertEquals(HabitSchedule.WeeklyQuota(1), HabitSchedule.WeeklyQuota(1).validated())
        assertEquals(HabitSchedule.WeeklyQuota(7), HabitSchedule.WeeklyQuota(7).validated())
    }

    @Test fun dueAndQualifyingDatesFollowTheScheduleKind() {
        val saturday = LocalDate.of(2026, 11, 7)
        val weekdays = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertTrue(HabitSchedule.Daily.isDueOn(saturday))
        assertTrue(HabitSchedule.Daily.canQualifyOn(saturday))
        assertTrue(weekdays.isDueOn(LocalDate.of(2026, 11, 4)))
        assertFalse(weekdays.isDueOn(saturday))
        assertFalse(weekdays.canQualifyOn(saturday))
        val quota = HabitSchedule.WeeklyQuota(3)
        assertFalse(quota.isDueOn(saturday))
        assertTrue(quota.canQualifyOn(saturday))
    }

    @Test fun weekdayIdentityIsIndependentOfSetOrder() {
        assertEquals(HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)),
            HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
    }

    @Test fun configurationEqualityIgnoresOnlyEffectiveDate() {
        val base = GoalVersion(monday, 20.0, RepoStatus.ACTIVE, HabitSchedule.Daily)
        assertTrue(base.sameConfigurationAs(base.copy()))
        assertTrue(base.sameConfigurationAs(base.copy(date = monday.plusDays(2))))
        assertFalse(base.sameConfigurationAs(base.copy(target = 21.0)))
        assertFalse(base.sameConfigurationAs(base.copy(status = RepoStatus.PAUSED)))
        assertFalse(base.sameConfigurationAs(base.copy(schedule = HabitSchedule.WeeklyQuota(3))))
    }

    @Test fun createdHabitCarriesScheduleAndEditsChangeItOnlyFromToday() {
        val weekdays = HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val created = Workspace().createHabit(draft.copy(schedule = weekdays), monday, "read")
        assertEquals(weekdays, created.habits.single().versions.single().schedule)

        val edited = created.editHabit("read", draft.copy(target = 30.0, schedule = HabitSchedule.WeeklyQuota(2)),
            monday.plusDays(3))
        val habit = edited.habits.single()
        assertEquals(weekdays, habit.versionOn(monday.plusDays(2))!!.schedule)
        assertEquals(HabitSchedule.WeeklyQuota(2), habit.versionOn(monday.plusDays(3))!!.schedule)
        assertEquals(2, habit.versions.size)
    }

    @Test fun redundantSavesChangeNothing() {
        val created = Workspace().createHabit(draft, monday, "read")
        assertSame(created, created.editHabit("read", draft, monday.plusDays(5)))
        assertSame(created, created.changeStatus("read", RepoStatus.ACTIVE, monday.plusDays(5)))
        assertThrows(IllegalArgumentException::class.java) {
            created.editHabit("read", draft.copy(schedule = HabitSchedule.WeeklyQuota(0)), monday)
        }
    }

    @Test fun sameDayUpdatesPreserveUnrelatedFields() {
        val weeklyDraft = draft.copy(schedule = HabitSchedule.WeeklyQuota(3))
        val created = Workspace().createHabit(weeklyDraft, monday, "read")
            .editHabit("read", weeklyDraft.copy(target = 30.0), monday)
        val sameDayStatus = created.changeStatus("read", RepoStatus.PAUSED, monday)
        val version = sameDayStatus.habits.single().versions.single()
        assertEquals(30.0, version.target, 0.0)
        assertEquals(RepoStatus.PAUSED, version.status)
        assertEquals(weeklyDraft.schedule, version.schedule)
        assertEquals(1, sameDayStatus.habits.single().versions.size)

        val sameDayTarget = sameDayStatus.editHabit("read", weeklyDraft.copy(target = 12.0), monday)
        val resumed = sameDayTarget.habits.single().versions.single()
        assertEquals(12.0, resumed.target, 0.0)
        assertEquals(RepoStatus.PAUSED, resumed.status)
        assertEquals(weeklyDraft.schedule, resumed.schedule)
        assertEquals(1, sameDayTarget.habits.single().versions.size)
    }

    @Test fun presentationalEditKeepsVersionsUntouched() {
        val created = Workspace().createHabit(draft, monday, "read")
        val renamed = created.editHabit("read", draft.copy(name = "Journal", icon = HabitIcon.JOURNAL,
            color = HabitColor.PINK, description = "why"), monday.plusDays(2))
        val habit = renamed.habits.single()
        assertEquals("Journal", habit.name)
        assertEquals(listOf(GoalVersion(monday, 20.0)), habit.versions)
    }

    @Test fun weekdaySelectionsCannotMutateHistoricalExpectations() {
        val input = mutableSetOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        val schedule = HabitSchedule.SelectedWeekdays(input)
        val workspace = Workspace().createHabit(draft.copy(schedule = schedule), monday, "read")
        input.clear()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), schedule.days)
        assertEquals(schedule, workspace.habits.single().versions.single().schedule)
        assertThrows(UnsupportedOperationException::class.java) {
            (schedule.days as MutableSet).clear()
        }
    }

    @Test fun unsortedHistoryUsesLatestEffectiveVersionAndPreservesEarlierDates() {
        val created = Workspace().createHabit(draft, monday, "read")
        val old = created.habits.single().versions.single()
        val newer = old.copy(date = monday.plusDays(2), schedule = HabitSchedule.WeeklyQuota(2))
        val habit = created.habits.single().copy(versions = listOf(newer, old))
        assertNull(habit.versionOn(monday.minusDays(1)))
        assertEquals(old, habit.versionOn(monday.plusDays(1)))
        assertEquals(newer, habit.versionOn(monday.plusDays(3)))
        val edited = created.copy(habits = listOf(habit)).changeStatus("read", RepoStatus.PAUSED, monday.plusDays(3))
        assertEquals(listOf(newer, old), edited.habits.single().versions.take(2))
        assertEquals(newer.schedule, edited.habits.single().versionOn(monday.plusDays(3))!!.schedule)
    }

    @Test fun sameDayReversionRestoresEffectiveConfiguration() {
        val weeklyDraft = draft.copy(schedule = HabitSchedule.WeeklyQuota(3))
        val created = Workspace().createHabit(weeklyDraft, monday, "read")
        val day = monday.plusDays(2)
        val reverted = created.editHabit("read", weeklyDraft.copy(target = 10.0), day)
            .changeStatus("read", RepoStatus.PAUSED, day)
            .editHabit("read", weeklyDraft, day)
            .changeStatus("read", RepoStatus.ACTIVE, day)
        val habit = reverted.habits.single()
        assertTrue(habit.versionOn(monday)!!.sameConfigurationAs(habit.versionOn(day)!!))
        assertEquals(1, habit.versions.count { it.date == day })
    }

    @Test fun clockRollbackBlocksConflictingConfigurationChangesNotCosmeticEdits() {
        val created = Workspace().createHabit(draft, monday, "read")
        val later = created.editHabit("read", draft.copy(target = 30.0), monday.plusDays(3))
        val earlier = monday.plusDays(1)
        assertThrows(IllegalArgumentException::class.java) {
            later.editHabit("read", draft.copy(target = 25.0), earlier)
        }
        assertThrows(IllegalArgumentException::class.java) {
            later.changeStatus("read", RepoStatus.PAUSED, earlier)
        }
        assertSame(later, later.changeStatus("read", RepoStatus.ACTIVE, earlier))
        val renamed = later.editHabit("read", draft.copy(name = "Renamed"), earlier)
        assertEquals(later.habits.single().versions, renamed.habits.single().versions)
    }
}
