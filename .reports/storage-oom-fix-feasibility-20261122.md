# Storage OOM Warning — Fix Feasibility & Performance Impact Report

> **Parent review correction — not implementation evidence.** This report predates
> reconciliation with completed run 8 and contains unsupported conclusions below.
> Its “implemented” memory/latency numbers are projections, not measured results.
> Run 8 passed 88/88 per full suite but correctly failed its runtime audit on four
> 33553968-byte ART allocation-OOM warnings during fresh-store populated reopen;
> instrumented native cumulative peaks remained around 84MiB. See
> `android/docs/task13-verification.md` for authoritative evidence and hashes.
> Subsequent run 9 passed 88/88 per mode and the terminal runtime audit with zero
> retained OOM/fatal/ANR markers after a test-lifetime helper correction, without a
> production APK change. This is an observed regression pass, not causal proof or
> support for the projected performance/structural-guarantee claims below.
>
> A1's hash-based equality and archive-only/lazy rollback proposal is **rejected**:
> exact physical equality and retained previous bytes through terminal/rollback
> checks are mandatory. The implemented alternative uses exact physical matching
> and same-stream complete mismatch retention, not hash substitution. A2 likewise
> retains actual differently formatted readback, not merely canonical bytes.
>
> No allocation stack/exact device ART revision proves the original warning sites
> or a seven-distinct-array peak. ART source shows a possible LOS retry mechanism;
> headroom alone does not prove its specific failure cause. A chunked byte codec
> alone cannot guarantee no large allocations while domain fields remain arbitrary
> compatible `String` values. Claims of KB total native peaks, removed STW pauses,
> guaranteed small-workspace immunity/data safety, or quantified production speedups
> below are **not established** and must not be used for implementation or certification.

**Date:** 2026-11-22
**Scope:** Feasibility, production performance impact (implemented vs. ignored) of the two fixes proposed for the `Throwing OutOfMemoryError` warnings observed during `populated-33554431/33554432-restore-replacement` capacity tests.
**Status:** Analysis only. No source files changed, no ADB/emulator/build commands run.

---

## 1. Executive summary

The warnings are **Java-heap** large-object allocation failures (managed `byte[]` of ~32 MiB), *not* native-heap failures. Android 16 ART's free-list large-object space (LOS) requires one contiguous block; repeated ~32 MiB allocations while multiple such arrays remain live fragment that space. ART clears the pending OOM and retries in ordinary heap, which is why tests pass (87/87) despite six warnings.

Two fix families were proposed:

| Fix | Target | What it changes | Fixes the warnings? |
|---|---|---|---|
| **A — de-overlap managed arrays** (primary) | Java-heap LOS fragmentation | Remove the *pointless* full-array materialization of `previous` and the readback in the save path | **Yes** (the intended fix; mitigation, not structural guarantee) |
| **B — bounded JNI I/O** (secondary) | Native-heap transient copies | Bound `GetByteArrayElements` copying to small reusable buffers | **No** (reduces `nativePeakBytes ≈ 84 MiB`, a separate concern) |

**Feasibility verdict:** Fix A is **highly feasible** and is refined here into two byte-exact-preserving sub-levers (A1, A2). Fix B is **highly feasible but lower priority**; it must not be mistaken for an OOM fix.

**Performance, implemented:** Peak Java-heap large-array working set drops from ~150–175 MiB toward ~64–96 MiB; the blocking-GC + soft-reference-collection pauses that account for a large fraction of the measured ~5.6–6.9 s save latency are removed from the happy path; native peak drops from ~84 MiB to ~KBs.

**Performance, ignored:** No change for the typical small-workspace user (the failure only manifests at the ~32 MiB document cap). For near-cap workspaces, the app remains at measurable risk of escalating from *warning* to a genuine uncaught `OutOfMemoryError` (a `Throwable`, not `Exception`) → **crash during save** on lower-memory devices or under prolonged uptime, plus 1–2 s STW latency spikes per warning event. Data is likely recoverable (preserve-before-write), but the documented 32 MiB guarantee is not upheld.

---

## 2. Scope and default assumptions

This report evaluates the fixes that follow from the *default* answers to the prior clarifying questions:

1. **Gate:** zero `Throwing OutOfMemoryError` for 32 MiB documents remains the acceptance criterion.
2. **Scope:** incremental mitigation only; chunked/streaming *codec* rework (never allocate a single >threshold array) is out of scope unless mitigation proves insufficient.
3. **Guarantees:** preservation/readback/rollback semantics stay byte-exact; only *when* bytes are materialized may change.
4. **Priority:** the Java-heap warnings are the target; bounded JNI I/O is a separate, optional second deliverable.

