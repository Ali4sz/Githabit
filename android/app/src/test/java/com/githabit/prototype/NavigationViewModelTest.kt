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
class NavigationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var count = 0
    private val workspace = Workspace(onboarded = true).createHabit(HabitDraft("Practice", "", 10.0, "minutes"),
        LocalDate.parse("2026-11-01"), "a")
    private fun TestScope.model(access: FakeAccessForViewModel, state: SavedStateHandle = SavedStateHandle()) =
        GitHabitViewModel(access, ioDispatcher = dispatcher, tickerScope = backgroundScope, savedStateHandle = state)
            .also { models.put("${count++}", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun newViewModelReloadsDiskAndRestoresRouteOverlayAndSession() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val first = model(access, saved)
        advanceUntilIdle()
        first.navigate(Destination.REPOS, mainTab = true)
        first.navigate(Destination.DETAIL, "a")
        first.navigate(Destination.SETTINGS)
        first.overlay = Overlay.LogForm("a")
        // A fresh handle/model simulates the values passed by platform restoration, not retained VM memory.
        val restored = model(access, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(workspace, restored.workspace)
        assertEquals(first.navigation, restored.navigation)
        assertEquals(Overlay.LogForm("a"), restored.overlay)
        restored.back()
        assertEquals(Destination.SETTINGS, restored.destination)
        restored.back()
        assertEquals(Destination.DETAIL, restored.destination)
        assertEquals("a", restored.selectedRepoId)
        assertEquals(0, access.writes)
    }

    @Test fun diskValidationDropsMissingSelectionAndCorrection() = runTest(dispatcher) {
        val nav = NavigationState().navigate(Destination.DETAIL, "deleted", false)
        val saved = SavedStateHandle(mapOf(NAVIGATION_STATE_KEY to nav.encode(),
            OVERLAY_STATE_KEY to Overlay.LogForm("a", "deleted-commit").encode()))
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val vm = model(access, saved)
        advanceUntilIdle()
        assertEquals(Destination.REPOS, vm.destination)
        assertNull(vm.selectedRepoId)
        assertNull(vm.overlay)
        assertEquals(0, access.writes)
    }

    @Test fun gestureCompletionCannotPopChangedRouteOrNavigateBeneathOverlay() = runTest(dispatcher) {
        val vm = model(FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray()))
        advanceUntilIdle()
        vm.navigate(Destination.REPOS, mainTab = true)
        val expected = vm.navigation
        vm.overlay = Overlay.Guide
        vm.completeBack(expected)
        assertSame(expected, vm.navigation)
        vm.overlay = null
        vm.navigate(Destination.SETTINGS)
        vm.completeBack(expected)
        assertEquals(Destination.SETTINGS, vm.destination)
        val settings = vm.navigation
        vm.completeBack(settings)
        vm.completeBack(settings)
        assertEquals(Destination.REPOS, vm.destination)
    }

    @Test fun failedReplacementKeepsStateButVerifiedReplacementInvalidatesSavedState() = runTest(dispatcher) {
        val access = FakeAccessForViewModel(WorkspaceJson.encode(workspace).toByteArray())
        val saved = SavedStateHandle()
        val vm = model(access, saved)
        advanceUntilIdle()
        vm.navigate(Destination.DETAIL, "a")
        vm.overlay = Overlay.RepoForm("a")
        val before = vm.navigation
        access.writeActions += { throw java.io.IOException("test-only failure") }
        vm.reset()
        advanceUntilIdle()
        assertEquals(before, vm.navigation)
        assertEquals(Overlay.RepoForm("a"), vm.overlay)
        assertEquals(0, vm.workspaceSession)
        vm.reset()
        advanceUntilIdle()
        assertEquals(NavigationState(), vm.navigation)
        assertNull(vm.overlay)
        assertEquals(1, saved.get<Int>(SESSION_STATE_KEY))
        assertEquals(NavigationState(), NavigationState.decode(saved.get<Any>(NAVIGATION_STATE_KEY)))
    }
}
