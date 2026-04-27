# OverlayPast – Claude Superprompt / Project Instruction (v3)

## CURRENT PRODUCT STATE (2026-04-27)

This section documents features that are fully implemented in the current codebase.
It supplements the role and feature-scope rules that follow.
Where a rule below marks something as "out of scope", that rule governs future work; it does not retroactively remove features listed here.

### Compare Screen

`CompareScreen` is implemented as a fullscreen slider-based comparison screen.

- Reachable from the Camera Flow after a successful capture (when both reference and capture are available)
- Reachable from the Compare Library when opening a saved session
- Reference image on the left, capture image on the right
- Single horizontally draggable vertical divider, starting at 50%
- Back navigation returns to the caller
- When session context is present (`sessionId` + `timestamp`), displays a formatted timestamp below the viewport
- When session context is present, displays a delete button in the top bar (deletes internal session only)

### Compare Library

`CompareLibraryScreen` is implemented as a 2-column grid screen listing saved compare sessions.

- Accessible from the camera screen; a button appears when at least one session exists
- Each tile shows reference thumbnail, capture thumbnail, and formatted session timestamp
- Tap on a tile opens `CompareScreen` with full session context (timestamp + delete enabled)
- Long press activates multi-select mode; selected sessions can be deleted via a confirmation dialog
- The Compare Library is a focused internal session overview, not a general gallery or photo browser

### Session Storage

Each successful capture with an active reference image creates a session in internal app storage.

- Location: `filesDir/sessions/<sessionId>/`
- Contents: `capture.jpg`, `reference.jpg`, `metadata.json`
- `metadata.json` stores: schema version, `sessionTimestampMs`, file names, capture MediaStore URI, reference picker URI
- Session ID is the directory name (format: `YYYY-MM-DD_HH-mm-ss`, unique within the sessions root)
- `SessionStorage` is the write path; `SessionScanner` is the read path; `SessionDeleter` is the delete path
- Session write is best-effort and never blocks or affects the main MediaStore capture save

### Camera Flow Session Context

After a successful capture, `CompareInput` in `CameraUiState` contains `referenceImageUri`, `captureImageUri`, and — when a session was successfully written — `sessionId` and `timestamp`.

`MainActivity` passes `sessionId` and `timestamp` to `compareRoute` when both are present.
Camera Flow and Library Flow both open `CompareScreen` with identical session context.

### Delete Rules

- Delete in `CompareScreen` or `CompareLibraryScreen` removes only the internal session folder under `filesDir/sessions/<sessionId>/`
- The captured photo in MediaStore (`Pictures/GhostShot/`) is **never** affected by any delete action in the compare flow
- `SessionDeleter` validates the session ID against the sessions root to prevent path traversal; absolute paths and `..` segments are rejected
- There is no MediaStore delete call anywhere in the compare or session management code

### Theme / Color Rule

- All colors must be defined centrally in `Color.kt` or via `MaterialTheme.colorScheme` tokens
- No hardcoded color values in screen Composables (accepted presentational exceptions in `CompareScreen`: viewport background `Color.Black` and slider divider line `Color.White`)
- `GhostShotLightColorScheme` explicitly sets `background = Color.White` and `surface = Color.White` to override M3 default tinting (`#FFFBFE`)
- Dynamic color is intentionally disabled

---

## ROLE
You are implementing and modifying a production-ready Android app.
Follow all constraints strictly.
Do not add features outside the defined scope.
Do not remove, refactor, optimize, rename, restructure, or simplify unrelated code.
Only perform the explicitly requested changes.

---

## PRIMARY GOAL
Build a Play-compliant Android camera app for accurate before/after photography using a selectable reference image overlay.

Core user flow:
1. Pick reference image
2. Adjust overlay
3. Align live camera preview with reference
4. Capture new image
5. Save the captured image
6. Optionally save deterministic comparison helper images derived from the capture and the reference

The app must stay focused on this flow.
Do not expand the product scope unless explicitly instructed.

### Current product decision (CRITICAL)
Comparison output is defined exclusively by Variant B bitmap normalization. The overlay has no influence on comparison output.

Instead, comparison output follows **Variant B** only:

1. Rotate the captured bitmap correctly
2. Apply EXIF orientation to the reference bitmap
3. Normalize both images independently with center-crop to portrait 9:16
4. Scale both normalized images to one fixed target size
5. These two normalized bitmaps are the deterministic comparison pair

Important:
- The overlay is a visual alignment aid only
- The overlay has **NO** influence on comparison output
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping are **NOT** part of the comparison model
- Do not reintroduce geometry-based comparison logic unless explicitly instructed

---

## HARD TECH CONSTRAINTS (MUST FOLLOW)

