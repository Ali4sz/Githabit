package com.githabit.prototype.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githabit.prototype.*
import com.githabit.prototype.R
import com.githabit.prototype.data.UnreadableKind
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RecoveryScreen(state: WorkspaceLoad.Recovery, canSaveCopy: Boolean, busy: Boolean,
    onRetry: () -> Unit, onSaveCopy: () -> Unit, onStartOver: () -> Unit) {
    var confirmStartOver by rememberSaveable { mutableStateOf(false) }
    PageColumn(Modifier.imePadding()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
            Text("Your data needs attention", style = MaterialTheme.typography.headlineMedium)
            Subtitle(state.reason)
        }
        Panel {
            SectionHeading("What this means")
            Subtitle(when (state.kind) {
                UnreadableKind.UNSUPPORTED_VERSION ->
                    "This app cannot open the file's format. Its bytes were not modified. A compatible app version may be needed."
                UnreadableKind.IO ->
                    "The storage location could not be read. This can be temporary — trying again is safe."
                UnreadableKind.INDETERMINATE ->
                    "The last save could not be verified. The file may contain the previous or attempted change. Saving is paused until storage can be re-read."
                else ->
                    "The saved workspace file is damaged or did not pass validation. It was never modified by opening the app."
            })
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(if (state.preserved) "Recovery copy verified" else "Preservation unavailable", state.preserved)
                if (state.kind == UnreadableKind.INDETERMINATE) StatusBadge("Paused for recovery")
            }
        }
        Panel {
            SectionHeading("Recovery options")
            Button(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .testTag("recovery-retry")) {
                Text("Try reading your data again")
            }
            OutlinedButton(onClick = onSaveCopy, enabled = canSaveCopy && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("recovery-save-copy")) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Save a copy of the data")
            }
            TextButton(onClick = { confirmStartOver = true }, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("recovery-start-over")) {
                Text("Start over with an empty workspace")
            }
        }
        Subtitle("Retry only re-reads storage. Start over requires confirmation and a verified preservation copy before replacement. Recovery copies are a safeguard, not a substitute for an external backup.")
    }
    if (confirmStartOver) ConfirmDialog("Replace this workspace with an empty one?",
        "A preservation copy of the current data is saved on this device first. The empty workspace replaces the file that could not be opened.",
        "Start over", destructive = true,
        onDismiss = { confirmStartOver = false }, onConfirm = { confirmStartOver = false; onStartOver() })
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
    val state = vm.workspace
    val today = vm.today
    if (state.habits.isEmpty()) {
        PageColumn {
            Text(stringResource(R.string.empty_workspace_title), style = MaterialTheme.typography.headlineMedium)
            Subtitle(stringResource(R.string.empty_workspace_description))
            Button(onClick = { vm.overlay = Overlay.RepoForm() }, enabled = !vm.busy,
                modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch)
                    .focusOutline(CircleShape, MaterialTheme.colorScheme.onPrimary).testTag("new-repo")) {
                Text(stringResource(R.string.create_first_repo))
            }
            Footer()
        }
        return
    }
    var onlyRemaining by rememberSaveable { mutableStateOf(false) }
    val active = state.habits.filter { it.versionOn(today)?.status == RepoStatus.ACTIVE }
    val complete = active.count { state.progress(it, today).complete }
    val visible = active.filter { !onlyRemaining || !state.progress(it, today).complete }
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Eyebrow("YOUR PERSONAL CHANGELOG")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(buildAnnotatedString {
                    append("A little better,\n")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("every day.") }
                }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
                FilledTonalIconButton(onClick = { vm.overlay = Overlay.RepoForm() }, modifier = Modifier.size(50.dp).testTag("new-repo")) {
                    Icon(Icons.Outlined.Add, "Create a new repo")
                }
            }
            Subtitle(if (state.name.isBlank()) "Your personal workspace."
                else "Welcome back, ${state.name.trim().substringBefore(' ')}. Let’s make today count.")
        }
        StatsRow(listOf(Triple("Goals reached\ntoday", "$complete", "/ ${active.size}"),
            Triple("Current activity\nstreak", "${state.activityStreak(today)}", "days"),
            Triple("Total\ncontributions", "${state.commits.size}", "")))
        Heatmap(state, today) { vm.overlay = Overlay.Day(it) }
        SectionHeading("Today’s repos · ${active.size}") {
            FilterChip(selected = onlyRemaining, onClick = { onlyRemaining = !onlyRemaining }, label = { Text("To do", fontSize = 12.sp) })
        }
        if (visible.isEmpty()) {
            if (active.isEmpty()) EmptyState("Your next chapter starts here.", "Create your first repo and let a small intention grow.") { vm.overlay = Overlay.RepoForm() }
            else EmptyState("That’s a wrap for today.", "Every goal reached. Take a moment to enjoy that.")
        }
        visible.forEach { RepoCard(vm, it) }
        Panel {
            SectionHeading("Daily focus")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(114.dp), Alignment.Center) {
                    CircularProgressIndicator(progress = { if (active.isEmpty()) 0f else complete.toFloat() / active.size },
                        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeWidth = 6.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$complete / ${active.size}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("goals reached", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You’re making progress.", style = MaterialTheme.typography.titleMedium)
                    Subtitle("You don’t need a perfect day. Just one small step forward.")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Subtitle("Overall goal progress: ${((state.dailyScore(today) ?: 0.0) * 100).roundToInt()}%")
        }
        Panel {
            SectionHeading("Recent commits") {
                IconButton(onClick = { vm.navigate(Destination.ACTIVITY) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "View all activity") }
            }
            if (state.commits.isEmpty()) Subtitle("Your first commit is the start of something good.")
            state.commits.sortedByDescending { it.timestamp }.take(3).forEach { commit ->
                CommitRow(state, commit, vm.zone) { vm.overlay = Overlay.LogForm(commit.habitId, commit.id) }
            }
        }
        Footer()
    }
}

