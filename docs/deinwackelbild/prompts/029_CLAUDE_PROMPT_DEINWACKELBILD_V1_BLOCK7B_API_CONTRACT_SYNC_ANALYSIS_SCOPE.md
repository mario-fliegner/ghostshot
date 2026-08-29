# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 7B: API CONTRACT SYNC — ANALYSIS & SCOPE CONFIRMATION ONLY

## Status

DeinWackelbild Blocks 1–6 are implemented and committed.

Block 7A correctly stopped because three wire-level API details were not proven by repository documentation. Those external gaps are now resolved by the actual DeinWackelbild/Lentiprint pilot specification received by email.

This gate exists only to synchronize the repository source-of-truth documentation with that confirmed external contract **before any Block-7 networking code is implemented**.

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files.
Do not implement Kotlin.
Do not add OkHttp.
Do not add Gradle dependencies.
Do not modify the manifest.
Do not add API keys.
Do not make any network call.
Do not contact DeinWackelbild.
Do not begin Block 7 implementation.

---

# 1. Security rule — do not copy the real pilot key

The actual pilot email contained a real `sv_test_...` API key.

That key is intentionally **not included in this prompt**.

Do not search prompt archives for it.
Do not copy any real key into:
- MD files
- Kotlin
- tests
- Gradle
- BuildConfig
- comments
- logs
- fixtures.

Documentation may only use placeholders such as:

`sv_test_REPLACE_ME`
or
`sv_live_REPLACE_ME`

Block 9 will handle secret injection separately.

---

# 2. Confirmed external API contract

Treat the following as externally confirmed pilot-contract facts.

## 2.1 Base URL

Production base URL:

`https://deinwackelbild.de/wp-json/dwb/v1`

No separate polling/status endpoint is required for V1.

## 2.2 Create handoff

Request:

`POST /wp-json/dwb/v1/partner-handoffs`

Headers:

- `Content-Type: application/json`
- `X-DWB-Partner-Key: <partner-key>`
- `Idempotency-Key: <stable-operation-key>`

`Idempotency-Key` rules:

- required
- length 8–100
- allowed characters: letters, digits, `.`, `_`, `-`
- retries for the same user operation reuse the same key.

JSON body contract supported by API:

```json
{
  "partner": "sameview",
  "external_reference": "optional-value",
  "locale": "de-DE"
}
```

SameView V1 deliberately omits `external_reference` according to the existing data-minimization/privacy decision.

`locale` is optional at the wire level.

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

Expected initial status:

`awaiting_files`

Initial `checkout_url`:

`null`

A repeated create request with the same valid `Idempotency-Key` returns the same still-valid handoff.

## 2.3 Upload image

Use the exact `upload_url` returned by the Create response.

Request:

`POST <upload_url>`

Header:

`X-DWB-Handoff-Token: <handoff_token>`

Do **not** send the partner key on upload requests.

Content type:

`multipart/form-data`

Multipart fields are confirmed:

- text field:
  - name: `slot`
  - values: `one` or `two`
- file field:
  - name: `file`
  - content: JPEG file
  - media type: `image/jpeg`

Exactly one image is uploaded per request.

## 2.4 Upload slot one

Request fields:

- `slot=one`
- `file=<first JPEG according to the approved SameView semantic mapping>`

Success:

`200 OK`

Response fields:

- `handoff_id`
- `status = "awaiting_files"`
- `uploaded_slots = ["one"]`
- `checkout_url = null`

Uploading an already fully stored slot is idempotent.

A retry after connection interruption may resend the same handoff/slot.

## 2.5 Upload slot two

Request fields:

- `slot=two`
- `file=<second JPEG according to the approved SameView semantic mapping>`

Success:

`200 OK`

Response fields:

- `handoff_id`
- `status = "ready"`
- `uploaded_slots = ["one", "two"]`
- `checkout_url = "<returned exact URL>"`

This resolves the previous Block-7A ambiguity:

**There is no separate V1 polling/status call required.**

The second successful upload directly returns:

- `status=ready`
- non-null `checkout_url`

when the handoff is ready.

## 2.6 Checkout behavior

SameView later opens the exact API-returned `checkout_url`.

The URL must:

- not be assembled manually;
- not be modified;
- not be normalized into another URL;
- not be polled;
- not be fetched by the raw API client merely to inspect it.

Custom Tab behavior belongs to a later block.

There is no V1 app-link return flow.
There is no V1 order-status callback to SameView.

## 2.7 File requirements

Each uploaded file:

- JPEG only
- `.jpg` or `.jpeg`
- `Content-Type: image/jpeg`
- max 20 MiB / 20,971,520 bytes
- max 16,000 px per side
- max 80 MP
- both files must have identical pixel dimensions
- identical orientation/aspect ratio
- must be fully decodable JPEG
- not renamed HEIC/PNG
- unneeded EXIF/GPS/camera metadata should be stripped before upload

These constraints should already be reflected in Block 5; do not redesign Block 5 here.

## 2.8 Recommended client behavior

Externally confirmed recommendations:

- connect/write timeout for image upload at least 60s
- Create and Upload retry on network failures up to three attempts with increasing delay
- Create retries use the same `Idempotency-Key`
- handoff state is temporary for the current operation only
- open Custom Tab only when:
  - `status=ready`
  - `checkout_url != null`
- HTTP 410 means start a completely new handoff with a new `Idempotency-Key`

Retry orchestration remains Block 8, not Block 7.

## 2.9 Error body

WordPress REST error format is confirmed:

```json
{
  "code": "dwb_handoff_image_invalid",
  "message": "SameView darf ausschließlich gültige JPG-Dateien übertragen.",
  "data": {
    "status": 415
  }
}
```

Therefore the error envelope may contain:

- `code`
- `message`
- nested `data.status`

