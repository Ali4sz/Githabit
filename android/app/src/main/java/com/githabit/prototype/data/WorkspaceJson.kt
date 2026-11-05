package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.time.Instant
import java.time.LocalDate
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, explicit serialization. Authentication credentials are not part of the model. */
object WorkspaceJson {
    /** Damaged UTF-8 is recoverable input, never permission to replace bytes with U+FFFD. */
    fun decode(bytes: ByteArray): Workspace = decode(Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString())

    fun encode(state: Workspace): String = JSONObject().apply {
        put("version", 1)
        put("signedIn", state.signedIn)
        put("onboarded", state.onboarded)
        put("darkTheme", state.darkTheme)
        put("name", state.name)
        put("bio", state.bio)
        put("habits", JSONArray(state.habits.map { habit -> JSONObject().apply {
            put("id", habit.id)
            put("name", habit.name)
            put("description", habit.description)
            put("unit", habit.unit)
            put("icon", habit.icon.name)
            put("color", habit.color.name)
            put("createdOn", habit.createdOn.toString())
            put("versions", JSONArray(habit.versions.map { version -> JSONObject().apply {
                put("date", version.date.toString())
                put("target", version.target)
                put("status", version.status.name)
            } }))
        } }))
        put("commits", JSONArray(state.commits.map { commit -> JSONObject().apply {
            put("id", commit.id)
            put("habitId", commit.habitId)
            put("amount", commit.amount)
            put("date", commit.date.toString())
            put("timestamp", commit.timestamp.toString())
            put("note", commit.note)
        } }))
    }.toString(2)

    fun decode(json: String): Workspace {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported workspace version." }
        val habits = root.getJSONArray("habits").objects().map { item ->
            Habit(
                id = item.getString("id"), name = item.getString("name"),
                description = item.getString("description"), unit = item.getString("unit"),
                icon = HabitIcon.valueOf(item.getString("icon")),
                color = HabitColor.valueOf(item.getString("color")),
                createdOn = LocalDate.parse(item.getString("createdOn")),
                versions = item.getJSONArray("versions").objects().map { version ->
                    GoalVersion(LocalDate.parse(version.getString("date")),
                        positive(version.getDouble("target")), RepoStatus.valueOf(version.getString("status")))
                },
            ).also { habit ->
                require(habit.id.isNotBlank() && habit.versions.isNotEmpty())
                HabitDraft(habit.name, habit.description, habit.versions.first().target, habit.unit).normalized()
                require(habit.versions.map { it.date }.distinct().size == habit.versions.size)
                require(habit.versions.minOf { it.date } == habit.createdOn)
            }
        }
        require(habits.map { it.id }.distinct().size == habits.size)
        val ids = habits.map { it.id }.toSet()
        val commits = root.getJSONArray("commits").objects().map { item ->
            Commit(item.getString("id"), item.getString("habitId"), positive(item.getDouble("amount")),
                LocalDate.parse(item.getString("date")), Instant.parse(item.getString("timestamp")), item.getString("note"))
                .also { require(it.habitId in ids && it.id.isNotBlank() && it.note.length <= 200) }
        }
        require(commits.map { it.id }.distinct().size == commits.size)
        val name = root.getString("name")
        val bio = root.getString("bio")
        require(name.trim().length in 1..40 && bio.length <= 160)
        return Workspace(root.getBoolean("signedIn"), root.getBoolean("onboarded"), root.getBoolean("darkTheme"),
            name, bio, habits, commits)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
