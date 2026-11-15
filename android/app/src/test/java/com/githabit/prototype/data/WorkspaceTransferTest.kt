package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkspaceTransferTest {
    private val monday = LocalDate.parse("2026-11-02")
    private fun read(bytes: ByteArray) = RestoreDocument.read { ByteArrayInputStream(bytes) }

    @Test fun exportedCalendarHistoryBridgePendingAndFutureRecordsRestoreLosslessly() {
        val state = Workspace(name = "Kept profile", bio = "Kept note", theme = ThemePreference.LIGHT)
            .createHabit(HabitDraft("Practice", "", 2.5, "minutes", schedule = HabitSchedule.WeeklyQuota(3)), monday, "sample-edited")
            .addCommit("sample-edited", 2.5, monday, "kept", "first", Instant.parse("2026-11-02T12:00:00Z"))
            .addCommit("sample-edited", 1.25, monday.plusDays(22), "future", "future", Instant.parse("2026-11-02T13:00:00Z"))
            .requestWeekStart(DayOfWeek.SUNDAY, monday.plusDays(1))
            .copy(onboarded = true)
        val bridge = state.copy(calendar = state.calendar.advancedTo(monday.plusDays(8)))
            .requestWeekStart(DayOfWeek.MONDAY, monday.plusDays(8))
        for (snapshot in listOf(Workspace(), state, bridge)) {
            val output = java.io.ByteArrayOutputStream()
            WorkspaceJson.export(snapshot) { output }
            val document = read(output.toByteArray())
            assertEquals(snapshot, document.workspace)
            assertEquals(2, document.sourceVersion)
            assertEquals(snapshot.habits.size, document.habitCount)
            assertEquals(snapshot.commits.size, document.commitCount)
            for (date in (0..30).map { monday.plusDays(it.toLong()) }) {
                assertEquals(snapshot.calendar.periodOn(date), document.workspace.calendar.periodOn(date))
                if (snapshot.habits.isNotEmpty()) assertEquals(
                    ProgressAnalytics(snapshot, monday.plusDays(8)).weekly("sample-edited", date),
                    ProgressAnalytics(document.workspace, monday.plusDays(8)).weekly("sample-edited", date))
            }
        }
    }

    @Test fun legacyMixedDocumentUsesStartupMigrationAndPreservesAllValues() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/v1-mixed-signed-out-dark.json")!!.use { it.readBytes() }
        val document = read(bytes)
        assertEquals(1, document.sourceVersion)
        assertEquals(WorkspaceJson.decodeDocument(bytes).workspace, document.workspace)
        assertEquals(document.workspace, WorkspaceJson.decode(WorkspaceJson.encode(document.workspace)))
        assertTrue(document.workspace.commits.isNotEmpty())
    }

    @Test fun malformedUnsupportedOversizedAndInterruptedInputsAreRejectedAndClosed() {
        for (bytes in listOf("{".toByteArray(), "{\"version\":999}".toByteArray(), byteArrayOf(0xc3.toByte()))) {
            assertThrows(WorkspaceJsonException::class.java) { read(bytes) }
        }
        var closed = false
        val oversized = object : java.io.InputStream() {
            var remaining = WorkspaceLimits.MAX_BYTES + 1
            override fun read() = if (remaining-- > 0) 32 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length)
                bytes.fill(32, offset, offset + count); remaining -= count
                return count
            }
            override fun close() { closed = true }
        }
        assertEquals(WorkspaceJsonFailure.CAPACITY,
            assertThrows(WorkspaceJsonException::class.java) { RestoreDocument.read { oversized } }.kind)
        assertTrue(closed)
        closed = false
        assertThrows(IOException::class.java) {
            RestoreDocument.read { object : java.io.InputStream() {
                override fun read(): Int = throw IOException("interrupted")
                override fun close() { closed = true }
            } }
        }
        assertTrue(closed)
    }

    @Test fun fingerprintIdentifiesValidatedSnapshotNotProviderWhitespace() {
        val encoded = WorkspaceJson.encode(Workspace())
        assertEquals(read(encoded.toByteArray()).fingerprint, read("  $encoded\n".toByteArray()).fingerprint)
        assertNotEquals(read(encoded.toByteArray()).fingerprint,
            read(WorkspaceJson.encode(Workspace(name = "Changed")).toByteArray()).fingerprint)
    }
}
