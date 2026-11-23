package com.githabit.prototype.data

import com.githabit.prototype.domain.*
import java.math.BigDecimal
import java.io.OutputStream
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Source format is retained so an unchanged v1 file cannot verify a v2 write. */
data class DecodedWorkspace(val workspace: Workspace, val sourceVersion: Int)

/** Strict, bounded codec shared by startup, save and document transfer. No identity is emitted. */
object WorkspaceJson {
    const val VERSION = 2

    fun decode(bytes: ByteArray): Workspace = decodeDocument(bytes).workspace
    fun decode(json: String): Workspace = decodeDocument(json).workspace
    fun decodeDocument(bytes: ByteArray): DecodedWorkspace = decodeText(Utf8JsonText(bytes))

    /** Compare every decoded value, discarding verified records instead of a second graph. */
    internal fun verifies(bytes: ByteArray, expected: Workspace): Boolean = try {
        Decoder(Utf8JsonText(bytes), VERSION, expected).read() == expected
    } catch (_: Exception) { false }

    /** Validate and bound the entire snapshot before opening a truncating provider destination. */
    fun export(state: Workspace, openOutput: () -> OutputStream) {
        val size = encodedSize(state)
        openOutput().use { output ->
            check(emit(state, BoundedJsonWriter(output = output)) == size.toLong())
        }
    }

    fun decodeDocument(json: String): DecodedWorkspace {
        return decodeText(json)
    }

    private fun decodeText(json: CharSequence): DecodedWorkspace {
        // Preflight grammar/depth before constructing domain records. A second streaming
        // pass allows arbitrary root field order without retaining a generic JSON tree.
        val probe = StrictJson(json)
        var version: Int? = null
        probe.readObject { key ->
            if (key == "version") version = integer(probe.readNumber()) else probe.skipValue()
        }
        probe.finish()
        val source = required(version, "version")
        if (source != 1 && source != VERSION) throw WorkspaceJsonException(
            WorkspaceJsonFailure.UNSUPPORTED_VERSION, "Unsupported workspace version: $source.")
        return try {
            DecodedWorkspace(Decoder(json, source).read(), source)
        } catch (error: WorkspaceJsonException) {
            if (source == 1 && error.kind == WorkspaceJsonFailure.INVALID) throw WorkspaceJsonException(
                WorkspaceJsonFailure.LEGACY_INCOMPATIBLE,
                "Legacy workspace is incompatible: ${error.message} Its original must be retained.", error)
            throw error
        } catch (error: IllegalArgumentException) {
            throw WorkspaceJsonException(if (source == 1) WorkspaceJsonFailure.LEGACY_INCOMPATIBLE
                else WorkspaceJsonFailure.INVALID, "Invalid workspace values.", error)
        } catch (error: java.time.DateTimeException) {
            throw WorkspaceJsonException(if (source == 1) WorkspaceJsonFailure.LEGACY_INCOMPATIBLE
                else WorkspaceJsonFailure.INVALID, "Invalid workspace date or timestamp.", error)
        }
    }

    fun encode(state: Workspace): String {
        return encodeToBytes(state).toString(Charsets.UTF_8)
    }

    /** Validates the complete escaped output before any large allocation or destination opening. */
    internal fun encodedSize(state: Workspace): Int {
        WorkspaceValidation.validate(state)
        return emit(state, BoundedJsonWriter(output = null)).toInt()
    }

    internal fun encodeToBytes(state: Workspace): ByteArray = encodeBytes(state, null)

