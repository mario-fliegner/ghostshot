# OverlayPast – Claude Superprompt / Project Instruction (v1)

---

## CURRENT PRODUCT STATE ADDENDUM (2026-04-29)

This addendum documents current product decisions after the compare-flow, session-library, landscape-control, debug-logging, and hybrid-fullscreen iterations.
It supplements the existing rules below without removing or weakening them.
If a future task conflicts with this addendum, the user must make an explicit product decision before implementation.

### Compare Screen

`CompareScreen` is implemented as a fullscreen slider-based comparison screen.

- Reachable from the Camera Flow after a successful capture with a reference image
- Reachable from the Compare Library when opening a saved session
- Reference image on the left, capture image on the right
- Single horizontally draggable vertical divider, starting at 50%
- Back navigation returns to the caller
- When session context is present (`sessionId` + `timestamp`), displays a formatted timestamp below the viewport
- When session context is present, displays a delete button in the top bar
- Delete from `CompareScreen` removes only the internal session folder and never deletes the MediaStore photo

### Compare Screen Hybrid Fullscreen Mode

`CompareScreen` supports a tap-based fullscreen viewing mode.

Normal mode:
- Top bar is visible
- Timestamp is visible when session context is present
- Images use `ContentScale.Fit`
- Full images remain visible and may show empty margins when image aspect ratios do not match the viewport

Fullscreen mode:
- Tap on the compare viewport toggles fullscreen on and off
- Back exits fullscreen before leaving the compare screen
- Top bar is hidden
- Timestamp is hidden
- Outer `systemBarsPadding` is not applied
- Portrait fullscreen removes the normal viewport padding
- The compare viewport uses the maximum available screen space
- Images use `ContentScale.Crop` so the comparison appears larger and more immersive

Rules:
- Fullscreen is a viewing enhancement, not a second compare mode
- Slider comparison remains the only compare mechanic
- The slider, divider, labels, and drag behavior remain available and unchanged in fullscreen
- Both images must always use the same `ContentScale` at the same time
- Normal mode must keep `ContentScale.Fit`; fullscreen must use `ContentScale.Crop`
- Fullscreen must not alter Variant B output, session storage, navigation contracts, or saved MediaStore images
- Do not remove or simplify this behavior without an explicit product decision

### Compare Library

`CompareLibraryScreen` is implemented as a focused internal session overview.

- Accessible from the camera screen when saved sessions exist
- Displays app-created compare sessions as a 2-column grid
- Each tile shows reference thumbnail, capture thumbnail, and formatted session timestamp
- Tap opens `CompareScreen` with full session context
- Long press activates multi-select mode
- Selected sessions can be deleted via confirmation dialog
- This is not a general gallery, MediaStore browser, or device photo history

### Session Storage

Each successful capture with an active reference image can create an internal compare session.

- Location: `filesDir/sessions/<sessionId>/`
- Contents: `capture.jpg`, `reference.jpg`, `metadata.json`
- `metadata.json` stores schema version, `sessionTimestampMs`, file names, capture MediaStore URI, and reference picker URI
- Session ID is the directory name, formatted as `YYYY-MM-DD_HH-mm-ss`
- `SessionStorage` writes sessions
- `SessionScanner` reads sessions
- `SessionDeleter` deletes sessions and validates session IDs against the sessions root
- Session write is best-effort and must not block or invalidate the main MediaStore capture save

### Comparison Output Decision: Variant B

Comparison output is defined exclusively by Variant B bitmap normalization.
The overlay has no influence on comparison output.

Variant B means:

1. Rotate the captured bitmap correctly
2. Apply EXIF orientation to the reference bitmap
3. Normalize both images independently with center-crop to portrait 9:16
4. Scale both normalized images to one fixed target size
5. Use these two normalized bitmaps as the deterministic comparison pair

Important:
- The overlay is a visual alignment aid only
- Overlay position, overlay scale, viewport size, and preview-to-capture mapping are not part of the comparison model
- Geometry-based comparison logic must not be reintroduced without explicit approval
- The reference overlay is never baked into the saved full camera image

### Debug Logging

Internal debug logging is allowed and expected during development.

Rules:
- Debug logs must be non-user-facing
- Debug logs must stay compatible with release/debug controls
- Do not log full URIs, internal file paths, or user-sensitive content


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
5. Save captured image

The app must stay focused on this flow.
Do not expand the product scope unless explicitly instructed.

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

Save exactly one file per capture.

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

There must be a clear, explicit mode switch between two interaction modes.

### Mode 1: Overlay Adjust Mode
Gestures affect ONLY the overlay:
- One-finger drag = move overlay
- Two-finger pinch = scale overlay

### Mode 2: Camera Zoom Mode
Gestures affect ONLY the live camera view through camera zoom:
- Two-finger pinch = camera zoom

Important:
- Do not mix overlay manipulation and camera zoom in the same gesture context
- The user must always know which mode is active
- The mode switch must be clearly visible in the UI
- The live camera image is not a freely transformable image object
- Do not implement free dragging of the live camera image

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

- Capture saves ONLY the new camera image
- The reference overlay must NOT be rendered into the saved output image
- No comparison export
- No collage export
- No side-by-side export
- No second output file

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
- Mode toggle (Overlay Adjust / Camera Zoom)
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
- switching between Overlay Adjust and Camera Zoom modes
- capture while overlay is active
- picker cancellation
- save failure handling
- drag behavior in Overlay Adjust mode
- pinch scaling in Overlay Adjust mode
- pinch zoom in Camera Zoom mode
- reset behavior
- grid toggle behavior

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
5. Camera zoom mode
6. Capture and save
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
