# Task 7 — local entry and preferences

Work started **2026-11-08**, from `d902697`. No physical phone or personal
workspace is used. This remains an intermediate build, not a final handoff.

## Local entry / fixture isolation

- Removed production `SampleWorkspace` and every auth/sample entry command and UI.
  JVM callers use `src/test/.../domain/SampleWorkspace.kt`; device journeys use the
  smaller `src/androidTest/.../fixtures/WorkspaceFixtures.kt` before Activity launch.
  The existing fail-closed device guard still precedes storage access.
- One welcome explains repos/commits, with Create first repo and Open empty
  workspace. Onboarding is persisted/verified before entering; no records are
  generated. Populated workspaces bypass welcome independently of onboarding.
- Loading/recovery gates and draft holders remain. No records or profiles are
  filtered by sample-looking IDs or content; legacy `signedIn` decoding remains
  compatibility-only.
- Removed fabricated blank-description fallback and visible demo/prototype copy.
- Version **1.1-local**, code **2**, replaces 1.0-demo/code 1. This names the local
  development line, not a readiness certification. Application ID, package, schema,
  signing configuration, and dependency versions are unchanged, preserving ordinary
  same-key update compatibility. No reinstall on a personal device is authorized.

Step-one verification:
`./gradlew :app:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
--console=plain --no-daemon` passed (`/tmp/opencode/task7-entry-verified.log`).
240 JVM tests per variant, zero lint errors; instrumentation compiled, not yet run.
New command regressions cover verified welcome, duplicate/no-op entry, retry and
unchanged history. Initial compilation found an ambiguous wildcard `R` import;
an explicit app-resource import resolved it.

Step 2's System/Light/Dark, calendar, and optional-profile settings work remains
pending; the Boolean theme-switch adapter is deliberately retained for now.
Artifact inspection and device verification have not been performed for this step.
Schedule forms (Task 9), Today/Insights honesty and layout (Tasks 10–11), and
import/reset enhancements (Task 12) are not claimed here.
