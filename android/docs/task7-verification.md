# Task 7 — local entry and preferences

Work started **2026-11-08**, from `d902697`. No physical phone or personal
workspace is used. This remains an intermediate build, not a final handoff.

Subsequent, separately authorized physical-phone testing is recorded in
[task7-phone-verification.md](task7-phone-verification.md); the evidence below
describes the original emulator-only implementation verification.

## Local entry / fixture isolation

- Removed production `SampleWorkspace` and every auth/sample entry command and UI.
  JVM callers use `src/test/.../domain/SampleWorkspace.kt`; device journeys use the
  smaller `src/androidTest/.../fixtures/WorkspaceFixtures.kt` before Activity launch.
  The existing fail-closed device guard still precedes storage access.
- One welcome explains repos/commits, with Create first repo and Open empty
  workspace. Onboarding is persisted/verified before entering; no records are
  generated. Populated workspaces bypass welcome independently of onboarding.
- Loading/recovery gates and draft holders remain. No records or profiles are
  filtered by sample-looking IDs or content; legacy `signedIn` decoding remains
  compatibility-only.
- Removed fabricated blank-description fallback and visible demo/prototype copy.
- Version **1.1-local**, code **2**, replaces 1.0-demo/code 1. This names the local
  development line, not a readiness certification. Application ID, package, schema,
  signing configuration, and dependency versions are unchanged, preserving ordinary
  same-key update compatibility. No reinstall on a personal device is authorized.

Step-one verification:
`./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
--console=plain --no-daemon` passed (`/tmp/opencode/task7-entry-verified.log`).
240 JVM tests per variant, zero lint errors; instrumentation compiled, not yet run.
New command regressions cover verified welcome, duplicate/no-op entry, retry and
unchanged history. Initial compilation found an ambiguous wildcard `R` import;
an explicit app-resource import resolved it.

Step one was committed as `1830407`. The following evidence covers step two on
top of that revision, verified **2026-11-08**. No push or publication was performed.

## Preferences and empty-state implementation

- `ui/Settings.kt` now owns optional profile, System/Light/Dark, calendar, and local
  data actions. The Boolean theme adapter is removed. Theme radios reflect saved
  preferences rather than merely the currently rendered system appearance.
- Profile fields allow blank values, use 40/160-character limits with pluralized
  remaining counts, and render a neutral workspace/person icon. Unedited legacy
  strings remain verbatim. Successful and normalized no-op saves acknowledge the
  draft; failed saves retain it. `GitHabitViewModel.update` has an explicit
  unchanged-result callback for that acknowledgement.
- Week start offers all seven full localized weekday names, single-choice roles,
  minimum 48dp rows, and an explicit Save. Saved effective/pending dates are separate
  from unsaved previews. Domain `CalendarStatus` supplies pending boundaries, fixed
  bridge ranges, and projected first-full-week dates; Settings does not recalculate
  the period policy. Cancellation previews require Save and remove only a pending
  request. Date changes require a second reviewed submission.
- Profile/calendar drafts survive Activity recreation and recovery to the verified
  previous workspace. Pending activation does not discard another unsaved choice.
- `ui/Screens.kt` provides an honest no-repos Today state;
  `ui/GitHabitApp.kt` labels loading separately from recovery/empty content.
  Welcome, preference and new empty/loading copy use string resources.

## Final automated checks

