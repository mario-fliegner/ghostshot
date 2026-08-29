# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 7: NETWORK CLIENT + DTOs — IMPLEMENTATION

## Authorization

Block 7C analysis/scope is approved.

Implement **exactly** the final Block-7 scope defined below.

This is the first HTTP-client implementation in SameView, but it is **still not an internet-enabled integration build**.

Do not begin Block 8.
Do not add `INTERNET`.
Do not use the real pilot API key.
Do not make any real network request.
Do not wire the client into ViewModel/UI/state machine.

---

# 1. Mandatory source-of-truth review

Before editing, read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Then inspect current:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`

Also inspect any current `org.json`, coroutine-cancellation and test-fake patterns relevant to this implementation.

Before changes report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 6 committed
- Block 7B doc-sync may still be uncommitted unless the user committed it
- no existing production package under `net/deinwackelbild/`

If repository state materially differs from the approved Block-7C scope, STOP and report before editing.

---

# 2. Security rule

A real pilot API key exists outside the repository.

It must not appear anywhere in this implementation.

Do not:

- search prompt archives for it;
- add any real `sv_test_...` or `sv_live_...` value;
- add it to code/tests/docs/Gradle/comments/logging/fixtures;
- add secret-loading behavior.

The concrete client receives a plain `partnerKey: String` constructor value.

Block 9 later owns:

- `local.properties` / env
- BuildConfig
- actual key injection

Block 7 must not know where the string came from.

---

# 3. Authorized implementation scope

Block 7 may implement only:

- OkHttp dependency
- DTOs
- API client interface
- concrete OkHttp client
- JSON parsing
- HTTP/transport/protocol error classification
- socket-free JVM tests
- documentation sync required by the implemented error model
- Block-7 implementation note

Block 7 must not implement:

- `INTERNET` permission
- ViewModel integration
- operation state machine
- `operationJob`
- retries/backoff
- idempotency-key generation
- temp cleanup orchestration
- BuildConfig secret wiring
- Custom Tabs
- CTA / consent
- UI
- order state/status.

---

# 4. Confirmed wire contract

Treat this as locked.

## Create

Method:

`POST`

URL:

`https://deinwackelbild.de/wp-json/dwb/v1/partner-handoffs`

Headers:

- `Content-Type: application/json`
- `X-DWB-Partner-Key`
- `Idempotency-Key`

Idempotency-Key:

- required
- 8–100 chars
- allowed `[A-Za-z0-9._-]`
- caller supplies it
- Block 7 does not generate it.

SameView V1 JSON body:

```json
{
  "partner": "sameview",
  "locale": "de-DE"
}
```

`external_reference` is deliberately omitted.

Success:

`201 Created`

Response fields:

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

Unknown additive fields are ignored.

## Upload

Use exact `upload_url` from Create.

Method:

`POST`

Header:

`X-DWB-Handoff-Token`

No `X-DWB-Partner-Key`.

Multipart:

- text field `slot`
- file field `file`
- media type `image/jpeg`

Mapping:

- Reference → `slot=one`
- Capture → `slot=two`

Exactly one JPEG per request.

## Upload one success

`200 OK`

- `status = awaiting_files`
- uploaded slots contains `one`
- `checkout_url = null`

## Upload two success

`200 OK`

- `status = ready`
- uploaded slots contains `one`, `two`
- non-null `checkout_url`

No polling endpoint exists in V1.

## Error body

WordPress REST envelope:

```json
{
  "code": "...",
  "message": "...",
  "data": {
    "status": 415
  }
}
```

Important HTTP statuses:

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

---

# 5. Exact production files

Expected production scope:

### Modify

1. `gradle/libs.versions.toml`
2. `app/build.gradle.kts`
3. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
4. `docs/IMPLEMENTATION_NOTES.md`

### Create

5. `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtos.kt`
6. `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildApiClient.kt`
7. `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt`
8. `app/src/test/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtoParsingTest.kt`
9. `app/src/test/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClientTest.kt`

If an additional file becomes genuinely necessary, STOP and report before expanding scope.

Do not create a separate error-classification file unless implementation proves the DTO file would become materially unclear.

---

# 6. Gradle dependency

Add exactly:

`com.squareup.okhttp3:okhttp:4.12.0`

Use the repository version catalog.

Expected:

- one version entry
- one library alias
- one `implementation(libs...)` line

Do not add:

- Retrofit
- Moshi
- Gson
- kotlinx.serialization
- MockWebServer
- logging-interceptor
- BOM.

Do not modify ProGuard unless `assembleRelease` proves it necessary.

---

# 7. Client interface

Implement:

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

Requirements:

- no OkHttp type in interface
- stateless across handoffs
- no retry
- no UUID generation
- no stored handoff state
- no business-state semantics.

