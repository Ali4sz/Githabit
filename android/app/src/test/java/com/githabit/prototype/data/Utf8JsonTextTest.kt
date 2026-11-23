package com.githabit.prototype.data

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class Utf8JsonTextTest {
    @Test fun knownLengthReadStillRejectsTruncationExtraBytesAndCapacity() {
        val bytes = "kept 🌱".toByteArray()
        assertArrayEquals(bytes, WorkspaceLimits.readBounded(bytes.inputStream(), knownSize = bytes.size.toLong()))
        assertThrows(java.io.IOException::class.java) { WorkspaceLimits.readBounded(bytes.inputStream(), knownSize = bytes.size + 1L) }
        assertThrows(java.io.IOException::class.java) { WorkspaceLimits.readBounded(bytes.inputStream(), knownSize = bytes.size - 1L) }
        assertEquals(WorkspaceJsonFailure.CAPACITY, assertThrows(WorkspaceJsonException::class.java) {
            WorkspaceLimits.readBounded(bytes.inputStream(), limit = bytes.size - 1, knownSize = bytes.size.toLong())
        }.kind)
        assertEquals(WorkspaceJsonFailure.CAPACITY, assertThrows(WorkspaceJsonException::class.java) {
            WorkspaceLimits.readBounded(bytes.inputStream(), limit = bytes.size - 1)
        }.kind)
    }

    @Test fun literalUnicodeAndBackwardSeeksMatchStringInput() {
        val original = "abc🌱العربية".repeat(1400)
        val text = Utf8JsonText(original.toByteArray())
        assertEquals(original.length, text.length)
        original.indices.forEach { assertEquals(original[it], text[it]) }
        original.indices.reversed().forEach { assertEquals(original[it], text[it]) }
        assertEquals(original, text.toString())
        for (start in listOf(0, 4090, original.length - 10)) {
            assertEquals(original.substring(start, start + 9), text.subSequence(start, start + 9).toString())
        }
    }

    @Test fun invalidUtf8IsRejectedWithoutReplacementCharacters() {
        for (bytes in listOf(byteArrayOf(0xc0.toByte(), 0x80.toByte()), byteArrayOf(0xc2.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), byteArrayOf(0xff.toByte()))) {
            assertEquals(WorkspaceJsonFailure.CORRUPT,
                assertThrows(WorkspaceJsonException::class.java) { Utf8JsonText(bytes) }.kind)
        }
    }

    @Test fun strictUtf8AcceptanceMatchesIndependentDecoderAcrossByteSequences() {
        val random = java.util.Random(13)
        repeat(3000) {
            val bytes = ByteArray(random.nextInt(12)) { random.nextInt(256).toByte() }
            val independent = runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            }
            val actual = runCatching { Utf8JsonText(bytes).toString() }
            assertEquals(independent.isSuccess, actual.isSuccess)
            if (independent.isSuccess) assertEquals(independent.getOrThrow(), actual.getOrThrow())
        }
    }

    @Test fun streamingReadbackRejectsDifferentTruncatedAndExtendedCopies() {
        val expected = "abc🌱".repeat(8192).toByteArray()
        assertTrue(ByteArrayInputStream(expected).matchesBytes(expected))
        assertFalse(ByteArrayInputStream(expected.copyOf(expected.size - 1)).matchesBytes(expected))
        assertFalse(ByteArrayInputStream(expected + byteArrayOf(1)).matchesBytes(expected))
        assertFalse(ByteArrayInputStream(expected.copyOf().also { it[8192] = 0 }).matchesBytes(expected))
    }
}
