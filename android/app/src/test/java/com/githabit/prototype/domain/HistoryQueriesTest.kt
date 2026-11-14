package com.githabit.prototype.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoryQueriesTest {
    private val today = LocalDate.parse("2026-11-14")
    @Test fun storedDatesSelectWhileTimestampsOrderAndTiesStayStable() {
        val a = Commit(id = "a", habitId = "repo", date = today.minusDays(1), amount = 1.0,
            timestamp = Instant.parse("2026-11-14T10:00:00Z"), note = "")
        val b = a.copy(id = "b", date = today.plusDays(10))
        val c = a.copy(id = "c", habitId = "other", timestamp = a.timestamp.plusSeconds(1))
        val index = CommitIndex(listOf(a, b, c))
        assertEquals(listOf(c, a, b), index.history())
        assertEquals(listOf(a, b), index.history("repo"))
        assertEquals(listOf(b), index.history("repo", b.date))
        assertEquals(listOf(a, c), index.history(date = a.date).sortedBy { it.id })
        assertEquals(1, index.activityCount(b.date, "repo"))
        assertEquals(a.date to b.date, index.storedDateBounds("repo"))
    }

    @Test fun inspectionIncludesFutureRecordsWithoutEarlyGoalCreditAndEmptyIsTodayOnly() {
        val empty = ProgressAnalytics(Workspace(), today)
        assertEquals(today to today, empty.inspectionBounds())
        val created = today.minusDays(30)
        val workspace = Workspace().createHabit(HabitDraft("Repo", "", 1.0, "units"), created, "repo")
            .addCommit("repo", 1.0, today.plusDays(10), "", "future")
        val index = ProgressAnalytics(workspace, today)
        assertEquals(created to today.plusDays(10), index.inspectionBounds())
        assertFalse(index.daily("repo", today.plusDays(10)).qualifies)
        assertEquals(DayAssessment.PENDING, index.daily("repo", today.plusDays(10)).assessment)
    }
}
