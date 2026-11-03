# GitHabit Android — Personal App Build Plan

## Context

Evolve the existing native Kotlin/Jetpack Compose prototype into a dependable personal habit tracker for real-world use by developers. Retain the Git-inspired language, progress logging, contribution history, and Material 3 foundations. Ship a real-data-only app with no sample workspace, seed generator, or fabricated user history. Prioritize quick daily use, flexible schedules, and preservation of personal history over commercial features or elaborate visual effects.

This is an implementation plan, not an implementation. No application code has been changed and no build or test has been executed as part of preparing it. Existing screenshots and reports describe previous prototype verification, not the finished app.

- **Project:** `/home/ali6s/workspace/app-idea/android/`.
- **Native source root:** `android/app/src/main/java/com/githabit/prototype/`.
- **Existing web prototype:** project-root `app.js`, `domain.js`, `index.html`, and `styles.css`; leave unchanged.
- **Distribution:** local installation first; optional public GitHub source sharing later. Publishing or uploading anything requires a separate explicit action.
- **Plan status:** research and requirements established; implementation not started.

## Requirements

### Confirmed by the user

1. The audience is developers; `repo`, `commit`, and contribution-style visualizations are intentional product language.
2. This is primarily for personal use, possibly shared on GitHub. Commercial viability, monetization, and a market-launch program are not goals.
3. Support three schedule types:
   - **Daily**.
   - **Selected weekdays**.
   - **N distinct days per calendar week**.
4. For a weekly quota, a date qualifies only when accumulated progress reaches that date's numeric daily target. Multiple commits on one date still contribute at most one qualifying day.
5. Partial progress remains recorded and visible even when the target has not been reached.
6. Retain the existing numeric units, fractional logging, remaining-only completion, historical corrections, deletion, and Undo behavior unless a documented reliability correction is required.
7. Preserve historical expectations when schedules or targets change. Unscheduled dates are not failures.
8. Activity streaks and adherence to a schedule are different metrics and must be labeled separately.
9. Use the four uploaded Material 3 documents as design guidance, with the verified qualifications recorded below.
10. Provide a **Settings → Week starts on** option so the user can choose the app's first weekday.
11. Remove mock/sample/test data and its generation paths from the **shipped native app**, including demo entry points and fabricated profile defaults. Fresh users start with an empty workspace and create their own habits.
12. Retain isolated test-only fixtures and automated verification, as explicitly selected by the user. They must not be packaged in any delivered app variant or injected into personal workspaces. Do not automatically delete existing personal or uncertain-origin data as part of this cleanup.

### Recommended scope and explicit planning assumptions

These are proposed defaults, not additional features explicitly requested by the user. They keep execution unambiguous and can be changed before the affected task begins.

- **Local-only, one workspace:** remove simulated credentials and sign-out concepts rather than build real authentication. Keep a short local welcome leading to Create first repo, with no sample-data walkthrough or fabricated records.
- **Restore included:** add validated, confirmed, replace-only JSON import. Do not add merge/conflict resolution or cloud backup.
- **Week definition:** app-wide **Week starts on** selection offering all seven weekdays. Monday remains the default for fresh workspaces and v1 migration; the setting, not a locale change, controls the convention. Use the device's local timezone and preserve dated week-definition history rather than applying the newest preference to all past dates.
- **Date identity:** a saved commit's calendar date is authoritative. Changing timezone must not re-bucket existing history from its timestamp.
- **New logs:** today only, matching the existing prototype. Existing historical commits can be corrected/deleted; creating backdated commits is not part of this increment.
- **Habit schedule edits:** effective today, like existing target/status changes. Earlier dates retain their versions. Same-day edits may legitimately change today's expectations. The workspace's week-start preference is a separate, future-effective change described in contract section 8.
- **Partial weekly periods:** creation or a substantive target/schedule/status change during a weekly period makes that period transitional and unscored. Do not invent prorated quotas. Details appear in the schedule contract below.
- **Theme:** system/light/dark choice; preserve an existing user's explicit light/dark preference during migration. Dynamic wallpaper colors are optional future work, not necessary for M3 compliance here.
- **Adaptive scope:** useful compact and expanded layouts, including bar/rail adaptation and a practical repo list/detail arrangement. No bespoke desktop product.
- **Accessibility:** 48dp minimum targets for ordinary touch controls, text scaling to 200%, usable TalkBack traversal, external-keyboard access, and reduced-motion behavior. Dense charts require an equivalent accessible inspection path, not hundreds of oversized squares.
- **Visual direction:** baseline M3 with a restrained green GitHabit brand. No mandatory Expressive dependency upgrade or spring/shape-morphing project.

### Out of scope

- Every-N-days intervals, rolling seven-day windows, weekly carryover, monthly quotas, alternative calendar systems, configurable recurrence engines, and per-habit week-start preferences.
- Real sign-in, account isolation, backend services, cloud sync, GitHub integration, social features, teams, or leaderboards.
- Reminders, notifications, widgets, billing, subscriptions, ads, analytics SDKs, and store-release work.
- Rewriting the native app from scratch, keeping the web prototype in feature parity, or changing persistence libraries solely for architectural fashion.
- Import merging, anti-cheating rules, biometric locking, application-level encryption, and public publishing without a follow-up decision.
- Removing automated tests or their isolated fixtures, adding a demo mode, and heuristic deletion of previously seeded or mixed-origin user history. “Real-world use” does not expand this increment into a backend/store-release project.

### Open decisions

There are no blocking product questions. The user explicitly chose to remove sample/mock data from the shipped app while retaining isolated test fixtures; this supersedes the earlier optional sample walkthrough. The week-start setting is user-requested; all-seven-day choices, Monday initialization, and the future-effective history-preservation policy are explicit implementation defaults. Apply these and the neutral-transition assumptions unless the owner changes them; Task 1 records them without requiring another approval round. Before any public sharing, the owner must choose a license and approve the files to publish. Those optional publication decisions do not block local development.

## Research Summary

### Existing implementation

The native app already has a useful foundation: Compose Material 3 components, immutable domain models, historical target/status versions, app-private atomic-file persistence, positive fractional progress, remaining-only completion, correction/deletion, Undo, date exploration, two themes, and JSON export.

Research was performed by separate read-only domain/persistence and UI exploration agents, plus official-documentation research. No project-specific `AGENTS.md` was found; the user's global instructions apply. File references below were checked against the existing native tree.

Verified file references from the prototype:

| Existing file | Responsibility / planning implication |
| --- | --- |
| `android/app/build.gradle.kts` | Native module; minimum SDK 26, compile/target SDK 36; dependency changes belong here. |
| `android/build.gradle.kts`, `android/settings.gradle.kts`, `android/gradle/wrapper/gradle-wrapper.properties` | Existing build/toolchain configuration; preserve a working reproducible baseline before upgrades. |
| `android/app/src/main/java/com/githabit/prototype/domain/Habits.kt` | `GoalVersion`, `Habit`, `Commit`, `Workspace`, numeric validation, progress/streak calculations, dated mutations, sample generation. |
| `android/app/src/main/java/com/githabit/prototype/data/WorkspaceJson.kt` | Versioned JSON encoding/decoding and validation; migration and round-trip contract belong here. |
| `android/app/src/main/java/com/githabit/prototype/data/WorkspaceStore.kt` | `AtomicFile` workspace reads/writes; physical atomicity exists, but recovery policy needs work. |
| `android/app/src/main/java/com/githabit/prototype/GitHabitViewModel.kt` | Load state, mutations, persistence coordination, Undo, navigation, date refresh, export, and simulated identity. |
| `android/app/src/main/java/com/githabit/prototype/MainActivity.kt` | Activity entry point and resume-time date refresh. |
| `android/app/src/main/java/com/githabit/prototype/ui/GitHabitApp.kt` | Scaffold, fixed bottom navigation, tab state, back handling, snackbar host, document picker. |
| `android/app/src/main/java/com/githabit/prototype/ui/Screens.kt` | Today, Repos, Detail, Insights, Activity, Settings, simulated sign-in/onboarding. |
| `android/app/src/main/java/com/githabit/prototype/ui/Sheets.kt` | Repo/log forms, date explorer, guide, confirmations. |
| `android/app/src/main/java/com/githabit/prototype/ui/Components.kt` | Cards, page layout, stat panels, custom Canvas heatmap, reusable copy/formatting. |
| `android/app/src/main/java/com/githabit/prototype/ui/Theme.kt` | Light/dark color schemes, typography, shapes, habit accents, heatmap colors. |
| `android/app/src/test/java/com/githabit/prototype/domain/HabitsTest.kt` | Existing domain regression tests. |
| `android/app/src/test/java/com/githabit/prototype/domain/WorkspaceJsonTest.kt` | Existing serialization regression tests. |
| `android/app/src/androidTest/java/com/githabit/prototype/GitHabitFlowTest.kt` | Existing native flow tests and screenshot workflow. |
| `android/README.md` | Native setup, previous test evidence, prototype boundaries, and distribution instructions. |

### Gaps to address

- A load exception currently leaves a fresh in-memory workspace usable, so a later mutation can overwrite the unreadable original (`GitHabitViewModel.kt`, initialization and `update`). Atomic file writes do not prevent this logical data-loss path.
- Today places a large hero, statistics, and heatmap before habit logging. Saved phone screenshots substantiate the hierarchy problem.
- Navigation is a bottom bar at every window size; content is one constrained column.
- The custom heatmap has tiny pointer-selected dates and only a general accessibility description. The existing date explorer is a useful starting point, not proof of accessible equivalence.
- Numerous typography, shape, and spacing literals live directly inside components. Custom theme definitions themselves are legitimate; scattered screen-level definitions are the consistency problem.
- Export cannot currently be restored in the app. Simulated sign-in is unnecessary for personal local use.
- The existing daily-only model cannot express weekly quotas without separating daily progress from weekly adherence.
- No week-start preference currently exists. `SettingsScreen` and `GitHabitViewModel` already provide the workspace-preference persistence path; the new calendar setting belongs there, not in theme state or an unrelated preference store. Heatmap Sunday alignment and rolling-seven-day Insights need the same shared period resolver.
- Existing test reports show 17 domain/serialization cases per build variant and five instrumented cases passing in earlier runs. The README explicitly does not establish full accessibility, tablet/landscape, or older-device coverage; its large-text run was 1.3x, not 2x.
- JSON is currently format **1**, with no migration dispatcher or explicit input-byte/count limits. Valid legacy files may contain unsorted dated versions and commits on dates later paused by a same-day status edit; migration must not reject these merely because new rules are stricter.
- `undo` removes its token before the inverse write succeeds. A storage failure therefore prevents retry. Some Undo construction/post-save callbacks can also throw after data has already been persisted, producing a misleading failure notice.
- `AtomicFile` does not provide locking. Initial reads, recovery reads, and writes must share the same storage ownership policy; an unsynchronized recovery-capable read can interfere with an in-flight write.

### Real-data readiness audit

