# SETTINGS_UX_V1

This document defines the intended settings architecture and UX philosophy for SameView.

This specification defines:
- settings placement
- settings categories
- implemented settings
- settings behavior
- persistence rules
- future extensibility boundaries
- explicit non-goals

This document complements:
- CAMERA_WORKFLOW_UX_V1
- COMPARE_SESSION_RENDERING_V1

This document does NOT redefine:
- compare rendering
- session architecture
- navigation semantics
- bottom workflow semantics

Those remain defined by their dedicated specifications.

---

# 1. Core Philosophy

SameView is intentionally:
- camera-focused
- workflow-focused
- visually calm
- low-noise
- simple to understand

Settings must support the workflow.

Settings must NOT:
- transform the app into a power-user camera tool
- overload the user with technical toggles
- destabilize workflow consistency
- introduce hidden workflow complexity

The app should remain understandable without entering Settings at all.

---

# 2. Settings Placement

Settings are APP-LEVEL functionality.

Settings are NOT part of the capture workflow.

Therefore:
- Settings do NOT belong in the bottom bar
- Settings do NOT replace Overlay
- Settings do NOT replace Compare
- Settings do NOT appear as floating controls
- Settings do NOT appear inside CompareScreen

---

# 3. Settings Entry Point

Settings are opened from the CameraScreen top-right overflow menu.

Top-right order:

1. History
2. Overflow

Overflow currently contains:
- Settings
- About

Additional future entries MAY include:
- Help
- Feedback

The overflow menu background color should visually match the bottom workflow controls for UI consistency.

---

# 4. Settings Philosophy

Settings should:
- improve usability
- support repeatability
- reduce friction
- support personal preference

Settings should NOT:
- expose internal rendering implementation
- expose debugging concepts
- expose temporary developer toggles
- expose architectural details
- expose unstable experimental systems

---

# 5. Current Settings Categories

The current intended Settings structure is:

1. Camera
2. Overlay & Compare
3. Appearance
4. Future GPS Guidance (reserved)

No additional categories are currently planned.

Current implemented settings:
- Keep Screen Awake
- Grid Type
- Reset Overlay After Capture
- Auto-Open Compare After Capture

Reserved but not yet implemented settings/features:
- Hide Reference Peek Hint
- Theme selection beyond the current dark theme
- Future GPS Guidance

---

# 6. Camera Settings

## 6.1 Keep Screen Awake

Status:
Implemented.

Purpose:
Prevent the display from dimming or turning off while using CameraScreen.

Default:
ON

Behavior:
- active whenever CameraScreen is visible
- cleared automatically when CameraScreen leaves composition
- independent from reference image state
- no WAKE_LOCK permission is required

Rationale:
The app is tripod/alignment-oriented and should not interrupt long framing workflows.

Users may disable this manually.

---

## 6.2 Front Camera Support

Status:
Planned.

Not yet implemented.

This feature is considered valid for:
- selfie recreations
- mirror recreations
- social content
- travel shots

Expected UX:
- quick camera switch directly on CameraScreen
- NOT buried inside Settings once implemented

Settings may later contain:
- remember last used camera

But camera switching itself belongs to workflow UI.

---

## 6.3 Grid Type

Status:
Implemented.

Purpose:
Optional framing assistance.

Supported values:

- Off
- Rule of Thirds
- Quarters

This is intentionally NOT a generic extensible grid framework.

The implementation should remain simple.

Potential future additions MAY include:
- Golden Ratio

No arbitrary/custom grid system is planned.

Default:
Rule of Thirds

---

# 7. Overlay & Compare Settings

## 7.1 Reset Overlay After Capture

Status:
Implemented.

Purpose:
Automatically reset overlay scale/offset after a successful capture.

Default:
OFF

When enabled:
- reference image is removed after successful capture (URI and metadata cleared)
- overlay transform is reset (offset and scale)
- display mode is reset to default
- opacity remains unchanged
- compare input remains set from the capture session

This setting exists for users repeatedly recreating many locations quickly.

---

## 7.2 Auto-Open Compare After Capture

