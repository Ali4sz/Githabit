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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
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

private fun editableAmount(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
private fun parseAmount(text: String): Double = positive(text.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSheet(vm: GitHabitViewModel, savedState: SaveableStateHolder) {
    val overlay = vm.overlay ?: return
    val identity = if (overlay is Overlay.Day) "day:${overlay.habitId.orEmpty()}" else overlay
    LaunchedEffect(identity) {
        // Inspection retains only its date/list state, not errors from a finished correction.
        if (overlay is Overlay.Day) savedState.removeState("form")
    }
    var presentation by remember(identity) { mutableIntStateOf(0) }
    key(identity, presentation) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                if (value == SheetValue.Hidden && (vm.busy || vm.formState?.dirty == true)) {
                    vm.requestDismissForm()
                    false
                } else true
            })
        val sheetDescription = stringResource(when (sheetState.currentValue) {
            SheetValue.Hidden -> R.string.sheet_hidden
            SheetValue.PartiallyExpanded -> R.string.sheet_partial
            SheetValue.Expanded -> R.string.sheet_expanded
        })
        ModalBottomSheet(
            onDismissRequest = { vm.requestDismissForm() },
            sheetState = sheetState,
            modifier = Modifier.testTag("workspace-sheet").semantics { stateDescription = sheetDescription },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(Modifier.testTag("sheet-drag-handle")) },
        ) {
            // Inside the dialog's composition/registry, not around the dialog itself.
            savedState.SaveableStateProvider(if (overlay is Overlay.Day) "day" else "form") {
                val container = if (overlay is Overlay.Day) Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .85f)
                    else Modifier.verticalScroll(rememberScrollState())
                Column(Modifier.fillMaxWidth().then(container)
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                         Eyebrow(stringResource(R.string.sheet_workspace), Modifier.weight(1f))
                         IconButton(onClick = { vm.requestDismissForm() }, enabled = !vm.busy, modifier = Modifier.focusOutline().testTag("close-sheet")) { Icon(Icons.Outlined.Close, stringResource(R.string.sheet_close)) }
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
    val context = LocalContext.current
    val locale = displayLocale()
    val habit = vm.workspace.habits.find { it.id == id }
    val form = vm.formState as? FormState.Repo ?: return
    val intendedDate = form.intendedDate.toString()
    val (name, description, target, unit, icon, color) = form.values
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val unitLocked = vm.workspace.commits.any { it.habitId == id }
    Text(stringResource(if (id == null) R.string.repo_form_new else R.string.repo_form_edit), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Subtitle(stringResource(R.string.repo_form_instruction))
    Subtitle(stringResource(R.string.form_changes_date, localizedDateLabel(LocalDate.parse(intendedDate))))
    if (id != null && habit == null) FormError(stringResource(R.string.form_repo_missing))
    LegacyTextInput(name, { text -> if (text.length <= 48) vm.editRepoForm { it.copy(name = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-name"),
        label = stringResource(R.string.form_repo_name), enabled = !vm.busy, singleLine = true)
    LegacyTextInput(description, { text -> if (text.length <= 160) vm.editRepoForm { it.copy(description = text) } }, modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.form_repo_why), enabled = !vm.busy, maxLines = 3)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(target, { text -> if (text.length <= 256) vm.editRepoForm { it.copy(target = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-target"),
            label = { Text(stringResource(R.string.form_daily_target)) }, enabled = !vm.busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text(stringResource(R.string.form_decimal_hint)) })
        LegacyTextInput(unit, { text -> if (text.length <= 20) vm.editRepoForm { it.copy(unit = text) } }, modifier = Modifier.fillMaxWidth().testTag("repo-unit"),
            label = stringResource(R.string.form_unit), singleLine = true, enabled = !unitLocked && !vm.busy)
    }
    if (unitLocked) Subtitle(stringResource(R.string.form_unit_locked))
    ScheduleControls(vm, form)
    val transition = remember(vm.workspace, id, form.values, form.intendedDate) { runCatching {
        vm.workspace.proposedWeeklyTransition(id, form.values.draft(), form.intendedDate)
    }.getOrDefault(false) }
    if (transition) Text(stringResource(R.string.schedule_transition_explanation),
        modifier = Modifier.testTag("schedule-transition"), style = MaterialTheme.typography.bodyMedium)
    SectionHeading(stringResource(R.string.form_recognizable))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitIcon.entries.forEach { candidate ->
            FilledTonalIconToggleButton(checked = icon == candidate, enabled = !vm.busy, onCheckedChange = { vm.editRepoForm { it.copy(icon = candidate) } }) {
                Icon(habitIcon(candidate), stringResource(when (candidate) {
                    HabitIcon.BOOK -> R.string.icon_book
                    HabitIcon.MOVEMENT -> R.string.icon_movement
                    HabitIcon.CODE -> R.string.icon_code
                    HabitIcon.WATER -> R.string.icon_water
                    HabitIcon.CALM -> R.string.icon_calm
                    HabitIcon.JOURNAL -> R.string.icon_journal
                }))
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitColor.entries.forEach { candidate ->
            FilterChip(selected = color == candidate, enabled = !vm.busy, onClick = { vm.editRepoForm { it.copy(color = candidate) } },
                label = { Text(stringResource(when (candidate) {
                    HabitColor.GREEN -> R.string.color_green
                    HabitColor.ORANGE -> R.string.color_orange
                    HabitColor.BLUE -> R.string.color_blue
                    HabitColor.TEAL -> R.string.color_teal
                    HabitColor.PURPLE -> R.string.color_purple
                    HabitColor.PINK -> R.string.color_pink
                })) },
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
                      error = context.getString(R.string.form_configuration_review)
                 }) { revised ->
                  vm.reviewFormDate(revised)
                  error = context.getString(R.string.form_date_review, dateLabel(revised, locale))
             }
        } catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy && (id == null || habit != null), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-repo")) {
         Text(stringResource(if (id == null) R.string.form_create else R.string.form_save))
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
                             Text(weekdayLabel(day), Modifier.weight(1f).padding(start = 12.dp))
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
    Text(diagnosticMessage(message), Modifier.fillMaxWidth().testTag("form-error").semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SavingFeedback() {
    Text(stringResource(R.string.form_saving), Modifier.testTag("form-saving").semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun LogForm(vm: GitHabitViewModel, overlay: Overlay.LogForm) {
    val context = LocalContext.current
    val locale = displayLocale()
    val habit = vm.workspace.habits.find { it.id == overlay.habitId }
    if (habit == null) {
        FormError(stringResource(R.string.form_repo_missing))
        return
    }
    val commit = vm.workspace.commits.find { it.id == overlay.commitId }
    val correcting = overlay.commitId != null
    val form = vm.formState as? FormState.Log ?: return
    val day = form.intendedDate
    val index = vm.analytics
    val daily = remember(index, habit.id, day) { index.daily(habit.id, day) }
    val progress = daily.progress
    val (amount, note) = form.values
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removeConfirmation by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitGlyph(habit, 48)
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (!correcting) R.string.form_log_title else R.string.form_correct_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Subtitle(displayPreview(habit.name))
        }
    }
    Subtitle(stringResource(R.string.form_log_summary, localizedDateLabel(day), amountLabel(progress.total), amountLabel(progress.target ?: habit.versions.first().target), displayPreview(habit.unit)))
    if (correcting && commit == null) FormError(stringResource(R.string.form_commit_missing))
    OutlinedTextField(amount, { text -> if (text.length <= 256) vm.editLogForm { it.copy(amount = text) } }, label = { Text(stringResource(R.string.form_amount, displayPreview(habit.unit))) }, singleLine = true, enabled = !vm.busy,
        modifier = Modifier.fillMaxWidth().testTag("commit-amount"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text(stringResource(R.string.form_decimal_hint)) })
    val remaining = progress.remaining ?: 0.0
    if (!correcting && remaining > 0) AssistChip(enabled = !vm.busy, onClick = { vm.editLogForm { it.copy(amount = editableAmount(remaining)) } },
        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("commit-remaining"),
        label = { Text(stringResource(R.string.log_remaining_amount, amountLabel(remaining), displayPreview(habit.unit))) }, leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
    if (!correcting) Subtitle(stringResource(R.string.log_remaining_instruction))
    if (!correcting && day == vm.today) {
        if (daily.configuration?.schedule is HabitSchedule.WeeklyQuota) {
            val weekly = remember(index, habit.id, day) { index.weekly(habit.id, day) }
            WeekPeriodSummary(weekly.period)
            TodayWeeklyContext(TodayHabit(habit.id, daily, weekly))
        }
    }
    OutlinedTextField(note, { text -> if (text.length <= 200) vm.editLogForm { it.copy(note = text) } }, label = { Text(stringResource(R.string.form_note)) },
        placeholder = { Text(stringResource(R.string.commit_note_instruction)) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("commit-note"),
        minLines = 2, maxLines = 4, supportingText = { Text(stringResource(R.string.form_character_count, note.length, 200)) })
    error?.let { FormError(it) }
    if (vm.formSaveFailed && error == null) FormError(stringResource(R.string.form_save_failed))
    if (vm.busy) SavingFeedback()
    Button(onClick = {
        try {
            val value = parseAmount(amount)
            error = null
            vm.saveCommit(habit.id, overlay.commitId, value, note, day) { revised ->
                vm.reviewFormDate(revised)
                 error = context.getString(R.string.form_date_review, dateLabel(revised, locale))
            }
        }
        catch (invalid: IllegalArgumentException) { error = invalid.message }
    }, enabled = !vm.busy && (!correcting || commit != null), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save-commit")) {
        Icon(Icons.Outlined.Commit, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (!correcting) R.string.form_commit else R.string.form_correction))
    }
    if (commit != null) {
        TextButton(onClick = { removeConfirmation = true }, enabled = !vm.busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline()) { Text(stringResource(R.string.form_delete)) }
    }
    if (removeConfirmation && commit != null) ConfirmDialog(stringResource(R.string.form_delete_title), stringResource(R.string.form_delete_message),
        stringResource(R.string.form_delete_confirm), destructive = true, onDismiss = { removeConfirmation = false },
        onConfirm = { removeConfirmation = false; vm.removeCommit(commit.id) })
}

@Composable
private fun Guide(onDone: () -> Unit) {
    Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    listOf(
        R.string.guide_create_title to R.string.guide_create,
        R.string.guide_log_title to R.string.guide_log,
        R.string.guide_insights_title to R.string.guide_insights,
        R.string.guide_history_title to R.string.guide_history,
    ).forEach { (title, text) -> Panel(Modifier.fillMaxWidth()) { SectionHeading(stringResource(title)); Subtitle(stringResource(text)) } }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_done)) }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, destructive: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TransferDialog(title, "confirmation-dialog", onDismiss) {
        Text(message)
        TextButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline(),
            colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) { Text(confirmLabel) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline()) { Text(stringResource(R.string.transfer_cancel)) }
    }
}
