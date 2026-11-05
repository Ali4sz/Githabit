# Task 4 — v2 migration and validation evidence

This task is in progress. Entries report actual checks, not planned delivery results.
No personal workspace/device is accessed or migrated by this work.

## Change 1 — fixed legacy fixture inventory (2026-11-05)

Source: the commit containing this entry, based on `5ed6c58`.
Closes the missing Task 1 legacy inventory before replacing the v1 encoder. These are
synthetic fixed test files, independent of production sample generation or the codec
being tested. They are not distributed in runtime/debug source sets.

All seven files live in `app/src/test/resources/fixtures/`:

| File | Coverage | SHA-256 |
| --- | --- | --- |
| `v1-empty-dark.json` | Empty, unfinished, signed out, old default-looking profile, dark | `70a855632be4805e319ef4a206d25e284a1096cd6f4f4573894535f1da2e7747` |
| `v1-empty-light.json` | Empty, onboarding complete, explicit light | `643af2778bf2a8ad9886a8b8296c858c3fd90480fa77f8db196faa428fccfec0` |
| `v1-populated-light.json` | Populated light, unsorted historical targets/statuses, fractional/excess activity, leap day, retained paused-date commit | `871ea5d6bed1e13a3e4195e14b4355f393780fe405a86f3df0d7d3f2e68ed567` |
| `v1-mixed-signed-out-dark.json` | Edited `sample-0` plus personal history, paused/archived repos, signed out/incomplete onboarding despite history, default-looking profile | `3f50db171eae1b5f7224560a764c28f369320a9d7eaa92181321c4f479e495aa` |
| `v1-padded-strings.json` | Strings/IDs retain whitespace; pre-creation/future stored dates and timestamp disagreement accepted by v1 | `e223ad1df5ff580244a8323cc9be1795539427f590b965050b257f383a654535` |
| `corrupt.json` | Incomplete JSON | `a686b3bbb4ed2b56ef52ee2a46ac8b20fc32698ee0cfd1fd570fac2e82f18929` |
| `unsupported.json` | Explicit unknown format 99, independent of encoder formatting | `7e0a11fb78c28141cf829b2473fa5b6286f95012c9ad97d0e34bb2d3454e39d2` |

The mixed signed-out fixture is duplicated byte-for-byte in
`app/src/androidTest/assets/fixtures/` for guarded device migration tests. It belongs
only to the separate test APK. No runtime fixture/seed hook was added.

Five characterization tests pass against the unchanged v1 codec. From `android/`:
`./gradlew :app:test :app:assembleDebugAndroidTest --console=plain --no-daemon`.
Completed before `2026-11-05T22:24:07+03:00`, exit 0, BUILD SUCCESSFUL (16s):
**147 tests per variant, zero failures/errors**; separate test APK assembled;
`git diff --check` passed. Log: `/tmp/opencode/task4a-fixtures.log` (temporary).
No connected tests or new lint/app-build claim for this test-only change.

Migration must preserve supported legacy content rather than replaying commits through
new-input commands: v1 permits commits on dates subsequently paused/archived and even
dates preceding creation. Saved local dates, timestamps, amounts and strings remain
authoritative. Unsorted legacy versions may be sorted for v2 representation without
deleting any dated configuration. Exact original bytes must also be preserved.

## Change 2 — bounded strict JSON foundation (2026-11-05)

Source: the commit containing this entry, based on `d311b3e`. Runtime codec/startup is
still unchanged until the coordinated cutover; these helpers do not themselves migrate.

- Centralized 32 MiB, 1,000-habit, 100,000-commit/configuration, 128-ID and depth-8
  constants, typed JSON failures, bounded stream reads and strict UTF-8 decoding.
- Added a platform-independent streaming reader retaining number lexemes for subsequent
  schema validation, without constructing a second document tree. It rejects duplicate keys (including escaped equivalents), trailing
  content, coercive/non-JSON literals, invalid number grammar, malformed escapes,
  lone surrogates, excessive depth and arrays exceeding supported collection capacity.
  Internal structural guards also limit an object to 16 fields (the schema needs at
  most eight) and the document to 2,109,032 values (derived from maximum supported
  configurations, commits and habits). They bound malformed-document work before
  post-parse validation and do not reduce the supported schema's record counts.
- Added incremental escaped-UTF-8 output accounting before buffer growth. No new
  runtime dependency or toolchain change. Schema validators and storage integration
  will enforce the remaining collection/record limits at cutover.
- Thirteen tests include actual below/at/above 32 MiB input, actual output limit,
  escaped/multibyte output, 100,000 array entries, depth 7/8/9, malformed UTF-8,
  duplicate keys, Unicode and primitive round trips. These are JVM checks, not a
  claim of minimum-device memory certification (Task 13). Nested aggregate arrays and
  wide objects have explicit resource-rejection regressions.

The initial tree-based attempt passed 158 tests but failed independent review: a
14,000,163-byte nested-array document exhausted a 256 MiB heap. It was not committed
or activated. The implementation was replaced with streaming callbacks and aggregate
guards; that initial passing suite is not evidence of resource safety.

