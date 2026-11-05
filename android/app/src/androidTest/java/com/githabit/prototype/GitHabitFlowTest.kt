package com.githabit.prototype

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.githabit.prototype.data.WorkspaceStore
import com.githabit.prototype.data.WorkspaceJson
import com.githabit.prototype.data.WriteOutcome
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

/** Real Compose UI interactions against the same APK/persistence used outside tests. */
class GitHabitFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = WorkspaceStore(context)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var disposableVerified = false

    // AtomicFile.openRead performs recovery and may delete an in-flight .new file.
    // Observe only the atomically renamed base file while the app is writing.
    private fun persisted(): Workspace = WorkspaceJson.decode(context.getFileStreamPath("githabit-workspace.json").readText())

    /**
     * Destructive guard (fail-closed): these tests replace the target app's workspace, and a
     * separate test APK does not isolate personal data. They may only run on an emulator with
     * explicit opt-in, exact package/AVD match and a provisioned one-run device marker.
     * See docs/verification.md before running; a generic emulator opt-in is insufficient.
     * Any missing prerequisite aborts BEFORE the first target-context write.
     */
    private fun requireDisposableDevice() {
        verifyDisposableDevice()
        disposableVerified = true
    }

    @Before fun launchFreshWorkspace() {
        requireDisposableDevice()
        // No Activity exists yet: this test owner cannot race the application's store.
        store.load()
        // These existing screenshot/theme journeys explicitly start dark; the app's
        // fresh/reset default is SYSTEM, independent of this isolated test preference.
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace(theme = ThemePreference.DARK)))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("google-sign-in")
    }

    @After fun closeActivity() {
        if (!disposableVerified || !::scenario.isInitialized) return
        try { screenshot("end-${testName.methodName}") } finally { scenario.close() }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun awaitState(predicate: (Workspace) -> Boolean): Workspace {
        try {
            compose.waitUntil(15_000) { runCatching { predicate(persisted()) }.getOrDefault(false) }
        } catch (error: Throwable) {
            screenshot("failure")
            val state = persisted()
            throw AssertionError("Persisted state did not match: habits=${state.habits.map { it.name to it.versions.last() }}, commits=${state.commits.size}", error)
        }
        compose.waitForIdle()
        return persisted()
    }

    private fun screenshot(name: String) {
        check(disposableVerified) { "Refusing screenshot writes on an unverified device." }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // PixelCopy synchronizes each Compose root with the rendered window before
        // the full-device capture (which also includes system bars and sheet scrims).
        val roots = compose.onAllNodes(isRoot())
        repeat(roots.fetchSemanticsNodes().size) { roots[it].captureToImage() }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(350, 5_000)
        val folder = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun dismissNotice() {
        val dismiss = compose.onAllNodesWithContentDescription("Dismiss")
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
    }
    private fun clickText(text: String) { dismissNotice(); compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun clickTag(tag: String) { dismissNotice(); compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun sample() { clickTag("explore-demo"); waitForTag("page-TODAY") }

    @Test fun onboardingFractionalProgressCompletionUndoCorrectionAndDeletion() {
        clickTag("google-sign-in")
        clickText("Continue")
        clickText("Continue")
        clickText("Create my first repo")
        waitForTag("repo-name")
        compose.onNodeWithTag("repo-name").performTextInput("Practice guitar")
        compose.onNodeWithTag("repo-target").performTextReplacement("0.3")
        compose.onNodeWithTag("repo-unit").performTextReplacement("hours")
        closeSoftKeyboard()
        clickTag("save-repo")
        val habit = awaitState { it.habits.size == 1 }.habits.single()
        clickTag("log-${habit.id}")
        waitForTag("commit-amount")
        compose.onNodeWithTag("commit-amount").performTextInput("0.1")
        closeSoftKeyboard()
        clickTag("save-commit")
        awaitState { it.commits.size == 1 }
        clickTag("complete-${habit.id}")
        val completed = awaitState { it.progress(habit, LocalDate.now()).complete }
        assertEquals(0.2, completed.commits.last().amount, 0.0)
        compose.onNodeWithText("Undo").performClick()
        awaitState { it.commits.size == 1 }
        clickTag("complete-${habit.id}")
        val commit = awaitState { it.commits.size == 2 }.commits.last()
        clickTag("habit-${habit.id}")
        waitForTag("page-DETAIL")
        clickTag("edit-commit-${commit.id}")
        compose.onNodeWithTag("commit-amount").performTextReplacement("0.05")
        compose.onNodeWithTag("commit-note").performTextInput("Corrected practice time")
        closeSoftKeyboard()
        clickTag("save-commit")
        awaitState { it.commits.last().amount == 0.05 }
        compose.onNodeWithTag("detail-total").assertTextEquals("0.15")
        clickTag("edit-commit-${commit.id}")
        clickText("Delete this commit")
        compose.onNodeWithText("Delete commit").performClick()
        awaitState { it.commits.size == 1 }
        compose.onNodeWithText("Undo").performClick()
        awaitState { it.commits.size == 2 }
        assertEquals(0.15, persisted().progress(habit, LocalDate.now()).total, 0.0)
    }

    @Test fun sampleNavigationHeatmapThemeAndActivityRecreation() {
        sample()
        compose.onNodeWithTag("tab-REPOS").performClick()
        compose.onNodeWithTag("repo-search").performTextInput("Read")
        closeSoftKeyboard()
        clickTag("habit-sample-0")
        waitForTag("page-DETAIL")
        clickText("Explore by date")
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        compose.onNodeWithTag("close-sheet").performClick()
        compose.onNodeWithTag("tab-INSIGHTS").performClick()
        compose.onNodeWithText("The bigger picture").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").performClick()
        clickTag("theme-switch")
        awaitState { it.theme == com.githabit.prototype.domain.ThemePreference.LIGHT }
        scenario.recreate()
        waitForTag("page-SETTINGS")
        compose.onNodeWithTag("theme-switch").assertIsOff()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("page-TODAY")
        assertEquals(com.githabit.prototype.domain.ThemePreference.LIGHT, persisted().theme)
        assertEquals(6, persisted().habits.size)
    }

    @Test fun pauseArchiveResumeAndHistoricalGoals() {
        sample()
        val before = persisted()
        compose.onNodeWithTag("tab-REPOS").performClick()
        clickTag("habit-sample-0")
        clickText("Pause")
        awaitState { it.habits.first().versionOn(LocalDate.now())?.status == RepoStatus.PAUSED }
        clickText("Resume repo")
        awaitState { it.habits.first().versionOn(LocalDate.now())?.status == RepoStatus.ACTIVE }
        clickText("Edit repo")
        compose.onNodeWithTag("repo-unit").assertIsNotEnabled()
        compose.onNodeWithTag("repo-target").performTextReplacement("40")
        closeSoftKeyboard()
        clickTag("save-repo")
        val edited = awaitState { it.habits.first().versionOn(LocalDate.now())?.target == 40.0 }
        assertEquals(before.dailyScore(LocalDate.now().minusDays(1)), edited.dailyScore(LocalDate.now().minusDays(1)))
        clickText("Archive")
        compose.onNodeWithText("Archive repo").performClick()
        awaitState { it.habits.first().versionOn(LocalDate.now())?.status == RepoStatus.ARCHIVED }
        compose.onNodeWithTag("tab-REPOS").performClick()
        clickText("Archived")
        compose.onNodeWithTag("habit-sample-0").assertExists()
        clickText("Resume")
        awaitState { it.habits.first().versionOn(LocalDate.now())?.status == RepoStatus.ACTIVE }
        assertEquals(before.commits, persisted().commits)
    }

    @Test fun emailValidationSignOutResetAndNoCredentialsStored() {
        clickTag("email-sign-in")
        compose.onNodeWithText("Use a demo email and at least 6 made-up password characters.").assertExists()
        compose.onNodeWithTag("demo-email").performTextInput("demo@example.test")
        compose.onNodeWithTag("demo-password").performTextInput("made-up-123")
        closeSoftKeyboard()
        clickTag("email-sign-in")
        clickText("Skip introduction")
        clickText("Try it with sample habits first")
        waitForTag("page-TODAY")
        val raw = context.getFileStreamPath("githabit-workspace.json").readText()
        assertFalse(raw.contains("demo@example.test"))
        assertFalse(raw.contains("made-up-123"))
        compose.onNodeWithTag("open-settings").performClick()
        clickText("Sign out of the demo")
        waitForTag("google-sign-in")
        assertEquals(6, persisted().habits.size)
        clickTag("explore-demo")
        waitForTag("page-TODAY")
        compose.onNodeWithTag("open-settings").performClick()
        clickText("Reset this prototype")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(6, persisted().habits.size)
        clickText("Reset this prototype")
        compose.onNodeWithText("Reset everything").performClick()
        waitForTag("google-sign-in")
        assertEquals(Workspace(), persisted())
    }

    @Test fun screenshotJourneyAcrossNativeScreens() {
        screenshot("01-sign-in")
        clickTag("google-sign-in")
        compose.onNodeWithText("Create your first repo.").assertExists()
        screenshot("02-onboarding")
        clickText("Skip introduction")
        clickText("Try it with sample habits first")
        waitForTag("page-TODAY")
        screenshot("03-today-dark")
        clickTag("new-repo")
        waitForTag("repo-name")
        screenshot("12-new-repo")
        compose.onNodeWithTag("close-sheet").performClick()
        compose.onNodeWithTag("tab-REPOS").performClick()
        waitForTag("page-REPOS")
        screenshot("04-repos")
        clickTag("habit-sample-0")
        waitForTag("page-DETAIL")
        screenshot("05-repo-detail")
        clickText("Log progress")
        waitForTag("commit-amount")
        screenshot("06-commit-sheet")
        compose.onNodeWithTag("close-sheet").performClick()
        clickText("Explore by date")
        screenshot("07-day-details")
        compose.onNodeWithTag("close-sheet").performClick()
        compose.onNodeWithTag("tab-INSIGHTS").performClick()
        waitForTag("page-INSIGHTS")
        screenshot("08-insights")
        compose.onNodeWithContentDescription("Open activity").performClick()
        waitForTag("page-ACTIVITY")
        screenshot("09-activity")
        compose.onNodeWithTag("open-settings").performClick()
        waitForTag("page-SETTINGS")
        screenshot("10-settings")
        clickTag("theme-switch")
        awaitState { it.theme == com.githabit.prototype.domain.ThemePreference.LIGHT }
        compose.onNodeWithTag("tab-TODAY").performClick()
        waitForTag("page-TODAY")
        screenshot("11-today-light")
    }
}
