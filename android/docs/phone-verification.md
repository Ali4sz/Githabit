# Authorized physical-phone verification — 2026-11-06

Source: production revision `15bd450`, plus the four test-harness changes committed
with this report (`GitHabitFlowTest.kt` and `fixtures/{DeviceGuard,DisposableTarget,
DisposableTargetTest}.kt` under `app/src/androidTest/java/com/githabit/prototype/`).
No production source, dependency, package ID, or web-prototype changes were needed.
This is Task 5 follow-up evidence, **not a finished personal-use handoff**.

## Authorization and scope

- The owner explicitly authorized installing the app/test APKs and creating,
  replacing, and resetting **only GitHabit's app data** on the connected phone.
  GitHabit was not listed as installed before this session's installation.
- Device: CPH2749, Android 17/API 37, current user `0`. Transport serial and
  independently read `ro.serialno`/`ro.boot.serialno` agreed. The exact serial and
  temporary tokens are intentionally not committed.
- Every ADB command selected that exact serial; installation/instrumentation used
  user `0`. No other app or personal-file operations, document-provider browsing,
  broad logs, or device-wide animation/date/timezone/font/display changes were used.
- Tests ran via `adb -s "$DEVICE" shell am instrument --user 0 -w -r`, **not**
  Gradle connected tests (`animationsDisabled = true` would affect global settings).
- Full-device and Compose-root capture are suppressed on physical targets before
  either capture API is called, including failure and teardown paths. The test named
  `screenshotJourneyAcrossNativeScreens` tested navigation without taking pictures.
  No GitHabit screenshot output directory existed after the run. No new screenshot
  or visual/a11y certification is claimed.

## Safety changes and checks

- The existing disposable-emulator branch still requires an exact
  `GitHabit_Disposable_*` AVD, packages, opt-in, and matching valid marker.
- The separate physical branch additionally requires `allowPhysicalDeviceReset=true`,
  an exact non-placeholder serial, and no AVD identity. Failed emulator evidence
  cannot fall through to physical authorization.
- The physical branch returns `PHYSICAL_APP_SANDBOX`, never screenshot permission.
  Flow-test store construction now occurs only after successful authorization.
- Five non-destructive guard-contract tests passed on the phone. They cover consent,
  package/serial/token mismatches, invalid identities, and emulator bypass rejection.
- Actual denied FlowTest setup, without arguments/marker, failed at the guard as
  expected. Synthetic primary and `.new` sentinel files remained byte-identical
  under host `cmp`; teardown raised no uninitialized-scenario exception. This
  expected negative failure is separate from the passing suite.
- Independent read-only safety review found no blockers before installation.

## Execution results

Local APK preparation passed `:app:test :app:lintDebug :app:assembleDebug
:app:assembleDebugAndroidTest` (incremental build; not claimed as forced execution).
After the phone run, `./gradlew :app:test :app:lintDebug --rerun-tasks --console=plain
--no-daemon` passed: **59/59 actionable tasks executed**, 1m 16s; reports inspected
at `2026-11-06T14:33:26+03:00`.

| Check | Actual result |
| --- | --- |
| Forced debug JVM suite | 234 tests, 0 failures/errors/skips |
| Forced release JVM suite | 234 tests, 0 failures/errors/skips |
| Debug lint | 0 errors, 8 existing version/target advisories |
| Physical guard contracts | 5/5 passed |
| Initial stale-form batch | 2 setup failures: no Compose hierarchy; phone found locked/screen off |
| Stale forms after owner unlocked phone | 2/2 passed, 6.825s, ended 14:29:50 +03:00 |
| Focused storage/recovery suite | 10/10 passed, 9.203s, ended 14:30:09 +03:00 |
| Full current instrumentation suite | **22/22 passed**, 41.468s, 14:30:18–14:31:00 +03:00 |

The locked-screen run was stopped and its marker revoked before asking the owner to
unlock. No code change or phone-setting workaround was used; the unlocked rerun
passed. The initial failures remain recorded, not relabeled as successes.

Full suite: 5 flow journeys, 10 platform storage/recovery tests, 2 stale-form tests,
5 guard-contract tests. Coverage includes fractional progress/remainder completion,
Undo/correction/deletion, historical targets/status changes, theme/activity recreation,
retained stale-date drafts, corrupt/future-format recovery, legacy backup recovery,
preserved start-over, interrupted writes, storage ownership, and preservation-first
v1 migration of mixed/sample-looking test history.

Runner evidence was checked for each test's status, final `OK (22 tests)`, and absence
of failure/crash output. A shell exit status alone is not evidence of passing tests.
Logs are temporary host files under `/tmp/opencode/`: `phone-safety-build.log`,
`phone-guard-contract.log`, `phone-denied-setup.log`, `phone-command-forms.log`,
`phone-command-forms-unlocked.log`, `phone-recovery.log`, `phone-full-suite.log`, and
`phone-final-local.log`. They are not packaged app content or durable repository assets.

### Exact installed/tested artifacts

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `d33fcd8b9aa69e5bba9499d7e839d4e1c6c0cde366547cdc0309aba402c4261d` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `9622c12ffefc7a69246a2504e9587c1f2d3c0faa3058135d9a314a4b399d7a85` |

