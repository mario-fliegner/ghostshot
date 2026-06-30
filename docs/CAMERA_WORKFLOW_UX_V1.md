# CAMERA_WORKFLOW_UX_V1

This document is the source of truth for CameraScreen UX decisions.
Future UI changes should follow this specification unless explicitly superseded.

## Status

Current UX specification for the SameView main camera workflow and navigation structure.

Last updated: 2026-05-13.

This document defines the intended long-term UX semantics for:
- CameraScreen
- Bottom workflow actions
- Compare behavior
- Shots/History access
- Settings placement
- Overlay interaction rules

The goal is to keep the app:
- camera-first
- compare-first
- simple
- predictable
- visually calm

---

# 1. Product Philosophy

SameView is primarily:
- a recreation camera
- a comparison workflow
- a then/now capture experience

SameView is NOT primarily:
- a gallery app
- a media management app
- a library browser

The camera workflow is always the primary focus.

---

# 2. CameraScreen Layout Philosophy

The CameraScreen is split into:

## Bottom Area
Primary workflow actions.

## Top Area
Secondary navigation and app-level actions.

This separation must remain consistent.

---

# 3. Bottom Bar Rules

The bottom bar represents the active capture workflow only.

It must stay:
- stable
- predictable
- low-noise
- free from semantic shape-shifting

## Bottom Bar Structure

Left:
- Reference

Center:
- Capture

Right:
- Compare

This structure should remain persistent.

## Exception: Reference Markers Edit Mode

Reference Markers Edit Mode may temporarily replace the center Capture slot with the Marker Done action.

Requirements:
- Applies only while Marker Edit Mode is active
- Center slot returns to Capture immediately after exiting edit mode
- Reference (left) and Compare (right) remain unchanged during edit mode
- The Done action uses accent-filled pill styling to distinguish it from Capture and from Reference/Compare

This exception is narrowly scoped. No other feature or mode may replace the center Capture slot.

---

# 4. Reference Button Semantics

The Reference button is a TOOL button.

It represents reference image management and related overlay options.

## Reference button behavior

Tap:
- opens the reference menu

The reference menu may contain:
- choose image
- replace image
- remove image
- reset transform
- compare display mode actions

This menu behavior is intentional and accepted.

## Important

The Reference button is NOT required to behave identically to the Compare button.

Semantic consistency is more important than mechanical symmetry.

---

# 5. Compare Button Semantics

The Compare button is a WORKFLOW button.

It represents:
- opening the latest comparison
- continuing the active compare workflow

It must NOT:
- become a menu launcher
- change identity dynamically
- switch between Compare and Shots
- expose hidden long-press behavior
- show secondary overflow controls

## Compare button rules

The right bottom button should ALWAYS represent Compare.

Never:

- Compare -> Shots switching
- Compare -> Gallery switching
- transient semantic changes

## Active Compare Session

An active compare session exists when both conditions are true:

- a reference image is loaded
- AND a last successful shot exists

The active compare session persists across:

- returning from CompareScreen
- camera lifecycle transitions (pause, rebind, resume)

The active compare session is cleared only when:

- the reference image is removed or replaced
- a new shot is started (optimistic clear, reset if shot fails — see State Contract in COMPARE_FLOW_V1.md)
- the optional `Reset overlay after capture` setting is enabled and a capture completes successfully

The active compare session must NOT be cleared by:

- navigating to CompareScreen
- returning from CompareScreen
- camera preview pausing or resuming

## Reset overlay after capture interaction

When `Reset overlay after capture` is enabled, the app may clear the active reference image after a successful capture.

This means the CameraScreen returns to State A after the successful capture, while the just-created compare session remains stored internally and remains reachable through History.

This is intentional current product behavior.

The setting must not:
- delete the saved MediaStore photo
- delete the internal compare session
- remove the newly stored session from History
- reset overlay opacity
- trigger compare navigation by itself

If `Auto-open compare after capture` is also enabled, auto-open may still navigate to CompareScreen after a successful capture, provided valid compare session data exists before the reference reset is applied.

## Compare states

