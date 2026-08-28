# CLAUDE PROMPT — DEINWACKELBILD V1 — GATE 3B: IMPLEMENTATION PLAN CORRECTION ONLY

## Role

You are working in the existing SameView Android repository.

Gate 3 produced:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

That plan has been reviewed. The architecture is broadly accepted, but several technical corrections are required before implementation can be approved.

This Gate 3B is **PLAN-CORRECTION ONLY**.

You are authorized to modify exactly one repository file:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Do not modify any production code.
Do not modify tests.
Do not modify Gradle.
Do not modify AndroidManifest.
Do not modify resources.
Do not modify `CLAUDE_PROJECT_INSTRUCTION.md`.
Do not modify the product specification.
Do not contact DeinWackelbild.de.
Do not run the real API.
Do not begin Block 1 implementation.

The goal is to correct the implementation plan so it fully matches the already-approved product/spec contract and removes avoidable ambiguity before implementation starts.

---

# 1. Required Inputs

Read fully before editing:

- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`

Also re-check the exact current code where needed to validate the corrections below, especially:

- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceRenderer.kt`
- the current capture/session rendering code that produces `capture.jpg`
- the current lifecycle/navigation patterns used by `ShareComparisonScreen` / `CreateVideoScreen`
- the current resource/manifest/build files only as read-only evidence.

Do not reinterpret the product spec.

If any correction below is impossible without a product decision, STOP and report it instead of guessing.

---

# 2. Authorized File Scope

Exactly one file may be modified:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

No other file may be changed.

If `git status` contains unrelated changes, leave them untouched.

---

# 3. Correction A — Common Resolution Must Consider BOTH Sources

The current plan's `WackelbildDimensionResolver` is insufficient because it derives scale from `captureOriginalDims` only.

That violates the approved contract:

> Both transfer images use the highest genuinely available **common** resolution and the weaker source must never be artificially upscaled.

Correct the plan so the output resolution is bounded by the real usable resolution of **both** sides:

1. Capture source capability.
2. Reference source capability after taking the stored SameView crop/alignment geometry into account.
3. API safety limits.

The reference side must not be evaluated merely from the full pixel dimensions of `reference-original.jpg`.

The relevant quantity is the maximum output resolution that can be produced from the **actually visible source area** represented by the stored reference transform / viewport geometry without upscaling source pixels.

The corrected plan must define, at implementation-plan level, how to compute a maximum genuine scale for:

- Capture.
- Reference.

Then:

`commonScale = min(captureScale, referenceScale, apiSideLimitScale, apiMegapixelLimitScale)`

or an equivalent exact algorithm.

Hard requirements:

- No source side may be upscaled.
- API maxima are upper bounds, not targets.
- The weaker source determines the maximum genuine common resolution.
- The final two files still have identical pixel dimensions.
- The visible Comparison crop/alignment remains unchanged.

Do not preserve `.coerceAtLeast(1f)` if that can force output larger than a genuinely available source.

If fallback output must be smaller than the persisted viewport dimensions to avoid upscaling, the plan must explicitly allow that rather than silently upscaling.

Update all affected sections, including:

- HQ Print Image Architecture.
- Common Resolution / API Limit Strategy.
- Reuse matrix if needed.
- Tests.
- Risk Register.
- Implementation Block 5.

---

# 4. Correction B — Capture Crop Parity Must Be Guaranteed, Not "Test and Maybe Fix"

The current plan explicitly admits that `decodeHqCapture()` may stretch if the native capture-original aspect ratio differs from the stored viewport ratio, and proposes detecting this later through a test.

That is not sufficient.

The approved spec requires:

> The HQ Capture transfer image must show exactly the same visible crop/content as persisted `capture.jpg`.

The implementation plan must define a deterministic Capture reconstruction path that guarantees this from the start.

Re-inspect how `capture.jpg` is actually produced today.

Then select the correct algorithm that reproduces the exact visible Capture composition at a higher resolution.

