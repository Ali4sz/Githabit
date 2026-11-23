package com.githabit.prototype.data

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class StrictJsonTest {
    @Test fun skippedStringsValidateIdenticallyWithoutMaterializingTheirValues() {
        val malformed = listOf("\"\\uD800\"", "\"\\uDC00\"", "\"\\uD800x\"", "\"\\uD800\\x\"",
            "\"\\u123\"", "\"\\uZZZZ\"", "\"bad\ntext\"", "\"unterminated", "\"\\")
        for (input in malformed) {
            val read = assertThrows(WorkspaceJsonException::class.java) { StrictJson(input).readString() }
            val skip = assertThrows(WorkspaceJsonException::class.java) { StrictJson(input).skipValue() }
            assertEquals(read.kind, skip.kind)
            assertEquals(read.message, skip.message)
        }
        for (input in listOf("\"\\uD83C\\uDF31\"", "\"🌱\"")) {
            StrictJson(input).validate()
        }
        // A full-sized discarded value exercises the allocation-free probe path.
        StrictJson("\"" + " ".repeat(WorkspaceLimits.MAX_BYTES - 2) + "\"").validate()
    }

    @Test fun strictParserAcceptsNestedValuesWithoutCoercingTypesOrNumbers() {
        val reader = StrictJson("{\"a\":[true,false,null,\"12\",-0.125e+2],\"b\":{}}")
        reader.readObject { key ->
            if (key == "a") {
                var index = 0
                reader.readArray {
                    when (index++) {
                        0 -> assertTrue(reader.readBoolean())
                        1 -> assertFalse(reader.readBoolean())
                        2 -> assertTrue(reader.consumeNull())
                        3 -> assertEquals("12", reader.readString())
                        4 -> assertEquals("-0.125e+2", reader.readNumber())
                        else -> fail("Unexpected element")
                    }
                }
                assertEquals(5, index)
            } else reader.readObject { fail("Expected empty object") }
        }
        reader.finish()
        assertEquals(WorkspaceJsonFailure.INVALID,
            assertThrows(WorkspaceJsonException::class.java) { StrictJson("\"12\"").readNumber() }.kind)
    }

    @Test fun malformedGrammarAndDuplicateKeysAreRejected() {
        val invalid = listOf("", "{", "[", "{'a':1}", "{a:1}", "{\"a\":1,}", "[1,]", "[1,,2]",
            "{\"a\":1;\"b\":2}", "[1 2]", "{}{}", "{} trailing", "// comment\n{}", "/* comment */{}",
            "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "NaN", "Infinity", "01", "-01",
            "+1", "1.", ".1", "1e", "1e+", "--1", "TRUE", "undefined", "\"bad\ntext\"", "\"\\x00\"",
            "\"\\u123\"", "\"\\uZZZZ\"", "\"\\u１２３４\"", "\"unterminated", "\"\\uD800\"", "\"\\uDC00\"")
        invalid.forEach { input ->
            val error = assertThrows("Input: $input", WorkspaceJsonException::class.java) { StrictJson(input).validate() }
            assertEquals(input, WorkspaceJsonFailure.CORRUPT, error.kind)
        }
    }

    @Test fun unicodeEscapesAndLiteralSupplementaryCharactersRoundTrip() {
        val input = "Quotes \" slash \\ / newline\n tab\t \b\u000c\r\u0000 🌱 العربية"
        val writer = BoundedJsonWriter()
        writer.obj { field("text", input) }
        val reader = StrictJson(writer.toString())
        reader.readObject { assertEquals("text", it); assertEquals(input, reader.readString()) }
        reader.finish()
        assertEquals("🌱", StrictJson("\"\\uD83C\\uDF31\"").readString())
    }

    @Test fun depthCountsRootContainerAndIgnoresBracketsInsideStrings() {
        for (depth in listOf(7, 8)) {
            StrictJson("[".repeat(depth) + "\"[{}]\"" + "]".repeat(depth)).validate()
        }
        val error = assertThrows(WorkspaceJsonException::class.java) { StrictJson("[".repeat(9) + "]".repeat(9)).validate() }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }

    @Test fun boundedReadChecksBelowAtAndAboveLimitWithoutReturningTruncation() {
        for (size in listOf(15, 16)) assertEquals(size,
            WorkspaceLimits.readBounded(ByteArrayInputStream(ByteArray(size)), 16).size)
        val source = ByteArrayInputStream(ByteArray(100))
        val error = assertThrows(WorkspaceJsonException::class.java) { WorkspaceLimits.readBounded(source, 16) }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
        assertEquals(83, source.available())
    }

    @Test fun outputByteLimitCountsEscapingAndMultibyteCharactersBeforeAppend() {
        val value = "\u0000🌱"
        val exact = "{\"v\":\"\\u0000🌱\"}".toByteArray().size
        for (limit in listOf(exact, exact + 1)) {
            val writer = BoundedJsonWriter(limit)
            writer.obj { field("v", value) }
            assertEquals(exact, writer.toString().toByteArray().size)
        }
        val error = assertThrows(WorkspaceJsonException::class.java) {
            BoundedJsonWriter(exact - 1).obj { field("v", value) }
        }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }

    @Test fun utf8DecodingRejectsDamageAndSizeBeforeParsing() {
        assertEquals("🌱", WorkspaceLimits.decodeUtf8("🌱".toByteArray()))
        val error = assertThrows(WorkspaceJsonException::class.java) {
            WorkspaceLimits.decodeUtf8(byteArrayOf(0xc3.toByte(), 0x28))
        }
        assertEquals(WorkspaceJsonFailure.CORRUPT, error.kind)
        assertThrows(WorkspaceJsonException::class.java) { BoundedJsonWriter().string("\uD800") }
    }

    @Test fun writerProducesStrictNestedSchemaAndPreservesAllPrimitiveTypes() {
        val writer = BoundedJsonWriter()
        writer.obj {
            field("version", 2)
            field("onboarded", false)
            arrayField("habits", listOf(1, 2)) { id -> obj { field("id", "$id"); field("target", 0.125) } }
            objectField("calendar") { field("initialWeekStart", "MONDAY"); nullField("pending") }
            field("name", "")
        }
        val reader = StrictJson(writer.toString())
        reader.readObject { key -> when (key) {
            "version" -> assertEquals("2", reader.readNumber())
            "onboarded" -> assertFalse(reader.readBoolean())
            "habits" -> {
                var count = 0
                reader.readArray { count++; reader.readObject { field ->
                    if (field == "id") assertEquals("$count", reader.readString())
                    else assertEquals("0.125", reader.readNumber())
                } }
                assertEquals(2, count)
            }
            "calendar" -> reader.readObject { field ->
                if (field == "initialWeekStart") assertEquals("MONDAY", reader.readString())
                else assertTrue(reader.consumeNull())
            }
            "name" -> assertEquals("", reader.readString())
            else -> fail("Unexpected field")
        } }
        reader.finish()
    }

    @Test fun actual32MiBInputLimitAcceptsBoundaryAndRejectsOneByteAbove() {
        val limit = WorkspaceLimits.MAX_BYTES
        for (size in listOf(limit - 1, limit)) {
            val text = "{}" + " ".repeat(size - 2)
            StrictJson(text).validate()
        }
        val oversized = "{}" + " ".repeat(limit - 1)
        val error = assertThrows(WorkspaceJsonException::class.java) { StrictJson(oversized).validate() }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
        assertThrows(WorkspaceJsonException::class.java) { WorkspaceLimits.decodeUtf8(ByteArray(limit + 1)) }
    }

    @Test fun actualOutputLimitIsEnforcedBeforeOversizedBufferGrowth() {
        val payload = " ".repeat(WorkspaceLimits.MAX_BYTES - 2)
        val writer = BoundedJsonWriter()
        writer.string(payload)
        assertEquals(WorkspaceLimits.MAX_BYTES, writer.toString().length)
        assertThrows(WorkspaceJsonException::class.java) { writer.string("") }
    }

    @Test fun largestSchemaArrayStopsAtSupportedCount() {
        val accepted = "[" + List(WorkspaceLimits.MAX_COMMITS) { "0" }.joinToString(",") + "]"
        val reader = StrictJson(accepted)
        var count = 0
        reader.readArray { assertEquals("0", reader.readNumber()); count++ }
        reader.finish()
        assertEquals(WorkspaceLimits.MAX_COMMITS, count)
        val error = assertThrows(WorkspaceJsonException::class.java) { StrictJson(accepted.dropLast(1) + ",0]").validate() }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }

    @Test fun nestedArraysHaveADocumentWideValueLimitWithoutBuildingATree() {
        val array = "[" + List(100_000) { "0" }.joinToString(",") + "]"
        val payload = "[" + List(70) { array }.joinToString(",") + "]"
        assertTrue(payload.length < WorkspaceLimits.MAX_BYTES)
        val error = assertThrows(WorkspaceJsonException::class.java) { StrictJson(payload).validate() }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }

    @Test fun wideObjectsAreRejectedBeforeUnboundedKeyAllocation() {
        val payload = "{" + (0 until 100_000).joinToString(",") { "\"$it\":0" } + "}"
        val error = assertThrows(WorkspaceJsonException::class.java) { StrictJson(payload).validate() }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }
}
