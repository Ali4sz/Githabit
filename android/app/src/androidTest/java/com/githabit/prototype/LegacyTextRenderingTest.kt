package com.githabit.prototype

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.displayPreview
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Rendered large-field/recreation evidence, not process-death or minimum-device certification. */
class LegacyTextRenderingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GitHabitViewModel
    private lateinit var original: Workspace

    @Before fun launch() {
        verifyDisposableDevice()
        val padding = " ".repeat(1_048_576)
        val day = LocalDate.of(2026, 1, 1)
        val repo = Habit("sample-edited-large", "Repo 🌱$padding", "Reason$padding", "units$padding",
            createdOn = day, versions = listOf(GoalVersion(day, 2.5)))
        original = Workspace(onboarded = true, name = "Owner 🌱$padding", habits = listOf(repo))
        val store = WorkspaceStore(context)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(original))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        bind()
        waitFor("page-TODAY")
    }

    @After fun close() { scenario?.close() }
    private fun bind() { scenario!!.onActivity {
        vm = ViewModelProvider(it, GitHabitViewModel.Factory)[GitHabitViewModel::class.java]
    } }
    private fun waitFor(tag: String) {
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        if (tag == "save-profile") hideActivityKeyboard() else closeSoftKeyboard()
        val dismiss = compose.onAllNodes(hasAnyAncestor(hasTestTag("notice-host")) and hasClickAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
    }
    private fun hideActivityKeyboard() {
        scenario!!.onActivity { activity ->
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(15_000) {
            var hidden = false
            scenario!!.onActivity { activity ->
                hidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            hidden
        }
        compose.waitForIdle()
    }
    private fun recreate() { scenario!!.recreate(); bind(); compose.waitForIdle() }
    private fun disk() = WorkspaceJson.decode(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).readBytes())
    private fun assertBoundedReadOnly(tag: String, original: String) {
        val node = compose.onNodeWithTag(tag)
        assertEquals(displayPreview(original, 4096), node.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        node.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        for (semantics in compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()) {
            assertTrue(semantics.config[SemanticsProperties.Text].all { it.length <= 8192 })
        }
    }

    @Test fun profilePreviewAndSmallDraftSurviveActivityRecreationWithoutSavingPreview() {
        compose.onAllNodesWithText(displayPreview(original.habits.single().name)).onFirst().assertExists()
        compose.onNodeWithTag("open-settings").performClick()
        waitFor("page-SETTINGS")
        assertBoundedReadOnly("profile-name", original.name)
        compose.onNodeWithTag("profile-bio").performScrollTo().performTextInput("Small explicit edit")
        closeSoftKeyboard()
        recreate()
        waitFor("page-SETTINGS")
        assertBoundedReadOnly("profile-name", original.name)
        compose.onNodeWithTag("profile-bio").assertTextContains("Small explicit edit")
        assertEquals(original, disk())
        click("save-profile")
        compose.waitUntil(60_000) { !vm.busy && vm.workspace.bio == "Small explicit edit" }
        assertEquals(original.copy(bio = "Small explicit edit"), disk())
        recreate()
        waitFor("page-SETTINGS")
        assertBoundedReadOnly("profile-name", original.name)
        assertEquals(original.name, vm.workspace.name)
    }

    @Test fun repoPreviewTargetEditAndExplicitReplacementKeepExactOriginalUntilCommit() {
        scenario!!.onActivity { vm.overlay = Overlay.RepoForm(original.habits.single().id) }
        waitFor("repo-name")
        assertBoundedReadOnly("repo-name", original.habits.single().name)
        assertBoundedReadOnly("repo-unit", original.habits.single().unit)
        compose.onNodeWithTag("repo-target").performScrollTo().performTextReplacement("3.5")
        closeSoftKeyboard()
        recreate()
        waitFor("repo-name")
        assertBoundedReadOnly("repo-name", original.habits.single().name)
        compose.onNodeWithTag("repo-target").assertTextContains("3.5")
        assertEquals(original, disk())
        click("save-repo")
        compose.waitUntil(60_000) { vm.overlay == null && !vm.busy }
        val committed = disk()
        assertEquals(original.name, committed.name)
        val repo = committed.habits.single()
        assertEquals(original.habits.single().name, repo.name)
        assertEquals(original.habits.single().description, repo.description)
        assertEquals(original.habits.single().unit, repo.unit)
        assertEquals(3.5, repo.versionOn(vm.today)!!.target, 0.0)
        scenario!!.onActivity { vm.overlay = Overlay.RepoForm(repo.id) }
        waitFor("repo-name")
        compose.onAllNodesWithText(context.getString(R.string.legacy_text_replace)).onFirst()
            .performScrollTo().assertIsDisplayed().performClick()
        assertEquals("", compose.onNodeWithTag("repo-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(committed, disk())
        click("close-sheet")
        compose.onNodeWithText(context.getString(R.string.discard_action)).performClick()
        compose.waitUntil(60_000) { vm.overlay == null }
        assertEquals(committed, disk())
    }
}