Do not surface raw server error text directly to users unless a later UX spec explicitly authorizes it.

## 2.10 Important HTTP statuses

Confirmed:

- `400` — invalid parameter/slot; do not blindly retry unchanged
- `401` — invalid SameView API key
- `403` — invalid handoff token
- `409` — both files not yet complete
- `410` — handoff expired
- `413` — file >20 MiB
- `415` — invalid JPEG
- `422` — invalid/too-large/mismatched dimensions
- `429` — temporary rate limit
- `5xx` — temporary server error

Business retry/state-machine behavior remains Block 8.

---

# 3. Repository sources to inspect

Read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Also inspect:

- any other current non-prompt DeinWackelbild API contract document under `docs/deinwackelbild/`

Do not treat prompt archives as the primary source of truth.

Report repository baseline:

- branch
- HEAD
- `git status --short`

Block 6 should be committed.

---

# 4. Required reconciliation analysis

Compare the confirmed external contract above against the current repository docs.

At minimum inspect whether the repository currently still says any of the following are unknown:

- upload multipart field name
- upload handoff-token authentication header
- whether second upload returns `checkout_url`
- whether a separate polling endpoint may be required
- Idempotency-Key transmission method
- Create response wire keys
- Upload response wire keys
- WordPress error-body structure.

Identify every stale statement.

Also inspect whether any current doc incorrectly says or implies:

- `external_reference` is required
- partner key is sent on uploads
- upload body uses an unspecified file field
- checkout URL must be polled
- API key may be stored in documentation
- SameView receives order status.

---

# 5. Privacy decision — `external_reference`

The API supports:

`external_reference`

but SameView V1 intentionally omits it.

Confirm this remains consistent with:

- data minimization
- no unnecessary metadata transfer
- current integration spec.

Do not reverse this decision merely because the external API supports the field.

Documentation should distinguish:

- API supports optional field
- SameView V1 does not send it.

---

# 6. Slot semantic mapping

Re-read the current DeinWackelbild integration spec and Block-5 output contract.

Determine the exact approved mapping:

- `slot=one` → which SameView image?
- `slot=two` → which SameView image?

Do not infer from email example filenames alone if the repository already locked a semantic mapping.

If the current repository has not yet explicitly locked this mapping, identify it as a remaining decision before Block 7 implementation.

The API contract itself confirms only the slot names, not SameView semantic meaning.

Do not silently choose.

---

# 7. Exact documentation scope

The expected documentation targets are likely:

1. `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
2. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

But do not assume blindly.

Determine exactly which authoritative docs contain stale API-contract uncertainty and must be updated.

Do not modify:

- historical prompt archives
- unrelated project docs
- source code
- tests
- Gradle
- manifest.

`docs/IMPLEMENTATION_NOTES.md` should not receive a Block-7 implementation-complete entry because no implementation happens in this gate. Only update it if it currently contains a factual stale API-contract statement that would remain contradictory.

---

# 8. Scope of the future documentation edit

The documentation sync must be surgical.

It may:

- replace "unknown/unconfirmed" API details with confirmed facts;
- add exact request/response field names;
- add exact auth header names;
- state no V1 polling endpoint is needed;
- add confirmed WordPress error envelope;
- retain the existing block sequencing;
- preserve data-minimization decisions;
- preserve security restrictions.

It must not:

- add the real test API key;
- change Block-5 rendering behavior;
- change Block-6 cleanup behavior;
- move BuildConfig secret injection earlier than Block 9;
- move INTERNET permission earlier than Block 10;
- move retry state machine earlier than Block 8;
- implement code.

---

# 9. Security review

Explicitly confirm the future doc changes use placeholders only.

Search any proposed patch content for:

- `sv_test_`
- actual key fragments
- accidental copied credentials.

If the repository already contains a real key anywhere, report that immediately as a security issue and STOP.

Do not reproduce the key in the report.

---

# 10. Block-7A blocker resolution verdict

At the end of the analysis, state whether the previous three Block-7A blockers are now fully resolved:

1. multipart file field name
2. upload auth mechanism
3. ready-detection / polling ambiguity

Expected based on the confirmed contract:

- multipart file field = `file`
- slot field = `slot`
- upload auth = `X-DWB-Handoff-Token`
- second upload returns `status=ready` + `checkout_url`
- no V1 polling endpoint

But verify current repository consistency before declaring Block 7 implementable.

---

# 11. Required output

Return exactly:

## 1. Repository Baseline
- branch
- HEAD
- working-tree state

## 2. Current Documentation Findings
List every stale/ambiguous API-contract statement found.

## 3. Confirmed External Contract
Summarize the exact wire facts that replace those ambiguities.

## 4. External Reference Decision
Confirm API support vs SameView V1 omission.

## 5. Slot Mapping
State the current repository-approved mapping, or explicitly identify it as unresolved.

## 6. Files Proposed for Modification
Table:

| File | Modify/Create | Exact documentation change | Why |

No vague file groups.

## 7. Files Explicitly Not Touched
Confirm:
- code
- tests
- Gradle
- manifest
- prompt archives
- secret wiring.

## 8. Security Check
Confirm no real API key will be copied.

## 9. Block-7A Blocker Resolution
For each former blocker:
- resolved / unresolved
- exact confirmed value.

## 10. Remaining Open Decisions
List only genuinely unresolved items that could still block Block 7 implementation.

If none, say:

`None`

## 11. Scope Confirmation

If documentation can now be synchronized safely and no product decision remains:

**BLOCK 7B DOC-SYNC SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If a real decision remains, end:

**BLOCK 7B BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final rule

Analysis only.

Do not modify files.

The objective is to make the repository source-of-truth catch up with the now-confirmed pilot wire contract without leaking the real test API key or changing implementation sequencing.
