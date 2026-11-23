package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Can also run alone in a constrained host heap; not Android device certification. */
class WorkspaceMemoryRegressionTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val habit = Habit("repo", "Repo", unit = "units", createdOn = day, versions = listOf(GoalVersion(day, 2.5)))

    @Test fun populatedByteBoundaryRoundtripsAndRestores() {
        val base = Workspace(habits = listOf(habit), commits = listOf(Commit("entry", habit.id, .125, day, Instant.EPOCH, "🌱\n")))
        val size = WorkspaceJson.encodedSize(base)
        val state = base.copy(habits = listOf(habit.copy(name = " ".repeat(WorkspaceLimits.MAX_BYTES - size) + habit.name)))
        val bytes = WorkspaceJson.encodeToBytes(state)
        assertEquals(WorkspaceLimits.MAX_BYTES, bytes.size)
        assertEquals(state, WorkspaceJson.decode(bytes))
        assertEquals(state, RestoreDocument.read { bytes.inputStream() }.workspace)
    }

    @Test fun largeEscapedCompatibleStringDecodesWithoutGrowingCharacterBuilder() {
        val prefix = "{\"version\":2,\"onboarded\":false,\"theme\":\"SYSTEM\",\"name\":\""
        val suffix = "Owner\",\"bio\":\"\",\"habits\":[],\"commits\":[],\"calendar\":{\"initialWeekStart\":\"MONDAY\",\"transitions\":[],\"pending\":null}}"
        val count = (WorkspaceLimits.MAX_BYTES - prefix.length - suffix.length) / 6
        val bytes = ByteArray(prefix.length + count * 6 + suffix.length)
        prefix.toByteArray().copyInto(bytes)
        val escape = "\\u0020".toByteArray()
        repeat(count) { escape.copyInto(bytes, prefix.length + it * 6) }
        suffix.toByteArray().copyInto(bytes, prefix.length + count * 6)
        val decoded = WorkspaceJson.decode(bytes)
        assertEquals(" ".repeat(count) + "Owner", decoded.name)
        assertTrue(WorkspaceJson.verifies(bytes, decoded))
    }

    @Test fun combinedPressureRoundtripsAndRestores() {
        val start = day.minusYears(5)
        val habits = List(1000) { index -> habit.copy(id = "qa-$index", createdOn = start,
            versions = List(100) { GoalVersion(start.plusDays(it * 10L), 2.5) }) }
        val state = Workspace(habits = habits, commits = List(100_000) { index ->
            Commit("commit-$index", habits[index % 1000].id, .125, start.plusDays((index / 1000).toLong()), Instant.EPOCH, "QA 🌱\n")
        })
        val bytes = WorkspaceJson.encodeToBytes(state)
        assertEquals(state, WorkspaceJson.decode(bytes))
        assertEquals(state, RestoreDocument.read { bytes.inputStream() }.workspace)
    }
}
