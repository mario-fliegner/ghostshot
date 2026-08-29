# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5B: IMPLEMENTATION PLAN CORRECTION BEFORE HQ/PRINT RENDERER

## Role

You are working in the existing SameView Android repository.

Block 5A analysis is complete. It found that the current `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` contains stale or technically incorrect assumptions that directly affect Block 5.

This gate is **PLAN CORRECTION ONLY**.

Do not implement Kotlin/Java/XML/Gradle/Manifest code.
Do not modify tests.
Do not begin the renderer.
Do not begin Block 6.
Do not contact DeinWackelbild.
Do not perform any API/network call.

You may modify exactly one file:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

The goal is to bring the plan into alignment with:

- the current repository;
- the authoritative feature/storage/HQ specs;
- the accepted Block 3B / Block 4 implementation corrections;
- the new Block 5A evidence.

After this gate, the implementation prompt must use the corrected plan as the single technical basis for Block 5.

---

# 1. Authoritative Inputs

Read fully before editing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/SESSION_ORIGINALS_V1.md`
- `docs/SESSION_ORIGINALS_PRIVACY_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Also inspect current implementation evidence needed to validate the corrections:

- `ShareImageRenderer.kt`
- `ShareRenderConfig.kt`
- `ReferenceRenderer.kt`
- camera capture/save pipeline
- current Wackelbild Block-3/4 implementation files.

Do not edit any of those files.

---

# 2. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Confirm working tree state before editing.

Do not disturb unrelated prompt archives.

---

# 3. Correction A — Capture Parity Contract

The current plan contains a stale/incorrect statement that Capture ratio mismatch is "architecturally unreachable".

Correct this.

The current repository evidence shows:

- `capture.jpg` is written from the in-memory corrected bitmap;
- `capture-original.jpg` is a byte-preserved copy of the MediaStore JPEG produced from the same corrected bitmap;
- no `ImageProxy.cropRect` crop is applied in the capture callback;
- therefore `capture.jpg` and `capture-original.jpg` preserve the same full image content;
- however their ratio may differ from metadata `viewport.width : viewport.height`.

The corrected plan must state:

1. Wackelbild does **not** use `prepareHqCaptureForSbs()`.
2. Wackelbild does **not** center-crop `capture-original.jpg` to viewport ratio.
3. The HQ path uses direct uncropped decode/downsample of `capture-original.jpg`.
4. Before doing so, the renderer compares the actual persisted Capture ratio against the expected viewport/session ratio.
5. If ratio divergence exceeds the approved tolerance, HQ Capture is considered unusable and the renderer switches to the frozen-pair fallback.
6. Fallback uses `capture.jpg` itself as the visual source of truth.
7. No stretching, no guessed crop, no approximation.

Remove all wording that calls mismatch "architecturally unreachable".

Update all related sections:

- Capture HQ reconstruction
- risk register
- implementation block
- tests
- reuse matrix
- Definition of Done.

---

# 4. Correction B — ShareImageRenderer Reuse: 3 Methods, Not 4

The old plan still assumes four `ShareImageRenderer` methods need visibility widening.

Correct it to exactly three:

- `decodeHqCapture`
- `renderHqReference`
- `decodeReferenceFallback`

`prepareHqCaptureForSbs` must remain untouched/private if it is not required by the current implementation.

The plan must state:

- only `private -> internal`
- zero logic change
- zero signature change
- zero existing Share Image call-site change.

Update:

- file scope
- reuse matrix
- implementation block
- regression risks
- tests.

---

# 5. Correction C — No Planned Legacy-Session Scope

The current plan still references explicit v2-v4 legacy-session validation.

Remove that from the DeinWackelbild V1 product/test scope.

Correct product assumption:

- SameView was publicly released after originals storage already existed.
- DeinWackelbild V1 does not need dedicated compatibility work for pre-release legacy sessions.
- Do not add legacy-specific code, fixtures, or acceptance criteria.

Keep the normal fallback behavior for current sessions:

- HQ source unexpectedly missing/corrupt/unusable
- frozen `reference.jpg` + `capture.jpg` valid
- fresh transfer JPEG pair is produced
- later UI warning applies.

Update:

- pilot validation
- tests
- risks
- Definition of Done
- open dependencies if needed.

---

# 6. Correction D — Current Block 3/4 Implementation Reality

The old plan contains stale planned files/values that were superseded by actual implementation.

Correct the plan to reflect current state:

- no separate `WackelbildPreview.kt`
- no `DateBadgeGeometry.kt`
- no `DateBadgeOverlay.kt`
- current preview remains in `WackelbildScreen.kt`
- current date formatter is `DateBadgeFormatter.kt`
- current preview badge is Compose-only
- current physical tilt trigger = `9°`
- current re-arm = `6°`

