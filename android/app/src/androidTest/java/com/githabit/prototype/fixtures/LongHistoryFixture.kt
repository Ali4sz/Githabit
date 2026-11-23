package com.githabit.prototype.fixtures

import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/** Synthetic QA history only. No app-owned generator, seed command or bundled payload. */
internal object LongHistoryFixture {
    val today: LocalDate = LocalDate.parse("2026-11-04")
    const val FIRST_ID = "long-repo-0"

    fun create(): Workspace {
        val start = today.minusYears(5)
        val habits = List(20) { i ->
            val schedule = when (i % 3) {
                0 -> HabitSchedule.Daily
                1 -> HabitSchedule.SelectedWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                else -> HabitSchedule.WeeklyQuota(3)
            }
            Habit("long-repo-$i", "QA repo $i", "Synthetic five-year history", "units", HabitIcon.CODE,
                HabitColor.entries[i % HabitColor.entries.size], start,
                listOf(GoalVersion(start, 2.0, schedule = schedule), GoalVersion(start.plusYears(3), 2.5, schedule = schedule)))
        }
        val commits = ArrayList<Commit>()
        var day = start
        var offset = 0
        while (day < today) {
            habits.forEachIndexed { i, habit ->
                commits += Commit("long-$i-$offset", habit.id, if ((i + offset) % 4 == 0) 1.25 else 2.5,
                    day, day.atTime(12, i).toInstant(ZoneOffset.UTC), "Synthetic 🌱 progress")
            }
            day = day.plusDays(1)
            offset++
        }
        return Workspace(onboarded = true, habits = habits, commits = commits)
            .requestWeekStart(DayOfWeek.SUNDAY, today)
    }
}
