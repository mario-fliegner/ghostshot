# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5C: FINAL PLAN CORRECTION BEFORE HQ/PRINT IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

Gate 5B corrected the DeinWackelbild implementation plan substantially, but final review found **two remaining technical logic errors** that must be fixed before Block 5 implementation starts.

This gate is **PLAN CORRECTION ONLY**.

Do not implement Kotlin/Java/XML/Gradle/Manifest code.
Do not modify tests.
Do not begin the renderer.
Do not begin Block 6.
Do not contact DeinWackelbild.
Do not perform any network/API call.

You may modify exactly one file:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

The purpose of Gate 5C is to correct exactly these two issues:

1. date-badge rendering must occur **before final JPEG encoding/file-size validation**
2. frozen fallback files with incompatible aspect ratios must **fail**, not be coerced into a common size

No other architecture change is authorized.

---

# 1. Authoritative Inputs

Read fully before editing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/SESSION_ORIGINALS_PRIVACY_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Do not modify any file except the implementation plan.

---

# 2. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Confirm the only planned modification in this gate is:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Preserve unrelated prompt archives.

---

# 3. Correction A — Date Badge Must Be Drawn BEFORE JPEG Encoding

The current corrected plan still contains an invalid render sequence in which the output JPEG is encoded/file-sized before the date badge is drawn.

That is wrong.

The actual upload candidate must be the **final visual output**, including the optional date badge.

Therefore:

- date badge must be rendered into the bitmap **before** JPEG compression;
- JPEG encoding must happen only after the bitmap contains its final visual state;
- `File.length()` must measure the actual file that would later be uploaded;
- any pair-level retry must re-render/re-encode both outputs completely for that attempt.

This matters because the date badge changes pixels, compressibility, and final file size.

---

# 4. Required Final Render Attempt Sequence

Correct the plan so each pair attempt follows this sequence:

## Reference side

1. create/decode/render the Reference bitmap at the current target dimensions
2. if date overlay enabled and Reference date string exists, draw the Reference date badge into that bitmap
3. encode bitmap to the candidate Reference JPEG using the current JPEG quality
4. recycle/release the Reference bitmap

## Capture side

5. create/decode/render the Capture bitmap at the same target dimensions
6. if date overlay enabled and Capture date string exists, draw the Capture date badge into that bitmap
7. encode bitmap to the candidate Capture JPEG using the same JPEG quality
8. recycle/release the Capture bitmap

## Pair validation

9. inspect both final encoded candidate files with `File.length()`
10. if both <=20MiB, attempt succeeds
11. otherwise advance pair-level quality/dimension strategy and regenerate both outputs on the next attempt

The date badge must never be appended/drawn after JPEG encoding.

Do not introduce a JPEG re-open/re-encode just for the badge.

---

# 5. Pair-Level Retry Semantics

The plan must explicitly preserve:

- one shared target dimension pair
- one shared quality per attempt
- Reference and Capture rendered sequentially
- both final candidate files checked only after both exist
- if either fails size limit, neither candidate is accepted
- next attempt regenerates both
- dimension reduction affects both sides identically
- quality resets according to the corrected strategy
- maximum attempt bound remains unchanged

No per-image independent acceptance.
No per-image independent dimension reduction.

---

# 6. Correction B — Frozen Fallback Pair With Incompatible Ratios Must Fail

The current fallback section says mismatched frozen dimensions can be downscaled to a weaker common size.

That is only safe if both frozen files have a compatible aspect ratio.

Frozen files are already the visual source of truth.

Therefore the fallback renderer must never:

- crop them
- stretch them
- letterbox them
- invent a crop transform
- distort one image to match the other.

If their aspect ratios are incompatible beyond a defined tolerance, there is no valid way to create two equal-dimension images while preserving exact frozen visual composition.

In that case:

`WackelbildPrintResult.Failure(PERMANENT_NO_VALID_SOURCE)`

must be returned.

---

# 7. Required Fallback Ratio Logic

Correct the plan to define:

1. Decode/read actual dimensions for `reference.jpg` and `capture.jpg`
2. Compute `referenceRatio` and `captureRatio`
3. Compare them using the same Wackelbild ratio-compatibility concept used elsewhere, or a separately justified fallback tolerance if needed
4. If ratios are incompatible:
   - hard failure
   - no crop
   - no stretch
   - no letterbox
5. If ratios are compatible:
   - determine a common no-upscale target size from the weaker frozen source
   - preserve the agreed session/frozen aspect ratio
   - render both to identical target dimensions
   - draw optional date badges
   - fresh JPEG encode
   - validate file size

Do not assume current sessions always contain exactly equal frozen dimensions without checking.

---

# 8. Fallback Equal/Compatible-Dimension Cases

The plan should distinguish:

## Case A — identical dimensions

Use those dimensions directly unless later pair-size enforcement requires a shared downscale.

## Case B — different dimensions but compatible ratio

