package com.githabit.prototype.domain

import java.time.LocalDate
import java.util.Collections

/** Actual amounts are never capped or combined across habits with potentially different units. */
data class RecordedProgress(val total: Double = 0.0, val commitCount: Int = 0)

data class TargetProgress(val total: Double, val target: Double?) {
    val ratio: Double? get() = target?.let { (total / it).coerceIn(0.0, 1.0) }
    val targetReached: Boolean get() = target != null && total >= target
    val remaining: Double? get() = target?.let { rounded((it - total).coerceAtLeast(0.0)) }
}

enum class DateEligibility { BEFORE_CREATION, PAUSED, ARCHIVED, NOT_SCHEDULED, DUE, WEEKLY_OPPORTUNITY }
enum class DayAssessment { NOT_APPLICABLE, PENDING, ACHIEVED, MISSED }

data class DailyProgress(
    val habitId: String,
    val date: LocalDate,
    val configuration: GoalVersion?,
    val progress: TargetProgress,
    val commitCount: Int,
    val eligibility: DateEligibility,
    val assessment: DayAssessment,
    /** Credit requires the dated schedule/status and may never come from a future date. */
    val qualifies: Boolean,
)

data class ActivityStreaks(val current: Int, val longest: Int)

/**
 * One pass over a validated snapshot's commits. Only stored dates determine membership;
 * timestamps order the activity feed, not goal credit. No input collections are retained.
 */
class CommitIndex(commits: List<Commit>) {
    private val byHabit: Map<String, Map<LocalDate, RecordedProgress>>
    private val dateCounts: Map<LocalDate, Int>

    init {
        val totals = mutableMapOf<String, MutableMap<LocalDate, RecordedProgress>>()
        val counts = mutableMapOf<LocalDate, Int>()
        for (commit in commits) {
            require(commit.amount.isFinite() && commit.amount in 0.001..1_000_000.0) {
                "Cannot index invalid progress."
            }
            val days = totals.getOrPut(commit.habitId) { mutableMapOf() }
            val previous = days[commit.date] ?: RecordedProgress()
            days[commit.date] = RecordedProgress(previous.total + commit.amount, previous.commitCount + 1)
            counts[commit.date] = (counts[commit.date] ?: 0) + 1
        }
        byHabit = totals.mapValues { (_, days) ->
            days.mapValues { (_, value) -> value.copy(total = rounded(value.total)) }
        }
        dateCounts = counts.toMap()
    }

    fun recorded(habitId: String, date: LocalDate): RecordedProgress =
        byHabit[habitId]?.get(date) ?: RecordedProgress()

    fun activityCount(date: LocalDate, habitId: String? = null): Int =
        if (habitId == null) dateCounts[date] ?: 0 else recorded(habitId, date).commitCount

    /** Any positive recorded activity counts, including optional and subsequently paused dates. */
    fun activityStreaks(today: LocalDate, habitId: String? = null): ActivityStreaks {
        val dates = (if (habitId == null) dateCounts.keys else byHabit[habitId]?.keys.orEmpty())
            .filter { it <= today }.sorted()
        var run = 0
        var longest = 0
        var previous: LocalDate? = null
        for (date in dates) {
            run = if (previous?.plusDays(1) == date) run + 1 else 1
            longest = maxOf(longest, run)
            previous = date
        }
        val current = if (previous != null && previous >= today.minusDays(1)) run else 0
        return ActivityStreaks(current, longest)
    }
}

/**
 * Reuse for all queries on the same committed snapshot and captured evaluation date.
 * Rebuild after a correction/deletion or date refresh. Legacy UI helpers in Habits.kt
 * remain compatibility-only until schedule-aware consumers replace them.
 */
class ProgressAnalytics(workspace: Workspace, val today: LocalDate) {
    val commits = CommitIndex(workspace.commits)
    val habits: List<Habit> = Collections.unmodifiableList(workspace.habits.map {
        it.copy(versions = Collections.unmodifiableList(it.versions.sortedBy { version -> version.date }))
    })
    private val habitsById = habits.associateBy { it.id }

    fun daily(habitId: String, date: LocalDate): DailyProgress {
        val habit = requireNotNull(habitsById[habitId]) { "This repo could not be found." }
        val configuration = configurationOn(habit, date)
        val recorded = commits.recorded(habitId, date)
        val progress = TargetProgress(recorded.total, configuration?.target)
        val eligibility = when {
            configuration == null -> DateEligibility.BEFORE_CREATION
            configuration.status == RepoStatus.PAUSED -> DateEligibility.PAUSED
            configuration.status == RepoStatus.ARCHIVED -> DateEligibility.ARCHIVED
            configuration.schedule.isDueOn(date) -> DateEligibility.DUE
            configuration.schedule is HabitSchedule.WeeklyQuota -> DateEligibility.WEEKLY_OPPORTUNITY
            else -> DateEligibility.NOT_SCHEDULED
        }
        val qualifies = date <= today && progress.targetReached &&
            (eligibility == DateEligibility.DUE || eligibility == DateEligibility.WEEKLY_OPPORTUNITY)
        val assessment = when {
            eligibility != DateEligibility.DUE -> DayAssessment.NOT_APPLICABLE
            date > today -> DayAssessment.PENDING
            qualifies -> DayAssessment.ACHIEVED
            date == today -> DayAssessment.PENDING
            else -> DayAssessment.MISSED
        }
        return DailyProgress(habitId, date, configuration, progress, recorded.commitCount,
            eligibility, assessment, qualifies)
    }

    // Sorted once per snapshot; large version histories are not rescanned for every graph cell.
    private fun configurationOn(habit: Habit, date: LocalDate): GoalVersion? {
        if (date < habit.createdOn) return null
        val index = habit.versions.binarySearchBy(date) { it.date }
        return habit.versions.getOrNull(if (index >= 0) index else -index - 2)
    }
}
