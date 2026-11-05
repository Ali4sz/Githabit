# Workspace storage safety

Implemented in Task 2 and extended by Task 4. See [baseline evidence](verification.md),
[Task 4 evidence](task4-verification.md) and [v2 format/recovery policy](workspace-format-v2.md).

## Ownership and publication

- `WorkspaceStore` owns all recovery-capable reads, writes and verification. Production
  `AtomicFileAccess` instances share one process-wide lock, including overlapping
  ViewModel lifetimes. An ordinary write also compares current bytes with its loaded
  snapshot, so a stale owner cannot overwrite another owner's acknowledged change.
- A successful load is required before ordinary writes. Missing storage is distinct from
  unreadable paths, sidecar recovery failures, invalid content and unsupported versions.
  UTF-8 is decoded strictly; damaged bytes are not silently replaced with U+FFFD.
- v1 startup is preservation-first migration, not read-only compatibility. Success requires
  actual v2 bytes and matching candidate values; the exact legacy original remains in an
  independent RECOVERY archive. Stale-snapshot checks retain observed verified bytes.
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
- Before retries replace in-memory recovery slots, outstanding originals and uncertain
  candidates must be preserved. Raw exports freeze their source before provider opening,
  independent of subsequent reset/reconciliation.
- Physical input is bounded. Oversized originals use streaming preservation/raw export;
  no truncated prefix is accepted. Replacement stays blocked until oversized primary storage
  is reconciled. Archived oversized last-good data does not block a healthy primary.
- AtomicFile sidecar evidence is retained before recovery-capable reads can alter it.
  A `.new`-only interrupted first write is exportable recovery, not writable empty state;
  explicit replacement re-preserves it. Failed staging cannot shadow finalized archives.
- All copies stay in app-private `filesDir`, covered by existing cloud/device-transfer
  exclusions. Recovery export writes a frozen byte snapshot or streams a retained archive;
  it does not decode and re-encode damaged data.

These safeguards are not a guarantee against hardware failure, power loss or exhausted
storage. If preservation cannot succeed, the UI says so rather than promising that history
is safe. A failed document-provider export may leave an incomplete external file.

## Scope boundary

This checkpoint activates v2 and preservation-first v1 migration, but retains temporary
prototype entry/forms. It does not remove or classify sample-looking records or implement
import UI. Real-data-only entry, schedule controls, confirmed restore, minimum-device memory
certification and fixture-free artifact delivery remain later gates.
