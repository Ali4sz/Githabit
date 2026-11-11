package com.githabit.prototype

import java.time.LocalDate

internal const val MAX_ROUTE_HISTORY = 20
internal const val NAVIGATION_STATE_KEY = "navigation.v1"
internal const val OVERLAY_STATE_KEY = "overlay.v1"
internal const val SESSION_STATE_KEY = "workspace.session"

/** Only small UI identities are saved. Workspace records are always reloaded from disk. */
internal data class Route(val destination: Destination = Destination.TODAY, val repoId: String? = null)

internal data class NavigationState(
    val route: Route = Route(),
    val selectedRepoId: String? = null,
    val history: List<Route> = emptyList(),
    val owningTab: Destination = Destination.TODAY,
) {
    val canGoBack get() = route.destination != Destination.TODAY
    val topLevel: Destination get() = sequenceOf(route).plus(history.asReversed().asSequence())
        .map { if (it.destination == Destination.DETAIL) Destination.REPOS else it.destination }
        .firstOrNull { it in mainDestinations } ?: owningTab

    fun navigate(next: Destination, repoId: String?, mainTab: Boolean): NavigationState {
        val target = Route(next, repoId.takeIf { next == Destination.DETAIL })
        if (target == route) return this
        return copy(route = target,
            selectedRepoId = repoId.takeIf { next == Destination.DETAIL } ?: selectedRepoId,
            owningTab = when {
                next in mainDestinations -> next
                next == Destination.DETAIL -> Destination.REPOS
                else -> topLevel
            },
            history = if (mainTab) {
                if (next == Destination.TODAY) emptyList() else listOf(Route())
            } else (history + route).takeLast(MAX_ROUTE_HISTORY))
    }

    fun back(): NavigationState {
        val previous = history.lastOrNull() ?: Route()
        return copy(route = previous, selectedRepoId = previous.repoId ?: selectedRepoId, history = history.dropLast(1))
    }

    fun validated(repoIds: Set<String>): NavigationState {
        val validHistory = history.filter { it.destination != Destination.DETAIL || it.repoId in repoIds }
        val validRoute = if (route.destination == Destination.DETAIL && route.repoId !in repoIds) Route(Destination.REPOS) else route
        return copy(route = validRoute, selectedRepoId = selectedRepoId?.takeIf { it in repoIds },
            history = validHistory.dropLastWhile { it == validRoute })
    }

    /** Never evict a detail that can still be reached by Back, even after branching. */
    fun retainedDetailKeys(previous: List<String>): List<String> {
        val current = selectedRepoId?.let { "detail:$it" }
        val next = if (current == null) previous else (previous - current) + current
        val reachable = (history + route).mapNotNullTo(mutableSetOf()) { it.repoId?.let { id -> "detail:$id" } }
        current?.let(reachable::add)
        var remove = (next.size - (MAX_ROUTE_HISTORY + 1)).coerceAtLeast(0)
        return next.filter { key ->
            if (remove > 0 && key !in reachable) { remove--; false } else true
        }
    }

    fun encode(): ArrayList<String> = arrayListOf("1", owningTab.name, selectedRepoId.orEmpty(), route.destination.name, route.repoId.orEmpty()).apply {
        history.forEach { add(it.destination.name); add(it.repoId.orEmpty()) }
    }

    companion object {
        val mainDestinations = setOf(Destination.TODAY, Destination.REPOS, Destination.INSIGHTS)
        fun decode(value: Any?): NavigationState {
            val values = (value as? List<*>) ?: return NavigationState()
            if (values.size !in 5..(5 + MAX_ROUTE_HISTORY * 2) || values.size % 2 != 1 ||
                values.any { it !is String || it.length > 128 } || values[0] != "1") return NavigationState()
            return try {
                fun route(index: Int): Route {
                    val destination = Destination.valueOf(values[index] as String)
                    val id = (values[index + 1] as String).takeIf { it.isNotEmpty() }
                    require((destination == Destination.DETAIL) == (id != null))
                    return Route(destination, id)
                }
                val owner = Destination.valueOf(values[1] as String)
                require(owner in mainDestinations)
                val current = route(3)
                NavigationState(current, current.repoId ?: (values[2] as String).takeIf { it.isNotEmpty() },
                    (5 until values.size step 2).map(::route), owner)
            } catch (_: IllegalArgumentException) { NavigationState() }
        }
    }
}

internal fun Overlay.encode(): ArrayList<String> = when (this) {
    is Overlay.RepoForm -> arrayListOf("repo", id.orEmpty())
    is Overlay.LogForm -> arrayListOf("log", habitId, commitId.orEmpty())
    is Overlay.Day -> arrayListOf("day", date.toString(), habitId.orEmpty())
    Overlay.Guide -> arrayListOf("guide")
}

internal fun decodeOverlay(value: Any?): Overlay? {
    val values = value as? List<*> ?: return null
    if (values.size !in 1..3 || values.any { it !is String || it.length > 128 }) return null
    fun id(index: Int) = (values[index] as String).takeIf { it.isNotEmpty() }
    return try {
        when {
            values[0] == "repo" && values.size == 2 -> Overlay.RepoForm(id(1))
            values[0] == "log" && values.size == 3 -> Overlay.LogForm(requireNotNull(id(1)), id(2))
            values[0] == "day" && values.size == 3 -> Overlay.Day(LocalDate.parse(values[1] as String), id(2))
            values[0] == "guide" && values.size == 1 -> Overlay.Guide
            else -> null
        }
    } catch (_: Exception) { null }
}
