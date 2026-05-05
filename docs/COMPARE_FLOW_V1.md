# COMPARE_FLOW_V1.md

## 1. DOCUMENT STATUS

This document is the **authoritative execution specification** for the V1 compare flow in the GhostShot / OverlayPast Android app.

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
4. User taps **Compare Images**
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

Returning MUST NOT:
- clear the reference image
- reset the camera screen unexpectedly
- cause a snackbar replay
- lose current session setup

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
- Right side = Capture image

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
- changing Variant B normalization or saved output behavior

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
- optional image labels such as "Reference" and "New" / "Capture"

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
- modifying bitmap processing logic
- modifying variant B image normalization logic
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

Accessible from the camera screen. A button labeled "Comparisons" appears when at least one saved session exists. After deletion, the library refreshes and either shows the remaining sessions or an empty-state message.

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

The captured photo in MediaStore (`Pictures/GhostShot/`) is never affected. No other files, preferences, or app data are touched.

### How delete is performed

`SessionDeleter.delete(sessionsRoot, sessionId)` validates that the resolved path is a direct child of `sessionsRoot`. Path traversal via `..` or absolute paths in `sessionId` is rejected. After deletion, `CameraViewModel.deleteSessions()` rescans to update `savedSessions`.

### Triggering delete

From `CompareScreen`: single session delete via confirmation dialog when `onDelete` is provided.
From `CompareLibraryScreen`: multi-session delete via multi-select and confirmation dialog.

---

## 34. IMPLEMENTATION STATUS (2026-04-27)

The following components are implemented and their tests are passing.

| Component | Status |
| --- | --- |
| `CompareScreen` | Implemented; instrumentation tests green |
| `CompareLibraryScreen` | Implemented |
| `SessionStorage` (returns `SavedSessionRef`) | Implemented |
| `SessionScanner` | Implemented |
| `SessionDeleter` | Implemented; unit tests green |
| `CompareInput` with `sessionId`/`timestamp` | Implemented |
| Camera Flow session context propagation | Implemented |
| Library Flow session context propagation | Implemented |
| Theme: `background`/`surface` override in light mode | Implemented |

Unit test count: 34 (all green as of last run).

---

## 35. CAMERA SCREEN LANDSCAPE CONTROL CONTEXT (2026-04-28)

This document remains focused on the compare flow.
This section only protects the camera-to-compare entry point and prevents layout regressions around the `Shots` / `Compare Images` control.

### Current camera-control decision

Landscape camera controls must mirror the portrait structure conceptually:

- Bottom row: `Overlay` / `Capture` / `Shots`
- Opacity slider: separate row above the bottom row

### Required landscape invariants

- Capture remains exactly centered at the bottom of the root
- Overlay remains left of capture
- Shots / Compare entry remains right of capture
- Overlay and Shots / Compare are symmetrically spaced around capture
- Slider is centered above capture
- Slider is above the button row
- Slider width is no greater than the button-group width
- Slider remains inside root bounds
- Overlay action menu remains visible, inside root bounds, and visually above the slider when overlapping

### Forbidden approaches

- Placing the opacity slider to the right of Shots / Compare
- Calculating slider width from remaining right-side space
- Reusing `safeEndPadding`, `rightControlsStart`, or equivalent right-control width logic for the slider
- Inline slider in the bottom button row
- Fallback alignments that move the slider to TopEnd or BottomStart
- Moving capture away from bottom center
- Squeezing Overlay or Shots / Compare with hard equal-width button constraints

### Compare-flow relevance

The `Shots` / `Compare Images` entry must remain reachable and visually stable in landscape.
The landscape layout fix must not change `CompareScreen`, Variant B normalization, session storage, delete behavior, or compare navigation contracts.

---

## 36. COMPARE SCREEN HYBRID FULLSCREEN STATUS (2026-04-29)

The compare screen now includes the V1 fullscreen viewing behavior described in section 11A.

Implemented behavior:
- Tap on the compare viewport toggles fullscreen
- Back exits fullscreen before leaving the compare screen
- Top bar and timestamp are hidden in fullscreen
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
- `testDebugUnitTest` green
- `:app:assembleDebug` green
- `CompareScreenTest` connected instrumentation tests green (26/26 on SM-S911B)


## CAMERA FEEDBACK CONSISTENCY (2026-05-05)

- Capture flash and haptic feedback exist only in CameraScreen
- Must not affect CompareScreen rendering
- Must not affect saved images
- Compare flow remains visually unaffected
