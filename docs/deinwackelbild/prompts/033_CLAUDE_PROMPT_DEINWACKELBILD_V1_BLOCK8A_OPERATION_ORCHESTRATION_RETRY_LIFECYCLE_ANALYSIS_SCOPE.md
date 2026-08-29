# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 8A: OPERATION ORCHESTRATION + RETRY/LIFECYCLE — ANALYSIS & SCOPE CONFIRMATION ONLY

## Purpose

DeinWackelbild Blocks 1–7 are implemented and committed.

Block 8 is the first block that connects the previously isolated layers into one real operation:

`prepare/render → temp files → create handoff → upload Reference → upload Capture → ready result`

It also becomes the first block where the previously deferred operation lifecycle becomes real:

- active operation ownership
- operation job
- idempotency-key lifecycle
- retry/backoff
- cancellation
- cleanup on success/error/cancel
- ViewModel destruction behavior
- handoff restart behavior after expired/invalid handoff

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files.
Do not implement code.
Do not add `INTERNET`.
Do not add the real pilot key.
Do not make any network request.
Do not begin Block 9/10/11.
Do not add UI/CTA/Custom Tabs yet unless the current authoritative plan explicitly requires a non-visual state surface for Block 8.

The goal is to derive the smallest, fully deterministic orchestration architecture before any implementation.

---

# 1. Mandatory source-of-truth review

Read fully:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation in detail:

## Wackelbild
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/DateBadgeRenderer.kt`

## Network
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildApiClient.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtos.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt`

## Tests
- `WackelbildViewModelTest.kt`
- `WackelbildTempFileManagerTest.kt`
- `WackelbildPrintRendererInstrumentedTest.kt`
- `DeinWackelbildDtoParsingTest.kt`
- `OkHttpDeinWackelbildApiClientTest.kt`

Also inspect current repository precedents for:
- retry loops
- backoff
- operation state machines
- coroutine Job ownership
- `NonCancellable`
- `onCleared()` cancellation
- user-triggered long-running work
- result propagation from ViewModel.

Prefer existing project patterns where appropriate, but do not copy unrelated architecture blindly.

---

# 2. Repository baseline

Report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 7 committed
- no production Block-8 files exist yet
- prompt archives may be untracked.

If the repository materially differs from the committed Block-7 state, STOP and report before proposing scope.

---

# 3. Block-8 boundary

Reconfirm the corrected implementation plan.

Block 8 should own the orchestration only.

Expected responsibilities:

- one real active Wackelbild operation
- rendering/preparation invocation
- temp operation directory ownership
- idempotency-key generation and reuse
- create-handoff request
- Reference upload to `slot=one`
- Capture upload to `slot=two`
- retry/backoff
- handling 403/409/410 semantics
- operation state/result
- cancellation
- cleanup on all terminal paths
- `onCleared()` cancellation
- recovery-compatible lifecycle with Block-6 next-entry sweep.

Block 8 must still **not** own:

- BuildConfig partner-key source (Block 9)
- manifest `INTERNET` permission (Block 10)
- Custom Tab opening
- CTA/consent UI
- product/order UI
- real pilot-server test.

Do not let the presence of the raw API client pull later blocks forward.

---

# 4. Key architectural question: where orchestration lives

Determine the exact owner.

Likely candidates:

- directly inside `WackelbildViewModel`
- a dedicated `WackelbildHandoffOrchestrator`
- a dedicated use-case/service object owned by ViewModel.

Choose the smallest design that:

- avoids bloating the ViewModel with protocol-specific retry logic;
- remains testable with fake renderer/API client/temp manager;
- gives exactly one owner of `operationJob` and `operationDir`;
- does not create a reusable abstraction with no second consumer.

Do not introduce a generic workflow engine.

If a dedicated orchestrator is justified, define exact responsibilities and boundaries.

If not, justify keeping logic in ViewModel.

---

# 5. Active-operation ownership

Lock one exact model.

Requirements:

- at most one active operation per Wackelbild screen/ViewModel;
- second start while active must be deterministically rejected/ignored;
- operation owns:
  - operation ID
  - temp directory
  - prepared Reference/Capture files
  - idempotency key
  - current handoff response data
  - retry attempt counters
  - current phase
- no operation state persisted to DataStore/SavedStateHandle;
- no background continuation after screen/ViewModel destruction;
- no WorkManager.

Determine which state belongs:
- internal-only
- exposed as StateFlow for later UI
- one-shot completion event.

