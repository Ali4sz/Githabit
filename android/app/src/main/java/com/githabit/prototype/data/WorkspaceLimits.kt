package com.githabit.prototype.data

import java.io.ByteArrayOutputStream
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
    // Allocation/CPU guards above the largest supported schema shape. The largest
    // record has eight fields; a selected-weekday version has at most fourteen values.
    internal const val MAX_OBJECT_FIELDS = 16
    internal const val MAX_JSON_VALUES = 14 * MAX_CONFIGURATIONS + 7 * MAX_COMMITS + 9 * MAX_HABITS + 32

    fun capacity(condition: Boolean, message: String) {
        if (!condition) throw WorkspaceJsonException(WorkspaceJsonFailure.CAPACITY, message)
    }

    /** Reads at most limit+1 bytes; a rejected file is never represented by a truncated prefix. */
    fun readBounded(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - output.size() + 1).toInt())
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                capacity(output.size() < limit, "Workspace exceeds the supported UTF-8 byte limit.")
                output.write(byte)
            } else {
                capacity(output.size().toLong() + count <= limit, "Workspace exceeds the supported UTF-8 byte limit.")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
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
