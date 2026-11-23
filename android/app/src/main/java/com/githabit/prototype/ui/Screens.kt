package com.githabit.prototype.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.githabit.prototype.*
import com.githabit.prototype.R
import com.githabit.prototype.data.UnreadableKind
import com.githabit.prototype.domain.*
import java.time.LocalDate

@Composable
fun RecoveryScreen(state: WorkspaceLoad.Recovery, canSaveCopy: Boolean, busy: Boolean,
    onRetry: () -> Unit, onSaveCopy: () -> Unit, onRestore: () -> Unit, onStartOver: () -> Unit) {
    PageColumn(Modifier.imePadding()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
            Subtitle(diagnosticMessage(state.diagnostic))
        }
        Panel {
            SectionHeading(stringResource(R.string.recovery_meaning))
            Subtitle(stringResource(when (state.kind) {
                UnreadableKind.UNSUPPORTED_VERSION -> R.string.recovery_unsupported
                UnreadableKind.IO -> R.string.recovery_io
                UnreadableKind.INDETERMINATE -> R.string.recovery_indeterminate
                else -> R.string.recovery_invalid
            }))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(stringResource(if (state.preserved) R.string.recovery_preserved else R.string.recovery_unpreserved), state.preserved)
                if (state.kind == UnreadableKind.INDETERMINATE) StatusBadge(stringResource(R.string.recovery_paused))
            }
        }
        Panel {
            SectionHeading(stringResource(R.string.recovery_options))
            Button(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .testTag("recovery-retry")) {
                Text(stringResource(R.string.recovery_retry))
            }
            OutlinedButton(onClick = onSaveCopy, enabled = canSaveCopy && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("recovery-save-copy")) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.transfer_export_recovery))
            }
            OutlinedButton(onClick = onRestore, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("recovery-restore")) {
                Text(stringResource(R.string.restore_title))
            }
            TextButton(onClick = onStartOver, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("recovery-start-over")) {
                Text(stringResource(R.string.recovery_start_over))
            }
        }
        Subtitle(stringResource(R.string.recovery_policy))
    }
}

@Composable
fun WelcomeScreen(vm: GitHabitViewModel) {
    PageColumn(Modifier.testTag("local-welcome")) {
        Brand()
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Subtitle(stringResource(R.string.welcome_description))
        Subtitle(stringResource(R.string.welcome_local))
        Button(onClick = { vm.finishWelcome() }, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch)
                .focusOutline(CircleShape, MaterialTheme.colorScheme.onPrimary).testTag("create-first-repo")) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(GitHabitSpace.small))
            Text(stringResource(R.string.create_first_repo))
        }
        TextButton(onClick = { vm.finishWelcome(createRepo = false) }, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch)
                .focusOutline(CircleShape).testTag("open-empty-workspace")) {
            Text(stringResource(R.string.open_empty_workspace))
        }
    }
}

@Composable
fun TodayScreen(vm: GitHabitViewModel) {
    TodayContent(vm)
}

@Composable
private fun RepoCard(vm: GitHabitViewModel, habit: Habit, selected: Boolean? = null) {
    HabitCard(vm.workspace, habit, vm.today, vm.busy,
        onOpen = { vm.navigate(Destination.DETAIL, habit.id) },
        onLog = { vm.overlay = Overlay.LogForm(habit.id) },
        onComplete = { vm.complete(habit.id) }, onResume = { vm.status(habit.id, RepoStatus.ACTIVE) }, selected = selected, analytics = vm.analytics)
}

@Composable
fun ReposScreen(vm: GitHabitViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        RepoListContent(vm, Modifier.widthIn(max = 720.dp).fillMaxSize())
    }
}

@Composable
internal fun RepoListContent(vm: GitHabitViewModel, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    val repos = vm.workspace.habits.filter {
        (filter == "All" || it.versionOn(vm.today)?.status?.name.equals(filter, ignoreCase = true)) &&
            (it.name.contains(query, true) || it.description.contains(query, true))
    }
    LazyColumn(modifier.testTag("repo-list-pane"), state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.section),
        verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section)) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(stringResource(R.string.repositories_title)) {
                    IconButton(onClick = { vm.overlay = Overlay.RepoForm() }) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.create_repo))
                    }
                }
                Subtitle(stringResource(R.string.repositories_subtitle))
            }
        }
        item(key = "search") {
            OutlinedTextField(query, { if (it.length <= 160) query = it }, modifier = Modifier.fillMaxWidth().testTag("repo-search"),
                label = { Text(stringResource(R.string.find_repository)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) })
        }
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Active" to R.string.repo_filter_active, "Paused" to R.string.repo_filter_paused,
                    "Archived" to R.string.repo_filter_archived, "All" to R.string.repo_filter_all).forEach { (value, label) ->
                    FilterChip(selected = value == filter, onClick = { filter = value },
                        modifier = Modifier.testTag("repo-filter-$value"), label = { Text(stringResource(label)) })
                }
            }
        }
        if (repos.isEmpty()) item(key = "empty") {
            EmptyState(stringResource(R.string.no_matching_repos), stringResource(R.string.no_matching_repos_description)) {
                vm.overlay = Overlay.RepoForm()
            }
        }
        items(repos, key = { "habit:${it.id}" }) { RepoCard(vm, it, selected = it.id == vm.selectedRepoId) }
        item(key = "footer") { Footer() }
    }
}