Do not add user-visible strings here.

---

# 6. Idempotency-key lifecycle

The API contract requires:

- 8–100 chars
- `[A-Za-z0-9._-]`
- same Create retry within same logical operation uses the same key.

Analyze and lock:

## Generation

Choose one exact algorithm.

Likely:

- UUID string

Check that it satisfies the allowed character set and length.

Do not derive key from:
- sessionId
- handoffId
- timestamp alone
- file hash
- user data.

## Reuse

Within one logical handoff attempt:
- Create network retry → same key.

## Regeneration

Determine exact cases where a new key is required:

- HTTP 410 expired handoff
- HTTP 403 invalid handoff token
- perhaps any explicit full-handoff restart
- user manually starts a new operation after terminal failure.

Do not regenerate for:
- transport retry on Create
- transport retry on upload within same valid handoff.

Lock the rule.

---

# 7. Retry model — exact counting

The external contract says:

- Create and uploads: up to three attempts
- increasing pause
- Create retries reuse the same Idempotency-Key.

The current plan previously left exact backoff intervals open.

Block 8 must resolve them now.

Choose exact values.

Use the smallest deterministic schedule.

For example evaluate:

- attempt 1 immediately
- retry 2 after 1s
- retry 3 after 2s

or another exact plan-supported sequence.

Requirements:

- max 3 total attempts per request stage, not 3 retries after initial;
- delays cancellation-aware;
- no retry after non-retryable classifications;
- no hidden OkHttp retries (already disabled);
- attempt count resets per stage:
  - Create
  - upload one
  - upload two
- same upload may be resent idempotently after transport failure.

Lock exact retryable classifications.

Likely:
- `RETRYABLE_NETWORK`
- `RETRYABLE_SERVER`
- `RATE_LIMITED`

But inspect the plan.

Decide whether `RATE_LIMITED` uses same backoff or a separate fixed delay.

Do not invent Retry-After support unless raw client exposes it.

---

# 8. HTTP/business reaction matrix

Build the exact Block-8 decision matrix.

For each raw classification/status, state:

- retry same request?
- restart whole handoff?
- continue to next stage?
- terminal failure?
- cleanup now?
- retain current temp files during retry?

Must cover:

- 400 INVALID_REQUEST
- 401 INTEGRATION_UNAVAILABLE
- 403 EXPIRED_HANDOFF / invalid token
- 409 INCOMPLETE_HANDOFF
- 410 EXPIRED_HANDOFF
- 413 FILE_TOO_LARGE
- 415 INVALID_IMAGE
- 422 DIMENSION_MISMATCH
- 429 RATE_LIMITED
- 5xx RETRYABLE_SERVER
- RETRYABLE_NETWORK
- MALFORMED_RESPONSE
- UNEXPECTED_HTTP_STATUS
- PERMANENT_LOCAL.

## 403 / 410

The confirmed contract says start a new handoff.

Determine:
- whether prepared files are reused
- whether operation directory is reused
- whether idempotency key is regenerated
- whether the whole-handoff restart count is bounded
- how to prevent infinite loop if server repeatedly returns 403/410.

This is critical.

## 409

The contract says "upload missing slot".

The raw client only returns classification plus server code/status, not structured uploaded slots on HTTP error unless error body contains them.

Determine what Block 8 can safely do.

Do not guess beyond what the API guarantees.

If 409 semantics cannot be safely implemented from current error data, identify the smallest safe rule.

---

# 9. Whole-handoff restart bound

This must be explicit.

Without a bound, repeated 403/410 could loop forever.

Choose one exact maximum number of fresh handoffs per user operation.

Consider whether the existing "up to three attempts" recommendation should also bound full handoff recreation.

Document exact maximum theoretical request count.

The user operation must always terminate.

No unbounded loop.

---

# 10. Operation phases

Define the exact internal phase model.

Likely candidates:

- IDLE
- PREPARING
- CREATING_HANDOFF
- UPLOADING_REFERENCE
- UPLOADING_CAPTURE
- READY
- FAILED

Possibly:
- CANCELLING
- RESTARTING_HANDOFF

Only add states that are necessary.

Requirements:
- UI-independent names
- deterministic transitions
- no user-visible strings
- READY must contain exact `checkout_url`
- terminal failure must carry typed operation failure
- no persisted state.

Determine whether `IDLE` belongs in the operation state or outside it.

---

# 11. Preparation/rendering integration

Block 8 now genuinely calls Block 5.

