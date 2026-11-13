# Task 10 — schedule-aware, logging-first Today

Verified locally **2026-11-13**. Intermediate build, not the final personal-use
handoff. No physical-phone operation, push, publication or APK upload occurred.

## Implementation

- `ui/TodayContent.kt` replaces Today's hero, large dashboard and graph with a
  compact dated header, actual today's commit count, saved obligation-only Remaining
  filter, four ordered domain groups and small history/date-inspection affordances.
  Insights retains its existing graph; its metrics/graph semantics remain Task 11.
- Stable globally keyed lazy habit items and saved lazy-list state survive progress
  updates, group moves and Activity recreation. Optional weekday dates expose Open
  repo, not Today quick logging; Detail still permits their optional progress.
- Numeric actual/target amounts remain uncapped; the visual ratio caps at 100%.
  Log opens a blank retained draft. Complete remaining adds only the positive daily
  remainder. Both are separate labeled standard controls with minimum 48dp targets.
  Complete is disabled once today's amount reaches target, even when another weekly
  qualifying date is still required. Optional extra logging remains available.
- Stable weekly rows show capped target-completed distinct dates x/N, pending/met
  quota and whether today already qualifies. Partial-week practice has completed-date
  counts and a separately labeled usual future quota, never a combined x/N score.
  Weekly headers show explicit current/bridge bounds; completed weekly rows retain
  bounds. Compact daily/weekday rows retain their schedule summaries.
- `domain/TodayOverview.kt` classifies unfiltered no-repo, inactive, no-obligation,
  quota-met, due-complete and remaining states. Filtered-empty copy explains how to
  recover optional/completed rows. No profile or success claim is invented for empty
  history. `WeeklyAnalytics.kt` shares the authoritative row group classifier.
- `GitHabitViewModel.kt` shares one analytics index for each relevant committed
  habits/commits/calendar snapshot and evaluation date, not each weekly card. Theme
  changes reuse it. Today action tokens revalidate rendered date, workspace session,
  entity, current progress/schedule and quick-log eligibility inside the serialized
  boundary. Stale dates require another tap, not a silent log on a different date.
- Save remains verified before publication/feedback/Undo. Quick Complete names its
  repo; a Today log that leaves Remaining names the repo and target/quota transition.
  Completion-notice preparation reuses the committed index, builds the candidate
  index off Main and inspects only the affected habit. The candidate cache is seeded
  only after exact verified commitment and only for a matching visible date.
- `ui/Sheets.kt` explains that Remaining only fills today's numeric amount, not a
  submission or weekly quota. Weekly LogForm context uses the shared period/index.
  Existing draft, correction/deletion, recovery and Undo policies remain intact.
- Touched Today/form copy uses string/plural resources. No dependency, schema,
  package/application ID or web-prototype change.

## Verification

Current Compose guidance was retrieved using Context7 resolution plus two scoped
queries and TinyFish search/fetch: https://developer.android.com/develop/ui/compose/lists
and the linked accessibility guidance. Pinned BOM 2025.09.01 / baseline M3 1.4.0
remains unchanged; no Expressive or navigation-framework upgrade was introduced.

From `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --rerun-tasks --console=plain --no-daemon
```

Full clean reruns passed after implementation/review fixes (last production rerun
98 seconds). A final incremental run after test-only additions passed in 51 seconds:
**279 JVM tests per debug/release variant**, zero failures/errors/skips. **Lint zero
errors, eight pre-existing SDK/dependency advisories.** App and separate test APKs
built. Reports remain in `app/build/test-results/` and `app/build/reports/`.

Eight new JVM tests cover stale date/timezone admission, changed progress/entity and
replacement session, repeated remainder completion, verified rollback/Undo, shared
index reuse, midnight during save/cache publication, distinct qualifying dates,
correction/deletion grouping, pending preference, bridges and empty classification.

Fresh **GitHabit_Disposable_Task10**, exact **emulator-5590**, Android 16/API 36
Google APIs x86_64, SwiftShader, no snapshots, 2560MiB RAM/256MiB heap. Registration
is `/tmp/opencode/githabit-task10-avds`; ignored userdata is
`android/.gradle/task10-avds/device.avd`. As in Task 9, AVD manager reported missing
device metadata after writing configuration; identity/RAM/display were explicitly
set and actual boot identity verified before any installation or fixture write.