Both APKs installed successfully. The test APK hash supersedes the earlier
compiled-only Task 5 hash; the app APK is unchanged. The later forced JVM/lint run
did not repackage these APKs. Neither artifact is a fixture-free delivery claim.

## Cleanup and remaining state

Each authorized batch used a fresh marker and an EXIT/INT/TERM cleanup trap. Cleanup
stopped the target/test packages, removed exactly
`/data/local/tmp/githabit-disposable-test-target`, and verified absence. After the
full run, a separate check confirmed the marker and app process were absent.

Both APKs remain installed. GitHabit retains **synthetic test state**: the final
legacy-migration fixture produced v2 with **3 habits and 2 commits**, plus test-owned
preservation copies. These are not the owner's real habits. No post-test app-data
clear or uninstall was performed. Revoking the marker blocks future guarded setup;
it neither removes test data nor, by itself, stops already running instrumentation.

Still unverified: minimum-API and low-memory capacity certification; device broadcast
date/timezone behavior; process death; real disk exhaustion/power loss; 200% text,
TalkBack/keyboard, landscape/tablet/insets matrix; document-provider restore; and
the fixture-free artifact/real-data-only handoff gates. Clock tests inject time into
a test-owned ViewModel; they do not change the device clock or prove OS broadcast
delivery. Existing runtime demo/sample entry remains until the planned removal task.

## Safe physical-phone procedure

Prefer a disposable emulator. A phone run requires **fresh explicit owner consent**
to install both packages and reset/replace GitHabit's data. Export/preserve any real
workspace first; these suites are destructive. Do not infer disposability from an
installed test APK. Keep the phone unlocked; do not change global settings to make
tests pass. Never collect full-screen captures or unrelated logs on a personal phone.

1. Build locally without connected tasks. Identify the authorized transport serial
   and independently verify `ro.serialno`, package IDs, and user `0` before installing.
   Install only the two known APK paths using `adb -s "$DEVICE" install --user 0 -r`.
2. Run the non-destructive `com.githabit.prototype.fixtures.DisposableTargetTest`
   class without authorization arguments. On test-owned synthetic storage, verify
   denied actual setup preserves sentinel bytes before permitting destructive suites.
3. Use the bounded session below, substituting only the verified serial and SDK path.
   Do not commit the serial/token. Any existing marker requires investigation, not
   blind overwrite. This example expects the current 22-test inventory.

```bash
# Run from android/ after consent, identity verification, and installation.
set -euo pipefail
: "${DEVICE:?Set the explicitly authorized and independently verified serial}"
ADB="${ANDROID_HOME:?Set the Android SDK path}/platform-tools/adb"
MARKER=/data/local/tmp/githabit-disposable-test-target
LOG=$(mktemp)
check_device() {
  [ "$("$ADB" -s "$DEVICE" shell getprop ro.serialno | tr -d '\r')" = "$DEVICE" ] &&
  [ "$("$ADB" -s "$DEVICE" shell am get-current-user | tr -d '\r')" = 0 ]
}
check_device
"$ADB" -s "$DEVICE" shell "test ! -e '$MARKER'"
TOKEN=$(python3 -c 'import uuid; print(uuid.uuid4())')
cleanup() {
  result=$?
  trap - EXIT INT TERM
  if check_device &&
     "$ADB" -s "$DEVICE" shell am force-stop --user 0 com.githabit.prototype &&
     "$ADB" -s "$DEVICE" shell am force-stop --user 0 com.githabit.prototype.test &&
     "$ADB" -s "$DEVICE" shell "rm -f '$MARKER'; test ! -e '$MARKER'"; then
    echo 'GitHabit stopped; marker revocation verified.'
  else
    echo 'WARNING: stop/revocation unconfirmed; reconnect the same phone and clean up.' >&2
    result=1
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$ADB" -s "$DEVICE" shell "umask 022; printf '%s' '$TOKEN' > '$MARKER'"
[ "$("$ADB" -s "$DEVICE" shell cat "$MARKER" | tr -d '\r')" = "$TOKEN" ]
"$ADB" -s "$DEVICE" shell am instrument --user 0 -w -r \
  -e disposableDevice true -e allowPhysicalDeviceReset true \
  -e expectedTargetPackage com.githabit.prototype \
  -e expectedDeviceSerial "$DEVICE" -e disposableToken "$TOKEN" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner > "$LOG" 2>&1
cat "$LOG"
grep -Fq 'OK (22 tests)' "$LOG"
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -[123]' "$LOG"
```

For focused runs, add `-e class com.githabit.prototype.CommandFormTest` (2 tests) or
`-e class com.githabit.prototype.WorkspaceRecoveryTest` (10 tests) and adjust the
expected completion count. Omit AVD arguments on the physical branch. If execution
hangs, interrupt it and verify cleanup; never blindly start a concurrent retry.
If the shell is killed or the cable disconnects, traps cannot guarantee revocation:
reconnect the **same verified phone**, stop both exact packages, remove the exact
marker, and verify absence before declaring testing finished. Leave remaining app
data explicitly labeled synthetic, or clear only GitHabit with the owner's consent.

Runner syntax reference checked during this work via Context7:
[AndroidJUnitRunner](https://developer.android.com/reference/kotlin/androidx/test/runner/AndroidJUnitRunner).
