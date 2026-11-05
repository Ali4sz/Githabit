package com.githabit.prototype.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.round

enum class RepoStatus { ACTIVE, PAUSED, ARCHIVED }
enum class HabitIcon { BOOK, MOVEMENT, CODE, WATER, CALM, JOURNAL }
enum class HabitColor { GREEN, ORANGE, BLUE, TEAL, PURPLE, PINK }

/**
 * Dated habit configuration: target, status, and schedule effective from [date].
 * [schedule] defaults to daily for v1 data; format v2 makes it explicit (Task 4).
 */
data class GoalVersion(
    val date: LocalDate,
    val target: Double,
    val status: RepoStatus = RepoStatus.ACTIVE,
    val schedule: HabitSchedule = HabitSchedule.Daily,
)

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val unit: String,
    val icon: HabitIcon = HabitIcon.BOOK,
    val color: HabitColor = HabitColor.GREEN,
    val createdOn: LocalDate,
    val versions: List<GoalVersion>,
) {
    fun versionOn(day: LocalDate): GoalVersion? = if (day < createdOn) null
    else versions.filter { it.date <= day }.maxByOrNull { it.date }
}

data class Commit(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val amount: Double,
    val date: LocalDate,
    val timestamp: Instant = Instant.now(),
    val note: String = "",
)

data class Workspace(
    val signedIn: Boolean = false,
    val onboarded: Boolean = false,
    val darkTheme: Boolean = true,
    val name: String = "Alex",
    val bio: String = "Building a better everyday.",
    val habits: List<Habit> = emptyList(),
    val commits: List<Commit> = emptyList(),
    val calendar: WeekCalendar = WeekCalendar(),
)

data class HabitDraft(
    val name: String,
    val description: String,
    val target: Double,
    val unit: String,
    val icon: HabitIcon = HabitIcon.BOOK,
    val color: HabitColor = HabitColor.GREEN,
    val schedule: HabitSchedule = HabitSchedule.Daily,
) {
    fun normalized(): HabitDraft {
        require(name.trim().length in 1..48) { "Give your repo a name of 1–48 characters." }
        require(unit.trim().length in 1..20) { "Add a unit, like pages or minutes (up to 20 characters)." }
        require(description.trim().length <= 160) { "Keep the description within 160 characters." }
        val schedule = schedule.validated()
        return copy(name = name.trim(), unit = unit.trim(), description = description.trim(),
            target = positive(target), schedule = schedule)
    }
}

data class Progress(val total: Double, val target: Double, val eligible: Boolean) {
    val ratio: Double? get() = if (eligible) (total / target).coerceIn(0.0, 1.0) else null
    val complete: Boolean get() = eligible && total >= target
    val remaining: Double get() = rounded((target - total).coerceAtLeast(0.0))
}

fun rounded(value: Double): Double = round(value * 1000.0) / 1000.0

fun positive(value: Double): Double {
    require(value.isFinite() && value in 0.001..1_000_000.0) {
        "Enter an amount between 0.001 and 1,000,000."
    }
    return rounded(value)
}

fun Workspace.progress(habit: Habit, day: LocalDate): Progress {
    val version = habit.versionOn(day)
    val total = rounded(commits.filter { it.habitId == habit.id && it.date == day }.sumOf { it.amount })
    return Progress(total, version?.target ?: habit.versions.first().target, version?.status == RepoStatus.ACTIVE)
}

fun Workspace.dailyScore(day: LocalDate, habitId: String? = null): Double? {
    val values = habits.filter { habitId == null || it.id == habitId }.mapNotNull { progress(it, day).ratio }
    return values.takeIf { it.isNotEmpty() }?.average()
}

fun Workspace.averageProgress(days: List<LocalDate>, habitId: String? = null): Double =
    days.mapNotNull { dailyScore(it, habitId) }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

fun daysEnding(end: LocalDate, count: Int): List<LocalDate> =
    (0 until count).map { end.minusDays((count - it - 1).toLong()) }

fun Workspace.activityStreak(today: LocalDate, habitId: String? = null): Int {
    val dates = commits.filter { (habitId == null || it.habitId == habitId) && it.date <= today }.map { it.date }.toSet()
    var day = if (today in dates) today else today.minusDays(1)
    var count = 0
    while (day in dates) { count++; day = day.minusDays(1) }
    return count
}

fun Workspace.longestStreak(today: LocalDate, habitId: String? = null): Int {
    val dates = commits.filter { (habitId == null || it.habitId == habitId) && it.date <= today }
        .map { it.date }.distinct().sorted()
    var current = 0
    var best = 0
    dates.forEachIndexed { index, day ->
        current = if (index > 0 && dates[index - 1].plusDays(1) == day) current + 1 else 1
        best = maxOf(best, current)
    }
    return best
}

fun Workspace.createHabit(draft: HabitDraft, today: LocalDate, id: String = UUID.randomUUID().toString()): Workspace {
    val fields = draft.normalized()
    require(habits.none { it.id == id }) { "This repo already exists." }
    val habit = Habit(id, fields.name, fields.description, fields.unit, fields.icon, fields.color,
        today, listOf(GoalVersion(today, fields.target, schedule = fields.schedule)))
    return copy(habits = habits + habit)
}

