# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 7A: NETWORK CLIENT + DTOs — ANALYSIS & SCOPE CONFIRMATION ONLY

## Status

DeinWackelbild Blocks 1–6 are implemented and committed.

The corrected implementation plan defines Block 7 as:

> Network client + DTOs

with the intended objective:

- add OkHttp as the first HTTP dependency;
- introduce `DeinWackelbildApiClient`;
- introduce `OkHttpDeinWackelbildApiClient`;
- implement request/response DTOs and parsing;
- implement transport/API error classification;
- keep the block isolated from UI, ViewModel, state-machine, API-key BuildConfig wiring, manifest permission and Custom Tabs.

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files.
Do not add dependencies yet.
Do not make network requests.
Do not contact the pilot endpoint.
Do not implement Block 8+ behavior.

The purpose of this gate is to verify the exact current API contract and determine the smallest safe Block-7 implementation scope before the first networking code enters SameView.

---

# 1. Mandatory source-of-truth review

Read fully before analysis:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Also read any repository-local DeinWackelbild API/pilot document if one exists under:

- `docs/deinwackelbild/`
- or another clearly current docs location.

Do not treat prompt archives as authoritative unless they document an accepted correction that has not yet been reflected in the current plan.

Authority order:

1. integration spec;
2. master project instruction;
3. current corrected implementation plan;
4. current code.

If current plan and spec disagree, report the conflict and stop rather than silently choosing one.

---

# 2. Repository baseline

Report:

- branch
- HEAD
- `git status --short`

Verify Block 6 is committed.

