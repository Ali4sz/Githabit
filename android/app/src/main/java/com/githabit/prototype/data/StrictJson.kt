package com.githabit.prototype.data

/**
 * Strict streaming reader: schema callbacks consume each value immediately, so parsing
 * cannot retain a second whole-document tree. It does not use platform org.json coercion.
 */
internal class StrictJson(private val text: String) {
    private var position = 0
    private var depth = 0
    private var values = 0

    init {
        WorkspaceLimits.capacity(WorkspaceLimits.utf8Size(text) <= WorkspaceLimits.MAX_BYTES,
            "Workspace exceeds the supported UTF-8 byte limit.")
    }

    fun validate() { skipValue(); finish() }

    fun finish() {
        whitespace()
        if (position != text.length) malformed("Trailing content")
    }

    private fun skipValue() {
        whitespace()
        when (peek()) {
            '{' -> readObject { skipValue() }
            '[' -> readArray { skipValue() }
            '"' -> readString()
            't', 'f' -> readBoolean()
            'n' -> consumeNull()
            '-', in '0'..'9' -> readNumber()
            else -> malformed("Expected a JSON value")
        }
    }

    private fun beginValue() {
        whitespace()
        WorkspaceLimits.capacity(++values <= WorkspaceLimits.MAX_JSON_VALUES, "Workspace has too many JSON values.")
    }

    private fun beginContainer(char: Char) {
        beginValue()
        if (peek() != char) wrongType(if (char == '{') "object" else "array")
        WorkspaceLimits.capacity(++depth <= WorkspaceLimits.MAX_DEPTH,
            "Workspace nesting exceeds ${WorkspaceLimits.MAX_DEPTH} containers.")
        expect(char)
    }

    fun readObject(field: (String) -> Unit) {
        beginContainer('{')
        whitespace()
        val fields = mutableSetOf<String>()
        try {
            if (consume('}')) return
            do {
                whitespace()
                WorkspaceLimits.capacity(fields.size < WorkspaceLimits.MAX_OBJECT_FIELDS, "Workspace object has too many fields.")
                if (peek() != '"') malformed("Object keys must be quoted")
                val key = string()
                if (!fields.add(key)) malformed("Duplicate object key")
                whitespace()
                expect(':')
                field(key)
                whitespace()
                if (consume('}')) return
                expect(',')
            } while (true)
        } finally { depth-- }
    }

    fun readArray(limit: Int = WorkspaceLimits.MAX_COMMITS, item: () -> Unit) {
        require(limit in 0..WorkspaceLimits.MAX_COMMITS)
        beginContainer('[')
        whitespace()
        var count = 0
        try {
            if (consume(']')) return
            do {
                WorkspaceLimits.capacity(count++ < limit, "Workspace array exceeds supported capacity.")
                item()
                whitespace()
                if (consume(']')) return
                expect(',')
            } while (true)
        } finally { depth-- }
    }

    fun readString(): String {
        beginValue()
        if (peek() != '"') wrongType("string")
        return string()
    }

    fun readBoolean(): Boolean {
        beginValue()
        return when (peek()) {
            't' -> { literal("true"); true }
            'f' -> { literal("false"); false }
            else -> wrongType("boolean")
        }
    }

    fun consumeNull(): Boolean {
        whitespace()
        if (peek() != 'n') return false
        beginValue()
        literal("null")
        return true
    }

    /** Retain the original lexeme until the schema checks bounds and exact precision. */
    fun readNumber(): String {
        beginValue()
        if (peek() != '-' && peek() !in '0'..'9') wrongType("number")
        return number()
    }

    private fun string(): String {
        expect('"')
        val result = StringBuilder()
        while (position < text.length) {
            val char = text[position++]
            when {
                char == '"' -> return result.toString().also { WorkspaceLimits.utf8Size(it) }
                char == '\\' -> {
                    if (position == text.length) malformed("Incomplete string escape")
                    when (val escape = text[position++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (text.length - position < 4) malformed("Incomplete Unicode escape")
                            var code = 0
                            repeat(4) {
                                val digit = text[position++]
                                code = code * 16 + when (digit) {
                                    in '0'..'9' -> digit - '0'
                                    in 'a'..'f' -> digit - 'a' + 10
                                    in 'A'..'F' -> digit - 'A' + 10
                                    else -> malformed("Invalid Unicode escape")
                                }
                            }
                            result.append(code.toChar())
                        }
                        else -> malformed("Invalid string escape")
                    }
                }
                char.code < 0x20 -> malformed("Unescaped control character")
                else -> result.append(char)
            }
        }
        malformed("Unterminated string")
    }

