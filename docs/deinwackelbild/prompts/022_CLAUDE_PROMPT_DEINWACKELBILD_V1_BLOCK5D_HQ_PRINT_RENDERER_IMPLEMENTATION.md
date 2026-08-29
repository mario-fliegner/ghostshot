# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5D: HQ / PRINT TWO-FILE RENDERER — IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Blocks 1–4 are complete and committed.

Block 5A analysis plus Gates 5B/5C corrected and finalized the Block-5 architecture. The current authoritative implementation basis is now:

- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` **after Gate 5C**
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`

This prompt authorizes **Implementation Block 5 only**.

Do not begin Block 6.
Do not add network/API/Custom Tabs.
Do not modify Wackelbild UI.
Do not add permissions/dependencies.
Do not contact DeinWackelbild.

Implement exactly the corrected Block-5 plan and nothing else.

---

# 1. Authoritative Inputs

Read before editing:

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

Inspect current code:

- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceRenderer.kt`
- current capture save pipeline
- current image renderer tests/instrumentation tests.

If current repository state materially differs from the finalized plan, STOP and report before editing.

---

# 2. Repository Baseline

Before modification, record:

- branch
- HEAD
- `git status --short`

Expected state:

- Gate 5B/5C plan file may still be uncommitted
- prompt archive files may be untracked
- no unrelated production-code modifications.

Preserve all unrelated working-tree state.

---

# 3. Exact Block-5 Scope

The Block-5 implementation must remain limited to the finalized plan.

Expected production files:

1. `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
2. `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt`
3. `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`
4. `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDateBadgeRenderer.kt`
5. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
6. corresponding finalized unit/instrumentation tests
7. `docs/IMPLEMENTATION_NOTES.md`

Use the corrected plan's exact file names if they differ slightly from this list.

Do not add extra abstractions merely for cleanliness.

If another production file is required, STOP and report why before expanding scope.

---

# 4. ShareImageRenderer — Visibility Widening Only

Modify `ShareImageRenderer.kt` only as approved.

Widen exactly these methods:

- `decodeHqCapture`
- `renderHqReference`
- `decodeReferenceFallback`

Change:

- `private` → `internal`

Do not change:

- method bodies
- signatures
- parameters
- return types
- existing callers
- Share Image behavior.

Do not widen:

- `prepareHqCaptureForSbs`

Do not refactor Share Image code.

Existing Share Image tests must pass unchanged.

---

# 5. Capture HQ Contract

Capture HQ uses:

`capture-original.jpg`

directly, uncropped.

Do not use:

- `prepareHqCaptureForSbs`
- center crop
- viewport crop
- stretch
- letterbox.

Before HQ use, compare:

- actual EXIF-oriented Capture original ratio
- metadata viewport/session ratio.

Use the corrected plan's named constant:

`CAPTURE_RATIO_TOLERANCE = 0.02f`

with:

`relativeError = abs(actualRatio - expectedRatio) / expectedRatio`

If error > tolerance:

- HQ Capture path is rejected
- renderer routes to frozen-pair fallback
- no guessed transform.

At or inside tolerance:

- direct uncropped HQ downsample/render is allowed.

Add exact boundary tests.

---

# 6. Reference HQ Contract

Reference HQ uses:

- `reference-original.jpg`
- overlay transform metadata
- `ReferenceRenderer.render()`

Do not use:

- `reference-source-original.<ext>`
- HEIC/HEIF/AVIF/raw direct decode
- new orientation pipeline.

Reuse existing HQ geometry.

Reference genuine-source scale must follow the corrected plan proof:

- display mode Fill/Fit
- overlay scale determines sampling density
- offset changes visible coverage, not sampling density.

Tests must cover:

- positive/negative X offsets
- positive/negative Y offsets
- overlay scale >1
- supported display modes.

No existing ReferenceRenderer behavior may change.

---

# 7. Common Genuine Resolution

Implement `WackelbildDimensionResolver` exactly per the corrected plan.

Inputs must be sufficient to derive:

- viewport width/height
- Capture original oriented dimensions
- Reference original dimensions
- Reference display mode
- Reference overlay scale
- API limits.

Compute:

- Capture genuine scale
- Reference genuine scale
- API side scale
- API 80MP scale

Final common scale:

`min(all allowed scales)`

No `.coerceAtLeast(1f)`.

Sub-viewport output is allowed.

No source may be upscaled.

Use the corrected plan's exact rounding/even-dimension rule.

Both outputs must share one immutable target-dimensions object.

