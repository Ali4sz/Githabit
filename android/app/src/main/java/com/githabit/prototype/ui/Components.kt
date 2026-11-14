package com.githabit.prototype.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.githabit.prototype.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githabit.prototype.domain.*
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAmount(value: Double): String = DecimalFormat("#,##0.###").format(value)
fun dateLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))

@Composable
internal fun WeekPeriodSummary(period: WeekPeriod) {
    Subtitle(stringResource(if (period.kind == WeekPeriodKind.BRIDGE) R.string.schedule_bridge else R.string.schedule_period,
        dateLabel(period.start), dateLabel(period.endExclusive.minusDays(1))))
}

@Composable
internal fun ScheduleSummary(state: Workspace, habit: Habit, today: LocalDate, analytics: ProgressAnalytics? = null) {
    val version = habit.versionOn(today) ?: return
    val schedule = version.schedule
    val target = formatAmount(version.target)
    val summary = when (schedule) {
        HabitSchedule.Daily -> stringResource(R.string.schedule_summary_daily, target, habit.unit)
        is HabitSchedule.SelectedWeekdays -> {
            val start = state.calendar.effectiveWeekStart(today)
            val names = (0..6).map { DayOfWeek.of((start.value - 1 + it) % 7 + 1) }
                .filter { it in schedule.days }.joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
            stringResource(R.string.schedule_summary_weekdays, names, target, habit.unit)
        }
        is HabitSchedule.WeeklyQuota -> pluralStringResource(R.plurals.schedule_summary_weekly, schedule.daysPerWeek, target, habit.unit, schedule.daysPerWeek)
    }
    Text(summary, Modifier.testTag("schedule-summary-${habit.id}"), style = MaterialTheme.typography.bodySmall)
    if (schedule is HabitSchedule.WeeklyQuota) {
        val index = analytics ?: remember(state, today) { ProgressAnalytics(state, today) }
        val weekly = remember(index, habit.id, today) { index.weekly(habit.id, today) }
        WeekPeriodSummary(weekly.period)
        val quota = weekly.quota
        val assessment = if (quota != null) stringResource(when (weekly.assessment) {
            WeekAssessment.ACHIEVED -> R.string.schedule_week_achieved
            WeekAssessment.MISSED -> R.string.schedule_week_missed
            else -> R.string.schedule_week_pending
        }, quota.displayDays, quota.goal.daysPerWeek) else stringResource(
            if (weekly.assessment == WeekAssessment.TRANSITIONAL) R.string.schedule_week_practice else R.string.schedule_week_inactive,
            weekly.qualifyingDates.size)
        Subtitle(assessment)
    }
}

fun habitIcon(icon: HabitIcon): ImageVector = when (icon) {
    HabitIcon.BOOK -> Icons.Outlined.AutoStories
    HabitIcon.MOVEMENT -> Icons.AutoMirrored.Outlined.DirectionsRun
    HabitIcon.CODE -> Icons.Outlined.Code
    HabitIcon.WATER -> Icons.Outlined.WaterDrop
    HabitIcon.CALM -> Icons.Outlined.WbSunny
    HabitIcon.JOURNAL -> Icons.Outlined.EditNote
}

@Composable
fun Brand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
        val green = MaterialTheme.colorScheme.primary
        if (!compact || LocalDensity.current.fontScale <= 1.3f) Box(Modifier.size(if (compact) GitHabitSize.compactBrand else GitHabitSize.glyph).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(GitHabitSize.icon)) {
                val x = size.width * 0.3f
                val right = size.width * 0.74f
                val top = size.height * .22f
                val bottom = size.height * .8f
                val stroke = 1.8.dp.toPx()
                drawLine(green, Offset(x, top + 3.dp.toPx()), Offset(x, bottom - 3.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(green, Offset(x, size.height * .52f), Offset(right, size.height * .42f), stroke, StrokeCap.Round)
                drawLine(green, Offset(right, top + 3.dp.toPx()), Offset(right, size.height * .42f), stroke, StrokeCap.Round)
                listOf(Offset(x, top), Offset(x, bottom), Offset(right, top)).forEach {
                    drawCircle(green, 2.5.dp.toPx(), it, style = Stroke(stroke))
                }
            }
        }
        val style = if (compact) GitHabitType.compactWordmark else GitHabitType.wordmark
        Text(androidx.compose.ui.text.buildAnnotatedString {
            append("git")
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Medium))
            append("habit")
            pop()
        }, style = style)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
        Box(Modifier.size(GitHabitSpace.micro).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, style = GitHabitType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(GitHabitSize.border, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(GitHabitSpace.large), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium), content = content)
    }
}

