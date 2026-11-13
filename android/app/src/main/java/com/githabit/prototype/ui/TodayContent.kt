package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.githabit.prototype.*
import com.githabit.prototype.R
import com.githabit.prototype.domain.*

@Composable
internal fun TodayContent(vm: GitHabitViewModel) {
    val state = vm.workspace
    val today = vm.today
    val index = vm.analytics
    val groups = remember(index) { index.todayGroups() }
    val habits = remember(state) { state.habits.associateBy { it.id } }
    val status = remember(groups, habits) { todayStatus(habits.size, groups) }
    var onlyRemaining by rememberSaveable { mutableStateOf(false) }
    val remaining = groups.filter { it.isRemaining }.sumOf { it.habits.size }
    val visible = groups.filter { !onlyRemaining || it.isRemaining }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("today-list"),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.medium),
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium)) {
            item(key = "today-header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.today_date, dateLabel(today)), Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge)
                        if (habits.isNotEmpty()) IconButton(onClick = { vm.overlay = Overlay.RepoForm() }, enabled = !vm.busy,
                            modifier = Modifier.focusOutline(CircleShape).testTag("new-repo")) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.create_repo))
                        }
                    }
                    Text(pluralStringResource(R.plurals.today_remaining, remaining, remaining),
                        Modifier.testTag("today-remaining-count"), style = MaterialTheme.typography.bodySmall)
                    val count = index.commits.activityCount(today)
                    Subtitle(pluralStringResource(R.plurals.today_activity, count, count), Modifier.testTag("today-activity-count"))
                }
            }
            if (habits.isNotEmpty()) item(key = "today-filter", contentType = "filter") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                    FilterChip(selected = onlyRemaining, onClick = { onlyRemaining = !onlyRemaining },
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("today-remaining-filter"),
                        label = { Text(stringResource(R.string.today_remaining_filter)) })
                    TextButton(onClick = { vm.overlay = Overlay.Day(today) },
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("today-explore")) {
                        Text(stringResource(R.string.today_explore))
                    }
                }
            }
            if (status != TodayStatus.REMAINING) item(key = "today-status", contentType = "status") {
                TodayEmptyStatus(vm, status, onlyRemaining)
            }
            visible.filter { it.habits.isNotEmpty() }.forEach { group ->
                item(key = "group:${group.kind}", contentType = "group") {
                    Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                        Text(stringResource(group.kind.titleResource()), Modifier.semantics { heading() }
                            .testTag("today-group-${group.kind}"), style = MaterialTheme.typography.titleMedium)
                        if (group.kind == TodayGroupKind.WEEKLY_REMAINING || group.kind == TodayGroupKind.PARTIAL_WEEK_PRACTICE) {
                            WeekPeriodSummary(index.calendar.periodOn(today))
                        }
                    }
                }
                // Global identity, not group-relative identity: completed rows can move safely.
                items(group.habits, key = { "habit:${it.habitId}" }, contentType = { "habit" }) { summary ->
                    val habit = habits.getValue(summary.habitId)
                    val action = TodayAction(vm.workspaceSession, today, habit, summary)
                    TodayHabitCard(habit, summary, vm.busy,
                        state = state, analytics = index,
                        showPeriod = group.kind == TodayGroupKind.COMPLETED_OR_OPTIONAL,
                        onOpen = { vm.navigate(Destination.DETAIL, habit.id) },
                        onLog = { vm.openTodayLog(action) }, onComplete = { vm.completeToday(action) })
                }
            }
            item(key = "today-history", contentType = "footer") {
                TextButton(onClick = { vm.navigate(Destination.INSIGHTS) },
                    modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("today-history")) {
                    Text(stringResource(R.string.today_history))
                }
            }
        }
    }
}

private fun TodayGroupKind.titleResource() = when (this) {
    TodayGroupKind.SCHEDULED -> R.string.today_group_scheduled
    TodayGroupKind.WEEKLY_REMAINING -> R.string.today_group_weekly
    TodayGroupKind.PARTIAL_WEEK_PRACTICE -> R.string.today_group_practice
    TodayGroupKind.COMPLETED_OR_OPTIONAL -> R.string.today_group_optional
}

