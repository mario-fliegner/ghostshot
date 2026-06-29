# COMPARE_FLOW_V1.md

## 1. DOCUMENT STATUS

This document is the **authoritative execution specification** for the V1 compare flow in the SameView Android app.

It is written for:
- AI coding systems
- implementation sessions
- analysis sessions
- regression-safe follow-up work

This document is intentionally explicit.
It must reduce interpretation to the smallest possible degree.

If an implementation proposal conflicts with this document, this document wins.

---

## 2. FEATURE PURPOSE

The compare feature exists to answer one core user question immediately after capture:

**"Did I match the reference?"**

The compare feature is NOT intended to become:
- a photo editor
- a gallery
- a history browser
- a multi-step workflow
- a settings-heavy feature
- a general image review module

The compare flow is a focused product feature directly tied to the app's main promise:

- choose reference
- align in camera
- capture new image
- compare reference vs new image

The compare rendering architecture is defined by `COMPARE_SESSION_RENDERING_V1.md`. Overlay geometry (scale, offset, display mode, viewport) is frozen at capture time and rendered deterministically into `reference.jpg`. `CompareScreen` renders passively from the stored session files only.

> Note (2026-04-27): A Compare Library is now implemented as part of V1. It is a focused internal session overview and does not expand the feature into a gallery, history browser, or general image review module. Sessions are only the internal capture+reference pairs created by the app's own capture flow. The Compare Library does not browse MediaStore or the device photo library. The product purpose described in this section remains unchanged.

---

## 3. PRODUCT DECISION (MANDATORY)

### V1 compare MUST be:

- a **separate fullscreen compare screen**
- reachable from the camera flow
- visually simple
- immediately understandable
- interactive through a **single slider-based comparison**
- easy to exit with clear back navigation

### V1 compare MUST NOT be:

- embedded inside the camera screen
- implemented as a dialog
- implemented as a bottom sheet
- implemented as a temporary overlay over the live camera
- implemented as a multi-mode comparison tool
- implemented as a side-by-side gallery view

This is a mandatory product decision, not a suggestion.

---

## 4. CORE UX FLOW

The intended user flow is:

1. User selects a reference image
2. User aligns the live camera preview with the reference
3. User captures a new image
4. User taps **Compare Images**, or Compare opens automatically if the optional setting `Auto-open compare after capture` is enabled
5. A dedicated fullscreen compare screen opens
6. User compares the images using a draggable slider
7. User returns to camera using back
8. Camera session is still intact

This means the compare flow is a **review mode**, not a camera mode.

---

## 5. MINIMAL V1 SCOPE

### Included in scope

V1 MUST include all of the following:

- Compare entry action from camera flow
- Compare screen as separate destination/screen
- Display of reference image
- Display of capture image
- Stacked image comparison
- Vertical divider / handle
- Horizontal drag interaction for split position
- Clear return/back behavior
- Proper handling when images are missing
- i18n-compliant visible text
- tests for compare entry, navigation, compare screen presence, and slider behavior

### Explicitly out of scope

The following are NOT allowed in V1:

- side-by-side comparison mode
- tap-to-toggle comparison mode
- compare mode selector
- image swapping left/right
- zoom
- pan
- pinch gestures
- crop tools
- edit tools
- image export
- share flow
- compare history
- session browser
- compare settings menu
- persistence of compare preferences across app restarts
- overlay compare inside the live camera screen

If a future enhancement requires any of the above, that is a separate feature and separate prompt.

> Note (2026-04-27): The items "compare history" and "session browser" in the list above refer to general-purpose browsing or history features. The Compare Library now implemented in V1 is not that: it is a narrowly scoped list of the app's own internal capture+reference sessions, limited to what the capture flow creates, with no MediaStore browsing or device gallery access. This does not contradict the out-of-scope items above.

---

## 6. COMPARE SCREEN REQUIREMENTS

### Screen type

The compare UI MUST open as a **new fullscreen screen**.

It MUST NOT reuse the camera screen as a compare host.

### Why this is mandatory

The camera screen already owns:
- live preview
- overlay state
- camera lifecycle
- camera interactions
- capture logic

The compare screen is a separate concern:
- static image review
- direct reference vs capture validation
- no live preview interaction

Mixing both concerns in one screen is forbidden in V1.

---

## 7. NAVIGATION CONTRACT

### Entry

Compare is entered via a user-visible action:
- button or equivalent clear action labeled **Compare Images**
- this label MUST use string resources
- this action belongs logically to the camera flow

### Entry availability

The compare entry MUST only be enabled when BOTH are available:
- reference image
- captured image

If one or both are missing, the compare entry MUST NOT behave as if compare is possible.

Permitted V1 behaviors:
- button disabled
- button hidden

Preferred behavior:
- button remains present but disabled when compare is impossible

### Exit

The compare screen MUST support:
- system back
- explicit top-left back navigation

Both must return to the camera screen.

### Return requirements

Returning from compare to camera MUST preserve the active camera session state already held by the app, including:

- selected reference image
- overlay state within the current session
- camera screen context
- active compare session state (`compareInput`)

Returning MUST NOT:

- clear the reference image
- reset the camera screen unexpectedly
- cause a snackbar replay
- lose current session setup
- disable the Compare button when an active compare session still exists

---

## 8. CAMERA LIFECYCLE CONTRACT

The compare screen is a separate review destination.

### Required behavior

- the camera UI is not the active foreground screen while compare is open
- compare must not behave as a translucent layer over the live camera
- compare must not expose camera interactions

### Important implementation meaning

This does NOT require destructive clearing of camera-related state.
It means:
- compare is conceptually separate
- camera preview is not the active interaction layer during compare
- resource handling should stay lifecycle-correct

### Forbidden behavior

- keeping compare and live camera active as competing simultaneous interactive layers
- allowing the user to still use capture/zoom/overlay controls while compare is open

---

## 9. COMPARE MODE CONTRACT

V1 allows **exactly one comparison mode**.

### Mandatory mode: slider compare

Required behavior:
- both images are stacked in the same viewport
- one image is visually revealed on the left side
- the other image is visually revealed on the right side
- a vertical divider indicates the split
- the divider is horizontally draggable

### Required image order

- Left side = Reference image
- Right side = Current image

This ordering is mandatory for V1 and must not be made configurable now.

