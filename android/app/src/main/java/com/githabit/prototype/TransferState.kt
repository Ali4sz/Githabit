package com.githabit.prototype

import com.githabit.prototype.data.RestoreDocument

/** Transfer presentation is independent of repo/log drafts and is never a large saved Bundle. */
sealed interface RestoreState {
    val requestId: String
    data class Selecting(override val requestId: String) : RestoreState
    data class Reading(override val requestId: String) : RestoreState
    @ConsistentCopyVisibility
    data class Preview internal constructor(
        override val requestId: String,
        val document: RestoreDocument,
        internal val revision: Long,
        internal val context: WorkspaceLoad,
        val currentHabitCount: Int,
        val currentCommitCount: Int,
        val consentUpdated: Boolean = false,
    ) : RestoreState
    data class Failed(override val requestId: String, val diagnostic: Diagnostic) : RestoreState {
        val message get() = diagnostic.english
    }
}

@ConsistentCopyVisibility
data class ResetPreview internal constructor(
    internal val revision: Long,
    internal val context: WorkspaceLoad,
    val habitCount: Int,
    val commitCount: Int,
    val consentUpdated: Boolean = false,
)
