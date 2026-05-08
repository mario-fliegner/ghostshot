# CAMERA_WORKFLOW_UX_V1

This document is the source of truth for CameraScreen UX decisions.
Future UI changes should follow this specification unless explicitly superseded.

## Status

Current UX specification for the GhostShot main camera workflow and navigation structure.

Last updated: 2026-05-08.

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

GhostShot is primarily:
- a recreation camera
- a comparison workflow
- a then/now capture experience

GhostShot is NOT primarily:
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
- Overlay

Center:
- Capture

Right:
- Compare

This structure should remain persistent.

---

# 4. Overlay Button Semantics

The Overlay button is a TOOL button.

It represents reference image management and related actions.

## Overlay button behavior

Tap:
- opens overlay menu

The overlay menu may contain:
- choose image
- replace image
- remove image
- reset transform
- compare display mode actions

This menu behavior is intentional and accepted.

## Important

The Overlay button is NOT required to behave identically to the Compare button.

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

## Compare states

The Compare button remains visible in all states.

### No reference image
Compare may appear visually disabled.

Tap feedback:
- short snackbar/hint:
  "Add a reference first"

### Reference image exists but no compare capture exists yet
Compare may appear visually disabled.

Tap feedback:
- short snackbar/hint:
  "Take a photo first"

### Compare available
Tap:
- opens latest compare session

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
- Settings do NOT belong in Overlay menu

## Placement

Top-right area of CameraScreen.

Final UI:
- Overflow icon placed at the far right
- History icon placed directly left of Overflow

Current prepared overflow entries:
- Settings
- About

Current behavior:
- Settings/About entries may exist before their final destination screens
- tapping placeholder entries must close the menu cleanly
- no fake route and no crash are allowed

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

The Compare button itself represents the persistent workflow continuation.

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
- Overlay
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

# 12. Intent

The long-term goal is a professional, calm and highly understandable camera workflow with:
- stable interaction patterns
- predictable navigation
- minimal cognitive load
- fast compare access
- clean separation between workflow and management/navigation
