# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5E: RATIO-PARITY SURGICAL FIX — IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

Block 5 has already been implemented. Final review then found one contract-breaking issue in the ratio-tolerance logic.

Block 5E analysis has now completed and established the exact surgical fix.

This prompt authorizes **Block 5E implementation only**.

Do not begin Block 6.
Do not change camera/storage architecture.
Do not change UI.
Do not change network/API code.
Do not refactor unrelated renderer code.

The only goal is to eliminate every path where a genuinely different source aspect ratio can be accepted and then silently stretched into the common output dimensions.

---

# 1. Authoritative Inputs

Read before editing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/SESSION_ORIGINALS_V1.md`
- `docs/SESSION_ORIGINALS_PRIVACY_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation:

- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt`
- `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
- `app/src/test/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolverTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRendererInstrumentedTest.kt`

Read-only verification only if needed:

- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `CameraScreen.kt`
- `SessionStorage.kt`

If current repository state materially differs from the completed Block 5E analysis, STOP and report before editing.

---

# 2. Repository Baseline

Before modification record:

- branch
- HEAD
- `git status --short`

Preserve all existing Block-5 uncommitted changes and prompt archives.

Do not touch unrelated files.

---

# 3. Exact Authorized File Scope

Modify exactly these six files:

1. `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt`
2. `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
3. `app/src/test/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolverTest.kt`
4. `app/src/androidTest/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRendererInstrumentedTest.kt`
5. `docs/IMPLEMENTATION_NOTES.md`
6. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

No seventh file may be changed.

If another file becomes necessary, STOP and report why.

---

# 4. Root Cause to Fix

The current implementation uses:

- `CAPTURE_RATIO_TOLERANCE = 0.02f`
- `FALLBACK_RATIO_TOLERANCE = 0.02f`

That is too permissive.

A source such as:

- `1920×1088`

can currently be accepted against:

- `1920×1080`

even though both are real decoded JPEG pixel dimensions.

Once accepted, current scaling calls can force that source into exact target dimensions and introduce non-uniform scaling/stretch.

This violates the product contract:

- no stretch
- no crop
- no letterbox
- no guessed normalization
- exact visible-content parity.

---

# 5. New Compatibility Rule

Replace the flat 2% constants with one dynamic tolerance derived from the expected integer viewport/reference dimensions:

```text
roundingToleranceFor(expectedW, expectedH)
    = 1f / min(expectedW, expectedH)
