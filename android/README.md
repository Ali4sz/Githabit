# GitHabit — native Android

A local personal habit tracker built with **Kotlin, Jetpack Compose, and Material 3**. Repo/commit language and contribution history are intentional. This is a native app, not a WebView; the [web prototype](../README.md) is unchanged.

**Current checkpoint (2026-11-14):** Tasks 1–11 are implemented; see
[Task 11 evidence](docs/task11-verification.md) for exact build/test results and limits.
The native APK is real-data-only: no demo/authentication, sample workspace, or runtime
generator. This is still an intermediate build: restore and final accessibility/
performance/transfer verification remain in the [build plan](../.plans/githabit-android-build-plan.md).
Earlier [phone results](docs/phone-verification.md) do not verify Task 11; no new phone
operation is authorized or performed for this task.

## Try it

- **Debug APK:** [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk) (Android 8.0/API 26 or newer). See the verification document for the exact audited hash.
- **First run:** launch **GitHabit** → **Create first repo**. Enter your own name, numeric daily target and unit; choose Daily, selected weekdays, or N distinct target-completed days per calendar week. Log your actual progress from Today.
- **No account:** one local workspace; optional profile and System/Light/Dark theme in Settings. No credentials or network access are needed.
- **Week starts on:** Settings → Calendar & scheduling. All seven weekday choices; Monday default. Existing expectations use preserved historical periods, future-effective changes and short unscored weekly bridges.
- **Start over:** Settings → Reset workspace, after confirmation and preservation. Existing valid records—including mixed or sample-looking legacy history—are never automatically purged.

### Screenshots

**Historical prototype captures**, not current Task 11 evidence or shipped content.
They use synthetic records on a test emulator. Old sign-in/onboarding screens no longer
exist; refreshed task-specific captures and final screenshot cleanup belong to Task 14.

| Screen | Preview | Screen | Preview |
| --- | --- | --- | --- |
| Sign-in | [Open](previews/01-sign-in.png) | Onboarding | [Open](previews/02-onboarding.png) |
| Today, dark | [Open](previews/03-today-dark.png) | Repos | [Open](previews/04-repos.png) |
| Repo detail | [Open](previews/05-repo-detail.png) | Log a commit | [Open](previews/06-commit-sheet.png) |
| Day explorer | [Open](previews/07-day-details.png) | Insights | [Open](previews/08-insights.png) |
| Activity | [Open](previews/09-activity.png) | Settings | [Open](previews/10-settings.png) |
| Today, light | [Open](previews/11-today-light.png) | Create a repo | [Open](previews/12-new-repo.png) |

## What works

- **Today:** immediately reachable logging and remaining-only completion; separate due, weekly remaining, partial-week practice and optional/completed groups. Extended charts live in Insights.
- **Repos:** search/filter habits; create/edit the three schedules, names, descriptions, daily targets, units, icons and colors; pause, archive and resume.
- **Repo detail:** per-habit heatmap, progress, history, and commit editing/deletion.
- **Commits:** positive fractional amounts, optional messages, multiple logs per day, and snackbar Undo for logging, correction, deletion, completion, and status changes.
- **Date explorer:** exact daily eligibility/amounts, weekly quota/practice context, and retained future records without early credit; returns to the inspected date after correction/deletion/cancellation.
- **Insights and Activity:** resolved calendar periods, separate day/week adherence, recorded-activity graph and lazy timestamp-ordered history.
- **Settings:** optional display name/bio, System/Light/Dark theme, dated week-start preference, guide, JSON export and confirmed reset. Restore UI is not implemented yet.
- **Native behavior:** adaptive bar/rail and list/detail, saved routes/drafts/scroll, system Back and predictive route gestures, keyboard-aware forms and document picker. Full accessibility/platform coverage remains unverified.

### Tracking rules

A repo has a numeric daily target and a schedule. A commit records an amount toward
that date's target. Quick completion adds **only the remaining amount**, not another
full target. Actual amounts are uncapped; visual daily-target ratios cap at 100%.
Weekly N counts distinct dates reaching their daily target, not commits or total
weekly quantity. Partial and optional activity is retained. Overview chart intensity
counts recorded commits, not an average success score; day/week adherence is separate.

