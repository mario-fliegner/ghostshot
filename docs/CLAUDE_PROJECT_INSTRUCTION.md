# SameView – Claude Superprompt / Project Instruction (v1)

---

## CURRENT PRODUCT STATE ADDENDUM (2026-04-29)

### Addendum (2026-05-05)

- Capture feedback: visual flash + haptic trigger implemented
- Grid overlay implemented (purely visual, not part of capture output)
- Landscape controls aligned strictly to preview center (nav bar independent)

### Addendum (2026-05-11 – Settings)

- SettingsScreen is implemented and reachable from the CameraScreen top-right Overflow menu
- Implemented settings: Grid Type, Keep screen awake, Reset overlay after capture, Auto-open compare after capture
- Settings persist locally via DataStore Preferences
- Grid Type supports Off, Rule of Thirds, and Quarters
- Keep screen awake is CameraScreen-local and must be cleared when CameraScreen leaves composition
- Reset overlay after capture removes the reference image entirely after successful capture (clears URI, metadata, display mode, and overlay transform); it must not reset opacity or compare input
- Auto-open compare after capture is optional and must only navigate after successful capture with valid compare session data
- Auto-open compare must not replay after rotation or recomposition and must not change CompareScreen or navigation architecture


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
- When session context is present (`sessionId` + `timestamp`), displays a metadata header above the slider: title, location, or `Created <date>` fallback
- When session context is present, displays a delete button in the top bar
- Delete from `CompareScreen` removes only the internal session folder and never deletes the MediaStore photo

### Compare Screen Hybrid Fullscreen Mode

`CompareScreen` supports a tap-based fullscreen viewing mode.

Normal mode:
- Top bar is visible
- Metadata header is visible above the slider when session context is present
- Images use `ContentScale.Fit`
- Full images remain visible and may show empty margins when image aspect ratios do not match the viewport

Fullscreen mode:
- Tap on the compare viewport toggles fullscreen on and off
- Back exits fullscreen before leaving the compare screen
- Top bar is hidden
- Metadata header is hidden
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
- Fullscreen must not alter compare session storage, navigation contracts, or saved MediaStore images
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
- Schema version 6 contents: `capture.jpg`, `capture-original.jpg`, `reference.jpg`, `reference-original.jpg`, `reference-source-original.[ext]`, `metadata.json`, and optionally `branding-handle.png` (when session has branding)
- `capture-original.jpg` is a byte-for-byte copy of the MediaStore capture file
- `reference-source-original.[ext]` is a byte-for-byte copy of the reference source (extension from MIME type)
- `branding-handle.png` (optional, v6 only): normalized 512×512 RGBA PNG branding asset; present only when session branding is set; always metadata-clean
- Schema versions 2–4 contain only: `capture.jpg`, `reference.jpg`, `reference-original.jpg`, `metadata.json`
- `metadata.json` stores schema version, session identity, file references, capture geometry, GPS fields, user content, and optional branding block — full v6 schema in `SESSION_METADATA_V1.md §6.7` and `SESSION_BRANDING_V1.md §9`
- Session ID is the directory name, formatted as `YYYY-MM-DD_HH-mm-ss`
- `SessionStorage` writes sessions; current schema version is 6
- `SessionScanner` reads sessions; `SUPPORTED_VERSIONS` accepts {2, 3, 4, 5, 6}
- `SessionDeleter` deletes sessions and validates session IDs against the sessions root
- Session write is best-effort and must not block or invalidate the main MediaStore capture save
- Full session originals specification: `SESSION_ORIGINALS_V1.md`

### Comparison Output Decision

The authoritative rendering specification is `COMPARE_SESSION_RENDERING_V1.md`.

The compare rendering architecture is deterministic and geometry-based:

- Overlay transforms (overlayScale, overlayOffsetX, overlayOffsetY, referenceImageDisplayMode, viewport) are frozen at capture time via `CaptureSessionSnapshot`
- `reference.jpg` is rendered deterministically via `ReferenceRenderer.render()` with the frozen geometry
- `reference-original.jpg` stores the EXIF-oriented original, separate from the rendered compare file
- `capture.jpg` is the unmodified camera output and is never cropped or composited with the overlay
- `CompareScreen` renders only the stored session files — no geometry reconstruction at compare time
- Overlay alpha, grid, and UI elements are never rendered into any saved file

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
- minSdk = 29
- targetSdk = 35
- compileSdk = 35

