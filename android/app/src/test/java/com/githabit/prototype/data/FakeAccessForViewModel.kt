package com.githabit.prototype.data

import java.io.File
import java.io.IOException

/** Test-only, byte-level fault injection. Never part of main/debug source sets. */
class FakeAccessForViewModel(initial: ByteArray? = null) : WorkspaceFileAccess {
    var stored: ByteArray? = initial?.copyOf()
    var failAllWrites = false
    var failReads = false
    var failPreservation = false
    var corruptCopy = false
    var failCopyReads = false
    var failLastGoodReads = false
    var reads = 0
        private set
    var writes = 0
        private set
    val preserved = mutableListOf<ByteArray>()
    val copyKinds = mutableListOf<CopyKind>()
    private val copies = mutableMapOf<File, ByteArray>()
    val retainedCopies: List<ByteArray> get() = copies.values.map { it.copyOf() }
    val writeActions = ArrayDeque<(ByteArray) -> Unit>()
    var beforeRead: (() -> Unit)? = null

    override fun readBytes(): ByteArray? {
        reads++
        beforeRead?.invoke()
        if (failReads) throw IOException("disk unavailable")
        return stored?.copyOf()
    }

    override fun writeBytes(bytes: ByteArray) {
        writes++
        if (failAllWrites) throw IOException("write failed")
        if (writeActions.isNotEmpty()) writeActions.removeFirst()(bytes.copyOf())
        else stored = bytes.copyOf()
    }

    override fun preserveCopy(bytes: ByteArray, kind: CopyKind): File? {
        if (failPreservation) return null
        val content = if (corruptCopy) byteArrayOf(0) else bytes.copyOf()
        preserved += content
        copyKinds += kind
        val name = if (kind == CopyKind.LAST_GOOD) "last-good.json" else "recovery-${preserved.size}.json"
        return File(name).also { copies[it] = content }
    }

    override fun readCopy(file: File): ByteArray {
        if (failCopyReads) throw IOException("copy unreadable")
        return copies.getValue(file).copyOf()
    }

    override fun preservedBytes(): ByteArray? = preserved.lastOrNull()?.copyOf()

    override fun lastGoodBytes(): ByteArray? {
        if (failLastGoodReads) throw IOException("last-good copy unavailable")
        return copies[File("last-good.json")]?.copyOf()
    }
}
