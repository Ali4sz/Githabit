# Task 13 Remediation Plan — Capacity OOM, Modal Localization, and Typed Diagnostics

## Context

Task 13 (capacity/long-history certification) exposed three defect classes that must be fixed
without changing the app's architecture or weakening its data-preservation guarantees:

1. **Capacity OOMs.** Near the 32 MiB byte limit, encoding, restore verification, fingerprinting,
   and storage verification hold several whole-document representations simultaneously and hit
   the observed 192 MiB process heap instead of either serializing losslessly or returning
   `CAPACITY`. This violates the core contract: *every valid workspace either round-trips losslessly
   or is rejected before exhausting the heap.*
2. **Modal localization gap.** The test harness overrides only the outer composition's locale and
   font scale; AndroidX dialogs establish their own context/configuration/density from the host
   view, so modal body/count/action text and 200 % modal font coverage were not actually under test.
3. **Incomplete typed diagnostics.** Interpolated notices, `WorkspaceLoad.Recovery.reason`, and the
   bottom-sheet semantic `stateDescription` bypass the existing string→resource mapper and render
   English verbatim.

This plan is the remediation layer for those defects. It is deliberately **not** a certification of
minimum hardware or of assistive technology; those remain separate, explicitly open gates (see
"Open Certification Gates" at the end).

The changes below were all still **uncommitted** when this plan was written; they are now
committed together with the remediation. The completed Tasks 1–12 end at commit
`64edf127e83effa03cee75999f3a42bd7be13ef3`. Every fix here must be an atomic, focused change that
preserves the existing Task 13 production, test, and documentation work.

---

## Requirements

### Confirmed scope
- Personal, local-first native Compose app; single workspace; `AtomicFile` + `WorkspaceCopies`
  recovery seam is the only storage ownership boundary.
- Preserve all valid records — mixed/edited `sample-*` IDs, legacy whitespace/multibyte notes,
  future-dated history, pending calendar transitions with preserved historical periods.
- No demo/auth/generator paths, no fabricated records, no shipped fixture/seed endpoints.
  Test-only fixtures stay isolated in `androidTest`.
- Provisional limits stay **unchanged**: 33,554,432 UTF-8 bytes; 1,000 habits; 100,000 commits;
  100,000 configurations; 128-code-unit IDs; root-inclusive depth 8; no temporal-span bound.
- Solutions must be in-line with the existing `StrictJson`/`BoundedJsonWriter` codec, the
  `WorkspaceFileAccess` seam, and the design tokens (48 dp targets, `focusOutline`, scrollable
  transfer dialogs, IME padding, locale-aware display). No third-party JSON/pub library, no database
  or framework rewrite, no dependency upgrade, no lowering limits, no trimming compatible data,
  no weakening assertions, no catching OOM as success.

### Key assumptions
- The canonical encoding is deterministic (fixed field order, fixed number formatting). Any change to
  byte *representation* is forbidden; only *where bytes are buffered/copied* and *when the digest is
  computed* may change.
- The SHA-256 fingerprint format `[0-9a-f]{64}` remains load-bearing for `transfer.replacements`
  markers and must be byte-stable across this refactor.
- Roundtrip lossless verification of a restore candidate is **not** optional; it may be made bounded,
  but it may not be deleted or converted to "hash matches".

### Out of scope
Database migration; dependency upgrades; arbitrary limit reduction; trimming compatible legacy data;
weakening assertions; treating OOM as expected success; phone operations; publishing/uploading;
personal-use handoff.

---

## Research Summary

### Fix viability and grounding

**1. Encoder amplification (viable, high confidence).**
Verified in `StrictJson.kt:200–275`: `BoundedJsonWriter` accumulates in a `StringBuilder` while
separately counting UTF-8 bytes (`raw()` at 267–272). A near-32 MiB ASCII payload keeps a ~64 MiB
UTF-16 char buffer (the observed ~134,216,200-byte failure), and `WorkspaceJson.encode` then calls
`.toString()`; callers frequently `.toByteArray(Charsets.UTF_8)` that result, adding yet another full
allocation. The fix is to emit UTF-8 bytes directly (one byte per byte, no 2× char amplification)
and to compute the SHA-256 digest during emission instead of a post-`.toByteArray()` pass. Because
`raw()` already performs the exact escaped UTF-8 byte count *before* growth, the count logic is a
design asset and must be reused as the single cap — only the sink changes.