### Forbidden compare modes in V1

- side-by-side
- tap toggle as an alternate compare mechanic
- swipe between full images
- overlay opacity comparison
- alternate compare tabs
- compare mode switching UI

The tap-based fullscreen viewing mode described in section 11A is explicitly not a second compare mode. It does not replace or alter the slider compare mechanic.

---

## 10. SLIDER UX CONTRACT

### Initial position

The slider MUST start at **50%** of the available comparison width.

This is mandatory.

### Interaction

Allowed:
- horizontal drag to change split position

Forbidden:
- vertical drag as main compare mechanic
- pinch zoom
- pan
- multi-touch compare gestures
- rotation gestures

### Visual requirement

The divider must be clearly visible.
A minimal handle or line is allowed and recommended.

The goal is direct discoverability:
the user should instantly understand that the split can be dragged.

### UX priority

The slider is the core comparison mechanic.
It must remain simple.
Do not overload it with:
- extra controls
- mode switches
- gesture ambiguity

---

## 11. IMAGE RENDERING CONTRACT

This section is critical.

### Both images MUST use:

- the same layout container
- the same viewport dimensions
- the same alignment logic
- the same scaling logic

### Forbidden

It is strictly forbidden that:
- one image uses a different content scale than the other
- one image is independently aligned
- one image is independently transformed for presentation
- compare artifacts are introduced by layout mismatch

### Why this is mandatory

The compare result must represent the actual visual difference between:
- reference
- capture

It must NOT represent:
- different rendering rules
- different fit/crop choices per image
- layout artifacts

### Minimal requirement

The compare screen must render both images as if they belong to the same presentation system.

---

## 11A. FULLSCREEN VIEWING MODE (V1 ADDENDUM 2026-04-29)

`CompareScreen` supports a tap-based fullscreen viewing mode.

### Purpose

Fullscreen exists only to make the existing slider comparison visually larger and more immersive.

It is NOT:
- a second compare mode
- a tap-to-toggle compare mechanic
- a replacement for the slider
- a new image rendering pipeline
- an export or editing feature

### Trigger

Allowed:
- Tap on the compare viewport toggles fullscreen on and off

Required behavior:
- Slider drag remains unchanged
- The divider remains draggable in fullscreen
- A quick tap on the viewport may toggle fullscreen
- No coordinate-based divider exclusion is required for V1
- No slider state hoisting is required for fullscreen

### Normal mode rendering

In normal mode:
- Top bar is visible
- Timestamp is visible when session context is present
- Images use `ContentScale.Fit`
- Full images remain visible
- Empty margins / gray areas may appear when image aspect ratios do not match the viewport

### Fullscreen rendering

In fullscreen mode:
- Top bar is hidden
- Timestamp is hidden
- Outer `systemBarsPadding` is not applied
- The viewport uses the maximum available screen space
- Images use `ContentScale.Crop`
- Images may be proportionally cropped
- Empty margins / gray areas may be reduced or disappear
- No stretch or distortion is allowed

### Render consistency rule

At any moment, both compare images MUST use the same rendering rules.

Required:
- Normal mode: both images use `ContentScale.Fit`
- Fullscreen mode: both images use `ContentScale.Crop`
- Both images keep the same alignment and viewport dimensions

Forbidden:
- one image using `Fit` while the other uses `Crop`
- changing only one side of the comparison
- stretching images to fill the viewport
- changing saved MediaStore output behavior or internal session behavior

### Back behavior

Required:
- If fullscreen is active, Back exits fullscreen first
- If fullscreen is not active, Back exits the compare screen as usual

### State

Fullscreen state is compare-screen-local UI state.

Allowed:
- `rememberSaveable` local state so fullscreen can survive normal rotation

Forbidden:
- storing fullscreen as a global preference
- persisting fullscreen across app restarts
- moving fullscreen state into camera state or session metadata

---

## 12. ORIENTATION CONTRACT

The compare screen MUST support:
- portrait
- landscape

### Mandatory behavior

- images remain correctly oriented
- compare screen remains usable
- slider remains usable
- compare stays the same product concept in both orientations

### Forbidden behavior

- switching compare mode based on device rotation
- changing into a totally different compare UI in landscape
- introducing separate landscape-only compare logic unless strictly needed by layout

### Goal

The compare experience must feel consistent across rotation.

---

## 13. MISSING DATA / ERROR CONTRACT

### Entry state when data is missing

If compare input is incomplete:
- compare must not open as if valid
- entry must be blocked in a clear and stable way

### Required invalid-input conditions

If either is missing:
- reference image
- capture image

then compare must not behave as a valid flow.

### Load failure handling

If a compare image cannot be loaded:
- show a simple fallback UI
- do not crash
- allow back navigation
- do not trap the user on a broken screen

### Forbidden behavior

- silent blank screen
- crash on missing/invalid data
- showing compare UI with only one valid image as if comparison is still valid

---

## 14. STATE CONTRACT

### Compare screen input

The compare screen must receive compare input clearly and explicitly.

At minimum:
- reference image input
- capture image input

This input must be sufficient to render the compare screen without re-deriving unrelated camera state.

The compare screen must not depend on reconstructing the previous camera preview geometry.
Overlay position, overlay scale, viewport size, and preview-to-capture mapping are frozen
into `reference.jpg` at capture time (see `COMPARE_SESSION_RENDERING_V1.md`). `CompareScreen`
does not recompute these at render time — it renders only the already-finalized session files.

### Persistent compare session state vs transient capture lock

These two concerns must be kept separate in the ViewModel:

**Persistent compare session state** (`compareInput`):

- represents the last successful capture paired with its reference
- must survive camera lifecycle transitions (CameraX rebind, preview pause/resume)
- must survive navigation to and from CompareScreen
- is cleared only by: reference removed, reference replaced, or a new successful capture

**Transient capture lock** (`isCaptureInProgress`):

- represents an in-flight capture attempt
- always reset to false after success, failure, or interrupt
- must NOT clear `compareInput` on interrupt or error — the previous session remains valid

### Compare-local state

V1 compare-local state may include:
- slider position
- fullscreen viewing state

### Compare-local state rules

Allowed:
- local UI state for slider position
- state surviving recomposition
- state surviving normal rotation if implemented cleanly