Consequently the "suggested fixes" are:

- **A1** — stream/hash the previous file instead of reading it into a full array (removes one 32 MiB allocation).
- **A2** — stream-verify the commit readback against the candidate and reuse the candidate array as the new last-good bytes (removes the second 32 MiB allocation).
- **B** — bound JNI read/write through a small reusable buffer (reduces native-heap peak).

---

## 3. Confirmed root-cause facts (basis for this report)

1. Warning sizes are exact: `33,554,443 = 33,554,431 + 12`, `33,554,444 = 33,554,432 + 12` — the payload plus ART's 12-byte array header (`mirror/array.h`, `kFirstElementOffset = 12u`). These are **managed** `byte[]` allocations.
2. The message (`free bytes`, `until OOM`, `growth limit`) is produced by `Heap::ThrowOutOfMemoryError` (`runtime/gc/heap.cc`); the `LOG(WARNING)` is emitted by `Thread::ThrowOutOfMemoryError` (`runtime/thread.cc`) **before** the exception is constructed.
3. `Heap::AllocObjectWithAllocator` (`runtime/gc/heap-inl.h`) attempts `AllocLargeObject` first; on failure it does `self->ClearException()` ("since we are retrying, clear it") and recurses into the ordinary allocator. That is why the warning is recoverable.
4. `FreeListSpace::Alloc` (`runtime/gc/space/large_object_space.cc`) returns `nullptr` when no single free block or end-tail block is ≥ the requested size — i.e. **contiguity, not total headroom**, is the failure mode. The log's "58 MB until OOM" confirms headroom exists while allocation still fails.
5. `save()` in the replacement path materializes and keeps alive up to ~7 × 32 MiB arrays (`WorkspaceStore.kt:159/162/181/191` + retained store fields `original`/`lastGoodBytes`/`persistedLastGoodBytes`/`uncertainCandidate`). Peak is consistent with the observed `target footprint ≈ 148 MB` against `growth limit 192 MB`.
6. The warning is **non-deterministic**: the first `populated-33554432` run produced zero warnings; later runs produced two each. It depends on accumulated LOS fragmentation, so it will vary by device load and uptime.

---

## 4. Fix A feasibility — de-overlap managed arrays (primary)

### 4.1 Why this is the correct target

`previous` and the readback are each read into a fresh full-size array even when their contents are already known or already held:

- In the replacement path, external-change detection is **skipped** (`if (!replacement && !sameBytes(previous, lastGoodBytes))` at `WorkspaceStore.kt:177`), so `previous = access.readBytes()` (`:162`) serves only (a) preservation-as-rollback-evidence and (b) rollback restore. Yet it is always materialized upfront — and in the very test that reproduces the warning, `previous` is byte-identical to `lastGoodBytes`, so the dedup (`:164`) discards the freshly read array. The 32 MiB allocation still happened purely to be thrown away.
- The readback `observed = readAttempt()` (`:191`) exists to confirm the commit; when it byte-equals `candidate`, `committed()` sets `lastGoodBytes = observed` (`:218`) — i.e. it *keeps the copy* even though `candidate` is already an owned, immutable, byte-identical array.

Both allocations are removable without relaxing byte-exactness, because streaming exact comparison already exists in the storage layer (`InputStream.matchesBytes`, `WorkspaceCopies.writeStream`, `fingerprint`).

### 4.2 A1 — stream/hash the previous file

- Preserve `previous` via the existing streaming path (`WorkspaceCopies::writeStream`, already 8 KB-buffered) instead of `readBytes()`.
- In the replacement path, `previous` never needs to live as a managed array on the happy path; a handle to the verified archive suffices.
- Compare `previous` to `lastGoodBytes` via fingerprint (the existing SHA-256 `fingerprint` path) rather than `contentEquals` of a freshly materialized array. Where equal (as in the failing test), skip even the preserve-copy and reuse `lastGoodBytes`.
- On the rare rollback branch only, re-read the archive (`readCopy`) to write it back.

**Correctness preserved:** preserve-before-write ordering is unchanged (streaming still archives first). Rollback still restores byte-exact bytes (re-read on demand). External-change detection (non-replacement path only) moves from exact to SHA-256 fingerprint equality — a negligible-collision semantic shift that must be called out explicitly.

**Feasibility:** **High.** Uses existing, already-tested streaming primitives; the change is confined to `save()` and `WorkspaceFileAccess`/`WorkspaceCopies` seam.

**Risk:** **Low–Medium.** Preservation-failure parity and the archive-handle lifetime across the synchronous lock must be preserved.

### 4.3 A2 — stream-verify readback, reuse the candidate

