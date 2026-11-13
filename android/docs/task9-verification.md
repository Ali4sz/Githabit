# Task 9 — accessible schedule forms

Verified locally **2026-11-13**. Intermediate personal-use build; no publication,
push, APK upload or physical-phone operation occurred.

## Implemented

- `FormState.kt` and `GitHabitViewModel.kt` own one small raw repo/log draft and its
  opening baseline through `SavedStateHandle`. New name, description, target and
  unit start empty. Daily, book icon and green accent remain intentional defaults;
  selected weekdays start empty and weekly N starts at 1. Hidden schedule selections
  survive switching kinds without making a reverted active schedule dirty.
- Invalid/cleared text, changed icon/accent and active schedule changes are meaningful
  work. Back, close, scrim dismissal, drag dismissal and navigation converge on the
  same discard gate. Cancel retains input; verified save/replacement may dismiss
  directly. Saving blocks input/dismissal and has visible live-region feedback.
- Opening and cancelling forms persist no record. Raw draft state is separate from
  workspace storage. Large unchanged legacy text is represented in saved state by
  identity-bound field references with SHA-256 value checks; literal marker-like
  input is escaped. Stored legacy strings remain unchanged on unrelated edits.
- Creations preallocate a stable ID. Saved submission fingerprints reconcile exact
  verified candidates on restoration/recovery, including edits, corrections and
  no-op saves. Changing/reviewing inputs invalidates old submission metadata.
  Entity fingerprints prevent stale corrections or cosmetic edits from silently
  overwriting newer verified entity state. Date changes require another submission.
- Recovery of unrelated maintenance preserves drafts; confirmed replacement clears
  them. Unknown save outcomes are reconciled, never automatically replayed.
- `ui/Sheets.kt` uses exactly three labeled radio choices and full-name checkbox
  rows, parent-owned selection semantics, 48dp minimum height, wrapping labels and
  focus outlines. Weekly N has labeled integer choices 1–7. Target and unit always
  stack. Note instructions are neutral rather than a reading example.
- Proposed weekly transition copy uses the same dated domain edit and analytics as
  Save. Cosmetic/no-op edits, first-day configuration and full same-day reverts do
  not fabricate warnings. Habit changes remain today-effective and distinct from
  future-effective Settings calendar changes.
- `ui/Components.kt` shares schedule summaries with `ui/Screens.kt` detail. The
  hard-coded DAILY badge is gone. Weekly copy explains target-completed distinct
  dates, resolved period bounds, active calendar bridges and unscored practice.
  Quota x/N display caps at N; today’s target amount does not claim weekly success.
  Today/Insights redesign remains Tasks 10–11.
- New copy is in `res/values/strings.xml`, including singular/plural day counts.
  No dependency, package ID, workspace schema or web-prototype changes.

## API checks

Current guidance retrieved with Context7 (`ctx7` library resolution plus two scoped
queries) and TinyFish search/fetch:

- https://developer.android.com/develop/ui/compose/components/radio-button
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://developer.android.com/develop/ui/compose/components/bottom-sheets

The actual pinned M3 **1.4.0** AAR was checked with `javap`, including
`rememberModalBottomSheetState`, `confirmValueChange`, sheet gesture support and
properties. Newer documentation’s replacement sheet APIs were not adopted. Sheet
presentation is recreated after cancelled discard, retaining centrally owned drafts,
to avoid keeping a vetoed dismissal’s presentation transform. This is not independent
certification of a real predictive **modal** gesture.

## Final automated results

