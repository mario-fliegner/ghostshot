# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 5E: RATIO-PARITY INVESTIGATION & SURGICAL FIX — ANALYSIS + SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

Block 5 has been implemented, but final review found one potentially contract-breaking issue in the current ratio-tolerance handling.

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files.
Do not output implementation code.
Do not begin Block 6.
Do not add network/API/UI work.
Do not refactor unrelated renderer code.

The only goal is to determine the exact mathematically correct rule for ratio mismatch handling in:

- HQ Capture
- frozen-pair fallback

and then propose the smallest surgical fix.

---

# 1. Problem Statement

Current Block-5 implementation uses:

`CAPTURE_RATIO_TOLERANCE = 0.02f`

and equivalent fallback ratio compatibility logic.

The renderer may therefore accept cases such as:

- `1920×1088`
- target/session ratio `1920×1080`

because the relative ratio error is below 2%.

But if the renderer then forces both images into identical target dimensions without:

- crop
- stretch
- letterbox

there is a mathematical contradiction.

If a source has a different true aspect ratio from the target, one of those transformations must occur.

The product contract explicitly forbids:

- stretch
- guessed crop
- letterbox
- approximate content alteration.

Therefore the current 2% tolerance must be re-evaluated.

---

# 2. Authoritative Inputs

Read before analysis:

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
- relevant capture-path code:
  - `CameraScreen.kt`
  - `CameraViewModel.kt`
  - `SessionStorage.kt`
- current renderer unit/instrumentation tests.

Do not assume the implementation report is sufficient. Re-read the actual code.

---

# 3. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Preserve current uncommitted Block-5 changes and plan corrections.

Do not touch prompt archives.

---

# 4. Exact Questions to Answer

## 4.1 What does 1920×1088 actually mean in this pipeline?

Determine from current Android/CameraX/image encoding behavior and repository code whether cases such as:

- 1920×1088 instead of 1920×1080
- 1088×1920 instead of 1080×1920

can occur because of:

- codec macroblock alignment
- JPEG MCU padding
- decoder stride
- encoded pixel padding
- stream-resolution selection
- or actual additional visible image rows/columns.

This must be evidence-based.

Do not assume codec padding is invisible unless the decoded bitmap dimensions/content prove it.

Important:

- stride padding that is not part of decoded image dimensions is irrelevant;
- encoded image padding that decodes into actual visible pixels is part of the image unless proven otherwise.

## 4.2 Are there current real/synthetic fixtures demonstrating such near-match ratios?

Search current tests/fixtures/session files for:

- 1920×1088
- 1088×1920
- other near-16:9 dimensions.

If present, inspect how `capture.jpg` and `capture-original.jpg` compare.

## 4.3 Does Android BitmapFactory/ImageDecoder expose only the actual decoded visible dimensions?

Confirm whether `Bitmap.width/height` after decode represents:

- true decoded image pixels
- or includes any non-visible codec padding.

This matters for deciding whether tiny ratio differences can safely be ignored.

## 4.4 Can the renderer normalize tiny dimension padding without violating visual parity?

Only if there is repository/API evidence that certain edge pixels are non-image padding.

If yes:

- define exact recognized padding pattern;
- define exact normalization rule;
- prove it does not remove actual visible content.

If no:

- the renderer must treat any true ratio difference as incompatible.

---

# 5. HQ Capture Parity Rule — Determine Exact Correct Contract

Current desired behavior:

- `capture-original.jpg` is visually the same full frame as `capture.jpg`
- no crop should be introduced
- HQ should preserve full content.

Determine the exact safe condition under which `capture-original.jpg` may be resized to common output dimensions without distortion.

The mathematically strict rule is:

- source ratio must equal target ratio within only integer-rounding noise attributable to the output dimension calculation itself.

Analyze whether the existing 2% tolerance is too broad.

If it is too broad, propose a replacement.

Possible acceptable approaches:

### Option A — exact rational compatibility

Cross-multiply integer dimensions:

`abs(sourceW * targetH - sourceH * targetW)`

and accept only exact or tiny integer rounding-compatible difference.

### Option B — much tighter relative tolerance

Use only a tolerance sufficient for integer rounding of target dimensions, not arbitrary stream mismatch.

### Option C — recognized codec-padding normalization

Only if proven from evidence.

Choose exactly one and justify it.

No open choice.

---

# 6. Frozen Fallback Ratio Rule

Frozen `reference.jpg` and `capture.jpg` are the visual source of truth.

Therefore:

- if their true decoded aspect ratios differ, both cannot be rendered to identical dimensions without crop/stretch/letterbox.

Determine the correct strict compatibility rule.

Prefer stronger behavior than HQ if needed.

Possible expected rule:

- identical reduced rational ratio after dividing by GCD
- or equivalent exact cross-multiplication equality
- with only deterministic integer-rounding tolerance if needed.

Do not reuse 2% merely for convenience.

If incompatible:

`PERMANENT_NO_VALID_SOURCE`

No second fallback.

---

# 7. Distinguish Source Ratio vs Output Rounding

Important:

A source may have exact 16:9 ratio while the final even-rounded output dimensions are, for example, off by 1–2 pixels from mathematically perfect ratio.

That is not the same as accepting a source with a genuinely different aspect ratio.

The corrected rule must distinguish:

- input-source compatibility
vs.
- output integer rounding.

Output rounding may use the existing deterministic even-dimension logic.

Input compatibility must remain strict enough to guarantee no visual distortion.

