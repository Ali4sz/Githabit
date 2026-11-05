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
