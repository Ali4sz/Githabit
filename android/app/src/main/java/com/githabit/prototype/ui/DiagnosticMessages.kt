package com.githabit.prototype.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.githabit.prototype.R
import com.githabit.prototype.Diagnostic
import com.githabit.prototype.MessageId
import com.githabit.prototype.Reached
import com.githabit.prototype.domain.RepoStatus
import com.githabit.prototype.data.StorageMessage
import com.githabit.prototype.data.WorkspaceJsonFailure

@Composable
internal fun diagnosticMessage(message: Diagnostic): String = when (message) {
    is Diagnostic.Fixed -> stringResource(message.id.resource())
    is Diagnostic.Storage -> stringResource(message.message.resource())
    is Diagnostic.ExportNotStarted -> stringResource(R.string.notice_export_not_started, diagnosticMessage(message.cause))
    is Diagnostic.DocumentFailure -> stringResource(when (message.kind) {
        WorkspaceJsonFailure.CORRUPT -> R.string.document_corrupt
        WorkspaceJsonFailure.UNSUPPORTED_VERSION -> R.string.document_unsupported
        WorkspaceJsonFailure.INVALID -> R.string.document_invalid
        WorkspaceJsonFailure.LEGACY_INCOMPATIBLE -> R.string.document_legacy_incompatible
        WorkspaceJsonFailure.CAPACITY -> R.string.document_capacity
    }) + "\n" + stringResource(R.string.diagnostic_technical_detail, displayPreview(message.detail))
    is Diagnostic.Dated -> stringResource(R.string.notice_dated, diagnosticMessage(message.message), dateLabel(message.date, displayLocale()))
    is Diagnostic.DateChanged -> stringResource(if (message.todayAction) R.string.notice_today_date_changed else R.string.notice_date_changed,
        dateLabel(message.date, displayLocale()))
    is Diagnostic.BeforeCreation -> stringResource(R.string.error_before_creation, dateLabel(message.date, displayLocale()))
    is Diagnostic.Progress -> stringResource(when {
        message.complete -> R.string.notice_remaining_saved
        message.reached == Reached.WEEKLY -> R.string.notice_weekly_reached
        message.reached == Reached.DAILY -> R.string.notice_daily_reached
        else -> R.string.notice_progress_saved
    }, displayPreview(message.repoName))
    is Diagnostic.Status -> stringResource(when (message.status) {
        RepoStatus.ACTIVE -> R.string.notice_resumed
        RepoStatus.PAUSED -> R.string.notice_paused
        RepoStatus.ARCHIVED -> R.string.notice_archived
    })
    is Diagnostic.External -> diagnosticMessage(message.english)
}

private fun MessageId.resource(): Int = when (this) {
    MessageId.UNDO_RETRY -> R.string.notice_undo_retry
    MessageId.REPLACED -> R.string.notice_replaced
    MessageId.REPLACEMENT_REFRESH -> R.string.notice_replacement_refresh
    MessageId.REPLACEMENT_FAILED -> R.string.notice_replacement_failed
    MessageId.CALENDAR_FAILED -> R.string.notice_calendar_failed
    MessageId.LEGACY_CHANGED -> R.string.notice_legacy_changed
    MessageId.UNCHANGED_REFRESH -> R.string.notice_unchanged_refresh
    MessageId.SAVED_REFRESH -> R.string.notice_saved_refresh
    MessageId.CHECK_ENTRY -> R.string.notice_check_entry
    MessageId.PREPARE_FAILED -> R.string.notice_prepare_failed
    MessageId.ROLLBACK -> R.string.notice_rollback
    MessageId.INDETERMINATE -> R.string.notice_indeterminate
    MessageId.RECOVERY_INDETERMINATE -> R.string.notice_recovery_indeterminate
    MessageId.STORAGE_REREAD -> R.string.notice_storage_reread
    MessageId.NEW_REPO -> R.string.notice_new_repo
    MessageId.UPDATED_REPO -> R.string.notice_updated_repo
    MessageId.COMMITTED -> R.string.notice_committed
    MessageId.CORRECTED -> R.string.notice_corrected
    MessageId.COMPLETED -> R.string.notice_completed
    MessageId.REMOVED -> R.string.notice_removed
    MessageId.UNDO_PAUSED -> R.string.notice_undo_paused
    MessageId.UNDONE -> R.string.notice_undone
    MessageId.CALENDAR -> R.string.notice_calendar
    MessageId.PROFILE -> R.string.notice_profile
    MessageId.RESTORED -> R.string.notice_restored
    MessageId.RESET -> R.string.notice_reset
    MessageId.EXPORTED -> R.string.notice_export
    MessageId.RECOVERY_EXPORTED -> R.string.notice_recovery_exported
    MessageId.EXPORT_FAILED -> R.string.notice_export_failed
    MessageId.EXPORT_RETRY_LOCATION -> R.string.notice_export_retry_location
    MessageId.DOCUMENT_READ_FAILED -> R.string.document_read_failed
}

