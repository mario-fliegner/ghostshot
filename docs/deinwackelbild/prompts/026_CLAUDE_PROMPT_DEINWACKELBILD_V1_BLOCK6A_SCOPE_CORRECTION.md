# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 6A: SCOPE CORRECTION / FINAL IMPLEMENTATION SCOPE

## Purpose

This is a **correction gate only** for the completed Block 6 analysis.

Do **not** implement production code in this gate.

The prior Block 6 analysis is accepted except for one architectural point: the proposed internal/non-UI ViewModel test seam that would artificially execute `create → render → cleanup` solely so Block 6 can simulate a preparation operation before the real CTA/handoff exists.

That test seam is **rejected**.

Block 6 must remain an honest infrastructure/lifecycle block. It must not create a fake user-inaccessible preparation flow merely to make later lifecycle behavior testable early.

Read the repository and authoritative DeinWackelbild documents again before correcting the scope.

---

# 1. Mandatory sources

Read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`
- current:
  - `WackelbildTempFileManager.kt`
  - `WackelbildViewModel.kt`
  - `WackelbildScreen.kt`
  - `WackelbildPrintRenderer.kt`
  - their relevant tests
- the existing video-export cancellation/cleanup precedent already identified in the prior analysis.

Use the current committed repository state as implementation evidence and the MD specifications as behavioral authority.

---

# 2. Accepted findings from the previous analysis

Unless repository reinspection disproves them, preserve these conclusions:

1. `WackelbildTempFileManager` is currently creation-only.
2. `WackelbildPrintRenderer` accepts an output directory but is not wired into `WackelbildViewModel`.
3. No real Wackelbild CTA/preparation/upload flow exists yet.
4. `WackelbildViewModel` is the correct future owner of one active operation.
5. `WackelbildTempFileManager` should remain stateless.
6. Sweep-on-screen-entry belongs at one-time ViewModel initialization, not generic `ON_RESUME`.
7. Cleanup is restricted to `context.cacheDir/wackelbild/`.
8. Persisted session files/originals must never be modified or deleted.
9. No network/UI/upload implementation belongs in Block 6.
10. Hard process death cannot guarantee `onCleared()` or coroutine `finally`; next-entry sweep is the recovery backstop.

---

# 3. Required correction: REMOVE the artificial preparation test seam

The previous proposal included an internal/non-UI method on `WackelbildViewModel` that would create an operation directory and invoke `WackelbildPrintRenderer` solely for tests/manual force-kill validation.

Do **not** add this.

Specifically forbidden in Block 6A/6 implementation:

- no internal fake `prepareForTest()`-style API;
- no debug-only renderer trigger;
- no hidden UI;
- no instrumentation-only production entry point;
- no fake busy state;
- no fake CTA;
- no artificial `WackelbildPrintRenderer` call from the ViewModel;
- no renderer dependency injected into the ViewModel merely to exercise cancellation before the real handoff exists.

The real operation chain must first become executable when the later product flow genuinely wires it.

Tests must test the infrastructure that actually exists at this block boundary, not invent production behavior for testability.

---

# 4. Re-evaluate what ViewModel changes are genuinely justified NOW

This is the key question for this correction gate.

The previous analysis proposed:

- `operationDir: File?`
- `operationJob: Job?`
- `onCleared()` cancellation
- operation rendering wrapper.

But without any real operation being startable in Block 6, determine which of those fields/methods are actually useful now versus premature scaffolding.

Apply this rule:

> Do not add dormant state or lifecycle machinery that has no legitimate production writer/caller until a later block.

Therefore inspect the plan and decide whether Block 6 should now be limited to:

- completing `WackelbildTempFileManager`;
- wiring stale sweep once on Wackelbild ViewModel creation/screen entry;
- establishing only the minimal cleanup API that later blocks will call;

while deferring:

- active `operationJob`;
- active `operationDir`;
- cancellation of an operation;
- `onCleared()` operation cancellation;
- `finally/NonCancellable` around real preparation/upload work;

until the first later block that actually creates an operation.

If the authoritative implementation plan explicitly requires dormant ViewModel cancellation infrastructure in Block 6 despite no operation caller, quote the exact requirement and explain why it should exist now. Otherwise prefer deferral.

Do not blindly preserve the previous proposal.

---

# 5. Cleanup manager design to retain

Analyze/finalize the minimal `WackelbildTempFileManager` API.

Expected responsibilities:

### Creation
Existing:
- create operation directory under `cacheDir/wackelbild/<operationId>`
- candidate file handles

### Cleanup
Proposed:
- delete one operation directory recursively
- sweep orphan/stale children under the Wackelbild cache root

Safety requirements:

- canonical containment validation;
- must never delete outside the Wackelbild cache root;
- must never touch `filesDir/sessions`;
- idempotent;
- missing path = safe no-op;
- unknown direct child in Wackelbild cache root may be removed during sweep if that is consistent with the dedicated-root contract;
- recursive cleanup must be best-effort/no-crash;
- no new permissions.

Re-evaluate whether an `excluding: File?` sweep parameter is needed **now** if no operation can exist concurrently with init sweep. Do not add future-only API without a current need unless the implementation plan explicitly requires it.

---

# 6. Define "stale sweep" precisely

The previous analysis interpreted sweep as deleting all children of the dedicated root on fresh screen entry.

Verify this against the source-of-truth documents.

If no age threshold exists, do not invent one.

The intended model should likely be:

- `cacheDir/wackelbild/` is dedicated solely to disposable operation data;
- a newly created Wackelbild ViewModel has no valid operation from a previous instance;
- therefore every child present at fresh entry is orphaned and removable.

Confirm or reject this with evidence.

---

# 7. Screen-entry trigger

Reconfirm whether:

`WackelbildViewModel.init` → `viewModelScope.launch(ioDispatcher)` → sweep

is the smallest correct trigger.

It must:

- run once per fresh Wackelbild destination/ViewModel;
- not run on recomposition;
- not run on every `ON_RESUME`;
- not interfere with tilt lifecycle;
- perform filesystem IO off main thread.

Check for test determinism with the existing injectable/overridable `ioDispatcher` pattern.

---

# 8. Correct lifecycle guarantee wording

The previous analysis said `NonCancellable` "guarantees the delete call itself completes once started."

Use more precise wording.

Required model:

### Normal in-process cancellation
Once a real operation exists in a later block, cleanup should live in that operation coroutine's `finally` and use a cancellation-resistant context such as the existing `NonCancellable` precedent so ordinary coroutine cancellation does not skip cleanup.

### ViewModel destruction
When an active operation eventually exists, `onCleared()` should cancel it. Its own `finally` is the primary cleanup path.

### Hard process death
Neither `onCleared()` nor `finally` is guaranteed to execute or finish if Android kills the process.

Therefore:

**next fresh Wackelbild entry → unconditional orphan sweep**

is the required recovery mechanism.

Do not claim stronger guarantees than Android provides.

---

# 9. Manual force-kill validation correction

The prior Block 6 analysis attempted to preserve a manual criterion:

> force-kill during preparation and verify next-entry cleanup

But there is no real user-triggerable preparation yet.

Therefore for Block 6 itself:

- do not create a fake trigger;
- do not require the user to perform an impossible/manual artificial preparation test;
- test sweep by creating representative cache leftovers in automated tests;
- defer the real "kill during active preparation/upload" physical-device validation until the later block where that operation genuinely exists.

Documentation must state this clearly if necessary.

---

# 10. Tests — corrected scope

Design tests only for behavior genuinely implemented in Block 6.

At minimum, for `WackelbildTempFileManagerTest` consider:

- creation still works;
- recursive deletion of an operation directory;
- nested candidate files removed;
- repeated deletion safe;
- missing directory safe;
- containment protection;
- path outside root rejected/ignored safely;
- persisted session directory cannot be deleted;
- sweep removes orphan operation directories;
- sweep removes unknown child files inside dedicated Wackelbild cache root if that is the chosen contract;
- empty/missing root safe;
- root itself handled according to the chosen contract;
- no traversal outside root.

For `WackelbildViewModelTest`, if init sweep is wired:

- stale sweep triggered once on ViewModel creation;
- stale operation directory removed;
- no persisted session file mutation;
- metadata/date behavior remains unaffected;
- test dispatcher makes init work deterministic.

Do **not** add tests for:

- operation-job cancellation;
- render cancellation;
- cleanup-after-render failure;
- success handoff cleanup;
- final upload error cleanup;
- `onCleared()` active-operation cleanup;

unless the corresponding real production behavior actually exists after the corrected scope review.

Those tests belong with the block that introduces the real operation.

No instrumentation test should be added unless the implementation genuinely changes Android UI/lifecycle behavior that cannot be proven in JVM tests.

---

# 11. Documentation correction

Review:

- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

If the implementation plan currently assigns active-operation cancellation/`onCleared()` cleanup to Block 6 in a way that presupposes a real operation that does not exist until later, identify the inconsistency.

Do not silently implement fake scaffolding to satisfy outdated sequencing.

Propose the smallest documentation correction:

- Block 6 = temp cleanup infrastructure + orphan sweep;
- real operation cancellation/finally/onCleared cleanup = implemented when the real operation is introduced;
- architectural contract remains unchanged;
- only sequencing is corrected.

If the plan already cleanly permits this interpretation, do not modify it unnecessarily.

---

# 12. Forbidden scope

Still forbidden:

- network
- INTERNET permission
- OkHttp/Retrofit
- API DTOs
- partner key
- uploads
- retry/backoff
- Custom Tabs
- checkout
- CTA
- Back confirmation dialog
- fake busy state
- fake operation
- hidden/debug operation trigger
- renderer changes
- HQ parity changes
- date badge changes
- sensor/swipe changes
- camera changes
- session storage changes
- WorkManager
- foreground service
- DataStore
- unrelated refactors.

---

# 13. Required output

Return exactly:

## 1. Repository Baseline
- branch
- HEAD
- status

## 2. Reinspection Result
State whether the prior Block 6 findings still hold.

## 3. Test-Seam Correction
Confirm the artificial renderer/preparation test seam is removed from scope.

## 4. Corrected Block-6 Boundary
Two lists:
- implement now
- defer until real operation/handoff exists

## 5. ViewModel Scope Decision
Explicitly decide whether `operationJob`, `operationDir`, cancellation and `onCleared()` belong now or later, with repository/spec evidence.

## 6. TempFileManager Final API
List exact methods/signatures/behavior proposed.

## 7. Sweep Semantics
State exactly what is deleted and when.

## 8. Lifecycle Guarantee
Explain normal cancellation vs ViewModel destruction vs hard process death without overstating guarantees.

## 9. Files Proposed for Modification
Table:

| File | Modify/Create | Exact change | Why |

List every file required for the eventual corrected Block-6 implementation.

## 10. Files Explicitly Not Touched
Confirm forbidden areas.

## 11. Tests to Add/Update
Exact test files and exact cases.

## 12. Tests Explicitly Deferred
List cancellation/render/upload/force-kill tests that cannot honestly exist yet.

## 13. Verification Commands
Exact commands for eventual implementation.

## 14. Documentation Impact
Exact docs to update, if any.

## 15. Risks
Only genuine corrected Block-6 risks.

## 16. Gate Result

End exactly with:

**BLOCK 6A CORRECTED SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

unless a real source-of-truth conflict requires user decision, in which case:

**BLOCK 6A BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final constraints

This is analysis/scope correction only.

Do not implement.

Do not solve a future problem by adding dormant production machinery or test-only production entry points today.

The target is the smallest truthful Block-6 change that improves temp-file safety now and leaves the real cancellation lifecycle to the first block where a real operation actually exists.