From `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

- **PASS:** 243 JVM tests in **each** of debug/release, zero failures/errors/skips.
- **PASS:** debug app and separate instrumentation APK build.
- **PASS:** lint, zero errors; eight existing version/target/dependency advisories.
  The newly introduced character-count warning was resolved with plurals.
- Build log: `/tmp/opencode/task7-reviewed-build.log`. XML reports are under
  `app/build/test-results/`; lint report is `app/build/reports/lint-results-debug.xml`.
- Pinned baseline unchanged: Gradle 8.14.3, AGP 8.13.0, Kotlin/Compose compiler
  2.2.20, Compose BOM 2025.09.01, Material 3 1.4.0, compile/target SDK 36, min SDK 26.
  The previously recorded vendor compatibility caveat still applies; passing this
  build is not a claim that every pinned toolchain combination is vendor-certified.

### Guarded emulator runs

Newly provisioned **GitHabit_Disposable_Task7**, exact serial **emulator-5584**,
API 36 Google APIs x86_64; 720×1600px at 320dpi (360×800dp nominal display),
2560MiB RAM, 256MiB heap, SwiftShader, no snapshots. This display size is not a
measurement of the app's usable inset-adjusted viewport. No physical phone was
installed, reset, tested, or captured. Guard unit cases mentioning physical devices
exercise synthetic evidence only.

AVD registration: `/tmp/opencode/githabit-task7-avds`; disposable userdata:
ignored `android/.gradle/task7-avds/device.avd`. Launch command:

```sh
ANDROID_AVD_HOME=/tmp/opencode/githabit-task7-avds \
  "$HOME/Android/Sdk/emulator/emulator" -avd GitHabit_Disposable_Task7 -port 5584 \
  -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect
```

Local runner `/tmp/opencode/run-task7-emulator-tests.sh` checks that serial's exact
AVD name and completed boot before creating a fresh UUID marker. It invokes:

```sh
adb -s emulator-5584 shell am instrument --user 0 -w -r \
  -e disposableDevice true -e expectedTargetPackage com.githabit.prototype \
  -e expectedAvdName GitHabit_Disposable_Task7 -e disposableToken "$TOKEN" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner
```

`$TOKEN` must match `/data/local/tmp/githabit-disposable-test-target` on this
explicitly provisioned emulator. The test guard independently verifies app/test
package, emulator identity, opt-in and marker **before target-context mutations**.
The runner's EXIT trap force-stops the app and removes/verifies removal of the
marker; it also rejects crashes/failures or a result other than `OK (36 tests)`.
Do not reuse these arguments on an arbitrary installation or personal device.

Exact final invocations (after guarded installation of both rebuilt APKs):

```sh
bash /tmp/opencode/run-task7-emulator-tests.sh all 36 /tmp/opencode/task7-reviewed-device.log
bash /tmp/opencode/run-task7-emulator-tests.sh all 36 /tmp/opencode/task7-reviewed-no-animations-device.log
```

- **PASS:** 36/36, default animation settings, **90.467s**.
- **PASS:** 36/36, all three animation scales set to 0, **63.041s**.
  A surrounding EXIT trap restored original window/transition scales `1.0/1.0`
  and removed the previously unset animator-duration value (`null`). Read-back
  confirmed restoration. Marker absence was verified before stopping the exact AVD;
  the emulator process then exited successfully.
- The earlier 34- and 35-test runs also passed, but are superseded by these final runs.
  `/tmp/opencode` logs and AVD state are local, disposable evidence, not repository
  prerequisites or distributable app content.

### Coverage and resolved issues

- Unseeded startup now begins with **missing storage**, checked before welcome
  completion, rather than a persisted empty fixture. Real UI create/log flows
  retain fractional amounts, remaining-only completion, correction/deletion and
  Undo. Separate fixture journeys preserve pause/archive/resume/history coverage.
- No-reseed checks exercise welcome, cancelled forms, help, profile save/clear,
  theme/calendar changes, navigation, recreation, restart, reset and clock changes.
  Legacy signed-out/mixed-history preservation continues to pass.
- Preference tests cover all seven choices, saved-vs-preview state, immediate-empty
  saves, pending replacement/cancellation, fixed active bridges, projected dates,
  stale-date review, failure rollback, and indeterminate-save recovery retaining
  both profile and calendar drafts. JVM tests verify acknowledgement timing and
  unchanged legacy profile strings.
- Final read-only review found a negative remaining-character count for padded
  legacy names (the codec validates trimmed name length). The display is now clamped
  to zero without rewriting the saved name. A device regression checks that count
  and preserves the original padded name through an unrelated profile-note edit.
- Existing theme-contract tests rerun SYSTEM response and explicit override checks.
  The large-text weekday test checks full labels, radio roles, row height, focus,
  Enter activation, persistence and no generated records at injected 2.0 font scale.
  Programmatic focus/Enter is **not** full keyboard traversal or TalkBack testing.
- Initial preference device tests incorrectly scrolled the fixed toolbar Settings
  button; direct click fixed the helper, after which the suite passed.
- Initial AVD launch lacked userdata space in `/tmp`; only this disposable AVD was
  relocated into ignored Gradle storage. During final compilation its process was
  killed (exit 137); after the successful build it was restarted and identity-checked
  before final APK installation. No device test failure was concealed by either event.

## Final debug APK inspection

Inspected **the rebuilt, emulator-installed** app, not the earlier intermediate APK:

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `4b315fb34febf9f15f485d759cbb30686be25051b830af41bc49bde594e4fd74` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` (test-only) | `625be60d2b3f8dd030b978eb2d4ae723f7e071f73c2ae42317bf53794b197ef2` |

