package com.githabit.prototype.domain

import java.time.LocalDate

/** In-memory, bounded by the coordinator; never serialized into workspace history. */
sealed interface UndoRecord {
    val operation: CommandTime
    fun reverse(workspace: Workspace, now: CommandTime): Workspace
}

class UndoConflict(message: String, val retryable: Boolean = false) : IllegalArgumentException(message)

private fun checkUndo(condition: Boolean, message: String, retryable: Boolean = false) {
    if (!condition) throw UndoConflict("Cannot undo: $message Nothing was changed.", retryable)
}

/** Restore/remove only the exact commit affected, not a subsequently corrected value. */
data class CommitUndo(
    override val operation: CommandTime,
    val before: Commit?,
    val after: Commit?,
    val habitCreatedOn: LocalDate,
    val unit: String,
) : UndoRecord {
    init { require(before != after && (before != null || after != null)) }

    override fun reverse(workspace: Workspace, now: CommandTime): Workspace {
        val record = requireNotNull(after ?: before)
        val habit = workspace.habits.firstOrNull { it.id == record.habitId }
        checkUndo(habit != null && habit.createdOn == habitCreatedOn && habit.unit == unit,
            "the repo or its unit has changed.")
        checkUndo(workspace.commits.firstOrNull { it.id == record.id } == after,
            "this commit has been changed or removed since the original operation.")
        // Corrections and deletion reversals retain the stored date and timestamp.
        // Pausing a repo later does not prohibit repairing its existing history.
        return when {
            before == null -> workspace.removeCommit(record.id)
            after == null -> workspace.copy(commits = workspace.commits + before)
            else -> workspace.copy(commits = workspace.commits.map { if (it.id == record.id) before else it })
        }
    }
}

/** Reverse only the operation date's status field, preserving targets/schedules and later versions. */
data class StatusUndo(
    override val operation: CommandTime,
    val habitId: String,
    val habitCreatedOn: LocalDate,
    val before: GoalVersion,
    val afterStatus: RepoStatus,
) : UndoRecord {
    override fun reverse(workspace: Workspace, now: CommandTime): Workspace {
        checkUndo(now.date >= operation.date, "the device date is before this status change. Check your device date.", retryable = true)
        val habit = workspace.habits.firstOrNull { it.id == habitId }
        checkUndo(habit != null && habit.createdOn == habitCreatedOn, "the repo has changed or is no longer available.")
        requireNotNull(habit)
        val current = habit.versions.firstOrNull { it.date == operation.date }
        checkUndo(current != null && current.status == afterStatus, "this dated status has since been changed.")
        val restored = requireNotNull(current).copy(status = before.status)
        val predecessor = habit.versions.filter { it.date < operation.date }.maxByOrNull { it.date }
        // Remove a version introduced solely by this action only when doing so leaves
        // the same effective fields. Otherwise retain later same-day target/schedule edits.
        val removeVersion = before.date < operation.date && predecessor?.copy(date = operation.date) == restored
        val versions = if (removeVersion) habit.versions.filter { it.date != operation.date }
        else habit.versions.map { if (it.date == operation.date) restored else it }
        return workspace.copy(habits = workspace.habits.map { if (it.id == habitId) it.copy(versions = versions) else it })
    }
}

fun commitUndo(before: Workspace, after: Workspace, id: String, time: CommandTime): CommitUndo {
    val old = before.commits.firstOrNull { it.id == id }
    val saved = after.commits.firstOrNull { it.id == id }
    val habit = before.habits.first { it.id == requireNotNull(saved ?: old).habitId }
    return CommitUndo(time, old, saved, habit.createdOn, habit.unit)
}
