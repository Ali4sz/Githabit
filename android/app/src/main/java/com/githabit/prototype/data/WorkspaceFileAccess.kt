package com.githabit.prototype.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

enum class CopyKind { LAST_GOOD, RECOVERY }

/** Oversized physical input is never returned as a truncated byte array. */
class WorkspaceFileCapacityException(val recoveryCopy: File?) : IOException("Workspace file exceeds supported capacity.")

/** Physical evidence retained even when AtomicFile cannot open a primary (e.g. only .new). */
class WorkspaceFileReadException(val recoveryCopy: File?, val sidecarOnly: Boolean, cause: IOException) :
    IOException("Workspace could not be opened; physical recovery evidence was retained.", cause)

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

    /** Raw recovery export may exceed the JSON acceptance limit; never decode or truncate it. */
    fun copyRecoveryTo(file: File, output: OutputStream) { output.write(readCopy(file)) }

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

    override fun readBytes(): ByteArray? {
        // AtomicFile may delete/replace sidecars while opening. Retain all physical
        // evidence before allowing that recovery, including oversized originals.
        val evidence = preserveAtomicSidecars(dir, copies)
        return try {
            copies.readBounded { file.openRead() }
        } catch (error: FileNotFoundException) {
            // Never treat sidecar-only interrupted first writes as fresh installations.
            if (workspaceFilesMissing(dir)) null
            else if (evidence == null) throw error
            else throw WorkspaceFileReadException(evidence, workspacePhysicalFiles(dir)
                .map { it.name } == listOf(FILE_NAME + ".new"), error)
        } catch (error: WorkspaceFileCapacityException) { throw error }
        catch (error: IOException) {
            if (evidence != null) throw WorkspaceFileReadException(evidence, false, error)
            throw error
        }
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

    override fun readCopy(file: File): ByteArray = file.inputStream().use { WorkspaceLimits.readBounded(it) }

    override fun copyRecoveryTo(file: File, output: OutputStream) {
        file.inputStream().use { it.copyTo(output) }
    }

    override fun preservedBytes(): ByteArray? = copies.latest()

    override fun lastGoodBytes(): ByteArray? = try {
        copies.readBounded { Files.newInputStream(File(dir, "githabit-last-good.json").toPath()) }
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

private fun workspacePhysicalFiles(dir: File): List<File> {
    val paths = listOf("", ".bak", ".new").map { File(dir, AtomicFileAccess.FILE_NAME + it) }
    return paths.filter { path ->
        try { Files.readAttributes(path.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS); true }
        catch (_: NoSuchFileException) { false }
    }
}

internal fun preserveAtomicSidecars(dir: File, copies: WorkspaceCopies): File? {
    val present = workspacePhysicalFiles(dir)
    var evidence: File? = null
    if (present.any { it.name != AtomicFileAccess.FILE_NAME }) {
        for (path in present) {
            evidence = copies.writeStream { path.inputStream() }
                ?: throw IOException("AtomicFile recovery evidence could not be preserved.")
        }
    }
    return evidence
}

/** Pure file operations so uniqueness, fsync/read-back, and retention can be fault-tested on JVM. */
internal class WorkspaceCopies(
    private val dir: File,
    private val createStaging: (File) -> File = { File.createTempFile("githabit-preservation-", ".partial", it) },
) {
    fun readBounded(open: () -> InputStream): ByteArray = try {
        open().use { WorkspaceLimits.readBounded(it) }
    } catch (error: WorkspaceJsonException) {
        if (error.kind != WorkspaceJsonFailure.CAPACITY) throw error
        throw WorkspaceFileCapacityException(writeStream(open))
    }

    /** Streaming preservation with full length/hash read-back, independent of JSON capacity. */
    fun writeStream(open: () -> InputStream): File? {
        var staging: File? = null
        return try {
            // Reuse a complete verified archive before requiring any additional disk space.
            val expected = fingerprint(open)
            val prefix = "githabit-recovery-${expected.second}-"
            val retained = dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
                ?.firstOrNull { runCatching { fingerprint { it.inputStream() } == expected }.getOrDefault(false) }
            if (retained != null) return retained
            val copy = createStaging(dir)
            staging = copy
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileOutputStream(copy).use { output ->
                open().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count); digest.update(buffer, 0, count); size += count
                    }
                }
                output.fd.sync()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if ((size to hash) != expected || fingerprint { copy.inputStream() } != expected)
                throw IOException("Recovery copy verification failed or source changed.")
            val target = File(dir, prefix + copy.name.removePrefix("githabit-preservation-").removeSuffix(".partial") + ".json")
            Files.move(copy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            staging = null
            target
        } catch (_: IOException) { null } catch (_: SecurityException) { null }
        finally {
            // A failed partial is not a verified snapshot. Remove only our staging
            // file; never remove source evidence or a previously retained archive.
            runCatching { staging?.delete() }
        }
    }

    private fun fingerprint(open: () -> InputStream): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        open().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count); size += count
            }
        }
        return size to digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun write(bytes: ByteArray, kind: CopyKind): File? {
        if (kind == CopyKind.RECOVERY) return writeStream { bytes.inputStream() }
        var staging: File? = null
        return try {
            val copy = createStaging(dir)
            staging = copy
            FileOutputStream(copy).use { output -> output.write(bytes); output.fd.sync() }
            if (!copy.inputStream().use { WorkspaceLimits.readBounded(it) }.contentEquals(bytes)) throw IOException("Recovery copy verification failed.")
            val lastGood = File(dir, "githabit-last-good.json")
            Files.move(copy.toPath(), lastGood.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            staging = null
            lastGood
        } catch (_: IOException) { null } catch (_: SecurityException) { null }
        finally { runCatching { staging?.delete() } }
    }

    fun latest(): ByteArray? {
        val files = dir.listFiles() ?: throw IOException("Recovery storage could not be inspected.")
        val copies = files.filter { (it.name.startsWith("githabit-recovery-") && it.name.endsWith(".json")) || it.name == "githabit-last-good.json" }
        // If the primary file vanished, do not pretend this is a new installation.
        val latest = copies.maxByOrNull { it.lastModified() } ?: run {
            if (files.any { it.name.endsWith(".partial") && (it.name.startsWith("githabit-preservation-") || it.name.startsWith("githabit-recovery-")) })
                throw IOException("Only incomplete recovery evidence remains.")
            return null
        }
        return readBounded { latest.inputStream() }
    }
}