---

# 8. Concrete client constructor

Implement the concrete client with a minimal constructor equivalent to:

```kotlin
class OkHttpDeinWackelbildApiClient(
    private val callFactory: Call.Factory,
    private val baseUrl: String = "https://deinwackelbild.de/wp-json/dwb/v1",
    private val partnerKey: String
) : DeinWackelbildApiClient
```

If constructor ordering must change for Kotlin default-argument legality/readability, preserve the same semantics.

`callFactory` must remain injectable for socket-free tests.

No generalized secret provider.

---

# 9. DTOs

Implement these types in the smallest sensible structure.

## `CreateHandoffRequest`

Fields:

- `partner: String = "sameview"`
- `locale: String?`

Rules:

- partner must remain `"sameview"`
- null locale omitted from JSON
- no `externalReference` field.

## `CreateHandoffResponse`

Fields:

- `handoffId: String`
- `handoffToken: String`
- `partner: String`
- `status: String`
- `expiresAt: String`
- `maxFileBytes: Long`
- `acceptedTypes: List<String>`
- `uploadedSlots: List<String>`
- `uploadUrl: String`
- `checkoutUrl: String?`

Required validation:

- required strings non-blank
- `maxFileBytes > 0`
- `acceptedTypes` non-empty
- `uploadedSlots` may be empty
- `uploadUrl` must be valid HTTPS
- `checkoutUrl` when present must be valid HTTPS.

Keep `expiresAt` as raw String.

Keep `status` as raw String.

## `UploadResponse`

Fields:

- `handoffId: String`
- `status: String`
- `uploadedSlots: List<String>`
- `checkoutUrl: String?`

Same URL rules.

## `DeinWackelbildSlot`

Enum:

- `ONE` → `"one"`
- `TWO` → `"two"`

Response uploaded-slot values stay raw strings.

## Error envelope

Implement:

- `DeinWackelbildErrorEnvelope`
- `DeinWackelbildErrorData`

Fields:

- code
- message
- nested status.

Do not surface message directly to users.

---

# 10. Result/error model

Implement one coherent raw-client result boundary.

Use:

```kotlin
sealed class DeinWackelbildResult<out T> {
    data class Success<T>(val value: T) : DeinWackelbildResult<T>()
    data class Failure(val error: DeinWackelbildApiError) : DeinWackelbildResult<Nothing>()
}
```

Implement:

```kotlin
data class DeinWackelbildApiError(
    val classification: DeinWackelbildErrorClassification,
    val httpStatus: Int? = null,
    val serverCode: String? = null
)
```

The raw error model must not contain:

- partner key
- handoff token
- upload URL
- checkout URL
- image file path
- response body.

---

# 11. Error classifications

Preserve current planned classifications and add the three Block-7C-required values.

Final classification set must include:

- `RETRYABLE_NETWORK`
- `RETRYABLE_SERVER`
- `RATE_LIMITED`
- `INVALID_REQUEST`
- `EXPIRED_HANDOFF`
- `INTEGRATION_UNAVAILABLE`
- `FILE_TOO_LARGE`
- `INVALID_IMAGE`
- `DIMENSION_MISMATCH`
- `PERMANENT_LOCAL`
- `INCOMPLETE_HANDOFF`
- `MALFORMED_RESPONSE`
- `UNEXPECTED_HTTP_STATUS`

Mapping:

- 400 → `INVALID_REQUEST`
- 401 → `INTEGRATION_UNAVAILABLE`
- 403 → `EXPIRED_HANDOFF`
- 409 → `INCOMPLETE_HANDOFF`
- 410 → `EXPIRED_HANDOFF`
- 413 → `FILE_TOO_LARGE`
- 415 → `INVALID_IMAGE`
- 422 → `DIMENSION_MISMATCH`
- 429 → `RATE_LIMITED`
- 5xx → `RETRYABLE_SERVER`
- other HTTP → `UNEXPECTED_HTTP_STATUS`
- IOException/connectivity → `RETRYABLE_NETWORK`
- SocketTimeoutException → `RETRYABLE_NETWORK`
- malformed/missing required response data → `MALFORMED_RESPONSE`
- invalid returned upload/checkout URL → `MALFORMED_RESPONSE`
- missing/unreadable local upload file → `PERMANENT_LOCAL`

Cancellation must not be converted to `Failure`.

---

# 12. Mandatory source-of-truth sync for error classifications

Because the implementation plan §14.1 is authoritative and currently does not contain all three new raw-client classifications, update:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

minimally so its error-classification model includes:

- `INCOMPLETE_HANDOFF`
- `MALFORMED_RESPONSE`
- `UNEXPECTED_HTTP_STATUS`

Also document their exact meanings.

This is a consistency update, not a redesign.

