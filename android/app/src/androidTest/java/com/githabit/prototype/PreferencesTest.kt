package com.githabit.prototype

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.GitHabitApp
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real storage and UI with test-owned time/faults, never a production clock/seed hook. */
class PreferencesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var models: ViewModelStore? = null
    private lateinit var vm: GitHabitViewModel
    private lateinit var inputMode: InputModeManager
    private val clock = object : AppClock {
        var day = LocalDate.of(2026, 11, 4)
        override fun today() = day
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = day.atTime(12, 0).atZone(zone()).toInstant()
    }
    private lateinit var access: WorkspaceFileAccess
    @Volatile private var failWrites = false
    @Volatile private var unknownOutcome = false
    @Volatile private var attemptedWrite = false
    private val locale get() = context.resources.configuration.locales[0]
    private fun date(day: LocalDate) = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    private fun weekday(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, locale)
    private fun persisted() = WorkspaceJson.decode(context.getFileStreamPath(AtomicFileAccess.FILE_NAME).readBytes())

    @Before fun authorize() {
        verifyDisposableDevice()
        val real = AtomicFileAccess(context)
        access = object : WorkspaceFileAccess by real {
            override fun readBytes(): ByteArray? {
                if (unknownOutcome && attemptedWrite) throw IOException("Test-only verification fault")
                return real.readBytes()
            }
            override fun writeBytes(bytes: ByteArray) {
                if (unknownOutcome) { attemptedWrite = true; throw IOException("Test-only indeterminate write") }
                if (failWrites) throw IOException("Test-only write fault")
                real.writeBytes(bytes)
            }
        }
    }

    private fun prepare(workspace: Workspace, fontScale: Float = 1f) {
        check(scenario == null)
        val store = WorkspaceStore(access)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(workspace))
        launch(fontScale)
    }

    private fun launch(fontScale: Float = 1f) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(access), clock)
            models = ViewModelStore().also { it.put("preferences", vm) }
            activity.setContent {
                inputMode = LocalInputModeManager.current
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { GitHabitApp(vm) }
            }
        }
        waitFor("page-TODAY")
        compose.onNodeWithTag("open-settings").performClick()
        waitFor("page-SETTINGS")
    }

    @After fun close() {
        scenario?.onActivity { models?.clear() }
        scenario?.close()
        scenario = null
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        val dismiss = compose.onAllNodesWithContentDescription("Dismiss")
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun awaitState(predicate: (Workspace) -> Boolean) {
        compose.waitUntil(15_000) { runCatching { predicate(persisted()) }.getOrDefault(false) }
        compose.waitForIdle()
    }
    private fun savedText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("calendar-saved")))
    private fun populated() = Workspace(onboarded = true)
        .createHabit(HabitDraft("Practice", "", 10.0, "minutes"), LocalDate.of(2026, 11, 2), "calendar-repo")

    @OptIn(ExperimentalTestApi::class)
    @Test fun allSevenWeekdaysHaveFullLabelsRadioSemanticsAndKeyboardActivationAtLargeText() {
        prepare(Workspace(onboarded = true), fontScale = 2f)
        for (day in DayOfWeek.entries) {
            val choice = compose.onNodeWithTag("week-start-${day.name}").performScrollTo()
            choice.assertTextContains(weekday(day)).assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
            choice.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            choice.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            choice.assertIsSelected()
            val before = persisted()
            if (before.calendar.initialWeekStart != day) {
                click("save-week-start")
                awaitState { it.calendar.initialWeekStart == day }
            } else compose.onNodeWithTag("save-week-start").assertIsNotEnabled()
            assertTrue(persisted().habits.isEmpty())
            assertTrue(persisted().commits.isEmpty())
            assertNull(persisted().calendar.pending)
        }
        close()
        launch()
        compose.onNodeWithTag("week-start-SUNDAY").assertIsSelected()
    }

    @Test fun savedPendingReplacementCancellationAndFixedBridgeUseExactDomainDates() {
        val original = populated()
        prepare(original)
        val boundary = LocalDate.of(2026, 11, 9)
        val sunday = LocalDate.of(2026, 11, 15)
        click("week-start-SUNDAY")
        assertEquals(original, persisted())
        savedText(context.getString(R.string.calendar_effective, weekday(DayOfWeek.MONDAY))).assertExists()
        click("save-week-start")
        awaitState { it.calendar.pending == WeekTransition(boundary, DayOfWeek.SUNDAY) }
        savedText(context.getString(R.string.calendar_pending, weekday(DayOfWeek.SUNDAY), date(boundary))).assertExists()
        savedText(context.getString(R.string.calendar_first_full_week, date(sunday))).assertExists()
        close()
        launch()
        click("week-start-TUESDAY")
        click("save-week-start")
        awaitState { it.calendar.pending == WeekTransition(boundary, DayOfWeek.TUESDAY) }
        click("review-calendar-cancellation")
        assertNotNull(persisted().calendar.pending)
        click("save-week-start")
        awaitState { it.calendar.pending == null }
        click("week-start-SUNDAY")
        click("save-week-start")
        awaitState { it.calendar.pending?.weekStart == DayOfWeek.SUNDAY }
        click("week-start-TUESDAY") // An unsaved choice must survive the saved request activating.
        scenario!!.onActivity { clock.day = boundary.plusDays(1); vm.refreshDate() }
        awaitState { it.calendar.transitions.size == 1 }
        compose.onNodeWithTag("week-start-TUESDAY").assertIsSelected()
        compose.onNodeWithTag("calendar-preview").assertExists()
        val activeBridge = context.getString(R.string.calendar_active_bridge, date(boundary), date(sunday.minusDays(1)))
        savedText(activeBridge).assertExists()
        savedText(context.getString(R.string.calendar_effective, weekday(DayOfWeek.SUNDAY))).assertExists()
        click("week-start-MONDAY")
        click("save-week-start")
        awaitState { it.calendar.pending == WeekTransition(sunday, DayOfWeek.MONDAY) }
        savedText(activeBridge).assertExists()
        savedText(context.getString(R.string.calendar_first_full_week, date(sunday.plusDays(1)))).assertExists()
        close()
        launch()
        savedText(activeBridge).assertExists()
        click("review-calendar-cancellation")
        click("save-week-start")
        awaitState { it.calendar.pending == null }
        savedText(activeBridge).assertExists()
        savedText(context.getString(R.string.calendar_first_full_week, date(sunday))).assertExists()
        assertEquals(original.habits, persisted().habits)
        assertEquals(original.commits, persisted().commits)
    }

    @Test fun staleDateRequiresReviewAndFailedCalendarSaveRetainsDraftAndCommittedState() {
        clock.day = LocalDate.of(2026, 11, 8)
        val original = populated()
        prepare(original)
        click("week-start-SUNDAY")
        scenario!!.onActivity { clock.day = clock.day.plusDays(1) }
        click("save-week-start")
        waitFor("calendar-date-review")
        assertEquals(original, persisted())
        compose.onNodeWithTag("week-start-SUNDAY").assertIsSelected()
        failWrites = true
        click("save-week-start")
        compose.waitUntil(15_000) { !vm.busy }
        assertEquals(original, persisted())
        compose.onNodeWithTag("week-start-SUNDAY").assertIsSelected()
        savedText(context.getString(R.string.calendar_effective, weekday(DayOfWeek.MONDAY))).assertExists()
        failWrites = false
        click("save-week-start")
        awaitState { it.calendar.pending == WeekTransition(LocalDate.of(2026, 11, 16), DayOfWeek.SUNDAY) }
        compose.onNodeWithTag("calendar-preview").assertDoesNotExist()
        compose.onNodeWithTag("calendar-date-review").assertDoesNotExist()
    }

    @Test fun profileAndThemeFailureDoNotPublishAndProfileDraftCanBeRetried() {
        val original = Workspace(onboarded = true)
        prepare(original)
        compose.onNodeWithTag("profile-name").performTextInput("Retained draft")
        closeSoftKeyboard()
        failWrites = true
        click("save-profile")
        compose.waitUntil(15_000) { !vm.busy }
        assertEquals(original, persisted())
        compose.onNodeWithTag("profile-name").assertTextContains("Retained draft")
        click("theme-DARK")
        compose.waitUntil(15_000) { !vm.busy }
        compose.onNodeWithTag("theme-SYSTEM").assertIsSelected()
        assertEquals(original, persisted())
        failWrites = false
        click("save-profile")
        awaitState { it.name == "Retained draft" }
        assertTrue(persisted().habits.isEmpty())
        assertTrue(persisted().commits.isEmpty())
    }

    @Test fun normalizedNoOpProfileSaveAcknowledgesDraftWithoutWritingHistory() {
        prepare(Workspace(onboarded = true))
        compose.onNodeWithTag("profile-name").performTextInput("   ")
        closeSoftKeyboard()
        click("save-profile")
        compose.onNodeWithTag("profile-name").assertTextContains("")
        compose.onNodeWithTag("save-profile").assertIsNotEnabled()
        assertEquals(Workspace(onboarded = true), persisted())
    }

    @Test fun paddedLegacyNameShowsNonnegativeLimitAndSurvivesUnrelatedProfileEdit() {
        val original = Workspace(onboarded = true, name = " " + "n".repeat(40) + " ")
        prepare(original)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.characters_remaining, 0, 0)).assertExists()
        compose.onNodeWithTag("profile-bio").performScrollTo().performTextInput("Updated note")
        closeSoftKeyboard()
        click("save-profile")
        awaitState { it.bio == "Updated note" }
        assertEquals(original.copy(bio = "Updated note"), persisted())
    }

    @Test fun indeterminateSaveRetainsProfileAndCalendarDraftsWhenPreviousStateIsVerified() {
        val original = populated()
        prepare(original)
        compose.onNodeWithTag("profile-name").performTextInput("Keep this profile draft")
        closeSoftKeyboard()
        click("week-start-TUESDAY")
        unknownOutcome = true
        click("save-week-start")
        waitFor("recovery-retry")
        compose.onNodeWithTag("page-SETTINGS").assertDoesNotExist()
        assertEquals(original, persisted())
        unknownOutcome = false
        click("recovery-retry")
        waitFor("page-SETTINGS")
        compose.onNodeWithTag("profile-name").assertTextContains("Keep this profile draft")
        compose.onNodeWithTag("week-start-TUESDAY").assertIsSelected()
        compose.onNodeWithTag("calendar-preview").assertExists()
        assertEquals(original, persisted())
        click("save-week-start")
        awaitState { it.calendar.pending?.weekStart == DayOfWeek.TUESDAY }
        assertEquals("", persisted().name) // Calendar save does not submit the profile draft.
    }
}
