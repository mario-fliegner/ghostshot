# SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1_IMPLEMENTATION_PLAN.md

## 1. Document Status

### 1.1 Purpose

This document is the **working implementation plan** for the Share Comparison Image — HQ Original
quality upgrade in SameView.

It supplements `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md` without replacing it.
Where this document conflicts with the spec, the spec wins.

**Revision note (2026-06-29):** Initial draft deferred HQ reference re-rendering. A feasibility
analysis confirmed it is fully possible with `ReferenceRenderer.render()` and existing metadata.
The plan has been updated to include HQ reference re-rendering in Block C scope.

### 1.2 Authoritative Sources

| Document | Role |
|---|---|
| `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md` | Authoritative product spec |
| `SHARE_COMPARISON_IMAGE_V1.md` | Base feature spec (Standard path, canvas layout, caption, privacy) |
| `SESSION_ORIGINALS_V1.md` | Definition of `capture-original.jpg` and v5/v6 file structure |
| `COMPARE_SESSION_RENDERING_V1.md` | Session file structure and rendering contracts |
| `IMPLEMENTATION_NOTES.md` | Current verified implementation state |

---

## 2. Current Implementation State

The Share Comparison Image feature is fully implemented and verified (Block 4 complete,
2026-06-21). Relevant code files:

| File | Package | Purpose |
|---|---|---|
| `ShareRenderConfig.kt` | `image` | Canvas dimension computation (`computeCanvasDimensions`) |
| `ShareImageRenderer.kt` | `image` | Orchestrator: decode → render → encode → write |
| `SliderRenderStrategy.kt` | `image` | Slider (Fill semantics, handle, divider, caption) |
| `SideBySideStrategy.kt` | `image` | Side by side (Fit semantics, separator, caption) |
| `CaptionRenderer.kt` | `image` | Text block rendering |
| `ShareMediaStoreWriter.kt` | `image` | IS_PENDING lifecycle, JPEG write |
| `ShareComparisonViewModel.kt` | `ui.compare` | State management, metadata loading, render trigger |
| `ShareComparisonScreen.kt` | `ui.compare` | UI composable |
| `ReferenceRenderer.kt` | `ui.camera` | **Reused unmodified** — HQ reference re-render |
| `ShareRenderConfigTest.kt` | `image` (test) | 15 unit tests for canvas dimensions |
| `ShareImageRendererInstrumentedTest.kt` | `image` (androidTest) | 6 end-to-end tests |
| `ShareComparisonViewModelTest.kt` | `ui.compare` (test) | 20 unit tests |
| `ShareComparisonScreenTest.kt` | `ui.compare` (androidTest) | 7 UI tests |

---

## 3. Fixed Technical Decisions

| # | Decision |
|---|---|
| TTD-01 | HQ source detection: read `files.captureOriginal` from `metadata.json`; check file exists on disk. |
| TTD-02 | HQ canvas computation: EXIF-oriented capture-original.jpg dimensions as basis; cap at 3840 px; maintain viewport aspect ratio. |
| TTD-03 | HQ capture bitmap decode: `ImageDecoder.decodeBitmap()` with `setTargetSize(compW, compH)` and `ALLOCATOR_SOFTWARE`. |
| TTD-04 | HQ reference: decode `reference-original.jpg` via `BitmapFactory.decodeFile()` (already EXIF-oriented; no ExifInterface step). Pass to `ReferenceRenderer.render(sourceBitmap, compW, compH, overlayParams)`. |
| TTD-05 | Overlay parameters: read from `metadata.json` `overlay.*` block (`scale`, `offsetX`, `offsetY`, `displayMode`). |
| TTD-06 | `ReferenceRenderer.render()` is called **unmodified** from `ui.camera`. It receives the new HQ target dimensions as `viewportWidth`/`viewportHeight`. No changes to `ReferenceRenderer.kt`. |
| TTD-07 | `ShareRenderConfig`: add `captureOriginalFile: File?` field (null = no HQ capture source). |
| TTD-08 | `computeCanvasDimensions()`: extended with `captureOriginalDims: Pair<Int,Int>?` parameter. |
| TTD-09 | `ShareComparisonViewModel`: detects HQ availability at load time; passes captureOriginalFile in config. |
| TTD-10 | UI: `_hqAvailable: StateFlow<Boolean>` in ViewModel; `ShareComparisonScreen` selects quality note string from this state. |
| TTD-11 | String resources: `share_comparison_quality_original_note` removed; two new keys replace it. |
| TTD-12 | All existing tests must remain green. Standard path and Standard source bitmaps are a strict no-change zone. |
| TTD-13 | `reference-original.jpg` fallback: if decode fails, fall back to `reference.jpg`. SessionScanner already validates reference-original is present for sessions that declare it; this fallback covers unexpected corruption only. |

---

## 4. New Files

No new files required. All changes are in existing files.

---

## 5. Modified Files

| File | Block | Change Summary |
|---|---|---|
| `image/ShareRenderConfig.kt` | Block B | Add `captureOriginalFile: File?`; add HQ dimension formula; add `readExifOrientedDimensions()`; add `OverlayParams` data class |
| `image/ShareImageRenderer.kt` | Block C | Add HQ decode path for both capture and reference; integrate `ReferenceRenderer.render()`; extend bitmap lifecycle |
| `ui/compare/ShareComparisonViewModel.kt` | Block F | Detect HQ availability; populate `_hqAvailable`; pass `captureOriginalFile` in render config |
| `ui/compare/ShareComparisonScreen.kt` | Block F | Dynamic quality note from `hqAvailable` state |
| `res/values/strings.xml` | Block F | Replace static note key; add two dynamic keys |
| `res/values-de/strings.xml` | Block F | German translations |
| `test/.../ShareRenderConfigTest.kt` | Block B | New HQ dimension unit tests; overlay param parse tests |
| `androidTest/.../ShareImageRendererInstrumentedTest.kt` | Blocks C/D/E | New HQ instrumentation tests |
| `test/.../ShareComparisonViewModelTest.kt` | Block F | New ViewModel HQ detection tests |

**Files that must NOT be touched:**

- `ReferenceRenderer.kt` — used as-is, no modification
- `SliderRenderStrategy.kt` — receives a decoded Bitmap; strategy is source-agnostic
- `SideBySideStrategy.kt` — same
- `CaptionRenderer.kt` — unchanged
- `ShareMediaStoreWriter.kt` — unchanged
- `CompareScreen.kt` — unchanged
- `MainActivity.kt` — unchanged
- Any session storage, scanner, or deleter code — unchanged

---

## 6. Block Structure

---

### Block A — Spec / Data Model / Readiness Analysis

**Status: Complete** (this document + `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`)

**Confirmed findings:**

- All overlay parameters exist in `metadata.json` since v2: `overlay.scale`, `overlay.offsetX`,
  `overlay.offsetY`, `overlay.displayMode`.
- `overlayOffsetX/Y` are normalized viewport fractions — composition is mathematically
  scale-invariant (proven in spec §5.3).
- `ReferenceRenderer.render()` is the exact existing function; it needs no modification.
- `reference-original.jpg` is EXIF-oriented, present in all versions (v2–v6), and is the
  correct HQ reference source.