    private fun number(): String {
        val start = position
        consume('-')
        if (!consume('0')) {
            if (peek() !in '1'..'9') malformed("Invalid number")
            while (peek() in '0'..'9') position++
        }
        if (consume('.')) digits()
        if (peek() == 'e' || peek() == 'E') {
            position++
            if (peek() == '+' || peek() == '-') position++
            digits()
        }
        return text.substring(start, position)
    }

    private fun digits() {
        if (peek() !in '0'..'9') malformed("Expected number digits")
        while (peek() in '0'..'9') position++
    }

    private fun literal(expected: String) {
        if (!text.startsWith(expected, position)) malformed("Invalid literal")
        position += expected.length
    }

    private fun whitespace() {
        while (true) when (peek()) {
            ' ', '\t', '\r', '\n' -> position++
            else -> return
        }
    }
    private fun peek(): Char? = text.getOrNull(position)
    private fun consume(char: Char): Boolean = if (peek() == char) { position++; true } else false
    private fun expect(char: Char) { if (!consume(char)) malformed("Expected '$char'") }
    private fun malformed(message: String): Nothing = throw WorkspaceJsonException(
        WorkspaceJsonFailure.CORRUPT, "$message at character $position.")
    private fun wrongType(expected: String): Nothing = throw WorkspaceJsonException(
        WorkspaceJsonFailure.INVALID, "Expected JSON $expected at character $position.")
}

/** Writes incrementally into a bounded buffer, checking escaped UTF-8 size before growth. */
internal class BoundedJsonWriter(private val limit: Int = WorkspaceLimits.MAX_BYTES) {
    private val output = StringBuilder()
    private var byteCount = 0L
    private var firstField = true

    init { require(limit >= 0) }

    fun obj(block: BoundedJsonWriter.() -> Unit) {
        val outerFirst = firstField
        raw("{")
        firstField = true
        block()
        raw("}")
        firstField = outerFirst
    }

    fun field(name: String, value: String) { key(name); string(value) }
    fun field(name: String, value: Boolean) { key(name); raw(value.toString()) }
    fun field(name: String, value: Int) { key(name); raw(value.toString()) }
    fun field(name: String, value: Double) {
        require(value.isFinite()) { "JSON numbers must be finite." }
        key(name); raw(value.toString())
    }
    fun nullField(name: String) { key(name); raw("null") }
    fun objectField(name: String, block: BoundedJsonWriter.() -> Unit) { key(name); obj(block) }
    fun <T> arrayField(name: String, values: Iterable<T>, block: BoundedJsonWriter.(T) -> Unit) {
        key(name)
        raw("[")
        values.forEachIndexed { index, value ->
            if (index > 0) raw(",")
            block(value)
        }
        raw("]")
    }

    fun string(value: String) {
        raw("\"")
        var start = 0
        value.forEachIndexed { index, char ->
            val escaped = when (char) {
                '"' -> "\\\""
                '\\' -> "\\\\"
                '\b' -> "\\b"
                '\u000c' -> "\\f"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> if (char.code < 0x20) "\\u" + char.code.toString(16).padStart(4, '0') else null
            }
            if (escaped != null) {
                raw(value, start, index)
                raw(escaped)
                start = index + 1
            }
        }
        raw(value, start, value.length)
        raw("\"")
    }

    private fun key(name: String) {
        if (!firstField) raw(",")
        firstField = false
        string(name)
        raw(":")
    }

    private fun raw(value: String, start: Int = 0, end: Int = value.length) {
        val bytes = WorkspaceLimits.utf8Size(value, start, end)
        WorkspaceLimits.capacity(byteCount + bytes <= limit, "Encoded workspace exceeds the supported UTF-8 byte limit.")
        output.append(value, start, end)
        byteCount += bytes
    }

    override fun toString(): String = output.toString()
}