Not required for V1:
- persistence across app restart
- global remember of last slider position
- compare user preferences

### Forbidden

- global compare settings
- session persistence expansion outside current scope
- using compare feature as a reason to redesign overall app state architecture

---

## 15. PREPARATION FOR FUTURE EXTENSIONS

V1 must remain minimal.
However, the implementation must not block obvious future extension.

### Future extensions that may exist later

Examples only, DO NOT implement now:
- additional compare modes
- side-by-side mode
- overlay opacity compare
- zoom/pan
- image swap
- labels toggle
- compare annotations

### What "prepare" means in V1

Allowed:
- naming that does not block future compare mode extension
- structure that keeps compare-screen-specific state separated from camera state

Forbidden:
- implementing future mode infrastructure now
- building unused abstraction layers
- introducing speculative settings/models just in case

---

## 16. I18N CONTRACT (STRICT)

This section is mandatory.

### General rule

All user-facing visible text MUST use string resources.

### Absolutely forbidden

- hardcoded visible strings in Composables
- hardcoded visible strings in screen code
- hardcoded visible strings in fallback UI
- hardcoded button labels
- hardcoded compare labels

### Mandatory string-resource coverage

At minimum, if visible in V1:
- compare screen title
- compare entry label ("Compare Images")
- back content description if shown
- fallback/error text
- optional image labels such as "Reference" and "Current"

### Implementation rule

Use the app's existing i18n/string-resource approach consistently.

Do not introduce compare UI text outside that system.

### AI execution rule

If a new visible text appears in implementation and is not backed by string resources, that implementation is invalid.

---

## 17. RECOMMENDED MINIMUM I18N KEYS

The exact final key names may follow project conventions, but the feature MUST include equivalent string-resource entries for at least the following meanings:

- compare_images
- compare_screen_title
- compare_label_reference
- compare_label_capture
- compare_error_missing_images
- compare_error_load_failed
- compare_back

If the app already uses different naming conventions, follow the existing naming style.
Do not invent a second naming system.

---

## 18. TESTING CONTRACT (STRICT)

This section is mandatory.
No compare implementation is acceptable without tests.

### General rule

Each implementation step must include tests for the exact introduced scope.

### Forbidden

- skipping tests
- postponing tests to "later"
- disabling tests to make the build pass
- silently rewriting existing tests to hide regressions
- changing unrelated tests without explicit need

---

## 19. TEST SCOPE FOR STEP 1 (ANALYSIS / PLANNING)

The analysis phase must identify at least:

- which existing tests might be affected
- which new tests must be added
- which regressions must be guarded against

This phase does not need code yet, but test planning is mandatory.

---

## 20. TEST SCOPE FOR STEP 2 (COMPARE SCREEN + NAVIGATION)

When implementing the compare screen shell / route / navigation, the implementation MUST include tests covering at least:

### Compare entry tests

1. Given both images are available  
   When the user activates Compare Images  
   Then the compare screen opens

2. Given compare input is incomplete  
   Then Compare Images is not enabled as a valid action

### Compare screen presence tests

3. Compare screen renders a distinct compare screen UI  
4. Compare screen can be exited using back

### Return tests

5. Given user returns from compare  
   Then camera screen is visible again  
   And current session context is still intact

### Regression tests

6. Existing camera flow tests remain green  
7. No snackbar replay regression is introduced by compare navigation

---

## 21. TEST SCOPE FOR STEP 3 (SLIDER INTERACTION)

When implementing slider interaction, tests MUST include at least:

### Rendering tests

1. Both images are present in compare screen
2. Slider/divider is visible
3. Initial slider position is centered at 50%

### Interaction tests

4. Dragging the slider changes the visible split
5. Slider remains usable after recomposition

### Rotation tests

6. Rotating device does not crash compare screen
7. Rotating device preserves valid compare rendering
8. Slider remains functional after rotation, or at minimum the screen remains valid and interactive if slider position resets intentionally

### Back navigation regression tests

9. Returning from compare after interaction still returns to valid camera screen state

---

## 22. FAILURE CONDITIONS FOR TESTING

The implementation must be considered failed if any of the following occurs:

- compare screen opens without valid inputs
- compare UI shows hardcoded user-facing text
- both images do not render under the same presentation logic
- slider is missing
- slider initial position is not centered
- compare causes crash on rotation
- compare breaks back navigation
- compare loses current session unexpectedly
- compare introduces snackbar replay regression
- previously green camera tests become red

---

## 23. EXISTING TEST PROTECTION

This project already has fragile and important regression coverage.

The compare feature MUST NOT break unrelated existing tests.

Especially important:
- camera overlay tests
- bitmap recycle tests
- snackbar replay protections
- bottom controls spacing / alignment tests
- camera screen state tests

If compare work breaks unrelated existing tests, that compare implementation is unacceptable.

---

## 24. HARD CONSTRAINTS (STRICTLY FORBIDDEN)

The following are strictly forbidden unless the user explicitly broadens scope:

- modifying the capture pipeline
- modifying bitmap/session processing logic
- using compare as a reason to redesign camera architecture
- embedding compare UI inside camera screen
- adding side-by-side or other extra compare modes
- adding zoom/pan
- adding global compare settings
- adding compare history
- adding export/share
- refactoring unrelated code
- changing unrelated layout logic
- touching unrelated test files without necessity
- changing working behavior outside compare feature

Any of the above counts as a scope violation.

---

## 25. IMPLEMENTATION DISCIPLINE FOR AI SYSTEMS

AI systems working from this document MUST follow these rules:

1. Solve only the requested compare step
2. Keep changes minimal and localized
3. Do not speculate beyond scope
4. Do not "improve" unrelated code
5. Do not rename unrelated symbols
6. Do not reformat unrelated code
7. Do not build future compare systems now
8. Always include tests for the current step
9. Always use string resources for visible text
10. Prefer the simplest working solution consistent with this document

---

## 26. PREFERRED IMPLEMENTATION SPLIT

This compare feature should be implemented in controlled steps.

### Recommended step 1
Analysis only:
- files affected
- navigation path
- compare input contract
- test plan

### Recommended step 2
Implementation of:
- compare route/screen shell
- compare entry
- back navigation
- missing input handling
- tests for these behaviors
- i18n wiring for visible text

