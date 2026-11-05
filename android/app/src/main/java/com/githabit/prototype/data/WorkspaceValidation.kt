package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace
import com.githabit.prototype.domain.WeekCalendar
import com.githabit.prototype.domain.HabitSchedule
import java.math.BigDecimal

/** Storage compatibility, not new-input normalization: preserve strings and stored dates. */
internal object WorkspaceValidation {
    fun validate(state: Workspace) {
        WorkspaceLimits.capacity(state.habits.size <= WorkspaceLimits.MAX_HABITS, "Workspace has too many repos.")
        WorkspaceLimits.capacity(state.commits.size <= WorkspaceLimits.MAX_COMMITS, "Workspace has too many commits.")
        val configurations = state.habits.sumOf { it.versions.size.toLong() } + state.calendar.transitions.size +
            (if (state.calendar.pending == null) 0 else 1)
        WorkspaceLimits.capacity(configurations <= WorkspaceLimits.MAX_CONFIGURATIONS, "Workspace has too many dated configurations.")
        valid(state.name.trim().length <= 40 && state.bio.length <= 160, "Profile exceeds supported length.")
        val ids = mutableSetOf<String>()
        for (habit in state.habits) {
            identifier(habit.id)
            valid(ids.add(habit.id), "Duplicate repo ID.")
            valid(habit.name.trim().length in 1..48 && habit.unit.trim().length in 1..20 &&
                habit.description.trim().length <= 160, "Repo text is invalid.")
            valid(habit.versions.isNotEmpty() && habit.versions.first().date == habit.createdOn,
                "Repo history must start on its creation date.")
            var previous = habit.createdOn
            habit.versions.forEachIndexed { index, version ->
                valid(index == 0 || version.date > previous, "Repo configurations must have ordered, distinct dates.")
                previous = version.date
                amount(version.target)
                when (val schedule = version.schedule) {
                    HabitSchedule.Daily -> Unit
                    is HabitSchedule.SelectedWeekdays -> valid(schedule.days.isNotEmpty(), "Select at least one weekday.")
                    is HabitSchedule.WeeklyQuota -> valid(schedule.daysPerWeek in 1..7, "Weekly goal must be 1 to 7 days.")
                }
            }
        }
        val commits = mutableSetOf<String>()
        for (commit in state.commits) {
            identifier(commit.id); identifier(commit.habitId)
            valid(commits.add(commit.id), "Duplicate commit ID.")
            valid(commit.habitId in ids, "Commit refers to a missing repo.")
            valid(commit.note.length <= 200, "Commit message exceeds supported length.")
            amount(commit.amount)
            // Do not reject pre-creation/future/paused dates accepted by legacy storage.
        }
        WeekCalendar(state.calendar.initialWeekStart, state.calendar.transitions, state.calendar.pending)
    }

    private fun identifier(value: String) {
        WorkspaceLimits.capacity(value.length <= WorkspaceLimits.MAX_ID_LENGTH, "Identifier exceeds supported length.")
        valid(value.isNotBlank(), "Identifier must not be blank.")
    }
    private fun amount(value: Double) {
        valid(value.isFinite() && value in 0.001..1_000_000.0 &&
            BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 3, "Amount is out of bounds or exceeds three decimal places.")
    }
    private fun valid(condition: Boolean, message: String) {
        if (!condition) throw WorkspaceJsonException(WorkspaceJsonFailure.INVALID, message)
    }
}
