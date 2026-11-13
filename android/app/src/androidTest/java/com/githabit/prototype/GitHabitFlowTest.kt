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
import com.githabit.prototype.data.AtomicFileAccess
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.fixtures.AuthorizedTestTarget
import com.githabit.prototype.fixtures.WorkspaceFixtures
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
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var disposableVerified = false
    private var screenshotsAllowed = false

    // AtomicFile.openRead performs recovery and may delete an in-flight .new file.
    // Observe only the atomically renamed base file while the app is writing.
    private fun persisted(): Workspace = WorkspaceJson.decode(context.getFileStreamPath("githabit-workspace.json").readText())

    /**
     * Destructive guard (fail-closed): these tests replace the target app's workspace, and a
     * separate test APK does not isolate personal data. Require exact device/package binding,
     * explicit reset consent and a provisioned session marker. Physical phones cannot capture screenshots.
     * See docs/verification.md before running; a generic emulator opt-in is insufficient.
     * Any missing prerequisite aborts BEFORE the first target-context write.
     */
    private fun requireDisposableDevice() {
        screenshotsAllowed = verifyDisposableDevice() == AuthorizedTestTarget.DISPOSABLE_EMULATOR
        disposableVerified = true
    }

    @Before fun authorizeWorkspace() {
        requireDisposableDevice()
    }

    private fun launch(workspace: Workspace? = null) {
        check(disposableVerified && !::scenario.isInitialized)
        // No Activity exists yet: this test owner cannot race the application's store.
        if (workspace == null) {
            context.filesDir.listFiles()?.filter {
                it.name.startsWith(AtomicFileAccess.FILE_NAME) ||
                    it.name.startsWith("githabit-recovery-") || it.name == "githabit-last-good.json"
            }?.forEach { check(it.delete()) { "Could not remove disposable workspace file ${it.name}" } }
        } else {
            val store = WorkspaceStore(context)
            store.load()
            assertEquals(WriteOutcome.Committed, store.replaceVerified(workspace))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag(if (workspace != null && (workspace.onboarded || workspace.habits.isNotEmpty())) "page-TODAY" else "local-welcome")
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
        // Never capture unrelated notifications, system overlays or personal phone content.
        // Return before any capture call, including failure and teardown screenshots.
        if (!screenshotsAllowed) return
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
    private fun launchFixture() = launch(WorkspaceFixtures.populated(LocalDate.now()))

    @Test fun onboardingFractionalProgressCompletionUndoCorrectionAndDeletion() {
        launch()
        clickTag("create-first-repo")
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

    @Test fun fixtureNavigationHeatmapThemeAndActivityRecreation() {
        launchFixture()
        compose.onNodeWithTag("tab-REPOS").performClick()
        compose.onNodeWithTag("repo-search").performTextInput("Read")
        closeSoftKeyboard()
        clickTag("habit-${WorkspaceFixtures.READING_ID}")
        waitForTag("page-DETAIL")
        clickText("Explore by date")
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        compose.onNodeWithTag("close-sheet").performClick()
        compose.onNodeWithTag("tab-INSIGHTS").performClick()
        compose.onNodeWithText("The bigger picture").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").performClick()
        clickTag("theme-LIGHT")
        awaitState { it.theme == com.githabit.prototype.domain.ThemePreference.LIGHT }
        scenario.recreate()
        waitForTag("page-SETTINGS")
        compose.onNodeWithTag("theme-LIGHT").assertIsSelected()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("page-TODAY")
        assertEquals(com.githabit.prototype.domain.ThemePreference.LIGHT, persisted().theme)
        assertEquals(2, persisted().habits.size)
    }

    @Test fun pauseArchiveResumeAndHistoricalGoals() {
        launchFixture()
        val before = persisted()
        compose.onNodeWithTag("tab-REPOS").performClick()
        clickTag("habit-${WorkspaceFixtures.READING_ID}")
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
        compose.onNodeWithTag("habit-${WorkspaceFixtures.READING_ID}").assertExists()
        clickText("Resume")
        awaitState { it.habits.first().versionOn(LocalDate.now())?.status == RepoStatus.ACTIVE }
        assertEquals(before.commits, persisted().commits)
    }

    @Test fun cancelledResetKeepsHistoryAndConfirmedResetStaysEmpty() {
        launchFixture()
        val before = persisted()
        compose.onNodeWithTag("open-settings").performClick()
        clickText("Reset workspace")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, persisted())
        clickText("Reset workspace")
        compose.onNodeWithText("Reset everything").performClick()
        waitForTag("local-welcome")
        assertEquals(Workspace(), persisted())
        scenario.recreate()
        waitForTag("local-welcome")
        assertEquals(Workspace(), persisted())
    }

    @Test fun welcomeHelpPreferencesAndCancelledFormsNeverCreateHistory() {
        launch()
        assertFalse(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).exists())
        screenshot("01-local-welcome-empty")
        clickTag("create-first-repo")
        waitForTag("repo-name")
        compose.onNodeWithTag("repo-name").performTextInput("Unsaved intention")
        closeSoftKeyboard()
        clickTag("close-sheet")
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Discard draft").performClick()
        assertEquals(Workspace(onboarded = true), persisted())
        screenshot("02-empty-workspace")
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("profile-heading").assertTextEquals("Your workspace")
        compose.onNodeWithTag("profile-name").performTextInput("Chosen name")
        compose.onNodeWithTag("profile-bio").performScrollTo().performTextInput("Chosen note")
        closeSoftKeyboard()
        scenario.recreate()
        waitForTag("page-SETTINGS")
        compose.onNodeWithTag("profile-name").assertTextContains("Chosen name")
        clickTag("save-profile")
        awaitState { it.name == "Chosen name" && it.bio == "Chosen note" }
        for (theme in listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM)) {
            clickTag("theme-${theme.name}")
            awaitState { it.theme == theme }
        }
        clickTag("week-start-SUNDAY")
        assertEquals(java.time.DayOfWeek.MONDAY, persisted().calendar.initialWeekStart)
        scenario.recreate()
        waitForTag("page-SETTINGS")
        compose.onNodeWithTag("week-start-SUNDAY").assertIsSelected()
        compose.onNodeWithTag("calendar-preview").assertExists()
        assertEquals(java.time.DayOfWeek.MONDAY, persisted().calendar.initialWeekStart)
        clickTag("save-week-start")
        awaitState { it.calendar.initialWeekStart == java.time.DayOfWeek.SUNDAY }
        clickTag("open-guide")
        clickTag("close-sheet")
        clickTag("profile-name")
        compose.onNodeWithTag("profile-name").performTextClearance()
        compose.onNodeWithTag("profile-bio").performScrollTo().performTextClearance()
        closeSoftKeyboard()
        clickTag("save-profile")
        awaitState { it.name.isEmpty() && it.bio.isEmpty() }
        for (tab in listOf("REPOS", "INSIGHTS", "TODAY")) {
            compose.onNodeWithTag("tab-$tab").performClick()
            waitForTag("page-$tab")
        }
        clickTag("new-repo")
        clickTag("close-sheet")
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("page-TODAY")
        assertEquals(Workspace(onboarded = true, calendar = WeekCalendar(java.time.DayOfWeek.SUNDAY)), persisted())
    }

    @Test fun screenshotJourneyAcrossNativeScreens() {
        launchFixture()
        screenshot("03-today-dark")
        clickTag("new-repo")
        waitForTag("repo-name")
        screenshot("12-new-repo")
        compose.onNodeWithTag("close-sheet").performClick()
        compose.onNodeWithTag("tab-REPOS").performClick()
        waitForTag("page-REPOS")
        screenshot("04-repos")
        clickTag("habit-${WorkspaceFixtures.READING_ID}")
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
        clickTag("theme-LIGHT")
        awaitState { it.theme == com.githabit.prototype.domain.ThemePreference.LIGHT }
        compose.onNodeWithTag("tab-TODAY").performClick()
        waitForTag("page-TODAY")
        screenshot("11-today-light")
    }
}
