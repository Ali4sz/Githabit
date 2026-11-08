package com.githabit.prototype.domain

import java.time.Clock
import java.time.LocalDate
import kotlin.math.round

/** Deterministic JVM-only fixture. Never included in an installed app. */
object SampleWorkspace {
    fun create(clock: Clock = Clock.systemDefaultZone()): Workspace {
        val today = LocalDate.now(clock)
        val start = today.minusDays(167)
        val definitions = listOf(
            HabitDraft("Read every day", "A few pages. A new perspective.", 20.0, "pages", HabitIcon.BOOK, HabitColor.GREEN),
            HabitDraft("Move your body", "Make a little room for movement.", 30.0, "minutes", HabitIcon.MOVEMENT, HabitColor.ORANGE),
            HabitDraft("Deep work", "Less distraction. More intention.", 60.0, "minutes", HabitIcon.CODE, HabitColor.BLUE),
            HabitDraft("Stay hydrated", "A small sip in the right direction.", 8.0, "glasses", HabitIcon.WATER, HabitColor.TEAL),
            HabitDraft("A moment of calm", "Breathe in. Find your focus.", 10.0, "minutes", HabitIcon.CALM, HabitColor.PURPLE),
            HabitDraft("Daily journal", "Make sense of the everyday.", 1.0, "entry", HabitIcon.JOURNAL, HabitColor.PINK),
        )
        val todayAmounts = listOf(12.0, 30.0, 35.0, 4.0, 10.0, 1.0)
        val habits = definitions.mapIndexed { index, draft -> Habit("sample-$index", draft.name, draft.description,
            draft.unit, draft.icon, draft.color, start, listOf(GoalVersion(start, draft.target))) }
        val commits = daysEnding(today, 168).flatMapIndexed { dayIndex, day ->
            definitions.mapIndexedNotNull { index, draft ->
                val pattern = (dayIndex * 17 + index * 11) % 23
                val amount = when {
                    day == today -> todayAmounts[index]
                    dayIndex % 31 == 7 || pattern < 3 -> 0.0
                    pattern < 9 -> maxOf(1.0, round(draft.target * 0.5))
                    else -> draft.target
                }
                if (amount == 0.0) null else {
                    val timestamp = if (day == today) maxOf(day.atStartOfDay(clock.zone).toInstant(),
                        clock.instant().minusSeconds((6 - index) * 1800L))
                    else day.atTime(8 + index, 30).atZone(clock.zone).toInstant()
                    Commit("sample-$index-$day", "sample-$index", amount, day, timestamp,
                        if (day == today && index == 0) "A quiet chapter with my morning coffee." else "")
                }
            }
        }
        return Workspace(onboarded = true, habits = habits, commits = commits)
    }
}