@Composable
private fun TodayEmptyStatus(vm: GitHabitViewModel, status: TodayStatus, filtered: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("today-status-$status"), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
        Text(stringResource(when (status) {
            TodayStatus.NO_REPOS -> R.string.empty_workspace_title
            TodayStatus.NO_ACTIVE_REPOS -> R.string.today_no_active
            TodayStatus.NO_SCHEDULE -> R.string.today_no_schedule
            TodayStatus.QUOTA_MET -> R.string.today_quota_met
            else -> R.string.today_due_complete
        }), style = MaterialTheme.typography.titleMedium)
        Subtitle(stringResource(when (status) {
            TodayStatus.NO_REPOS -> R.string.empty_workspace_description
            TodayStatus.NO_ACTIVE_REPOS -> R.string.today_no_active_description
            TodayStatus.NO_SCHEDULE -> R.string.today_no_schedule_description
            else -> R.string.today_complete_description
        }))
        if (status == TodayStatus.NO_REPOS) Button(onClick = { vm.overlay = Overlay.RepoForm() }, enabled = !vm.busy,
            modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape, MaterialTheme.colorScheme.onPrimary).testTag("new-repo")) {
            Text(stringResource(R.string.create_first_repo))
        } else if (filtered) Subtitle(stringResource(R.string.today_filtered_empty), Modifier.testTag("today-filtered-empty"))
        if (status == TodayStatus.NO_ACTIVE_REPOS) TextButton(onClick = { vm.navigate(Destination.REPOS) },
            modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape)) {
            Text(stringResource(R.string.navigation_repos))
        }
    }
}

@Composable
internal fun TodayWeeklyContext(summary: TodayHabit) {
    val week = summary.weekly ?: return
    val quota = week.quota
    if (quota != null) {
        Text(stringResource(R.string.today_weekly_score, quota.displayDays, quota.goal.daysPerWeek),
            Modifier.testTag("today-quota-${summary.habitId}"), style = MaterialTheme.typography.bodySmall)
        Subtitle(stringResource(if (quota.remainingDays == 0) R.string.today_quota_reached else R.string.today_week_pending))
        Subtitle(stringResource(if (summary.daily.qualifies) R.string.today_qualified else R.string.today_not_qualified))
    } else {
        Text(pluralStringResource(R.plurals.today_practice_dates, week.qualifyingDates.size, week.qualifyingDates.size),
            Modifier.testTag("today-practice-${summary.habitId}"), style = MaterialTheme.typography.bodySmall)
        Subtitle(stringResource(R.string.today_practice_unscored))
        week.endingWeeklyGoal?.let { goal ->
            Subtitle(pluralStringResource(R.plurals.today_usual_quota, goal.daysPerWeek, goal.daysPerWeek))
        }
    }
}

@Composable
private fun TodayHabitCard(habit: Habit, summary: TodayHabit, busy: Boolean,
    state: Workspace, analytics: ProgressAnalytics, showPeriod: Boolean,
    onOpen: () -> Unit, onLog: () -> Unit, onComplete: () -> Unit) {
    val progress = summary.daily.progress
    val actionable = summary.daily.eligibility == DateEligibility.DUE || summary.daily.eligibility == DateEligibility.WEEKLY_OPPORTUNITY
    val dailyStatus = stringResource(when {
        !actionable -> R.string.today_optional_date
        progress.targetReached -> R.string.schedule_today_reached
        progress.total > 0 -> R.string.today_partial
        else -> R.string.today_no_progress
    })
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().focusOutline(MaterialTheme.shapes.medium)
        .testTag("habit-${habit.id}").semantics { stateDescription = dailyStatus },
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(GitHabitSpace.medium), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium)
            if (summary.weekly == null) ScheduleSummary(state, habit, summary.daily.date, analytics)
            else if (showPeriod) WeekPeriodSummary(summary.weekly.period)
            Text(stringResource(R.string.today_numeric_progress, formatAmount(progress.total), formatAmount(requireNotNull(progress.target)), habit.unit),
                Modifier.testTag("progress-${habit.id}"), style = MaterialTheme.typography.bodyMedium)
            Subtitle(dailyStatus)
            TodayWeeklyContext(summary)
            if (actionable) {
                LinearProgressIndicator(progress = { (progress.ratio ?: 0.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(GitHabitSize.progressTrack),
                    color = habitColor(habit.color), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                    OutlinedButton(onClick = onLog, enabled = !busy,
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("log-${habit.id}")) {
                        Text(stringResource(R.string.today_log))
                    }
                    FilledTonalButton(onClick = onComplete, enabled = !busy && !progress.targetReached,
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("complete-${habit.id}")) {
                        Text(stringResource(R.string.today_complete))
                    }
                }
            } else TextButton(onClick = onOpen,
                modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("today-open-${habit.id}")) {
                Text(stringResource(R.string.today_open_repo))
            }
        }
    }
}
