package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.githabit.prototype.R
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun dailyStateText(day: DailyProgress, today: LocalDate, week: WeeklyProgress? = null): String {
    val state = when (day.eligibility) {
        DateEligibility.BEFORE_CREATION -> R.string.history_before_creation
        DateEligibility.PAUSED -> R.string.history_paused
        DateEligibility.ARCHIVED -> R.string.history_archived
        DateEligibility.NOT_SCHEDULED -> if (day.commitCount > 0) R.string.history_optional_activity else R.string.history_not_scheduled
        DateEligibility.WEEKLY_OPPORTUNITY -> when {
            day.date > today -> R.string.history_future_uncredited
            day.qualifies -> if (week?.assessment == WeekAssessment.TRANSITIONAL) R.string.history_practice_day else R.string.history_qualifying_day
            day.progress.total > 0 -> R.string.history_partial_weekly
            else -> R.string.history_weekly_opportunity
        }
        DateEligibility.DUE -> when {
            day.date > today -> R.string.history_future_uncredited
            day.assessment == DayAssessment.ACHIEVED -> R.string.history_complete
            day.assessment == DayAssessment.MISSED -> if (day.progress.total > 0) R.string.history_partial_missed else R.string.history_missed
            day.progress.total > 0 -> R.string.history_partial_pending
            else -> R.string.history_pending
        }
    }
    return stringResource(state)
}

@Composable
internal fun DailyHistorySummary(habit: Habit, day: DailyProgress, today: LocalDate, week: WeeklyProgress? = null) {
    val locale = displayLocale()
    Column(Modifier.fillMaxWidth().testTag("date-summary-${habit.id}"), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
        Text(displayPreview(habit.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val target = day.progress.target
        Text(if (target == null) stringResource(R.string.history_amount_no_target, amountLabel(day.progress.total), displayPreview(habit.unit))
            else stringResource(R.string.history_amount, amountLabel(day.progress.total), amountLabel(target), displayPreview(habit.unit)))
        Text(dailyStateText(day, today, week), style = MaterialTheme.typography.bodyMedium)
        day.configuration?.let { version ->
            Subtitle(when (val schedule = version.schedule) {
                HabitSchedule.Daily -> stringResource(R.string.schedule_daily_instruction)
                is HabitSchedule.SelectedWeekdays -> stringResource(R.string.history_selected_weekdays,
                    schedule.days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.FULL, locale) })
                is HabitSchedule.WeeklyQuota -> pluralStringResource(R.plurals.schedule_weekly_instruction, schedule.daysPerWeek, schedule.daysPerWeek)
            })
        }
        Subtitle(pluralStringResource(R.plurals.history_commits, day.commitCount, day.commitCount))
    }
}

@Composable
internal fun WeeklyHistorySummary(week: WeeklyProgress) {
    val quota = week.quota
    Column(Modifier.testTag("week-summary-${week.habitId}"), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
        if (quota != null) {
            Text(stringResource(when (week.assessment) {
                WeekAssessment.ACHIEVED -> R.string.schedule_week_achieved
                WeekAssessment.MISSED -> R.string.schedule_week_missed
                else -> R.string.schedule_week_pending
            }, quota.displayDays, quota.goal.daysPerWeek))
            Subtitle(pluralStringResource(R.plurals.history_qualifying_dates, quota.qualifyingDays, quota.qualifyingDays))
        } else {
            Text(stringResource(if (week.assessment == WeekAssessment.TRANSITIONAL) R.string.history_transitional else R.string.history_week_inactive))
            Subtitle(pluralStringResource(R.plurals.history_qualifying_dates, week.qualifyingDates.size, week.qualifyingDates.size))
            week.endingWeeklyGoal?.let { Subtitle(pluralStringResource(R.plurals.history_usual_quota, it.daysPerWeek, it.daysPerWeek, amountLabel(it.dailyTarget))) }
            week.transitionReasons.forEach { reason -> Subtitle(stringResource(when (reason) {
                WeekTransitionReason.CALENDAR_BRIDGE -> R.string.history_bridge_reason
                WeekTransitionReason.CREATED_DURING_PERIOD -> R.string.history_creation_reason
                WeekTransitionReason.CONFIGURATION_CHANGED -> R.string.history_change_reason
            })) }
        }
    }
}

@Composable
internal fun AdherencePanel(summary: AdherenceSummary) {
    Panel(Modifier.fillMaxWidth().testTag("adherence")) {
        SectionHeading(stringResource(R.string.history_adherence))
        Text(if (summary.days.assessedDates == 0) stringResource(R.string.history_days_unavailable)
            else stringResource(R.string.history_day_adherence, summary.days.achievedDates, summary.days.assessedDates))
        Text(if (summary.weeks.assessedWeeks == 0) stringResource(R.string.history_weeks_unavailable)
            else stringResource(R.string.history_week_adherence, summary.weeks.achievedWeeks, summary.weeks.assessedWeeks))
        Subtitle(stringResource(R.string.history_adherence_explanation))
    }
}

@Composable
internal fun CommitRow(habit: Habit, commit: Commit, zone: ZoneId, today: LocalDate,
    focus: FocusRequester? = null, onEdit: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
            HabitGlyph(habit, 32)
            Text(displayPreview(habit.name), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onEdit, modifier = Modifier.then(if (focus == null) Modifier else Modifier.focusRequester(focus))
                .focusOutline().testTag("edit-commit-${commit.id}")) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.history_edit_commit, displayPreview(habit.name)))
            }
        }
        Text(stringResource(R.string.history_recorded_amount, amountLabel(commit.amount), displayPreview(habit.unit)), style = MaterialTheme.typography.bodyLarge)
        if (commit.note.isNotBlank()) Text(commit.note, style = MaterialTheme.typography.bodyMedium)
        Subtitle(stringResource(R.string.history_commit_time, localizedDateLabel(commit.date),
            commit.timestamp.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(displayLocale()))))
        if (commit.date > today) Text(stringResource(R.string.history_future_record))
    }
}

internal fun LazyListScope.commitHistory(analytics: ProgressAnalytics, commits: List<Commit>, zone: ZoneId,
    onEdit: (Commit) -> Unit) {
    item(key = "history-heading", contentType = "heading") { SectionHeading(stringResource(R.string.history_title)) }
    if (commits.isEmpty()) item(key = "history-empty", contentType = "empty") { Subtitle(stringResource(R.string.history_empty)) }
    items(commits, key = { "commit:${it.id}" }, contentType = { "commit" }) { commit ->
        CommitRow(analytics.habit(commit.habitId), commit, zone, analytics.today) { onEdit(commit) }
    }
}
