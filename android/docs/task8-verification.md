# Task 8 — adaptive shell and saved navigation

Verified **2026-11-11**, production/test source **`b7f13f2`**, following
`1153c63` (saved navigation) and the Task 7 phone-evidence commit `4f90a13`.
This is an intermediate local build, not the final personal-use handoff.

## Implemented contract

- `NavigationState.kt` owns a small route snapshot: current route, retained repo
  selection, owning top-level tab, and at most **20** Back-history entries. A bounded
  string-list encoding rejects malformed/oversized identities. No workspace records
  or Undo inverses are placed in saved state.
- `GitHabitViewModel.kt` uses `SavedStateHandle`; its production factory calls
  `CreationExtras.createSavedStateHandle()`. `MainActivity` already obtains that
  factory through `by viewModels`, so it required no change. Route and overlay
  identities restore before UI opens and are validated **after verified disk load**.
  Missing detail returns to Repos, missing selection is cleared, and stale
  edit/correction overlays are dismissed without creating replacement records.
- Workspace-session invalidation is saved. Only verified replacement/reconciliation
  invalidates page/form state. Failed replacement with verified rollback retains
  route, selection, overlay, drafts and the existing session.
- `ui/GitHabitApp.kt` uses baseline M3 `NavigationSuiteScaffold` and its documented
  width/height/posture defaults: bar for compact width/height or tabletop, rail
  otherwise. Three labeled primary tabs remain Today, Repos and Insights; Activity
  and Settings remain toolbar destinations. No drawer or navigation framework was
  introduced.
- Repos uses a keyed lazy list with saveable query, status filter and list state.
  Its provider key is **REPOS**, independent of selected repo. Detail content is
  separated from the page wrapper and gets one provider per repo identity.
  At most **21** detail-state keys are retained; eviction protects every detail
  still reachable through Back and removes unreachable keys first.
- Two panes require at least **900dp × 480dp of usable content**, after navigation,
  toolbar and system insets. They share at most 1440dp; individual readable pages
  retain a 720dp bound. The shell itself has no global 720dp constraint. Tabletop or
  reported hinges conservatively disable the custom two-pane presentation; this
  is not certification of a fold/hinge-spanning layout.
- Expanded Repos does **not** auto-select a repo. Returning to its tab shows the
  list/selection prompt while retaining the previous selection and list context.
  Opening detail and resizing preserves that route; only one detail and one set of
  its semantics/test IDs are composed. Retained list selection has visual and
  semantic feedback.
- Root Today yields to Android. Standard modal windows handle Back before the
  Activity route handler. The consistently composed `PredictiveBackHandler` is
  enabled only for an open, non-root workspace without an overlay. Progress changes
  only nonessential opacity/scale; cancellation resets it. Completion pops once,
  guarded against a changed route, recovery or an active overlay. No duplicate
  hidden destination is rendered for a preview.
- Each navigation item owns independent focus-outline state. New navigation/pane
  copy is in `res/values/strings.xml`; schedule-form and Today/Insights redesign
  work remains in Tasks 9–11.

## API and dependency checks

Current official guidance was retrieved through Context7 and the Android docs:

- [Adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)
- [ViewModel factory and saved-state extras](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
- [State saving](https://developer.android.com/develop/ui/compose/state-saving)
- [Predictive Back progress](https://developer.android.com/develop/ui/compose/system/predictive-back-progress)

The actual resolved AAR classes were inspected with `javap`, rather than copying
newer examples into the pinned stack. The only new direct dependency is
`androidx.compose.material3:material3-adaptive-navigation-suite`, resolved by BOM
**2025.09.01** to **1.4.0**, with adaptive core **1.1.0** and Window **1.4.0**.
Activity remains **1.11.0**, lifecycle **2.9.4**, Compose core **1.9.2**,
Kotlin/Compose compiler **2.2.20**, AGP **8.13.0**, Gradle **8.14.3**;
min/compile/target SDK remain **26/36/36**. No Expressive upgrade occurred.
The previously documented Kotlin/AGP vendor-compatibility caveat remains.
Resolved-tree evidence: `/tmp/opencode/task8-dependencies.txt`.

## Fresh automated results

From `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

- **PASS:** **255 JVM tests in each variant**, no failures/errors/skips. The 12 new
  tests cover route/overlay encoding, malformed bounds, selection/Back ordering,
  history truncation, branch-aware detail-cache eviction, disk-loaded reference
  validation, stale gesture completion and replacement invalidation.
- **PASS:** debug app and separate test APK build; final fresh build **88s**.
- **PASS:** lint, **zero errors**, eight existing target/version advisories.
- Reports: `app/build/test-results/`, `app/build/reports/lint-results-debug.xml`;
  host build log: `/tmp/opencode/task8-reviewed-build.log`.

### Guarded disposable emulator

Fresh AVD **GitHabit_Disposable_Task8**, exact transport **emulator-5586**, Android
16/API 36 Google APIs x86_64, 2560MiB RAM/256MiB heap, SwiftShader, no snapshots.
Registration is `/tmp/opencode/githabit-task8-avds`; disposable userdata is in
ignored `android/.gradle/task8-avds/device.avd`.

```sh
ANDROID_AVD_HOME=/tmp/opencode/githabit-task8-avds \
  "$HOME/Android/Sdk/emulator/emulator" -avd GitHabit_Disposable_Task8 -port 5586 \
  -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect
```

The local runner `/tmp/opencode/run-task8-tests.sh` verifies exact AVD identity,
completed boot and absence of an old marker, creates a fresh UUID marker, and runs:

```sh
adb -s emulator-5586 shell am instrument -w -r \
  -e disposableDevice true -e expectedTargetPackage com.githabit.prototype \
  -e expectedAvdName GitHabit_Disposable_Task8 -e disposableToken "$TOKEN" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner
```

The test guard independently checks app/test package, emulator evidence, opt-in and
matching marker before workspace writes. `AdaptiveNavigationTest` additionally
requires **DISPOSABLE_EMULATOR before window changes or pixel capture**, rejecting
even an authorized physical sandbox. Do not reuse this procedure on a live phone.
EXIT/INT/TERM cleanup stops the app, revokes/verifies the marker and returns the
test display to 720×1600px/320dpi. Results require the expected passing count and
absence of failure/crash statuses, not merely a successful shell exit.

| Final run | Result |
| --- | --- |
| Full suite, normal animations | **45/45**, **126.242s**, 16:49:34–16:51:41 +03:00 |
| Full suite, all three animation scales 0 | **45/45**, **85.913s**, 16:56:46–16:58:13 +03:00 |

Logs: `/tmp/opencode/task8-reviewed-device.log` and
`/tmp/opencode/task8-reviewed-no-animations.log`. The nine new instrumented tests
cover compact recreation, expanded selection/pane uniqueness, live resize with
both scroll states, short/narrow navigation, modal ordering, predictive callbacks,
system gestures, system 200% navigation labels and pixel-checked focus ownership
with Enter activation. Existing logging, Undo, recovery, profile/theme/calendar
and fixture-guard regressions all reran. Earlier focused runs are superseded by
these final totals, not added as extra unique coverage.

Display-size equivalents exercised at 320dpi: **720×1600px (360×800dp)**,
**640×1200/1600px (320×600/800dp)**, **1600×720px (800×360dp)** and
**2400×1800px (1200×900dp)**. These are nominal display sizes, not claimed usable
page sizes. Narrow resize represents split-window constraints; actual Android
split-screen divider interaction was not exercised.

Predictive verification includes both dispatcher-level events and actual
`UiAutomation.injectInputEvent` **left-edge touchscreen sequences delivered through
the system**, with gesture navigation mode 2. Progress was observed while the route
was unchanged, returning to the edge cancelled without a pop, and releasing the
committed gesture popped exactly once. This is not inferred from a Back button.
Modal ordering separately uses platform Back; it is not a predictive-modal gesture
certification. Root handler disablement was asserted.

### Real process death, separate from recreation

After the final build, two host-driven production-Activity journeys passed using
the guarded local `/tmp/opencode/task8-platform-check.py`:

1. Test-owned 12-repo workspace: search **Practice**, filter **All**, open repo 7,
   scroll detail; background with HOME, allow stop/saved-state delivery, `am kill`
   the exact package, verify old PID absent, reopen the retained task and verify a
   **different PID**. Visible text/description **and pixel bounds** matched before/
   after for detail and, after Back, the scrolled list. Query and selected filter
   also restored. Decoded workspace JSON was unchanged.
2. Missing-storage first use: explicitly create one repo through the real UI, open
   an unsaved log with amount **7.5** and note **Return after process death**, then
   perform the same verified process kill/restart. Detail/overlay/draft and Repos
   query/filter restored; disk still contained exactly **one habit, zero commits**.

The process kill did **not** use force-stop or finish/remove the task. Force-stop
was used only after each completed journey for cleanup. ActivityScenario recreation
tests remain separate. These runs do not establish reboot/task-removal restoration.
Logs: `/tmp/opencode/task8-process-scroll.log`, `task8-process-draft.log`;
before/after UI XML files use `task8-*-process-death.xml` names. The host runner and
logs/XML are ephemeral local evidence, not runtime app assets.

## Review and resolved findings

- Read-only review found reachable detail-state eviction after branching at cache
  capacity, shared focus-outline state across tabs, and loss of owning-tab selection
  after deep history truncation. All three were fixed with regressions; follow-up
  review found no new meaningful correctness issues.
- An initial scroll matcher matched both vertical detail scrolling and horizontal
  chart scrolling. It now explicitly selects the vertical range.
- The font test initially treated `didOverflowWidth` as glyph clipping. Diagnostics
  showed a 202px paragraph allocation with a 139px widget and glyph end at 137.39px:
  no glyph was clipped. It now checks every rendered line bound, ellipsis and height;
  navigation labels pass at actual system font scale 2.0 in all tested sizes.
- Host process-test setup initially used stale field coordinates and checked PID
  too soon after HOME. It now reacquires fields and verifies process absence after
  Activity stop delivery. Filter selection is checked on its semantics-owning
  ancestor rather than only its text child. Final journeys passed in full.

## Inspected artifacts and cleanup

| Artifact | SHA-256 |
| --- | --- |
| Debug app | `27752d06810ac820e3acd8593af1980e5213e1411c6754603281881f4d9420da` |
| Separate debug test APK | `270ef4fe8f6eb885be85f0c67b0141a671cb20221f2aaac22bc9167c1fa14d25` |

Both installed `base.apk` hashes matched their freshly built host artifacts.
`apksigner verify --print-certs` passed. Inspection used `apkanalyzer dex packages
--defined-only`, manifest print, `aapt2 dump resources`, ZIP inventory and DEX/resource
forbidden-marker checks. No app-owned fixture factory/test class/seed marker, JSON
dataset, `assets/` or `res/raw/` payload was found. NavigationState and MainActivity
definitions were present. Identity remains **com.githabit.prototype, 1.1-local,
code 2**; the only uses-permission remains the AndroidX dynamic-receiver signature
permission, with **no INTERNET/account/broad-storage permission**. Legacy `signedIn`
compatibility decoding remains intentionally untouched.

Evidence files under `/tmp/opencode/`: `task8-debug-{dex,manifest,resources,signing}`
with their `.txt`/`.xml` extensions and `task8-apk-inventory.txt`. Only debug was
inspected as an installable artifact; no release APK or AAB was delivered/certified.

Original animation settings were restored (1.0/1.0/unset); system font scale returned
to 1.0 and navigation mode remained 2. Final read-back matched; display overrides
were reset to the AVD's physical 720×1600px/320dpi. Marker absence was checked and
the **exact Task 8 emulator was stopped successfully**. Synthetic userdata remains
only in ignored disposable storage. The physical phone and its Task 7 installation
were not touched. No push, publication or APK upload occurred.

**Still unverified:** oldest-supported Android, physical-device Task 8 behavior,
right-edge/root-exit gesture visuals, folding/tabletop hardware, actual split-screen
divider use, full TalkBack and hardware-keyboard traversal, complete screens/forms
at 200%, RTL/pseudo-localization, process restoration after reboot/task removal,
long-history performance and final transfer journeys. These remain explicit gaps,
not passing results. Task 9 is next: accessible schedule creation/editing controls.
