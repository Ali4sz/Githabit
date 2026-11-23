package com.githabit.prototype

import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

internal const val FORM_STATE_KEY = "active-form-v1"
internal const val DISCARD_STATE_KEY = "discard-form-v1"

/** Small raw inputs, not workspace records. Invalid/cleared input is meaningful work too. */
enum class ScheduleChoice { DAILY, WEEKDAYS, WEEKLY }

data class RepoFormValues(
    val name: String = "",
    val description: String = "",
    val target: String = "",
    val unit: String = "",
    val icon: HabitIcon = HabitIcon.BOOK,
    val color: HabitColor = HabitColor.GREEN,
    val choice: ScheduleChoice = ScheduleChoice.DAILY,
    val weekdayMask: Int = 0,
    val weeklyDays: String = "1",
) {
    fun schedule(): HabitSchedule = when (choice) {
        ScheduleChoice.DAILY -> HabitSchedule.Daily
        ScheduleChoice.WEEKDAYS -> HabitSchedule.SelectedWeekdays(
            DayOfWeek.entries.filterTo(mutableSetOf()) { weekdayMask and (1 shl (it.value - 1)) != 0 })
        ScheduleChoice.WEEKLY -> HabitSchedule.WeeklyQuota(weeklyDays.toIntOrNull() ?: 0)
    }.validated()

    fun draft(): HabitDraft = HabitDraft(name, description, parseFormAmount(target), unit, icon, color, schedule())

    // Hidden subforms are retained, but switching back to the opening schedule is a revert.
    fun meaningfulComparedWith(other: RepoFormValues): Boolean =
        copy(weekdayMask = 0, weeklyDays = "") != other.copy(weekdayMask = 0, weeklyDays = "") ||
            (choice == ScheduleChoice.WEEKDAYS && weekdayMask != other.weekdayMask) ||
            (choice == ScheduleChoice.WEEKLY && weeklyDays != other.weeklyDays)

    internal fun encode(): List<String> = listOf(name, description, target, unit, icon.name, color.name,
        choice.name, weekdayMask.toString(), weeklyDays)

    companion object {
        fun opening(habit: Habit?, date: LocalDate): RepoFormValues {
            val version = habit?.versionOn(date)
            val schedule = version?.schedule ?: HabitSchedule.Daily
            return RepoFormValues(habit?.name ?: "", habit?.description ?: "",
                version?.target?.let(::editableFormAmount) ?: "", habit?.unit ?: "",
                habit?.icon ?: HabitIcon.BOOK, habit?.color ?: HabitColor.GREEN,
                when (schedule) {
                    HabitSchedule.Daily -> ScheduleChoice.DAILY
                    is HabitSchedule.SelectedWeekdays -> ScheduleChoice.WEEKDAYS
                    is HabitSchedule.WeeklyQuota -> ScheduleChoice.WEEKLY
                }, (schedule as? HabitSchedule.SelectedWeekdays)?.days?.sumOf { 1 shl (it.value - 1) } ?: 0,
                (schedule as? HabitSchedule.WeeklyQuota)?.daysPerWeek?.toString() ?: "1")
        }

        internal fun decode(values: List<String>): RepoFormValues {
            require(values.size == 9)
            return RepoFormValues(values[0], values[1], values[2], values[3], HabitIcon.valueOf(values[4]),
                HabitColor.valueOf(values[5]), ScheduleChoice.valueOf(values[6]), values[7].toInt(), values[8])
                .also { require(it.weekdayMask in 0..127) }
        }
    }
}

data class LogFormValues(val amount: String = "", val note: String = "")

sealed interface FormState {
    val intendedDate: LocalDate
    val dirty: Boolean
    fun matches(overlay: Overlay?): Boolean
    fun encode(): List<String>