### Recommended step 3
Implementation of:
- actual slider compare
- divider interaction
- render contract
- slider tests
- rotation safety tests

This split is preferred to reduce regression risk.

---

## 27. FINAL UX STANDARD FOR V1

When implemented correctly, the user experience must feel like this:

- user captures a photo
- user taps Compare Images
- fullscreen compare opens immediately
- user instantly sees reference on the left and new capture on the right
- user drags the divider and understands the result without explanation
- user presses back
- camera screen is ready again without lost session context

This is the V1 success condition.

---

## 28. FINAL SUMMARY

The V1 compare feature is:

- fullscreen
- separate from camera
- slider-only
- minimal
- i18n-compliant
- test-backed
- regression-safe
- not a general image editor
- not a multi-mode compare system

Anything beyond that is out of scope for V1.

---

## 29. AUTHORITATIVE EXECUTION RULE

If an AI model is unsure how to proceed:
- choose the smaller scope
- preserve existing code
- keep compare isolated
- do not expand features
- do not modify unrelated systems
- do not skip tests
- do not hardcode strings

This rule is mandatory.

---

## 30. COMPARE LIBRARY (V1 EXTENSION)

The Compare Library is a V1 feature providing a focused overview of saved compare sessions.

### What it is

- A grid-based screen listing sessions created by the app's own capture flow
- Each session is a `capture.jpg` + `reference.jpg` pair stored in internal app storage under `filesDir/sessions/<sessionId>/`
- Sessions are displayed with reference and capture thumbnails and a formatted timestamp
- Tap on a session tile opens `CompareScreen` with full session context

### What it is not

- Not a general gallery
- Not a MediaStore browser
- Not a device photo history
- Not an arbitrary image comparison tool

### Multi-select delete

Long press on a session tile activates multi-select mode. Selected sessions can be deleted via a confirmation dialog. Delete removes only the internal session folder; MediaStore photos are unaffected.

### Navigation

Accessible from the camera screen through the persistent top-right History action.

The History action:
- is always visible
- opens the Compare Library directly
- remains available even when no sessions exist
- may show the Compare Library empty state

The Compare bottom-bar action must not dynamically switch to Library/History.
After deletion, the library refreshes and either shows the remaining sessions or an empty-state message.

---

## 31. SESSION CONTEXT IN COMPARESCREEN

`CompareScreen` accepts optional session context parameters: `timestamp: Long?`, `onDelete: (() -> Unit)?`.

### When session context is present

- A formatted timestamp is shown below the image viewport
- A delete button is shown in the top bar
- Tapping delete opens a confirmation dialog
- Confirmed delete calls `onDelete`, which removes the internal session and navigates back

### When session context is absent

- Timestamp is not shown
- Delete button is not shown
- The screen acts as a transient compare viewer

### Rule

Both the Camera Flow and the Library Flow must provide session context to `CompareScreen` when a valid session exists. A `CompareScreen` opened after a successful capture with a reference image must receive `sessionId` and `timestamp`.

---

## 32. CAMERA-FLOW VS. LIBRARY-FLOW CONSISTENCY

Both navigation paths lead to the same `CompareScreen` composable.

### Camera Flow

After a successful capture, `CompareInput` in `CameraUiState` contains `referenceImageUri`, `captureImageUri`, `sessionId`, and `timestamp`. `MainActivity` passes all four values to `compareRoute`, so `CompareScreen` opens with full session context.

### Library Flow

When a session tile is tapped in `CompareLibraryScreen`, `ScannedSession` provides `referenceFileUri`, `captureFileUri`, `sessionId`, and `timestamp`. `MainActivity` passes all four values to `compareRoute`, so `CompareScreen` opens with full session context.

### Requirement

The UX of `CompareScreen` must be identical regardless of which flow opened it, whenever valid session data is available.

---

## 33. DELETE CONTRACT

Delete in the compare flow follows strict rules.

### What gets deleted

Only the internal session folder: `filesDir/sessions/<sessionId>/`, including `capture.jpg`, `reference.jpg`, and `metadata.json`.

### What does not get deleted

The captured photo in MediaStore (`Pictures/SameView/`) is never affected. No other files, preferences, or app data are touched.

### How delete is performed

`SessionDeleter.delete(sessionsRoot, sessionId)` validates that the resolved path is a direct child of `sessionsRoot`. Path traversal via `..` or absolute paths in `sessionId` is rejected. After deletion, `CameraViewModel.deleteSessions()` rescans to update `savedSessions`.

### Triggering delete

From `CompareScreen`: single session delete via confirmation dialog when `onDelete` is provided.
From `CompareLibraryScreen`: multi-session delete via multi-select and confirmation dialog.

---

## 34. IMPLEMENTATION STATUS

The following components are implemented and are part of the current release state.

| Component | Status |
| --- | --- |
| `CompareScreen` | Implemented |
| `CompareLibraryScreen` | Implemented |
| `SessionStorage` (returns `SavedSessionRef`) | Implemented |
| `SessionScanner` | Implemented |
| `SessionDeleter` | Implemented |
| `CompareInput` with `sessionId`/`timestamp` | Implemented |
| Camera Flow session context propagation | Implemented |
| Library Flow session context propagation | Implemented |
| Theme: `background`/`surface` override in light mode | Implemented |
| Compare fullscreen viewing mode | Implemented |
| Session title support | Implemented |
| Compare/library delete behavior | Implemented |
| CameraScreen stable bottom workflow `Reference` / `Capture` / `Compare` | Implemented |
| CameraScreen top-right History and Overflow navigation | Implemented |
| Short disabled-Compare snackbar hints | Implemented |

Closed-testing status:
- No known critical blocker is documented for the compare flow
- Release-relevant risks are limited to bounded robustness/polish cases
- Final pre-upload verification should use the current unit, instrumentation, and release-install smoke flows

---

## 35. CAMERA SCREEN LANDSCAPE CONTROL CONTEXT (2026-05-08)

This document remains focused on the compare flow.
This section only protects the camera-to-compare entry point and prevents layout regressions around the `Compare` control.

CameraScreen UX semantics are now defined by `CAMERA_WORKFLOW_UX_V1.md`.
If this section conflicts with `CAMERA_WORKFLOW_UX_V1.md`, the CameraScreen UX specification wins for CameraScreen layout and navigation semantics.

