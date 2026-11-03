package com.githabit.prototype.domain

import com.githabit.prototype.data.WorkspaceJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkspaceJsonTest {
    @Test fun emptyWorkspaceRoundTrips() {
        assertEquals(Workspace(), WorkspaceJson.decode(WorkspaceJson.encode(Workspace())))
    }

    @Test fun sampleWorkspaceRoundTripsWithoutLosingHistory() {
        val state = SampleWorkspace.create(Clock.fixed(Instant.parse("2026-11-02T16:00:00Z"), ZoneOffset.UTC))
            .copy(name = "A < B", bio = "A quote: \"keep going\" 🌱")
        assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        assertFalse(WorkspaceJson.encode(state).contains("password"))
    }

    @Test fun rejectsUnknownVersions() {
        val data = JSONObject(WorkspaceJson.encode(Workspace())).put("version", 900)
        assertThrows(IllegalArgumentException::class.java) { WorkspaceJson.decode(data.toString()) }
    }

    @Test fun rejectsDuplicateHabitIds() {
        val state = SampleWorkspace.create()
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceJson.decode(WorkspaceJson.encode(state.copy(habits = state.habits + state.habits.first())))
        }
    }

    @Test fun rejectsInvalidAmountsAndOrphanCommits() {
        val state = SampleWorkspace.create()
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceJson.decode(WorkspaceJson.encode(state.copy(commits = listOf(state.commits.first().copy(amount = -1.0)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceJson.decode(WorkspaceJson.encode(state.copy(commits = listOf(state.commits.first().copy(habitId = "missing")))))
        }
    }
}