Hard limits:

- longest side <= 16,000
- total pixels <= 80,000,000.

Do not inherit Share Image's 3840px cap.

Do not add an arbitrary heap-MB cap.

---

# 8. Memory Discipline

Use the corrected sequential strategy.

For every attempt:

## Reference
- decode/render Reference bitmap at target dimensions
- draw optional Reference badge
- encode
- recycle/release

## Capture
- decode/render Capture bitmap at same target dimensions
- draw optional Capture badge
- encode
- recycle/release

Only after both encoded files exist:

- compare both final file sizes.

Never hold both final full-resolution bitmaps simultaneously unless an unavoidable current API forces it.

Do not use giant encoded `ByteArray` buffers.

Catch `OutOfMemoryError` at the renderer boundary:

- HQ OOM → route to frozen fallback
- fallback OOM → permanent preparation failure.

Do not invent an OOM-specific resolution retry ladder.

---

# 9. Bitmap Date Badge

Create the bitmap-side badge renderer per the corrected plan.

It receives:

- final output dimensions
- already-formatted date string
- bitmap/canvas.

It must not:

- read metadata
- format dates
- access ViewModel
- access Compose dp/sp density.

Use output-short-edge-relative ratios from the corrected plan.

Style:

- `SameViewAppSurface`
- white anti-aliased text
- bottom-right
- no shadow
- no border
- no title
- no location
- no branding.

Use exact finalized ratios for:

- text size
- horizontal padding
- vertical padding
- corner radius
- image-edge margin.

If date string is null:

- draw nothing.

Block-4 preview code remains untouched.

---

# 10. Final-Pixels-Before-Encoding Rule

This is mandatory.

Every attempt must render the badge **before** JPEG encoding.

Exact order per side:

1. render/decode bitmap
2. draw optional badge
3. compress to candidate JPEG
4. recycle bitmap

Only the final badge-containing candidate files may be measured for the 20 MiB limit.

Do not:

- encode first
- append badge later
- reopen JPEG solely to add the badge.

---

# 11. Pair-Level ≤20 MiB Algorithm

Implement exactly the corrected bounded pair algorithm.

Per dimension level:

1. quality 92
2. quality 85

If either final candidate file exceeds 20 MiB after quality 85:

- reduce BOTH target dimensions by the corrected plan's factor `0.85f`
- derive one new common target dimensions object
- restart both sides at quality 92.

Bounds:

- max 4 dimension levels
- max 8 pair attempts.

Every retry regenerates both files.

Never accept one side independently.

Never resize one side independently.

Do not use quality 50.

Do not use unbounded loops.

Use `File.length()`.

---

# 12. Frozen-Pair Fallback

Fallback visual sources:

- `reference.jpg`
- `capture.jpg`

Never byte-copy them.

Always:

- decode
- render/downscale if allowed
- draw optional badge
- fresh JPEG encode.

This is mandatory because `capture.jpg` may contain GPS EXIF.

## Fallback ratio cases

### Case A — identical dimensions

Use them directly as target dimensions unless pair-size loop later downscales both.

### Case B — different dimensions, compatible ratios

Use:

`FALLBACK_RATIO_TOLERANCE = 0.02f`

or the exact finalized plan value.

Choose highest common no-upscale target dimensions.

No crop.
No stretch.
No letterbox.

### Case C — incompatible ratios

Return:

`Failure(PERMANENT_NO_VALID_SOURCE)`

No output pair may be accepted.

No guessed transform.

No extra fallback.

---

# 13. HQ-to-Fallback Rules

Route HQ → frozen fallback on approved HQ-unusable conditions, including:

- missing/corrupt original
- missing/unusable overlay metadata
- Capture ratio guard failure
- HQ decode/render exception
- HQ OOM
- other finalized plan HQ precondition failures.

Fallback success returns:

`Success(pair, usedFallback = true)`

HQ success returns:

`Success(pair, usedFallback = false)`

Fallback failure returns typed `Failure`.

A successful fallback is never represented as an error enum.

---

# 14. Pair Result / Mapping

Use explicit semantic fields:

- `referenceFile`
- `captureFile`

Do not expose only:

- `imageOne`
- `imageTwo`

inside the renderer result.

Network/API `one/two` mapping belongs to a later block.

Do not claim partner slot mapping here.

Add a test preventing accidental Reference/Capture reversal.

---

# 15. Temp File Manager — Creation Side Only

Implement only the Block-5 creation side of:

`WackelbildTempFileManager`

Use:

`context.cacheDir/wackelbild/<operationId>/`

