# Task 13 — remediation regression passed; certification gates open

> **2026-11-22 remediation status:** The November 15 results below are retained as
> historical failures/evidence, not current verification. See
> [remediation progress](task13-remediation-progress.md) and
> [snapshot ownership](workspace-snapshot-ownership.md). Current host checks passed
> **333 tests per debug/release JVM variant**, zero failures/errors/skips; lint,
> debug/unsigned-release/test APK builds passed (`task13-host-final-11.log`).
> Constrained 192MiB host codec/verification regressions passed, including large
> escaped compatible text (`task13-constrained-final-3.log`). Latest guarded run 9
> (`task13-device-20261122-201256`) passed **88/88 in each complete animation mode**,
> codec **8/8**, production storage **4/4**, huge-text rendering/recreation **2/2**,
> modal journeys **2/2 at each of four windows**, and standalone preparation/readiness.
> The terminal runtime audit passed **exit=0**, with **no OOM/crash/ANR markers in
> retained logcat**; both complete suites' starts are retained. Installed debug APK
> bytes match the host artifact, marker removal and emulator shutdown are verified.
> This clears the observed remediation regression blocker on this disposable target,
> **not supported-capacity/minimum-device certification or personal-use handoff**.
> Runs 7/8 still contain blocking historical allocation-OOM warnings despite green
> assertions; run 5 remains inconclusive. No earlier failure is relabeled a pass.
>
> Current host artifacts (host-final-11; run-9 installed debug bytes verified equal): debug SHA-256
> `ae0ed8c4990b8630637db54827339bb63135f1391ffdbe148d16e4e4efcd9539`;
> unsigned release `6275e4ea4d2152e4f25833cda31124a00362b721f14c379794e86b9373df060b`;
> separate test APK `59697072979f9df5f3bb320a12db25046f605e29c3028a8012b340dd169445c0`.
> App source/module snapshot (same sorted path/NUL/content method as historical
> audit): `246d3fddf4fd35a55a810fbf235cab00866a7387e697b351ed063beed5fb65a4`.
> Fresh debug/release DEX, manifest and backup audits passed (`task13-final11-*`).
> Limits, pinned dependencies, identities, recovery/consent and canonical bytes stay
> unchanged; the work is committed locally. Actual assistive technology, oldest-platform,
> full configuration/contrast/focus-return and minimum-hardware gates remain open.

## Physical-phone e2e — clean pass (Task 13 core on real hardware)

Evidence: `/tmp/opencode/task13-phone-20261122-211651`; runner
`/tmp/opencode/run-task13-phone-e2e.sh`. Physical `CPH2749`, serial `3C1672002K000000`,
Android 17 / SDK 37, fingerprint `OnePlus/CPH2749/OP611FL1:17/CP2A.260605.016/…/
user/release-keys`, native display 1272×2772 px @ 560 dpi, animation scales 1.0
(never changed). No emulator identity (`ro.kernel.qemu` empty, no AVD name). No
`wm size` / `wm density` / `font_scale` / animation settings were mutated.

Pre-condition recorded before install: the app was **not installed** (only a stale
`com.githabit.prototype.test`, versionCode 0, dated 2026-11-06/08); no `/data/data`
or external workspace existed. There was **no personal data to preserve or reset**;
the suite operated on clean synthetic fixtures.

Authorization used the designed `PHYSICAL_APP_SANDBOX` guard path
(`allowPhysicalDeviceReset=true`, exact `expectedDeviceSerial=3C1672002K000000`,
per-run token at `/data/local/tmp/githabit-disposable-test-target`). The emulator-only
pixel/window tests (`ThemeRenderingTest`, `TodayFlowTest`, `ScheduleFormTest`,
`HistoryInsightsTest`, `AdaptiveNavigationTest`) hard-`check(... == DISPOSABLE_EMULATOR)`
in `@Before` and were not run for pass; a guard-refusal smoke confirmed
`ThemeRenderingTest` refused on hardware without touching the workspace.

