# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5A: HQ / PRINT TWO-FILE RENDERER — ANALYSIS & SCOPE CONFIRMATION ONLY (REVISED)

## Role

You are working in the existing SameView Android repository.

Blocks 1–4 are complete and committed. Block 4 was physically validated on-device after the responsive-preview/date-badge correction.

We now start **Block 5: the local HQ / print two-file renderer**.

This is one of the highest-risk blocks in the feature because it governs:

- exact crop/alignment parity,
- highest genuinely available common source quality,
- no-upscale guarantees,
- output dimensions,
- JPEG privacy,
- memory/OOM behavior,
- date rendering into print output,
- fallback semantics.

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files.
Do not output implementation code.
Do not begin Block 6.
Do not begin network/API/Custom Tabs.
Do not contact DeinWackelbild.de.
Do not add permissions/dependencies.
Do not create test images outside normal local test fixtures.

The goal is to re-derive the exact Block-5 implementation scope against the CURRENT repository after Blocks 1–4, and to surface any remaining plan/spec conflict before code is authorized.

---

# 1. Authoritative Sources — Read First

Read fully and reconcile:

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

Then inspect the CURRENT code directly, especially:

- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- current camera capture/save pipeline relevant to `capture.jpg` / `capture-original.jpg`
- current `WackelbildViewModel.kt`
- current `DateBadgeFormatter.kt`
- current renderer/unit/instrumentation tests.

Authority order:

