package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.githabit.prototype.Destination
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.Overlay
import com.githabit.prototype.R
import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(vm: GitHabitViewModel, onExport: () -> Unit, onRestore: () -> Unit) {
    PageColumn(Modifier.imePadding()) {
        PreferenceHeading(stringResource(R.string.settings_title))
        ProfilePreferences(vm)
        Panel {
            PreferenceHeading(stringResource(R.string.theme_title))
            Subtitle(stringResource(R.string.theme_description))
            Column(Modifier.selectableGroup()) {
                ThemePreference.entries.forEach { preference ->
                    PreferenceChoice(stringResource(when (preference) {
                        ThemePreference.SYSTEM -> R.string.theme_system
                        ThemePreference.LIGHT -> R.string.theme_light
                        ThemePreference.DARK -> R.string.theme_dark
                    }), preference == vm.workspace.theme, !vm.busy, "theme-${preference.name}") {
                        vm.setTheme(preference)
                    }
                }
            }
        }
        CalendarPreferences(vm)
        Panel {
            PreferenceHeading(stringResource(R.string.data_title))
            SettingsAction(stringResource(R.string.export_title), stringResource(R.string.export_description),
                 Icons.Outlined.Download, !vm.busy && !vm.exportInProgress, "export-workspace", onExport)
            SettingsAction(stringResource(R.string.restore_title), stringResource(R.string.restore_description),
                Icons.Outlined.Upload, !vm.busy && !vm.exportInProgress, "restore-workspace", onRestore)
            SettingsAction(stringResource(R.string.guide_title), stringResource(R.string.guide_description),
                Icons.AutoMirrored.Outlined.HelpOutline, !vm.busy, "open-guide") { vm.overlay = Overlay.Guide }
            SettingsAction(stringResource(R.string.activity_title), stringResource(R.string.activity_description),
                Icons.Outlined.History, !vm.busy, "open-activity") { vm.navigate(Destination.ACTIVITY) }
            TextButton(onClick = vm::requestReset, enabled = !vm.busy && !vm.exportInProgress,
                modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch)
                    .focusOutline(CircleShape, MaterialTheme.colorScheme.error).testTag("reset-workspace"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.reset_workspace))
            }
        }
        Subtitle(stringResource(R.string.local_storage_notice))
    }
}