From `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

- **271 JVM tests per debug/release variant**, zero failures/errors/skips. Sixteen
  new `FormStateTest` cases cover blank defaults, schedule validation/identity,
  meaningful/reverted work, bounded decoding, saved-state restoration, discard paths,
  write failure/replacement, transitions, stale configuration, pre-completion
  creation/edit/correction snapshots, legacy references, literal tokens, unrelated
  recovery and stale submission invalidation.
- **Lint: zero errors, eight existing SDK/dependency advisories.** New plural
  advisories were corrected rather than suppressed.
- Debug app and separate test APK built. Final full build **94 seconds**;
  subsequent test-only rebuild updated old tests for the new form contract.
- Reports in `app/build/test-results/` and `app/build/reports/`.
  Host logs under `/tmp/opencode/task9-*` are ephemeral, not shipped assets.

## Guarded emulator

Fresh AVD **GitHabit_Disposable_Task9**, exact serial **emulator-5588**, Android
16/API 36 Google APIs x86_64, SwiftShader, no snapshots, 2560MiB RAM/256MiB heap.
Registration: `/tmp/opencode/githabit-task9-avds`; ignored userdata:
`android/.gradle/task9-avds/device.avd`. The AVD manager reported a missing device
metadata file after creating its config; its identity, RAM and physical display
were explicitly set before boot. Actual boot/AVD identity passed the runner guard.

Runner `/tmp/opencode/run-task9-tests.sh` checks exact identity, boot completion and
absence of an old marker, creates a new UUID marker and passes explicit disposable
arguments to the separate test runner. Device-setting tests additionally require
`DISPOSABLE_EMULATOR`, never physical sandbox authorization.

| Final full suite | Result |
| --- | --- |
| Normal animations | **53/53**, **198.320s** |
| All three animation scales 0 | **53/53**, **143.946s** |

Logs: `task9-final-device2.log`, `task9-final-no-animations.log`. Passing counts and
absence of failure/crash statuses were checked; Android instrumentation shell exit
alone is not evidence of success.

Eight new instrumented cases cover all schedule round-trips, invalid blank/weekday
submission, effective weekday order, retained hidden selections, recreation,
Back/close/navigation/semantic-scrim/real-handle-drag discard, log/correction discard,
reverted edits, capped weekly summary/calendar bridge, keyboard-mode focus/Enter and
**actual system font scale 2.0 at 320dp nominal width with IME-open submission**.
Existing command-date, recovery, navigation, preference, theme and fixture-guard
tests reran in both full suites. Existing system route gestures also reran.

Initial device failures exposed old tests assuming silent dirty dismissal and
prefilled target/unit; they now explicitly confirm discard or enter required inputs.
Two new test matchers needed substring mode and the scrim needed its semantic action
rather than a coordinate click through the sheet-covered center. Keyboard focus
required explicit keyboard input mode. Final totals above supersede focused runs.

## Review

Read-only review found creation replay after pre-completion restoration, oversized
legacy draft state, unrelated recovery invalidation, uncapped quota display, vetoed
sheet presentation, stale edit/correction revisions, marker collisions and stale
submission metadata. Each was resolved with contained fixes/regressions. Final
follow-up found no significant unresolved correctness issues. Reviewer did not
independently execute the host/device results.

## Artifacts and cleanup

| Inspected artifact | SHA-256 |
| --- | --- |
| Debug app | `bac25da5b057dbba163f02ca4e0e3464ff48cabca34e70a08a927f372a6e3e2a` |
| Separate debug test APK | `9d9931a362708c67b70d97af500a9685df0a55e3f4c9b439ee7d16c99fd3944f` |

Installed base APK hashes matched. `apksigner` verification, manifest, defined DEX
packages, resources and ZIP inventory were inspected. No app-owned fixture factory,
demo generator, seed marker or dataset was found. The `seedInitialized` match belongs
to Kotlin’s random library, not an app data generator. Identity remains
**com.githabit.prototype, 1.1-local, code 2**; no INTERNET/account/broad-storage
permission was added. Legacy `signedIn` compatibility decoding remains untouched.

Marker absence verified; physical display restored to **720×1600px/320dpi**, font
scale **1.0**, animation settings **1.0/1.0/unset**. Exact Task 9 emulator stopped
successfully. Synthetic userdata remains only in ignored disposable storage.

**Coverage limits:** no Task 9 host-driven real process-death journey (fresh-handle
JVM snapshots and Activity recreation are distinct evidence), no full TalkBack or
hardware-keyboard traversal, no real predictive modal gesture verification, oldest
supported Android, folding/tabletop hardware, actual split-screen divider, RTL,
all screens at 200%, long-history performance or final transfer journey. The narrow
form test verifies operation, not a complete pixel-clipping audit. Physical phone was
untouched. Task 10 is next; these gaps remain explicit.