- Language: Kotlin
- UI stack: Jetpack Compose ONLY
- No XML-based UI for new screens or new UI work
- Material 3
- Architecture: MVVM
- Dependency Injection: Hilt
- Camera stack: CameraX ONLY
- Use CameraX Preview + ImageCapture
- No direct Camera2 implementation unless explicitly required
- Single-Activity architecture
- Navigation: Navigation Compose
- minSdk = 26
- targetSdk = 35
- compileSdk = 35

Use modern Android best practices appropriate for a new app targeting current Android versions.

---

## PERMISSIONS

Allowed:
- CAMERA

Required selection mechanism:
- Android Photo Picker for reference image selection

Forbidden unless explicitly approved by the user:
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO
- Any additional dangerous permission

Do not introduce unnecessary permissions.

---

## STORAGE

Use:
- MediaStore ONLY
- RELATIVE_PATH = Pictures/OverlayPast
- JPEG output

Do not use:
- raw file paths
- legacy external storage flags
- unmanaged filesystem paths
- deprecated storage patterns

The app saves:
1. The full captured camera image
2. Optional additional derived comparison images only when explicitly in scope

Do not store legacy comparison metadata models unless explicitly requested in a dedicated task.

---

## PRIVACY / PLAY COMPLIANCE

- No analytics
- No tracking
- No telemetry
- No network calls
- No uploads
- No cloud sync
- No hidden data collection
- Fully offline by default

Any future data transfer or telemetry is out of scope unless explicitly requested.

---

## FEATURE SCOPE (STRICT V1)

### Overlay Features
The overlay is the selected reference image.

Supported:
- Transparency adjustment
- Drag to move
- Pinch to scale

Not supported:
- Manual rotation
- Cropping
- Perspective transform
- Mirroring
- AI alignment
- Auto-detection
- Auto-matching

### Transparency
- Allowed range: 10% to 90%
- Default value: 50%

### Overlay Reset
A reset action must exist.

Reset behavior:
- Reset overlay position to default
- Reset overlay scale to default
- Keep current reference image
- Keep current transparency value

### Overlay Deletion
There must be an explicit overlay removal action.
It must require confirmation to prevent accidental loss.

---

## INTERACTION MODEL

The interaction model is overlay-alignment-first.

### Overlay Interaction
Gestures affect the overlay only:
- One-finger drag = move overlay
- Two-finger pinch = scale overlay

### Comparison Display Mode
The app may offer explicit comparison display modes for how the reference is shown inside the viewport.
Examples include a fitted/default view and a comparison-oriented view.

Important:
- The live camera image is not a freely transformable image object
- Do not implement free dragging of the live camera image
- Do not add live camera zoom unless it is explicitly requested in a dedicated task
- The primary user control is overlay alignment, not camera manipulation
- Any comparison mode UI must be clear and low-friction

---

## GRID / ALIGNMENT HELP

Provide:
- Optional 3x3 grid overlay
- User can toggle it on/off

Do not implement in v1:
- center-line-only mode as a separate feature
- horizon leveling
- snapping system
- perspective guides
- advanced alignment helpers

---

## CAPTURE BEHAVIOR

- Capture saves the new full camera image
- The reference overlay must NOT be rendered into the saved output image
- No visual merge of reference and live image unless explicitly defined in scope
- No collage export unless explicitly defined in scope
- No side-by-side export unless explicitly defined in scope
- Additional derived images are only allowed when explicitly defined in scope

### Comparison Output Definition (CRITICAL)

Current valid comparison output model is **Variant B only**.

Each successful capture may produce:
1. Full Camera Image
   - the original photo captured via CameraX
   - stored via MediaStore
2. Variant B Comparison Pair
   - normalized capture bitmap
   - normalized reference bitmap

The normalized pair is defined ONLY by:
- capture rotation correction
- reference EXIF orientation
- center-crop to portrait 9:16
- scaling to a fixed target size

Important constraints:
- The overlay image is NEVER baked into the saved camera image
- The comparison is currently defined by deterministic bitmap normalization, not by viewport/overlay geometry
- The app must not silently switch back to geometry-based comparison logic
- Any future change away from Variant B requires an explicit product decision

After successful capture:
- Stay on the camera screen
- Keep overlay state during the current session
- Show short, non-blocking success feedback

On save failure:
- Do not crash
- Keep the current state where possible
- Show a short, clear error message

---

## STATE RULES

### During active session
Preserve:
- selected reference image
- overlay position
- overlay scale
- overlay transparency
- relevant screen UI state
- active mode where appropriate

The user must not lose the current working setup because of normal lifecycle changes.

### Across app restarts
Do NOT persist:
- selected reference image
- overlay position
- overlay scale
- overlay transparency
- previous session state

After a full app restart:
- App starts empty
- No automatic session restore

---

## LIFECYCLE / ORIENTATION

The app must support:
- Portrait
- Landscape

The app must preserve active session state across:
- rotation
- recomposition
- temporary backgrounding
- normal lifecycle recreation within the same session

The app must NOT restore prior session state after full restart.

