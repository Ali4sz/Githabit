package com.githabit.prototype.data

import android.content.Context
import com.githabit.prototype.domain.Workspace
import java.io.IOException
import java.nio.ByteBuffer
import org.json.JSONException

/**
 * Single owner of AtomicFile reads, writes, verification and recovery. Never open a
 * second recovery-capable reader while the app is running. All methods are blocking;
 * application callers dispatch IO off Main. A successful load authorizes ordinary writes.
 */
class WorkspaceStore(private val access: WorkspaceFileAccess) {
    constructor(context: Context) : this(AtomicFileAccess(context))

    private val lock = access.ownershipLock
    private var loaded = false
    private var writable = false
    private var preservationBlocked = false
    private var seenFile = false
    private var lastGoodBytes: ByteArray? = null
    private var persistedLastGoodBytes: ByteArray? = null
    private var original: ByteArray? = null
    private var uncertainCandidate: ByteArray? = null

    @Volatile var recoveryPreserved: Boolean = false
        private set
    @Volatile var hasRecoveryBytes: Boolean = false
        private set

    fun load(): WorkspaceLoadResult = synchronized(lock) {
        loaded = true
        writable = false
        preservationBlocked = true
        persistedLastGoodBytes = try { access.lastGoodBytes() } catch (_: Exception) {
            return@synchronized unreadable(UnreadableKind.IO, "The last-good recovery file could not be inspected.")
        }
        preservationBlocked = false
        val raw = try { access.readBytes() } catch (_: IOException) {
            return@synchronized unreadable(UnreadableKind.IO, "Workspace storage could not be read.")
        } catch (_: SecurityException) {
            return@synchronized unreadable(UnreadableKind.IO, "Workspace storage is not accessible.")
        }
        if (raw == null) {
            val backup = try { access.preservedBytes() } catch (_: Exception) {
                return@synchronized unreadable(UnreadableKind.IO, "Recovery storage could not be inspected.")
            }
            if (seenFile || backup != null || original != null || uncertainCandidate != null) {
                if (original == null) original = backup
                return@synchronized unreadable(UnreadableKind.IO,
                    "The workspace file is missing, but earlier data may still be recoverable.")
            }
            lastGoodBytes = null
            writable = true
            return@synchronized WorkspaceLoadResult.Missing
        }
        seenFile = true
        try {
            val workspace = WorkspaceJson.decode(raw)
            if ((original != null || uncertainCandidate != null) && !preserveOutstanding()) {
                return@synchronized unreadable(UnreadableKind.IO,
                    "The file is readable, but earlier recovery snapshots could not yet be preserved.")
            }
            lastGoodBytes = raw.copyOf()
            original = null
            uncertainCandidate = null
            writable = true
            hasRecoveryBytes = true
            WorkspaceLoadResult.Ready(workspace)
        } catch (error: Exception) {
            original = raw.copyOf()
            unreadable(kindOf(error), when (kindOf(error)) {
                UnreadableKind.UNSUPPORTED_VERSION -> "This workspace uses an unsupported format. Its bytes were left untouched."
                UnreadableKind.INVALID -> "Saved data did not pass validation."
                else -> "Saved data could not be decoded."
            })
        }
    }

    fun reload(): WorkspaceLoadResult = load()

    fun write(state: Workspace): WriteOutcome = synchronized(lock) {
        if (!writable) return@synchronized WriteOutcome.Blocked
        save(state, replacement = false)
    }

    /** Explicitly confirmed replacement. Preservation and write share this ownership boundary. */
    fun replaceVerified(state: Workspace): WriteOutcome = synchronized(lock) {
        if (!loaded) return@synchronized WriteOutcome.Blocked
        if (preservationBlocked) return@synchronized WriteOutcome.Rejected(
            "Re-read recovery storage before replacement. An earlier backup could not be inspected.")
        save(state, replacement = true)
    }

    /** Snapshot only: exporting recovery never decodes/re-encodes potentially damaged UTF-8. */
    fun recoveryBytes(): ByteArray? = synchronized(lock) {
        (original ?: lastGoodBytes ?: persistedLastGoodBytes ?: uncertainCandidate)?.copyOf()
    }

