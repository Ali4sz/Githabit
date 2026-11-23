package com.githabit.prototype.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.githabit.prototype.Destination
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.WorkspaceLoad
import com.githabit.prototype.R
import kotlinx.coroutines.launch

internal val BackPreviewProgress = SemanticsPropertyKey<Float>("BackPreviewProgress")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHabitApp(vm: GitHabitViewModel) {
    // Recovery hides UI, but only a verified replacement may dispose of its drafts.
    val savedState = key(vm.workspaceSession) { rememberSaveableStateHolder() }
    val inspection = when (val overlay = vm.overlay) {
        is com.githabit.prototype.Overlay.Day -> overlay
        is com.githabit.prototype.Overlay.LogForm -> overlay.returnTo
        else -> null
    }
    val sheetKey = if (inspection != null) "inspection:${inspection.habitId.orEmpty()}" else vm.overlay
    val sheetState = key(vm.workspaceSession, sheetKey) { rememberSaveableStateHolder() }
    var detailKeys by key(vm.workspaceSession) { rememberSaveable { mutableStateOf(emptyList<String>()) } }
    LaunchedEffect(vm.navigation, savedState) {
        val next = vm.navigation.retainedDetailKeys(detailKeys)
        (detailKeys - next.toSet()).forEach(savedState::removeState)
        detailKeys = next
    }
    val dark = vm.workspace.theme.isDark(isSystemInDarkTheme())
    GitHabitSystemBars(dark)
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    // Notice translation runs in composition while the original command diagnostic
    // remains intact for recovery/tests. Snackbar action handling stays ID-based.
    var pendingNotice by remember { mutableStateOf<com.githabit.prototype.Notice?>(null) }
    var noticeSequence by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm, undoLabel) {
        vm.notices.collect { notice ->
            pendingNotice = notice
            noticeSequence++
        }
    }
    val renderedNotice = pendingNotice?.let { diagnosticMessage(it.diagnostic) }
    LaunchedEffect(noticeSequence, renderedNotice, undoLabel) {
        pendingNotice?.let { notice ->
            snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(requireNotNull(renderedNotice), actionLabel = notice.undoId?.let { undoLabel },
                    withDismissAction = true, duration = if (notice.undoId == null) SnackbarDuration.Short else SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) notice.undoId?.let(vm::undo)
        }
    }
    // Unconditional registration and small correlation tokens survive Activity recreation.
    // Payloads and consent stay in the ViewModel only; late process-death results are ignored.
    var exportRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        vm.completeExport(exportRequest, uri)
        exportRequest = null
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreRequest?.let { vm.restoreSelected(it, uri) }
        restoreRequest = null
    }
    val onExport: () -> Unit = {
        vm.prepareExport(recovery = vm.load is WorkspaceLoad.Recovery) { id, filename ->
            exportRequest = id
            try { export.launch(filename) } catch (_: Exception) { vm.completeExport(id, null); exportRequest = null }
        }
    }
    val onRestore: () -> Unit = {
        vm.requestRestore()?.let { id ->
            restoreRequest = id
            try { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: Exception) { vm.restoreSelected(id, null); restoreRequest = null }
        }
    }
    val load = vm.load
    val recovery = load as? WorkspaceLoad.Recovery
    val workspaceOpen = load is WorkspaceLoad.Ready && !vm.needsWelcome
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navigationType = if (workspaceOpen) NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        else NavigationSuiteType.None
    var backProgress by remember { mutableFloatStateOf(0f) }
    // Always compose this handler. Dialog/sheet windows retain their standard, higher-priority Back.
    // No route/history changes occur while collecting; cancellation only resets presentation.
    PredictiveBackHandler(enabled = workspaceOpen && vm.overlay == null && vm.restoreState == null && vm.resetPreview == null && vm.canGoBack) { progress ->
        val expected = vm.navigation
        try {
            progress.collect { backProgress = it.progress.coerceIn(0f, 1f) }
            vm.completeBack(expected)
        } finally { backProgress = 0f }
    }
    GitHabitTheme(dark) {
        val mainTabs = listOf(Destination.TODAY, Destination.REPOS, Destination.INSIGHTS)
        val navigationItemModifiers = mainTabs.associateWith { tab -> key(tab) { Modifier.focusOutline() } }
        NavigationSuiteScaffold(
            modifier = Modifier.fillMaxSize().testTag(if (navigationType == NavigationSuiteType.NavigationRail) "navigation-rail" else "navigation-bar"),
            layoutType = navigationType,
            containerColor = MaterialTheme.colorScheme.background,
            navigationSuiteItems = {
                mainTabs.forEach { tab ->
                    item(selected = vm.topLevelDestination == tab,
                        onClick = { vm.navigate(tab, mainTab = true) },
                        modifier = navigationItemModifiers.getValue(tab).testTag("tab-${tab.name}"),
                        icon = { Icon(when (tab) {
                            Destination.TODAY -> Icons.Outlined.Today
                            Destination.REPOS -> Icons.Outlined.FolderOpen
                            else -> Icons.Outlined.BarChart
                        }, null) },
                        label = { Text(stringResource(when (tab) {
                            Destination.TODAY -> R.string.navigation_today
                            Destination.REPOS -> R.string.navigation_repos
                            else -> R.string.navigation_insights
                        })) }, alwaysShowLabel = true)
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("notice-host")) },
                topBar = {
                    if (workspaceOpen) TopAppBar(
                        title = {
                            if (vm.destination in listOf(Destination.TODAY, Destination.REPOS, Destination.INSIGHTS)) Brand(compact = true)
                            else Text(stringResource(when (vm.destination) {
                                Destination.SETTINGS -> R.string.navigation_workspace
                                Destination.ACTIVITY -> R.string.navigation_activity
                                else -> R.string.navigation_repository
                            }), style = MaterialTheme.typography.titleMedium)
                        },
                        navigationIcon = {
                            if (vm.destination !in listOf(Destination.TODAY, Destination.REPOS, Destination.INSIGHTS)) {
                                IconButton(onClick = vm::back, modifier = Modifier.focusOutline(CircleShape).testTag("navigate-back")) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.navigation_back))
                                }
                            }
                        },
                        actions = {
                            if (vm.destination != Destination.ACTIVITY) IconButton(onClick = { vm.navigate(Destination.ACTIVITY) }, modifier = Modifier.focusOutline(CircleShape)) {
                                Icon(Icons.Outlined.History, stringResource(R.string.open_activity))
                            }
                            if (vm.destination != Destination.SETTINGS) IconButton(onClick = { vm.navigate(Destination.SETTINGS) },
                                modifier = Modifier.focusOutline(CircleShape).testTag("open-settings")) {
                                Icon(Icons.Outlined.Settings, stringResource(R.string.open_settings))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                    when {
                        load is WorkspaceLoad.Loading -> Column(Modifier.fillMaxSize().testTag("workspace-loading"),
                            verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_workspace))
                        }
                        recovery != null -> RecoveryScreen(recovery, vm.canSaveRecoveryCopy, vm.busy || vm.exportInProgress,
                             onRetry = vm::retryLoad,
                             onSaveCopy = onExport, onRestore = onRestore,
                             onStartOver = vm::requestReset)
                        vm.needsWelcome -> WelcomeScreen(vm)
                        else -> Box(Modifier.fillMaxSize().testTag("page-${vm.destination.name}")
                            .semantics { this[BackPreviewProgress] = backProgress }
                            .graphicsLayer {
                                // Gesture-driven, nonessential feedback; no settling animation or hidden route instance.
                                alpha = 1f - backProgress * .08f
                                scaleX = 1f - backProgress * .02f
                                scaleY = scaleX
                            }) {
                            if (vm.destination == Destination.REPOS || vm.destination == Destination.DETAIL) {
                                RepositoryPages(vm, savedState,
                                    allowTwoPanes = !adaptiveInfo.windowPosture.isTabletop && adaptiveInfo.windowPosture.hingeList.isEmpty())
                            } else savedState.SaveableStateProvider(vm.destination.name) {
                                when (vm.destination) {
                                    Destination.TODAY -> TodayScreen(vm)
                                    Destination.INSIGHTS -> InsightsScreen(vm)
                                    Destination.ACTIVITY -> ActivityScreen(vm, onExport)
                                     Destination.SETTINGS -> SettingsScreen(vm, onExport, onRestore)
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
        if (workspaceOpen) WorkspaceSheet(vm, sheetState)
        TransferDialogs(vm, onExport, onRestore)
    }
}

@Composable
private fun RepositoryPages(vm: GitHabitViewModel, savedState: SaveableStateHolder, allowTwoPanes: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // These are usable-content bounds AFTER system insets, top bar and navigation.
        val twoPanes = allowTwoPanes && maxWidth >= 900.dp && maxHeight >= 480.dp
        val detailActive = vm.destination == Destination.DETAIL
        Row(Modifier.widthIn(max = 1440.dp).fillMaxSize().testTag(if (twoPanes) "repos-two-pane" else "repos-single-pane")) {
            if (twoPanes || !detailActive) Box(Modifier.weight(if (twoPanes) .42f else 1f).fillMaxHeight()) {
                savedState.SaveableStateProvider("REPOS") { ReposScreen(vm) }
            }
            if (twoPanes) VerticalDivider()
            if (twoPanes || detailActive) Box(Modifier.weight(if (twoPanes) .58f else 1f).fillMaxHeight()) {
                if (detailActive && vm.selectedRepoId != null) savedState.SaveableStateProvider("detail:${vm.selectedRepoId}") {
                    DetailScreen(vm)
                } else Column(Modifier.fillMaxSize().padding(GitHabitSpace.page).testTag("repo-selection-prompt"),
                    verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium, Alignment.CenterVertically)) {
                    Text(stringResource(R.string.choose_repository), style = MaterialTheme.typography.titleLarge)
                    Subtitle(stringResource(R.string.choose_repository_description))
                }
            }
        }
    }
}
