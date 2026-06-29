# SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md

## 1. Document Status

This document is the **authoritative specification** for the Original quality tier upgrade in the
Share Comparison Image feature of SameView.

It supersedes and extends `SHARE_COMPARISON_IMAGE_V1.md §8.2` (Original quality definition).
All other sections of `SHARE_COMPARISON_IMAGE_V1.md` remain unchanged.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.
Where this document is silent, `SHARE_COMPARISON_IMAGE_V1.md` wins.

**Revision note (2026-06-29 — initial):** Initial draft used an upscaled `reference.jpg` on the
reference side. A follow-up feasibility analysis confirmed that HQ reference re-rendering using
`reference-original.jpg` + `ReferenceRenderer.render()` is fully possible with current metadata
and requires no schema changes. The spec was updated accordingly.

**Revision note (2026-06-29 — SbS architecture):** Manual validation identified that the initial
HQ bitmap preparation strategy — preparing bitmaps at the full comparison-area dimensions
`(compW, compH)` — is correct for Slider but produces incorrect results for Side-by-side.
In Side-by-side, each image occupies an independent slot of width `compW/2`. Preparing bitmaps
at the full `compW` target causes portrait sources to be decoded as landscape, which then produces
massive letterbox bands when Fit-scaled into the portrait slot. The spec has been updated to
document the correct style-aware bitmap preparation architecture.

---

## 2. Problem Statement

The current Share Comparison Image feature offers two quality tiers: **Standard** and **Original**.

Original is currently defined as using the session viewport dimensions from `metadata.json` with
no upscaling cap (see `SHARE_COMPARISON_IMAGE_V1.md §8.2`). In practice:

- The camera preview viewport for most phones is 1080×1920 or similar (longest edge ≤ 2048 px).
- Standard quality caps the longest edge at 2048 px, so for these sessions Standard = Original.
- For a session with viewport 1080×1920: Standard produces a 1080×1920 canvas. Original also
  produces a 1080×1920 canvas. The two exports are **byte-identical**.
- The "Full session resolution, larger file" hint shown when Original is selected is misleading —
  there is no larger file.

The app already stores true high-resolution originals for v5/v6 sessions:
- `capture-original.jpg`: byte-for-byte copy of the quality-95 MediaStore JPEG at full camera
  sensor resolution. This is the highest-quality representation of the new photo the user took.
- `reference-source-original.<ext>`: byte-for-byte copy of the original reference source.
- `reference-original.jpg`: EXIF-oriented, quality-90 re-encoded reference image.

`reference-original.jpg` is present in **all session versions (v2–v6)** — it predates Session
Originals and is the long-established full-resolution reference file.

These files were explicitly introduced to enable future high-quality export and print workflows
(see `SESSION_ORIGINALS_V1.md §12`). This feature activates that use case for both sides of
the comparison.

---

## 3. Source of Truth Review

### 3.1 Current Rendering Pipeline

The current `ShareImageRenderer.render()` traces through:

```
ShareComparisonViewModel.onShare()
  └── ShareImageRenderer.render(config, contentResolver)
        ├── readSessionViewport() → reads metadata.json viewport.width/height
        │     └── fallback: BitmapFactory.inJustDecodeBounds on capture.jpg
        ├── computeCanvasDimensions(viewportW, viewportH, quality, captionData, style)
        │     ├── STANDARD: scale if longest edge > 2048 px
        │     └── ORIGINAL: use viewport dimensions directly (no cap)
        ├── BitmapFactory.decodeFile("reference.jpg")
        ├── BitmapFactory.decodeFile("capture.jpg")
        ├── SliderRenderStrategy or SideBySideRenderStrategy → renders to canvas Bitmap
        ├── CaptionRenderer → renders text onto canvas
        └── ShareMediaStoreWriter → IS_PENDING write → JPEG quality 92
```

**Source files used today**: `reference.jpg` and `capture.jpg` — both are viewport-sized files.

### 3.2 Session File Availability by Version

| File | v2 | v3 | v4 | v5 | v6 |
|---|---|---|---|---|---|
| `capture.jpg` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `reference.jpg` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `reference-original.jpg` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `capture-original.jpg` | — | — | — | ✓ | ✓ |
| `reference-source-original.<ext>` | — | — | — | ✓ | ✓ |
| `branding-handle.png` | — | — | — | — | optional |

`SessionScanner.SUPPORTED_VERSIONS = {2, 3, 4, 5, 6}`

`reference-original.jpg` is present in all versions and is always EXIF-oriented. It is the
primary HQ reference source for this feature.

### 3.3 Metadata Available for HQ Rendering

`metadata.json` contains all parameters required to re-render the reference at any resolution.
All fields have been present since v2.

| Field | JSON path | Role |
|---|---|---|
| Schema version | `version` | Detect HQ capture source availability |
| Viewport width | `viewport.width` | Canvas aspect ratio and scale basis |
| Viewport height | `viewport.height` | Canvas aspect ratio and scale basis |
| Overlay scale | `overlay.scale` | ReferenceRenderer parameter |
| Overlay offset X | `overlay.offsetX` | ReferenceRenderer parameter (normalized fraction) |
| Overlay offset Y | `overlay.offsetY` | ReferenceRenderer parameter (normalized fraction) |
| Display mode | `overlay.displayMode` | ReferenceRenderer parameter |
| EXIF orientation | `reference.exifOrientation` | Reference source orientation (not needed for reference-original.jpg which is pre-oriented) |
| Capture original filename | `files.captureOriginal` | Locate capture-original.jpg (v5/v6 only) |

