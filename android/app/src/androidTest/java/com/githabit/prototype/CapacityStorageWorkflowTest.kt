package com.githabit.prototype

import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.OperationMetrics
import com.githabit.prototype.fixtures.verifyDisposableDevice
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Production file/stream workflows. Samples include instrumentation and expected state. */
class CapacityStorageWorkflowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.of(2026, 11, 4)
    private val habit = Habit("repo", "Repo", unit = "units", createdOn = day, versions = listOf(GoalVersion(day, 2.5)))
    @Before fun guard() { verifyDisposableDevice() }

    private fun workflow(state: Workspace, label: String) {
        val output = File(context.cacheDir, "capacity-workflow-export.json")
        try {
            OperationMetrics().use { metrics ->
                saveExportAndRestore(state, label, output, metrics)
                // The old Store/candidate belong to a returned non-inline frame, not reopen.
                // Keep independent fresh-load equality and the full original expected state.
                metrics.measure("$label-reopen") { assertEquals(WorkspaceLoadResult.Ready(state), WorkspaceStore(context).load()) }
            }
        } finally { output.delete() }
    }

    private fun saveExportAndRestore(state: Workspace, label: String, output: File, metrics: OperationMetrics) {
        val store = WorkspaceStore(context)
        store.load()
        metrics.measure("$label-save") { assertEquals(WriteOutcome.Committed, store.replaceVerified(state)) }
        metrics.measure("$label-startup") { assertEquals(WorkspaceLoadResult.Ready(state), store.reload()) }
        metrics.measure("$label-export") { WorkspaceJson.export(state) { output.outputStream() } }
        val candidate = metrics.measure("$label-restore-validation") { RestoreDocument.read { output.inputStream() } }
        assertEquals(state, candidate.workspace)
        metrics.measure("$label-restore-replacement") { assertEquals(WriteOutcome.Committed, store.replaceVerified(candidate.workspace)) }
    }

    private fun populated(size: Int) {
        val base = Workspace(onboarded = true, habits = listOf(habit),
            commits = listOf(Commit("entry", habit.id, .125, day, Instant.EPOCH, "🌱\n")))
        val state = base.copy(habits = listOf(habit.copy(name = " ".repeat(size - WorkspaceJson.encodedSize(base)) + habit.name)))
        assertEquals(size, WorkspaceJson.encodedSize(state))
        workflow(state, "populated-$size")
    }
    @Test fun populatedBelowBoundaryProductionWorkflows() = populated(WorkspaceLimits.MAX_BYTES - 1)
    @Test fun populatedAtBoundaryProductionWorkflows() = populated(WorkspaceLimits.MAX_BYTES)
    @Test fun atLimitPhysicalMatchingReadsReuseExactSnapshotAndRetainActualMismatch() {
        val dir = File(context.cacheDir, "capacity-matching-read").apply { check(mkdirs() || isDirectory) }
        try {
            val access = AtomicFileAccess(dir)
            val expected = ByteArray(WorkspaceLimits.MAX_BYTES) { (it * 31).toByte() }
            access.writeBytes(expected)
            assertSame(expected, access.readBytesMatching(listOf(expected)))
            val position = expected.size - 8193
            val changed = (expected[position].toInt() xor 255) and 255
            java.io.RandomAccessFile(File(dir, AtomicFileAccess.FILE_NAME), "rw").use {
                it.seek(position.toLong()); it.write(changed)
            }
            val actual = requireNotNull(access.readBytesMatching(listOf(expected)))
            assertNotSame(expected, actual)
            assertEquals(expected.size, actual.size)
            assertTrue(actual.indices.all { index -> actual[index].toInt() and 255 ==
                if (index == position) changed else expected[index].toInt() and 255 })
            assertSame(actual, access.readBytesMatching(listOf(expected, actual)))
        } finally { check(dir.deleteRecursively()) }
    }
    @Test fun combinedCountsProductionWorkflows() {
        val start = day.minusYears(5)
        val habits = List(1000) { i -> habit.copy(id = "qa-$i", createdOn = start,
            versions = List(100) { GoalVersion(start.plusDays(it * 10L), 2.5) }) }
        workflow(Workspace(onboarded = true, habits = habits, commits = List(100_000) { i ->
            Commit("commit-$i", habits[i % 1000].id, .125, start.plusDays((i / 1000).toLong()), Instant.EPOCH, "QA 🌱\n")
        }), "combined-counts")
    }
}