@Composable
private fun RepoCard(vm: GitHabitViewModel, habit: Habit) {
    HabitCard(vm.workspace, habit, vm.today, vm.busy,
        onOpen = { vm.navigate(Destination.DETAIL, habit.id) },
        onLog = { vm.overlay = Overlay.LogForm(habit.id) },
        onComplete = { vm.complete(habit.id) }, onResume = { vm.status(habit.id, RepoStatus.ACTIVE) })
}

@Composable
fun ReposScreen(vm: GitHabitViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    val repos = vm.workspace.habits.filter {
        (filter == "All" || it.versionOn(vm.today)?.status?.name.equals(filter, ignoreCase = true)) &&
            (it.name.contains(query, true) || it.description.contains(query, true))
    }
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("Your repositories") { IconButton(onClick = { vm.overlay = Overlay.RepoForm() }) { Icon(Icons.Outlined.Add, "Create a new repo") } }
            Subtitle("Small projects. A bigger picture.")
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("repo-search"),
            label = { Text("Find a repository") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Active", "Paused", "Archived", "All").forEach { value ->
                FilterChip(selected = value == filter, onClick = { filter = value }, label = { Text(value) })
            }
        }
        if (repos.isEmpty()) EmptyState("No matching repos.", "Try another filter, or start a new daily intention.") { vm.overlay = Overlay.RepoForm() }
        repos.forEach { RepoCard(vm, it) }
        Footer()
    }
}