private fun StorageMessage.resource(): Int = when (this) {
    StorageMessage.PRESERVE_BEFORE_READ -> R.string.storage_preserve_before_read
    StorageMessage.LAST_GOOD_UNINSPECTED -> R.string.storage_last_good_uninspected
    StorageMessage.OPEN_FAILED -> R.string.storage_open_failed
    StorageMessage.READ_FAILED -> R.string.storage_read_failed
    StorageMessage.INACCESSIBLE -> R.string.storage_inaccessible
    StorageMessage.RECOVERY_UNINSPECTED -> R.string.storage_recovery_uninspected
    StorageMessage.MISSING_WITH_EVIDENCE -> R.string.storage_missing_with_evidence
    StorageMessage.READABLE_UNPRESERVED -> R.string.storage_readable_unpreserved
    StorageMessage.LEGACY_UNPRESERVED -> R.string.storage_legacy_unpreserved
    StorageMessage.MIGRATION_UNVERIFIED -> R.string.storage_migration_unverified
    StorageMessage.MIGRATION_UNCOMMITTED -> R.string.storage_migration_uncommitted
    StorageMessage.UNSUPPORTED -> R.string.storage_unsupported
    StorageMessage.INVALID -> R.string.storage_invalid
    StorageMessage.LEGACY_INCOMPATIBLE -> R.string.storage_legacy_incompatible
    StorageMessage.CAPACITY -> R.string.storage_capacity
    StorageMessage.DECODE_FAILED -> R.string.storage_decode_failed
    StorageMessage.REPLACEMENT_BLOCKED -> R.string.storage_replacement_blocked
    StorageMessage.EARLIER_UNPRESERVED -> R.string.storage_earlier_unpreserved
    StorageMessage.COPY_UNVERIFIED -> R.string.storage_copy_unverified
    StorageMessage.PHYSICAL_CAPACITY -> R.string.storage_physical_capacity
}

/** Presentation only. Never pass the preview back to storage or validation. */
internal fun displayPreview(original: String, limit: Int = 512): String {
    require(limit > 0)
    if (original.length <= limit) return original
    var end = limit
    if (original[end - 1].isHighSurrogate()) end--
    return original.substring(0, end) + "…"
}