Use modern Android best practices appropriate for a new app targeting current Android versions.

---

## PERMISSIONS

Allowed:
- CAMERA
- ACCESS_FINE_LOCATION (GPS Recreation System; foreground-only; lazy request triggered by Settings toggle)
- ACCESS_COARSE_LOCATION (Android 12+ platform companion to ACCESS_FINE_LOCATION; required so the system permission dialog correctly offers the "Precise" vs "Approximate" location choice; declared in manifest and included in the runtime request alongside ACCESS_FINE_LOCATION; SameView does not treat approximate location as sufficient — Recreation Guidance remains OFF unless ACCESS_FINE_LOCATION is granted)
- ACCESS_MEDIA_LOCATION (companion permission enabling Photo Picker to return unredacted GPS EXIF; not a dangerous permission)

Required selection mechanism:
- Android Photo Picker is the standard reference image import path
- SAF / ACTION_OPEN_DOCUMENT is a contextual fallback only: offered when Recreation Guidance is ON and the Photo Picker selection returned an image with no readable GPS EXIF; it is not a general-purpose second import path

Forbidden unless explicitly approved by the user:
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO
- BACKGROUND_LOCATION
- Any additional dangerous permission not listed above

Do not introduce unnecessary permissions.

---

## STORAGE

Use:
- MediaStore ONLY
- RELATIVE_PATH = Pictures/SameView
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


### Addendum (2026-05-05 – Shot Titles)

Compare Screen:
- Sessions may include an optional title
- When present, title is displayed in the metadata header above the compare slider
- Title can be edited via overflow menu (⋮ → Edit Session)
- Title changes must update immediately in UI

Compare Library:
- Each tile may display an optional title above the timestamp
- If no title is present, timestamp remains the only visible text
- Layout must remain stable regardless of title presence

Session Storage:
- metadata.json may include optional field: "title"
- Title is stored as plain string
- Missing title field is valid and must not break parsing


### Addendum (2026-06-01 – Session Backup Export)

#### Scope Clarification: Storage and Capture Behavior Constraints

The STORAGE section ("Use: MediaStore ONLY", "Save exactly one file per capture") and the CAPTURE BEHAVIOR section ("No comparison export", "No second output file") apply exclusively to the camera capture pipeline — the path from shutter press to the saved photo in `Pictures/SameView/`. They do not apply to user-initiated session backup export.

Session backup export uses `Intent.ACTION_CREATE_DOCUMENT` (Android Storage Access Framework) to write a ZIP file to user-chosen device storage. This is a session management operation, not a camera capture operation, and does not conflict with the MediaStore-only capture rule.

"Share flow" in the OUT OF SCOPE section refers to social sharing via the Android Share Sheet. User-initiated local backup to device storage via SAF is not a share flow and is in scope.

#### Session Backup Export

A user-initiated session backup feature is implemented. Full specification: `SESSION_BACKUP_EXPORT_V1.md`.

- Export is triggered from CompareScreen overflow menu (single session) or Compare Library multi-select action bar (one or more sessions)
- Output is a ZIP file written to user-chosen location via SAF `ACTION_CREATE_DOCUMENT`
- ZIP contains all four session files unchanged (`capture.jpg`, `reference.jpg`, `reference-original.jpg`, `metadata.json`)
- No confirmation dialog before the SAF picker; SAF picker is the implicit confirmation step
- No GPS stripping; backup is always a complete, full-fidelity copy
- Streaming directly to SAF OutputStream; no intermediate temp file
- No new permissions required
- No FileProvider required for this feature

#### Compare Screen (Addendum Update)

The overflow menu now contains three entries:

- Edit Title
- Remove Title (only when a title is present)
- Backup Session

Delete Session remains a dedicated icon in the top app bar, unchanged.

**Planned future top app bar structure (not yet implemented; implemented as part of the Create Video scope):**

```
← Back  |  [Create Video icon]  |  [Delete Session icon]  |  ⋮
```

Product intent for the future structure: Delete Session is the primary action when the compare result is unsatisfactory; Create Video is the primary action when the result is successful. This restructuring must not be pre-implemented with placeholders or disabled icons.

