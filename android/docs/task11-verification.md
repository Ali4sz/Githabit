# Task 11 — schedule-aware Insights, history and date inspection

Work verified locally on **2026-11-14**, based on Task 10 revision `cfc2e48`.
Intermediate app, not the final personal-use/transfer handoff. No physical-phone
operations, push, publication, or APK upload are part of this task.

## Implementation

- `domain/ProgressAnalytics.kt` indexes actual amounts/counts and timestamp-ordered
  history in one source-list pass. Per-repo/date/combined history queries reuse
  immutable lists. Inspection bounds retain creation/history and the latest stored
  date, including future records; no heuristic origin filtering or truncation.
- `domain/HeatmapQueries.kt` returns 13/26/52 resolved periods, traversing adjacent
  boundaries instead of assuming seven-day shifts. Bridges keep their 1–6 actual
  dates. Overview intensity is commit count (0/1/2/3–4/5+), not achievement. Per-repo
  intensity is actual/dated-target ratio capped at 100%; future dates are uncredited.
- ViewModel load/save preparation builds indexes on the IO dispatcher before
  verified publication. Theme-only changes reuse the analytics object; date refresh
  reuses the unchanged commit index. Today feedback shares its prepared index with
  persistence. Storage verification, retryable Undo and replacement barriers remain.
- `NavigationState.kt`, `FormState.kt`, `GitHabitViewModel.kt` save bounded correction
  origin date/repo/focus identities, decode old overlays, and match drafts by entity
  rather than origin. Verified correction/deletion, clean cancellation and confirmed
  discard return to that date; failed/indeterminate saves retain drafts. Replacement
  clears origin along with other transient state. Focus requests are one-shot.
- `ui/DateInspector.kt` makes date inspection a bounded lazy sheet with stable keys,
  full-width date entry/Go, Previous/Next/Today, exact resolved period bounds and
  textual eligibility, actual/target amounts, distinct qualifying/practice dates,
  weekly context and editable records. Future records are explicitly labeled.
- `ui/Sheets.kt` keeps a sheet stable when its date changes; inspection list state
  survives a correction round-trip. Finished correction presentation is discarded
  independently of inspection state. Dirty-draft safeguards remain shared.
- `ui/HistoryComponents.kt`, `ui/Screens.kt`, `ui/InsightsContent.kt` use keyed lazy
  history/Insights content with indexed habit lookup. Detail retains optional logging,
  numeric progress, status changes and correction/Undo. Insights defaults to the
  current resolved period and navigates Previous/Next/Today; day/week adherence stays
  separate, unavailable with zero denominators, and excludes pending/future/practice.
- `ui/Components.kt` removes all tiny Canvas actions and automatic end scrolling.
  Weekday rows follow the current preference without changing historical columns.
  Explicit legends explain neutral/partial/target-reached progress and bridge gaps.
  Labeled date controls provide exact inspection without relying on chart colors.
- Touched history/Insights/Guide copy uses resources/plurals and locale-aware dates,
  weekday names, numbers and recording times. Native README documents the intentional
  change from legacy average-score overview to recorded activity. No schema, pinned
  dependency, application/package ID or web-prototype changes.

## Checks and evidence status

Current Compose docs were retrieved via Context7 resolution and two scoped queries,
plus TinyFish search/fetch of official lists/semantics guidance. The pinned baseline
M3 BOM `2025.09.01` remains unchanged.

Build environment: Linux, OpenJDK **21.0.13.0.3**, Gradle wrapper **8.14.3**, AGP
**8.13.0**, Kotlin/Compose compiler plugin **2.2.20**, SDK build tools **36.0.0**,
compile/target API **36**, JVM target **17**. Existing pinned-toolchain compatibility
caveats from the build plan remain; a successful local run is not a vendor-support
claim or a dependency-upgrade audit.

