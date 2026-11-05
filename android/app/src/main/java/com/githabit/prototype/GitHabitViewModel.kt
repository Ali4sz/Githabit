package com.githabit.prototype

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.githabit.prototype.data.AtomicFileAccess
import com.githabit.prototype.data.UnreadableKind
import com.githabit.prototype.data.WorkspaceFileAccess
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.data.WorkspaceLoadResult
import com.githabit.prototype.data.WorkspaceStore
import com.githabit.prototype.data.WriteOutcome
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

/** App-level storage state. Ordinary mutations are permitted only in [Ready]. */
sealed interface WorkspaceLoad {
    data object Loading : WorkspaceLoad
    data class Ready(val isNew: Boolean) : WorkspaceLoad
    data class Recovery(val kind: UnreadableKind, val reason: String, val preserved: Boolean) : WorkspaceLoad
}

class GitHabitViewModel(
    /** Application context, for document-provider export only; null in fault tests. */
    internal val appContext: Context?,
    internal val store: WorkspaceStore,
    internal val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    tickerScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutation = Mutex()
    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    private val undos = linkedMapOf<String, (Workspace) -> Workspace>()
    private var backStack = emptyList<Pair<Destination, String?>>()
    val notices = noticeChannel.receiveAsFlow()

    var workspace by mutableStateOf(Workspace())
        private set
    var load by mutableStateOf<WorkspaceLoad>(WorkspaceLoad.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var today by mutableStateOf(clock.today())
        private set
    var destination by mutableStateOf(Destination.TODAY)
        private set
    var selectedRepoId by mutableStateOf<String?>(null)
        private set
    var overlay by mutableStateOf<Overlay?>(null)
    var onboardingStep by mutableIntStateOf(0)
    var workspaceSession by mutableIntStateOf(0)
        private set

    constructor(application: Application) : this(
        application,
        WorkspaceStore(AtomicFileAccess(application)),
        SystemAppClock(),
    )

    /** Test seam: inject storage access without a real application or files. */
    internal constructor(
        access: WorkspaceFileAccess,
        clock: AppClock = SystemAppClock(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        tickerScope: CoroutineScope? = null,
    ) : this(null, WorkspaceStore(access), clock, ioDispatcher, tickerScope)

    companion object {
        /** Production factory wiring the real application, storage, and clock. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                GitHabitViewModel(app, WorkspaceStore(AtomicFileAccess(requireNotNull(app))), SystemAppClock())
            }
        }
    }

    // Production updates stay on Main and share the ViewModel lifetime. Tests inject
    // backgroundScope so advancing foreground work does not run an endless ticker.
    private val ticker = (tickerScope ?: viewModelScope).launch {
        while (true) { delay(30_000); refreshDate() }
    }

    init {
        viewModelScope.launch {
            mutation.withLock {
                applyLoadResult(withContext(ioDispatcher) { store.load() })
            }
        }
    }

    override fun onCleared() {
        ticker.cancel()
    }

    private fun applyLoadResult(result: WorkspaceLoadResult) {
        when (result) {
            is WorkspaceLoadResult.Missing -> {
                workspace = Workspace()
                load = WorkspaceLoad.Ready(isNew = true)
            }
            is WorkspaceLoadResult.Ready -> {
                // Reconciliation is not a replay. Changed data invalidates stale inverses/routes.
                if (load is WorkspaceLoad.Recovery && workspace != result.workspace) clearTransientState()
                workspace = result.workspace
                refreshDate()
                load = WorkspaceLoad.Ready(isNew = false)
            }
            is WorkspaceLoadResult.Unreadable -> {
                // Keep the last committed snapshot for reconciliation; ordinary UI is gated.
                load = WorkspaceLoad.Recovery(result.kind, result.reason, result.preserved)
            }
        }
    }

    /** Whether a copy of the unreadable/last-good data can be saved from the recovery screen. */
    val canSaveRecoveryCopy: Boolean
        get() = load is WorkspaceLoad.Recovery && store.hasRecoveryBytes

    /** Re-read the stored file as-is; reconciles rather than guessing an unknown outcome. */
    fun retryLoad() {
        if (busy || load !is WorkspaceLoad.Recovery) return
        busy = true
        viewModelScope.launch {
            try {
                mutation.withLock {
                    if (load is WorkspaceLoad.Recovery) applyLoadResult(withContext(ioDispatcher) { store.reload() })
                }
            } finally { busy = false }
        }
    }

    /** Confirmed start-over from recovery: preserve, then replace with verified empty data. */
    fun startOverFromRecovery() = replaceWithEmpty(fromRecovery = true)

    private fun clearTransientState() {
        workspaceSession++
        undos.clear()
        overlay = null
        backStack = emptyList()
        destination = Destination.TODAY
        selectedRepoId = null
        onboardingStep = 0
    }

    private fun replaceWithEmpty(fromRecovery: Boolean) {
        if (busy || (if (fromRecovery) load !is WorkspaceLoad.Recovery else load !is WorkspaceLoad.Ready)) return
        busy = true
        viewModelScope.launch {
            mutation.withLock {
                try {
                    if (if (fromRecovery) load !is WorkspaceLoad.Recovery else load !is WorkspaceLoad.Ready) return@withLock
                    val fresh = Workspace()
                    withContext(NonCancellable) {
                        if (persist(fresh, replacement = true)) {
                            clearTransientState()
                            load = WorkspaceLoad.Ready(isNew = true)
                            noticeChannel.trySend(Notice("Workspace reset. Any previous data was preserved on this device before replacement."))
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    noticeChannel.trySend(Notice("Could not prepare the reset. Nothing was replaced."))
                } finally { busy = false }
            }
        }
    }

    fun refreshDate() { today = clock.today() }

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
        retryUndoId: String? = null,
        transform: (Workspace) -> Workspace,
    ) {
        if (busy || load !is WorkspaceLoad.Ready) return
        busy = true
        viewModelScope.launch {
            mutation.withLock {
                try {
                    if (load !is WorkspaceLoad.Ready) return@withLock
                    refreshDate()
                    val before = workspace
                    val next = transform(before)
                    // A validated unchanged form is finished, but no-op commands still
                    // persist nothing, announce nothing, and create no undo token.
                    if (next == before) {
                        if (dismiss) overlay = null
                        return@withLock
                    }
                    // Build the inverse before anything is written: a builder failure must
                    // not leave a persisted change behind a misleading failure notice.
                    val inverse = undo?.invoke(before, next)
                    // Complete reconciliation/publication even if the ViewModel is cleared
                    // during blocking disk IO. Cancellation must not hide a committed result.
                    withContext(NonCancellable) {
                        if (persist(next, retryUndoId = retryUndoId)) {
                            if (dismiss) overlay = null
                            val undoId = inverse?.let { registerUndo(it) }
                            try {
                                after()
                                message?.let { noticeChannel.trySend(Notice(it, undoId)) }
                            } catch (_: Exception) {
                                noticeChannel.trySend(Notice("Your change was saved, but the screen could not be updated. Reopen it."))
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    noticeChannel.trySend(Notice(if (error is IllegalArgumentException) error.message ?: "Check your entry."
                        else "Could not prepare your change. Please try again.", retryUndoId))
                } finally { busy = false }
            }
        }
    }

    /** Write, verify, publish on success; never publish on rollback/indeterminate. */
    private suspend fun persist(next: Workspace, replacement: Boolean = false, retryUndoId: String? = null): Boolean =
        when (val outcome = withContext(ioDispatcher) {
            if (replacement) store.replaceVerified(next) else store.write(next)
        }) {
        WriteOutcome.Committed -> {
            workspace = next
            load = WorkspaceLoad.Ready(isNew = false)
            true
        }
        WriteOutcome.RolledBack -> {
            noticeChannel.trySend(Notice("The change was not saved. The previous file was verified; you can retry.", retryUndoId))
            false
        }
        WriteOutcome.Indeterminate -> {
            load = WorkspaceLoad.Recovery(
                UnreadableKind.INDETERMINATE,
                "The result of your last change could not be verified on disk. GitHabit paused to protect your history.",
                preserved = store.recoveryPreserved,
            )
            noticeChannel.trySend(Notice("The save outcome is unknown. Re-read storage before making further changes."))
            false
        }
        WriteOutcome.Blocked -> {
            load = WorkspaceLoad.Recovery(UnreadableKind.IO, "Storage must be re-read before saving.", store.recoveryPreserved)
            false
        }
        is WriteOutcome.Rejected -> {
            noticeChannel.trySend(Notice(outcome.reason, retryUndoId))
            false
        }
    }

    private fun registerUndo(inverse: (Workspace) -> Workspace): String {
        val id = UUID.randomUUID().toString()
        undos[id] = inverse
        if (undos.size > 20) undos.remove(undos.keys.first())
        return id
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
        update(message = "Repo ${if (status == RepoStatus.ACTIVE) "resumed" else status.name.lowercase()}. Your history is safe.",
            undo = { before, _ ->
                val operationDate = today
                val oldStatus = requireNotNull(before.habits.first { it.id == id }.versionOn(operationDate)).status
                { current -> current.changeStatus(id, oldStatus, operationDate) }
            }) { it.changeStatus(id, status, today) }
    }

    fun undo(id: String) {
        undos[id]?.let { inverse ->
            update(message = "Undone. You’re all set.", retryUndoId = id,
                after = { undos.remove(id) }, transform = inverse)
        }
    }

    fun setTheme(dark: Boolean) = update { it.copy(darkTheme = dark) }

    fun saveProfile(name: String, bio: String) = update(message = "Your workspace feels a little more like you.") {
        require(name.trim().length in 1..40) { "Enter a name of 1–40 characters." }
        require(bio.length <= 160) { "Keep your note within 160 characters." }
        it.copy(name = name.trim(), bio = bio.trim())
    }

    fun signOut() = update(after = { undos.clear(); overlay = null; navigate(Destination.TODAY, mainTab = true) }) {
        it.copy(signedIn = false)
    }

    /** Confirmed reset: preserve a copy first, then replace with verified empty data. */
    fun reset() = replaceWithEmpty(fromRecovery = false)

    fun export(uri: Uri) {
        if (load !is WorkspaceLoad.Ready) return
        val snapshot = workspace
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    requireNotNull(requireNotNull(appContext).contentResolver.openOutputStream(uri, "wt"))
                        .bufferedWriter().use { it.write(WorkspaceJson.encode(snapshot.copy(signedIn = false))) }
                }
                noticeChannel.trySend(Notice("Your workspace has been exported."))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.trySend(Notice("Export failed. The chosen file may be incomplete; try another location."))
            }
        }
    }

    /** Save the preserved unreadable/last-good data as a user-selected document. */
    fun exportRecoveryCopy(uri: Uri) {
        if (load !is WorkspaceLoad.Recovery) return
        viewModelScope.launch {
            try {
                val bytes = withContext(ioDispatcher) { store.recoveryBytes() }
                if (bytes == null) {
                    noticeChannel.trySend(Notice("No readable recovery bytes are available to save."))
                    return@launch
                }
                withContext(ioDispatcher) {
                    requireNotNull(requireNotNull(appContext).contentResolver.openOutputStream(uri, "wt"))
                        .use { it.write(bytes) }
                }
                noticeChannel.trySend(Notice("A recovery snapshot was saved byte-for-byte. It may need repair before use."))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.trySend(Notice("Copy failed. The chosen file may be incomplete; try another location."))
            }
        }
    }
}