| Check | Observed result |
| --- | --- |
| Instrumented tests (12 classes, per-class `am instrument`) | **55/55** green |
| Breakout | guard-logic 5, legacy-rendering 2, modal-native 2, capacity 8, capacity-storage 4, long-history 1, recovery 10, transfer-flow 7, preferences 7, command-form 2, flow-degraded 6, standalone-fixture 1 |
| Emulator-only guard on physical | `ThemeRenderingTest` refused (no write) |
| Installed APK identity | Debug SHA-256 `ae0ed8c4990b8630637db54827339bb63135f1391ffdbe148d16e4e4efcd9539`, byte-equal to host artifact |
| Standalone real-device long-history load | `QA repo 0` rendered ~3302 ms after instrumentation exited; 20 repos / 36520 commits / 41 configs / 5363134 bytes |
| Loaded memory (point sample) | TOTAL PSS 211406 KiB, Rss 375577 KiB; Java Heap 94716 KiB, Native Heap 13028 KiB, Graphics 19617 KiB |
| Terminal runtime audit | Exit=0; zero app OOM/crash/ANR markers in retained logcat |
| Cleanup | Token absent; both packages uninstalled; no data dir; no trace |

This is the first real-hardware validation of the Task 13 capacity path: the full
five-year workspace loads on a physical phone without OOM/ANR. It is **not**
minimum-hardware certification (a late-model phone is not a minimum device) and does
not replace the remaining assistive-technology/process-death/oldest-platform gates.

Disruption note (retained, not a pass/fail): the first phone attempt ran under the
sandbox background model and was SIGKILL'd mid-`capacity-storage`, leaving a
`DeadObjectException` from `UiAutomation.disconnect()` in that teardown — a host
disconnection artifact, not an app defect (`task13-phone-20261122-205721`,
historical). The clean re-run cleared logcat first. Run 9 emulator evidence stays
separate and unchanged below.

## Latest verification — warning-free observed run 9

Evidence: `/tmp/opencode/task13-device-20261122-201256`; host output
`/tmp/opencode/task13-host-final-11.log`. Exact disposable target `emulator-5596`,
AVD `GitHabit_Disposable_Task13`, API36 Google APIs x86_64, SwiftShader, 2048MiB
RAM, ART maximum heap **201326592 bytes**, baseline 720×1600px/320dpi.

| Check | Latest observed result |
| --- | --- |
| Debug/release host suites | 333 each; zero failures/errors/skips |
| Lint and all APK builds | Passed; lint has 0 errors, 11 warnings |
| Codec boundaries/combined pressure | 8/8, including lossless below/at and CAPACITY above |
| Production capacity workflows and exact physical matching/mismatch | 4/4 |
| Huge legacy text preview/edit/recreation | 2/2; unchanged originals preserved |
| Real modal 200% en-XA/ar-XB journeys | 2/2 each at 360×800, 320×640, 640×360, 1200×800dp |
| Complete animations disabled / normal suites | 88/88 each |
| Standalone preparation and rendered readiness | 1/1; `QA repo 0` rendered after instrumentation exited |
| Runtime audit | Exit=0; no OOM/fatal/ANR markers in terminal or cleanup retained logcat |
| Installed identity / cleanup | Debug bytes equal; marker absent; emulator stopped; no process/listener |

The capacity helper now owns the earlier Store/candidate in a non-inline frame
returning before independent fresh-store reopen. Every original assertion and the
full expected workspace remain. No explicit GC, expected-workspace load shortcut,
production lifetime change or rollback-byte removal was introduced. This fixes
possible test-frame retention, **not a demonstrated production String-allocation
defect**. Run 9 observed no warnings; it does not establish causation, permanent LOS
fragmentation safety, absence outside log retention, or a guarantee on other devices.

### App-only long-history observation, separate from instrumented capacity

