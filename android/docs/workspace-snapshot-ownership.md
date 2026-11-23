# Workspace byte snapshot ownership

`WorkspaceStore` owns byte snapshots under `WorkspaceFileAccess.ownershipLock`.
Reads transfer fresh, caller-owned arrays; writes/preservation borrow read-only arrays
and must neither mutate nor retain them. Domain snapshots use immutable application
lists/records. No array is exposed by reference to UI callers.

| Snapshot | Owner / aliases | Lifetime / release | Restart evidence |
| --- | --- | --- | --- |
| Published domain state | ViewModel; export may freeze the same immutable object | Publication replaces a reference, never mutates old records | Verified primary |
| Encoded candidate | Save call; uncertainty may retain it | Through readback, rollback, or transfer to `uncertainCandidate` | Preserve candidate before risky rollback; uncertainty archives it |
| Loaded raw / lastGoodBytes | Store; v1 original may alias it, read-only | Until verified next commit/reconciliation; old export retains its reference safely | Primary and verified preservation copies |
| persistedLastGoodBytes | Store, fresh read | Keep while recovery requires it; release on verified commit | Independently retained LAST_GOOD file |
| Previous observed bytes | Save call; alias lastGood only after exact byte comparison | **Through all rollback and terminal-outcome checks** | Preservation verified before write |
| original | Store, read-only raw/observed array; may alias lastGood | Only clear after outstanding preservation and verified replacement/reconciliation | Nonrotating RECOVERY archive |
| uncertainCandidate | Store, encoded candidate alias | Preserve before reload/replacement; clear only after reconciliation | Nonrotating RECOVERY archive |
| Readback / restored bytes | Save call, freshly owned | Transfer to lastGood/original or release after terminal check | Preserve unexpected content before rollback |
| Preservation verification | File-access seam, bounded streaming buffer | Only during full byte comparison, including EOF | fsync + independently read-back verified file |
| Matching physical read | Store lends candidate/previous immutable arrays; access retains none | Return an alias only after a fresh read matches every byte and EOF | Same sidecar retention and AtomicFile recovery path as ordinary reads |
| Mismatching physical read | Fresh Store-owned actual bytes; matched prefix copied from immutable snapshot | Retain through stale/rollback/terminal checks exactly as ordinary readback | Never replace different formatting/content with canonical candidate bytes |
| Byte I/O scratch | Operation-local mutable small array, never a snapshot | Refilled from immutable source or read stream for each chunk | Does not replace any recovery evidence |
| Recovery export | Private captured read-only array or nonrotating physical archive | Until request completes/cancels, regardless of later store reference changes | Never capture rotating LAST_GOOD pathname |

## Review rules

- Aliases above are safe because arrays are private and never mutated after capture.
  Local variable aliases do not require `copyOf`; public `recoveryBytes()` still does.
- Ordinary `readBytes()` remains fresh. `readBytesMatching()` may reuse only supplied
  immutable arrays after independently matching the complete physical bytes and EOF;
  unequal reads retain actual bytes. Matching and mismatch materialization share one
  open stream, with the same length/capacity/error checks. Previous survives rollback.
- Native-facing snapshot reads/writes use a genuinely small scratch array: applying
  offset/count to a full array does not bound JNI whole-array copying. Array-backed
  recovery export also uses scratch copies so an external destination cannot mutate
  the frozen private snapshot.
- Keep fail-closed copy verification at the seam: fake/fault implementations returning
  corrupt or unreadable copies cannot authorize writes.
- Byte-equal readback can avoid another decode **only after** the encoded candidate's
  lossless roundtrip validation. Differently formatted value-equivalent readback still
  decodes/compares every value, requires source v2, and retains actual bytes for stale
  checks. Verified records/text can reuse expected immutable objects only after exact
  decoded equality; mismatches fail closed.
- App-owned regular files supply an explicit known length, transfer one exact owned
  array and verify EOF/length. Generic provider streams assemble bounded chunks
  without geometric growth; a `FileInputStream` may wrap a pipe and is not itself
  evidence of a known length. Public raw-byte
  exports remain defensive copies; private `RecoverySnapshot` closures may capture
  owned immutable arrays directly and never reference rotating LAST_GOOD paths.
- Sidecar retention, missing-first-write handling, and preservation-before-rollback
  sequencing are unchanged. Releasing a previous reference after stale checking alone
  would violate rollback and is explicitly forbidden.