Habit target/schedule/status changes take effect today; earlier expectations remain.
Weekly periods with substantive midperiod changes are unscored practice; no prorating
or carryover. Week-start edits instead apply at future boundaries, preserving existing
periods. Units are locked after the first commit. Activity streaks count dates with
any commit, not schedule adherence. New logs use today; existing historical/future
records can be corrected or deleted without rewriting their stored dates.

## Open in Android Studio

1. Open **this `android/` directory**, not the web project root, in an Android Studio version compatible with AGP 8.13.
2. Use a Gradle JDK compatible with the pinned toolchain (JDK 17 minimum; this project was verified with OpenJDK 21).
3. In SDK Manager, install **Android SDK Platform 36**, Platform-Tools, and the SDK Build-Tools requested during sync. For the verified emulator configuration, also install Android Emulator and an API 36 Google APIs system image appropriate for your host.
4. Let Gradle sync. The wrapper downloads Gradle and the dependencies; the first build needs Internet access, but the app itself does not.
5. Select the **app** run configuration and a device/emulator, then Run.

### Pinned toolchain

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 8.13.0 |
| Gradle wrapper | 8.14.3 |
| Kotlin / Compose compiler plugin | 2.2.20 |
| Compose BOM | 2025.09.01 |
| Compile / target SDK | 36 / 36 |
| Minimum SDK | 26 |
| Java / Kotlin bytecode target | 17 |

No backend configuration, API keys, npm packages, or separately installed Gradle are required.

## Command-line build and install

From the web project root, on Linux/macOS:

```bash
cd android
# Set this to your actual SDK location; Android Studio can also create local.properties.
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`, signed with a **debug key**,
not a production distribution key. On Windows use `gradlew.bat`. Preserve the signing
key for ordinary installed-data updates; do not uninstall a personal installation to
bypass a signing mismatch. Restore is still pending.

Only on an explicitly selected installation you intend to update:

```bash
SERIAL=your-explicitly-selected-serial
"$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" shell am start -n com.githabit.prototype/.MainActivity
```

Package ID: `com.githabit.prototype`. The APK is a generated, ignored build artifact; rebuild it if it is absent from a source-only copy.

## Verification

