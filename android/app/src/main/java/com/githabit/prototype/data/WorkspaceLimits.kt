package com.githabit.prototype.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class WorkspaceJsonFailure { CORRUPT, UNSUPPORTED_VERSION, INVALID, LEGACY_INCOMPATIBLE, CAPACITY }

class WorkspaceJsonException(val kind: WorkspaceJsonFailure, message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Shared save/load/export/import contract. Device-memory certification remains a delivery gate. */
object WorkspaceLimits {
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_HABITS = 1_000
    const val MAX_COMMITS = 100_000
    const val MAX_CONFIGURATIONS = 100_000
    const val MAX_ID_LENGTH = 128
    const val MAX_DEPTH = 8
    internal const val IO_BUFFER_BYTES = 8192
    // Allocation/CPU guards above the largest supported schema shape. The largest
    // record has eight fields; a selected-weekday version has at most fourteen values.
    internal const val MAX_OBJECT_FIELDS = 16
    internal const val MAX_JSON_VALUES = 14 * MAX_CONFIGURATIONS + 7 * MAX_COMMITS + 9 * MAX_HABITS + 32

    fun capacity(condition: Boolean, message: String) {
        if (!condition) throw WorkspaceJsonException(WorkspaceJsonFailure.CAPACITY, message)
    }

    /** Reads at most limit+1 bytes; a rejected file is never represented by a truncated prefix. */
    fun readBounded(input: InputStream, limit: Int = MAX_BYTES, knownSize: Long? = null): ByteArray {
        require(limit >= 0)
        if (knownSize != null) {
            return readKnownSize(input, limit, knownSize, emptyList())
        }
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        while (true) {
            val buffer = ByteArray(minOf(8192L, limit.toLong() - size + 1).toInt())
            var used = 0
            var ended = false
            while (used < buffer.size) {
                val count = input.read(buffer, used, buffer.size - used)
                if (count < 0) { ended = true; break }
                if (count == 0) {
                    val byte = input.read()
                    if (byte < 0) { ended = true; break }
                    buffer[used++] = byte.toByte()
                } else used += count
                capacity(size.toLong() + used <= limit, "Workspace exceeds the supported UTF-8 byte limit.")
            }
            if (used > 0) { chunks += if (used == buffer.size) buffer else buffer.copyOf(used); size += used }
            if (ended) break
        }
        return ByteArray(size).also { result ->
            var position = 0
            for (chunk in chunks) { chunk.copyInto(result, position); position += chunk.size }
        }
    }

    /** App-owned files only: aliases require exact independently read bytes and final EOF. */
    internal fun readMatchingBounded(input: InputStream, knownSize: Long?, expected: List<ByteArray>): ByteArray =
        if (knownSize != null) readKnownSize(input, MAX_BYTES, knownSize, expected)
        else readBounded(input).let { actual -> expected.firstOrNull { it.contentEquals(actual) } ?: actual }

    private fun readKnownSize(input: InputStream, limit: Int, size: Long, expected: List<ByteArray>): ByteArray {
        if (size < 0) throw java.io.IOException("Workspace stream position exceeds its length.")
        capacity(size <= limit, "Workspace exceeds the supported UTF-8 byte limit.")
        val length = size.toInt()
        val matching = expected.filterTo(mutableListOf()) { it.size == length }
        var prefix = matching.firstOrNull()
        var result: ByteArray? = if (matching.isEmpty()) ByteArray(length) else null
        val buffer = ByteArray(minOf(IO_BUFFER_BYTES, length))
        var position = 0
        while (position < length) {
            var count = input.read(buffer, 0, minOf(buffer.size, length - position))
            if (count < 0) throw java.io.IOException("Workspace changed length while reading.")
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) throw java.io.IOException("Workspace changed length while reading.")
                buffer[0] = byte.toByte()
                count = 1
            }
            if (result == null) {
                matching.removeAll { candidate ->
                    (0 until count).any { buffer[it] != candidate[position + it] }
                }
                if (matching.isEmpty()) {
                    // The earlier prefix matched every surviving candidate, including prefix.
                    result = ByteArray(length).also { prefix!!.copyInto(it, 0, 0, position) }
                } else prefix = matching.first()
            }
            result?.let { buffer.copyInto(it, position, 0, count) }
            position += count
        }
        if (input.read() != -1) {
            capacity(length < limit, "Workspace exceeds the supported UTF-8 byte limit.")
            throw java.io.IOException("Workspace changed length while reading.")
        }
        return result ?: matching.first()
    }

    fun decodeUtf8(bytes: ByteArray): String {
        capacity(bytes.size <= MAX_BYTES, "Workspace exceeds the supported UTF-8 byte limit.")
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw WorkspaceJsonException(WorkspaceJsonFailure.CORRUPT, "Workspace contains damaged UTF-8.", error)
        }
    }

    /** Counts without allocating another encoded copy; rejects unpaired UTF-16 surrogates. */
    internal fun utf8Size(text: CharSequence, start: Int = 0, end: Int = text.length): Long {
        var size = 0L
        var index = start
        while (index < end) {
            val char = text[index++]
            size += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() -> {
                    if (index >= end || !text[index++].isLowSurrogate()) invalidUnicode()
                    4
                }
                char.isLowSurrogate() -> invalidUnicode()
                else -> 3
            }
        }
        return size
    }

    private fun invalidUnicode(): Nothing =
        throw WorkspaceJsonException(WorkspaceJsonFailure.CORRUPT, "Workspace contains an unpaired Unicode surrogate.")
}