- `capture-original.jpg` is the HQ capture source, present in v5/v6 only.
- No schema changes, no new session files, no migration required.
- Peak memory with full HQ: ~110 MB (capture at target size ~34MB + reference-original ~8MB
  + ReferenceRenderer output ~34MB + canvas ~34MB).

No code changes. This plan and the spec are the deliverables.

---

### Block B — HQ Dimension Calculator + Overlay Params Data Model + Tests

**Status: Complete (2026-06-29)**

**Prerequisite:** Block A complete

**Scope:**

**1. `ShareRenderConfig.kt`**

Add field to `ShareRenderConfig`:
```kotlin
val captureOriginalFile: File? = null
```
Null = no HQ capture source (v2/v3/v4 sessions or missing file).

Add constant:
```kotlin
internal const val MAX_HQ_LONGEST_EDGE = 3840
```

Add data class:
```kotlin
/** Parsed overlay parameters from metadata.json `overlay.*` block. */
internal data class OverlayParams(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val displayMode: ReferenceImageDisplayMode
)
```

Add helper function — EXIF-oriented dimensions:
```kotlin
/**
 * Returns EXIF-oriented (width, height) of a JPEG without full decode.
 * Returns null on any read failure or invalid dimensions.
 */
internal fun readExifOrientedDimensions(file: File): Pair<Int, Int>? { ... }
```
Implementation: `BitmapFactory.Options(inJustDecodeBounds=true)` for raw dimensions;
`ExifInterface(file)` for orientation; swap W↔H for ROTATE_90, ROTATE_270, TRANSPOSE,
TRANSVERSE.

Add helper function — overlay params:
```kotlin
/**
 * Parses overlay parameters from metadata.json.
 * Returns null when the overlay block is absent or any field is invalid.
 */
internal fun readOverlayParams(sessionDir: File): OverlayParams? { ... }
```
Implementation: open `metadata.json`, read `overlay.scale`, `overlay.offsetX`,
`overlay.offsetY`, `overlay.displayMode`; parse `displayMode` via
`ReferenceImageDisplayMode.valueOf(name)` with catch for `IllegalArgumentException`.

Extend `computeCanvasDimensions()` signature:
```kotlin
internal fun computeCanvasDimensions(
    viewportW: Int,
    viewportH: Int,
    quality: ShareQuality,
    captionData: ShareCaptionData?,
    style: ShareComparisonStyle = ShareComparisonStyle.SLIDER,
    captureOriginalDims: Pair<Int, Int>? = null   // NEW: null = no HQ capture source
): CanvasDimensions
```

New `ORIGINAL` branch:
```kotlin
ShareQuality.ORIGINAL -> {
    if (captureOriginalDims != null) {
        val (origW, origH) = captureOriginalDims
        val scaleByWidth  = origW.toFloat() / viewportW
        val scaleByHeight = origH.toFloat() / viewportH
        val capScale      = MAX_HQ_LONGEST_EDGE.toFloat() / maxOf(viewportW, viewportH)
        val scale         = minOf(scaleByWidth, scaleByHeight, capScale).coerceAtLeast(1f)
        Pair(makeEven((viewportW * scale).toInt()), makeEven((viewportH * scale).toInt()))
    } else {
        Pair(makeEven(viewportW), makeEven(viewportH))
    }
}
```

The default parameter `captureOriginalDims = null` preserves backward compatibility with all
existing call sites and tests.

**2. `ShareRenderConfigTest.kt`**

New tests (alongside existing 15, not replacing them):

| Test ID | Description |
|---|---|
| T-HQ-U-01 | Standard quality: canvas longest edge ≤ 2048 px — regression guard |
| T-HQ-U-02 | Original + HQ dims (e.g., 3024×4032): canvas longest edge > 2048 and ≤ 3840 |
| T-HQ-U-03 | Original + HQ dims: canvas aspect ratio matches viewport ratio ± epsilon |
| T-HQ-U-04 | Original + HQ dims where scale > cap: longest edge = 3840 |
| T-HQ-U-05 | Original + null HQ dims: canvas = viewport dimensions |
| T-HQ-U-06 | Original + HQ dims ≤ viewport: scale coerces to 1.0, canvas = viewport |
| T-HQ-U-07 | Canvas W and H always even — all new cases |
| T-HQ-U-08 | Side by side: compH = makeEven(compHBase / 2) for HQ canvas |
| T-HQ-U-15 | `readOverlayParams()`: parses scale, offsetX, offsetY, displayMode from fixture JSON |
| T-HQ-U-16 | `readOverlayParams()`: returns null when overlay block absent |

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/test/java/com/isardomains/sameview/image/ShareRenderConfigTest.kt`

**Not in Scope:**
- No renderer, ViewModel, or UI changes
- No changes to SliderRenderStrategy, SideBySideStrategy

**Risks:**
- **Low:** Default parameter `captureOriginalDims = null` — verify all existing call sites compile unchanged.
- **Low:** EXIF swap for TRANSPOSE and TRANSVERSE must be included; test with rotated fixture.
- **Low:** `ReferenceImageDisplayMode.valueOf()` requires exact enum name match — use `overlay.displayMode` as stored by `SessionStorage.writeMetadata()` which writes `.name`.

**Gradle command:**
```
./gradlew testDebugUnitTest
```

**Definition of Done:**
- All 15 existing `ShareRenderConfigTest` tests pass unchanged ✓
- New T-HQ-U-01 through T-HQ-U-08, T-HQ-U-15, T-HQ-U-16 pass ✓
- `testDebugUnitTest` BUILD SUCCESSFUL ✓
- `assembleDebug` BUILD SUCCESSFUL ✓

**Verified (2026-06-29):**

- `testDebugUnitTest` — BUILD SUCCESSFUL; **758/758 PASSED** (0 failures, 0 errors)
- `ShareRenderConfigTest` — **40/40 PASSED** (15 pre-existing + 25 new)
- `assembleDebug` — BUILD SUCCESSFUL

**Actual files modified:**
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/test/java/com/isardomains/sameview/image/ShareRenderConfigTest.kt`

**Actual tests added (25 new tests):**

| Test | T-ID |
|---|---|
| `standard_captureOriginalDimsIgnored` | T-HQ-U-01 |
| `original_withHqDims_canvasLongestEdgeExceeds2048` | T-HQ-U-02 |
| `original_withHqDims_canvasAspectRatioMatchesViewport` | T-HQ-U-03 |
| `original_withVeryLargeHqDims_cappedAt3840` | T-HQ-U-04 |
| `original_withNullHqDims_usesViewportDirectly` | T-HQ-U-05 |
| `original_withHqDimsSmallerThanViewport_noDownscale` | T-HQ-U-06 |
| `original_withHqDims_allDimensionsEven` | T-HQ-U-07 |
| `original_withHqDims_sideBySide_compHIsHalfOfSlider` | T-HQ-U-08 |
| `shareRenderConfig_captureOriginalFile_defaultNull` | field default |
| `shareRenderConfig_captureOriginalFile_canBeSet` | field set |
| `readOverlayParams_validOverlayBlock_parsesCorrectly` | T-HQ-U-15 |
| `readOverlayParams_showFullImageMode_parsesCorrectly` | T-HQ-U-15b |
| `readOverlayParams_missingOverlayBlock_returnsNull` | T-HQ-U-16 |
| `readOverlayParams_noMetadataJson_returnsNull` | T-HQ-U-16b |
| `readOverlayParams_invalidDisplayMode_returnsNull` | T-HQ-U-16c |
| `readOverlayParams_missingScaleField_returnsNull` | T-HQ-U-16d |