### Current camera-control decision

Landscape camera controls must mirror the portrait structure conceptually:

- Bottom row: `Reference` / `Capture` / `Compare`
- Opacity slider: separate row above the bottom row
- Sessions/History access: top-right CameraScreen navigation
- History access: persistent top-right CameraScreen navigation
- Overflow access: persistent top-right CameraScreen app-level actions
- Current overflow entries: Settings and About

### Required landscape invariants

- Capture remains exactly centered at the bottom of the root
- Overlay remains left of capture
- Compare remains right of capture
- Overlay and Compare are symmetrically spaced around capture
- Compare remains a stable workflow action and must not dynamically switch to Shots/History
- Sessions/History must not replace Compare in the bottom row
- Slider is centered above capture
- Slider is above the button row
- Slider width is no greater than the button-group width
- Slider remains inside root bounds
- Overlay action menu remains visible, inside root bounds, and visually above the slider when overlapping

### Forbidden approaches

- Reintroducing `Shots` / `History` as the right bottom button
- Dynamically switching the right bottom button between `Compare` and `Shots` / `History`
- Hiding Sessions/History inside the Compare button
- Placing Settings or app-level overflow actions in the bottom row
- Placing the opacity slider to the right of Compare
- Calculating slider width from remaining right-side space
- Reusing `safeEndPadding`, `rightControlsStart`, or equivalent right-control width logic for the slider
- Inline slider in the bottom button row
- Fallback alignments that move the slider to TopEnd or BottomStart
- Moving capture away from bottom center
- Squeezing Overlay or Compare with hard equal-width button constraints

### Compare-flow relevance

The `Compare` entry must remain reachable and visually stable in landscape.
History access belongs to the top-right CameraScreen navigation area and must not take over the bottom-right workflow slot. The top-right Overflow action belongs to app-level actions such as Settings and About.
The landscape layout fix must not change `CompareScreen`, session storage, delete behavior, or compare navigation contracts.

### Disabled Compare hint timing

Disabled Compare taps may show short workflow hints. These hints should dismiss after approximately 2000 ms and must not become navigation anchors or persistent error states.

---

## 36. COMPARE SCREEN HYBRID FULLSCREEN STATUS (2026-04-29)

The compare screen now includes the V1 fullscreen viewing behavior described in section 11A.

Implemented behavior:
- Tap on the compare viewport toggles fullscreen
- Back exits fullscreen before leaving the compare screen
- Top bar and metadata header are hidden in fullscreen
- Outer `systemBarsPadding` is disabled in fullscreen
- Portrait fullscreen removes the normal viewport padding
- Normal mode uses `ContentScale.Fit`
- Fullscreen uses `ContentScale.Crop`
- Slider, divider, labels, and drag behavior remain unchanged

Test coverage added:
- Fullscreen is not the default mode
- Tapping the viewport enters fullscreen
- Back exits fullscreen without triggering compare-screen navigation

Validation status:
- covered by the current compare fullscreen/navigation instrumentation tests
- final release validation should use the current test suite rather than historical test counts


## CAMERA FEEDBACK CONSISTENCY (2026-05-05)

- Capture flash and haptic feedback exist only in CameraScreen
- Must not affect CompareScreen rendering
- Must not affect saved images
- Compare flow remains visually unaffected


## 37. SESSION TITLE SUPPORT (V1 ADDENDUM 2026-05-05)

Sessions may include an optional title.

### Behavior

- Title is metadata only, not part of comparison logic
- Title does not affect rendering or slider behavior
- Title is displayed in CompareScreen when available
- Title is displayed in CompareLibraryScreen when available

### Editing

- Title can be edited via overflow menu in CompareScreen
- Title can be removed directly without confirmation
- Title updates must reflect immediately in UI

### Constraints

- Title must not affect compare mechanics
- Title must not introduce new navigation
- Title must remain optional

---

## 38. ACTIVE COMPARE SESSION CONTRACT (2026-05-08)

### Definition

An active compare session exists when:

- a reference image is loaded in `CameraUiState`
- AND `compareInput` is non-null (last successful shot exists)

### Lifecycle

The active compare session is created when:

- a capture completes successfully with a reference image present
- `SessionStorage.saveSession()` returns a non-null `SavedSessionRef`
- `onCaptureSaved()` sets `compareInput` to the resulting session pair

The active compare session persists across:

- navigation to CompareScreen
- navigation back from CompareScreen
- camera preview pause and resume (CameraX lifecycle transitions)
- configuration changes (rotation)

The active compare session is cleared only when:

- the reference image is removed (`onReferenceImageRemoveConfirmed`)
- the reference image is replaced (`onReferenceImageSelected`)
- a new capture completes successfully (replaced with new session)

### What must NOT clear the active compare session

- `onCaptureInterrupted()` — must only release the capture lock, not clear `compareInput`
- `tryStartCapture()` — must not clear `compareInput` before the new capture result is known
- `onPhotoCaptureError()` — a failed capture does not invalidate the previous session
- Exception and OOM paths in the capture pipeline — same rule as error path

### Camera Compare button state mapping

| Condition | Compare state |
| --- | --- |
| No reference image | Disabled — hint: "Add a reference first" |
| Reference present, `compareInput` null | Disabled — hint: "Take a photo first" |
| Reference present, `compareInput` non-null | Enabled — opens active compare session |

After returning from CompareScreen, state must remain in the third row.
Returning from CompareScreen must not transition Compare back to the second row.


---

## 39. AUTO-OPEN COMPARE AFTER CAPTURE SETTING (2026-05-11)

`Auto-open compare after capture` is implemented as an optional workflow pacing setting.

Default:
- OFF

When enabled:
- after a successful capture with a valid compare session, CameraScreen emits a one-shot navigation event to CompareScreen
- the event must carry the exact `CompareInput` created from the successful session save
- the event must not be derived later by rereading camera state

Required guards:
- no auto-open without reference image
- no auto-open without valid `sessionRef` / `CompareInput`
- no auto-open on capture error
- no auto-open on capture interrupt
- no auto-open replay after rotation or recomposition

Implementation contract:
- use the existing CameraScreen `onCompareImages` callback path
- do not change MainActivity navigation architecture
- do not change CompareScreen
- do not change Compare Library
- do not change active `compareInput` lifecycle semantics
- do not introduce a generic event system