fun Workspace.editHabit(id: String, draft: HabitDraft, today: LocalDate): Workspace {
    val habit = habits.first { it.id == id }
    val fields = draft.normalized()
    require(fields.unit == habit.unit || commits.none { it.habitId == id }) {
        "Units stay fixed after your first commit. Create a new repo for a different unit."
    }
    val version = requireNotNull(habit.versionOn(today))
    // A redundant save with no substantive change adds no configuration version (contract 4).
    val configChanged = version.target != fields.target || version.schedule != fields.schedule
    if (configChanged) habit.requireConfigurationDate(today)
    if (!configChanged && fields.name == habit.name && fields.description == habit.description &&
        fields.unit == habit.unit && fields.icon == habit.icon && fields.color == habit.color) return this
    // Presentational edits never create versions; only target/schedule changes do.
    val versions = if (configChanged) habit.versions.filter { it.date != today } +
        version.copy(date = today, target = fields.target, schedule = fields.schedule) else habit.versions
    val updated = habit.copy(name = fields.name, description = fields.description, unit = fields.unit,
        icon = fields.icon, color = fields.color, versions = versions)
    return copy(habits = habits.map { if (it.id == id) updated else it })
}

fun Workspace.changeStatus(id: String, status: RepoStatus, today: LocalDate): Workspace {
    val habit = habits.first { it.id == id }
    val version = requireNotNull(habit.versionOn(today))
    // Same-day updates preserve target and schedule; a redundant save changes nothing.
    if (version.status == status) return this
    habit.requireConfigurationDate(today)
    val updated = habit.copy(versions = habit.versions.filter { it.date != today } + version.copy(date = today, status = status))
    return copy(habits = habits.map { if (it.id == id) updated else it })
}

private fun Habit.requireConfigurationDate(today: LocalDate) {
    require(versions.none { it.date > today }) {
        "This repo has a newer dated configuration. Check your device date before changing its goal or status."
    }
}

fun Workspace.addCommit(
    habitId: String, amount: Double, day: LocalDate, note: String = "",
    id: String = UUID.randomUUID().toString(), timestamp: Instant = Instant.now(),
): Workspace {
    require(habits.firstOrNull { it.id == habitId }?.versionOn(day)?.status == RepoStatus.ACTIVE) {
        "Resume this repo before logging progress."
    }
    require(note.length <= 200) { "Keep your commit message within 200 characters." }
    require(commits.none { it.id == id }) { "This commit has already been saved." }
    return copy(commits = commits + Commit(id, habitId, positive(amount), day, timestamp, note.trim()))
}

fun Workspace.completeGoal(habitId: String, day: LocalDate, id: String = UUID.randomUUID().toString()): Workspace {
    val habit = habits.first { it.id == habitId }
    val value = progress(habit, day)
    if (value.remaining == 0.0) return this
    return addCommit(habitId, value.remaining, day, "Daily goal completed", id)
}

fun Workspace.editCommit(id: String, amount: Double, note: String): Workspace {
    require(commits.any { it.id == id }) { "This commit could not be found." }
    require(note.length <= 200) { "Keep your commit message within 200 characters." }
    val validAmount = positive(amount)
    return copy(commits = commits.map { if (it.id == id) it.copy(amount = validAmount, note = note.trim()) else it })
}

fun Workspace.removeCommit(id: String): Workspace = copy(commits = commits.filter { it.id != id })

object SampleWorkspace {
    fun create(clock: Clock = Clock.systemDefaultZone()): Workspace {
        val today = LocalDate.now(clock)
        val start = today.minusDays(167)
        val definitions = listOf(
            HabitDraft("Read every day", "A few pages. A new perspective.", 20.0, "pages", HabitIcon.BOOK, HabitColor.GREEN),
            HabitDraft("Move your body", "Make a little room for movement.", 30.0, "minutes", HabitIcon.MOVEMENT, HabitColor.ORANGE),
            HabitDraft("Deep work", "Less distraction. More intention.", 60.0, "minutes", HabitIcon.CODE, HabitColor.BLUE),
            HabitDraft("Stay hydrated", "A small sip in the right direction.", 8.0, "glasses", HabitIcon.WATER, HabitColor.TEAL),
            HabitDraft("A moment of calm", "Breathe in. Find your focus.", 10.0, "minutes", HabitIcon.CALM, HabitColor.PURPLE),
            HabitDraft("Daily journal", "Make sense of the everyday.", 1.0, "entry", HabitIcon.JOURNAL, HabitColor.PINK),
        )
        val todayAmounts = listOf(12.0, 30.0, 35.0, 4.0, 10.0, 1.0)
        val habits = definitions.mapIndexed { index, draft -> Habit("sample-$index", draft.name, draft.description,
            draft.unit, draft.icon, draft.color, start, listOf(GoalVersion(start, draft.target))) }
        val commits = daysEnding(today, 168).flatMapIndexed { dayIndex, day ->
            definitions.mapIndexedNotNull { index, draft ->
                val pattern = (dayIndex * 17 + index * 11) % 23
                val amount = when {
                    day == today -> todayAmounts[index]
                    dayIndex % 31 == 7 || pattern < 3 -> 0.0
                    pattern < 9 -> maxOf(1.0, round(draft.target * 0.5))
                    else -> draft.target
                }
                if (amount == 0.0) null else {
                    val timestamp = if (day == today) maxOf(day.atStartOfDay(clock.zone).toInstant(),
                        clock.instant().minusSeconds((6 - index) * 1800L))
                    else day.atTime(8 + index, 30).atZone(clock.zone).toInstant()
                    Commit("sample-$index-$day", "sample-$index", amount, day, timestamp,
                        if (day == today && index == 0) "A quiet chapter with my morning coffee." else "")
                }
            }
        }
        return Workspace(signedIn = true, onboarded = true, habits = habits, commits = commits)
    }
}