Device-side SHA-256 of each installed `base.apk` matched its local artifact.
`apksigner verify --print-certs` succeeded. This is the debug development artifact,
not a final personal-use handoff; the test APK must remain separate.

Inspection used `apkanalyzer dex packages --defined-only`, `apkanalyzer manifest
print`, `aapt2 dump resources`, ZIP inventory, and DEX/resource forbidden-marker
scans. Evidence under `/tmp/opencode/`: `task7-final-debug-dex.txt`,
`task7-final-debug-resources.txt`, `task7-final-debug-manifest.xml`,
`task7-final-apk-inventory.txt`, `task7-final-debug-signing.txt`, and
`task7-final-artifact-audit.txt`.

- No `SampleWorkspace`, `WorkspaceFixtures`, test fault factories/test classes,
  old auth screens/commands, fixture IDs or old auth/demo tags detected in compiled
  app definitions/resources. Local `WelcomeScreen`/`finishWelcome` remain present.
- No `assets/`, `res/raw/`, fixture JSON payloads, or runtime preview seed hooks.
  Source directories remain main/test/androidTest; no debug fixture source set or
  dependency/source-set redirection was introduced. Dependencies are unchanged.
- Manifest retains `com.githabit.prototype`, version 2 / 1.1-local and MainActivity
  launcher. Baseline debug-tooling PreviewActivity/ComponentActivity entries remain;
  they are not app-owned seed entry points. No INTERNET/account/storage permission
  was added. The only uses-permission is the app's AndroidX dynamic-receiver signature
  permission. Legacy `signedIn` exists only in the v1 compatibility decoder.
- Only debug was inspected as an installable artifact here; no release APK/AAB was
  delivered or certified by this audit.

## Visual evidence and remaining limits

New screenshots in [`../previews/task7/`](../previews/task7/) are native captures
from the disposable emulator, never runtime assets or personal history:

- `01-local-welcome-empty.png`: missing-storage welcome, no fixtures/profile.
- `02-empty-workspace.png`: welcome completed, form cancelled, still zero records.
- `10-settings.png`: test fixture journey's Settings viewport, blank optional profile.

Direct inspection found no visible text/control clipping or fabricated profile in
these three normal-text viewports. Settings continues below the viewport; calendar
date correctness is established by semantic tests, not these screenshots. Images
directly under `previews/` are older prototype evidence, **not Task 7 UI**; their
sign-in/onboarding images are obsolete and retained only as historical evidence.

**Unverified:** full TalkBack use, hardware-keyboard Tab/D-pad traversal, complete
screens/forms at system 200% nonlinear scaling, pseudo-localization/RTL, adaptive
window matrix, oldest-supported Android, process death distinct from Activity
recreation, long-history performance and full transfer journeys. These are not
silently counted as passed. Schedule forms/neutral target-unit inputs (Task 9),
populated Today/Insights semantics and layout (Tasks 10–11), restore enhancements
(Task 12), and final accessibility/artifact handoff (Tasks 13–15) remain later work.
