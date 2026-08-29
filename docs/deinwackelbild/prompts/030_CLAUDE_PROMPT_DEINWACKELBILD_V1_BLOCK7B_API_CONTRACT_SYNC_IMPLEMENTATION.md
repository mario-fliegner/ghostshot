# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 7B: API CONTRACT DOC-SYNC — IMPLEMENTATION

## Authorization

The preceding Block 7B analysis/scope gate is approved.

Implement **exactly** the approved documentation synchronization.

The final product/technical decision is now also locked:

- SameView **Reference** image → DeinWackelbild API slot `one`
- SameView **Capture** image → DeinWackelbild API slot `two`

Do not ask again.

This remains a **documentation-only gate**. Do not begin Block 7 network implementation.

---

# 1. Repository baseline and source-of-truth

Before editing, verify:

- branch
- HEAD
- `git status --short`

Expected baseline from the approved analysis:

- branch `main`
- HEAD `edac966` unless the user has committed/amended something since analysis
- Block 6 committed
- only expected prompt-archive files may be untracked

If there is material repository drift affecting the approved files or API contract, STOP and report it.

Read before editing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

The confirmed external contract supplied in the preceding Block 7B analysis is authoritative for this doc sync.

---

# 2. Security — absolute restriction

A real DeinWackelbild pilot API key exists outside the repository.

It must **not** be copied into this gate.

Do not:

- search prompt archives for the real key;
- add any real `sv_test_...` or `sv_live_...` credential;
- add credentials to MD, Kotlin, Gradle, properties, comments, fixtures, logs, or examples.

Only header names and generic placeholders may be documented.

If you discover a real credential already committed/tracked in the repository, STOP immediately and report the security issue **without reproducing the credential**.

---

# 3. Authorized files

Only these two files are authorized for modification:

1. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
2. `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Do **not** modify `docs/IMPLEMENTATION_NOTES.md`: the approved analysis found no stale statement there and this is not an implementation-complete gate.

Do not modify any other file.

---

# 4. Required change — implementation plan §13.1

In `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §13.1, synchronize the API client interface documentation with the confirmed dynamic upload target.

The documented `uploadImage(...)` interface must include the exact API-returned `uploadUrl: String` input in addition to the handoff token, slot, and file.

The documentation must make clear:

- Create uses the fixed create endpoint.
- Upload uses the exact `upload_url` returned by Create.
- SameView must not reconstruct the upload URL.
- Upload authentication uses `X-DWB-Handoff-Token`.
- The partner key is not sent on upload requests.

Do not implement code.

---

# 5. Required change — implementation plan §14.1 DTO/wire contract

Update the pseudocode/documented DTO contract so it reflects the now-confirmed response structures rather than the earlier minimal/incomplete sketches.

## Create response

Document the confirmed wire fields:

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

Initial expected semantics:

- `status = awaiting_files`
- `checkout_url = null`

Unknown future JSON fields must remain ignorable for V1 compatibility.

## Upload response

Document:

- `handoff_id`
- `status`
- `uploaded_slots`
- `checkout_url`

Slot-one success:

- `status = awaiting_files`
- `uploaded_slots` contains `one`
- `checkout_url = null`

Slot-two ready success:

- `status = ready`
- `uploaded_slots` contains `one` and `two`
- `checkout_url` is non-null

## Error envelope

Document the confirmed WordPress REST structure:

```json
{
  "code": "...",
  "message": "...",
  "data": {
    "status": 415
  }
}
```

The documented DTO/error model must account for:

- `code`
- `message`
- nested `data.status`

Do not introduce user-facing raw server-message behavior.

---

# 6. Required change — implementation plan §14.2 state-machine contract

Remove the stale hedge that said ready detection was still pending Olaf/API confirmation or might require polling.

Replace it with the confirmed V1 behavior:

1. Create handoff.
2. Upload Reference to slot `one`.
3. Upload Capture to slot `two`.
4. The second successful upload returns `status=ready` and non-null `checkout_url`.
5. No separate V1 polling/status endpoint is required.
6. Later Custom Tab logic opens the exact returned `checkout_url`.

Also document the confirmed `Idempotency-Key` format:

- required
- 8–100 characters
- allowed characters `[A-Za-z0-9._-]`
- same user operation reuses the same key on Create retries.

Do not move retry orchestration from Block 8 into Block 7.

---

# 7. Required change — implementation plan §30

Remove the now-resolved open dependency concerning:

- whether second upload directly returns `checkout_url`; and
- whether a separate polling call is required.

That question is closed.

Retain only genuinely unresolved external items already present, such as applicable remaining:

- exact retry/backoff tuning beyond the fixed policy;
- supported locale matrix / wording sign-off;
- CI/release secret injection mechanism;
- debug/release key split if still genuinely unresolved;
- Privacy Policy / Play Data Safety / partner-disclosure work.

Do not invent new open dependencies.

---

# 8. Lock the slot semantic mapping

The user has explicitly decided:

- Reference → slot `one`
- Capture → slot `two`

Update the appropriate existing location(s) in the implementation plan where the mapping was deliberately deferred/open.

In particular, the prior Correction N language must no longer claim that the mapping remains a future Block-7+ decision.

Document it as a locked SameView V1 mapping.