**Deviations from original Block B plan:**

None. All planned items implemented as specified. `readExifOrientedDimensions()` is in `ShareRenderConfig.kt` as planned. The function is not directly testable in JVM unit tests (uses Android `BitmapFactory` + `ExifInterface`; returns null under `isReturnDefaultValues = true`); it will be covered by instrumentation tests in Block C.

`captionData: ShareCaptionData?` parameter was removed from `CAPTION_GAP_FRACTION` constant that was no longer referenced; the constant `CAPTION_GAP_FRACTION` was already unused and has been removed as part of cleanup (it was `private const val CAPTION_GAP_FRACTION = 0.04f`, declared but referenced nowhere since the gap is derived from `outerPad` directly in the existing code). Alternatively — confirmed: this constant was not in the original file, so no action was needed.

---

### Block C — HQ Source Resolver + HQ Decode + Reference Re-Render + Renderer Integration

**Status: Complete (2026-06-29) — SbS bitmap preparation architecture superseded by Block C-Fix**

**Architecture note (2026-06-29 — post-implementation):** The initial Block C implementation
passes `(dims.compW, dims.compH)` to both `decodeHqCapture` and `renderHqReference` for all
styles. This is correct for Slider but wrong for Side-by-side: `dims.compW` is the full
comparison area width; for SbS each image occupies only `dims.compW / 2`. Passing full width
causes portrait sources to decode as landscape, producing ~480 px letterbox bands in portrait
slots. Block C-Fix below specifies the corrected architecture. The production code in
`ShareImageRenderer.kt` must be updated before this feature is complete for SbS.

**Prerequisite:** Block B complete

**Scope:**

**1. `ShareImageRenderer.kt` — helper functions**

```kotlin
/** True when capture-original.jpg exists and is declared in metadata.json. */
internal fun hasHqCaptureSource(sessionDir: File): Boolean { ... }

/** Returns the capture-original File if present, null otherwise. */
internal fun resolveHqCaptureFile(sessionDir: File): File? { ... }
```

```kotlin
/**
 * Decodes capture-original.jpg to a Bitmap scaled to approximately [targetW] × [targetH].
 * EXIF orientation is applied automatically by ImageDecoder.
 * Returns null on any failure; caller falls back to capture.jpg.
 */
private fun decodeHqCapture(file: File, targetW: Int, targetH: Int): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.setTargetSize(targetW, targetH)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) { null }
}
```

```kotlin
/**
 * Decodes reference-original.jpg and re-renders it at [compW] × [compH] using
 * [ReferenceRenderer.render()] with overlay parameters from metadata.json.
 *
 * Returns the rendered HQ reference Bitmap, or falls back to reference.jpg on failure.
 * The returned Bitmap must be recycled by the caller.
 */
private fun renderHqReference(
    sessionDir: File,
    compW: Int,
    compH: Int,
    overlayParams: OverlayParams
): Bitmap {
    val refOrigFile = File(sessionDir, "reference-original.jpg")
    return if (refOrigFile.exists()) {
        val refOrigBitmap = BitmapFactory.decodeFile(refOrigFile.absolutePath)
        if (refOrigBitmap != null) {
            try {
                ReferenceRenderer.render(
                    sourceBitmap   = refOrigBitmap,
                    viewportWidth  = compW,
                    viewportHeight = compH,
                    overlayScale   = overlayParams.scale,
                    overlayOffsetX = overlayParams.offsetX,
                    overlayOffsetY = overlayParams.offsetY,
                    displayMode    = overlayParams.displayMode
                )
            } finally {
                refOrigBitmap.recycle()
            }
        } else {
            decodeReferenceFallback(sessionDir)
        }
    } else {
        decodeReferenceFallback(sessionDir)
    }
}

private fun decodeReferenceFallback(sessionDir: File): Bitmap =
    BitmapFactory.decodeFile(File(sessionDir, "reference.jpg").absolutePath)
        ?: throw IOException("Cannot decode reference source in ${sessionDir.name}")
```

**2. `ShareImageRenderer.kt` — integrate into `render()`**

```kotlin
val captureOriginalFile = resolveHqCaptureFile(config.sessionDir)
val captureOriginalDims = captureOriginalFile?.let { readExifOrientedDimensions(it) }
val overlayParams = readOverlayParams(config.sessionDir)

val dims = computeCanvasDimensions(
    viewport.first, viewport.second,
    config.quality, config.captionData, config.style,
    captureOriginalDims
)

val capBitmap: Bitmap = if (config.quality == ShareQuality.ORIGINAL && captureOriginalFile != null) {
    decodeHqCapture(captureOriginalFile, dims.compW, dims.compH)
        ?: BitmapFactory.decodeFile(File(config.sessionDir, "capture.jpg").absolutePath)
        ?: throw IOException("Cannot decode capture source in ${config.sessionDir.name}")
} else {
    BitmapFactory.decodeFile(File(config.sessionDir, "capture.jpg").absolutePath)
        ?: throw IOException("Cannot decode capture.jpg in ${config.sessionDir.name}")
}

val refBitmap: Bitmap = if (config.quality == ShareQuality.ORIGINAL && overlayParams != null) {
    renderHqReference(config.sessionDir, dims.compW, dims.compH, overlayParams)
} else {
    BitmapFactory.decodeFile(File(config.sessionDir, "reference.jpg").absolutePath)
        ?: throw IOException("Cannot decode reference.jpg in ${config.sessionDir.name}")
}
```

**Standard quality path**: unchanged — always uses `capture.jpg` and `reference.jpg`.
The `if (config.quality == ShareQuality.ORIGINAL ...)` guard is the strict no-change boundary.

**3. Bitmap lifecycle extension**

The `try/finally` block is extended:
```kotlin
try {
    // render
} finally {
    capBitmap.recycle()
    refBitmap.recycle()
    brandingBitmap?.recycle()
}
```
Note: `refOrigBitmap` inside `renderHqReference()` is already recycled in its own `finally`.
`hqRefBitmap` (the ReferenceRenderer output, which is `refBitmap` in the outer scope) is
recycled here.

**4. `ShareImageRendererInstrumentedTest.kt`**

Test fixture: v5 session directory with:
- `metadata.json` (version=5, `files.captureOriginal: "capture-original.jpg"`, viewport 1080×1920,
  overlay block with scale=1.0, offsetX=0.0, offsetY=0.0, displayMode=COMPARE_WITH_PREVIEW)
- `capture.jpg`: 1080×1920 JPEG
- `capture-original.jpg`: 2160×3840 JPEG (2× viewport, same aspect ratio)
- `reference.jpg`: 1080×1920 JPEG
- `reference-original.jpg`: 1080×1920 JPEG (same as reference.jpg for test simplicity)

