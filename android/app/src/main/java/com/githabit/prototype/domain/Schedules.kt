package com.githabit.prototype.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Collections

/**
 * Schedule of obligation for a dated habit configuration (contract 1). Schedules live on
 * every [GoalVersion], so historical days keep the schedule effective on that date.
 * Evaluation is pure: no Compose, storage, or clock access here.
 */
sealed interface HabitSchedule {
    /** A target is due every active day. */
    data object Daily : HabitSchedule

    /**
     * A target is due only on the selected weekdays. The set must be nonempty; weekday
     * identity is independent of any app-wide week-start convention or display order.
     */
    class SelectedWeekdays(days: Set<DayOfWeek>) : HabitSchedule {
        // A caller's mutable selection must never be able to rewrite dated expectations.
        val days: Set<DayOfWeek> = Collections.unmodifiableSet(days.toSet())

        override fun equals(other: Any?): Boolean = other is SelectedWeekdays && days == other.days
        override fun hashCode(): Int = days.hashCode()
        override fun toString(): String = "SelectedWeekdays(days=$days)"
    }

    /**
     * Qualifying on [daysPerWeek] distinct dates per calendar week meets the goal.
     * Any active date can qualify when its accumulated progress reaches that date's
     * target; each date contributes at most one qualifying day (contract 3).
     */
    data class WeeklyQuota(val daysPerWeek: Int) : HabitSchedule

    /** Revalidates invariants; used by drafts now and by strict decoding in format v2. */
    fun validated(): HabitSchedule = when (this) {
        is Daily -> this
        is SelectedWeekdays -> if (days.isNotEmpty()) this
        else throw IllegalArgumentException("Select at least one weekday.")
        is WeeklyQuota -> if (daysPerWeek in 1..7) this
        else throw IllegalArgumentException("Set a weekly goal of 1 to 7 days.")
    }

    /** Whether this schedule imposes a due target on the given date. Weekly quotas never do. */
    fun isDueOn(date: LocalDate): Boolean = when (this) {
        is Daily -> true
        is SelectedWeekdays -> date.dayOfWeek in days
        is WeeklyQuota -> false
    }

    /**
     * Whether progress on an active date counts toward this schedule's goal: due days for
     * daily/weekday schedules, any date for a weekly quota (contract 2 and 3).
     */
    fun canQualifyOn(date: LocalDate): Boolean = when (this) {
        is WeeklyQuota -> true
        else -> isDueOn(date)
    }
}

/** Whether two dated configurations assess the same for scheduling purposes (contract 4). */
fun GoalVersion.sameConfigurationAs(other: GoalVersion): Boolean =
    target == other.target && status == other.status && schedule == other.schedule