Do not rewrite unrelated plan sections.

Do not leave the plan intentionally behind the implementation.

---

# 13. JSON parsing

Use existing platform/library support:

- `org.json.JSONObject`
- `org.json.JSONArray`

No serialization dependency.

Behavior:

- malformed JSON → `MALFORMED_RESPONSE`
- missing required field → `MALFORMED_RESPONSE`
- blank required string → `MALFORMED_RESPONSE`
- unknown/additive fields ignored
- optional/null checkout accepted
- invalid HTTPS URL → `MALFORMED_RESPONSE`
- unknown status string accepted as raw string.

Error-body parsing:

- best-effort
- HTTP classification is primary
- malformed error JSON must **not** replace a known HTTP classification with `MALFORMED_RESPONSE`
- retain safe technical `serverCode` if parsed.

---

# 14. URL validation

## `upload_url`

Requirements:

- parse successfully
- scheme must be HTTPS
- use the exact returned string as request target
- do not reconstruct with path concatenation
- do not manually normalize it into another URL.

No strict host allowlist to `deinwackelbild.de`, because the confirmed contract may later use another HTTPS upload host/CDN and Block 7 should honor the exact returned upload URL.

## `checkout_url`

Requirements:

- syntax valid
- HTTPS
- preserve exact raw value
- preserve fragment
- do not fetch/open it in Block 7.

Do not round-trip through a transformation that could alter the fragment/token.

---

# 15. OkHttp configuration

Configure explicitly:

- `retryOnConnectionFailure(false)`
- `followRedirects(false)`
- `followSslRedirects(false)`
- `connectTimeout(60, SECONDS)`
- `writeTimeout(60, SECONDS)`
- `readTimeout(60, SECONDS)`
- `callTimeout(90, SECONDS)`

Rationale:

- Block 8 owns visible retries/accounting
- API contract does not require redirects
- upload recommendation requires >=60s connect/write
- one shared client is simpler and sufficient.

No infinite timeout.

No logging interceptor.

No new logging unless implementation truly requires it; preferred = none.

---

# 16. Create request construction

Build exactly:

`POST {baseUrl}/partner-handoffs`

Headers:

- `Content-Type: application/json`
- `X-DWB-Partner-Key`
- `Idempotency-Key`

Body:

- `partner`
- `locale` only when non-null

Must not contain:

- `external_reference`
- session ID
- comparison ID
- image metadata
- partner key in JSON
- idempotency key in JSON.

Validate idempotency-key format before creating the call:

- 8–100 chars
- `[A-Za-z0-9._-]+`

Invalid local key should return `PERMANENT_LOCAL` or the exact minimal local classification chosen consistently with the approved model, without making a request.

Do not silently mutate/sanitize the key.

---

# 17. Upload request construction

Build:

`POST <exact uploadUrl>`

Header:

- `X-DWB-Handoff-Token`

Do not send:

- partner key
- Idempotency-Key
- external reference
- session metadata.

Multipart:

- text part name `slot`
- text value `one` or `two`
- file part name `file`
- local generated filename
- media type `image/jpeg`
- body backed by File.

Requirements:

- file exists
- regular/readable file
- missing/unreadable file → `PERMANENT_LOCAL`
- do not load file into a ByteArray
- do not rewrite file.

Reference/Capture semantic mapping is enforced by the caller choosing the appropriate enum/file pair later; Block 7 itself only sends the supplied slot + supplied file.

---

# 18. Cancellation / response lifecycle

Implement the OkHttp coroutine bridge via:

- `Call.enqueue(...)`
- `suspendCancellableCoroutine`
- `invokeOnCancellation { call.cancel() }`

Requirements:

- coroutine cancellation calls `Call.cancel()`
- cancellation propagates as `CancellationException`
- cancellation is never converted to `DeinWackelbildResult.Failure`
- no hidden request continues after cancellation
- response body is closed exactly once on every response path
- success/error body consumption uses `.use {}` or equivalent.

Do not use blocking `execute()` as the production suspend bridge.

---

# 19. Socket-free test seam

Use:

`okhttp3.Call.Factory`

Tests must inject a hand-written fake `Call.Factory` / fake `Call`.

No:

- MockWebServer
- localhost socket
- external socket
- real endpoint.

Synthetic responses may be built with OkHttp response objects in memory.

The fake must support:

- synthetic success
- synthetic HTTP error
- IOException
- SocketTimeoutException
- cancellation
- response-body close tracking.

Do not add a new production transport abstraction solely for testing.

---

# 20. Exact tests

Create:

1. `DeinWackelbildDtoParsingTest.kt`
2. `OkHttpDeinWackelbildApiClientTest.kt`

## DTO/parser tests

At minimum:

- full valid create response
- create checkout null
- extra fields ignored
- missing `handoff_id`
- missing `handoff_token`
- missing `upload_url`
- malformed JSON
- invalid non-HTTPS upload URL
- valid upload-one response
- valid ready upload-two response
- checkout URL exact fragment preserved
- invalid checkout URL
- upload extra fields ignored
- valid error envelope
- missing error data still parses code/message.

## Create request tests

At minimum:

- method POST
- exact URL
- JSON content type
- partner header present
- idempotency header unchanged
- partner fixed to sameview
- locale included when non-null
- locale omitted when null
- no `external_reference`
- no session identifier
- no key in URL/body
- invalid idempotency key → no call.

## Upload request tests

At minimum:

- exact supplied upload URL
- POST
- handoff-token header present
- partner-key header absent
- idempotency header absent
- multipart `slot=one`
- multipart `slot=two`
- file part name `file`
- JPEG media type
- file content streamed from File
- no request for missing/unreadable file.

## HTTP/error tests

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
- unexpected status

Assert exact classifications.

## Transport/cancellation tests

- IOException → `RETRYABLE_NETWORK`
- SocketTimeoutException → `RETRYABLE_NETWORK`
- coroutine cancellation propagates
- `Call.cancel()` invoked
- response body closes on success
- response body closes on HTTP error.

## Privacy/security tests

Assert:

- partner key absent from URL
- partner key absent from result/error object
- handoff token absent from URL
- external reference absent
- no logging-interceptor dependency added.

No instrumentation tests.

---

# 21. Documentation — IMPLEMENTATION_NOTES

Append a concise Block-7 implementation entry to:

`docs/IMPLEMENTATION_NOTES.md`

Record:

- OkHttp 4.12.0 added
- raw client/DTO/error layer added
- socket-free tests
- no manifest/INTERNET yet
- no real key
- no real request
- no retries/state machine
- no UI
- error-classification plan sync performed.

Do not rewrite historical entries.

---

# 22. Files explicitly forbidden

Do not modify:

- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro` unless `assembleRelease` fails specifically because a real keep rule is required; if so STOP before changing it
- `WackelbildViewModel.kt`
- `WackelbildScreen.kt`
- `WackelbildPrintRenderer.kt`
- `WackelbildTempFileManager.kt`
- camera/session files
- strings
- MainActivity/navigation
- BuildConfig wiring
- `local.properties`
- Custom Tabs
- Block-8 state machine files
- any prompt archive.

No unrelated refactor.
No formatting cleanup.
No rename.

---

# 23. Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew :app:dependencies --configuration debugRuntimeClasspath
git diff --check
git status --short
```

For dependency inspection confirm only expected networking additions appeared, primarily:

- OkHttp
- Okio
- expected Kotlin/runtime transitives.

No instrumentation test.
No physical-device test.
No real network request.

Do not suppress failures.

If `assembleRelease` fails because of new dependency/R8 issues, investigate and report before expanding file scope.

---

# 24. Required final report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List every changed file.

Confirm exact scope.

## 3. Dependency Change

Report:

- OkHttp version
- Gradle files
- transitive dependency findings
- no logging interceptor / Retrofit / MockWebServer.

## 4. API Client Implementation

Describe:

- interface
- concrete client
- constructor injection
- create request
- upload request
- slot behavior.

## 5. DTO / Parsing

Describe exact model and validation.

## 6. Error Model

List full final classification set and mappings.

Confirm plan doc updated for the three additive values.

## 7. Cancellation / Resource Safety

Explain:

- `enqueue`
- coroutine cancellation
- `call.cancel()`
- response closing.

## 8. Security / Privacy

Confirm:

- no real key
- no key in URL/body/errors
- no external_reference
- no session metadata
- no logging interceptor
- no network call.

## 9. Tests

Report exact test counts/results for new test classes where available.

## 10. Verification

Report exact result for each command:

- testDebugUnitTest
- assembleDebug
- assembleRelease
- dependency report
- diff check
- status.

## 11. Block Boundary Confirmation

Explicitly confirm no:

- manifest/INTERNET
- ViewModel
- retries/state machine
- BuildConfig key wiring
- Custom Tabs/UI.

## 12. Diff Scope

Confirm no unauthorized edits.

## 13. Remaining Work

State only later blocks:

- Block 8 orchestration/retries/real operation lifecycle
- Block 9 key injection
- Block 10 manifest/INTERNET integration activation
- later UI/Custom Tabs.

## 14. Gate Result

If successful:

**BLOCK 7 IMPLEMENTED — READY FOR REVIEW**

If blocked:

**BLOCK 7 BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final instruction

Implement exactly the raw networking layer.

No secrets.
No manifest.
No real network.
No retry orchestration.
No UI.

This block must remain isolated, deterministic and fully JVM-testable.
