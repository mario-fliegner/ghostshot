# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 7C: NETWORK CLIENT + DTOs — FINAL ANALYSIS & SCOPE CONFIRMATION

## Purpose

Block 7A originally analyzed the networking layer but correctly stopped because three wire-level API details were not yet confirmed.

Block 7B has now synchronized the repository documentation with the confirmed pilot contract and locked the remaining SameView slot mapping:

- Reference → API slot `one`
- Capture → API slot `two`

The network-client implementation is therefore unblocked.

This gate is the **final pre-implementation analysis/scope confirmation for Block 7**.

Do **not** implement code in this gate.

Do not add dependencies.
Do not modify Gradle.
Do not modify the manifest.
Do not add `INTERNET`.
Do not add BuildConfig key wiring.
Do not make any real network call.
Do not use the real pilot API key.
Do not begin Block 8.

The objective is to convert the now-confirmed contract into one exact, implementation-ready file/test scope with no remaining wire ambiguity.

---

# 1. Mandatory repository/source review

Read fully:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current project files relevant to adding the first HTTP dependency:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`

Inspect existing repository patterns for:

- `org.json`
- coroutine cancellation
- injected test seams
- result/error sealed types
- debug logging conventions.

Report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 6 committed
- Block 7B docs may still be uncommitted unless the user committed them
- no production networking code exists yet.

If repository state materially differs from the now-corrected docs, STOP and report it.

---

# 2. Security rule

A real pilot API key exists outside the repository.

Do not search for it.
Do not reproduce it.
Do not add it to any prompt-generated code plan.

Block 7 must operate with an injected credential value only.

Block 9 later owns:

- local.properties/env
- BuildConfig field
- real pilot/release secret injection.

Block 7 must not know where the key comes from.

---

# 3. Confirmed wire contract — treat as locked

## 3.1 Create handoff

Method:

`POST`

URL:

`https://deinwackelbild.de/wp-json/dwb/v1/partner-handoffs`

Headers:

- `Content-Type: application/json`
- `X-DWB-Partner-Key: <injected key>`
- `Idempotency-Key: <caller-supplied stable key>`

Idempotency-Key constraints:

- required
- 8–100 chars
- allowed: `[A-Za-z0-9._-]`

SameView V1 body:

```json
{
  "partner": "sameview",
  "locale": "de-DE"
}
```

`external_reference` is supported by the partner API but **deliberately omitted** by SameView V1.

Success:

`201 Created`

Confirmed fields:

- `handoff_id`
- `handoff_token`
- `partner`
- `status`
- `expires_at`
- `max_file_bytes`
- `accepted_types`
- `uploaded_slots`
- `upload_url`
- `checkout_url`

Initial expected values include:

- `status = "awaiting_files"`
- `checkout_url = null`

Unknown additive response fields must be ignored.

## 3.2 Upload

Use the exact `upload_url` returned by Create.

Do not reconstruct it.

Method:

`POST`

Header:

`X-DWB-Handoff-Token: <handoff token>`

Do **not** send `X-DWB-Partner-Key` on upload requests.

Multipart:

- text field `slot`
- file field `file`
- media type `image/jpeg`

Slot mapping is now fixed:

- Reference → `slot=one`
- Capture → `slot=two`

Exactly one JPEG per request.

## 3.3 Upload one

Expected success:

`200 OK`

Response:

- `handoff_id`
- `status = "awaiting_files"`
- `uploaded_slots` contains `"one"`
- `checkout_url = null`

## 3.4 Upload two

Expected success:

`200 OK`

Response:

- `handoff_id`
- `status = "ready"`
- `uploaded_slots` contains `"one"` and `"two"`
- non-null `checkout_url`

There is **no V1 polling endpoint**.

The raw API client must not poll.

The later state machine may proceed only when:

- `status == "ready"`
- `checkout_url != null`

## 3.5 Error body

Confirmed WordPress REST envelope:

```json
{
  "code": "...",
  "message": "...",
  "data": {
    "status": 415
  }
}
```

Important statuses:

- 400
- 401
- 403
- 409
- 410
- 413
- 415
- 422
- 429
- 5xx

Block 7 classifies raw transport/HTTP/protocol outcomes only.

Block 8 owns business reactions and retries.

---

# 4. Exact Block-7 boundary

Block 7 may implement:

- OkHttp dependency
- DTOs
- raw API client interface
- concrete OkHttp implementation
- JSON parsing
- raw HTTP/transport/protocol error model
- socket-free JVM tests
- `docs/IMPLEMENTATION_NOTES.md` Block-7 implementation entry later.

