package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class WorkspaceMigrationTest {
    private val original get() = legacyFixture("v1-mixed-signed-out-dark.json")

    @Test fun migrationPreservesOriginalBeforeWritingAndRunsOnlyOnceAcrossRestart() {
        val raw = original
        val expected = WorkspaceJson.decode(raw)
        val access = FakeAccessForViewModel(raw)
        access.writeActions += { candidate ->
            assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
            assertTrue(access.copyKinds.all { it == CopyKind.RECOVERY })
            assertEquals(2, WorkspaceJson.decodeDocument(candidate).sourceVersion)
            access.stored = candidate
        }
        assertEquals(WorkspaceLoadResult.Ready(expected), WorkspaceStore(access).load())
        assertEquals(1, access.writes)
        val restarted = WorkspaceStore(access)
        assertEquals(WorkspaceLoadResult.Ready(expected), restarted.load())
        assertEquals(1, access.writes)
        assertEquals(WriteOutcome.Committed, restarted.write(expected.copy(bio = "edit")))
        assertEquals(WriteOutcome.Committed, restarted.write(expected.copy(bio = "another edit")))
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
    }

    @Test fun unchangedV1BytesCannotFalselyVerifyAMigration() {
        val access = FakeAccessForViewModel(original).apply { failAllWrites = true }
        val store = WorkspaceStore(access)
        assertTrue(store.load() is WorkspaceLoadResult.Unreadable)
        assertEquals(1, access.writes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertArrayEquals(original, access.stored)
        assertEquals(1, WorkspaceJson.decodeDocument(requireNotNull(access.stored)).sourceVersion)
        access.failAllWrites = false
        assertTrue(store.reload() is WorkspaceLoadResult.Ready)
        assertEquals(2, access.writes)
    }

    @Test fun failedOrUnverifiedPreservationNeverStartsMigration() {
        for (mode in 0..2) {
            val access = FakeAccessForViewModel(original).apply {
                failPreservation = mode == 0; corruptCopy = mode == 1; failCopyReads = mode == 2
            }
            val store = WorkspaceStore(access)
            assertTrue(store.load() is WorkspaceLoadResult.Unreadable)
            assertEquals(0, access.writes)
            assertArrayEquals(original, access.stored)
            assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        }
    }

    @Test fun failedMigrationVerificationRollsBackToExactLegacyBytes() {
        val access = FakeAccessForViewModel(original)
        access.writeActions += { access.stored = "bad migration".toByteArray() }
        val store = WorkspaceStore(access)
        assertTrue(store.load() is WorkspaceLoadResult.Unreadable)
        assertArrayEquals(original, access.stored)
        assertEquals(2, access.writes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertTrue(WorkspaceStore(access).load() is WorkspaceLoadResult.Ready)
    }

    @Test fun failedRollbackRetainsOriginalAcrossColdRestartAndReset() {
        val raw = original
        val access = FakeAccessForViewModel(raw)
        access.writeActions += { access.stored = "broken".toByteArray() }
        access.writeActions += { throw IOException("rollback") }
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.INDETERMINATE, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        val restarted = WorkspaceStore(access)
        assertTrue(restarted.load() is WorkspaceLoadResult.Unreadable)
        assertEquals(WriteOutcome.Committed, restarted.replaceVerified(Workspace()))
        assertEquals(WriteOutcome.Committed, restarted.write(Workspace(bio = "rotates backup")))
        assertTrue(access.retainedCopies.any { it.contentEquals(raw) })
    }

    @Test fun actualObservedFormattingIsUsedForSubsequentStaleSnapshotChecks() {
        val access = FakeAccessForViewModel(original)
        access.writeActions += { access.stored = ("\n " + it.toString(Charsets.UTF_8) + "\n").toByteArray() }
        val store = WorkspaceStore(access)
        val state = (store.load() as WorkspaceLoadResult.Ready).workspace
        assertEquals(WriteOutcome.Committed, store.write(state.copy(bio = "next save")))
        assertEquals(2, access.writes)
    }

    @Test fun incompatibleLegacyDataStaysUnmodifiedAndRecoverable() {
        val raw = original.toString(Charsets.UTF_8).replace("\"amount\": 7.125", "\"amount\": 7.1251").toByteArray()
        val access = FakeAccessForViewModel(raw)
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.LEGACY_INCOMPATIBLE, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertArrayEquals(raw, access.stored)
        assertArrayEquals(raw, store.recoveryBytes())
        assertEquals(0, access.writes)
    }
}
