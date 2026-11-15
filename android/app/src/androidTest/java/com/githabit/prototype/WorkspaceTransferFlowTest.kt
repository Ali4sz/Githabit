package com.githabit.prototype

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.GitHabitApp
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Guarded real UI/storage; a test-only registry supplies picker results. No shipped provider/seed hook. */
class WorkspaceTransferFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var models: ViewModelStore? = null
    private lateinit var vm: GitHabitViewModel
    private lateinit var access: WorkspaceFileAccess
    @Volatile private var failWrites = false
    private val documents = mutableListOf<File>()
    private val results = mutableMapOf<String, File?>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            val action = contract.createIntent(context, input).action!!
            check(results.containsKey(action)) { "Missing test picker result for $action" }
            val file = results.remove(action)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                dispatchResult(requestCode, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    file?.let { Intent().setData(Uri.fromFile(it)) })
            }
        }
    }
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }
    private val day = LocalDate.parse("2026-11-04")
    private val clock = object : AppClock {
        override fun today() = day
        override fun zone() = ZoneId.of("UTC")
        override fun now(): Instant = day.atTime(12, 0).atZone(zone()).toInstant()
    }
    private fun fixture() = Workspace(onboarded = true, name = "Retained profile")
        .createHabit(HabitDraft("Practice", "", 2.5, "minutes", schedule = HabitSchedule.WeeklyQuota(3)),
            day.minusDays(2), "sample-edited")
        .addCommit("sample-edited", 1.25, day, "partial", "kept", clock.now())
        .requestWeekStart(java.time.DayOfWeek.SUNDAY, day)
    private fun persisted() = WorkspaceJson.decode(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).readBytes())

    @Before fun authorize() {
        verifyDisposableDevice() // BEFORE target files, monitors, or Activity launch.
        val real = AtomicFileAccess(context)
        access = object : WorkspaceFileAccess by real {
            override fun writeBytes(bytes: ByteArray) {
                if (failWrites) throw IOException("Test-only verified rollback")
                real.writeBytes(bytes)
            }
        }
    }
    private fun prepare(state: Workspace = fixture(), scale: Float = 1f) {
        val store = WorkspaceStore(access)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(state))
        launch(scale)
    }
    private fun launch(scale: Float = 1f) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(access), clock)
            models = ViewModelStore().also { it.put("transfer", vm) }
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalActivityResultRegistryOwner provides registryOwner) { GitHabitApp(vm) }
            }
        }
        waitFor("page-TODAY")
        click("open-settings")
    }
    private fun close() {
        scenario?.onActivity { models?.clear() }
        scenario?.close()
        scenario = null
    }
    @After fun cleanup() {
        close()
        documents.forEach { it.delete() }
    }
    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        val dismiss = compose.onAllNodesWithContentDescription("Dismiss")
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
        val node = compose.onNodeWithTag(tag)
        if (tag != "open-settings") node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun awaitState(predicate: (Workspace) -> Boolean) {
        compose.waitUntil(15_000) { runCatching { predicate(persisted()) }.getOrDefault(false) }
        compose.waitForIdle()
    }
    private fun document(contents: ByteArray? = null): File = File.createTempFile("transfer-test-", ".json", context.filesDir)
        .also { file -> documents += file; if (contents != null) file.writeBytes(contents) }
    private fun picker(action: String, file: File? = null) {
        results[action] = file
    }

    @Test fun exportFrozenSnapshotAndConfirmedRestoreRoundTripCalendarAndMixedHistory() {
        prepare()
        val snapshot = persisted()
        val output = document()
        picker(Intent.ACTION_CREATE_DOCUMENT, output)
        click("export-workspace")
        compose.waitUntil(15_000) { runCatching { WorkspaceJson.decode(output.readBytes()) == snapshot }.getOrDefault(false) }
        compose.waitUntil(15_000) { !vm.exportInProgress }
        compose.waitForIdle()
        picker(Intent.ACTION_OPEN_DOCUMENT, output)
        click("restore-workspace")
        waitFor("restore-preview")
        compose.onAllNodesWithText("1 repo").assertCountEquals(2)
        output.writeText(WorkspaceJson.encode(Workspace(name = "Changed after preview")))
        click("confirm-restore")
        waitFor("page-TODAY")
        assertEquals(snapshot, persisted())
        assertEquals(snapshot.calendar.periodOn(day), persisted().calendar.periodOn(day))
        assertEquals(ProgressAnalytics(snapshot, day).weekly("sample-edited", day),
            ProgressAnalytics(persisted(), day).weekly("sample-edited", day))
    }

    @Test fun pickerCancellationAndInvalidDocumentLeaveWorkspaceUntouched() {
        prepare()
        val before = persisted()
        picker(Intent.ACTION_OPEN_DOCUMENT)
        click("restore-workspace")
        compose.runOnIdle { assertNull(vm.restoreState) }
        picker(Intent.ACTION_OPEN_DOCUMENT, document("{\"version\":999}".toByteArray()))
        click("restore-workspace")
        waitFor("restore-failed")
        click("cancel-restore")
        assertEquals(before, persisted())
    }

    @Test fun rollbackRetainsPreviewAndDirtyDraftUntilRetrySucceeds() {
        prepare()
        val before = persisted()
        compose.runOnIdle {
            vm.overlay = Overlay.RepoForm("sample-edited")
            vm.editRepoForm { it.copy(description = "dirty retained draft") }
            val request = vm.requestRestore()!!
            vm.readRestore(request) { WorkspaceJson.encode(Workspace(onboarded = true)).byteInputStream() }
        }
        waitFor("restore-preview")
        failWrites = true
        click("confirm-restore")
        compose.waitUntil(15_000) { !vm.busy }
        assertEquals(before, persisted())
        compose.runOnIdle { assertNotNull(vm.formState); assertTrue(vm.restoreState is RestoreState.Preview) }
        failWrites = false
        click("confirm-restore")
        awaitState { it.habits.isEmpty() && it.commits.isEmpty() }
        compose.runOnIdle { assertNull(vm.formState); assertNull(vm.restoreState) }
    }

    @Test fun changedRevisionRequiresAnotherConfirmation() {
        prepare()
        picker(Intent.ACTION_OPEN_DOCUMENT, document(WorkspaceJson.encode(Workspace(onboarded = true)).toByteArray()))
        click("restore-workspace")
        waitFor("restore-preview")
        compose.runOnIdle { vm.saveCommit("sample-edited", null, 1.0, "intervening") }
        awaitState { it.commits.size == 2 }
        click("confirm-restore")
        waitFor("transfer-consent-updated")
        assertEquals(2, persisted().commits.size)
        click("confirm-restore")
        awaitState { it == Workspace(onboarded = true) }
    }

    @Test fun resetCancelExportFirstAndExplicitResetStayEmptyAfterRestartAtTwoHundredPercent() {
        prepare(scale = 2f)
        val before = persisted()
        click("reset-workspace")
        waitFor("reset-preview")
        click("cancel-reset")
        assertEquals(before, persisted())
        click("reset-workspace")
        val output = document()
        picker(Intent.ACTION_CREATE_DOCUMENT, output)
        click("export-before-reset")
        compose.waitUntil(15_000) { runCatching { WorkspaceJson.decode(output.readBytes()) == before }.getOrDefault(false) }
        compose.waitUntil(15_000) { !vm.exportInProgress }
        compose.waitForIdle()
        click("confirm-reset")
        waitFor("local-welcome")
        assertEquals(Workspace(), persisted())
        assertEquals(before, WorkspaceJson.decode(output.readBytes()))
        close()
        // Separate real factory startup; close only after the welcome and disk checks.
        val main = ActivityScenario.launch(MainActivity::class.java)
        try { waitFor("local-welcome"); assertEquals(Workspace(), persisted()) } finally { main.close() }
    }

    @Test fun activityRecreationRetainsValidatedPreviewWithoutRereadingDocument() {
        val store = WorkspaceStore(access)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(fixture()))
        val main = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitFor("page-TODAY")
            main.onActivity { activity ->
                vm = ViewModelProvider(activity, GitHabitViewModel.Factory)[GitHabitViewModel::class.java]
                val request = vm.requestRestore()!!
                vm.readRestore(request) { WorkspaceJson.encode(Workspace(onboarded = true, name = "Retained candidate")).byteInputStream() }
            }
            waitFor("restore-preview")
            val before = vm.restoreState
            main.recreate()
            waitFor("restore-preview")
            main.onActivity { activity ->
                assertSame(before, ViewModelProvider(activity, GitHabitViewModel.Factory)[GitHabitViewModel::class.java].restoreState)
            }
            click("confirm-restore")
            awaitState { it == Workspace(onboarded = true, name = "Retained candidate") }
        } finally { main.close() }
    }

    @Test fun recoveryAllowsValidatedRestoreAfterPreservingUnreadableOriginal() {
        val damaged = "{invalid original".toByteArray()
        context.getFileStreamPath(AtomicFileAccess.FILE_NAME).writeBytes(damaged)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(access), clock)
            models = ViewModelStore().also { it.put("recovery-transfer", vm) }
            activity.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) { GitHabitApp(vm) }
            }
        }
        waitFor("recovery-restore")
        picker(Intent.ACTION_OPEN_DOCUMENT, document(WorkspaceJson.encode(fixture()).toByteArray()))
        click("recovery-restore")
        waitFor("restore-preview")
        click("confirm-restore")
        waitFor("page-TODAY")
        assertEquals(fixture(), persisted())
        assertTrue(context.filesDir.listFiles()!!.filter { it.name.startsWith("githabit-recovery-") }
            .any { it.readBytes().contentEquals(damaged) })
    }
}
