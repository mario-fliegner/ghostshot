# COMPARE_SESSION_RENDERING_V1.md

## Purpose

This document defines the rendering, persistence and comparison architecture for SameView session rendering.

The goal is to guarantee:

- deterministic compare behavior
- visually stable compare results
- reproducible rendering
- future extensibility
- correct handling of overlay transforms
- predictable UX for the user

This specification is the authoritative source for all future compare/session rendering changes.

---

# Core Principle

The compare result must represent what the user actually saw while taking the photo.

The app must NOT silently reinterpret, recrop, refit or recompute the comparison later in the compare screen.

The compare result must be deterministic.

The compare experience must behave like a frozen capture state.

---

# Selected Rendering Strategy

## Selected Variant: Variant A

The compare geometry is always based on the visible camera/preview viewport at capture time.

This is a deliberate UX decision.

The compare result must visually match the capture experience.

The compare system must prioritize:

1. visible compare viewport
2. visible overlay geometry
3. user-visible alignment state
4. capture consistency

The compare system must NOT prioritize:

- original image dimensions
- original image aspect ratio
- maximum image coverage
- mathematically optimal crops

---

# Session File Structure

Each session stores:

capture.jpg
reference.jpg
reference-original.jpg
metadata.json

---

# File Definitions

## capture.jpg

The stored camera capture image used for compare.

This image represents the final compare capture geometry.

capture.jpg must NEVER later be recropped based on reference image limitations.

capture.jpg always represents the actual captured camera result.

---

## reference.jpg

The rendered compare reference image.

This is NOT the original image.

This image represents the exact visible reference geometry corresponding to the compare viewport at capture time.

reference.jpg MUST include:

- overlayScale
- overlayOffsetX
- overlayOffsetY
- referenceImageDisplayMode
- viewport geometry
- orientation handling

reference.jpg MUST visually match the compare viewport geometry of capture.jpg.

reference.jpg MUST NOT contain:

- camera preview
- overlay alpha
- flash effect
- grid
- UI
- scrim
- controls
- composited camera image

reference.jpg is always:

- fully opaque
- final rendered geometry
- deterministic
- compare-ready

reference.jpg is NOT dynamically recalculated later.

---

## reference-original.jpg

The EXIF-oriented full original reference image.

This file is:

- decoded
- EXIF-oriented
- re-encoded as JPEG (quality 90)

This is NOT a raw byte-copy of the original source file.

reference-original.jpg exists for:

- future re-editing
- future re-alignment
- export workflows
- advanced compare features
- debugging
- future premium features

reference-original.jpg must NOT be used in normal compare rendering.

---

## metadata.json

Stores session metadata and rendering state.

Example structure:

{
  "version": 2,
  "referenceFile": "reference.jpg",
  "referenceOriginalFile": "reference-original.jpg",
  "captureFile": "capture.jpg",
  "overlayScale": 1.0,
  "overlayOffsetX": 0.0,
  "overlayOffsetY": 0.0,
  "referenceImageDisplayMode": "COMPARE_WITH_PREVIEW",
  "viewportWidth": 0,
  "viewportHeight": 0
}

The metadata schema must remain forward-compatible.

---

# Deterministic Compare Rule

Compare rendering must become deterministic.

The compare screen must ONLY render:

- capture.jpg
- reference.jpg

The compare screen must NOT:

- recalculate overlay transforms
- recompute crops
- reinterpret display mode
- rerender based on metadata
- access the original picker URI
- dynamically rebuild geometry

The compare result must already be finalized during session save.

---

# Immediate Compare Consistency

Immediate compare after capture and library compare must behave identically.

This means:

- snackbar compare
- compare library compare
- future compare reopen

must all use the stored:

reference.jpg
capture.jpg

The live picker URI must no longer be used for compare rendering.

---

# Capture-Time Rendering Pipeline

## Step 1 — Live Preview State

The user sees:

- live camera preview
- transformed overlay reference
- current display mode
- current viewport geometry

The visible compare geometry at this moment becomes the authoritative compare target.

---

## Step 2 — Capture Snapshot Freeze

At capture time the app freezes:

- overlayScale
- overlayOffsetX
- overlayOffsetY
- referenceImageDisplayMode
- viewportWidth
- viewportHeight
- orientation state

This frozen state defines the final compare rendering.

The compare must later reproduce THIS frozen state.

---

## Step 3 — Capture Image Save

capture.jpg is saved from the camera pipeline.

capture.jpg remains the full compare capture result.

capture.jpg must NEVER be cropped smaller because of reference image limitations.

---

## Step 4 — Reference Original Save

The original reference source is:

- decoded
- EXIF-oriented
- normalized

Then stored as:

reference-original.jpg

This file remains the full reference source.

No overlay transforms are applied to reference-original.jpg.

---

## Step 5 — Render reference.jpg

reference.jpg is rendered from:

- EXIF-oriented original reference image
- frozen overlay state
- frozen viewport geometry
- frozen display mode

The result must represent exactly what the user visually saw during capture.

reference.jpg must match capture.jpg compare geometry.

reference.jpg becomes the canonical compare reference image.

---

## Step 6 — Final Session Commit

Only after ALL required files are successfully written:

- capture.jpg
- reference.jpg
- reference-original.jpg
- metadata.json

may the session become visible in the library.

Partial sessions are invalid.

---

# Viewport Geometry Rules

