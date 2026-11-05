package com.githabit.prototype.data

import java.io.File
import java.io.IOException
import java.io.ByteArrayInputStream
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
}
