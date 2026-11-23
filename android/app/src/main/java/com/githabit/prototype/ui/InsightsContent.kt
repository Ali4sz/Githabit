package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.Overlay
import com.githabit.prototype.R
import com.githabit.prototype.domain.*
import java.time.LocalDate

@Composable
internal fun InsightsContent(vm: GitHabitViewModel) {
    val analytics = vm.analytics
    var anchorText by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = anchorText?.let(LocalDate::parse) ?: vm.today
    val summary = remember(analytics, anchor) { analytics.weekSummary(anchor) }
    val period = summary.period
    val bounds = remember(analytics) { analytics.inspectionBounds() }
    val adherence = remember(analytics, period) { analytics.adherence(period.start, period.endExclusive) }
    val streaks = remember(analytics) { analytics.commits.activityStreaks(vm.today) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("insights-list"), state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.section),
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section)) {
            item(key = "period", contentType = "controls") {
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeading(stringResource(R.string.insights_title))
                    WeekPeriodSummary(period)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                        OutlinedButton(onClick = { anchorText = period.start.minusDays(1).toString() }, enabled = period.start > bounds.first,
                            modifier = Modifier.focusOutline().testTag("previous-period")) { Text(stringResource(R.string.insights_previous_period)) }
                        OutlinedButton(onClick = { anchorText = period.endExclusive.toString() }, enabled = period.endExclusive <= bounds.second,
                            modifier = Modifier.focusOutline().testTag("next-period")) { Text(stringResource(R.string.insights_next_period)) }
                        TextButton(onClick = { anchorText = null }, modifier = Modifier.focusOutline().testTag("current-period")) { Text(stringResource(R.string.history_today)) }
                    }
                    Text(pluralStringResource(R.plurals.history_commits, summary.activityCount, summary.activityCount), modifier = Modifier.testTag("period-activity"))
                    Subtitle(stringResource(R.string.insights_activity_explanation))
                    if (period.endExclusive > vm.today.plusDays(1)) Subtitle(stringResource(R.string.insights_open_period))
                }
            }
            item(key = "adherence", contentType = "summary") { AdherencePanel(adherence) }
            item(key = "dates", contentType = "dates") {
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeading(stringResource(R.string.insights_date_activity))
                    summary.dates.forEach { date ->
                        OutlinedButton(onClick = { vm.overlay = Overlay.Day(date.date) }, enabled = date.date >= bounds.first && date.date <= bounds.second,
                            modifier = Modifier.fillMaxWidth()
                            .heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("inspect-${date.date}")) {
                            Text(pluralStringResource(R.plurals.insights_date_count, date.activityCount, localizedDateLabel(date.date), date.activityCount))
                        }
                    }
                }
            }
            item(key = "repo-heading", contentType = "heading") { SectionHeading(stringResource(R.string.insights_repo_periods)) }
            if (analytics.habits.isEmpty()) item(key = "empty", contentType = "empty") { Subtitle(stringResource(R.string.insights_empty)) }
            items(summary.habits, key = { "habit:${it.habitId}" }, contentType = { "repo-summary" }) { week ->
                val habit = analytics.habit(week.habitId)
                val repoAdherence = remember(analytics, period, habit.id) { analytics.adherence(period.start, period.endExclusive, habit.id) }
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeading(displayPreview(habit.name))
                    if (week.days.any { it.configuration?.schedule is HabitSchedule.WeeklyQuota }) WeeklyHistorySummary(week)
                    Text(if (repoAdherence.days.assessedDates == 0) stringResource(R.string.history_days_unavailable)
                        else stringResource(R.string.history_day_adherence, repoAdherence.days.achievedDates, repoAdherence.days.assessedDates))
                    Text(if (repoAdherence.weeks.assessedWeeks == 0) stringResource(R.string.history_weeks_unavailable)
                        else stringResource(R.string.history_week_adherence, repoAdherence.weeks.achievedWeeks, repoAdherence.weeks.assessedWeeks))
                    Subtitle(stringResource(R.string.insights_daily_inspection))
                    OutlinedButton(onClick = { vm.overlay = Overlay.Day(if (vm.today in period) vm.today else period.start, habit.id) },
                        modifier = Modifier.fillMaxWidth().focusOutline().testTag("inspect-repo-${habit.id}")) { Text(stringResource(R.string.history_date_title)) }
                }
            }
            item(key = "heatmap", contentType = "chart") { Heatmap(vm.workspace, vm.today, analytics = analytics) { vm.overlay = Overlay.Day(it) } }
            item(key = "streaks", contentType = "summary") {
                StatsRow(listOf(Triple(stringResource(R.string.history_activity_streak), pluralStringResource(R.plurals.schedule_day_count, streaks.current, streaks.current), ""),
                    Triple(stringResource(R.string.history_longest_streak), pluralStringResource(R.plurals.schedule_day_count, streaks.longest, streaks.longest), ""),
                    Triple(stringResource(R.string.history_total_commits), amountLabel(analytics.commits.history().size.toDouble()), "")))
                Subtitle(stringResource(R.string.history_streak_explanation))
            }
        }
    }
}