### 3.4 What reference.jpg Represents

`reference.jpg` is a deterministically rendered file created by `ReferenceRenderer.render()` at
session creation time, limited to the session viewport dimensions. It encodes the exact aligned
composition at viewport resolution. It is the source for **Standard quality** exports and for
the **live preview** in `ShareComparisonScreen`.

`reference.jpg` is **not used** for Original quality exports in this feature. Original quality
re-renders the reference from `reference-original.jpg` at the HQ target dimensions using the
same overlay parameters stored in `metadata.json`.

### 3.5 What reference-original.jpg Represents

`reference-original.jpg` is the EXIF-oriented, quality-90 re-encoded reference image. It is:
- Present in all session versions (v2–v6)
- Already EXIF-oriented (no separate orientation step required)
- At the full source image resolution (not limited to viewport)
- The same source bitmap that `ReferenceRenderer.render()` used at session creation time

This is the primary HQ reference source for this feature. It provides HQ on the reference side
for all sessions — not just v5/v6.

### 3.6 Why reference-source-original Cannot Be Used Directly

`reference-source-original.<ext>` (v5/v6 only) is a raw byte copy of the original source file.
It has no overlay transform, no viewport crop, and may be HEIC, PNG, AVIF, or other formats.
It cannot be used as a drop-in reference source without format decoding, EXIF orientation, and
the full ReferenceRenderer transform pipeline.

Using `reference-source-original.<ext>` in a future enhancement would provide marginally higher
reference quality (avoiding the quality-90 re-encode to `reference-original.jpg`). This is the
documented future enhancement (see §6 Non-Goals). It is **not in scope for this feature**.

### 3.7 What capture-original.jpg Represents

`capture-original.jpg` is a byte-for-byte copy of the MediaStore JPEG (quality 95). It is the
highest-quality representation of the user's captured photo. Key properties:

- May include EXIF orientation tag (device was rotated during capture)
- Full camera sensor resolution (e.g., 12MP: 3024×4032 or 4032×3024)
- May have a **different aspect ratio** than the session viewport (e.g., 3:4 capture vs 9:16
  preview viewport)
- Contains all EXIF tags including GPS when Recreation Guidance was active
- Present only in v5/v6 sessions; validated by SessionScanner

For HQ export with **Slider style** (Fill semantics on full comparison area), the center crop
of capture-original.jpg at `(compW, compH)` matches the visual composition of capture.jpg at
higher resolution.

For HQ export with **Side-by-side style**, the capture-original.jpg is center-cropped to the
viewport aspect ratio and prepared at slot dimensions `(compW/2, compH)`. This eliminates
letterboxing while preserving the correct image proportions — the crop matches the same
center-of-sensor area that was captured at full resolution. See §5.2 for the full architecture.

### 3.8 Existing Test Coverage

Directly relevant existing tests:
- `ShareRenderConfigTest` — 15/15 unit tests covering canvas dimensions, even numbers, Standard/Original quality
- `ShareImageRendererInstrumentedTest` — 6/6 end-to-end JPEG export tests (Slider/SbS, Standard/Original, caption ON/OFF)
- `ShareComparisonViewModelTest` — 20/20 unit tests covering state and caption building

These tests must remain green after this feature. The Standard quality path must be regression-free.

### 3.9 Document Conflicts

**SHARE_COMPARISON_IMAGE_V1.md §8.2 (Original quality)**:

Current text: "Canvas dimensions derived from session `viewport.width` / `viewport.height`
stored in `metadata.json`." and "Wizard hint: `share_comparison_quality_original_note` =
'Full session resolution, larger file'"

This document supersedes §8.2. After this feature:
- Canvas dimensions for Original are derived from `capture-original.jpg` dimensions when
  available (v5/v6), or from viewport when not (v2/v3/v4).
- The hint text is dynamic.
- `SHARE_COMPARISON_IMAGE_V1.md §8.2` must be updated to reference this document.

**No other conflicts detected** between `SESSION_ORIGINALS_V1.md`, `COMPARE_SESSION_RENDERING_V1.md`,
`COMPARE_FLOW_V1.md`, `IMPLEMENTATION_NOTES.md`, and this feature.

---

## 4. Fixed Product Decisions

