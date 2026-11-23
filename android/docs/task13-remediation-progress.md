# Task 13 remediation progress

Implementation began against `.plans/task13-remediation-plan.md`. Existing Task 13
changes are retained; publishing is not authorized. Phone operations
require explicit separate approval (the 2026-11-22 physical-phone e2e was explicitly
authorized and is recorded in its own section below).

## Corrections agreed before implementation

- Exact-sized output uses a counting traversal followed by one emission traversal.
  Growing `ByteArrayOutputStream` plus `toByteArray()` is not a no-copy solution.
- Raw input surrogate validation cannot validate ASCII `\uD800` escapes. The skipped
  string scanner must validate decoded surrogate pairs too.
- Previous bytes remain usable through rollback/terminal outcome; equality permits
  immutable alias deduplication, not destruction of rollback evidence.
- A configuration context must belong to the actual host view, not just a supplied
  composition local. Modal configuration must be asserted independently.
- User approved focused bounded decoding/materialization/readback corrections inside
  the existing codec/storage seams if measurements show inadequate heap headroom.
  Limits, canonical bytes, lossless checks, and recovery guarantees remain unchanged.

## Verification status

Latest run 9 passed the remediation regression ladder and terminal retained-log
runtime audit. Earlier OOM runs remain failed historical evidence. Minimum hardware,
app-only capacity and actual assistive technology certification remain open; no
personal-use handoff is authorized.

## 2026-11-22 continuation

- Canonical UTF-8 emission uses count/exact-array, streamed export, and incremental
  digest sinks. Probe strings/numbers discard values without proportional buffers.
- A measured 192MiB JVM OOM in whole-document UTF-8 conversion justified the approved
  byte-backed strict text view. Escaped retained strings now materialize via exact
  UTF-8 sizing/emission rather than a growing character builder.
- Actual ART measurement then failed materializing the second populated 32MiB name
  during restore. The plan's bounded-comparison gate was triggered. Verification
  now validates every decoded text unit, record, count, field, version and final
  workspace value, discarding verified records instead of retaining a second graph.
  It is not hash-only verification. Golden bytes and existing fault tests remain.
- Codec capacity rerun passed **8/8** on ART max heap **201326592 bytes**. Subsequent
  production storage tests passed combined counts but failed two populated restore
  replacements in the growing `readBounded` buffer's final copy. App-owned regular
  files now use one exact owned array with EOF/length checking; generic provider
  streams use chunks, even when their descriptor is wrapped in `FileInputStream`.
  Run 4 (`task13-device-20261122-180827`) then passed **8/8 codec** and **3/3
  production storage workflows**. Earlier failures remain historical evidence.
- The first corrected modal host lost the Activity window token and failed both
  sheets with `BadTokenException`. An Activity-backed `ContextThemeWrapper` with
  `applyOverrideConfiguration` fixed the harness; modal journeys passed **2/2** with
  real modal density/resources at 200%. Run 4 also passed reset/restore/sheet
  assertions at all four windows: **2/2 each** at 360×800, 320×640, 640×360 and
  1200×800 dp. This is supporting modal evidence, not full accessibility certification.
- Storage recovery/rejection reasons originate as typed identities. Dynamic notices,
  export start failures, restore failures and sheet semantics use resources; codec
  detail is an explicit bounded original-text field. Legacy/unknown domain fallbacks
  remain supported rather than fabricating a translated exception reason.
- Huge legacy text has bounded read-only previews and explicit replacement actions.
  Display previews are never saved; storage values/identities remain unchanged.
- Added owned-array/public-export/stale-disk/rollback regressions and bounded
  verification corruption/value-change tests. Host-final-7 passed 326 tests per
  variant; the latest host-final-10 passed 333 each. Constrained heap cases,
  including escaped legacy text, passed after the bounded-reader changes.
- Run 4's complete animations-disabled suite passed **84/85**. The sole failure
  waited for the obsolete literal `Expanded`; both waits now use `sheet_expanded`,
  retaining the existing gesture/dismissal and draft-preservation assertions.
  The runner stopped before normal animations and standalone sampling. Run 5's
  host build passed, but its background job was lost (`Shell.NotFoundError`).
  Saved instrumentation stops at the beginning of the long-history test, with no
  terminal suite result or cleanup logs. No runner/emulator process remains and
  ports 5596/5597 are unused. Run 5 is **inconclusive**, not a pass or app failure.
  Run 6 (`task13-device-20261122-184411`) passed the rendered huge repo
  target-edit/recreation/explicit-replacement case. Its profile case failed at an
  Espresso keyboard-close timeout after recreation, before the save click, not
  a data assertion. The helper now hides IME via the Activity window and explicitly
  waits for hidden insets without swallowing errors. Run 6 captured exit=1, logs,
  marker-absent evidence and emulator shutdown. Runs 7 and 8 subsequently completed
  the ladder with allocation-OOM warnings; latest run 9 was warning-free in retained
  runtime evidence (details below).

