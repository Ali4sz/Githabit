package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HeatmapQueriesTest {
    private val monday = LocalDate.parse("2026-11-02")
    private fun workspace() = Workspace().createHabit(HabitDraft("Repo", "", 10.0, "units", schedule = HabitSchedule.WeeklyQuota(3)), monday, "r")

    @Test fun activityIsCommitCountWhileRepoIntensityIsNumericProgressNotAdherence() {
        val state = workspace().addCommit("r", 5.0, monday, "", "a").addCommit("r", 5.0, monday, "", "b")
            .addCommit("r", 2.0, monday.plusDays(1), "", "c")
        val index = ProgressAnalytics(state, monday.plusDays(2))
        val overview = index.heatmap(13).last().dates
        val repo = index.heatmap(13, "r").last().dates
        assertEquals(2, overview[0].level)
        assertEquals(4, repo[0].level)
        assertEquals(1, repo[1].level)
        assertEquals(DayAssessment.NOT_APPLICABLE, repo[2].daily!!.assessment)
        assertEquals(0, repo[2].level)
        assertEquals(WeekAssessment.PENDING, index.weekly("r", monday).assessment)
    }

    @Test fun bridgeColumnContainsOnlyItsActualDatesAndHistoricalBoundsSurvive() {
        val state = workspace().requestWeekStart(DayOfWeek.SUNDAY, monday)
        val index = ProgressAnalytics(state, monday.plusDays(14))
        val columns = index.heatmap(13)
        val bridge = columns.single { it.period.kind == WeekPeriodKind.BRIDGE }
        assertEquals(6, bridge.dates.size)
        assertEquals(monday.plusDays(7), bridge.period.start)
        val dates = columns.flatMap { it.dates }.map { it.date }
        assertEquals(dates.size, dates.distinct().size)
        assertEquals(7, columns[columns.indexOf(bridge) - 1].dates.size)
        assertEquals(monday, columns[columns.indexOf(bridge) - 1].period.start)
    }

    @Test fun emptyChartsHaveNoPositiveCellsAndFutureRecordsNeverGetEarlyIntensity() {
        assertTrue(ProgressAnalytics(Workspace(), monday).heatmap(52).flatMap { it.dates }.all { it.level == 0 })
        val index = ProgressAnalytics(workspace().addCommit("r", 10.0, monday.plusDays(2), "", "future"), monday)
        assertEquals(0, index.heatmap(13).last().dates[2].level)
        assertEquals(0, index.heatmap(13, "r").last().dates[2].level)
        assertEquals(1, index.commits.activityCount(monday.plusDays(2)))
    }
}