| # | Decision |
|---|---|
| HQ-FD-01 | Original quality uses `capture-original.jpg` as the capture-side HQ source when available (v5/v6 sessions). |
| HQ-FD-02 | Original quality uses `reference-original.jpg` as the reference-side HQ source. `reference-original.jpg` is present in all session versions (v2–v6) and is already EXIF-oriented. It is passed to `ReferenceRenderer.render()` with the overlay parameters from `metadata.json` at the HQ target dimensions. |
| HQ-FD-03 | The HQ reference re-render uses `ReferenceRenderer.render()` — the same function that created `reference.jpg` at session creation time. No new rendering algorithm is required. The overlay offsets stored in `metadata.json` are normalized viewport fractions; the composition is mathematically guaranteed to be identical to the original `reference.jpg` at any target resolution that maintains the viewport aspect ratio (see §5.3). |
| HQ-FD-04 | For v2/v3/v4 sessions (no `capture-original.jpg`): the capture side falls back to `capture.jpg`; the reference side still uses `reference-original.jpg` + `ReferenceRenderer.render()`. Canvas dimensions come from the session viewport (no 2048 px cap). |
| HQ-FD-05 | Canvas aspect ratio for Original always follows the session viewport, not the capture-original.jpg dimensions. The viewport ratio defines the comparison geometry. |
| HQ-FD-06 | Maximum output longest edge for Original quality: **3840 px**. This gives 4K-equivalent output while keeping peak memory to a manageable level on supported Android devices (minSdk 29). |
| HQ-FD-07 | JPEG output quality remains 92% (unchanged from `SHARE_COMPARISON_IMAGE_V1.md §8.4` FD-08). |
| HQ-FD-08 | HQ bitmap preparation is **style-aware**. Bitmaps are prepared for their final rendering destination: **Slider** receives bitmaps sized at `(compW, compH)`; **Side-by-side** receives bitmaps sized at `(compW/2, compH)` (slot dimensions). `capture-original.jpg` is always decoded with EXIF orientation awareness via `ImageDecoder.decodeBitmap()` (API 28+, minSdk 29). For Side-by-side, the decode includes a center-crop to the viewport aspect ratio to avoid letterboxing and distortion. |
| HQ-FD-09 | No EXIF metadata from `capture-original.jpg` or `reference-original.jpg` is copied to the exported JPEG. The output is a freshly rendered composite with no EXIF block. |
| HQ-FD-10 | The UI must not claim "larger file" when the session has no HQ capture source. The quality note text is dynamic. |
| HQ-FD-11 | On OOM or decode failure during HQ render: emit an error Snackbar. No silent failure. |
| HQ-FD-12 | The Standard quality path is unchanged. No regression to existing Standard or Original behavior for sessions already benefiting from viewport > 2048 px. |
| HQ-FD-13 | The `ShareComparisonStyle.SLIDER` and `ShareComparisonStyle.SIDE_BY_SIDE` render strategies are unchanged and remain completely source-agnostic. They receive bitmaps that have already been prepared at the correct dimensions for their rendering destination. `SideBySideRenderStrategy` receives bitmaps at `(compW/2, compH)` slot dimensions; applying Fit semantics to these yields scale=1.0 — the bitmaps fill the slots exactly. The strategy has no knowledge of HQ mode, original files, EXIF orientation, or crop logic. |

---

## 5. Design Question Answers

### 5.1 What Does Standard Mean?

Standard quality is **unchanged**:
- Canvas longest edge capped at 2048 px, preserving session viewport aspect ratio.
- Source bitmaps: `capture.jpg` and `reference.jpg` (viewport-sized files).
- Always fast (small files, predictable memory).
- Typical output: 200–800 KB.

No changes to `computeCanvasDimensions()` for `ShareQuality.STANDARD`.

### 5.2 What Does Original / High Quality Mean?

Original quality uses the highest-quality available source on **both sides** of the comparison.
Bitmaps are prepared at the correct dimensions for their **rendering destination** — not for the
full canvas. This ensures no letterboxing, no distortion, and no unused dark space.

#### Architecture: Style-aware bitmap preparation

The renderer (`ShareImageRenderer`) selects the bitmap target dimensions based on the export style:

| Style | Capture target | Reference target | RenderRenderer dims |
|---|---|---|---|
| **Slider** | `(compW, compH)` — full comparison area | `(compW, compH)` | `(compW, compH)` |
| **Side-by-side** | `(compW/2, compH)` — per-slot | `(compW/2, compH)` | `(compW/2, compH)` |

`SideBySideRenderStrategy` receives bitmaps that already have slot dimensions. Applying Fit
semantics to a `(compW/2, compH)` bitmap in a `(compW/2, compH)` slot yields scale=1.0 —
the bitmap fills the slot exactly with no computation needed in the strategy itself.

**Why slot-sized bitmaps for Side-by-side:**
The SbS comparison area has width `compW` but each image occupies only `compW/2`. If bitmaps
are prepared at the full `compW`, a portrait source (e.g., 3:4 from a phone camera) is decoded
to landscape dimensions (`compW × compH`), which then produce ~480 px letterbox bands in a
portrait slot. Preparing at `(compW/2, compH)` — which has the viewport aspect ratio — avoids
this entirely.

#### Capture preparation for Side-by-side

`capture-original.jpg` has the camera's native aspect ratio (typically 3:4 for portrait mode)
which differs from the viewport's aspect ratio (typically 9:16). To fill the `(compW/2, compH)`
slot without letterboxing AND without distortion, a **center-crop** to the viewport aspect ratio
is applied before scaling:

```
Viewport ratio: viewportW : viewportH  (e.g. 9:16)
Source: captureOriginalW × captureOriginalH (e.g. 3024×4032, 3:4)

Center-crop to viewport ratio at source height:
  cropW = captureOriginalH × (viewportW / viewportH)
         = 4032 × (9/16) = 2268
  cropX = (captureOriginalW - cropW) / 2 = 378  [center]
  Crop region: 2268×4032

Scale cropped region to slot: 2268×4032 → (compW/2) × compH  (e.g. 1080×1920)
```

This is equivalent to Fill semantics on the source — the same center-crop behaviour that
Slider uses, but applied during the decode/preparation phase so the strategy receives a
pre-prepared slot-sized bitmap and requires no crop logic of its own.

#### For v5/v6 sessions (have `capture-original.jpg`):
- Canvas size: viewport aspect ratio, scaled to match `capture-original.jpg` EXIF-oriented
  resolution, capped at 3840 px on the longest edge. See §5.5 for the exact formula.