Host command from `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

Final exact-source host rerun completed successfully in **120 seconds**, all 105
tasks executed. **288 JVM tests per debug/release variant**, zero failures/errors/
skips. Lint: **zero errors, ten warnings**: eight existing SDK/tool/dependency update
advisories and two `PluralsCandidate` heuristics on the collective adherence counter
unit labels (`habit-dates` / `habit-weeks`). No warning is suppressed. Reports are
under `app/build/test-results/`, `app/build/reports/tests/` and
`app/build/reports/lint-results-debug.{xml,html}`. Exact host log:
`/tmp/opencode/task11-host-final.log`.

Fresh guarded emulator: **GitHabit_Disposable_Task11**, **emulator-5592**, Android
16/API 36 Google APIs x86_64, SwiftShader, no snapshots, 2560MiB RAM/256MiB heap.
Registration `/tmp/opencode/githabit-task11-avds`; ignored userdata
`android/.gradle/task11-avds/device.avd`. Explicit AVD identity, display and memory
replace manager defaults; **`hw.mainKeys=no`** is required for actual system edge
gestures. The initial default `yes` left SystemUI's `EdgeBackGestureHandler` detached
despite navigation mode 2. After correcting the profile and cold restarting the exact
AVD, SystemUI reports enabled/attached/gesture-handling true. Provisioned/setup-complete
values are 1; gesture overlay is enabled. API 36 needs no legacy developer-animation
toggle (verified against current Android documentation). Runner checks actual
boot/AVD/emulator identity and marker
absence before installing. `/tmp/opencode/run-task11-tests.sh` creates a fresh UUID
marker and supplies exact package/AVD/token arguments; it removes the marker at exit.
It now explicitly requires the JUnit `OK (N tests)` summary and rejects failed/crashed
instrumentation status codes, because adb exit zero is not proof of passing tests.
No generic connected-device command or physical target is used.

Reproducible guarded invocation (after explicitly provisioning/booting that AVD and
building the APKs; run from `android/`):

```sh
set -euo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
SERIAL=emulator-5592
AVD=GitHabit_Disposable_Task11
"$ADB" -s "$SERIAL" wait-for-device
test "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')" = 1
test "$("$ADB" -s "$SERIAL" shell getprop ro.boot.qemu.avd_name | tr -d '\r')" = "$AVD"
test "$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
"$ADB" -s "$SERIAL" shell 'test ! -e /data/local/tmp/githabit-disposable-test-target'
TOKEN=$(cat /proc/sys/kernel/random/uuid)
trap '"$ADB" -s "$SERIAL" shell rm -f /data/local/tmp/githabit-disposable-test-target' EXIT
"$ADB" -s "$SERIAL" shell "printf '%s' '$TOKEN' > /data/local/tmp/githabit-disposable-test-target"
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" -s "$SERIAL" shell am instrument -w -r \
  -e disposableDevice true -e expectedTargetPackage com.githabit.prototype \
  -e expectedAvdName "$AVD" -e disposableToken "$TOKEN" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/opencode/task11-results.log
grep -Eq '^OK \([0-9]+ tests?\)' /tmp/opencode/task11-results.log
! grep -Eq '^INSTRUMENTATION_STATUS_CODE: -|^INSTRUMENTATION_FAILED:|^FAILURES!!!' /tmp/opencode/task11-results.log
```

Initial four-case instrumented run: future inspection/discard/200% text and schedule
eligibility/bridge cases passed. Two harness selectors were fixed (lazy key is not a
test tag; modal/background text must be scoped). It is not counted as a suite pass.

Intermediate full suite: **62/64 passed**, 261.733 seconds. All five
`HistoryInsightsTest` cases passed. Two failures were resolved: the flow scroll helper
now detects both repo/log forms instead of searching background lazy history for form
buttons; the emulator physical-key profile prevented actual edge gesture previews.
Real system gesture assertions remain unchanged, including cancellation and single-pop.

Final exact-APK suite: **64/64 passed**, **241.140 seconds**, no failure/error/crash/
skip status. Fresh opt-in marker was removed by the runner. All existing storage,
recovery, command, form, preferences, theme, Today, adaptation and actual gesture
checks passed, along with the five new history cases:

- Closed 3-day quota: separate 1/1 habit-week adherence, unavailable habit-date
  adherence; qualifying correction below target recomputes the closed result;
  deletion and Undo retain the exact inspected date and recorded expectations.
- Distinct selected-weekday not-scheduled, eligible daily missed, weekly partial
  opportunity and six-day unscored bridge states; no record loss.
- Retained future record accessible via date entry at 200% font/320dp width; dirty
  correction cancellation/discard retains origin and existing note. Semantic focus
  is operable; full assistive-technology focus return is still unverified.
- Empty Insights has zero activity/unavailable adherence; heatmap has no cell click
  actions; exact date view contains no fabricated history.
- Real Activity recreation retains inspection origin and correction draft, saves
  the reviewed amount, and restores the same inspected date after another recreation.

Exact suite log: `/tmp/opencode/task11-full-final.log`. App/test APK hashes below
are the artifacts installed by this runner, not an earlier successful build.

Disabled-animation exact-APK rerun: **64/64 passed**, **176.909 seconds**, with
window/transition/animator scales **0/0/0**. Log:
`/tmp/opencode/task11-full-disabled.log`. Both final logs have exactly 64 successful
test statuses, no negative test statuses, failure summaries, skips or reported
instrumentation crashes. Terminal `INSTRUMENTATION_CODE: -1` is AndroidJUnitRunner's
successful completion here, not a negative per-test status. This rerun establishes
the automated journeys with animations disabled, not the full accessibility matrix.

Final read-only review found no significant remaining issues after checking the
prepared-index sharing guard, correction recovery/cancellation, inspector focus/list
state lifecycle, and zero/future semantics. This is static review, not runtime evidence.

## Exact intermediate APK audit

The final debug app/test APKs built above are distinct artifacts:

Verified native source/config snapshot SHA-256:
`9508b64d36aca25afa9e2f294dfa467c430bb56f48b79e7c2e85abc8734ea17d`.
This identifies the uncommitted Task 11 implementation on `cfc2e48` used for the final
host build and both final suites. Rechecked after teardown; only documentation was
edited after that build. Reproduce from `android/` with Python:

```python
from pathlib import Path
import hashlib

