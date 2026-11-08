package com.githabit.prototype.fixtures

import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.ZoneOffset

/** Small deterministic device fixture; written by guarded setup BEFORE Activity launch. */
internal object WorkspaceFixtures {
    const val READING_ID = "fixture-reading"

    fun populated(today: LocalDate): Workspace {
        val start = today.minusDays(14)
        var workspace = Workspace(onboarded = true, theme = ThemePreference.DARK)
            .createHabit(HabitDraft("Read", "Test-only reading history", 20.0, "pages"), start, READING_ID)
            .createHabit(HabitDraft("Practice", "Test-only practice history", 30.0, "minutes",
                HabitIcon.CODE, HabitColor.BLUE), start, "fixture-practice")
        for (day in daysEnding(today, 15)) {
            workspace = workspace.addCommit(READING_ID, if (day == today) 12.0 else 20.0, day,
                id = "fixture-reading-$day", timestamp = day.atTime(8, 30).toInstant(ZoneOffset.UTC))
        }
        return workspace
    }
}