If the correct method is equivalent to the fill/crop logic used by `prepareHqCaptureForSbs`, plan a Wackelbild-specific adaptation of that geometry.

If the correct method is something else, document it precisely with repository evidence.

Do not leave the architecture as:

> use `decodeHqCapture()` unless a future test proves it wrong.

Remove that conditional architecture.

Tests should verify the selected guaranteed algorithm, not decide which algorithm implementation should use after the fact.

Update:

- §9.3 Capture side.
- Crop-parity explanation.
- Tests.
- Risk Register.
- Block 5 stop criteria.
- Reuse matrix if necessary.

Reference-side parity remains based on the existing proven `ReferenceRenderer` geometry.

---

# 5. Correction C — Foreground Resume Must NOT Be Confused With Custom-Tab Return

The current plan proposes resetting the Wackelbild screen on generic `ON_RESUME`.

That conflicts with the approved lifecycle behavior:

- A short app backgrounding may allow the current in-memory upload to continue.
- Returning from Home/another app must not be mistaken for returning from DeinWackelbild.
- The shop must not unexpectedly open while SameView is still backgrounded.

Correct the plan with an explicit ephemeral state distinction.

The plan must include a state equivalent to:

- `awaitingCustomTabReturn`
- or another clearly named transient marker.

Required behavior:

## Normal background / resume during preparation or upload

- Do not reset the screen.
- Do not reset to Reference merely because `ON_RESUME` fires.
- Do not re-enable the date toggle if the handoff is still active.
- Keep the active operation state intact.

## Upload finishes while SameView is backgrounded

- Do not launch the Custom Tab from the background.
- Keep the ready `checkout_url` in ephemeral memory.
- Launch the Custom Tab only once SameView is active/foreground again.

## Actual Custom Tab launch

Immediately before launching the Custom Tab, mark that the screen is awaiting return from that Custom Tab.

## Actual resume after the Custom Tab was launched

Only then perform the approved return reset:

- Reference visible.
- date toggle editable again.
- same date toggle value retained for that screen visit.
- normal CTA shown again.
- no order-status inference.

The implementation plan must explicitly distinguish:

- foreground state,
- ready-to-open state,
- custom-tab-launched state,
- custom-tab-return state.

No persistent storage is introduced.

Update:

- Navigation / Screen Architecture.
- ViewModel state model.
- Custom Tab integration.
- Lifecycle / Background Behavior.
- Tests.
- Real-device validation.
- Risk Register.

---

# 6. Correction D — Partner Key Header Is NOT an Open Question

The supplied DeinWackelbild V1 API contract already specifies:

`X-DWB-Partner-Key: sv_live_...`

Alternative accepted form:

`Authorization: Bearer sv_live_...`

SameView V1 should use the explicitly documented partner header:

`X-DWB-Partner-Key`

unless Olaf later supplies a newer API contract.

Correct the plan:

- Replace the placeholder `X-SameView-Partner-Key`.
- Remove "exact request header name" from Open External Dependencies.
- State that `X-DWB-Partner-Key` is the V1 header contract.
- Keep the key on create-handoff only, per the supplied API.
- Do not add the key to upload requests unless the actual API spec changes.

Also correct retry/timeout uncertainty.

The supplied API already states:

- Create/upload retries at network failure: up to three attempts with increasing delay.
- Upload connection/write timeout: at least 60 seconds.
- Same Idempotency-Key for Create retries belonging to the same user operation.

Therefore:

- Do not list retry count itself as wholly open.
- Do not list the minimum upload timeout as open.
- Only exact backoff intervals remain implementation/external tuning if not defined.
- Exact server-specific read/connect timeout beyond the documented minimum can remain a plan decision.

Update:

- Partner-Key Provisioning.
- API client.
- Retry model.
- Open External Dependencies.
- Implementation Blocks 7/8/9 as needed.

---

# 7. Correction E — JPEG ≤20 MiB Strategy Must Prefer Print Quality and Operate on the PAIR

The current quality ladder:

`92 → 85 → 75 → 65 → 55 → 50`

