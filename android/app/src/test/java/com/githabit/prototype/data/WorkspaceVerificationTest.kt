package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkspaceVerificationTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val state = Workspace(name = "  Owner 🌱  ")
        .createHabit(HabitDraft("Read", "kept", 2.5, "pages"), day, "sample-edited")
        .addCommit("sample-edited", .125, day, "kept 🌱\n", "entry", Instant.EPOCH)

    @Test fun boundedComparisonAcceptsEscapesAndFormattingButRejectsEveryChangedValue() {
        val bytes = WorkspaceJson.encodeToBytes(state)
        assertTrue(WorkspaceJson.verifies(bytes, state))
        val escaped = bytes.toString(Charsets.UTF_8).replace("🌱", "\\uD83C\\uDF31")
        assertTrue(WorkspaceJson.verifies((" \n" + escaped + " \t").toByteArray(), state))
        for (other in listOf(state.copy(name = "Owner 🌱"), state.copy(bio = "changed"), state.copy(onboarded = !state.onboarded),
            state.copy(habits = state.habits.map { it.copy(unit = "units") }), state.copy(commits = emptyList()),
            state.copy(commits = state.commits.map { it.copy(amount = .126) }),
            state.copy(calendar = WeekCalendar(initialWeekStart = java.time.DayOfWeek.SUNDAY)))) {
            assertFalse(WorkspaceJson.verifies(bytes, other))
            assertFalse(WorkspaceJson.verifies(WorkspaceJson.encodeToBytes(other), state))
        }
    }

    @Test fun boundedComparisonStillRejectsMissingDuplicateUnknownVersionAndMalformedFields() {
        val encoded = WorkspaceJson.encode(state)
        for (invalid in listOf(encoded.replace("\"version\":2", "\"version\":1"),
            encoded.replace("\"version\":2", "\"version\":2,\"version\":2"),
            encoded.replace("\"version\":2", "\"version\":2,\"extra\":null"),
            encoded.replace("\"bio\":\"\",", ""), encoded.replace("Owner", "\\uD800"), encoded + "{}")) {
            assertFalse(WorkspaceJson.verifies(invalid.toByteArray(), state))
        }
    }
}