## Run 7 audit — green assertions do not establish OOM-free capacity

Run 7 (`task13-device-20261122-184716`) completed exit=0: both complete animation
suites passed **87/87**, huge-text rendering/recreation **2/2**, codec **8/8**,
production storage **3/3**, and four-window modal journeys **2/2 each**. Normal
app-only launch rendered `QA repo 0`, installed APK bytes matched, marker removal
and emulator shutdown are recorded. Host-final-8 passed 326 tests per variant,
lint and all APK builds.

**Capacity remains blocked:** final logcat inspection found six ART
`Throwing OutOfMemoryError` allocation warnings in populated restore replacements
in the full suites, despite passing assertions and no fatal/ANR exit. These are not
accepted as clean success. Investigation of the byte-level write path and a fresh
warning-free verification run are required before finalizing remediation evidence.

## Bounded physical matching-read correction

Source inspection identifies a likely ART large-object-space failure followed by
an ordinary-heap retry (not a caught application OOM). The warning sizes match
32MiB payloads plus array headers; without allocation stacks and an exact ART
revision match, attribution to previous/readback remains provisional. Android 16
ART explicitly clears OOM before this fallback:
[heap-inl.h](https://android.googlesource.com/platform/art/+/android16-release/runtime/gc/heap-inl.h).

The contained storage correction avoids duplicate equal previous/readback arrays
by a fresh physical byte-for-byte/EOF comparison before immutable alias reuse.
Mismatches materialize complete actual bytes in the same stream; unchanged
capacity/length/sidecar checks and rollback retention apply. Actual small scratch
arrays bound JNI-facing reads/writes and prevent recovery destinations mutating
private frozen snapshots. Delegating read-fault wrappers explicitly forward through
their injected read implementations. Focused storage/fault tests and instrumentation
compilation passed (`task13-matching-read-focused-1.log`); final suites/device
warning-free verification remain pending.

Host-final-10 then passed **333 tests per variant**, lint and debug/unsigned-release/
test APK builds. Constrained 192MiB codec/verification/matching-read tests passed
(`task13-constrained-final-3.log`). Debug/release package/min26/target36, permission,
backup-domain and fixture-exclusion audits passed (`task13-final10-*`). Run 8
completed with the additional ART at-limit physical-read regression and terminal
runtime audit; it failed the runtime gate as recorded below.

The acceptance gate is unchanged: ART allocation-OOM warnings remain blocking even
when an internal retry succeeds and all instrumentation assertions pass. Managed
matching-read alias reuse reduces redundant full-array allocation pressure; small
JNI-facing scratch buffers separately reduce potential native copying. Neither is
a structural guarantee against allocator fragmentation, and native headroom is not
reported as proof that Java allocation warnings are resolved. Previous snapshots
remain in memory through rollback/terminal checks; no lazy archive/re-read rollback
substitution was introduced. Run 8's completed runtime evidence shows that this
contained correction is not sufficient; any next correction must be justified
against the remaining allocation seam, not inferred to need a codec rewrite.

The Store's independent exact `copyMatches` preservation gate is retained.
Existing raw-stream sidecar/oversized preservation uses length/digest readback;
that pre-existing mechanism is not described as exact value/byte comparison and
is not substituted for any lossless candidate or matching-read verification.
- Disposable runs use only `emulator-5596` / `GitHabit_Disposable_Task13` with a fresh
  token and exact package/AVD checks. Each completed runner captured evidence,
  removed its marker and stopped that emulator. No phone operations or commits.

## Remaining evidence, not inferred from capacity success

- Run 8 (`task13-device-20261122-193800`) passed codec 8/8, production storage 4/4,
  huge-text rendering/recreation 2/2, modal journeys 2/2 at four windows, complete
  animation modes 88/88 each and standalone preparation/readiness. Installed debug
  bytes match, marker removal/exit=1/shutdown are recorded, ports are unused.
- **Runtime gate failed:** four ART 33553968-byte allocation-OOM warnings during
  fresh-store populated reopen in full-suite PIDs4403/7435, logcat lines
  37408/38135/91379/92093. The phase/size suggest huge legacy-name materialization;
  no allocation stack proves it. No retained warnings appeared in restore
  replacement in this run. Fewer warnings and successful ART retries are not success.
- Read-only reopen analysis identifies the required giant domain String as the
  strongest suspect; byte-backed unescaped text already materializes once without
  a proportional StringBuilder. Old Store/candidate liveness during reopen remains
  uncertain. The test now places prior operations in a non-inline helper that returns
  before independent fresh-load equality. Every assertion and original expected
  workspace remains; no expected-state load shortcut, explicit GC, production
  lifetime change or snapshot removal is introduced. This is a test-lifetime
  measurement correction, not a production fix or capacity certification.
  Host-final-11 passed 333 tests per variant/lint/builds; production APK hashes are
  unchanged. Run 9 completed the unchanged full ladder and mandatory terminal audit
  warning-free in retained logcat. Earlier warning attribution/liveness remains
  uncertain; this is not causal proof or guaranteed fragmentation safety.
- If future warning evidence shows the correction insufficient, a broader byte-snapshot/domain change needs
  separate approval; a chunked byte codec alone cannot remove a required giant
  domain String. Limits, previous rollback bytes and lossless assertions stay.

## Latest run 9 — observed regression pass, not hardware certification

`task13-device-20261122-201256` / `task13-device-run-9.log`: runner **exit=0**,
terminal audit passed, **zero OOM/fatal/ANR markers** in retained terminal/cleanup
logcat. Both complete suites' starts are retained; earlier log-retention gaps are
not claims of universal absence. Codec **8/8**, storage **4/4**, huge-text **2/2**,
modal **2/2 at each of four windows**, complete modes **88/88 each**, preparation
**1/1**, standalone `QA repo 0` readiness and installed debug byte equality passed.
Host-final-11: **333 tests per variant**, lint/builds; final11 artifact/manifest/
backup/fixture-exclusion audits passed. Current source/test hashes are in verification.

Marker absence and emulator shutdown were captured. Host inspection found no runner,
emulator or port listener; only TCP TIME-WAIT entries remained, bindable with address
reuse. No push, publication or upload occurred. All changes are committed locally.

The five-year lifecycle retains 20 repos/36520 commits/41 configurations/5363134
bytes. Separate normal app PID10545 readiness observation5923ms includes tool
overhead; PSS point samples111932/135994KiB, loaded142663KiB. Instrumented native
cumulative peaks still≈84MiB; no native-peak/latency reduction is claimed. Minimum
hardware, app-only byte-capacity, actual AT, process death, oldest platform and
complete configuration/contrast/focus-return certification remain open.
- Large compatible text has constrained-host codec and saved-form restoration
  regressions; byte-boundary production workflows exercise ART storage but do not
  render the huge fields. A dedicated rendered preview/profile-edit/recreation
  regression in `LegacyTextRenderingTest` passed 2/2 in runs 7/8. The fixture stays
  test-only and does not establish process-death or byte-limit rendering support.
- App-only samples must come from a normal app process after preparation
  instrumentation exits. Instrumented capacity and lifecycle metrics include
  expected inputs/tests and cannot establish app-only peaks.
- Minimum hardware is undefined. Actual TalkBack/hardware keyboard, process death,
  oldest platform and complete font/RTL/IME/contrast/focus-return certification
  remain open. Limits and personal-use handoff status stay provisional.

## Physical-phone e2e (2026-11-22) — clean pass, first real-hardware capacity evidence

With explicit phone authorization (and after confirming the phone held **no app or
workspace** — only a stale `com.githabit.prototype.test`, versionCode 0), the
phone-safe runner (`/tmp/opencode/run-task13-phone-e2e.sh`) exercised the Task 13
core on `CPH2749` (`3C1672002K000000`, Android 17/SDK 37, 1272×2772 @ 560 dpi) using
the designed `PHYSICAL_APP_SANDBOX` guard path. No `wm`/`font_scale`/animation
settings were touched. Evidence `/tmp/opencode/task13-phone-20261122-211651`.

- **55/55 instrumented tests** across 12 phone-runnable classes, runner exit=0.
- Emulator-only pixel/window tests correctly **refused** on hardware via their
  `check(... == DISPOSABLE_EMULATOR)` guard (smoke: `ThemeRenderingTest`).
- Installed debug APK byte-equal to host artifact (SHA-256
  `ae0ed8c4990b8630637db54827339bb63135f1391ffdbe148d16e4e4efcd9539`).
- Standalone five-year load (20 repos / 36520 commits / 41 configs / 5363134 bytes)
  rendered `QA repo 0` ~3302 ms after instrumentation exited; loaded TOTAL PSS
  211406 KiB, Java Heap 94716 KiB, Native Heap 13028 KiB. No OOM/crash/ANR.
- Terminal audit zero OOM/crash/ANR markers; token removed, both packages
  uninstalled, no trace left.

A first attempt under the sandbox background model was SIGKILL'd mid-run and left a
`DeadObjectException` (`UiAutomation.disconnect`) teardown artifact — host
disconnection, not an app defect. The clean re-run cleared logcat first. Run 9
emulator evidence is unchanged and remains separate.
