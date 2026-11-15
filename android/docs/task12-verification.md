# Task 12 — local export, validated restore and explicit reset

Verified locally on **2026-11-15**, based on Task 11 revision `4c4efb2`.
Intermediate build, not final personal-use handoff. No phone operation, push,
publication, or upload was performed. The root web prototype, package ID, permissions,
schema version, pinned dependencies and backup exclusions remain unchanged.

## Implementation

- `data/WorkspaceTransfer.kt`: bounded owned-stream read using the shared codec and
  v1 migration. Validated candidates must also encode/decode losslessly within v2's
  output capacity. Retain immutable workspace, source version, counts and canonical
  SHA-256 fingerprint; never reread a provider document after preview.
- `TransferState.kt`, `GitHabitViewModel.kt`: token-correlated off-Main validation;
  cancellation/stale results cannot publish. Restore/reset consent checks candidate
  identity, committed revision and load context inside the mutation boundary.
  User edits, Undo, calendar maintenance and verified loads invalidate consent.
  Refresh the preview and require another confirmation, rather than silently replace.
- Reuse `WorkspaceStore.replaceVerified` for preservation, read-back verification,
  verified rollback and indeterminate recovery. Drafts/navigation/Undo are cleared
  only after commitment or verified candidate reconciliation. Unknown outcomes block
  public transfer confirmation until storage is re-read. Small pre-write fingerprint
  barriers use `SavedStateHandle` so equal-value replacements can invalidate old
  saved drafts after recreated reconciliation; payloads/consent are not saved.
- Frozen export requests capture the committed workspace before opening the picker;
  recovery exports retain raw evidence. Both use one request-based API. Cancellation
  and failure release requests; failure warns that external files may be incomplete.
- `ui/GitHabitApp.kt`: unconditional CreateDocument/OpenDocument launchers and saved
  small callback tokens. Late process-death callbacks without a retained request are
  ignored. Picker access requires no Internet or broad storage permission.
- `ui/Settings.kt`, `Screens.kt`, `TransferDialogs.kt`, `res/values/strings.xml`:
  Settings/recovery restore, explicit incoming/current counts, v1 migration warning,
  replace-not-merge warning, refreshed consent, reconciliation, export-before-reset
  and recovery-copy export. Transfer modal ownership is separate from form overlays.
  Full dialog content/actions scroll; controls use the existing 48dp minimum and
  focus outlines. Copy uses resources/plurals; detailed codec/store error reasons
  retain existing English diagnostics (full localization audit remains Task 13).

## Host verification

Existing environment: Linux/OpenJDK 21, Gradle 8.14.3, AGP 8.13.0, Kotlin/Compose
compiler 2.2.20, Compose BOM 2025.09.01, build tools 36.0.0, compile/target 36,
minimum 26, JVM target 17. Existing vendor-compatibility caveats remain.
Official picker/SAF/lifecycle docs were retrieved in Task 12's initial analysis
through Context7 and TinyFish; no dependency upgrade was required.

Final exact-source command, from `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

**Passed in 104 seconds**, all 105 tasks executed. **304 JVM tests per variant**,
zero failures/errors/skips. Lint **zero errors, ten warnings**: eight existing
tool/dependency/target update advisories and two existing collective adherence-label
`PluralsCandidate` heuristics. No warning suppression. Exact host log:
`/tmp/opencode/task12-host-final.log`; normal generated test/lint reports under
`app/build/test-results/` and `app/build/reports/`.

New tests: four `WorkspaceTransferTest` and twelve `WorkspaceTransferCommandTest`
cases cover empty/v1/mixed/calendar/future preservation, fingerprints, malformed/
unsupported/oversized/interrupted reads and closure, cancellation/stale callbacks,
changed provider, revision/Undo/calendar invalidation, preservation failure, verified
rollback/retry, draft/Undo retention, indeterminate no-retry, recreated equal-value
reconciliation, frozen export, failed/cancelled export, and empty reset/restart.

## Guarded connected verification

Fresh provisioned AVD **GitHabit_Disposable_Task12**, exact serial **emulator-5594**:
Android 16/API 36 Google APIs x86_64, SwiftShader, 2560MiB RAM/256MiB heap,
720×1600 at 320dpi, `hw.mainKeys=no`, gesture navigation, no snapshots, wiped
disposable userdata. AVD registration: `/tmp/opencode/githabit-task12-avds`;
userdata/config: ignored `android/.gradle/task12-avds/device.avd`.

Runner `/tmp/opencode/run-task12-tests.sh` checks boot completion, exact AVD,
`ro.kernel.qemu=1`, and marker absence before installing. It provisions a fresh UUID
opt-in marker, passes package/AVD/token to the fail-closed test guard, removes the
marker at exit, and requires `OK (N tests)` with no failed/crashed per-test statuses.
Equivalent guarded core invocation after provisioning/boot:

```sh
set -euo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
SERIAL=emulator-5594
AVD=GitHabit_Disposable_Task12
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
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner \
  | tee /tmp/opencode/task12-results.log
