# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 8: OPERATION ORCHESTRATION + RETRY/LIFECYCLE — IMPLEMENTATION

## Authorization

Block 8B corrected scope is approved.

Implement **exactly** the final Block-8 architecture established by Blocks 8A/8B.

This is an implementation gate.

Do not begin Block 9.
Do not add `INTERNET`.
Do not use the real pilot API key.
Do not make any real network request.
Do not add UI, CTA, strings or Custom Tabs.

The purpose of Block 8 is to compose the already-implemented:

- Block 5 print renderer
- Block 6 temp-file cleanup
- Block 7 raw API client

into one finite, deterministic, cancellation-safe handoff operation.

---

# 1. Mandatory source-of-truth review

Before editing, read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Then inspect current:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildApiClient.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtos.kt`
- corresponding tests

Also inspect existing repo precedents for:

- `NonCancellable`
- `onCleared()`
- `Job` ownership
- `StateFlow`
- coroutine-test virtual time
- constructor/test seams in current ViewModels.

Before modifying, report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 7 committed
- no Block-8 production code exists yet
- prompt archives may be untracked.

If repository state materially differs from the approved 8B scope, STOP and report before editing.

---

# 2. Authorized file scope

Modify/create exactly these seven files:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildOperationState.kt` — create
2. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildHandoffOrchestrator.kt` — create
3. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt` — modify
4. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildHandoffOrchestratorTest.kt` — create
5. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt` — modify
6. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` — modify
7. `docs/IMPLEMENTATION_NOTES.md` — modify

If any eighth file is required, STOP and report why before expanding scope.

No UI/screen/string/manifest/Gradle/BuildConfig file is authorized.

---

# 3. Preserve accepted Block-8B decisions

Implement these decisions exactly:

- dedicated `WackelbildHandoffOrchestrator`
- `operationJob` owned only by `WackelbildViewModel`
- no ViewModel `operationDir` field
- at most one active operation
- operation-local retry/idempotency/handoff variables inside orchestrator
- `StateFlow<WackelbildOperationState>`
- UUID idempotency key
- same key reused across Create retries
- new key on full handoff restart
- 3 total attempts per request stage
- delays: 1s then 2s
- retryable:
  - `RETRYABLE_NETWORK`
  - `RETRYABLE_SERVER`
  - `RATE_LIMITED`
- 403 → full handoff restart
- 409 → full handoff restart
- 410 → full handoff restart
- max 3 handoff generations
- prepared files reused across handoff restarts
- no re-render on restart
- Reference → slot `one`
- Capture → slot `two`
- cleanup via `finally` + `NonCancellable`
- `onCleared()` cancels active job
- hard process death recovered only by Block-6 next-entry sweep
- no implicit fallback approval
- explicit fallback-confirmation phase before any network call.

Do not reopen these decisions.

---

# 4. Operation state model

Create:

`WackelbildOperationState.kt`

Implement a small sealed model equivalent to:

```kotlin
sealed interface WackelbildOperationState {
    data object Idle : WackelbildOperationState
    data object Preparing : WackelbildOperationState
    data object AwaitingFallbackConfirmation : WackelbildOperationState
    data object CreatingHandoff : WackelbildOperationState
    data class UploadingSlot(val slot: DeinWackelbildSlot) : WackelbildOperationState
    data class Ready(
        val checkoutUrl: String,
        val usedFallback: Boolean
    ) : WackelbildOperationState
    data class Failed(
        val failure: WackelbildOperationFailure
    ) : WackelbildOperationState
}
```

Exact class/interface syntax may follow project style.

Also implement:

```kotlin
enum class WackelbildOperationFailureCategory {
    PREPARATION_FAILED,
    NETWORK_UNAVAILABLE,
    SERVER_TEMPORARY,
    INTEGRATION_UNAVAILABLE,
    HANDOFF_FAILED,
    INVALID_LOCAL_OUTPUT
}
```

and:

```kotlin
data class WackelbildOperationFailure(
    val category: WackelbildOperationFailureCategory,
    val classification: DeinWackelbildErrorClassification? = null
)
```

Requirements:

- no user-facing strings
- no secrets
- no handoff token
- no upload URL
- no server message.

---

# 5. Orchestrator architecture

Create:

`WackelbildHandoffOrchestrator.kt`

The orchestrator must remain stateless across calls.

Do not store:

- operation Job
- StateFlow
- active operation directory
- handoff state
- retries

as fields.

All operation state lives in local variables inside `execute()`.

The orchestrator may have only stable configuration seams such as:

- idempotency-key factory.

Use the approved pattern:

```kotlin
class WackelbildHandoffOrchestrator(
    private val idempotencyKeyFactory: () -> String = {
        UUID.randomUUID().toString()
    }
)
```

Do not introduce a generic workflow engine.

---

# 6. Orchestrator execute() contract

Implement one monolithic suspend operation.

It must receive all concrete operation dependencies at call time so current ViewModel test injection remains valid.

Conceptually include:

- session directory
- temp-file manager
- API client
- renderer lambda
- date overlay data
- fallback-confirmation suspend callback
- phase-change callback

The fallback callback is **required** and has **no default**:

```kotlin
awaitFallbackConfirmation: suspend () -> Unit
```

Do not use:

```kotlin
= { true }
```

Do not accept implicit approval.

Do not expose UI.

---

# 7. Happy-path sequence

Implement exactly:

1. Create operation directory.
2. Emit `Preparing`.
3. Render transfer pair through existing `WackelbildPrintRenderer`.
4. On renderer failure:
   - return operation failure
   - no API call.
5. On renderer success:
   - keep `referenceFile`
   - keep `captureFile`
   - preserve `usedFallback`.
6. If `usedFallback == true`:
   - invoke required `awaitFallbackConfirmation()`
   - do not generate network traffic until it returns.
7. Generate idempotency key.
8. Emit `CreatingHandoff`.
9. Create handoff through retry policy.
10. Validate Create semantic response.
11. Emit `UploadingSlot(ONE)`.
12. Upload Reference as slot `ONE`.
13. Validate slot-one semantic response.
14. Emit `UploadingSlot(TWO)`.
15. Upload Capture as slot `TWO`.
16. Validate slot-two semantic response.
17. Delete operation directory.
18. Emit/return:
   - `Ready(checkoutUrl, usedFallback)`
19. The outer `finally` also performs idempotent cleanup.

No Custom Tab call.

---

# 8. Renderer integration

Use the existing renderer entry point only.

Do not modify renderer internals.

The orchestrator must support test injection through a minimal lambda equivalent to:

```kotlin
suspend (File, File, WackelbildDateOverlay?) -> WackelbildPrintResult
```

or the exact current renderer signature.

Production default/call site may use:

`WackelbildPrintRenderer()::renderPrintPair`

if consistent with current API.

Do not create a new renderer interface solely for tests.

Do not reimplement:

- HQ logic
- fallback rendering
- date badge
- dimensions
- privacy logic.

---

# 9. Fallback confirmation

This is mandatory.

When render result says:

`usedFallback == true`

the orchestrator must:

- emit/allow ViewModel to expose `AwaitingFallbackConfirmation`
- suspend before Create
- keep the same operation directory/files
- perform zero API calls until confirmation.

The ViewModel will bridge this with a one-shot `CompletableDeferred<Unit>`.

No default approval path exists.

No rerender after approval.

---

# 10. ViewModel fallback signal

Modify `WackelbildViewModel.kt`.

Add:

```kotlin
private var operationJob: Job? = null
private var fallbackConfirmation: CompletableDeferred<Unit>? = null
```

Add operation state:

```kotlin
private val _operationState =
    MutableStateFlow<WackelbildOperationState>(WackelbildOperationState.Idle)