The Compare button remains visible in all states.

### State A — No reference image

Compare is disabled.

Tap feedback:

- short snackbar/hint: "Add a reference first"

### State B — Reference exists, no shot yet

Compare is disabled.

Tap feedback:

- short snackbar/hint: "Take a photo first"

### State C — Active compare session

Compare is enabled.

Tap: opens the active compare session.

This state persists after returning from CompareScreen unless a setting or explicit user action clears the active reference image.

Returning from CompareScreen must NOT reset Compare to State B.

## Compare-disabled snackbar timing

Compare-disabled snackbar hints should be short-lived:
- approximately 2000 ms
- shorter than normal error snackbars
- not used as workflow navigation

---

# 6. History Rules

History is a SECONDARY navigation area.

It is not part of the primary capture workflow.

Therefore:
- History must NOT replace Compare in the bottom bar
- History must NOT dynamically take over the Compare button
- History must NOT be hidden inside the Compare button

## Placement

History is accessible from the top-right area of CameraScreen.

Final UI:
- dedicated History icon
- positioned left of the Overflow icon
- always visible, even when no sessions exist

Portrait behavior:
- History and Overflow remain in the classic top-right area
- History stays left of Overflow
- actions remain horizontally aligned

Landscape behavior:
- History and Overflow use an adaptive side-rail navigation area
- actions move to the opposite side of the system navigation / navigation bar area
- actions are vertically aligned
- History remains above Overflow
- actions sit in the upper side-rail zone instead of geometric center alignment
- the Overflow popup opens toward the free side area:
  - left rail -> popup opens right
  - right rail -> popup opens left

Tap:
- opens the Compare Library / History screen directly

Empty history is valid:
- the History screen may show its empty state
- navigation should not be blocked by a snackbar or disabled icon

The goal:
- fast access
- persistent discoverability
- no workflow confusion

---

# 7. Settings Placement

Settings are app-level actions.

Settings are NOT workflow actions.

Therefore:
- Settings do NOT belong in the bottom bar
- Settings do NOT replace Compare
- Settings do NOT belong in the Reference menu

## Placement

Top-right area of CameraScreen.

Final UI:
- Overflow icon placed at the far right
- History icon placed directly left of Overflow

Current overflow entries:
- Settings
- About

Current behavior:
- Settings opens the implemented Settings screen
- About opens the implemented About screen
- tapping any non-routed placeholder entry must close the menu cleanly
- no fake route and no crash are allowed

When camera permission is blocked, CameraScreen shows a dedicated polished fallback state and updates the blocked state after returning from Android Settings.

Potential future entries:
- Help
- Feedback

---

# 8. Snackbar Philosophy

Snackbars should NOT become core workflow navigation.

Snackbars are:
- transient feedback
- temporary hints

They are NOT:
- persistent navigation
- workflow anchors
- primary Compare access

## Current intended behavior

### Capture without reference image

Snackbar allowed:
- "Photo saved"

### Capture with active reference image

No success snackbar required.

The Compare button itself represents the persistent workflow continuation when the reference remains active.

If `Reset overlay after capture` is enabled, the reference may be cleared after a successful capture. In that case, the Compare button may return to disabled/no-reference state, while the saved compare session remains reachable through History.

If the optional setting `Auto-open compare after capture` is enabled, the app may navigate directly to CompareScreen after a successful capture with valid compare session data. This is a user-selected workflow pacing option and must not change the stable bottom-bar semantics.

### Compare disabled hints

Short snackbar hints are allowed for disabled Compare taps:
- no reference image
- reference exists but no compare capture exists yet

These hints should be dismissed after approximately 2000 ms.
They are workflow hints, not error states and not navigation anchors.

---

# 9. UX Principles

The CameraScreen should remain:
- calm
- direct
- camera-focused
- low-noise

Avoid:
- shape-shifting buttons
- hidden long-press features
- duplicated navigation paths
- multiple competing primary actions
- context-dependent button identities

Prefer:
- stable button semantics
- persistent workflow actions
- direct interactions
- discoverable navigation
- clean visual hierarchy

