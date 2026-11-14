package com.githabit.prototype

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.FakeAccessForViewModel
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.domain.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionReturnTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val date = LocalDate.parse("2026-11-01")
    private val workspace = Workspace(onboarded = true).createHabit(HabitDraft("Repo", "", 10.0, "units"), date, "r")
        .addCommit("r", 10.0, date, "", "c")
    private val origin = Overlay.Day(date, "r", "c")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private fun TestScope.model(access: FakeAccessForViewModel, state: SavedStateHandle = SavedStateHandle()) =
        GitHabitViewModel(access, ioDispatcher = dispatcher, tickerScope = backgroundScope, savedStateHandle = state)
            .also { models.put("${models.keys().size}", it) }

    @Test fun originEncodingRemainsBoundedAndReadsLegacyOverlays() {
        val correcting = Overlay.LogForm("r", "c", origin)
        assertEquals(correcting, decodeOverlay(correcting.encode()))
        assertEquals(origin, decodeOverlay(origin.encode()))
        assertEquals(Overlay.Day(date, "r"), decodeOverlay(listOf("day", "$date", "r")))
        assertNull(decodeOverlay(listOf("log", "r", "", "$date", "r", "c")))
    }

    @Test fun inspectionFocusIsConsumedOnceWithoutChangingDateOrSavedDrafts() = runTest(dispatcher) {
        val vm = model(FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray()))
        advanceUntilIdle()
        vm.overlay = origin
        vm.consumeInspectionFocus(origin)
        assertEquals(origin.copy(focusCommitId = null), vm.overlay)
        vm.consumeInspectionFocus(origin)
        assertEquals(origin.copy(focusCommitId = null), vm.overlay)
        vm.correctFromDay(workspace.commits.single())
        assertEquals(Overlay.LogForm("r", "c", origin), vm.overlay)
    }

    @Test fun correctionDeletionAndCancellationReturnToExactInspection() = runTest(dispatcher) {
        val vm = model(FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray()))
        advanceUntilIdle()
        vm.overlay = origin
        vm.correctFromDay(workspace.commits.single())
        vm.saveCommit("r", "c", 5.0, "corrected", date)
        advanceUntilIdle()
        assertEquals(origin, vm.overlay)
        assertEquals(5.0, vm.analytics.daily("r", date).progress.total, 0.0)
        vm.correctFromDay(vm.workspace.commits.single())
        vm.requestDismissForm()
        assertEquals(origin, vm.overlay)
        vm.correctFromDay(vm.workspace.commits.single())
        vm.editLogForm { it.copy(note = "dirty") }
        vm.requestDismissForm()
        assertTrue(vm.discardConfirmation)
        vm.cancelDiscard()
        assertTrue(vm.overlay is Overlay.LogForm)
        vm.requestDismissForm()
        vm.confirmDiscard()
        assertEquals(origin, vm.overlay)
        vm.correctFromDay(vm.workspace.commits.single())
        vm.removeCommit("c")
        advanceUntilIdle()
        assertEquals(origin, vm.overlay)
        assertTrue(vm.workspace.commits.isEmpty())
    }

    @Test fun failedSaveAndProcessRestorationKeepOriginAndDraftReplacementClearsBoth() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val vm = model(access, saved)
        advanceUntilIdle()
        vm.overlay = origin
        vm.correctFromDay(workspace.commits.single())
        vm.editLogForm { it.copy(amount = "4") }
        access.writeActions += { throw java.io.IOException("test-only failure") }
        vm.saveCommit("r", "c", 4.0, "", date)
        advanceUntilIdle()
        assertEquals(Overlay.LogForm("r", "c", origin), vm.overlay)
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(vm.overlay, restored.overlay)
        assertEquals("4", (restored.formState as FormState.Log).values.amount)
        restored.requestDismissForm()
        restored.confirmDiscard()
        assertEquals(origin, restored.overlay)
        restored.reset()
        advanceUntilIdle()
        assertNull(restored.overlay)
        assertNull(restored.formState)
    }
}
