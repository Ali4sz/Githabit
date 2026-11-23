package com.githabit.prototype

import java.time.LocalDate
import com.githabit.prototype.domain.RepoStatus
import com.githabit.prototype.data.StorageMessage
import com.githabit.prototype.data.WorkspaceJsonFailure

/** Presentation identity, not formatted resource text. Nothing here enters workspace JSON. */
sealed interface Diagnostic {
    val english: String
    data class Fixed(val id: MessageId) : Diagnostic { override val english get() = id.english }
    data class Storage(val message: StorageMessage) : Diagnostic { override val english get() = message.english }
    data class ExportNotStarted(val cause: Diagnostic) : Diagnostic {
        override val english get() = "Export was not started. ${cause.english}"
    }
    data class DocumentFailure(val kind: WorkspaceJsonFailure, val detail: String) : Diagnostic {
        override val english get() = detail
    }
    data class Dated(val message: Diagnostic, val date: LocalDate) : Diagnostic {
        override val english get() = "${message.english} Date: $date."
    }
    data class DateChanged(val date: LocalDate, val todayAction: Boolean = false) : Diagnostic {
        override val english get() = "The local date changed to $date. " + if (todayAction)
            "Review Today and tap again; nothing was saved." else "Review your draft and submit again; nothing was saved."
    }
    data class BeforeCreation(val date: LocalDate) : Diagnostic {
        override val english get() = "This repo did not exist on $date. Check your device date."
    }
    data class Progress(val repoName: String, val reached: Reached? = null, val complete: Boolean = false) : Diagnostic {
        override val english get() = if (complete) "Today’s remaining target saved for $repoName."
        else "Progress saved for $repoName." + (reached?.let {
            " ${if (it == Reached.WEEKLY) "Weekly quota" else "Today’s target"} reached; moved out of Remaining."
        } ?: "")
    }
    data class Status(val status: RepoStatus) : Diagnostic {
        override val english get() = "Repo ${when (status) { RepoStatus.ACTIVE -> "resumed"; RepoStatus.PAUSED -> "paused"; RepoStatus.ARCHIVED -> "archived" }}. Your history is safe."
    }
    /** Compatibility adapter for pure domain exceptions/provider details; never parse interpolated text. */
    data class External(override val english: String) : Diagnostic
}

enum class Reached { DAILY, WEEKLY }

enum class MessageId(val english: String) {
    UNDO_RETRY("The previous workspace was verified. Undo did not commit; you can retry."),
    REPLACED("Workspace replaced. Any previous data was preserved on this device before replacement."),
    REPLACEMENT_REFRESH("The replacement was saved. Reopen GitHabit to refresh the screen."),
    REPLACEMENT_FAILED("Could not prepare the replacement. Nothing was replaced."),
    CALENDAR_FAILED("Calendar activation could not be saved. Your history was not replaced; try again."),
    LEGACY_CHANGED("An unchanged legacy field changed on disk. Your other draft inputs were retained; review the current field and save again."),
    UNCHANGED_REFRESH("Your saved data is unchanged, but the screen could not be updated. Reopen it."),
    SAVED_REFRESH("Your change was saved, but the screen could not be updated. Reopen it."),
    CHECK_ENTRY("Check your entry."),
    PREPARE_FAILED("Could not prepare your change. Please try again."),
    ROLLBACK("The change was not saved. The previous file was verified; you can retry."),
    INDETERMINATE("The save outcome is unknown. Re-read storage before making further changes."),
    RECOVERY_INDETERMINATE("The result of your last change could not be verified on disk. GitHabit paused to protect your history."),
    STORAGE_REREAD("Storage must be re-read before saving."),
    NEW_REPO("Your new repo is ready. Start with a small commit."),
    UPDATED_REPO("Repo updated. Keep growing."),
    COMMITTED("Progress committed. That’s a little win."),
    CORRECTED("Commit corrected."),
    COMPLETED("Daily goal reached. A little win worth keeping."),
    REMOVED("Commit removed."),
    UNDO_PAUSED("Undo is paused. Re-read storage before trying again."),
    UNDONE("Undone. You’re all set."),
    CALENDAR("Calendar preference saved. Check its effective and pending dates in Settings."),
    PROFILE("Profile saved on this device."),
    RESTORED("Workspace restored. Previous data was preserved on this device before replacement."),
    RESET("Workspace reset. Previous data was preserved on this device; external exports were not deleted."),
    EXPORTED("Your selected workspace snapshot has been exported."),
    RECOVERY_EXPORTED("A recovery snapshot was saved byte-for-byte. It may need repair before use."),
    EXPORT_FAILED("Export failed. The chosen file may be incomplete; select a location to retry."),
    EXPORT_RETRY_LOCATION("Try another location."),
    DOCUMENT_READ_FAILED("The document could not be read completely. Select it again or choose another file. Nothing was replaced."),
}
