# Task 6 — design foundation verification

Date: **2026-11-08**. Base: `8fdefc4`; theme foundation: `fa737df`; shared-component
work is recorded in the commit containing this document. No publishing or physical
phone installation/testing occurred. Intermediate app still contains runtime demo
features; Task 7 removes them. This is not a personal-use release.

## Changes

- Explicit role pairings, categorical habit/ordered chart palettes, compact
  typography/spacing/shape tokens and System/Light/Dark resolver.
- Single effective theme drives content and system-bar icons/scrims. Light/night
  startup resources with API-27 navigation attributes and explicit status backgrounds.
- Shared component token adoption, larger small text, wrapped controls, scalable
  chart label geometry and high-contrast keyboard-focus outlines.
- Contract and actual pair ratios: [design-contract.md](design-contract.md).

## Local checks

Pinned Gradle 8.14.3 / AGP 8.13.0 / Kotlin 2.2.20 / Compose BOM 2025.09.01 /
Material3 1.4.0, compile/target 36 and minimum 26 unchanged. Existing toolchain
support caveat remains in the plan; no dependency update was needed.

From `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

- **238 tests in each JVM variant**, zero failures/errors/skips.
- Lint: **0 errors**, 8 existing dependency/target advisory warnings.
- App and instrumentation APKs compile/build.
- `git diff --check` passes.
- Reports: `app/build/test-results/test{Debug,Release}UnitTest/`,
  `app/build/reports/lint-results-debug.{html,xml}`. Build logs are local ephemeral
  evidence under `/tmp/opencode/task6-*.log`, not shipped app content.

Final forced pass: `/tmp/opencode/task6-final-verified.log`, all 105 tasks executed.
The app APK hash is identical to the one tested on the emulator:

```text
app-debug.apk
7bb4aafb7162af91ad2e7ef6bc7de83e553b63efa4aa909b24684fd8e172588d
instrumentation APK used for the two full device runs
1e2e751660c0907e9a59c95058b723c5c21eb07847a22cf15b27dd273741cfd3
instrumentation APK after final forced compilation (same source, not device-rerun)
cb8159e7b749a0e7b40b670cb0e392f0b1e83797eeda91b52cb7ba1c0b857b86
```

No APK is a release handoff; fixture-exclusion is not yet satisfied. Test APKs are
verification tools only, never the personal-use deliverable.

## Disposable emulator checks

Provisioned a **new** `GitHabit_Disposable_Task6`, port 5582, under
`/tmp/opencode/githabit-task6-avds`. API 36 Google APIs x86_64, 720×1600px,
320dpi (**360×800dp display**, before insets), 1536 MiB RAM, 256 MiB app heap,
SwiftShader, no snapshots. Existing AVDs and physical phones were not used.

Before install/test, checked exact AVD identity and completed boot. Each invocation
used a fresh random session marker. Instrumentation verified package, opt-in, AVD
identity and matching marker before host launch or workspace access. Rendering
tests additionally reject physical devices before any pixel capture. Their
ComponentActivity host has no workspace IO; fixtures are test-owned in memory.

Reproduction template (only on a newly provisioned disposable AVD):

```sh
# First verify adb -s "$SERIAL" shell getprop ro.boot.qemu.avd_name equals
# the explicitly provisioned GitHabit_Disposable_* name, and boot is complete.
adb -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Generate a fresh random TOKEN and write it into the emulator-only marker.
printf '%s' "$TOKEN" | adb -s "$SERIAL" shell \
  'cat > /data/local/tmp/githabit-disposable-test-target'
adb -s "$SERIAL" shell am instrument --user 0 -w -r \
  -e disposableDevice true -e expectedTargetPackage com.githabit.prototype \
  -e expectedAvdName "$AVD" -e disposableToken "$TOKEN" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner
# Always clean up, including on failure (the executed script used an EXIT trap):
adb -s "$SERIAL" shell am force-stop com.githabit.prototype
adb -s "$SERIAL" shell rm -f /data/local/tmp/githabit-disposable-test-target
adb -s "$SERIAL" shell test ! -e /data/local/tmp/githabit-disposable-test-target
```

Check instrumentation summary, not just adb exit status. Actual full suite:
**28/28 passed** (67.712s), log `/tmp/opencode/task6-full-device.log`.

Repeated **28/28 passed** (46.749s) with window/transition/animator duration scales
all zero on **this emulator only**; log `/tmp/opencode/task6-no-motion-device.log`.
Restored original emulator settings: window=1.0, transition=1.0, animator unset.
Session marker removal verified after each invocation.
The disposable emulator was stopped after verification; no running test target or
active authorization marker was left behind.

New rendering coverage (six tests):
- All six explicit-preference/system-night combinations: actual Surface pixel and
  window status/navigation icon appearance agree, including live SYSTEM changes.
- Resolved light/night startup backgrounds/icon attributes.
- Representative subtitle, badge and focused-error pixels match tested roles.
- Shared card at 320dp with local 1×/2× font scale, both themes: no occupied-line
  overflow, Log/Complete at least 48dp and reachable after scrolling; Log activation
  is asserted in each combination. Complete activation is covered by the existing
  ordinary-flow suite, not the font-scale matrix.
- Keyboard input mode, programmatic focus, visible Log ring pixel and Enter
  activation in both themes. This is not a full external-keyboard traversal audit.
- Negative control proving genuinely clipped text fails the text-bounds helper.

Existing 22 tests also passed: ordinary native flows/recreation, stale-date forms,
migration/recovery/AtomicFile behavior, and five guard cases. Existing screenshot
journey captured **synthetic** content only. Inspected Today light/dark, Repos and
Log sheet locally: no new visible clipping/overlap in these captures. Today still
has the known dashboard-first hierarchy (Task 10). Screenshots remain ephemeral at
`/tmp/opencode/task6-synthetic-screenshots/`; no personal data or QA assets were
added to runtime resources. Vision-agent inspection timed out; direct image
inspection was used instead.

## Issues found and resolved

- Light secondary copy initially failed on surfaceDim (4.23:1); darkened token to
  #536459 (minimum 4.71:1). Dark outline strengthened for meaningful boundaries.
- Lint rejected the API-27 navigation-light attribute in minimum-26 resources;
  moved it to qualified resources instead of suppressing lint.
- Review found an inherited gray light-startup status background; paired it
  explicitly with the light icon-appearance flag before the first commit.
- Test review caught a focus request issued in touch mode; now explicitly enters
  keyboard mode and asserts request success.
- Runtime resource test used an unsorted attribute array; replaced with individual
  theme attribute resolution. No production-resource change was needed.
- String Text semantics reconstruct a full-width paragraph for wrap-content text;
  `hasVisualOverflow` therefore falsely flagged short labels. Helper now checks
  occupied line bounds, max lines and ellipsis, with a failing clipped-label control.

## Still unverified / deliberately deferred

Full TalkBack traversal, hardware-keyboard traversal, RTL/pseudolocales, complete
screen/form 200% nonlinear system-font scaling, short/expanded windows, oldest-API
runtime/scrim appearance and process death remain later tasks. The 2× card check is
an injected Compose density test, not proof of all Android nonlinear font scaling.
The current chart's tiny pointer actions and prototype legend remain Task 11 work.
Some destination-specific literal styling/focus states remain for owning screen
tasks and Task 13. Startup prior to reading a persisted override follows SYSTEM,
as documented; do not claim there can never be a brief theme transition.

The full test suite still exercises prototype sample/auth paths. Passing it does
not establish sample-free delivery, flexible schedule UI, restore UI or final
accessibility readiness.
