package com.githabit.prototype

import com.githabit.prototype.data.StorageMessage
import com.githabit.prototype.data.WorkspaceLoadResult
import com.githabit.prototype.data.UnreadableKind
import com.githabit.prototype.data.WriteOutcome
import com.githabit.prototype.domain.RepoStatus
import com.githabit.prototype.ui.displayPreview
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTest {
    @Test fun storageReasonsKeepEnglishCompatibilityButCarryIdentity() {
        for (message in StorageMessage.entries) {
            assertEquals(message.english, Diagnostic.Storage(message).english)
            assertEquals(message.english, WorkspaceLoadResult.Unreadable(UnreadableKind.IO, message, false).reason)
            assertEquals(message.english, WriteOutcome.Rejected(message).reason)
        }
    }

    @Test fun dynamicMessagesRetainOriginalArgumentsAndEnglish() {
        val name = "  🌱 repo  "
        val progress = Diagnostic.Progress(name, Reached.WEEKLY)
        assertEquals(name, progress.repoName)
        assertEquals("Progress saved for $name. Weekly quota reached; moved out of Remaining.", progress.english)
        assertEquals("${progress.english} Date: 2026-11-04.", Diagnostic.Dated(progress, LocalDate.of(2026, 11, 4)).english)
        assertEquals("Repo paused. Your history is safe.", Diagnostic.Status(RepoStatus.PAUSED).english)
        assertEquals("Export was not started. Try another location.",
            Diagnostic.ExportNotStarted(Diagnostic.Fixed(MessageId.EXPORT_RETRY_LOCATION)).english)
        val date = LocalDate.of(2026, 11, 4)
        assertEquals(com.githabit.prototype.domain.RepoBeforeCreation(date).message, Diagnostic.BeforeCreation(date).english)
    }

    @Test fun displayPreviewDoesNotMutateOriginalOrSplitSupplementaryCharacters() {
        val original = "x".repeat(511) + "🌱" + " kept".repeat(1000)
        assertEquals("x".repeat(511) + "…", displayPreview(original))
        assertEquals(5513, original.length)
        assertEquals("small 🌱", displayPreview("small 🌱"))
    }
}
