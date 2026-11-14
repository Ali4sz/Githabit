package com.githabit.prototype.domain

import java.time.LocalDate
import kotlin.math.ceil

/** Intensity is activity for the overview, numeric target progress for one repo; never adherence. */
data class HeatmapDate(val date: LocalDate, val level: Int, val daily: DailyProgress?)
data class HeatmapPeriod(val period: WeekPeriod, val dates: List<HeatmapDate>)

fun ProgressAnalytics.heatmap(periodCount: Int, habitId: String? = null): List<HeatmapPeriod> {
    require(periodCount in setOf(13, 26, 52))
    val periods = ArrayList<WeekPeriod>(periodCount)
    var period = calendar.periodOn(today)
    repeat(periodCount) {
        periods.add(period)
        period = calendar.periodOn(period.start.minusDays(1))
    }
    return periods.asReversed().map { bounds ->
        HeatmapPeriod(bounds, (0 until bounds.dayCount).map { offset ->
            val date = bounds.start.plusDays(offset.toLong())
            val daily = habitId?.let { daily(it, date) }
            val level = if (date > today) 0 else if (daily == null) when (commits.activityCount(date)) {
                0 -> 0
                1 -> 1
                2 -> 2
                in 3..4 -> 3
                else -> 4
            } else {
                val ratio = daily.progress.ratio ?: 0.0
                if (ratio <= 0) 0 else if (ratio >= 1) 4 else ceil(ratio * 3).toInt()
            }
            HeatmapDate(date, level, daily)
        })
    }
}
