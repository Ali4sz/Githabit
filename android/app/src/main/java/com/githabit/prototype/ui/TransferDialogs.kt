package com.githabit.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.githabit.prototype.GitHabitViewModel
import com.githabit.prototype.R
import com.githabit.prototype.RestoreState
import com.githabit.prototype.WorkspaceLoad

/** Separate modal ownership: showing/cancelling transfer never changes repo/log drafts. */
@Composable
fun TransferDialogs(vm: GitHabitViewModel, onExport: () -> Unit, onRestore: () -> Unit) {
    val recovery = vm.load is WorkspaceLoad.Recovery
    val locked = vm.busy || vm.exportInProgress
    val canExport = !locked && (!recovery || vm.canSaveRecoveryCopy)
    when (val state = vm.restoreState) {
        is RestoreState.Reading -> TransferDialog(stringResource(R.string.restore_reading), "restore-reading", vm::cancelRestore) {
            CircularProgressIndicator()
            Text(stringResource(R.string.restore_validating))
            TransferButton(stringResource(R.string.transfer_cancel), "cancel-restore", true, vm::cancelRestore)
        }
        is RestoreState.Failed -> TransferDialog(stringResource(R.string.restore_failed), "restore-failed", vm::cancelRestore) {
            Text(state.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.transfer_unchanged))
            TransferButton(stringResource(R.string.restore_choose_another), "retry-restore", !locked, onRestore)
            TransferButton(stringResource(R.string.transfer_cancel), "cancel-restore", true, vm::cancelRestore)
        }
        is RestoreState.Preview -> TransferDialog(stringResource(R.string.restore_preview_title), "restore-preview",
            { if (!locked) vm.cancelRestore() }) {
            Text(stringResource(R.string.restore_format, state.document.sourceVersion))
            TransferCounts(stringResource(R.string.restore_incoming), state.document.habitCount, state.document.commitCount)
            if (recovery) Text(stringResource(R.string.transfer_recovery_counts))
            else TransferCounts(stringResource(R.string.transfer_current), state.currentHabitCount, state.currentCommitCount)
            if (state.document.sourceVersion == 1) Text(stringResource(R.string.restore_migration))
            Text(stringResource(R.string.restore_replace_warning))
            Text(stringResource(R.string.transfer_preservation))
            if (state.consentUpdated) ConsentUpdated()
            if (vm.transferNeedsReconciliation) Reconciliation(vm)
            TransferButton(stringResource(if (recovery) R.string.transfer_export_recovery else R.string.transfer_export_first),
                "export-before-restore", canExport, onExport)
            TransferButton(stringResource(R.string.restore_confirm), "confirm-restore", !locked && !vm.transferNeedsReconciliation,
                { vm.confirmRestore(state) }, destructive = true)
            TransferButton(stringResource(R.string.transfer_cancel), "cancel-restore", !locked, vm::cancelRestore)
        }
        else -> Unit
    }
    vm.resetPreview?.let { preview ->
        TransferDialog(stringResource(R.string.reset_title), "reset-preview", { if (!locked) vm.cancelReset() }) {
            if (recovery) Text(stringResource(R.string.transfer_recovery_counts))
            else TransferCounts(stringResource(R.string.transfer_current), preview.habitCount, preview.commitCount)
            Text(stringResource(R.string.reset_replace_warning))
            Text(stringResource(R.string.transfer_preservation))
            if (preview.consentUpdated) ConsentUpdated()
            if (vm.transferNeedsReconciliation) Reconciliation(vm)
            TransferButton(stringResource(if (recovery) R.string.transfer_export_recovery else R.string.transfer_export_first),
                "export-before-reset", canExport, onExport)
            TransferButton(stringResource(R.string.reset_confirm), "confirm-reset", !locked && !vm.transferNeedsReconciliation,
                { vm.confirmReset(preview) }, destructive = true)
            TransferButton(stringResource(R.string.transfer_cancel), "cancel-reset", !locked, vm::cancelReset)
        }
    }
}

@Composable
private fun TransferCounts(label: String, habits: Int, commits: Int) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(pluralStringResource(R.plurals.transfer_repos, habits, habits))
    Text(pluralStringResource(R.plurals.transfer_commits, commits, commits))
}

@Composable
private fun ConsentUpdated() {
    Text(stringResource(R.string.transfer_consent_updated), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("transfer-consent-updated"))
}

@Composable
private fun Reconciliation(vm: GitHabitViewModel) {
    Text(stringResource(R.string.transfer_reconcile), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    TransferButton(stringResource(R.string.recovery_retry), "transfer-retry-load", !vm.busy, vm::retryLoad)
}

@Composable
private fun TransferButton(label: String, tag: String, enabled: Boolean, action: () -> Unit, destructive: Boolean = false) {
    OutlinedButton(onClick = action, enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline(CircleShape).testTag(tag)) {
        Text(label)
    }
}

/** The entire dialog scrolls, including actions, in short windows and at 200% text. */
@Composable
private fun TransferDialog(title: String, tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(GitHabitSpace.medium), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).testTag(tag)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(GitHabitSpace.page),
                    verticalArrangement = Arrangement.spacedBy(GitHabitSpace.medium)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    content()
                }
            }
        }
    }
}
