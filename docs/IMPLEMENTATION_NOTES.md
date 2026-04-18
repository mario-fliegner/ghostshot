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

### Comparison / Display UX
- Comparison display mode is available from the reference-image controls/menu
- Format-mismatch hint uses a compact badge + local inline bubble
- Bottom control zone and top hint behavior are stable in portrait and landscape

### Layout (IMPORTANT)
- Fullscreen `Box` as root
- Camera preview ALWAYS `fillMaxSize()`
- UI is layered ABOVE the preview

This ensures:
- Preview size NEVER changes due to UI
- Overlay scaling remains stable

### UI Structure
- Bottom UI is an overlay (not part of layout flow)
- Portrait and landscape layouts are both actively supported
- Existing snackbar feedback and local hint feedback are stable after lifecycle changes

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

### Important implication
A visually aligned overlay in the app must correspond to the later comparison result as closely as technically possible.
The app must therefore maintain a reliable mapping between:
- Compose viewport / preview space
- overlay geometry
- captured image space

---

## UI Design Principles (IMPORTANT)

- The preview is the primary content → must stay dominant
- UI must NOT resize or affect preview geometry
- UI is always an overlay layer
- Use dark semi-transparent surfaces sparingly
- Use a single accent color across the app
- Avoid visual clutter
- Comparison UX must stay understandable without exposing technical camera complexity

---

## Not Implemented Yet

- Optional 3x3 grid overlay
- Comparison crop definition / preview-to-capture mapping implementation
- Persisted storage of comparison crop metadata alongside captures
- Any persistence across full app restarts
- Any export flow built on top of the comparison crop definition
- Live camera zoom as a comparison tool

---

## Immediate Next Step

Next implementation step:
- define and implement the comparison crop definition pipeline

Scope direction:
- derive the comparison frame from the visible overlay state at capture time
- map preview coordinates into captured image coordinates
- keep saving the full image unchanged
- prepare deterministic crop metadata for later before/after comparison

Not part of this step:
- no live camera zoom feature
- no visual merge export
- no website/export UI yet

---

## Practical Working Rules

### Scope discipline
- Only implement the requested feature
- No speculative future features

### Change discipline
- Keep changes minimal
- No refactoring outside scope

### UI discipline
- No layout that resizes preview
- No hardcoded colors
- No avoidable UX complexity for camera manipulation

### State
- ViewModel is source of truth for core behavior
- No UI-only state for core behavior

### Testability
- Keep logic testable
- Prefer deterministic state + geometry logic over UI-only heuristics
- Comparison frame behavior must be testable without depending on visual screenshots

---

## Notes

The project now has a stable camera UI baseline.

Further work must:
- preserve lifecycle-safe behavior
- preserve current overlay alignment behavior
- avoid breaking the product promise around reproducible before/after comparison
- build comparison output logic on top of the existing stable camera and overlay system