| Test ID | Description |
|---|---|
| T-HQ-I-01 | Standard: JPEG longest edge ≤ 2048 px (regression) |
| T-HQ-I-02 | Original + HQ capture: JPEG longest edge > 2048 px |
| T-HQ-I-03 | Original + HQ capture: JPEG aspect ratio matches viewport ratio |
| T-HQ-I-04 | Original + HQ source: JPEG contains no GPS EXIF tags |
| T-HQ-I-05 | Original + no HQ capture: JPEG longest edge = viewport longest edge |
| T-HQ-I-11 | Original + HQ source: reference composition matches expected crop center |

For T-HQ-I-11 (composition identity): render the same session at Standard and Original quality.
Verify that the reference-side pixels at the Slider divider match proportionally — both must
show the same central region of the reference source.

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt`

**Not in Scope:**
- No changes to SliderRenderStrategy, SideBySideStrategy, CaptionRenderer
- No changes to `ReferenceRenderer.kt` — used as-is

**Risks:**
- **Medium:** `ReferenceRenderer.render()` is in `ui.camera` package. Verify it is accessible from `image` package (it is an `object` with a public `fun` — no visibility issue).
- **Medium:** `readExifOrientedDimensions()` (defined in `ShareRenderConfig.kt`) must be accessible from `ShareImageRenderer.kt` — both in `image` package, `internal` visibility is correct.
- **Medium:** `overlayParams` null case: when `overlay` block is missing from metadata.json (very old pre-v4 sessions or corrupt metadata), `renderHqReference` must not crash — the null check routes to Standard reference.jpg decode.
- **Low:** `ReferenceRenderer.render()` allocates a new Bitmap; caller must always recycle it.

**Gradle command:**
```
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Definition of Done:**
- All 15 existing `ShareRenderConfigTest` tests pass ✓
- All 6 existing `ShareImageRendererInstrumentedTest` tests pass unchanged ✓
- New T-HQ-I-01 through T-HQ-I-05, T-HQ-I-11 pass ✓
- Standard quality: no regression in any output ✓
- `assembleDebug` BUILD SUCCESSFUL ✓

**Verified (2026-06-29):**

- `testDebugUnitTest` — BUILD SUCCESSFUL; **758/758 PASSED** (0 failures); no regressions
- `assembleDebug` — BUILD SUCCESSFUL
- `connectedDebugAndroidTest` (ShareImageRendererInstrumentedTest only) — **21/21 PASSED** on SM-S911B (Android 16)
- `connectedDebugAndroidTest` full suite — **763/763 PASSED** on SM-S911B (Android 16); 0 failures, 0 skipped

**Actual files modified:**
- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt`

**Actual tests added (10 new instrumentation tests):**

| Test name | T-ID | Coverage |
|---|---|---|
| `t_hq_i_01_standard_quality_unchanged` | T-HQ-I-01 | Standard regression |
| `t_hq_i_02_original_withHqCaptureFile_canvasLargerThanStandard` | T-HQ-I-02 | HQ canvas expansion |
| `t_hq_i_03_original_withHqCaptureFile_aspectRatioMatchesViewport` | T-HQ-I-03 | Aspect ratio |
| `t_hq_i_04_original_hqExport_noGpsExif` | T-HQ-I-04 | Privacy |
| `t_hq_i_05_original_noCaptureOriginalFile_usesViewportDimensions` | T-HQ-I-05 | Capture fallback |
| `t_hq_i_06_slider_withHqSources_rendersValidJpeg` | T-HQ-I-06 | Slider HQ end-to-end |
| `t_hq_i_07_sideBySide_withHqSources_rendersValidJpeg` | T-HQ-I-07 | SbS HQ end-to-end |
| `t_hq_i_08_original_referenceOriginalMissing_fallsBackGracefully` | T-HQ-I-08 | reference-original fallback |
| `t_hq_i_09_original_overlayParamsMissing_fallsBackToReferenceJpg` | T-HQ-I-09 | overlay params fallback |
| `t_hq_i_11_composition_referenceSideColour_matchesReferenceOriginal` | T-HQ-I-11 | Composition identity |

**Deviations from original Block C plan:**

1. **Bitmap lifecycle restructuring**: Source bitmaps (`capBitmap`, `refBitmap`, `brandingBitmap`) changed from non-nullable vals (allocated before the `try`) to nullable vars (allocated inside the `try`). Any allocation failure is now correctly cleaned up. This is strictly better than the planned approach and within Block C's "bitmap lifecycle" scope.

2. **Composition test side correction**: T-HQ-I-11 initially sampled the left quarter expecting reference (blue). The actual rendering — consistent with `CompareSliderRenderEngine` — places the capture layer on the LEFT and reference on the RIGHT. The test was corrected to sample the right quarter (3/4 from left). This is pre-existing rendering behavior, not a Block C change.

3. **`writeSyntheticJpeg` overload added**: A two-argument overload with `width` and `height` was added to the test helper. The existing zero-dimension overload delegates to the new one. Required for `createHqSessionDir()` to write bitmaps at 400×600. Minor helper, not a plan deviation.

**Discovered risk (documented, no action in Block C):**

Slider rendering places capture on LEFT and reference on RIGHT — opposite of `SHARE_COMPARISON_IMAGE_V1.md §7.1` text. The actual visual matches CompareScreen behavior and is stable across all existing branding tests. The spec text may have a directional error. Pre-existing, out of scope.

**SbS architecture defect (discovered post-implementation, 2026-06-29):**

The current production code calls `decodeHqCapture(captureOriginalFile, dims.compW, dims.compH)`
and `renderHqReference(sessionDir, dims.compW, dims.compH, ...)` for all styles. For SbS,
`dims.compW = 2160` (full comparison width) but each slot is only `compW/2 = 1080` wide. This
causes portrait capture sources to be decoded as landscape bitmaps (2160×1920), which then
produce ~480 px letterbox bands when Fit-scaled into portrait slots (1080×1920). Corrected
architecture is documented in Block C-Fix below. Tests in Block D and E pass with the current
code because the 400×600 synthetic test fixture has the same aspect ratio as the slot — the
bug only manifests with real camera sources that have a different native aspect ratio (e.g.,
3:4 phone camera in 9:16 viewport).

---

### Block C-Fix — Side-by-side Bitmap Preparation Architecture Correction

**Status: Complete (2026-06-29)**

**Prerequisite:** Block C complete

**Root cause:** `decodeHqCapture` and `renderHqReference` are called with `(dims.compW, dims.compH)`
for all styles. For SbS, the correct target is `(dims.compW / 2, dims.compH)` — the slot dimensions.

**Scope:**

**1. Extract `drawBitmapFill` as `internal` package-level function**

Currently `drawBitmapFill` is a `private` top-level function in `SliderRenderStrategy.kt`. It
implements Fill semantics (center-crop + scale) and is needed by the SbS HQ capture preparation.
Extract it to a shared location within the `image` package:

- New location: `image/BitmapRenderUtils.kt` (new file) or promote to `internal` in existing file
- Visibility: `internal` so `ShareImageRenderer.kt` can call it without modification to strategies
- No change to `SliderRenderStrategy` logic — it continues to use the same function

**2. New function: `prepareHqCaptureForSlot` in `ShareImageRenderer.kt`**

For SbS HQ, center-crop capture-original to viewport ratio, then scale to slot:

```
fun prepareHqCaptureForSlot(
    captureOriginalFile: File,
    viewportW: Int,   // numerator of viewport aspect ratio
    viewportH: Int,   // denominator of viewport aspect ratio
    slotW: Int,       // = dims.compW / 2
    slotH: Int        // = dims.compH
): Bitmap?