**2. Probe-pass allocation (viable, medium confidence).**
Verified in `WorkspaceJson.kt:27–53` (probe pass) and `StrictJson.kt:24–35` (`skipValue`):
`skipValue()` dispatches `'"' -> readString()`, materializing every skipped string, and
`WorkspaceLimits.decodeUtf8` creates a whole-document `String` representation. A non-allocating
`skipString()` mirroring the exact `string()` state machine removes the discarded-value allocation.
Unpaired-surrogate validation does **not** need to be duplicated: `StrictJson.init` already runs
`WorkspaceLimits.utf8Size(text)` over the entire input and rejects unpaired surrogates globally
(verified at `StrictJson.kt:12–15`). The skip path must still validate escapes, control characters,
structure, depth, and duplicate keys identically to a read.

**3. Transfer/fingerprint serialization amplification (viable, high confidence).**
`RestoreDocument.read()` (`WorkspaceTransfer.kt:16–27`) decodes, then `encode` (full `String`), then
`decode(encoded)` (second whole-workspace materialization), then `workspaceDigest(encoded)` (another
`toByteArray`). `workspaceFingerprint` (`GitHabitViewModel.kt:730`) re-encodes canonical v2 output
for replacement markers; `prepareExport` (`GitHabitViewModel.kt:1041–1052`) performs a throwaway
`WorkspaceJson.encode(snapshot)` whose result is discarded, then `writeExport` calls
`WorkspaceJson.export` which encodes again (`GitHabitViewModel.kt:1075`). Centralizing on a byte
sink with an incrementally captured digest collapses these into one emission per operation.

