package com.githabit.prototype.domain

import java.time.LocalDate

enum class WeekAssessment { NOT_APPLICABLE, PENDING, ACHIEVED, MISSED, TRANSITIONAL }
enum class WeekTransitionReason { CALENDAR_BRIDGE, CREATED_DURING_PERIOD, CONFIGURATION_CHANGED }

data class WeeklyGoal(val daysPerWeek: Int, val dailyTarget: Double)

/** Available only when one active weekly configuration applies to the entire full week. */
data class WeeklyQuotaProgress(val qualifyingDays: Int, val goal: WeeklyGoal) {
    val displayDays: Int get() = qualifyingDays.coerceAtMost(goal.daysPerWeek)
    val remainingDays: Int get() = (goal.daysPerWeek - qualifyingDays).coerceAtLeast(0)
}

data class WeeklyProgress(
    val habitId: String,
    val period: WeekPeriod,
    val assessment: WeekAssessment,
    val days: List<DailyProgress>,
    /** Weekly-configured, active completed dates only. Extra dates are retained, never carried. */
    val qualifyingDates: List<LocalDate>,
    val quota: WeeklyQuotaProgress?,
    /** Context for practice, not an authoritative x/N score for a transitional period. */
    val endingWeeklyGoal: WeeklyGoal?,
    val transitionReasons: Set<WeekTransitionReason>,
)

data class DayAdherence(val achievedDates: Int, val missedDates: Int) {
    val assessedDates: Int get() = achievedDates + missedDates
    val rate: Double? get() = if (assessedDates == 0) null else achievedDates.toDouble() / assessedDates
}

data class WeekAdherence(val achievedWeeks: Int, val missedWeeks: Int) {
    val assessedWeeks: Int get() = achievedWeeks + missedWeeks
    val rate: Double? get() = if (assessedWeeks == 0) null else achievedWeeks.toDouble() / assessedWeeks
}

data class AdherenceSummary(val days: DayAdherence, val weeks: WeekAdherence)

data class DateSummary(
    val date: LocalDate,
    val period: WeekPeriod,
    val activityCount: Int,
    val habits: List<DailyProgress>,
)

data class WeekSummary(
    val period: WeekPeriod,
    val activityCount: Int,
    val dates: List<DateSummary>,
    val habits: List<WeeklyProgress>,
)

enum class TodayGroupKind { SCHEDULED, WEEKLY_REMAINING, PARTIAL_WEEK_PRACTICE, COMPLETED_OR_OPTIONAL }
data class TodayHabit(val habitId: String, val daily: DailyProgress, val weekly: WeeklyProgress?)
data class TodayGroup(val kind: TodayGroupKind, val habits: List<TodayHabit>) {
    val isRemaining: Boolean get() = kind == TodayGroupKind.SCHEDULED || kind == TodayGroupKind.WEEKLY_REMAINING
}

fun ProgressAnalytics.weekly(habitId: String, date: LocalDate): WeeklyProgress {
    val period = calendar.periodOn(date)
    val days = (0 until period.dayCount).map { daily(habitId, period.start.plusDays(it.toLong())) }
    val weeklyDays = days.filter { it.configuration?.schedule is HabitSchedule.WeeklyQuota }
    val qualifyingDates = weeklyDays.filter { it.qualifies }.map { it.date }
    val endingWeeklyGoal = days.last().configuration?.weeklyGoal()
    // A wholly inactive weekly period imposes no obligation. A partially active one
    // must instead be shown as transitional, not as a smaller/prorated quota.
    if (weeklyDays.none { it.configuration?.status == RepoStatus.ACTIVE }) {
        return WeeklyProgress(habitId, period, WeekAssessment.NOT_APPLICABLE, days,
            qualifyingDates, null, endingWeeklyGoal, emptySet())
    }
    val reasons = buildSet {
        if (period.kind == WeekPeriodKind.BRIDGE) add(WeekTransitionReason.CALENDAR_BRIDGE)
        if (days.any { it.configuration == null }) add(WeekTransitionReason.CREATED_DURING_PERIOD)
        val configurations = days.mapNotNull { it.configuration }
        if (configurations.any { !it.sameConfigurationAs(configurations.first()) }) {
            add(WeekTransitionReason.CONFIGURATION_CHANGED)
        }
    }
    if (reasons.isNotEmpty()) {
        return WeeklyProgress(habitId, period, WeekAssessment.TRANSITIONAL, days,
            qualifyingDates, null, endingWeeklyGoal, reasons)
    }
    val goal = requireNotNull(days.first().configuration?.weeklyGoal())
    val quota = WeeklyQuotaProgress(qualifyingDates.size, goal)
    val assessment = when {
        quota.remainingDays == 0 -> WeekAssessment.ACHIEVED
        today < period.endExclusive -> WeekAssessment.PENDING
        else -> WeekAssessment.MISSED
    }
    return WeeklyProgress(habitId, period, assessment, days, qualifyingDates, quota, endingWeeklyGoal, emptySet())
}