- `domain/Habits.kt` defines production `SampleWorkspace.create()`, generating six habits and 168 days of synthetic history. `GitHabitViewModel.explore()` can save it into the ordinary workspace. `SignInScreen` and `OnboardingScreen` in `ui/Screens.kt` expose sample entry points, including `explore-demo`. Remove the generator and callers from runtime source sets, not just the visible buttons.
- `Workspace` currently defaults to the fabricated name **Alex** and bio **Building a better everyday.** New/reset workspaces must instead have an unset optional profile. This requires coordinated changes to the model, `WorkspaceJson.decode`, `saveProfile`, and Settings/greeting/avatar UI, which currently assume a nonblank name. Existing persisted values must still be preserved.
- `RepoForm` currently prepopulates **20 pages** and uses reading-related copy; `LogForm` has a sample note prompt. These do not automatically create records, but replace them with intentional user input and neutral instructions. `HabitCard` must not present a fabricated description when none was supplied.
- Existing statistics derive from workspace data; no hardcoded nonzero statistics were found. However, `TodayScreen` has unconditional progress encouragement, and `InsightsScreen` renders minimum colored bars for zero/missing values. Empty states must not visually imply activity or success.
- No reliable seed-provenance field exists in the current model/JSON. `sample-*` IDs, names, dates, or profile text cannot prove that records remain disposable: users can edit generated habits and add real commits. Preserve valid legacy records without origin-based filtering.
- `HabitsTest.kt` and `WorkspaceJsonTest.kt` use the production sample generator. `GitHabitFlowTest.kt` uses the runtime sample entry and expected sample IDs. Move fixture generation into unit/instrumented test sources and update those callers in the same task that removes the runtime generator. Unit-test sources are not automatically shared with instrumented tests; `src/debug` is not an acceptable fixture location because debug APKs are deliverable here.
- `GitHabitFlowTest.launchFreshWorkspace()` writes through `targetContext` to the app's normal workspace file. A separate test APK does **not** protect personal data. Establish a fail-closed disposable-device test guard before its first baseline run.
- No bundled JSON datasets, runtime preview generators, `assets/`, or `res/raw/` sample payloads were found in the inspected native tree. `android/previews/` is documentation evidence outside runtime sources. `android/app/build.gradle.kts` still labels the app **1.0-demo** and disables release shrinking; simply leaving unreachable sample code in main sources would not satisfy removal. Inspect the actual delivered artifact rather than relying on visibility or shrinking.

### Verified build and platform posture

- The pinned Compose BOM **2025.09.01** maps core UI/Foundation/Runtime to **1.9.2**, Material3 and `material3-adaptive-navigation-suite` to **1.4.0**, and the adaptive/adaptive-layout/adaptive-navigation artifacts to **1.1.0**. These support the planned baseline; dependency constraints do not automatically add artifacts to the module.
- Start with the existing BOM and add only the adaptive artifact actually used. Do not copy current alpha/Expressive versions into the app just because a documentation example shows them.
- The current AGP **8.13.0**, Gradle **8.14.3**, and Kotlin/Compose compiler **2.2.20** combination has previous project test evidence, but AGP 8.13 is beyond Kotlin 2.2.20's published fully supported AGP range in the reviewed compatibility table. Record that caveat; verify fresh results and warnings before claiming a supported configuration or making a coordinated upgrade. The compiler plugin follows Kotlin, not the Compose BOM.
- Android 16 behavior relevant to target SDK 36 includes enforced edge-to-edge and large-screen resizability/orientation changes, independently of Play distribution. Insets, short windows, rotation, and multi-window tests are therefore practical requirements even for personal use.
- The existing `activity-compose` **1.11.0** is newer than the documented introduction of `PredictiveBackHandler` in Activity 1.8.0. For custom history, compose handlers consistently, enable them only in the applicable context, pop only on completion, and revert preview state on cancellation. Framework progress callbacks apply on API 34+; ordinary Back must still work on the minimum API.
- The platform implementation of `AtomicFile.finishWrite` can log certain sync/rename failures rather than propagate all of them. Keep an independently named last-known-good copy and verify the resulting file before treating a critical replacement as successful; do not promise absolute durability from a returned method alone.

### Material guidance to apply, not copy mechanically

1. Use semantic theme/component roles. Raw values are legitimate in theme/token definitions; ordinary screens should consume those roles.
2. Prefer one dominant action, not a rigid ban on more than one filled button in every possible context. Official guidance says “ideally” one action per page.
3. Do not prohibit primary-colored text on a surface categorically. Follow the relevant component treatment and verify contrast.
4. Adapt navigation using actual window constraints, height, posture, and content; do not require a drawer solely because a numeric width threshold was crossed.
5. Use Undo only for operations the app really can reverse. Reset and workspace replacement require explicit confirmation and preservation opportunities.
6. Preserve standard component accessibility, focus, state feedback, and platform back behavior. An attractive theme is not an accessibility certification.
7. Check library/API availability against the selected build versions. Baseline M3 is sufficient; Expressive is optional.

### External references

- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3): theme roles, customization, components, dynamic color, and accessibility.
- [Build adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation): window-aware bar/rail behavior; height and posture affect defaults.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): touch targets and semantic component behavior.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics): custom-component state and accessible descriptions.
- [Material button guidance](https://m3.material.io/components/buttons/guidelines): emphasis, labels, and contextual use.
- [Material Android is Compose-first](https://m3.material.io/blog/material-is-compose-first): Compose direction and Material Views maintenance status; not a requirement to adopt every new API.
- [Exact BOM 2025.09.01 POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.09.01/compose-bom-2025.09.01.pom) and [BOM guidance](https://developer.android.com/develop/ui/compose/bom): verified dependency mapping and compiler/BOM distinction.
- [State saving](https://developer.android.com/develop/ui/compose/state-saving): preserve small route/draft state, not the entire workspace in a saved-state bundle.
- [Predictive back progress](https://developer.android.com/develop/ui/compose/system/predictive-back-progress) and [Activity 1.8.0 release notes](https://developer.android.com/jetpack/androidx/releases/activity#1.8.0): gesture handling and API-introduction caveat.
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16): target-36 runtime adaptations.
- [Android 14 nonlinear font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling) and [Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing): 200% text and manual assistive-technology verification.
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) and [backup configuration](https://developer.android.com/identity/data/autobackup): user-selected transfers versus platform backup.
- [AtomicFile concurrency contract](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/AtomicFile.java#L36-L49) and [finish implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/AtomicFile.java#L166-L188): atomic replacement limits and verification rationale.
- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) and [Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html): record build caveats rather than assuming every pinned combination is vendor-tested.

## Product and Scheduling Contract

This section is normative for the proposed implementation. Tests must establish these rules before UI wiring relies on them.

### 1. Schedule and history model

- A dated habit configuration consists of its numeric target, active/paused/archived status, and schedule. Extend the existing dated-version model rather than reading the current settings when calculating earlier days.
- Schedule kinds are daily, selected weekdays, and weekly quota. Selected weekdays must be a nonempty set; weekly N must be an integer from 1 through 7.
- Every historical version gains an explicit schedule. Existing workspaces migrate to daily schedules for all existing dated versions.
- Configuration lookup uses the latest effective version on or before a requested date. Pre-creation dates are not applicable.
- Name, description, icon, and accent edits are presentational; they do not invalidate a weekly period. Target, schedule, or status edits can affect assessment.
- Keep the existing three-decimal precision and positive numeric bounds unless a regression proves they are wrong. Do not change units after progress history has been recorded.
- Store workspace-level week-definition history separately from habit target/status/schedule versions. Historical weekly queries resolve the convention effective for that period, not the current Settings value.

### 2. Daily progress and eligibility

- Sum all valid commits for a habit/date; compare the sum with that date's target.
- Partial progress is retained. The visual completion ratio caps at 100%, while the actual logged amount may exceed the target.
- “Complete remaining goal” adds only the positive remainder and does nothing when the target is already met.
- Daily and weekday schedules have obligations only on active, eligible dates. Today is pending until it closes, not failed merely because the target is unfinished.
- A past eligible date below target is missed; pre-creation, paused, archived, and nonselected weekday dates are not applicable.
- Optional progress on an active habit's nonselected weekday may be logged from its detail screen. It is extra activity, not an obligation or credit for another date.
- Paused/archived habits reject new commits. Historical corrections/deletions remain available and recompute actual progress without altering dated expectations.

### 3. Weekly quota assessment

- Any distinct date in the calendar week can qualify when the habit is active and its accumulated progress reaches that date's target.
- Each date contributes zero or one qualifying day. Three qualifying commits on Monday are still one qualifying day.
- Partial days do not count toward N. Additional qualifying days are retained, but quota progress caps at N for completion display.
- Before a stable, nontransitional week closes: show the count, target N, and pending or achieved status. Never show failure while the week is still open. Transitional assessment takes precedence over both labels, as specified below.
- After an uninterrupted full week closes: achieved if at least N dates qualified; missed otherwise.
- Do not carry surplus days into a later week or credit future dates.
- Weekly habits are not seven separate daily obligations. Once the weekly quota is met, remove them from the default “remaining” group while keeping optional logging available.
- Resolve weekly bounds through the configured, dated week-definition policy. A pending preference change does not alter the current ordinary week's bounds, quota, or eligibility.

### 4. Transitional weeks and edits

- A weekly assessment is scored only if the weekly configuration was applicable for the full period: the habit existed, remained active, and had one consistent weekly quota and daily target throughout that week.
- A short bridge between different workspace week-start conventions is also unscored for weekly quotas. Daily and selected-weekday obligations continue normally during that bridge; see section 8.
- Creation after the week's first day, pause/archive/resume during the week, or a target/schedule change during the week makes that week **transitional / unscored**. Keep all recorded progress visible; do not call the week a success or failure in adherence statistics.
- **State precedence:** transitional periods remain unscored even if recorded completions reach a nominal quota. On Today, put these active habits in an optional “Partial-week practice” group, showing completed dates and the usual future weekly target without implying a full-week obligation. Do not combine competing configurations into an authoritative x/N score for that transitional week.
- Changes effective on the first day of a week can establish the configuration for that full week. A redundant save with no substantive change must not create a fake transition.
- Evaluate the effective per-date configuration history, not an intraday action log. If a same-day change is fully reverted, it must not leave a permanent transition flag unsupported by that history.
- Daily and selected-weekday assessments still use each date's effective configuration; an edit does not erase completed or missed earlier dates.
- Same-day changes replace that date's configuration, matching the existing model. Preserve all fields not deliberately edited.
- Corrections and successful Undo may change an achievement result. “Preserve history” means preserving dated expectations, not freezing erroneous totals forever.
- If the device date moves backward behind the newest effective configuration, do not silently overwrite the version chain. Block conflicting configuration edits with an explanation; never rewrite existing commit dates.

### 5. Time and freshness

- Resolve “today” through an injectable clock/calendar service, not scattered independent calls inside UI and domain logic.
- Refresh visible date-dependent state on launch/resume, local midnight while foregrounded, and relevant timezone/date changes.
- Capture one operation date inside the serialized mutation boundary and use it for validation, mutation, notices, and Undo metadata. A midnight boundary must not split a single operation across two dates.
- Store ISO local dates and timestamps separately. The timestamp orders activity; the stored date determines progress credit.
- Week grouping uses an explicit start/end date, including weeks crossing month/year boundaries. No assumption that a calendar week belongs entirely to one year.
- Week-start changes activate on their stored boundary dates even if the app is closed at the time. Reopening resolves those original dates; it must not postpone activation until launch. No background job or notification service is needed.
- A pending future week-start request is not a clock-rollback violation and must not block otherwise valid current-day logging or habit edits.

### 6. Metrics and heatmaps

- **Daily target progress:** a habit/date's logged amount and capped ratio.
- **Weekly quota progress:** qualifying distinct dates out of N for that habit/week.
- **Activity streak:** consecutive calendar dates with any logged progress, retaining the existing definition and unfinished-today behavior. Nonselected weekdays may break this activity streak; they must not be described as failures of schedule adherence.
- **Adherence:** assessed eligible dates for daily/weekday habits and assessed full weeks for weekly habits. Exclude pending and transitional periods from the corresponding denominator. Do not combine unlike day/week rates into one unexplained percentage.
- **Proposed overall chart adjustment:** use the overview contribution graph to show recorded activity (progress-bearing commit count by date), with an explicit legend and exact counts in the date explorer. It is not an overall success score. Per-repo charts retain numeric daily progress and schedule-aware states. This is an intentional UI semantics change from the prototype's cross-repo daily average, not a migration of stored amounts.
- Distinguish no activity, not scheduled, pending, partial, complete, and transitional states in accessible text. Color alone is insufficient. A weekly habit with no log on Tuesday must not appear as a failed Tuesday.
- Today, Insights, quota summaries, and historical week inspection share one period resolver. Calendar weekday ordering can follow the current preference, but a visual reorder must never change a saved date or a historically assessed period. Explicitly label historical/bridge period bounds rather than silently regrouping old weekly results.

### 7. Persistence, compatibility, and transfer policy

- The new workspace format is **version 2**, including the default/effective week-start convention, historical transitions, and any pending preference change needed to reproduce period boundaries. Extend this planned format rather than inventing a v3 for an unimplemented schema. Decode by explicit version: v1 migrates, v2 validates, unknown versions enter recovery/rejection without mutation.
- Use one storage owner/lock for initial loading, recovery-capable reads, mutations, verification, migration, reset, and replacement. Exports may use an immutable committed in-memory snapshot; they must not open an unsynchronized `AtomicFile` reader during a write.
- Prepare the candidate state and any Undo inverse before writing. Failure in later UI feedback must not be reported as failure of an already committed write.
- After writing, verify the committed file within the same ownership boundary against the candidate before publishing a new committed snapshot. Keep the last-known-good copy available for a failed verification. A retained recovery copy is a safeguard, not a guarantee against every device/storage failure.
- Distinguish **verified new state**, **verified previous state after rollback**, and **indeterminate storage outcome**. If verification and recovery both fail, enter a non-writable recovery state and preserve every available copy. Do not promise rollback or blindly retry a mutation/Undo whose on-disk outcome is unknown; reconcile the readable file with the candidate/previous snapshot first.
- Imports are replace-only. A preview is tied to a workspace revision; if edits occur before confirmation, refresh the preview and obtain confirmation again rather than replacing newly changed data under stale consent.
- Bind confirmation to the exact validated import candidate as well as the active revision, using retained bytes/snapshot and a content fingerprint rather than rereading a potentially changed provider document after consent. Clear routes, drafts, selections, and Undo only after verified replacement. A cancelled/failed replacement retains them unless a later verified reconciliation establishes that replacement actually committed.
- **Proposed initial resource bounds:** 32 MiB UTF-8 JSON input/output, 1,000 habits, 100,000 commits, 100,000 total dated configuration records including week-definition records, 128-character IDs, and structural nesting no deeper than 8. Enforce the same supported-capacity contract on new saves and imports; show a capacity error rather than silently truncate. Validate these bounds against the Task 1 fixtures and Task 13 device-memory measurements before freezing them for v2.
- Legacy files exceeding a new bound or containing an otherwise incompatible record remain recoverable and unmodified; do not silently discard records. Raise a bound deliberately if a legitimate existing workspace requires it and the device can support it.
- Preserve valid legacy unsorted versions by resolving/sorting for calculations without losing dates. Do not reject an existing commit solely because a later same-day status edit made its date paused/archived. Do not infer dates from timestamps or force exact date/timezone agreement.
- New-input validation and migration compatibility are different concerns. Existing string values accepted by v1 should not be silently rewritten merely because the new UI trims newly entered strings.
- Missing storage and explicitly confirmed reset/start-over produce empty habit/commit collections, never sample records. Loading failure stays an error, not fabricated or writable empty data. Migration/import preserves the validated records supplied; do not seed missing content or filter sample-looking IDs/names. Section 9 defines the real-data and fixture-isolation contract.
- Android document-provider destinations are not guaranteed to support atomic file replacement. On export failure report that the chosen external file may be incomplete and offer retry; never claim that app-private `AtomicFile` semantics apply to arbitrary provider URIs.

### 8. Settings: configurable start of the week

**User-facing behavior**

- Add **Settings → Calendar & scheduling → Week starts on**, with a labeled single-choice control for Monday through Sunday. Use full localized weekday names, not ambiguous single-letter labels.
- Default to Monday for a fresh workspace, a reset workspace, and migrated v1 history. A valid restored v2 workspace brings its own convention/history/pending request.
- Show the currently effective convention and, when different, the pending requested weekday with exact transition and projected first-full-week dates. During a bridge, label its fixed date range separately and update the projected first-full-week date if another change is queued. Use an explicit Save action when changing an existing workspace's convention; do not imply that a delayed choice has already taken effect.
- If there are no habits, no dated expectations, and no activity/history, apply the choice immediately. Having zero commits alone does not qualify as an empty workspace: missed historical obligations still count as history.
- Keep this app-wide. Reordering weekday choices in a habit form must not change the identities of already selected weekdays. Daily targets, stored commit dates, amounts, activity streaks, and historical expectations remain unchanged.

**Future-effective change policy**

1. Preserve the current period and all earlier periods. Let **B** be the next boundary ending the current period under its existing convention.
2. Let **T** be the first occurrence of the newly requested weekday on or after B.
3. At **B**, the requested weekday becomes the effective preference for weekday ordering and future full weeks. The half-open interval **[B, T)** is a fixed short bridge, not a regular week under either convention. If nonempty, it is neutral/unscored for weekly quotas only; keep all progress visible and do not prorate or carry its qualifying dates forward.
4. The first complete new week is **[T, T + 7 days)**, unless another preference change is queued during the bridge; subsequent complete weeks follow the effective weekday. A queued change may instead start another bridge at T, in which case update the projected first-full-week date.
5. Saving the unchanged effective weekday is a no-op when no pending request exists; otherwise it cancels only that pending request. A genuine different-weekday change produces a 1–6-day bridge; no date belongs to two periods or disappears from activity history.
6. Before B, a revised selection replaces the pending request without moving B. Selecting the currently effective weekday cancels that pending request.
7. Once a bridge has begun, its dates and destination weekday are fixed. A further preference change queues at that bridge's end using the same boundary rule; it may create another short bridge, but cannot redraw elapsed periods. Compare no-op/cancellation choices with the now-effective bridge destination, not the previous convention. A queued request can be replaced/cancelled before the bridge ends without changing the active bridge.
8. If the app is closed across one or more boundaries, resolve and retain the transition on its originally scheduled dates. Save/restore must preserve enough history and pending information for identical results after restart.

**Examples**

- **Monday → Sunday:** the existing Monday–Sunday week completes unchanged; the following Monday–Saturday is an unscored weekly bridge; the first full Sunday–Saturday week begins next Sunday.
- **Sunday → Monday:** the existing Sunday–Saturday week completes unchanged; the following Sunday is an unscored weekly bridge; the first full Monday–Sunday week begins Monday.
- **Change back during a Monday → Sunday bridge:** Sunday is already the effective preference. Selecting Monday queues a return at the fixed bridge's Sunday end, creating a one-day Sunday bridge before full Monday-start weeks. Selecting Sunday again before that boundary cancels only the queued return; it does not cancel or shorten the active bridge. The simple examples above assume no additional queued change.
- In either bridge, a daily habit or a selected-weekday habit remains due on its normal eligible dates. Only weekly-quota assessment is neutral.

**Storage and error behavior**

- Process preference changes through the same serialized, date-captured, verified-write command path as other settings. Do not publish a pending choice before it is saved.
- Validate weekday values and an ordered, nonoverlapping period/transition history; distinguish the replaceable future request from the locked active bridge and completed transitions. Retain currently effective history when replacing or cancelling a pending request.
- Do not offer generic snackbar Undo for this preference change. Provide explicit pending-change replacement/cancellation; once a period begins, changes follow the next-boundary rule. This avoids retroactively undoing calendar membership.
- Failed save with verified rollback leaves effective/pending values unchanged. An indeterminate save follows the existing non-writable recovery policy rather than guessing which calendar is active.

### 9. Real-data-only runtime and isolated verification

- **No shipped fixtures:** remove sample/mock datasets, seed generators, fake profile defaults, sample entry controls, and hidden fallback/preview seeding paths from app-owned runtime code/resources. This applies to every delivered APK variant, including debug, and to an AAB if one is later delivered. Hiding controls or relying on shrinking is insufficient.
- **Empty means empty:** a fresh or explicitly reset workspace contains zero habits/commits and an unset name/bio. Onboarding, help, navigation, preference changes, opening/cancelling forms, resume/recreation, date changes, and recovery retries must not create habit/activity records. Only explicit create/log/correction/Undo actions or confirmed user-selected restore can change that history according to their contracts.
- **Optional profile:** use empty strings as the planned v2 representation of an unset name/bio. Accept an empty display name; otherwise retain the existing maximum length of 40 characters and bio limit of 160. Render a neutral greeting/avatar and optional profile fields without inventing a person or biography. Preserve valid v1 profile values verbatim through migration, even when they match old defaults; only explicit user edits change them.
- **Honest forms and metrics:** planning default is blank name/description/target/unit input for a new repo, with neutral instructional labels rather than a prefilled reading habit. Normal configuration defaults such as theme, week start, schedule, icon, and accent are not mock data and remain allowed. Do not persist a repo until valid user input is submitted. Empty history shows zero activity and an unavailable adherence value when its denominator is empty, not invented progress, successful streaks, or positive chart bars. Loading/recovery errors remain distinct from valid zero-data states.
- **Preserve existing workspaces:** this is removal of shipped data/code, not permission to purge installed data. Keep all valid records during upgrade/import, including edited or mixed `sample-*` histories and default-looking profiles. Never infer deletability from IDs, names, timestamps, or similarity to fixtures. Existing explicitly confirmed reset/replacement remains available with preservation; no new automatic cleanup or demo-origin classifier is required.
- **Test source isolation:** retain deterministic generators/fixtures only in `android/app/src/test/...` and `android/app/src/androidTest/...`. For this scope, use a unit-test fixture and a separate small instrumented fixture factory; no shared runtime/debug fixture module is needed. Test helpers may construct workspaces using production domain APIs, but the installed app must have no seed command or test-only entry point.
- **Device isolation:** instrumented, screenshot, migration, and performance tests may populate only an explicitly provisioned disposable installation. Before any target-context write/reset, verify the intended disposable device/package and explicit test-only opt-in; fail closed when absent or uncertain. Do not run destructive suites on the owner's live installation. Apply this before the baseline as well as final tests, and serialize test setup with app startup/storage access.
- **Evidence, not bundled content:** synthetic screenshots and fixtures may remain in the source repository's test/documentation areas, clearly labeled as test evidence. Do not copy them into app assets, an in-app demo, or a prepopulated distributable backup. Final verification includes an unseeded cold start and a user-driven first-repo/first-commit flow, plus source-set and delivered-artifact inspection proving test fixtures are excluded.

## Work Breakdown

**Path convention:** full `android/...` paths are workspace-relative. In task file lists, `domain/`, `data/`, `ui/`, and standalone production Kotlin filenames are shorthand relative to `android/app/src/main/java/com/githabit/prototype/`. Named existing tests refer to the exact test directories in Research Summary. Any path marked **new** is proposed, not already present. Each task includes its own verification; tests are not postponed until the final task.

Each completed task should leave the native app buildable. Where a new schema/model/theme interface precedes its UI migration, retain a small explicit compatibility adapter/default until the consumer task removes it; do not create a broken intermediate build that depends on later unrelated work. Intermediate builds are not real-use handoffs: remove all demo compatibility paths and pass the fixture-free artifact gate before delivery.

### Task 1: Establish a reproducible baseline and freeze the schedule contract

- **Description**:
  - Confirm the current native build configuration, applicable repository instructions, installed Android/JDK tools, and test prerequisites without changing versions first.
  - Preserve the current source and any personal workspace before changing data formats. Use disposable test data/devices; do not reset the user's active installation.
  - Before any connected baseline run, add the test-only fail-closed guard for `GitHabitFlowTest` setup: explicitly selected/provisioned disposable device and target package, plus opt-in checked before any target-context write. Abort rather than overwrite when isolation is unverified. Record this safety-only harness change separately from baseline results.
  - Run and record the existing unit, lint, debug build, and available connected-test baseline. Distinguish fresh results from the reports already in the tree.
  - Record the week-start setting contract: all seven weekday choices, Monday initialization, dated future-effective changes, neutral weekly bridges, and pending replacement/cancellation. Review the other explicit assumptions: transitional habit periods, today-only new logging, and overview activity-chart semantics. Apply them unless the owner changes one before work begins.
  - Create a small test-only fixture set of v1 empty, populated, signed-out, light-theme, dark-theme, paused/archived, historical-target, and mixed/edited `sample-*` workspaces with default-looking profiles. Include a deliberately corrupt file and unsupported future-format file. Store payloads only in unit/instrumented test resources, never in runtime/debug assets.
  - Make only dependency upgrades needed by a chosen feature or a demonstrated compatibility/security issue; pin and record the versions actually used.
- **Files/Components**: `android/README.md`; existing Gradle configuration; existing unit/instrumented test files, especially `GitHabitFlowTest.kt`; `android/docs/schedule-rules.md` (**new**); `android/docs/verification.md` (**new**); `android/app/src/test/resources/fixtures/` and `android/app/src/androidTest/assets/fixtures/` (**new, test-only**).
- **Dependencies**: None.
- **Acceptance Criteria**:
  - Fresh baseline results, environment, date, and fixture inventory are recorded, or unavailable checks are explicitly marked unverified with the missing prerequisite.
  - All existing workspace data, including mixed or uncertain-origin history, has a verified preservation copy outside destructive test paths.
  - Missing/incorrect isolation prerequisites cause instrumentation setup to abort before writing to the target app. Successful destructive baseline runs identify their disposable device and test-only fixture sources.
  - The scheduling contract has no unresolved implementation-blocking alternatives.
  - No toolchain replacement, package-ID change, or web-prototype edit has occurred merely to start this work.
- **Estimated Complexity**: Small.

### Task 2: Prevent data loss during loading, writing, and recovery

- **Description**:
  - Replace the `ready` Boolean's implicit error behavior with explicit loading, ready, empty/new, recoverable-read-error, and unsupported-format outcomes. An unreadable file must never be treated as permission to start overwriting a new workspace.
  - Permit ordinary mutations only in an explicitly writable state. Distinguish a genuinely missing file from an existing unreadable one.
  - Preserve unreadable originals and a last-known-good snapshot before destructive replacement/migration. Keep recovery copies app-private and exclude them from automatic backup, like the main workspace.
  - Retain `AtomicFile` rollback and serialized mutation ordering. Publish new in-memory state and success feedback only after persistence succeeds.
  - Introduce a small injected persistence interface/file-access seam for fault tests, not a general repository framework. Serialize initial/recovery reads and post-write verification with writes; follow the read-back and capacity policy above.
  - Add a minimal recovery screen with Retry, save a recovery copy, and an explicitly confirmed start-over/restore route. Cancellation is non-destructive.
  - Keep the current persistence mechanism unless testing establishes a reason to change it; atomicity plus correct state policy is the immediate requirement.
- **Files/Components**: `data/WorkspaceStore.kt`, `data/WorkspaceJson.kt`, `GitHabitViewModel.kt`, `ui/GitHabitApp.kt`, `ui/Screens.kt` under the native source root; `data/WorkspaceLoadResult.kt` (**new**, if needed); storage/recovery tests (**new**).
- **Dependencies**: Task 1.
- **Acceptance Criteria**:
  - Missing-file startup creates zero habits/commits; corrupt/unsupported-file startup cannot trigger normal writes through onboarding, theme changes, logging, or sample fallback. The unset v2 profile is completed by Task 4 and its UI by Task 7, not a hidden dependency of storage-safety work.
  - Read failure followed by every exposed recovery action preserves the original unless the user explicitly confirms replacement and a preservation copy has succeeded.
  - Injected ordinary write failure with successful verified rollback leaves the prior disk/committed state intact and reports an actionable error.
  - Injected failure of write verification and rollback enters a non-writable recovery state, preserves available copies, and does not claim either a committed change or a successful rollback.
  - No success snackbar appears before verified commitment; restart/retry reconciles the readable state rather than blindly replaying a command with an unknown outcome.
- **Estimated Complexity**: Medium.

### Task 3: Implement pure, schedule-aware progress and period calculations

- **Description**:
  - Add the three schedule types to dated habit configurations and validated habit drafts; keep schedule evaluation independent of Compose and Android storage.
  - Separate numeric progress, date eligibility, weekly quota assessment, activity streak, and adherence results. Prefer explicit result types over a nullable percentage with several meanings.
  - Provide queries for a date summary, a calendar-week summary, and the ordered Today groups. Include pending, achieved, missed, not-applicable, and transitional states where relevant.
  - Centralize calendar-week bounds and use injected clock/date inputs for deterministic tests.
  - Add a pure workspace week-period resolver covering all seven conventions, preserved historical periods, pending preference changes, and short bridges. Keep it separate from per-habit schedule eligibility; test all 49 old/new weekday pairs, including unchanged saves.
  - Index commit amounts/counts by habit and stored date for an immutable workspace snapshot. Reuse that index across graphs and summaries rather than rescanning the entire history for every cell.
  - Ensure no-op edits do not add spurious configuration transitions and preserve unrelated fields on same-day updates.
- **Files/Components**: `domain/Habits.kt`; `domain/Schedules.kt` and `domain/ProgressAnalytics.kt` (**new**); `HabitsTest.kt`; `SchedulesTest.kt` and `ProgressAnalyticsTest.kt` under `android/app/src/test/java/com/githabit/prototype/domain/` (**new**).
- **Dependencies**: Task 1.
- **Acceptance Criteria**:
  - Every pure scheduling/analytics rule in contract sections 1–6 and the pure period-resolution rules of section 8 is represented by at least one deterministic unit test. Storage, command persistence, lifecycle, transfer, and UI rules are tested by their owning later tasks, not made hidden prerequisites of this task.
  - N=1 and N=7, partial accumulation, excess logging, repeated same-date commits, selected weekdays, and empty/paused/pre-creation dates yield the defined results.
  - Weekly results use dated configured calendar bounds, not `daysEnding(today, 7)` or a hard-coded Sunday alignment. Sunday is valid when explicitly selected.
  - Every date belongs to exactly one resolved period. Requesting a change leaves the current ordinary week intact; bridges have 1–6 days for changed weekdays, and only weekly-quota assessment becomes neutral.
  - Midweek creation/configuration/status transitions are neutral; a complete stable week is scored; an open week is not failed.
  - Historical corrections below/above target update achievement without changing stored dated expectations.
  - Mixed units are never summed, and day-based adherence is not averaged with week-based adherence.
- **Estimated Complexity**: Large.

### Task 4: Add backward-compatible schema migration and strict validation

- **Description**:
  - Introduce explicit **workspace format v2**, including dated habit schedules, workspace week-definition history/pending preference changes, local onboarding state, unset optional profile support, and `SYSTEM/LIGHT/DARK` preference.
  - Implement a v1 migration that preserves habit/commit IDs, local dates, timestamps, amounts, notes, historical target/status versions, profile fields, and explicit theme intent.
  - Change new/reset model defaults to empty name/bio and update v2 validation to allow an unset profile. Keep populated legacy profile values, sample-looking IDs, and mixed histories unchanged; do not add seed detection, purge rules, or data generation to migration. Adapt any interim name-dependent rendering safely until Task 7 completes the optional-profile UI.
  - Assign daily schedules to every legacy configuration version. Map legacy `darkTheme=true` to DARK and `false` to LIGHT; do not replace an existing preference with SYSTEM.
  - Initialize fresh/v1 calendar history with Monday and no pending request. Preserve the complete week-definition history and pending choice on v2 round trips/imports; do not derive them from the device locale.
  - Remove simulated identity from new state. Existing populated workspaces must open regardless of legacy `signedIn`; migrate onboarding independently of that flag.
  - Keep only the temporary runtime compatibility needed by the still-existing UI until Task 7, without emitting identity fields into v2 or making legacy sign-in a condition of data access. Remove those adapters with the local-entry UI change.
  - Validate enum values, weekday sets, quota range, date ordering/duplicate effective dates, identifiers/references, finite numeric amounts, strings, and reasonable resource bounds before acceptance. Reject invalid data; never silently drop or repair records without a documented migration rule.
  - Implement the explicit v2 capacity contract with pre-parse byte/depth protection and post-parse collection validation. Distinguish incompatible legacy data from corrupt data without modifying either. Keep v1's legitimate unsorted versions and retained same-day paused commits compatible.
  - Use the recovery-safe write policy to persist migration only after full validation and preservation of the original. Reject unknown newer formats without resetting them.
- **Files/Components**: `domain/Habits.kt`, `domain/Schedules.kt` (**new** from Task 3), `data/WorkspaceJson.kt`, `data/WorkspaceStore.kt`, `ui/Screens.kt` (interim unset-profile safety if needed), `WorkspaceJsonTest.kt`; legacy/new-format fixtures from Task 1.
- **Dependencies**: Tasks 2 and 3.
- **Acceptance Criteria**:
  - Every valid v1 fixture migrates deterministically; domain history before migration equals history afterward under daily scheduling. Corrupt/unsupported negative fixtures fail safely rather than being forced through migration.
  - A migrated signed-out populated workspace is usable without sign-in and without sample-data replacement.
  - Fresh v2 empty profiles round-trip successfully; empty v1 history stays empty, and mixed/edited legacy sample-like histories and profile values remain intact. Repeated migration/startup adds or removes no habit/commit records.
  - New-format encode/decode is lossless for all schedule and preference variants.
  - Valid and invalid week-definition/pending records have fixture coverage; defaults, historical boundaries, cancellation, and a scheduled change survive restart and export/import without regrouping dates.
  - Duplicate IDs/dates, empty weekday selections, invalid quotas, orphan commits, nonfinite amounts, corrupt files, and unsupported versions fail safely.
  - Failed migration keeps a recoverable v1 original; successful migration does not rerun destructively on the next launch.
  - Read/save/export/import limits are aligned so an ordinarily saved/exported supported workspace can be restored without truncation.
- **Estimated Complexity**: Large.

### Task 5: Make application commands date-consistent and Undo failure-safe

- **Description**:
  - Route schedule-aware create/edit/log/complete/pause/archive/resume/correct/delete commands through the existing serialized mutation coordinator.
  - Capture the operation's effective local date once inside the mutation boundary; use it consistently even if the clock crosses midnight while saving.
  - Extract clock/calendar access behind a small injectable dependency; refresh snapshots on launch/resume and foreground date/timezone changes without a background notification service.
  - Revalidate a stale open form at submission. If midnight changes its intended date, show the actual date being saved and avoid a silent mismatch between displayed and committed progress.
  - Add the serialized week-start preference command using section 8's immediate-empty/future-effective rules. Revalidate its boundary at save, persist pending replacement/cancellation atomically, and resolve changes crossed while the app was closed on their original dates.
  - Retain Undo tokens until their reverse mutation has persisted successfully. A failed Undo remains retryable; invalidated Undo is explained rather than silently lost.
  - Store sufficient operation context for precise reversal. Schedule/status Undo must not erase a later unrelated target or schedule change, and old Undo must not resurrect data after reset/import.
  - Use typed, ID-based Undo records instead of opaque closures where necessary for conflict validation. Keep the existing bounded, in-memory Undo scope; do not add persistent multi-level Undo or redo. A no-op completion/removal must not generate a misleading success/reversal token.
  - Reject duplicate in-flight submissions or make their command IDs idempotent; do not rely only on temporarily disabling a button.
- **Files/Components**: `GitHabitViewModel.kt`, `MainActivity.kt`, domain mutation functions; `domain/CalendarContext.kt` (**new**, name may be adjusted to project conventions); ViewModel/command tests (**new**).
- **Dependencies**: Task 4.
- **Acceptance Criteria**:
  - Midnight between click and persistence produces one operation on one documented date, not mismatched state/Undo dates.
  - DST, leap days, timezone travel, clock rollback, resume after several days, and week/year boundaries have deterministic tests.
  - Failed Undo with verified rollback retains a usable reversal opportunity; an indeterminate outcome blocks blind retries until reconciliation; successful Undo affects only its intended operation.
  - Corrections/deletion recompute affected weekly results. Command-level tests verify reset/replacement invalidation hooks using injected candidates; Task 12 owns the full document-transfer flow and Task 14 its end-to-end coverage.
  - Rapid repeated Complete taps do not add a second full target or create duplicate day credit.
  - Preference saves do not change completed/current ordinary weeks; no-op saves, pending replacement/cancellation, bridge-time changes, missed-boundary restarts, and failed writes have command tests. A future pending calendar record does not block ordinary current-day mutations.
- **Estimated Complexity**: Large.

### Task 6: Establish a compact Material 3 design contract and reusable tokens

- **Description**:
  - Define the app's color roles, surface levels, typography roles, shape categories, spacing scale, and routine interaction/motion behavior in a small centralized theme layer.
  - Complete relevant light/dark role pairings; retain separately named semantic palettes for habit accents and heatmap data. Do not force categorical data into unrelated error/action roles.
  - Use explicit contrast targets: at least 4.5:1 for normal text, 3:1 for large text, and 3:1 for meaningful nontext controls/icons and visible focus indicators against relevant adjacent colors. Decorative separators need not be treated as essential control boundaries. Record the actual pairings tested.
  - Keep the GitHabit green identity, but verify actual text/icon/focus combinations in both themes. Avoid claiming that generated colors alone prove contrast.
  - Replace repeated ad hoc component sizes/radii/type treatments with theme/component roles as touched screens are updated. Document necessary chart geometry and other intentional exceptions.
  - Use baseline M3 buttons, fields, selection controls, dialogs/sheets, and state feedback. Reserve a clearly labeled dominant action for the current task.
  - Resolve SYSTEM/LIGHT/DARK into one effective theme used by content and system bars; align startup resources with the selected behavior as far as platform capabilities allow.
- **Files/Components**: `ui/Theme.kt`, `ui/Components.kt`, `ui/GitHabitApp.kt`; `ui/DesignTokens.kt` (**new**, if needed); `res/values/themes.xml`; night resource variants (**new**, where appropriate); `android/docs/design-contract.md` (**new**).
- **Dependencies**: Task 1. The persisted preference is connected after Task 4.
- **Acceptance Criteria**:
  - Theme/token reference lists the source role for recurring styles and meaningful chart colors.
  - Real rendered foreground/background pairs meet the stated contrast targets, including secondary text and error/focus states.
  - Ordinary interactive components retain standard feedback, semantics, and minimum hit targets.
  - Existing custom brand typography is evaluated as a tokenized design choice, not rejected solely for differing from default Roboto measurements.
  - Motion remains nonessential and respects system settings; adopting Expressive is not a dependency.
- **Estimated Complexity**: Medium.

### Task 7: Remove shipped demo data and add real local entry/preferences

- **Description**:
  - Remove Google/email/password entry, simulated sign-in/sign-out commands, and account-security implications from the native app.
  - Provide a short local welcome that explains repos/habits and commits/progress once, then offers Create first repo. Remove both sample walkthrough buttons, `explore-demo`, `GitHabitViewModel.explore()`, and all runtime seed paths; there is no replacement demo mode.
  - Remove `SampleWorkspace` from production `domain/Habits.kt`. Move deterministic unit-test data generation into a test-only file and give instrumentation a separate fixture factory. Update all generator imports, the instrumentation `sample()` helper, sample-ID selectors, and auth-based setup together so tests remain buildable without production seeding. Seed disposable test workspaces only before app launch through test-owned setup, never an installed-app hook.
  - Retain optional display-name/bio preferences without presenting them as an account. Complete `saveProfile`/Settings/greeting/avatar support for Task 4's empty defaults; remove fabricated profile/description fallback copy without rewriting existing user values. Resetting or replacing history remains separately confirmed and preserved.
  - Remove misleading demo/prototype user-facing copy and the `1.0-demo` version label; choose and record the next non-demo local version and appropriate version code while preserving the application ID and installed-data compatibility. Do not rename the Kotlin package merely to remove a UI label.
  - Add System/Light/Dark selection; use SYSTEM for a fresh workspace while preserving migrated explicit preferences.
  - Add **Calendar & scheduling → Week starts on** with all seven weekdays, current/pending values, an explicit save action, and exact effective/bridge-date explanations. Offer cancellation/replacement of a request that has not started, following the domain contract rather than recalculating periods inside Settings.
  - Add clear first-run, empty-workspace, loading, and recovery states. Existing populated users bypass promotional onboarding.
- **Files/Components**: `ui/Screens.kt`, `ui/GitHabitApp.kt`, `ui/Components.kt`, `GitHabitViewModel.kt`, `domain/Habits.kt`, `res/values/strings.xml`, `android/app/build.gradle.kts`; existing `HabitsTest.kt`, `WorkspaceJsonTest.kt`, and `GitHabitFlowTest.kt`; `android/app/src/test/java/com/githabit/prototype/domain/SampleWorkspace.kt` and `android/app/src/androidTest/java/com/githabit/prototype/fixtures/WorkspaceFixtures.kt` (**new, test-only**).
- **Dependencies**: Tasks 4, 5, and 6.
- **Acceptance Criteria**:
  - A new user can create and log a habit without credentials, network access, or a compulsory multi-page introduction.
  - Existing data opens even if the old workspace was signed out.
  - A clean install opens with no habits/commits or invented profile. No visible/hidden sample entry, generator, fixture asset, or fallback seed path remains in runtime sources, including debug. All affected unit/instrumented tests compile and use only test-owned fixture generation.
  - Completing onboarding, visiting help/screens, changing preferences, and recreating/restarting the empty app leave habit/commit collections empty. Saving/clearing an optional profile works without creating history.
  - Theme selection persists and reacts correctly to a system-theme change in SYSTEM mode.
  - Week-start selection is operable with TalkBack/keyboard, persists after restart, and displays the actual current convention plus any pending change. Fresh/v1 default Monday; genuinely empty-workspace changes can apply immediately; failed saves leave the prior setting intact or enter explicit recovery.
  - Credential-related UI/test tags and misleading sign-out/demo-account copy are removed or replaced coherently.
- **Estimated Complexity**: Large.

### Task 8: Adapt the shell and preserve predictable navigation state

- **Description**:
  - Retain the existing destination vocabulary and small routing structure. Do not introduce a navigation framework solely for six destinations.
  - Use a supported M3 adaptive navigation component, or its documented equivalent for the pinned stack, to select bar/rail by window constraints and posture. Add the compatible adaptive artifact only if required.
  - Remove the global 720dp single-column constraint from the shell. Apply readable content bounds per destination.
  - Separate repo-list and detail content from full-screen wrappers so they can share an expanded list/detail layout while preserving compact list-to-detail behavior.
  - Preserve selected repo, top-level destination, filters, list/detail scroll state, and a small serializable back-stack snapshot through supported saved-state mechanisms. A missing/deleted selected repo returns to a safe list state.
  - Define Back behavior for overlays, detail, secondary screens, and root. Preserve standard modal handling; verify route changes and cancellation on supported predictive-back configurations using the pinned Activity/Compose APIs. Do not claim predictive gesture support from a Back button test alone.
  - For a custom route preview, use `PredictiveBackHandler` consistently in composition with an explicit enabled condition. At root, yield to system navigation; do not intercept the system's exit preview. Store only small route/draft state in saved state and reload the actual workspace from disk.
  - Keep navigation labels visible and avoid duplicated semantic/test IDs in two-pane layouts.
- **Files/Components**: `ui/GitHabitApp.kt`, `GitHabitViewModel.kt`, `ui/Screens.kt`, `ui/Sheets.kt`, `MainActivity.kt`, `android/app/build.gradle.kts`; saved-state/navigation tests.
- **Dependencies**: Tasks 5 and 6. Coordinate shared shell edits with Task 7; onboarding completion is not a functional prerequisite.
- **Acceptance Criteria**:
  - Compact portrait, short landscape, split-window, and expanded layouts expose usable navigation and content.
  - Resizing between list-only and list/detail preserves selection and working context.
  - Back dismisses the topmost modal before navigating; cancellation does not commit a pop; committed Back pops exactly once.
  - Activity recreation and tested process recreation restore the intended safe route rather than losing the workspace or opening stale detail.
  - No mandatory drawer is introduced solely to match a table in the uploaded documents.
- **Estimated Complexity**: Large.

### Task 9: Add accessible schedule controls to repo creation and editing

- **Description**:
  - Add exactly three schedule choices to the repo form using standard labeled selection controls.
  - Start new repo name/description/target/unit fields empty; replace the reading preset and sample note prompts with neutral instructions. Keep intentional schedule/icon/accent defaults and existing edit values. No form opening, validation failure, or cancellation persists a record or placeholder content.
  - For selected weekdays, show a keyboard/TalkBack-operable multi-selection with full accessible weekday names; require at least one day.
  - Order weekday choices using the current app convention without changing selected weekday identities. Make weekly schedule summaries refer to the resolved period bounds and identify any active calendar bridge.
  - For N/week, select an integer 1–7 and show an explicit sentence such as “Reach the daily target on 3 different days each week.” Keep the numeric daily target and unit visible.
  - Show a readable schedule summary on cards/detail and replace the hard-coded DAILY badge.
  - Explain today-effective **habit** edits and neutral transitional weeks before saving a substantive weekly change. Distinguish them from future-effective Settings week-start changes. Do not insert warnings for cosmetic or no-op changes.
  - Preserve drafts during ordinary recreation; confirm before discarding meaningful unsaved work. Revalidate dates and entity existence on submission.
- **Files/Components**: `ui/Sheets.kt`, `ui/Components.kt`, `ui/Screens.kt`, `GitHabitViewModel.kt`, `res/values/strings.xml`; schedule-form instrumented tests (**new** or added to the existing suite).
- **Dependencies**: Tasks 3, 5, and 6. Connect to whichever compatible entry shell is present; coordinate, rather than block on, Task 7.
- **Acceptance Criteria**:
  - All three schedule kinds can be created, edited, saved, reopened, and announced correctly.
  - Empty weekday sets and N outside 1–7 cannot be persisted.
  - A weekly schedule cannot be mistaken for a total weekly quantity or a count of commits.
  - The form works at narrow width and 200% text without clipped labels or side-by-side field failure; fields stack when needed.
  - Busy/saving/error feedback is visible and fields retain input on failure.
  - Required blank target/unit/name inputs cannot be saved, and only explicitly submitted valid values appear in the created repo. Cancelling repo/log creation leaves history unchanged.
- **Estimated Complexity**: Medium.

### Task 10: Make Today a fast, schedule-aware logging surface

- **Description**:
  - Replace the promotional hero/large dashboard with a compact date/status header and immediately reachable habit logging.
  - Present scheduled daily/weekday repos, stable weekly repos with remaining quota, transitional partial-week practice, and completed/optional activity in clearly labeled groups. Preserve a simple remaining filter without treating partial-week practice as an unmet full-week obligation.
  - Show numeric progress and Log/Complete remaining actions for each actionable repo. Stable weekly rows additionally show completed distinct days out of N and whether today's date already qualified. Transitional/bridge rows show practice counts and a separately labeled usual future weekly target, not an authoritative combined x/N quota score.
  - Keep Log and Complete semantics visibly distinct. Do not require opening a graph or detail screen for the common operation.
  - Move the large contribution graph and extended statistics to Insights. Keep a small navigation affordance to history/date exploration, not a second dominant dashboard.
  - Use keyed lazy content for growing lists; preserve scroll when a commit updates a row. Avoid hiding the updated row unexpectedly without clear completion feedback.
  - Distinguish no repos, no schedule today, weekly quota already met, all due work complete, and filtered-empty states with relevant actions.
  - Remove unconditional progress-success copy for empty history. Show a neutral empty state with Create first repo and actual zero activity, rather than populated example cards, an invented profile greeting, or an achievement claim.
- **Files/Components**: `ui/Screens.kt` (`TodayScreen`, repo row/card bridges), `ui/Components.kt`, `ui/Sheets.kt` (`LogForm`), `res/values/strings.xml`; Today flow tests.
- **Dependencies**: Tasks 8 and 9.
- **Acceptance Criteria**:
  - The first actionable repo's Log/Complete controls appear in the initial viewport of a 360dp × 800dp portrait app window at 100% font scale, with test-only due work in a guarded disposable installation and no keyboard open; large decorative panels do not precede them. Record actual usable window/inset measurements with the test.
  - At 200% text the task remains immediately identifiable and reachable without navigating through unrelated content, even when more scrolling is unavoidable.
  - A 3-days/week repo shows 1/3 after multiple qualifying Monday commits, remains 1/3 after an incomplete Tuesday, and shows 2/3 after Wednesday qualifies.
  - Meeting the quota removes the obligation from Remaining but does not hide history or prohibit optional progress.
  - Changing the week-start setting does not reset current-period quota progress. An active calendar bridge uses optional weekly practice while daily/selected-weekday obligations continue; headers show the correct explicit dates.
  - Corrections and Undo update grouping, daily amounts, and weekly quota coherently.
  - An unseeded workspace shows only its honest empty state; after explicitly creating one repo and one commit, all displayed amounts/counts reflect exactly that input, with no hidden additions.
- **Estimated Complexity**: Large.

### Task 11: Make Insights, repo history, and date exploration schedule-aware

- **Description**:
  - Replace the current mislabeled rolling-seven-day “this week” calculation with explicit calendar-week summaries. Label any retained trailing-seven-day view literally.
  - Resolve historical/current weeks and calendar bridges through the shared week-definition history. Align current weekday labels to the selected convention without relabeling historical assessment boundaries; display short bridge ranges explicitly rather than forcing them into misleading seven-day weekly columns.
  - Show per-repo daily target progress and weekly quota progress separately; exclude pending/transitional periods from adherence denominators.
  - Apply the proposed overview activity-graph definition and document the changed legend. Do not present commit count or chart intensity as goal achievement.
  - Keep per-repo daily heatmaps, but distinguish not applicable, optional activity, partial, complete, and relevant pending states. Add weekly summary context for N/week repos.
  - Make the date explorer the primary precise inspection method: date, eligibility/schedule explanation, target, actual amount, qualifying-day status, week context, and editable commits.
  - Remove or redesign inaccessible tiny per-cell pointer actions; default to a noninteractive overview plus an equivalent labeled date explorer with adequate targets. Do not merely add an accessibility label to an unusably small action.
  - Use reusable domain summaries/indexes and lazy history rendering; preserve chronological ordering and correction/deletion access.
  - Render zero/missing activity without a minimum positive chart bar or mock graph values. Keep neutral calendar cells/axes and legends if useful; distinguish no assessed periods from 0% adherence and from load failure. Do not fabricate repo descriptions or changelog entries to fill space.
- **Files/Components**: `ui/Screens.kt` (`InsightsScreen`, `DetailScreen`, `ActivityScreen`, history), `ui/Components.kt` (`Heatmap`), `ui/Sheets.kt` (`DayDetails`, `Guide`), `domain/ProgressAnalytics.kt` (**new** from Task 3), string resources.
- **Dependencies**: Tasks 3, 6, 8, and 9.
- **Acceptance Criteria**:
  - A completed 3-days/week goal is not shown as four failed weekdays or as 43% adherence.
  - Exact date details are available without touching a tiny square, relying on color, or using a mouse.
  - An unscheduled date and an eligible zero-progress date have distinguishable textual states.
  - Correcting a qualifying day below target updates that week's count and status, including a closed week's result.
  - Overview activity counts, per-repo ratios, and adherence have distinct names, explanations, legends, and tests.
  - Historical weekly boundaries do not change after a Settings edit; date exploration explains whether a date belongs to an old-convention week, a bridge, or a new full week. Stored dates and activity counts are unchanged.
  - Fresh empty history has zero contributions/streaks, no positive activity cells/bars, and unavailable adherence where no period is assessable. Exact date/history views contain only actual stored records.
- **Estimated Complexity**: Large.

### Task 12: Complete local export, validated restore, and explicit reset

- **Description**:
  - Retain Android document-provider export of an immutable validated workspace snapshot, with a descriptive filename and success/error feedback.
  - Include workspace week-definition history and pending preference changes in export/restore. A restored workspace uses its own convention; reset initializes Monday with no pending transition.
  - Add a document-open import flow: bounded read, validation/migration, a preview of format/habit/commit counts, and a clear replacement warning.
  - Before replacement, preserve the current workspace; if preservation fails, do not proceed. Keep a recovery copy until the replacement has been verified readable.
  - On confirmation, replace through the same atomic mutation boundary as ordinary saves. Clear obsolete routes, selections, Undo, and drafts; display the restored workspace only after successful persistence.
  - Recheck the active workspace revision at confirmation. If it changed since the preview, regenerate the replacement summary and ask for confirmation again rather than discarding intervening edits. Commit the exact retained validated candidate/fingerprint that was previewed, not a newly read provider file.
  - Cancelled selection/preview, invalid input, unsupported format, read interruption, and write failure must leave the active workspace untouched.
  - Require explicit confirmation for reset, explain that external export copies are not deleted, and offer export before reset. Do not present a transient Undo as sufficient for a full reset.
  - Confirmed reset/start-over creates zero habits/commits and an unset profile; it must not call a sample initializer. Restore accepts the user's exact validated candidate under the migration contract, including sample-looking legacy records; it neither generates fillers nor silently removes records by presumed origin.
  - Explain that exports contain plaintext habits/notes/profile data and that a chosen cloud-backed document provider may sync the file independently of GitHabit.
- **Files/Components**: `ui/GitHabitApp.kt` (document launchers), `ui/Screens.kt` (Settings/recovery), `ui/Sheets.kt` (preview/confirmation), `GitHabitViewModel.kt`, `data/WorkspaceJson.kt`, `data/WorkspaceStore.kt`; transfer tests.
- **Dependencies**: Tasks 2, 4, 5, and 7.
- **Acceptance Criteria**:
  - Export → confirmed replacement import preserves all supported history, schedules, dates, units, preferences, and IDs.
  - Round-trip tests include an old-convention closed week, an active bridge, and a future pending selection. Restored period bounds and weekly results match the export snapshot under the same evaluation date.
  - Valid v1 import follows the same migration rules as startup migration.
  - Empty-workspace exports/restores stay empty. After a guarded test's explicit reset, profile/history stay empty through restart; cancelled reset leaves the previous workspace intact. Mixed-origin import preserves every valid record.
  - Invalid, oversized, unsupported, interrupted, or cancelled imports never partially replace current state.
  - A failed final write with verified rollback preserves the previous workspace, routes, drafts, and existing Undo tokens and permits retry. If the storage outcome cannot be established, preserve copies and enter recovery without promising rollback or a safe blind retry.
  - No Internet or broad storage permission is introduced for document-provider operations.
- **Estimated Complexity**: Large.

### Task 13: Verify accessibility, layout, resource handling, and long-history performance

- **Description**:
  - Audit complete tasks, not just widgets: local entry, create/edit each schedule, log, correct, Undo, inspect a date/week, change theme/week start, replace/cancel a pending preference change, export/restore, and reset confirmation.
  - Test TalkBack grouping, names/roles/state, headings, selected weekdays, error announcements, focus return after sheets, and concise chart summaries. Standard component defaults do not excuse incorrect composition.
  - Test 100% and 200% text, compact/short-height/expanded windows, dark/light/system theme, external keyboard, and system animation reduction/disable settings.
  - Move touched user-facing strings and accessibility descriptions into resources; use plurals and locale-aware date/number formatting. Validate pseudo-localization and RTL layout without promising fully translated languages.
  - Exercise long history using a documented test-only synthetic fixture (at least 20 repos over five years) and profile aggregation, scrolling, save/export/import, and startup on the provisioned disposable test device. The generator/payload stays in instrumented test sources; no debug-app seed shortcut is allowed.
  - Exercise each proposed capacity/depth/ID boundary at, below, and above its limit, including memory use during parsing. A five-year typical fixture is not a substitute for boundary validation. Adjust and document bounds if the minimum-supported device cannot safely handle them.
  - Fix repeated full-history scans and eager large lists before considering a database migration. Keep IO off the main thread and avoid recalculating analytics solely on unrelated theme changes.
  - Verify backup/device-transfer exclusions still cover workspace/recovery data and that no personal fixtures leak into captured screenshots.
- **Files/Components**: all touched UI/domain/data files; `res/values/strings.xml`; manifest/theme/extraction resources; instrumentation and performance fixtures; `android/docs/verification.md` (**new**).
- **Dependencies**: Tasks 10, 11, and 12.
- **Acceptance Criteria**:
  - Every core task can be completed through TalkBack and external-keyboard navigation without a tiny Canvas target or color-only cue.
  - No clipped controls, overlapping essential text, inaccessible confirm action, or keyboard-obscured form submission remains in the supported matrix.
  - Contrast and minimum-target checks are recorded for actual rendered states, including errors and focus.
  - The long-history fixture loads, scrolls, logs, and restores without crashes/ANRs; measured timings and frame issues are recorded rather than claiming performance from inspection.
  - Resource extraction covers touched UI; pseudo-localization/RTL findings are resolved or explicitly bounded before handoff.
  - Fixture/performance/screenshot setup passes the same disposable-device guard as other instrumentation; absence of the guard causes no target-app mutation.
- **Estimated Complexity**: Large.

### Task 14: Run fresh end-to-end and failure-recovery verification

- **Description**:
  - Extend the test harness already adapted in Task 7 for local onboarding, three-way themes, and test-only fixtures rather than replacing it wholesale or postponing the generator-removal fixes until here.
  - Replace the shared test setup's wait for `google-sign-in` and the obsolete `emailValidationSignOutResetAndNoCredentialsStored` journey. Preserve business assertions and replace credential assertions with local entry, legacy reopening, and data/reset behavior. Keep persistence observers from invoking recovery-capable reads concurrently with app writes.
  - Preserve existing fractional logging, remainder completion, correction/deletion, historical-target, pause/archive/resume, and activity-recreation coverage.
  - Add schedule, weekly-boundary, migration, restoration, failed-save/Undo, and adaptive-layout journeys. Update selectors/helpers for lazy lists and two-pane layouts.
  - Add Settings → select weekday → save → restart → boundary activation journeys, including pending cancellation, bridge display, historical preservation, and failed-save behavior.
  - Run unit tests for supported build variants, lint, debug build, and connected tests on disposable devices. Add an oldest-supported Android smoke pass and a current-target-device pass when available.
  - Exercise process death separately from activity recreation and verify read-failure recovery after restart.
  - Add unseeded-install and no-reseed journeys across onboarding, help/navigation, preferences, form cancellation, restart, clock changes, migration, and recovery. Add a no-fixture first-use journey that creates one repo/commit through the real UI and checks exact persistence, export, and restore results.
  - Audit effective source sets, dependencies, packaged code/resources/assets, and manifest entry points in every freshly built deliverable. Prove app-owned fixture payloads, `SampleWorkspace`, test fixture factories, and seed commands are absent from the installed app, including a delivered debug build. Record artifact identity/hash and inspection evidence; inspect an AAB too only if it is being delivered. Source grep alone or release shrinking alone is not sufficient.
  - Refresh screenshots of real native screens using test-only synthetic data on guarded disposable installations, plus a genuine empty-state screenshot. Retain them only as clearly labeled documentation/QA evidence; delete or relabel obsolete sign-in/prototype screenshots as part of documented asset updates, not as runtime assets.
- **Files/Components**: existing `HabitsTest.kt`, `WorkspaceJsonTest.kt`, `GitHabitFlowTest.kt`; new domain/storage/command test files and fixtures; `android/previews/`; Gradle test configuration if required; `android/docs/verification.md` (**new**).
- **Dependencies**: Task 13.
- **Acceptance Criteria**:
  - All checks actually run have a dated result and reproducible command/configuration; old reports are not relabeled as new.
  - Unit/instrumented assertions cover the acceptance-case matrix below; no obsolete credential assertions remain.
  - Destructive tests operate only on isolated fixtures/installations and preserve any personal workspace.
  - The unseeded first-use journey produces only explicitly submitted records, which survive restart/export/restore. Empty/mixed legacy migrations and recovery paths neither seed new records nor automatically purge old ones.
  - Each delivered APK has recorded artifact-inspection evidence confirming no app-owned mock dataset, generator, test fixture, or seed entry point is packaged. Test APKs/fixtures remain separate and are not handed over as the personal-use app.
  - Remaining platform gaps are clearly marked unverified; no claim of full accessibility or device coverage exceeds the evidence.
  - A newly built APK, not a stale artifact, passes the personal-use walkthrough.
- **Estimated Complexity**: Medium.

### Task 15: Document personal use and prepare optional source sharing

- **Description**:
  - Update the native README with local onboarding, the three schedules, **Week starts on** choices and Monday defaults, future-effective changes/short bridges, preserved historical boundaries, the difference between activity/adherence, backup/restore, theme choices, and actual verification evidence.
  - Replace sample-mode/auth instructions with empty startup → create your own first repo → log actual progress. Explain that existing records are preserved on upgrade and that test fixtures/screenshots are verification assets, not app content or a supported demo mode.
  - Keep the root web README separate; add only a factual native-project pointer if useful, not a claim that the web version shares the new features.
  - Preserve the existing package ID for installed-data continuity. Document signing implications: switching signing keys prevents an ordinary update, so export/restore must be available before any reinstall.
  - Document reproducible local build/install steps and hand over the exact APK inspected/tested in Task 14. If rebuilt or repackaged, rerun relevant verification/fixture-exclusion checks and record its new hash. Keep secrets, SDK paths, personal exports, recovery snapshots, and private screenshots out of distributable source.
  - For optional GitHub sharing, review `.gitignore`, third-party notices/assets, dependency licensing, test-only synthetic fixtures/documentation screenshots, and the owner's license choice. Keep tests available to contributors without packaging their data into the app. Do not initialize/publish a remote or upload an APK without approval.
  - State remaining limitations honestly: local storage is not an encrypted secure journal; public-release hardening, reminders, sync, and interval scheduling are outside this increment.
- **Files/Components**: `android/README.md`; optional root `README.md` pointer; `android/.gitignore` and root `.gitignore`; `android/docs/`; optional LICENSE/NOTICE files (**new, owner-approved only**).
- **Dependencies**: Task 14. Public sharing additionally depends on the owner's explicit license/publication decision.
- **Acceptance Criteria**:
  - Another developer can build and use the app from an empty workspace, create their own first repo, and log progress without credentials, backend setup, or a sample-data step.
  - Schedule and data-loss/recovery behavior in documentation matches the tested implementation.
  - No personal data, signing credentials, local SDK paths, or misleading verification claims are included in the proposed shareable tree.
  - The local-use deliverable is complete even if GitHub publishing is never requested.
- **Estimated Complexity**: Small.

## Execution Order

### Dependency sequence

1. **Task 1** establishes the baseline and freezes the contract.
2. **Tasks 2, 3, and 6** can run in parallel: storage safety, domain scheduling, and design tokens. Coordinate shared-file ownership; parallel task labels are not permission to overwrite another task's edits.
3. **Task 4** follows storage/domain work; **Task 5** follows migration.
4. **Tasks 7, 8, and 9** can proceed after Tasks 5 and 6 (and their transitive dependencies), removing runtime demo data and connecting local entry, adaptive shell, and schedule forms. Task 7 updates affected fixture/test callers atomically with generator removal; coordinate shared files or serialize those edits rather than introducing artificial functional dependencies.
5. **Tasks 10 and 11** become available when Tasks 8 and 9 complete; connect the final entry flow when Task 7 completes.
6. **Task 12** can proceed alongside shell/form work once Tasks 2, 4, 5, and 7 are complete.
7. Divide **Tasks 10 and 11** by screen ownership while sharing the same tested domain queries; do not duplicate schedule calculations in UI.
8. **Task 13** integrates accessibility/adaptivity/performance checks after the user journeys exist. Its practices also apply throughout earlier tasks.
9. **Task 14** performs fresh whole-app verification.
10. **Task 15** completes local handoff; public sharing is optional and separately approved.

### Delivery gates

- **Gate A — Protect history:** Task 1's disposable-test guard applies before any connected test, including the baseline. Task 2 must pass before any data migration or replacement is attempted on a personal workspace. Never auto-purge sample-looking legacy records.
- **Gate B — Trust the numbers:** Tasks 3–5 must pass deterministic schedule, dated week-definition/bridge, migration, and Undo tests before weekly counts are presented as authoritative.
- **Gate C — Usable daily driver:** Tasks 7–12 deliver sample-free local entry, usable week-start/theme preferences, logging-first routines, honest empty states, accessible date inspection, and working restore.
- **Gate D — Verified real-data handoff:** Tasks 13–15 establish the supported device/accessibility scope, pass unseeded first-use and preservation tests, and verify that the exact delivered APK contains no app-owned fixtures/generators/seed entry points. No prototype/sample-bearing intermediate build is a real-use handoff.

These are dependency gates, not calendar promises. No delivery-time estimate is asserted without knowing available development time and devices.

## Acceptance-Case Matrix

Each case must be traced to a unit, integration, instrumented, or explicitly manual check. Unit tests cover the math; UI tests prove that the same meaning is presented to the user.

| Case | Expected result | Primary task |
| --- | --- | --- |
| Daily habit, 20-page target, commits of 5 and 7 | 12/20 partial; Complete adds 8, not 20 | 3, 5 |
| Weekly quota 3; multiple Monday commits total the target | One qualifying date, not multiple day credits | 3 |
| Weekly quota 3; Monday/Thursday/Saturday reach target | 3/3; achieved; other dates are not daily failures | 3, 10, 11 |
| Partial Tuesday in that week | Visible progress, zero additional qualifying day | 3, 11 |
| Selected Monday/Wednesday/Friday; Tuesday has no log | Not scheduled, not missed | 3, 10 |
| Extra Tuesday log for the active weekday habit | Optional activity; no borrowed credit for Wednesday | 3, 9, 11 |
| Open eligible day or open incomplete stable week | Pending, not failed; transitional weeks stay unscored | 3, 10 |
| Weekly N=1 and N=7 | Correct distinct-day counting at both limits | 3 |
| Creation on Thursday or pause/resume midweek | Weekly period visible but transitional/unscored | 3, 9, 11 |
| Schedule/target change Wednesday | Earlier expectations preserved; affected weekly period neutral | 3, 4, 9 |
| Cosmetic edit or unchanged Save | No artificial transition or duplicate effective version | 3, 9 |
| Correction drops an earlier qualified day below target | Weekly count decreases; closed result recomputes | 3, 5, 11 |
| Undo after later unrelated habit changes | Reverses its own effect without clobbering newer settings | 5 |
| Persistence failure during Undo with verified rollback | Prior state intact; reversal remains retryable | 2, 5 |
| Write verification and rollback both fail | Non-writable recovery; preserved copies; no blind command/Undo retry | 2, 5, 12 |
| Midnight during a save | One captured operation date and consistent Undo metadata | 5 |
| Every configured weekday boundary, New Year, leap day, DST | Correct local dates and explicit configured week bounds | 3, 5 |
| Timezone travel or clock rollback | Stored dates unchanged; conflicting historical edits blocked, not rewritten | 5 |
| Corrupt/future-version startup file | Recovery state; ordinary writes blocked; original preserved | 2, 4 |
| Legacy signed-out populated workspace | History opens locally without credentials/reseeding | 4, 7 |
| Legacy light/dark preference | Preserved as explicit LIGHT/DARK | 4, 6, 7 |
| New workspace SYSTEM theme | Follows system; explicit override persists | 6, 7 |
| Export and replace-only restore | Same supported data and schedules; no identity credentials | 12 |
| Cancelled/invalid/interrupted import input before replacement begins | Current workspace untouched | 12 |
| Failed replacement with verified rollback and an existing Undo token | Workspace and transient state/Undo retained | 12 |
| Source document or active workspace changes after preview | Use only retained confirmed candidate; changed active revision requires refreshed consent | 12 |
| Reset then stale Undo action | Cannot resurrect the reset workspace | 5, 12 |
| Compact to expanded resize while detail is open | Selection and context retained; navigation adapts | 8 |
| Keyboard/TalkBack date inspection | Exact amount and schedule status without tiny-cell taps | 11, 13 |
| 200% text and software keyboard on form | All required fields, errors, and submission remain usable | 9, 13 |
| Five-year synthetic history | No repeated per-cell full-history scans, crash, or ANR; measurements recorded | 3, 13 |
| Fresh workspace or v1 migration | Monday week start, no pending request, historical dates retained | 4, 7 |
| All 49 old/new weekday choices | Same weekday is a no-op; changed weekday preserves old period and produces a 1–6-day bridge | 3, 5 |
| Monday → Sunday / Sunday → Monday | Six-day / one-day weekly bridge respectively; no overlap or lost date | 3 |
| Preference saved in the middle of a stable week | Current week's boundaries/count remain unchanged; Settings shows exact future dates | 5, 7, 10 |
| Pending choice revised or cancelled before boundary | Latest request replaces pending only; cancellation preserves effective history | 5, 7 |
| Another choice saved after a bridge begins | Existing bridge dates fixed; new change queued at its end | 3, 5 |
| Select Monday during a Monday → Sunday bridge, then select Sunday before its end | Monday queues a return and revises the projected full-week date; Sunday cancels only that queued return, leaving the active bridge fixed | 3, 5, 7 |
| Bridge with daily and selected-weekday habits | Ordinary eligible daily obligations still assessed; only weekly quotas neutral | 3, 10, 11 |
| Truly empty workspace versus habits with zero commits | Immediate application only for no dated expectations/activity; existing missed obligations preserved | 5, 7 |
| App closed across preference boundaries | Original effective dates honored on restart, not shifted to launch date | 5, 14 |
| Failed week-start preference save | Effective/pending values unchanged after verified rollback, or explicit recovery if outcome unknown | 2, 5, 7 |
| Export/restore with historical and pending week-start changes | Same configured period membership/results under the same evaluation date | 4, 12 |
| Keyboard/TalkBack/200% text in week-start Settings | Full weekday names, selected/pending status and dates understandable and operable | 7, 13 |
| Unseeded fresh installation | Zero habits/commits; unset profile; honest empty views; no demo entry point | 4, 7, 10, 11, 14 |
| Onboarding/help/navigation, preferences, restart/date changes with empty history | No implicit creation of habits/commits | 5, 7, 14 |
| New repo/log form opened, invalid, or cancelled | No prefilled reading habit or persisted placeholder record | 9, 14 |
| First real UI creation of one repo and one commit | Exact user-entered records and metrics across screens, restart, export/restore | 7, 9, 10, 11, 12, 14 |
| Empty v1 workspace and mixed/edited `sample-*` legacy history | Empty stays empty; existing valid records/profiles remain intact; no origin-based purge | 4, 14 |
| Unset profile saved/cleared and round-tripped | Empty name/bio permitted; neutral UI; no fabricated identity | 4, 7, 12 |
| Empty history charts and adherence | No positive activity bars/cells or fake streak; no-denominator adherence is unavailable | 3, 10, 11 |
| Corrupt/unsupported storage, retry/cancel, confirmed reset/start-over | No sample fallback; error preserves originals; only explicit preserved reset produces empty state | 2, 7, 12, 14 |
| Missing disposable-device test guard | Abort before target-context writes; personal installation untouched | 1, 13, 14 |
| Delivered APK source-set and artifact audit | No runtime sample generator, test fixture payload/factory, or seed hook; tests remain separate | 7, 14, 15 |

## Verification and Evidence Requirements

### Automated baseline and final checks

From `android/`, an executor should use the checked-in Gradle wrapper with the documented SDK/JDK setup. Commands are instructions for future execution; they were not run while writing this plan.

```text
./gradlew :app:test :app:lintDebug :app:assembleDebug --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Connected tests require an explicitly provisioned disposable emulator/device and may reset or replace its workspace. **Do not run the connected command on an arbitrary attached personal device.** Before baseline or later runs, the test-only setup must verify the selected disposable target/package and explicit opt-in and abort before any target-context mutation if those checks fail. Record the exact guarded invocation with the results. A separate test APK alone is not isolation. Where the full matrix cannot be automated, record a manual walkthrough; do not silently count missing checks as passed.

### Fixture isolation and delivered-artifact checks

- Unit fixtures/generators: `android/app/src/test/...`; instrumented/performance fixtures: `android/app/src/androidTest/...`. Synthetic screenshots: `android/previews/` or documented QA output, not runtime assets. Do not introduce fixture data or seed hooks in `src/main`, `src/debug`, other delivered source sets, or runtime dependencies.
- Update tests as part of removing production `SampleWorkspace`, not after leaving broken test imports. Instrumented tests use their own factory or create data through real UI/domain operations on the guarded disposable installation.
- Verify an unseeded cold start independently of seeded fixture tests. Create one repo/commit through the real app, then assert exact counts/amounts after restart/export/restore. Do not use the old Explore demo path or a replacement runtime shortcut.
- Inspect effective source sets and the freshly delivered APK's compiled code, resources/assets, and manifest. Record absence of app-owned fixture generators/payloads/seed entry points and inclusion of the intended real entry point. Record variant, hash, methods, and result; inspect each delivered variant rather than assuming a release-only check covers debug.

### Minimum representative configuration coverage

- Compact phone near 360dp width at normal text, light and dark themes.
- Narrow phone near 320dp width and 200% text for logging, forms, errors, navigation, and confirmations.
- Short landscape and split-window cases where height constrains the layout.
- Expanded window at or above the chosen two-pane threshold, plus a large window smoke test.
- SYSTEM theme switching; reduced/disabled animations; software keyboard open.
- All weekday choices in Settings; pending-change display/cancellation; a one-day and a six-day bridge; app restart across a scheduled calendar change.
- TalkBack and an external keyboard for the complete primary task and data-transfer confirmation.
- Minimum-supported Android smoke pass and a current-target Android device/emulator pass, when available; unavailable configurations remain explicitly unverified.
- Process recreation, loading failures, IO failures, migration, and malformed imports with synthetic data.
- Genuine unseeded first-use flow, empty charts/profile, no-reseed behavior, mixed legacy-history preservation, and negative tests of the disposable-device guard. Synthetic data in all other checks is test-only and never applied to a personal installation.

### Evidence to save

Record the source revision if a repository exists, otherwise a source snapshot identifier; exact build versions; command; timestamp; device/API/window/font/theme settings; disposable-target guard evidence; actual outcome; and linked generated report/screenshot. Include delivered-artifact hashes and fixture-exclusion audit results. Screenshots must come from the native implementation with test-only synthetic records or genuine empty states, never personal history; label populated images as verification examples rather than shipped content.

## Requirements Traceability

| Requirement | Tasks |
| --- | --- |
| Developer-oriented personal/local app | 7, 10, 15 |
| Real-data-only shipped app; no demo data/generation; isolated tests retained | 1, 4, 7, 9, 10, 11, 12, 13, 14, 15 |
| Preserve existing/mixed history without automatic sample cleanup | 1, 2, 4, 7, 12, 14 |
| Daily, selected weekdays, N days/week | 3, 4, 5, 9, 10, 11 |
| Settings choice of first weekday with preserved historical weeks | 3, 4, 5, 7, 9, 10, 11, 12, 13, 14, 15 |
| Numeric targets, partial progress, distinct day credit | 3, 5, 9, 10 |
| Historical expectations and safe corrections/Undo | 3, 4, 5, 11 |
| No failure for unscheduled/pending periods | 3, 10, 11 |
| Activity versus adherence distinction | 3, 10, 11, 15 |
| Tokens, light/dark/system, standard M3 behavior | 6, 7, 9, 13 |
| Adaptive navigation and readable layouts | 8, 10, 11, 13 |
| Complete states and usable accessibility | 2, 7, 9, 10, 11, 12, 13 |
| Data preservation and restore | 1, 2, 4, 5, 12, 14 |
| Reproducible testing and optional GitHub handoff | 1, 14, 15 |

## Risks & Mitigations

- Risk: schedule behavior becomes ambiguous at boundaries → Mitigation: implement the contract as pure domain tests before rendering it.
- Risk: migration/recovery overwrites personal history → Mitigation: explicit load outcomes, protected original copies, validated atomic writes, and non-destructive failure tests.
- Risk: personal-app scope becomes a platform project → Mitigation: preserve the native prototype and defer sync, notifications, recurrence engines, and mandatory Expressive adoption.
- Risk: repeated midweek edits create many neutral periods → Mitigation: explain the conservative transition policy, do not invalidate cosmetic/no-op edits, and keep raw history visible. This is a personal tool, not an anti-cheating system.
- Risk: the overview chart's meaning changes without users noticing → Mitigation: explicitly change its label/legend, retain per-repo numeric progress, and document the old/new semantics in the native README.
- Risk: chart accessibility is claimed from a generic label → Mitigation: verify an equivalent date/weekly inspection task with TalkBack and keyboard; remove tiny actions that remain unusable.
- Risk: six simple destinations trigger an unnecessary navigation rewrite → Mitigation: retain the small routing model; use supported saved-state/back/adaptive APIs and verify behaviors explicitly.
- Risk: restoration accepts data that normal runtime cannot handle → Mitigation: share validation and resource bounds across save/export/import and test large legitimate round trips.
- Risk: immutable snapshots still cause expensive repeated analytics → Mitigation: one reusable indexed summary per relevant snapshot/date horizon, keyed lazy lists, and measured long-history profiling.
- Risk: system-theme migration surprises an existing user → Mitigation: map legacy Booleans to explicit light/dark; only new workspaces default to SYSTEM.
- Risk: source sharing leaks personal data or breaks installed updates → Mitigation: synthetic fixtures, explicit publication review, preserved package identity, and documented signing/export implications.
- Risk: old screenshots/reports create false confidence → Mitigation: attach dated configuration-specific evidence and mark every unperformed check unverified.
- Risk: newer documentation examples introduce incompatible alpha APIs → Mitigation: resolve against the verified pinned BOM/artifacts and keep compiler/Kotlin alignment separate.
- Risk: stronger v2 validation rejects legitimate old history → Mitigation: fixture-based compatibility rules, protected originals, and explicit recovery instead of destructive normalization.
- Risk: disk failures make the actual commit outcome unknown → Mitigation: retain candidate/previous copies, enter non-writable recovery, and reconcile before allowing retries; do not overpromise rollback.
- Risk: changing the first weekday silently regroups old weekly goals → Mitigation: dated workspace conventions, preserved current/closed periods, explicit short bridges, and one shared period resolver.
- Risk: a delayed preference appears broken or a bridge looks like a missed week → Mitigation: show exact pending/bridge/full-week dates in Settings and neutral weekly practice states elsewhere; daily obligations continue normally.
- Risk: hiding sample buttons leaves production generators or debug fixtures in the shipped APK → Mitigation: remove runtime definitions/callers, move fixtures only to test sources, and inspect each delivered artifact rather than relying on shrinking.
- Risk: “remove mock data” deletes real history added to sample habits → Mitigation: no reliable legacy provenance means preserve all valid existing records; no ID/name/date heuristics or automatic purge. Destructive reset/replacement requires separate explicit consent and preservation.
- Risk: instrumentation overwrites a personal installation despite using a test APK → Mitigation: fail-closed disposable-device/package opt-in before every destructive setup, including baseline, and never run the suite against the owner's live installation.
- Risk: empty-profile validation, fabricated description defaults, or minimum chart bars keep the app looking populated → Mitigation: coordinate model/codec/profile UI changes, blank new form inputs, and explicit zero/unavailable-state assertions.

## Decisions Log

- Developer-oriented personal app, not a commercial launch — confirmed by the user.
- Daily, selected weekdays, and N distinct goal-completed days/calendar week — confirmed defaults.
- Partial amounts remain visible; multiple commits cannot create extra day credits — confirmed defaults.
- Existing native app is the implementation base; web prototype is frozen — planning default to avoid an unnecessary rewrite.
- Local onboarding replaces demo authentication — recommended scope, consistent with single-user personal use.
- Real-world use with no mock/sample/test content in the shipped app — explicitly requested; the prior optional sample walkthrough is removed from scope.
- Retain automated tests and isolated test-only fixtures, not runtime/debug seed data — explicitly selected by the user after scope clarification.
- Preserve legacy sample-looking/mixed records and existing profile values — no reliable origin marker exists, so cleanup must not become an automatic data-loss migration.
- New workspaces use an unset optional profile and intentional user-entered repo fields — explicit planning policy for genuine empty-state use, while normal theme/schedule defaults remain available.
- Validated replace-only restore is included — recommended reliability work, not a cloud feature.
- Settings **Week starts on** — explicitly requested by the user; app-wide Monday–Sunday choices with Monday as fresh/v1 default are the planned behavior.
- Future-effective week-start changes preserve the current ordinary week, then use a short neutral weekly bridge before the first full new week — explicit conservative implementation policy, not a retroactive regrouping.
- Today-effective habit changes and unscored transitional weekly periods — proposed conservative policy; distinct from the Settings calendar-change policy and avoids retroactive quotas/proration.
- Overview activity and schedule adherence are separate — proposed metric clarification required by mixed schedule types.
- Dynamic color and Expressive extras are optional — baseline M3 meets the core design goal without expanding scope.
- Optional GitHub sharing requires a license and publication review — do not choose a license or publish on the user's behalf.

## Planning Handoff Checklist

- [x] Confirmed requirements map to explicit tasks and acceptance cases.
- [x] Assumptions are separated from user-confirmed requirements; no further requirements interview is necessary to start the stated plan.
- [x] Listed dependencies form an acyclic sequence; shared-file coordination is distinguished from functional prerequisites.
- [x] Each task identifies affected components, dependencies, acceptance criteria, and estimated complexity.
- [x] Existing paths and extension seams were verified by read-only codebase research; proposed files are marked new.
- [x] Official documentation informs version-sensitive recommendations; build/test claims are limited to actual evidence.
- [x] Data-preservation and indeterminate-failure behavior are explicit rather than promising impossible rollback guarantees.
- [x] The no-shipped-mock-data requirement covers runtime entry points, generators/defaults, test isolation, legacy preservation, honest empty states, and artifact acceptance gates.
- [x] The plan is saved at `.plans/githabit-android-build-plan.md`.
- [x] No application implementation, build, test execution, or publication was performed while preparing this plan.
