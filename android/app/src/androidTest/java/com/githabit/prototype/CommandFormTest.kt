package com.githabit.prototype

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.GitHabitApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test-owned clock and fixtures; no installed-app clock/seed entry point. */
class CommandFormTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val models = ViewModelStore()
    private lateinit var vm: GitHabitViewModel
    private val clock = object : AppClock {
        var instant = Instant.parse("2026-11-04T23:59:59Z")
        override fun now() = instant
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today(): LocalDate = instant.atZone(zone()).toLocalDate()
    }

    @Before fun launchIsolatedWorkspace() {
        verifyDisposableDevice() // Must precede every target-context write or Activity launch.
        val initial = Workspace(onboarded = true).createHabit(HabitDraft("Practice", "", 10.0, "minutes"), clock.today(), "habit")
        val store = WorkspaceStore(context)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(initial))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(context), clock)
            models.put("forms", vm)
            activity.setContent { GitHabitApp(vm) }
        }
        waitFor("page-TODAY")
    }

    @After fun close() {
        scenario?.onActivity { models.clear() }
        scenario?.close()
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun clickSave(tag: String) {
        closeSoftKeyboard()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(15_000) { !vm.busy }
        compose.waitForIdle()
    }

    @Test fun staleLogRetainsDraftAndRequiresSecondSubmissionOnNewDate() {
        scenario!!.onActivity { vm.overlay = Overlay.LogForm("habit") }
        waitFor("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextInput("4")
        compose.onNodeWithTag("commit-note").performTextInput("Retain this draft")
        scenario!!.onActivity { clock.instant = clock.instant.plusSeconds(1); vm.refreshDate() }
        clickSave("save-commit")
        compose.onNodeWithTag("commit-amount").assertTextContains("4")
        compose.onNodeWithTag("commit-note").assertTextContains("Retain this draft")
        compose.onNodeWithText("Review your draft and submit again.", substring = true).assertExists()
        scenario!!.onActivity { assertTrue(vm.workspace.commits.isEmpty()) }
        clickSave("save-commit")
        scenario!!.onActivity {
            assertEquals(LocalDate.parse("2026-11-05"), vm.workspace.commits.single().date)
            assertEquals("Retain this draft", vm.workspace.commits.single().note)
            assertNull(vm.overlay)
        }
    }

    @Test fun staleHabitEditRetainsTargetAndHistoryUntilDateIsReviewed() {
        scenario!!.onActivity { vm.overlay = Overlay.RepoForm("habit") }
        waitFor("repo-target")
        compose.onNodeWithTag("repo-target").performTextReplacement("20")
        scenario!!.onActivity { clock.instant = clock.instant.plusSeconds(1); vm.refreshDate() }
        clickSave("save-repo")
        compose.onNodeWithTag("repo-target").assertTextContains("20")
        scenario!!.onActivity { assertEquals(1, vm.workspace.habits.single().versions.size) }
        clickSave("save-repo")
        scenario!!.onActivity {
            val habit = vm.workspace.habits.single()
            assertEquals(10.0, habit.versionOn(LocalDate.parse("2026-11-04"))!!.target, 0.0)
            assertEquals(20.0, habit.versionOn(LocalDate.parse("2026-11-05"))!!.target, 0.0)
        }
    }
}
