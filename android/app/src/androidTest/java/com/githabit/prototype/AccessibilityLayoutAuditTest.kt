package com.githabit.prototype

import android.content.res.Configuration
import android.os.LocaleList
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.githabit.prototype.data.*
import com.githabit.prototype.domain.*
import com.githabit.prototype.fixtures.verifyDisposableDevice
import com.githabit.prototype.ui.GitHabitApp
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Supporting semantics/layout evidence, explicitly not actual TalkBack or hardware-keyboard certification. */
class AccessibilityLayoutAuditTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val models = ViewModelStore()
    private lateinit var vm: GitHabitViewModel
    private lateinit var host: ComposeView
    private val clock = object : AppClock {
        override fun today() = LocalDate.parse("2026-11-04")
        override fun zone() = ZoneId.of("UTC")
        override fun now() = today().atTime(12, 0).atZone(zone()).toInstant()
    }
    @Before fun guard() { verifyDisposableDevice() }
    @After fun close() { scenario?.onActivity { models.clear() }; scenario?.close() }
    private fun launch(locale: String, scale: Float, theme: ThemePreference) {
        val store = WorkspaceStore(context)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(Workspace(onboarded = true, theme = theme)))
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            vm = GitHabitViewModel(context, WorkspaceStore(context), clock)
            models.put("audit", vm)
            val configuration = Configuration(activity.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(locale))); fontScale = scale }
            val localized = ContextThemeWrapper(activity, activity.theme).apply {
                applyOverrideConfiguration(configuration)
            }
            // Dialog obtains LocalView.context, not an outer LocalContext override.
            host = ComposeView(localized)
            activity.setContentView(host)
            host.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides activity) { GitHabitApp(vm) }
            }
            assertEquals(locale, host.context.resources.configuration.locales[0].toLanguageTag())
            assertEquals(scale, host.context.resources.configuration.fontScale, 0f)
        }
        waitFor("page-TODAY")
    }
    private fun waitFor(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        closeSoftKeyboard()
        compose.waitForIdle()
        // Material localizes its dismiss description too. Select only the
        // described icon button inside our notice host, never an English label.
        val dismiss = compose.onAllNodes(hasAnyAncestor(hasTestTag("notice-host")) and hasClickAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) dismiss.onFirst().performClick()
        compose.waitForIdle()
        // Short windows may not compose the empty-state action until its lazy
        // item is brought into view. Do not equate absence with clipping.
        if (tag == "new-repo" && compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag(tag))
        }
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }
    private fun journey(locale: String, scale: Float, theme: ThemePreference) {
        launch(locale, scale, theme)
        click("new-repo")
        waitFor("repo-name")
        assertEquals(scale, compose.onNodeWithTag("workspace-sheet").fetchSemanticsNode().layoutInfo.density.fontScale, 0f)
        assertModalText("workspace-sheet", R.string.repo_form_new)
        click("save-repo")
        compose.onNodeWithTag("form-error").performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("repo-name").performTextInput("Synthetic QA")
        compose.onNodeWithTag("repo-target").performScrollTo().performTextInput("1,25")
        compose.onNodeWithTag("repo-unit").performScrollTo().performTextInput("units")
        click("schedule-WEEKDAYS")
        compose.onNodeWithTag("weekday-MONDAY").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff().performClick().assertIsOn()
        click("weekday-WEDNESDAY")
        click("save-repo")
        compose.waitUntil(30_000) { vm.overlay == null && !vm.busy }
        compose.waitForIdle()
        val habit = vm.workspace.habits.single()
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY), (habit.versions.single().schedule as HabitSchedule.SelectedWeekdays).days)
        click("log-${habit.id}")
        try { waitFor("commit-amount") } catch (error: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("QA-locale-failure")
            throw error
        }
        compose.onNodeWithTag("commit-amount").performTextInput("0.625")
        click("save-commit")
        compose.waitUntil(30_000) { vm.overlay == null && !vm.busy }
        compose.waitForIdle()
        assertEquals(.625, vm.workspace.commits.single().amount, 0.0)
        compose.runOnIdle { vm.overlay = Overlay.Day(clock.today(), habit.id) }
        waitFor("date-inspector")
        compose.onNodeWithTag("date-inspector").performScrollToNode(hasTestTag("date-summary-${habit.id}"))
        compose.onNodeWithTag("date-summary-${habit.id}").assertExists()
        compose.runOnIdle { vm.overlay = null; vm.requestReset() }
        waitFor("reset-preview")
        assertEquals(scale, compose.onNodeWithTag("reset-preview").fetchSemanticsNode().layoutInfo.density.fontScale, 0f)
        assertModalText("reset-preview", R.string.reset_title)
        assertModalText("reset-preview", R.string.reset_replace_warning)
        assertModalText("reset-preview", R.string.transfer_preservation)
        compose.onNodeWithTag("confirm-reset").assertTextContains(host.context.getString(R.string.reset_confirm))
        compose.onNode(hasText(host.context.resources.getQuantityString(R.plurals.transfer_repos, 1, 1)) and
            hasAnyAncestor(hasTestTag("reset-preview"))).assertExists()
        compose.onNodeWithTag("confirm-reset").performScrollTo().assertIsDisplayed().assertIsEnabled()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(context.cacheDir, "locale-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        click("cancel-reset")
        assertEquals(1, vm.workspace.commits.size)
        val snapshot = vm.workspace
        compose.runOnIdle {
            val request = requireNotNull(vm.requestRestore())
            vm.readRestore(request) { WorkspaceJson.encodeToBytes(snapshot).inputStream() }
        }
        waitFor("restore-preview")
        assertEquals(scale, compose.onNodeWithTag("restore-preview").fetchSemanticsNode().layoutInfo.density.fontScale, 0f)
        assertModalText("restore-preview", R.string.restore_preview_title)
        assertModalText("restore-preview", R.string.restore_replace_warning)
        assertModalText("restore-preview", R.string.transfer_preservation)
        compose.onNodeWithTag("confirm-restore").assertTextContains(host.context.getString(R.string.restore_confirm))
        compose.onAllNodes(hasText(host.context.resources.getQuantityString(R.plurals.transfer_commits, 1, 1)) and
            hasAnyAncestor(hasTestTag("restore-preview"))).assertCountEquals(2)
        click("cancel-restore")
        assertEquals(snapshot, vm.workspace)
    }
    private fun assertModalText(tag: String, resource: Int) {
        compose.onNode(hasText(host.context.getString(resource)) and hasAnyAncestor(hasTestTag(tag))).assertExists()
    }
    @Test fun expandedEnglishPseudoLocaleAtTwoHundredPercentDark() = journey("en-XA", 2f, ThemePreference.DARK)
    @Test fun rtlPseudoLocaleAtTwoHundredPercentLight() = journey("ar-XB", 2f, ThemePreference.LIGHT)
}
