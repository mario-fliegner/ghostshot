# IMPLEMENTATION_NOTES.md

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

### Comparison / Core Logic (NEW)
- `ComparisonCropProcessor` implemented
- Deterministic crop logic based on `ComparisonFrame`
- Uses:
  - CaptureRect → crop from capture bitmap
  - ReferenceRect → crop from reference bitmap
- Capture crop is scaled to exact reference crop size
- Strict rounding rules:
  - floor (left/top)
  - ceil (right/bottom)
  - hard clamp to bitmap bounds
- No geometry recalculation
- No EXIF / no URI / no pipeline logic inside processor
- Fully isolated, testable core logic

### Tests (NEW)
- Full instrumentation test coverage for `ComparisonCropProcessor`
- Covers:
  - standard cases
  - edge cases (rounding, bounds, small crops)
  - error cases (degenerate rects)
  - memory guarantees (inputs not recycled)
- Uses synthetic Bitmaps only
- No additional frameworks introduced

---

## Product Decision: Comparison Output (CRITICAL)

GhostShot does **not** promise only a saved camera photo.
It promises a reproducible before/after comparison result.

### Final decision
- The reference image is the master for comparison alignment
- The visible overlay state at capture time defines the exact comparison frame
- "What the user sees is what the user gets"

### Capture result model
Each capture consists conceptually of:
1. Full Capture Image
2. Comparison Crop Definition

The full image is still saved unchanged.
The comparison frame is defined at capture time from overlay/view geometry and later used for comparison output.

---

## Not Implemented Yet

- Integration of `ComparisonCropProcessor` into capture flow
- Comparison crop definition / preview-to-capture mapping usage in runtime
- Persisted storage of comparison crop metadata
- Any export flow based on comparison result
- Debug preview of generated crops

---

## Immediate Next Step

Next implementation step:

👉 Integrate `ComparisonCropProcessor` into capture flow (read-only)

Scope:
- Call processor after successful capture
- Use existing:
  - saved capture bitmap
  - loaded reference bitmap
  - existing ComparisonFrame
- DO NOT:
  - persist results yet
  - add UI
  - modify pipeline structure

Goal:
- Validate visually that computed crops match overlay alignment

---

## Practical Working Rules

### Scope discipline
- Only implement the requested feature
- No speculative future features

### Change discipline
- Keep changes minimal
- No refactoring outside scope

### Testability
- Core logic remains isolated and deterministic
- No UI dependency in core logic
