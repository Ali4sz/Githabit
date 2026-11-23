package com.githabit.prototype.data

/** Replayable owned bytes, not a whole-document UTF-16 copy. Sparse offsets bound seeking costs. */
internal class Utf8JsonText(private val bytes: ByteArray) : CharSequence {
    private val byteOffsets = IntArray(bytes.size / 4096 + 2)
    private val charOffsets = IntArray(byteOffsets.size)
    private var checkpoints = 1
    private var cursorByte = 0
    private var cursorChar = 0
    override val length: Int

    init {
        WorkspaceLimits.capacity(bytes.size <= WorkspaceLimits.MAX_BYTES, "Workspace exceeds the supported UTF-8 byte limit.")
        var offset = 0
        var chars = 0
        var nextCheckpoint = 4096
        while (offset < bytes.size) {
            val code = codePoint(offset)
            offset += width(offset)
            chars += if (code > 0xffff) 2 else 1
            if (offset >= nextCheckpoint) {
                byteOffsets[checkpoints] = offset
                charOffsets[checkpoints++] = chars
                nextCheckpoint = offset + 4096
            }
        }
        length = chars
    }

    override fun get(index: Int): Char {
        require(index in 0 until length)
        seek(index)
        val code = codePoint(cursorByte)
        return if (code <= 0xffff) code.toChar()
        else if (index == cursorChar) Character.highSurrogate(code) else Character.lowSurrogate(code)
    }

    private fun seek(index: Int) {
        if (index < cursorChar) {
            // Small backwards seeks are common for materializing the just-scanned key/value.
            // Do not replay thousands of bytes from a checkpoint for every schema field.
            if (cursorChar - index <= 4096) {
                while (cursorChar > index) {
                    cursorByte--
                    while ((bytes[cursorByte].toInt() and 255) in 128..191) cursorByte--
                    cursorChar -= if (width(cursorByte) == 4) 2 else 1
                }
                return
            }
            var low = 0
            var high = checkpoints
            while (low + 1 < high) {
                val middle = (low + high) / 2
                if (charOffsets[middle] <= index) low = middle else high = middle
            }
            cursorByte = byteOffsets[low]
            cursorChar = charOffsets[low]
        }
        while (cursorChar < index && cursorByte < bytes.size) {
            val count = if (codePoint(cursorByte) > 0xffff) 2 else 1
            if (cursorChar + count > index) break
            cursorChar += count
            cursorByte += width(cursorByte)
        }
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex in 0..endIndex && endIndex <= length)
        seek(startIndex)
        val start = cursorByte
        val completeStart = cursorChar == startIndex
        seek(endIndex)
        if (completeStart && cursorChar == endIndex) return String(bytes, start, cursorByte - start, Charsets.UTF_8)
        // Only generic callers splitting a supplementary pair need this small fallback.
        return CharArray(endIndex - startIndex) { get(startIndex + it) }.concatToString()
    }

    override fun toString(): String = subSequence(0, length).toString()

    private fun width(offset: Int): Int = when (bytes[offset].toInt() and 255) {
        in 0..127 -> 1
        in 194..223 -> 2
        in 224..239 -> 3
        in 240..244 -> 4
        else -> damaged()
    }

    private fun codePoint(offset: Int): Int {
        val first = bytes[offset].toInt() and 255
        val width = width(offset)
        if (width == 1) return first
        if (bytes.size - offset < width) damaged()
        var code = first and (127 shr width)
        for (index in 1 until width) {
            val next = bytes[offset + index].toInt() and 255
            if (next !in 128..191) damaged()
            code = (code shl 6) or (next and 63)
        }
        if (code < when (width) { 2 -> 128; 3 -> 2048; else -> 65536 } ||
            code in 0xd800..0xdfff || code > 0x10ffff) damaged()
        return code
    }

    private fun damaged(): Nothing = throw WorkspaceJsonException(WorkspaceJsonFailure.CORRUPT,
        "Workspace contains damaged UTF-8.")
}
