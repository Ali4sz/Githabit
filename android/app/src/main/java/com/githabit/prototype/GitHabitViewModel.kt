package com.githabit.prototype

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.data.WorkspaceStore
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Destination { TODAY, REPOS, INSIGHTS, ACTIVITY, SETTINGS, DETAIL }
sealed interface Overlay {
    data class RepoForm(val id: String? = null) : Overlay
    data class LogForm(val habitId: String, val commitId: String? = null) : Overlay
    data class Day(val date: LocalDate, val habitId: String? = null) : Overlay
    data object Guide : Overlay
}
data class Notice(val message: String, val undoId: String? = null)

class GitHabitViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkspaceStore(application)
    private val mutation = Mutex()
    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    private val undos = linkedMapOf<String, (Workspace) -> Workspace>()
    private var backStack = emptyList<Pair<Destination, String?>>()
    val notices = noticeChannel.receiveAsFlow()

    var workspace by mutableStateOf(Workspace())
        private set
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var today by mutableStateOf(LocalDate.now())
        private set
    var destination by mutableStateOf(Destination.TODAY)
        private set
    var selectedRepoId by mutableStateOf<String?>(null)
        private set
    var overlay by mutableStateOf<Overlay?>(null)
    var onboardingStep by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            try { workspace = withContext(Dispatchers.IO) { store.read() } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.send(Notice("Saved data could not be read. A fresh workspace is open."))
            } finally { ready = true }
        }
        viewModelScope.launch {
            while (true) { delay(30_000); refreshDate() }
        }
    }

    fun refreshDate() { today = LocalDate.now() }

    fun navigate(next: Destination, repoId: String? = null, mainTab: Boolean = false) {
        overlay = null
        if (next == destination && repoId == selectedRepoId) return
        backStack = if (mainTab) {
            if (next == Destination.TODAY) emptyList() else listOf(Destination.TODAY to null)
        } else backStack + (destination to selectedRepoId)
        destination = next
        selectedRepoId = repoId
    }

    fun back() {
        if (overlay != null) { overlay = null; return }
        val previous = backStack.lastOrNull() ?: (Destination.TODAY to null)
        backStack = backStack.dropLast(1)
        destination = previous.first
        selectedRepoId = previous.second
    }

    private fun update(
        message: String? = null,
        dismiss: Boolean = false,
        undo: ((Workspace, Workspace) -> ((Workspace) -> Workspace))? = null,
        after: () -> Unit = {},
        transform: (Workspace) -> Workspace,
    ) {
        viewModelScope.launch {
            mutation.withLock {
                busy = true
                try {
                    refreshDate()
                    val before = workspace
                    val next = transform(before)
                    withContext(Dispatchers.IO) { store.write(next) }
                    workspace = next
                    if (dismiss) overlay = null
                    val undoId = undo?.let {
                        val id = UUID.randomUUID().toString()
                        undos[id] = it(before, next)
                        if (undos.size > 20) undos.remove(undos.keys.first())
                        id
                    }
                    after()
                    message?.let { noticeChannel.send(Notice(it, undoId)) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    noticeChannel.send(Notice(if (error is IllegalArgumentException) error.message ?: "Check your entry."
                        else "Could not save your change. Please try again."))
                } finally { busy = false }
            }
        }
    }

    fun signIn() = update(after = { onboardingStep = 0; navigate(Destination.TODAY, mainTab = true) }) {
        it.copy(signedIn = true)
    }

    fun explore() = update(after = { navigate(Destination.TODAY, mainTab = true) }) {
        if (it.habits.isNotEmpty() || it.onboarded) it.copy(signedIn = true, onboarded = true)
        else SampleWorkspace.create().copy(darkTheme = it.darkTheme)
    }

    fun startEmpty() = update(after = {
        navigate(Destination.TODAY, mainTab = true)
        overlay = Overlay.RepoForm()
    }) { it.copy(signedIn = true, onboarded = true) }

    fun saveHabit(id: String?, draft: HabitDraft) = update(
        message = if (id == null) "Your new repo is ready. Start with a small commit." else "Repo updated. Keep growing.",
        dismiss = true,
    ) { if (id == null) it.createHabit(draft, today) else it.editHabit(id, draft, today) }

    fun saveCommit(habitId: String, commitId: String?, amount: Double, note: String) {
        val id = commitId ?: UUID.randomUUID().toString()
        update(message = if (commitId == null) "Progress committed. That’s a little win." else "Commit corrected.",
            dismiss = true, undo = { before, _ ->
                val old = before.commits.find { it.id == id }
                val revert: (Workspace) -> Workspace = { current -> if (old == null) current.removeCommit(id)
                    else current.copy(commits = current.commits.map { if (it.id == id) old else it }) }
                revert
            }) { if (commitId == null) it.addCommit(habitId, amount, today, note, id) else it.editCommit(id, amount, note) }
    }

    fun complete(habitId: String) {
        val id = UUID.randomUUID().toString()
        update(message = "Daily goal reached. A little win worth keeping.", undo = { _, _ ->
            { current -> current.removeCommit(id) }
        }) { it.completeGoal(habitId, today, id) }
    }

    fun removeCommit(id: String) = update(message = "Commit removed.", dismiss = true, undo = { before, _ ->
        val old = before.commits.first { it.id == id }
        val revert: (Workspace) -> Workspace = { current ->
            if (current.commits.any { it.id == id }) current else current.copy(commits = current.commits + old)
        }
        revert
    }) { it.removeCommit(id) }

    fun status(id: String, status: RepoStatus) {
        val day = LocalDate.now()
        update(message = "Repo ${if (status == RepoStatus.ACTIVE) "resumed" else status.name.lowercase()}. Your history is safe.",
            undo = { before, _ ->
                val oldStatus = requireNotNull(before.habits.first { it.id == id }.versionOn(day)).status
                { current -> current.changeStatus(id, oldStatus, day) }
            }) { it.changeStatus(id, status, today) }
    }

    fun undo(id: String) { undos.remove(id)?.let { update(message = "Undone. You’re all set.", transform = it) } }
    fun setTheme(dark: Boolean) = update { it.copy(darkTheme = dark) }
    fun saveProfile(name: String, bio: String) = update(message = "Your workspace feels a little more like you.") {
        require(name.trim().length in 1..40) { "Enter a name of 1–40 characters." }
        require(bio.length <= 160) { "Keep your note within 160 characters." }
        it.copy(name = name.trim(), bio = bio.trim())
    }
    fun signOut() = update(after = { undos.clear(); overlay = null; navigate(Destination.TODAY, mainTab = true) }) {
        it.copy(signedIn = false)
    }
    fun reset() = update(after = {
        undos.clear(); overlay = null; onboardingStep = 0; navigate(Destination.TODAY, mainTab = true)
    }) { Workspace() }

    fun export(uri: Uri) {
        val snapshot = workspace
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt"))
                        .bufferedWriter().use { it.write(WorkspaceJson.encode(snapshot.copy(signedIn = false))) }
                }
                noticeChannel.send(Notice("Your workspace has been exported."))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.send(Notice("Could not export the workspace. Please try another location."))
            }
        }
    }
}
