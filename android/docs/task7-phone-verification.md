# Task 7 — authorized physical-phone follow-up

Verified **2026-11-08**, production/test source **`df0c310`**. This supplements
[Task 7 emulator verification](task7-verification.md); it does not replace that
evidence or constitute final whole-app accessibility certification.

## Authorization and isolation

- The owner selected **Automated tests**, explicitly authorizing installation of
  the app/test APKs and creation, replacement and reset of **only GitHabit data**.
  No prior phone authorization or emulator token was reused.
- Read-only discovery found one authorized USB phone: **CPH2749, Android 17/API 37,
  current user 0**. GitHabit was absent, including retained uninstall records; an
  older `com.githabit.prototype.test` package remained. Its presence was not treated
  as reset consent.
- Transport serial independently matched `ro.serialno` and `ro.boot.serialno`;
  AVD identity was absent. Exact serial and fresh UUID tokens are not committed.
  All device commands selected that serial and installation/testing used user 0.
- Phone was awake/unlocked. No screenshots, screen recordings, unrelated logs,
  other-app operations, uninstall, or device-wide setting changes were performed.
- Physical guard authorization disables both full-device and Compose-root capture.
  All six `ThemeRenderingTest` tests were excluded because that class requires an
  emulator before any pixel-capture work. The flow named `screenshotJourney...`
  exercised navigation without capture, as required by its physical-device guard.

## Artifacts and execution

Reused the exact APKs already built, audited and emulator-tested for `df0c310`.
No production/test code changes or fresh build were necessary. Host hashes were
checked before installation; each installed `base.apk` subsequently matched:

| Artifact | SHA-256 |
| --- | --- |
| Debug app, `com.githabit.prototype`, 1.1-local/code 2 | `4b315fb34febf9f15f485d759cbb30686be25051b830af41bc49bde594e4fd74` |
| Separate test APK, `com.githabit.prototype.test` | `625be60d2b3f8dd030b978eb2d4ae723f7e071f73c2ae42317bf53794b197ef2` |

Installation used `adb -s "$DEVICE" install --user 0 -r` for the two known APKs.
Testing used direct instrumentation, **not Gradle connected tests**, avoiding the
Gradle `animationsDisabled` device-setting changes.

| Check | Result |
| --- | --- |
| Non-destructive guard contracts, no reset token | 5/5 passed, 0.026s |
| Actual FlowTest setup with no opt-in/marker | Expected guard rejection; 1 expected failure; primary and `.new` synthetic sentinel SHA-256 unchanged |
| Phone-compatible suite | **30/30 passed**, 63.607s, 19:00:24–19:01:29 +03:00 |
| Final in-app reset/recreation journey | 1/1 passed, 2.286s, 19:03:57–19:04:00 +03:00 |

The 30-test suite contains 2 command-form, 6 flow, 7 preference, 10 storage/recovery,
and 5 guard tests. The separate guard and final-reset runs repeat existing tests;
they do not increase unique phone coverage above 30. No test failed in the authorized
suite. The deliberate unauthorized run is a safety assertion, not an app defect.

Coverage includes unseeded first use, fractional logging/remainder completion,
Undo, corrections/deletion, historical targets/statuses, theme persistence,
optional profile and padded legacy names, all seven weekdays, pending replacement/
cancellation, fixed bridge dates, stale-date review, recovery and retained drafts,
reset, migration and Activity recreation. The preference test injects 2.0 font scale
and exercises programmatic focus/Enter locally; it does not change system font scale
or establish full hardware-keyboard/TalkBack traversal.

### Guarded invocation

The local runner `/tmp/opencode/run-task7-phone-tests.sh` rechecks exact serial,
user 0, absent AVD identity and absent old marker before issuing a fresh UUID marker.
Its explicit class allowlist excludes `ThemeRenderingTest`:

```sh
CLASSES=com.githabit.prototype.CommandFormTest,com.githabit.prototype.GitHabitFlowTest,com.githabit.prototype.PreferencesTest,com.githabit.prototype.WorkspaceRecoveryTest,com.githabit.prototype.fixtures.DisposableTargetTest
adb -s "$DEVICE" shell am instrument --user 0 -w -r \
  -e disposableDevice true -e allowPhysicalDeviceReset true \
  -e expectedTargetPackage com.githabit.prototype \
  -e expectedDeviceSerial "$DEVICE" -e disposableToken "$TOKEN" \
  -e class "$CLASSES" \
  com.githabit.prototype.test/androidx.test.runner.AndroidJUnitRunner
```

This is **not reusable authorization**. Follow the fresh consent/identity/marker
procedure in [phone-verification.md](phone-verification.md#safe-physical-phone-procedure).
The runner has EXIT/INT/TERM cleanup which stops both exact packages, removes the
marker, and verifies removal. Results require the expected test count and no
failure/crash status, not merely a zero shell exit code.

Local logs under `/tmp/opencode/`: `task7-phone-guard-contract.log`,
`task7-phone-denied-setup.log`, `task7-phone-suite.log`,
`task7-phone-final-reset.log` and matching console logs. They are ephemeral host
evidence, not app content or required source-tree assets.

## Cleanup and state left on the phone

- Test processes were stopped and the marker revoked after every authorized batch.
  Final absence was independently checked. No app screenshot directory was created.
- A cleanup attempt using `pm clear --user 0 com.githabit.prototype` was rejected by
  the phone with `SecurityException` / `CLEAR_APP_USER_DATA`. No security setting,
  root command, uninstall or raw-file-deletion workaround was used.
- Instead, the already tested in-app reset journey was run with a **new** bounded
  authorization token. It verified cancelled reset preserves data, confirmed reset
  yields `Workspace()`, and recreation remains empty. App-private preservation copies
  of synthetic test data may remain; complete removal of all app storage is **not**
  claimed.
- Passive read-back confirmed the active workspace has **0 habits, 0 commits,
  blank name/bio and onboarding false**. The app was then deliberately opened for
  the owner: `am start --user 0 -W -n com.githabit.prototype/.MainActivity` returned
  `Status: ok` (cold launch, 602ms reported). This one launch is not a performance
  benchmark. Both APKs remain installed; no authorization marker remains.
- Before/after/final metadata matched: display 1272×2772px, density 560dpi, system
  font scale 1.0, window/transition animation scales 1.0/1.0, animator-duration
  setting unset. No device clock/timezone/font/display/animation changes were made.

Still unverified on this phone: pixel/contrast checks, screenshots/visual review,
TalkBack and full keyboard traversal, system 200% scaling, adaptive/landscape matrix,
process death distinct from recreation, long-history performance/capacity and final
document-transfer journeys. Later Tasks 8–15 remain unchanged in scope. No source
publication or APK upload occurred.