**4. Storage snapshot copy amplification (viable, requires the ownership table first).**
`WorkspaceStore` retains `lastGoodBytes`, `persistedLastGoodBytes`, `original`, and
`uncertainCandidate` and defensively `copyOf()` at several points (`load` 92/94/114, `committed`
211, `indeterminate` 225). `preserve()` (255–258) adds a full read-back via `readCopy(file)` on top
of `WorkspaceCopies`' already-internal read-back/fingerprint verification (`WorkspaceCopies.kt:166–204,
221–235`). These are real, removable allocations, but each removal must be proven safe by a
snapshot-lifetime/ownership table before any edit.

**5. Modal localization bypass (viable, high confidence — root cause confirmed against the pinned
artifact).**
The Gradle cache contains Compose UI **1.9.2** (`ui-android/1.9.2/…/ui-android-1.9.2.pom`). The
release source at `b6177e34a7667c42030e47ee8ecba82f09e2a5de` shows `Dialog` reads `LocalView.current`
then wraps *that view's context*; the dialog composition re-supplies its own context/configuration;
`DeviceConfigurationOverride.OverriddenConfiguration` wraps `LocalContext.current` in a
`ContextThemeWrapper` — i.e., **composition-local** overrides only, not a host-view configuration.
`AccessibilityLayoutAuditTest.kt:59–66` overrides exactly those outer composition locals, while
`TransferDialogs` resolves the dialog *title* outside the `Dialog` and the body/plural/action strings
inside it. This fully explains the observed "title localized, body English" split and puts modal
200 % font coverage in doubt. The fix is a host-configuration-driven harness plus assertions *inside*
the modal window — not changing production localization code on this evidence alone.

**6. Typed diagnostics (viable, medium-large surface).**
`DiagnosticMessages.kt:9–43` matches only complete English literals; interpolated notices (e.g.
`"Today’s remaining target saved for ${action.habit.name}."` at `GitHabitViewModel.kt:891`, dated
notices elsewhere), `RecoveryScreen` rendering `state.reason` (`Screens.kt:33`), and `WorkspaceSheet`
exposing the English enum `sheetState.currentValue.name` (`Sheets.kt:68`) all bypass it. The fix is a
small typed message-identity model with structured arguments resolved to resources at the UI
boundary — not a translation framework.

---

## Work Breakdown

### Task 1: Make `BoundedJsonWriter` emit UTF-8 bytes with an incremental digest and a count-only mode

- **Description**:
  Refactor the writer so the *sink* (destination of bytes) is pluggable, while the *emission logic*
  (escaping, field order, number formatting, the existing `byteCount` accounting) is shared and
  byte-identical across all sinks. Concretely:
  1. Replace the `private val output = StringBuilder()` at `StrictJson.kt:202` with a small private
     sink abstraction. Three concrete sinks:
     - `ByteSink` — appends UTF-8 bytes into an exact/`ByteArrayOutputStream` buffer and, when a
       digest is requested, feeds each chunk to a `MessageDigest` (SHA-256) as it is appended.
     - `CountingSink` — validates every `raw()` cap and advances `byteCount` but discards bytes
       (validation-only; used by export preflight in Task 4).
     - (Optionally) `StreamSink` — writes bytes to a caller `OutputStream` as they are emitted,
       used directly by `export` so a full in-memory buffer is never built on the export path.
  2. `raw(value, start, end)` (`StrictJson.kt:267–272`) keeps its `utf8Size` check and `byteCount`
     update unchanged, then calls `sink.writeUtf8(value, start, end)` instead of
     `output.append(value, start, end)`.
  3. Expose:
     - `internal fun byteCount(): Long` — exact final UTF-8 size (already tracked).
     - `internal fun toByteArray(): ByteArray` — exact-sized result for callers that need a `String`
       (allocates `ByteArray(byteCount.toInt())` precisely, no doubling and no final `.toByteArray`
       copy).
     - an internal `encode(state, sink)` entry point so the digest and the serialization are produced
       in a single pass.
  4. Keep the `String` convenience path (`toString()`) only for callers that genuinely need a
     `String` (tests, small diagnostics); production save/export/hash paths must not route whole
     32 MiB documents through it.
- **Files/Components**: `android/app/src/main/java/com/githabit/prototype/data/StrictJson.kt`
  (`BoundedJsonWriter`), plus new callers in `WorkspaceJson.kt`.
- **Dependencies**: none (foundation for Tasks 3 and 4).
- **Acceptance Criteria**:
  - Populated 32 MiB−1 and 32 MiB documents encode losslessly (byte-for-byte).
  - 32 MiB+1 returns `WorkspaceJsonFailure.CAPACITY` with **no** OOM; the destination is never opened.
  - A golden/small-cases test asserts the canonical bytes are **unchanged** from the pre-change
    `String` encoding (byte-stability gate for the fingerprint).
  - Digest computed during emission equals `SHA-256(bytes)` computed independently.
  - Unpaired-surrogate and illegal-control-character inputs still reject identically.
- **Estimated Complexity**: Medium.

### Task 2: Non-allocating `skipValue()` in the strict reader

- **Description**:
  Add a `private fun skipString()` to `StrictJson` that walks a JSON string from the opening quote
  to the closing quote validating the exact same grammar as `string()` (`StrictJson.kt:119–157`):
  the same escape set (`\" \\ \/ \b \f \n \r \t \uXXXX` with exactly four hex digits), the same
  unescaped-control-character rejection (`char.code < 0x20`), and the same unterminated-string error,
  but with **no** `StringBuilder` and no value retained. Change `skipValue()` (`StrictJson.kt:24–35`)
  so the `'"'` branch calls `skipStringValue()` (a `beginValue()` + `skipString()`) instead of
  `readString()`. Do not duplicate surrogate validation: `StrictJson.init` already rejects unpaired
  surrogates across the whole input. Ensure duplicate-key detection is unaffected (object keys are
  still materialized via `string()` in `readObject`).
- **Files/Components**: `android/app/src/main/java/com/githabit/prototype/data/StrictJson.kt`.
- **Dependencies**: none (parallelizable with Task 1).
- **Acceptance Criteria**:
  - Existing `StrictJsonTest` and all v1/v2 migration, capacity, and legacy-fixture assertions pass.
  - New cases: (a) a large skipped string does not allocate a proportional char buffer (assert via
    a counting test double or a coarse memory ceiling in the existing capacity suite); (b) malformed
    escapes, truncated `\u` sequences, and unescaped control characters inside a *skipped* value are
    rejected with the same kind/position as when *read*; (c) duplicate keys spelled via escapes and
    version-last documents are still detected.
- **Estimated Complexity**: Small.

### Task 3: Single-serialization restore and fingerprint path