After fixture preparation exited and force-stop, normal PID10545 rendered `QA repo 0`.
COLD TotalTime **945ms**/WaitTime **946ms**; external rendered-readiness observation
**5923ms** includes tool overhead and is not precise cold-content timing. External
PSS samples **111932/135994KiB**; separate loaded sample **142663KiB PSS**, Dalvik
allocation **33208KiB**, native allocation **17694KiB**. These are point samples,
not peaks, latency budgets, minimum-hardware evidence or byte-limit app-only capacity.

### Instrumented lifecycle and memory caveats

Both modes preserved **20 repos/36520 commits/41 configurations/5363134 bytes**
through log, correction, Undo, frozen export, restore and Activity reopen. Elapsed ms:

| Operation | Disabled | Normal |
| --- | ---: | ---: |
| Activity launch→ready | 2108 | 2518 |
| Log / correction / Undo | 2149 / 2213 / 2266 | 2313 / 2230 / 2266 |
| Export freeze / output | 371 / 2364 | 374 / 2378 |
| Restore validation / confirmation | 3704 / 3189 | 3955 / 3355 |
| Close/reopen→ready | 2252 | 2936 |

Frames **98/243**, over16ms **77/217**, over33ms **31/77**, p95 **71.88/83.45ms**,
max **117.46/125.20ms**, callback drops **0/1** are debug-emulator jank observations,
not performance certification. Populated instrumented cumulative heap samples still
reach about **175.6MB**, native samples about **84.3MB**; no claimed KB native peak,
isolated per-operation memory reduction or causal latency improvement follows.
Tests retain original inputs and samplers report cumulative peaks, not app-only peaks.

### Remaining gates and closure conditions

- Define minimum supported hardware/RAM/heap and obtain app-only cold/loaded and
  capacity-workflow evidence there; the current 192MiB regression heap is not that definition.
- Actual TalkBack/hardware keyboard, process death, oldest-platform and complete
  font/RTL/IME/contrast/focus-return journeys remain unverified.
- Limits remain provisional and unchanged. Task13/Gate D certification and handoff
  stay open; further device/phone operations, commits and publication require approval.

## Historical run 8 — runtime gate failed

Fresh independently read exact bytes/EOF may reuse a lent immutable snapshot before
allocating an equal full document. Different content retains complete actual bytes
from the same stream. AtomicFile sidecar retention, capacity/length checking,
preservation-before-write/rollback, every-value codec verification and previous-byte
lifetime remain intact. Genuine small scratch arrays bound stream-facing JNI copies
and isolate private recovery snapshots from destination-array mutation.

Focused storage/fault regressions and instrumented compilation passed, then both
333-test host variants/lint/APK builds passed (`task13-host-final-10.log`). Constrained
192MiB host regressions passed (`task13-constrained-final-3.log`); they are not ART
certification. Fresh debug/release artifact audits are `/tmp/opencode/task13-final10-*`.
Run 8 (`/tmp/opencode/task13-device-20261122-193800`) completed: codec **8/8**,
production storage **4/4** (including at-limit exact-alias/mismatch checks), huge-text
rendering/recreation **2/2**, modal journeys **2/2 each** at all four windows, full
animation modes **88/88 each**, standalone preparation **1/1**. These are supporting
assertion passes, not an OOM-free capacity result.

The terminal runtime audit correctly failed **exit=1**: four ART
`Throwing OutOfMemoryError` warnings allocating **33553968 bytes** appear in the
fresh-store reopen phases for populated 32MiB−1/32MiB, one each per full animation
mode. Logcat lines **37408/38135/91379/92093**, PIDs **4403/7435**. No app fatal
exception/ANR was found, and exit-info lists instrumentation-finished force-stops;
that does not excuse the allocation warnings. Their phase and size are consistent
with huge legacy-name string materialization, but no allocation stack or exact ART
revision match establishes that attribution. No retained warnings were observed
in populated restore replacement in this run. Neither that observation nor fewer
warnings proves allocator-fragmentation safety or sufficient minimum-device headroom.

