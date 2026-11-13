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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
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
    internal var navigation by mutableStateOf(NavigationState.decode(savedStateHandle.get<Any>(NAVIGATION_STATE_KEY)))
        private set
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
    val destination get() = navigation.route.destination
    val selectedRepoId get() = navigation.selectedRepoId
    val topLevelDestination get() = navigation.topLevel
    val canGoBack get() = navigation.canGoBack
    private var currentOverlay by mutableStateOf(decodeOverlay(savedStateHandle.get<Any>(OVERLAY_STATE_KEY)))
    var formState by mutableStateOf(FormState.decode(savedStateHandle.get<Any>(FORM_STATE_KEY)))
        private set
    var discardConfirmation by mutableStateOf(savedStateHandle.get<Boolean>(DISCARD_STATE_KEY) ?: false)
        private set
    var formSaveFailed by mutableStateOf(false)
        private set
    private var pendingFormNavigation: (() -> Unit)? = null
    private var restoredFormEncoding: Any? = savedStateHandle.get<Any>(FORM_STATE_KEY)
    var overlay: Overlay?
        get() = currentOverlay
        set(value) {
            if (value != currentOverlay) {
                formSaveFailed = false
                publishForm(null)
                cancelDiscard()
            }
            currentOverlay = value
            savedStateHandle[OVERLAY_STATE_KEY] = value?.encode()
            if (load is WorkspaceLoad.Ready) ensureFormState()
        }
    val needsWelcome: Boolean
        get() = !workspace.onboarded && workspace.habits.isEmpty() && workspace.commits.isEmpty()
    var workspaceSession by mutableIntStateOf(savedStateHandle.get<Int>(SESSION_STATE_KEY) ?: 0)
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
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) : this(null, WorkspaceStore(access), clock, ioDispatcher, tickerScope, savedStateHandle)

    companion object {
        /** Production factory wiring the real application, storage, and clock. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                GitHabitViewModel(app, WorkspaceStore(AtomicFileAccess(requireNotNull(app))), SystemAppClock(),
                    savedStateHandle = createSavedStateHandle())
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
                    if (replaced) {
                        clearTransientState(clearCalendarObservation = replaced)
                    } else if (workspace != result.workspace) {
                        // An unrelated maintenance/status write must not discard a dirty form.
                        undos.clear()
                        publishNavigation(navigation.validated(result.workspace.habits.mapTo(mutableSetOf()) { it.id }))
                    } else uncertainUndoId?.takeIf { it in undos }?.let {
                        noticeChannel.trySend(Notice("The previous workspace was verified. Undo did not commit; you can retry.", it))
                    }
                }
                uncertainReplacements.clear()
                uncertainUndoId = null
                uncertainOrdinaryBoundary = null
                workspace = result.workspace
                reconcileSubmittedForm()
                reconcileCalendarObservation()
                load = WorkspaceLoad.Ready(isNew = false)
            }
            is WorkspaceLoadResult.Unreadable -> {
                // Keep the last committed snapshot for reconciliation; ordinary UI is gated.
                load = WorkspaceLoad.Recovery(result.kind, result.reason, result.preserved)
            }
        }
        if (load is WorkspaceLoad.Ready) validateNavigation()
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
        savedStateHandle[SESSION_STATE_KEY] = workspaceSession
        undos.clear()
        uncertainUndoId = null
        uncertainOrdinaryBoundary = null
        if (clearCalendarObservation) observedCalendarBoundary = null
        overlay = null
        publishNavigation(NavigationState())
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
        if (load !is WorkspaceLoad.Ready) return
        if (overlay != null) {
            requestDismissForm { navigate(next, repoId, mainTab) }
            return
        }
        if (next == Destination.DETAIL && workspace.habits.none { it.id == repoId }) {
            navigate(Destination.REPOS, mainTab = true)
            return
        }
        overlay = null
        publishNavigation(navigation.navigate(next, repoId, mainTab))
    }

    fun back() {
        if (overlay != null) { requestDismissForm(); return }
        if (load is WorkspaceLoad.Ready) publishNavigation(navigation.back())
    }

    /** Gesture completion may not pop a route changed/replaced while progress was collected. */
    internal fun completeBack(expected: NavigationState) {
        if (navigation === expected && overlay == null && load is WorkspaceLoad.Ready) back()
    }

    private fun publishNavigation(value: NavigationState) {
        navigation = value
        savedStateHandle[NAVIGATION_STATE_KEY] = value.encode()
    }

    private fun validateNavigation() {
        restoredFormEncoding?.let { raw ->
            restoredFormEncoding = null
            formState = decodeReferencedForm(raw)
            reconcileSubmittedForm()
        }
        val repoIds = workspace.habits.mapTo(mutableSetOf()) { it.id }
        publishNavigation(navigation.validated(repoIds))
        val valid = when (val value = overlay) {
            is Overlay.RepoForm -> value.id == null || value.id in repoIds
            is Overlay.LogForm -> value.habitId in repoIds && (value.commitId == null ||
                workspace.commits.any { it.id == value.commitId && it.habitId == value.habitId })
            is Overlay.Day -> value.habitId == null || value.habitId in repoIds
            else -> true
        }
        if (!valid) overlay = null
        ensureFormState()
    }

    private fun ensureFormState() {
        if (formState?.matches(overlay) == true) return
        val next = when (val value = overlay) {
            is Overlay.RepoForm -> {
                val habit = workspace.habits.find { it.id == value.id }
                FormState.Repo(value.id, today, RepoFormValues.opening(habit, today),
                    reviewedConfiguration = habit?.versionOn(today), reviewedEntity = habit?.let(::entityFingerprint))
            }
            is Overlay.LogForm -> {
                val commit = workspace.commits.find { it.id == value.commitId }
                FormState.Log(value.habitId, value.commitId, commit?.date ?: today,
                    LogFormValues(commit?.amount?.let(::editableFormAmount) ?: "", commit?.note ?: ""),
                    reviewedEntity = commit?.let(::entityFingerprint))
            }
            else -> null
        }
        publishForm(next)
        if (next == null) cancelDiscard()
    }

    private fun publishForm(value: FormState?) {
        val encoded = value?.encode()?.mapIndexed { index, text ->
            // Valid v1 strings may have large surrounding whitespace. Keep these only on disk.
            val isLegacyField = value is FormState.Repo && index in listOf(3, 4, 6, 12, 13, 15)
            if (text.length > 4096 && isLegacyField) "\u0000reference:$index:${textFingerprint(text)}" else {
                require(text.length <= 4096) { "Draft input is too large for saved state." }
                if (text.startsWith('\u0000')) "\u0000literal:$text" else text
            }
        }
        restoredFormEncoding = null
        formState = value
        savedStateHandle[FORM_STATE_KEY] = encoded
    }

    private fun legacyFormField(form: FormState, index: Int): String? = when (form) {
        is FormState.Repo -> workspace.habits.find { it.id == form.id }?.let { habit ->
            when (index) { 3, 12 -> habit.name; 4, 13 -> habit.description; 6, 15 -> habit.unit; else -> null }
        }
        is FormState.Log -> workspace.commits.find { it.id == form.commitId }?.let { commit ->
            if (index == 5 || index == 7) commit.note else null
        }
    }

    private fun decodeReferencedForm(raw: Any): FormState? {
        val strings = (raw as? List<*>)?.map { it as? String ?: return null } ?: return null
        val decoded = FormState.decode(strings.map { if (it.startsWith("\u0000literal:")) it.removePrefix("\u0000literal:") else it }) ?: return null
        val encoded = decoded.encode()
        val replacements = encoded.mapIndexed { index, text ->
            if (strings[index].startsWith("\u0000reference:$index:")) {
                val source = legacyFormField(decoded, index) ?: return null
                val expected = strings[index].substringAfterLast(':')
                if (textFingerprint(source) == expected) source else {
                    formSaveFailed = true
                    noticeChannel.trySend(Notice("An unchanged legacy field changed on disk. Your other draft inputs were retained; review the current field and save again."))
                    if (index in listOf(3, 4, 6)) text else source
                }
            } else text
        }
        // Decode bounded tokens first, then hydrate raw legacy fields without a large Bundle.
        return when (decoded) {
            is FormState.Repo -> decoded.copy(
                baseline = decoded.baseline.copy(name = replacements[3], description = replacements[4], unit = replacements[6]),
                values = decoded.values.copy(name = replacements[12], description = replacements[13], unit = replacements[15]))
            is FormState.Log -> decoded.copy(baseline = decoded.baseline.copy(note = replacements[5]),
                values = decoded.values.copy(note = replacements[7]))
        }
    }

    private fun reconcileSubmittedForm() {
        val committed = when (val form = formState) {
            is FormState.Repo -> form.submitted && workspace.habits.find { it.id == (form.id ?: form.creationId) }?.let {
                form.submittedEntity != null && entityFingerprint(it) == form.submittedEntity
            } == true
            is FormState.Log -> form.submitted && workspace.commits.find { it.id == (form.commitId ?: form.creationId) }?.let {
                form.submittedEntity != null && entityFingerprint(it) == form.submittedEntity
            } == true
            null -> false
        }
        if (committed) overlay = null
    }

    fun editRepoForm(edit: (RepoFormValues) -> RepoFormValues) {
        if (!busy) (formState as? FormState.Repo)?.let { publishForm(it.copy(values = edit(it.values), submitted = false, submittedEntity = null)) }
    }

    fun editLogForm(edit: (LogFormValues) -> LogFormValues) {
        if (!busy) (formState as? FormState.Log)?.let { publishForm(it.copy(values = edit(it.values), submitted = false, submittedEntity = null)) }
    }

    fun reviewFormDate(date: LocalDate) {
        publishForm(when (val value = formState) {
            is FormState.Repo -> value.copy(intendedDate = date, submitted = false, submittedEntity = null,
                reviewedConfiguration = workspace.habits.find { it.id == value.id }?.versionOn(date),
                reviewedEntity = workspace.habits.find { it.id == value.id }?.let(::entityFingerprint))
            is FormState.Log -> value.copy(intendedDate = date, submitted = false, submittedEntity = null,
                reviewedEntity = workspace.commits.find { it.id == value.commitId }?.let(::entityFingerprint))
            null -> null
        })
    }

    /** All user dismissals converge here; verified save/replacement uses the direct setter. */
    fun requestDismissForm(afterDismiss: (() -> Unit)? = null) {
        if (busy || load !is WorkspaceLoad.Ready) return
        if (formState?.dirty == true) {
            if (!discardConfirmation) pendingFormNavigation = afterDismiss
            discardConfirmation = true
            savedStateHandle[DISCARD_STATE_KEY] = true
        } else {
            overlay = null
            afterDismiss?.invoke()
        }
    }

    fun cancelDiscard() {
        discardConfirmation = false
        savedStateHandle[DISCARD_STATE_KEY] = false
        pendingFormNavigation = null
    }

    fun confirmDiscard() {
        if (busy || load !is WorkspaceLoad.Ready) return
        val next = pendingFormNavigation
        overlay = null
        next?.invoke()
    }

    private fun update(
        message: String? = null,
        dated: Boolean = false,
        dismiss: Boolean = false,
        intendedDate: LocalDate? = null,
        onDateChanged: (LocalDate) -> Unit = {},
        undo: ((Workspace, Workspace, CommandTime) -> UndoRecord)? = null,
        after: () -> Unit = {},
        afterUnchanged: () -> Unit = {},
        retryUndoId: String? = null,
        transform: (Workspace, CommandTime) -> Workspace,
    ) {
        if (busy || load !is WorkspaceLoad.Ready) return
        busy = true
        if (dismiss) formSaveFailed = false
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
                        if (before != committedBefore && !withContext(NonCancellable) { persist(before) }) {
                            if (dismiss) formSaveFailed = true
                            return@withLock
                        }
                        if (dismiss) overlay = null
                        try { afterUnchanged() } catch (_: Exception) {
                            noticeChannel.trySend(Notice("Your saved data is unchanged, but the screen could not be updated. Reopen it."))
                        }
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
                        } else if (dismiss) formSaveFailed = true
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (dismiss) formSaveFailed = true
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
                if (!replacement) validateNavigation()
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

    private fun textFingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun entityFingerprint(habit: Habit): String = workspaceFingerprint(Workspace(habits = listOf(habit)))
    private fun entityFingerprint(commit: Commit): String = textFingerprint(listOf(commit.id, commit.habitId,
        commit.date.toString(), editableFormAmount(commit.amount), commit.note).joinToString("") { "${it.length}:$it" })

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

    fun saveHabit(id: String?, draft: HabitDraft, intendedDate: LocalDate = today,
        reviewedConfiguration: GoalVersion? = null, onConfigurationChanged: () -> Unit = {},
        onDateChanged: (LocalDate) -> Unit = {}) {
        if (busy || load !is WorkspaceLoad.Ready) return
        val form = (formState as? FormState.Repo)?.takeIf { it.id == id }
        val creationId = form?.creationId ?: UUID.randomUUID().toString()
        val candidate = runCatching {
            val proposed = if (id == null) workspace.createHabit(draft, intendedDate, creationId) else workspace.editHabit(id, draft, intendedDate)
            entityFingerprint(proposed.habits.first { it.id == (id ?: creationId) })
        }.getOrNull()
        form?.let { publishForm(it.copy(submitted = true, submittedEntity = candidate)) }
        update(
        message = if (id == null) "Your new repo is ready. Start with a small commit." else "Repo updated. Keep growing.",
        dated = true, dismiss = true, intendedDate = intendedDate, onDateChanged = onDateChanged,
    ) { it, time ->
        if (id != null && form?.reviewedEntity != null && it.habits.find { habit -> habit.id == id }?.let(::entityFingerprint) != form.reviewedEntity) {
            onConfigurationChanged()
            reviewFormDate(time.date)
            throw IllegalArgumentException("This repo changed since the form opened. Review your draft and save again.")
        }
        if (id != null && reviewedConfiguration != null) {
            val current = it.habits.find { habit -> habit.id == id }?.versionOn(time.date)
            if (current != null && current != reviewedConfiguration) {
                onConfigurationChanged()
                throw IllegalArgumentException("This repo’s dated configuration changed. Review your draft and save again.")
            }
        }
        if (id == null) {
            require(it.habits.none { habit -> habit.id == creationId }) { "This repo creation already committed. Close and reopen the form." }
            it.createHabit(draft, time.date, creationId)
        } else it.editHabit(id, draft, time.date)
        }
    }

    fun saveCommit(habitId: String, commitId: String?, amount: Double, note: String,
        intendedDate: LocalDate = today, onDateChanged: (LocalDate) -> Unit = {}) {
        if (busy || load !is WorkspaceLoad.Ready) return
        val form = (formState as? FormState.Log)?.takeIf { it.habitId == habitId && it.commitId == commitId }
        val id = commitId ?: form?.creationId ?: UUID.randomUUID().toString()
        val candidate = if (commitId != null) runCatching { entityFingerprint(workspace.editCommit(commitId, amount, note).commits.first { it.id == id }) }.getOrNull()
            else runCatching { entityFingerprint(Commit(id = id, habitId = habitId, date = intendedDate, amount = positive(amount), note = note.trim(), timestamp = clock.now())) }.getOrNull()
        form?.let { publishForm(it.copy(submitted = true, submittedEntity = candidate)) }
        update(message = if (commitId == null) "Progress committed. That’s a little win." else "Commit corrected.",
            dated = commitId == null, dismiss = true,
            intendedDate = intendedDate.takeIf { commitId == null }, onDateChanged = onDateChanged,
            undo = { before, after, time -> commitUndo(before, after, id, time) }) { it, time ->
                if (commitId == null) it.addCommit(habitId, amount, time.date, note, id, time.instant)
                 else {
                     require(it.commits.any { commit -> commit.id == id && commit.habitId == habitId }) { "This commit could not be found. Reopen the form." }
                     if (form?.reviewedEntity != null && entityFingerprint(it.commits.first { commit -> commit.id == id }) != form.reviewedEntity) {
                         reviewFormDate(form.intendedDate)
                         throw IllegalArgumentException("This commit changed since the form opened. Review your draft and save again.")
                     }
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

    fun setTheme(theme: ThemePreference) = update { it, _ -> it.copy(theme = theme) }

    /** Settings and calendar maintenance share the verified storage boundary. */
    fun setWeekStart(weekday: DayOfWeek, intendedDate: LocalDate = today, onDateChanged: (LocalDate) -> Unit = {}) = update(
        message = "Calendar preference saved. Check its effective and pending dates in Settings.",
        intendedDate = intendedDate, onDateChanged = onDateChanged,
    ) { it, time -> it.requestWeekStart(weekday, time.date) }

    fun saveProfile(name: String, bio: String, onSaved: () -> Unit = {}) = update(
        message = "Profile saved on this device.", after = onSaved, afterUnchanged = onSaved,
    ) { it, _ ->
        require(name.trim().length <= 40) { "Keep your display name within 40 characters." }
        require(bio.length <= 160) { "Keep your note within 160 characters." }
        // An unrelated edit must not normalize valid legacy values left untouched.
        it.copy(name = if (name == it.name) it.name else name.trim(), bio = if (bio == it.bio) it.bio else bio.trim())
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