Block 7 must **not** implement:

- `INTERNET` permission
- ViewModel wiring
- state machine
- `operationJob`
- retries
- backoff
- idempotency-key generation
- temp-file lifecycle orchestration
- partner-key BuildConfig wiring
- Custom Tabs
- UI
- consent dialog
- CTA
- order status.

Reconfirm this against the now-corrected plan.

---

# 5. Client interface — finalize exact signatures

Derive the exact implementation-ready interface.

Expected shape conceptually:

```kotlin
interface DeinWackelbildApiClient {
    suspend fun createHandoff(
        request: CreateHandoffRequest,
        idempotencyKey: String
    ): DeinWackelbildResult<CreateHandoffResponse>

    suspend fun uploadImage(
        uploadUrl: String,
        handoffToken: String,
        slot: DeinWackelbildSlot,
        file: File
    ): DeinWackelbildResult<UploadResponse>
}
```

Confirm whether this exact boundary is sufficient.

Requirements:

- no OkHttp types in interface
- no secret-provider interface unless actually needed
- fakeable in JVM
- client stateless across handoffs
- no UUID generation inside client
- no retry state inside client.

If partner key is constructor-injected into the concrete client, document why that is sufficient for Block 9.

Do not over-generalize.

---

# 6. DTOs — finalize exact model

Define exact Kotlin DTOs and required validations.

At minimum analyze:

### `CreateHandoffRequest`

Fields:

- partner
- locale

Rules:

- partner should be fixed to `"sameview"` either by constructor/default/factory
- locale optional or nullable according to contract
- no `externalReference`

### `CreateHandoffResponse`

Fields:

- handoffId
- handoffToken
- partner
- status
- expiresAt
- maxFileBytes
- acceptedTypes
- uploadedSlots
- uploadUrl
- checkoutUrl

Decide exact Kotlin types for:

- `expiresAt`
- `maxFileBytes`
- `acceptedTypes`
- `uploadedSlots`
- nullable checkout URL.

Avoid adding a date/time library if a raw string is sufficient for Block 8.

### `UploadResponse`

Fields:

- handoffId
- status
- uploadedSlots
- checkoutUrl

### Error envelope

Fields:

- code
- message
- nested data.status

Determine whether a tiny nested DTO is cleaner than flattening `dataStatus`.

No user-facing string behavior.

---

# 7. URL validation

The API returns:

- `upload_url`
- `checkout_url`

Determine exact validation rules.

## Upload URL

Must be:

- valid HTTPS URL
- parsed safely by OkHttp/HttpUrl
- used exactly as returned for the request target

Do not manually concatenate path segments.

Consider whether Block 7 should additionally require host:

`deinwackelbild.de`

for security, or whether that would make testability harder and should instead be a production-base/client invariant.

Decide explicitly.

## Checkout URL

Block 7 must:

- validate syntax
- require HTTPS
- preserve exact returned string/value
- not open/fetch it

Do not strip fragments or normalize away token fragments.

This is important because the returned checkout URL may contain a fragment-based handoff token.

Do not accidentally lose `#...`.

---

# 8. OkHttp version/dependency

Reconfirm the plan-locked version:

`com.squareup.okhttp3:okhttp:4.12.0`

If the current plan explicitly locks it, use that.

Confirm required Gradle changes:

- one version-catalog version
- one library alias
- one `implementation(...)`

No:

- Retrofit
- Moshi
- Gson
- kotlinx.serialization
- MockWebServer
- logging-interceptor.

Confirm no ProGuard rule change should be needed unless release build proves otherwise.

---

# 9. JSON implementation

Use:

- `org.json.JSONObject`
- `JSONArray`

No new serializer.

Define parser behavior:

- malformed JSON → protocol failure
- missing required field → protocol failure
- blank required string → protocol failure
- extra fields ignored
- optional `checkout_url = null` accepted
- invalid URL → protocol failure
- unknown `status` string: decide whether raw client accepts it as string or rejects it.

Prefer raw strings for status if Block 8 owns business semantics, unless the plan already locks an enum.

Do not prematurely bake state-machine meaning into DTO parsing.

---

# 10. Error model — finalize exact sealed types

Define one coherent raw-client result model.

For example:

```text
DeinWackelbildResult<T>
- Success<T>
- Failure(error)
```

Error must distinguish at least:

### Transport
- network/connectivity failure
- timeout