Preserve the distinction:

- the external API defines slot names `one` and `two`;
- SameView's semantic assignment is the now-approved product/technical decision.

Where relevant, align this with the existing Block-5 temp-file convention:

- `image_one.jpg` = Reference output
- `image_two.jpg` = Capture output

Do not rename files.

---

# 9. `external_reference` privacy rule

Preserve the existing SameView V1 privacy decision:

- the external API supports optional `external_reference`;
- SameView V1 deliberately does **not** send it.

Do not weaken or reverse this rule.

Do not transmit or propose transmitting:

- local `sessionId`
- comparison ID
- names
- email addresses
- other unnecessary identifiers.

---

# 10. Integration spec update

In `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`, make the narrow approved addition around the existing handoff/network contract section:

State explicitly that V1 requires no separate polling/status endpoint.

The confirmed flow is:

- Create handoff
- upload Reference as `slot=one`
- upload Capture as `slot=two`
- second successful upload returns `status=ready` and `checkout_url`
- SameView later opens the exact returned URL

Keep this document at its existing architectural/product level. Do not duplicate the entire implementation-plan wire specification unnecessarily.

If the slot mapping belongs naturally in the same existing integration-contract section, record it there too so the authoritative integration spec and implementation plan do not contradict one another.

---

# 11. Confirmed request contract to preserve

The synchronized docs must remain consistent with these facts:

## Create

`POST /wp-json/dwb/v1/partner-handoffs`

Headers:

- `Content-Type: application/json`
- `X-DWB-Partner-Key`
- `Idempotency-Key`

SameView request body:

- `partner = sameview`
- optional locale as supported
- **no `external_reference` in SameView V1**

## Upload

`POST <exact upload_url returned by Create>`

Header:

- `X-DWB-Handoff-Token`

Multipart:

- `slot`
- `file`

File field:

- name `file`
- JPEG
- `image/jpeg`

No partner key on uploads.

## Ready

Second successful upload:

- HTTP `200`
- `status=ready`
- non-null `checkout_url`
- no polling.

---

# 12. Block boundaries that must remain unchanged

This documentation sync must **not** alter the implementation sequencing.

Keep:

- Block 7: network client + DTOs only
- Block 8: operation/retry orchestration
- Block 9: partner-key/BuildConfig secret injection
- Block 10: manifest/INTERNET permission and real integration activation, according to the existing approved plan
- later Custom Tab/UI work according to the current plan

Do not move functionality between blocks.

Do not implement any of it now.

---

# 13. Forbidden changes

Strictly forbidden:

- Kotlin changes
- tests
- Gradle changes
- OkHttp dependency
- manifest changes
- INTERNET permission
- BuildConfig changes
- `local.properties`
- real API calls
- pilot endpoint probing
- API-key storage
- retry implementation
- ViewModel changes
- Wackelbild renderer changes
- temp-file cleanup changes
- Custom Tab changes
- UI changes
- unrelated documentation cleanup
- prompt archive changes
- formatting churn outside edited passages.

---

# 14. Verification

After editing, run:

```bash
git diff --check
git status --short
git diff -- docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md
```

Also perform targeted textual checks proving that:

- stale "polling may be required" uncertainty is removed from live guidance;
- `uploadUrl` is documented at the upload client boundary;
- `X-DWB-Handoff-Token` is documented for uploads;
- `file` and `slot` are documented;
- `Reference → one` and `Capture → two` are now locked;
- `external_reference` remains deliberately omitted by SameView V1;
- no real `sv_test_...` / `sv_live_...` key appears in the modified files;
- no third file was modified by this gate.

No Gradle/test run is required because this gate is documentation-only.

---

# 15. Required final report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified

List the exact files.

Explicitly confirm whether only the two authorized docs changed.

## 3. Contract Corrections Applied

Report separately:

### A. Dynamic upload URL
What changed in §13.1.

### B. DTO / error envelope
What changed in §14.1.

### C. Ready detection / no polling
What changed in §14.2 and integration spec.

### D. Idempotency-Key
Exact documented constraints.

### E. Slot mapping
Confirm:
- Reference → `one`
- Capture → `two`
- prior deferred-decision wording removed/corrected.

### F. External reference
Confirm optional API support but deliberate SameView V1 omission.

### G. Open dependencies
State what was removed from §30 and what genuinely remains.

## 4. Security Check

Confirm:

- no real API key copied;
- no secret-wiring file touched;
- no key appears in modified docs.

Do not print any discovered secret value.

## 5. Block Boundary Preservation

Confirm no Block 8/9/10 work was pulled forward.

## 6. Verification

Report:

- `git diff --check`
- final `git status --short`
- exact diff scope
- no Gradle/tests run and why.

## 7. Remaining Open Decisions

Only list decisions that can still block Block 7 network-client implementation.

If none, write:

`None`

## 8. Gate Result

If everything is clean:

**BLOCK 7B DOC SYNC COMPLETE — BLOCK 7 NETWORK IMPLEMENTATION UNBLOCKED**

Otherwise state the exact blocker and STOP.

---

# Final instruction

Implement exactly this documentation sync and nothing else.

No opportunistic cleanup.
No code.
No secrets.
No network calls.
