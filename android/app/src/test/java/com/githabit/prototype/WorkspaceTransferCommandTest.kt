package com.githabit.prototype

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceTransferCommandTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val clock = object : AppClock {
        var instant = Instant.parse("2026-11-04T12:00:00Z")
        override fun now() = instant
        override fun zone() = ZoneId.of("UTC")
        override fun today() = now().atZone(zone()).toLocalDate()
    }
    private val original = Workspace(onboarded = true).createHabit(
        HabitDraft("Practice", "", 10.0, "minutes"), LocalDate.parse("2026-11-02"), "habit")
    private fun access() = FakeAccessForViewModel(WorkspaceJson.encode(original).toByteArray())
    private fun TestScope.model(access: FakeAccessForViewModel, saved: SavedStateHandle = SavedStateHandle()) =
        GitHabitViewModel(access, clock, dispatcher, backgroundScope, saved).also { models.put("${System.identityHashCode(it)}", it) }
    private fun TestScope.events(vm: GitHabitViewModel) = mutableListOf<Notice>().also { events ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.collect { events += it } }
    }
    private fun TestScope.preview(vm: GitHabitViewModel, candidate: Workspace = Workspace(name = "Restored")): RestoreState.Preview {
        val request = vm.requestRestore()!!
        vm.readRestore(request) { ByteArrayInputStream(WorkspaceJson.encode(candidate).toByteArray()) }
        advanceUntilIdle()
        return vm.restoreState as RestoreState.Preview
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun selectionCancellationValidationAndStaleCallbacksDoNotTouchWorkspaceOrDraft() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("habit")
        vm.editRepoForm { it.copy(description = "dirty") }
        val draft = vm.formState
        val first = vm.requestRestore()!!
        val second = vm.requestRestore()!!
        vm.readRestore(first) { error("stale callback must not open provider") }
        vm.readRestore(second, null)
        assertNull(vm.restoreState)
        val bad = vm.requestRestore()!!
        vm.readRestore(bad) { ByteArrayInputStream("{\"version\":999}".toByteArray()) }
        advanceUntilIdle()
        assertTrue(vm.restoreState is RestoreState.Failed)
        assertEquals(draft, vm.formState)
        assertEquals(original, vm.workspace)
        assertEquals(0, access.writes)
    }

    @Test fun revisedWorkspaceRequiresFreshConsentAndOldPreviewCannotConfirm() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        val first = preview(vm)
        vm.saveCommit("habit", null, 1.0, "new edit")
        advanceUntilIdle()
        vm.confirmRestore(first)
        advanceUntilIdle()
        val revised = vm.restoreState as RestoreState.Preview
        assertNotSame(first, revised)
        assertTrue(revised.consentUpdated)
        assertEquals(1, revised.currentCommitCount)
        assertEquals(1, access.writes)
        vm.confirmRestore(first)
        advanceUntilIdle()
        assertEquals(1, access.writes)
        vm.confirmRestore(revised)
        advanceUntilIdle()
        assertEquals(Workspace(name = "Restored"), vm.workspace)
        assertNull(vm.restoreState)
        assertTrue(access.retainedCopies.any { WorkspaceJson.decode(it).commits.size == 1 })
    }

    @Test fun changedProviderIsNotReadAfterPreviewAndFailedWriteKeepsDraftUndoAndCandidate() = runTest(dispatcher) {
        val access = access(); val vm = model(access); val events = events(vm)
        advanceUntilIdle()
        vm.saveCommit("habit", null, 2.0, "saved")
        advanceUntilIdle()
        val undo = events.last().undoId!!
        vm.navigate(Destination.DETAIL, "habit")
        vm.overlay = Overlay.RepoForm("habit")
        vm.editRepoForm { it.copy(description = "dirty") }
        val draft = vm.formState; val route = vm.navigation; val session = vm.workspaceSession
        var provider = WorkspaceJson.encode(Workspace(name = "Confirmed")).toByteArray()
        var opens = 0
        val request = vm.requestRestore()!!
        vm.readRestore(request) { opens++; ByteArrayInputStream(provider) }
        advanceUntilIdle()
        val preview = vm.restoreState as RestoreState.Preview
        provider = WorkspaceJson.encode(Workspace(name = "Unconfirmed")).toByteArray()
        access.failAllWrites = true
        vm.confirmRestore(preview); vm.confirmRestore(preview)
        advanceUntilIdle()
        assertEquals(1, opens)
        assertSame(preview, vm.restoreState)
        assertEquals(draft, vm.formState)
        assertSame(route, vm.navigation)
        assertEquals(session, vm.workspaceSession)
        access.failAllWrites = false
        vm.undo(undo)
        advanceUntilIdle()
        assertTrue(vm.workspace.commits.isEmpty())
        vm.confirmRestore(preview)
        advanceUntilIdle() // Undo invalidated consent.
        vm.confirmRestore(vm.restoreState as RestoreState.Preview)
        advanceUntilIdle()
        assertEquals("Confirmed", vm.workspace.name)
        assertNull(vm.overlay)
        assertEquals(session + 1, vm.workspaceSession)
        assertEquals(1, opens)
    }

    @Test fun previewConsentDoesNotSurviveNewViewModelAndEqualReplacementClearsTransientState() = runTest(dispatcher) {
        val access = access(); val saved = SavedStateHandle(); val vm = model(access, saved)
        advanceUntilIdle()
        vm.navigate(Destination.DETAIL, "habit")
        vm.overlay = Overlay.RepoForm("habit")
        vm.editRepoForm { it.copy(description = "dirty") }
        val preview = preview(vm, original)
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        advanceUntilIdle()
        assertNull(restored.restoreState)
        restored.confirmRestore(preview)
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertNotNull(restored.formState)
        vm.confirmRestore(preview)
        advanceUntilIdle()
        assertEquals(original, vm.workspace)
        assertEquals(1, vm.workspaceSession)
        assertNull(vm.formState)
    }

    @Test fun exportFreezesRequestSnapshotEvenWhenResetOccursBeforePickerReturn() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        var token: String? = null
        vm.prepareExport { id, name -> token = id; assertTrue(name.contains("workspace-v2")) }
        advanceUntilIdle()
        vm.reset()
        advanceUntilIdle()
        assertEquals(Workspace(), vm.workspace)
        val output = ByteArrayOutputStream()
        vm.writeExport(token) { output }
        advanceUntilIdle()
        assertEquals(original, WorkspaceJson.decode(output.toByteArray()))
        assertFalse(vm.exportInProgress)
    }

    @Test fun resetRechecksRevisionAndCancellationPreservesData() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        vm.requestReset(); vm.cancelReset()
        assertNull(vm.resetPreview)
        assertEquals(0, access.writes)
        vm.requestReset()
        val first = vm.resetPreview!!
        vm.saveCommit("habit", null, 1.0, "intervening")
        advanceUntilIdle()
        vm.confirmReset(first)
        advanceUntilIdle()
        assertTrue(vm.resetPreview!!.consentUpdated)
        assertEquals(1, access.writes)
        vm.confirmReset(vm.resetPreview!!)
        advanceUntilIdle()
        assertEquals(Workspace(), vm.workspace)
        val reopened = model(access)
        advanceUntilIdle()
        assertEquals(Workspace(), reopened.workspace)
    }

    @Test fun preservationFailureDoesNotWriteOrDiscardCandidateAndAllowsRetry() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        vm.overlay = Overlay.RepoForm("habit")
        vm.editRepoForm { it.copy(description = "retained") }
        val draft = vm.formState
        val preview = preview(vm)
        access.failPreservation = true
        vm.confirmRestore(preview)
        advanceUntilIdle()
        assertEquals(0, access.writes)
        assertSame(preview, vm.restoreState)
        assertEquals(original, vm.workspace)
        assertEquals(draft, vm.formState)
        assertTrue(vm.load is WorkspaceLoad.Ready)
        access.failPreservation = false
        vm.confirmRestore(preview)
        advanceUntilIdle()
        assertEquals("Restored", vm.workspace.name)
        assertNull(vm.formState)
    }

    @Test fun cancellationWhileReadingClosesStreamAndCannotPublishOverNewPreview() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        var closed = false
        var newer: String? = null
        val first = vm.requestRestore()!!
        vm.readRestore(first) {
            object : ByteArrayInputStream(WorkspaceJson.encode(original).toByteArray()) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    newer = vm.requestRestore()!!
                    return super.read(bytes, offset, length)
                }
                override fun close() { closed = true; super.close() }
            }
        }
        advanceUntilIdle()
        assertTrue(closed)
        assertEquals(RestoreState.Selecting(newer!!), vm.restoreState)
        vm.readRestore(newer!!) { ByteArrayInputStream(WorkspaceJson.encode(Workspace(name = "Newer")).toByteArray()) }
        advanceUntilIdle()
        assertEquals("Newer", (vm.restoreState as RestoreState.Preview).document.workspace.name)
        assertEquals(0, access.writes)
    }

    @Test fun calendarMaintenanceInvalidatesRestoreConsent() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(original.requestWeekStart(
            java.time.DayOfWeek.SUNDAY, clock.today())).toByteArray())
        val vm = model(access)
        advanceUntilIdle()
        val beforeBoundary = preview(vm)
        clock.instant = Instant.parse("2026-11-09T12:00:00Z")
        vm.refreshDate()
        advanceUntilIdle()
        assertEquals(1, access.writes)
        vm.confirmRestore(beforeBoundary)
        advanceUntilIdle()
        assertEquals(1, access.writes)
        assertTrue((vm.restoreState as RestoreState.Preview).consentUpdated)
    }

    @Test fun indeterminateRestoreCannotRetryUntilReconciliationAndVerifiedPreviousNeedsFreshConsent() = runTest(dispatcher) {
        val access = access(); val vm = model(access)
        advanceUntilIdle()
        val preview = preview(vm)
        access.writeActions += { access.failReads = true; throw IOException("write uncertain") }
        access.writeActions += { throw IOException("rollback failed") }
        vm.confirmRestore(preview)
        advanceUntilIdle()
        assertTrue(vm.transferNeedsReconciliation)
        assertTrue(vm.load is WorkspaceLoad.Recovery)
        val writes = access.writes
        vm.confirmRestore(preview)
        vm.requestReset(); vm.confirmReset(vm.resetPreview!!)
        advanceUntilIdle()
        assertEquals(writes, access.writes)
        access.failReads = false
        vm.cancelReset()
        vm.retryLoad()
        advanceUntilIdle()
        assertFalse(vm.transferNeedsReconciliation)
        assertEquals(original, vm.workspace)
        val fresh = preview(vm)
        vm.confirmRestore(fresh)
        advanceUntilIdle()
        assertEquals("Restored", vm.workspace.name)
    }

    @Test fun recreatedIndeterminateEqualReplacementClearsSavedDraftOnlyAfterVerifiedLoad() = runTest(dispatcher) {
        val access = access(); val saved = SavedStateHandle(); val vm = model(access, saved)
        advanceUntilIdle()
        vm.navigate(Destination.DETAIL, "habit")
        vm.overlay = Overlay.RepoForm("habit")
        vm.editRepoForm { it.copy(description = "old workspace draft") }
        val preview = preview(vm, original)
        access.writeActions += { bytes -> access.stored = bytes; access.failReads = true }
        access.writeActions += { throw IOException("rollback failed") }
        vm.confirmRestore(preview)
        advanceUntilIdle()
        assertNotNull(vm.formState)
        assertTrue(saved.get<List<String>>("transfer.replacements")!!.isNotEmpty())
        val reopened = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        advanceUntilIdle()
        assertTrue(reopened.load is WorkspaceLoad.Recovery)
        assertNotNull(reopened.formState)
        assertNull(reopened.restoreState)
        access.failReads = false
        reopened.retryLoad()
        advanceUntilIdle()
        assertEquals(original, reopened.workspace)
        assertNull(reopened.formState)
        assertNull(reopened.overlay)
        assertEquals(Destination.TODAY, reopened.destination)
        assertEquals(1, reopened.workspaceSession)
    }

    @Test fun failedAndCancelledExportsReleaseRequestAndNeverChangeWorkspace() = runTest(dispatcher) {
        val access = access(); val vm = model(access); val events = events(vm)
        advanceUntilIdle()
        var token: String? = null
        vm.prepareExport { id, _ -> token = id }
        advanceUntilIdle()
        vm.writeExport("stale") { error("must not open") }
        assertTrue(vm.exportInProgress)
        vm.writeExport(token, null)
        assertFalse(vm.exportInProgress)
        vm.prepareExport { id, _ -> token = id }
        advanceUntilIdle()
        vm.writeExport(token) { throw IOException("provider unavailable") }
        advanceUntilIdle()
        assertFalse(vm.exportInProgress)
        assertTrue(events.last().message.contains("incomplete"))
        assertEquals(original, vm.workspace)
        assertEquals(0, access.writes)
    }
}