Define exact flow:

1. create operation directory via `WackelbildTempFileManager`
2. call `WackelbildPrintRenderer`
3. obtain `referenceFile` and `captureFile`
4. if renderer fallback succeeded, continue but preserve `usedFallback=true` for later UX warning
5. if renderer fails, terminal local failure
6. only after successful render start network Create stage.

Determine exact inputs required from `WackelbildViewModel`:

- sessionId
- dateOverlayEnabled
- formatted date strings
- metadata/viewport details already present in renderer input.

Do not duplicate renderer logic in ViewModel.

Check whether current renderer API already exposes everything needed or whether Block 8 would require a new adapter/input construction helper.

If a new production file is required, list it.

---

# 12. Partner-key dependency before Block 9

Block 8 must orchestrate an API client but Block 9 owns real key injection.

Determine how Block 8 is implemented/tested now.

Likely:

- ViewModel/orchestrator receives `DeinWackelbildApiClient`
- tests use fake client
- production wiring to concrete key-bearing client is deferred to Block 9.

Confirm exact DI boundary.

Do not add dummy production keys.

Do not add feature-disable logic yet unless necessary.

---

# 13. No INTERNET permission yet

Block 10 owns manifest activation.

Therefore Block 8 implementation must remain testable entirely with fakes.

No real API call can succeed in the app yet.

This is expected.

Do not add a temporary permission just to test orchestration.

---

# 14. Cancellation design

Now that the real operation exists, implement the lifecycle that Block 6 intentionally deferred.

Analyze exact design.

Requirements:

- `operationJob: Job?`
- one active operation max
- cancellation from future UI/Back action
- `onCleared()` cancels active operation
- cancellation propagates through:
  - render/preparation if cooperative
  - retry delays
  - API calls
- cleanup in `finally`
- cleanup executed under `NonCancellable` or equivalent approved pattern
- operation directory deleted on:
  - success
  - terminal failure
  - cancellation
- next-entry sweep remains hard-process-death recovery.

Important:

If successful handoff needs temp files only until both uploads are accepted, cleanup may happen before later Custom Tab opening.

Confirm exact cleanup point:
- after `status=ready` + `checkout_url` received
- before exposing READY result.

Do not keep temp files through browser checkout.

---

# 15. `onCleared()` guarantee wording

Be precise:

- `onCleared()` cancellation handles normal ViewModel destruction while process remains alive;
- active operation's `finally` is primary cleanup;
- `NonCancellable` prevents ordinary coroutine cancellation from skipping cleanup;
- hard process death still may bypass all teardown;
- Block-6 next-entry sweep is the recovery backstop.

Do not claim hard-kill guarantees.

---

# 16. Cleanup race cases

Analyze:

1. render succeeds, cancellation before Create
2. cancellation during Create
3. cancellation during retry delay
4. cancellation during upload one
5. cancellation after upload one but before upload two
6. cancellation after upload two request sent but before response arrives
7. READY received while cancellation arrives concurrently
8. terminal failure while cleanup also requested
9. `onCleared()` while explicit cancel already in progress
10. cleanup called twice.

State exact safe outcome and state-transition rule.

---

# 17. Success result

Define what Block 8 emits when complete.

Minimum required data:

- `checkoutUrl`
- `usedFallback`

Potentially:
- handoffId (internal only?)

Prefer data minimization.

Do not expose:
- partner key
- handoff token
- upload URL
- raw server error message.

Determine exact success type.

---

# 18. Failure model

Create a Block-8 operation-level failure model distinct from raw API errors.

Do not leak raw transport enum directly to UI later unless intentional.

Possible categories:

- PREPARATION_FAILED
- NETWORK_UNAVAILABLE
- SERVER_TEMPORARY
- INTEGRATION_UNAVAILABLE
- INVALID_LOCAL_OUTPUT
- HANDOFF_FAILED

Cancellation may be a non-error terminal state.

Do not invent user-facing labels.

Determine exact minimal categories needed for later UX mapping.

---

# 19. StateFlow/event semantics

Determine how the ViewModel exposes operation state.

Use a stable observable model.

Likely:
- `StateFlow<WackelbildOperationState>`

Avoid one-shot event loss.

READY must survive recomposition until consumed/cleared by later UI.

But do not persist across process death.

Determine:
- how state resets before a new operation
- whether READY remains until screen leaves
- how cancellation returns to idle.

Do not add UI code yet.

---

# 20. Retry helper design