The installed debug APK is byte-equal to the current host artifact. Marker absence,
runner exit=1 and emulator shutdown are recorded; subsequent host process/listener
inspection found no runner/emulator or listener at 5596/5597. No phone was addressed.
No weaker audit, limit change, assertion removal or OOM catch was introduced.

Run-8 app-only launch rendered `QA repo 0` after preparation exited and a normal
process launch: COLD TotalTime **1093ms**/WaitTime **1094ms**, externally observed
readiness **7359ms** including tool overhead. External PSS samples **115650/137005KiB**;
separate loaded PSS **145567KiB**, Dalvik allocation **25964KiB**, native allocation
**17679KiB**. These are point samples of the long-history app, not peaks or byte-limit
app-only capacity certification. Instrumented populated metrics still report native
cumulative peaks around **84MiB**; no measured native-peak reduction is claimed.

Both modes preserved the long-history **20 repos/36520 commits/41 configurations/
5363134 bytes** through log, correction, Undo, export, restore and Activity reopen.
Frame observations **101/243** with p95 **100.20/83.59ms**, max **150.15/145.30ms**
remain debug-emulator jank observations, not performance certification. At the end
of run 8, capacity remained blocked pending a justified correction and fresh evidence.

Follow-up run 9 completed after a test-lifetime-only correction: earlier workflow
operations now own the Store and candidate in a non-inline helper returning before
independent fresh-load equality. Original expected state and every assertion remain;
no explicit GC, snapshot release, expected-workspace decode shortcut or production
change is introduced. Host-final-11 passed 333 tests per variant/lint/builds; app APK
hashes remain unchanged. Its warning-free observed results are recorded above;
run 8 remains a failed runtime audit, not retroactively accepted success.

## Historical November 22 run 7 (runtime warnings blocked capacity)

Evidence: `/tmp/opencode/task13-device-20261122-184716`; paired host output
`/tmp/opencode/task13-host-final-8.log`. API 36 disposable x86_64 Google APIs AVD,
SwiftShader, 2048MiB RAM, ART maximum heap 201326592 bytes, 720×1600px/320dpi.

| Check | Observed result |
| --- | --- |
| Host debug/release JVM suites | 326 each; zero failures/errors/skips |
| Lint, debug/unsigned release/test APK builds | Passed |
| Codec boundary/combined cases | 8/8 |
| Production startup/save/export/restore/reopen capacity workflows | 3/3 assertions; **OOM warnings prevent a capacity pass** |
| Huge legacy-text rendering/edit/recreation | 2/2; read-only previews never submitted; originals remain exact |
| Real modal 200% en-XA/ar-XB journeys | 2/2 each at 360×800, 320×640, 640×360, 1200×800dp |
| Complete animations disabled / normal suites | 87/87 each |
| Standalone fixture preparation | 1/1; instrumentation exited before normal app launch |
| Installed debug APK | Pulled bytes exactly equal host APK |
| Runtime audit | No fatal app exception/ANR; **six ART allocation-OOM warnings** |
| Cleanup | Marker absent, exit=0, emulator shutdown recorded; no remaining listener/process |

### Separate app-only readiness observation

A fresh normal `MainActivity` process (PID 10571) rendered `QA repo 0` after
preparation instrumentation ended and the app was force-stopped. `am start -W`
reports COLD, TotalTime 1002ms/WaitTime 1008ms; the externally observed rendered
readiness bound was **6947ms**, including tool overhead. It is one observation,
not precise first-content timing, a device minimum or a latency pass.

Two external samples observed total PSS **112278/120278KiB**. A separate loaded
sample observed **156962KiB PSS**, Dalvik heap allocation **25968KiB**, native heap
allocation **17705KiB**. These are point samples, not transient peaks. The loaded
sample was taken after hierarchy sampling and may include sampling-induced work.

