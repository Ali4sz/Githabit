# Task 3 — pure scheduling and analytics

Intermediate domain work, **not a personal-use delivery**. Runtime persistence remains
v1 and UI remains daily-only until the owning migration/command/UI tasks. Non-daily
state must not be sent through the v1 codec; store round-trip verification remains intact.
Runtime sample removal is still Task 7. No personal device or workspace was accessed.

## Change 1/4 — schedule model (2026-11-05)

Source: the commit containing this entry, based on `100005a`.

- Added daily, immutable selected-weekday, and 1–7 distinct-days/week schedule values.
- Dated configurations and normalized drafts carry a schedule; v1 defaults stay daily.
- Redundant/cosmetic saves do not add versions. Same-day edits preserve unrelated
  fields; effective configuration comparison ignores dates, including same-day reversions.
- Conflicting target/schedule/status edits behind a newer configuration are rejected.
  Legacy unsorted history is resolved without rewriting existing dates.

The resumed 81-test debug run passed after the previous two failures were corrected.
After added coverage, from `android/`:

```sh
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --console=plain --no-daemon
```

Completed before `2026-11-05T20:44:15+03:00`, exit 0, BUILD SUCCESSFUL (57s).
Both debug and release ran **85 tests, zero failures/errors**. Lint: zero errors,
eight existing advisory warnings. Debug app and separate test APK assemble passed;
`git diff --check` passed. Log: `/tmp/opencode/task3a-verified-build.log` (temporary).
No connected tests were run for this pure-domain increment.

Remaining: indexed daily analytics, historical period resolver, weekly assessment and
summaries. Command-level no-op feedback/overlay dismissal is owned by Task 5; the
current ViewModel returns early for unchanged snapshots. The legacy fixture inventory,
device/accessibility/capacity checks, and sample-free artifact gates remain outstanding.

## Change 2/4 — indexed daily progress and activity (2026-11-05)

Source: the commit containing this entry, based on `941682b`.

- `CommitIndex` aggregates amounts/counts per habit/stored date and overall counts
  without mixing units. Snapshots do not retain caller-owned mutable collections.
- `ProgressAnalytics` sorts versions once, uses binary configuration lookup, and
  separates numeric progress from obligation, assessment, and qualifying-day credit.
- Partial/excess/optional/paused-date activity remains visible. Future records are
  retained but cannot qualify or extend activity streaks before their saved date.
- Activity streaks count positive activity, not schedule adherence. Empty history
  yields zero counts/streaks. Correction/deletion rebuilds affect only the new snapshot.
- Fourteen tests cover accumulation, rounding, remainder, optional weekdays, weekly
  daily opportunities, past/today/future states, dated changes, pause/archive, stored
  date identity, immutable snapshots, mixed units, correction/deletion, leap/year/DST
  calendar arithmetic, and empty history.

`./gradlew :app:testDebugUnitTest --console=plain --no-daemon` passed (17s).
Then `./gradlew :app:test :app:lintDebug :app:assembleDebug --console=plain --no-daemon`
passed (45s), completed before `2026-11-05T20:49:00+03:00`, exit 0. **99 tests per
variant, zero failures/errors**; debug reused its immediately preceding passing run,
release ran afresh. Lint: zero errors/eight existing advisories. Debug APK assembled;
`git diff --check` passed. Logs: `/tmp/opencode/task3b-tests.log` and
`/tmp/opencode/task3b-verified-build.log` (temporary). No connected tests.

The old `progress`/`dailyScore` UI path is deliberately unchanged; schedule-aware UI
consumers will share this new index rather than construct one per cell. Calendar
resolution, weekly assessment, adherence, and composed summaries follow next.

## Change 3/4 — historical calendar periods (2026-11-05)

Source: the commit containing this entry, based on `62fbdd6`.

- `WeekCalendar` provides immutable ordered transitions, separate replaceable pending
  requests, half-open full weeks and short bridges, effective preference, and projected
  first-full-week dates. Binary lookup is shared by all future weekly consumers.
- Pure requests preserve the current period, fix started bridges, replace/cancel only
  pending changes, queue at bridge ends, and honor original boundaries after reopening.
- `Workspace.calendar` defaults to Monday. `requestWeekStart` applies immediately only
  without habits, commits, or calendar history. This domain command is not yet wired
  to persistence/UI; the v1 round-trip safety boundary is unchanged.
- Fourteen tests include all 49 weekday pairs, exact 1/6-day bridges, replacement,
  cancellation, chains, activation on B, reopening, empty/nonempty workspaces, invalid
  history, immutable inputs, clock rollback, leap/year/DST dates, and 84 sequential
  requests proving every elapsed date retains its period without gaps or overlaps.

From `android/`, `./gradlew :app:test :app:lintDebug :app:assembleDebug
:app:assembleDebugAndroidTest --console=plain --no-daemon` passed (57s), exit 0,
completed before `2026-11-05T20:55:28+03:00`. **113 tests per variant, zero
failures/errors**; lint zero errors/eight existing advisories; both APKs assembled;
`git diff --check` passed. Log: `/tmp/opencode/task3c-verified-build.log` (temporary).
No connected tests or personal-workspace writes. Weekly assessment/summaries remain.