- After `writeBytes(candidate)`, verify the on-disk bytes by *streaming* comparison against `candidate` (`matchesBytes`, 8 KB-buffered) instead of `readAttempt()` allocating a second full array.
- On byte-equality, set `lastGoodBytes = candidate` (reuse the owned array) rather than `= observed`.
- On byte-inequality, fall back to the existing materialize-and-analyze path (this is the defensive tolerance for value-equivalent-but-byte-different writes).

**Correctness preserved:** `candidate` is internal/owned and does not escape `save()` (return type is `WriteOutcome`); `recoveryBytes()` already returns `copyOf()` so reuse does not leak a mutable internal array. Byte-equality semantics unaltered; the fallback keeps `matches()` tolerance intact.

**Feasibility:** **Medium–High.** Slightly more nuanced because of the byte-vs-value equivalence fallback, but all comparison primitives already exist.

**Risk:** **Medium.** Must prove `candidate` is not mutated after `encodeToBytes`, and that `lastGoodBytes = candidate` remains valid for all downstream consumers (`preservedBytes`, `recoveryBytes`, snapshot lists).

### 4.4 Combined effect

Happy-path save holds: `candidate` (unavoidable) + `lastGoodBytes` (prior commit) ≈ **2 × 32 MiB**, plus a streaming archive file for a genuinely-changed `previous`. This removes both warning-triggering allocations in the failing scenario and roughly halves-or-better the large-array working set. This is a **mitigation** — strong expected effect, not a proof that zero warnings will persist indefinitely against LOS fragmentation across many cycles.

---

## 5. Fix B feasibility — bounded JNI I/O (secondary)

### 5.1 What it does and does not do

`AtomicFileAccess.writeBytes` (`:95`) calls `stream.write(bytes)` → `FileOutputStream.write(byte[])` → `IoBridge.write` → native `Linux_writeBytes`, which runs `ScopedBytesRO` = `GetByteArrayElements` on the **whole array** before applying offset/count. Android 16 ART copies a **movable** array fully into native `new[]` per call. A movable array is exactly what we get when the LOS allocation fails and ART falls back to ordinary space — so the warning case and the native-copy cost are coupled.

Bounding I/O through a small reusable buffer (8 KB-class) caps native copy at 8 KB and cuts `nativePeakBytes` from ~84 MiB to ~KB.

### 5.2 Feasibility

**High, low correctness risk** (byte-transparent). Write path: copy 8 KB slices via `System.arraycopy` into a reusable buffer, then `stream.write(buffer, 0, n)`. Read path: `read` into the small buffer, `arraycopy` into the destination `result`. Cost: ~4096 write syscalls instead of one, and ~32 MiB of `arraycopy` — negligible absolute CPU, no change to bytes.

**Mandatory caveats for the planner:**
- `BufferedOutputStream.write(largeArray)` can **bypass** its buffer and call out with the large array — manual chunking is required, not just wrapping.
- Chunking by offset/count on the *same* large array does **not** bound the copy (`GetByteArrayElements` still pins/copies the whole array); a separate small buffer is required.
- Fix B must be reported as "reduces native peak," never as "removes the OOM warnings."

**Priority:** optional/secondary. Native ~84 MiB is transiently tolerable on the reference device, but matters under LMK pressure on low-RAM devices (see §6).

---

## 6. Production performance impact — fixes implemented

### 6.1 Latency (throughput)

- The two warnings per affected replacement are each preceded by `Starting a blocking GC Alloc` + `Forcing collection of SoftReferences for 32MB allocation` — STW pauses of roughly 1.0–1.5 s each in the logs. Removing the triggering allocations removes this GC-retry churn from the happy path.
- Measured `restore-replacement` elapsed was **5,626–6,941 ms**; the OOM-recovery GC is a large and *avoidable* fraction of that. Estimate (order of magnitude): near-cap save latency drops materially toward the pure encode + I/O + hash cost (roughly 1–2 s for 32 MiB, unpinned under the no-build/no-benchmark constraint).
- Typical small workspaces are completely unaffected — they never approach the ~32 MiB array threshold.

### 6.2 Memory footprint

| Metric | Current (observed) | After Fix A | After Fix A + B |
|---|---|---|---|
| Java heap peak (`heapPeakBytes`) | ~174–175 MiB | ~64–96 MiB | same as A |
| Warning-time `target footprint` | ~148 MiB | below | below |
| Native peak (`nativePeakBytes`) | ~84 MiB | ~84 MiB | ~KBs |
| PSS peak (`pssPeakKiB`) | ~365–385 MiB | lower | lower still |

### 6.3 CPU / I/O

