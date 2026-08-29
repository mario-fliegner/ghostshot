# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 8B: CORRECTION + FINAL SCOPE CONFIRMATION

## Purpose

Block 8A analysis is largely accepted, but two points must be corrected before implementation:

1. the theoretical maximum request count was derived incorrectly;
2. the proposed fallback-confirmation callback with a default `true` prematurely pulls future UI consent behavior into Block 8 and risks bypassing the required fallback warning/confirmation semantics.

This prompt is **ANALYSIS + SCOPE CORRECTION ONLY**.

Do not modify files.
Do not implement code.
Do not add UI.
Do not add `INTERNET`.
Do not add the real pilot key.
Do not make network calls.
Do not begin Block 9/10/11.

The goal is to resolve exactly these two issues and then restate the final Block-8 implementation scope.

---

# 1. Mandatory source review

Re-read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Re-read current Block 8A analysis conclusions and current code for:

- `WackelbildViewModel.kt`
- `WackelbildTempFileManager.kt`
- `WackelbildPrintRenderer.kt`
- `DeinWackelbildApiClient.kt`
- `DeinWackelbildDtos.kt`

Report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 7 committed
- no Block-8 production code yet.

---

# 2. Accepted Block-8A decisions to preserve

Unless direct source-of-truth review disproves them, preserve these decisions:

- dedicated `WackelbildHandoffOrchestrator`
- `operationJob` owned only by `WackelbildViewModel`
- no separate ViewModel `operationDir`
- at most one active operation
- operation-local handoff/idempotency/retry state
- `StateFlow<WackelbildOperationState>`
- UUID-based idempotency key
- same key reused across Create retries
- new key on full handoff restart
- 3 total attempts per request stage
- delays: 1s then 2s
- retryable:
  - `RETRYABLE_NETWORK`
  - `RETRYABLE_SERVER`
  - `RATE_LIMITED`
- 403/410 → full handoff restart
- 409 → full handoff restart because missing slot cannot be inferred safely
- max 3 handoff generations
- prepared files reused across handoff restarts
- no re-render on restart
- Reference → slot `one`
- Capture → slot `two`
- final cleanup via `finally` + `NonCancellable`
- `onCleared()` cancels the active job
- hard process death recovered by Block-6 next-entry sweep
- no manifest/BuildConfig/UI/Custom Tab work in Block 8.

Do not reopen these unless the two corrections below force a direct consequence.

---

# 3. Correction A — derive the TRUE maximum request count

The Block-8A formula:

`3 stages × 3 attempts × 3 handoff generations = 27`

is not a valid reachable worst-case derivation.

Reason:

A handoff generation terminates as soon as a 403/409/410 triggers a restart. It therefore cannot simultaneously:

- exhaust all three attempts for Create,
- exhaust all three attempts for upload one,
- exhaust all three attempts for upload two,
- and then still trigger a restart.

You must calculate the **actual reachable upper bound**.

## Required analysis

Model:

- max 3 handoff generations total;
- each stage has max 3 attempts;
- restart-trigger classifications immediately end the current generation;
- only `RETRYABLE_NETWORK`, `RETRYABLE_SERVER`, `RATE_LIMITED` consume same-stage retries;
- 403/409/410 trigger full restart immediately rather than being retried three times at the same stage.

Derive the worst-case request sequence explicitly.

Consider separately:

### Restart from Create
A generation may consume some Create attempts, then restart.

### Restart from upload one
A generation may:
- complete Create
- consume upload-one attempts
- then restart.

### Restart from upload two
A generation may:
- complete Create
- complete upload one
- consume upload-two attempts
- then restart.

The final generation may end in:
- success
- retry exhaustion
- terminal non-retryable failure
- restart-budget exhaustion.

Compute the maximum number of actual HTTP requests reachable under the chosen rules.

Show the sequence, not just the final number.

If there are multiple equally maximal paths, state one representative path.

This final bound must be used in the implementation plan later.

---

# 4. Correction B — fallback confirmation must NOT default to implicit approval

Block 8A proposed:

`confirmFallbackUsage: suspend () -> Boolean = { true }`