- Capture bitmap: `capture-original.jpg`, center-cropped to viewport ratio and scaled to
  `(compW, compH)` for Slider or `(compW/2, compH)` for Side-by-side.
- Reference bitmap: `reference-original.jpg` passed to `ReferenceRenderer.render()` with
  `(compW, compH)` for Slider or `(compW/2, compH)` for Side-by-side.
- Result: both slots filled entirely with HQ content, 4× more pixels than Standard per slot.

#### For v2/v3/v4 sessions (no `capture-original.jpg`):
- Canvas size: viewport dimensions with no Standard cap.
- Capture source: `capture.jpg` (viewport-sized, 9:16). For SbS slots (compW/2 × compH), a
  viewport-sized capture fills the slot exactly (same aspect ratio as slot).
- Reference source: `reference-original.jpg` + `ReferenceRenderer.render()` at viewport
  dimensions for Slider or slot dimensions for Side-by-side.
- When viewport longest edge ≤ 2048 px: canvas matches Standard. The UI communicates this.
- The Original option remains selectable.

**Product rationale**: A user selecting Original quality expects both sides of the comparison to
use the highest practically available source quality with no visual artefacts. Preparing each
bitmap for its actual rendering destination eliminates letterboxing, distortion, and asymmetric
rendering semantics while keeping the render strategies source-agnostic.

### 5.3 Can True HQ Preserve Overlay Geometry?

**Yes, fully.** The composition is mathematically guaranteed to be identical.

#### Why the Math Works

`ReferenceRenderer.render()` stores overlay offsets as **normalized viewport fractions**. The
actual pixel translation is computed as `overlayOffsetX × viewportWidth` and
`overlayOffsetY × viewportHeight`. When the viewport dimensions change (scaled uniformly to
maintain aspect ratio), the offset scales automatically:

```kotlin
// From ReferenceRenderer.render() — COMPARE_WITH_PREVIEW mode:
val tX = (overlayOffsetX * vW).coerceIn(-maxTX, maxTX)
val tY = (overlayOffsetY * vH).coerceIn(-maxTY, maxTY)

// SHOW_FULL_IMAGE mode:
val tX = overlayOffsetX * vW
val tY = overlayOffsetY * vH
```

#### Proof of Composition Identity

The center source pixel visible in the comparison is:

```
sx = iW/2 - overlayOffsetX × (vW / fillScale)
```

For uniform viewport scaling by factor `k` (maintaining aspect ratio):
- `vW_new = k × vW_old`
- `fillScale_new = k × fillScale_old` (Fill/Fit scale is linear in the viewport)
- Therefore: `vW_new / fillScale_new = vW_old / fillScale_old = constant`

The visible source pixel `sx` is identical at all viewport sizes — the composition is preserved.

The `coerceIn` clamp bounds also scale proportionally:
- `maxTX_new = (scaledW_new − vW_new) / 2 = k × maxTX_old`
- The clamp ratio `maxTX / vW` is constant across all scales

No special-casing for `SHOW_FULL_IMAGE` mode is needed: the proof holds for both modes since
neither introduces scale-dependent terms that would break the invariant.

#### All Required Parameters Are Present in metadata.json

| Parameter | JSON path | Stored since |
|---|---|---|
| `overlayScale` | `overlay.scale` | v2 |
| `overlayOffsetX` | `overlay.offsetX` (normalized fraction) | v2 |
| `overlayOffsetY` | `overlay.offsetY` (normalized fraction) | v2 |
| `referenceImageDisplayMode` | `overlay.displayMode` | v2 |
| `viewportWidth` | `viewport.width` | v2 |
| `viewportHeight` | `viewport.height` | v2 |
| EXIF-oriented reference source | `reference-original.jpg` | all versions |

No new metadata fields are needed. No schema changes are required.

#### No New Rendering Algorithm

`ReferenceRenderer.render()` is the exact existing function. It is called with the HQ target
dimensions as `viewportWidth`/`viewportHeight` and the stored overlay parameters unchanged.
The function output is a Bitmap at the new HQ size with identical composition.

### 5.4 Which Files Are Authoritative for HQ?

| File | Role | Used in this feature? |
|---|---|---|
| `capture-original.jpg` | Primary HQ capture source (v5/v6) | **Yes** |
| `capture.jpg` | Standard capture + v2/v3/v4 fallback | Yes — Standard and non-HQ Original |
| `reference-original.jpg` | Primary HQ reference source (all versions) | **Yes** |
| `reference.jpg` | Standard reference + live preview | Yes — Standard only |
| `reference-source-original.<ext>` | Raw reference bytes (v5/v6) | No — future enhancement |

**Capture-side fallback hierarchy:**
1. `capture-original.jpg` declared in `files.captureOriginal` AND file exists → HQ capture
2. Missing/unreadable → `capture.jpg` (same as Standard)

**Reference-side:** always `reference-original.jpg` + `ReferenceRenderer.render()` for
Original quality. No fallback needed — `reference-original.jpg` is present in all versions.

**If `reference-original.jpg` is missing** (unexpected, would only happen with a corrupt session):
fall back to `reference.jpg` for the reference side. SessionScanner already validates that
`files.reference` exists on disk; `reference-original.jpg` should always be present.

### 5.5 Output Dimensions for Original Quality

#### Canvas Aspect Ratio