The compare geometry always follows the visible camera/preview viewport.

It does NOT follow:

- original image aspect ratio
- original image dimensions
- maximum available image coverage

The visible viewport at capture time defines the compare target geometry.

---

# Empty Area Rules

If the reference image does not fully cover the compare viewport:

- reference.jpg may contain empty areas
- these empty areas must match the actual visible preview geometry

The app must NOT:

- auto-center
- auto-expand
- auto-fill
- auto-clamp
- silently crop capture.jpg smaller

Empty areas must be rendered with the app surface color (#17202F).

Transparent output is not allowed.

Black (#000000) is not permitted for empty areas.

---

# Overlay Offset Rules

Overlay transforms outside the visible viewport must be preserved exactly.

The app must NOT:

- re-clamp offsets
- re-center the overlay
- normalize geometry differently during save

The saved compare result must match the visible preview state exactly.

---

# Display Mode Rules

The active referenceImageDisplayMode must fully affect the rendered reference.jpg.

Supported modes include:

- COMPARE_WITH_PREVIEW
- SHOW_FULL_IMAGE

Different display modes may produce different compare geometries.

The saved result must reflect the active mode at capture time.

---

# Orientation Rules

Orientation handling is critical.

Example:

- original reference image is portrait
- device rotates into landscape
- overlay is scaled and positioned for landscape alignment
- capture occurs in landscape

Expected result:

- reference-original.jpg remains portrait/full original
- capture.jpg is landscape compare geometry
- reference.jpg is also landscape compare geometry
- compare rendering remains visually aligned

reference.jpg orientation follows compare geometry.

reference-original.jpg orientation follows original image orientation.

---

# Compare Geometry Examples

## Example A — Portrait Reference → Landscape Compare

Scenario:

- original image is portrait
- device rotates into landscape
- user scales overlay until it visually matches the scene
- capture occurs in landscape

Result:

- reference-original.jpg remains portrait
- reference.jpg becomes landscape
- capture.jpg becomes landscape
- compare later aligns immediately without manual repositioning

---

## Example B — Reference Image Cannot Fully Cover Viewport

Scenario:

- original image resolution is too small
- even at maximum scale the overlay does not fully fill the landscape viewport

Result:

- reference.jpg contains empty areas filled with the app surface color (#17202F)
- capture.jpg remains untouched
- compare geometry still follows the visible viewport

capture.jpg must NEVER be cropped smaller to match the smaller reference image.

---

## Example C — Extreme Overlay Offset

Scenario:

- user intentionally pushes overlay partially outside the viewport

Result:

- the saved compare geometry preserves this exact state
- no auto-centering
- no offset correction
- no geometry normalization

If overlay coverage drops below 20 % of the viewport area, a live UX warning is shown in the CameraScreen top-left hint slot.

This warning is informational only:

- capture is still allowed
- compare rendering is unchanged
- no geometry correction occurs

---

## Example D — SHOW_FULL_IMAGE Mode

Scenario:

- user enables SHOW_FULL_IMAGE display mode
- overlay no longer fills the viewport like preview mode

Result:

- reference.jpg reflects this exact display mode geometry
- compare later reproduces this state deterministically

---

# Compare Rendering Rules

Compare rendering must be passive.

The compare screen must NOT:

- reinterpret geometry
- rerender overlay transforms
- rebuild crops
- recompute display mode behavior

The compare screen only displays:

- capture.jpg
- reference.jpg

This guarantees deterministic rendering.

---

# Existing Rendering Logic

Before introducing new rendering logic, existing compare/crop infrastructure must be evaluated for reuse.

Relevant existing systems include:

- ComparisonFrameCalculator
- ComparisonCropProcessor
- CaptureRect / ReferenceRect logic

ImageCompositor may be unsuitable because it performs compositing instead of pure reference rendering.

Pure reference rendering is preferred.

---

# Session Atomicity

Session saving must be atomic.

If any required file fails:

- no partial session may remain
- no broken library entry may exist
- metadata.json must not reference missing files

Required files:

- capture.jpg
- reference.jpg
- reference-original.jpg
- metadata.json

Partial sessions are invalid.

No silent fallback behavior is allowed.

---

# Performance and OOM Requirements

Large reference images must be handled carefully.

The implementation must avoid:

- unnecessary bitmap duplication
- repeated full-resolution decodes
- unnecessary intermediate bitmaps

Bitmap lifecycle management must be explicit:

- recycle when safe
- try/finally cleanup
- avoid simultaneous full-resolution copies where possible

OOM stability is mandatory.

---

# Explicitly Forbidden Behaviors

The implementation must NOT:

- rerender compare dynamically later
- silently reinterpret geometry
- use picker URIs in compare
- recompute transforms during compare
- apply overlay alpha to reference.jpg
- render grid/UI into reference.jpg
- crop capture.jpg to smaller reference bounds
- auto-fix alignment during session save
- transparently replace missing compare geometry
- silently normalize offsets

---

# Future Compatibility

This architecture intentionally preserves compatibility for:

- future re-alignment
- export features
- GPS compare
- cloud sync
- compare editing
- advanced rendering workflows
- premium features

without changing the core compare pipeline later.

---

# Final Architectural Goal

The compare experience must behave like a frozen capture state.

The user must later see exactly the compare result that was visible during capture.

No hidden reinterpretation must happen after capture.