Do not rewrite historical commentary unless needed for plan accuracy.

Update only current/future implementation sections.

---

# 7. Correction E — 20 MiB Pair-Level Strategy

Confirm the plan contains the accepted Gate-3B corrected algorithm.

If any stale wording remains, normalize it to exactly:

- encode quality 92
- then encode quality 85
- if either file is still >20 MiB:
  - reduce **both** output dimensions together
  - restart from quality 92
- dimension step factor: use the currently accepted plan value only if already explicitly locked
- maximum 4 dimension levels
- maximum 8 total pair attempts
- both files always have identical dimensions
- direct temp-file encoding
- size check via `File.length()`
- no repeated giant `ByteArray`
- no JPEG quality 50 path
- bounded failure if still too large.

If the dimension reduction factor is not already authoritatively fixed in the current corrected plan, do **not** invent one here. Mark it as requiring a technical decision in this gate and resolve it using existing plan evidence.

---

# 8. Correction F — Resolve the New Memory-Safety Question

Block 5A proposed an explicit 150MB combined-bitmap budget.

Do **not** simply copy that into the plan.

Re-evaluate this from repository evidence.

The plan must not claim generic Android heap guarantees that are not actually guaranteed.

Instead, choose the smallest defensible approach.

Preferred analysis path:

1. Calculate bitmap memory from actual target dimensions.
2. Confirm whether common-resolution calculation can happen from dimensions/metadata before decode.
3. Confirm whether Reference and Capture final bitmaps must coexist.
4. Confirm whether pair-level file-size decisions really require simultaneous decoded bitmaps, or whether files can be encoded sequentially per attempt and compared afterward.
5. Determine whether sequential rendering can avoid two full-size output bitmaps in memory.

The corrected plan should prefer:

- dimension decision first;
- render Reference -> encode -> recycle;
- render Capture -> encode -> recycle;
- compare file sizes after both files exist;
- if either too large, delete/replace both pair candidates at the next shared dimension/quality attempt.

This avoids holding two final output bitmaps simultaneously.

If this is compatible with the current size-loop design, adopt it.

Then determine whether a separate hard memory cap is still needed.

If no repository-backed safe universal heap cap exists, do **not** introduce an arbitrary MB limit.

Instead prefer:

- source-bounded/common-resolution logic;
- API ceiling;
- sequential rendering;
- `OutOfMemoryError` caught at the renderer boundary;
- HQ path failure -> approved frozen-pair fallback;
- if fallback itself OOMs -> permanent preparation failure.

If a lower renderer-side pixel cap is still genuinely necessary, derive it from an explicit repository/device requirement and document the evidence.

No ungrounded 150MB constant.

---

# 9. Correction G — Resolve Capture Ratio Tolerance

Block 5A proposed `2%`.

Do not blindly encode it into the plan.

The plan needs one exact deterministic rule, but it should be technically grounded.

Analyze:

- CameraX target 16:9 / 9:16 intent
- common stream sizes
- benign encoder dimension padding such as 1920×1088
- genuine incompatible ratios such as 4:3 / 3:4.

Choose the smallest tolerance that:

- accepts normal codec/stream-alignment variance;
- rejects genuinely different aspect ratios.

If 2% remains the best justified value, keep it, but explain the evidence in the plan.

Do not use absolute pixel tolerances.

Use relative ratio error:

`abs(actualRatio - expectedRatio) / expectedRatio`

The plan must make this a named Wackelbild-specific technical constant and test it around the boundary.

---

# 10. Correction H — Replace the Weak xxhdpi Badge Scaling Proposal

Do **not** use:

`preview dp * 3.0 * commonScale`

This is not a valid general mapping between Compose dp/sp and print bitmap pixels because `commonScale` is based on session viewport pixels, not physical display density.

The corrected plan must derive bitmap badge geometry from the **output image itself**, not from an arbitrary device-density baseline.

Use a proportional output-space model.

Choose exact ratios for:

- text size
- edge margin
- horizontal padding
- vertical padding
- corner radius

relative to the final image's **short edge**.

The ratios must preserve the visual character of Block 4:

- compact badge
- not pill-shaped
- white `labelMedium`-like text
- dark CI background
- 8dp-ish edge/padding character
- 6dp-ish corner character.

Derive the ratios from the actual Block-4 geometry against a representative/current preview baseline, then lock the resulting fractions in the plan.

Requirements:

- same proportions for Portrait/Landscape
- deterministic
- independent of physical Android density
- scales with output resolution
- no dp->px conversion in the bitmap renderer
- no dependence on device DPI
- no implementation-time choice.

