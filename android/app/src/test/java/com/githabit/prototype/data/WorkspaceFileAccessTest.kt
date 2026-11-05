package com.githabit.prototype.data

import java.io.File
import java.io.IOException
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
}