val operationState: StateFlow<WackelbildOperationState> =
    _operationState.asStateFlow()
```

Use existing ViewModel style exactly.

Implement a private suspend bridge conceptually:

```kotlin
private suspend fun awaitFallbackConfirmation() {
    val deferred = CompletableDeferred<Unit>()
    fallbackConfirmation = deferred
    _operationState.value =
        WackelbildOperationState.AwaitingFallbackConfirmation
    try {
        deferred.await()
    } finally {
        if (fallbackConfirmation === deferred) {
            fallbackConfirmation = null
        }
    }
}
```

The exact cleanup structure may vary but must avoid stale deferred references.

---

# 11. startOperation()

Implement one real operation entry point.

It must:

- ignore a second start while `operationJob?.isActive == true`
- launch one job in `viewModelScope`
- call orchestrator
- pass current dependencies at call time
- pass current `dateOverlayEnabled`
- pass current already-formatted date values needed by renderer
- pass `awaitFallbackConfirmation`
- update `_operationState` via phase callback/result
- never create a second concurrent operation.

Do not add UI behavior.

Do not add partner-key construction.

The API client dependency must remain injectable and future production wiring may be completed in Block 9.

If current ViewModel cannot obtain a production API client yet without a key, use the smallest compile-safe dependency seam consistent with existing internal constructor/test patterns.

Do not add a fake production key.

---

# 12. confirmFallbackAndContinue()

Implement:

```kotlin
fun confirmFallbackAndContinue()
```

Rules:

- valid only when a fallback confirmation is pending
- completes the current deferred
- otherwise safe no-op
- double call safe
- no exception
- does not create a second job
- does not rerender.

---

# 13. cancelOperation()

Implement:

```kotlin
fun cancelOperation()
```

Behavior:

- cancel `operationJob`
- synchronously set state to `Idle`
- safe if no operation active
- safe if called repeatedly
- safe while:
  - Preparing
  - AwaitingFallbackConfirmation
  - CreatingHandoff
  - UploadingSlot(ONE)
  - UploadingSlot(TWO)

Do not model cancellation as `Failed`.

No `Cancelled` state required.

---

# 14. onCleared()

Override `onCleared()` minimally:

- cancel active `operationJob`
- do not perform manual filesystem cleanup here
- rely on the operation's `finally`
- preserve Block-6 next-entry sweep for hard process death.

Do not claim cleanup is guaranteed after hard process kill.

---

# 15. Cleanup ownership

The orchestrator owns the operation directory lifecycle.

Use:

```text
try {
   ...
} finally {
   withContext(NonCancellable) {
       tempFileManager.deleteOperationDir(operationDir)
   }
}
```

Requirements:

- cleanup on success
- cleanup on terminal failure
- cleanup on cancellation
- cleanup after fallback-confirmation cancellation
- idempotent repeated delete is fine.

On success:

- delete temp dir immediately after validating slot-two `ready`
- before exposing `Ready`
- outer finally may call delete again; safe.

Do not retain transfer images through browser checkout.

---

# 16. Idempotency-key lifecycle

Generation:

```kotlin
UUID.randomUUID().toString()
```

Requirements:

- new key at first handoff generation
- same key reused for every Create retry in that generation
- new key on every full handoff restart
- no new key for:
  - upload retry
  - Create transient retry
- no derivation from user/session data.

Tests must inject deterministic keys.

---

# 17. Retry policy

Lock:

- max attempts per stage = 3 total
- attempt 1 immediately
- delay 1s
- attempt 2
- delay 2s
- attempt 3

Retryable classifications:

- `RETRYABLE_NETWORK`
- `RETRYABLE_SERVER`
- `RATE_LIMITED`

All use same schedule.

No Retry-After parsing in Block 8.

No custom sleeper abstraction.

Use coroutine `delay()`.

Do not retry:

- `INVALID_REQUEST`
- `INTEGRATION_UNAVAILABLE`
- `FILE_TOO_LARGE`
- `INVALID_IMAGE`
- `DIMENSION_MISMATCH`
- `MALFORMED_RESPONSE`
- `UNEXPECTED_HTTP_STATUS`
- `PERMANENT_LOCAL`

Restart classifications:

- `EXPIRED_HANDOFF`
- `INCOMPLETE_HANDOFF`

---

# 18. Full-handoff restart policy

A full restart occurs on:

- 403 mapped to `EXPIRED_HANDOFF`
- 410 mapped to `EXPIRED_HANDOFF`
- 409 mapped to `INCOMPLETE_HANDOFF`

On restart:

- reuse same rendered files
- reuse same operation directory
- do not rerender
- discard prior handoff token/upload URL locals
- generate a new Idempotency-Key
- return to Create stage.

Max handoff generations:

- 3 total
- generation 1 + max 2 restarts.

If a restart-triggering result occurs in generation 3:

- terminate with `HANDOFF_FAILED`.

No unbounded loop.

---

# 19. Maximum reachable HTTP request count

Document and test logic consistent with the proven bound:

**27 requests maximum**

Representative reachable sequence:

Per generation:

- Create:
  - transient
  - transient
  - success
- Upload one:
  - transient
  - transient
  - success
- Upload two:
  - transient
  - transient
  - restart-triggering result

= 9 requests.

Generation 1 → restart  
Generation 2 → restart  
Generation 3 → restart-triggering result ends operation

Total:

`9 + 9 + 9 = 27`

Do not use an unproven bare multiplication in documentation; preserve the explicit derivation.

---

# 20. Create semantic validation

After raw client success, require:

- `status == "awaiting_files"`
- handoff token valid/nonblank
- upload URL valid/nonblank

Parser already guarantees required data, but orchestration must validate expected business status.

Unexpected Create success semantics:

- no retry
- terminal `HANDOFF_FAILED`.

---

# 21. Upload-one semantic validation

Require:

- `status == "awaiting_files"`
- `uploadedSlots` contains `"one"`
- `checkoutUrl == null`

Any deviation:

- no retry
- terminal `HANDOFF_FAILED`.

Do not proceed to slot two.

---

# 22. Upload-two semantic validation

Require:

- `status == "ready"`
- `uploadedSlots` contains `"one"`
- `uploadedSlots` contains `"two"`
- `checkoutUrl != null`

Any deviation:

- no `Ready`
- terminal `HANDOFF_FAILED`.

Do not open checkout URL here.

---

# 23. Operation-level failure mapping

Implement the six approved categories:

- `PREPARATION_FAILED`
- `NETWORK_UNAVAILABLE`
- `SERVER_TEMPORARY`
- `INTEGRATION_UNAVAILABLE`
- `HANDOFF_FAILED`
- `INVALID_LOCAL_OUTPUT`

Map raw errors minimally and consistently.

Expected principles:

- exhausted `RETRYABLE_NETWORK` → `NETWORK_UNAVAILABLE`
- exhausted `RETRYABLE_SERVER` / `RATE_LIMITED` → `SERVER_TEMPORARY`
- 401 / `INTEGRATION_UNAVAILABLE` → `INTEGRATION_UNAVAILABLE`
- invalid local render/file conditions → `PREPARATION_FAILED` or `INVALID_LOCAL_OUTPUT`
- 400/403/409/410 exhaustion/malformed protocol/unexpected semantic shape → `HANDOFF_FAILED`
- 413/415/422 should map to `INVALID_LOCAL_OUTPUT` because locally prepared transfer output was rejected.

Retain raw classification internally in `WackelbildOperationFailure.classification`.

Do not expose raw server message.

---

# 24. No production secret wiring yet

Block 9 still owns the real partner-key source.

Therefore:

- do not read BuildConfig
- do not read local.properties
- do not read environment variables
- do not create a dummy production key.

Use the smallest dependency seam needed for Block 8 to compile and tests to inject a fake API client.

If the ViewModel's current production constructor cannot yet instantiate a real client, keep API-client provision injectable/deferred using the existing project test/constructor pattern.

Do not add Hilt wiring if not already part of the approved scope.

If production compilation genuinely requires a broader DI change, STOP and report before editing an eighth file.

---

# 25. No INTERNET yet

Do not modify:

`AndroidManifest.xml`

No `android.permission.INTERNET`.

Block 8 tests must use fake `DeinWackelbildApiClient`.

No real socket.

No live server.

---

# 26. Orchestrator tests

Create comprehensive JVM tests in:

`WackelbildHandoffOrchestratorTest.kt`

At minimum cover:

## Happy path

- render success
- Create success
- Reference uploaded as `ONE`
- Capture uploaded as `TWO`
- slot-two `ready`
- cleanup
- `Ready(checkoutUrl, usedFallback=false)`.

## Fallback confirmation

- non-fallback skips `AwaitingFallbackConfirmation`
- fallback enters/waits
- zero API calls before confirmation
- confirm resumes same operation
- no rerender
- same prepared files reused
- cancel while waiting → no network
- cleanup after cancel
- double confirmation harmless
- `onCleared()` behavior tested at ViewModel layer
- no implicit approval path.

## Renderer failure

- render failure → no network
- cleanup
- correct operation failure.

## Idempotency

- same key across Create transient retries
- new key on 403 restart
- new key on 410 restart
- new key on 409 restart
- new `execute()` operation gets new key.

## Retry

- network failure then success
- 5xx then success
- rate limit then success
- exact 1s/2s virtual delay behavior
- exactly 3 max attempts
- non-retryable 400 no retry.

## Restarts

- 403 restart
- 410 restart
- 409 restart
- files reused
- operation dir reused
- no rerender
- generation 3 restart-trigger → terminal
- no generation 4.

## 27 bound

Add a deterministic test or explicit call-count scenario proving the upper-bound logic does not exceed 27 requests under the worst-case configured sequence.

Do not artificially issue 27 network calls if a smaller structural test proves the bound more clearly; but the orchestration must be bounded and observable.

## Semantic validation

- Create wrong status → failure
- upload one wrong status → failure
- upload one missing `"one"` → failure
- upload one premature checkout → failure
- upload two not ready → failure
- upload two missing slot one → failure
- upload two missing slot two → failure
- upload two ready without checkout → failure.

## Cancellation

- during preparation
- during Create
- during retry delay
- during upload one
- during upload two
- cleanup always runs
- no Ready after cancellation.

No real API client.

---

# 27. ViewModel tests

Update:

`WackelbildViewModelTest.kt`

Keep this layer thin.

Test:

- initial operation state = Idle
- startOperation() starts one operation
- second start while active ignored
- orchestrator phase callbacks update StateFlow
- fallback state exposed
- confirmFallbackAndContinue resumes pending confirmation
- double confirm safe
- cancel while awaiting fallback → Idle
- cancel during active operation → Idle
- onCleared cancels active job
- no Ready after cancel
- existing tilt/date/metadata behavior remains unchanged.

Do not duplicate orchestrator retry matrix at ViewModel level.

---

# 28. Documentation plan sync

Update:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Minimally lock:

- retry delays = 1s then 2s
- max 3 attempts per stage
- 403 → restart
- 409 → restart
- 410 → restart
- max 3 handoff generations
- exact reachable max = 27 requests with explicit derivation
- fallback confirmation:
  - no implicit approval
  - `AwaitingFallbackConfirmation`
  - no network before explicit continue
  - same prepared files retained
  - cancel cleans them
- hard process death relies on next-entry sweep.

Remove now-resolved open TODOs in §30 relating to exact backoff timing.

Do not rewrite unrelated plan sections.

---

# 29. IMPLEMENTATION_NOTES

Append Block-8 entry to:

`docs/IMPLEMENTATION_NOTES.md`

Record:

- orchestrator introduced
- StateFlow operation model
- renderer/temp/API composition
- 3-attempt 1s/2s retry
- 3-generation restart bound
- 409/403/410 restart behavior
- max 27 HTTP requests
- explicit fallback-confirmation pause
- no implicit approval
- cancellation/finally/NonCancellable cleanup
- onCleared cancellation
- no INTERNET/key/UI yet.

Do not rewrite earlier entries.

---

# 30. Files explicitly forbidden

Do not modify:

- `app/src/main/AndroidManifest.xml`
- Gradle files
- `WackelbildScreen.kt`
- strings
- MainActivity/navigation
- Custom Tabs
- BuildConfig
- `local.properties`
- camera/session files
- `WackelbildPrintRenderer.kt`
- `WackelbildDimensionResolver.kt`
- `DateBadgeRenderer.kt`
- `WackelbildTempFileManager.kt`
- Block-7 client files unless compilation reveals a real contradiction; if so STOP first.

No unrelated refactor.
No format churn.
No rename.

---

# 31. Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Also run:

```bash
./gradlew compileDebugAndroidTestKotlin
```

only if required by current project verification conventions; no `androidTest` file is expected to change in this block.

No `connectedDebugAndroidTest`.

No physical-device test.

No live server.

Do not suppress failures.

---

# 32. Required final report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List every file.

Confirm exactly seven authorized files.

## 3. Operation Architecture

Describe:
- orchestrator
- ViewModel job ownership
- StateFlow
- no ViewModel operationDir.

## 4. State Machine

List all phases and transitions.

## 5. Fallback Confirmation

Confirm:
- no implicit approval
- pause before network
- continue/cancel behavior
- same temp files reused.

## 6. Retry / Restart

Report:
- 3 attempts
- 1s/2s
- retryable classifications
- 403/409/410 behavior
- 3 generations
- 27-request bound and test.

## 7. Idempotency

Report:
- UUID generation
- reuse
- regeneration.

## 8. Semantic Validation

Report exact Create/slot-one/slot-two requirements.

## 9. Cancellation / Cleanup

Report:
- explicit cancel
- retry-delay cancel
- API cancel propagation
- finally
- NonCancellable
- onCleared
- hard process-death limitation.

## 10. Failure Model

List six operation-level categories and mapping.

## 11. Tests

Report:
- orchestrator test count/result
- ViewModel test count/result
- full unit suite result.

## 12. Documentation

Report exact plan/notes updates.

## 13. Verification

Report exact results for:
- testDebugUnitTest
- assembleDebug
- assembleRelease
- diff check
- status
- AndroidTest compile if run.

## 14. Block Boundary Confirmation

Confirm no:
- INTERNET
- real key
- real request
- BuildConfig
- UI
- Custom Tab
- Gradle/manifest changes.

## 15. Diff Scope

Confirm no unauthorized edits.

## 16. Remaining Work

State only:
- Block 9 key injection
- Block 10 INTERNET/activation
- later UI/CTA/Custom Tab
- real pilot test after activation.

## 17. Gate Result

If successful:

**BLOCK 8 IMPLEMENTED — READY FOR REVIEW**

If blocked:

**BLOCK 8 BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final instruction

Implement exactly the final Block-8B architecture.

No hidden approval.
No infinite retries.
No unbounded handoff restarts.
No UI.
No secret.
No live network.

The result must be a finite, deterministic, cancellation-safe orchestration layer ready for later production activation.