**Prefer an explicitly provisioned disposable emulator for instrumented tests:** they
reset/replace GitHabit's workspace. A separate test APK does not isolate app data.
The guard requires exact package/device identity, explicit opt-in, and a matching
temporary marker before destructive setup. See the [emulator procedure](docs/verification.md#disposable-device-verification).

Physical-phone testing additionally requires the owner's explicit GitHabit-only reset
consent, an exact serial match, and **direct serial-selected instrumentation**. Do not
use Gradle connected tests on a personal phone: `animationsDisabled = true` can change
device-wide settings. Physical runs suppress all screenshot capture, including failures
and teardown. Follow the [phone procedure and cleanup requirements](docs/phone-verification.md#safe-physical-phone-procedure).

```bash
# No device required: unit tests for both variants, lint, and both APKs.
./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

### Historical prototype results (2026-11-02)

These results used the `GitHabit_API_36` Android 16 emulator (Google APIs, x86_64)
and do not verify the current source:

| Check | Result |
| --- | --- |
| Domain + serialization unit tests | 17 cases passing in debug and the same 17 passing in release |
| Native instrumented UI tests | 5 passing at the default phone configuration |
| Narrow/large-font interaction pass | All 5 passing at 360dp width and font scale 1.3 |
| Debug build and APK signature | Passed |
| Android lint | 0 errors, 7 warnings about pinned dependency/Gradle versions and target SDK |
| Native document-picker export | Manually verified: 6 repos, 849 commits, `signedIn=false`, no credentials |
| APK permissions / emulator crash log | No Internet permission; crash log empty during verification |
| Screenshots | 12 native captures in `previews/` |

The UI tests cover onboarding, repo creation, fractional logging, remaining-only completion, Undo, correction/deletion, pause/archive/resume, historical targets, navigation, theme persistence, activity recreation, sign-out/reset, and credential non-persistence. Compact stats stack vertically on narrow screens or with larger fonts.

Generated reports live under `app/build/reports/`; machine-readable unit results are under
`app/build/test-results/`, and Gradle connected results under `app/build/outputs/androidTest-results/`.
Direct phone instrumentation instead uses explicitly captured runner logs. The later
[phone results](docs/phone-verification.md) cover one CPH2749/API 37 configuration only;
older Android releases, landscape/tablet layouts, and a full accessibility audit remain unverified.

## Implementation map

All app sources below are relative to `app/src/main/java/com/githabit/prototype/`:

- `MainActivity.kt` — activity entry point, Compose content, and date refresh on resume.
- `GitHabitViewModel.kt` — navigation/overlays/drafts, clock, serialized verified commands, analytics cache, Undo, reset and export.
- `domain/Habits.kt`, `Schedules.kt`, `WeekCalendar.kt`, `ProgressAnalytics.kt`, `WeeklyAnalytics.kt`, `HeatmapQueries.kt` — immutable models, historical expectations/periods, schedule analytics and indexed chart/history queries.
- `data/WorkspaceJson.kt` — versioned, validated JSON serialization.
- `data/WorkspaceStore.kt` — app-private atomic-file persistence.
- `ui/GitHabitApp.kt` — scaffold, tab-state retention, back handling, system bars, snackbars, and document picker.
- `ui/TodayContent.kt`, `InsightsContent.kt`, `HistoryComponents.kt`, `DateInspector.kt` — logging-first Today, schedule-aware Insights and lazy history/inspection.
- `ui/Screens.kt`, `ui/Settings.kt`, `ui/Sheets.kt`, `ui/Components.kt`, `ui/Theme.kt` — screens, preferences, forms/dialogs, charts/shared components and Material 3 themes.

Unit tests live in `app/src/test/`; native interaction tests and the screenshot walkthrough live in `app/src/androidTest/`.

## Current boundaries

- **Local only:** no backend, cloud sync, GitHub integration, real authentication, reminders, or notifications.
- **Not a secure journal:** workspace data is plaintext JSON in app-private storage, not application-encrypted. The debug APK is debuggable. Backup/device-transfer exclusions are configured, but exports are ordinary plaintext files.
- **Export, not import yet:** Android's document picker writes validated v2 history, schedules, calendar/preferences and profile. No credentials/sign-in flag. Restore UI is Task 12. Exports are plaintext; a cloud-backed provider may sync them independently.
- **One workspace per installation:** reset preserves the previous workspace app-privately before verified replacement; clearing app data/uninstalling removes app-private data and preservation copies. External exports are not deleted by reset.
- **Small saved UI state:** routes/drafts/selection/scroll use saved state; records reload from disk. Undo remains bounded in memory and does not survive process death.
- **Intermediate local build:** not a Play Store release or final personal-use handoff. Full TalkBack, older-device, performance and transfer coverage is pending; see dated evidence rather than historical screenshots.

Build reference: [Android command-line builds](https://developer.android.com/build/building-cmdline) · [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

## Task 11: Insights and history

Insights uses exact resolved calendar periods, including short calendar bridges;
Previous/Next moves between those periods rather than a rolling seven-day window.
The overview contribution heatmap now counts **recorded commits**, not the
prototype's cross-repo average goal score. Partial and optional activity count as
activity, not goal achievement. Per-repo heatmaps retain capped numeric daily
target progress. Charts are noninteractive; **Explore by date** provides exact
amounts, eligibility, weekly quota/practice context, and editable stored commits.

Day adherence counts achieved/assessed habit-dates for daily and weekday schedules.
Week adherence counts achieved/assessed full habit-weeks for weekly quotas. These
denominators are never combined. Pending, future, transitional and not-applicable
results are excluded; an achieved open date/week may count. No assessed results
means unavailable, not 0%. Activity streaks remain a separate metric.

History orders commits by recording timestamp, newest first, while stored local
dates determine credit. Inspection includes retained future-dated records without
early goal credit. Correcting, deleting, or cancelling a correction returns to the
inspected date; stored expectations remain unchanged.