This setting does not create a second compare mode. It only changes whether the existing CompareScreen opens automatically after a successful capture.

---

## 40. SESSION BACKUP EXPORT ENTRY POINTS (2026-06-01)

The session backup export feature adds two entry points to the compare flow. Full specification: `SESSION_BACKUP_EXPORT_V1.md`.

### CompareScreen — Overflow Menu

"Backup Session" is added to the existing overflow menu.

**Complete overflow menu (updated):**

- Edit Title
- Remove Title (only when a title is present)
- Backup Session

Delete Session remains a dedicated top app bar icon, unchanged. Backup Session in the overflow menu requires a valid session context (`sessionId`). If CompareScreen is opened without session context, "Backup Session" is not shown.

Tapping "Backup Session" launches the SAF file picker with the suggested filename `SameView_<sessionId>.zip`. No confirmation dialog is shown before the picker opens.

### Compare Library — Multi-Select Action Bar

The multi-select action bar gains two additions: a Select All / Deselect All toggle and a Backup icon.

**Complete multi-select action bar (updated):**

- Select All / Deselect All (toggles between all selected and none selected)
- Backup icon (exports selected sessions as a single ZIP; no confirmation required)
- Delete icon (existing; requires confirmation dialog; behavior unchanged)

**Select All** selects all sessions in the complete scanned session list, not only the visible tiles in the scroll viewport.

After a **successful** backup, multi-select mode exits automatically and the selection is cleared; the user returns to normal library state. After a **failed** backup, multi-select mode remains active and the selection is preserved so the user can retry. A `LinearProgressIndicator` is shown below the TopAppBar while the backup is running.

### No Impact on Compare Mechanics

This feature does not change:

- Compare rendering, slider behavior, or image display
- Session storage, session scanning, or session deletion logic
- Navigation contracts from CameraScreen to CompareScreen or from Library to CompareScreen
- `compareInput` lifecycle or `savedSessions` state
- Delete confirmation dialog behavior

### Planned Future CompareScreen Top App Bar

The product-intended future top app bar structure (implemented as part of the Create Video scope, not this spec):

```
← Back  |  [Create Video icon]  |  [Delete Session icon]  |  ⋮
```

Current structure (unchanged by this spec):

```
← Back  |  [Delete Session icon]  |  ⋮
```

---

## 41. COMPARE SLIDER DATE LABELS AND HANDLE DESIGN (2026-06-10)

This section documents the product decision to replace the existing Reference/Current image-overlay badges with temporal context labels positioned adjacent to the slider handle, and to redesign the slider handle to communicate drag affordance.

The motivation for this change originated in the Session Metadata v4 work: once `reference.date` and `capture.timestampMs` became reliably available in `metadata.json`, displaying temporal context at the comparison point became feasible and desirable.

This section is the authoritative specification for the new handle design and label logic. It supersedes any previously existing badge-based label approach.

---

### 41.1 Badge Replacement

The existing Reference/Current image-overlay badges are replaced by the slider handle labels defined in this section.

Removed:

- "Reference" overlay badge positioned on or near the left image
- "Current" overlay badge positioned on or near the right image

Added:

- Text label to the left of the slider handle (representing the reference / past side)
- Text label to the right of the slider handle (representing the current / present side)

The image-level badges must be removed. Label responsibility moves entirely to the handle area. The two systems must not coexist.

---

### 41.2 Handle Design

The slider handle is redesigned as follows:

- **Shape:** filled circle
- **Fill color:** SameView CI primary blue (the current handle color, unchanged)
- **Icons:** left-facing white arrow (◀) and right-facing white arrow (▶) rendered inside the circle
- **Size:** moderately larger than the current handle point; exact pixel size is an implementation decision, not a spec decision; the size must not be so large that it significantly obscures the image content or conflicts with the "calm, low-noise" UX principle
- **Visibility:** the handle must remain always visible in all positions and both viewing modes
- **Contrast aid:** a subtle text shadow or equivalent is required on the flanking labels to ensure legibility over variable image content; the handle itself has sufficient contrast from its filled background

The existing vertical divider line remains unchanged.

---

### 41.3 Label Position and Behavior

- The left label is rendered directly to the left of the handle circle, with a small gap
- The right label is rendered directly to the right of the handle circle, with a small gap
- Both labels move horizontally with the handle as the user drags
- Labels are always visible when sufficient viewport space exists (see §41.6 for edge behavior)
- Labels are informational only — they are not interactive, not tappable, and must not introduce any new gesture or interaction
- Tap on the compare viewport continues to toggle fullscreen (§11A); labels must not intercept or prevent this
- No animation, no auto-hide, no fade-out, no tap-to-reveal behavior is implemented

---

### 41.4 Date Label Logic

Label content follows a five-level priority chain. Each level is evaluated in order; the first matching level determines the displayed content.

All parsing uses the two canonical data sources defined in §41.5. No other data is consulted.

#### Level 1 — Different years

Condition: `reference.date` is present AND the year extracted from `reference.date` differs from the capture year derived from `capture.timestampMs`.

Display: year number on each side.

```
2008   ◀ ● ▶   2026
```

Year extraction from `reference.date`:

- `"2008"` → year 2008
- `"2008-06"` → year 2008
- `"2008-06-15"` → year 2008

Capture year is always derived from `capture.timestampMs`.

#### Level 2 — Same year, different months, month precision available

Condition: `reference.date` has month precision or better (format `"YYYY-MM"` or `"YYYY-MM-DD"`) AND extracted reference year equals capture year AND extracted reference month differs from capture month derived from `capture.timestampMs`.

Display: abbreviated month + year on each side, locale-formatted.

```
Mar 2026   ◀ ● ▶   Oct 2026
```

#### Level 3 — Same year, same month, day precision available

Condition: `reference.date` has full date precision (format `"YYYY-MM-DD"`) AND extracted reference year equals capture year AND extracted reference month equals capture month.

Display: day number + abbreviated month on each side, locale-formatted (year omitted; it is shared context and would add no information). When reference day equals capture day, both labels show the same value — this is intentional and informs the user that both photos are from the same calendar day.

```
12 Jun   ◀ ● ▶   28 Jun
```

Same-day example:

```
10 Jun   ◀ ● ▶   10 Jun
```

