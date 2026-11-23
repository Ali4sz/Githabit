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
                StorageMessage.PRESERVE_BEFORE_READ)
        }
        preservationBlocked = true
        persistedLastGoodBytes = try { access.lastGoodBytes() } catch (error: WorkspaceFileCapacityException) {
            if (error.recoveryCopy == null) return@synchronized capacityRecovery(error)
            // Already archived byte-for-byte with bounded memory. A retained oversized
            // archive must not prevent loading a now-reconciled supported primary.
            physicalRecovery = error.recoveryCopy
            null
        } catch (_: Exception) {
            return@synchronized unreadable(UnreadableKind.IO, StorageMessage.LAST_GOOD_UNINSPECTED)
        }
        preservationBlocked = false
        val raw = try { access.readBytes() } catch (error: WorkspaceFileCapacityException) {
            return@synchronized capacityRecovery(error)
        } catch (error: WorkspaceFileReadException) {
            if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
            return@synchronized unreadable(UnreadableKind.IO,
                StorageMessage.OPEN_FAILED)
        } catch (_: IOException) {
            return@synchronized unreadable(UnreadableKind.IO, StorageMessage.READ_FAILED)
        } catch (_: SecurityException) {
            return@synchronized unreadable(UnreadableKind.IO, StorageMessage.INACCESSIBLE)
        }
        if (raw == null) {
            val backup = try { access.preservedBytes() } catch (error: WorkspaceFileCapacityException) {
                return@synchronized capacityRecovery(error)
            } catch (_: Exception) {
                return@synchronized unreadable(UnreadableKind.IO, StorageMessage.RECOVERY_UNINSPECTED)
            }
            if (seenFile || backup != null || original != null || uncertainCandidate != null) {
                if (original == null) original = backup
                return@synchronized unreadable(UnreadableKind.IO,
                    StorageMessage.MISSING_WITH_EVIDENCE)
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
                    StorageMessage.READABLE_UNPRESERVED)
            }
            lastGoodBytes = raw
            if (document.sourceVersion == 1) {
                original = raw
                // Independently retain all earlier snapshots, not just LAST_GOOD,
                // which ordinary saves are allowed to rotate after migration.
                if (!preserveOutstanding()) return@synchronized unreadable(UnreadableKind.IO,
                    StorageMessage.LEGACY_UNPRESERVED)
                return@synchronized when (save(workspace, replacement = false, migration = true)) {
                    WriteOutcome.Committed -> WorkspaceLoadResult.Ready(workspace)
                    WriteOutcome.Indeterminate -> unreadable(UnreadableKind.INDETERMINATE,
                        StorageMessage.MIGRATION_UNVERIFIED)
                    else -> unreadable(UnreadableKind.IO,
                        StorageMessage.MIGRATION_UNCOMMITTED)
                }
            }
            original = null
            uncertainCandidate = null
            physicalRecovery = null
            writable = true
            hasRecoveryBytes = true
            WorkspaceLoadResult.Ready(workspace)
        } catch (error: Exception) {
            original = raw
            unreadable(kindOf(error), when (kindOf(error)) {
                UnreadableKind.UNSUPPORTED_VERSION -> StorageMessage.UNSUPPORTED
                UnreadableKind.INVALID -> StorageMessage.INVALID
                UnreadableKind.LEGACY_INCOMPATIBLE -> StorageMessage.LEGACY_INCOMPATIBLE
                UnreadableKind.CAPACITY -> StorageMessage.CAPACITY
                else -> StorageMessage.DECODE_FAILED
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
            StorageMessage.REPLACEMENT_BLOCKED)
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
        else (original ?: lastGoodBytes ?: persistedLastGoodBytes ?: uncertainCandidate)?.let { bytes ->
            // Private immutable owned array; later store reference changes cannot mutate this source.
            RecoverySnapshot { output -> output.writeSnapshot(bytes) }
        }
    }

    private fun save(state: Workspace, replacement: Boolean, migration: Boolean = false): WriteOutcome {
        if (!writable && (original != null || uncertainCandidate != null) && !preserveOutstanding()) {
            return WriteOutcome.Rejected(StorageMessage.EARLIER_UNPRESERVED)
        }
        val candidate = WorkspaceJson.encodeToBytes(state)
        require(matches(candidate, state)) { "The workspace cannot be saved without changing its values." }

        // Avoid allocating an equal full-file read; previous still survives every rollback check.
        val previous = try { access.readBytesMatching(listOfNotNull(lastGoodBytes, original))
        } catch (error: WorkspaceFileCapacityException) {
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
            return WriteOutcome.Rejected(StorageMessage.COPY_UNVERIFIED)
        }

        try { access.writeBytes(candidate) } catch (_: Exception) {
            // A thrown finish/write does not prove the candidate was not committed.
        }
        val observed = readAttempt(listOfNotNull(candidate, previous))
        if (preservationBlocked) return indeterminate(candidate, observed.bytes)
        if (observed.readable && observed.bytes != null &&
            (observed.bytes.contentEquals(candidate) || matches(observed.bytes, state))) {
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
            val restored = readAttempt(listOf(previous, candidate))
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
        lastGoodBytes = observed
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
        if (observed != null) { original = observed; seenFile = true }
        preserveAvailable()
        return WriteOutcome.Indeterminate
    }

    private fun unreadable(kind: UnreadableKind, message: StorageMessage): WorkspaceLoadResult.Unreadable {
        preserveAvailable()
        return WorkspaceLoadResult.Unreadable(kind, message, recoveryPreserved)
    }

    private fun capacityRecovery(error: WorkspaceFileCapacityException): WorkspaceLoadResult.Unreadable {
        writable = false
        preservationBlocked = true
        seenFile = true
        if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
        return unreadable(UnreadableKind.CAPACITY,
            StorageMessage.PHYSICAL_CAPACITY)
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
        file != null && access.copyMatches(file, bytes)
    } catch (_: Exception) { false }

    private data class ReadAttempt(val readable: Boolean, val bytes: ByteArray?)
    private fun readAttempt(expected: List<ByteArray>): ReadAttempt = try {
        ReadAttempt(true, access.readBytesMatching(expected))
    } catch (error: WorkspaceFileCapacityException) {
        capacityRecovery(error)
        ReadAttempt(false, null)
    } catch (error: WorkspaceFileReadException) {
        if (error.recoveryCopy != null) physicalRecovery = error.recoveryCopy
        ReadAttempt(false, null)
    } catch (_: Exception) { ReadAttempt(false, null) }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a == null && b == null else a.contentEquals(b)

    private fun matches(raw: ByteArray, state: Workspace): Boolean = WorkspaceJson.verifies(raw, state)

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