@Composable
fun DetailScreen(vm: GitHabitViewModel) {
    val state = vm.workspace
    val habit = state.habits.find { it.id == vm.selectedRepoId }
    if (habit == null) { EmptyState(stringResource(R.string.repo_missing_title), stringResource(R.string.repo_missing_description)); return }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("repo-detail-pane"), state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.section),
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section)) {
            item(key = "detail", contentType = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section)) { RepoDetailContent(vm, habit) }
            }
            commitHistory(vm.analytics, vm.analytics.commits.history(habit.id), vm.zone) { vm.overlay = Overlay.LogForm(it.habitId, it.id) }
        }
    }
}

@Composable
internal fun ColumnScope.RepoDetailContent(vm: GitHabitViewModel, habit: Habit) {
    val state = vm.workspace
    val analytics = vm.analytics
    val daily = remember(analytics, habit.id) { analytics.daily(habit.id, vm.today) }
    val progress = daily.progress
    val status = daily.configuration?.status
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitGlyph(habit, 48)
                Text(displayPreview(habit.name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
            if (habit.description.isNotBlank()) Subtitle(displayPreview(habit.description))
            ScheduleSummary(state, habit, vm.today, vm.analytics)
             FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                 StatusBadge(status?.let { repoStatusLabel(it) } ?: stringResource(R.string.history_before_creation), status == RepoStatus.ACTIVE)
                OutlinedButton(onClick = { vm.overlay = Overlay.RepoForm(habit.id) }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.history_edit_repo))
                }
            }
        }
        Panel {
            SectionHeading(stringResource(R.string.history_today_progress))
             Text(amountLabel(progress.total), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("detail-total"))
            val week = remember(analytics, habit.id) { analytics.weekly(habit.id, vm.today) }
            DailyHistorySummary(habit, daily, vm.today, week)
            LinearProgressIndicator(progress = { (progress.ratio ?: 0.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(GitHabitSize.progressTrack), color = habitColor(habit.color),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
            if (status == RepoStatus.ACTIVE) {
                Button(onClick = { vm.overlay = Overlay.LogForm(habit.id) }, enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).testTag("detail-log")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.history_log_progress)) }
                TextButton(onClick = { vm.complete(habit.id) }, enabled = !progress.targetReached && !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(if (progress.targetReached) R.string.schedule_today_reached else R.string.history_complete_remaining))
                }
            } else {
                Subtitle(stringResource(R.string.history_retained))
                if (status != null) Button(onClick = { vm.status(habit.id, RepoStatus.ACTIVE) }, enabled = !vm.busy) { Text(stringResource(R.string.history_resume)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == RepoStatus.ACTIVE) OutlinedButton(onClick = { vm.status(habit.id, RepoStatus.PAUSED) }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Pause, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.history_pause))
                }
                if (status != null && status != RepoStatus.ARCHIVED) OutlinedButton(onClick = { archiveConfirmation = true }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Archive, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.history_archive))
                }
            }
        }
        Heatmap(state, vm.today, habit.id, analytics) { vm.overlay = Overlay.Day(it, habit.id) }
        val streaks = remember(analytics, habit.id) { analytics.commits.activityStreaks(vm.today, habit.id) }
         StatsRow(listOf(Triple(stringResource(R.string.history_activity_streak), pluralStringResource(R.plurals.schedule_day_count, streaks.current, streaks.current), ""),
             Triple(stringResource(R.string.history_longest_streak), pluralStringResource(R.plurals.schedule_day_count, streaks.longest, streaks.longest), ""),
             Triple(stringResource(R.string.history_total_commits), amountLabel(analytics.commits.history(habit.id).size.toDouble()), "")))
        Subtitle(stringResource(R.string.history_streak_explanation))
    if (archiveConfirmation) ConfirmDialog(stringResource(R.string.history_archive_title), stringResource(R.string.history_archive_message),
        stringResource(R.string.history_archive), onDismiss = { archiveConfirmation = false }, onConfirm = { archiveConfirmation = false; vm.status(habit.id, RepoStatus.ARCHIVED) })
}

@Composable
fun ActivityScreen(vm: GitHabitViewModel, onExport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("activity-history"), state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.section),
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section)) {
            item(key = "activity-title", contentType = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                    SectionHeading(stringResource(R.string.history_changelog))
                    Subtitle(stringResource(R.string.history_ordering))
                    OutlinedButton(onClick = onExport) { Text(stringResource(R.string.history_export)) }
                }
            }
            commitHistory(vm.analytics, vm.analytics.commits.history(), vm.zone) { vm.overlay = Overlay.LogForm(it.habitId, it.id) }
        }
    }
}

@Composable
fun InsightsScreen(vm: GitHabitViewModel) {
    InsightsContent(vm)
}

@Composable
private fun Footer() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.local_footer), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.app_developer_credit), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