/** Translate stable domain diagnostics at the presentation boundary, not in pure storage/math. */
@Composable
internal fun diagnosticMessage(message: String): String = when (message) {
    "Give your repo a name of 1–48 characters." -> stringResource(R.string.error_repo_name)
    "Add a unit, like pages or minutes (up to 20 characters)." -> stringResource(R.string.error_repo_unit)
    "Keep the description within 160 characters." -> stringResource(R.string.error_description)
    "Enter an amount between 0.001 and 1,000,000." -> stringResource(R.string.error_amount)
    "Select at least one weekday." -> stringResource(R.string.error_weekdays)
    "Set a weekly goal of 1 to 7 days." -> stringResource(R.string.error_weekly_goal)
    "Keep your commit message within 200 characters." -> stringResource(R.string.error_commit_note)
    "Keep your display name within 40 characters." -> stringResource(R.string.error_profile_name)
    "Keep your note within 160 characters." -> stringResource(R.string.error_profile_note)
    "Workspace has too many repos." -> stringResource(R.string.error_capacity_repos)
    "Workspace has too many commits." -> stringResource(R.string.error_capacity_commits)
    "Workspace has too many dated configurations." -> stringResource(R.string.error_capacity_configurations)
    "Identifier exceeds supported length." -> stringResource(R.string.error_capacity_identifier)
    "Workspace exceeds the supported UTF-8 byte limit." -> stringResource(R.string.error_capacity_bytes)
    "Encoded workspace exceeds the supported UTF-8 byte limit." -> stringResource(R.string.error_capacity_bytes)
    "This repo’s dated configuration changed. Review your draft and save again." -> stringResource(R.string.form_configuration_review)
    "Resume this repo before logging progress." -> stringResource(R.string.error_resume)
    "This repo has a newer dated configuration. Check your device date before changing its goal or status." -> stringResource(R.string.error_newer_configuration)
    "This repo changed since the form opened. Review your draft and save again." -> stringResource(R.string.error_repo_changed)
    "This repo creation already committed. Close and reopen the form." -> stringResource(R.string.error_repo_creation_committed)
    "This commit could not be found. Reopen the form." -> stringResource(R.string.error_commit_missing_reopen)
    "This commit changed since the form opened. Review your draft and save again." -> stringResource(R.string.error_commit_changed)
    "Close the open form before logging from Today. Your draft was retained." -> stringResource(R.string.error_close_form)
    "Progress or scheduling changed. Review Today and tap again; nothing was saved." -> stringResource(R.string.error_progress_changed)
    "This repo changed. Review Today and tap again; nothing was saved." -> stringResource(R.string.error_today_repo_changed)
    "This repo is not scheduled for quick logging today. Open the repo for optional activity." -> stringResource(R.string.error_today_not_scheduled)
    "This repo already exists." -> stringResource(R.string.error_repo_exists)
    "This repo could not be found. Reopen the form." -> stringResource(R.string.form_repo_missing)
    "This repo could not be found." -> stringResource(R.string.error_repo_missing)
    "Units stay fixed after your first commit. Create a new repo for a different unit." -> stringResource(R.string.error_unit_fixed)
    "This commit has already been saved." -> stringResource(R.string.error_commit_saved)
    "This commit could not be found." -> stringResource(R.string.form_commit_missing)
    "The calendar has a newer effective change. Check your device date before changing week start." -> stringResource(R.string.error_calendar_newer)
    "This Undo has expired or was cleared after workspace replacement. Nothing was changed." -> stringResource(R.string.error_undo_expired)
    "This operation is no longer available to undo. Nothing was changed." -> stringResource(R.string.error_undo_unavailable)
    "Cannot undo: the repo or its unit has changed. Nothing was changed." -> stringResource(R.string.error_undo_unit_changed)
    "Cannot undo: this commit has been changed or removed since the original operation. Nothing was changed." -> stringResource(R.string.error_undo_commit_changed)
    "Cannot undo: the device date is before this status change. Check your device date. Nothing was changed." -> stringResource(R.string.error_undo_date)
    "Cannot undo: the repo has changed or is no longer available. Nothing was changed." -> stringResource(R.string.error_undo_repo_changed)
    "Cannot undo: this dated status has since been changed. Nothing was changed." -> stringResource(R.string.error_undo_status_changed)
    "The change was not saved. The previous file was verified; you can retry." -> stringResource(R.string.notice_rollback)
    "The save outcome is unknown. Re-read storage before making further changes." -> stringResource(R.string.notice_indeterminate)
    "Undo is paused. Re-read storage before trying again." -> stringResource(R.string.notice_undo_paused)
    "Undone. You’re all set." -> stringResource(R.string.notice_undone)
    "Commit removed." -> stringResource(R.string.notice_removed)
    "Commit corrected." -> stringResource(R.string.notice_corrected)
    "Calendar preference saved. Check its effective and pending dates in Settings." -> stringResource(R.string.notice_calendar)
    "Profile saved on this device." -> stringResource(R.string.notice_profile)
    "Your selected workspace snapshot has been exported." -> stringResource(R.string.notice_export)
    "Export failed. The chosen file may be incomplete; select a location to retry." -> stringResource(R.string.notice_export_failed)
    "Workspace restored. Previous data was preserved on this device before replacement." -> stringResource(R.string.notice_restored)
    "Workspace reset. Previous data was preserved on this device; external exports were not deleted." -> stringResource(R.string.notice_reset)
    // Unexpected/provider-specific diagnostics remain verbatim rather than inventing a
    // misleading translated reason. They are explicitly outside translation coverage.
    else -> displayPreview(message)
}