#### Compare Library (Addendum Update)

Multi-select mode action bar now contains three elements:

- Select All / Deselect All toggle
- Backup icon (exports selected sessions as ZIP; no confirmation dialog)
- Delete icon (existing behavior, unchanged; requires confirmation dialog)

**Select All** selects all sessions in the complete scanned session list, not just visible tiles.
**Deselect All** clears the selection (same toggle as Select All, state-dependent label).
After a successful backup, multi-select mode remains active and the selection is preserved.

Backup is not a Share action. It writes to local device storage via SAF.

### Addendum (2026-06-21 – Session Post-Processing Export Features)

#### Scope Clarification: Capture Behavior Constraints

The CAPTURE BEHAVIOR section contains the following rules:

- "No comparison export"
- "No collage export"
- "No side-by-side export"
- "No second output file"

These rules apply **exclusively to the camera capture pipeline** — the path from shutter press to the saved photo in `Pictures/SameView/`. They mean: when the user presses the shutter, exactly one photo is saved to MediaStore. No overlay is baked in. No second composite file is created alongside the capture.

These rules do **not** apply to user-initiated session post-processing features that operate on already-saved session files (`capture.jpg`, `reference.jpg`) as their input. Those features are separate operations, explicitly requested by the user, and governed by their own specifications.

#### Scope Clarification: OUT OF SCOPE List

The original OUT OF SCOPE list contains:

- "Video" — this referred to video recording and playback via the camera (camera video mode). It does not refer to video export from saved sessions.
- "Share flow" — this referred to social sharing as a **primary product feature** (e.g., a Share button as the main capture outcome, or deep third-party integrations). It does not refer to the Android Share Sheet used as a secondary delivery mechanism for user-initiated exports.
- "Overlay export" — this refers to exporting the overlay image itself. It is not affected by session export features.

#### Session Post-Processing Export Features — In Scope

The following session post-processing export features are **explicitly in scope**:

##### Create Video (MP4 session export)

Fully implemented. Specification: `VIDEO_EXPORT_V1.md`. Entry point: CompareScreen Export icon → "Share video". Output: MP4 in `Movies/SameView` via MediaStore. Android Share Sheet on explicit user tap only.

##### Share Comparison Image (JPEG session export)

New feature. Specification: `SHARE_COMPARISON_IMAGE_V1.md`. Implementation plan: `SHARE_COMPARISON_IMAGE_IMPLEMENTATION_PLAN.md`. Entry point: CompareScreen Export icon → "Share image". Output: JPEG in `Pictures/SameView` via MediaStore. Android Share Sheet on explicit user tap only.

Both features:

- Are user-initiated
- Operate on existing session files as input
- Write output via MediaStore (no raw file paths, no external storage)
- Use the Android Share Sheet as the delivery mechanism — not a social media integration
- Require no new Manifest permissions
- Make no network calls
- Contain no analytics, tracking, or telemetry

#### Remains Explicitly Out of Scope

The following remain out of scope regardless of this addendum and must not be implemented:

- Cloud upload or cloud sync of any session data or export file
- Server-side storage or processing
- Social media integrations (direct TikTok, Instagram, WhatsApp, YouTube API calls)
- Automatic sharing or publishing without explicit user action
- Online galleries or web viewer features
- Session synchronization across devices
- External sharing services beyond Android Share Sheet
- Background export without foreground user interaction
- Any feature requiring the INTERNET permission

#### CompareScreen TopAppBar — Current Authoritative Structure

The 2026-06-01 addendum documented a "planned future top app bar structure" with a dedicated Create Video icon. That structure was implemented as part of the Create Video scope and has since been superseded by the Export icon introduced with the Share Comparison Image feature.

The **current authoritative CompareScreen top app bar structure** is:

```text
← Back  |  [Favourite]  |  [Export]  |  [Delete Session]  |  ⋮
```

The Export icon opens a dropdown with:

- Share image → `ShareComparisonScreen`
- Share video → `CreateVideoScreen`

The overflow menu (⋮) contains:

- Edit Session
- Backup Session

Full specification: `COMPARE_FLOW_V1.md §43` and `SHARE_COMPARISON_IMAGE_V1.md §6`.