files = [p for base in ('app/src/main', 'app/src/test', 'app/src/androidTest')
         for p in Path(base).rglob('*') if p.is_file()]
files += list(map(Path, ('app/build.gradle.kts', 'build.gradle.kts',
                        'settings.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties')))
digest = hashlib.sha256()
for path in sorted(files):
    digest.update(str(path).encode() + b'\0' + path.read_bytes() + b'\0')
print(digest.hexdigest())
```

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `9171106f470b3bdda1259c34ee4fbe00d72a82cd3343b6bdf5b092978592300c` |
| Separate instrumentation APK | `076bf8abac98efa942f9b2f0ed403d6ce3f1b7351f7d63a55a3bedf74cbdaef5` |

`aapt2 dump badging/xmltree/resources`, ZIP entry inventory and SDK `dexdump` across
**every** app dex establish application `com.githabit.prototype`, version `1.1-local`
(code 2), min API 26, target/compile API 36, GitHabit label and `MainActivity` launcher.
721 app-owned compiled classes; no `SampleWorkspace`, test factories/test suites,
old auth screens, or sample/auth/seed methods. No app assets/raw fixture payloads or
old demo/default-person resource text. The separate test APK contains its test-owned
classes and `assets/fixtures/v1-mixed-signed-out-dark.json`, proving test separation.
Backup remains disabled; merged debug manifest contains library-owned test/preview,
startup and profile-installation components, not an app-owned seed entry point.
APK signing verifies with the existing local Android Debug certificate (SHA-256
`8d022d0486e4f3f26a06b825c02780494c585ea2f313d81ecb4cccb845b0de25`).

Audit outputs: `/tmp/opencode/task11-{badging,manifest,resources,apk-entries,signing}.txt`
and per-dex app/test inventories. Initial UTF-8 decoding of dexdump strings failed;
the audit was rerun across all dex files with replacement decoding and checked class/
method descriptors, not counted as complete from a partial scan.

Read-only review fixes: daily bridge obligations no longer get practice outlines;
transitional completed dates are practice, not quota credit; focus requests are
consumed once; history indexing runs off Main and shares Today candidate preparation.
JVM regressions cover sorted indexed queries, exact dates, future uncredited activity,
origin/draft restoration, cancellation/deletion, failure, replacement and chart math.

## Installed identity and disposable teardown

At **2026-11-14 12:32:15 UTC**, read-only checks against explicitly selected
**emulator-5592** confirmed AVD **GitHabit_Disposable_Task11**, emulator property 1,
Android 16/API 36/x86_64, and absent opt-in marker. Installed package
`com.githabit.prototype` reports version `1.1-local`, code 2, min API 26, target API
36. Its sole base APK was read with serial-selected `adb exec-out cat` and hashed:
**`9171106f470b3bdda1259c34ee4fbe00d72a82cd3343b6bdf5b092978592300c`**,
equal to the locally audited/tested debug app. The post-suite crash-buffer dump is
empty. Evidence: `/tmp/opencode/task11-installed-{identity,hashes}.txt` and
`/tmp/opencode/task11-crash-buffer.txt`.

At **12:32:28 UTC**, after rechecking AVD/emulator identity and marker absence, only
that disposable target's display overrides were reset and settings restored:
physical **720×1600px/320dpi**, font **1.0**, window/transition scales **1.0/1.0**,
animator scale **unset (`null`, system default)**. The result exactly matches
`/tmp/opencode/task11-settings-before-disabled.txt`; the marker remained absent.
Serial-selected `adb -s emulator-5592 emu kill` acknowledged shutdown. At
**12:32:42 UTC**, exact-serial `get-state` failed with device not found, and no
matching emulator/QEMU process remained. The emulator background job also completed.
Evidence: `/tmp/opencode/task11-teardown-{settings,kill,stopped}.txt`.
No phone operation, personal-workspace change, publication or push occurred.

**Coverage limits:** full TalkBack/hardware-keyboard focus return, actual process death
for the new correction origin, oldest-supported Android, folding/tabletop hardware,
actual split-screen divider, RTL/pseudo-localization, all-screen 200% pixel audit,
capacity/long-history frame/memory profiling and final restore journeys remain
unverified. Semantic focus assertions and Activity recreation are not substitutes.
