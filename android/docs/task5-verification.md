# Task 5 — date-consistent commands and conflict-safe Undo

Verified 2026-11-06 (final report inspected at 10:09 UTC), based on Task 4 revision `bc9b248` plus the Task 5 commits containing this document. Native Android only; root web prototype unchanged. No remote publication or personal-device access.

## Implementation

- `domain/AppClock.kt`: `CommandTime` captures one instant and zone; its local date is derived from that pair. Commands do not independently call `today()` or use default wall-clock commit timestamps. `completeGoal` now accepts the captured timestamp.
- `GitHabitViewModel.kt`: existing mutex, busy admission, verified persistence, and recovery states remain authoritative. Command preparation, validation, dated feedback, and Undo metadata use one captured context inside the mutex. Duplicate in-flight submissions are rejected; redundant completion/removal/configuration/correction saves create no success token.
- `saveHabit`, new `saveCommit`, and `setWeekStart` compare the displayed/intended date with the command date. A mismatch saves no user change, retains the draft, reports the applicable date, and requires another submission. Corrections retain the requested commit ID, date, and timestamp; missing/mismatched entities fail explicitly.
- Week-start commands use the existing `requestWeekStart` and `WeekCalendar` policy. Immediate-empty application, all seven choices, pending replacement/cancellation, locked bridges, and original boundary dates persist through the same verified-write path. No generic calendar Undo is offered. Task 7 supplies Settings controls.
- Launch/foreground refresh persists crossed pending boundaries on their original dates. Pending observations survive ordinary candidate writes, verified rollback, and indeterminate recovery—even when the device clock subsequently moves backward. Evidence is reconciled against the actual verified snapshot, not a stale published snapshot during recovery replacement. An unresolved ordinary candidate's boundary remains observable while recovery is open. Cancelled/rolled-back requests cannot contaminate a later request reusing a boundary.
- `domain/UndoRecord.kt`: typed commit/status inverses replace closures. Commit inverses check the expected post-operation record plus habit identity/unit. They cannot overwrite later corrections or restore a deleted last commit under a changed unit. Status inverses change only the original date's status, preserve unrelated targets/schedules/presentation/later dated versions, and remove a status-only dated version when safely redundant. Clock-rollback conflicts remain retryable.
- Undo remains bounded to 20 in-memory tokens. Tokens are consumed only after verified inverse commitment. Verified rollback retains the snackbar action; indeterminate outcomes block replay. Reconciliation of an uncommitted inverse re-offers the Undo action; expired/conflicting tokens explain why no change occurred.
- Verified reset/replacement invalidates routes, drafts, selections, and Undo even for equal-value or same-ID candidates. SHA-256 fingerprints retain all unresolved replacement candidates without retaining additional large workspaces; a later failed recovery attempt cannot erase an earlier replacement barrier. Fingerprinting uses canonical validated v2 encoding on IO. `replaceWorkspace` is an internal confirmed-candidate seam, not an import UI or installed test entry point; Task 12 still owns candidate preview/revision consent.
- `MainActivity.kt`: starts/stops foreground refresh with `onStart`/`onStop`, refreshes on resume, and registers a lifecycle-scoped non-exported receiver for protected date/time/timezone actions. Extras are ignored. The ticker waits at most 30 seconds, shortening its next wait to local midnight using zone-aware date boundaries. No service, alarm, background poller, or dependency was added.
- `ui/Sheets.kt`: saves the intended form date independently of visible Today, retains fields on stale submission, explains the revised date, and never converts a missing correction to a new log. `ui/Screens.kt` and sheet history rows use the observed ViewModel timezone for timestamp presentation; saved date identity is unchanged.

## Verification performed

From `android/`, OpenJDK 21.0.12.1; existing pinned Gradle/AGP/Kotlin/Compose configuration unchanged:

```text
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks --console=plain --no-daemon
```

Final log: `/tmp/opencode/task5-final.log`. **BUILD SUCCESSFUL**, 105 actionable tasks / 105 executed.