```

This represents the only proven legitimate discrepancy source:

- integer truncation/rounding of at most about one pixel when the viewport dimensions are calculated.

Do not retain `0.02f` as an active production fallback.

Do not add another arbitrary percentage.

---

# 6. WackelbildDimensionResolver.kt

Make the smallest possible change.

## Remove

- `CAPTURE_RATIO_TOLERANCE = 0.02f`
- `FALLBACK_RATIO_TOLERANCE = 0.02f`

if they are no longer used.

## Add

A pure helper equivalent to:

```text
roundingToleranceFor(expectedW, expectedH)
```

Requirements:

- validate positive dimensions
- compute `1f / min(expectedW, expectedH)`
- deterministic
- no Android dependency.

Keep `isRatioWithinTolerance(...)` itself structurally unchanged if possible.

Do not change dimension-resolution formulas unrelated to compatibility checking.

---

# 7. HQ Capture Guard

In `WackelbildPrintRenderer.tryRenderHq()`:

replace the flat tolerance argument with:

```text
WackelbildDimensionResolver.roundingToleranceFor(
    viewport.first,
    viewport.second
)
```

Do not change:

- direct uncropped Capture HQ path
- `decodeHqCapture`
- target-size logic
- fallback architecture.

The stricter guard must simply prevent an incompatible source from reaching the stretch-capable target-size decode path.

Expected behavior:

- exact same ratio → HQ allowed
- same ratio, different size → HQ allowed
- genuine near mismatch like `1920×1088` vs `1920×1080` → HQ rejected → fallback
- clear mismatch → fallback.

---

# 8. Frozen Fallback Guard

In `WackelbildDimensionResolver.resolveFallbackDimensions()`:

replace the flat fallback tolerance with:

```text
roundingToleranceFor(
    referenceDims.first,
    referenceDims.second
)
```

Rationale:

- `reference.jpg` represents the integer viewport dimensions
- `capture.jpg` is the actual frozen visual source being checked against it.

Keep existing Case A/B/C architecture:

### Case A
Identical dimensions → valid.

### Case B
Different dimensions but compatible under the new rounding-only tolerance → common no-upscale dimensions allowed.

### Case C
Outside tolerance → incompatible → permanent failure.

Do not introduce:

- crop
- stretch
- letterbox
- center-crop normalization
- codec-padding assumptions.

---

# 9. 1920×1088 Must Now Be Rejected

This case is mandatory.

For an expected 1920×1080 viewport:

```text
roundingTolerance = 1 / 1080 ≈ 0.000926
```

The ratio error for 1920×1088 vs 1920×1080 is materially larger.

Therefore:

- HQ Capture must not proceed
- the renderer must route to frozen fallback.

Do not preserve the existing test label/assumption that this is “benign encoder padding.”

That assumption is disproven.

---

# 10. Do Not Change Current Scaling Implementations

Do not modify:

- `ShareImageRenderer.decodeHqCapture()`
- `Bitmap.createScaledBitmap` fallback helper
- ReferenceRenderer
- any crop logic.

The fix is precondition-based:

**genuinely incompatible source ratios must never reach a code path that can non-uniformly scale them.**

This keeps scope minimal and regression risk low.

---

# 11. Unit Tests — WackelbildDimensionResolverTest

Update/add exact tests.

## Remove/correct stale test

The current test:

`isRatioWithinTolerance_benignEncoderPadding_isAcceptable`

must no longer assert that 1920×1088 vs 1920×1080 is valid.

Rename/rewrite it to assert rejection.

## Add/verify

### Exact compatible

- 1920×1080 vs 1920×1080 → accepted

### Same ratio, different size

- 1280×720 vs 1920×1080 → accepted

### Near mismatch

- 1920×1088 vs 1920×1080 → rejected

### Clear mismatch

- 4:3 vs 16:9 → rejected

### Dynamic tolerance

Verify:

- 1920×1080 → `1/1080`
- 1080×1920 → `1/1080`
- smaller viewport gets proportionally larger rounding tolerance
- larger viewport gets proportionally smaller tolerance

### Boundary

Create one case just inside/at the derived one-pixel-equivalent tolerance and one clearly outside.

### Frozen fallback

- exact same ratio, different dimensions → valid
- rounding-only compatible case → valid
- near mismatch → invalid
- clear mismatch → invalid.

### Output rounding independence

Confirm existing `makeEven`/rounding output behavior is unchanged by the new input-compatibility rule.

Do not weaken any unrelated dimension tests.

---

# 12. Instrumented Tests — WackelbildPrintRendererInstrumentedTest

Add a real decode/render regression case proving the current bug is closed.

Create a synthetic current-release session fixture with:

- valid Reference/HQ inputs
- Capture original ratio that is near but genuinely incompatible with viewport ratio
- e.g. an equivalent of `600×403` vs `600×400` if this keeps fixture size reasonable while remaining clearly outside the dynamic tolerance.

Test:

1. HQ Capture path is rejected
2. renderer routes to frozen fallback
3. frozen fallback remains valid only if frozen `reference.jpg` / `capture.jpg` ratios themselves satisfy the corrected compatibility rule
4. no non-uniformly stretched HQ Capture output is accepted.

Also add a fallback incompatible-ratio case if not already present:

- frozen Reference/Capture ratios outside the dynamic tolerance
- result = `Failure(PERMANENT_NO_VALID_SOURCE)`.

Do not add screenshot golden infrastructure.

---

# 13. EXIF Privacy Test Completion

In the existing `assertNoSensitiveExif()` helper/test coverage, add all currently inspectable identifier-sensitive tags found in the Block 5E analysis:

- `TAG_MAKER_NOTE`
- `TAG_BODY_SERIAL_NUMBER`
- `TAG_LENS_SERIAL_NUMBER`
- `TAG_IMAGE_UNIQUE_ID`
- `TAG_CAMERA_OWNER_NAME`

Keep existing checks for:

- GPS
- DateTimeOriginal
- DateTime
- Make
- Model
- Software
- Lens fields.

Use the current AndroidX `ExifInterface` API only.

No new metadata library.

No production privacy code change is required.

---

# 14. IMPLEMENTATION_NOTES.md

Correct only the Block-5 ratio-rule documentation.

Remove/replace references to:

- `CAPTURE_RATIO_TOLERANCE = 0.02f`
- `FALLBACK_RATIO_TOLERANCE = 0.02f`

Document instead:

- compatibility tolerance is derived dynamically as `1 / min(expectedWidth, expectedHeight)`
- this represents only integer viewport-rounding slack
- real decoded ratio mismatches such as 1920×1088 vs 1920×1080 are rejected
- HQ mismatch → frozen fallback
- frozen mismatch beyond rounding tolerance → permanent failure.

Do not rewrite unrelated Block-5 notes.

---

# 15. Implementation Plan Correction

Update only the directly affected Block-5 sections in:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Replace the finalized-but-now-disproven 2% rule with the new dynamic rounding tolerance.

Update all affected mentions in:

- Capture ratio guard
- fallback ratio guard
- constants/technical decisions
- tests
- risk register
- Definition of Done / acceptance criteria
- Block-5 sequence if necessary.

Explicitly remove any claim that 1920×1088-style differences are acceptable codec padding.

Do not reopen unrelated Gate-5B/5C decisions.

---

# 16. Architecture That Must Remain Unchanged

Do not change:

- 3-method ShareImageRenderer visibility widening
- direct uncropped Capture HQ design
- no `prepareHqCaptureForSbs`
- no `reference-source-original` path
- common-resolution logic
- no-upscale
- pair-level 92/85 algorithm
- 0.85 dimension step
- max 4 dimension levels / 8 attempts
- badge-before-encode
- sequential bitmap lifecycle
- metadata-clean fresh JPEGs
- temp-file creation boundary
- typed result/fallback model
- date badge renderer
- UI/ViewModel behavior
- Block-6 cleanup scope.

---

# 17. Files Explicitly Forbidden

Do not modify:

- `ShareImageRenderer.kt`
- `DateBadgeRenderer.kt`
- `WackelbildTempFileManager.kt`
- Wackelbild UI/ViewModel
- DateBadgeFormatter
- strings
- MainActivity/navigation
- sensor files
- CameraScreen
- CameraViewModel
- SessionStorage
- ReferenceRenderer
- ShareRenderConfig
- Gradle
- AndroidManifest
- network/API code.

No refactor.
No cleanup.
No rename.
No formatting churn.

---

# 18. Verification

After implementation run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. narrow `WackelbildPrintRendererInstrumentedTest` on `pixel2Api29`
4. existing `ShareImageRendererInstrumentedTest`
5. `./gradlew assembleDebug`
6. `./gradlew assembleRelease`
7. `git diff --check`
8. `git status --short`

Explicitly verify:

- 1920×1088-style near mismatch no longer passes
- valid exact-ratio sources still pass
- frozen incompatible ratio hard-fails
- all existing Block-5 tests remain green
- EXIF identifier coverage expanded
- exactly the six authorized files changed in Block 5E.

Do not suppress failures.

---

# 19. Physical-Device Revalidation

Still required after implementation.

On physical hardware later verify:

- normal real Capture session still takes HQ path where ratios genuinely match
- devices whose CameraX JPEG stream ratio differs materially now correctly fall back instead of stretching
- fallback visual result still matches frozen Compare image
- no regression to large-session memory/performance.

Do not change the new tolerance based only on emulator preference.

---

# 20. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified

List exactly the six authorized files.

Confirm no unauthorized file changed.

## 3. Ratio Rule Implementation

Report:

- old 2% constants removed
- new `roundingToleranceFor(...)`
- exact formula
- HQ call-site change
- fallback call-site change.

## 4. Stretch Loophole Closure

Explain how incompatible sources are now prevented from reaching stretch-capable scaling calls.

## 5. Test Changes

List:

- stale 1920×1088 test corrected
- exact-ratio tests
- dynamic tolerance tests
- fallback ratio tests
- instrumented near-mismatch routing test
- EXIF tag additions.

## 6. Documentation Updates

Report exact Block-5 note/plan corrections.

## 7. Regression Safety

Confirm no change to:

- ShareImageRenderer
- date badge
- pair-size loop
- temp files
- camera/storage
- UI
- network.

## 8. Verification

Report exact commands/results:

- unit tests
- AndroidTest compile
- renderer instrumentation
- Share Image regression tests
- assembleDebug
- assembleRelease
- diff check
- final status.

## 9. Physical-Device Status

State what remains pending.

## 10. Diff Scope

Confirm exactly six files changed.

## 11. Gate Result

Choose exactly one:

- **BLOCK 5E COMPLETE — RATIO PARITY FIX READY FOR REVIEW**
- **BLOCK 5E INCOMPLETE — USER DECISION REQUIRED**

Then STOP.

Do not begin Block 6.

---

# Final Rule

Fix exactly one production issue:

**Replace permissive 2% aspect-ratio compatibility with rounding-only dynamic tolerance so genuinely different source ratios are never silently stretched.**

The secondary test-only addition is the missing EXIF identifier coverage.

No other behavior changes.
