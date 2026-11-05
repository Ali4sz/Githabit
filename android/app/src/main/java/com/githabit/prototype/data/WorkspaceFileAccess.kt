package com.githabit.prototype.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

enum class CopyKind { LAST_GOOD, RECOVERY }

/**
 * Byte-level storage seam. [WorkspaceStore] owns all synchronization and verification on
 * top of this; tests inject failing implementations to exercise load/write/recovery
 * paths without a device. No other component may open the workspace file directly.
 */
interface WorkspaceFileAccess {
    /** All access objects for the same physical file must share this process-wide lock. */
    val ownershipLock: Any get() = this

    /** Raw stored bytes, or null when no file exists yet. Throws on read failure. */
    fun readBytes(): ByteArray?

    /** Atomically replace the stored file. Throws on failure. */
    fun writeBytes(bytes: ByteArray)

    /** Independently named copy. The owner must read it back before trusting it. */
    fun preserveCopy(bytes: ByteArray, kind: CopyKind = CopyKind.RECOVERY): File?

    fun readCopy(file: File): ByteArray

    /** Bytes of the most recent preserved copy, if any. */
    fun preservedBytes(): ByteArray?

    /** Independently retained previous file, including across process restart. */
    fun lastGoodBytes(): ByteArray?
}

/**
 * Real implementation over Android's [AtomicFile]. The file domain is excluded from cloud
 * backup and device transfer, so preservation copies in filesDir are excluded as well.
 */
class AtomicFileAccess internal constructor(private val dir: File) : WorkspaceFileAccess {
    constructor(context: Context) : this(context.filesDir)

    private val file = AtomicFile(File(dir, FILE_NAME))
    private val copies = WorkspaceCopies(dir)
    // This app has one local workspace. A process-wide lock also covers overlapping
    // Activity/ViewModel lifetimes without filesystem calls during construction.
    override val ownershipLock: Any = processStorageLock

    override fun readBytes(): ByteArray? = try {
        file.openRead().use { it.readBytes() }
    } catch (error: FileNotFoundException) {
        // openRead also throws for permissions, a directory, failed recovery, etc.
        // File.exists() alone cannot distinguish absence from access errors.
        if (workspaceFilesMissing(dir)) null else throw error
    }

    override fun writeBytes(bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun preserveCopy(bytes: ByteArray, kind: CopyKind): File? = copies.write(bytes, kind)

    override fun readCopy(file: File): ByteArray = file.readBytes()

    override fun preservedBytes(): ByteArray? = copies.latest()

    override fun lastGoodBytes(): ByteArray? = try {
        Files.readAllBytes(File(dir, "githabit-last-good.json").toPath())
    } catch (_: NoSuchFileException) { null }

    companion object {
        const val FILE_NAME = "githabit-workspace.json"
        private val processStorageLock = Any()
    }
}

/** Fail closed if a storage path cannot be inspected. Also detect AtomicFile sidecars. */
internal fun workspaceFilesMissing(dir: File): Boolean {
    val directory = Files.readAttributes(dir.toPath(), BasicFileAttributes::class.java)
    if (!directory.isDirectory) throw IOException("Workspace storage is not a directory.")
    return listOf("", ".bak", ".new").all { suffix ->
        try {
            Files.readAttributes(File(dir, AtomicFileAccess.FILE_NAME + suffix).toPath(), BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS)
            false
        } catch (_: NoSuchFileException) { true }
    }
}

/** Pure file operations so uniqueness, fsync/read-back, and retention can be fault-tested on JVM. */
internal class WorkspaceCopies(private val dir: File) {
    fun write(bytes: ByteArray, kind: CopyKind): File? = try {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val prefix = "githabit-recovery-$digest-"
        val retained = if (kind == CopyKind.RECOVERY) dir.listFiles { file -> file.name.startsWith(prefix) }
            ?.firstOrNull { runCatching { it.readBytes().contentEquals(bytes) }.getOrDefault(false) } else null
        if (retained != null) retained else {
            val copy = File.createTempFile(prefix, ".json", dir)
            FileOutputStream(copy).use { output -> output.write(bytes); output.fd.sync() }
            if (!copy.readBytes().contentEquals(bytes)) throw IOException("Recovery copy verification failed.")
            if (kind == CopyKind.LAST_GOOD) {
                val lastGood = File(dir, "githabit-last-good.json")
                Files.move(copy.toPath(), lastGood.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                lastGood
            } else copy
        }
    } catch (_: IOException) { null } catch (_: SecurityException) { null }

    fun latest(): ByteArray? {
        val files = dir.listFiles() ?: throw IOException("Recovery storage could not be inspected.")
        val copies = files.filter { it.name.startsWith("githabit-recovery-") || it.name == "githabit-last-good.json" }
        // If the primary file vanished, do not pretend this is a new installation.
        val latest = copies.maxByOrNull { it.lastModified() } ?: return null
        return latest.readBytes()
    }
}
