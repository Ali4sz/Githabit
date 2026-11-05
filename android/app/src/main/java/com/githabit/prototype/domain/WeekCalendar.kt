package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections

enum class WeekPeriodKind { FULL_WEEK, BRIDGE }

/** All period membership is half-open: [start, endExclusive). */
data class WeekPeriod(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val weekStart: DayOfWeek,
    val kind: WeekPeriodKind = WeekPeriodKind.FULL_WEEK,
) {
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    init {
        require(endExclusive > start && endExclusive.dayOfWeek == weekStart) { "Invalid week bounds." }
        require(if (kind == WeekPeriodKind.FULL_WEEK) dayCount == 7 else dayCount in 1..6) {
            "A full week has seven days; a calendar bridge has one to six days."
        }
    }

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}

/** The requested weekday becomes effective at boundary, not at the bridge's end. */
data class WeekTransition(val boundary: LocalDate, val weekStart: DayOfWeek) {
    val bridgeEnd: LocalDate get() = boundary.plusDays(((weekStart.value - boundary.dayOfWeek.value + 7) % 7).toLong())
}

data class CalendarStatus(
    val effectiveWeekStart: DayOfWeek,
    val period: WeekPeriod,
    val pending: WeekTransition?,
    /** Includes any queued bridge; without a change, this is the current full week's start. */
    val firstFullWeekStart: LocalDate,
)

/**
 * Workspace calendar history, separate from habit configurations. Completed/started
 * transitions are immutable; only [pending] can be replaced. Queries also honor a
 * pending boundary crossed while the app was closed, without needing a background job.
 */
class WeekCalendar(
    val initialWeekStart: DayOfWeek = DayOfWeek.MONDAY,
    transitions: List<WeekTransition> = emptyList(),
    val pending: WeekTransition? = null,
) {
    val transitions: List<WeekTransition> = Collections.unmodifiableList(transitions.toList())
    private val changes = this.transitions + listOfNotNull(pending)

    init {
        var weekday = initialWeekStart
        var earliestBoundary: LocalDate? = null
        for (change in changes) {
            require(change.weekStart != weekday) { "An unchanged weekday is not a calendar transition." }
            require(change.boundary.dayOfWeek == weekday &&
                (earliestBoundary == null || change.boundary >= earliestBoundary)) {
                "Calendar changes must follow ordered, nonoverlapping period boundaries."
            }
            weekday = change.weekStart
            earliestBoundary = change.bridgeEnd
        }
    }

    fun periodOn(date: LocalDate): WeekPeriod {
        val change = changeOn(date)
        val weekday = change?.weekStart ?: initialWeekStart
        if (change != null && date < change.bridgeEnd) {
            return WeekPeriod(change.boundary, change.bridgeEnd, weekday, WeekPeriodKind.BRIDGE)
        }
        val start = date.minusDays(((date.dayOfWeek.value - weekday.value + 7) % 7).toLong())
        return WeekPeriod(start, start.plusDays(7), weekday)
    }

    fun effectiveWeekStart(date: LocalDate): DayOfWeek = changeOn(date)?.weekStart ?: initialWeekStart

    fun status(today: LocalDate): CalendarStatus {
        val period = periodOn(today)
        val future = pending?.takeIf { it.boundary > today }
        val firstFullWeekStart = future?.bridgeEnd ?:
            if (period.kind == WeekPeriodKind.BRIDGE) period.endExclusive else period.start
        return CalendarStatus(effectiveWeekStart(today), period, future, firstFullWeekStart)
    }

    /** Persist the original boundary rather than shifting it to the next launch date. */
    fun advancedTo(today: LocalDate): WeekCalendar = if (pending != null && pending.boundary <= today)
        WeekCalendar(initialWeekStart, transitions + pending) else this

    fun request(weekday: DayOfWeek, today: LocalDate): WeekCalendar {
        val current = advancedTo(today)
        require(current.transitions.lastOrNull()?.boundary?.let { it <= today } != false) {
            "The calendar has a newer effective change. Check your device date before changing week start."
        }
        val effective = current.effectiveWeekStart(today)
        if (weekday == effective) {
            return if (current.pending == null) current else WeekCalendar(initialWeekStart, current.transitions)
        }
        // Revisions keep B. During an active bridge, B is that fixed bridge's end.
        val boundary = current.pending?.boundary ?: current.periodOn(today).endExclusive
        val requested = WeekTransition(boundary, weekday)
        return if (requested == current.pending) current
        else WeekCalendar(initialWeekStart, current.transitions, requested)
    }

    private fun changeOn(date: LocalDate): WeekTransition? {
        val index = changes.binarySearchBy(date) { it.boundary }
        return changes.getOrNull(if (index >= 0) index else -index - 2)
    }

    override fun equals(other: Any?): Boolean = other is WeekCalendar &&
        initialWeekStart == other.initialWeekStart && transitions == other.transitions && pending == other.pending
    override fun hashCode(): Int = 31 * (31 * initialWeekStart.hashCode() + transitions.hashCode()) + (pending?.hashCode() ?: 0)
    override fun toString(): String = "WeekCalendar(initialWeekStart=$initialWeekStart, transitions=$transitions, pending=$pending)"
}

/** Zero commits alone is not empty: habits and calendar history preserve dated expectations. */
fun Workspace.requestWeekStart(weekday: DayOfWeek, today: LocalDate): Workspace {
    val genuinelyEmpty = habits.isEmpty() && commits.isEmpty() && calendar.transitions.isEmpty() && calendar.pending == null
    val updated = if (genuinelyEmpty) WeekCalendar(weekday) else calendar.request(weekday, today)
    return if (updated == calendar) this else copy(calendar = updated)
}