before meaningful dimension reduction is too aggressive for a physical print product.

Correct the plan so the strategy prioritizes:

1. Preserve high JPEG quality.
2. Reduce pixel dimensions when needed.
3. Use lower JPEG quality only within a sensible print-quality floor.

The algorithm must operate on the **pair**, not independently.

Required invariants:

- Both output images always end with identical pixel dimensions.
- A dimension reduction triggered because either file exceeds 20 MiB applies to BOTH images.
- The final target dimensions are a pair-level decision.
- Never leave one side at larger pixel dimensions than the other.
- No unbounded loop.
- No artificial upscaling.

The plan should choose a bounded, concrete strategy.

A preferred shape is:

- Start at high JPEG quality (for example the existing 92).
- Try a small set of high-quality values only.
- If either image remains >20 MiB, reduce BOTH dimensions by a defined factor.
- Re-render/re-decode the pair at the new common dimensions.
- Retry from high JPEG quality.
- Repeat for a bounded number of dimension steps.
- Fail safely if still impossible.

Do not descend to JPEG quality 50 unless the plan provides a strong, print-specific justification. A physical print should generally sacrifice excess pixel dimensions before severe JPEG quality.

Also reconsider memory behavior:

The pipeline already uses temp files.

Prefer planning to encode directly to temporary files (or another bounded stream/file target) and check `File.length()` rather than building repeated potentially >20 MiB in-memory `ByteArray` buffers for every attempt.

If a small in-memory buffer is still needed for a specific reason, justify it.

Update:

- §10.3.
- HQ renderer flow.
- Temp file flow.
- OOM mitigation.
- Tests.
- Risk Register.
- Block 5.

The corrected plan must explicitly state the exact maximum number of pair-level attempts and why that bound is safe.

---

# 8. Correction F — Release Artifact Key Check Must Be Technically Honest

The plan correctly states that an API key embedded in a released APK/AAB is extractable.

It then later requires an artifact check that the key never appears in plaintext resources/strings.

That is not a valid secrecy guarantee for a `BuildConfig`-embedded runtime key.

Correct the plan so the release/security verification checks what is actually enforceable:

Must verify the real partner key is:

- not committed to VCS;
- not present in tracked source files;
- not placed in Android resources unnecessarily;
- not placed in the manifest;
- not placed in URLs;
- not logged;
- not emitted in request/response debug logs;
- not included in crash/telemetry output;
- not duplicated unnecessarily across generated artifacts/config.

Must explicitly acknowledge:

- the runtime key remains extractable from the compiled APK/AAB;
- artifact inspection cannot prove secrecy;
- R8/obfuscation is not a secrecy control;
- security depends on server-side narrow scope, rate limits, rotation, and limited API authority.

Update:

- Partner-Key Provisioning.
- Release / Privacy / Play Compliance.
- Test / release artifact checks.
- Definition of Done.
- Risk Register.

---

# 9. Correction G — Remove "Implementer's Choice" From Block 1

The implementation plan must be deterministic enough for the strict SameView one-fix-per-iteration workflow.

Current Block 1 says:

> temporarily navigates to a stub screen or is feature-flagged off until Block 2 lands, implementer's choice

Remove this ambiguity.

Use this corrected sequence:

## Block 1 — Compare menu entry only

Modify only the existing CompareScreen entry point:

- new callback parameter;
- divider;
- Wackelbild menu item;
- click wiring to callback;
- strings;
- tests.

Do NOT add MainActivity navigation yet.

At this point the caller may pass a no-op/null callback in the existing integration point as needed to keep compilation green, but do not introduce a temporary screen, fake route, feature flag, or disposable stub architecture.

Prefer the smallest compile-safe callback surface.

## Block 2 — Real destination + navigation

Then create:

- `WackelbildScreen`
- initial `WackelbildViewModel`
- real route constants
- route builder
- `MainActivity` wiring
- navigation tests

This removes temporary architecture and keeps each block focused.

Update:

- §6.1 / §6.2 if needed.
- File Scope block mapping.
- Implementation Blocks.
- Final implementation sequence.

