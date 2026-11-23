package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStoreTest {
    private val state = Workspace(name = "Owner")
        .createHabit(HabitDraft("Read", "", 20.0, "pages"), LocalDate.of(2026, 11, 1), "sample-edited")
        .addCommit("sample-edited", 12.0, LocalDate.of(2026, 11, 2),
            note = "A < B \"quoted\" 🌱", id = "entry", timestamp = Instant.parse("2026-11-02T12:30:00Z"))
    private val raw get() = WorkspaceJson.encode(state).toByteArray()
    private val broken get() = "{ not json".toByteArray()
    private fun store(access: FakeAccessForViewModel) = WorkspaceStore(access).also { it.load() }
    private fun disk(access: FakeAccessForViewModel) = WorkspaceJson.decode(requireNotNull(access.stored).toString(Charsets.UTF_8))

    @Test fun missingFileLoadsAsMissingThenBecomesReady() {
        val store = WorkspaceStore(FakeAccessForViewModel())
        assertEquals(WorkspaceLoadResult.Missing, store.load())
        assertEquals(WriteOutcome.Committed, store.write(Workspace()))
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.load())
    }

    @Test fun loadingMustAuthorizeWritesAndReplacements() {
        val access = FakeAccessForViewModel(raw)
        val store = WorkspaceStore(access)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertEquals(WriteOutcome.Blocked, store.replaceVerified(Workspace()))
        assertEquals(0, access.writes)
        assertArrayEquals(raw, access.stored)
    }

    @Test fun corruptFileIsPreservedAndOrdinaryWritesAreBlocked() {
        val access = FakeAccessForViewModel(broken)
        val store = WorkspaceStore(access)
        val result = store.load() as WorkspaceLoadResult.Unreadable
        assertEquals(UnreadableKind.CORRUPT, result.kind)
        assertTrue(result.preserved)
        assertArrayEquals(broken, access.preserved.single())
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertEquals(0, access.writes)
    }

    @Test fun unsupportedVersionIsPreservedNotReseeded() {
        val newer = WorkspaceJson.encode(state).replace("\"version\":2", "\"version\":99").toByteArray()
        val access = FakeAccessForViewModel(newer)
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.UNSUPPORTED_VERSION, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertArrayEquals(newer, store.recoveryBytes())
        assertArrayEquals(newer, access.stored)
    }

    @Test fun invalidDataFailsValidationWithoutModification() {
        val invalid = WorkspaceJson.encode(state).replace("\"target\":20.0", "\"target\":0").toByteArray()
        val access = FakeAccessForViewModel(invalid)
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.INVALID, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertArrayEquals(invalid, access.stored)
    }

    @Test fun readFailureBlocksWritesAndReplacement() {
        val access = FakeAccessForViewModel(raw).apply { failReads = true }
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.IO, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertEquals(WriteOutcome.Indeterminate, store.replaceVerified(Workspace()))
        assertEquals(0, access.writes)
        assertArrayEquals(raw, access.stored)
    }

    @Test fun writeFailureWithUnchangedFileIsVerifiedRollbackWithoutAnotherWrite() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.failAllWrites = true
        assertEquals(WriteOutcome.RolledBack, store.write(Workspace()))
        assertEquals(1, access.writes)
        assertEquals(state, disk(access))
        assertEquals(CopyKind.LAST_GOOD, access.copyKinds.single())
    }

    @Test fun thrownWriteThatActuallyCommittedIsRecognized() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { bytes -> access.stored = bytes; throw IOException("late failure") }
        assertEquals(WriteOutcome.Committed, store.write(Workspace()))
        assertEquals(Workspace(), disk(access))
        assertEquals(1, access.writes)
    }

    @Test fun verificationMismatchRollsBackToByteIdenticalPreviousFile() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { access.stored = broken }
        assertEquals(WriteOutcome.RolledBack, store.write(Workspace()))
        assertArrayEquals(raw, access.stored)
        assertTrue(access.preserved.any { it.contentEquals(broken) })
        assertEquals(2, access.writes)
    }

    @Test fun unreadableVerificationCanStillRollbackAndVerify() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = false }
        assertEquals(WriteOutcome.RolledBack, store.write(Workspace()))
        assertArrayEquals(raw, access.stored)
    }

    @Test fun rollbackFailureIsIndeterminateAndBlocksBlindRetry() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { access.stored = broken }
        access.writeActions += { throw IOException("rollback failed") }
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace()))
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertTrue(store.recoveryPreserved)
        assertTrue(access.preserved.any { it.contentEquals(raw) })
        assertTrue(access.preserved.any { it.contentEquals(broken) })
        assertTrue(access.preserved.any { it.contentEquals(WorkspaceJson.encode(Workspace()).toByteArray()) })
    }

    @Test fun firstWriteFailureWithVerifiedMissingFileCanRetry() {
        val access = FakeAccessForViewModel()
        val store = store(access)
        access.failAllWrites = true
        assertEquals(WriteOutcome.RolledBack, store.write(state))
        assertNull(access.stored)
        access.failAllWrites = false
        assertEquals(WriteOutcome.Committed, store.write(state))
    }

    @Test fun firstWriteWithUnverifiableContentDoesNotDeleteIt() {
        val access = FakeAccessForViewModel()
        val store = store(access)
        access.writeActions += { access.stored = broken }
        assertEquals(WriteOutcome.Indeterminate, store.write(state))
        assertArrayEquals(broken, access.stored)
        assertEquals(WriteOutcome.Blocked, store.write(state))
    }

    @Test fun replacementPreservesCurrentBytesWithinTheWriteBoundary() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { bytes ->
            assertArrayEquals(raw, access.preserved.single())
            assertEquals(CopyKind.RECOVERY, access.copyKinds.single())
            access.stored = bytes
        }
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertEquals(Workspace(), disk(access))
    }

    @Test fun failedPreservationPreventsNormalAndDestructiveWrites() {
        for (replacement in listOf(false, true)) {
            val access = FakeAccessForViewModel(raw)
            val store = store(access)
            access.failPreservation = true
            val result = if (replacement) store.replaceVerified(Workspace()) else store.write(Workspace())
            assertTrue(result is WriteOutcome.Rejected)
            assertEquals(0, access.writes)
            assertArrayEquals(raw, access.stored)
        }
    }

    @Test fun unverifiedCopyIsNotReportedAsPreserved() {
        for (failRead in listOf(false, true)) {
            val access = FakeAccessForViewModel(broken).apply {
                corruptCopy = !failRead
                failCopyReads = failRead
            }
            val store = WorkspaceStore(access)
            assertFalse((store.load() as WorkspaceLoadResult.Unreadable).preserved)
            assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
            assertArrayEquals(broken, access.stored)
            assertEquals(0, access.writes)
        }
    }

    @Test fun recoveryReplacementCanRollbackToCorruptOriginal() {
        val access = FakeAccessForViewModel(broken)
        val store = store(access)
        access.writeActions += { access.stored = "unexpected".toByteArray() }
        assertEquals(WriteOutcome.RolledBack, store.replaceVerified(Workspace()))
        assertArrayEquals(broken, access.stored)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
    }

    @Test fun corruptRecoveryReplacementSucceedsOnlyAfterPreservation() {
        val access = FakeAccessForViewModel(broken)
        val store = store(access)
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.load())
        assertTrue(access.preserved.any { it.contentEquals(broken) })
        assertEquals(WriteOutcome.Committed, store.write(state))
    }

    @Test fun missingAfterKnownDataNeverBecomesWritableEmpty() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.stored = null
        assertTrue(store.reload() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertArrayEquals(raw, store.recoveryBytes())
    }

    @Test fun restartWithMissingPrimaryAndRecoveryCopyIsNotFreshStartup() {
        val access = FakeAccessForViewModel()
        access.preserveCopy(raw)
        val store = WorkspaceStore(access)
        assertTrue(store.load() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertEquals(0, access.writes)
    }

    @Test fun retryReconcilesCandidateWithoutReplayingWrite() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failed") }
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace()))
        access.failReads = false
        val writes = access.writes
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.reload())
        assertEquals(writes, access.writes)
    }

    @Test fun changedFileSinceLoadIsPreservedNotOverwritten() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val other = WorkspaceJson.encode(state.copy(bio = "External update")).toByteArray()
        access.stored = other
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace()))
        assertEquals(0, access.writes)
        assertArrayEquals(other, access.stored)
        assertArrayEquals(other, store.recoveryBytes())
    }

    @Test fun invalidCandidateNeverReachesDisk() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        assertThrows(IllegalArgumentException::class.java) { store.write(state.copy(name = "x".repeat(41))) }
        assertEquals(0, access.writes)
        assertArrayEquals(raw, access.stored)
    }

    @Test fun roundTripPreservesEveryValueIncludingSampleLookingIds() {
        val access = FakeAccessForViewModel()
        val store = store(access)
        assertEquals(WriteOutcome.Committed, store.write(state))
        assertEquals(WorkspaceLoadResult.Ready(state), store.reload())
        assertEquals(state, disk(access))
    }

    @Test fun recoveryExportsAreRawDefensiveByteSnapshots() {
        val bytes = byteArrayOf(0x7b, 0xff.toByte(), 0xc0.toByte(), 0)
        val store = store(FakeAccessForViewModel(bytes))
        val snapshot = requireNotNull(store.recoveryBytes())
        assertArrayEquals(bytes, snapshot)
        snapshot[0] = 0
        assertArrayEquals(bytes, store.recoveryBytes())
    }

    @Test fun ownedLoadAndReadbackDoNotAliasMutableDiskOrPublicExports() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val export = requireNotNull(store.recoveryBytes())
        export.fill(0)
        assertArrayEquals(raw, store.recoveryBytes())
        access.stored!!.fill(0)
        assertArrayEquals(raw, store.recoveryBytes())
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace()))
        assertEquals(0, access.writes)
        assertTrue(access.preserved.any { it.contentEquals(raw) })

        val cleanAccess = FakeAccessForViewModel()
        val cleanStore = store(cleanAccess)
        assertEquals(WriteOutcome.Committed, cleanStore.write(state))
        cleanAccess.stored!!.fill(0)
        assertArrayEquals(raw, cleanStore.recoveryBytes())
    }

    @Test fun deduplicatedPreviousStillSurvivesRollbackAndFrozenExport() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val snapshot = requireNotNull(store.recoverySnapshot())
        access.writeActions += { access.stored = broken }
        assertEquals(WriteOutcome.RolledBack, store.write(Workspace()))
        assertArrayEquals(raw, access.stored)
        assertEquals(WriteOutcome.Committed, store.write(Workspace(bio = "later")))
        val output = java.io.ByteArrayOutputStream()
        snapshot.writeTo(output)
        assertArrayEquals(raw, output.toByteArray())
    }

    @Test fun recoveryExportSourceIsFrozenBeforeDestinationOpenEvenIfResetCommits() {
        val access = FakeAccessForViewModel(broken)
        val store = store(access)
        val snapshot = requireNotNull(store.recoverySnapshot())
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertEquals(WriteOutcome.Committed, store.write(Workspace(bio = "later change")))
        val output = java.io.ByteArrayOutputStream()
        snapshot.writeTo(output)
        assertArrayEquals(broken, output.toByteArray())
    }

    @Test fun hostileRecoveryDestinationCannotMutateFrozenSnapshotOrOwnedRecoveryBytes() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val snapshot = requireNotNull(store.recoverySnapshot())
        val received = java.io.ByteArrayOutputStream()
        snapshot.writeTo(object : java.io.OutputStream() {
            override fun write(byte: Int) { fail("Expected bounded bulk write") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                received.write(bytes, offset, length)
                bytes.fill(0)
            }
        })
        assertArrayEquals(raw, received.toByteArray())
        assertArrayEquals(raw, store.recoveryBytes())
        assertEquals(WriteOutcome.Committed, store.write(Workspace(bio = "later")))
        val repeated = java.io.ByteArrayOutputStream()
        snapshot.writeTo(repeated)
        assertArrayEquals(raw, repeated.toByteArray())
    }

    @Test fun malformedUtf8InsideOtherwiseValidJsonNeverBecomesWritable() {
        val bytes = raw
        val name = "Owner".toByteArray()
        val position = (0..bytes.size - name.size).first { bytes.copyOfRange(it, it + name.size).contentEquals(name) }
        bytes[position] = 0xff.toByte()
        assertEquals("\uFFFDwner", WorkspaceJson.decode(bytes.toString(Charsets.UTF_8)).name)
        val access = FakeAccessForViewModel(bytes)
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.CORRUPT, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(state))
        assertArrayEquals(bytes, access.stored)
        assertArrayEquals(bytes, store.recoveryBytes())
    }

    @Test fun startOverRetainsAllOutstandingSnapshotsBeforeRotatingLastGood() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val candidate = Workspace()
        access.writeActions += {
            access.stored = broken
            access.failPreservation = true
        }
        assertEquals(WriteOutcome.Indeterminate, store.write(candidate))
        assertFalse(store.recoveryPreserved)
        access.failPreservation = false
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertEquals(WriteOutcome.Committed, store.write(Workspace().copy(bio = "another save")))
        // This fake now rotates LAST_GOOD like production, not an append-only history.
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
        assertTrue(access.retainedCopies.any { it.contentEquals(broken) })
        assertTrue(access.retainedCopies.any { it.contentEquals(WorkspaceJson.encode(candidate).toByteArray()) })
    }

    @Test fun readableRetryWaitsForOutstandingSnapshotsToBePreserved() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        access.writeActions += {
            access.stored = broken
            access.failPreservation = true
        }
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace()))
        access.stored = WorkspaceJson.encode(Workspace()).toByteArray()
        assertTrue(store.reload() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        access.failPreservation = false
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.reload())
        assertEquals(WriteOutcome.Committed, store.write(Workspace().copy(bio = "another save")))
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
        assertTrue(access.retainedCopies.any { it.contentEquals(broken) })
    }

    @Test fun repeatedFailedRecoveryDoesNotReplaceAnUnpreservedCandidate() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val candidate = state.copy(bio = "uncertain personal addition")
        access.writeActions += { access.stored = broken; access.failPreservation = true }
        assertEquals(WriteOutcome.Indeterminate, store.write(candidate))
        access.failReads = true
        assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
        access.failReads = false
        access.failPreservation = false
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertTrue(access.retainedCopies.any { it.contentEquals(WorkspaceJson.encode(candidate).toByteArray()) })
    }

    @Test fun retryCannotForgetAnUnpreservedOriginalWhenDiskChangesAgain() {
        val first = "first damaged original".toByteArray()
        val second = "second damaged original".toByteArray()
        val access = FakeAccessForViewModel(first).apply { failPreservation = true }
        val store = store(access)
        val reads = access.reads
        access.stored = second
        assertTrue(store.reload() is WorkspaceLoadResult.Unreadable)
        assertEquals(reads, access.reads)
        assertArrayEquals(first, store.recoveryBytes())
        access.failPreservation = false
        assertTrue(store.reload() is WorkspaceLoadResult.Unreadable)
        assertTrue(access.retainedCopies.any { it.contentEquals(first) })
        assertTrue(access.retainedCopies.any { it.contentEquals(second) })
    }

    @Test fun coldRestartPreservesPersistedLastGoodBeforeStartOverAndRotation() {
        val access = FakeAccessForViewModel(raw)
        val originalStore = store(access)
        access.writeActions += {
            access.stored = broken
            access.failPreservation = true
        }
        assertEquals(WriteOutcome.Indeterminate, originalStore.write(Workspace()))
        assertArrayEquals(raw, access.lastGoodBytes())
        access.failPreservation = false
        val restarted = WorkspaceStore(access)
        assertTrue(restarted.load() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Committed, restarted.replaceVerified(Workspace()))
        assertEquals(WriteOutcome.Committed, restarted.write(Workspace().copy(bio = "rotates backup")))
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
    }

    @Test fun unreadableLastGoodFileCannotBeRotatedByStartOver() {
        val access = FakeAccessForViewModel(broken)
        access.preserveCopy(raw, CopyKind.LAST_GOOD)
        access.failLastGoodReads = true
        val store = WorkspaceStore(access)
        assertTrue(store.load() is WorkspaceLoadResult.Unreadable)
        assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
        assertEquals(0, access.writes)
        access.failLastGoodReads = false
        assertTrue(store.reload() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        assertEquals(WriteOutcome.Committed, store.write(Workspace().copy(bio = "rotates backup")))
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
    }

    @Test fun separateOwnersRejectAStaleSnapshotInsteadOfOverwritingAcknowledgedChanges() {
        val access = FakeAccessForViewModel(raw)
        val first = store(access)
        val second = store(access)
        val changed = state.copy(bio = "acknowledged edit")
        assertEquals(WriteOutcome.Committed, first.write(changed))
        assertEquals(WriteOutcome.Indeterminate, second.write(state.copy(name = "different edit")))
        assertEquals(changed, disk(access))
        assertEquals(1, access.writes)
        assertEquals(WorkspaceLoadResult.Ready(changed), second.reload())
    }

    @Test fun readsCannotInterleaveWithWriteVerification() {
        val access = FakeAccessForViewModel(raw)
        val store = store(access)
        val otherOwner = store(access)
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val readerStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            access.writeActions += { bytes ->
                writing.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                access.stored = bytes
            }
            val write = executor.submit<WriteOutcome> { store.write(Workspace()) }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            val reads = access.reads
            val read = executor.submit<WorkspaceLoadResult> { readerStarted.countDown(); otherOwner.reload() }
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS))
            assertEquals(reads, access.reads)
            assertFalse(read.isDone)
            release.countDown()
            assertEquals(WriteOutcome.Committed, write.get(5, TimeUnit.SECONDS))
            assertEquals(WorkspaceLoadResult.Ready(Workspace()), read.get(5, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }
}