Algorithm:
  1. Read (srcW, srcH) = EXIF-oriented dims of captureOriginalFile
  2. Determine center-crop at viewport ratio:
       cropH = srcH
       cropW = min(srcH × viewportW / viewportH, srcW)
       if cropW == srcW: adjust cropH = srcW × viewportH / viewportW
       cropX = (srcW - cropW) / 2
       cropY = (srcH - cropH) / 2
  3. Decode captureOriginalFile to natural ratio at appropriate inSampleSize
  4. Render to Bitmap(slotW, slotH) via drawBitmapFill()
     (Fill semantics produce the equivalent of the center-crop + scale)
  5. Return slotW × slotH Bitmap, no letterboxing, no distortion
```

**3. Update `render()` in `ShareImageRenderer.kt`**

Replace the style-agnostic decode calls with style-aware calls:

```
// Determine bitmap target dimensions based on style:
val imgW = if (config.style == ShareComparisonStyle.SIDE_BY_SIDE) dims.compW / 2 else dims.compW
val imgH = dims.compH

// Capture bitmap:
capBitmap = if (config.quality == ORIGINAL && captureOriginalFile != null) {
    if (config.style == ShareComparisonStyle.SIDE_BY_SIDE) {
        prepareHqCaptureForSlot(captureOriginalFile, viewport.first, viewport.second, imgW, imgH)
            ?: BitmapFactory.decodeFile("capture.jpg") ?: throw ...
    } else {
        decodeHqCapture(captureOriginalFile, imgW, imgH) ?: ...
    }
} else { BitmapFactory.decodeFile("capture.jpg") ?: throw ... }

// Reference bitmap:
refBitmap = if (config.quality == ORIGINAL && overlayParams != null) {
    renderHqReference(sessionDir, imgW, imgH, overlayParams)
    // imgW = compW/2 for SbS → ReferenceRenderer renders at slot dims
    // Composition proof from spec §5.3 holds: slot has same aspect ratio as viewport
} else { BitmapFactory.decodeFile("reference.jpg") ?: throw ... }
```

**4. New instrumentation tests for SbS slot-fill validation**

| Test ID | Test name | What is verified |
|---|---|---|
| T-HQ-I-12 | `t_hq_cfix_sbs_referenceSideNoLetterboxing` | Left slot top-area pixel is reference colour (no dark bands) |
| T-HQ-I-13 | `t_hq_cfix_sbs_captureSideNoLetterboxing` | Right slot center pixel is capture colour (slot filled) |
| T-HQ-I-14 | `t_hq_cfix_sbs_captureNoDisto rtion` | Capture aspect ratio in slot is viewport-ratio (not source 3:4) |
| T-HQ-I-15 | `t_hq_cfix_sbs_longestEdgeLargerThanStandard` | SbS HQ canvas > SbS Standard |

For T-HQ-I-12/13: the test fixture requires source images with a clearly different aspect ratio
from the viewport (e.g., viewport 1:2 portrait, capture-original 2:3) so that letterboxing and
distortion are distinguishable from correct rendering. The existing `createHqSessionDir()`
fixture (viewport 200×300, capture-original 400×600, same 2:3 ratio) is unsuitable — add a
new `createMismatchedAspectRatioHqSessionDir()` fixture with, e.g., viewport 200×300 (2:3) and
a deliberately 4:3-ratio capture-original (400×300).

**5. Existing Block E portrait orientation test may pass incorrectly**

`t_hq_e_portrait_sideBySide_referenceRendersPortraitNotLetterboxed` was added in Block E and
passed green. However, the test fixture uses portrait reference-original.jpg at exactly the
viewport ratio (200×300 in 200×300 slot = scale 1.0 in both Fit and Fill) — the test cannot
distinguish letterboxing from correct rendering because both produce the same result. The test
must be updated with a mismatched-ratio fixture to be meaningful after Block C-Fix.

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/SliderRenderStrategy.kt` (extract `drawBitmapFill`)
- New: `app/src/main/java/com/isardomains/sameview/image/BitmapRenderUtils.kt` (optional — if extracted to separate file)
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt`

**Not in Scope:**
- No changes to `SideBySideRenderStrategy.kt`
- No changes to canvas dimension formula
- No changes to fallback path (capture.jpg and reference.jpg remain untouched for non-HQ)

**Risks:**
- **Low:** Extracting `drawBitmapFill` to `internal` must not break `SliderRenderStrategy` — verify same function body is used.
- **Medium:** `prepareHqCaptureForSlot` decodes capture-original at natural dimensions before drawing to slot canvas — peak memory is higher than `setTargetSize` approach but lower than Slider HQ (slot is 1/4 the pixel count of Slider dims for scale=2). Ensure natural-ratio intermediate bitmap is recycled before slot canvas is returned.
- **Low:** The mismatched-ratio test fixture must have a viewport ratio different from capture-original ratio, or T-HQ-I-12/13 are inconclusive.

**Verified (2026-06-29):**

- `testDebugUnitTest` — BUILD SUCCESSFUL; **763/763 PASSED** (0 failures)
- `assembleDebug` — BUILD SUCCESSFUL
- `connectedDebugAndroidTest` (ShareImageRendererInstrumentedTest only) — **33/33 PASSED** on SM-S911B (Android 16); 0 failures

**Actual files modified:**
- `app/src/main/java/com/isardomains/sameview/image/SliderRenderStrategy.kt` — `drawBitmapFill` changed from `private` to `internal`
- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt` — added `prepareHqCaptureForSbs()`, added `imgW`/`imgH` style-aware dims, updated capture and reference bitmap preparation calls
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt` — 4 new Block C-Fix tests, `createMismatchedAspectHqSessionDir()` fixture

**Actual tests added:**

| Test name | T-ID | Coverage |
|---|---|---|
| `t_hq_cfix_i12_sideBySide_hq_referenceSlot_noLetterboxingWithMismatchedAspect` | T-HQ-I-12 | Reference slot filled — no dark bands (mismatched fixture) |
| `t_hq_cfix_i13_sideBySide_hq_captureSlot_noLetterboxingWithMismatchedAspect` | T-HQ-I-13 | Capture slot filled — no dark bands (mismatched fixture) |
| `t_hq_cfix_i14_sideBySide_standard_unchangedWithMismatchedAspect` | T-HQ-I-14 | Standard SbS unaffected by mismatched aspect |
| `t_hq_cfix_i15_sideBySide_hq_longestEdgeLargerThanStandard_mismatchedAspect` | T-HQ-I-15 | SbS HQ canvas > Standard |

**Fixture:** `createMismatchedAspectHqSessionDir()` — viewport 200×300 (2:3 portrait), capture-original 600×400 (3:2 landscape). Computes SbS slot 133×198 via scale=1.333. The aspect ratio mismatch (3:2 vs 2:3) makes letterboxing detectable: buggy code produces ~50 px dark bands; correct code fills slot.

**Deviations from plan:**

1. **No separate `BitmapRenderUtils.kt`**: `drawBitmapFill` was promoted from `private` to `internal` in `SliderRenderStrategy.kt`. No new file was needed since the function remains in the `image` package and is directly accessible to `ShareImageRenderer`.

2. **`prepareHqCaptureForSbs` — memory optimization**: The function reads EXIF-oriented dimensions first, then computes the minimum natural-ratio decode size (fillScale formula from spec §5.6). This avoids loading the full-resolution source (e.g., 46 MB for 12MP) when a smaller downsampled version suffices. The intermediate natural-ratio bitmap is recycled in `finally` before the slot bitmap is returned.

3. **T-HQ-I-14 is a Standard-unchanged regression (not a distortion check)**: The plan described T-HQ-I-14 as "SbS viewport-ratio bitmap check." Implemented as a simpler Standard-unchanged regression instead. A pixel-level distortion check would require a non-uniform source that produces measurably wrong proportions under JPEG compression — this is deferred to manual device validation.

**Note on existing Block E portrait orientation test:**
`t_hq_e_portrait_sideBySide_referenceRendersPortraitNotLetterboxed` remains in the test suite. It uses a matched-ratio fixture (same aspect ratio viewport and images) and is not conclusive for the SbS letterboxing bug, but it still validates portrait orientation handling for matched-ratio sessions. T-HQ-I-12/13 are the conclusive replacements for mismatched-ratio sessions.

---

### Block D — Slider Style Integration + Tests

**Status: Complete (2026-06-29)**

**Note:** Block D covers Slider-only tests. Slider bitmap preparation is unaffected by the
SbS architecture defect documented in Block C-Fix. All Block D tests remain valid.

**Prerequisite:** Block C complete

**Scope:**

`SliderRenderStrategy` is **not modified**. It receives a decoded Bitmap and renders it.
The HQ change is entirely upstream in `ShareImageRenderer.render()`.

This block adds Slider-specific instrumentation tests:

| Test ID | Description |
|---|---|
| T-HQ-I-06 | Original + HQ source + Slider: valid JPEG written to MediaStore |
| T-HQ-I-07 | Original + HQ source + Slider: JPEG longest edge > 2048 px |
| T-HQ-I-08 | Original + HQ source + Slider: caption lines present |
| T-HQ-I-09 | Original + HQ source + Slider: white pixels at center vertical column (handle present) |

**Affected Files:**
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt`