    internal fun encodeAndDigest(state: Workspace): Pair<ByteArray, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = encodeBytes(state, digest)
        return bytes to digest.digest().hexDigest()
    }

    /** Fingerprint-only work never retains a serialized document. */
    internal fun fingerprint(state: Workspace): String {
        encodedSize(state)
        val digest = MessageDigest.getInstance("SHA-256")
        emit(state, BoundedJsonWriter(output = object : OutputStream() {
            override fun write(value: Int) { digest.update(value.toByte()) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { digest.update(bytes, offset, length) }
        }))
        return digest.digest().hexDigest()
    }

    private fun encodeBytes(state: Workspace, digest: MessageDigest?): ByteArray {
        val bytes = ByteArray(encodedSize(state))
        var position = 0
        val output = object : OutputStream() {
            override fun write(value: Int) { bytes[position++] = value.toByte(); digest?.update(value.toByte()) }
            override fun write(source: ByteArray, offset: Int, length: Int) {
                source.copyInto(bytes, position, offset, offset + length)
                position += length
                digest?.update(source, offset, length)
            }
        }
        check(emit(state, BoundedJsonWriter(output = output)) == bytes.size.toLong() && position == bytes.size)
        return bytes
    }

    private fun emit(state: Workspace, writer: BoundedJsonWriter): Long {
        writer.apply {
            obj {
                field("version", VERSION)
                field("onboarded", state.onboarded)
                field("theme", state.theme.name)
                field("name", state.name)
                field("bio", state.bio)
                arrayField("habits", state.habits) { habit -> obj {
                    field("id", habit.id); field("name", habit.name)
                    field("description", habit.description); field("unit", habit.unit)
                    field("icon", habit.icon.name); field("color", habit.color.name)
                    field("createdOn", habit.createdOn.toString())
                    arrayField("versions", habit.versions) { version -> obj {
                        field("date", version.date.toString()); field("target", version.target)
                        field("status", version.status.name)
                        objectField("schedule") {
                            when (val schedule = version.schedule) {
                                HabitSchedule.Daily -> field("kind", "DAILY")
                                is HabitSchedule.SelectedWeekdays -> {
                                    field("kind", "SELECTED_WEEKDAYS")
                                    arrayField("weekdays", schedule.days.sortedBy { it.value }) { string(it.name) }
                                }
                                is HabitSchedule.WeeklyQuota -> { field("kind", "WEEKLY_QUOTA"); field("days", schedule.daysPerWeek) }
                            }
                        }
                    } }
                } }
                arrayField("commits", state.commits) { commit -> obj {
                    field("id", commit.id); field("habitId", commit.habitId); field("amount", commit.amount)
                    field("date", commit.date.toString()); field("timestamp", commit.timestamp.toString())
                    field("note", commit.note)
                } }
                objectField("calendar") {
                    field("initialWeekStart", state.calendar.initialWeekStart.name)
                    arrayField("transitions", state.calendar.transitions) { transition -> obj { transition(transition) } }
                    val pending = state.calendar.pending
                    if (pending == null) nullField("pending") else objectField("pending") { transition(pending) }
                }
            }
        }
        return writer.finish()
    }

    private fun BoundedJsonWriter.transition(change: WeekTransition) {
        field("boundary", change.boundary.toString()); field("weekStart", change.weekStart.name)
    }

    private class Decoder(json: CharSequence, private val source: Int, private val expected: Workspace? = null) {
        private val reader = StrictJson(json)
        private var configurations = 0

        fun read(): Workspace {
            var onboarded: Boolean? = null
            var theme: ThemePreference? = null
            var name: String? = null
            var bio: String? = null
            var habits: List<Habit>? = null
            var commits: List<Commit>? = null
            var calendar: WeekCalendar? = null
            val fields = if (source == 1) setOf("version", "signedIn", "onboarded", "darkTheme", "name", "bio", "habits", "commits")
                else setOf("version", "onboarded", "theme", "name", "bio", "habits", "commits", "calendar")
            objectFields(fields) { key -> when (key) {
                "version" -> checkValue(integer(reader.readNumber()) == source, "Inconsistent version.")
                "signedIn" -> reader.readBoolean() // Validate type, deliberately ignore legacy identity.
                "onboarded" -> onboarded = reader.readBoolean()
                "darkTheme" -> theme = if (reader.readBoolean()) ThemePreference.DARK else ThemePreference.LIGHT
                "theme" -> theme = enumValue<ThemePreference>()
                "name" -> name = text(expected?.name)
                "bio" -> bio = text(expected?.bio)
                "habits" -> habits = comparedArray(WorkspaceLimits.MAX_HABITS, expected?.habits) { habit(it) }
                "commits" -> commits = comparedArray(WorkspaceLimits.MAX_COMMITS, expected?.commits) { commit() }
                "calendar" -> calendar = calendar()
            } }
            reader.finish()
            val repos = required(habits, "habits")
            val activity = required(commits, "commits")
            val state = Workspace(
                onboarded = required(onboarded, "onboarded") || (source == 1 && (repos.isNotEmpty() || activity.isNotEmpty())),
                theme = required(theme, "theme"), name = required(name, "name"), bio = required(bio, "bio"),
                habits = repos, commits = activity, calendar = if (source == 1) WeekCalendar() else required(calendar, "calendar"),
            )
            // Legacy names were mandatory. Their content is preserved verbatim, never defaulted.
            if (source == 1) checkValue(state.name.trim().isNotEmpty(), "Legacy display name is missing.")
            WorkspaceValidation.validate(state)
            return state
        }

        private fun habit(expected: Habit?): Habit {
            var id: String? = null; var name: String? = null; var description: String? = null; var unit: String? = null
            var icon: HabitIcon? = null; var color: HabitColor? = null; var createdOn: LocalDate? = null
            var versions: List<GoalVersion>? = null
            objectFields(setOf("id", "name", "description", "unit", "icon", "color", "createdOn", "versions")) { key -> when (key) {
                "id" -> id = text(expected?.id); "name" -> name = text(expected?.name)
                "description" -> description = text(expected?.description); "unit" -> unit = text(expected?.unit)
                "icon" -> icon = enumValue<HabitIcon>(); "color" -> color = enumValue<HabitColor>()
                "createdOn" -> createdOn = date()
                "versions" -> versions = comparedArray(WorkspaceLimits.MAX_CONFIGURATIONS, expected?.versions) { configuration(); version() }
            } }
            val history = required(versions, "versions")
            return Habit(required(id, "id"), required(name, "name"), required(description, "description"), required(unit, "unit"),
                required(icon, "icon"), required(color, "color"), required(createdOn, "createdOn"),
                if (source == 1) history.sortedBy { it.date } else history)
        }

        private fun version(): GoalVersion {
            var date: LocalDate? = null; var target: Double? = null; var status: RepoStatus? = null
            var schedule: HabitSchedule? = null
            objectFields(if (source == 1) setOf("date", "target", "status") else setOf("date", "target", "status", "schedule")) { key -> when (key) {
                "date" -> date = date(); "target" -> target = amount(); "status" -> status = enumValue<RepoStatus>()
                "schedule" -> schedule = schedule()
            } }
            return GoalVersion(required(date, "date"), required(target, "target"), required(status, "status"),
                if (source == 1) HabitSchedule.Daily else required(schedule, "schedule"))
        }

        private fun schedule(): HabitSchedule {
            // The payload type depends on kind, but field order is insignificant. A
            // separate 'weekdays' field keeps dispatch unambiguous while streaming.
            var kind: String? = null; var days: Int? = null; var weekdays: List<DayOfWeek>? = null
            val seen = mutableSetOf<String>()
            reader.readObject { key ->
                seen += key
                when (key) {
                    "kind" -> kind = reader.readString()
                    "days" -> days = integer(reader.readNumber())
                    "weekdays" -> weekdays = array(7) { enumValue<DayOfWeek>() }
                    else -> invalid("Unknown schedule field.")
                }
            }
            return when (required(kind, "kind")) {
                "DAILY" -> { checkValue(seen == setOf("kind"), "Daily schedule has extra fields."); HabitSchedule.Daily }
                "SELECTED_WEEKDAYS" -> {
                    checkValue(seen == setOf("kind", "weekdays"), "Weekday schedule fields are invalid.")
                    val selected = required(weekdays, "weekdays")
                    checkValue(selected.isNotEmpty() && selected.distinct().size == selected.size, "Weekdays must be nonempty and distinct.")
                    HabitSchedule.SelectedWeekdays(selected.toSet())
                }
                "WEEKLY_QUOTA" -> {
                    checkValue(seen == setOf("kind", "days"), "Weekly quota fields are invalid.")
                    HabitSchedule.WeeklyQuota(required(days, "days"))
                }
                else -> invalid("Unknown schedule kind.")
            }
        }

        private fun commit(): Commit {
            var id: String? = null; var habitId: String? = null; var amount: Double? = null
            var date: LocalDate? = null; var timestamp: Instant? = null; var note: String? = null
            objectFields(setOf("id", "habitId", "amount", "date", "timestamp", "note")) { key -> when (key) {
                "id" -> id = reader.readString(); "habitId" -> habitId = reader.readString(); "amount" -> amount = amount()
                "date" -> date = date(); "timestamp" -> timestamp = Instant.parse(reader.readString()); "note" -> note = reader.readString()
            } }
            return Commit(required(id, "id"), required(habitId, "habitId"), required(amount, "amount"),
                required(date, "date"), required(timestamp, "timestamp"), required(note, "note"))
        }

        private fun calendar(): WeekCalendar {
            var initial: DayOfWeek? = null; var transitions: List<WeekTransition>? = null; var pending: WeekTransition? = null
            objectFields(setOf("initialWeekStart", "transitions", "pending")) { key -> when (key) {
                "initialWeekStart" -> initial = enumValue<DayOfWeek>()
                "transitions" -> transitions = comparedArray(WorkspaceLimits.MAX_CONFIGURATIONS, expected?.calendar?.transitions) { configuration(); transition() }
                "pending" -> if (!reader.consumeNull()) { configuration(); pending = transition() }
            } }
            // Do not advance an elapsed pending request using today's device date.
            return WeekCalendar(required(initial, "initialWeekStart"), required(transitions, "transitions"), pending)
        }

        private fun transition(): WeekTransition {
            var boundary: LocalDate? = null; var weekday: DayOfWeek? = null
            objectFields(setOf("boundary", "weekStart")) { key -> when (key) {
                "boundary" -> boundary = date(); "weekStart" -> weekday = enumValue<DayOfWeek>()
            } }
            return WeekTransition(required(boundary, "boundary"), required(weekday, "weekStart"))
        }

        private fun objectFields(expected: Set<String>, field: (String) -> Unit) {
            val seen = mutableSetOf<String>()
            reader.readObject { key ->
                if (key !in expected) {
                    // v1 ignored extra metadata. Keep it in the original raw recovery
                    // copy, but never introduce unknown fields into the v2 domain.
                    if (source == 1) reader.skipValue() else invalid("Unknown workspace field: $key.")
                } else { seen += key; field(key) }
            }
            checkValue(seen == expected, "Required workspace fields are missing.")
        }

        private fun <T> array(limit: Int, item: () -> T): List<T> = mutableListOf<T>().apply {
            reader.readArray(limit) { add(item()) }
        }
        private fun <T> comparedArray(limit: Int, expected: List<T>?, item: (T?) -> T): List<T> {
            if (expected == null) return array(limit) { item(null) }
            var index = 0
            reader.readArray(limit) {
                checkValue(index < expected.size, "Decoded workspace has extra records.")
                checkValue(item(expected[index]) == expected[index], "Decoded workspace record differs from the expected value.")
                index++
            }
            checkValue(index == expected.size, "Decoded workspace records are missing.")
            return expected
        }
        private fun text(expected: String?): String = if (expected == null) reader.readString() else reader.readStringMatching(expected)
        private fun configuration() = WorkspaceLimits.capacity(++configurations <= WorkspaceLimits.MAX_CONFIGURATIONS,
            "Workspace has too many dated configurations.")
        private fun date(): LocalDate = LocalDate.parse(reader.readString())
        private inline fun <reified T : Enum<T>> enumValue(): T = enumValueOf(reader.readString())
        private fun amount(): Double {
            val value = decimal(reader.readNumber())
            checkValue(value >= MIN_AMOUNT && value <= MAX_AMOUNT && value.stripTrailingZeros().scale() <= 3,
                "Amounts must be between 0.001 and 1,000,000 with at most three decimal places.")
            return value.toDouble()
        }
    }

    private val MIN_AMOUNT = BigDecimal("0.001")
    private val MAX_AMOUNT = BigDecimal("1000000")
    private fun decimal(source: String): BigDecimal {
        // A supported finite three-decimal value never needs an enormous lexeme.
        WorkspaceLimits.capacity(source.length <= 128, "Numeric representation exceeds supported capacity.")
        return try { BigDecimal(source) } catch (error: NumberFormatException) { invalid("Invalid decimal number.") }
    }
    private fun integer(source: String): Int = try { decimal(source).intValueExact() }
        catch (error: ArithmeticException) { invalid("Expected an integer in range.") }
    private fun <T : Any> required(value: T?, field: String): T = value ?: invalid("Missing $field.")
    private fun checkValue(condition: Boolean, message: String) { if (!condition) invalid(message) }
    private fun invalid(message: String): Nothing = throw WorkspaceJsonException(WorkspaceJsonFailure.INVALID, message)
}