This is rejected.

A default `true` silently allows transfer after renderer fallback even though the feature contract requires a fallback warning/confirmation before upload continues.

Block 8 must not manufacture future UI behavior.

Re-read the exact authoritative wording in the integration spec around:

- fallback usage
- warning timing
- consent before upload
- sequencing of local preparation vs external transfer.

Then choose **one exact architecture** that preserves the product contract without adding UI in Block 8.

---

# 5. Evaluate the two safe architecture options

## Option A — explicit operation input

Block 8 receives an explicit boolean/input saying fallback use is already approved.

Example conceptually:

`startOperation(fallbackApproved: Boolean)`

But this is only valid if:
- the fallback condition is known before starting the network portion;
- the caller can honestly know whether approval is needed.

Because the renderer determines `usedFallback` during preparation, verify whether this option is actually practical without rendering twice or exposing renderer internals.

If it is not, reject it.

## Option B — orchestrator pauses in a non-UI state

The orchestrator renders first.

If `usedFallback == false`:
- continue directly.

If `usedFallback == true`:
- do **not** create the handoff yet;
- return/expose a non-terminal state such as:
  - `AwaitingFallbackConfirmation`
  containing only the minimum continuation context needed.

Later Block 11 UI:
- shows the required warning;
- explicit Continue resumes the same logical operation;
- Cancel terminates and cleans up.

Analyze whether this requires keeping:
- operation directory
- prepared files
- operation job
alive while waiting for user input.

If so, determine whether that is acceptable under the current lifecycle model or whether a continuation token/object is cleaner.

Do not add UI now.

---

# 6. Preferred design principle

Block 8 must satisfy all of these:

- no implicit approval;
- no hidden default `true`;
- no upload starts before fallback approval if fallback was used;
- no duplicate render merely to discover fallback state;
- temp files remain safely owned while waiting for approval;
- cancellation/back/`onCleared()` still cleans them;
- later UI can continue or cancel without bypassing the orchestrator;
- no persisted operation state;
- no WorkManager/background continuation.

Choose the smallest state-machine design that meets these constraints.

---

# 7. Re-evaluate operation state machine after fallback correction

If fallback confirmation becomes a real non-UI operation phase, update the phase model.

Likely candidate:

- `Idle`
- `Preparing`
- `AwaitingFallbackConfirmation`
- `CreatingHandoff`
- `UploadingSlot(ONE)`
- `UploadingSlot(TWO)`
- `Ready`
- `Failed`

Do not add a UI dialog.

The state itself may be exposed through StateFlow and later consumed by Block 11.

Define:

- exact fields in `AwaitingFallbackConfirmation`
- what method resumes the operation
- what method cancels it
- whether `operationJob` remains alive/suspended or whether the operation is split into resumable stages.

Choose one exact approach.

Avoid storing sensitive network data in the state.

At this point, before Create, no handoff token exists, which is desirable.

---

# 8. Resume/continue API

If fallback confirmation is a separate state, define exact ViewModel/orchestrator API.

Possible concepts:

- `confirmFallbackAndContinue()`
- `cancelOperation()`

Do not create generic event-dispatch architecture unless necessary.

Rules:

- Continue only valid while awaiting confirmation
- double continue ignored/rejected deterministically
- cancel cleans files
- start new operation while awaiting confirmation is not allowed
- `onCleared()` cleans files
- no network call before continue.

---

# 9. Temp-file lifetime during fallback confirmation

This must be explicit.

If renderer fallback produced valid temporary JPEGs:

- keep the same operation directory while awaiting confirmation;
- do not rerender after approval;
- approval resumes with those exact prepared files;
- cancel deletes them;
- `onCleared()` deletes them;
- hard process death may leave them, recovered by next-entry sweep.

Confirm.

Do not copy files elsewhere.

---

# 10. Cleanup/finally architecture after a pause state

The Block-8A design used one monolithic `execute()` with a `finally`.

If the operation must pause for user confirmation, determine whether a monolithic suspended coroutine can safely wait on a deferred/signal.

Evaluate the minimal pattern:

- orchestrator coroutine remains active;
- renderer returns `usedFallback=true`;
- phase becomes `AwaitingFallbackConfirmation`;
- coroutine suspends awaiting a one-shot continuation signal;
- ViewModel Continue completes the signal;
- Cancel cancels the job;
- `finally` still owns cleanup.

This may be simpler than splitting the operation into two functions.

Analyze:
- race safety
- multiple Continue calls
- cancellation while waiting
- ViewModel destruction.

If this pattern is safe, prefer it.

No UI implementation required.

---

# 11. Required correction to tests

Update the future test plan to include fallback-confirmation semantics.

At minimum:

- normal HQ path never enters `AwaitingFallbackConfirmation`
- fallback path enters `AwaitingFallbackConfirmation`
- no Create call occurs before explicit Continue
- Continue resumes same operation
- prepared files reused, no rerender
- Continue exactly once
- double Continue harmless/rejected
- Cancel while awaiting confirmation → no network, cleanup
- `onCleared()` while awaiting confirmation → cleanup
- start second operation while awaiting confirmation → ignored/rejected
- hard process death remains covered by next-entry sweep, not falsely guaranteed.

---

# 12. Recalculate request bound after fallback correction

Fallback confirmation itself must not add HTTP requests.

Confirm that the corrected maximum request count from §3 remains unchanged by:
- waiting for approval
- cancellation
- no-network-before-approval behavior.

---

# 13. Files — final corrected scope

Restate the exact implementation file list after resolving both corrections.

Expected candidates may still include:

- `WackelbildOperationState.kt`
- `WackelbildHandoffOrchestrator.kt`
- `WackelbildViewModel.kt`
- `WackelbildHandoffOrchestratorTest.kt`
- `WackelbildViewModelTest.kt`
- `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `IMPLEMENTATION_NOTES.md`

But do not assume blindly.

If fallback continuation requires an additional tiny type/file, justify it.

No UI file.
No screen file.
No strings.
No manifest.
No Gradle.
No BuildConfig.

---

# 14. Documentation consistency

The implementation plan must later be updated to lock:

- exact 1s/2s retry schedule
- max 3 handoff generations
- 409 → full handoff restart
- actual maximum reachable request count
- fallback-confirmation orchestration semantics
- no implicit approval.

Identify exact sections to modify.

Do not update docs now in this analysis gate.

---

# 15. Required final output

Return exactly:

## 1. Repository Baseline

## 2. Accepted Block-8A Decisions

Confirm preserved decisions.

## 3. Corrected Maximum Request Count

Show:
- reachable worst-case sequence
- exact count
- why 27 was wrong.

## 4. Fallback Confirmation Contract

Quote/summarize the authoritative requirement and state why default-true is invalid.

## 5. Final Fallback Architecture

Choose exactly one architecture.

No options left open.

## 6. Corrected Operation State Machine

Table:

| Phase | Entered when | Resume/exit rule | Cleanup behavior |

## 7. Fallback Continue/Cancel API

Exact method/state semantics.

## 8. Temp-File Lifetime

State exact ownership while awaiting confirmation.

## 9. Cancellation / Race Handling

Cover:
- cancel while waiting
- double continue
- start while waiting
- onCleared
- hard process death.

## 10. Retry/Restart Policy

Re-state:
- max attempts
- delays
- 403
- 409
- 410
- max generations
- corrected max request count.

## 11. Files Proposed for Modification / Creation

Table:

| File | Modify/Create | Exact change | Why |

## 12. Tests to Add / Update

Include fallback-confirmation cases plus all critical retry/restart/cancellation cases.

## 13. Documentation Impact

Exact plan sections to update during implementation.

## 14. Remaining Open Decisions

If none:

`None`

## 15. Gate Result

If fully corrected:

**BLOCK 8B CORRECTED SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Otherwise:

**BLOCK 8B BLOCKED — USER/EXTERNAL DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

Do not implement.

Correct exactly two issues:

1. derive the real reachable maximum HTTP request count;
2. remove implicit fallback approval and model explicit confirmation without adding UI.

Everything else from Block 8A remains in force unless one of these corrections directly requires an adjustment.