The canvas aspect ratio always follows the session viewport ratio: `viewportW / viewportH`.

The viewport defines the comparison geometry. The capture and reference were both rendered at
this aspect ratio. Deviating from it would change the composition.

#### Canvas Size Formula for HQ (v5/v6 with capture-original.jpg)

```
// 1. Get EXIF-oriented dimensions of capture-original.jpg
val (origOrientedW, origOrientedH) = getExifOrientedDimensions(captureOriginalFile)

// 2. Largest viewport-ratio canvas that fits within capture-original.jpg dimensions
//    AND within the 3840 px HQ cap
val scaleByWidth  = origOrientedW.toFloat() / viewportW
val scaleByHeight = origOrientedH.toFloat() / viewportH
val capScale      = MAX_HQ_LONGEST_EDGE.toFloat() / maxOf(viewportW, viewportH)
val scale         = minOf(scaleByWidth, scaleByHeight, capScale).coerceAtLeast(1f)

// 3. Apply scale at viewport ratio; enforce even numbers
val compW     = makeEven((viewportW * scale).toInt())
val compHBase = makeEven((viewportH * scale).toInt())
```

Where `MAX_HQ_LONGEST_EDGE = 3840`.

The `.coerceAtLeast(1f)` ensures no downscaling for Original quality.

#### Canvas Size Formula for Non-HQ-Capture Original (v2/v3/v4)

```
val compW     = makeEven(viewportW)
val compHBase = makeEven(viewportH)
// No cap applied
```

The reference is still re-rendered at these viewport dimensions via `ReferenceRenderer.render()`.

#### Side by Side Height Adjustment

Unchanged from the current implementation:
```
val compH = when (style) {
    SLIDER       -> compHBase
    SIDE_BY_SIDE -> makeEven(compHBase / 2)
}
```

#### Even Numbers

All canvas and comparison area dimensions are even integers. Enforced by `makeEven()`.

#### Caption Scaling

Caption text sizes are fractions of `min(compW, compH)` — scale proportionally with the canvas.
Handle geometry and divider proportions also scale proportionally.

### 5.6 Memory and OOM Safety

This is an explicit requirement.

#### Peak Memory Budget — Slider HQ

During Slider HQ render (example: viewport 1080×1920, scale=2.0, bitmaps at 2160×3840):

| Bitmap | Source | Typical size | Peak bytes (ARGB_8888) |
|---|---|---|---|
| capture decoded + cropped to Slider dims | `capture-original.jpg` | 2160×3840 | ~31 MB |
| reference-original decoded | `reference-original.jpg` | viewport-sized | ~8 MB |
| ReferenceRenderer output (Slider dims) | `ReferenceRenderer.render()` | 2160×3840 | ~31 MB |
| Canvas bitmap (Slider) | allocated once | ~2332×3992 | ~35 MB |

**Slider peak total: ~105 MB** (before early recycling).

#### Peak Memory Budget — Side-by-side HQ

During Side-by-side HQ render (same session, bitmaps at slot dims 1080×1920):

| Bitmap | Source | Typical size | Peak bytes (ARGB_8888) |
|---|---|---|---|
| capture decoded + cropped to slot dims | `capture-original.jpg` | 1080×1920 | ~8 MB |
| reference-original decoded | `reference-original.jpg` | viewport-sized | ~8 MB |
| ReferenceRenderer output (slot dims) | `ReferenceRenderer.render()` | 1080×1920 | ~8 MB |
| Canvas bitmap (SbS) | allocated once | ~2332×2072 | ~18 MB |

**Side-by-side peak total: ~42 MB** — significantly lower than Slider HQ because slot-sized
bitmaps are used, not full-canvas bitmaps.

`reference-original.jpg` is recycled immediately after `ReferenceRenderer.render()` completes.
`ReferenceRenderer` output is recycled after it has been drawn onto the canvas.

#### Maximum Output Constraint

- **Maximum longest edge**: 3840 px (HQ-FD-06)
- **Maximum pixel count**: ~11 MP for 4:3 aspect ratio, ~8 MP for 16:9
- This caps peak canvas bitmap at ≤ 56 MB

#### Decode Strategy for capture-original.jpg — Slider

For Slider, capture-original.jpg is decoded to the full comparison area dimensions:

```kotlin
// imgW = compW, imgH = compH  (full Slider comparison area)
ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    if (imgW < info.size.width || imgH < info.size.height) {
        decoder.setTargetSize(imgW, imgH)
    }
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
}
```

EXIF orientation is applied automatically. `setTargetSize` downsamples efficiently.

#### Decode Strategy for capture-original.jpg — Side-by-side

For Side-by-side, capture-original.jpg is center-cropped to the viewport aspect ratio and
scaled to the slot dimensions `(compW/2, compH)`. Because capture-original.jpg typically has
a different aspect ratio than the slot (e.g., 3:4 sensor vs. 9:16 viewport), non-uniform
scaling is incorrect (distortion) and simple Fit is incorrect (letterboxing). Center-crop is
the only approach that delivers no distortion and no letterboxing:

```
slotW = compW / 2
slotH = compH
viewportRatioW = viewportW  (numerator of viewport ratio)
viewportRatioH = viewportH  (denominator)

Step 1: Read EXIF-oriented source dims (srcW, srcH) from readExifOrientedDimensions()

Step 2: Determine center-crop region at source scale:
  // Crop to viewport ratio, bounded by source dims.
  // Use height as the fixed dimension when width permits enough horizontal coverage.
  cropH = srcH
  cropW = srcH × viewportW / viewportH
  if (cropW > srcW):
      // Source too narrow — fix width, crop height instead
      cropW = srcW
      cropH = srcW × viewportH / viewportW
  cropX = (srcW - cropW) / 2   [center horizontally]
  cropY = (srcH - cropH) / 2   [center vertically]

Step 3: Decode source with this crop, target output (slotW, slotH).
  Implementation: decode to natural ratio at appropriate inSampleSize, then render
  to (slotW, slotH) canvas via drawBitmapFill(). The drawBitmapFill utility
  already implements Fill semantics (center crop + scale) and is extracted as an
  internal package-level function for reuse by ShareImageRenderer.
```

The resulting bitmap is exactly `slotW × slotH` with viewport-ratio content, no letterboxing,
no distortion.

#### Decode Strategy for reference-original.jpg

```kotlin
val refOrigBitmap = BitmapFactory.decodeFile(referenceOriginalFile.absolutePath)
    ?: throw IOException("Cannot decode reference-original.jpg")
// Already EXIF-oriented — no ExifInterface step needed.
// imgW and imgH are the target dimensions: (compW, compH) for Slider,
// (compW/2, compH) for Side-by-side.
val hqRefBitmap = ReferenceRenderer.render(
    sourceBitmap  = refOrigBitmap,
    viewportWidth  = imgW,
    viewportHeight = imgH,
    overlayScale   = overlayParams.scale,
    overlayOffsetX = overlayParams.offsetX,
    overlayOffsetY = overlayParams.offsetY,
    displayMode    = overlayParams.displayMode
)
refOrigBitmap.recycle()
```

For Side-by-side, passing `(compW/2, compH)` as the viewport dimensions to ReferenceRenderer
produces a bitmap at slot dimensions. The composition proof from §5.3 holds: the slot has the
same aspect ratio as the viewport (both use `viewportW : viewportH`), so the normalized offsets
produce an identical composition at the smaller scale.

`reference-original.jpg` is typically viewport-sized (~8 MB decoded) — full decode without
`setTargetSize` is appropriate and safe.

#### Fallback on Decode Failure or OOM

Any exception during HQ decode (including `OutOfMemoryError`) must:
1. Recycle any partially allocated bitmaps immediately
2. Emit an error Snackbar: `share_comparison_error_render_failed`

The `share_comparison_error_render_failed` string ("Could not create image") covers this case.
No new error string is needed.

#### Lifecycle

The `try/finally` bitmap recycle pattern in `ShareImageRenderer.render()` covers all HQ bitmaps:

```kotlin
try {
    // render
} finally {
    capBitmap?.recycle()
    refBitmap?.recycle()
    brandingBitmap?.recycle()
}
```

Canvas bitmap is recycled in the outer `try/finally` block after the MediaStore write.

### 5.7 Old Sessions (v2/v3/v4)

For sessions without `capture-original.jpg`:
- Original quality **proceeds** — it does not crash or block.
- Canvas uses viewport dimensions (no 2048 px Standard cap).
- Capture source: `capture.jpg`.
- Reference source: `reference-original.jpg` + `ReferenceRenderer.render()` at viewport
  dimensions. Result is composition-identical to `reference.jpg`.
- When viewport longest edge ≤ 2048 px: canvas dimensions equal Standard. Content is the
  same as Standard (reference re-rendered from viewport-sized original; capture at viewport
  quality). Visually indistinguishable from Standard.
- UI communicates the capture HQ source situation (see §5.9).
- The Original option remains selectable — it is not disabled.

### 5.8 Sessions at Exactly 2048 px Viewport

After this feature:

- v5/v6 session: Original uses capture-original.jpg (typically > 2048 px). Standard and
  Original are no longer identical.
- v2/v3/v4 session: Standard and Original produce the same 2048 px canvas. Both reference paths
  converge to the same composition (though Original re-renders from reference-original.jpg).
  The UI notes this.

### 5.9 UI Wording

#### Quality Segment Labels

Unchanged: **Standard** / **Original**

#### Dynamic Quality Note

The quality note below the Original segment is dynamic:

| Condition | String resource |
|---|---|
| v5/v6 session + `capture-original.jpg` present | `share_comparison_quality_original_note_hq` |
| v2/v3/v4 session (no `capture-original.jpg`) | `share_comparison_quality_original_note_no_hq` |
| v5/v6 session but `capture-original.jpg` missing | `share_comparison_quality_original_note_no_hq` |

The note is shown only when **Original** segment is selected.

#### New String Resources

| Key | English | German |
|---|---|---|
| `share_comparison_quality_original_note_hq` | "Source photo resolution, larger file" | "Auflösung des Originalfotos, größere Datei" |
| `share_comparison_quality_original_note_no_hq` | "Full session resolution — no source photo stored for this session" | "Sessionauflösung – kein Originalfoto für diese Session gespeichert" |

The old key `share_comparison_quality_original_note` is **replaced** by these two dynamic keys.
All German strings use informal `du` address consistent with the project localization rules.

#### No "Original quality available" Positive Indicator

No badge, chip, or indicator proactively signals HQ availability. The dynamic note is sufficient.

### 5.10 Preview Behavior