private fun GoalVersion.weeklyGoal(): WeeklyGoal? =
    (schedule as? HabitSchedule.WeeklyQuota)?.let { WeeklyGoal(it.daysPerWeek, target) }

fun ProgressAnalytics.dateSummary(date: LocalDate, habitId: String? = null): DateSummary =
    DateSummary(date, calendar.periodOn(date), commits.activityCount(date, habitId),
        selectedHabits(habitId).map { daily(it.id, date) })

fun ProgressAnalytics.weekSummary(date: LocalDate, habitId: String? = null): WeekSummary {
    val period = calendar.periodOn(date)
    val dates = (0 until period.dayCount).map { dateSummary(period.start.plusDays(it.toLong()), habitId) }
    return WeekSummary(period, dates.sumOf { it.activityCount }, dates,
        selectedHabits(habitId).map { weekly(it.id, date) })
}

/**
 * Day counts cover [start, endExclusive). Week counts include only resolved periods
 * wholly contained in those bounds (never a clipped quota). Achieved results can count
 * before closing; pending, transitional, future, and not-applicable results cannot.
 * Rates stay separate because their denominators have different units.
 */
fun ProgressAnalytics.adherence(start: LocalDate, endExclusive: LocalDate, habitId: String? = null): AdherenceSummary {
    require(endExclusive >= start) { "Adherence range ends before it starts." }
    val selected = selectedHabits(habitId)
    var achievedDates = 0
    var missedDates = 0
    var date = start
    while (date < endExclusive && date <= today) {
        for (habit in selected) when (daily(habit.id, date).assessment) {
            DayAssessment.ACHIEVED -> achievedDates++
            DayAssessment.MISSED -> missedDates++
            else -> Unit
        }
        date = date.plusDays(1)
    }
    var achievedWeeks = 0
    var missedWeeks = 0
    var period = calendar.periodOn(start)
    while (period.start < endExclusive && period.start <= today) {
        if (period.start >= start && period.endExclusive <= endExclusive) {
            for (habit in selected) when (weekly(habit.id, period.start).assessment) {
                WeekAssessment.ACHIEVED -> achievedWeeks++
                WeekAssessment.MISSED -> missedWeeks++
                else -> Unit
            }
        }
        period = calendar.periodOn(period.endExclusive)
    }
    return AdherenceSummary(DayAdherence(achievedDates, missedDates), WeekAdherence(achievedWeeks, missedWeeks))
}

/** Stable workspace order inside each group keeps rows predictable after progress changes. */
fun ProgressAnalytics.todayGroups(): List<TodayGroup> {
    val groups = TodayGroupKind.entries.associateWith { mutableListOf<TodayHabit>() }
    for (habit in habits) {
        val day = daily(habit.id, today)
        if (day.configuration?.status != RepoStatus.ACTIVE) continue
        val week = if (day.configuration.schedule is HabitSchedule.WeeklyQuota) weekly(habit.id, today) else null
        val kind = when {
            week?.assessment == WeekAssessment.TRANSITIONAL -> TodayGroupKind.PARTIAL_WEEK_PRACTICE
            week?.assessment == WeekAssessment.PENDING -> TodayGroupKind.WEEKLY_REMAINING
            day.eligibility == DateEligibility.DUE && !day.qualifies -> TodayGroupKind.SCHEDULED
            else -> TodayGroupKind.COMPLETED_OR_OPTIONAL
        }
        groups.getValue(kind).add(TodayHabit(habit.id, day, week))
    }
    return groups.map { (kind, rows) -> TodayGroup(kind, rows.toList()) }
}

private fun ProgressAnalytics.selectedHabits(habitId: String?): List<Habit> =
    if (habitId == null) habits else listOf(requireNotNull(habits.firstOrNull { it.id == habitId }) {
        "This repo could not be found."
    })