grep -Eq '^OK \([0-9]+ tests?\)' /tmp/opencode/task12-results.log
! grep -Eq '^INSTRUMENTATION_STATUS_CODE: -|^INSTRUMENTATION_FAILED:|^FAILURES!!!' /tmp/opencode/task12-results.log
```

Final normal exact-APK suite: **71/71 passed in 256.267 seconds**;
`/tmp/opencode/task12-full-final.log`. Disabled-animation same-APK suite:
**71/71 passed in 184.956 seconds**; `/tmp/opencode/task12-full-disabled.log`.
All seven new `WorkspaceTransferFlowTest` cases passed: real codec/ContentResolver/
storage and rendered launchers via a **test-only result registry**; cancellation,
invalid import, edited provider after preview, rollback with dirty draft, refreshed
consent, export-first reset at 200% text and empty restart, real Activity recreation,
and damaged-original preservation before recovery restore.

Separately, **real DocumentsUI**, without a test result registry: selected Downloads
in CreateDocument, saved the exported v2 JSON, opened that file using OpenDocument,
reviewed 1 repo/1 commit counts, scrolled to confirm, and restored. Passive reads of
the external export and committed private file were **byte-for-byte identical**.
This used synthetic test-owned history on the exact disposable emulator only.

Initial UI runs were not passes: unconditional test scrolling targeted a non-scroll
top action; Activity monitors failed to deliver picker callbacks; an export notice
intercepted a subsequent tap. Fixed the harness using a result registry, applicable
scrolling and notice dismissal; business assertions remained. Initial full suite
was 70/71: the existing recovery test used removed “Start over” confirmation text;
updated it to `confirm-reset`, retaining exact empty/recovery-copy assertions. Final
host rebuild and both full suites above followed those corrections.

Teardown: window/transition scales restored to 1, absent animator setting restored
by deleting its temporary value; test marker absent; crash buffer empty. Installed
APK pulled and hash-matched. Exact emulator stopped with serial-selected `emu kill`.
No physical-phone command or generic connected-device run was used.

## Exact intermediate artifact audit

- Native source/config snapshot SHA-256:
  `7b196cc055cee5b7187e0e97368be5785c933faaae8028062c45218368fe1605`.
  Same algorithm as Task 11: sort files beneath `app/src/main`, `app/src/test`,
  `app/src/androidTest` plus module/root Gradle, settings and wrapper properties;
  hash each Android-relative path + NUL + bytes + NUL. Only documentation changed
  after the final build.
- Debug app APK / pulled installed APK SHA-256:
  `b13f2689e3662805c6a56a8803a7c1409ad637858678658c80248b4962ce847b`.
- Separate test APK SHA-256:
  `3fd33d6973f5903419e7157506c3f78be386e7da58347e38749a1c5c1f98aa17`.
- `aapt dump badging`: `com.githabit.prototype`, `1.1-local`, code 2, min 26,
  target/compile 36. Only existing app-specific dynamic-receiver permission;
  no Internet/broad storage permission.
- App ZIP contains no `assets/`/`res/raw/` fixture payloads. All app DEX checked:
  `SampleWorkspace`, `WorkspaceFixtures`, `WorkspaceTransferFlowTest`,
  `explore-demo`, `google-sign-in` absent. Test code/data remain in the separate
  test APK, not the personal-use app. No AAB/release APK is delivered here.

## Remaining gates

Full TalkBack/external-keyboard transfer journeys, oldest-supported Android,
RTL/pseudo-localization, complete 200%/short-height layout matrix, real process death
while an external picker or replacement is pending, and capacity/device-memory/
long-history profiling remain unverified Tasks 13–14 work. Saved-state unit tests
and Activity recreation are **not** real process-death evidence. Blocking providers
may delay stream closure until their current read returns; cancelled results cannot
publish or replace. On-device preservation is not protection against every storage
failure/uninstall/device loss. No final personal-use handoff is claimed.
