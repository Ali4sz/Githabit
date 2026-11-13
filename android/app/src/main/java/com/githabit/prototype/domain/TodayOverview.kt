package com.githabit.prototype.domain

enum class TodayStatus { NO_REPOS, NO_ACTIVE_REPOS, NO_SCHEDULE, QUOTA_MET, DUE_COMPLETE, REMAINING }

/** Classify from the unfiltered domain groups, never from a numeric-target-only adapter. */
fun todayStatus(habitCount: Int, groups: List<TodayGroup>): TodayStatus {
    val rows = groups.flatMap { it.habits }
    return when {
        habitCount == 0 -> TodayStatus.NO_REPOS
        rows.isEmpty() -> TodayStatus.NO_ACTIVE_REPOS
        groups.any { it.isRemaining && it.habits.isNotEmpty() } -> TodayStatus.REMAINING
        rows.any { it.daily.eligibility == DateEligibility.DUE } -> TodayStatus.DUE_COMPLETE
        rows.any { it.weekly?.quota?.remainingDays == 0 } -> TodayStatus.QUOTA_MET
        else -> TodayStatus.NO_SCHEDULE
    }
}