From `android/`, `./gradlew :app:test :app:lintDebug :app:assembleDebug
--console=plain --no-daemon` passed (52s), exit 0, completed before
`2026-11-05T22:43:16+03:00`: **160 tests per variant, zero failures/errors**;
lint zero errors/eight existing advisories; debug APK assembled; `git diff --check`
passed. Log: `/tmp/opencode/task4b-streaming-verified.log` (temporary). No connected tests.

## Change 3 — coordinated v2 activation (2026-11-05)

Source: the commit containing this entry, based on `4c0fb58`. The codec, model, storage
and minimal UI compatibility are cut over together. See [format details](workspace-format-v2.md)
for the exact schema, compatibility decisions and physical recovery policy.

Implemented:

- Strict two-pass streaming v1/v2 decode, v2 encode and source-version metadata; required
  fields/types, IDs/references, enum/schedule/calendar invariants, exact decimal precision,
  ordering, strings and shared input/output/count limits. No whole-document JSON tree.
- Empty profile defaults, SYSTEM/LIGHT/DARK, and local onboarding independent of legacy
  sign-in. Legacy records/profile values are preserved, including edited `sample-*` history,
  unsorted versions (sorted without loss), padded strings/IDs, paused/precreation/future dates
  and date/timestamp disagreements. Incompatible precision is rejected, not rounded.
- Migration within the existing ownership lock: independently retain originals and outstanding
  snapshots, then write and verify actual v2 **and** values. Observed bytes become the stale
  snapshot baseline. Unknown/incompatible/oversized input never authorizes ordinary writes.
- Bounded physical reads; streaming full-original preservation and frozen-source raw export;
  sidecar evidence retention; explicit `.new`-only recovery; finalized staging and archive reuse
  without an additional full copy. Overflow during verification remains indeterminate.
- Snapshot export validation before opening a truncating provider destination. Minimal interim
  theme/optional-profile rendering and schedule-preserving editing; old demo controls remain.

Independent read-only review uncovered and prompted fixes before committing:

1. Recovery retries could overwrite unpreserved in-memory originals/candidates. They now
   fail closed until outstanding snapshots are independently retained, with regressions.
2. The old editor could silently reset a restored weekly schedule to Daily; it now carries
   the existing schedule. Padded legacy units/unchanged text remain editable and preserved.
3. Recovery export selected its source after provider opening, allowing reset to change it;
   source selection is now frozen before opening, with reset/reconciliation regressions.
4. Sidecar-only evidence was archived but inaccessible. It now reaches recovery export and
   explicit start-over, which re-preserves current physical evidence.
5. Failed streaming staging could shadow complete archives; fragments are excluded from
   complete selection and cleaned up. Finalized archives are verified/reused before requiring
   another writable staging file. Byte-array LAST_GOOD writes use finalized staging too.

Final read-only review reported no remaining material blockers in those paths. This is not
a claim that later UI/transfer/accessibility tasks have passed.

### Fresh verification

From `android/`:

```text
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

Exit 0, BUILD SUCCESSFUL (1m 18s), **all 105 tasks executed**, completed before
`2026-11-05T23:29:34+03:00`. **198 tests per variant, zero failures/errors**; lint **zero
errors/eight existing advisories**; app and separate test APK assembled. `git diff --check`
passed. Log: `/tmp/opencode/task4c-final.log` (temporary). No dependency/toolchain changes.

New tests cover all schedule/theme/week-start representations, historical/chained bridges,
elapsed pending records, strict schema negatives, legacy preservation, incompatible precision,
missing/duplicate/orphan records, migration idempotence/failures/cold restart/backup rotation,
source-format false positives, observed-byte verification, optional profiles and export ordering.
Boundary tests exercise below/at/above habit, commit, total-configuration and ID limits, alongside
the earlier actual 32 MiB/depth/Unicode tests. A physical MAX_BYTES+1 file retains its final byte
in a full streamed archive. Fault tests cover overflow on load/verification, archive failure,
sidecar replacement, frozen recovery exports, partial staging and no-space archive reuse.

Intermediate artifact SHA-256 (identification, **not** fixture-free delivery certification):

- Debug app: `8c84b656f7c30329c19ea678343c68fa41f00698df8d66c6856f583c56103ce3`.
- Separate instrumentation APK: `517aa0f9fbbbb5cb98efd698d230a8b7d0e24e46a46c9e0810e062fe2f217342`.

### Still unverified / not a handoff

No connected tests ran. Two additional guarded platform tests (fixed legacy migration and
`.new`-only start-over/export) compile, but execution still needs a provisioned disposable
device and renewed guard authorization. No owner-installed history was accessed or backed up.
Minimum-device memory certification, platform/device/accessibility coverage, real-data-only UI,
restore confirmation, schedule controls and fixture-free artifact inspection remain later gates.
Existing screenshot/theme tests now explicitly choose DARK in isolated setup rather than
assuming that a fresh SYSTEM preference resolves dark. No new screenshot claim is made.