Decide whether retry logic belongs:

- private function inside orchestrator/ViewModel
- small `RetryPolicy` helper object
- separate production file.

Avoid a generic reusable retry framework.

The helper must support:
- max attempts
- exact delay sequence
- cancellation-aware `delay`
- predicate based on typed result.

If separate helper is not justified, keep local.

---

# 21. Testability architecture

Block 8 must be exhaustively JVM-testable with fakes.

Determine fake seams:

- fake `DeinWackelbildApiClient`
- fake/real temp manager with temp dir
- renderer fake or injectable renderer interface/wrapper
- controllable coroutine scheduler
- deterministic idempotency-key generator.

The current `WackelbildPrintRenderer` may be a concrete class not interface-based.

Do not make a broad production abstraction solely for tests unless needed.

Choose the smallest seam.

Tests must not encode real JPEGs unless necessary for orchestration.

---

# 22. Idempotency-key generator seam

To test reuse/regeneration exactly, key generation must be deterministic in tests.

Preferred:
- constructor lambda `idempotencyKeyFactory: () -> String = { UUID.randomUUID().toString() }`

Confirm whether this fits repository style.

Test:
- same key across Create retries
- new key on handoff restart
- new user operation → new key.

---

# 23. Delay/backoff seam

Tests must not actually sleep.

Use coroutine-test scheduler if retry uses `delay`.

Determine whether existing tests use `StandardTestDispatcher`/`advanceTimeBy`.

Prefer built-in coroutine-test scheduling.

Do not add a custom sleeper abstraction unless required.

---

# 24. Detailed state-machine walkthrough

Write the exact happy path:

1. start operation
2. PREPARING
3. render pair
4. generate idempotency key
5. CREATING_HANDOFF
6. create handoff with retry policy
7. UPLOADING_REFERENCE
8. upload Reference / slot one with retry policy
9. UPLOADING_CAPTURE
10. upload Capture / slot two with retry policy
11. verify:
    - status == ready
    - checkoutUrl != null
12. cleanup temp operation dir
13. READY(checkoutUrl, usedFallback)

Now define exact paths for:
- Create exhausts retries
- upload one exhausts retries
- upload two exhausts retries
- 403/410 restart
- 409
- 401
- local renderer failure
- cancellation.

No ambiguity.

---

# 25. Validate raw response semantics

The raw API client currently parses status as String.

Block 8 owns business validation.

Determine exact checks:

## Create success must require:
- `status == "awaiting_files"`?
- nonblank handoff token
- valid upload URL already guaranteed by parser.

## Upload-one success must require:
- status == `awaiting_files`
- `uploaded_slots` contains `one`
- checkout null?

## Upload-two success must require:
- status == `ready`
- `uploaded_slots` contains one and two
- checkout non-null

Lock exact behavior.

Unknown/malformed semantic success states should not proceed to Custom Tab.

Map to operation failure, not retry unless specified.

---

# 26. 409 semantics

This deserves explicit treatment.

The confirmed server table says:

`409` — both files not yet complete — upload missing slot.

But the error envelope does not necessarily include `uploaded_slots`.

Since the client already knows its current stage, analyze the safest deterministic behavior.

Do not invent a clever recovery without data.

If the contract is insufficient to know the missing slot, identify whether Block 8 should conservatively restart the full handoff on 409.

Choose one exact rule and justify against idempotency/server semantics.

If external clarification is genuinely necessary, mark Block 8A blocked rather than guessing.

---

# 27. Whole-operation retry explosion analysis

Calculate the theoretical maximum request count with your chosen:
- per-stage attempts
- handoff restart bound.

This is required.

Choose a bounded model that is robust but not excessive.

Document exact maximum.

---

# 28. Files likely modified/created

Determine exact file scope.

Likely candidates may include:

### Modify
- `WackelbildViewModel.kt`
- `WackelbildViewModelTest.kt`
- `docs/IMPLEMENTATION_NOTES.md`
- possibly `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` if backoff/restart decisions close previously-open plan items

### Create
Potentially:
- `WackelbildOperationState.kt`
- `WackelbildHandoffOrchestrator.kt`
- tests

But do not assume these files are needed.

Enumerate exact final files only after choosing architecture.

No UI file unless strictly required.

No manifest.
No Gradle unless a new test dependency is genuinely missing.

---

# 29. Documentation consistency

The plan still has open items around exact retry/backoff timing.

Block 8 is the point where those become real technical decisions.

