package com.githabit.prototype.data

import java.io.File
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFileAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun genuinelyMissingRequiresAccessibleDirectoryAndNoSidecars() {
        assertTrue(workspaceFilesMissing(temporary.root))
        for (suffix in listOf("", ".bak", ".new")) {
            val file = temporary.newFile(AtomicFileAccess.FILE_NAME + suffix)
            assertFalse(workspaceFilesMissing(temporary.root))
            assertTrue(file.delete())
        }
    }

    @Test fun nonDirectoryAndUninspectableStorageAreNotTreatedAsMissing() {
        val file = temporary.newFile("not-a-directory")
        assertThrows(IOException::class.java) { workspaceFilesMissing(file) }
        assertThrows(IOException::class.java) { workspaceFilesMissing(File(temporary.root, "absent-directory")) }
        temporary.newFolder(AtomicFileAccess.FILE_NAME)
        assertFalse(workspaceFilesMissing(temporary.root))
    }

    @Test fun recoveryCopiesRetainExactBytesAndRepeatedRetriesReuseVerifiedContent() {
        val copies = WorkspaceCopies(temporary.root)
        val raw = byteArrayOf(0, 0xff.toByte(), 0xc0.toByte(), 123)
        val first = requireNotNull(copies.write(raw, CopyKind.RECOVERY))
        val other = raw + byteArrayOf(42)
        val second = requireNotNull(copies.write(other, CopyKind.RECOVERY))
        assertNotEquals(first, second)
        assertArrayEquals(raw, first.readBytes())
        assertArrayEquals(other, second.readBytes())
        assertEquals(first, copies.write(raw, CopyKind.RECOVERY))
        assertEquals(2, temporary.root.listFiles()!!.size)
    }

    @Test fun rotatingLastGoodDoesNotDeletePermanentRecoveryCopies() {
        val copies = WorkspaceCopies(temporary.root)
        val recovery = requireNotNull(copies.write("original".toByteArray(), CopyKind.RECOVERY))
        val first = requireNotNull(copies.write("previous".toByteArray(), CopyKind.LAST_GOOD))
        val second = requireNotNull(copies.write("next".toByteArray(), CopyKind.LAST_GOOD))
        assertEquals(first, second)
        assertEquals("next", second.readText())
        assertEquals("original", recovery.readText())
        assertEquals(2, temporary.root.listFiles()!!.size)
    }

    @Test fun failedCopyToInvalidDirectoryReturnsNoFile() {
        val copies = WorkspaceCopies(temporary.newFile("not-a-directory"))
        assertNull(copies.write("data".toByteArray(), CopyKind.RECOVERY))
    }

    @Test fun streamingCopiesRetainFullContentsAndDeduplicateOnlyVerifiedMatches() {
        val copies = WorkspaceCopies(temporary.root)
        val bytes = byteArrayOf(0, 0xff.toByte(), 0xc0.toByte(), 42)
        val first = requireNotNull(copies.writeStream { ByteArrayInputStream(bytes) })
        assertArrayEquals(bytes, first.readBytes())
        assertEquals(first, copies.writeStream { ByteArrayInputStream(bytes) })
        first.writeText("damaged archive")
        val repaired = requireNotNull(copies.writeStream { ByteArrayInputStream(bytes) })
        assertNotEquals(first, repaired)
        assertEquals("damaged archive", first.readText())
        assertArrayEquals(bytes, repaired.readBytes())
    }

    @Test fun oversizedPhysicalReadRetainsAnEntireExportableFileInsteadOfAPrefix() {
        val source = temporary.newFile("oversized.json")
        java.io.RandomAccessFile(source, "rw").use {
            it.setLength(WorkspaceLimits.MAX_BYTES.toLong() + 1)
            it.seek(WorkspaceLimits.MAX_BYTES.toLong()); it.write(42)
        }
        val copies = WorkspaceCopies(temporary.root)
        val error = assertThrows(WorkspaceFileCapacityException::class.java) { copies.readBounded { source.inputStream() } }
        val recovery = requireNotNull(error.recoveryCopy)
        assertEquals(source.length(), recovery.length())
        java.io.RandomAccessFile(recovery, "r").use { it.seek(WorkspaceLimits.MAX_BYTES.toLong()); assertEquals(42, it.read()) }
        val repeated = assertThrows(WorkspaceFileCapacityException::class.java) { copies.readBounded { source.inputStream() } }
        assertEquals(recovery, repeated.recoveryCopy)
        assertEquals(WorkspaceLimits.MAX_BYTES.toLong() + 1, source.length())
    }

    @Test fun sidecarEvidenceIsRetainedBeforeAnyAtomicRecoveryRead() {
        val copies = WorkspaceCopies(temporary.root)
        val inputs = listOf("", ".bak", ".new").mapIndexed { index, suffix ->
            temporary.newFile(AtomicFileAccess.FILE_NAME + suffix).apply { writeText("physical $index") }
        }
        preserveAtomicSidecars(temporary.root, copies)
        val retained = temporary.root.listFiles()!!.filter { it.name.startsWith("githabit-recovery-") }
        assertEquals(inputs.map { it.readText() }.toSet(), retained.map { it.readText() }.toSet())
        assertEquals(listOf("physical 0", "physical 1", "physical 2"), inputs.map { it.readText() })
    }

    @Test fun failedStreamingStagingCannotShadowACompleteCopyAndIsCleanedUp() {
        val copies = WorkspaceCopies(temporary.root)
        val complete = requireNotNull(copies.writeStream { ByteArrayInputStream("complete".toByteArray()) })
        var opens = 0
        assertNull(copies.writeStream {
            if (opens++ == 0) ByteArrayInputStream("intended source".toByteArray())
            else object : java.io.InputStream() {
                var count = 0
                override fun read(): Int = if (count++ < 4) 42 else throw IOException("interrupted")
            }
        })
        assertArrayEquals(complete.readBytes(), copies.latest())
        assertFalse(temporary.root.listFiles()!!.any { it.name.endsWith(".partial") })
        temporary.newFile("githabit-recovery-staging-leftover.partial").apply {
            writeText("incomplete"); setLastModified(complete.lastModified() + 10_000)
        }
        assertArrayEquals("complete".toByteArray(), copies.latest())
    }

    @Test fun verifiedArchiveReuseDoesNotRequireAnotherWritableStagingFile() {
        val bytes = "retained original".toByteArray()
        val retained = requireNotNull(WorkspaceCopies(temporary.root).writeStream { ByteArrayInputStream(bytes) })
        val noStagingSpace = WorkspaceCopies(temporary.root) { throw IOException("no staging space") }
        assertEquals(retained, noStagingSpace.writeStream { ByteArrayInputStream(bytes) })
        assertNull(noStagingSpace.writeStream { ByteArrayInputStream("different".toByteArray()) })
        assertArrayEquals(bytes, retained.readBytes())
    }

    @Test fun matchingReadsReuseOnlyCompleteExactSnapshotsIncludingEmptyFiles() {
        for (size in listOf(0, 1, 8191, 8192, 8193, 24_613)) {
            val expected = ByteArray(size) { (it * 31).toByte() }
            assertSame(expected, WorkspaceLimits.readMatchingBounded(expected.inputStream(), size.toLong(), listOf(expected)))
            val ordinary = WorkspaceLimits.readBounded(expected.inputStream(), knownSize = size.toLong())
            assertNotSame(expected, ordinary)
            assertArrayEquals(expected, ordinary)
        }
    }

    @Test fun mismatchingReadsRetainActualPrefixChunkAndSuffixNotFirstCandidatesPrefix() {
        val actual = ByteArray(24_613) { (it * 31).toByte() }
        val first = actual.copyOf().also { it[1]++ }
        val second = actual.copyOf().also { it[20_000]++ }
        val result = WorkspaceLimits.readMatchingBounded(actual.inputStream(), actual.size.toLong(), listOf(first, second))
        assertNotSame(first, result)
        assertNotSame(second, result)
        assertArrayEquals(actual, result)
        for (position in listOf(0, 8191, 8192, actual.lastIndex)) {
            val changed = actual.copyOf().also { it[position]++ }
            assertArrayEquals(changed, WorkspaceLimits.readMatchingBounded(changed.inputStream(), changed.size.toLong(), listOf(actual)))
        }
        assertSame(actual, WorkspaceLimits.readMatchingBounded(actual.inputStream(), actual.size.toLong(), listOf(first, actual)))
    }

    @Test fun matchingReadsRejectChangedLengthAndCapacityWithoutReturningAliasOrPrefix() {
        val expected = byteArrayOf(0, 0xff.toByte(), 42)
        for (actual in listOf(expected.copyOf(2), expected + byteArrayOf(1))) {
            assertThrows(IOException::class.java) {
                WorkspaceLimits.readMatchingBounded(actual.inputStream(), expected.size.toLong(), listOf(expected))
            }
            val result = WorkspaceLimits.readMatchingBounded(actual.inputStream(), actual.size.toLong(), listOf(expected))
            assertNotSame(expected, result)
            assertArrayEquals(actual, result)
        }
        val error = assertThrows(WorkspaceJsonException::class.java) {
            WorkspaceLimits.readMatchingBounded(expected.inputStream(), WorkspaceLimits.MAX_BYTES.toLong() + 1, listOf(expected))
        }
        assertEquals(WorkspaceJsonFailure.CAPACITY, error.kind)
    }

    @Test fun knownSizeReadsExposeOnlySmallArraysAndHandleShortZeroAndUnsignedReads() {
        val expected = ByteArray(24_613) { (it * 31).toByte() }
        fun input() = object : InputStream() {
            var position = 0
            var calls = 0
            override fun read(): Int = if (position == expected.size) -1 else expected[position++].toInt() and 255
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                assertTrue(bytes.size <= WorkspaceLimits.IO_BUFFER_BYTES)
                if (++calls % 3 == 0) return 0
                val count = minOf(7, length, expected.size - position)
                if (count == 0) return -1
                expected.copyInto(bytes, offset, position, position + count)
                position += count
                return count
            }
        }
        assertArrayEquals(expected, WorkspaceLimits.readBounded(input(), knownSize = expected.size.toLong()))
        assertSame(expected, WorkspaceLimits.readMatchingBounded(input(), expected.size.toLong(), listOf(expected)))
        assertArrayEquals(expected, WorkspaceLimits.readMatchingBounded(input(), expected.size.toLong(), listOf(ByteArray(expected.size))))
    }

    @Test fun fileCopyMatchingUsesOneOpenAndReturnsActualDifferentFormatting() {
        val expected = "{\"version\":2}".toByteArray()
        val actual = "{ \"version\" : 2 }".toByteArray()
        val file = temporary.newFile().apply { writeBytes(expected) }
        val copies = WorkspaceCopies(temporary.root)
        var opens = 0
        assertSame(expected, copies.readBounded(listOf(expected)) { opens++; file.inputStream() })
        assertEquals(1, opens)
        file.writeBytes(actual)
        assertArrayEquals(actual, copies.readBounded(listOf(expected)) { opens++; file.inputStream() })
        assertEquals(2, opens)
    }

    @Test fun snapshotWritesUseSmallScratchArraysAndNeverExposeOrMutateSource() {
        val expected = ByteArray(24_613) { (it * 31).toByte() }
        val snapshot = expected.copyOf()
        val received = ByteArrayOutputStream()
        val output = object : OutputStream() {
            override fun write(byte: Int) { fail("Expected bounded bulk writes") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                assertNotSame(snapshot, bytes)
                assertTrue(bytes.size <= WorkspaceLimits.IO_BUFFER_BYTES)
                received.write(bytes, offset, length)
                bytes.fill(0)
            }
        }
        output.writeSnapshot(snapshot)
        assertArrayEquals(expected, snapshot)
        assertArrayEquals(expected, received.toByteArray())
        output.writeSnapshot(byteArrayOf())
        val error = IOException("disk full")
        assertSame(error, assertThrows(IOException::class.java) {
            object : OutputStream() { override fun write(byte: Int) { throw error } }.writeSnapshot(snapshot)
        })
    }
}