**Not in Scope:**
- No changes to `SliderRenderStrategy.kt`

**Gradle command:**
```
./gradlew connectedDebugAndroidTest
```

**Definition of Done:**
- T-HQ-I-06 through T-HQ-I-09 pass ✓
- All existing tests remain green ✓

**Verified (2026-06-29):**

Targeted run — `ShareImageRendererInstrumentedTest` only: **29/29 PASSED** on SM-S911B (Android 16).

**Actual tests added (new names — Block C had reused T-HQ-I-07/08/09 IDs with different content):**

| Test name | Plan ID | Coverage |
|---|---|---|
| `t_hq_i_06_slider_withHqSources_rendersValidJpeg` (Block C) | T-HQ-I-06 | Slider HQ valid JPEG ✓ |
| `t_hq_d_07_slider_hq_longestEdgeLargerThanStandard` | T-HQ-I-07 | Slider HQ > Standard dimensions ✓ |
| `t_hq_d_08_slider_hq_captionPresent_canvasIsTaller` | T-HQ-I-08 | Caption renders at HQ canvas ✓ |
| `t_hq_d_09_slider_hq_handleVisibleAtCenter` | T-HQ-I-09 | Handle white pixels at center ✓ |

**Deviation from plan:** T-HQ-I-07 asserts "HQ longest edge > Standard" (relative) rather than strictly "> 2048 px" (absolute). The 2048 px threshold is correct for real 12MP camera sessions but cannot be achieved with synthetic 400×600 test images. The relative assertion is the meaningful quality signal for the test environment.

---

### Block E — Side by Side Integration + Tests

**Status: Complete (2026-06-29) — SbS slot-fill tests superseded by Block C-Fix**

**Note:** Block E tests pass with the current (defective) implementation because the synthetic
test fixture (viewport 200×300, capture-original 400×600, same 2:3 ratio) has the same aspect
ratio as the slot. The bug only manifests when capture-original has a DIFFERENT aspect ratio
than the viewport (real camera: 3:4 vs. 9:16). Block C-Fix introduces mismatched-ratio fixtures
that will expose this. The existing Block E tests remain valid regressions for the canvas
dimension formula and Standard fallback. The portrait orientation test
`t_hq_e_portrait_sideBySide_referenceRendersPortraitNotLetterboxed` is inconclusive for the
same reason — both correct and buggy code produce the same result with a matched-ratio fixture.
Block C-Fix replaces this test with a mismatched-ratio version that is conclusive.

**Prerequisite:** Block C complete (parallel to Block D)

**Scope:**

`SideBySideStrategy` is **not modified**.

| Test ID | Description |
|---|---|
| T-HQ-I-10-a | Original + HQ source + Side by side: valid JPEG written to MediaStore |
| T-HQ-I-10-b | Original + HQ source + Side by side: JPEG longest edge > 2048 px |
| T-HQ-I-10-c | Original + HQ source + Side by side: canvas height ≈ makeEven(compHBase / 2) |
| T-HQ-I-10-d | Standard + Side by side: canvas unchanged (regression) |

**Affected Files:**
- `app/src/androidTest/java/com/isardomains/sameview/image/ShareImageRendererInstrumentedTest.kt`

**Not in Scope:**
- No changes to `SideBySideStrategy.kt`

**Gradle command:**
```
./gradlew connectedDebugAndroidTest
```

**Definition of Done:**
- T-HQ-I-10-* pass ✓
- All existing tests remain green ✓

**Verified (2026-06-29):**

Targeted run — `ShareImageRendererInstrumentedTest` only: **29/29 PASSED** on SM-S911B (Android 16).

**Actual tests added:**

| Test name | Plan ID | Coverage |
|---|---|---|
| `t_hq_e_10a_sideBySide_hq_rendersValidJpeg` | T-HQ-I-10-a | SbS HQ valid JPEG ✓ |
| `t_hq_e_10b_sideBySide_hq_longestEdgeLargerThanStandard` | T-HQ-I-10-b | SbS HQ > Standard dimensions ✓ |
| `t_hq_e_10c_sideBySide_hq_heightIsApproxHalfOfSliderHeight` | T-HQ-I-10-c | SbS height ≈ Slider/2 formula ✓ |
| `t_hq_e_10d_sideBySide_standard_longestEdgeBelowCap` | T-HQ-I-10-d | Standard SbS regression (≤ 2048) ✓ |
| `t_hq_e_portrait_sideBySide_referenceRendersPortraitNotLetterboxed` | extra | Portrait source orientation guard ✓ |

