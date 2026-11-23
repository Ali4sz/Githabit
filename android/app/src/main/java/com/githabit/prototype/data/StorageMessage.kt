package com.githabit.prototype.data

/** Stable storage guidance; technical exceptions never determine the user-facing identity. */
enum class StorageMessage(val english: String) {
    PRESERVE_BEFORE_READ("Earlier recovery snapshots must be preserved before storage can be re-read."),
    LAST_GOOD_UNINSPECTED("The last-good recovery file could not be inspected."),
    OPEN_FAILED("The workspace could not be opened. Retained physical evidence can be exported before a confirmed start-over."),
    READ_FAILED("Workspace storage could not be read."),
    INACCESSIBLE("Workspace storage is not accessible."),
    RECOVERY_UNINSPECTED("Recovery storage could not be inspected."),
    MISSING_WITH_EVIDENCE("The workspace file is missing, but earlier data may still be recoverable."),
    READABLE_UNPRESERVED("The file is readable, but earlier recovery snapshots could not yet be preserved."),
    LEGACY_UNPRESERVED("The legacy original could not be preserved. Migration did not proceed."),
    MIGRATION_UNVERIFIED("Migration could not be verified. Re-read storage before making changes."),
    MIGRATION_UNCOMMITTED("Migration was not committed. The legacy original is retained; retry after checking storage."),
    UNSUPPORTED("This workspace uses an unsupported format. Its bytes were left untouched."),
    INVALID("Saved data did not pass validation."),
    LEGACY_INCOMPATIBLE("Legacy data cannot be migrated without changing values. Its original was retained."),
    CAPACITY("Workspace exceeds supported capacity. Its original was retained without truncation."),
    DECODE_FAILED("Saved data could not be decoded."),
    REPLACEMENT_BLOCKED("Reconcile and re-read storage before replacement. An oversized or uninspectable file still needs recovery."),
    EARLIER_UNPRESERVED("Earlier recovery snapshots could not be preserved. Nothing was replaced."),
    COPY_UNVERIFIED("A verified preservation copy could not be saved. Nothing was replaced."),
    PHYSICAL_CAPACITY("A stored file exceeds supported capacity. It was not truncated or replaced. Save the recovery copy if available; replacement stays blocked until storage is reconciled."),
}
