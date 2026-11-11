package com.githabit.prototype

import org.junit.Assert.*
import org.junit.Test

class NavigationStateTest {
    @Test fun tabsUseTodayRootButRetainSelection() {
        val detail = NavigationState().navigate(Destination.REPOS, null, true).navigate(Destination.DETAIL, "a", false)
        val insights = detail.navigate(Destination.INSIGHTS, null, true)
        val repos = insights.navigate(Destination.REPOS, null, true)
        assertEquals("a", repos.selectedRepoId)
        assertEquals(Route(Destination.REPOS), repos.route)
        assertEquals(listOf(Route()), repos.history)
        assertFalse(repos.back().canGoBack)
    }

    @Test fun secondaryRoutesAndNestedDetailPopOnceInOrder() {
        var state = NavigationState().navigate(Destination.REPOS, null, true)
            .navigate(Destination.DETAIL, "a", false).navigate(Destination.DETAIL, "b", false)
            .navigate(Destination.SETTINGS, null, false)
        assertEquals(Destination.REPOS, state.topLevel)
        state = state.back()
        assertEquals("b", state.selectedRepoId)
        state = state.back()
        assertEquals("a", state.selectedRepoId)
        assertEquals("a", state.route.repoId)
        state = state.back()
        assertEquals(Destination.REPOS, state.route.destination)
        assertEquals("a", state.selectedRepoId)
        assertEquals(Destination.TODAY, state.back().route.destination)
    }

    @Test fun boundedHistoryRoundTripsAndReselectionDoesNotGrowIt() {
        var state = NavigationState()
        repeat(100) { state = state.navigate(Destination.DETAIL, "repo-$it", false) }
        assertEquals(MAX_ROUTE_HISTORY, state.history.size)
        assertEquals(state, NavigationState.decode(state.encode()))
        assertSame(state, state.navigate(Destination.DETAIL, "repo-99", false))
    }

    @Test fun missingDetailAndHistoryReturnToSafeListWithoutChoosingARepo() {
        val state = NavigationState().navigate(Destination.REPOS, null, true)
            .navigate(Destination.DETAIL, "a", false).navigate(Destination.DETAIL, "b", false)
        val valid = state.validated(setOf("other"))
        assertEquals(Route(Destination.REPOS), valid.route)
        assertNull(valid.selectedRepoId)
        assertEquals(listOf(Route()), valid.history)
        assertEquals(NavigationState(), NavigationState.decode(null))
    }

    @Test fun malformedOrOversizedSnapshotsFailClosed() {
        listOf<Any>(listOf("99", "TODAY", "", "TODAY", ""), listOf("1", "TODAY", "", "UNKNOWN", ""),
            listOf("1", "TODAY", "", "DETAIL", ""), listOf("1", "TODAY", "", "TODAY", "unexpected"),
            listOf("1", "TODAY", "x".repeat(129), "TODAY", ""), listOf(1, "TODAY", "", "TODAY", ""),
            listOf("1", "DETAIL", "", "TODAY", ""),
            List(100) { "TODAY" }).forEach { assertEquals(NavigationState(), NavigationState.decode(it)) }
    }

    @Test fun historyTruncationPreservesOwningTabAndItsSavedSnapshot() {
        var state = NavigationState().navigate(Destination.INSIGHTS, null, true)
        repeat(30) {
            state = state.navigate(if (it % 2 == 0) Destination.SETTINGS else Destination.ACTIVITY, null, false)
        }
        assertEquals(MAX_ROUTE_HISTORY, state.history.size)
        assertEquals(Destination.INSIGHTS, state.topLevel)
        assertEquals(state, NavigationState.decode(state.encode()))
        assertEquals(Destination.INSIGHTS, NavigationState.decode(state.encode()).back().topLevel)
    }

    @Test fun branchingCacheEvictsUnreachableDetailsInsteadOfBackHistory() {
        var state = NavigationState()
        var keys = emptyList<String>()
        repeat(21) {
            state = state.navigate(Destination.DETAIL, "repo-$it", false)
            keys = state.retainedDetailKeys(keys)
        }
        repeat(14) { state = state.back(); keys = state.retainedDetailKeys(keys) }
        repeat(5) {
            state = state.navigate(Destination.DETAIL, "branch-$it", false)
            keys = state.retainedDetailKeys(keys)
        }
        assertEquals(MAX_ROUTE_HISTORY + 1, keys.size)
        (state.history + state.route).mapNotNull { it.repoId }.forEach { assertTrue("Missing reachable $it", "detail:$it" in keys) }
        assertTrue("detail:repo-0" in keys)
        assertFalse("detail:repo-20" in keys)
    }

    @Test fun overlaysRoundTripWithoutRecordsAndRejectMalformedValues() {
        listOf(Overlay.RepoForm(), Overlay.RepoForm("a"), Overlay.LogForm("a"), Overlay.LogForm("a", "c"),
            Overlay.Day(java.time.LocalDate.parse("2026-11-01")), Overlay.Guide).forEach {
            assertEquals(it, decodeOverlay(it.encode()))
        }
        assertNull(decodeOverlay(listOf("log", "", "")))
        assertNull(decodeOverlay(listOf("day", "invalid", "")))
        assertNull(decodeOverlay(listOf("guide", "extra")))
    }
}