- **Description**:
  Remove the repeated encode/decode/hash in the transfer and replacement-marker paths. Rely on Task 1's
  digest-capturing byte sink:
  1. Add `internal fun encodeToBytes(state: Workspace): ByteArray` and an
     `internal fun encodeAndDigest(state: Workspace): Pair<ByteArray, String>` where the digest is
     captured *while* the bytes are emitted (single pass, no post-encode `.toByteArray()`).
  2. Change `workspaceDigest(encoded: String)` (`WorkspaceTransfer.kt:31–32`) to operate on the already
     captured digest/bytes (`digestOf(bytes)`), never a fresh `String`, and mark the `String` overload
     removed or test-only.
  3. Change `RestoreDocument.read()` (`WorkspaceTransfer.kt:18–27`) to:
     - `bytes = WorkspaceLimits.readBounded(it)` (transient; freed at end of `use`);
     - `decoded = WorkspaceJson.decodeDocument(bytes)` (frees `bytes` as early as the API allows);
     - `(encodedBytes, digest) = encodeAndDigest(decoded.workspace)`;
     - a bounded lossless check `decode(encodedBytes) == decoded.workspace` where `decode` operates on
       the owned `encodedBytes` (no fresh copy); the second workspace and `encodedBytes` are released
       before returning;
     - `RestoreDocument(decoded.workspace, decoded.sourceVersion, digest)`.
  4. Change `workspaceFingerprint` (`GitHabitViewModel.kt:730`) to use `encodeAndDigest` and discard the
     bytes after the digest is taken (no retained second copy).
  
  **Roundtrip guardrail (decision point resolved in this task):** First measure whether the byte-sink +
  digest-reuse change alone brings the peak below the 192 MiB regression target. Only if it does **not**
  is a record-by-record streaming comparison (decode the owned bytes and compare each decoded record to
  the domain model without materializing a second full `Workspace`) authorized. Do not delete the
  roundtrip check under any circumstance.
- **Files/Components**: `WorkspaceTransfer.kt`, `WorkspaceJson.kt`, `GitHabitViewModel.kt`.
- **Dependencies**: Task 1 (digest-capturing sink).
- **Acceptance Criteria**:
  - All `WorkspaceTransferTest` and `WorkspaceTransferCommandTest` cases pass unchanged, especially:
    changed provider after preview; changed workspace requiring renewed consent; equal-value
    replacement clearing transient state only after verified commitment; frozen export surviving an
    intervening reset; failed preservation retaining candidate/draft/Undo; recreation not restoring
    consent.
  - The combined 1,000 repos / 100,000 commits / 100,000 configs restore candidate can be read and
    fingerprinted without OOM (previously failed requesting ~75,497,480 bytes).
  - Fingerprint byte-stability: `workspaceDigest` result identical before and after the refactor.
- **Estimated Complexity**: Medium.

### Task 4: Single-pass export freeze and stream

- **Description**:
  Eliminate the double encode in export while preserving the "freeze and reject before the picker"
  guarantee:
  1. In `prepareExport` (`GitHabitViewModel.kt:1041–1052`), replace the throwaway
     `WorkspaceJson.encode(snapshot)` with the Task 1 `CountingSink` validation (count/validate the
     canonical output, reject `CAPACITY` before opening the picker) — no retained serialization. Keep
     the `snapshot = workspace` freeze and `date = today` freeze exactly as-is.
  2. In `writeExport` (`GitHabitViewModel.kt:1068–1085`), change the workspace branch to stream the
     already-frozen `request.workspace` through the writer's `StreamSink` (or a byte-buffer path that
     writes in bounded chunks) into `openOutput()`, rather than `WorkspaceJson.export` building a full
     in-memory `String` first. The recovery branch (`request.recovery::writeTo`) is unchanged.
  3. Keep the committed-snapshot ownership guarantee: `request.workspace` is the frozen object; no
     re-read of storage and no re-encode-to-a-different-source may occur.
- **Files/Components**: `GitHabitViewModel.kt`, `WorkspaceJson.kt` (`export`).
- **Dependencies**: Task 1 (sinks).
- **Acceptance Criteria**:
  - An oversized/invalid snapshot is rejected at `prepareExport` and the picker is never opened.
  - Export of a committed workspace produces bytes identical to `encodeToBytes(workspace)` (no
    divergence between the two paths).
  - "Frozen export surviving reset before picker return" test (`WorkspaceTransferCommandTest`) passes.
- **Estimated Complexity**: Small.

### Task 5: Storage snapshot lifetime/ownership table, then remove redundant copies

