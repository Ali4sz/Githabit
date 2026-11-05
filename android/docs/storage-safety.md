# Workspace storage safety

Implemented in Task 2; see [verification evidence](verification.md) for tested scope.

## Ownership and publication

- `WorkspaceStore` owns all recovery-capable reads, writes and verification. Production
  `AtomicFileAccess` instances share one process-wide lock, including overlapping
  ViewModel lifetimes. An ordinary write also compares current bytes with its loaded
  snapshot, so a stale owner cannot overwrite another owner's acknowledged change.
- A successful load is required before ordinary writes. Missing storage is distinct from
  unreadable paths, sidecar recovery failures, invalid content and unsupported versions.
  UTF-8 is decoded strictly; damaged bytes are not silently replaced with U+FFFD.
- ViewModel commands prepare the candidate/inverse before saving, do IO off Main, and
  publish only after read-back verification. Once writing starts, reconciliation completes
  even if the ViewModel is cleared. The bounded date ticker remains lifecycle-owned;
  tests inject its scope and IO dispatcher rather than depending on real-time delays.

## Save outcomes

| Outcome | Meaning |
| --- | --- |
| Committed | Read-back matches the candidate; publish it |
| RolledBack | Previous bytes, or a previously missing file, were independently verified |
| Indeterminate | Outcome cannot be established; block writes and re-read before continuing |
| Rejected | No write attempted, usually because preservation could not be verified |
| Blocked | Storage has not authorized ordinary writing |

A thrown write is not assumed to mean failure: the candidate may already be on disk.
An unknown save is never blindly replayed. Retry reconciles the readable file; stale
Undo/routes/drafts are invalidated only when reconciliation establishes changed data or
a confirmed replacement commits. Entered form state survives a temporary recovery screen.

## Preservation

- `githabit-last-good.json` is an independently named, rotating previous-file copy.
  It is loaded during recovery even after process restart.
- `githabit-recovery-<sha256>-<unique>.json` copies retain unreadable originals and recovery
  snapshots. Repeated identical recoveries reuse verified bytes; different content never
  overwrites a previous recovery copy. Copies are synced and read back before being trusted.
- Confirmed replacement preserves **all** outstanding snapshots, not just the corrupt
  primary, before clearing state. An uninspectable last-good file blocks replacement.
  Later backup rotation therefore cannot erase the sole retained good snapshot.
- All copies stay in app-private `filesDir`, covered by existing cloud/device-transfer
  exclusions. Recovery export writes an immutable raw byte snapshot to a chosen document;
  it does not decode and re-encode damaged data.

These safeguards are not a guarantee against hardware failure, power loss or exhausted
storage. If preservation cannot succeed, the UI says so rather than promising that history
is safe. A failed document-provider export may leave an incomplete external file.

## Scope boundary

This checkpoint retains v1 compatibility and the existing prototype entry/forms. It does
not remove or classify sample-looking records, add v2 capacity limits, or implement import.
The real-data-only UI, migration, schedules and confirmed restore follow in later tasks.
