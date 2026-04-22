## Purpose
This file supplements `CLAUDE_PROJECT_INSTRUCTION.md`.

It documents the **current implementation state**, **recent decisions**, and **immediate next steps**.

It must stay short, precise, and aligned with the actual codebase.

If there is any conflict, `CLAUDE_PROJECT_INSTRUCTION.md` remains the source of truth.

---

## Current Project Status

Project name:
- GhostShot / OverlayPast Android app

Technical baseline:
- Kotlin
- Jetpack Compose
- Material 3
- MVVM + Hilt
- CameraX preview + image capture
- minSdk 29 / targetSdk 35

Permissions:
- CAMERA only

Storage baseline:
- Android 10 / API 29+ only
- MediaStore saves rely on scoped-storage-era behavior for app-created images
- No Android 8/9 legacy storage compatibility path

---

## Implemented Features

### Camera / Capture
- CameraX preview working reliably
- Back camera only
- Capture and save via MediaStore implemented
- Capture lock is lifecycle-safe across rotation / recreation

### Permissions
- Full permission flow implemented:
  - initial request
  - rationale
  - permanent denial → app settings

### Reference Image / Overlay
- Android Photo Picker integration
- Single image selection
- Overlay displayed above preview
- Picker cancellation does NOT remove existing overlay
- Overlay drag implemented
- Overlay pinch scaling implemented
- Opacity adjustable via slider (0.1–0.9)
- Overlay state stored in ViewModel
- Reset restores sensible default alignment state
- Remove reference image supported with undo flow
- Undo state survives rotation correctly

### Comparison / Core Logic (Variant B)
- Geometry-based comparison output has been abandoned
- `ComparisonFrame` / snapshot-based result generation is no longer part of the active runtime path
- `CenterCropNormalizer` is implemented
- Capture bitmap is rotated before comparison normalization
- Reference bitmap is EXIF-oriented before comparison normalization
- Both images are normalized independently:
  - center-crop to portrait 9:16
  - scale to fixed output size
- The resulting normalized capture/reference pair is the current deterministic comparison basis
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping do NOT define comparison output anymore

### Logging
- Internal debug logging is explicitly allowed and expected during development
- Logging must remain non-user-facing
- Logging must not create live-mode UI output
- Logging should remain compatible with release/debug controls

### Tests
- Bitmap recycle behavior is covered in `CameraViewModelBitmapRecycleTest`
- Legacy snapshot/comparison-frame tests have been removed where they only covered obsolete logic
- Current test focus should follow active Variant B runtime behavior, not dead geometry-based comparison behavior

---

## Product Decision: Comparison Output (CRITICAL)

GhostShot currently promises:
1. A saved full camera image
2. A deterministic comparison pair derived from the capture and the selected reference

### Final decision
Comparison output follows **Variant B only**:

- Capture bitmap is rotated correctly
- Reference bitmap is EXIF-oriented correctly
- Both images are center-cropped independently to portrait 9:16
- Both images are scaled to one fixed target size
- These two normalized bitmaps are the comparison basis

Important:
- The overlay is a visual aid only
- The overlay does NOT define comparison output
- No `ComparisonFrame`, `CaptureRect`, `ReferenceRect`, or preview/capture mapping is part of the current product model

---

## Not Implemented Yet

- Full project-wide cleanup of obsolete geometry-based helper classes still left in the codebase
- Final removal of unused legacy comparison classes and their remaining tests, where still present
- Any user-facing comparison viewer / before-after slider output
- Any persistence model for derived comparison metadata
- Any export flow beyond the currently saved image(s)

---

## Immediate Next Step

Next implementation step:

👉 Remove remaining obsolete geometry-based comparison classes and their directly related tests, but only where they are now fully unreferenced.

Scope:
- remove dead legacy comparison classes
- remove dead legacy tests tied only to obsolete comparison-frame logic
- do NOT change Variant B runtime behavior
- do NOT change UI
- do NOT add new features

Goal:
- make the codebase consistent with Variant B as the only active comparison model

---

## Practical Working Rules

### Scope discipline
- Only implement the requested feature
- No speculative future features

### Change discipline
- Keep changes minimal
- No refactoring outside scope

### Testability
- Active logic must stay testable and deterministic
- No UI dependency in core logic
- Do not preserve dead tests only to protect obsolete architecture