#### Level 4 — Indistinguishable dates at available precision

Condition: `reference.date` is present, but no level above (1–3) would produce different labels on the two sides. This covers:

- Year-only precision (`"YYYY"`) and reference year equals capture year
- Month precision (`"YYYY-MM"`) and reference year equals capture year and reference month equals capture month

Display: semantic temporal labels.

```
Past   ◀ ● ▶   Present
```

Left label: `Past` (string resource `compare_label_past`)

Right label: `Present` (string resource `compare_label_present`)

#### Level 5 — No reference date available

Condition: `reference.date` is absent from `metadata.json`.

Display: role labels.

```
Reference   ◀ ● ▶   Current
```

Left label: `Reference` (string resource `compare_label_reference`, existing)

Right label: `Current` (string resource `compare_label_current`)

---

### 41.5 Data Sources

The label logic uses exactly these two data sources:

| Source | Field | Availability |
| --- | --- | --- |
| `metadata.json` | `reference.date` | Optional; may be absent; ISO 8601 string at year, month, or day precision |
| `metadata.json` | `capture.timestampMs` | Always present for v4 sessions; fallback to `session.createdAtMs` for v2/v3 sessions |

No EXIF re-reads are performed at compare time. No live data beyond what is stored in `metadata.json` is used. No new metadata fields are required for this feature.

Precision of `reference.date` is determined by string length:

- Length 4 (`"YYYY"`) → year precision
- Length 7 (`"YYYY-MM"`) → month precision
- Length 10 (`"YYYY-MM-DD"`) → day precision

---

### 41.6 Edge Behavior — Labels Near Viewport Boundary

Labels must never be clipped, truncated, ellipsized, or rendered even partially outside the compare viewport.

Each label is evaluated independently.

Left label visibility rule: the left label is shown only when it can be rendered fully within the compare viewport. It is hidden when the handle position is too close to the left viewport edge for the full left label text to fit without clipping.

Right label visibility rule: the right label is shown only when it can be rendered fully within the compare viewport. It is hidden when the handle position is too close to the right viewport edge for the full right label text to fit without clipping.

Asymmetric hide behavior: when one label must be hidden, the other label remains visible if it fits. One visible label is always more informative than zero.

Handle always visible: the handle must remain always visible regardless of label visibility state, including at the extreme left and right slider positions.

No partial rendering: a label is either fully visible or fully hidden. There is no partial display, no fade at boundaries, and no ellipsis.

Behavior at extreme positions (examples):

Slider near right edge — right label hidden:

```
2008   ◀ ● ▶
```

Slider near left edge — left label hidden:

```
◀ ● ▶   2026
```

Slider at center — both labels visible:

```
2008   ◀ ● ▶   2026
```

Implementation note (non-normative): determining whether a label fits requires measuring the rendered text width. The implementation must measure each label's pixel width (using the same text style and locale that will be used for rendering) and compare it against the available space on each side of the handle. Text width measurement must account for the actual locale-formatted label string, since month names vary in length across locales.

---

### 41.7 Fullscreen Mode

In fullscreen mode:

- Labels remain visible (same rule as the slider and divider — they are unchanged in fullscreen per §11A)
- Labels do not hide in fullscreen
- Edge behavior (§41.6) applies identically in fullscreen
- The contrast aid (text shadow or equivalent) is especially important in fullscreen, where `ContentScale.Crop` may place high-contrast image content beneath the labels

Labels are part of the slider mechanism, not part of the session metadata display (timestamp, title) that is hidden in fullscreen. The distinction: session metadata provides context about the session; slider labels provide orientation for the comparison mechanic itself. These are different concerns at different hierarchy levels.

---

### 41.8 Landscape Mode

In landscape mode, the compare viewport is wider than in portrait. Label and edge-behavior rules apply identically in both orientations. No special landscape-specific label logic is required.

The label visibility check operates on actual viewport pixel bounds, which correctly reflect the landscape dimensions.

---

### 41.9 Accessibility

- The slider handle's semantic node must include both label values and communicate the drag interaction, for example: "Compare slider: [left label] on the left, [right label] on the right. Drag to adjust split position."
- When a label is hidden due to edge behavior, its value must still be included in the semantic description of the handle
- All formatted date values in accessibility descriptions must be locale-aware
- All fixed strings in accessibility descriptions must use string resources

---

### 41.10 i18n Requirements

The following string resources are required for this feature in addition to the existing resources defined in §17:

| Key | Usage |
| --- | --- |
| `compare_label_past` | Level 4 left label |
| `compare_label_present` | Level 4 right label |
| `compare_label_current` | Level 5 right label |

`compare_label_reference` is already required by §17 and is reused as the Level 5 left label.

All date formatting (years, month+year, day+month) must use Android locale-aware date formatters. No hardcoded date format strings are permitted. The formatted date strings are data values, not UI strings, and must not be stored in `strings.xml`.

All existing i18n rules from §16 apply to this feature.

---

### 41.11 Implementation Prerequisites

Before this feature can be implemented, the following is required at the data layer:

- `ScannedSession` (used by the Compare Library flow) must expose `reference.date` as an optional field
- The Camera Flow path to `CompareScreen` must pass `reference.date` alongside the existing session context parameters

These are data-layer prerequisites, not UI prerequisites. The spec for those changes will be defined as part of the implementation planning for this feature.

---

### 41.12 Explicit Non-Goals

The following are explicitly out of scope for this feature and must not be implemented:

- Animation or auto-hide of labels
- Tap-to-show or tap-to-hide labels
- User setting to toggle labels on or off
- Displaying `reference.dateSource` or `reference.userEdited` in the label
- Resetting or modifying `reference.date` from `CompareScreen`
- Any new interaction or gesture added to the slider
- Displaying full date with time (day + month + year + time)
- Displaying GPS location or place name in the slider area
- Retaining image-overlay badges alongside the new labels (badges are fully removed)
- Label content influencing compare rendering, `ContentScale`, or divider position
- Adding `additional.originalDate` or any new metadata field for this feature
- Label color customization or theming beyond the standard contrast aid

---

## 42. COMPARE SCREEN METADATA HEADER (2026-06-18)