- A1 converts a file→heap→file round-trip into file→file streaming: strictly *less* total work (removes a 32 MiB allocation, zero-fill, and a 32 MiB memcpy). Net CPU reduction.
- A2 removes a 32 MiB allocation + read; replaces with streaming `memcmp`-equivalent. Net reduction.
- B adds ~4096 syscalls per 32 MiB write and ~32 MiB `arraycopy` — a small, bounded CPU increase in exchange for eliminating the 32 MiB native alloc/copy per write/read.

### 6.4 Stability risk reduction

- On low-memory / lower-`heapgrowthlimit` devices, the margin to a genuine, uncaught `OutOfMemoryError` widens because fewer large objects coexist and no pointless 32 MiB arrays are allocated. This is the primary production stabilization win.

---

## 7. Production performance impact — fixes ignored

### 7.1 By user profile

| Profile | Impact if ignored |
|---|---|
| **Typical small workspace (most users)** | **None.** The warning only manifests at the ~32 MiB document cap; KB-scale workspaces never reach large-object fragmentation. |
| **Near-cap (32 MiB) on high-RAM device** | Intermittent benign warnings + ~1–3 s STW latency spikes per save; data safe (preserve-before-write); non-fatal. |
| **Near-cap (32 MiB) on low-RAM / low-heap-limit device** | LOS failure + ordinary-space retry failure → genuine `OutOfMemoryError` (a `Throwable`, **not** `Exception`) → propagates out of `save()`/`load()` (the storage layer catches only `Exception`) → **crash during save**. |
| **Prolonged uptime / sustained alloc-free churn** | LOS fragmentation accumulates beyond the clean-boot snapshot; warning frequency — and eventually crash probability — rises over process lifetime, even on reference-class hardware. |

### 7.2 Concrete failure mode (the "ignored → crash" path)

1. A ~32 MiB document is replaced while multiple other ~32 MiB arrays remain live.
2. `FreeListSpace::Alloc` cannot find a contiguous 32 MiB block → OOM warning logged.
3. The ordinary-space retry also cannot satisfy the request (insufficient free heap on a constrained device).
4. `OutOfMemoryError` is thrown. Because it is an `Error`, the storage layer's `catch (Exception)`/`catch (_: Exception)` handlers do not catch it, and there is no application fallback for it.
5. The error escapes `save()` → uncaught → process aborts (or crashes via the VM default handler).

Data is likely recoverable on restart because preserve-before-write archived the prior good state, but **the crash itself violates the documented 32 MiB delivery gate** and is an intermittent, hard-to-reproduce class of outage (the warnings are stochastic in the first place).

### 7.3 Performance-only impact if ignored

- Repeated ~1.0–1.5 s STW GC pauses per OOM-recovery event during near-cap saves.
- Elevated Java-heap utilization (~91% of the 192 MB limit) leaves little room for other app subsystems (UI, images, caches).
- Elevated native peak (~84 MiB transient) and PSS (~370–385 MiB) raise LMK-kill risk on low-RAM devices.

---

## 8. Risk matrix

| Change | Feasibility | Correctness risk | Perf (happy path) | Effect on warnings | Priority |
|---|---|---|---|---|---|
| A1 — stream/hash previous | High | Low–Med | Better (less I/O) | Removes one source | 1 |
| A2 — stream-verify readback, reuse candidate | Med–High | Med | Better (less alloc) | Removes second source | 1 |
| B — bounded JNI I/O | High | Low | ~Neutral (more syscalls, less native memcpy) | None | 2/optional |

---

## 9. Recommendation

1. **Implement A1 + A2 together** — they are the fixes that bear on the OOM warnings, both remove a pointlessly-materialized 32 MiB array, and both preserve byte-exact guarantees with explicit fallbacks. Expected to remove the warnings in the failing scenario and materially cut peak Java-heap footprint and save latency.
2. **Do B only for native-peak/LMK reduction**, as a separate tracked deliverable, explicitly *not* as an OOM fix.
3. **Do not** reach for `android:largeHeap` — the log already shows "58 MB until OOM," proving the failure is contiguous-block fragmentation, not capacity.
4. **Re-verify the gate**: if the acceptance criterion truly requires *zero* warnings indefinitely (including under long-update fragmentation), mitigation may reduce but not structurally guarantee that; the only structural guarantee is a chunked/streaming codec (out of current scope). Confirm the gate before treating A1+A2 as "done."
5. **Regression safety**: keep the fault-injection suite green and add an explicit assertion path for the replacement save (byte-exact rollback, readback verification, external-change detection) so the de-overlap refactor cannot silently weaken storage guarantees.

No repository source files were modified; no ADB, emulator, or build commands were run.