@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Readability belongs to each page/pane, not a width cap around the whole app.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = GitHabitSpace.page, vertical = GitHabitSpace.section),
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.section), content = content)
    }
}

@Composable
fun SectionHeading(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusBadge(text: String, positive: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = if (positive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (positive) null else BorderStroke(GitHabitSize.border, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, Modifier.padding(horizontal = GitHabitSpace.small, vertical = GitHabitSpace.micro), style = MaterialTheme.typography.labelSmall,
            color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HabitGlyph(habit: Habit, size: Int = GitHabitSize.glyph.value.toInt()) {
    val color = habitColor(habit.color)
    Box(Modifier.size(size.dp).clip(MaterialTheme.shapes.small).background(color.copy(alpha = HabitAccentPalette.glyphContainerAlpha)), Alignment.Center) {
        Icon(habitIcon(habit.icon), null, Modifier.size((size * .52).dp), tint = color)
    }
}

@Composable
fun StatsRow(values: List<Triple<String, String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                values.forEach { (label, value, suffix) ->
                    Panel(Modifier.fillMaxWidth()) {
                        Subtitle(label.replace('\n', ' '))
                        Text(value + if (suffix.isEmpty()) "" else " $suffix", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                values.forEach { (label, value, suffix) ->
                    OutlinedCard(Modifier.weight(1f), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(GitHabitSize.border, MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(GitHabitSpace.medium), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, minLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = GitHabitType.statistic)
                            if (suffix.isNotEmpty()) Subtitle(suffix)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitCard(state: Workspace, habit: Habit, today: LocalDate, busy: Boolean,
    onOpen: () -> Unit, onLog: () -> Unit, onComplete: () -> Unit, onResume: () -> Unit,
    selected: Boolean? = null, analytics: ProgressAnalytics? = null) {
    val value = remember(state, habit, today) { state.progress(habit, today) }
    val status = habit.versionOn(today)?.status ?: RepoStatus.ACTIVE
    val color = habitColor(habit.color)
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().focusOutline(MaterialTheme.shapes.medium)
        .testTag("habit-${habit.id}").semantics { if (selected != null) this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (selected == true) GitHabitSize.focusBorder else GitHabitSize.border,
            if (selected == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(GitHabitSpace.large), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.large)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HabitGlyph(habit)
                Spacer(Modifier.weight(1f))
                if (value.complete) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(GitHabitSize.supportingIcon), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.schedule_today_reached), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (status != RepoStatus.ACTIVE) StatusBadge(status.name.lowercase())
            }
            Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                if (habit.description.isNotEmpty()) Subtitle(habit.description)
                ScheduleSummary(state, habit, today, analytics)
            }
            Column(verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                    Text(stringResource(R.string.schedule_today_amount, formatAmount(value.total), formatAmount(value.target), habit.unit), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("progress-${habit.id}"))
                    Text(if (value.eligible) "${((value.ratio ?: 0.0) * 100).toInt()}%" else "Not scheduled", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { (value.total / value.target).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(GitHabitSize.progressTrack), color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {})
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(GitHabitSize.supportingIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.activityStreak(today, habit.id)}-day activity streak", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                if (status == RepoStatus.ACTIVE) {
                    OutlinedButton(onClick = onLog, enabled = !busy,
                        modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("log-${habit.id}")) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(GitHabitSize.supportingIcon)); Spacer(Modifier.width(GitHabitSpace.micro)); Text("Log")
                    }
                    if (!value.complete) FilledTonalIconButton(onClick = onComplete, enabled = !busy,
                        modifier = Modifier.size(GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("complete-${habit.id}")) {
                        Icon(Icons.Outlined.Check, "Complete remaining goal for ${habit.name}", Modifier.size(GitHabitSize.supportingIcon))
                    }
                } else OutlinedButton(onClick = onResume, enabled = !busy,
                    modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape)) { Text("Resume") }
            }
        }
    }
}

@Composable
fun Heatmap(state: Workspace, today: LocalDate, habitId: String? = null, analytics: ProgressAnalytics? = null, onDay: (LocalDate) -> Unit) {
    var weeks by rememberSaveable(habitId) { mutableIntStateOf(26) }
    val index = analytics ?: remember(state, today) { ProgressAnalytics(state, today) }
    val columns = remember(index, weeks, habitId) { index.heatmap(weeks, habitId) }
    val first = columns.first().period.start
    val weekday = state.calendar.effectiveWeekStart(today)
    val colors = heatColors()
    val primary = MaterialTheme.colorScheme.primary
    val pitch = with(LocalDensity.current) { maxOf(HeatmapGeometry.minimumPitch, GitHabitType.chartLabel.lineHeight.toDp()) }
    val scroll = rememberScrollState()
    val description = stringResource(if (habitId == null) R.string.heatmap_activity_description else R.string.heatmap_progress_description,
        dateLabel(first), dateLabel(today))
    Panel(Modifier.fillMaxWidth()) {
        SectionHeading(stringResource(if (habitId == null) R.string.heatmap_activity_title else R.string.heatmap_progress_title))
        Subtitle(stringResource(if (habitId == null) R.string.heatmap_activity_legend else R.string.heatmap_progress_legend))
        Subtitle(stringResource(R.string.heatmap_period_explanation))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
            listOf(13, 26, 52).forEach { count ->
                FilterChip(selected = weeks == count, onClick = { weeks = count }, label = { Text(pluralStringResource(R.plurals.heatmap_period_count, count, count)) },
                    modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline().testTag("heatmap-periods-$count"))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.padding(end = GitHabitSpace.small)) {
                (0..6).map { DayOfWeek.of((weekday.value - 1 + it) % 7 + 1) }.forEach {
                    Box(Modifier.height(pitch), contentAlignment = Alignment.Center) {
                        Text(it.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = GitHabitType.chartLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.horizontalScroll(scroll), verticalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
                Row(Modifier.width(pitch * weeks), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(first.format(DateTimeFormatter.ofPattern("MMM yyyy")), style = GitHabitType.chartLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy")), style = GitHabitType.chartLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val cellPixels = with(LocalDensity.current) { pitch.toPx() }
                Canvas(Modifier.width(pitch * weeks).height(pitch * 7)
                    .testTag("heatmap-canvas").semantics { contentDescription = description }) {
                    columns.forEachIndexed { column, period -> period.dates.forEach { cell ->
                        val day = cell.date
                        if (day <= today) {
                              val inset = GitHabitSize.border.toPx()
                              val side = (pitch - HeatmapGeometry.gap).toPx()
                              val row = (day.dayOfWeek.value - weekday.value + 7) % 7
                              val position = Offset(column * cellPixels + inset, row * cellPixels + inset)
                              drawRoundRect(colors[cell.level], position, Size(side, side), CornerRadius(HeatmapGeometry.corner.toPx()))
                              // Outline neutral practice/non-obligation cells without inventing failure.
                              if (habitId != null && cell.daily?.eligibility != DateEligibility.DUE) {
                                  drawRoundRect(primary, position, Size(side, side), CornerRadius(HeatmapGeometry.corner.toPx()), style = Stroke(inset))
                              }
                             if (day == today) drawRoundRect(primary, position - Offset(inset / 2, inset / 2),
                                 Size(side + inset, side + inset), CornerRadius(HeatmapGeometry.gap.toPx()), style = Stroke(inset))
                        }
                    } }
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.small)) {
            TextButton(onClick = { onDay(today) },
                modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag("heatmap-explore")) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(GitHabitSize.supportingIcon)); Spacer(Modifier.width(GitHabitSpace.micro))
                Text(stringResource(R.string.history_date_title))
            }
            Row(Modifier.heightIn(min = GitHabitSize.minimumTouch), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GitHabitSpace.micro)) {
                Subtitle(stringResource(if (habitId == null) R.string.heatmap_zero else R.string.heatmap_zero_progress))
                colors.forEach { Box(Modifier.size(HeatmapGeometry.legendCell).clip(RoundedCornerShape(GitHabitSize.border)).background(it)) }
                Subtitle(stringResource(if (habitId == null) R.string.heatmap_many else R.string.heatmap_complete))
            }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, onCreate: (() -> Unit)? = null) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = GitHabitSpace.large), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium)) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(GitHabitSize.glyph), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Subtitle(description)
            onCreate?.let {
                Button(onClick = it, modifier = Modifier.heightIn(min = GitHabitSize.minimumTouch)
                    .focusOutline(CircleShape, MaterialTheme.colorScheme.onPrimary)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(GitHabitSpace.micro)); Text("Create a repo")
                }
            }
        }
    }
}