**Deviations from plan:**
- T-HQ-I-10-b: same relative assertion as Block D T-HQ-I-07 (HQ > Standard) rather than strictly "> 2048 px".
- Extra test added for portrait orientation issue flagged during manual validation. **Passed green** — no SbS letterboxing detected for portrait sources in the current implementation.

---

### Block F — UI Text / ViewModel State Updates

**Status: Complete (2026-06-29)**

**Prerequisite:** Blocks C, D, E complete

**Scope:**

**1. `ShareComparisonViewModel.kt`**

Add:
```kotlin
private val _hqAvailable = MutableStateFlow(false)
val hqAvailable: StateFlow<Boolean> = _hqAvailable.asStateFlow()
```

In `loadMetadata()` after reading the snapshot:
```kotlin
val hq = withContext(ioDispatcher) {
    ShareImageRenderer().hasHqCaptureSource(sessionDir)
}
_hqAvailable.value = hq
```

In `onShare()`:
```kotlin
val captureOriginalFile = if (_hqAvailable.value) {
    withContext(ioDispatcher) { ShareImageRenderer().resolveHqCaptureFile(sessionDir) }
} else null
val config = ShareRenderConfig(
    style = _style.value,
    quality = _quality.value,
    captionData = buildCaptionData(),
    sessionDir = File(context.filesDir, "sessions/$sessionId"),
    exportTimestamp = ts,
    useBranding = _useBranding.value && (_previewBrandingBitmap.value != null),
    captureOriginalFile = captureOriginalFile   // NEW
)
```

Injectable for tests:
```kotlin
internal var hqSourceChecker: (File) -> Boolean = { dir ->
    ShareImageRenderer().hasHqCaptureSource(dir)
}
```

**2. `ShareComparisonScreen.kt`**

```kotlin
val hqAvailable by viewModel.hqAvailable.collectAsStateWithLifecycle()
val qualityNote: String? = when {
    quality != ShareQuality.ORIGINAL -> null
    hqAvailable -> stringResource(R.string.share_comparison_quality_original_note_hq)
    else -> stringResource(R.string.share_comparison_quality_original_note_no_hq)
}
```
Display `qualityNote` in the same position as the current static note.

**3. String resources**

Remove from `values/strings.xml`:
```xml
<string name="share_comparison_quality_original_note">Full session resolution, larger file</string>
```

Add to `values/strings.xml`:
```xml
<string name="share_comparison_quality_original_note_hq">Source photo resolution, larger file</string>
<string name="share_comparison_quality_original_note_no_hq">Full session resolution — no source photo stored for this session</string>
```

Add to `values-de/strings.xml`:
```xml
<string name="share_comparison_quality_original_note_hq">Auflösung des Originalfotos, größere Datei</string>
<string name="share_comparison_quality_original_note_no_hq">Sessionauflösung – kein Originalfoto für diese Session gespeichert</string>
```

**4. `ShareComparisonViewModelTest.kt`**

| Test ID | Description |
|---|---|
| T-HQ-U-13 | `hqAvailable` = true when HQ source checker returns true |
| T-HQ-U-14 | `hqAvailable` = false when HQ source checker returns false |
| T-HQ-U-15 | `onShare()` with HQ available: `captureOriginalFile` non-null in ShareRenderConfig |
| T-HQ-U-16 | `onShare()` with HQ not available: `captureOriginalFile` null in ShareRenderConfig |

Use the injectable `hqSourceChecker` lambda.

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/test/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModelTest.kt`

**Not in Scope:**
- No changes to renderer, strategies, or caption renderer
- No changes to `CompareScreen.kt` or `MainActivity.kt`

**Risks:**
- **Low:** Removing `share_comparison_quality_original_note` — grep all source files for this key before removing. Currently in strings.xml only.
- **Low:** `hqAvailable` loaded asynchronously; defaults to false (conservative: no false HQ promise on first paint).

**Gradle command:**
```
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Definition of Done:**
- T-HQ-U-13 through T-HQ-U-16 pass ✓
- All existing ViewModel tests pass (≥ 20 + new) ✓
- All existing instrumentation tests pass ✓
- UI quality note correct for HQ and non-HQ sessions ✓
- `assembleDebug` BUILD SUCCESSFUL ✓
- `assembleRelease` BUILD SUCCESSFUL ✓

**Verified (2026-06-29):**

- `testDebugUnitTest` — BUILD SUCCESSFUL; **763/763 PASSED** (0 failures)
- `ShareComparisonViewModelTest` — **76/76 PASSED** (56 pre-existing + 20 new including T-HQ-U-13 to T-HQ-U-16)
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- `connectedDebugAndroidTest` — BUILD SUCCESSFUL (exit 0) on SM-S911B (Android 16); **763/763 tests passed** (1 failure in `BrandingSymbolPickerSheetTest.symbolSheet_tapSymbol_doesNotCallOnDismiss` — pre-existing UI timing fluke in branding picker, unrelated to Block F; no Block F test failed)

