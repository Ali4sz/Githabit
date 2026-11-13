package com.githabit.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.Workspace
import com.githabit.prototype.domain.SystemAppClock
import com.githabit.prototype.ui.GitHabitApp
import com.githabit.prototype.fixtures.verifyDisposableDevice
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real platform storage and recovery UI. All data belongs to a provisioned disposable AVD. */
class WorkspaceRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var injectedModels: ViewModelStore? = null
    private val primary get() = File(context.filesDir, AtomicFileAccess.FILE_NAME)

    @Before fun prepareDisposableStorage() {
        verifyDisposableDevice()
        // No Activity is running yet; erase only test-owned workspace/copy files.
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("githabit-workspace.json") ||
                it.name.startsWith("githabit-recovery-") || it.name == "githabit-last-good.json"
        }?.forEach { check(it.delete()) { "Could not remove disposable test file ${it.name}" } }
    }

    @After fun closeActivity() {
        scenario?.onActivity { injectedModels?.clear() }
        scenario?.close()
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test fun missingStartupAndRecreationCreateNoData() {
        assertFalse(primary.exists())
        launch()
        waitFor("local-welcome")
        assertFalse(primary.exists())
        scenario!!.recreate()
        waitFor("local-welcome")
        assertFalse(primary.exists())
    }

    @Test fun corruptStartupRetryAndCancelledStartOverPreserveExactBytes() {
        val raw = byteArrayOf(123, 0xff.toByte(), 0xc0.toByte(), 0)
        AtomicFileAccess(context).writeBytes(raw)
        launch()
        waitFor("recovery-retry")
        compose.onNodeWithTag("local-welcome").assertDoesNotExist()
        compose.onNodeWithTag("recovery-save-copy").assertIsEnabled()
        compose.onNodeWithTag("recovery-start-over").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("recovery-retry").performScrollTo().performClick()
        compose.waitForIdle()
        assertArrayEquals(raw, primary.readBytes()) // Passive base-file read only.
        assertTrue(context.filesDir.listFiles()!!.any {
            it.name.startsWith("githabit-recovery-") && it.readBytes().contentEquals(raw)
        })
        scenario!!.recreate()
        waitFor("recovery-retry")
        assertArrayEquals(raw, primary.readBytes())
    }

    @Test fun confirmedStartOverPreservesOriginalBeforeFreshEntry() {
        val raw = "{ broken fixture".toByteArray()
        AtomicFileAccess(context).writeBytes(raw)
        launch()
        waitFor("recovery-start-over")
        compose.onNodeWithTag("recovery-start-over").performScrollTo().performClick()
        compose.onNodeWithText("Start over", substring = false).performClick()
        waitFor("local-welcome")
        assertEquals(Workspace(), WorkspaceJson.decode(primary.readText()))
        assertTrue(context.filesDir.listFiles()!!.any {
            it.name.startsWith("githabit-recovery-") && it.readBytes().contentEquals(raw)
        })
        scenario!!.recreate()
        waitFor("local-welcome")
        assertEquals(Workspace(), WorkspaceJson.decode(primary.readText()))
    }

    @Test fun unsupportedFormatCannotEnterOrdinaryUi() {
        val raw = WorkspaceJson.encode(Workspace()).replace("\"version\":2", "\"version\":99").toByteArray()
        AtomicFileAccess(context).writeBytes(raw)
        launch()
        waitFor("recovery-retry")
        compose.onNodeWithTag("local-welcome").assertDoesNotExist()
        compose.onNodeWithTag("page-TODAY").assertDoesNotExist()
        assertArrayEquals(raw, primary.readBytes())
    }

    @Test fun existingUnreadablePathIsNotReportedAsMissing() {
        assertTrue(primary.mkdir())
        val access = AtomicFileAccess(context)
        assertThrows(FileNotFoundException::class.java) { access.readBytes() }
        val store = WorkspaceStore(access)
        assertEquals(UnreadableKind.IO, (store.load() as WorkspaceLoadResult.Unreadable).kind)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        assertTrue(primary.isDirectory)
    }

    @Test fun platformAtomicFileRecoversLegacyBackupBeforeLoading() {
        val backup = File(context.filesDir, AtomicFileAccess.FILE_NAME + ".bak")
        val raw = WorkspaceJson.encode(Workspace()).toByteArray()
        backup.writeBytes(raw)
        val store = WorkspaceStore(context)
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.load())
        assertArrayEquals(raw, primary.readBytes())
        assertEquals(WriteOutcome.Committed, store.write(Workspace().copy(bio = "A test-only change")))
    }

    @Test fun interruptedFirstWriteSidecarCanBeExportedAndExplicitlyReplaced() {
        val raw = "interrupted test-only first write".toByteArray()
        File(context.filesDir, AtomicFileAccess.FILE_NAME + ".new").writeBytes(raw)
        val store = WorkspaceStore(context)
        val result = store.load() as WorkspaceLoadResult.Unreadable
        assertTrue(result.preserved)
        assertTrue(store.hasRecoveryBytes)
        assertEquals(WriteOutcome.Blocked, store.write(Workspace()))
        val snapshot = requireNotNull(store.recoverySnapshot())
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace()))
        val output = java.io.ByteArrayOutputStream()
        snapshot.writeTo(output)
        assertArrayEquals(raw, output.toByteArray())
        assertEquals(WorkspaceLoadResult.Ready(Workspace()), store.reload())
    }

    @Test fun fixedSignedOutLegacyHistoryMigratesOnceAndRetainsItsOriginal() {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/v1-mixed-signed-out-dark.json").use { it.readBytes() }
        AtomicFileAccess(context).writeBytes(raw)
        val expected = WorkspaceJson.decode(raw)
        val store = WorkspaceStore(context)
        assertEquals(WorkspaceLoadResult.Ready(expected), store.load())
        assertEquals(2, WorkspaceJson.decodeDocument(primary.readBytes()).sourceVersion)
        assertTrue(expected.onboarded)
        assertEquals(WorkspaceLoadResult.Ready(expected), WorkspaceStore(context).load())
        assertEquals(WriteOutcome.Committed, store.write(expected.copy(bio = "Test-only later save")))
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("githabit-recovery-") && it.readBytes().contentEquals(raw) })
        launch()
        waitFor("page-TODAY")
        compose.onNodeWithTag("local-welcome").assertDoesNotExist()
    }

    @Test fun productionAccessObjectsShareStorageOwnership() {
        assertSame(AtomicFileAccess(context).ownershipLock, AtomicFileAccess(context).ownershipLock)
    }

    @Test fun enteredDraftSurvivesRecoveryWhenPreviousWorkspaceIsReconciled() {
        val previous = Workspace(onboarded = true)
        val raw = WorkspaceJson.encode(previous).toByteArray()
        val real = AtomicFileAccess(context)
        real.writeBytes(raw)
        val faults = object : WorkspaceFileAccess by real {
            @Volatile var fail = false
            @Volatile var attempted = false
            override fun readBytes(): ByteArray? {
                if (fail && attempted) throw IOException("injected verification failure")
                return real.readBytes()
            }
            override fun writeBytes(bytes: ByteArray) {
                if (fail) { attempted = true; throw IOException("injected write/rollback failure") }
                real.writeBytes(bytes)
            }
        }
        launch()
        lateinit var vm: GitHabitViewModel
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(faults), SystemAppClock())
            injectedModels = ViewModelStore().also { it.put("injected", vm) }
            activity.setContent { GitHabitApp(vm) }
        }
        waitFor("page-TODAY")
        scenario!!.onActivity { vm.overlay = Overlay.RepoForm() }
        waitFor("repo-name")
        compose.onNodeWithTag("repo-name").performTextInput("Keep my entered draft")
        compose.onNodeWithTag("repo-target").performScrollTo().performTextInput("10")
        compose.onNodeWithTag("repo-unit").performScrollTo().performTextInput("minutes")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        faults.fail = true
        compose.onNodeWithTag("save-repo").performScrollTo().performClick()
        waitFor("recovery-retry")
        faults.fail = false
        compose.onNodeWithTag("recovery-retry").performScrollTo().performClick()
        waitFor("repo-name")
        compose.onNodeWithTag("repo-name").assertTextContains("Keep my entered draft")
        assertArrayEquals(raw, primary.readBytes())
    }
}
