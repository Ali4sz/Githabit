package com.githabit.prototype.data

import android.content.Context
import android.util.AtomicFile
import com.githabit.prototype.domain.Workspace
import java.io.File
import java.io.FileNotFoundException

/** The ViewModel calls this on Dispatchers.IO, and serializes writes with a mutex. */
class WorkspaceStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "githabit-workspace.json"))

    fun read(): Workspace = try {
        file.openRead().bufferedReader().use { WorkspaceJson.decode(it.readText()) }
    } catch (_: FileNotFoundException) {
        Workspace()
    }

    fun write(state: Workspace) {
        val bytes = WorkspaceJson.encode(state).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