---

# 10. Current Preferred UX Direction

## CameraScreen

Bottom:
- Reference
- Capture
- Compare

Top-right:
- History access
- Overflow access

Final order:
- History
- Overflow

## CompareScreen

May later contain:
- history access
- compare management
- session-related actions

Note (2026-06-01): Session backup export is implemented as a session-related action in the CompareScreen overflow menu. See `SESSION_BACKUP_EXPORT_V1.md`.

But Compare itself remains the primary workflow action from CameraScreen.

---

# 11. Explicit Non-Goals

The following are currently considered undesirable:

- Compare/Shots dynamic button switching
- hidden long-press compare actions
- bottom-bar settings entry
- multiple bottom-bar menus
- snackbar-driven compare workflow
- transient compare-only access
- context-dependent button identity changes

---

# 12. Top-Left Hint Zone

A single hint slot exists in the top-left area of CameraScreen, visible only when a reference image is active.

Only one hint can be shown at a time. The priority order is:

1. Overlay Coverage Warning — when `isOverlayNearlyInvisible` is true
2. Format Mismatch Hint — when `hasViewportMismatch` is true and the coverage warning is inactive
3. No hint

The hint zone must NOT:

- show two hints simultaneously
- use a row or multi-hint layout
- block capture
- auto-correct overlay state

## Overlay Coverage Warning

Shown when less than 20 % of the overlay is visible within the camera viewport.

This is a soft warning only.

Behavior:

- Capture is always still allowed
- No automatic correction occurs
- No auto-centering, auto-clamping, or auto-fill

The warning updates live in real time.

The warning is primarily relevant in SHOW_FULL_IMAGE mode or when the overlay has been pushed to an extreme offset.
In COMPARE_WITH_PREVIEW mode, the 20 % threshold cannot be reached in practice due to fillScale, clamped offsets, and MIN_SCALE 0.5.

## Format Mismatch Hint

Shown when the reference image has a strong aspect ratio mismatch with the current viewport, and the Overlay Coverage Warning is not active.

---

# 13. Settings Impact on Camera Workflow

Implemented settings must not destabilize CameraScreen workflow semantics.

Current workflow-affecting settings:
- Grid Type controls only the preview grid overlay
- Keep screen awake applies only while CameraScreen is visible and must be cleared when CameraScreen leaves composition
- Reset overlay after capture may automatically clear the active reference after a successful capture when enabled
- Reset overlay after capture clears:
  - reference image URI
  - reference metadata
  - display mode state
  - overlay transform state
  - overlay coverage warning state
  - user display-mode override state
- Reset overlay after capture preserves:
  - overlay opacity
  - saved MediaStore capture
  - newly created internal compare session
  - History access to the saved compare session
- Auto-open compare after capture may navigate to CompareScreen after successful capture only when a valid compare session exists

Settings must not:
- replace bottom-bar actions
- add bottom-bar entries
- turn Compare into a menu
- trigger compare navigation without a valid compare session
- replay navigation after rotation or recomposition
- delete internal compare sessions unless the user explicitly deletes them

CameraScreen internally ignores stale capture callbacks after rotation or navigation so old CameraX success/error callbacks do not create new compare, navigation, save, or snackbar side effects.

---

# 14. Intent

The long-term goal is a professional, calm and highly understandable camera workflow with:
- stable interaction patterns
- predictable navigation
- minimal cognitive load
- fast compare access
- clean separation between workflow and management/navigation

---

# 15. GPS Recreation Guidance

The GPS guidance chip is implemented. It is visible on CameraScreen when Recreation Guidance is enabled in Settings, the reference image contains GPS EXIF data, location permission is granted, and CameraScreen is in the foreground.

The full specification — including chip placement, bearing model, proximity color model, GPS states, and lifecycle rules — is defined in `GPS_RECREATION_SYSTEM_V1.md`.

The GPS guidance chip must not occupy or conflict with the Top-Left Hint Zone described in section 12. The chip is a separate, independent UI element positioned in the top area of CameraScreen outside the existing hint zone.