### Instrumented long-history lifecycle (not app-only)

The five-year fixture retained **20 repos / 36520 commits / 41 configurations /
5363134 bytes**. Both complete modes verified log, correction, Undo, frozen export,
restore and Activity reopen equality. Representative elapsed milliseconds:

| Operation | Disabled | Normal |
| --- | ---: | ---: |
| Activity launch→ready | 2166 | 2579 |
| Log / correction / Undo | 2133 / 2213 / 2283 | 2148 / 2313 / 2299 |
| Export freeze / output | 374 / 2355 | 364 / 2462 |
| Restore validation / confirmation | 3807 / 3241 | 4033 / 3253 |
| Close/reopen→ready | 2353 | 2987 |

Activity-window frame observations were 99/243 frames, 77/210 over 16.67ms,
28/75 over 33.33ms, p95 83.54/83.48ms, max 116.83/144.40ms, callback drops 0/1.
This is substantial debug-emulator jank, not a performance certification. Memory
samples include instrumentation and retained expected inputs; cumulative sampler
peaks must not be read as per-operation isolated memory or app-only measurements.

## Historical November 15 evidence (not current verification)

Local observations on **2026-11-15**, based on `64edf12` plus the then-uncommitted Task 13
changes. **Task 13 and Gate D remain open.** No personal-use APK is handed over;
no phone operation, commit, push, publication or upload occurred in this continuation.
The web prototype, package ID, pinned dependencies, schema and provisional limits
remain unchanged. See [the predefined protocol](task13-methodology.md).

## Implementation and investigation

- Repo cards/log forms consume indexed analytics rather than legacy full-history
  progress/streak scans. Schedule draft previews index only the proposed repo and
  no commits. `DraftPreviewCapacityTest` uses a deliberately unreadable 100k history
  to detect accidental preview enumeration.
- `ui/DisplayFormatting.kt` separates app-configuration display locale from ISO
  date/JSON identity and the existing ungrouped dot-or-comma decimal input contract.
  Form/card descriptions, selection labels, headings, count copy and Undo use
  resources. Debug pseudolocales are enabled; release pseudolocales are not.
- Generic confirmations use the existing scrollable transfer-dialog surface;
  transfer surfaces include IME padding. Standard Material actions remain intact.
- `DiagnosticMessages.kt` translates selected stable diagnostics at presentation
  boundaries, retaining unrecognized reasons rather than inventing explanations.
  **Coverage is partial:** dated/dynamic success notices, detailed recovery/codec/
  provider reasons and some state descriptions remain English. Captured reset
  pseudolocale screenshots also contain unexpanded transfer body/count/action copy.
  This is a recorded localization gap, not evidence of complete resource rendering.
- Test-only long-history, frame/memory and capacity sources live exclusively in
  `src/androidTest`; the new JVM regressions live in `src/test`.
- The original pseudolocale Log failure was **tap interception by a large snackbar**,
  not a reproduced Today-card overlap. The helper looked for English `Dismiss`,
  which is localized in Material too. A screenshot showed the snackbar covering Log;
  offscreen lazy semantics had stale footer coordinates. A `notice-host` tag lets
  the helper locate its described dismiss icon without an English text dependency.
  Log is still physically tapped; it is not bypassed by a direct VM/semantic action.
- Short-window initial failures were absent offscreen lazy nodes. The helper now
  scrolls the Today empty item and date-summary item into composition. It still
  requires visible enabled submission controls and exact persisted amounts.

## Environment and isolation

Only `emulator-5596`, AVD **GitHabit_Disposable_Task13**, was used: API 36 Google
APIs x86_64, SwiftShader, configured 2560MiB RAM/256MiB heap, actual instrumented
process max heap **201,326,592 bytes (192MiB)**, 720×1600px at 320dpi, 60Hz.
Primary app window is 360×800dp; the earlier semantics tree showed main content
between y=176 and 1392px at 200% scale, with app/system navigation outside it.

