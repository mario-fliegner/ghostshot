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
- minSdk 29 / targetSdk 35 — Current implementation: minSdk is 26 (see `build.gradle.kts` and `CLAUDE_PROJECT_INSTRUCTION.md`, which is the source of truth)

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

- Comparison output is defined exclusively by Variant B bitmap normalization
- `CenterCropNormalizer` is implemented
- Capture bitmap is rotated before comparison normalization
- Reference bitmap is EXIF-oriented before comparison normalization
- Both images are normalized independently:
  - center-crop to portrait 9:16
  - scale to fixed output size
- The resulting normalized capture/reference pair is the current deterministic comparison basis
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping do NOT define comparison output

### Logging
- Internal debug logging is explicitly allowed and expected during development
- Logging must remain non-user-facing
- Logging must not create live-mode UI output
- Logging should remain compatible with release/debug controls

### Tests
- Bitmap recycle behavior is covered in `CameraViewModelBitmapRecycleTest`
- Current test focus follows active Variant B runtime behavior

### Compare Screen

`CompareScreen` is implemented as a fullscreen slider-based comparison screen.

- Reachable from Camera Flow after a successful capture with a reference image
- Reachable from Compare Library when opening a saved session
- Reference image on the left, capture image on the right; single draggable vertical divider at 50%
- Shows session timestamp below viewport when `timestamp` is provided
- Shows delete button when `onDelete` is provided; deletes internal session only, not MediaStore photo
- Instrumentation tests in `CompareScreenTest.kt` cover render, slider, rotation, timestamp, and delete

### Compare Library

`CompareLibraryScreen` is implemented as a 2-column grid listing saved compare sessions.

- Tap on a tile opens `CompareScreen` with full session context (timestamp + delete)
- Long press activates multi-select mode; multi-select delete confirmed via dialog

### Session Storage

`SessionStorage` writes each successful capture+reference pair to `filesDir/sessions/<sessionId>/`.

- Each session contains `capture.jpg`, `reference.jpg`, `metadata.json`
- `SavedSessionRef(sessionId, timestamp)` is returned on success; `null` on failure
- `SessionScanner` reads and validates sessions from the sessions root directory
- `SessionDeleter` removes a session folder; validates session ID to prevent path traversal; unit tests in `SessionDeleterTest.kt`

### CompareInput Session Context

`CompareInput` contains optional `sessionId: String?` and `timestamp: Long?` in addition to the two URIs.
These are populated by `onCaptureSaved(savedUri, sessionRef)` when a session was successfully written.
`MainActivity` passes `sessionId` and `timestamp` to `compareRoute` when both are present,
making Camera Flow and Library Flow consistent in what they provide to `CompareScreen`.

### Current Test Status (2026-04-27)

34 unit tests, all green (`testDebugUnitTest`). Notable additions:

- `compareInput_sessionIdAndTimestampAreNullWhenNoSessionRef`
- `compareInput_hasSessionIdAndTimestampWhenSessionRefProvided`

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
- Geometry-based comparison logic is not part of the current product model

---

## Not Implemented Yet

- Any user-facing comparison viewer / before-after slider output
- Session-based storage for capture + reference image pairs
- Any persistence model for derived comparison metadata
- Any export flow beyond the currently saved image(s)

Updated (2026-04-27):

- Comparison viewer: Implemented — `CompareScreen` provides a fullscreen slider-based before/after comparison.
- Session-based storage: Implemented — `SessionStorage` writes `capture.jpg` + `reference.jpg` + `metadata.json` to `filesDir/sessions/<sessionId>/`.
- Persistence model for comparison metadata: Implemented — `metadata.json` stores `sessionTimestampMs`, capture MediaStore URI, reference picker URI, and schema version. `SessionScanner` reads and validates these files.
- Export flow: Still not implemented. Remains out of scope.

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