Choose the highest common no-upscale target dimensions.

Both sources may be downscaled.
Neither may be upscaled.
No crop.

## Case C — incompatible ratio

Permanent failure.

This logic must be deterministic.

---

# 9. Fresh Re-Encode Still Mandatory

Preserve Gate 5B privacy decisions:

Even in fallback:

- never byte-copy `reference.jpg`
- never byte-copy `capture.jpg`
- always decode and fresh-encode
- date badge is drawn before encoding
- sensitive EXIF/GPS is not copied

This is especially mandatory for `capture.jpg`, which may contain GPS EXIF.

---

# 10. Update All Affected Plan Sections

Search the full implementation plan and correct every place where either stale sequence appears.

At minimum inspect/update:

- Block-5 architecture sequence
- WackelbildPrintRenderer flow
- pair-level <=20MiB section
- date badge rendering section
- fallback section
- typed result/failure model
- implementation block stop criteria
- tests
- risk register
- Definition of Done / acceptance criteria
- any pseudo-code or numbered sequence

There must be no remaining wording implying:

- badge drawn after encoding
- file size checked before badge
- fallback ratio mismatch can be solved by generic downscale alone

---

# 11. Tests Required by the Corrected Plan

Update the planned tests to include:

## Date-badge/file-size integration

A test must prove that:

- date ON changes the candidate JPEG before file-size validation
- size limit is checked on the final badge-containing JPEG
- if the badge causes either file to exceed the limit, pair-level retry occurs
- both files are regenerated on the next pair attempt

Do not rely only on unit-testing the badge renderer separately.

## Fallback ratio compatibility

Add explicit tests:

### Compatible frozen pair

- different dimensions
- same/compatible ratio
- common no-upscale output produced
- identical final dimensions
- no crop/stretch

### Incompatible frozen pair

- clearly different ratios
- renderer returns `PERMANENT_NO_VALID_SOURCE`
- no output pair accepted
- no stretch/crop/letterbox

### Identical frozen dimensions

- remains valid
- re-encoded fresh
- optional badge handled before encode

---

# 12. Preserve All Other Gate-5B Decisions

Do not reopen or change:

- 3-method `ShareImageRenderer` visibility widening
- no `prepareHqCaptureForSbs`
- no planned legacy-session support
- current 9°/6° UI reality
- no `reference-source-original` direct rendering
- sequential Reference/Capture bitmap handling
- no arbitrary 150MB heap cap
- 2% capture-ratio tolerance unless the current plan already changed it
- output-relative bitmap badge geometry
- Reference genuine-scale proof
- Block-5/Block-6 temp-file boundary
- typed `Success(pair, usedFallback)` / `Failure(reason)`
- typed `referenceFile` / `captureFile`
- expanded privacy tests
- persisted-file immutability checks
- pair-level 92/85 quality strategy
- 0.85 dimension factor
- 4 dimension levels / max 8 attempts

This gate corrects only the two final logic errors.

---

# 13. Verification

After editing the plan:

1. `git diff --check`
2. `git diff -- docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
3. `git status --short`

Then re-read the full plan and verify:

- date badge always precedes JPEG encoding
- file-size validation always uses final visual JPEGs
- both pair files regenerated together on retry
- frozen incompatible-ratio fallback hard-fails
- no crop/stretch/letterbox fallback
- no unrelated plan changes
- no code changed
- no API/network call occurred

---

# 14. Required Final Response

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. File Modified

- exact path
- before/after line count
- confirm no other file changed

## 3. Correction A — Final-Pixel-Before-Encoding Order

Explain:

- prior invalid order
- corrected exact sequence
- sections updated

## 4. Correction B — Frozen Fallback Ratio Compatibility

Explain:

- prior unsafe fallback assumption
- corrected compatible/incompatible rules
- permanent-failure case
- sections updated

## 5. Test Plan Changes

List:

- date-badge/file-size integration tests
- compatible fallback ratio test
- incompatible fallback ratio hard-failure test
- identical frozen pair test

## 6. Architecture Preserved

Confirm all Gate-5B decisions listed in §12 remain unchanged.

## 7. Remaining Open Decisions

If none:

`None`

If anything genuinely remains unresolved, list it and explain why.

## 8. Verification

Report:

- `git diff --check`
- final `git status --short`
- exact diff scope
- Gradle/tests run or not run
- no API call
- no production-code change

## 9. Gate Result

Choose exactly one:

- **BLOCK 5C COMPLETE — PLAN READY FOR BLOCK 5 IMPLEMENTATION**
- **BLOCK 5C BLOCKED — TECHNICAL DECISION REQUIRED**

Then STOP.

Do not implement Block 5.

---

# Final Rule

One file only.

No code.

Correct exactly:

1. badge-before-encode/file-size-check
2. hard failure for incompatible frozen fallback ratios

After this gate, the next prompt may authorize Block 5 implementation.