- **Description**:
  Produce the ownership table **first** (a doc or leading comment + a review checklist), covering each
  field and temporary across the publish → candidate → previous → preservation → readback →
  rollback/indeterminate lifecycle. For every byte array, the table must state: owner, mutability,
  aliasing (which other references may still point at it), release condition, and restart requirement.

  Then, with the table as gate, make only these specific, individually-verifiable changes:
  1. `load()` (`WorkspaceStore.kt:92, 94, 114`): when `raw` is an exclusively owned fresh array,
     assign it directly to `lastGoodBytes`/`original` instead of `raw.copyOf()`. Only keep a copy where
     `raw` is also captured elsewhere in the same frame (it is not, in these paths).
  2. `committed()` (`WorkspaceStore.kt:211`) and `indeterminate()` (`WorkspaceStore.kt:225`): assign the
     exclusively owned `observed.bytes` directly instead of `copyOf()`.
  3. `preserve()` (`WorkspaceStore.kt:255–258`): rely on `WorkspaceCopies.write`/`writeStream`, which
     already read back and verify the copy internally; replace the additional
     `access.readCopy(file).contentEquals(bytes)` full-materialization read-back with a streaming
     equality/fingerprint comparison (or delete it where the copy method's own verification is provably
     sufficient). Do **not** change `WorkspaceCopies` behavior except to expose its verification result.
  4. `save()` (`WorkspaceStore.kt:156–159`): use Task 1's `encodeToBytes` (not `encode(...)` +
     `.toByteArray`), and avoid retaining `previous` beyond the stale-snapshot comparison unless the
     bytes actually differ (external-modification guard remains).
- **Files/Components**: `WorkspaceStore.kt`, `WorkspaceFileAccess.kt`/`WorkspaceCopies` (minimal),
  ownership table document (`.plans/` or `android/docs/`).
- **Dependencies**: Tasks 1 and 3 (byte-accurate encode), plus the ownership table as a blocking
  prerequisite within the task.
- **Acceptance Criteria**:
  - All `WorkspaceStoreTest`, `WorkspaceMigrationTest`, and `WorkspaceCapacityStorageTest` fault cases
    pass unchanged (disk-full/copy failure, interrupted write, changed readback, rollback failure,
    restart, preservation-failure, first-write-with-unverifiable-content).
  - New bounded-memory aliasing tests prove no removed `copyOf()` introduces shared-mutable state.
  - NOT accepted if any snapshot is removed without the table proving it is not also recovery/export
    evidence. Export snapshots must never reference a rotating "last-good" file.
- **Estimated Complexity**: Medium (higher for the ownership table; the edits are small).

### Task 6: Host-configuration-driven modal localization harness and in-dialog assertions

- **Description**:
  Fix the localization test so modal content is genuinely covered, without changing production
  localization code first:
  1. In `AccessibilityLayoutAuditTest`, replace (or augment) the outer `CompositionLocalProvider`
     locale/font override (`line 59–66`) so the **host Activity's view/context configuration** carries
     the intended locale, font scale, and layout direction before content is composed — e.g., apply an
     override via `activity.createConfigurationContext(...)` and a themed/configuration context at the
     `setContent` host so `Dialog`'s `LocalView.current` inherits it. Keep the existing
     `LocalActivityResultRegistryOwner` fix (pseudolocale setup must continue to work).
  2. Add explicit assertions **inside** `Dialog` and `ModalBottomSheet` composables (or their content)
     for resolved resource text, plural formatting, locale-aware count formatting, layout direction,
     and scaled `Density(fontScale)` — covering the transfer restore/reset preview dialog and the
     workspace sheet. Where in-composition assertion is impractical, assert on the rendered semantics
     text of the dialog window's root.
  3. Route both pseudo-locales (en-XA, ar-XB) and the 200 % font scale through the modal journeys.
  4. Only if a genuine defect reproduces through the real configuration path (not the old harness)
     is production `TransferDialogs` configuration handling to be changed; that change is out of scope
     of this task and must be a separate, justified task.
- **Files/Components**: `android/app/src/androidTest/java/com/githabit/prototype/AccessibilityLayoutAuditTest.kt`
  (harness), possibly `TransferDialogs.kt` test tags only.
- **Dependencies**: none (independent).
- **Acceptance Criteria**:
  - Modal title, body, plurals, count, and action buttons render consistently under en-XA/ar-XB.
  - 200 % font-scale modal content is actually exercised and asserted.
  - Existing form/input pseudo-locale journeys still pass at 360×800/320×640/640×360/1200×800 dp.
  - Actual TalkBack and hardware-keyboard usability remain **explicitly unverified** (semantic
    assertions do not claim them).
