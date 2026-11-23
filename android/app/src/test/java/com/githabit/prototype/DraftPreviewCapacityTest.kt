package com.githabit.prototype

import com.githabit.prototype.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DraftPreviewCapacityTest {
    private val date = LocalDate.parse("2026-11-04")
    private val draft = HabitDraft("Practice", "", 1.0, "units", schedule = HabitSchedule.WeeklyQuota(3))
    // A preview may not enumerate history merely to explain a configuration transition.
    private val unreadableHistory = object : AbstractList<Commit>() {
        override val size = 100_000
        override fun get(index: Int): Commit = error("Draft preview scanned commit $index")
    }

    @Test fun creationAndTargetEditDoNotReadCommitHistory() {
        val state = Workspace(commits = unreadableHistory)
        assertTrue(state.proposedWeeklyTransition(null, draft, date))
        val existing = Workspace().createHabit(draft, date.minusDays(2), "repo").copy(commits = unreadableHistory)
        assertTrue(existing.proposedWeeklyTransition("repo", draft.copy(target = 2.0), date))
        assertFalse(existing.proposedWeeklyTransition("repo", draft.copy(description = "cosmetic"), date))
    }

    @Test fun revertedSameDayConfigurationRetainsStableWeekWithoutHistoryScan() {
        val monday = date.minusDays(2)
        val state = Workspace().createHabit(draft, monday, "repo")
            .editHabit("repo", draft.copy(target = 2.0), date).copy(commits = unreadableHistory)
        assertFalse(state.proposedWeeklyTransition("repo", draft, date))
    }
}
