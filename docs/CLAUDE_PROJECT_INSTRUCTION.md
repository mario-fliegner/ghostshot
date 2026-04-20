# OverlayPast – Claude Superprompt / Project Instruction (v2)

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
5. Save captured image and a deterministic comparison frame definition

The app must stay focused on this flow.
Do not expand the product scope unless explicitly instructed.

Product promise:
- What the user aligns on screen must define the later before/after comparison result
- The app captures the full camera image, but the comparison frame is defined by the visible overlay state at capture time

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

The interaction model is comparison-first, not camera-app-first.

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
- No visual merge of reference and live image
- No collage export
- No side-by-side export
- No second user-facing image file in v1

### Comparison Output Definition (CRITICAL)

The visible overlay state at the moment of capture defines the exact comparison frame.

"What the user sees is what the user gets."

Each capture produces:
1. Full Camera Image
   - the original photo captured via CameraX
   - stored unchanged via MediaStore
2. Comparison Frame Definition (CRITICAL)

Each capture produces:
1. Full Camera Image
2. ComparisonFrame:
   - CaptureRect (normalized [0..1] in captured image)
   - ReferenceRect (normalized [0..1] in reference image)

Important:
- The comparison frame is defined ONLY by the visible overlay state at capture time
- The reference image is the alignment master
- No live camera zoom is part of v1 interaction
- The mapping must be deterministic and reproducible

   - a deterministic description of the visible comparison area
   - derived from:
     - overlay position
     - overlay scale
     - viewport size
     - preview-to-capture mapping

Important constraints:
- The overlay image is NEVER baked into the saved camera image
- The comparison is defined by geometry, not by compositing pixels into the photo
- The app must not silently change the comparison frame after capture
- The later before/after slider result must match the alignment the user saw during capture as closely as technically possible

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
- comparison crop definition behavior, where relevant

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
5. Capture and save
6. Comparison crop definition / preview-to-capture mapping
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
