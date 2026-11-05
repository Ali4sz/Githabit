package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeekCalendarTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test fun all49WeekdayPairsPreserveTheCurrentWeekAndCoverEveryDateExactlyOnce() {
        for (old in DayOfWeek.entries) for (new in DayOfWeek.entries) {
            val calendar = WeekCalendar(old)
            val original = calendar.periodOn(monday)
            val requested = calendar.request(new, original.start.plusDays(3))
            assertEquals("$old → $new", original, requested.periodOn(original.start.plusDays(6)))
            if (old == new) {
                assertSame(calendar, requested)
                assertNull(requested.pending)
            } else {
                val pending = requested.pending!!
                assertEquals(original.endExclusive, pending.boundary)
                val bridge = requested.periodOn(pending.boundary)
                assertEquals(WeekPeriodKind.BRIDGE, bridge.kind)
                assertTrue(bridge.dayCount in 1..6)
                assertEquals(new, requested.effectiveWeekStart(bridge.start))
                assertEquals(new, bridge.endExclusive.dayOfWeek)
                val next = requested.periodOn(bridge.endExclusive)
                assertEquals(WeekPeriodKind.FULL_WEEK, next.kind)
                assertEquals(7, next.dayCount)
                assertEquals(new, next.start.dayOfWeek)
            }
            val dates = (0L..49L).map { original.start.minusDays(14).plusDays(it) }
            val periods = dates.map { requested.periodOn(it) }.distinct().sortedBy { it.start }
            periods.zipWithNext().forEach { (a, b) -> assertEquals(a.endExclusive, b.start) }
            dates.forEach { date -> assertEquals(1, periods.count { date in it }) }
        }
    }

    @Test fun mondayToSundayCreatesSixDayBridgeAndSundayToMondayCreatesOneDayBridge() {
        val six = WeekCalendar().request(DayOfWeek.SUNDAY, monday).periodOn(monday.plusDays(7))
        assertEquals(WeekPeriod(monday.plusDays(7), monday.plusDays(13), DayOfWeek.SUNDAY, WeekPeriodKind.BRIDGE), six)
        val one = WeekCalendar(DayOfWeek.SUNDAY).request(DayOfWeek.MONDAY, monday).periodOn(monday.plusDays(6))
        assertEquals(WeekPeriod(monday.plusDays(6), monday.plusDays(7), DayOfWeek.MONDAY, WeekPeriodKind.BRIDGE), one)
    }

    @Test fun pendingReplacementAndCancellationNeverMoveBoundary() {
        val initial = WeekCalendar()
        val sunday = initial.request(DayOfWeek.SUNDAY, monday.plusDays(1))
        val friday = sunday.request(DayOfWeek.FRIDAY, monday.plusDays(5))
        assertEquals(sunday.pending!!.boundary, friday.pending!!.boundary)
        assertEquals(DayOfWeek.FRIDAY, friday.pending.weekStart)
        assertSame(friday, friday.request(DayOfWeek.FRIDAY, monday.plusDays(6)))
        assertEquals(initial, friday.request(DayOfWeek.MONDAY, monday.plusDays(6)))
        assertEquals(monday.plusDays(11), friday.status(monday.plusDays(6)).firstFullWeekStart)
        assertEquals(DayOfWeek.MONDAY, friday.status(monday.plusDays(6)).effectiveWeekStart)
    }

    @Test fun activeBridgeDestinationControlsNoOpAndQueuedCancellation() {
        val calendar = WeekCalendar().request(DayOfWeek.SUNDAY, monday)
        val during = monday.plusDays(9)
        val fixed = calendar.periodOn(during)
        val queued = calendar.request(DayOfWeek.MONDAY, during)
        assertEquals(listOf(calendar.pending), queued.transitions)
        assertEquals(fixed, queued.periodOn(during))
        assertEquals(DayOfWeek.SUNDAY, queued.status(during).effectiveWeekStart)
        assertEquals(fixed.endExclusive, queued.pending!!.boundary)
        assertEquals(monday.plusDays(14), queued.status(during).firstFullWeekStart)
        val cancelled = queued.request(DayOfWeek.SUNDAY, during.plusDays(1))
        assertNull(cancelled.pending)
        assertEquals(queued.transitions, cancelled.transitions)
        assertEquals(fixed, cancelled.periodOn(during))
        assertEquals(fixed.endExclusive, cancelled.status(during).firstFullWeekStart)
        assertSame(cancelled, cancelled.request(DayOfWeek.SUNDAY, during.plusDays(1)))
    }

    @Test fun queuedReplacementKeepsActiveBridgeFixedAndChainsAnotherAtItsEnd() {
        val first = WeekCalendar().request(DayOfWeek.SUNDAY, monday)
        val during = monday.plusDays(8)
        val firstBridge = first.periodOn(during)
        val queued = first.request(DayOfWeek.MONDAY, during).request(DayOfWeek.WEDNESDAY, during.plusDays(1))
        assertEquals(firstBridge, queued.periodOn(during.plusDays(2)))
        val second = queued.periodOn(firstBridge.endExclusive)
        assertEquals(WeekPeriodKind.BRIDGE, second.kind)
        assertEquals(3, second.dayCount)
        assertEquals(DayOfWeek.WEDNESDAY, queued.effectiveWeekStart(second.start))
        assertEquals(second.endExclusive, queued.status(during).firstFullWeekStart)
        val full = queued.periodOn(second.endExclusive)
        assertEquals(WeekPeriodKind.FULL_WEEK, full.kind)
        assertEquals(DayOfWeek.WEDNESDAY, full.start.dayOfWeek)
    }

    @Test fun reopeningAfterBoundariesResolvesOriginalDatesWithoutPostponement() {
        val calendar = WeekCalendar().request(DayOfWeek.SUNDAY, monday)
        val later = monday.plusDays(100)
        val advanced = calendar.advancedTo(later)
        assertNull(advanced.pending)
        assertEquals(listOf(calendar.pending), advanced.transitions)
        for (days in -14L..120L) {
            val date = monday.plusDays(days)
            assertEquals(calendar.periodOn(date), advanced.periodOn(date))
        }
        assertSame(advanced, advanced.advancedTo(later))
        assertNull(calendar.status(later).pending)
        val followUp = calendar.request(DayOfWeek.THURSDAY, later)
        assertEquals(advanced.transitions, followUp.transitions)
        assertEquals(advanced.periodOn(later).endExclusive, followUp.pending!!.boundary)
    }

    @Test fun repeatedChangesAndChainedBridgesNeverRegroupElapsedDates() {
        var calendar = WeekCalendar()
        var today = monday
        repeat(84) { step ->
            today = today.plusDays((step % 5).toLong())
            val elapsed = generateSequence(monday.minusDays(14)) { it.plusDays(1) }
                .takeWhile { it <= today }.associateWith { calendar.periodOn(it) }
            calendar = calendar.request(DayOfWeek.entries[(step * 3) % 7], today)
            elapsed.forEach { (date, period) -> assertEquals("step=$step date=$date", period, calendar.periodOn(date)) }
            val periods = elapsed.keys.map { calendar.periodOn(it) }.distinct().sortedBy { it.start }
            periods.zipWithNext().forEach { (a, b) -> assertEquals(a.endExclusive, b.start) }
        }
        assertTrue(calendar.transitions.size > 7)
    }

    @Test fun savingExactlyAtBoundaryCannotCancelOrRedrawAStartedBridge() {
        val calendar = WeekCalendar().request(DayOfWeek.SUNDAY, monday)
        val boundary = calendar.pending!!.boundary
        val returning = calendar.request(DayOfWeek.MONDAY, boundary)
        assertEquals(calendar.periodOn(boundary), returning.periodOn(boundary))
        assertEquals(calendar.pending.bridgeEnd, returning.pending!!.boundary)
        assertEquals(1, returning.transitions.size)
    }

    @Test fun emptyWorkspaceChangesImmediatelyButHabitWithZeroCommitsDoesNot() {
        val empty = Workspace()
        assertEquals(DayOfWeek.MONDAY, empty.calendar.initialWeekStart)
        assertSame(empty, empty.requestWeekStart(DayOfWeek.MONDAY, monday))
        val immediate = empty.requestWeekStart(DayOfWeek.FRIDAY, monday)
        assertEquals(DayOfWeek.FRIDAY, immediate.calendar.initialWeekStart)
        assertNull(immediate.calendar.pending)
        val withHabit = empty.createHabit(HabitDraft("Read", "", 20.0, "pages"), monday.minusDays(10), "read")
        val changed = withHabit.requestWeekStart(DayOfWeek.FRIDAY, monday)
        assertEquals(DayOfWeek.MONDAY, changed.calendar.effectiveWeekStart(monday))
        assertNotNull(changed.calendar.pending)
        assertEquals(withHabit.habits, changed.habits)
        assertEquals(withHabit.commits, changed.commits)
    }

    @Test fun calendarHistoryAndActivityAlsoDisqualifyImmediateReplacement() {
        val calendar = WeekCalendar().request(DayOfWeek.SUNDAY, monday).advancedTo(monday.plusDays(8))
        val state = Workspace(calendar = calendar)
        val changed = state.requestWeekStart(DayOfWeek.THURSDAY, monday.plusDays(9))
        assertEquals(calendar.transitions, changed.calendar.transitions)
        assertNotNull(changed.calendar.pending)
        val activityOnly = Workspace(commits = listOf(Commit("log", "legacy", 1.0, monday)))
        assertNotNull(activityOnly.requestWeekStart(DayOfWeek.FRIDAY, monday).calendar.pending)
    }

    @Test fun calendarWeeksCrossYearLeapDayAndDstUsingLocalDates() {
        listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2024, 2, 29), LocalDate.of(2026, 11, 1)).forEach { date ->
            for (weekday in DayOfWeek.entries) {
                val period = WeekCalendar(weekday).periodOn(date)
                assertTrue(date in period)
                assertEquals(7, period.dayCount)
                assertEquals(weekday, period.start.dayOfWeek)
            }
        }
        assertEquals(LocalDate.of(2025, 12, 29), WeekCalendar().periodOn(LocalDate.of(2026, 1, 1)).start)
    }

    @Test fun malformedCalendarHistoryIsRejectedInsteadOfRepaired() {
        fun reject(transitions: List<WeekTransition>, pending: WeekTransition? = null) {
            assertThrows(IllegalArgumentException::class.java) { WeekCalendar(transitions = transitions, pending = pending) }
        }
        reject(listOf(WeekTransition(monday.plusDays(7), DayOfWeek.MONDAY)))
        reject(listOf(WeekTransition(monday.plusDays(8), DayOfWeek.SUNDAY)))
        val first = WeekTransition(monday.plusDays(7), DayOfWeek.SUNDAY)
        reject(listOf(first, WeekTransition(monday.plusDays(6), DayOfWeek.FRIDAY)))
        reject(listOf(first, first))
        reject(listOf(first), WeekTransition(monday.plusDays(8), DayOfWeek.FRIDAY))
        assertThrows(IllegalArgumentException::class.java) {
            WeekPeriod(monday, monday.plusDays(7), DayOfWeek.MONDAY, WeekPeriodKind.BRIDGE)
        }
    }

    @Test fun calendarSnapshotsDoNotRetainMutableHistory() {
        val history = mutableListOf(WeekTransition(monday.plusDays(7), DayOfWeek.SUNDAY))
        val calendar = WeekCalendar(transitions = history)
        history.clear()
        assertEquals(1, calendar.transitions.size)
        assertThrows(UnsupportedOperationException::class.java) { (calendar.transitions as MutableList).clear() }
    }

    @Test fun rollbackCannotRewriteLockedCalendarHistoryButFuturePendingDoesNotBlockHabitEdits() {
        val locked = WeekCalendar().request(DayOfWeek.SUNDAY, monday).advancedTo(monday.plusDays(8))
        assertThrows(IllegalArgumentException::class.java) { locked.request(DayOfWeek.FRIDAY, monday) }
        val draft = HabitDraft("Read", "", 20.0, "pages")
        val pending = Workspace().createHabit(draft, monday, "read").requestWeekStart(DayOfWeek.SUNDAY, monday)
        val edited = pending.editHabit("read", draft.copy(target = 30.0), monday.plusDays(1))
            .addCommit("read", 5.0, monday.plusDays(1))
        assertEquals(pending.calendar, edited.calendar)
        assertEquals(1, edited.commits.size)
    }
}