Each instrumented setup runs `verifyDisposableDevice` before launch/file mutation.
The host runner `/tmp/opencode/run-task13-tests.sh` selects only this serial, checks
exact AVD name, `ro.kernel.qemu=1`, marker absence, and writes a fresh UUID token.
It installs separate app/test APKs, invokes the AndroidJUnitRunner with
`disposableDevice=true`, `expectedTargetPackage=com.githabit.prototype`,
`expectedAvdName=GitHabit_Disposable_Task13`, and matching `disposableToken`;
an EXIT trap removes `/data/local/tmp/githabit-disposable-test-target`.

No command addressed phone serial `3C1672002K000000`. Tests use synthetic history
or their explicitly entered QA values, not personal exports or profiles.

## Fresh host and connected checks

Host command from `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest --console=plain --no-daemon
```

Successful completion is `/tmp/opencode/task13-final-host-retry.log`: **308 tests
per debug/release JVM variant, zero failures/errors/skips**, lint completes, debug
and unsigned release APKs build. The first combined invocation hit the host tool's
120s timeout during release work; the retry completed successfully. Existing
toolchain/lint-version caveats still apply; no dependency upgrade was attempted.

Guarded connected invocation:

```sh
bash /tmp/opencode/run-task13-tests.sh /tmp/opencode/task13-full-normal.log
# Repeat after setting the three system animation scales to 0 on the exact AVD:
bash /tmp/opencode/run-task13-tests.sh /tmp/opencode/task13-full-disabled.log
```

**Both complete runs: 82 tests, 78 pass, 4 fail.** The failures are combined count
pressure and populated byte below/at/above cases described below. Functional,
preservation, guarded negative tests, rendered-role/focus/target checks, and the
long-history lifecycle passed. These runs are **not a green suite**. Raw instrument
logs, metrics, crash-buffer and full logcat captures are beside each output log in
`/tmp/opencode`; temporary files are local evidence, not shareable-tree assets.

After the complete runs, a final test-helper-only change added short-window lazy
scroll handling. It compiled and the affected journey was rerun; the full 82-test
suites predate that helper-only change. Production APK bytes did not change.

## Representative locale/layout checks

Two create → invalid-submit/error → selected Monday/Wednesday → save → Log →
fractional commit → exact date summary → reset confirmation/cancel journeys pass
under injected `en-XA`/dark and `ar-XB`/light configuration, linear 200% density
font scaling and matching layout direction. Explicit exact checks require one repo,
selected identities, and one .625 commit. This is not real system nonlinear-font,
locale-switch, TalkBack or complete hardware-keyboard evidence.

| Physical override at 320dpi | Window dp | Result / local log |
| --- | --- | --- |
| 720×1600 baseline | 360×800 | 2/2; `task13-locales3.log`, also both full runs |
| 640×1280 | 320×640 | 2/2; `task13-compact-locales.log` |
| 1280×720 | 640×360 | 2/2 after lazy helper fix; `task13-short-locales2.log` |
| 2400×1600 | 1200×800 | 2/2; `task13-expanded-locales.log` |

Short-window failure logs are retained, not relabeled passes. The expanded journey
does not itself navigate Repos/list-detail; existing adaptive tests run in the full
suite. Representative coverage is not an exhaustive Cartesian matrix.

`ThemeRenderingTest` passed in both complete runs: actual pixel checks of theme
surface/system-bar resolution, secondary/badge/error role ink, visible focus border,
320dp shared-card 100/200% layout, 48dp actions, and injected Enter activation.
`ThemeContractTest` passed its paired-color contrast thresholds (4.5:1 text,
3:1 meaningful controls) in both JVM variants. These support the specific tested
pairs, **not every rendered screen/state**, actual TalkBack, or a physical keyboard
journey. Screenshot review found no reset-confirm overlap in the sampled portrait
state but did reveal the partial pseudolocale rendering described above.

