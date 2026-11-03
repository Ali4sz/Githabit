package com.githabit.prototype.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githabit.prototype.domain.*
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

fun formatAmount(value: Double): String = DecimalFormat("#,##0.###").format(value)
fun dateLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))

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
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val green = MaterialTheme.colorScheme.primary
        Box(Modifier.size(if (compact) 34.dp else 40.dp).clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
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
        Row {
            Text("git", fontSize = if (compact) 23.sp else 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text("habit", fontSize = if (compact) 23.sp else 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, fontSize = 9.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier, shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp), content = content)
}

@Composable
fun SectionHeading(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusBadge(text: String, positive: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = if (positive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (positive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HabitGlyph(habit: Habit, size: Int = 40) {
    val color = habitColor(habit.color)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .11f)), Alignment.Center) {
        Icon(habitIcon(habit.icon), null, Modifier.size((size * .52).dp), tint = color)
    }
}

@Composable
fun StatsRow(values: List<Triple<String, String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { (label, value, suffix) ->
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Subtitle(label.replace('\n', ' '), Modifier.weight(1f))
                            Text(value + if (suffix.isEmpty()) "" else " $suffix", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { (label, value, suffix) ->
                    OutlinedCard(Modifier.weight(1f), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, fontSize = 10.sp, lineHeight = 15.sp, minLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
                                if (suffix.isNotEmpty()) Text(" $suffix", Modifier.padding(bottom = 4.dp), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitCard(state: Workspace, habit: Habit, today: LocalDate, busy: Boolean,
    onOpen: () -> Unit, onLog: () -> Unit, onComplete: () -> Unit, onResume: () -> Unit) {
    val value = remember(state, habit, today) { state.progress(habit, today) }
    val status = habit.versionOn(today)?.status ?: RepoStatus.ACTIVE
    val color = habitColor(habit.color)
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("habit-${habit.id}"),
        shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HabitGlyph(habit)
                Spacer(Modifier.weight(1f))
                if (value.complete) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Goal reached", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                } else StatusBadge(if (status == RepoStatus.ACTIVE) "DAILY" else status.name.lowercase())
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Subtitle(habit.description.ifEmpty { "A small step toward your better everyday." })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${formatAmount(value.total)} / ${formatAmount(value.target)} ${habit.unit}", fontSize = 12.sp,
                        modifier = Modifier.weight(1f).testTag("progress-${habit.id}"))
                    Text(if (value.eligible) "${((value.ratio ?: 0.0) * 100).toInt()}%" else "Not scheduled", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { (value.total / value.target).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp), color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {})
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.activityStreak(today, habit.id)}-day activity streak", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (status == RepoStatus.ACTIVE) {
                    OutlinedButton(onClick = onLog, enabled = !busy, contentPadding = PaddingValues(horizontal = 13.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("log-${habit.id}")) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Log")
                    }
                    if (!value.complete) FilledTonalIconButton(onClick = onComplete, enabled = !busy,
                        modifier = Modifier.size(48.dp).testTag("complete-${habit.id}")) {
                        Icon(Icons.Outlined.Check, "Complete remaining goal for ${habit.name}", Modifier.size(20.dp))
                    }
                } else OutlinedButton(onClick = onResume, enabled = !busy) { Text("Resume") }
            }
        }
    }
}

@Composable
fun Heatmap(state: Workspace, today: LocalDate, habitId: String? = null, onDay: (LocalDate) -> Unit) {
    var weeks by rememberSaveable(habitId) { mutableIntStateOf(26) }
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val first = sunday.minusWeeks((weeks - 1).toLong())
    val dates = remember(first, weeks) { List(weeks * 7) { first.plusDays(it.toLong()) } }
    val scores = remember(state, dates, habitId) { dates.map { state.dailyScore(it, habitId) } }
    val colors = heatColors()
    val primary = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    LaunchedEffect(weeks, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Panel(Modifier.fillMaxWidth()) {
        SectionHeading(if (habitId == null) "Your contributions" else "Your consistency")
        Subtitle("Every square is a day you showed up.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(13 to "3 months", 26 to "6 months", 52 to "1 year").forEach { (count, label) ->
                FilterChip(selected = weeks == count, onClick = { weeks = count }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.width(27.dp).height(99.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("", "M", "", "W", "", "F", "").forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.horizontalScroll(scroll), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.width((weeks * 14 - 3).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(first.format(DateTimeFormatter.ofPattern("MMM yyyy")), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy")), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val cellPixels = with(LocalDensity.current) { 14.dp.toPx() }
                Canvas(Modifier.width((weeks * 14 - 3).dp).height(95.dp)
                    .semantics { contentDescription = "Contribution heatmap. Use Explore by date for accessible daily goal details." }
                    .pointerInput(dates, today) {
                        detectTapGestures { point ->
                            val column = (point.x / cellPixels).toInt()
                            val row = (point.y / cellPixels).toInt()
                            dates.getOrNull(column * 7 + row)?.takeIf { it <= today }?.let(onDay)
                        }
                    }) {
                    dates.forEachIndexed { index, day ->
                        if (day <= today) {
                            val ratio = scores[index] ?: 0.0
                            val level = if (ratio <= 0.0) 0 else if (ratio >= 1.0) 4 else ceil(ratio * 3).toInt()
                            val position = Offset((index / 7) * 14.dp.toPx(), (index % 7) * 14.dp.toPx())
                            drawRoundRect(colors[level], position, Size(11.dp.toPx(), 11.dp.toPx()), CornerRadius(2.dp.toPx()))
                            if (day == today) drawRoundRect(primary, position - Offset(1.dp.toPx(), 1.dp.toPx()),
                                Size(13.dp.toPx(), 13.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDay(today) }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("Explore by date", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Less", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            colors.forEach { Box(Modifier.padding(1.dp).size(7.dp).clip(RoundedCornerShape(1.dp)).background(it)) }
            Spacer(Modifier.width(5.dp))
            Text("More", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, onCreate: (() -> Unit)? = null) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Subtitle(description)
            onCreate?.let { Button(onClick = it) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(5.dp)); Text("Create a repo") } }
        }
    }
}
