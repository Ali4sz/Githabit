package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace

/** Why a stored workspace could not be loaded. Drives recovery-screen guidance. */
enum class UnreadableKind { CORRUPT, UNSUPPORTED_VERSION, INVALID, LEGACY_INCOMPATIBLE, CAPACITY, IO, INDETERMINATE }

/** Result of loading the persisted workspace. Failures must never fall back to writable empty data. */
sealed interface WorkspaceLoadResult {
    /** No stored file exists: a genuinely new workspace may be created. */
    data object Missing : WorkspaceLoadResult

    data class Ready(val workspace: Workspace) : WorkspaceLoadResult

    /**
     * The stored file could not be read or decoded. The original bytes are preserved
     * (when [preserved]); ordinary mutations must stay blocked until recovery resolves.
     */
    data class Unreadable(
        val kind: UnreadableKind,
        val message: StorageMessage,
        val preserved: Boolean,
    ) : WorkspaceLoadResult {
        val reason get() = message.english
    }
}

/** Verified outcome of a persisted write. */
sealed interface WriteOutcome {
    /** No write attempted: loading/reconciliation is required. */
    data object Blocked : WriteOutcome

    /** No write attempted, for example because preservation failed. */
    data class Rejected(val message: StorageMessage) : WriteOutcome {
        val reason get() = message.english
    }

    /** The file was re-read after writing and matches the candidate. */
    data object Committed : WriteOutcome

    /** The candidate could not be committed; the verified previous state remains on disk. */
    data object RolledBack : WriteOutcome

    /** The on-disk outcome could not be established; ordinary mutations must stop and recover. */
    data object Indeterminate : WriteOutcome
}
