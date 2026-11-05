package com.githabit.prototype

import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.FakeAccessForViewModel
import com.githabit.prototype.data.UnreadableKind
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.HabitDraft
import com.githabit.prototype.domain.Workspace
import com.githabit.prototype.domain.addCommit
import com.githabit.prototype.domain.createHabit
import com.githabit.prototype.domain.AppClock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ViewModel-level storage policy: unreadable data must never become a writable workspace,
 * and recovery actions must preserve the original.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitHabitViewModelLoadTest {

    private val testDispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelId = 0

    private val clock = object : AppClock {
        var date: LocalDate = LocalDate.of(2026, 11, 5)
        override fun today() = date
        override fun now(): Instant = date.atStartOfDay(zone()).toInstant()
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private fun TestScope.model(access: FakeAccessForViewModel): GitHabitViewModel =
        GitHabitViewModel(access, clock = clock, ioDispatcher = testDispatcher, tickerScope = backgroundScope)
            .also { models.put("vm-${modelId++}", it) }

    private fun TestScope.notices(vm: GitHabitViewModel): MutableList<Notice> = mutableListOf<Notice>().also { notices ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.collect { notices += it } }
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun populated() = Workspace()
        .createHabit(HabitDraft("Read", "", 20.0, "pages"), LocalDate.of(2026, 11, 1), "read")
        .addCommit("read", 5.0, LocalDate.of(2026, 11, 2), id = "entry", timestamp = Instant.parse("2026-11-02T12:00:00Z"))

    @Test fun corruptStartupEntersRecoveryAndBlocksMutations() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel("{ not json".toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        val recovery = vm.load as WorkspaceLoad.Recovery
        assertEquals(UnreadableKind.CORRUPT, recovery.kind)
        assertTrue(recovery.preserved)
        assertTrue(vm.canSaveRecoveryCopy)

        vm.setTheme(false)
        vm.saveHabit(null, HabitDraft("X", "", 1.0, "unit"))
        vm.complete("read")
        advanceUntilIdle()

        assertEquals(0, access.writes)
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        assertTrue(vm.workspace.habits.isEmpty())
    }

    @Test fun missingStartupIsReadyNewWorkspace() = runTest(testDispatcher) {
        val vm = model(FakeAccessForViewModel(null))
        advanceUntilIdle()
        val ready = vm.load as WorkspaceLoad.Ready
        assertTrue(ready.isNew)
        assertEquals(Workspace(), vm.workspace)
    }

    @Test fun readableStartupPublishesWorkspace() = runTest(testDispatcher) {
        val expected = populated()
        val vm = model(FakeAccessForViewModel(WorkspaceJson.encode(expected).toByteArray()))
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Ready)
        assertEquals(expected, vm.workspace)
    }

    @Test fun retryLoadReconcilesWhateverIsOnDisk() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel("{ broken".toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Recovery)

        val restored = populated()
        access.stored = WorkspaceJson.encode(restored).toByteArray()
        vm.retryLoad()
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Ready)
        assertEquals(restored, vm.workspace)
    }

    @Test fun startOverFromRecoveryPreservesThenReplaces() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel("{ broken".toByteArray())
        val vm = model(access)
        advanceUntilIdle()

        vm.startOverFromRecovery()
        advanceUntilIdle()

        val ready = vm.load as WorkspaceLoad.Ready
        assertTrue(ready.isNew)
        assertEquals(Workspace(), vm.workspace)
        assertEquals(Workspace(), WorkspaceJson.decode(String(access.stored!!, Charsets.UTF_8)))
        assertTrue(access.preserved.isNotEmpty())
        // Ordinary mutations work again after recovery.
        vm.saveHabit(null, HabitDraft("Read", "", 5.0, "pages"))
        advanceUntilIdle()
        assertEquals(1, vm.workspace.habits.size)
    }

    @Test fun normalMutationsStillVerifyWrites() = runTest(testDispatcher) {
        val vm = model(FakeAccessForViewModel(null))
        advanceUntilIdle()
        vm.saveHabit(null, HabitDraft("Read", "", 5.0, "pages"))
        advanceUntilIdle()
        assertEquals(1, vm.workspace.habits.size)
        assertTrue(vm.load is WorkspaceLoad.Ready)
    }

    @Test fun commandsCannotWriteWhileStartupIsQueued() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel()
        val vm = model(access)
        assertEquals(WorkspaceLoad.Loading, vm.load)
        vm.signIn(); vm.explore(); vm.startEmpty(); vm.setTheme(false); vm.reset()
        vm.saveHabit(null, HabitDraft("Read", "", 5.0, "pages"))
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertEquals(Workspace(), vm.workspace)
    }

    @Test fun unsupportedStartupBlocksEveryOrdinaryMutation() = runTest(testDispatcher) {
        val bytes = WorkspaceJson.encode(populated()).replace("\"version\": 1", "\"version\": 99").toByteArray()
        val access = FakeAccessForViewModel(bytes)
        val vm = model(access)
        advanceUntilIdle()
        vm.signIn(); vm.signOut(); vm.explore(); vm.startEmpty(); vm.setTheme(false); vm.reset()
        vm.saveProfile("New", ""); vm.removeCommit("entry"); vm.complete("read")
        vm.saveCommit("read", null, 1.0, "")
        vm.status("read", com.githabit.prototype.domain.RepoStatus.PAUSED)
        vm.saveHabit(null, HabitDraft("New", "", 1.0, "unit"))
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertArrayEquals(bytes, access.stored)
        assertEquals(UnreadableKind.UNSUPPORTED_VERSION, (vm.load as WorkspaceLoad.Recovery).kind)
    }

    @Test fun failedRecoveryCopyIsNotReportedAsPreservedAndBlocksStartOver() = runTest(testDispatcher) {
        val original = "{ broken".toByteArray()
        val access = FakeAccessForViewModel(original).apply { corruptCopy = true }
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        assertFalse((vm.load as WorkspaceLoad.Recovery).preserved)
        assertTrue(vm.canSaveRecoveryCopy) // In-memory original can still be exported.
        vm.startOverFromRecovery()
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertArrayEquals(original, access.stored)
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        assertTrue(notices.last().message.contains("Nothing was replaced"))
    }

    @Test fun readFailureRecoveryActionsNeverOverwriteUnreadableOriginal() = runTest(testDispatcher) {
        val original = WorkspaceJson.encode(populated()).toByteArray()
        val access = FakeAccessForViewModel(original).apply { failReads = true }
        val vm = model(access)
        advanceUntilIdle()
        vm.retryLoad()
        advanceUntilIdle()
        vm.startOverFromRecovery()
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertArrayEquals(original, access.stored)
        assertTrue(vm.load is WorkspaceLoad.Recovery)
    }

    @Test fun failedWriteKeepsCommittedSnapshotAndDoesNotAnnounceSuccess() = runTest(testDispatcher) {
        val previous = populated()
        val access = FakeAccessForViewModel(WorkspaceJson.encode(previous).toByteArray())
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.failAllWrites = true
        vm.saveHabit(null, HabitDraft("New", "", 1.0, "unit"))
        advanceUntilIdle()
        assertEquals(previous, vm.workspace)
        assertTrue(vm.load is WorkspaceLoad.Ready)
        assertEquals(1, notices.size)
        assertTrue(notices.single().message.contains("previous file was verified"))
        assertNull(notices.single().undoId)
    }

    @Test fun successIsPublishedOnlyAfterReadBackVerification() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel()
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        access.beforeRead = {
            if (access.writes > 0) {
                assertTrue(vm.workspace.habits.isEmpty())
                assertTrue(notices.isEmpty())
            }
        }
        vm.saveHabit(null, HabitDraft("New", "", 1.0, "unit"))
        advanceUntilIdle()
        assertEquals(1, vm.workspace.habits.size)
        assertEquals(1, notices.size)
    }

    @Test fun indeterminateWritePausesAndRetryLoadsCandidateWithoutDuplicateLog() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(populated()).toByteArray())
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        val previous = vm.workspace
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failure") }
        vm.saveCommit("read", null, 2.0, "new log")
        advanceUntilIdle()
        assertEquals(previous, vm.workspace)
        assertEquals(UnreadableKind.INDETERMINATE, (vm.load as WorkspaceLoad.Recovery).kind)
        assertTrue(notices.last().message.contains("unknown"))
        vm.complete("read")
        advanceUntilIdle()
        assertEquals(2, access.writes)
        access.failReads = false
        vm.retryLoad()
        advanceUntilIdle()
        assertTrue(vm.load is WorkspaceLoad.Ready)
        assertEquals(2, vm.workspace.commits.size)
        assertEquals(2, access.writes)
        assertEquals(1, vm.workspace.commits.count { it.note == "new log" })
    }

    @Test fun failedUndoCanBeRetriedUsingTheSameToken() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(populated()).toByteArray())
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("entry")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        access.failAllWrites = true
        vm.undo(token)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
        assertEquals(token, notices.last().undoId)
        access.failAllWrites = false
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(listOf("entry"), vm.workspace.commits.map { it.id })
        val writes = access.writes
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(writes, access.writes)
    }

    @Test fun failedResetKeepsRouteDraftAndUndoButSuccessfulResetInvalidatesThem() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(populated()).toByteArray())
        val vm = model(access)
        val notices = notices(vm)
        advanceUntilIdle()
        vm.removeCommit("entry")
        advanceUntilIdle()
        val token = requireNotNull(notices.last().undoId)
        vm.navigate(Destination.DETAIL, "read")
        vm.overlay = Overlay.RepoForm("read")
        access.failAllWrites = true
        vm.reset()
        advanceUntilIdle()
        assertEquals(Destination.DETAIL, vm.destination)
        assertEquals(Overlay.RepoForm("read"), vm.overlay)
        access.failAllWrites = false
        vm.undo(token)
        advanceUntilIdle()
        assertEquals(1, vm.workspace.commits.size)
        vm.removeCommit("entry")
        advanceUntilIdle()
        val stale = requireNotNull(notices.last().undoId)
        vm.reset()
        advanceUntilIdle()
        assertEquals(Workspace(), vm.workspace)
        assertEquals(Destination.TODAY, vm.destination)
        assertNull(vm.overlay)
        vm.undo(stale)
        advanceUntilIdle()
        assertEquals(Workspace(), vm.workspace)
    }

    @Test fun duplicateInflightSubmissionIsRejected() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(populated()).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        vm.saveCommit("read", null, 1.0, "once")
        vm.saveCommit("read", null, 1.0, "once")
        advanceUntilIdle()
        assertEquals(1, vm.workspace.commits.count { it.note == "once" })
        assertEquals(1, access.writes)
    }

    @Test fun tickerUsesControlledTimeAndStopsWhenModelIsCleared() = runTest(testDispatcher) {
        val vm = model(FakeAccessForViewModel())
        advanceUntilIdle()
        clock.date = clock.date.plusDays(1)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(clock.date, vm.today)
        models.clear()
        val last = vm.today
        clock.date = clock.date.plusDays(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(last, vm.today)
    }

    @Test fun clearingModelDuringWriteDoesNotInterruptReconciliation() = runTest(testDispatcher) {
        val access = FakeAccessForViewModel()
        val vm = model(access)
        advanceUntilIdle()
        access.writeActions += { bytes -> access.stored = bytes; models.clear() }
        vm.saveHabit(null, HabitDraft("New", "", 1.0, "unit"))
        advanceUntilIdle()
        assertEquals(1, WorkspaceJson.decode(requireNotNull(access.stored).toString(Charsets.UTF_8)).habits.size)
        assertEquals(1, vm.workspace.habits.size)
        assertTrue(vm.load is WorkspaceLoad.Ready)
    }
}
