# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 6: TEMP-FILE CLEANUP, CANCELLATION & LIFECYCLE — ANALYSIS + SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild Blocks 1–5E are complete and Block 5 has been committed. Block 5 deliberately implemented only the **creation side** of the temporary-file architecture. Block 6 owns the missing lifecycle/cleanup/cancellation behavior.

This prompt is **STEP 1 + STEP 2 ONLY: analysis and scope confirmation**.

Do not modify files.
Do not output implementation code.
Do not begin Block 7.
Do not add networking/API/Custom Tabs.
Do not wire the HQ renderer into a real upload flow prematurely.
Do not change UI/product behavior beyond what is strictly required for Block 6 lifecycle/cancellation ownership.

The goal is to derive the smallest repository-accurate Block-6 implementation scope and stop for explicit approval.

---

# 1. Mandatory Source-of-Truth Review

Read these before analyzing code:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/SESSION_ORIGINALS_PRIVACY_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`

Also inspect any Block-5 implementation notes and current tests that define the temp-file boundary.

The current authoritative plan explicitly assigns to Block 6:

- cleanup on success
- cleanup on cancel
- cleanup on final error
- cancellation-safe cleanup
- `onCleared()` cleanup
- sweep-on-screen-entry
- ViewModel cancellation wiring

Block 5 owns only creation of:

`context.cacheDir/wackelbild/<operationId>/`

and deterministic candidate files inside that operation directory.

If current code/docs conflict with that boundary, report the conflict and STOP rather than silently redefining the architecture.

---

# 2. Repository Baseline

Before analysis report:

- current branch
- HEAD
- `git status --short`

Expected:

- Block 5/5E committed
- prompt archive files may be untracked
- no unrelated production changes.

Preserve all unrelated working-tree state.

---

# 3. Inspect Current Block-5 Implementation

Read completely:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`

Read corresponding tests:

- `WackelbildTempFileManagerTest.kt`
- `WackelbildViewModelTest.kt`
- `WackelbildScreenTest.kt`
- `WackelbildPrintRendererInstrumentedTest.kt`

Also inspect repository precedents for cancellation-safe cleanup, especially:

- current video export pipeline / ViewModel
- any `NonCancellable` cleanup precedent
- any `onCleared()` job cancellation/cleanup precedent
- any cache sweep/stale-directory cleanup precedent.

Do not assume the implementation plan pseudocode exactly matches current code after Blocks 1–5E. Use current repository code as implementation evidence while keeping the specs authoritative for behavior.

---

# 4. Establish the Exact Block-6 Boundary

Answer precisely what Block 6 can implement **before networking exists**.

This is important.

The plan refers to cleanup:

- after successful upload
- after cancellation
- after final error.

But Block 7+ owns the real network handoff.

Therefore determine which cleanup primitives and orchestration can genuinely be implemented now without inventing a fake upload flow.

Distinguish clearly between:

## A. Infrastructure Block 6 can implement now

Potential examples, only if supported by current architecture:

- delete one operation directory recursively
- sweep stale operation directories
- track current operation directory in ViewModel
- cancel an active preparation/render job
- cancellation-safe cleanup of current temp files
- `onCleared()` cleanup
- screen-entry stale sweep
- test seams for later upload-success/final-error cleanup
- a single lifecycle owner for operation temp files.

## B. Hooks that must remain for later network blocks

Examples:

- actual "upload succeeded" event
- actual API final-error event
- Custom Tab completion
- checkout lifecycle.

Do not simulate those events just to claim Block 6 is complete.

The analysis must say exactly what is implemented now versus what is merely made callable for Block 7+.

---

# 5. Ownership Model

Determine one exact owner for the active operation.

The architecture must prevent:

- orphaned operation directories
- multiple active operation directories for one screen/ViewModel
- deleting another operation's files
- cleanup races while rendering
- stale ViewModel references to deleted files.

Analyze whether ownership should be:

- ViewModel-owned operation handle/state
- TempFileManager-owned registry
- another already-existing pattern.

Choose the smallest architecture consistent with current code.

Do not introduce a repository-wide temp-file framework.

---

# 6. WackelbildTempFileManager Responsibilities

Determine the minimal extension to the existing manager.

At minimum analyze whether it needs operations equivalent to:

- create operation directory
- delete a specific operation directory
- delete/sweep stale operation directories under `cacheDir/wackelbild`
- optionally delete the empty root directory after cleanup.

Define safety constraints:

- never recurse outside `cacheDir/wackelbild`
- never accept arbitrary external paths without containment validation
- never touch `filesDir/sessions`
- never touch persisted originals
- cleanup must be idempotent
- missing files/directories are not errors
- one failed file deletion must have defined behavior.

Determine the stale-sweep policy from the authoritative plan/spec. If no age threshold is actually fixed there, do not invent one silently. Report whether sweep means:

- delete all orphan operation dirs on screen entry
or
- age-based cleanup.

Choose only if the source of truth resolves it; otherwise identify the smallest required product/technical decision.

---

# 7. Screen-Entry Sweep

Trace the current screen lifecycle.

Determine the exact safest trigger for stale sweep:

- ViewModel initialization
- first screen composition
- `ON_START`
- `ON_RESUME`
- another existing repository pattern.

Requirements:

- run once per genuine Wackelbild screen entry, not on every recomposition
- must not delete the currently active operation
- must not race against rendering
- must run off the main thread if filesystem traversal/deletion can block
- no network
- no user-visible progress UI for a normal stale sweep unless the spec explicitly requires it.

Also reconcile this with the existing Block-3/4 lifecycle handling for tilt calibration and Custom-Tab-related future state.

Do not break current sensor lifecycle.

---

# 8. Cancellation Wiring

Analyze current `WackelbildViewModel`.

Block 6 must establish cancellation ownership for future prepare/upload work without implementing upload.

Determine:

- exact coroutine Job ownership
- how a preparation/render operation will be started/cancelled
- whether an existing `viewModelScope.launch` pattern is sufficient
- how cancellation propagates into `WackelbildPrintRenderer`
- whether the renderer is already cooperative or contains blocking bitmap work that cannot be instantly interrupted
- what cleanup occurs after cancellation
- whether cleanup must use `withContext(NonCancellable + ioDispatcher)` or equivalent current-project precedent.

The rule must be:

**cancel operation → no further operation work is accepted → temp files are cleaned even if the coroutine was cancelled.**

Do not add WorkManager, foreground service, background uploader, or persistent operation state.

---

# 9. Back Behavior

Current Wackelbild Back behavior was implemented before active operations existed.

Analyze what Block 6 should change now.

The feature contract eventually requires a confirmation dialog while a busy transfer/preparation operation is active.

But there is still no network in Block 6.

Determine whether Block 6 should:

- wire the existing/previously-planned cancel-confirmation UI to an active **preparation** operation now,
or
- only implement ViewModel cancellation primitives and leave the dialog to the later block that first exposes an active operation in the UI.

Do not invent a fake busy state.

If no user-triggered preparation exists yet, say so explicitly.

The final scope must avoid UI work that cannot be exercised honestly in Block 6.

---

# 10. `onCleared()` Cleanup

Determine exact behavior when the ViewModel is destroyed.

Required contract:

- active operation job cancelled
- active operation temp directory cleaned
- cleanup must not depend on a composable still being alive
- no persisted session files touched
- no network work continued.

Investigate the technical constraint that `viewModelScope` is cancelled as ViewModel clearing occurs.

If asynchronous cleanup launched in `viewModelScope` would be unreliable after cancellation, choose a repository-safe mechanism and explain it.

Do not perform expensive blocking recursive IO on the main thread without justification.

If Android ViewModel lifecycle makes guaranteed asynchronous cleanup impossible solely in `onCleared()`, define the layered safety model precisely (e.g. cancellation-finally cleanup + best-effort onCleared + next-entry sweep), based on actual platform behavior rather than claiming an impossible guarantee.

---

# 11. Success / Final-Error Cleanup Hooks

Because the network layer does not yet exist, determine the narrow API that later blocks will call.

Possible concept:

- `completeOperation(...)`
- `failOperation(...)`
- `cancelOperation(...)`

But do not invent three methods if one idempotent cleanup primitive plus state transition is enough.

The later upload flow must be able to guarantee:

- successful upload → delete temp files immediately after both uploads accepted
- final error → delete temp files
- user cancellation → delete temp files.

Analyze the minimal interface needed.

No API/network DTOs in Block 6.

---

# 12. Race Conditions to Analyze

Explicitly analyze these cases:

1. user starts operation and immediately presses Back
2. ViewModel cleared while rendering
3. cleanup called twice
4. stale sweep runs while a current operation exists
5. render fails before both candidate files exist
6. renderer succeeds but caller is cancelled before consuming result
7. cancellation happens during recursive deletion
8. screen leaves and re-enters quickly
9. process is killed before cleanup completes
10. cache directory has an unknown/corrupt child file instead of an operation directory.

For each, state the intended safe outcome.

---

# 13. Privacy / Storage Safety

Confirm Block 6 must not modify or delete:

- `reference.jpg`
- `capture.jpg`
- `reference-original.jpg`
- `capture-original.jpg`
- `metadata.json`
- any session directory
- any MediaStore item.

All cleanup is confined to:

`context.cacheDir/wackelbild/`

No new permission.
No storage permission.
No telemetry.
No logging of session IDs or image paths unless already unavoidable and non-sensitive; preferably no new logging.