    private fun save(state: Workspace, replacement: Boolean): WriteOutcome {
        val candidate = WorkspaceJson.encode(state).toByteArray(Charsets.UTF_8)
        require(matches(candidate, state)) { "The workspace cannot be saved without changing its values." }

        val previous = try { access.readBytes() } catch (_: Exception) {
            return indeterminate(candidate, null)
        }
        if (!replacement && !sameBytes(previous, lastGoodBytes)) {
            // External changes or unexpected disappearance must be reconciled, not overwritten.
            return indeterminate(candidate, previous)
        }
        val snapshots = if (replacement) listOfNotNull(previous, original, lastGoodBytes, persistedLastGoodBytes, uncertainCandidate)
            .distinctBy { ByteBuffer.wrap(it) } else listOfNotNull(previous)
        val kind = if (replacement) CopyKind.RECOVERY else CopyKind.LAST_GOOD
        if ((replacement && !writable && snapshots.isEmpty()) || !snapshots.all { preserve(it, kind) }) {
            return WriteOutcome.Rejected("A verified preservation copy could not be saved. Nothing was replaced.")
        }

        try { access.writeBytes(candidate) } catch (_: Exception) {
            // A thrown finish/write does not prove the candidate was not committed.
        }
        val observed = readAttempt()
        if (observed.readable && observed.bytes != null && matches(observed.bytes, state)) {
            return committed(candidate)
        }
        if (observed.readable && sameBytes(observed.bytes, previous)) return WriteOutcome.RolledBack

        // Preserve any unexpected on-disk content before attempting rollback. Keep the
        // candidate too, so an uncertain new log is recoverable rather than silently lost.
        if (!preserve(candidate) || (observed.bytes != null && !preserve(observed.bytes))) {
            return indeterminate(candidate, observed.bytes)
        }
        if (previous != null) {
            try { access.writeBytes(previous) } catch (_: Exception) { /* Verify regardless. */ }
            val restored = readAttempt()
            if (restored.readable && sameBytes(restored.bytes, previous)) return WriteOutcome.RolledBack
            if (restored.bytes != null && matches(restored.bytes, state)) return committed(candidate)
            return indeterminate(candidate, restored.bytes ?: observed.bytes)
        }
        // No previous file to restore. Never delete an unverified first-write result.
        return indeterminate(candidate, observed.bytes)
    }

    private fun committed(candidate: ByteArray): WriteOutcome {
        lastGoodBytes = candidate
        persistedLastGoodBytes = null
        original = null
        uncertainCandidate = null
        seenFile = true
        writable = true
        hasRecoveryBytes = true
        return WriteOutcome.Committed
    }

    private fun indeterminate(candidate: ByteArray, observed: ByteArray?): WriteOutcome {
        writable = false
        uncertainCandidate = candidate
        if (observed != null) { original = observed.copyOf(); seenFile = true }
        preserveAvailable()
        return WriteOutcome.Indeterminate
    }

    private fun unreadable(kind: UnreadableKind, reason: String): WorkspaceLoadResult.Unreadable {
        preserveAvailable()
        return WorkspaceLoadResult.Unreadable(kind, reason, recoveryPreserved)
    }

    private fun preserveAvailable() {
        val available = listOfNotNull(original, lastGoodBytes, persistedLastGoodBytes, uncertainCandidate)
        hasRecoveryBytes = available.isNotEmpty()
        recoveryPreserved = false
        for (bytes in available) if (preserve(bytes)) recoveryPreserved = true
    }

    private fun preserveOutstanding(): Boolean = listOfNotNull(original, lastGoodBytes, persistedLastGoodBytes, uncertainCandidate)
        .distinctBy { ByteBuffer.wrap(it) }.all { preserve(it) }

    private fun preserve(bytes: ByteArray, kind: CopyKind = CopyKind.RECOVERY): Boolean = try {
        val file = access.preserveCopy(bytes, kind)
        file != null && access.readCopy(file).contentEquals(bytes)
    } catch (_: Exception) { false }

    private data class ReadAttempt(val readable: Boolean, val bytes: ByteArray?)
    private fun readAttempt(): ReadAttempt = try {
        ReadAttempt(true, access.readBytes())
    } catch (_: Exception) { ReadAttempt(false, null) }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a == null && b == null else a.contentEquals(b)

    private fun matches(raw: ByteArray, state: Workspace): Boolean = try {
        WorkspaceJson.decode(raw) == state
    } catch (_: Exception) { false }

    private fun kindOf(error: Exception): UnreadableKind = when {
        error is JSONException -> UnreadableKind.CORRUPT
        error.message?.contains("Unsupported workspace version") == true -> UnreadableKind.UNSUPPORTED_VERSION
        error is IllegalArgumentException -> UnreadableKind.INVALID
        else -> UnreadableKind.CORRUPT
    }
}