## Long-history measurements

Fixture: **20 repos, 36,520 commits, 41 habit/pending configurations, 5,363,134
encoded UTF-8 bytes**, five years ending 2026-11-04. Daily, selected weekday and
weekly schedules, dated target changes, fractional/multibyte notes and pending
week-start are test-owned. The lifecycle verifies counts/equality through save,
load, four scrolling surfaces, log, correction, snackbar Undo, export, retained
restore confirmation, on-disk equality and reopen. It passed initially, in both
full runs, and isolated again in `task13-long-final.log` (40.055s test duration).

Final isolated single-run endpoints (milliseconds): verified fixture save **3765**;
Activity launch→ready **2460**; index/summaries **54**; six swipes+idle Today **1500**,
Repos **1283**, Activity **1533**, detail **1300**; log **3749**; correction **3857**;
Undo **3766**; frozen export preparation **477** / output-close **2007**;
restore validation **3815** / confirmation **4676**; close/reopen→ready **2553**.
These are observations, not budgets or benchmark pass thresholds.

Activity-window frame listener: **98 non-first-draw frames**, 0 first draw/callback
drops reported, 85 >16.67ms, 50 >33.33ms; median **33.42ms**, p95 **212.82ms**, max
**433.51ms**. This is substantial emulator/debug jank, **not a performance pass**.
Modal windows are not covered; reopen replaces the original listener's window.
Sampled test-process peaks reached Java heap **192,206,592 bytes** (setup sampler)
and PSS **431,747KiB** (lifecycle sampler). They include instrumentation/input
retention, cumulative peaks and can miss transients. A reported 0 PSS on very short
capacity operations means no completed sample before logging, not zero memory use.

Standalone `am force-stop` → `am start -W` reported `LaunchState: COLD`, TotalTime
**1029ms**, WaitTime **1030ms**. The subsequent UI hierarchy still showed **Reading
your workspace…**. Therefore this is **first Activity draw, not loaded-workspace
cold readiness**. App-only `dumpsys meminfo` sampled PSS **131,661KiB** afterward;
it is a single observation during startup, not a loaded steady-state or peak sample.
Standalone cold→workspace-ready and loaded app-only PSS remain unverified.

## Capacity: observed failures are blocking

Limits unchanged/provisional: 33,554,432 UTF-8 bytes; 1,000 repos; 100,000 commits;
100,000 total configurations; 128 UTF-16-code-unit IDs; root-inclusive depth 8.
Isolated class-method selections use the same guarded runner with a third argument
such as `com.githabit.prototype.CapacityMeasurementTest#combinedDistributedPressure`.

| Dimension | Actual result |
| --- | --- |
| Repos 999/1000/1001 | Below/at lossless codec+restore; above CAPACITY |
| IDs 127/128/129 UTF-16 units | Below/at multibyte IDs lossless; above CAPACITY |
| Depth 7/8/9 | Below/at grammar valid; above CAPACITY (not schema acceptance) |
| Commits 99,999/100,000/100,001 | Below/at lossless codec+restore; above CAPACITY |
| Configurations 99,999/100,000/100,001 | Below/at lossless codec+restore; above CAPACITY |
| Combined 1000/100k/100k | **OOM**, isolated restore re-encoding in `BoundedJsonWriter` |
| Populated 32MiB−1 / 32MiB | **OOM**, encoder StringBuilder UTF-16 inflation |
| Populated 32MiB+1 | **OOM instead of required CAPACITY** before finishing output |

