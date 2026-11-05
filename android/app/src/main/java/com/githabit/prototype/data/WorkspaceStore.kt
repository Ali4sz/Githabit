package com.githabit.prototype.data

import android.content.Context
import com.githabit.prototype.domain.Workspace
import java.io.IOException
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

/** Frozen export source: selection cannot switch to a later reset/reconciled workspace. */
class RecoverySnapshot internal constructor(private val write: (OutputStream) -> Unit) {
    fun writeTo(output: OutputStream) = write(output)
}

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
    private var physicalRecovery: File? = null

    @Volatile var recoveryPreserved: Boolean = false
        private set
    @Volatile var hasRecoveryBytes: Boolean = false
        private set

    fun load(): WorkspaceLoadResult = synchronized(lock) {
        loaded = true
        writable = false
        // Never replace an unpreserved original/candidate with a later retry's bytes.
        if ((original != null || uncertainCandidate != null) && !preserveOutstanding()) {
            return@synchronized unreadable(UnreadableKind.IO,
                "Earlier recovery snapshots must be preserved before storage can be re-read.")
        }
        preservationBlocked = true
        persistedLastGoodBytes = try { access.lastGoodBytes() } catch (error: WorkspaceFileCapacityException) {
            if (error.recoveryCopy == null) return@synchronized capacityRecovery(error)
            // Already archived byte-for-byte with bounded memory. A retained oversized
            // archive must not prevent loading a now-reconciled supported primary.
            physicalRecovery = error.recoveryCopy
            null
        } catch (_: Exception) {
            return@synchronized unreadable(UnreadableKind.IO, "The last-good recovery file could not be inspected.")
        }
        preservationBlocked = false
        val raw = try { access.readBytes() } catch (error: WorkspaceFileCapacityException) {
            return@synchronized capacityRecovery(error)
        } catch (error: WorkspaceFileReadException) {
            if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
            return@synchronized unreadable(UnreadableKind.IO,
                "The workspace could not be opened. Retained physical evidence can be exported before a confirmed start-over.")
        } catch (_: IOException) {
            return@synchronized unreadable(UnreadableKind.IO, "Workspace storage could not be read.")
        } catch (_: SecurityException) {
            return@synchronized unreadable(UnreadableKind.IO, "Workspace storage is not accessible.")
        }
        if (raw == null) {
            val backup = try { access.preservedBytes() } catch (error: WorkspaceFileCapacityException) {
                return@synchronized capacityRecovery(error)
            } catch (_: Exception) {
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
            val document = WorkspaceJson.decodeDocument(raw)
            val workspace = document.workspace
            if ((original != null || uncertainCandidate != null) && !preserveOutstanding()) {
                return@synchronized unreadable(UnreadableKind.IO,
                    "The file is readable, but earlier recovery snapshots could not yet be preserved.")
            }
            lastGoodBytes = raw.copyOf()
            if (document.sourceVersion == 1) {
                original = raw.copyOf()
                // Independently retain all earlier snapshots, not just LAST_GOOD,
                // which ordinary saves are allowed to rotate after migration.
                if (!preserveOutstanding()) return@synchronized unreadable(UnreadableKind.IO,
                    "The legacy original could not be preserved. Migration did not proceed.")
                return@synchronized when (save(workspace, replacement = false, migration = true)) {
                    WriteOutcome.Committed -> WorkspaceLoadResult.Ready(workspace)
                    WriteOutcome.Indeterminate -> unreadable(UnreadableKind.INDETERMINATE,
                        "Migration could not be verified. Re-read storage before making changes.")
                    else -> unreadable(UnreadableKind.IO,
                        "Migration was not committed. The legacy original is retained; retry after checking storage.")
                }
            }
            original = null
            uncertainCandidate = null
            physicalRecovery = null
            writable = true
            hasRecoveryBytes = true
            WorkspaceLoadResult.Ready(workspace)
        } catch (error: Exception) {
            original = raw.copyOf()
            unreadable(kindOf(error), when (kindOf(error)) {
                UnreadableKind.UNSUPPORTED_VERSION -> "This workspace uses an unsupported format. Its bytes were left untouched."
                UnreadableKind.INVALID -> "Saved data did not pass validation."
                UnreadableKind.LEGACY_INCOMPATIBLE -> "Legacy data cannot be migrated without changing values. Its original was retained."
                UnreadableKind.CAPACITY -> "Workspace exceeds supported capacity. Its original was retained without truncation."
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
            "Reconcile and re-read storage before replacement. An oversized or uninspectable file still needs recovery.")
        save(state, replacement = true)
    }

    /** Snapshot only: exporting recovery never decodes/re-encodes potentially damaged UTF-8. */
    fun recoveryBytes(): ByteArray? = synchronized(lock) {
        (original ?: lastGoodBytes ?: persistedLastGoodBytes ?: uncertainCandidate)?.copyOf()
    }

    /** Capture before opening an external destination; raw archives are never rotated. */
    fun recoverySnapshot(): RecoverySnapshot? = synchronized(lock) {
        val file = physicalRecovery
        if (file != null) RecoverySnapshot { output -> access.copyRecoveryTo(file, output) }
        else recoveryBytes()?.let { bytes -> RecoverySnapshot { output -> output.write(bytes) } }
    }

    private fun save(state: Workspace, replacement: Boolean, migration: Boolean = false): WriteOutcome {
        if (!writable && (original != null || uncertainCandidate != null) && !preserveOutstanding()) {
            return WriteOutcome.Rejected("Earlier recovery snapshots could not be preserved. Nothing was replaced.")
        }
        val candidate = WorkspaceJson.encode(state).toByteArray(Charsets.UTF_8)
        require(matches(candidate, state)) { "The workspace cannot be saved without changing its values." }

        val previous = try { access.readBytes() } catch (error: WorkspaceFileCapacityException) {
            capacityRecovery(error)
            return indeterminate(candidate, null)
        } catch (error: WorkspaceFileReadException) {
            if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
            // Only an explicitly confirmed replacement may supersede an interrupted
            // first-write sidecar, freshly archived under this same storage lock.
            if (!replacement || !error.sidecarOnly || error.recoveryCopy == null) return indeterminate(candidate, null)
            null
        } catch (_: Exception) {
            return indeterminate(candidate, null)
        }
        if (!replacement && !sameBytes(previous, lastGoodBytes)) {
            // External changes or unexpected disappearance must be reconciled, not overwritten.
            return indeterminate(candidate, previous)
        }
        val snapshots = if (replacement) listOfNotNull(previous, original, lastGoodBytes, persistedLastGoodBytes, uncertainCandidate)
            .distinctBy { ByteBuffer.wrap(it) } else listOfNotNull(previous)
        val kind = if (replacement || migration) CopyKind.RECOVERY else CopyKind.LAST_GOOD
        if ((replacement && !writable && snapshots.isEmpty() && physicalRecovery == null) || !snapshots.all { preserve(it, kind) }) {
            return WriteOutcome.Rejected("A verified preservation copy could not be saved. Nothing was replaced.")
        }

        try { access.writeBytes(candidate) } catch (_: Exception) {
            // A thrown finish/write does not prove the candidate was not committed.
        }
        val observed = readAttempt()
        if (preservationBlocked) return indeterminate(candidate, observed.bytes)
        if (observed.readable && observed.bytes != null && matches(observed.bytes, state)) {
            return committed(observed.bytes)
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
            if (restored.bytes != null && matches(restored.bytes, state)) return committed(restored.bytes)
            return indeterminate(candidate, restored.bytes ?: observed.bytes)
        }
        // No previous file to restore. Never delete an unverified first-write result.
        return indeterminate(candidate, observed.bytes)
    }

    private fun committed(observed: ByteArray): WriteOutcome {
        // The file may be value-equivalent JSON with different whitespace/field order.
        // Stale-snapshot checks must compare with actual verified bytes, not our encoder.
        lastGoodBytes = observed.copyOf()
        persistedLastGoodBytes = null
        original = null
        uncertainCandidate = null
        physicalRecovery = null
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

    private fun capacityRecovery(error: WorkspaceFileCapacityException): WorkspaceLoadResult.Unreadable {
        writable = false
        preservationBlocked = true
        seenFile = true
        if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
        return unreadable(UnreadableKind.CAPACITY,
            "A stored file exceeds supported capacity. It was not truncated or replaced. " +
                "Save the recovery copy if available; replacement stays blocked until storage is reconciled.")
    }

    private fun preserveAvailable() {
        val available = listOfNotNull(original, lastGoodBytes, persistedLastGoodBytes, uncertainCandidate)
        hasRecoveryBytes = available.isNotEmpty() || physicalRecovery != null
        recoveryPreserved = physicalRecovery != null
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
    } catch (error: WorkspaceFileCapacityException) {
        capacityRecovery(error)
        ReadAttempt(false, null)
    } catch (error: WorkspaceFileReadException) {
        if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
        ReadAttempt(false, null)
    } catch (_: Exception) { ReadAttempt(false, null) }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a == null && b == null else a.contentEquals(b)

    private fun matches(raw: ByteArray, state: Workspace): Boolean = try {
        val document = WorkspaceJson.decodeDocument(raw)
        document.sourceVersion == WorkspaceJson.VERSION && document.workspace == state
    } catch (_: Exception) { false }

    private fun kindOf(error: Exception): UnreadableKind = when (error) {
        is WorkspaceJsonException -> when (error.kind) {
            WorkspaceJsonFailure.CORRUPT -> UnreadableKind.CORRUPT
            WorkspaceJsonFailure.UNSUPPORTED_VERSION -> UnreadableKind.UNSUPPORTED_VERSION
            WorkspaceJsonFailure.INVALID -> UnreadableKind.INVALID
            WorkspaceJsonFailure.LEGACY_INCOMPATIBLE -> UnreadableKind.LEGACY_INCOMPATIBLE
            WorkspaceJsonFailure.CAPACITY -> UnreadableKind.CAPACITY
        }
        is IllegalArgumentException -> UnreadableKind.INVALID
        else -> UnreadableKind.CORRUPT
    }
}
