package com.githabit.prototype

import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.OperationMetrics
import com.githabit.prototype.fixtures.verifyDisposableDevice
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Separate dimensions and combined pressure. Process samples INCLUDE generated inputs. */
class CapacityMeasurementTest {
    private val day = LocalDate.parse("2026-11-04")
    private val habit = Habit("repo", "QA repo", unit = "units", createdOn = day, versions = listOf(GoalVersion(day, 2.5)))
    @Before fun guard() { verifyDisposableDevice() }
    private fun capacity(block: () -> Unit) {
        assertEquals(WorkspaceJsonFailure.CAPACITY, assertThrows(WorkspaceJsonException::class.java, block).kind)
    }
    private fun roundTrip(label: String, state: Workspace) {
        OperationMetrics().use { metrics -> metrics.measure(label) {
            val encoded = WorkspaceJson.encodeToBytes(state)
            assertEquals(state, WorkspaceJson.decode(encoded))
            assertEquals(state, RestoreDocument.read { encoded.inputStream() }.workspace)
        } }
    }
    @Test fun independentRepoAndIdentifierBoundaries() {
        for (count in listOf(999, 1000)) roundTrip("repos-$count", Workspace(habits = List(count) { habit.copy(id = "repo-$it") }))
        capacity { WorkspaceJson.encode(Workspace(habits = List(1001) { habit.copy(id = "repo-$it") })) }
        for (length in listOf(127, 128)) roundTrip("id-$length", Workspace(habits = listOf(habit.copy(id = "🌱".repeat(length / 2) + if (length % 2 == 1) "x" else ""))))
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit.copy(id = "x".repeat(129))))) }
    }
    @Test fun independentCommitBoundary() {
        for (count in listOf(99_999, 100_000)) {
            val commits = List(count) { Commit("qa-$it", habit.id, .125, day, Instant.EPOCH, "🌱\n") }
            roundTrip("commits-$count-concentrated", Workspace(habits = listOf(habit), commits = commits))
        }
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit), commits = List(100_001) { Commit("qa-$it", habit.id, .125, day) })) }
    }
    @Test fun independentConfigurationBoundary() {
        val start = day.minusDays(100_000)
        for (count in listOf(99_999, 100_000)) roundTrip("configurations-$count", Workspace(habits = listOf(habit.copy(createdOn = start,
            versions = List(count) { GoalVersion(start.plusDays(it.toLong()), 2.5) }))))
        capacity { WorkspaceJson.encode(Workspace(habits = listOf(habit.copy(createdOn = start,
            versions = List(100_001) { GoalVersion(start.plusDays(it.toLong()), 2.5) })))) }
    }
    private fun populatedBytes(bytes: Int) {
        val base = Workspace(habits = listOf(habit), commits = listOf(Commit("qa", habit.id, .125, day, Instant.EPOCH, "🌱\n")))
        val size = WorkspaceJson.encodedSize(base)
        // Storage accepts unchanged legacy padding. Exact populated valid JSON, not {}.
        val state = base.copy(habits = listOf(habit.copy(name = " ".repeat(bytes - size) + habit.name)))
        OperationMetrics().use { metrics -> metrics.measure("populated-bytes-$bytes") {
            if (bytes > WorkspaceLimits.MAX_BYTES) {
                var opened = false
                capacity { WorkspaceJson.export(state) { opened = true; java.io.ByteArrayOutputStream() } }
                assertFalse(opened)
            }
            else {
                val encoded = WorkspaceJson.encodeToBytes(state)
                assertEquals(bytes, encoded.size)
                assertEquals(state, WorkspaceJson.decode(encoded))
                assertEquals(state, RestoreDocument.read { encoded.inputStream() }.workspace)
            }
        } }
    }
    @Test fun populatedBytesBelowBoundary() = populatedBytes(WorkspaceLimits.MAX_BYTES - 1)
    @Test fun populatedBytesAtBoundary() = populatedBytes(WorkspaceLimits.MAX_BYTES)
    @Test fun populatedBytesAboveBoundary() = populatedBytes(WorkspaceLimits.MAX_BYTES + 1)
    @Test fun parserDepthBoundary() {
        for (depth in listOf(7, 8)) OperationMetrics().use { metrics -> metrics.measure("depth-$depth") { StrictJson("[".repeat(depth) + "0" + "]".repeat(depth)).validate() } }
        capacity { StrictJson("[".repeat(9) + "0" + "]".repeat(9)).validate() }
    }
    @Test fun combinedDistributedPressure() {
        val start = day.minusYears(5)
        val habits = List(1000) { i -> habit.copy(id = "qa-$i", createdOn = start,
            versions = List(100) { GoalVersion(start.plusDays(it * 10L), 2.5) }) }
        val commits = List(100_000) { i -> Commit("commit-$i", habits[i % 1000].id, .125, start.plusDays((i / 1000).toLong()), Instant.EPOCH, "QA 🌱\n") }
        roundTrip("combined-1000-repos-100k-commits-100k-configurations", Workspace(habits = habits, commits = commits))
    }
}