Runner `/tmp/opencode/run-task10-tests.sh` checks exact AVD identity, completed boot
and absence of an old marker, generates a fresh UUID marker and supplies explicit
disposable package/AVD/token arguments. Tests changing display settings require
disposable-emulator authorization, never physical-phone consent.

| Final exact app/test APK suite | Result |
| --- | --- |
| Normal animations | **59/59**, **179.588s** |
| All three animation scales 0 | **59/59**, **167.479s** |

Host logs are ephemeral `/tmp/opencode/task10-final3-*`. Results were checked for
the expected passing count and absence of failure/crash statuses, not shell exit
alone. An earlier host shell run was terminated by the tool while running tests;
the complete fresh-marker rerun passed. This interruption is not counted as a pass.

Six new `TodayFlowTest` cases establish daily and weekly initial reachability,
exact one-log amounts, Remaining fills without submitting, Monday multiple commits
→ 1/3, incomplete Tuesday → 1/3, Wednesday → 2/3, corrections/quota/optional extra
logs/Undo, pending preference and fixed bridge bounds, daily/weekday bridge
obligations, unscheduled-date Detail logging, 25-row partial updates, filtered
completion-specific feedback, saved filter/scroll through Activity recreation and
320dp system 200% text Log/Complete operation. All existing form, recovery,
preferences, migration, adaptive navigation and route-gesture tests reran.

### Measured initial viewport

API 36 emulator, 720×1600px window at density **2.0** = **360×800dp**, font **1.0**,
no IME. Actual system insets: top **136px/68dp**, bottom **48px/24dp**, sides zero.
Today page after shell/insets: `(0,264)–(720,1392)px` = **360×564dp**. Daily Complete
bounds: `(226,901)–(593,997)px`, wholly inside the initial page and **48dp** high.
Log and Complete were asserted displayed before any scroll. Stable weekly Log and
Complete likewise passed initial-display assertions. `Task10Viewport` log records
the actual window, density, font, insets and page/control bounds.

Initial new-test failures were harness errors: merged card semantics hide child
test tags from merged-tree lazy search, navigation tabs have no scroll parent, and
one fixture name exceeded the domain's 48-character bound. Tests now use unmerged
lazy search/child assertions, direct tab clicks and a valid long fixture name.
Product assertions were not weakened. The retained legacy flow helper now searches
Today lazy rows before clicking them.

## Review and artifacts

Read-only review identified missing compact schedule/optional-week period context,
generic feedback when Log leaves Remaining and redundant Main-thread indexes in
notice preparation. Each was fixed and rerun. Final follow-up found no significant
unresolved correctness/preservation issue. Reviewer did not independently execute
the reported suites.

| Artifact | SHA-256 |
| --- | --- |
| Debug app | `1cf4e7248b9836b9208e78f2265a6194ee9ee1eee2ba6d4f53611d05ff765670` |
| Separate debug test APK | `1cfcc1de7ae350a4e1f19090f41f32e8a1ae490bcec5983212a6baf703ac150c` |

App signature verification, manifest, defined DEX packages and ZIP inventory were
inspected with SDK tools. No app-owned sample generator, fixture factory/payload or
seed entry point was found. App hash matched the installed base APK; subsequent
test-only builds left the app hash unchanged. Identity remains
**com.githabit.prototype, 1.1-local, code 2**; no Internet/account/broad-storage
permission added. Test fixtures remain solely in automated-test source sets.

Marker absence verified, display restored to physical **720×1600px/320dpi**, font
**1.0**, animation settings **1.0/1.0/unset**, and exact emulator stopped. Synthetic
userdata remains only in ignored disposable storage. Phone untouched.

**Limits:** no Task 10 real process-death journey, complete TalkBack/hardware-keyboard
focus-return audit, predictive modal gesture, oldest-supported Android, folding/
tabletop hardware, actual split-screen divider, RTL, all-screen 200% pixel audit,
long-history capacity/performance profile or final restore/transfer journey.
Activity recreation, semantic interactions and a 25-repo list are not substitutes.
Task 11 owns the deferred Insights/Detail/date-explorer semantics redesign.
