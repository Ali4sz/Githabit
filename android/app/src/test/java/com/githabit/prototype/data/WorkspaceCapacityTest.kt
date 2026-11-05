package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCapacityTest {
    private val day = LocalDate.of(2026, 11, 2)
    private val habit = Habit(id = "repo", name = "Repo", unit = "units", createdOn = day, versions = listOf(GoalVersion(day, 1.0)))
    private val commit = Commit("entry", habit.id, 0.125, day, Instant.parse("2026-11-02T12:00:00Z"))

    private fun capacity(block: () -> Unit) {
        assertEquals(WorkspaceJsonFailure.CAPACITY, assertThrows(WorkspaceJsonException::class.java, block).kind)
    }

    @Test fun repoCountBelowAtAndAboveLimitUsesTheSameSaveAndDecodeContract() {
        for (count in listOf(999, 1000)) {
            val state = Workspace(habits = List(count) { habit.copy(id = "repo-$it") })
            assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        }
        capacity { WorkspaceJson.encode(Workspace(habits = List(1001) { habit.copy(id = "repo-$it") })) }
        val raw = JSONObject(WorkspaceJson.encode(Workspace(habits = listOf(habit))))
        val item = raw.getJSONArray("habits").getJSONObject(0)
        raw.put("habits", JSONArray().apply { repeat(1001) { put(item) } })
        capacity { WorkspaceJson.decode(raw.toString()) }
    }

    @Test fun commitCountBelowAtAndAboveLimitUsesTheSameSaveAndDecodeContract() {
        for (count in listOf(99_999, 100_000)) {
            val state = Workspace(habits = listOf(habit), commits = List(count) { commit.copy(id = "c$it") })
            assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        }
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit), commits = List(100_001) { commit.copy(id = "c$it") })) }
        val item = "{\"id\":\"c\",\"habitId\":\"repo\",\"amount\":1,\"date\":\"2026-11-02\",\"timestamp\":\"2026-11-02T12:00:00Z\",\"note\":\"\"}"
        val raw = WorkspaceJson.encode(Workspace(habits = listOf(habit)))
            .replace("\"commits\":[]", "\"commits\":[" + (0..100_000).joinToString(",") { item } + "]")
        capacity { WorkspaceJson.decode(raw) }
    }

    @Test fun totalConfigurationLimitIncludesBothHistoricalAndPendingCalendarChanges() {
        val calendar = WeekCalendar(DayOfWeek.MONDAY,
            listOf(WeekTransition(day.plusDays(7), DayOfWeek.SUNDAY)), WeekTransition(day.plusDays(13), DayOfWeek.MONDAY))
        for (count in listOf(99_997, 99_998)) {
            val state = Workspace(habits = listOf(habit.copy(versions = List(count) { GoalVersion(day.plusDays(it.toLong()), 1.0) })), calendar = calendar)
            assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        }
        val tooMany = Workspace(habits = listOf(habit.copy(versions = List(99_999) { GoalVersion(day.plusDays(it.toLong()), 1.0) })), calendar = calendar)
        capacity { WorkspaceJson.encode(tooMany) }
        val supported = tooMany.copy(calendar = WeekCalendar())
        val raw = JSONObject(WorkspaceJson.encode(supported))
        raw.put("calendar", JSONObject(WorkspaceJson.encode(Workspace(calendar = calendar))).getJSONObject("calendar"))
        capacity { WorkspaceJson.decode(raw.toString()) }
    }

    @Test fun idLengthBelowAtAndAboveLimitIsEnforcedForAllIdentifiers() {
        for (length in listOf(127, 128)) {
            val id = "x".repeat(length)
            val state = Workspace(habits = listOf(habit.copy(id = id)), commits = listOf(commit.copy(id = id, habitId = id)))
            assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        }
        val tooLong = "x".repeat(129)
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit.copy(id = tooLong)))) }
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit), commits = listOf(commit.copy(id = tooLong)))) }
        capacity { WorkspaceJson.decode(WorkspaceJson.encode(Workspace(habits = listOf(habit))).replace("\"id\":\"repo\"", "\"id\":\"$tooLong\"")) }
    }
}
