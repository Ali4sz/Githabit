package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkspaceV2Test {
    private val day = LocalDate.of(2026, 11, 2)
    private fun state(schedule: HabitSchedule = HabitSchedule.Daily) = Workspace(onboarded = true)
        .createHabit(HabitDraft("Repo", "", 0.125, "units", schedule = schedule), day, "repo")
        .addCommit("repo", 0.025, day, "note", "commit", Instant.parse("2026-11-02T12:00:00.123456789Z"))
    private fun json() = JSONObject(WorkspaceJson.encode(state()))
    private fun JSONObject.version() = getJSONArray("habits").getJSONObject(0).getJSONArray("versions").getJSONObject(0)
    private fun fails(root: JSONObject, kind: WorkspaceJsonFailure = WorkspaceJsonFailure.INVALID) {
        assertEquals(kind, assertThrows(WorkspaceJsonException::class.java) { WorkspaceJson.decode(root.toString()) }.kind)
    }

    @Test fun emptyDefaultsAndAllThemeChoicesRoundTripWithoutIdentity() {
        for (theme in ThemePreference.entries) {
            val state = Workspace(theme = theme)
            val encoded = WorkspaceJson.encode(state)
            assertEquals(state, WorkspaceJson.decode(encoded))
            assertEquals(2, WorkspaceJson.decodeDocument(encoded).sourceVersion)
            assertFalse(encoded.contains("signedIn")); assertFalse(encoded.contains("darkTheme"))
            assertEquals("", state.name); assertEquals("", state.bio)
            assertEquals(DayOfWeek.MONDAY, state.calendar.initialWeekStart)
        }
    }

    @Test fun everyScheduleAndCalendarConventionRoundTripsLosslessly() {
        val schedules = listOf(HabitSchedule.Daily, HabitSchedule.SelectedWeekdays(DayOfWeek.entries.toSet())) +
            DayOfWeek.entries.map { HabitSchedule.SelectedWeekdays(setOf(it)) } + (1..7).map { HabitSchedule.WeeklyQuota(it) }
        for (schedule in schedules) for (weekday in DayOfWeek.entries) {
            val state = state(schedule).copy(calendar = WeekCalendar(weekday))
            assertEquals(state, WorkspaceJson.decode(WorkspaceJson.encode(state)))
        }
    }

    @Test fun activeAndChainedBridgesAndElapsedPendingDatesAreNeverAdvancedOnDecode() {
        val calendar = WeekCalendar().request(DayOfWeek.SUNDAY, day).request(DayOfWeek.MONDAY, day.plusDays(7))
        assertEquals(1, calendar.transitions.size)
        assertNotNull(calendar.pending)
        val state = state(HabitSchedule.WeeklyQuota(3)).copy(calendar = calendar)
        val restored = WorkspaceJson.decode(WorkspaceJson.encode(state))
        assertEquals(state, restored)
        for (offset in -10L..50L) {
            val date = day.plusDays(offset)
            assertEquals(calendar.periodOn(date), restored.calendar.periodOn(date))
            assertEquals(calendar.status(date), restored.calendar.status(date))
        }
        val cancelled = state.copy(calendar = calendar.request(DayOfWeek.SUNDAY, day.plusDays(8)))
        assertNull(cancelled.calendar.pending)
        assertEquals(cancelled, WorkspaceJson.decode(WorkspaceJson.encode(cancelled)))
    }

    @Test fun requiredFieldsTypesAndEnumsAreStrictWithoutCoercion() {
        for (field in listOf("version", "onboarded", "theme", "name", "bio", "habits", "commits", "calendar")) {
            fails(json().apply { remove(field) })
            fails(json().put(field, JSONObject.NULL))
        }
        fails(json().put("onboarded", "true"))
        fails(json().put("theme", "BLUE"))
        fails(json().put("signedIn", true))
        fails(json().apply { version().put("target", "0.125") })
        fails(json().apply { version().put("status", "DELETED") })
        fails(json().apply { getJSONArray("habits").getJSONObject(0).put("icon", "INVALID") })
        fails(json().apply { getJSONArray("commits").getJSONObject(0).put("date", "2026-02-30") })
        fails(json().apply { getJSONObject("calendar").put("initialWeekStart", "FUNDAY") })
    }

    @Test fun schedulePayloadsRejectMissingExtraDuplicateAndOutOfRangeValues() {
        val invalid = listOf(
            "{}", "{\"kind\":\"NOPE\"}", "{\"kind\":\"DAILY\",\"days\":1}",
            "{\"kind\":\"SELECTED_WEEKDAYS\",\"weekdays\":[]}",
            "{\"kind\":\"SELECTED_WEEKDAYS\",\"weekdays\":[\"MONDAY\",\"MONDAY\"]}",
            "{\"kind\":\"SELECTED_WEEKDAYS\",\"weekdays\":[1]}",
            "{\"kind\":\"WEEKLY_QUOTA\",\"days\":0}", "{\"kind\":\"WEEKLY_QUOTA\",\"days\":8}",
            "{\"kind\":\"WEEKLY_QUOTA\",\"days\":1.5}")
        for (schedule in invalid) fails(json().apply { version().put("schedule", JSONObject(schedule)) })
        // Kind need not be the first field.
        val root = json().apply { version().put("schedule", JSONObject("{\"days\":3,\"kind\":\"WEEKLY_QUOTA\"}")) }
        assertEquals(HabitSchedule.WeeklyQuota(3), WorkspaceJson.decode(root.toString()).habits.single().versions.single().schedule)
    }

    @Test fun duplicateIdsOrDatesUnorderedVersionsAndOrphanCommitsAreRejected() {
        fails(json().apply { getJSONArray("habits").put(getJSONArray("habits").getJSONObject(0)) })
        fails(json().apply { getJSONArray("commits").put(getJSONArray("commits").getJSONObject(0)) })
        fails(json().apply { getJSONArray("commits").getJSONObject(0).put("habitId", "absent") })
        fails(json().apply { getJSONArray("habits").getJSONObject(0).getJSONArray("versions").put(version()) })
        fails(json().apply {
            val history = getJSONArray("habits").getJSONObject(0).getJSONArray("versions")
            history.put(JSONObject(version().toString()).put("date", "2026-11-04"))
            history.put(JSONObject(version().toString()).put("date", "2026-11-03"))
        })
        fails(json().apply { version().put("date", "2026-11-01") })
    }

    @Test fun invalidCalendarBoundariesOrderingAndPendingOverlapAreRejected() {
        val invalid = listOf(
            "{\"boundary\":\"2026-11-09\",\"weekStart\":\"MONDAY\"}", // no change
            "{\"boundary\":\"2026-11-10\",\"weekStart\":\"SUNDAY\"}", // wrong boundary
            "{\"boundary\":\"2026-11-09\",\"weekStart\":\"FUNDAY\"}")
        for (pending in invalid) fails(json().apply { getJSONObject("calendar").put("pending", JSONObject(pending)) })
        val transition = JSONObject("{\"boundary\":\"2026-11-09\",\"weekStart\":\"SUNDAY\"}")
        fails(json().apply { getJSONObject("calendar").put("transitions", JSONArray().put(transition))
            .put("pending", JSONObject("{\"boundary\":\"2026-11-08\",\"weekStart\":\"MONDAY\"}")) })
        fails(json().apply { getJSONObject("calendar").put("transitions", JSONArray().put(transition).put(transition)) })
    }

    @Test fun exactDecimalPrecisionIsPreservedAndNeverRoundedDuringMigration() {
        for (value in listOf("0", "-1", "0.0009", "0.1251", "1000000.001", "1e309", "1e-999999999")) {
            val raw = WorkspaceJson.encode(state()).replace("\"target\":0.125", "\"target\":$value")
            assertEquals(WorkspaceJsonFailure.INVALID, assertThrows(WorkspaceJsonException::class.java) { WorkspaceJson.decode(raw) }.kind)
        }
        val exact = WorkspaceJson.encode(state()).replace("\"target\":0.125", "\"target\":1.25000e-1")
        assertEquals(state(), WorkspaceJson.decode(exact))
        val legacy = legacyFixture("v1-padded-strings.json").toString(Charsets.UTF_8).replace("\"target\": 0.3", "\"target\": 0.3001")
        assertEquals(WorkspaceJsonFailure.LEGACY_INCOMPATIBLE,
            assertThrows(WorkspaceJsonException::class.java) { WorkspaceJson.decode(legacy) }.kind)
        assertThrows(WorkspaceJsonException::class.java) { WorkspaceJson.encode(state().copy(commits = listOf(state().commits.single().copy(amount = Double.NaN)))) }
    }

    @Test fun fixedLegacyFixturesMigrateDeterministicallyWithoutLosingHistory() {
        for (file in listOf("v1-empty-dark.json", "v1-empty-light.json", "v1-populated-light.json", "v1-mixed-signed-out-dark.json", "v1-padded-strings.json")) {
            val document = WorkspaceJson.decodeDocument(legacyFixture(file))
            assertEquals(1, document.sourceVersion)
            assertEquals(document.workspace, WorkspaceJson.decode(WorkspaceJson.encode(document.workspace)))
            assertEquals(WeekCalendar(), document.workspace.calendar)
            assertTrue(document.workspace.habits.flatMap { it.versions }.all { it.schedule == HabitSchedule.Daily })
        }
    }

    @Test fun profileAndStringLimitsPreserveLegacyWhitespaceWithoutNormalization() {
        val padded = state().copy(name = " " + "x".repeat(40) + " ", bio = "x".repeat(160),
            habits = state().habits.map { it.copy(name = "  " + "x".repeat(48) + "  ", description = " " + "x".repeat(160) + " ", unit = " " + "x".repeat(20) + " ") })
        assertEquals(padded, WorkspaceJson.decode(WorkspaceJson.encode(padded)))
        for (invalid in listOf(padded.copy(name = "x".repeat(41)), padded.copy(bio = "x".repeat(161))))
            assertThrows(WorkspaceJsonException::class.java) { WorkspaceJson.encode(invalid) }
    }

    @Test fun legacyPaddedUnitsAndUnchangedStringsRemainEditableWithoutRewritingHistory() {
        val state = WorkspaceJson.decode(legacyFixture("v1-padded-strings.json"))
        val habit = state.habits.single()
        val version = habit.versions.single()
        val draft = HabitDraft(habit.name, habit.description, version.target, habit.unit, habit.icon, habit.color, version.schedule)
        assertEquals(state, state.editHabit(habit.id, draft, day))
        val renamed = state.editHabit(habit.id, draft.copy(name = "Renamed"), day)
        assertEquals(habit.unit, renamed.habits.single().unit)
        assertEquals(habit.description, renamed.habits.single().description)
        assertEquals(habit.versions, renamed.habits.single().versions)
        assertEquals(state.commits, renamed.commits)
        assertEquals(renamed, WorkspaceJson.decode(WorkspaceJson.encode(renamed)))
    }

    @Test fun exportValidatesBeforeOpeningAndUsesRestorableUtf8WithoutIdentity() {
        var opened = false
        assertThrows(WorkspaceJsonException::class.java) {
            WorkspaceJson.export(state().copy(name = "x".repeat(41))) {
                opened = true
                ByteArrayOutputStream()
            }
        }
        assertFalse(opened)
        val expected = state(HabitSchedule.WeeklyQuota(7)).copy(bio = "🌱 العربية", calendar = WeekCalendar().request(DayOfWeek.SUNDAY, day))
        val output = ByteArrayOutputStream()
        WorkspaceJson.export(expected) { output }
        assertEquals(expected, WorkspaceJson.decode(output.toByteArray()))
        assertEquals(2, WorkspaceJson.decodeDocument(output.toByteArray()).sourceVersion)
    }
}
