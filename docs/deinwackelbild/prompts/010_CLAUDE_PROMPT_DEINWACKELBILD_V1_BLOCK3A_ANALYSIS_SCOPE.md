# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 3A: ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Blocks 1 and 2 are complete and committed.

This prompt covers **Implementation Block 3 only** and follows the SameView strict workflow:

1. analysis only;
2. scope confirmation;
3. STOP and wait for explicit approval;
4. no implementation yet.

Do not write code.
Do not modify files.
Do not commit.
Do not begin Block 4.

---

# 1. Block 3 Objective

Add the local Wackelbild image-switch interaction only:

- use device tilt when a suitable sensor is available;
- always allow horizontal swipe as a fallback/alternative;
- switch directly between Reference and Capture;
- no fade;
- no animation;
- no sound;
- no haptics;
- Reference initially visible;
- sensor and swipe must not fight each other;
- sensor lifecycle must be tied to the Wackelbild screen;
- no runtime permission.

Block 3 does **not** add date overlay, HQ rendering, network, temp files, API, Custom Tabs, or ordering behavior.

The intended user-visible behavior after this block is:

`Reference visible → tilt/swipe → Capture → tilt/swipe → Reference`

---

# 2. Authoritative Inputs

Read fully before analysis:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Also inspect relevant authoritative docs:

- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect the current implementation of:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CompassProvider.kt`
- current camera/GPS lifecycle code that activates/deactivates the compass provider
- current pointer/drag gesture patterns in `CompareScreen.kt`
- current Wackelbild instrumentation tests
- current unit-test dependencies/mocking patterns.

If repository state differs materially from the approved plan, report it and stop instead of silently changing architecture.

---

# 3. Repository Baseline

Before analysis, report:

- current branch;
- current HEAD;
- `git status --short`;
- any pre-existing modified/untracked files.

Do not touch unrelated working-tree state.

---

# 4. Product Constraints That Apply Now

Preserve exactly:

- initial image = Reference;
- direct A/B switch;
- no crossfade;
- no animation;
- no partial transition;
- no slider reveal;
- no sound;
- no haptics;
- tilt is left/right relative to current display orientation;
- neutral position is relative to device posture when sensor interaction is initialized;
- normal hand jitter must not cause repeated switching;
- exact tilt constants are not product decisions and must remain real-device tuning values;
- horizontal swipe toggles regardless of direction;
- image does not track finger;
- no drag progress;
- vertical scrolling must not trigger image switching;
- swipe must remain available even when sensor exists;
- after a manual swipe, an unchanged sensor reading must not immediately undo it;
- sensor control resumes only after a genuinely new tilt movement;
- if no suitable sensor exists, the feature remains fully usable via swipe;
- no visible sensor error state;
- no sensor data is persisted, logged, or transmitted;
- no runtime permission.

Do not alter these decisions.

---

# 5. Required Repository Analysis

## 5.1 Current Wackelbild screen structure

Confirm exactly:

- how `reference.jpg` is currently rendered;
- whether `capture.jpg` is already resolved anywhere;
- where the preview Box/Image lives;
- whether the preview is outside or inside the scrollable content;
- what state currently lives in `WackelbildViewModel`;
- how Block 2 handles image-load errors.

Determine the smallest change needed to add a two-image preview without disturbing Block 2's fallback behavior.

## 5.2 Capture image source

Inspect current session-file contract and confirm the correct persisted Capture preview source is:

`capture.jpg`

Plan how Block 3 should resolve it.

Do not use originals/HQ files.

Decide whether the local fallback should trigger if:

- reference exists but capture is missing;
- capture exists but fails to decode.

The product intent after Block 3 is a two-image Wackelbild preview, so analyze whether both files must be valid for the interaction screen to be functional.

Do not invent a new repair flow.

## 5.3 Tilt sensor API

Re-inspect `CompassProvider.kt`.

Determine whether the approved `Sensor.TYPE_ROTATION_VECTOR` plan remains correct against current code.

Confirm:

- exact display-rotation remap logic to mirror;
- exact orientation array index for roll;
- no permission needed;
- provider constructor/testability pattern;
- start/stop lifecycle pattern;
- sensor-unavailable behavior.

The plan should prefer a new isolated `TiltProvider` and leave `CompassProvider` unchanged unless repository evidence now contradicts that decision.

## 5.4 Neutral position

Plan exactly:

- when the neutral roll is captured;
- when it resets;
- what happens after screen rotation;
- what happens after pause/resume;
- whether returning from background recalibrates neutral;
- whether Custom Tab lifecycle matters yet (it does not exist in Block 3, so do not design it now).

The behavior must remain intuitive and relative to the current device posture.

## 5.5 Hysteresis state machine

Plan a small pure state machine.

Clarify:

- states;
- threshold-crossing semantics;
- re-arm semantics;
- angle wrap handling;
- how image-switch events are emitted;
- how placeholder constants are represented;
- what tests are required.

Do not finalize production tuning constants as if they were validated.

If placeholder constants are needed for implementation/testability, mark them explicitly for later real-device tuning.

## 5.6 Swipe behavior

Inspect current Compose pointer-input patterns.

Plan the smallest stable horizontal swipe detector.

Requirements:

- one deliberate horizontal swipe toggles once;
- direction does not matter;
- no continuous drag progress;
- no image following the finger;
- vertical movement should not trigger a toggle;
- no haptic/sound;
- no animation.

Do not introduce a slider or draggable state.

## 5.7 Swipe vs. scroll

Block 2's current screen layout is now the source of truth.

Verify whether the preview is structurally outside the vertically scrollable content.

If yes:

- confirm that this avoids scroll/gesture contention;
- explain any remaining need for axis discrimination inside the preview itself.

If no:

- identify the smallest layout adjustment necessary.

Do not redesign the entire screen.

## 5.8 Swipe vs. sensor arbitration

The approved behavior requires:

- swipe immediately changes the visible image;
- unchanged current tilt must not immediately revert it;
- sensor resumes only after a genuinely new tilt movement.

Plan the exact minimal state required.

Avoid time-based hacks if a state-transition-based solution is sufficient.

Clarify:

- what state is captured at swipe time;
- what sensor transition clears swipe override;
- whether the hysteresis machine keeps observing while override is active.

## 5.9 Lifecycle

Inspect the existing screen lifecycle code and current Compass lifecycle precedent.

Plan:

- when sensor updates start;
- when they stop;
- cleanup on leaving screen;
- behavior during app backgrounding;
- behavior on configuration change/rotation;
- whether neutral is recalibrated after resume/rotation.

No WorkManager/background service.

No persistent state.

## 5.10 Accessibility

The user must not be forced to tilt.

Analyze the smallest accessibility semantics for the preview.

Potential behavior:

- current visible image is announced;
- an accessibility action toggles the image;
- swipe remains available.

Do not add a visible accessibility mode or setting.

Do not over-design this block.

---

# 6. Strict Block 3 Scope

Likely production scope should be limited to the existing Wackelbild package plus new local interaction files.

Expected candidates:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- new `WackelbildPreview.kt` if the current screen should be split minimally
- new `TiltProvider.kt`
- new `TiltHysteresisStateMachine.kt`
- string resources for interaction hints only if the approved plan schedules them now
- Wackelbild tests
- `docs/IMPLEMENTATION_NOTES.md`

Do not assume this list blindly.

Confirm exact files from repository evidence.

---

# 7. Forbidden in Block 3

Do not include:

- date toggle;
- date formatting;
- date badge;
- HQ renderer;
- `ShareImageRenderer` changes;
- dimension resolver;
- temp-file manager;
- OkHttp;
- network DTOs;
- API state machine;
- API key;
- Gradle changes;
- AndroidManifest changes;
- `INTERNET`;
- `androidx.browser`;
- Custom Tabs;
- ordering CTA;
- transfer disclosure;
- upload/loading/error state machine;
- fallback-quality warning;
- release/privacy work;
- unrelated refactors.

If any forbidden item appears necessary, stop and explain why instead of expanding scope.

---

# 8. Missing/Corrupt Capture Behavior

This needs an explicit Block-3 answer.

Block 2 already has a local fallback when `reference.jpg` is missing/unreadable.

Now Block 3 requires both `reference.jpg` and `capture.jpg`.

Analyze and decide the smallest consistent behavior if `capture.jpg` is:

- missing;
- present but undecodable.

Preferred principle:

- one unified local preview fallback for any broken required preview asset;
- no attempt to degrade into a one-image Wackelbild preview;
- no repair flow.

But confirm against current SameView patterns/spec before final scope.

---

# 9. Regression-Safety Review

Explicitly assess risk to:

- Block 2 navigation;
- Block 2 local fallback;
- Reference preview sizing;
- screen layout;
- app lifecycle;
- sensor registration/unregistration;
- configuration changes;
- accessibility;
- existing tests.

Block 3 should not touch CompareScreen/MainActivity unless analysis proves a real necessity.

---

# 10. STEP 2 — Required Scope Confirmation Output

Return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- working-tree state

## 2. Current Implementation Evidence

List:

- Wackelbild screen structure;
- current ViewModel state;
- current reference/capture file handling;
- CompassProvider precedent;
- current lifecycle precedent;
- current gesture precedent;
- current test files.

Use repository-relative paths and line ranges where practical.

## 3. Root Cause / Required Change

Explain why Block 3 requires the identified changes and why nothing broader is needed.

## 4. Files Proposed for Modification

Table:

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

List **ALL** files intended for Block 3 implementation.

No hidden files.

## 5. Files Explicitly NOT Touched

Confirm at minimum:

- `MainActivity.kt`
- `CompareScreen.kt`
- image/HQ renderer files
- Gradle
- AndroidManifest
- network/API files
- date-overlay files
- DeinWackelbild spec/plan unless a real conflict is found.

## 6. Exact Implementation Plan for Block 3

Describe precise edits, but **do not provide code**.

Include:

- capture file resolution;
- two-image preview state;
- sensor provider;
- neutral position;
- rotation handling;
- hysteresis;
- swipe gesture;
- swipe/sensor arbitration;
- sensor-unavailable UI hint;
- accessibility action;
- lifecycle start/stop;
- fallback for missing/corrupt capture;
- tests;
- documentation update.

## 7. Risks

List only real Block-3 risks and mitigations.

## 8. Verification Planned After Implementation

State exact commands/tests to run after approval.

At minimum assess:

- `./gradlew testDebugUnitTest`
- relevant Wackelbild instrumentation tests
- relevant Managed Device task
- `./gradlew assembleDebug`

State clearly:

- which aspects can be validated on Managed Device;
- which aspects require a real physical device because sensor feel/tuning cannot be validated meaningfully in CI.

Do not claim real-device tuning is complete in Block 3 unless it is actually performed later.

## 9. Scope Confirmation

End with exactly:

**BLOCK 3 SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

# Final Rule

This prompt is ANALYSIS + SCOPE CONFIRMATION only.

No code.
No file modifications.
No date.
No HQ.
No network.
No Block 4.
No unrelated cleanup.

Wait for explicit approval.