    data class Repo(val id: String?, override val intendedDate: LocalDate,
        val baseline: RepoFormValues, val values: RepoFormValues = baseline,
        val reviewedConfiguration: GoalVersion? = null,
        val creationId: String = UUID.randomUUID().toString(), val submitted: Boolean = false,
        val reviewedEntity: String? = null, val submittedEntity: String? = null) : FormState {
        override val dirty get() = values.meaningfulComparedWith(baseline)
        override fun matches(overlay: Overlay?) = overlay == Overlay.RepoForm(id)
        override fun encode(): List<String> = listOf("repo", id ?: "", intendedDate.toString()) +
            baseline.encode() + values.encode() + (reviewedConfiguration?.let {
                listOf(it.date.toString(), editableFormAmount(it.target), it.status.name) +
                    RepoFormValues.opening(Habit(name = "", unit = "", createdOn = it.date, versions = listOf(it)), it.date)
                        .encode().takeLast(3)
            } ?: List(6) { "" }) + listOf(creationId, submitted.toString(), reviewedEntity ?: "", submittedEntity ?: "")
    }

    data class Log(val habitId: String, val commitId: String?, override val intendedDate: LocalDate,
        val baseline: LogFormValues, val values: LogFormValues = baseline,
        val creationId: String = UUID.randomUUID().toString(), val submitted: Boolean = false,
        val reviewedEntity: String? = null, val submittedEntity: String? = null) : FormState {
        override val dirty get() = values != baseline
        override fun matches(overlay: Overlay?) = overlay is Overlay.LogForm && overlay.habitId == habitId && overlay.commitId == commitId
        override fun encode() = listOf("log", habitId, commitId ?: "", intendedDate.toString(),
            baseline.amount, baseline.note, values.amount, values.note, creationId, submitted.toString(), reviewedEntity ?: "", submittedEntity ?: "")
    }

    companion object {
        fun decode(raw: Any?): FormState? = runCatching {
            val values = (raw as? List<*>)?.map { it as String } ?: return null
            require(values.size <= 31 && values.all { it.length <= 4096 })
            when (values.firstOrNull()) {
                "repo" -> {
                    require(values.size == 31 && values[27].length in 1..256)
                    val reviewed = if (values[21].isEmpty()) null else GoalVersion(LocalDate.parse(values[21]),
                        values[22].toDouble(), RepoStatus.valueOf(values[23]),
                        RepoFormValues.decode(listOf("", "", "", "", "BOOK", "GREEN") + values.subList(24, 27)).schedule())
                    Repo(values[1].ifEmpty { null }, LocalDate.parse(values[2]),
                        RepoFormValues.decode(values.subList(3, 12)), RepoFormValues.decode(values.subList(12, 21)), reviewed,
                        values[27], values[28].toBooleanStrict(), values[29].ifEmpty { null }, values[30].ifEmpty { null })
                }
                "log" -> {
                    require(values.size == 12 && values[1].isNotEmpty() && values[8].length in 1..256)
                    Log(values[1], values[2].ifEmpty { null }, LocalDate.parse(values[3]),
                        LogFormValues(values[4], values[5]), LogFormValues(values[6], values[7]), values[8], values[9].toBooleanStrict(),
                        values[10].ifEmpty { null }, values[11].ifEmpty { null })
                }
                else -> null
            }
        }.getOrNull()
    }
}

fun editableFormAmount(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
fun parseFormAmount(text: String): Double = positive(text.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)

/** Preview the same domain mutation used by Save; reverted intraday edits are not warnings. */
fun Workspace.proposedWeeklyTransition(id: String?, draft: HabitDraft, date: LocalDate): Boolean {
    val habit = habits.find { it.id == id }
    val version = habit?.versionOn(date)
    val fields = draft.normalized()
    if (id != null && version?.target == fields.target && version.schedule == fields.schedule) return false
    val previewId = id ?: generateSequence("form-preview") { "$it-" }.first { candidate -> habits.none { it.id == candidate } }
    val proposed = if (id == null) createHabit(draft, date, previewId) else editHabit(id, draft, date)
    // Transition eligibility depends on dated configurations, never on recorded
    // amounts. Index only the proposed repo and no commits while typing a draft.
    val preview = proposed.copy(habits = listOf(proposed.habits.first { it.id == previewId }), commits = emptyList())
    return ProgressAnalytics(preview, date).weekly(previewId, date).assessment == WeekAssessment.TRANSITIONAL
}