1. `DEINWACKELBILD_INTEGRATION_V1.md`
2. `CLAUDE_PROJECT_INSTRUCTION.md`
3. other current authoritative feature/storage/rendering specs
4. `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
5. current code

If the plan conflicts with the feature spec or authoritative storage/rendering contract, identify it explicitly. Do not silently implement the plan.

---

# 2. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Confirm Block 4 is committed.

Preserve unrelated untracked prompt directories.

---

# 3. Critical Product-Scope Clarification — No Planned Legacy-Session Support

This is a fixed product decision for DeinWackelbild V1:

**Do not design this feature around pre-release legacy v2/v3/v4 sessions.**

The Android app was first publicly released with session-original storage already present.

Therefore:

- do not add V1-specific logic solely to support old pre-release sessions lacking `capture-original.jpg`;
- do not add dedicated v2/v3/v4 DeinWackelbild fixtures/tests;
- do not describe legacy-session support as a product requirement;
- do not make legacy compatibility a Block-5 stop criterion.

However, the existing approved fallback still remains mandatory:

> If HQ/original reconstruction for a current normal session is unexpectedly unavailable or fails, but persisted `reference.jpg` and `capture.jpg` are usable, the feature must fall back to freshly generated transfer JPEGs from those frozen compare files and later show the approved HQ-unavailable warning.

So distinguish strictly:

- **not supporting legacy as a planned user case**
vs.
- **having a robust fallback when HQ sources are unexpectedly unusable**.

If generic implementation naturally happens to work on an old session, that is fine, but do not broaden scope to guarantee/test it.

---

# 4. Block-5 Output Contract

Block 5 must create the local print pair that later gets uploaded:

- `image_one.jpg`
- `image_two.jpg`

Confirm the exact semantic mapping from current product/API specs. Do not infer it from filenames.

The mapping must be deterministic and tested.

The two outputs must:

- be independent JPEG files, not a composite;
- have exactly identical pixel dimensions;
- have exactly identical aspect ratio;
- preserve the same visible composition/crop/alignment as persisted:
  - `reference.jpg`
  - `capture.jpg`;
- use the highest genuinely available **common** source resolution;
- never upscale either side beyond genuine visible-source quality;
- contain the optional date badge only when enabled;
- contain no hidden personal/session metadata;
- never modify persisted session/original files.

The rendered pair is local/offline.

---

# 5. Important Distinction: DeinWackelbild Limits vs Existing Share-Image 3840px Cap

`SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md` has an existing **3840 px longest-edge cap** for the Share Image feature.

That cap exists for the Share Image composite pipeline and must **not automatically be inherited** by DeinWackelbild.

For DeinWackelbild, the approved external hard ceilings are:

- longest side ≤ 16,000 px
- total pixels ≤ 80,000,000
- file size ≤ 20 MiB each

But those are **upper safety/API limits, not targets**.

Analyze whether the Wackelbild renderer needs its own lower app-side memory-safety cap or adaptive downscaling rule.

Do not silently reuse 3840px merely because Share Image uses it.

Do not blindly target 16,000px/80MP either.

If a new app-side memory cap is necessary, derive and justify it from:

- actual source dimensions,
- minSdk/device constraints,
- current renderer architecture,
- peak bitmap memory,
- current OOM-handling precedent.

If this requires a product decision rather than a purely technical safety bound, mark it as a blocker instead of inventing one.

---

# 6. Reference Source — Lock the Correct V1 Source

For DeinWackelbild V1, re-verify and preserve the existing HQ reconstruction source:

- `reference-original.jpg`
- plus stored overlay transform
- through `ReferenceRenderer.render()`.

Do **not** switch to `reference-source-original.<ext>` in Block 5.

Although `reference-source-original.<ext>` is the byte-preserved raw source for newer sessions, the existing authoritative HQ Share spec explicitly treats direct use of it as a **future enhancement**, because it would require extra format/orientation handling.

Block 5 must not expand into:

- HEIC/HEIF/AVIF/raw-format reference decoding,
- direct raw reference-source usage,
- a new orientation pipeline for `reference-source-original`.

Re-verify:

- `reference-original.jpg` is already EXIF-oriented;
- it is the exact source compatible with existing `ReferenceRenderer` transform math;
- re-rendering at a larger viewport preserves the frozen visible composition.

If current code/spec contradicts this, report the conflict.

---

# 7. Reference HQ Reconstruction — Exact Evidence

Trace the complete current Reference HQ path.

Identify:

- exact source-file resolution;
- exact metadata fields needed;
- exact `ReferenceRenderer.render()` inputs;
- exact overlay display-mode handling;
- exact offset/scale math;
- current fallback behavior;
- exact methods in `ShareImageRenderer.kt` that Wackelbild needs.

Verify whether the corrected plan still requires widening exactly four methods from `private` to `internal`.

List names/signatures.

The allowed existing-file change must remain:

- visibility only;
- zero logic change;
- zero existing Share Image call-site change.

If more than visibility widening is required in `ShareImageRenderer.kt`, treat that as increased regression risk and explain why before scope approval.

Prove mathematically/code-wise why the new Reference output matches the frozen `reference.jpg` composition.

---

# 8. Capture Reconstruction — Re-open the Gate-3B Assumption

Do **not** assume the earlier Gate-3B capture conclusion is correct.

There is an important authoritative tension that must be resolved now:

- an earlier Wackelbild planning pass concluded that `capture-original.jpg` and `capture.jpg` effectively share the same ratio/content for direct downsample;
- `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md` explicitly states that `capture-original.jpg` may have a different sensor aspect ratio from the session viewport and may require viewport-ratio center-crop for HQ export.

Therefore re-trace the CURRENT capture pipeline from source:

- CameraX `ViewPort`
- `UseCaseGroup`
- ImageCapture output
- MediaStore output
- `capture-original.jpg`
- in-memory bitmap
- `capture.jpg`
- any crop/rotation step between them.

Answer conclusively:

1. Can `capture-original.jpg` differ in aspect ratio/content from frozen `capture.jpg`?
2. If yes, what exact crop transform reproduces `capture.jpg` at HQ?
3. Is center-crop to viewport ratio mathematically sufficient?
4. Does any stored crop offset exist, or is crop necessarily centered?
5. Can the existing Share Image `drawBitmapFill` / related helper be reused without changing its behavior?
6. If direct downsample is safe only under an exact ratio match, what ratio tolerance is used?

Hard contract:

- no stretching;
- no letterboxing;
- no guessed crop;
- exact visible-content parity with frozen `capture.jpg`.

If exact parity cannot be proven from persisted data, **BLOCK 5 MUST STOP as a product/technical blocker**. Do not implement an approximation.

---

# 9. Common Genuine Resolution — BOTH Sides

This calculation must use real visible-source quality on both sides.

## 9.1 Capture maximum genuine scale

Derive the maximum output scale that does not upscale pixels from the actual Capture source region that will be used after any required viewport crop.

Do not use raw sensor width/height naïvely if part of the sensor image is cropped away.

## 9.2 Reference maximum genuine scale

Derive from:

- source bitmap dimensions,
- `ReferenceRenderer` fill/fit/display mode,
- overlay scale,
- offsets,
- actual visible source region.

Do not use `reference-original.jpg` raw dimensions alone.

The value must represent the maximum target viewport scale for which every visible source pixel remains at ≤1:1 source sampling.

## 9.3 API scales

Also calculate:

- max-side scale from 16,000px;
- max-pixel scale from 80MP.

## 9.4 Final common scale

Use equivalent of:

`commonScale = min(captureGenuineScale, referenceGenuineScale, apiSideScale, apiPixelScale, any justified memorySafetyScale)`

No `.coerceAtLeast(1f)`.

If `commonScale < 1`, output may be smaller than the stored viewport.

That is preferable to upscaling.

Only invalid/non-positive/degenerate dimensions fail.

Define exact integer rounding/even-dimension strategy and prove:

- identical width/height for both images;
- no source is upscaled;
- session viewport aspect ratio preserved within deterministic integer tolerance;
- max side/pixel ceilings obeyed.

---

# 10. Memory/OOM — Must Be Concrete

Explicitly calculate:

- 80MP ARGB_8888 ≈ 320MB per bitmap before overhead;
- source decode bitmap memory;
- output bitmap memory;
- simultaneous source+output footprint;
- whether two outputs are ever held simultaneously.

Trace current Share Image HQ memory behavior.

Design the smallest safe Wackelbild strategy.

Preferred discipline:

- process Reference and Capture sequentially where possible;
- encode/write/recycle one before processing the other;
- avoid a full shared composite bitmap;
- avoid holding two full-size final output bitmaps at once.

But verify whether common-resolution calculation can happen from dimensions/metadata alone before decoding full images.

Also determine OOM policy:

- Does OOM during HQ reconstruction cause the approved HQ-unavailable fallback?
- Is there a defined lower-resolution retry from originals before falling back to viewport files?
- Does current product/plan already answer this?

Do not invent a resolution-step-down-on-OOM algorithm unless current plan/spec authorizes it.

If the plan is silent and this affects “highest suitable print quality,” flag it explicitly.

---

# 11. Block-5 / Block-6 Boundary — Use Current Plan

The current implementation plan says:

### Block 5
- visibility widening;
- dimension resolver;
- print renderer;
- bitmap-side date renderer;
- fallback detection;
- **`WackelbildTempFileManager` creation side**.

### Block 6
- complete temp-file cleanup paths;
- cancellation wiring;
- sweep-on-entry/process-leftover behavior;
- ViewModel cleanup integration.

Analyze the current plan and keep this boundary unless repository evidence requires correction.

Block 5 may therefore create the minimum temp-file abstraction necessary to:

- create an operation directory;
- create/write the pair outputs;
- expose produced file handles.

Block 5 must **not** yet implement:

- screen-entry stale sweep;
- cleanup-on-success upload;
- cleanup-on-cancel;
- cleanup-on-final-error orchestration;
- `onCleared()` cleanup;
- ViewModel cancellation wiring.

Those belong to Block 6.

If a test needs cleanup of its own test temp directory, that is test teardown, not product cleanup logic.

---

# 12. Pair-Level ≤20 MiB Enforcement — Reconcile Current Corrected Plan

There is known plan-history here.

An older plan version used:

- qualities 92/85/75/65/55/50
- 18 attempts
- large in-memory buffers.

Gate 3B explicitly corrected that architecture to a print-quality-first, pair-level strategy:

- high-quality encode values only (92 and 85);
- if either output exceeds 20MiB, downscale **both** images;
- restart encoding at high quality after each pair downscale;
- maximum four dimension levels;
- maximum eight pair attempts;
- write to temp files and inspect `File.length()`;
- avoid repeated giant in-memory `ByteArray`s.

During analysis, check the CURRENT `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`.

If it still contains the old 18-attempt/quality-50 algorithm despite Gate 3B's accepted correction, report this as **documentation drift that must be corrected before implementation**.

Do not silently choose between stale and corrected versions.

Determine whether the ≤20MiB loop belongs inside Block 5's renderer/output creation. Given Block 5 creates actual JPEG outputs, it likely does, but confirm from current plan.

If included, define exact pair-level algorithm and exact dimension downscale factor(s) from the corrected plan.

If the corrected plan does not lock those factors, identify the gap rather than inventing a hidden choice.

---

# 13. Fallback — Current-Release Sessions Only

Fallback is required when HQ/original reconstruction fails unexpectedly.

Preferred path:

- Reference: `reference-original.jpg` + `ReferenceRenderer`
- Capture: `capture-original.jpg` with exact crop/parity transform.

Fallback path:

- persisted `reference.jpg`
- persisted `capture.jpg`

Important privacy fact:

`capture.jpg` may contain GPS EXIF according to the current session-originals/storage contract.

Therefore **fallback transfer files must never directly byte-copy persisted `capture.jpg`**.

Even with date OFF:

- decode frozen Reference/Capture;
- resize/render to common target;
- fresh JPEG encode;
- write new temp files;
- no EXIF copied.

This same fresh-reencode discipline should apply to both pair files.

Date ON:

- draw date into new output bitmap only.

Fallback output must still be:

- identical dimensions;
- same visible crop;
- metadata-clean.

Do not add dedicated old-session support/tests.

---

# 14. Metadata Privacy vs Color Fidelity

The transfer JPEG privacy rule forbids personal/hidden metadata such as:

- GPS
- EXIF timestamps
- device/camera identifiers
- MakerNote
- session identifiers
- XMP/IPTC personal data.

But accurate print color also matters.

Analyze current Android encode behavior for:

- Bitmap color space;
- wide-gamut inputs;
- ICC profile behavior;
- final JPEG color compatibility.

Do not treat ICC/color-profile data as personal metadata automatically; the current privacy spec explicitly distinguishes harmless colorimetric profile information from personal metadata.

Determine whether current `Bitmap.compress()` output:

- preserves/embeds a color profile,
- normalizes to sRGB,
- or otherwise changes color space.

Do not add a color-management architecture in this block unless required for correctness.

But report any realistic print-color risk and whether it needs testing.

---

# 15. Date Badge Bitmap Rendering — Must Match Current Approved Preview

Block 4 is already implemented and approved.

Current preview badge appearance:

- corner radius 6dp
- horizontal padding 8dp
- vertical padding 4dp
- image-edge margin 8dp
- `MaterialTheme.typography.labelMedium`
- `SameViewAppSurface`
- white
- no shadow
- no border.

Do not refactor Block 4 into an old planned shared `DateBadgeGeometry` abstraction.

Do not change Block-4 Compose UI.

Create the smallest bitmap-side renderer that reproduces the same **relative visual character** at print resolution.

Lock exact bitmap scaling.

Do not leave “implementation-time decision.”

Analyze an exact reference basis, such as:

- derive all bitmap badge dimensions as fractions of output image short edge or viewport baseline;
- use a defined baseline mapping from the actual Block-4 visual proportions;
- clamp minimum/maximum where necessary only if justified.

Must define exact rules for:

- text size;
- horizontal padding;
- vertical padding;
- edge margin;
- corner radius;
- baseline/vertical centering;
- anti-aliasing.

The print badge must remain in the same relative bottom-right position on Reference and Capture.

No title/location/branding.

If Capture date is absent, do not invent one.

---

# 16. Output JPEG Encoding and Metadata

Fresh encoding is mandatory for both HQ and fallback transfer outputs.

Analyze exact encode path.

Requirements:

- JPEG;
- pixel orientation correct without relying on EXIF orientation;
- no copied EXIF;
- no GPS;
- no session metadata;
- no source filename/session id in metadata;
- no EXIF writer;
- no XMP/IPTC copy.

Identify exact current test precedent, preferably `ShareImageRendererInstrumentedTest`.

Plan metadata assertions beyond just GPS:

- GPS latitude/longitude;
- DateTimeOriginal;
- DateTime;
- Make;
- Model;
- Software;
- Lens;
- serial-number tags if supported by test API;
- MakerNote where inspectable;
- orientation tag absent/normal and not required.

Also verify output is a decodable JPEG.

---

# 17. Persisted-File Immutability — Must Be Proven

Before renderer execution, hash/check bytes for all existing relevant session files.

After renderer execution, assert unchanged:

- `reference.jpg`
- `capture.jpg`
- `reference-original.jpg`
- `capture-original.jpg`
- `metadata.json`

If `reference-source-original.<ext>` exists in the fixture, also assert unchanged even though it is not used by Block 5.

No production Block-5 code may open session files in write mode.

---

# 18. Deterministic Pair Mapping

Determine and lock:

- which semantic image is API slot/file `one`;
- which semantic image is API slot/file `two`.

The renderer result type must avoid accidental reversal later.

Prefer explicit typed fields even if temp filenames are generic, e.g.:

- `referenceFile`
- `captureFile`

with later upload mapping performed deliberately.

If product/API spec mandates `one = reference` and `two = capture` (or vice versa), state it.

Add a test preventing accidental reversal.

---

# 19. Minimal Production API

Analyze exact need for new production files.

Likely candidates:

- `image/wackelbild/WackelbildPrintRenderer.kt`
- `image/wackelbild/WackelbildDimensionResolver.kt`
- `image/wackelbild/WackelbildDateBadgeRenderer.kt`
- `ui/wackelbild/WackelbildTempFileManager.kt` — creation-only portion in Block 5

Do not create more abstractions unless needed.

Define:

- renderer input;
- sessionDir input;
- date strings input;
- `dateOverlayEnabled`;
- typed HQ/fallback result;
- typed failure causes;
- output file mapping;
- whether renderer internally determines HQ-vs-fallback or caller does;
- bitmap ownership/recycling;
- coroutine/dispatcher expectations.

Renderer must not know about:

- network;
- handoff token;
- partner key;
- checkout URL;
- Custom Tabs;
- ViewModel UI state.

If `WackelbildViewModel.kt` is not required until Block 6/later, explicitly exclude it.

---

# 20. ShareImageRenderer Reuse / Regression Safety

If Block 5 widens methods in `ShareImageRenderer.kt`:

- only `private` → `internal`;
- zero logic changes;
- zero parameter/signature changes unless absolutely unavoidable;
- zero existing caller changes;
- no cleanup/refactor.

Identify exact methods.

Re-run existing Share Image tests unmodified.

Block-5 approval requires confidence that:

- Standard Share Image remains byte/behavior-equivalent;
- Original Share Image remains behavior-equivalent;
- Slider/SbS remain untouched.

If Wackelbild would require modifying existing Share Image rendering logic, STOP and escalate scope/risk rather than doing it silently.

---

# 21. Comprehensive Test Plan

Plan exact unit/instrumentation tests.

## Dimension resolver

- Capture is weaker source.
- Reference visible-source density is weaker.
- API side cap limits.
- API 80MP cap limits.
- any app memory-safety cap limits, if approved.
- no upscale.
- common scale <1 allowed.
- identical output dimensions.
- deterministic rounding.
- aspect-ratio error tolerance.
- degenerate input fails.

## Reference parity

- synthetic transform cases:
  - FIT/FILL or current display modes;
  - offset X;
  - offset Y;
  - overlay scale;
- HQ output normalized composition matches frozen reference.
- same visible source region at higher output size.

Use existing parity methodology where possible.

## Capture parity

- real/current normal case.
- source aspect ratio differs from viewport case if repository confirms it can occur.
- output content matches frozen `capture.jpg`.
- never stretches.
- never letterboxes.
- mismatch/unsupported geometry fails rather than guessing.

## Two-file pair

- exactly two files.
- same width/height.
- distinct content.
- no SBS composite.
- deterministic Reference/Capture mapping.
- valid JPEG.

## Date badge

- OFF: no badge.
- ON Reference: correct text.
- ON Capture: correct text.
- Capture date absent: no invented Capture badge.
- exact bottom-right placement/scaling math.
- deterministic style constants.
- compare representative bitmap output against expected geometry, not screenshot-golden UI.

## Privacy

- output has no sensitive EXIF/GPS/device metadata.
- orientation applied in pixels.
- fresh encode.

## Immutability

- before/after hash/byte equality for persisted session files.

## Fallback

Current-release fixture with intentionally:
- missing/corrupt capture original;
- missing/corrupt reference original/overlay reconstruction path as technically possible;
- usable frozen pair → fallback result;
- unusable frozen pair → hard failure.

Do **not** add dedicated v2/v3/v4 fixtures.

## File size loop

If Block 5 owns it:
- one output >20MiB causes pair-level downscale;
- both outputs end at same reduced dimensions;
- each new dimension level restarts at high quality;
- bounded max attempts;
- no quality 50 path if corrected Gate-3B plan is authoritative;
- direct temp-file size measurement;
- no giant ByteArray accumulation.

## Memory

Do not intentionally OOM CI.
Test:
- sequential bitmap release/recycling where observable;
- no two-final-bitmap retention if design avoids it.
Reserve real stress check for physical device.

---

# 22. Real-Device / Manual Validation Plan

Block 5 must later be manually checked on a physical device with real sessions.

At minimum:

1. normal Portrait session;
2. normal Landscape session;
3. date OFF;
4. date ON;
5. visual compare Reference transfer output against `reference.jpg`;
6. visual compare Capture transfer output against `capture.jpg`;
7. inspect dimensions;
8. inspect file size;
9. inspect EXIF/GPS absence;
10. force HQ failure to exercise fallback;
11. verify originals/frozen files remain unchanged;
12. use the largest realistic session originals available to check memory/performance;
13. check printed-date badge relative size, not just presence;
14. check color appearance for an image with saturated/wide-gamut colors if a fixture is available.

Do not require a real DeinWackelbild network call in Block 5.

---

# 23. Exact File Scope — Determine, Do Not Guess

Produce the complete proposed Block-5 file list.

For every file state:

- Modify/Create
- exact responsibility
- why required
- risk.

At minimum explicitly evaluate:

- `ShareImageRenderer.kt`
- `WackelbildPrintRenderer.kt`
- `WackelbildDimensionResolver.kt`
- `WackelbildDateBadgeRenderer.kt`
- `WackelbildTempFileManager.kt` creation side
- corresponding unit/instrumentation tests
- `docs/IMPLEMENTATION_NOTES.md`

Explicitly exclude `WackelbildViewModel.kt` unless Block-5 implementation genuinely needs it.

No hidden file.

---

# 24. Explicitly Out of Scope

Do not include:

- WackelbildScreen UI changes;
- preview badge changes;
- tilt/swipe changes;
- threshold changes;
- navigation;
- strings;
- API/network;
- OkHttp;
- partner key;
- BuildConfig;
- AndroidManifest;
- Gradle dependency changes;
- Custom Tabs;
- WorkManager/service;
- upload state;
- checkout/order state;
- privacy-policy/Play Console work;
- Block-6 cleanup orchestration;
- legacy-session support project;
- `reference-source-original` direct rendering.

No unrelated refactor/cleanup/rename/format churn.

---

# 25. Documentation Drift Checks

Before proposing scope, explicitly inspect the CURRENT implementation plan for stale items caused by accepted later corrections.

At minimum check:

1. old `isWackelbildAvailable` plan text;
2. old Block-2 metadata-ratio assumptions;
3. old `WackelbildPreview.kt` plan text;
4. old shared `DateBadgeGeometry` plan text;
5. old 12° threshold;
6. old 18-attempt / JPEG-quality-50 size loop;
7. old legacy v2-v4 Wackelbild validation language;
8. old capture direct-downsample assumption if it conflicts with current storage/HQ specs.

If stale plan text materially affects Block 5, report it.

Do not edit the plan in this analysis gate.

State whether a documentation-correction gate is required before Block 5 implementation.

---

# 26. Verification Commands for Later Implementation

Plan exact commands.

At minimum:

- `./gradlew testDebugUnitTest`
- `./gradlew compileDebugAndroidTestKotlin`
- narrow Wackelbild renderer instrumentation test on `pixel2Api29`
- existing `ShareImageRendererInstrumentedTest`
- existing Share Image unit tests
- `./gradlew assembleDebug`
- `git diff --check`
- `git status --short`

Assess whether `assembleRelease` should be run because this block introduces high-memory renderer code even without manifest/dependency changes.

No test suppression/baseline.

---

# 27. Required Output

Return exactly:

## 1. Repository Baseline

## 2. Authoritative Contract Reconciliation

Include explicit statements on:

- no planned legacy-session support;
- reference-original vs reference-source-original;
- Share 3840 cap vs Wackelbild limits;
- any stale implementation-plan items.

## 3. Current HQ Renderer Evidence

Separate:

### 3.1 Reference
### 3.2 Capture

## 4. Capture-Parity Verdict

Choose exactly one:

- **PARITY PROVABLE — IMPLEMENTABLE**
- **PARITY NOT PROVABLE — BLOCKED**

Explain evidence.

## 5. Block-5 Boundary

Precisely what Block 5 owns and what Block 6/later blocks own.

## 6. Common Genuine Resolution Strategy

Give exact formulas/rounding and any memory-safety limit decision.

## 7. Pair-Level 20MiB Strategy

State whether current plan is corrected or stale and the exact intended algorithm.

## 8. Date-Badge Bitmap Strategy

Exact scaling rules; no open design choice.

## 9. Privacy / Color / Immutability Strategy

## 10. Memory / OOM Strategy

Include peak-memory estimates.

## 11. Fallback Strategy

Current-release failure fallback only; no legacy-product scope.

## 12. Pair Mapping / Proposed Production API

## 13. Files Proposed for Modification / Creation

Table:

| File | Modify/Create | Exact change | Why | Risk |
|---|---|---|---|---|

## 14. Files Explicitly NOT Touched

## 15. Tests to Add / Update

## 16. Verification Planned

## 17. Physical-Device Validation Required

## 18. Risks / Blockers

Only genuine Block-5 items.

## 19. Documentation Correction Needed?

Choose:

- **NO — CURRENT PLAN IS SUFFICIENT FOR BLOCK 5**
- **YES — PLAN CORRECTION REQUIRED BEFORE IMPLEMENTATION**

Explain exact sections if YES.

## 20. Scope Confirmation

If and only if there is no blocker, end exactly:

**BLOCK 5A SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If capture parity or plan inconsistency blocks implementation, instead end exactly:

**BLOCK 5A BLOCKED — CORRECTION / DECISION REQUIRED**

Then STOP.

---

# Final Rule

Analysis only.

Block 5 must not be approved on assumptions.

The most important questions are:

1. Can exact Capture crop parity be proven from the current persisted data?
2. Can Reference genuine-resolution limits be computed without upscaling?
3. Can output be produced at the highest suitable common quality without unsafe memory behavior?
4. Is the current plan internally consistent with all accepted corrections?
5. Can every output be proven metadata-clean and every session file immutable?

Do not implement until those are answered.