The live preview in `ShareComparisonScreen` remains **unchanged**:
- Renders from `capture.jpg` and `reference.jpg` via Coil (viewport-sized).
- Compose-only simulation — not a bitmap render.
- Does not use `capture-original.jpg` or `reference-original.jpg`.
- Representative of layout and composition; not of final pixel resolution.

No "HQ preview" mode is introduced. Rendering large bitmaps in the UI thread would cause jank
or OOM.

### 5.11 Metadata / Privacy

The existing privacy contract from `SHARE_COMPARISON_IMAGE_V1.md §18` applies without change:

- No EXIF from `capture-original.jpg` or `reference-original.jpg` is copied to the output JPEG.
- `Bitmap.compress()` produces only pixel data.
- `ExifInterface` is never called on the output JPEG.
- GPS coordinates are not present in the output.
- `ImageDecoder.decodeBitmap()` and `BitmapFactory.decodeFile()` do not propagate EXIF to the
  returned Bitmap.

`reference-original.jpg` may contain GPS EXIF from the original reference source. This is
consumed only for bitmap pixel decoding; the EXIF block is discarded by BitmapFactory.

### 5.12 Branding / Handle / Captions

All of the following are unchanged and scale proportionally with canvas resolution:

- Slider handle design (standard and branding handle variants)
- Side by side separator line
- Caption typography fractions (text size = fraction of `min(compW, compH)`)
- Caption line spacing
- Canvas outer padding fraction (4% of shortest dimension)
- JPEG quality (92%)

### 5.13 Fallback Hierarchy

**Original quality export — complete decision tree:**

```
Definitions:
  imgW, imgH = bitmap target dimensions, style-dependent:
    Slider:       imgW = compW,     imgH = compH
    Side-by-side: imgW = compW / 2, imgH = compH

1. Read metadata.json
   ├── Parse files.captureOriginal → HQ capture available?
   │   ├── Non-empty AND file exists
   │   │   └── → HQ CAPTURE PATH
   │   └── Empty OR missing
   │       └── → NON-HQ CAPTURE PATH (use capture.jpg)
   └── metadata.json unreadable
       └── → FULL FALLBACK (use capture.jpg + reference.jpg, Standard dims)

HQ CAPTURE PATH:
  2. Read EXIF-oriented dims of capture-original.jpg
     ├── Success → Compute HQ canvas (§5.5 formula, cap 3840 px)
     └── Failure → error Snackbar, abort

  3. Prepare HQ capture bitmap at (imgW, imgH):
     ├── Slider: decode via ImageDecoder.setTargetSize(compW, compH)
     │   ├── Success → capBitmap
     │   └── OOM/exception → recycle → error Snackbar, abort
     └── Side-by-side: center-crop to viewport ratio + scale to (compW/2, compH)
         ├── Success → capBitmap at slot dimensions (compW/2, compH)
         └── OOM/exception → recycle → error Snackbar, abort

NON-HQ CAPTURE PATH:
  2. Canvas dims from viewport (no cap)
  3. Decode capture.jpg → capBitmap (viewport-sized; fills SbS slot exactly)

REFERENCE PATH (applies to BOTH HQ and NON-HQ, for both styles):
  4. Decode reference-original.jpg via BitmapFactory.decodeFile()
     ├── Success
     │   └── ReferenceRenderer.render(refOrigBitmap, imgW, imgH, overlay params)
     │       ← Note: imgW and imgH are slot dims for SbS, full dims for Slider
     │       ├── Success → refBitmap at (imgW, imgH)
     │       └── OOM/exception → recycle → error Snackbar, abort
     └── Failure → fall back to reference.jpg (decode via BitmapFactory)
         └── reference.jpg failure → error Snackbar, abort

  5. Render canvas (SliderRenderStrategy or SideBySideRenderStrategy)
     ← Strategies receive bitmaps already sized for their rendering destination.
     ← SideBySideRenderStrategy: Fit of (compW/2, compH) into (compW/2, compH) = scale 1.0.
     ├── Success → MediaStore write → Share Sheet
     └── OOM/exception → error Snackbar
```

**Snackbar on any failure**: `share_comparison_error_render_failed` ("Could not create image").
No new error string required.

### 5.14 Tests

#### Unit Tests (new)

| # | Test | What is verified |
|---|---|---|
| T-HQ-U-01 | Standard quality: canvas unchanged — longest edge ≤ 2048 px | Regression: Standard path unmodified |
| T-HQ-U-02 | Original + HQ capture source: canvas longest edge > 2048 px when capture-original > viewport | HQ canvas expansion |
| T-HQ-U-03 | Original + HQ capture source: canvas maintains viewport aspect ratio | Aspect ratio correctness |
| T-HQ-U-04 | Original + HQ capture source: canvas longest edge capped at 3840 px | Max cap enforcement |
| T-HQ-U-05 | Original + no HQ capture source: canvas uses viewport dimensions | v2/v3/v4 capture fallback |
| T-HQ-U-06 | Original + HQ source resolution ≤ viewport: canvas = viewport (no downscale) | coerceAtLeast(1f) |
| T-HQ-U-07 | Canvas width and height always even — all cases | Even dimension invariant |
| T-HQ-U-08 | Side by side compH = makeEven(compHBase / 2) for HQ canvas | SbS height formula preserved |
| T-HQ-U-09 | `hasHqCaptureSource()`: true when `files.captureOriginal` present and file exists | Source detection |
| T-HQ-U-10 | `hasHqCaptureSource()`: false when metadata.json missing | Edge case |
| T-HQ-U-11 | `hasHqCaptureSource()`: false when files.captureOriginal missing from metadata | Edge case |
| T-HQ-U-12 | `hasHqCaptureSource()`: false when captureOriginal file absent from disk | Edge case |
| T-HQ-U-13 | Quality note state: HQ session → `_hqAvailable = true` | ViewModel state |
| T-HQ-U-14 | Quality note state: non-HQ session → `_hqAvailable = false` | ViewModel state |
| T-HQ-U-15 | `readOverlayParams()`: correctly parses scale, offsetX, offsetY, displayMode from metadata.json | Overlay param extraction |
| T-HQ-U-16 | `readOverlayParams()`: returns null / defaults when overlay block absent | Missing metadata robustness |

