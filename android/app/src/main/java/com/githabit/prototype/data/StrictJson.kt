package com.githabit.prototype.data

import java.io.OutputStream

/**
 * Strict streaming reader: schema callbacks consume each value immediately, so parsing
 * cannot retain a second whole-document tree. It does not use platform org.json coercion.
 */
internal class StrictJson(private val text: CharSequence) {
    private var position = 0
    private var depth = 0
    private var values = 0

    init {
        WorkspaceLimits.capacity(text is Utf8JsonText || WorkspaceLimits.utf8Size(text) <= WorkspaceLimits.MAX_BYTES,
            "Workspace exceeds the supported UTF-8 byte limit.")
    }

    fun validate() { skipValue(); finish() }

    fun finish() {
        whitespace()
        if (position != text.length) malformed("Trailing content")
    }

    fun skipValue() {
        whitespace()
        when (peek()) {
            '{' -> readObject { skipValue() }
            '[' -> readArray { skipValue() }
            '"' -> { beginValue(); scanString(null) }
            't', 'f' -> readBoolean()
            'n' -> consumeNull()
            '-', in '0'..'9' -> { beginValue(); scanNumber() }
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

    /** Exact decoded code-unit comparison, validating every escape and surrogate pair. */
    fun readStringMatching(expected: String): String {
        beginValue()
        if (peek() != '"') wrongType("string")
        var index = 0
        var equal = true
        scanString(null) { char ->
            if (index >= expected.length || expected[index] != char) equal = false
            index++
        }
        if (!equal || index != expected.length) throw WorkspaceJsonException(
            WorkspaceJsonFailure.INVALID, "Decoded workspace text differs from the expected value.")
        return expected
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
        val start = position
        val escaped = scanString(null)
        val end = position
        if (!escaped) return text.subSequence(start + 1, end - 1).toString()
        position = start
        var size = 0
        scanString(null) { char -> size += when {
            char.code < 128 -> 1
            char.code < 2048 -> 2
            char.isHighSurrogate() -> 4
            char.isLowSurrogate() -> 0
            else -> 3
        } }
        val bytes = ByteArray(size)
        var offset = 0
        var high: Char? = null
        fun byte(value: Int) { bytes[offset++] = value.toByte() }
        position = start
        scanString(null) { char -> when {
            char.code < 128 -> byte(char.code)
            char.code < 2048 -> { byte(0xc0 or (char.code shr 6)); byte(0x80 or (char.code and 63)) }
            char.isHighSurrogate() -> high = char
            char.isLowSurrogate() -> {
                val code = Character.toCodePoint(requireNotNull(high), char)
                byte(0xf0 or (code shr 18)); byte(0x80 or ((code shr 12) and 63))
                byte(0x80 or ((code shr 6) and 63)); byte(0x80 or (code and 63))
                high = null
            }
            else -> { byte(0xe0 or (char.code shr 12)); byte(0x80 or ((char.code shr 6) and 63)); byte(0x80 or (char.code and 63)) }
        } }
        check(offset == bytes.size)
        return bytes.toString(Charsets.UTF_8)
    }

    /** Null result is validation-only, including Unicode obtained from escaped ASCII input. */
    private fun scanString(result: StringBuilder?, consume: ((Char) -> Unit)? = null): Boolean {
        expect('"')
        var escaped = false
        var highSurrogate = false
        var invalidSurrogate = false
        while (position < text.length) {
            val char = text[position++]
            val decoded = when {
                char == '"' -> {
                    if (highSurrogate || invalidSurrogate) throw WorkspaceJsonException(
                        WorkspaceJsonFailure.CORRUPT, "Workspace contains an unpaired Unicode surrogate.")
                    return escaped
                }
                char == '\\' -> {
                    escaped = true
                    if (position == text.length) malformed("Incomplete string escape")
                    when (val escape = text[position++]) {
                        '"', '\\', '/' -> escape
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
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
                            code.toChar()
                        }
                        else -> malformed("Invalid string escape")
                    }
                }
                char.code < 0x20 -> malformed("Unescaped control character")
                else -> char
            }
            if (highSurrogate) { if (!decoded.isLowSurrogate()) invalidSurrogate = true }
            else if (decoded.isLowSurrogate()) invalidSurrogate = true
            highSurrogate = decoded.isHighSurrogate()
            result?.append(decoded)
            consume?.invoke(decoded)
        }
        malformed("Unterminated string")
    }

    private fun number(): String {
        val start = position
        scanNumber()
        return text.subSequence(start, position).toString()
    }

    private fun scanNumber() {
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
    }

    private fun digits() {
        if (peek() !in '0'..'9') malformed("Expected number digits")
        while (peek() in '0'..'9') position++
    }

    private fun literal(expected: String) {
        if (text.length - position < expected.length || expected.indices.any { text[position + it] != expected[it] }) malformed("Invalid literal")
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

/** One canonical emission path. Null output counts; production output is exact-sized or streamed. */
internal class BoundedJsonWriter(
    private val limit: Int = WorkspaceLimits.MAX_BYTES,
    private val output: OutputStream? = JsonChunks(),
) {
    private var byteCount = 0L
    private var firstField = true
    private var buffer: ByteArray? = null
    private var buffered = 0

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
        byteCount += bytes
        if (output == null) return
        var index = start
        while (index < end) {
            val char = value[index++]
            when {
                char.code < 0x80 -> byte(char.code)
                char.code < 0x800 -> { byte(0xc0 or (char.code shr 6)); byte(0x80 or (char.code and 63)) }
                char.isHighSurrogate() -> {
                    // utf8Size above already checked pairing, before writing any of this span.
                    val code = Character.toCodePoint(char, value[index++])
                    byte(0xf0 or (code shr 18)); byte(0x80 or ((code shr 12) and 63))
                    byte(0x80 or ((code shr 6) and 63)); byte(0x80 or (code and 63))
                }
                else -> {
                    byte(0xe0 or (char.code shr 12)); byte(0x80 or ((char.code shr 6) and 63))
                    byte(0x80 or (char.code and 63))
                }
            }
        }
    }

    private fun byte(value: Int) {
        val bytes = buffer ?: ByteArray(8192).also { buffer = it }
        bytes[buffered++] = value.toByte()
        if (buffered == bytes.size) flush()
    }

    private fun flush() {
        if (buffered > 0) { requireNotNull(output).write(requireNotNull(buffer), 0, buffered); buffered = 0 }
    }

    fun finish(): Long { flush(); return byteCount }
    fun toByteArray(): ByteArray {
        finish()
        return (output as JsonChunks).bytes(byteCount.toInt())
    }
    override fun toString(): String = toByteArray().toString(Charsets.UTF_8)
}

/** Convenience writer only; production callers pre-count and allocate their final array once. */
private class JsonChunks : OutputStream() {
    private val chunks = mutableListOf<ByteArray>()
    override fun write(value: Int) { chunks += byteArrayOf(value.toByte()) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) { chunks += bytes.copyOfRange(offset, offset + length) }
    fun bytes(size: Int): ByteArray = ByteArray(size).also { result ->
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
    }
}
