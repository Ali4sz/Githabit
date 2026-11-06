# GitHabit — native Kotlin mock-up

A standalone Android prototype built with **Kotlin, Jetpack Compose, and Material 3**. GitHub-inspired charcoal surfaces, green contribution grids, and repo/commit language turn daily habits into a personal changelog. This is a native app, not a WebView; the [web prototype](../README.md) is unchanged.

**Current checkpoint (2026-11-06):** Task 5 command/storage work is verified, including
[22 tests on an explicitly authorized physical phone](docs/phone-verification.md).
This remains a sample-bearing intermediate build, not the real-data-only personal-use
handoff. [Task 5 evidence](docs/task5-verification.md) and the
[build plan](../.plans/githabit-android-build-plan.md) describe the completed work and remaining gates.

## Try it

- **Installable debug APK:** [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk) (~18 MB; Android 8.0/API 26 or newer).
- **Fastest walkthrough:** launch **GitHabit**, then tap **Explore with sample habits**. A fresh workspace gets six habits and sample history.
- **First-run flow:** choose **Continue with Google** (marked DEMO), or use made-up email/password details, then follow the three onboarding steps and create a repo.
- **Start over:** Settings → **Reset this prototype**. Reset clears the local workspace after confirmation.

**Sign-in is simulated.** There is no Google OAuth, account creation, or identity verification. Email/password inputs are neither saved nor sent. All demo users on an installation share the same local workspace; signing out does not delete it. Never enter real credentials or sensitive data.

### Screenshots

Actual Android emulator captures, not design renders:

| Screen | Preview | Screen | Preview |
| --- | --- | --- | --- |
| Sign-in | [Open](previews/01-sign-in.png) | Onboarding | [Open](previews/02-onboarding.png) |
| Today, dark | [Open](previews/03-today-dark.png) | Repos | [Open](previews/04-repos.png) |
| Repo detail | [Open](previews/05-repo-detail.png) | Log a commit | [Open](previews/06-commit-sheet.png) |
| Day explorer | [Open](previews/07-day-details.png) | Insights | [Open](previews/08-insights.png) |
| Activity | [Open](previews/09-activity.png) | Settings | [Open](previews/10-settings.png) |
| Today, light | [Open](previews/11-today-light.png) | Create a repo | [Open](previews/12-new-repo.png) |

## What works

- **Today:** daily totals, overall contribution heatmap, remaining-goal filter, and quick completion.
- **Repos:** search/filter habits; create and edit names, descriptions, daily targets, units, icons, and colors; pause, archive, and resume.
- **Repo detail:** per-habit heatmap, progress, history, and commit editing/deletion.
- **Commits:** positive fractional amounts, optional messages, multiple logs per day, and snackbar Undo for logging, correction, deletion, completion, and status changes.
- **Day explorer:** navigate earlier dates or enter a date to inspect goals and commits.
- **Insights and Activity:** progress summaries and the commit history.
- **Settings:** editable display name/bio, persistent light/dark theme, guide, JSON export, sign-out, and confirmed reset.
- **Native behavior:** system back navigation, keyboard-aware forms, system insets, Android document picker, and retained tab state while navigating.

### Tracking rules

A repo is a habit with a numeric daily goal. A commit records an amount toward it. Quick completion adds **only the remaining amount**, not another full target. You can log beyond the target, but each repo's score caps at 100%; overall scores average the repos active on that date. A day with no scheduled repos has no score.

Target and status changes take effect today; earlier days retain their historical goals/status. Units are locked after the first commit. Activity streaks count days with any commit, not only days with fully completed goals. New commits use today’s local date; existing historical commits can be corrected or deleted, but the UI does not create backdated entries.

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

The output is `app/build/outputs/apk/debug/app-debug.apk`. It is signed with a **debug key**, suitable for trying the mock-up, not production distribution. On Windows use `gradlew.bat` and configure your SDK path via Android Studio or `ANDROID_HOME`.

With an emulator running or a USB-debugging-enabled device connected:

```bash
./gradlew :app:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n com.githabit.prototype/.MainActivity
```

To install the already-built APK instead:

```bash
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
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
- `GitHabitViewModel.kt` — navigation/overlays, serialized workspace mutations, persistence coordination, Undo, demo sign-in, reset, and export.
- `domain/Habits.kt` — immutable models, validation, historical goal/status versions, tracking calculations, and sample data.
- `data/WorkspaceJson.kt` — versioned, validated JSON serialization.
- `data/WorkspaceStore.kt` — app-private atomic-file persistence.
- `ui/GitHabitApp.kt` — scaffold, tab-state retention, back handling, system bars, snackbars, and document picker.
- `ui/Screens.kt`, `ui/Sheets.kt`, `ui/Components.kt`, `ui/Theme.kt` — screens, forms/dialogs, heatmaps/shared components, and Material 3 themes.

Unit tests live in `app/src/test/`; native interaction tests and the screenshot walkthrough live in `app/src/androidTest/`.

## Prototype boundaries

- **Local only:** no backend, cloud sync, GitHub integration, real authentication, reminders, or notifications.
- **Not a secure journal:** workspace data is plaintext JSON in app-private storage, not application-encrypted. The debug APK is debuggable. Backup/device-transfer exclusions are configured, but exports are ordinary plaintext files.
- **Export, not import:** Android's document picker writes a snapshot with the sign-in flag cleared. There is no restore/import UI. Habit names, notes, history, and profile fields are included, so choose the export destination carefully; a cloud-backed document provider may sync it independently of this app.
- **One workspace per installation:** sign-out keeps it; reset, clearing app data, or uninstalling removes it. Exported copies are not deleted by reset.
- **Navigation is not a persisted session:** a fresh activity launch returns to Today after onboarding; workspace contents and theme persist. Undo history does not survive a fresh process.
- **Phone-first mock-up:** suitable for exploring the flows, not a production or Play Store release. The pinned toolchain/target should be revisited before distribution.

Build reference: [Android command-line builds](https://developer.android.com/build/building-cmdline) · [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