Inspect:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`

Confirm:

- no HTTP client dependency currently exists;
- `INTERNET` permission is still absent;
- no `net/deinwackelbild/` production package already exists, unless created after the plan.

Preserve unrelated prompt archives/untracked files.

---

# 3. Reconfirm Block-7 boundary after Block-6 resequencing

Block 6 was intentionally narrowed to real cleanup infrastructure only.

Therefore Block 7 must **not** reintroduce premature operation lifecycle code.

Block 7 owns only:

- network abstraction;
- concrete OkHttp request construction/parsing;
- DTOs;
- response/error classification required at the raw API-client layer;
- tests of those pieces.

Block 7 does **not** own:

- ViewModel wiring;
- `operationJob`;
- `operationDir`;
- `WackelbildOperationPhase`;
- handoff state machine;
- retries/backoff orchestration;
- idempotency-key lifecycle;
- rendering invocation;
- temp cleanup orchestration;
- CTA/UI;
- partner-key BuildConfig source;
- `INTERNET` permission;
- Custom Tabs.

Those belong to later blocks according to the corrected sequencing.

Explicitly check that the current implementation plan still reflects this after Block 6's documentation correction.

---

# 4. Exact external API contract — derive, do not guess

Extract the exact contract from the current integration spec/plan.

For every endpoint needed by V1, report:

- HTTP method;
- exact path or URL construction rule;
- request headers;
- request content type;
- request body fields;
- multipart part names;
- filename expectations if specified;
- success status codes;
- response body fields;
- nullable/optional response fields;
- documented error status codes;
- whether response bodies may be empty;
- whether `checkout_url` is returned immediately or only after a later request;
- whether polling/status retrieval exists in V1;
- whether any endpoint requires an idempotency key;
- whether partner key is sent on all requests or only a specific request.

Do not invent endpoint names or JSON keys.

If any part of the installed/pilot API contract remains external/unconfirmed, identify it explicitly.

Block 7 must not encode guessed wire names into production code.

---

# 5. Partner-key handling boundary

The current accepted contract names:

`X-DWB-Partner-Key`

Reconfirm this from the current plan/spec.

Block 9 owns the mechanism:

`local.properties/env -> BuildConfig`

Therefore Block 7 must not:

- add `BuildConfig.DEINWACKELBILD_PARTNER_KEY`;
- read `local.properties`;
- read environment variables;
- store a real key;
- add a placeholder secret;
- log the key.

Determine the clean Block-7 API boundary.

Examples to evaluate:

- constructor-injected `partnerKey: String`;
- request-level credential parameter;
- a tiny credential-provider interface.

Choose the smallest structure that lets Block 9 later supply the key without changing the HTTP protocol implementation.

Do not build a generalized secret-management framework.

Also verify the accepted rule that the partner key is never placed in:

- URL/query parameters;
- request JSON/body unless the partner contract explicitly says so;
- logs.

---

# 6. Idempotency boundary

The corrected implementation plan assigns idempotency-key lifecycle to Block 8.

Therefore distinguish:

### Block 7 responsibility

If the raw API endpoint supports/requires an idempotency header, the client may expose a parameter that lets a caller supply that value and place it in the correct header.

### Block 8 responsibility

- generating the key;
- retaining it across retries;
- deciding when to regenerate;
- retry orchestration.

Confirm the exact header name from the authoritative contract.

Do not generate UUIDs inside the OkHttp client if lifecycle belongs to Block 8.

---

# 7. Retry/backoff boundary

The accepted plan has a three-attempt policy with increasing delay, but Block 8 owns retry/backoff orchestration.

Verify whether OkHttp itself would introduce automatic retries that could undermine the state machine's attempt accounting.

Inspect/decide:

- `retryOnConnectionFailure`;
- redirects;
- follow-up requests;
- call timeout vs connect/read/write timeout.

The Block-7 client must have deterministic transport behavior that Block 8 can reason about.

If automatic OkHttp retry should be disabled, say so and explain.

Do not implement custom retry loops in Block 7.

---

# 8. Timeout contract

Re-read the plan/spec for the fixed requirements:

- upload timeout >= 60 seconds;
- any exact connect/read/write/call timeout values already locked.

Determine which timeout(s) matter for:

- create request;
- image upload;
- checkout retrieval/status request if one exists.

Do not invent exact timeout values if only a lower bound is authoritative.

If the plan deliberately leaves exact timeout beyond `>=60s` tunable, propose one concrete technical value only if this gate is the designated decision point; otherwise flag it for confirmation.

No infinite timeout.

---

# 9. DTO design

Determine the minimal DTO set from the actual wire contract.

For each DTO, report:

- Kotlin type name;
- fields;
- nullable vs required;
- raw API field name;
- validation required after parsing.

Do not add fields for:

- price;
- product catalog;
- order status not used by V1;
- external reference if the spec says it is omitted;
- session ID;
- title/location/date metadata unless actually required by the partner API.

The image transfer must send only the generated image pair plus contract-required technical fields.

No SameView `metadata.json`.

---

# 10. JSON parsing

The repository currently uses `org.json` rather than a serialization framework.

The accepted plan says no Retrofit.

Analyze whether Block 7 should use:

- OkHttp + `org.json.JSONObject`

with no Moshi/Gson/kotlinx.serialization dependency.

Prefer the smallest dependency surface.

Do not add another JSON library unless the current API contract cannot be safely handled with the existing approach.

Parsing requirements:

- malformed JSON → typed API-client failure, not crash;
- missing required fields → typed failure;
- unexpected extra fields → ignore unless dangerous;
- invalid checkout URL → classify safely; do not launch it in this block.

No UI error strings here.

---

# 11. Request-body and file-streaming behavior

The Block-5 renderer produces JPEG files.

Analyze the upload implementation:

- use OkHttp `RequestBody` backed by `File`;
- avoid loading JPEGs into `ByteArray`;
- preserve cancellation via `Call.cancel()` / coroutine cancellation as applicable;
- close response bodies reliably;
- do not duplicate files.

Determine whether a small coroutine bridge is needed.

If using synchronous `Call.execute()` inside `withContext(Dispatchers.IO)`, compare it with an `enqueue()` suspend bridge.

Choose the smallest approach that:

- is cancellation-aware enough for Block 8;
- does not leak sockets/response bodies;
- is straightforward to fake through the interface in JVM tests.

Do not add a general networking coroutine framework.

---

# 12. Network client interface

Design the exact minimal `DeinWackelbildApiClient` interface.

It must mirror the raw partner operations, not the higher-level business state machine.

For each method define:

- parameters;
- return DTO/result;
- cancellation behavior;
- whether it throws or returns typed failures.

Avoid mixing two error models.

Choose one consistent boundary, e.g.:

- success DTO + typed `DeinWackelbildApiException`;
or
- sealed `ApiResult`.

Explain why it best supports Block 8.

The interface must be fakeable without sockets.

Do not expose OkHttp types from the interface.

---

# 13. Error-classification model

Derive exact classifications needed at the raw client layer from spec §14.6 / implementation plan.

Separate:

### Transport-level
Examples only if supported:
- DNS/connectivity
- timeout
- connection reset
- cancellation

### HTTP-level
Map only documented status families/codes.

### Protocol-level
- malformed body
- missing required JSON field
- invalid URL
- unexpected response state

### Integration-local
Partner key missing belongs to Block 9 / higher-level integration availability, not necessarily the raw HTTP parser.

Determine exact sealed enum/class names and which layer owns user-visible mapping.

Block 7 must **not** produce UI strings.

Block 8/11 will map raw failures to operation state/user-visible errors.

Do not over-classify speculative cases.

---

# 14. HTTP logging / privacy

The corrected plan explicitly forbids `HttpLoggingInterceptor`.

Verify that Block 7 must add:

- OkHttp core only;
- no logging-interceptor dependency.

Production networking must never log:

- partner key;
- request headers containing credentials;
- filenames if avoidable;
- image bytes;
- checkout URL if it contains capability/session tokens;
- response bodies containing sensitive identifiers.

If minimal debug logging already exists as an accepted plan concept, define exactly what is allowed (e.g. request class/status code only), but prefer no new logging in Block 7 unless needed.

No analytics.
No telemetry.
No tracking.

---

# 15. URL / TLS safety

Determine the authoritative API base URL.

Verify:

- HTTPS only;
- no cleartext;
- no dynamic arbitrary host supplied by server for API calls;
- checkout URL is data returned for later Custom Tab launch, not recursively fetched by the raw client unless contract says so.

Block 7 does not add `networkSecurityConfig`.

If the API client accepts an injectable base URL for tests, constrain the production default separately.

Do not build certificate pinning unless the spec requires it.

---

# 16. No real network in Block 7

This is mandatory.

The current manifest intentionally still lacks:

`android.permission.INTERNET`

Block 7 must not modify the manifest.

No test may contact:

- DeinWackelbild;
- localhost via a real socket if the approved plan explicitly requires socket-free JVM fakes;
- any external host.

The plan says Block 7 is testable with synthetic responses/fakes.

Re-evaluate how the concrete OkHttp request/parser can be unit tested **without MockWebServer and without real sockets**.

Possible patterns:

- injectable OkHttp `Call.Factory`;
- fake `Interceptor` returning synthetic `Response`;
- small transport abstraction.

Choose the minimal testable seam.

Do not add MockWebServer unless the authoritative plan has been corrected to allow it.

---

# 17. OkHttp dependency/version decision

Inspect:

- current version-catalog conventions;
- current minSdk;
- current Kotlin/AGP toolchain.

Determine the exact OkHttp artifact and version to add.

Requirements:

- stable release;
- compatible with minSdk 29;
- no unnecessary BOM/module set;
- core functionality only.

Do not add:

- Retrofit;
- MockWebServer unless explicitly approved;
- logging-interceptor;
- additional converter libraries.

If the implementation plan already locks a version, use it.

If not, identify the chosen version as a Block-7 technical dependency decision and justify it from compatibility/repository conventions.

---

# 18. Gradle and release impact

Even without `INTERNET` permission, adding OkHttp changes the dependency graph and release artifact.

Analyze:

- whether ProGuard/R8 rules are needed;
- whether OkHttp provides consumer rules;
- whether `proguard-rules.pro` needs no change;
- APK/AAB size impact qualitatively;
- whether any transitive manifest entries appear;
- whether `assembleRelease` should be required in Block 7 even though the old plan listed only `assembleDebug`.

Given release safety, prefer verifying both debug and release builds after dependency addition.

Do not modify ProGuard unless evidence proves necessary.

---

# 19. Tests to plan

Plan exact test files and cases.

At minimum cover:

## DTO parsing
- each documented success response;
- optional/nullable fields;
- missing required field;
- malformed JSON;
- unexpected extra field.

## Request construction
- exact HTTP method/path;
- exact content type;
- exact multipart field names;
- image file body is streamed, not converted to giant `ByteArray`;
- partner header placed where contract requires;
- idempotency header passed through unchanged where contract requires;
- no `external_reference`;
- no session metadata;
- no credential in URL.

## Error classification
- documented 4xx mappings;
- documented 5xx mapping;
- timeout;
- transport failure;
- cancellation;
- malformed protocol response.

## Resource/cancellation safety
- response body closed;
- cancelled coroutine/call does not become generic error if cancellation should propagate;
- no hidden retry loop.

## Security
- request URL does not contain partner key;
- error objects/messages do not include partner key;
- no logging interceptor.

No real socket/server required.

---

# 20. Documentation impact

Determine whether Block 7 implementation should update only:

- `docs/IMPLEMENTATION_NOTES.md`

or whether a current source-of-truth section needs correction based on actual API-client findings.

Do not edit integration spec/plan simply to record implementation completion if no contract changed.

If an external API detail remains unverified and blocks safe wire implementation, do not silently guess. Mark Block 7 blocked.

---

# 21. Exact future implementation file scope

Produce the exact file list for Block 7.

Expected categories:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- new files under:
  `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/`
- exact unit test files under:
  `app/src/test/java/com/isardomains/sameview/net/deinwackelbild/`
- `docs/IMPLEMENTATION_NOTES.md` if consistent with project ledger practice.

Do not say `net/deinwackelbild/*.kt`; enumerate every proposed file.

No manifest.
No ViewModel.
No UI.
No Block-8 state machine.

---

# 22. Verification plan after implementation

At minimum plan:

```text
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Assess whether additional commands are warranted:

- dependency report;
- lint;
- `bundleRelease`.

No instrumentation test should be necessary for a pure networking/DTO block with no manifest/UI integration unless repository evidence says otherwise.

No physical-device test and no real network call should occur in Block 7.

---

# 23. Required response

Return exactly:

## 1. Repository Baseline
- branch
- HEAD
- working-tree state

## 2. Current Network Baseline
- dependencies
- manifest
- existing network packages/precedents

## 3. Block-7 Boundary
List:
- implement in Block 7
- explicitly deferred to Block 8/9/10/11

## 4. Authoritative API Contract
Table:

| Operation | Method/Path | Headers | Body | Success response | Error statuses |

No guessed values.

## 5. Unverified External Contract Items
List any remaining pilot/API facts that cannot be proven from the repository specs.

If any are required to encode the wire protocol safely, mark them blocking.

## 6. Proposed Client Interface
Exact method signatures conceptually, no implementation code.

## 7. DTO Model
Table:

| DTO | Field | Wire key | Required/Optional | Validation |

## 8. Error Model
Exact classifications and ownership boundary.

## 9. Partner Key / Idempotency / Retry Boundaries
State exactly what Block 7 does and does not do.

## 10. OkHttp Configuration
- version/artifact
- timeouts
- retry setting
- redirects if relevant
- logging
- base URL/TLS policy

## 11. Testability Design
Explain how the concrete client is tested without real network sockets.

## 12. Files Proposed for Modification / Creation
Table:

| File | Modify/Create | Exact responsibility | Why |

Enumerate every file.

## 13. Files Explicitly Not Touched
Confirm:
- manifest
- ViewModel
- screen/UI
- renderer
- temp manager
- BuildConfig secret wiring
- Custom Tabs
- state machine

## 14. Tests to Add
Exact test files and cases.

## 15. Verification Commands
Exact commands after implementation.

## 16. Documentation Impact
Exact docs, if any.

## 17. Risks / Blockers
Only real Block-7 risks.

## 18. Gate Result

If safe and fully specified:

**BLOCK 7A SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If required API details are still unknown:

**BLOCK 7A BLOCKED — EXTERNAL API CONTRACT REQUIRED**

Then STOP.

---

# Final Rule

Analysis only.

This is SameView's first HTTP-client block. Do not let the existence of an approved network exception justify premature network reachability.

Block 7 must remain:

**dependency + raw API abstraction + DTO/parsing/error classification + socket-free tests**

and nothing more.

No manifest permission.
No real partner key.
No real request.
No upload flow.
No state machine.
No UI.