| Check | Result |
| --- | --- |
| Debug JVM suite | 234 tests, 0 failures/errors/skips |
| Release JVM suite | 234 tests, 0 failures/errors/skips |
| New command suite | 36 tests per variant, including a loop over all 49 weekday pairs |
| Debug lint | 0 errors; 8 existing version/target advisories |
| Debug app APK | Built freshly |
| Debug instrumentation APK | Built freshly, including two new guarded stale-form journeys |
| `git diff --check` | Passed |
| Independent review | Calendar/reconciliation/Undo findings fixed and regression-tested; final re-review found no remaining material blockers |

Lint advisories: `GradleDependency` ×4, `NewerVersionAvailable` ×2, `OldTargetApi` ×1, `AndroidGradlePluginVersion` ×1. The build also reported the existing inability to strip `libandroidx.graphics.path.so`; packaged unchanged. Existing toolchain compatibility caveats remain in the earlier verification documents.

Report locations: `app/build/test-results/test{Debug,Release}UnitTest/`, `app/build/reports/tests/`, `app/build/reports/lint-results-debug.{xml,html}`.

### Coverage highlights

- Midnight during IO; midnight between admission and execution; stale repo/log/calendar submission; correction-ID preservation; injected instant passed to Complete.
- Leap day, New Year/week boundaries, spring DST jump, repeated autumn hour, timezone travel, same-date zone refresh, clock rollback, foreground midnight wake, repeated start/stop, and multi-day resume.
- Weekly correction/deletion/Undo recomputation for a closed achieved week; historical expectations unchanged.
- Status Undo after same-day target/schedule/presentation changes and later dated versions; status conflict; exact same-day round trip without a false transition.
- Failed Undo retry, both reconciled indeterminate outcomes, re-exposed retry action, expired 20-token bound, reset/replacement invalidation including repeated failed equal-value replacement attempts.
- Calendar write rollback/indeterminate recovery, pending revisions during IO, observation across changed ordinary reconciliation, first-request rollback, committed cancellation reconciliation, and failures of Start over while an ordinary candidate remains unresolved.

Review-driven tests also establish that a pre-boundary cancellation may finish after the boundary using its captured command date, without incorrectly locking a later new request.

## Artifact identities (intermediate, not delivery)

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `d33fcd8b9aa69e5bba9499d7e839d4e1c6c0cde366547cdc0309aba402c4261d` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `a12805e385b162ce36ab536f3635c955ee8c75c7c93c4bd4925a9c0ac2edf7d2` |

## Platform/security review and remaining gates

- Android broadcast lifecycle/flags were checked against current official docs via Context7 CLI and the [broadcast overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts). No dependency bump was needed.
- Fresh merged debug manifest confirms `com.githabit.prototype.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is signature-protected and requested, providing AndroidX's pre-33 non-exported receiver protection. The receiver accepts only the three protected system actions and obtains actual clock values itself; it does not forward intents or parse extras. No new manifest component or broad permission was added.
- The new `CommandFormTest` uses a test-owned clock and synthetic workspace, calls `verifyDisposableDevice()` before any target-context write/Activity launch, and has **compiled only**. Neither it nor other connected tests ran. Device broadcast delivery, real lifecycle/UI interaction, TalkBack, recreation, and minimum-device memory/performance remain unverified without renewed disposable-device authorization.
- In-memory observation/Undo evidence does not survive process death after an unverified storage failure. Restart resolves retained pending boundaries by their stored dates and the current clock; no persistent multi-level Undo or separate persistent clock high-water record was introduced. Verified calendar transitions do survive restart and clock rollback.
- Capacity limits remain provisional. Canonical replacement fingerprints add bounded serialization work on IO; device memory certification remains Task 13's gate, not a claim based on JVM success.
- Runtime sample generation/demo entry, full theme/calendar Settings, schedule controls, and confirmed document restore remain later tasks. These APKs are sample-bearing intermediate verification artifacts, **not a real-use handoff or fixture-free delivery**. No installed workspace was inspected, migrated, reset, or populated during this work.

## Subsequent authorized phone verification

After this report, the owner explicitly authorized installation and GitHabit-only
data resets on a physical phone. The [separate phone report](phone-verification.md)
records the updated test-only guard, new instrumentation APK identity, 22 passing
device tests (including both stale-form journeys), and verified marker revocation.
The compiled-only and no-device statements above describe the earlier checkpoint,
not that subsequent run. Production APK identity is unchanged.
