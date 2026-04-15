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
- CameraX preview
- minSdk 26 / targetSdk 35

Permissions:
- CAMERA only

---

## Implemented Features

### Camera
- CameraX Preview working reliably
- Back camera only
- Lifecycle-safe preview handling

### Permissions
- Full permission flow implemented:
  - initial request
  - rationale
  - permanent denial → app settings

### Reference Image
- Android Photo Picker integration
- Single image selection
- Overlay displayed above preview
- Picker cancellation does NOT remove existing overlay

### Overlay Rendering
- Overlay displayed centered using AsyncImage
- Opacity adjustable via slider (0.1–0.9)
- Overlay state stored in ViewModel

### Layout (IMPORTANT)

The layout has been refactored to:

- Fullscreen `Box` as root
- Camera preview ALWAYS `fillMaxSize()`
- UI is layered ABOVE the preview

This ensures:
- Preview size NEVER changes due to UI
- Overlay scaling remains stable

### UI Structure

- Bottom UI is an overlay (not part of layout flow)
- Portrait:
  - stacked controls (opacity + bottom bar)
- Landscape:
  - compact single-row controls (reference + opacity)

---

## UI Design Principles (IMPORTANT)

- The preview is the primary content → must stay dominant
- UI must NOT resize or affect preview geometry
- UI is always an overlay layer
- Use dark semi-transparent surfaces (camera app pattern)
- Use a single accent color across the app
- Avoid visual clutter

Color rules:
- Background overlays: black with alpha (~60–70%)
- Primary text: white
- Secondary text: light gray
- Accent color: single consistent color (e.g. blue)

---

## Not Implemented Yet

- Overlay drag (move)
- Overlay pinch scaling
- Camera zoom mode
- Reset functionality
- Overlay delete (with confirmation)
- Grid overlay
- Capture / save via MediaStore
- Any persistence across app restarts

---

## Immediate Next Step

Next implementation step:

- Enable dragging (move) of the overlay

Scope:
- One-finger drag moves overlay
- Position stored in ViewModel
- State survives rotation within same session

Not part of this step:
- No scaling
- No zoom mode
- No reset/delete
- No capture

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
- Use central color definitions

### State
- ViewModel is source of truth
- No UI-only state for core behavior

### Testability
- Keep logic testable
- Do not introduce complex UI logic in Composables

---

## Notes

The project has reached a stable UI baseline.

Further work must:
- build on top of the current overlay-based layout
- avoid breaking preview stability
- maintain clear separation between UI and logic