@Composable
private fun ProfilePreferences(vm: GitHabitViewModel) {
    // Only explicit small edits enter saved state; a huge compatible original remains on disk.
    var nameEdit by rememberSaveable(vm.workspace.name) { mutableStateOf<String?>(null) }
    val name = nameEdit ?: vm.workspace.name
    var bio by rememberSaveable(vm.workspace.bio) { mutableStateOf(vm.workspace.bio) }
    // Legacy names may include padding beyond the validated trimmed-name limit.
    val nameCharactersRemaining = (40 - name.length).coerceAtLeast(0)
    Panel {
        PreferenceHeading(stringResource(R.string.profile_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.medium)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(GitHabitSize.minimumTouch), Alignment.Center) {
                    Icon(Icons.Outlined.PersonOutline, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(displayPreview(vm.workspace.name.ifBlank { stringResource(R.string.workspace_title) }),
                modifier = Modifier.weight(1f).testTag("profile-heading"), style = MaterialTheme.typography.titleLarge)
        }
        Subtitle(stringResource(R.string.profile_description))
        LegacyTextInput(name, { if (it.length <= 40) nameEdit = it }, enabled = !vm.busy,
            label = stringResource(R.string.profile_name), singleLine = true,
            supportingText = { Text(pluralStringResource(R.plurals.characters_remaining, nameCharactersRemaining, nameCharactersRemaining)) },
            modifier = Modifier.fillMaxWidth().testTag("profile-name"))
        OutlinedTextField(bio, { if (it.length <= 160) bio = it }, enabled = !vm.busy,
            label = { Text(stringResource(R.string.profile_bio)) },
            supportingText = { Text(pluralStringResource(R.plurals.characters_remaining, 160 - bio.length, 160 - bio.length)) },
            modifier = Modifier.fillMaxWidth().testTag("profile-bio"))
        OutlinedButton(onClick = { vm.saveProfile(name, bio) {
            nameEdit = null
            bio = vm.workspace.bio
        } },
            enabled = !vm.busy && (name != vm.workspace.name || bio != vm.workspace.bio),
            modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("save-profile")) {
            Text(stringResource(R.string.profile_save))
        }
    }
}

@Composable
private fun CalendarPreferences(vm: GitHabitViewModel) {
    val locale = displayLocale()
    val status = vm.workspace.calendar.status(vm.today)
    val savedChoice = status.pending?.weekStart ?: status.effectiveWeekStart
    var draft by rememberSaveable { mutableStateOf<DayOfWeek?>(null) }
    var intendedDate by rememberSaveable { mutableStateOf(vm.today.toString()) }
    var dateReview by rememberSaveable { mutableStateOf(false) }
    val selected = draft ?: savedChoice
    val changed = selected != savedChoice
    // A verified save can accept the draft. A calendar activation or failed save
    // must not overwrite a different, still-unsubmitted choice.
    LaunchedEffect(savedChoice) {
        if (draft == savedChoice) { draft = null; dateReview = false }
    }
    val preview = if (changed) runCatching { vm.workspace.requestWeekStart(selected, vm.today).calendar.status(vm.today) } else null
    Panel {
        PreferenceHeading(stringResource(R.string.calendar_title))
        Text(stringResource(R.string.week_starts_on), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.testTag("calendar-saved"), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
            Text(stringResource(R.string.calendar_effective, weekdayName(status.effectiveWeekStart, locale)))
            CalendarDates(status, locale)
        }
        Subtitle(stringResource(R.string.calendar_policy))
        Column(Modifier.selectableGroup()) {
            DayOfWeek.entries.forEach { weekday ->
                PreferenceChoice(weekdayName(weekday, locale), selected == weekday, !vm.busy, "week-start-${weekday.name}") {
                    draft = weekday
                    intendedDate = vm.today.toString()
                    dateReview = false
                }
            }
        }
        if (status.pending != null) TextButton(onClick = {
            draft = status.effectiveWeekStart
            intendedDate = vm.today.toString()
            dateReview = false
        }, enabled = !vm.busy,
            modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("review-calendar-cancellation")) {
            Text(stringResource(R.string.calendar_review_cancellation))
        }
        if (changed) {
            Column(Modifier.testTag("calendar-preview"), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                Text(stringResource(R.string.calendar_unsaved, weekdayName(selected, locale)), style = MaterialTheme.typography.titleSmall)
                preview?.getOrNull()?.let { projected ->
                    if (selected == status.effectiveWeekStart && status.pending != null) {
                        Subtitle(stringResource(R.string.calendar_cancelling))
                    } else if (projected.pending == null) {
                        Subtitle(stringResource(R.string.calendar_immediate))
                    }
                    CalendarDates(projected, locale, preview = true)
                }
                preview?.exceptionOrNull()?.let { Subtitle(stringResource(R.string.calendar_invalid)) }
            }
        }
        if (changed && (dateReview || intendedDate != vm.today.toString())) Text(
            stringResource(R.string.calendar_date_review, calendarDate(vm.today, locale)),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("calendar-date-review"),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            vm.setWeekStart(selected, LocalDate.parse(intendedDate)) { revised ->
                intendedDate = revised.toString()
                dateReview = true
            }
        }, enabled = !vm.busy && changed && preview?.isSuccess == true,
            modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch)
                .focusOutline(CircleShape, MaterialTheme.colorScheme.onPrimary).testTag("save-week-start")) {
            Text(stringResource(R.string.calendar_save))
        }
    }
}

/** All boundaries come from the domain resolver; minus one only formats inclusive end labels. */
@Composable
private fun CalendarDates(status: CalendarStatus, locale: Locale, preview: Boolean = false) {
    val period = status.period
    if (period.kind == WeekPeriodKind.BRIDGE) {
        Subtitle(stringResource(R.string.calendar_active_bridge,
            calendarDate(period.start, locale), calendarDate(period.endExclusive.minusDays(1), locale)))
    }
    status.pending?.let { pending ->
        Subtitle(stringResource(if (preview) R.string.calendar_pending_preview else R.string.calendar_pending,
            weekdayName(pending.weekStart, locale), calendarDate(pending.boundary, locale)))
        Subtitle(stringResource(R.string.calendar_planned_bridge,
            calendarDate(pending.boundary, locale), calendarDate(pending.bridgeEnd.minusDays(1), locale)))
    }
    if (status.pending != null || period.kind == WeekPeriodKind.BRIDGE) {
        Subtitle(stringResource(R.string.calendar_first_full_week, calendarDate(status.firstFullWeekStart, locale)))
    }
}

private fun weekdayName(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)
private fun calendarDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

@Composable
private fun PreferenceHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PreferenceChoice(label: String, selected: Boolean, enabled: Boolean, tag: String, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline()
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .testTag(tag).padding(horizontal = GitHabitSpace.small, vertical = GitHabitSpace.small),
        horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.medium), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsAction(title: String, detail: String, icon: ImageVector, enabled: Boolean, tag: String, action: () -> Unit) {
    Surface(onClick = action, enabled = enabled, shape = MaterialTheme.shapes.small,
        modifier = Modifier.focusOutline().testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).padding(vertical = GitHabitSpace.small),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.medium)) {
            Icon(icon, null, Modifier.size(GitHabitSize.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Subtitle(detail) }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(GitHabitSize.supportingIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