#### Instrumentation Tests (new)

| # | Test | What is verified |
|---|---|---|
| T-HQ-I-01 | Standard quality: exported JPEG longest edge ≤ 2048 px | Regression |
| T-HQ-I-02 | Original + HQ capture source: JPEG longest edge > Standard (Slider) | HQ Slider canvas expansion |
| T-HQ-I-03 | Original + HQ capture source: JPEG aspect ratio matches viewport ratio | Aspect ratio |
| T-HQ-I-04 | Original + HQ source: JPEG contains no GPS EXIF tags | Privacy |
| T-HQ-I-05 | Original + no HQ capture source: JPEG longest edge = viewport longest edge | Non-HQ fallback |
| T-HQ-I-06 | Original + HQ source, Slider: valid JPEG written to MediaStore | Slider HQ end-to-end |
| T-HQ-I-07 | Original + HQ source, Side by side: valid JPEG written to MediaStore | SbS HQ end-to-end |
| T-HQ-I-08 | Caption present in HQ Slider export (title + date canvas taller) | Caption at HQ dims |
| T-HQ-I-09 | Slider handle visible in HQ Slider export (white pixels at divider center) | Handle at HQ dims |
| T-HQ-I-10 | Standard SbS export: canvas unchanged regression | Regression |
| T-HQ-I-11 | Original + HQ source, Slider: reference composition — right-half pixel is reference colour | Composition identity |
| T-HQ-I-12 | Original + HQ source, SbS: left-half top-area pixel is reference colour (no letterboxing) | SbS slot-fill without bands |
| T-HQ-I-13 | Original + HQ source, SbS: right-half (capture) fills slot — no dark bands at top | SbS capture slot-fill without bands |
| T-HQ-I-14 | Original + HQ source, SbS: both slot bitmaps are viewport-ratio (no distortion) | SbS viewport-ratio bitmaps |
| T-HQ-I-15 | Original + HQ source, SbS longest edge > SbS Standard (HQ canvas expansion) | SbS HQ canvas expansion |

#### Regression Guard

All of the following must remain fully green:
- `ShareRenderConfigTest` (15 tests) — canvas dimension logic
- `ShareImageRendererInstrumentedTest` (6 tests) — end-to-end render
- `ShareComparisonViewModelTest` (20 tests) — ViewModel state
- `ShareComparisonScreenTest` (7 tests) — UI integration
- All other existing test suites (unit and instrumentation)

---

## 6. Non-Goals

The following are explicitly **forbidden** in this feature:

- Changing `CompareScreen` rendering, slider behavior, or image display
- Changing the session storage format or session file structure
- Changing the metadata schema (no new fields added to `metadata.json`)
- Changing backup ZIP behavior or `SessionBackupExporter`
- Changing video export behavior
- Changing camera capture behavior
- Adding network or cloud behavior of any kind
- Adding new permissions
- Copying EXIF or GPS metadata from any source file into the exported JPEG
- Decoding `capture-original.jpg` at full resolution without size targeting (unlimited allocation)
- Applying any HQ-specific logic inside `SliderRenderStrategy` or `SideBySideRenderStrategy`; both strategies remain source-agnostic
- Changing the Fit/Fill semantic definitions of either render strategy
- Using `reference-source-original.<ext>` as a render source (future enhancement: would provide
  marginally higher reference quality by avoiding the quality-90 re-encode in
  `reference-original.jpg`; requires format decoding, EXIF orientation, and v5/v6 scope)
- Changing the live preview to render from HQ sources
- Changing the Standard quality path in any way
- Redesigning the ShareComparisonScreen UI beyond the quality note text changes
- Changing caption logic, toggle behavior, or information cards

---

## 7. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `SHARE_COMPARISON_IMAGE_V1.md` | This document supersedes §8.2 (Original quality). All other sections remain authoritative. |
| `SESSION_ORIGINALS_V1.md` | Defines `capture-original.jpg` and `reference-original.jpg`. This feature is the first consumer of both in an export workflow (§12 "Future Use Cases"). |
| `COMPARE_SESSION_RENDERING_V1.md` | `reference.jpg` is not modified and remains the compare rendering source. This feature adds a parallel HQ export path using `reference-original.jpg` + `ReferenceRenderer.render()`. The compare pipeline is unchanged. |
| `SESSION_ORIGINALS_PRIVACY_V1.md` | When Privacy mode is ON, `capture-original.jpg` has metadata stripped but full resolution preserved. `reference-original.jpg` is not affected by privacy mode (it is a re-encode, not a byte-copy). HQ export from a privacy-mode session produces full-resolution output without hidden GPS/EXIF. This is correct. |
| `IMPLEMENTATION_NOTES.md` | Must be updated after each implementation block. |