Therefore if this analysis locks:
- exact backoff intervals
- handoff restart bounds
- 409 behavior

the implementation must later minimally sync the authoritative implementation plan.

Identify exact sections to update.

Do not leave source-of-truth behind code.

---

# 30. Tests to plan

Design exact tests.

At minimum:

## Happy path
- render success
- create success
- upload one success
- upload two ready
- cleanup
- READY result
- Reference→one / Capture→two verified.

## Rendering
- renderer fallback success propagates `usedFallback=true`
- renderer permanent failure → terminal
- no network call after render failure.

## Idempotency
- same key reused across Create retry
- new key on 403/410 whole-handoff restart
- new operation uses new key.

## Retry
- network failure then success
- server 5xx then success
- rate limit then success
- non-retryable 400 no retry
- max attempts stop exactly
- delay sequence exact using virtual time.

## Restart
- 403 restart
- 410 restart
- restart bound reached → terminal
- prepared files reused across handoff restart
- no rerender unless spec says otherwise.

## Uploads
- Reference uploaded as one
- Capture uploaded as two
- upload-one retry
- upload-two retry
- no duplicate next-stage transition before success.

## Success semantics
- upload-two ready + checkout → READY
- ready without checkout → failure
- unexpected status → failure
- upload-one wrong semantic response → failure.

## 409
According to the rule chosen in this gate.

## Cancellation
- during rendering
- during Create
- during retry delay
- during upload one
- during upload two
- explicit cancel
- `onCleared`
- no READY emitted after cancellation
- cleanup still occurs.

## Cleanup
- success
- terminal failure
- cancellation
- repeated cleanup safe
- next-entry sweep remains compatible.

## Single active operation
- second start while active rejected/ignored
- new start after terminal state allowed according to chosen reset rule.

No real network.
No real API key.

---

# 31. Verification commands after future implementation

At minimum:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Assess whether Android instrumentation is actually required.

If no UI change and all lifecycle logic is in ViewModel/coroutines with fake dependencies, JVM tests may be sufficient.

No physical server test yet.

---

# 32. Required final response

Return exactly:

## 1. Repository Baseline

## 2. Source-of-Truth Verification

## 3. Block-8 Boundary

Separate:
- implement now
- defer.

## 4. Orchestration Owner

State exact architecture and why.

## 5. Active Operation Model

State exact owned fields/state.

## 6. Operation State Machine

Table:

| Phase | Entered when | Exits on success | Exits on failure/cancel |

## 7. Happy-Path Sequence

Numbered exact sequence.

## 8. Idempotency-Key Lifecycle

Generation/reuse/regeneration rules.

## 9. Retry Policy

State exact:
- max attempts
- delays
- retryable classifications
- per-stage reset behavior.

## 10. Handoff Restart Policy

State exact:
- 403 behavior
- 410 behavior
- 409 behavior
- max handoff generations
- theoretical max request count.

## 11. Response Semantic Validation

Exact required success conditions for Create/upload-one/upload-two.

## 12. Cancellation / Cleanup Lifecycle

Explain:
- explicit cancel
- onCleared
- finally
- NonCancellable
- hard process death / next-entry sweep.

## 13. Success Result

Exact fields exposed.

## 14. Failure Model

Exact operation-level categories.

## 15. Testability Design

Exact fake/injection seams.

## 16. Files Proposed for Modification / Creation

Table:

| File | Modify/Create | Exact change | Why |

Enumerate every file.

## 17. Files Explicitly Not Touched

Confirm:
- manifest
- Gradle unless needed
- UI
- Custom Tabs
- BuildConfig secret injection
- camera/session files
- Block-5 renderer internals.

## 18. Tests to Add / Update

Exact files and cases.

## 19. Documentation Impact

Exact sections/docs requiring sync.

## 20. Verification Commands

Exact commands.

## 21. Risks / Blockers

Only genuine Block-8 risks.

## 22. Remaining Open Decisions

If none:

`None`

Otherwise list real blockers only.

## 23. Gate Result

If fully specified:

**BLOCK 8A SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If required contract detail is still missing:

**BLOCK 8A BLOCKED — USER/EXTERNAL DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

Do not implement.

Block 8 is the first true end-to-end **operation orchestration** block, but still not a live-network block.

No `INTERNET`.
No real key.
No real API request.
No UI/CTA/Custom Tab.

The goal is a finite, deterministic, cancellation-safe state machine that composes Blocks 5, 6 and 7 without expanding scope.