---

# 8. Check Current Renderer for Actual Stretch

Inspect current Block-5 implementation and answer:

1. Where are source bitmaps scaled to target dimensions?
2. Does the current code use:
   - `Bitmap.createScaledBitmap`
   - Canvas draw into fixed rect
   - another scaling path?
3. If accepted ratios differ slightly, does current code actually stretch?
4. Is there any hidden crop/fit behavior already preventing distortion?
5. Which exact code lines must change to fix the problem?

Do not guess from architecture. Trace the current implementation.

---

# 9. Minimal Fix Strategy

Propose the smallest possible fix.

Preferred scope:

- ratio-compatibility helper/constant logic
- directly affected renderer branch
- directly affected tests
- documentation note if current `IMPLEMENTATION_NOTES.md` or implementation plan records the 2% rule as final.

Do not redesign:

- dimension resolver generally
- date badge
- 20MiB loop
- temp files
- ShareImageRenderer reuse
- privacy pipeline
- memory pipeline.

---

# 10. Test Plan

Plan exact regression tests.

## HQ Capture

### Exact compatible ratio

Example:
- `1920×1080`
- target 16:9

→ HQ allowed.

### Same ratio, different size

Example:
- `1280×720`
- target 16:9

→ HQ allowed.

### Near but genuinely different ratio

Example:
- `1920×1088`
- target 16:9

→ expected behavior depends on evidence from §4:
- either rejected
- or normalized only if proven safe.

### Clearly incompatible ratio

Example:
- 4:3 vs 16:9

→ HQ rejected → fallback.

## Frozen fallback

### exact same ratio, different dimensions

Example:
- 1080×1920
- 720×1280

→ compatible.

### one-pixel/rounding case

Define exact expected result based on chosen compatibility rule.

### near mismatch

Example:
- 1080×1920
- 1088×1920

→ must not stretch.

### clearly incompatible

→ hard failure.

## Output rounding

Verify source compatibility check is independent from final even-dimension rounding.

---

# 11. EXIF Test Coverage Follow-Up

Also inspect the current instrumentation tests for metadata assertions.

The Block-5 implementation report mentions:

- GPS
- DateTimeOriginal
- DateTime
- Make
- Model
- Software
- Lens fields

but does not mention:

- serial identifier tags
- MakerNote.

Determine:

1. whether AndroidX `ExifInterface` exposes constants/access for these tags in the current dependency version;
2. whether the current tests already check them under different names;
3. if inspectable, add them to the eventual fix scope;
4. if not inspectable, document explicitly that they cannot be asserted with the current API.

This is secondary to ratio parity and must not expand into a metadata subsystem.

---

# 12. Strict Scope

This gate is analysis only.

Expected future implementation scope should be small.

Likely candidates:

- `WackelbildDimensionResolver.kt`
- `WackelbildPrintRenderer.kt`
- directly affected unit/instrumentation tests
- `docs/IMPLEMENTATION_NOTES.md`
- possibly `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` only if the final rule changes the corrected 2% contract.

Do not assume all are needed.

List exact files.

---

# 13. Files Explicitly NOT Touched

Confirm the future fix does not require changes to:

- `ShareImageRenderer.kt`
- DateBadgeRenderer
- TempFileManager
- Wackelbild UI/ViewModel
- strings
- sensor/swipe
- navigation
- Gradle
- AndroidManifest
- network/API
- SessionStorage
- camera pipeline.

If camera/session-storage changes appear necessary, STOP — that is outside Block 5E scope and must be escalated.

---

# 14. Verification Planned After Fix

Plan exact commands:

- `./gradlew testDebugUnitTest`
- `./gradlew compileDebugAndroidTestKotlin`
- narrow `WackelbildPrintRendererInstrumentedTest`
- existing `ShareImageRendererInstrumentedTest`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `git diff --check`
- `git status --short`

Physical-device revalidation remains required after fix.

---

# 15. Required Final Response

Return exactly:

## 1. Repository Baseline

## 2. Current Ratio Logic Evidence

Show exact current constants/functions/call sites.

## 3. 1920×1088 Investigation

State clearly whether it is:
- actual visible decoded content
- or provably ignorable codec/alignment padding.

Use repository/API evidence.

## 4. HQ Capture Compatibility Verdict

Give one exact rule.

No options left open.

## 5. Frozen Fallback Compatibility Verdict

Give one exact rule.

No options left open.

## 6. Current Stretch/Crop Behavior

State exactly whether current code stretches near-mismatch inputs and where.

## 7. Proposed Surgical Fix

Describe exact logic change.

## 8. Files Proposed for Modification

Table:

| File | Modify/Create | Exact change | Why |

## 9. Tests to Add / Update

List exact cases.

## 10. EXIF Coverage Follow-Up

State:
- serial tags inspectable or not
- MakerNote inspectable or not
- whether tests need updating.

## 11. Risks

Only genuine Block-5E risks.

## 12. Verification Planned

## 13. Scope Confirmation

If parity can be fixed surgically, end exactly:

**BLOCK 5E SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If exact parity cannot be guaranteed without broader camera/storage changes, end exactly:

**BLOCK 5E BLOCKED — BROADER ARCHITECTURE DECISION REQUIRED**

Then STOP.

---

# Final Rule

Analysis only.

Do not implement.

The goal is to eliminate any path where two genuinely different source aspect ratios are silently forced into identical dimensions by stretching, while preserving valid same-ratio sources and existing Block-5 behavior.