No stub route.

No temporary feature flag.

No throwaway implementation.

---

# 10. Correction H — Remove Remaining Avoidable "Either/Or" Plan Choices

Search the full implementation plan for phrases such as:

- "implementer's choice"
- "or"
- "whichever"
- "if appropriate"
- "could"
- "either"
- "e.g."

Do not mechanically delete all such words, but inspect whether they represent unresolved **implementation choices that the plan itself should settle**.

Examples that should be deterministic before implementation:

## Local key documentation

The plan currently says:

> `local.properties.example` (or a comment in the existing README, whichever the implementation block finds appropriate)

Pick one concrete repository path/method now.

Do not leave this to the implementation block.

If a new example file would be undesirable because `local.properties` is Android-generated/local, choose a tracked documentation file under the DeinWackelbild docs folder instead and specify the exact path.

## Network logging

Do not introduce an OkHttp logging interceptor unless it is genuinely needed.

The safest plan is likely:

- no `HttpLoggingInterceptor` dependency at all;
- feature-specific debug logs, if any, manually log only non-sensitive high-level state;
- never log headers or bodies.

If this is the chosen path, say so explicitly.

## ProGuard

Keep `app/proguard-rules.pro` as "modify only if release build proves necessary" if that is truly evidence-driven; this is acceptable because it is a conditional reaction to an actual build failure, not a design choice.

The goal is not to eliminate legitimate conditional verification, but to eliminate avoidable architecture decisions being deferred to the implementer.

---

# 11. Preserve These Accepted Architecture Decisions

Do NOT change these unless one of the required corrections above directly forces a narrow adjustment:

- OkHttp, no Retrofit.
- `TYPE_ROTATION_VECTOR`.
- New `TiltProvider`, leaving `CompassProvider` untouched.
- Pure hysteresis state machine.
- Swipe override behavior.
- Preview outside vertical scroll.
- `cacheDir/wackelbild/<operationId>/`.
- Sweep stale temp files on screen entry.
- New two-file print pipeline.
- Existing Reference HQ geometry reuse.
- `ShareImageRenderer` visibility-only widening where still appropriate after Capture-parity correction.
- No broad renderer refactor.
- No persistent handoff state.
- No WorkManager.
- No foreground upload service.
- `androidx.browser` Custom Tabs.
- Exact API-returned checkout URL.
- `org.json` instead of adding another JSON library.
- BuildConfig-based partner-key injection from local/CI configuration.
- No `external_reference`.
- No metadata in transfer images.
- No session/original-file mutation.
- No product price/options inside SameView.
- No order-status tracking.

If any accepted choice must change because Correction A/B/C/E proves it technically incompatible, explain that exact dependency in the plan rather than making an unrelated redesign.

---

# 12. Tests Required by the Corrections

The corrected plan must add/adjust explicit tests for:

## Common source resolution

- Capture is the weaker source.
- Reference visible source area is the weaker source.
- API side cap is the limiting factor.
- API megapixel cap is the limiting factor.
- No source is upscaled.
- Output may be smaller than viewport if necessary to honor no-upscale.

## Capture parity

- Native capture-original aspect ratio equals viewport.
- Native capture-original aspect ratio differs from viewport.
- Resulting HQ Capture content matches persisted `capture.jpg` crop/composition.

## Lifecycle

- Home/background during active upload does NOT reset UI.
- Resume from unrelated app does NOT simulate Custom Tab return.
- Upload completes while backgrounded → no background browser launch.
- Foreground after ready → Custom Tab launches exactly once.
- Actual Custom Tab return → reset-to-Reference behavior occurs exactly once.
- Date toggle value retained across actual Custom Tab return.
- Date toggle remains frozen during active upload.

## Partner header

- Create request uses `X-DWB-Partner-Key`.
- Upload requests do NOT contain partner key.
- Same Idempotency-Key is reused on Create retry.

## Pair size handling

