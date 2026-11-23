package com.githabit.prototype

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.*
import com.githabit.prototype.ui.GitHabitApp
import java.io.File
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LongHistoryPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val models = ViewModelStore()
    private lateinit var vm: GitHabitViewModel
    private val clock = object : AppClock {
        override fun today() = LongHistoryFixture.today
        override fun zone() = ZoneId.of("UTC")
        override fun now() = today().atTime(12, 0).atZone(zone()).toInstant()
    }
    @Before fun guard() { verifyDisposableDevice() }
    @After fun close() {
        scenario?.onActivity { models.clear() }
        scenario?.close()
    }
    private fun ready() {
        compose.waitUntil(120_000) { ::vm.isInitialized && vm.load is WorkspaceLoad.Ready && !vm.busy }
        compose.waitForIdle()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(context), clock)
            models.put("long", vm)
            activity.setContent { GitHabitApp(vm) }
        }
        ready()
        compose.onNodeWithTag("page-TODAY").assertExists()
    }
    private fun dismissNotice() {
        val dismiss = compose.onAllNodesWithContentDescription("Dismiss")
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
    }
    @Test fun fiveYearWorkspaceMeasuredLifecyclePreservesEveryRecord() {
        val fixture = LongHistoryFixture.create()
        Log.i("GitHabitMetrics", "fixture repos=${fixture.habits.size} commits=${fixture.commits.size} " +
            "configurations=${fixture.habits.sumOf { it.versions.size } + 1} bytes=${WorkspaceJson.encodedSize(fixture)}")
        val store = WorkspaceStore(context)
        store.load()
        OperationMetrics().use { metrics ->
            metrics.measure("fixture-verified-save") { assertEquals(WriteOutcome.Committed, store.replaceVerified(fixture)) }
            metrics.measure("activity-launch-to-ready") { launch() }
            metrics.measure("index-and-summaries") {
                val analytics = ProgressAnalytics(fixture, clock.today())
                assertEquals(fixture.commits.size, analytics.commits.history().size)
                assertEquals(20, analytics.weekSummary(clock.today()).habits.size)
                assertEquals(52, analytics.heatmap(52).size)
                assertEquals(20, analytics.dateSummary(clock.today().minusDays(1)).habits.size)
            }
        }
        lateinit var frames: OperationMetrics
        scenario!!.onActivity { frames = OperationMetrics(it.window) }
        frames.use { metrics ->
            listOf(Destination.TODAY to "today-list", Destination.REPOS to "repo-list-pane",
                Destination.ACTIVITY to "activity-history", Destination.DETAIL to "repo-detail-pane").forEach { (destination, tag) ->
                compose.runOnIdle { vm.navigate(destination, LongHistoryFixture.FIRST_ID.takeIf { destination == Destination.DETAIL }, mainTab = destination != Destination.DETAIL) }
                compose.waitForIdle()
                metrics.measure("scroll-$destination-6-swipes") {
                    repeat(6) { compose.onNodeWithTag(tag).performTouchInput { swipeUp() }; compose.waitForIdle() }
                }
            }
            metrics.measure("log-to-verified-published") {
                compose.runOnIdle { vm.saveCommit(LongHistoryFixture.FIRST_ID, null, 1.25, "QA measured log") }
                ready()
                assertEquals(fixture.commits.size + 1, vm.workspace.commits.size)
            }
            dismissNotice()
            val saved = vm.workspace.commits.last()
            metrics.measure("correction-to-verified-published") {
                compose.runOnIdle { vm.saveCommit(saved.habitId, saved.id, 2.5, "QA measured correction") }
                ready()
                assertEquals(2.5, vm.workspace.commits.last().amount, 0.0)
            }
            metrics.measure("undo-through-snackbar-to-verified-published") {
                compose.onNodeWithText("Undo").performClick()
                ready()
                assertEquals(1.25, vm.workspace.commits.last().amount, 0.0)
            }
            dismissNotice()
            val snapshot = vm.workspace
            val output = File(context.cacheDir, "long-history-qa-export.json")
            try {
                var exportId: String? = null
                metrics.measure("export-freeze-to-picker-ready") {
                    compose.runOnIdle { vm.prepareExport { id, _ -> exportId = id } }
                    compose.waitUntil(120_000) { exportId != null }
                }
                metrics.measure("export-output-to-closed-stream") {
                    compose.runOnIdle { vm.writeExport(exportId) { output.outputStream() } }
                    compose.waitUntil(120_000) { !vm.exportInProgress }
                    assertEquals(snapshot, WorkspaceJson.decode(output.readBytes()))
                }
                metrics.measure("restore-read-to-validated-preview") {
                    compose.runOnIdle { val id = vm.requestRestore()!!; vm.readRestore(id) { output.inputStream() } }
                    compose.waitUntil(120_000) { vm.restoreState is RestoreState.Preview }
                }
                metrics.measure("restore-confirm-to-verified-published") {
                    compose.runOnIdle { vm.confirmRestore(vm.restoreState as RestoreState.Preview) }
                    ready()
                    assertEquals(snapshot, vm.workspace)
                    assertNull(vm.restoreState)
                }
                metrics.measure("close-and-reopen-to-ready") {
                    scenario!!.onActivity { models.clear() }
                    scenario!!.close()
                    launch()
                    assertEquals(snapshot, vm.workspace)
                }
                assertEquals(snapshot, WorkspaceJson.decode(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).readBytes()))
            } finally { output.delete() }
        }
    }
}
