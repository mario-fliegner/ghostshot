# IMPLEMENTATION_NOTES.md

## Purpose
This file supplements `CLAUDE_PROJECT_INSTRUCTION.md`.

It documents the **current implementation state**, **recent decisions**, and **release-relevant notes**.

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
- minSdk 29 / targetSdk 35

Permissions:
- CAMERA only
- No INTERNET permission
- No READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE permission

Current release state:
- Closed-testing-ready based on the current code and documented verification
- No known critical blocker for Closed Testing / Play upload
- Release build hardening is active: R8, resource shrinking, backup/session exclusions, and debug-log gating
- A signed release APK has been installed and used successfully on a real device

---

## Implemented Features

### Camera
- CameraX Preview working reliably
- Back camera only
- Lifecycle-safe preview handling
- Capture and save via MediaStore
- Capture flash and haptic feedback on capture trigger
- Save failures show user-facing feedback and keep the app usable
- CameraScreen bottom workflow is stable: `Overlay` / `Capture` / `Compare`
- Compare is always visible and never dynamically switches to Shots/History
- Disabled Compare taps show short workflow hints (~2000 ms)
- Top-right CameraScreen navigation contains persistent History and Overflow actions
- History opens the internal Compare Library even when it is empty
- Overflow is prepared with Settings and About entries

### Permissions
- Full permission flow implemented:
  - initial request
  - rationale
  - permanent denial -> app settings

### Reference Image
- Android Photo Picker integration
- Single image selection
- Picker cancellation does NOT remove existing overlay
- Invalid or unreadable reference images show a Snackbar

### Overlay Rendering
- Overlay displayed above preview using AsyncImage
- Opacity adjustable via slider (0.1-0.9)
- Overlay drag implemented
- Overlay pinch scaling implemented
- Reset restores the default alignment state
- Overlay delete is supported with confirmation and undo flow
- Overlay state is stored in ViewModel and survives normal rotation within the active session
- Live overlay coverage warning shown in top-left hint slot when less than 20 % of the overlay is visible within the viewport; soft warning only, capture always allowed

### Grid
- Grid overlay implemented as a preview-only Canvas layer
- Grid is not written into captured images

### Compare
- `CompareScreen` is implemented as a separate fullscreen slider-based comparison screen
- Tap-based fullscreen viewing mode is implemented
- Back exits fullscreen before leaving the screen
- Compare supports timestamp, delete, and optional shot title display
- Compare image load failures show a fallback UI and allow back navigation

### Compare Library
- `CompareLibraryScreen` is implemented as a focused internal session overview
- Sessions are shown in a grid
- Long-press multi-select delete is implemented with confirmation
- Titles are displayed when present
- This is not a general gallery or MediaStore browser

### Session Storage
- Successful captures with an active reference can create an internal session under `filesDir/sessions/<sessionId>/`
- Each session stores `capture.jpg`, `reference.jpg`, and `metadata.json`
- `metadata.json` includes schema version, timestamp, file names, MediaStore URI, picker URI, and optional title
- Missing, corrupt, or incomplete session metadata is ignored during scanning
- Session writes are best-effort and do not invalidate the main MediaStore save
- Session deletion only removes internal session folders

### Shot Titles
- Optional session title stored in `metadata.json`
- Missing title field is valid
- Titles are trimmed; blank titles are stored as absent
- CompareScreen allows editing and removing a title
- CompareLibrary displays titles above timestamps when present
- Title-save failures show a Snackbar

### Layout
- Fullscreen `Box` root
- Camera preview uses `fillMaxSize()`
- UI is layered above the preview and does not resize it
- Portrait controls are stacked above the bottom bar
- Landscape controls are aligned to the preview center, independent of the navigation bar
- Landscape bottom row is `Overlay` / `Capture` / `Compare`
- Landscape opacity slider is centered above the button row
- Top-right History and Overflow actions stay visible in portrait and landscape
- Overlay action menu stays visible and inside root bounds

---

## Compare Approach

The current compare approach is intentionally pragmatic and stable.

It focuses on:
- consistent rendering rules for both images
- stable slider UX
- reproducible session behavior
- clear fallback states when images cannot be loaded

It does not claim perfect geometric reconstruction of the camera preview or overlay state.

Important:
- The old geometry reconstruction / `ComparisonFrame` direction is no longer the active product approach
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping are not used to reconstruct a mathematically exact comparison frame
- The overlay remains a visual alignment aid
- The saved MediaStore capture is not composited with the overlay

---

## Storage / Privacy / Release Hardening

- Manifest declares CAMERA only
- The app has no INTERNET permission
- No analytics, telemetry, tracking, upload, or network feature is implemented
- Android Photo Picker is used for reference image selection
- Captures are saved through MediaStore under `Pictures/GhostShot`
- Internal compare sessions are stored under `filesDir/sessions/`
- `backup_rules.xml` excludes `sessions/` from Auto Backup
- `data_extraction_rules.xml` excludes `sessions/` from cloud backup and device transfer
- `SessionDeleter` validates target paths against path traversal
- `SessionStorage.updateTitle` validates target paths against path traversal
- Debug/session logs are guarded by `BuildConfig.DEBUG`
- R8 minify and resource shrinking are enabled for release
- Build and release artifacts are ignored by `.gitignore`

---

## Known Release-Relevant Residual Risks

No critical blockers are currently documented.

Accepted residual risks:
- Very large reference images can still increase memory pressure during session creation; failures are caught and the main MediaStore save remains the source of truth
- Overlay and library thumbnail image-load failures may produce limited visual feedback, but do not block navigation or core capture
- Delete failures are handled by rescanning state, but currently do not show a dedicated user-facing error

These are robustness/polish items, not known Closed Testing blockers.

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
- Use central color definitions
- Keep camera controls practical and uncluttered

### State
- ViewModel is source of truth for active camera-session state
- No persistence of active overlay/camera state across full app restarts

### Testability
- Keep logic testable
- Do not introduce complex UI logic in Composables

---

## Current Verification Notes

Existing tests cover the critical release paths around:
- ViewModel overlay/reference state
- capture lock and capture error handling
- snackbar replay protection
- session scanner/storage/deleter behavior
- title metadata read/write behavior
- compare navigation
- compare slider and fullscreen behavior
- compare library grid, navigation, selection, delete, and title display
- Camera top-right History/Overflow navigation
- stable Compare button semantics and disabled Compare hints
- Camera controls, landscape alignment, grid, and capture feedback
- Overlay coverage computation and live visibility warning state

Before Closed Testing, the useful final verification remains:
- unit tests
- connected instrumentation tests
- release build/sign/install smoke test on a real device