### HTTP
- invalid request (400)
- integration unavailable (401)
- forbidden/invalid handoff token (403)
- incomplete handoff (409)
- expired handoff (410)
- file too large (413)
- invalid image (415)
- dimension mismatch (422)
- rate limited (429)
- server error (5xx)
- other HTTP status

### Protocol
- malformed/missing response
- invalid returned URL

### Cancellation
Must **not** become Failure if coroutine cancellation is expected to propagate.

Confirm exact class/enum structure.

Do not include UI text.

Do not embed secret/header values in error messages.

---

# 11. Error-envelope parsing

For non-2xx responses:

- parse body best-effort into error envelope
- retain technical `code`
- nested `data.status`
- optionally retain message internally if safe/needed
- do not depend on parsing success to classify HTTP status.

If error body itself is malformed:

- still classify by HTTP code
- body parsing failure must not replace a clear HTTP classification with generic protocol failure.

Decide exact behavior.

---

# 12. Cancellation model

This is important because Block 8 will later own retries/jobs.

Choose the concrete transport implementation pattern.

Preferred approach to evaluate:

- `Call.enqueue()`
- `suspendCancellableCoroutine`
- `invokeOnCancellation { call.cancel() }`

Requirements:

- cancellation cancels the OkHttp Call
- `CancellationException` propagates
- no conversion to generic network failure
- response closed exactly once
- no hidden background request surviving cancelled coroutine.

Compare briefly against blocking `execute()` inside `Dispatchers.IO`.

Choose one.

Do not add general networking framework code.

---

# 13. Retry behavior inside OkHttp

Block 8 owns retries.

Therefore decide exact OkHttp config:

- `retryOnConnectionFailure(false)`

Reconfirm why.

Also inspect redirect behavior.

The API contract does not require redirects.

For security/determinism, evaluate whether to disable:

- `followRedirects`
- `followSslRedirects`

or leave defaults.

Choose explicitly and justify based on:

- exact API-returned upload URL
- no hidden host changes
- retry/accounting transparency
- checkout URL is never requested by this client.

Do not leave as accidental default.

---

# 14. Timeouts — make implementation-ready

The external contract requires upload connect/write timeout at least 60 seconds.

The previous 7A analysis suggested shorter create timeout but did not fully lock exact values.

This gate must either:

- derive exact values already present in the corrected plan;
or
- make one minimal technical decision now.

Propose concrete timeout configuration for the shared client or per-call client behavior.

Requirements:

- no infinite timeout
- uploads meet >=60s connect/write recommendation
- create does not accidentally inherit an excessively short upload write timeout
- no separate client explosion unless needed.

Prefer simple deterministic configuration.

State exact seconds for:

- connect
- read
- write
- call timeout if used.

If a single client can safely use upload-capable timeout values for all calls, consider that simpler.

---

# 15. Request construction

Finalize exact Create request:

- POST
- fixed HTTPS create URL
- JSON
- partner header
- idempotency header
- body only:
  - partner
  - locale if non-null
- no external_reference
- no session ID
- no other metadata.

Finalize Upload request:

- POST exact returned upload URL
- `X-DWB-Handoff-Token`
- multipart:
  - `slot`
  - `file`
- filename may come from the generated file name unless contract requires another exact name
- media type `image/jpeg`
- no partner key
- no idempotency header unless partner contract explicitly requires it for upload (it does not)
- no extra fields.

Confirm whether upload filename itself matters to server contract.

If not, preserve local filename and test only extension/media type.

---

# 16. File streaming

Do not read JPEG files into memory.

Use OkHttp file-backed `RequestBody`.

Confirm:

- current file must exist
- file size can be checked locally but Block 5 already guarantees max size
- Block 7 may fail early on missing/unreadable file
- do not duplicate/rewrite file.

Decide whether missing file is:

- protocol/local client failure
or
- argument/precondition exception.

Keep error model coherent.

---

# 17. Testability without sockets

No `INTERNET` permission yet.

No real network test.

No MockWebServer unless specifically approved later.

Finalize the socket-free test seam.

Preferred approach:

- injectable `okhttp3.Call.Factory`
- fake `Call.Factory`
- fake `Call`
- synthetic `Response.Builder`

This must exercise actual request construction and parser logic.

Confirm how to simulate:

- success
- HTTP errors
- IO exception
- cancellation
- response-body close tracking.

Avoid production abstractions created solely to support tests if `Call.Factory` already suffices.

---

# 18. Logging/privacy

Block 7 should add no `HttpLoggingInterceptor`.

Decide whether any new logging is necessary at all.

Preferred:

- none in Block 7.

If minimal debug logging is retained, it must never contain:

- partner key
- handoff token
- upload URL
- checkout URL
- image file path
- request/response body.

Do not log secrets.

No telemetry.

---

# 19. Exact file scope for implementation

Enumerate every file required.

Expected likely scope:

### Modify
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `docs/IMPLEMENTATION_NOTES.md`

### Create
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildApiClient.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtos.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildErrorClassification.kt`
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt`
- exact test files

Determine whether DTO parsing helpers belong inside DTO/client files or require another file.

Do not create a file merely because 7A guessed one.

Keep file count minimal.

No manifest.
No ViewModel.
No BuildConfig wiring.

---

# 20. Exact tests

Plan exact JVM tests.

At minimum:

## Create request

- method POST
- exact URL/path
- JSON content type
- partner header present
- idempotency header present
- partner = sameview
- locale included if supplied
- locale omitted if null
- `external_reference` absent
- partner key absent from URL/body
- idempotency key passed unchanged.

## Create response parsing

- full 201 response
- checkout null
- extra fields ignored
- missing handoff_id
- missing handoff_token
- missing upload_url
- malformed JSON
- invalid upload URL.

## Upload one

- POST exact supplied upload URL
- handoff-token header
- no partner-key header
- multipart `slot=one`
- multipart file part named `file`
- JPEG media type
- correct local Reference file.

## Upload two

- same request shape
- `slot=two`
- Capture file
- parse ready response
- checkout URL retained exactly.

## Upload response parsing

- awaiting_files/null checkout
- ready/non-null checkout
- unknown extra fields
- malformed/missing required fields
- invalid checkout URL.

## HTTP classification

- 400
- 401
- 403
- 409
- 410
- 413
- 415
- 422
- 429
- 500
- unexpected status.

## Error body

- valid WordPress envelope
- malformed error JSON still preserves HTTP classification
- nested data.status parsed.

## Transport/cancellation

- IOException → network failure
- SocketTimeoutException → timeout
- cancellation propagates CancellationException
- call.cancel invoked
- response body closed.

## Privacy

- no secret in result/error text
- no partner key in URL
- no external_reference
- no upload token in URL
- no logging interceptor dependency.

No instrumentation tests.

No real socket.

---

# 21. Gradle/release verification plan

After implementation, require:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Also assess whether:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

is useful to confirm only expected OkHttp transitive dependencies.

No `connectedDebugAndroidTest`.

No physical-device test.

No real request.

---

# 22. Documentation impact

Implementation later should update only:

`docs/IMPLEMENTATION_NOTES.md`

with a Block 7 entry, unless implementation reveals a true contradiction in the now-synced docs.

Do not edit integration spec/implementation plan merely to say implementation completed.

---

# 23. Required final output

Return exactly:

## 1. Repository Baseline
- branch
- HEAD
- status

## 2. Source-of-Truth Verification
Confirm Block 7B doc sync is present and consistent.

## 3. Final Block-7 Boundary
- implement now
- defer

## 4. Client Interface
Exact method signatures conceptually.

## 5. DTO Model
Table:

| Type | Field | Kotlin type | Required | Validation |

## 6. Error Model
Exact sealed/result structure.

## 7. OkHttp Configuration
State exact:
- artifact/version
- retry
- redirects
- timeouts
- logging
- TLS/base URL rules.

## 8. Request Construction
Create + upload exact details.

## 9. Cancellation / Resource Handling
Exact behavior.

## 10. Testability Design
Exact fake seam, no sockets.

## 11. Files Proposed for Modification / Creation
Table:

| File | Modify/Create | Exact responsibility | Why |

Enumerate all files.

## 12. Files Explicitly Not Touched
Confirm:
- manifest
- ViewModel
- screen/UI
- renderer
- temp manager
- BuildConfig secret wiring
- Custom Tabs
- Block-8 state machine

## 13. Tests to Add
List exact test files and exact cases.

## 14. Verification Commands
Exact commands.

## 15. Documentation Impact
Exact docs.

## 16. Risks / Blockers
Only genuine Block-7 items.

## 17. Remaining Open Decisions
If none:

`None`

Otherwise list only real implementation blockers.

## 18. Gate Result

If implementation is now fully specified:

**BLOCK 7C SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If anything still blocks safe implementation:

**BLOCK 7C BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

Do not implement.

Block 7 is the first HTTP-client code in SameView, but **still not an internet-enabled app build**.

No manifest permission.
No real key.
No real request.
No retries.
No state machine.
No UI.

The goal is a small, testable, raw network-client layer that later blocks can safely orchestrate.