The populated byte fixture is valid under storage compatibility: one repo/commit,
legacy-compatible name whitespace and multibyte/escaped commit note. It is not a
padded empty object. Tests retain generated inputs, so app-only reproduction is
still needed; nevertheless current positive expectations **fail** at this heap.
The below/at failures request ~134,216,200-byte contiguous UTF-16 backing storage;
combined restore requested 75,497,480 bytes. Isolated combined duration was 17,608ms,
178 GC count after versus 0 before, sampled heap 193,816,800 bytes/PSS 366,167KiB.
Independent 100k commit roundtrip/restore took 13,383ms; 100k configurations 8830ms.

No catch-OOM-as-success, arbitrary limit reduction, whitespace trimming, dropped
records or weakened assertion was introduced. Resource-safe encoding/decoding,
bounded-input behavior and retained restore/write copies need a focused remediation
and remeasurement before declaring supported capacity. Minimum-supported RAM/heap/
hardware is unspecified. No date-span bound exists; count-valid extreme dates can
still make range analytics expensive. Those gates cannot be certified by API 26.

## Artifact/backup audit and cleanup

`apkanalyzer manifest print`, `dex packages --defined-only`, `files list`, and
decoded extraction XML were inspected on the freshly built artifacts:

- Debug SHA-256: `9c9e3c0300a8e88a7ee2cf49a2ca87aefd218d5ea40dffc6074e24d1526a8876`.
- Unsigned release SHA-256: `364619c2a286e6c47d96452193c4b261deed7e1f561e10fd560f1a6194a45e92`.
- Final separate test APK: `b62688d7849f8226414f7700ca196963443483584c8d96ee79dcdc80b22663b2`.
- App `src` files plus module Gradle snapshot SHA-256:
  `26805bf360a95994c6dd5b2dd6b0f6fd957f32d05dea62927c100b1dd3e39aa9`
  (sorted relative paths, NUL, contents, NUL; paths relative to `android/`).

No app-owned SampleWorkspace, WorkspaceFixtures, LongHistoryFixture, OperationMetrics,
capacity/layout tests or seed command was found in compiled app code. No fixture
asset/raw payload was found; release assets contain Android baseline profiles.
Broad search initially matched unrelated Kotlin random `seedInitialized` and
legitimate date `today_explore`; the final check distinguishes these from app-owned
seed paths. No shipped app-owned preview generator/source set was introduced.

Both manifests retain MainActivity, min26/target36, RTL, no Internet/broad storage
permission, backup=false/fullBackupContent=false. The merged signature-level
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and AndroidX components remain. Debug
contains tooling PreviewActivity and test-manifest ComponentActivity as before;
they are library hosts, not app-owned seed endpoints. Release XML was resolved via
`resources value --config default --type xml --name data_extraction_rules` to
`res/4j.xml`; both variants exclude file/root/database/sharedpref/external from
cloud backup and device transfer. These are intermediate inspected artifacts, not
release/device/security certification or proof of the final installed pulled hash.

The disposable AVD was returned to physical 720×1600/320dpi, font 1.0, window and
transition scales 1.0, animator setting absent (original value). Marker absence
was verified; temporary cold hierarchy was removed. Captured crash buffers and
final event buffer show no recorded FATAL/ANR events; instrument logs explicitly
retain caught test OOM failures. Logs are bounded observations, not proof that no
event can occur. `adb -s emulator-5596 emu kill` succeeded and the emulator process
completion was reported. No phone cleanup/update was performed.

## Remaining work recorded November 15 (historical, superseded above)

1. Address allocation amplification without weakening lossless preservation, then
   rerun all isolated capacity cases and complete suites.
2. Complete/bound dynamic diagnostics and investigate unexpanded transfer resources.
3. Complete actual TalkBack and hardware-keyboard primary/transfer journeys, modal
   focus-return evidence, rendered full-state contrast/targets and supported system
   font/RTL/platform matrix. Obtain minimum-supported memory/device definition.
4. Measure process-cold workspace-ready and loaded app-only PSS; retain jank findings.
5. Final source/artifact/installed-identity review, focused local commits, and Tasks
   14–15 handoff remain pending. None of the unavailable checks is counted as passed.