@Composable
fun DetailScreen(vm: GitHabitViewModel) {
    val state = vm.workspace
    val habit = state.habits.find { it.id == vm.selectedRepoId }
    if (habit == null) { EmptyState("This repo isn’t here.", "Return to your repositories to choose another habit."); return }
    val progress = state.progress(habit, vm.today)
    val status = habit.versionOn(vm.today)?.status ?: RepoStatus.ACTIVE
    var archiveConfirmation by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable(habit.id) { mutableIntStateOf(20) }
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitGlyph(habit, 48)
                Text(habit.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
            Subtitle(habit.description)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status.name.lowercase(), status == RepoStatus.ACTIVE)
                OutlinedButton(onClick = { vm.overlay = Overlay.RepoForm(habit.id) }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Edit repo")
                }
            }
        }
        Panel {
            SectionHeading("Today’s progress")
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatAmount(progress.total), fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("detail-total"))
                Text(" / ${formatAmount(progress.target)} ${habit.unit}", Modifier.padding(bottom = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { (progress.total / progress.target).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(5.dp), color = habitColor(habit.color),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
            if (status == RepoStatus.ACTIVE) {
                Button(onClick = { vm.overlay = Overlay.LogForm(habit.id) }, enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Log progress") }
                TextButton(onClick = { vm.complete(habit.id) }, enabled = !progress.complete && !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text(if (progress.complete) "Daily goal reached" else "Complete remaining goal")
                }
            } else {
                Subtitle("Your history is safe. Resume whenever you’re ready.")
                Button(onClick = { vm.status(habit.id, RepoStatus.ACTIVE) }, enabled = !vm.busy) { Text("Resume repo") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == RepoStatus.ACTIVE) OutlinedButton(onClick = { vm.status(habit.id, RepoStatus.PAUSED) }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Pause, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Pause")
                }
                if (status != RepoStatus.ARCHIVED) OutlinedButton(onClick = { archiveConfirmation = true }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Archive, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Archive")
                }
            }
        }
        Heatmap(state, vm.today, habit.id) { vm.overlay = Overlay.Day(it, habit.id) }
        StatsRow(listOf(Triple("Activity\nstreak", "${state.activityStreak(vm.today, habit.id)}", "days"),
            Triple("Longest\nstreak", "${state.longestStreak(vm.today, habit.id)}", "days"),
            Triple("Total\ncommits", "${state.commits.count { it.habitId == habit.id }}", "")))
        HistoryList(state, state.commits.filter { it.habitId == habit.id }, limit, vm.zone,
            onMore = { limit += 30 }, onEdit = { vm.overlay = Overlay.LogForm(it.habitId, it.id) })
    }
    if (archiveConfirmation) ConfirmDialog("Close this chapter?", "Archive this repo without removing any commits. You can resume it at any time.",
        "Archive repo", onDismiss = { archiveConfirmation = false }, onConfirm = { archiveConfirmation = false; vm.status(habit.id, RepoStatus.ARCHIVED) })
}

@Composable
internal fun CommitRow(state: Workspace, commit: Commit, zone: ZoneId, onEdit: () -> Unit) {
    val habit = state.habits.firstOrNull { it.id == commit.habitId } ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitGlyph(habit, 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(habit.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (commit.note.isNotBlank()) Subtitle(commit.note)
            Text("${dateLabel(commit.date)} · ${commit.timestamp.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))}",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("+${formatAmount(commit.amount)}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            Text(habit.unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit, modifier = Modifier.testTag("edit-commit-${commit.id}")) {
            Icon(Icons.Outlined.Edit, "Edit ${habit.name} commit", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HistoryList(state: Workspace, commits: List<Commit>, limit: Int, zone: ZoneId, onMore: () -> Unit, onEdit: (Commit) -> Unit) {
    Panel {
        SectionHeading("Commit history") { StatusBadge("${commits.size} commits") }
        if (commits.isEmpty()) Subtitle("No commits yet. Your story is still being written.")
        commits.sortedByDescending { it.timestamp }.take(limit).forEachIndexed { index, commit ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CommitRow(state, commit, zone) { onEdit(commit) }
        }
        if (commits.size > limit) TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("Show more commits") }
    }
}

@Composable
fun ActivityScreen(vm: GitHabitViewModel, onExport: () -> Unit) {
    var limit by rememberSaveable { mutableIntStateOf(20) }
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your changelog", style = MaterialTheme.typography.headlineMedium)
            Subtitle("A record of the little things that add up.")
            OutlinedButton(onClick = onExport) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export workspace") }
        }
        HistoryList(vm.workspace, vm.workspace.commits, limit, vm.zone, { limit += 30 }) { vm.overlay = Overlay.LogForm(it.habitId, it.id) }
    }
}

@Composable
fun InsightsScreen(vm: GitHabitViewModel) {
    val state = vm.workspace
    val days = daysEnding(vm.today, 7)
    val average = (state.averageProgress(days) * 100).roundToInt()
    val commits = state.commits.filter { it.date in days }
    val activeDays = commits.map { it.date }.distinct().size
    val ranks = state.habits.filter { habit -> days.any { state.dailyScore(it, habit.id) != null } }
        .map { it to state.averageProgress(days, it.id) }.sortedByDescending { it.second }
    PageColumn {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The bigger picture", style = MaterialTheme.typography.headlineMedium)
            Subtitle("Progress you can see. Consistency you can feel.")
        }
        StatsRow(listOf(Triple("Average goal\nprogress", "$average%", ""), Triple("Days you\nshowed up", "$activeDays", "/ 7"),
            Triple("This week’s\ncommits", "${commits.size}", "")))
        Panel {
            SectionHeading("A week of small wins")
            Subtitle("Average progress across each day’s active repos")
            Row(Modifier.fillMaxWidth().height(194.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    val value = state.dailyScore(day)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom)) {
                        Text(if (value == null) "—" else "${(value * 100).roundToInt()}%", fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height((maxOf(value ?: 0.0, .02) * 130).dp).clip(RoundedCornerShape(7.dp, 7.dp, 3.dp, 3.dp))
                            .background(if (day == vm.today) MaterialTheme.colorScheme.primary else heatColors()[2]))
                        Text(day.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Panel {
            SectionHeading("Your steady habits")
            Subtitle("Average daily goal progress this week")
            if (ranks.isEmpty()) Subtitle("Your habits will find their rhythm here.")
            ranks.forEach { (habit, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HabitGlyph(habit, 36)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(habit.name, fontSize = 12.sp)
                        LinearProgressIndicator(progress = { value.toFloat() }, modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = habitColor(habit.color), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
                    }
                    Text("${(value * 100).roundToInt()}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Panel {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text(if (activeDays > 0) "You made time for yourself on $activeDays of the last 7 days." else "Your first small win is waiting.",
                style = MaterialTheme.typography.titleMedium)
            Subtitle("Partial progress counts, too. Focus on returning, not on being perfect.")
        }
        Heatmap(state, vm.today) { vm.overlay = Overlay.Day(it) }
    }
}

@Composable
private fun Footer() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.local_footer), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
