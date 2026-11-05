package com.githabit.prototype.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.githabit.prototype.Destination
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.WorkspaceLoad
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHabitApp(vm: GitHabitViewModel) {
    // Keep page/form saveable state outside the writable/recovery branch. A failed
    // save may hide ordinary UI without authorizing disposal of the user's draft.
    val savedState = key(vm.workspaceSession) { rememberSaveableStateHolder() }
    val sheetState = key(vm.workspaceSession, vm.overlay) { rememberSaveableStateHolder() }
    val view = LocalView.current
    val dark = vm.workspace.darkTheme
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            // Replace stale notices rather than queuing Undo behind an older snackbar.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(notice.message, actionLabel = notice.undoId?.let { "Undo" },
                    withDismissAction = true, duration = if (notice.undoId == null) SnackbarDuration.Short else SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) notice.undoId?.let(vm::undo)
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val onExport = { export.launch("githabit-${vm.today}.json") }
    val recoveryExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportRecoveryCopy)
    }
    val load = vm.load
    val recovery = load as? WorkspaceLoad.Recovery
    val authenticated = load is WorkspaceLoad.Ready && vm.workspace.signedIn && vm.workspace.onboarded
    val mainTabs = listOf(Destination.TODAY, Destination.REPOS, Destination.INSIGHTS)
    BackHandler(enabled = vm.overlay == null && authenticated && vm.destination != Destination.TODAY) { vm.back() }
    BackHandler(enabled = load is WorkspaceLoad.Ready && vm.workspace.signedIn && !vm.workspace.onboarded) {
        if (vm.onboardingStep > 0) vm.onboardingStep-- else vm.signOut()
    }
    GitHabitTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (authenticated) TopAppBar(
                    title = {
                        if (vm.destination in mainTabs) Brand(compact = true)
                        else Text(when (vm.destination) {
                            Destination.SETTINGS -> "Your workspace"
                            Destination.ACTIVITY -> "Activity"
                            else -> "Repository"
                        }, style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        if (vm.destination !in mainTabs) IconButton(onClick = vm::back) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (vm.destination != Destination.ACTIVITY) IconButton(onClick = { vm.navigate(Destination.ACTIVITY) }) {
                            Icon(Icons.Outlined.History, "Open activity")
                        }
                        if (vm.destination != Destination.SETTINGS) IconButton(onClick = { vm.navigate(Destination.SETTINGS) },
                            modifier = Modifier.testTag("open-settings")) {
                            Icon(Icons.Outlined.AccountCircle, "Open settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                if (authenticated) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(selected = vm.destination == tab || (tab == Destination.REPOS && vm.destination == Destination.DETAIL),
                            onClick = { vm.navigate(tab, mainTab = true) }, modifier = Modifier.testTag("tab-${tab.name}"),
                            icon = { Icon(when (tab) {
                                Destination.TODAY -> Icons.Outlined.Today
                                Destination.REPOS -> Icons.Outlined.FolderOpen
                                else -> Icons.Outlined.BarChart
                            }, null) },
                            label = { Text(when (tab) { Destination.TODAY -> "Today"; Destination.REPOS -> "Repos"; else -> "Insights" }) })
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    when {
                        load is WorkspaceLoad.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        recovery != null -> RecoveryScreen(
                            recovery, vm.canSaveRecoveryCopy, vm.busy,
                            onRetry = vm::retryLoad,
                            onSaveCopy = { recoveryExport.launch("githabit-recovery-${vm.today}.json") },
                            onStartOver = vm::startOverFromRecovery,
                        )
                        !vm.workspace.signedIn -> SignInScreen(vm)
                        !vm.workspace.onboarded -> OnboardingScreen(vm)
                        else -> {
                            savedState.SaveableStateProvider("${vm.destination}-${vm.selectedRepoId}") {
                                Box(Modifier.fillMaxSize().testTag("page-${vm.destination.name}")) {
                                    when (vm.destination) {
                                        Destination.TODAY -> TodayScreen(vm)
                                        Destination.REPOS -> ReposScreen(vm)
                                        Destination.INSIGHTS -> InsightsScreen(vm)
                                        Destination.DETAIL -> DetailScreen(vm)
                                        Destination.ACTIVITY -> ActivityScreen(vm, onExport)
                                        Destination.SETTINGS -> SettingsScreen(vm, onExport)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (authenticated) WorkspaceSheet(vm, sheetState)
    }
}