- One file >20MiB triggers a pair-level dimension reduction.
- Both images get the same new dimensions.
- Encoding restarts at high quality after pair downscale.
- Pair-level algorithm is bounded.
- No large unnecessary in-memory encoded buffers remain after each attempt.

## Security

- Real key absent from tracked files.
- No key in URL.
- No key in logs.
- No request-body/header logging.
- Test builds work with blank/fake key without real API traffic.

---

# 13. Update the Risk Register

The corrected plan must explicitly include:

- reference-source true-resolution calculation risk;
- capture crop-parity risk;
- background/custom-tab state confusion risk;
- pair-level size-loop/OOM risk;
- embedded-key extractability as an accepted limitation, not something artifact scanning can eliminate.

Remove risk text that is no longer accurate after the corrections.

---

# 14. Update Open External Dependencies

After correction, the following must **not** be listed as externally unknown:

- partner header name — defined as `X-DWB-Partner-Key` by the supplied V1 API contract;
- whether retries are allowed — yes, supplied spec says up to three on network errors;
- whether upload timeout should be at least 60s — yes, supplied spec says so.

What may remain external/open:

- actual installed pilot endpoint behavior if it differs from the supplied spec;
- exact backoff intervals if Olaf's installed implementation imposes additional constraints;
- exact supported locale matrix;
- whether debug and release partner keys differ;
- CI secret-injection mechanism;
- compliance/Play/Privacy items;
- real-device tilt tuning;
- final visual badge tuning;
- whether the installed pilot API has changed since the 2026-08-18 contract.

Phrase these as validation of possible API drift, not as if the supplied written V1 contract did not already exist.

---

# 15. Implementation Block Discipline

After corrections, every implementation block must:

- have one primary concern;
- list exact files;
- contain no temporary architecture;
- contain no "implementer's choice";
- state tests;
- state regression risks;
- state stop criteria.

The plan must remain compatible with the SameView workflow:

1. analysis;
2. scope confirmation;
3. explicit approval;
4. implementation;
5. verification.

No block may be treated as automatically approved merely because it appears in the plan.

---

# 16. Verification for Gate 3B

After editing the plan, run:

1. `git diff --check`
2. `git diff -- docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
3. `git status --short`

Then re-read the full corrected plan and verify:

- no product decision changed;
- all eight correction groups above are addressed;
- no new unresolved implementation-choice language was introduced;
- no file except the plan changed;
- no API call occurred;
- no implementation code was added.

No Gradle build is required for this documentation-only correction gate.

---

# 17. Required Final Response

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. File Modified

- exact path
- before/after line count
- confirm no other file changed in this gate

## 3. Corrections Applied

Use exactly these subheadings:

### A. Common resolution — both sources

### B. Capture crop parity

### C. Background vs. Custom Tab lifecycle

### D. Partner header / retry / timeout contract

### E. Pair-level ≤20 MiB strategy

### F. Partner-key release/security wording

### G. Implementation block sequencing

### H. Remaining ambiguity cleanup

For each:
- summarize previous problem;
- summarize corrected plan;
- cite the plan section(s) changed.

## 4. Architecture Decisions Preserved

Confirm all accepted architecture decisions in §11 remain intact, or list any narrowly changed item and why.

## 5. Test Plan Changes

Summarize the new/changed tests added to the plan because of this correction gate.

## 6. Open External Items

List only the genuinely external items remaining after correction.

## 7. Verification

Report:

- `git diff --check`
- final `git status --short`
- exact diff scope
- Gradle/tests run or not run
- API calls made or not made

## 8. Gate Result

Choose exactly one:

- **GATE 3B COMPLETE — CORRECTED IMPLEMENTATION PLAN READY FOR FINAL REVIEW**
- **GATE 3B BLOCKED — PRODUCT/SPEC DECISION REQUIRED**

No implementation is authorized by this gate.

---

# Final Rule

Correct the plan only.

One file.

No code.
No manifest.
No Gradle.
No tests.
No API.
No product changes.
No unrelated cleanup.

The purpose of Gate 3B is to remove technical ambiguity before Block 1 begins.
