package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceCapacityStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    private class CapacityAccess(val backing: FakeAccessForViewModel, val archive: File) : WorkspaceFileAccess by backing {
        var primaryTooLarge = false
        var backupTooLarge = false
        var preservationFails = false
        override fun readBytes(): ByteArray? {
            if (primaryTooLarge) throw WorkspaceFileCapacityException(if (preservationFails) null else archive)
            return backing.readBytes()
        }
        override fun readBytesMatching(expected: List<ByteArray>): ByteArray? = super<WorkspaceFileAccess>.readBytesMatching(expected)
        override fun lastGoodBytes(): ByteArray? {
            if (backupTooLarge) throw WorkspaceFileCapacityException(if (preservationFails) null else archive)
            return backing.lastGoodBytes()
        }
        override fun copyRecoveryTo(file: File, output: OutputStream) { file.inputStream().use { it.copyTo(output) } }
    }

    private fun access() = CapacityAccess(FakeAccessForViewModel(WorkspaceJson.encode(Workspace()).toByteArray()),
        temporary.newFile().apply { writeBytes(byteArrayOf(0, 0xff.toByte(), 42)) })

    @Test fun oversizedStartupIsNonwritableAndExportsItsPreservedRawSnapshot() {
        val access = access().apply { primaryTooLarge = true }
        val store = WorkspaceStore(access)
        val result = store.load() as WorkspaceLoadResult.Unreadable
        assertEquals(UnreadableKind.CAPACITY, result.kind)
        assertTrue(result.preserved)
        assertTrue(store.hasRecoveryBytes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
        assertEquals(0, access.backing.writes)
        val exported = ByteArrayOutputStream()
        requireNotNull(store.recoverySnapshot()).writeTo(exported)
        assertArrayEquals(access.archive.readBytes(), exported.toByteArray())
        access.primaryTooLarge = false
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.reload())
        assertEquals(WriteOutcome.Committed, store.write(Workspace(bio = "reconciled")))
        assertTrue(access.archive.exists())
    }

    @Test fun failedOversizedPreservationBlocksReplacementAndReportsNoCopy() {
        val access = access().apply { primaryTooLarge = true; preservationFails = true }
        val store = WorkspaceStore(access)
        val result = store.load() as WorkspaceLoadResult.Unreadable
        assertEquals(UnreadableKind.CAPACITY, result.kind)
        assertFalse(result.preserved)
        assertFalse(store.hasRecoveryBytes)
        assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
        assertEquals(0, access.backing.writes)
    }

    @Test fun independentlyArchivedOversizedBackupDoesNotBlockAHealthyPrimary() {
        val access = access().apply { backupTooLarge = true }
        val store = WorkspaceStore(access)
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.load())
        assertEquals(WriteOutcome.Committed, store.write(Workspace(bio = "ordinary save")))
        assertArrayEquals(byteArrayOf(0, 0xff.toByte(), 42), access.archive.readBytes())
        access.preservationFails = true
        assertEquals(UnreadableKind.CAPACITY, (WorkspaceStore(access).load() as WorkspaceLoadResult.Unreadable).kind)
    }

    @Test fun overflowDuringVerificationIsIndeterminateWithoutDestructiveRollback() {
        val access = access()
        val store = WorkspaceStore(access)
        store.load()
        access.backing.writeActions += { access.backing.stored = it; access.primaryTooLarge = true }
        assertEquals(WriteOutcome.Indeterminate, store.write(Workspace(bio = "candidate")))
        assertEquals(1, access.backing.writes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertTrue(store.replaceVerified(Workspace()) is WriteOutcome.Rejected)
        val exported = ByteArrayOutputStream()
        requireNotNull(store.recoverySnapshot()).writeTo(exported)
        assertArrayEquals(access.archive.readBytes(), exported.toByteArray())
        access.primaryTooLarge = false
        assertEquals(WorkspaceLoadResult.Ready(Workspace(bio = "candidate")), store.reload())
        assertEquals(1, access.backing.writes)
    }

    @Test fun physicalSnapshotRemainsFrozenAfterReconciliationAndBackupRotation() {
        val access = access().apply { primaryTooLarge = true }
        val store = WorkspaceStore(access)
        store.load()
        val snapshot = requireNotNull(store.recoverySnapshot())
        access.primaryTooLarge = false
        store.reload()
        store.write(Workspace(bio = "later state"))
        val output = ByteArrayOutputStream()
        snapshot.writeTo(output)
        assertArrayEquals(access.archive.readBytes(), output.toByteArray())
    }

    @Test fun retainedSidecarOnlyEvidenceIsExportableAndExplicitlyReplaceable() {
        val backing = FakeAccessForViewModel()
        val archive = temporary.newFile().apply { writeText("unfinished first write") }
        val access = object : WorkspaceFileAccess by backing {
            override fun readBytes(): ByteArray? = backing.stored ?: throw WorkspaceFileReadException(archive, true, java.io.IOException("only .new"))
            override fun readBytesMatching(expected: List<ByteArray>): ByteArray? = super<WorkspaceFileAccess>.readBytesMatching(expected)
            override fun copyRecoveryTo(file: File, output: OutputStream) { output.write(file.readBytes()) }
        }
        val store = WorkspaceStore(access)
        val result = store.load() as WorkspaceLoadResult.Unreadable
        assertTrue(result.preserved)
        assertTrue(store.hasRecoveryBytes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        val snapshot = requireNotNull(store.recoverySnapshot())
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        val output = ByteArrayOutputStream()
        snapshot.writeTo(output)
        assertEquals("unfinished first write", output.toString(Charsets.UTF_8.name()))
    }
}
