package com.githabit.prototype.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githabit.prototype.*
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
fun SignInScreen(vm: GitHabitViewModel) {
    val dark = LocalDarkTheme.current
    // Deliberately not rememberSaveable: credentials must never enter saved state or storage.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    PageColumn(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Brand(); Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.setTheme(!dark) }) {
                Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, "Switch theme")
            }
        }
        Spacer(Modifier.height(2.dp))
        Eyebrow("A LITTLE PROGRESS. EVERY SINGLE DAY.")
        Text(buildAnnotatedString {
            append("Small commits.\n")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Meaningful change.") }
        }, style = MaterialTheme.typography.displaySmall)
        Subtitle("Your life is your most important project. Build better habits, one contribution at a time.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your next chapter starts here.", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { vm.signIn() }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("google-sign-in")) {
                Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp))
                Text("Continue with Google"); Spacer(Modifier.width(10.dp)); StatusBadge("DEMO")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text("or try the email flow", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            OutlinedTextField(email, { email = it }, label = { Text("Demo email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("demo-email"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("Demo password") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("demo-password"), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), supportingText = { Text("Use made-up details, never your real password.") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                if (!email.contains('@') || password.length < 6) error = "Use a demo email and at least 6 made-up password characters."
                else { email = ""; password = ""; error = null; vm.signIn() }
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("email-sign-in")) {
                Text("Continue"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Subtitle("Local prototype, not real sign-in. Credentials are never saved or sent. All demo users share this device’s workspace.")
                }
            }
            TextButton(onClick = { vm.explore() }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().testTag("explore-demo")) {
                Text("Explore with sample habits"); Spacer(Modifier.width(7.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun OnboardingScreen(vm: GitHabitViewModel) {
    val titles = listOf("Create your first repo.", "Make a small commit.", "Watch consistency grow.")
    val descriptions = listOf(
        "Every habit is a little project. Give yours a name and a daily goal—like reading 20 pages or moving for 30 minutes.",
        "Log a little progress throughout your day, or complete your remaining goal with one tap. Every step forward counts.",
        "Your heatmap turns greener as you get closer to your goals. Build a history of showing up, without chasing perfection.")
    val step = vm.onboardingStep
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Brand()
        Box(Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
            Icon(when (step) { 0 -> Icons.Outlined.FolderOpen; 1 -> Icons.Outlined.Commit; else -> Icons.Outlined.GridView },
                null, Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.width(22.dp).height(4.dp).clip(CircleShape)
                .background(if (step == it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
        }
        Text(titles[step], style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(descriptions[step], style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (step < 2) {
            Button(onClick = { vm.onboardingStep++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Continue") }
            TextButton(onClick = { vm.onboardingStep = 2 }) { Text("Skip introduction") }
        } else {
            Button(onClick = { vm.startEmpty() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(5.dp)); Text("Create my first repo")
            }
            TextButton(onClick = { vm.explore() }, enabled = !vm.busy) { Text("Try it with sample habits first") }
        }
    }
}

@Composable
fun TodayScreen(vm: GitHabitViewModel) {
    val state = vm.workspace
    val today = vm.today
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
                CommitRow(state, commit) { vm.overlay = Overlay.LogForm(commit.habitId, commit.id) }
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
        HistoryList(state, state.commits.filter { it.habitId == habit.id }, limit,
            onMore = { limit += 30 }, onEdit = { vm.overlay = Overlay.LogForm(it.habitId, it.id) })
    }
    if (archiveConfirmation) ConfirmDialog("Close this chapter?", "Archive this repo without removing any commits. You can resume it at any time.",
        "Archive repo", onDismiss = { archiveConfirmation = false }, onConfirm = { archiveConfirmation = false; vm.status(habit.id, RepoStatus.ARCHIVED) })
}

@Composable
internal fun CommitRow(state: Workspace, commit: Commit, onEdit: () -> Unit) {
    val habit = state.habits.firstOrNull { it.id == commit.habitId } ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitGlyph(habit, 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(habit.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (commit.note.isNotBlank()) Subtitle(commit.note)
            Text("${dateLabel(commit.date)} · ${commit.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))}",
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
private fun HistoryList(state: Workspace, commits: List<Commit>, limit: Int, onMore: () -> Unit, onEdit: (Commit) -> Unit) {
    Panel {
        SectionHeading("Commit history") { StatusBadge("${commits.size} commits") }
        if (commits.isEmpty()) Subtitle("No commits yet. Your story is still being written.")
        commits.sortedByDescending { it.timestamp }.take(limit).forEachIndexed { index, commit ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CommitRow(state, commit) { onEdit(commit) }
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
        HistoryList(vm.workspace, vm.workspace.commits, limit, { limit += 30 }) { vm.overlay = Overlay.LogForm(it.habitId, it.id) }
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
fun SettingsScreen(vm: GitHabitViewModel, onExport: () -> Unit) {
    var name by rememberSaveable(vm.workspace.name) { mutableStateOf(vm.workspace.name) }
    var bio by rememberSaveable(vm.workspace.bio) { mutableStateOf(vm.workspace.bio) }
    var resetConfirmation by rememberSaveable { mutableStateOf(false) }
    PageColumn(Modifier.imePadding()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make it your space", style = MaterialTheme.typography.headlineMedium)
            Subtitle("A few preferences for your everyday.")
        }
        Panel {
            SectionHeading("Personal workspace")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                    if (vm.workspace.name.isBlank()) Icon(Icons.Outlined.PersonOutline, null)
                    else Text(vm.workspace.name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                }
                Column { Text(vm.workspace.name.ifBlank { "Your workspace" }, style = MaterialTheme.typography.titleLarge); Subtitle("Local workspace") }
            }
            OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { if (it.length <= 160) bio = it }, label = { Text("A note to yourself") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.saveProfile(name, bio) }, enabled = !vm.busy) { Text("Save profile") }
        }
        Panel {
            SectionHeading("Look & feel")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Dark theme", style = MaterialTheme.typography.titleMedium); Subtitle("A familiar green, in your favorite light.") }
                Switch(checked = LocalDarkTheme.current, onCheckedChange = { vm.setTheme(it) }, modifier = Modifier.testTag("theme-switch"))
            }
        }
        Panel {
            SectionHeading("Your data, your device")
            SettingsAction("Export your workspace", "Save your habits and history as JSON.", Icons.Outlined.Download, onExport)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsAction("Replay the introduction", "Repos, commits, and consistency.", Icons.AutoMirrored.Outlined.HelpOutline) { vm.overlay = Overlay.Guide }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsAction("Activity log", "Review or correct your contributions.", Icons.Outlined.History) { vm.navigate(Destination.ACTIVITY) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsAction("Sign out of the demo", "Local data stays on this device.", Icons.AutoMirrored.Outlined.Logout) { vm.signOut() }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { resetConfirmation = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Reset this prototype")
            }
        }
        Subtitle("GitHabit is a native design prototype. Sign-in is simulated. There is no cloud sync, real account, or background notification service. Data is stored only on this device; uninstalling erases it. JSON export does not include an import feature.")
        Footer()
    }
    if (resetConfirmation) ConfirmDialog("Start with a clean slate?", "This removes all local repos, commits, and preferences. Export a copy first if you want to keep it.",
        "Reset everything", destructive = true, onDismiss = { resetConfirmation = false }, onConfirm = { resetConfirmation = false; vm.reset() })
}

@Composable
private fun SettingsAction(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Surface(onClick = action, color = Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium); Subtitle(detail) }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Footer() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Made of small, everyday commits.  ·  LOCAL DEMO", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
