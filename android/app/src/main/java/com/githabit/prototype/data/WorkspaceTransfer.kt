package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace
import java.io.InputStream

/** Owned, validated candidate. Provider contents are never read again after preview. */
class RestoreDocument private constructor(
    internal val workspace: Workspace,
    val sourceVersion: Int,
    val fingerprint: String,
) {
    val habitCount get() = workspace.habits.size
    val commitCount get() = workspace.commits.size

    companion object {
        /** Blocking IO; caller dispatches off Main and owns cancellation of the read. */
        fun read(openInput: () -> InputStream): RestoreDocument {
            val decoded = openInput().use { WorkspaceJson.decodeDocument(WorkspaceLimits.readBounded(it)) }
            // A small enough legacy input may expand beyond the v2 output limit.
            // Do not offer a candidate that cannot be saved losslessly.
            val (encoded, fingerprint) = WorkspaceJson.encodeAndDigest(decoded.workspace)
            require(WorkspaceJson.verifies(encoded, decoded.workspace)) {
                "This document cannot be restored without changing its values."
            }
            return RestoreDocument(decoded.workspace, decoded.sourceVersion, fingerprint)
        }
    }
}
