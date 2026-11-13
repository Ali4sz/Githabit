package com.githabit.prototype.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.Overlay
import com.githabit.prototype.FormState
import com.githabit.prototype.ScheduleChoice
import com.githabit.prototype.proposedWeeklyTransition
import com.githabit.prototype.R
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private fun editableAmount(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
private fun parseAmount(text: String): Double = positive(text.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSheet(vm: GitHabitViewModel, savedState: SaveableStateHolder) {
    val overlay = vm.overlay ?: return
    var presentation by remember(overlay) { mutableIntStateOf(0) }
    key(overlay, presentation) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                if (value == SheetValue.Hidden && (vm.busy || vm.formState?.dirty == true)) {
                    vm.requestDismissForm()
                    false
                } else true
            })
        ModalBottomSheet(
            onDismissRequest = { vm.requestDismissForm() },
            sheetState = sheetState,
            modifier = Modifier.testTag("workspace-sheet").semantics { stateDescription = sheetState.currentValue.name },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(Modifier.testTag("sheet-drag-handle")) },
        ) {
            // Inside the dialog's composition/registry, not around the dialog itself.
            savedState.SaveableStateProvider("form") {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("YOUR LOCAL WORKSPACE", Modifier.weight(1f))
                        IconButton(onClick = { vm.requestDismissForm() }, enabled = !vm.busy, modifier = Modifier.testTag("close-sheet")) { Icon(Icons.Outlined.Close, "Close sheet") }
                    }
                    if (vm.discardConfirmation) ConfirmDialog(stringResource(R.string.discard_title),
                        stringResource(R.string.discard_message), stringResource(R.string.discard_action),
                        destructive = true, onDismiss = { vm.cancelDiscard(); presentation++ }, onConfirm = vm::confirmDiscard)
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
    val form = vm.formState as? FormState.Repo ?: return
    val intendedDate = form.intendedDate.toString()
    val (name, description, target, unit, icon, color) = form.values
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val unitLocked = vm.workspace.commits.any { it.habitId == id }
    Text(stringResource(if (id == null) R.string.repo_form_new else R.string.repo_form_edit), style = MaterialTheme.typography.headlineMedium)
    Subtitle(stringResource(R.string.repo_form_instruction))
    Subtitle("Changes apply on ${dateLabel(LocalDate.parse(intendedDate))}.")
    if (id != null && habit == null) Text("This repo is no longer available. Close and reopen the form.", color = MaterialTheme.colorScheme.error)
    OutlinedTextField(name, { text -> if (text.length <= 48) vm.editRepoForm { it.copy(name = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-name"),
        label = { Text("Repo name") }, enabled = !vm.busy, singleLine = true)
    OutlinedTextField(description, { text -> if (text.length <= 160) vm.editRepoForm { it.copy(description = text) } }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Your why (optional)") }, enabled = !vm.busy, maxLines = 3)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(target, { text -> if (text.length <= 256) vm.editRepoForm { it.copy(target = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-target"),
            label = { Text("Daily target") }, enabled = !vm.busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(unit, { text -> if (text.length <= 20) vm.editRepoForm { it.copy(unit = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-unit"),
            label = { Text("Unit") }, singleLine = true, enabled = !unitLocked && !vm.busy)
    }
    if (unitLocked) Subtitle("Units stay fixed after your first commit. Goal changes apply from today; older days keep their target.")
    ScheduleControls(vm, form)
    val transition = runCatching {
        vm.workspace.proposedWeeklyTransition(id, form.values.draft(), form.intendedDate)
    }.getOrDefault(false)
    if (transition) Text(stringResource(R.string.schedule_transition_explanation),
        modifier = Modifier.testTag("schedule-transition"), style = MaterialTheme.typography.bodyMedium)
    SectionHeading("Make it recognizable")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitIcon.entries.forEach { candidate ->
            FilledTonalIconToggleButton(checked = icon == candidate, enabled = !vm.busy, onCheckedChange = { vm.editRepoForm { it.copy(icon = candidate) } }) {
                Icon(habitIcon(candidate), "${candidate.name.lowercase()} icon")
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitColor.entries.forEach { candidate ->
            FilterChip(selected = color == candidate, enabled = !vm.busy, onClick = { vm.editRepoForm { it.copy(color = candidate) } },
                label = { Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = { Icon(Icons.Outlined.Circle, null, Modifier.size(14.dp), tint = habitColor(candidate)) })
        }
    }
    error?.let { FormError(it) }
    if (vm.formSaveFailed && error == null) FormError(stringResource(R.string.form_save_failed))
    if (vm.busy) SavingFeedback()
    Button(onClick = {
        try {
            val draft = form.values.draft()
            draft.normalized() // Validate here; retain untouched legacy strings for the command.
            error = null
             vm.saveHabit(id, draft, form.intendedDate, reviewedConfiguration = form.reviewedConfiguration,
                 onConfigurationChanged = {
                     vm.reviewFormDate(vm.today)
                     error = "This repo’s dated configuration changed. Review your draft and save again."
                 }) { revised ->
                  vm.reviewFormDate(revised)
                 error = "The local date changed to ${dateLabel(revised)}. Review your draft and save again."
             }
        } catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy && (id == null || habit != null), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-repo")) {
        Text(if (id == null) "Create repo" else "Save changes")
    }
}

@Composable
private fun ScheduleControls(vm: GitHabitViewModel, form: FormState.Repo) {
    val values = form.values
    SectionHeading(stringResource(R.string.schedule_label))
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        ScheduleChoice.entries.forEach { choice ->
            val label = stringResource(when (choice) {
                ScheduleChoice.DAILY -> R.string.schedule_daily
                ScheduleChoice.WEEKDAYS -> R.string.schedule_weekdays
                ScheduleChoice.WEEKLY -> R.string.schedule_weekly
            })
            SelectionRow(label, values.choice == choice, !vm.busy, "schedule-${choice.name}") {
                vm.editRepoForm { it.copy(choice = choice) }
            }
        }
    }
    when (values.choice) {
        ScheduleChoice.DAILY -> Subtitle(stringResource(R.string.schedule_daily_instruction))
        ScheduleChoice.WEEKDAYS -> {
            Subtitle(stringResource(R.string.schedule_weekday_instruction))
            val start = vm.workspace.calendar.effectiveWeekStart(vm.today)
            Column {
                (0..6).map { DayOfWeek.of((start.value - 1 + it) % 7 + 1) }.forEach { day ->
                    key(day) {
                        val bit = 1 shl (day.value - 1)
                        val checked = values.weekdayMask and bit != 0
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).focusOutline()
                            .testTag("weekday-${day.name}").toggleable(checked, enabled = !vm.busy, role = Role.Checkbox,
                                onValueChange = { selected -> vm.editRepoForm {
                                    it.copy(weekdayMask = if (selected) it.weekdayMask or bit else it.weekdayMask and bit.inv())
                                } }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = null, enabled = !vm.busy)
                            Text(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), Modifier.weight(1f).padding(start = 12.dp))
                        }
                    }
                }
            }
        }
        ScheduleChoice.WEEKLY -> {
            SectionHeading(stringResource(R.string.schedule_weekly_count))
            Column(Modifier.selectableGroup()) {
                (1..7).forEach { n ->
                    SelectionRow(pluralStringResource(R.plurals.schedule_day_count, n, n), values.weeklyDays == "$n", !vm.busy, "weekly-days-$n") {
                        vm.editRepoForm { it.copy(weeklyDays = "$n") }
                    }
                }
            }
            val n = values.weeklyDays.toIntOrNull()?.coerceIn(1, 7) ?: 1
            Text(pluralStringResource(R.plurals.schedule_weekly_instruction, n, n), Modifier.testTag("weekly-explanation"))
            Subtitle(stringResource(R.string.schedule_weekly_target_instruction))
            WeekPeriodSummary(vm.workspace.calendar.periodOn(vm.today))
        }
    }
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).focusOutline().testTag(tag)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun FormError(message: String) {
    Text(message, Modifier.fillMaxWidth().testTag("form-error").semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SavingFeedback() {
    Text(stringResource(R.string.form_saving), Modifier.testTag("form-saving").semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun LogForm(vm: GitHabitViewModel, overlay: Overlay.LogForm) {
    val habit = vm.workspace.habits.find { it.id == overlay.habitId }
    if (habit == null) {
        Text("This repo is no longer available. Close and reopen the form.", color = MaterialTheme.colorScheme.error)
        return
    }
    val commit = vm.workspace.commits.find { it.id == overlay.commitId }
    val correcting = overlay.commitId != null
    val form = vm.formState as? FormState.Log ?: return
    val day = form.intendedDate
    val progress = vm.workspace.progress(habit, day)
    val (amount, note) = form.values
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removeConfirmation by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitGlyph(habit, 48)
        Column(Modifier.weight(1f)) {
            Text(if (!correcting) "Make a small commit" else "Correct a commit", style = MaterialTheme.typography.titleLarge)
            Subtitle(habit.name)
        }
    }
    Subtitle("${dateLabel(day)} · ${formatAmount(progress.total)} / ${formatAmount(progress.target)} ${habit.unit}")
    if (correcting && commit == null) Text("This commit is no longer available. Your draft was not saved as new progress.", color = MaterialTheme.colorScheme.error)
    OutlinedTextField(amount, { text -> if (text.length <= 256) vm.editLogForm { it.copy(amount = text) } }, label = { Text("Amount (${habit.unit})") }, singleLine = true, enabled = !vm.busy,
        modifier = Modifier.fillMaxWidth().testTag("commit-amount"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text("Partial progress counts. Decimals are welcome.") })
    if (!correcting && progress.remaining > 0) AssistChip(enabled = !vm.busy, onClick = { vm.editLogForm { it.copy(amount = editableAmount(progress.remaining)) } },
        label = { Text("Remaining: ${formatAmount(progress.remaining)} ${habit.unit}") }, leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
    OutlinedTextField(note, { text -> if (text.length <= 200) vm.editLogForm { it.copy(note = text) } }, label = { Text("Commit message (optional)") },
        placeholder = { Text(stringResource(R.string.commit_note_instruction)) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("commit-note"),
        minLines = 2, maxLines = 4, supportingText = { Text("${note.length} / 200") })
    error?.let { FormError(it) }
    if (vm.formSaveFailed && error == null) FormError(stringResource(R.string.form_save_failed))
    if (vm.busy) SavingFeedback()
    Button(onClick = {
        try {
            val value = parseAmount(amount)
            error = null
            vm.saveCommit(habit.id, overlay.commitId, value, note, day) { revised ->
                vm.reviewFormDate(revised)
                error = "The local date changed to ${dateLabel(revised)}. Review your draft and submit again."
            }
        }
        catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy && (!correcting || commit != null), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-commit")) {
        Icon(Icons.Outlined.Commit, null); Spacer(Modifier.width(8.dp)); Text(if (!correcting) "Commit progress" else "Save correction")
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
        CommitRow(state, commit, vm.zone) { vm.overlay = Overlay.LogForm(commit.habitId, commit.id) }
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
