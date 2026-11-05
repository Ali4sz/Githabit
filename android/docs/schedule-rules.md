# GitHabit schedule rules (implementation contract)

Normative summary of the product/scheduling contract in
`.plans/githabit-android-build-plan.md` for this implementation. When this file and the
plan disagree, the plan wins. Tests are the executable version of these rules.

## 1. Configurations and history

- A habit's dated configuration version is `(date, target, status, schedule)`.
- Schedule kinds: `Daily`, `Weekdays(nonempty set)`, `DaysPerWeek(1..7)`.
- Lookup for a date uses the latest version dated on or before it; pre-creation dates are
  not applicable.
- Name/description/icon/accent edits are presentational and never invalidate a week.
  Target, schedule, or status edits can.
- Habit edits are **today-effective**. Units stay locked after the first commit.
- Workspace week definition (start-of-week) is **workspace-level**, stored separately from
  habit versions, and changes are **future-effective** (section 5).

## 2. Daily progress

- A date's amount is the rounded sum of all commits for the habit on that stored date.
- Partial progress is kept and visible; display ratio caps at 100%.
- "Complete remaining goal" adds only the positive remainder; it is a no-op otherwise.
- Daily/Weekdays obligations exist only on active, eligible dates. Today is pending, never
  failed while open. Past eligible dates below target are missed.
- Progress on a non-selected weekday is optional activity: no obligation, no credit.
- Paused/archived habits reject new commits; historical corrections remain available.

## 3. Weekly quota (`DaysPerWeek(n)`)

- Any distinct date in the calendar week qualifies when accumulated progress reaches that
  date's target and the habit is active/eligible that day. One qualifying day per date max.
- Partial days never count. Quota display caps at n. Surplus never carries over.
- An open stable week shows count/n and pending-or-achieved, never failure.
- A closed, uninterrupted stable week is achieved (>= n qualifying dates) or missed.
- Weekly habits are not seven daily obligations; once the quota is met they leave the
  default "remaining" group while optional logging stays available.

## 4. Transitional periods

- A weekly period is scored only when one configuration (schedule + target + active
  status) applied for every day of the period and the habit existed from its first day.
- Creation, pause/resume, or a target/schedule change during the period makes it
  **transitional/unscored**; progress stays visible and is excluded from adherence.
- Transitional status outranks pending/achieved/missed labels. On Today these rows appear
  as partial-week practice with the usual future weekly target, not an x/N quota score.
- A change effective on a period's first day establishes that period's configuration; a
  redundant save creates no fake transition.
- Corrections and successful Undo recompute results (dated expectations persist; totals
  are not frozen). No-op edits add no versions.

## 5. Week definition (Settings → Week starts on)

- Workspace stores a base first-weekday plus dated transitions
  `(effectiveFrom, weekday)`; the first period covers all history.
- Saving the currently-effective weekday is a no-op when nothing is pending; otherwise it
  cancels the pending request. A genuinely different weekday schedules a change at the
  next boundary `B` (end of the period containing today).
- At `B` the requested weekday becomes effective. `T` = first occurrence of the requested
  weekday on/after `B`. `[B, T)` is a fixed **bridge**: neutral/unscored for weekly quotas
  only; daily/weekday obligations continue. The first full new week is `[T, T+7)`.
- Bridges are 1–6 days for changed weekdays. No date belongs to two periods.
- Before `B` a pending request can be replaced (weekday only, same `B`) or cancelled.
  During an active bridge its dates/destination are fixed; further changes queue at the
  bridge end `T` and can be replaced/cancelled before `T`. A queued change may create
  another short bridge and always updates the projected first-full-week date.
- Activation is computed from stored dates, not app launch time: closed-across-boundary
  changes apply on their original dates.
- A genuinely empty workspace (no habits, no dated expectations, no commits) applies the
  new weekday immediately; zero commits alone is not "empty".

## 6. Metrics

- Daily target progress: amount and capped ratio per habit/date.
- Weekly quota progress: qualifying distinct dates out of n for the habit/period.
- Activity streak: consecutive dates with any logged progress (any amount); unfinished
  today allowed. Distinct from adherence and always labeled as activity.
- Adherence denominators include only assessed eligible dates (day schedules) and scored
  full periods (weekly). Transitional/bridge periods are excluded. Day and week rates are
  never averaged together.
- Overview chart shows recorded activity (commit counts by date), not success.
- All weekly grouping uses the resolved week timeline: explicit start/end dates, bridges
  labeled, historical boundaries never regrouped by later preference changes.

## 7. Real data

- The shipped app contains no sample/mock datasets, seed generators, or fabricated
  profile defaults. Fresh/reset workspaces are empty with an unset profile.
- Existing user records are preserved on upgrade/import regardless of origin; there is no
  provenance-based cleanup.
- Synthetic generators exist only in test sources and are used only against guarded,
  disposable installations.

## Domain query integration (Task 3)

The rules above describe the final contract, not a claim that the intermediate v1 UI
already implements it. The new pure API is ready for migration/command/UI tasks:

- Construct `ProgressAnalytics(workspace, capturedToday)` once per committed history
  snapshot/date horizon. Reuse it for `daily`, `weekly`, `dateSummary`, `weekSummary`,
  `todayGroups`, `adherence`, and `commits.activityStreaks`; do not rebuild per graph cell.
- `TargetProgress` retains actual totals and a capped ratio even on an optional or
  subsequently paused date. Use `DailyProgress.eligibility`, `assessment`, and
  `qualifies` to describe schedule credit, **not** the numeric `targetReached` alone.
  Future saved activity remains inspectable but cannot qualify before its date.
- `WeeklyProgress.quota` is present only for a stable active full week. Transitional
  results provide weekly-configured qualifying dates and a separately labeled
  `endingWeeklyGoal` (the goal effective at the period's end, if still weekly).
  Earlier daily/weekday configurations keep their own day assessments; they are not
  pooled into an authoritative weekly quota after a midweek schedule change.
- `adherence(start, endExclusive)` counts days inside that half-open range, and weeks
  only when their **entire resolved bounds** fit inside it. Request calendar bounds
  when displaying a weekly rate. Achieved open periods may count, pending periods
  do not; later corrections/edits can legitimately change the result. Empty denominators
  yield null rates, not zero or fabricated success. Never combine day/week rates.
- `todayGroups` always returns the four ordered groups, with stable workspace order
  inside them. Only Scheduled and Weekly remaining are obligation/Remaining groups.
  Hide empty headers in UI without relabeling practice as outstanding work.
- `WeekCalendar.periodOn` is the only source of period membership. The immutable
  calendar separates locked `transitions` from replaceable `pending`. `status` provides
  effective/pending conventions, current fixed bounds, and projected first-full-week
  start; `advancedTo` retains original activation dates when persisting after reopening.
- `Workspace.requestWeekStart` is pure. Task 5 must run it inside the serialized,
  captured-date, verified-write boundary. Task 4 must serialize schedules and the
  complete calendar losslessly before runtime commands can enable these features.
