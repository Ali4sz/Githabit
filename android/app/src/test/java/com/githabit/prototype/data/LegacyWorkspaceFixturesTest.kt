package com.githabit.prototype.data

import com.githabit.prototype.domain.HabitSchedule
import com.githabit.prototype.domain.ProgressAnalytics
import com.githabit.prototype.domain.RepoStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Fixed files, not output from the encoder under test. All content is test-only. */
class LegacyWorkspaceFixturesTest {
    @Test fun emptyFilesRetainExplicitLegacyPreferencesAndNoRecords() {
        val dark = WorkspaceJson.decode(legacyFixture("v1-empty-dark.json"))
        assertTrue(dark.darkTheme)
        assertFalse(dark.signedIn)
        assertFalse(dark.onboarded)
        assertEquals("Alex", dark.name)
        assertTrue(dark.habits.isEmpty())
        assertTrue(dark.commits.isEmpty())
        val light = WorkspaceJson.decode(legacyFixture("v1-empty-light.json"))
        assertFalse(light.darkTheme)
        assertTrue(light.onboarded)
        assertTrue(light.habits.isEmpty())
        assertTrue(light.commits.isEmpty())
    }

    @Test fun unsortedVersionsAndPausedDateCommitsRemainValid() {
        val state = WorkspaceJson.decode(legacyFixture("v1-populated-light.json"))
        val habit = state.habits.single()
        assertEquals(listOf("2024-03-02", "2024-02-28", "2024-03-01"), habit.versions.map { it.date.toString() })
        assertEquals(3, state.commits.size)
        assertEquals(0.125, state.commits.first().amount, 0.0)
        assertEquals(LocalDate.of(2024, 2, 29), state.commits.first().date)
        assertEquals(Instant.parse("2024-03-01T01:15:00Z"), state.commits.first().timestamp)
        val analytics = ProgressAnalytics(state, LocalDate.of(2024, 3, 3))
        assertEquals(20.0, analytics.daily(habit.id, LocalDate.of(2024, 3, 1)).progress.total, 0.0)
        assertEquals(RepoStatus.PAUSED, habit.versionOn(LocalDate.of(2024, 3, 1))!!.status)
        assertEquals(30.0, habit.versionOn(LocalDate.of(2024, 3, 2))!!.target, 0.0)
        assertTrue(habit.versions.all { it.schedule == HabitSchedule.Daily })
    }

    @Test fun signedOutMixedOriginHistoryIsNotDisposable() {
        val state = WorkspaceJson.decode(legacyFixture("v1-mixed-signed-out-dark.json"))
        assertFalse(state.signedIn)
        assertEquals("Alex", state.name)
        assertEquals("Building a better everyday.", state.bio)
        assertEquals(listOf("sample-0", "personal-paused", "personal-archived"), state.habits.map { it.id })
        assertEquals("Edited legacy repo", state.habits.first().name)
        assertEquals(listOf("sample-0-2025-12-29", "personal-on-legacy"), state.commits.map { it.id })
        assertEquals(7.125, state.commits.last().amount, 0.0)
        assertEquals(RepoStatus.ARCHIVED, state.habits.last().versions.single().status)
    }

    @Test fun stringsAndDateTimestampDisagreementsArePreservedVerbatim() {
        val state = WorkspaceJson.decode(legacyFixture("v1-padded-strings.json"))
        assertEquals("  Legacy name  ", state.name)
        assertEquals("  Preserve surrounding whitespace  ", state.bio)
        assertEquals("  Legacy repo  ", state.habits.single().name)
        assertEquals(" pages ", state.habits.single().unit)
        assertEquals(" padded-id ", state.habits.single().id)
        assertEquals("  Preserve legacy dates and note verbatim  ", state.commits.first().note)
        assertTrue(state.commits.first().date < state.habits.single().createdOn)
        assertEquals(LocalDate.of(2050, 1, 1), state.commits.last().date)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), state.commits.last().timestamp)
    }

    @Test fun fixedNegativeFilesAreRejectedWithoutChangingTheirBytes() {
        for (name in listOf("corrupt.json", "unsupported.json")) {
            val bytes = legacyFixture(name)
            val before = bytes.copyOf()
            assertThrows(Exception::class.java) { WorkspaceJson.decode(bytes) }
            assertArrayEquals(before, bytes)
        }
    }
}

internal fun legacyFixture(name: String): ByteArray =
    requireNotNull(LegacyWorkspaceFixturesTest::class.java.getResourceAsStream("/fixtures/$name")).use { it.readBytes() }