---

# 14. Tests Required

Design the exact Block-6 tests.

## TempFileManager unit tests

At minimum:

- delete existing operation directory recursively
- delete is idempotent
- missing directory succeeds/no-ops
- nested candidate files removed
- root containment enforced
- persisted session path cannot be deleted
- sweep removes stale/orphan operation dirs
- sweep does not delete active operation
- unknown child handling
- empty root behavior.

## ViewModel unit tests

At minimum, if ViewModel becomes the owner:

- one active operation at a time
- cancellation cancels the job
- cancellation invokes cleanup
- cleanup occurs under coroutine cancellation
- repeated cancellation is safe
- render/preparation failure cleans files
- successful preparation retains files only for the next future handoff stage if that is the correct Block-6 boundary
- explicit later-stage completion hook cleans files
- `onCleared()` cancels/cleans according to the chosen layered guarantee
- stale sweep triggered once per intended screen entry
- no session-file mutation.

## Instrumentation / screen tests

Only include if Block 6 actually changes screen lifecycle or UI.

Do not add instrumentation tests merely to inflate coverage.

If screen-entry sweep is triggered from Composable lifecycle, add the narrow test proving it fires at the correct lifecycle point and not on recomposition.

If no UI change is required, state that no WackelbildScreen instrumentation change is needed.

---

# 15. Test Execution Plan

For the eventual implementation, plan:

- `./gradlew testDebugUnitTest`
- `./gradlew compileDebugAndroidTestKotlin` if Android tests change
- narrow Wackelbild instrumentation class if applicable
- existing `WackelbildPrintRendererInstrumentedTest`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `git diff --check`
- `git status --short`

No test suppression.
No baselines.
No disabled tests.

Real-device validation is required only if Block 6 changes user-visible/lifecycle behavior that cannot be fully proven in JVM/instrumentation tests. State exactly what remains.

---

# 16. Documentation Consistency

Determine whether Block 6 requires updating only:

- `docs/IMPLEMENTATION_NOTES.md`

or whether the implementation plan/spec contains wording that must be corrected based on actual lifecycle constraints discovered in this analysis.

Do not rewrite historical documentation.

Do not update unrelated specs.

If the current plan promises an impossible `onCleared()` guarantee, identify it explicitly and propose the smallest documentation correction before implementation.

---

# 17. Forbidden Scope

Block 6 must not introduce:

- INTERNET permission
- OkHttp/Retrofit
- API DTOs
- partner key
- upload endpoints
- retry/backoff
- locale mapping
- Custom Tabs
- checkout URL handling
- upload progress
- price/product UI
- order tracking
- WorkManager
- foreground service
- persistent handoff state
- DataStore state for Wackelbild
- changes to HQ parity/dimension/date-badge logic
- changes to camera/session storage
- changes to Compare flow
- unrelated refactors.

---

# 18. Required Final Response

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Current Block-5 Temp Architecture

Describe exact current manager/renderer/ViewModel boundary with code evidence.

## 3. Block-6 Boundary Verdict

Separate:

- what Block 6 can implement now
- what must remain for Block 7+ because networking does not exist.

## 4. Cleanup Ownership

State one exact owner and lifecycle.

## 5. TempFileManager Changes

List exact methods/behavior proposed.

## 6. Screen-Entry Sweep

State exact trigger and why.

## 7. Cancellation / `onCleared()` Design

State exact coroutine/job/cleanup behavior and any platform limitation.

## 8. Success / Error Hook Design

State the minimal later-stage cleanup API without implementing network.

## 9. Race-Condition Analysis

Table:

| Scenario | Safe outcome | Mechanism |

Cover all ten scenarios from §12.

## 10. Files Proposed for Modification

Table:

| File | Modify/Create | Exact change | Why |

List ALL files that would be changed in implementation.

No vague “corresponding tests”.

## 11. Files Explicitly Not Touched

Confirm all major forbidden areas.

## 12. Tests to Add / Update

List exact test files and cases.

## 13. Verification Commands

List exact commands to run after implementation.

## 14. Documentation Impact

State exactly which docs change and why.

## 15. Risks

Only genuine Block-6 risks.

## 16. Scope Confirmation

Confirm:

- no network
- no UI expansion unless proven necessary
- no renderer parity changes
- no session mutation
- no unrelated code touched.

End exactly with one of:

**BLOCK 6 SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

or, if the current architecture/spec prevents a safe isolated Block 6:

**BLOCK 6 BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final Rule

This is analysis and scope confirmation only.

Do not implement.

Block 6 is a lifecycle/cleanup safety block, not the network handoff block. Build the smallest reliable ownership and cleanup layer that later blocks can call, while ensuring temporary Wackelbild images cannot be left behind unnecessarily and persisted SameView session data can never be touched.