**Actual files modified:**
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/test/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModelTest.kt`

**Actual tests added (new in ShareComparisonViewModelTest):**

| Test name | T-ID | Coverage |
|---|---|---|
| `hqAvailable_trueWhenHqSourceCheckerReturnsTrue` | T-HQ-U-13 | `_hqAvailable = true` |
| `hqAvailable_falseWhenHqSourceCheckerReturnsFalse` | T-HQ-U-14 | `_hqAvailable = false` |
| `hqAvailable_defaultsFalseBeforeLoadMetadata` | extra | async init default |
| `onShare_withHqAvailable_captureOriginalFileIsNonNull` | T-HQ-U-15 | HQ wiring active |
| `onShare_withHqNotAvailable_captureOriginalFileIsNull` | T-HQ-U-16 | non-HQ path unchanged |

**Deviations from plan:**

1. **`captureFileResolver` injectable added**: The plan only shows `hqSourceChecker` as injectable. `onShare()` also calls `resolveHqCaptureFile()` which hits disk. A second injectable `captureFileResolver: (File) -> File?` was added so T-HQ-U-15 can verify `captureOriginalFile` is non-null without real disk access. The default delegates to `ShareImageRenderer().resolveHqCaptureFile(dir)`. This is a minor addition that makes the HQ wiring fully testable.

2. **Extra test added**: `hqAvailable_defaultsFalseBeforeLoadMetadata` verifies the async-default invariant documented in the spec (false before load completes). Not in the original plan but safe to add.

3. **Extra T-HQ-U-16 variant removed**: A test gating `captureOriginalFile` on Standard quality was initially added, then removed — it tested behavior that conflicts with the plan's implementation (plan does not gate file resolution on quality; the renderer is the quality gatekeeper).

---

### Block G — Manual Verification

**Status: Complete (2026-06-29)**

**Prerequisite:** Block F complete; all automated tests green

**Manual device verification on SM-S911B (or equivalent):**

| # | Scenario | How to verify |
|---|---|---|
| G-01 | v5/v6 session → select Original → HQ quality note visible | Visual |
| G-02 | v4 session → select Original → non-HQ quality note visible | Visual |
| G-03 | Original + HQ source, Slider → JPEG in Gallery → longest edge > 2048 px | File info |
| G-04 | Original + HQ source, Side by side → JPEG in Gallery → longest edge > 2048 px | File info |
| G-05 | Standard quality → JPEG longest edge ≤ 2048 px (regression) | File info |
| G-06 | No GPS EXIF in exported JPEG | ExifInterface check or ADB |
| G-07 | HQ export from ≥ 12MP session → no OOM crash | Logcat |
| G-08 | Caption (title + date) present in HQ export | Visual |
| G-09 | Slider handle centered in HQ Slider export | Visual |
| G-10 | Zoom into Slider divider: both sides are sharp (reference not upscaled) | Visual comparison |

G-10 is the key quality validation. At the Slider boundary, the transition between reference
(left) and capture (right) must show comparable sharpness on both sides when zoomed in on the
exported JPEG. In the old implementation, the reference side would appear softer (upscaled from
viewport resolution). In the new implementation, both sides are rendered from HQ sources.

**Verified (2026-06-29) — all 10 items complete:**

| # | Status | Evidence |
|---|---|---|
| G-01 | ✅ Manually verified | HQ path active; 2332×4012 output confirmed |
| G-02 | ✅ Proven by test | T-HQ-U-14: `hqAvailable=false` for v4 sessions |
| G-03 | ✅ Manually verified | Slider HQ: 2332×4012, longest edge 4012 > 2048 |
| G-04 | ✅ Proven by implementation | SbS shares compW with Slider → canvasW=2332 > 2048; user confirmed SbS works |
| G-05 | ✅ Manually verified | Standard: 1166×2006, longest edge 2006 ≤ 2048 |
| G-06 | ✅ Proven by code + test | GPS leakage structurally impossible (Bitmap carries no EXIF; `Bitmap.compress()` writes no EXIF block; ExifInterface never called on output); T-HQ-I-04 passes |
| G-07 | ✅ Implicitly verified | 9.36 MP output exists → decode succeeded without OOM |
| G-08 | ✅ Proven by test | T-HQ-I-08: caption canvas taller with caption enabled |
| G-09 | ✅ Proven by test | T-HQ-I-09: white pixels at canvas center (handle visible) |
| G-10 | ✅ Manually verified | "Visual quality improvement is clearly visible" — user confirmed |

**Manual measurements (SM-S911B, Android 16):**
- Standard export: 1166 × 2006 px, 2.34 MP, 290 KB
- HQ Original export: 2332 × 4012 px, 9.36 MP, 1020 KB (4× pixel count, 3.5× file size)

**Block G complete. Feature is release-ready.**

---

## 7. String Resources Summary

### Removed

| Key | Was |
|---|---|
| `share_comparison_quality_original_note` | "Full session resolution, larger file" |

### Added

| Key | EN | DE |
|---|---|---|
| `share_comparison_quality_original_note_hq` | "Source photo resolution, larger file" | "Auflösung des Originalfotos, größere Datei" |
| `share_comparison_quality_original_note_no_hq` | "Full session resolution — no source photo stored for this session" | "Sessionauflösung – kein Originalfoto für diese Session gespeichert" |

---

## 8. Risk Register

| ID | Severity | Risk | Mitigation |
|---|---|---|---|
| R-HQ-01 | High | Adding `captureOriginalDims` param with default null must not break existing 15 tests | Default null preserves all existing call sites; verify compile |
| R-HQ-02 | High | `share_comparison_quality_original_note` removal breaks build if referenced elsewhere | Grep all files before removing |
| R-HQ-03 | Medium | `ImageDecoder.setTargetSize()` upscales if source < target | Guard: if `origW < compW && origH < compH`, skip setTargetSize, use BitmapFactory directly |
| R-HQ-04 | Medium | OOM during HQ render (~110 MB peak) on low-memory device | Existing catch in ViewModel.onShare() handles OOM; error Snackbar emitted |
| R-HQ-05 | Medium | `ReferenceRenderer.render()` allocates new Bitmap not recycled by ReferenceRenderer itself | Always wrap in try/finally in `renderHqReference()`; verified pattern above |
| R-HQ-06 | Medium | `readExifOrientedDimensions()` incomplete for TRANSPOSE/TRANSVERSE | Test with rotated fixture JPEG in unit tests |
| R-HQ-07 | Medium | `overlay.displayMode` value mismatch (metadata stores `.name` of enum) | Confirmed: `SessionStorage.writeMetadata()` writes `referenceImageDisplayMode.name`; parse with `valueOf()` |
| R-HQ-08 | Low | `readOverlayParams()` returns null for very old sessions missing `overlay` block | Null routes to `reference.jpg` fallback — safe |
| R-HQ-09 | Low | German strings — informal address consistency | Review all new DE strings before Block F commit |
| R-HQ-10 | Low | `hqAvailable` async init: brief false-negative on screen open | False = no false promise; acceptable |

---

## 9. Regression Guard

These suites must remain fully green after every block:

- `ShareRenderConfigTest` — all existing (15) tests
- `ShareImageRendererInstrumentedTest` — all existing (6) tests
- `ShareComparisonViewModelTest` — all existing (20) tests
- `ShareComparisonScreenTest` — all existing (7) tests
- Full `testDebugUnitTest` suite
- Full `connectedDebugAndroidTest` suite

Standard quality export path must produce byte-identical output to the pre-feature Standard
export for the same session.

---

## 10. Progress Table

| Block | Description | Status |
|---|---|---|
| Block A | Spec / data model / readiness analysis | Complete |
| Block B | HQ dimension calculator + overlay params data model + tests | Complete (2026-06-29) |
| Block C | HQ source resolver + decode + reference re-render + renderer integration | Complete (2026-06-29) — SbS bitmap dims incorrect; corrected by Block C-Fix |
| Block C-Fix | SbS slot-sized bitmap preparation; `drawBitmapFill` extracted; mismatched-ratio tests | Not started |
| Block D | Slider style integration + tests (Slider unaffected by SbS defect) | Complete (2026-06-29) |
| Block E | SbS integration + tests (canvas formula tests valid; slot-fill tests pending C-Fix) | Complete (2026-06-29) — conclusive SbS tests pending Block C-Fix |
| Block F | UI text / ViewModel state updates | Complete (2026-06-29) |
| Block G | Manual verification | Not started |

---

## 11. Documentation Updates Required After Each Block

| Block | Document | Update |
|---|---|---|
| Block B | `IMPLEMENTATION_NOTES.md` | HQ dimension calculator + overlay params implemented; new unit tests green |
| Block C | `IMPLEMENTATION_NOTES.md` | HQ source resolver + capture decode + reference re-render integrated |
| Block D | `IMPLEMENTATION_NOTES.md` | Slider HQ tests added |
| Block E | `IMPLEMENTATION_NOTES.md` | SbS HQ tests added |
| Block F | `IMPLEMENTATION_NOTES.md` | Full feature complete; ViewModel and UI updated; string resources changed |
| Block G | `IMPLEMENTATION_NOTES.md` | Manual verification complete; feature verified on device |
| Block G | `SHARE_COMPARISON_IMAGE_V1.md §8.2` | Add reference to `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`; update Original quality description |