If needed, use a normalized baseline such as:
- short edge = 360 logical preview units
and derive proportions from Block-4's 8/4/6/12 values.

But document the math explicitly.

---

# 11. Correction I — Reference Genuine Scale Formula

Re-check the current planned formula:

`referenceScale = 1 / effectiveScale`

This may be incomplete if offsets cause one edge of the visible source region to run closer to the source boundary than another.

The corrected plan must prove that the chosen formula is sufficient for all supported `ReferenceRenderer` modes and offsets.

If the actual visible-source density depends only on `effectiveScale`, state why mathematically.

If offset affects required source coverage but not sampling density, explain that distinction.

Do not leave this implicit.

Tests must include:

- positive/negative X offset
- positive/negative Y offset
- scale >1
- FIT/FILL/current display modes.

---

# 12. Correction J — Fallback Common Dimensions

The plan must specify exact fallback dimension behavior.

Fallback sources:

- frozen `reference.jpg`
- frozen `capture.jpg`.

They may already have matching dimensions in normal sessions, but do not assume blindly.

Define:

- actual decoded dimensions read first;
- target ratio derived from frozen Comparison/session viewport;
- common no-upscale target derived from the weaker frozen source;
- both freshly rendered/re-encoded to identical dimensions.

If frozen pair dimensions differ unexpectedly:

- no stretch;
- no guessed crop;
- use only a mathematically safe Fit/downscale path if it preserves exact visible content;
- otherwise return permanent preparation failure.

Because frozen files are already the exact visual compositions, avoid any crop operation in fallback.

---

# 13. Correction K — Date Badge Bitmap Strategy Must Be Exact

After resolving §10, update all date-badge renderer sections.

The renderer should receive already-formatted date strings.

It must not read metadata itself.

It must not format dates.

Block 4 remains untouched.

Bitmap badge must use:

- output-relative ratios
- `SameViewAppSurface`
- white text
- anti-aliasing
- bottom-right
- no shadow
- no border
- no title/location/branding.

If Capture date is missing:

- Capture gets no date badge
- Reference still gets its badge.

---

# 14. Correction L — Temp File Manager Boundary

Keep the corrected Block-5/Block-6 boundary explicit.

Block 5 may implement creation side only:

- `cacheDir/wackelbild/<operationId>/`
- output handles
- candidate replacement during pair-size loop.

Block 5 must not implement:

- screen-entry sweep
- cleanup on upload success
- cleanup on user cancel
- cleanup on final network error
- `onCleared()` cleanup
- lifecycle cleanup orchestration.

Test teardown cleanup is fine.

---

# 15. Correction M — Typed Result / Failure Model

Correct any awkward result model in the plan.

A successful fallback is **not** a failure enum.

Use a clean model such as:

- `Success(pair, usedFallback = false)`
- `Success(pair, usedFallback = true)`
- `Failure(reason)`

Failure reasons should cover only true failures.

Do not include `HQ_UNAVAILABLE_FALLBACK_USED_BUT_SUCCEEDED` in a failure enum.

Keep API minimal.

---

# 16. Correction N — Pair Mapping

Lock semantic result fields:

- `referenceFile`
- `captureFile`

Do not let renderer internals expose only `one`/`two`.

If external API later uses slots one/two, mapping happens explicitly in network block.

Do not claim API mapping is contractually defined if it is not in the partner spec.

---

# 17. Correction O — Privacy Test Scope

The corrected plan must test more than GPS.

Plan explicit assertions for sensitive metadata where Android APIs allow:

- GPS latitude/longitude
- DateTimeOriginal
- DateTime
- Make
- Model
- Software
- Lens fields
- serial identifiers
- orientation dependence
- MakerNote if inspectable.

Fresh JPEG must decode correctly.

Do not require removal of harmless color-profile data if Android encoder includes it and it contains no personal content.

---

# 18. Correction P — Persisted-File Immutability

Add explicit before/after byte/hash checks for:

- `reference.jpg`
- `capture.jpg`
- `reference-original.jpg`
- `capture-original.jpg`
- `metadata.json`

If `reference-source-original.<ext>` exists in a fixture, verify unchanged even though unused.

No Block-5 production code may open those files for write.

---

# 19. Correction Q — No Direct `reference-source-original` Use

Keep explicit:

- V1 uses `reference-original.jpg`
- `reference-source-original.<ext>` remains out of scope
- no HEIC/HEIF/AVIF/raw direct decoding path
- no new orientation pipeline.

This prevents future implementation drift.

---

# 20. File Scope Update

Update the implementation plan's Block-5 file table to reflect the corrected likely scope.

Expected production files:

- modify `ShareImageRenderer.kt` — visibility only, exactly 3 methods
- create `image/wackelbild/WackelbildDimensionResolver.kt`
- create `image/wackelbild/WackelbildPrintRenderer.kt`
- create `image/wackelbild/WackelbildDateBadgeRenderer.kt` or final agreed name
- create `ui/wackelbild/WackelbildTempFileManager.kt` creation-side only

Expected tests:

- dimension resolver unit test
- renderer unit test
- date badge renderer unit test
- instrumented print renderer test

Expected docs:

- `docs/IMPLEMENTATION_NOTES.md` in implementation block
- no feature-spec rewrite unless implementation later reveals a real contract conflict.

Remove stale files from the plan:

- `WackelbildPreview.kt`
- `DateBadgeGeometry.kt`
- `DateBadgeOverlay.kt`

Do not modify current code in this gate.

---

# 21. Implementation Block 5 Rewrite

Rewrite the Block-5 plan section so future implementation has no open choices.

It must state exact sequence:

1. dimension/source analysis
2. HQ eligibility decision
3. common target dimensions
4. temp operation directory creation
5. render/encode Reference
6. recycle
7. render/encode Capture
8. recycle
9. check both file sizes
10. if needed pair-level quality/dimension retry
11. output typed pair
12. if HQ path fails -> recreate pair from frozen fallback sources
13. if fallback fails -> permanent preparation failure.

Do not leave implementation choices to the coding step.

---

# 22. Test Plan Rewrite

Make tests match the corrected architecture.

Must include:

- ratio tolerance boundary tests
- no-upscale
- reference offset/scale cases
- capture direct-uncropped HQ case
- ratio mismatch -> fallback
- pair size loop
- sequential render behavior where testable
- date badge geometry ratios
- privacy
- immutability
- fallback
- pair mapping.

No legacy v2-v4 fixtures.

---

# 23. Risk Register Update

Correct/add risks:

- Capture ratio divergence
- reference visible-source scale math
- memory/OOM
- JPEG size loop
- metadata leakage
- persisted-file mutation
- badge proportional scaling
- color fidelity
- ShareImageRenderer regression surface.

Remove stale risk wording.

---

# 24. Documentation Consistency

After correction, the implementation plan must no longer contradict:

- current Block 3/4 code
- current feature spec
- current HQ original spec
- current session storage/privacy specs.

Do not change other docs in this gate.

---

# 25. Verification for Gate 5B

After editing only the plan:

1. `git diff --check`
2. `git diff -- docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
3. `git status --short`

Then re-read the full plan and verify:

- no stale "architecturally unreachable" capture mismatch text
- no 4-method widening
- no legacy v2-v4 Wackelbild requirement
- no old 12° threshold
- no old `WackelbildPreview.kt`
- no old shared date-badge geometry files
- no 18-attempt/JPEG-50 path
- no arbitrary xxhdpi bitmap badge scaling
- no arbitrary 150MB heap guarantee unless repository evidence truly supports it
- Block 5/6 boundary is explicit
- no code changed
- no API/network call occurred.

---

# 26. Required Final Response

Return exactly:

## 1. Repository Baseline

## 2. File Modified

- exact path
- before/after line count
- confirm no other file changed

## 3. Corrections Applied

Use these exact subheadings:

### A. Capture parity
### B. ShareImageRenderer reuse scope
### C. Legacy-session scope
### D. Current Block 3/4 reality
### E. Pair-level 20 MiB algorithm
### F. Memory / OOM strategy
### G. Capture ratio tolerance
### H. Bitmap date-badge scaling
### I. Reference genuine-scale proof
### J. Fallback dimensions
### K. Temp-file Block 5/6 boundary
### L. Result/failure model
### M. Pair mapping
### N. Privacy / immutability
### O. File scope / block sequence / tests

For each:
- prior problem
- corrected plan
- exact plan sections updated.

## 4. Final Block-5 Architecture

Summarize the final renderer flow in order.

## 5. Remaining Open Technical Decisions

List only items that genuinely remain unresolved after this gate.

If none, say `None`.

## 6. Verification

Report:

- `git diff --check`
- final status
- exact diff scope
- Gradle/tests run or not run
- confirm no API calls
- confirm no production code changes.

## 7. Gate Result

Choose exactly one:

- **BLOCK 5B COMPLETE — CORRECTED PLAN READY FOR BLOCK 5 IMPLEMENTATION**
- **BLOCK 5B BLOCKED — PRODUCT/TECHNICAL DECISION REQUIRED**

Then STOP.

Do not implement Block 5.

---

# Final Rule

This gate corrects the plan only.

One file.
No Kotlin.
No tests.
No Gradle.
No manifest.
No network.
No Block 5 implementation.

The next implementation prompt must be generated from the corrected plan, not from the pre-correction Block-5A report.