`CompareScreen` displays session metadata above the compare slider. In **portrait mode**, a dedicated `CompareMetadataHeader` component renders between the top app bar and the slider viewport. In **landscape mode**, metadata is integrated inline into the TopAppBar center slot; no separate header component is rendered below the TopAppBar. This replaced the previous footer (timestamp + title below the viewport).

### Metadata header purpose

The metadata header establishes session identity before the user engages with the comparison. Reading flow: "What is this? Where? → then compare." This purpose applies in both portrait (separate header) and landscape (TopAppBar center slot).

### Portrait layout

Two rows maximum, followed by the slider:

| Content available | Row 1 | Row 2 |
| --- | --- | --- |
| Title + Location | `content.title` | `📍 displayName · city, country` |
| Title only | `content.title` | — |
| Location only | `📍 displayName · city, country` | — |
| Neither | `Created <date>` | — |
| Neither + no timestamp | (empty, no header) | — |

Title: `maxLines = 2`, `TextOverflow.Ellipsis`.
Location line: smart reduction — tries `displayName · city, country` → `displayName · city` → `displayName` (then city/country fallback) until the string fits the available width without mid-word ellipsis.
Fallback date format: `DateFormat.getDateInstance(MEDIUM)` (date only, no time). String resource `compare_screen_metadata_created` = `"Created %s"`.

### Landscape layout

Session metadata is rendered inline in the TopAppBar center slot. No separate `CompareMetadataHeader` component is rendered below the TopAppBar in landscape mode. The reclaimed vertical space is returned to the compare viewport (~48 dp on a standard phone in landscape).

The TopAppBar center slot follows this priority:

| Content available | TopAppBar center slot |
| --- | --- |
| Title + Location | Line 1: `content.title` (`bodyLarge`, `maxLines = 1`, `Ellipsis`) |
| | Line 2: `📍 displayName · city, country` (`bodySmall`, smart-reduction) |
| Title only | Line 1: `content.title` (`bodyLarge`, `maxLines = 1`, `Ellipsis`) |
| Location only | Line 1: `📍 displayName · city, country` (`bodySmall`, smart-reduction) |
| Neither, timestamp present | Line 1: `"Compare"` (`titleLarge`, screen title, primary) |
| | Line 2: `Created <date>` (`bodySmall`, `onSurfaceVariant`, secondary) |
| Neither, no timestamp | `"Compare"` (`titleLarge`) only |

Both title and location are shown simultaneously when both are present. Location smart-reduction applies identically to portrait. The testTags `compare_screen_metadata_title`, `compare_screen_metadata_location`, and `compare_screen_metadata_fallback` remain on the respective inline elements.

**Compare viewport bottom padding:** In landscape normal mode the compare viewport has 8 dp bottom padding, preventing it from touching the screen edge. In fullscreen this padding is 0 dp.

**Portrait sessions in landscape — accepted geometry:** When the reference image has a portrait aspect ratio and the device is in landscape orientation, `ContentScale.Fit` produces a narrow vertical viewport within the wider landscape area. The remaining horizontal space appears as the app background color. This geometry is intentionally accepted. No zoom, crop, or alternative compare mode is introduced to compensate. Any future change requires an explicit product decision.

### Fullscreen

The portrait `CompareMetadataHeader` and the landscape TopAppBar metadata are both hidden in fullscreen, identically to the top app bar — implemented as `if (!isFullscreen)` gating at the call site. The landscape 8 dp bottom padding on the compare viewport is also disabled in fullscreen (`padding(bottom = if (isFullscreen) 0.dp else 8.dp)`), so the viewport uses the maximum available screen space without padding.

### Data sources

`location.displayName`, `location.city`, `location.country` are read from `metadata.json` via `SessionScanner` (added to `ScannedSession`) and passed to `CompareScreen` via `MainActivity`, following the same pattern as `sessionTitle`.

`CompareInput`, `SavedSessionRef`, and `SessionStorage` are unchanged — location fields are user-authored metadata, never set at capture time.

### What is NOT shown

- `reference.date` and the date pair (Reference ↔ Capture) — already present in the slider handle labels (§41)
- Capture time (only date in fallback, no time component)
- `content.description`
- Session ID

---

## 43. COMPARESCREEN TOPAPPBAR — EXPORT ICON RESTRUCTURING (2026-06-21)

This section documents the product decision to replace the dedicated Create Video icon in the
`CompareScreen` top app bar with a new **Export** icon that opens a dropdown menu.

Full specification: `SHARE_COMPARISON_IMAGE_V1.md`

### 43.1 Motivation

The introduction of the Share Comparison Image feature adds a second export action to
`CompareScreen`. Two dedicated icons for export actions would overcrowd the top app bar. A single
Export icon with a dropdown menu groups all export actions cleanly and leaves room for the
Favourite star, Delete Session icon, and overflow menu (⋮).

### 43.2 New TopAppBar Structure

**Previous structure (implemented as of Block 3+4 of `VIDEO_EXPORT_V1.md`):**

```text
← Back  |  [Favourite]  |  [Create Video]  |  [Delete Session]  |  ⋮
```

**New structure (implemented as part of `SHARE_COMPARISON_IMAGE_V1.md` scope):**

```text
← Back  |  [Favourite]  |  [Export]  |  [Delete Session]  |  ⋮
```

### 43.3 Export Dropdown Contents

Tapping the Export icon opens a `DropdownMenu`:

```text
─────────────────────────────────
  Share image   → ShareComparisonScreen
  Share video   → CreateVideoScreen (unchanged behavior)
─────────────────────────────────
```

The dropdown uses the same `DropdownMenu` / `DropdownMenuItem` pattern as the existing overflow
menu (⋮). No new menu component is introduced.

### 43.4 Overflow Menu (⋮) Unchanged

The overflow menu continues to contain:

- Edit Session
- Backup Session

### 43.5 Availability

The Export icon is shown only when `sessionId != null`. Each dropdown item has its own
availability rule (see `SHARE_COMPARISON_IMAGE_V1.md §6.3`).

### 43.6 No Impact on Compare Mechanics

This restructuring does not change:

- Compare rendering, slider behavior, or image display
- Session storage, session scanning, or session deletion logic
- `compareInput` lifecycle or `savedSessions` state
- Navigation contracts from `CameraScreen` or from Library to `CompareScreen`
- Fullscreen behavior or metadata header behavior
