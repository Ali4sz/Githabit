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
import com.githabit.prototype.data.WorkspaceJsonException
import com.githabit.prototype.data.WorkspaceLoadResult
import com.githabit.prototype.data.WorkspaceStore
import com.githabit.prototype.data.WriteOutcome
import com.githabit.prototype.domain.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.Duration
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val undos = linkedMapOf<String, UndoRecord>()
    // Fingerprints retain every unresolved replacement barrier without retaining large
    // workspace snapshots. A later failed attempt cannot erase an earlier candidate.
    private val uncertainReplacements = mutableSetOf<String>()
    private var uncertainUndoId: String? = null
    private var inFlightWorkspace: Workspace? = null
    // Only one ordinary candidate can be unresolved: recovery blocks further ordinary
    // commands. Its boundary may be crossed while the recovery screen is visible.
    private var uncertainOrdinaryBoundary: LocalDate? = null
    // An observed activation must not become cancellable after a clock rollback,
    // even when its maintenance write failed. Reconcile only against verified snapshots.
    private var observedCalendarBoundary: LocalDate? = null
    private var calendarRefreshQueued = false
    private var backStack = emptyList<Pair<Destination, String?>>()
    val notices = noticeChannel.receiveAsFlow()

    var workspace by mutableStateOf(Workspace())
        private set
    var load by mutableStateOf<WorkspaceLoad>(WorkspaceLoad.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    private var visibleTime by mutableStateOf(clock.capture())
    val today: LocalDate get() = visibleTime.date
    val zone get() = visibleTime.zone
    var destination by mutableStateOf(Destination.TODAY)
        private set
    var selectedRepoId by mutableStateOf<String?>(null)
        private set
    var overlay by mutableStateOf<Overlay?>(null)
    val needsWelcome: Boolean
        get() = !workspace.onboarded && workspace.habits.isEmpty() && workspace.commits.isEmpty()
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

    private val refreshScope = tickerScope ?: viewModelScope
    private var ticker: Job? = null

    /** Start/stop with the visible Activity; no lifetime/background polling or service. */
    fun startForegroundRefresh() {
        refreshDate()
        if (ticker?.isActive == true) return
        ticker = refreshScope.launch {
            while (true) {
                val time = clock.capture()
                val midnight = time.date.plusDays(1).atStartOfDay(time.zone).toInstant()
                delay(Duration.between(time.instant, midnight).toMillis().coerceIn(1, 30_000))
                refreshDate()
            }
        }
    }

    fun stopForegroundRefresh() { ticker?.cancel(); ticker = null }

    init {
        viewModelScope.launch {
            mutation.withLock {
                applyLoadResult(withContext(ioDispatcher) { store.load() })
            }
        }
    }

    override fun onCleared() {
        stopForegroundRefresh()
    }

    private suspend fun applyLoadResult(result: WorkspaceLoadResult) {
        when (result) {
            is WorkspaceLoadResult.Missing -> {
                workspace = Workspace()
                load = WorkspaceLoad.Ready(isNew = true)
            }
            is WorkspaceLoadResult.Ready -> {
                val replaced = uncertainReplacements.isNotEmpty() && withContext(ioDispatcher) {
                    workspaceFingerprint(result.workspace) in uncertainReplacements
                }
                // Reconciliation is not a replay. Changed data invalidates stale inverses/routes.
                if (load is WorkspaceLoad.Recovery) {
                    if (workspace != result.workspace || replaced) {
                        clearTransientState(clearCalendarObservation = replaced)
                    } else uncertainUndoId?.takeIf { it in undos }?.let {
                        noticeChannel.trySend(Notice("The previous workspace was verified. Undo did not commit; you can retry.", it))
                    }
                }
                uncertainReplacements.clear()
                uncertainUndoId = null
                uncertainOrdinaryBoundary = null
                workspace = result.workspace
                reconcileCalendarObservation()
                load = WorkspaceLoad.Ready(isNew = false)
            }
            is WorkspaceLoadResult.Unreadable -> {
                // Keep the last committed snapshot for reconciliation; ordinary UI is gated.
                load = WorkspaceLoad.Recovery(result.kind, result.reason, result.preserved)
            }
        }
        refreshDate()
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

    private fun clearTransientState(clearCalendarObservation: Boolean = true) {
        workspaceSession++
        undos.clear()
        uncertainUndoId = null
        uncertainOrdinaryBoundary = null
        if (clearCalendarObservation) observedCalendarBoundary = null
        overlay = null
        backStack = emptyList()
        destination = Destination.TODAY
        selectedRepoId = null
    }

    private fun replaceWithEmpty(fromRecovery: Boolean) = replaceWorkspace(Workspace(), fromRecovery)

    /** Internal confirmed-candidate seam; Task 12 owns document preview/revision consent. */
    internal fun replaceWorkspace(candidate: Workspace, fromRecovery: Boolean = false) {
        if (busy || (if (fromRecovery) load !is WorkspaceLoad.Recovery else load !is WorkspaceLoad.Ready)) return
        busy = true
        viewModelScope.launch {
            mutation.withLock {
                try {
                    if (if (fromRecovery) load !is WorkspaceLoad.Recovery else load !is WorkspaceLoad.Ready) return@withLock
                    withContext(NonCancellable) {
                        if (persist(candidate, replacement = true)) {
                            clearTransientState()
                            load = WorkspaceLoad.Ready(isNew = candidate == Workspace())
                            refreshDate()
                            noticeChannel.trySend(Notice("Workspace replaced. Any previous data was preserved on this device before replacement."))
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    noticeChannel.trySend(Notice("Could not prepare the replacement. Nothing was replaced."))
                } finally { busy = false }
            }
        }
    }

    private fun observe(time: CommandTime) {
        if (visibleTime.date != time.date || visibleTime.zone != time.zone) visibleTime = time
        // A pre-midnight command can be writing a revised pending destination while
        // the old snapshot is still displayed. Lock the observed boundary, not its weekday.
        listOfNotNull(workspace.calendar.pending?.boundary, inFlightWorkspace?.calendar?.pending?.boundary, uncertainOrdinaryBoundary)
            .filter { it <= time.date }.forEach {
                observedCalendarBoundary = maxOf(observedCalendarBoundary ?: it, it)
            }
    }

    private fun advancedCalendar(time: CommandTime): WeekCalendar {
        val calendar = workspace.calendar
        return calendar.advancedTo(maxOf(time.date, observedCalendarBoundary ?: time.date))
    }

    /** Retain only an observed pending boundary that survives in the verified snapshot. */
    private fun reconcileCalendarObservation() {
        observedCalendarBoundary = workspace.calendar.pending?.boundary?.takeIf {
            it <= (observedCalendarBoundary ?: LocalDate.MIN)
        }
    }

    fun refreshDate() {
        val time = clock.capture()
        observe(time)
        if (load !is WorkspaceLoad.Ready || calendarRefreshQueued || advancedCalendar(time) == workspace.calendar) return
        calendarRefreshQueued = true
        viewModelScope.launch {
            try {
                mutation.withLock {
                    if (load !is WorkspaceLoad.Ready) return@withLock
                    val captured = clock.capture()
                    observe(captured)
                    val calendar = advancedCalendar(captured)
                    if (calendar != workspace.calendar) withContext(NonCancellable) {
                        persist(workspace.copy(calendar = calendar))
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.trySend(Notice("Calendar activation could not be saved. Your history was not replaced; try again."))
            } finally { calendarRefreshQueued = false }
        }
    }

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
        dated: Boolean = false,
        dismiss: Boolean = false,
        intendedDate: LocalDate? = null,
        onDateChanged: (LocalDate) -> Unit = {},
        undo: ((Workspace, Workspace, CommandTime) -> UndoRecord)? = null,
        after: () -> Unit = {},
        retryUndoId: String? = null,
        transform: (Workspace, CommandTime) -> Workspace,
    ) {
        if (busy || load !is WorkspaceLoad.Ready) return
        busy = true
        viewModelScope.launch {
            mutation.withLock {
                try {
                    if (load !is WorkspaceLoad.Ready) return@withLock
                    val time = clock.capture()
                    observe(time)
                    if (intendedDate != null && intendedDate != time.date) {
                        onDateChanged(time.date)
                        noticeChannel.trySend(Notice("The local date changed to ${time.date}. Review your draft and submit again; nothing was saved."))
                        return@withLock
                    }
                    val committedBefore = workspace
                    val before = workspace.copy(calendar = advancedCalendar(time))
                    val next = transform(before, time)
                    // A validated unchanged form is finished, but no-op commands still
                    // persist nothing, announce nothing, and create no undo token.
                    if (next == before) {
                        if (before != committedBefore && !withContext(NonCancellable) { persist(before) }) return@withLock
                        if (dismiss) overlay = null
                        return@withLock
                    }
                    // Build the inverse before anything is written: a builder failure must
                    // not leave a persisted change behind a misleading failure notice.
                    val inverse = undo?.invoke(before, next, time)
                    // Complete reconciliation/publication even if the ViewModel is cleared
                    // during blocking disk IO. Cancellation must not hide a committed result.
                    withContext(NonCancellable) {
                        if (persist(next, retryUndoId = retryUndoId)) {
                            if (dismiss) overlay = null
                            val undoId = inverse?.let { registerUndo(it) }
                            try {
                                after()
                                message?.let { noticeChannel.trySend(Notice(if (dated) "$it Date: ${time.date}." else it, undoId)) }
                            } catch (_: Exception) {
                                noticeChannel.trySend(Notice("Your change was saved, but the screen could not be updated. Reopen it."))
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    noticeChannel.trySend(Notice(if (error is IllegalArgumentException) error.message ?: "Check your entry."
                        else "Could not prepare your change. Please try again.",
                        retryUndoId.takeUnless { error is UndoConflict && !error.retryable }))
                } finally { busy = false }
            }
        }
    }

    /** Write, verify, publish on success; never publish on rollback/indeterminate. */
    private suspend fun persist(next: Workspace, replacement: Boolean = false, retryUndoId: String? = null): Boolean {
        inFlightWorkspace = next.takeUnless { replacement }
        val (outcome, fingerprint) = try {
            withContext(ioDispatcher) {
                val fingerprint = if (replacement) workspaceFingerprint(next) else null
                (if (replacement) store.replaceVerified(next) else store.write(next)) to fingerprint
            }
        } finally { inFlightWorkspace = null }
        return when (outcome) {
            WriteOutcome.Committed -> {
                if (replacement) uncertainReplacements.clear()
                workspace = next
                // Locked transitions now carry their own evidence. A cancellation captured
                // before B may also finish after B; do not apply its observation to a later,
                // newly created request that happens to reuse the same boundary.
                reconcileCalendarObservation()
                load = WorkspaceLoad.Ready(isNew = false)
                true
            }
            WriteOutcome.RolledBack -> {
                // Recovery replacement's verified prior file can differ from the published
                // snapshot. Only reload may reconcile it; do not discard candidate evidence.
                if (load is WorkspaceLoad.Ready) reconcileCalendarObservation()
                noticeChannel.trySend(Notice("The change was not saved. The previous file was verified; you can retry.", retryUndoId))
                false
            }
            WriteOutcome.Indeterminate -> {
                if (!replacement) uncertainOrdinaryBoundary = next.calendar.pending?.boundary
                fingerprint?.let { uncertainReplacements += it }
                if (retryUndoId != null) uncertainUndoId = retryUndoId
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
                if (load is WorkspaceLoad.Ready) reconcileCalendarObservation()
                noticeChannel.trySend(Notice(outcome.reason, retryUndoId))
                false
            }
        }
    }

    /** Canonical v2 encoding aligns value-equivalent loaded JSON with confirmed candidates. IO only. */
    private fun workspaceFingerprint(candidate: Workspace): String = MessageDigest.getInstance("SHA-256")
        .digest(WorkspaceJson.encode(candidate).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun registerUndo(inverse: UndoRecord): String {
        val id = UUID.randomUUID().toString()
        undos[id] = inverse
        if (undos.size > 20) undos.remove(undos.keys.first())
        return id
    }

    /** Welcome changes only onboarding state; opening/cancelling the form adds no records. */
    fun finishWelcome(createRepo: Boolean = true) {
        if (busy || load !is WorkspaceLoad.Ready) return
        val openWorkspace = {
            navigate(Destination.TODAY, mainTab = true)
            if (createRepo) overlay = Overlay.RepoForm()
        }
        // A redundant entry action still opens its form without an unnecessary write.
        if (workspace.onboarded) openWorkspace()
        else update(after = openWorkspace) { it, _ -> it.copy(onboarded = true) }
    }

    fun saveHabit(id: String?, draft: HabitDraft, intendedDate: LocalDate = today, onDateChanged: (LocalDate) -> Unit = {}) = update(
        message = if (id == null) "Your new repo is ready. Start with a small commit." else "Repo updated. Keep growing.",
        dated = true, dismiss = true, intendedDate = intendedDate, onDateChanged = onDateChanged,
    ) { it, time -> if (id == null) it.createHabit(draft, time.date) else it.editHabit(id, draft, time.date) }

    fun saveCommit(habitId: String, commitId: String?, amount: Double, note: String,
        intendedDate: LocalDate = today, onDateChanged: (LocalDate) -> Unit = {}) {
        val id = commitId ?: UUID.randomUUID().toString()
        update(message = if (commitId == null) "Progress committed. That’s a little win." else "Commit corrected.",
            dated = commitId == null, dismiss = true,
            intendedDate = intendedDate.takeIf { commitId == null }, onDateChanged = onDateChanged,
            undo = { before, after, time -> commitUndo(before, after, id, time) }) { it, time ->
                if (commitId == null) it.addCommit(habitId, amount, time.date, note, id, time.instant)
                else {
                    require(it.commits.any { commit -> commit.id == id && commit.habitId == habitId }) { "This commit could not be found. Reopen the form." }
                    it.editCommit(id, amount, note)
                }
            }
    }

    fun complete(habitId: String) {
        val id = UUID.randomUUID().toString()
        update(message = "Daily goal reached. A little win worth keeping.", dated = true, undo = { before, after, time ->
            commitUndo(before, after, id, time)
        }) { it, time -> it.completeGoal(habitId, time.date, id, time.instant) }
    }

    fun removeCommit(id: String) = update(message = "Commit removed.", dismiss = true, undo = { before, after, time ->
        commitUndo(before, after, id, time)
    }) { it, _ -> it.removeCommit(id) }

    fun status(id: String, status: RepoStatus) {
        update(message = "Repo ${if (status == RepoStatus.ACTIVE) "resumed" else status.name.lowercase()}. Your history is safe.",
            dated = true, undo = { before, _, time ->
                val habit = before.habits.first { it.id == id }
                StatusUndo(time, id, habit.createdOn, requireNotNull(habit.versionOn(time.date)), status)
            }) { it, time -> it.changeStatus(id, status, time.date) }
    }

    fun undo(id: String) {
        if (load !is WorkspaceLoad.Ready) {
            noticeChannel.trySend(Notice("Undo is paused. Re-read storage before trying again."))
            return
        }
        update(message = "Undone. You’re all set.", retryUndoId = id,
            after = { undos.remove(id) }) { it, time ->
            val inverse = undos[id] ?: throw UndoConflict("This Undo has expired or was cleared after workspace replacement. Nothing was changed.")
            inverse.reverse(it, time).also { result ->
                if (result == it) throw UndoConflict("This operation is no longer available to undo. Nothing was changed.")
            }
        }
    }

    fun setTheme(dark: Boolean) = setTheme(if (dark) ThemePreference.DARK else ThemePreference.LIGHT)
    fun setTheme(theme: ThemePreference) = update { it, _ -> it.copy(theme = theme) }

    /** Task 7 supplies Settings controls; calendar commands already share verified storage. */
    fun setWeekStart(weekday: DayOfWeek, intendedDate: LocalDate = today, onDateChanged: (LocalDate) -> Unit = {}) = update(
        message = "Calendar preference saved. Check its effective and pending dates in Settings.",
        intendedDate = intendedDate, onDateChanged = onDateChanged,
    ) { it, time -> it.requestWeekStart(weekday, time.date) }

    fun saveProfile(name: String, bio: String) = update(message = "Your workspace feels a little more like you.") { it, _ ->
        require(name.trim().length <= 40) { "Keep your display name within 40 characters." }
        require(bio.length <= 160) { "Keep your note within 160 characters." }
        it.copy(name = name.trim(), bio = bio.trim())
    }

    /** Confirmed reset: preserve a copy first, then replace with verified empty data. */
    fun reset() = replaceWithEmpty(fromRecovery = false)

    fun export(uri: Uri) {
        if (load !is WorkspaceLoad.Ready) return
        val snapshot = workspace
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    WorkspaceJson.export(snapshot) {
                        requireNotNull(requireNotNull(appContext).contentResolver.openOutputStream(uri, "wt"))
                    }
                }
                noticeChannel.trySend(Notice("Your workspace has been exported."))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.trySend(Notice(if (error is WorkspaceJsonException) "Export was not started: ${error.message}"
                    else "Export failed. The chosen file may be incomplete; try another location."))
            }
        }
    }

    /** Save the preserved unreadable/last-good data as a user-selected document. */
    fun exportRecoveryCopy(uri: Uri) {
        if (load !is WorkspaceLoad.Recovery) return
        viewModelScope.launch {
            try {
                val snapshot = mutation.withLock {
                    if (load !is WorkspaceLoad.Recovery) null else withContext(ioDispatcher) { store.recoverySnapshot() }
                }
                if (snapshot == null) {
                    noticeChannel.trySend(Notice("No readable recovery bytes are available to save."))
                    return@launch
                }
                withContext(ioDispatcher) {
                    requireNotNull(requireNotNull(appContext).contentResolver.openOutputStream(uri, "wt"))
                        .use(snapshot::writeTo)
                }
                noticeChannel.trySend(Notice("A recovery snapshot was saved byte-for-byte. It may need repair before use."))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                noticeChannel.trySend(Notice("Copy failed. The chosen file may be incomplete; try another location."))
            }
        }
    }
}
