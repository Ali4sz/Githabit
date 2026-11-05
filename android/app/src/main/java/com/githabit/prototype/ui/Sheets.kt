package com.githabit.prototype.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.Overlay
import com.githabit.prototype.domain.*
import java.time.LocalDate
import kotlin.math.roundToInt

private fun editableAmount(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
private fun parseAmount(text: String): Double = positive(text.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSheet(vm: GitHabitViewModel, savedState: SaveableStateHolder) {
    val overlay = vm.overlay ?: return
    key(overlay) {
        ModalBottomSheet(
            onDismissRequest = { if (!vm.busy) vm.overlay = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            // Inside the dialog's composition/registry, not around the dialog itself.
            savedState.SaveableStateProvider("form") {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("YOUR LOCAL WORKSPACE", Modifier.weight(1f))
                        IconButton(onClick = { vm.overlay = null }, enabled = !vm.busy, modifier = Modifier.testTag("close-sheet")) { Icon(Icons.Outlined.Close, "Close sheet") }
                    }
                    when (overlay) {
                        is Overlay.RepoForm -> RepoForm(vm, overlay.id)
                        is Overlay.LogForm -> LogForm(vm, overlay)
                        is Overlay.Day -> DayDetails(vm, overlay)
                        Overlay.Guide -> Guide { vm.overlay = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoForm(vm: GitHabitViewModel, id: String?) {
    val habit = vm.workspace.habits.find { it.id == id }
    var name by rememberSaveable { mutableStateOf(habit?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(habit?.description ?: "") }
    var target by rememberSaveable { mutableStateOf(habit?.versionOn(vm.today)?.target?.let(::editableAmount) ?: "20") }
    var unit by rememberSaveable { mutableStateOf(habit?.unit ?: "pages") }
    var icon by rememberSaveable { mutableStateOf(habit?.icon ?: HabitIcon.BOOK) }
    var color by rememberSaveable { mutableStateOf(habit?.color ?: HabitColor.GREEN) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val unitLocked = vm.workspace.commits.any { it.habitId == id }
    Text(if (id == null) "A new daily intention" else "Refine your repo", style = MaterialTheme.typography.headlineMedium)
    Subtitle("Start small. Make room for something that matters.")
    OutlinedTextField(name, { if (it.length <= 48) name = it }, modifier = Modifier.fillMaxWidth().testTag("repo-name"),
        label = { Text("Repo name") }, placeholder = { Text("Read every day") }, singleLine = true)
    OutlinedTextField(description, { if (it.length <= 160) description = it }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Your why (optional)") }, maxLines = 3)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(target, { target = it }, modifier = Modifier.weight(1f).testTag("repo-target"),
            label = { Text("Daily target") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(unit, { if (it.length <= 20) unit = it }, modifier = Modifier.weight(1f).testTag("repo-unit"),
            label = { Text("Unit") }, singleLine = true, enabled = !unitLocked)
    }
    if (unitLocked) Subtitle("Units stay fixed after your first commit. Goal changes apply from today; older days keep their target.")
    SectionHeading("Make it recognizable")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitIcon.entries.forEach { candidate ->
            FilledTonalIconToggleButton(checked = icon == candidate, onCheckedChange = { icon = candidate }) {
                Icon(habitIcon(candidate), "${candidate.name.lowercase()} icon")
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitColor.entries.forEach { candidate ->
            FilterChip(selected = color == candidate, onClick = { color = candidate },
                label = { Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = { Icon(Icons.Outlined.Circle, null, Modifier.size(14.dp), tint = habitColor(candidate)) })
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Button(onClick = {
        try {
            // Schedule controls arrive in Task 9. This interim form must retain an
            // imported/restored schedule rather than silently resetting it to Daily.
            val draft = HabitDraft(name, description, parseAmount(target), unit, icon, color,
                habit?.versionOn(vm.today)?.schedule ?: HabitSchedule.Daily)
            draft.normalized() // Validate here; retain untouched legacy strings for the command.
            error = null
            vm.saveHabit(id, draft)
        } catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-repo")) {
        Text(if (id == null) "Create repo" else "Save changes")
    }
}

@Composable
private fun LogForm(vm: GitHabitViewModel, overlay: Overlay.LogForm) {
    val habit = vm.workspace.habits.find { it.id == overlay.habitId } ?: return
    val commit = vm.workspace.commits.find { it.id == overlay.commitId }
    val day = commit?.date ?: vm.today
    val progress = vm.workspace.progress(habit, day)
    var amount by rememberSaveable { mutableStateOf(commit?.amount?.let(::editableAmount) ?: "") }
    var note by rememberSaveable { mutableStateOf(commit?.note ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removeConfirmation by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitGlyph(habit, 48)
        Column(Modifier.weight(1f)) {
            Text(if (commit == null) "Make a small commit" else "Correct a commit", style = MaterialTheme.typography.titleLarge)
            Subtitle(habit.name)
        }
    }
    Subtitle("${dateLabel(day)} · ${formatAmount(progress.total)} / ${formatAmount(progress.target)} ${habit.unit}")
    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (${habit.unit})") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("commit-amount"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text("Partial progress counts. Decimals are welcome.") })
    if (commit == null && progress.remaining > 0) AssistChip(onClick = { amount = editableAmount(progress.remaining) },
        label = { Text("Remaining: ${formatAmount(progress.remaining)} ${habit.unit}") }, leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
    OutlinedTextField(note, { if (it.length <= 200) note = it }, label = { Text("Commit message (optional)") },
        placeholder = { Text("A quiet chapter with my morning coffee.") }, modifier = Modifier.fillMaxWidth().testTag("commit-note"),
        minLines = 2, maxLines = 4, supportingText = { Text("${note.length} / 200") })
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Button(onClick = {
        try { val value = parseAmount(amount); error = null; vm.saveCommit(habit.id, commit?.id, value, note) }
        catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-commit")) {
        Icon(Icons.Outlined.Commit, null); Spacer(Modifier.width(8.dp)); Text(if (commit == null) "Commit progress" else "Save correction")
    }
    if (commit != null) {
        TextButton(onClick = { removeConfirmation = true }, enabled = !vm.busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Delete this commit") }
    }
    if (removeConfirmation && commit != null) ConfirmDialog("Remove this commit?", "Your totals and heatmaps will update. You can undo this change.",
        "Delete commit", destructive = true, onDismiss = { removeConfirmation = false },
        onConfirm = { removeConfirmation = false; vm.removeCommit(commit.id) })
}

@Composable
private fun DayDetails(vm: GitHabitViewModel, overlay: Overlay.Day) {
    var dateText by rememberSaveable(overlay.date) { mutableStateOf(overlay.date.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val day = overlay.date
    val state = vm.workspace
    val score = state.dailyScore(day, overlay.habitId)
    Text("A day in your story", style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.overlay = overlay.copy(date = day.minusDays(1)) }) { Icon(Icons.Outlined.ChevronLeft, "Previous day") }
        Text(dateLabel(day), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.overlay = overlay.copy(date = day.plusDays(1)) }, enabled = day < vm.today) { Icon(Icons.Outlined.ChevronRight, "Next day") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(dateText, { dateText = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
            modifier = Modifier.weight(1f).testTag("explore-date"))
        OutlinedButton(onClick = {
            val parsed = runCatching { LocalDate.parse(dateText) }.getOrNull()
            if (parsed == null || parsed > vm.today) error = "Choose a valid date, no later than today."
            else { error = null; vm.overlay = overlay.copy(date = parsed) }
        }) { Text("Go") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text(if (score == null) "No goals scheduled" else "${(score * 100).roundToInt()}% daily goal progress", style = MaterialTheme.typography.titleMedium)
    Subtitle("Scores average each active repo’s progress, capped at 100%. Paused and archived repos don’t count toward that day’s score.")
    state.habits.filter { (overlay.habitId == null || it.id == overlay.habitId) && it.createdOn <= day }.forEach { habit ->
        val value = state.progress(habit, day)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HabitGlyph(habit, 36)
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Subtitle("${formatAmount(value.total)} / ${formatAmount(value.target)} ${habit.unit}")
            }
            StatusBadge(if (value.eligible) "${((value.ratio ?: 0.0) * 100).roundToInt()}%" else "Not scheduled", value.complete)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SectionHeading("Commits on this day")
    val commits = state.commits.filter { it.date == day && (overlay.habitId == null || it.habitId == overlay.habitId) }
    if (commits.isEmpty()) Subtitle("No commits on this day. There’s always another small beginning.")
    commits.sortedByDescending { it.timestamp }.forEach { commit ->
        CommitRow(state, commit) { vm.overlay = Overlay.LogForm(commit.habitId, commit.id) }
    }
}

@Composable
private fun Guide(onDone: () -> Unit) {
    Text("Small commits. Real momentum.", style = MaterialTheme.typography.headlineMedium)
    listOf(
        "1 · Create a repo" to "A repo is a habit with a numeric daily goal, like 20 pages or 30 minutes.",
        "2 · Commit your progress" to "Log any positive amount, or tap the checkmark to add only what remains of today’s goal.",
        "3 · See the bigger picture" to "Each heatmap square shows that day’s goal progress. Overall squares average the active repos; excess progress never inflates the score.",
        "4 · Keep your history" to "Correct commits, pause a repo, or archive it. Target and status changes apply from today without rewriting earlier days. Activity streaks count any day with a commit, not just completed goals.",
    ).forEach { (title, text) -> Panel(Modifier.fillMaxWidth()) { Text(title, style = MaterialTheme.typography.titleMedium); Subtitle(text) } }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Ready for a little progress") }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, destructive: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
