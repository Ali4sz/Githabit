package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.Overlay
import com.githabit.prototype.R
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun ColumnScope.DayDetails(vm: GitHabitViewModel, overlay: Overlay.Day) {
    val analytics = vm.analytics
    val day = overlay.date
    val bounds = remember(analytics, overlay.habitId) { analytics.inspectionBounds(overlay.habitId) }
    val summary = remember(analytics, day, overlay.habitId) { analytics.dateSummary(day, overlay.habitId) }
    val commits = remember(analytics, day, overlay.habitId) { analytics.commits.history(overlay.habitId, day) }
    var dateText by rememberSaveable(day) { mutableStateOf(day.toString()) }
    var error by rememberSaveable(day) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dateFocus = remember { FocusRequester() }
    val commitFocus = remember { FocusRequester() }
    fun move(date: LocalDate) { vm.overlay = overlay.copy(date = date, focusCommitId = null) }
    // Only a correction return requests focus. Ordinary date navigation keeps the input stable.
    LaunchedEffect(overlay.focusCommitId) {
        if (overlay.focusCommitId != null) {
            val index = commits.indexOfFirst { it.id == overlay.focusCommitId }
            if (index >= 0) {
                listState.scrollToItem(2 + summary.habits.size + index)
                withFrameNanos { }
                commitFocus.requestFocus()
            } else {
                listState.scrollToItem(0)
                dateFocus.requestFocus()
            }
            vm.consumeInspectionFocus(overlay)
        }
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("date-inspector"), state = listState,
        contentPadding = PaddingValues(bottom = GitHabitSpace.large), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.large)) {
        item(key = "date-controls", contentType = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                Text(stringResource(R.string.history_date_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(localizedDateLabel(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inspected-date").semantics { liveRegion = LiveRegionMode.Polite })
                if (day > vm.today) Text(stringResource(R.string.history_future_record))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                    OutlinedButton(onClick = { move(day.minusDays(1)) }, enabled = day > bounds.first && !vm.busy,
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("previous-day")) { Text(stringResource(R.string.history_previous_day)) }
                    OutlinedButton(onClick = { move(day.plusDays(1)) }, enabled = day < bounds.second && !vm.busy,
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("next-day")) { Text(stringResource(R.string.history_next_day)) }
                    TextButton(onClick = { move(vm.today) }, enabled = !vm.busy, modifier = Modifier.focusOutline()) { Text(stringResource(R.string.history_today)) }
                }
                OutlinedTextField(dateText, { if (it.length <= 10) dateText = it }, label = { Text(stringResource(R.string.history_date_input)) }, singleLine = true,
                    enabled = !vm.busy, isError = error, modifier = Modifier.fillMaxWidth().focusRequester(dateFocus).testTag("explore-date"))
                OutlinedButton(onClick = {
                    val parsed = runCatching { LocalDate.parse(dateText) }.getOrNull()
                    if (parsed == null || parsed < bounds.first || parsed > bounds.second) error = true
                    else { error = false; move(parsed) }
                }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().focusOutline().testTag("explore-go")) { Text(stringResource(R.string.history_go)) }
                Subtitle(stringResource(R.string.history_inspection_bounds, localizedDateLabel(bounds.first), localizedDateLabel(bounds.second)))
                if (error) Text(stringResource(R.string.history_date_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                WeekPeriodSummary(summary.period)
                Subtitle(stringResource(R.string.history_period_convention, weekdayLabel(summary.period.weekStart)))
                Text(pluralStringResource(R.plurals.history_commits, summary.activityCount, summary.activityCount))
            }
        }
        items(summary.habits, key = { "habit:${it.habitId}" }, contentType = { "daily" }) { daily ->
            val habit = analytics.habit(daily.habitId)
            Panel(Modifier.fillMaxWidth()) {
                val weekly = remember(analytics, daily.habitId, day) { analytics.weekly(daily.habitId, day) }
                DailyHistorySummary(habit, daily, vm.today, weekly)
                if (weekly.assessment != WeekAssessment.NOT_APPLICABLE || daily.configuration?.schedule is HabitSchedule.WeeklyQuota) WeeklyHistorySummary(weekly)
            }
        }
        item(key = "commits-heading", contentType = "heading") { SectionHeading(stringResource(R.string.history_date_commits)) }
        if (commits.isEmpty()) item(key = "empty", contentType = "empty") { Subtitle(stringResource(R.string.history_date_empty)) }
        items(commits, key = { "commit:${it.id}" }, contentType = { "commit" }) { commit ->
            CommitRow(analytics.habit(commit.habitId), commit, vm.zone, vm.today,
                focus = commitFocus.takeIf { commit.id == overlay.focusCommitId }) { vm.correctFromDay(commit) }
        }
    }
}