Preview, overlay, and controls must remain usable in both portrait and landscape.

---

## CAMERA RULES

- Back camera only
- No video support
- Autofocus enabled
- No flash in v1
- No gallery browser
- No in-app media browser

Camera preview and image capture must work reliably with the overlay UI layered on top.

---

## UI REQUIREMENTS

The UI must remain clear, minimal, and practical.

Core controls must be available and clearly reachable:
- Camera preview
- Reference image picker
- Capture button
- Comparison display mode control, if such a mode is exposed in the UI
- Transparency slider
- Reset action
- Grid toggle
- Overlay delete action

Rules:
- No hidden important functionality
- No unnecessary menus
- No cluttered control layout
- Main actions must remain reachable while using the app
- The active mode must be visually obvious
- The overlay must not become practically unrecoverable
- Use reset as the recovery mechanism

Default expectations:
- Overlay starts centered
- Overlay starts at a sensible default fit/scale
- Grid default can be off

---

## ERROR HANDLING

Gracefully handle:
- user cancels picker
- invalid image URI
- image loading failure
- camera unavailable
- camera initialization issues
- save failure
- SecurityException
- IOException

Rules:
- No crashes
- No silent broken state
- Show short and clear user-facing messages
- Preserve state where possible during recoverable errors

---

## PERFORMANCE

- Downsample large images where appropriate
- Avoid unnecessarily large in-memory bitmaps
- Keep gesture interactions smooth
- Avoid unnecessary recomputation during Compose updates
- Prefer efficient image handling and reasonable memory usage

Do not introduce heavy or unnecessary processing for v1.

---

## TESTING REQUIREMENTS

At minimum, the implementation must consider and support tests for:
- rotation with active overlay
- comparison display mode switching, where applicable
- capture while overlay is active
- picker cancellation
- save failure handling
- drag behavior for overlay alignment
- pinch scaling for overlay alignment
- reset behavior
- grid toggle behavior
- Variant B normalization behavior, where relevant
- bitmap lifecycle / recycle behavior, where relevant

Where relevant:
- unit tests for logic
- instrumentation / UI tests for flows

Do not invent an oversized test matrix beyond scope, but do not ignore the critical interaction paths.

---

## OUT OF SCOPE (DO NOT IMPLEMENT)

- Video
- Front camera
- Overlay export
- Share flow
- Gallery
- Cloud sync
- History
- Multi-project/session management
- AI features
- Automatic alignment
- Advanced editing tools
- Unrequested visual redesigns
- Unrequested refactors
- Unrequested architectural rewrites
- Reintroducing geometry-based comparison output without explicit approval

---

## CHANGE RULES FOR EXISTING CODE

This is critical:

- Only make the requested changes
- Do not remove unrelated code
- Do not optimize unrelated code
- Do not refactor unrelated code
- Do not rename unrelated classes, files, methods, variables, or resources
- Do not reformat unrelated files just for style
- Do not "clean up" surrounding code unless explicitly requested
- Do not silently alter behavior outside the requested scope
- Preserve existing functionality unless the requested change directly requires modification

If a requested change requires touching related code, keep those changes as small and localized as possible.

---

## CODE OUTPUT RULES

When providing code:
- Always include the FULL file path before each file
- Be explicit about which file is new and which file is changed
- If the user wants file modifications, provide the COMPLETE file content unless the user explicitly asks otherwise
- Do not provide partial snippets when full files are needed for safe application
- Do not omit important surrounding code required to understand placement

Preferred file heading format:
- `// path: app/src/main/java/.../FileName.kt`

If XML is ever touched for legacy reasons, also include the full path.
If Gradle files are touched, include the full path.
If Manifest is touched, include the full path.

---

## COMMENTING / DOCUMENTATION RULES

All code comments must be written in English.

All public classes and public functions must include concise English Javadoc-style documentation where appropriate.

Documentation should cover:
- purpose
- inputs / outputs where relevant
- important behavior
- threading / coroutine expectations where relevant
- important error handling where relevant

Do not add useless boilerplate comments.
Comments must be concise, useful, and technically accurate.

---

## IMPLEMENTATION PRIORITY

When building from scratch or extending the feature, prefer this order:
1. Camera preview
2. Reference image picker
3. Overlay rendering
4. Overlay drag/scale gestures
5. Capture and save
6. Variant B normalization and derived comparison outputs
7. Grid / reset / delete controls
8. Error handling
9. Tests
10. UI polish

---

## RESPONSE DISCIPLINE

When answering implementation requests:
- Stay within the requested scope
- Do not add speculative extras
- Do not invent product decisions not present in this instruction
- If something is not defined here and must be decided, choose the simplest solution consistent with the existing scope
- Keep changes surgical and controlled

---

## FINAL RULE

If uncertain:
- choose the simpler solution
- preserve existing code
- do not add features
- do not remove unrelated code
- stay inside this specification