- **Estimated Complexity**: Medium.

### Task 7: Typed message-ID diagnostics with structured arguments

- **Description**:
  Replace raw `String` diagnostics that encode at the presentation boundary with typed identities and
  structured arguments resolved to resources:
  1. Introduce a small `sealed interface NoticeMessage` / diagnostic-id type with structured arguments
     (dates as `LocalDate`, repo names as identifiers, counts as `Int`, technical/provider details as an
     opaque verbatim field). Keep it deliberately small; no localization framework.
  2. Change `Notice(message: String, …)` (`GitHabitViewModel.kt:58`) to carry the typed message key +
     args, and migrate every `noticeChannel.trySend(Notice("…"))` call site (including the interpolated
     ones such as `GitHabitViewModel.kt:891`, dated notices at `status`/`undo`, etc.) to the typed form.
     This is mechanical: the *same* string identity and *same* argument values must be preserved — no
     behavior change to keys/messages.
  3. Extend `DiagnosticMessages.kt` to resolve the typed IDs (including plural resources) at the UI
     boundary, keeping the existing literal `diagnosticMessage(String)` mapper only as the
     legacy/unknown fallback.
  4. Make `WorkspaceLoad.Recovery.reason` structured (kind + optional params) so `RecoveryScreen`
     (`Screens.kt:33`) renders it through resources instead of the raw `String`; keep any
     provider-specific undecoded detail as an explicit verbatim sub-field.
  5. Replace the English enum name in `WorkspaceSheet`'s `stateDescription`
     (`Sheets.kt:68`) with a resource-backed description keyed by `SheetState` value.
  6. Continue bounding any *rendered* preview of a huge compatible string without mutating the stored
     original (see "Performance / UX cross-check" note).
- **Files/Components**: `GitHabitViewModel.kt` (Notice + call sites), `ui/DiagnosticMessages.kt`,
  `ui/Screens.kt`, `ui/Sheets.kt`, `res/values/strings.xml` (new/plurals entries).
- **Dependencies**: none (independent; largest surface — pair with Tasks 1–5 in parallel where possible).
- **Acceptance Criteria**:
  - Locale changes update localized presentation without changing JSON identity, stored dates, decimal
    input rules, or notice keys/parameters.
  - `RecoveryScreen` and the sheet `stateDescription` render localized text; unknown external details
    remain verbatim and explicitly bounded.
  - All existing exact business assertions pass unchanged.
- **Estimated Complexity**: Medium.

### Task 8: Reproduce, verify, and refresh suites

- **Description**:
  After Tasks 1–7 land, re-run the full verification ladder with **never the pre-change green subsets**:
  1. Reproduce each capacity case through production workflows (startup/save/export/restore/reopen),
     instrumented for app-only heap/PSS (fixture generation concluded before measurement).
  2. Re-run every independent boundary (repo/ID/depth/commit/configuration) and combined-pressure case;
     confirm 32 MiB±1 behavior and combined 1,000/100,000/100,000 restore.
  3. Re-run the fresh animation-mode suites (both modes) and the long-history lifecycle
     (36,520 commits / 41 configs / 5,363,134 bytes) end-to-end.
  4. JVM tests per debug/release variant, lint, and debug + unsigned-release + test-APK builds; refresh
     artifact/backup/fixture-exclusion and installed-identity audits.
  5. Re-run the Task 6 localization journeys at all four window sizes with the new in-dialog assertions.
  6. Update `android/docs/task13-verification.md` and `task13-methodology.md` with fresh hashes,
     caveats, and the corrected measurement scopes.
- **Files/Components**: docs, `androidTest` fixtures/metrics, no production logic.
- **Dependencies**: Tasks 1–7.
- **Acceptance Criteria**: capacity failures replaced by lossless success or correct rejection; all
  fault guarantees still hold; localization coverage demonstrably real; fresh evidence cleanly separates
  passed checks from unverified gates.
- **Estimated Complexity**: Large (breadth, not depth).

### Task 9: Documentation finalization and open-gates statement

- **Description**: Record the remediation outcome, the ownership-table rationale, and the two explicitly
  open certification gates (minimum hardware; actual assistive technology) with the conditions that
  would close them.