Status:
Implemented.

Purpose:
Automatically navigate to CompareScreen immediately after successful capture.

Default:
OFF

Reason:
Many users may prefer taking multiple attempts before opening Compare.

This setting changes workflow pacing significantly and therefore must remain optional.

Behavior:
- only triggers after successful capture with a valid compare session
- does not trigger without a reference image
- does not trigger on capture error or capture interrupt
- does not replay after rotation or recomposition

---

## 7.3 Hide Reference Peek Hint

Status:
Reserved. Not implemented.

Purpose:
Allow advanced users to suppress onboarding-style helper hints.

Default:
OFF

This setting only affects:
- informational helper hints
- discoverability hints

This setting must NOT:
- disable critical warnings
- disable overlay coverage warnings
- disable error states

---

# 8. Appearance Settings

SameView intentionally uses a dark visual design focused on:
- scene visibility
- overlay visibility
- distraction reduction
- nighttime usability

The app is intentionally not optimized around bright/light UI usage.

---

## 8.1 Theme

Status:
Prepared/reserved. No user-selectable theme setting is implemented yet.

Current supported theme:
- Dark

Future planned:
- Light Theme

Light Theme is considered lower priority and optional.

The dark theme remains the primary intended experience.

---

## 8.2 AMOLED Mode

Not currently planned.

Pure-black UI optimization is intentionally avoided because:
- extremely black UI reduces visual separation
- app surface hierarchy becomes harder to understand
- overlay edges become less readable

The current dark surface design remains preferred.

---

## 8.3 App Language

The app follows the system language.

An in-app language selector is NOT currently planned.

Reason:
- reduces complexity
- avoids duplicated platform behavior
- aligns with modern Android app expectations

---

# 9. About Screen

About is intentionally separated from Settings.

Reason:
Settings represent configuration.

About represents:
- app information
- version information
- credits
- licenses
- privacy information
- future acknowledgements

About should remain lightweight.

---

# 10. Future GPS Guidance

GPS Guidance is considered an important future feature.

However:
- it is NOT part of the current implementation scope
- it is NOT part of current Settings behavior
- it is reserved for future work

---

## Planned GPS Guidance Goals

Future GPS Guidance may support:

- distance comparison to original location
- directional guidance
- alignment assistance
- color-coded proximity feedback
- location-aware recreation workflows

Possible future indicators:
- green = close
- orange = moderate deviation
- red = far away

Potential future capabilities:
- directional arrow guidance
- compass integration
- recreation alignment assistance

---

## GPS Metadata Rules

Future GPS workflows should:
- reuse EXIF metadata when available
- avoid unnecessary cloud/location storage
- remain privacy-conscious
- remain fully optional

GPS functionality must NEVER:
- become mandatory
- block capture
- require account systems
- require cloud sync

---

# 11. Persistence Rules

All implemented settings persist locally on-device via DataStore Preferences.

Settings persistence must:
- survive app restarts
- remain offline-only
- avoid cloud dependencies

No settings sync system is currently planned.

---

# 12. Explicit Non-Goals

The following are intentionally NOT planned:

- professional DSLR-style settings explosion
- shutter speed controls
- ISO controls
- RAW workflows
- export quality tuning
- automatic cloud sync
- automatic gallery opening after capture
- workflow-disrupting dialogs
- advanced overlay debugging controls
- arbitrary/custom grid frameworks
- bottom-bar settings actions
- settings-driven workflow identity changes

---

# 13. UX Stability Requirement

Settings must NEVER destabilize the core workflow defined in CAMERA_WORKFLOW_UX_V1.

The following must remain stable regardless of Settings state:

Bottom bar:
- Overlay
- Capture
- Compare

Top-right:
- History
- Overflow

The app must remain:
- predictable
- calm
- camera-first
- comparison-first

even as future settings are added.

---

# 14. Long-Term Intent

The long-term goal is:
- a focused recreation camera
- with minimal friction
- minimal cognitive load
- strong compare consistency
- stable workflow semantics
- optional power features without UI chaos

Settings should support this goal — not compete with it.