# Task 13 measurement protocol (defined before device runs)

These are debug/emulator observations, not minimum-device certification or latency
budgets. No retrospective pass thresholds will be invented. Semantic/Compose key
injection checks do not establish actual TalkBack or hardware-keyboard usability.

## Remediation measurement correction (2026-11-22)

The original November 15 results below are historical. Fresh remediation runs use
counted UTF-8 byte sinks, production file/stream workflows and the actual configured
host view for modal localization. `OperationMetrics` remains explicitly a combined
instrumentation/input-retention sampler. Finishing generation before its start does
**not** make the target process app-only: tests and expected workspaces remain there.

App-only observations require the fixture-preparation instrumentation to exit, then
`am force-stop` and a normal `MainActivity` launch with external `dumpsys meminfo`
sampling. Loaded readiness must be shown by a rendered fixture repo, not just the
first Activity draw or disappearance of a loading label. Sampling/tool invocation
adds latency and misses transient peaks; results are upper-bound readiness samples,
not precise cold-readiness benchmarks or minimum-device evidence.

Exact serial/AVD/package/token checks precede every mutating host operation. A runner
EXIT trap captures logcat/exit-info, resets display/animations, removes its marker,
and stops only the verified disposable AVD. Failed runs retain their evidence and
do not proceed to dependent verification steps. Host tests may use a constrained
192MiB JVM heap only as a regression check, never as ART/device certification.

Green instrumentation results alone do not establish OOM-free execution. Review
retained ART allocation warnings as well as fatal exceptions, ANRs and process
exit reasons. Run 7 exposed allocation-OOM warnings despite 87/87 results in each
mode; the runner now rejects app OOM/crash/ANR markers during its terminal audit.
Run 8 exercised that audit: 88/88 suites did not override four fresh-store reopen
allocation-OOM warnings; runner exit=1 and cleanup evidence were retained.
The follow-up capacity workflow moves save/export/restore/replacement into a
non-inline helper returning before fresh-store reopen, removing possible retained
test-frame Store/candidate pressure. It retains all assertions and the original
expected state, introduces no explicit GC or expected-workspace load shortcut,
and does not change production lifetimes. This is a measurement-harness correction,
not app-only capacity certification; ART may already have considered those locals
dead, so no improvement is assumed before observing the fresh run.
Run 9 then passed both 88/88 suites and the terminal runtime audit with zero OOM/
fatal/ANR markers in retained logcat. Both complete suites' starts are retained;
this is warning-free observed regression evidence, not universal absence outside
retention, proven causal attribution, lifetime fragmentation safety, app-only byte-
capacity evidence or minimum-device certification. App-only long-history samples
remain separate from instrumented boundary/combined-input measurements.
Absence in retained logcat cannot prove absence outside that log retention window.

## Isolation and environment

Only a new `GitHabit_Disposable_Task13` AVD may be provisioned. Every instrumented
setup checks package, AVD identity and fresh opt-in marker before any file write or
Activity launch. All host ADB commands select its exact serial. No phone operations.
Record API, image, RAM/heap, renderer, display/density, usable bounds/insets, locale,
font scale, theme, animation scales, source and APK hashes. Restore modified settings,
remove marker, inspect crash/ANR logs and stop the target after testing.

## Representative layout coverage

Baseline 360×800dp/100%; compact 320dp/200%; short-height and expanded windows;
light/dark/system; English XA and Arabic XB debug pseudolocales. Use representative
journeys rather than claiming an exhaustive Cartesian matrix. Validate form errors,
weekday/radio state, scroll-reachable save/confirm, noninteractive chart descriptions,
exact date inspection and correction. Record which checks use synthetic density or
configuration overrides rather than real system font/locale/display settings.

## Long history

Instrumented-only deterministic fixture: 20 repos, five calendar years ending
2026-11-04, one fractional or complete entry per repo per date before today; three
schedule types, dated goal changes, and a future week-start request. Current-day
repos remain actionable. Generator stays in `src/androidTest`, never `src/debug`.

Endpoints: launch call → rendered Today and ready; explicit index construction →
week/date/heatmap summaries; repeated Today/Repos/Activity/detail swipes → idle;
log/correct/Undo invocation → verified published snapshot; frozen export prepare →
callback and output write → closed stream; restore read → validated preview; confirm
→ verified replacement; close/relaunch → ready. Verify equality/counts after operations.
Report individual elapsed wall times and repetition count; no warm runs labeled cold.
An Activity relaunch is not OS process death. External `am start -W` after force-stop
provides a separate process-cold startup observation when run.

`FrameMetrics` listener copies metric values immediately on a dedicated HandlerThread;
report total frames, first-draw separately, callback drops, >16.67ms and >33.33ms
non-first-draw frames, max/median/p95 total duration. These are 60Hz reference buckets,
not device deadline compliance or a benchmark pass. Listener belongs to tested Activity
window; modal windows are not implicitly covered. Sample Java used heap/native heap/PSS
and GC runtime counters during operations; sampled peaks may miss transients. Tests and
fixtures share the app instrumentation process, so these samples include test overhead
and retained input allocations. Only standalone-app external PSS is app-only evidence.

## Capacity

Retain provisional shared bounds: 32MiB UTF-8, 1000 repos, 100000 commits, 100000 total
configurations, IDs of 128 UTF-16 code units, root-inclusive depth 8. Separate below/at/
above cases per dimension and representative combined pressure. Byte cases must include
a **populated valid workspace**, not just padded `{}`. Use accepted surrounding whitespace
in a legacy-compatible field to hit exact bytes without exceeding semantic text limits;
include escaped/multibyte data separately. Depth parser validity is distinct from schema
validity. Above-boundary failure must be CAPACITY, with no returned truncation/mutation.
Measure codec/validation allocations, duration, GC and process heap/PSS. Generation and
input retention are explicitly included in instrumentation-process memory, not subtracted
using guessed baselines. A count-limit new log must reject safely, not succeed.

Minimum RAM/heap/hardware and actual assistive-tech journeys are not specified/available;
keep capacity limits provisional and Gate D open until that evidence exists. API 26 alone
does not define a minimum-memory device. Full RTL/layout/contrast handoff claims require
actual rendered-state inspection, not simply successful compilation or palette tests.