- **Files/Components**: `android/docs/task13-verification.md`, README if referenced.
- **Dependencies**: Task 8.
- **Estimated Complexity**: Small.

---

## Execution Order

```
Task 1 ──▶ Task 3 ──▶ Task 4 ──┐
   │            │              │
   │            ▼              │
Task 2      (Task 5 depends on 1 & 3, and its ownership table) ──┐
(parallel)                                                       │
Task 6 (parallel; independent) ──────────────────────────────▶ Task 8 ──▶ Task 9
Task 7 (parallel; independent, largest surface) ───────────────┘
```

- **Wave A (parallel):** Task 1, Task 2, Task 6, Task 7 — independent foundations.
- **Wave B:** Task 3 and Task 4 (depend on Task 1); Task 5 (depends on Task 1/3 output and its own
  ownership-table prerequisite).
- **Wave C:** Task 8 (depends on all), then Task 9.

No circular dependencies exist. Task 3's "streaming verification" fallback is gated behind a
measurement decision inside Task 3 and does not block Tasks 4–5 unless peak memory still exceeds target.

---

## Risks & Mitigations

- **Risk:** Changing the writer sink skews canonical bytes. → Mitigation: byte-stability golden test in
  Task 1; single shared emission path; digest equality against independent `SHA-256` in acceptance.
- **Risk:** `skipValue()` divergence rejects/accepts different inputs than `readString()`. → Mitigation:
  mirror the exact state machine; cross-tests with malformed skipped values at identical positions.
- **Risk:** Removing storage copies drops recovery/rollback evidence. → Mitigation: ownership table is
  mandatory before any edit; all fault tests must still pass; aliasing tests added.
- **Risk:** Roundtrip verification weakens under memory pressure. → Mitigation: roundtrip is never
  deleted; bounded only if measurement proves necessity; `WorkspaceTransfer*` corpus is the invariant.
- **Risk:** Host-configuration harness still doesn't reach dialogs. → Mitigation: assert *inside* the
  dialog content and on rendered semantics; the pinned 1.9.2 source already confirms the mechanism, so
  the fix targets the host view context directly.
- **Risk:** Typed-diagnostics migration silently changes a message or key. → Mitigation: no behavior
  change to identity/args; exact-business-assertion suite must remain green; unknown details stay verbatim.
- **Risk:** Measurement still conflates instrumented/test retention with app-only. → Mitigation: fixture
  generation ends before sampling; app-only heap/PSS sampling is a hard requirement in Task 8.

---

## Decisions Log

- **Byte-accurate writer + incremental digest + count-only/stream sinks** — chosen because the existing
  unit already counts bytes before growth; only the sink changes, preserving canonical identity and
  eliminating the UTF-16 amplifier and the hash `.toByteArray()` copy.
- **Non-allocating skip path, reuse existing scope of the strict reader** — chosen over a byte/chunked
  streaming parser to avoid Unicode-boundary/`position` complexity while removing the identified
  `skipValue` allocation; surrogate validation stays global in `StrictJson.init`.
- **Restore/fingerprint single serialization, keep roundtrip** — the fingerprint must stay byte-stable
  for `transfer.replacements`; lossless verification is retained and may only become bounded under
  measurable headroom pressure, never deleted.
- **Snapshot-lifetime table precedes snapshot edits** — the single storage ownership boundary and
  `WorkspaceFileAccess` seam are fixed; the table is the only safe way to remove defensive copies.
- **Modal localization fixed in the harness + in-dialog assertions, not production** — the pinned
  Compose source proves composition-local overrides do not cover `Dialog`; production `TransferDialogs`
  changes require real-configuration reproduction.
- **Typed message-identity model, no framework** — bounded coverage is the goal; unknown/external
  diagnostics remain verbatim.

---

## Open Certification Gates (not part of this remediation)

- **Minimum supported device/RAM/heap:** still unspecified. The 192 MiB observed heap is a regression
  environment, not a supported-device claim. API 26 alone is insufficient; this gate closes only with a
  named minimum hardware definition and app-only cold-start/loaded PSS evidence on it.
- **Assistive technology:** actual TalkBack and hardware-keyboard journeys remain unverified; semantic
  and direct-action assertions do not establish usability. This gate remains explicitly open, with no
  phone operations and a fresh disposable-target guard if emulator testing later resumes.