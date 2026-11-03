package com.githabit.prototype.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HabitsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val draft = HabitDraft("Read", "Read a little", 20.0, "pages")
    private fun workspace() = Workspace().createHabit(draft, today.minusDays(1), "read")

    @Test fun partialCommitsAccumulateAndCompletionAddsOnlyRemainder() {
        var state = workspace().addCommit("read", 5.0, today).addCommit("read", 7.0, today)
        assertEquals(12.0, state.progress(state.habits.first(), today).total, 0.0)
        state = state.completeGoal("read", today)
        assertEquals(8.0, state.commits.last().amount, 0.0)
        assertTrue(state.progress(state.habits.first(), today).complete)
        assertSame(state, state.completeGoal("read", today))
    }

    @Test fun overTargetAmountsAreRetainedButScoresAreCapped() {
        val state = workspace().addCommit("read", 35.0, today)
        assertEquals(35.0, state.progress(state.habits.first(), today).total, 0.0)
        assertEquals(1.0, state.dailyScore(today)!!, 0.0)
    }

    @Test fun validatesAmountsNamesAndUnits() {
        listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 1_000_001.0, 0.0001).forEach {
            assertThrows(IllegalArgumentException::class.java) { workspace().addCommit("read", it, today) }
        }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(name = " ").normalized() }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(unit = "").normalized() }
    }

    @Test fun fractionalGoalsDoNotSufferFloatingPointErrors() {
        val state = Workspace().createHabit(draft.copy(target = 0.3), today, "read")
            .addCommit("read", 0.1, today).addCommit("read", 0.2, today)
        assertEquals(0.3, state.progress(state.habits.first(), today).total, 0.0)
        assertSame(state, state.completeGoal("read", today))
    }

    @Test fun overallScoreAveragesPercentagesAndUnscheduledDaysAreNull() {
        val state = workspace().createHabit(draft.copy(name = "Move", target = 60.0, unit = "minutes"), today, "move")
            .addCommit("read", 20.0, today)
        assertEquals(0.5, state.dailyScore(today)!!, 0.0)
        assertNull(state.dailyScore(today.minusDays(2)))
    }

    @Test fun goalEditsPreserveHistoricalTargets() {
        val state = workspace().addCommit("read", 20.0, today.minusDays(1))
            .editHabit("read", draft.copy(target = 40.0), today)
        assertEquals(1.0, state.dailyScore(today.minusDays(1))!!, 0.0)
        assertEquals(40.0, state.progress(state.habits.first(), today).target, 0.0)
        assertThrows(IllegalArgumentException::class.java) { state.editHabit("read", draft.copy(unit = "books"), today) }
    }

    @Test fun pausedAndArchivedHabitsKeepHistoryAndCanResume() {
        val state = workspace().addCommit("read", 20.0, today.minusDays(1))
            .changeStatus("read", RepoStatus.PAUSED, today)
        assertNull(state.dailyScore(today))
        assertEquals(1.0, state.dailyScore(today.minusDays(1))!!, 0.0)
        assertThrows(IllegalArgumentException::class.java) { state.addCommit("read", 5.0, today) }
        val archived = state.changeStatus("read", RepoStatus.ARCHIVED, today)
        assertEquals(1, archived.commits.size)
        assertEquals(0.0, archived.changeStatus("read", RepoStatus.ACTIVE, today).dailyScore(today)!!, 0.0)
    }

    @Test fun correctionsAndRemovalRecomputeAllMetrics() {
        val state = workspace().addCommit("read", 20.0, today, id = "entry")
            .editCommit("entry", 5.0, "Corrected")
        assertEquals(0.25, state.dailyScore(today)!!, 0.0)
        assertEquals(0.0, state.removeCommit("entry").dailyScore(today)!!, 0.0)
    }

    @Test fun streakAllowsAnUnfinishedTodayButNotAMissedYesterday() {
        val state = workspace().addCommit("read", 1.0, today.minusDays(1))
        assertEquals(1, state.activityStreak(today))
        assertEquals(0, state.activityStreak(today.plusDays(1)))
        assertEquals(1, state.longestStreak(today))
    }

    @Test fun calendarDaysCrossDstAndLeapDays() {
        assertEquals(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 28).plusDays(1))
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), daysEnding(today, 3))
        val clock = Clock.fixed(Instant.parse("2026-11-02T01:00:00Z"), ZoneId.of("America/New_York"))
        val state = SampleWorkspace.create(clock)
        assertEquals(LocalDate.of(2026, 11, 1), state.commits.maxOf { it.date })
    }

    @Test fun sampleDataIsDeterministicAndTodayHasThreeCompletedGoals() {
        val clock = Clock.fixed(Instant.parse("2026-11-02T16:00:00Z"), ZoneId.of("UTC"))
        val state = SampleWorkspace.create(clock)
        assertEquals(state, SampleWorkspace.create(clock))
        assertEquals(6, state.habits.size)
        assertEquals(3, state.habits.count { state.progress(it, today).complete })
        assertTrue(state.commits.all { it.timestamp <= clock.instant() })
    }

    @Test fun mutationsReturnNewStateAndDuplicateCommitsAreRejected() {
        val original = workspace()
        val updated = original.addCommit("read", 5.0, today, id = "entry")
        assertTrue(original.commits.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { updated.addCommit("read", 5.0, today, id = "entry") }
    }
}