Provide:

- operation directory
- candidate Reference file
- candidate Capture file
- deterministic replace/reuse behavior needed by the pair retry loop.

Do not implement:

- stale sweep
- screen-entry cleanup
- cleanup after upload
- cancellation cleanup
- final-error cleanup
- `onCleared()` cleanup
- ViewModel lifecycle cleanup.

Those are Block 6.

Test-only teardown cleanup is allowed.

---

# 16. JPEG Privacy

Every output must be a fresh JPEG.

Do not write EXIF.

Do not copy:

- GPS
- DateTimeOriginal
- DateTime
- Make
- Model
- Software
- Lens data
- serial identifiers
- MakerNote
- session ID
- source filename metadata
- XMP/IPTC personal metadata.

Pixel orientation must be correct without relying on EXIF orientation.

Color-profile data is not considered personal metadata, but do not introduce a color-management subsystem.

Do not regress existing color handling deliberately.

---

# 17. Persisted Session Immutability

Block 5 must never modify:

- `reference.jpg`
- `capture.jpg`
- `reference-original.jpg`
- `capture-original.jpg`
- `metadata.json`
- `reference-source-original.<ext>` if present.

Instrumented tests must hash/read bytes before and after rendering and assert equality.

No production Block-5 code may open session files for write.

---

# 18. Unit Tests — Dimension Resolver

Create comprehensive tests for:

- Capture weaker source
- Reference weaker source
- API side limit
- API pixel limit
- no-upscale
- common scale <1
- identical output dimensions
- deterministic rounding
- session-ratio preservation tolerance
- Capture ratio guard pass below threshold
- boundary threshold
- ratio guard failure above threshold
- degenerate dimensions fail
- Reference offset X/Y do not affect sampling-density scale
- overlay scale affects Reference genuine scale
- supported display modes.

Do not weaken existing tests.

---

# 19. Unit Tests — Date Badge Renderer

Test exact deterministic geometry:

- short-edge-relative text size
- edge margin
- horizontal padding
- vertical padding
- corner radius
- bottom-right placement
- same ratios for Portrait/Landscape
- null date → no draw
- correct CI background
- white text
- no shadow/border behavior.

Do not create screenshot-golden infrastructure.

---

# 20. Unit Tests — Print Renderer

Test:

- HQ success
- fallback success with `usedFallback=true`
- permanent fallback failure
- Reference/Capture typed mapping
- pair dimensions identical
- distinct content
- no SBS composite
- sequential attempt semantics where observable
- pair-quality order 92 then 85
- dimension reduction factor
- max 8 attempts
- both regenerated after either exceeds size
- date badge included before file-size validation
- badge-induced overage triggers pair retry
- no per-side independent acceptance
- compatible frozen fallback dimensions
- incompatible frozen fallback hard failure.

Use injected seams where necessary to make file-size/retry behavior deterministic without creating giant real 20MiB fixture files.

Do not add broad architecture solely for tests.

---

# 21. Instrumented Renderer Tests

Create a focused Wackelbild renderer instrumentation test.

Use real Android bitmap/JPEG/Exif stack.

Cover:

### Reference parity
- representative Fill/Fit/current display modes
- offsets
- overlay scale
- normalized composition/parity against expected frozen result.

### Capture parity
- normal direct-uncropped case
- ratio mismatch routes to fallback
- never crop/stretch/letterbox.

### Output pair
- valid JPEGs
- exactly two files
- identical dimensions
- independent images
- correct semantic mapping.

### Privacy
Assert absence where inspectable:
- GPS
- DateTimeOriginal
- DateTime
- Make
- Model
- Software
- Lens*
- serial*
- MakerNote
- orientation dependency.

### Immutability
Before/after hashes for all session files.

### Fallback
Use current-release fixture with intentionally broken HQ source.

Do not add legacy v2-v4 fixtures.

---

# 22. Existing Share Image Regression Verification

Re-run existing unmodified Share Image tests.

At minimum:

- existing `ShareImageRendererInstrumentedTest`
- existing `ShareRenderConfigTest`
- relevant Share Image unit tests.

The only production change in `ShareImageRenderer.kt` is visibility.

If an existing Share Image test breaks, investigate before proceeding.

Do not modify existing Share Image behavior to satisfy Wackelbild.

---

# 23. Documentation

Update only:

`docs/IMPLEMENTATION_NOTES.md`

Add a concise Block-5 entry covering:

- two-file print renderer
- Reference HQ reuse
- Capture direct uncropped HQ path + 2% guard
- common genuine resolution
- no-upscale
- API limits
- pair-level 20MiB strategy
- sequential bitmap lifecycle
- bitmap date badge
- fresh metadata-clean JPEG output
- frozen-pair fallback
- no legacy-specific scope
- creation-only temp-file manager
- Block 6 cleanup still pending.

Do not rewrite prior block entries.

Do not modify the feature spec or implementation plan in this implementation gate unless the actual implementation uncovers a new contradiction. If that happens, STOP and report.

---

# 24. Files Explicitly Forbidden

Do not modify:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- Block-4 date formatter
- strings
- MainActivity/navigation
- CompareScreen
- TiltProvider
- TiltHysteresisStateMachine
- CompassProvider
- SessionStorage
- CameraScreen
- CameraViewModel
- Slider/SideBySide render strategies
- CaptionRenderer
- Gradle
- AndroidManifest
- network/API files
- partner key config
- BuildConfig
- Custom Tabs
- WorkManager/services
- release/privacy policy docs.

No unrelated refactor.
No cleanup.
No rename.
No format churn.

---

# 25. Verification Commands

After implementation run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. narrow `WackelbildPrintRendererInstrumentedTest` on `pixel2Api29`
4. existing `ShareImageRendererInstrumentedTest`
5. existing relevant Share Image unit tests
6. `./gradlew assembleDebug`
7. `./gradlew assembleRelease`
8. `git diff --check`
9. `git status --short`

Do not suppress failures.
Do not add baselines.
Do not disable tests.

If unrelated pre-existing Managed Device failures appear, establish baseline evidence before calling them pre-existing.

---

# 26. Physical-Device Validation

Block 5 is not fully validated until real-device/manual output inspection is done.

After implementation, report physical validation as still required unless actually performed.

Manual checks:

1. Portrait real session
2. Landscape real session
3. date OFF
4. date ON
5. compare Reference output visually with frozen `reference.jpg`
6. compare Capture output visually with frozen `capture.jpg`
7. inspect final dimensions
8. inspect file sizes
9. inspect EXIF/GPS absence
10. force HQ failure and verify fallback output
11. verify all session/original files remain unchanged
12. use a large real session to observe memory/performance
13. inspect bitmap date badge relative size/position
14. spot-check saturated/wide-gamut color appearance.

No real DeinWackelbild API/network call is required in Block 5.

---

# 27. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List every file changed.

Confirm no unauthorized file changed.

## 3. ShareImageRenderer Reuse

Confirm exactly 3 visibility widenings and zero logic/signature/caller changes.

## 4. Dimension / Parity Implementation

Cover:

- Capture ratio guard
- direct uncropped Capture HQ
- Reference HQ path
- genuine-scale formulas
- no-upscale
- API limits
- rounding.

## 5. Render / Encode / File-Size Flow

State exact final sequence:

`render -> optional badge -> encode -> recycle`

for Reference and Capture, followed by pair file-size validation.

Confirm both sides regenerate together on retry.

## 6. Date Badge Renderer

Report exact output-relative ratios and styling.

## 7. Fallback

Report:

- identical frozen pair case
- compatible ratio case
- incompatible ratio hard failure
- fresh re-encode
- no crop/stretch/letterbox.

## 8. Privacy / Immutability

Report metadata assertions and persisted-file before/after checks.

## 9. Temp File Boundary

Confirm creation side only and list cleanup work still deferred to Block 6.

## 10. Tests / Verification

Report exact commands/results:

- unit tests
- AndroidTest compile
- Wackelbild renderer instrumentation
- Share Image regression tests
- assembleDebug
- assembleRelease
- diff check
- final status.

## 11. Physical-Device Status

State exactly what remains to be validated manually.

## 12. Diff Scope

Confirm exact scope and no unrelated changes.

## 13. Remaining Work

State only:

- Block 6 temp-file cleanup/cancellation/sweep
- later network/API integration
- physical output validation if pending.

## 14. Gate Result

Choose exactly one:

- **BLOCK 5 COMPLETE — READY FOR REVIEW**
- **BLOCK 5 INCOMPLETE — USER DECISION REQUIRED**

Then STOP.

Do not begin Block 6.

---

# Final Rule

Implement exactly the finalized Block 5 architecture.

No UI changes.
No network.
No manifest/Gradle change.
No legacy-session project.
No reference-source-original direct path.
No crop/stretch/letterbox approximation.
No hidden fallback behavior.

If exact parity cannot be maintained, fail rather than guess.
