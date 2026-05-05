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
- Grid overlay (IMPLEMENTED)
- Capture / save via MediaStore (IMPLEMENTED)
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

---

## Current Implementation Addendum (2026-04-28)

### Implemented Since Previous Notes

Compare flow:
- `CompareScreen` is implemented as a fullscreen slider-based before/after comparison screen
- `CompareLibraryScreen` is implemented as a focused internal session overview
- `SessionStorage` writes `capture.jpg`, `reference.jpg`, and `metadata.json` under `filesDir/sessions/<sessionId>/`
- `SessionScanner` reads saved sessions
- `SessionDeleter` deletes only internal session folders and validates session IDs against path traversal
- `CompareInput` includes optional `sessionId` and `timestamp`
- Camera Flow and Library Flow both pass session context to `CompareScreen` when available

Comparison logic:
- Current comparison output follows Variant B only
- Capture bitmap is rotated before comparison normalization
- Reference bitmap is EXIF-oriented before comparison normalization
- Both images are independently center-cropped to portrait 9:16 and scaled to one fixed target size
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping do not define comparison output

Camera / overlay:
- Capture and save via MediaStore are implemented
- Overlay drag is implemented
- Overlay pinch scaling is implemented
- Reset restores sensible default alignment state
- Remove reference image is supported with undo flow
- Undo state survives rotation correctly

Logging:
- Internal debug logging is allowed and expected during development
- Logging must stay non-user-facing and compatible with release/debug controls

### Camera Screen Controls: Current Decision

### Additional Implementation Notes (2026-05-05)

Camera / Preview:
- Landscape preview now uses correct 16:9 ViewPort (instead of 9:16)
- ScaleType is unified to FIT_CENTER for both orientations
- Letterbox / pillarbox areas are expected and intentional
- PreviewView background is explicitly set to a neutral scrim color

Scrim / Background:
- Preview frame background uses a central color definition (GhostShotPreviewFrameScrim)
- Current value is a semi-transparent dark grey (~0x99222222)
- This replaces pure black to improve contrast with system UI elements

Edge-to-edge:
- enableEdgeToEdge is active
- Status bar icons are forced to light (white) globally via SystemBarStyle.dark(...)
- No per-screen override implemented yet (next step)

FormatMismatchHint:
- Remains inside CameraControlsOverlay (NOT moved)
- Position is now frame-relative via frameLeft / frameTop
- Additional safety: top offset uses max(frameTop, statusBarInset)
- Prevents overlap with system status bar in landscape



Portrait:
- Current portrait layout works correctly
- Slider sits above the bottom controls
- Portrait must not be touched by the current landscape-control fix

Landscape:
- Capture is already fixed at bottom center and must remain exactly centered
- Overlay button is left of capture
- Shots / Compare entry is right of capture
- Final target structure is a bottom button row: `Overlay` / `Capture` / `Shots`
- Opacity slider sits in a separate row above the button row
- Slider is centered above capture
- Slider width is constrained by the visible button-group width
- Slider must not be placed to the right of Shots / Compare
- Slider must not use remaining-space calculations
- Old right-side / fallback slider logic must not be reused
- Overlay action menu opens from Overlay, remains inside root bounds, and may overlap the slider

### Immediate Next Step (2026-04-28)

Fix only the landscape camera-control layout.

Scope:
- Keep capture bottom-centered
- Arrange landscape bottom controls as `Overlay` / `Capture` / `Shots`
- Move landscape opacity slider into a centered row above the buttons
- Constrain slider width to the button-group width
- Keep overlay action menu above the slider when overlapping
- Add or adjust focused tests for this landscape behavior

Not part of this step:
- No portrait changes
- No `CompareScreen` changes
- No Variant B changes
- No session storage changes
- No visual redesign beyond the landscape-control structure
- No refactoring of unrelated `CameraScreen` code

### Tests To Consider For The Current Landscape Fix

- Capture is horizontally centered in landscape
- Overlay and Shots / Compare are symmetrically positioned around capture
- Slider is centered above capture
- Slider is above the button row
- Slider width is less than or equal to the button-group width
- Slider remains inside root bounds
- Overlay menu remains visible and visually above the slider when overlapping


## Addendum (2026-05-05)

- Capture flash implemented (preview-only visual feedback)
- Haptic feedback implemented on capture trigger
- Grid overlay implemented (Canvas-based, preview-only)
- Landscape controls alignment fixed (centered to preview, nav bar independent)
