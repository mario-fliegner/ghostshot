# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 3B: IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

Block 3A analysis/scope was reviewed and approved with three explicit corrections:

1. Do **not** create a separate `WackelbildPreview.kt`.
2. Swipe override must only hand control back to the sensor after:
   - returning to the neutral/re-arm zone, and then
   - a **new** threshold crossing.
3. Android lifecycle observation stays entirely in the screen/composable layer. The ViewModel may own `TiltProvider`, but it must not depend on `LifecycleOwner`, `Lifecycle`, or lifecycle observer classes.

This prompt authorizes implementation of **Block 3 only**.

Do not begin Block 4.
Do not add date, HQ, network, manifest, Gradle, API, temp-file, ordering, or Custom Tab work.

Implement exactly the approved scope and nothing else.

---

# 1. Authoritative Inputs

Read before changing anything:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current code:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CompassProvider.kt`
- current camera/GPS lifecycle wiring in `CameraScreen.kt` / `CameraViewModel.kt`
- current drag/pointer patterns in `CompareScreen.kt`
- current `WackelbildScreenTest.kt`
- current test dependencies and `CompassProviderTest.kt`.

If repository state differs materially from the approved Block 3A analysis, STOP and report before editing.

---

# 2. Repository Baseline

Before modification, record:

- branch
- HEAD
- `git status --short`

Do not touch unrelated untracked prompt directories or any other unrelated state.

---

# 3. Exact Authorized File Scope

You may modify/create only these files:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltProvider.kt`
4. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt`
5. `app/src/main/res/values/strings.xml`
6. `app/src/main/res/values-de/strings.xml`
7. `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`
8. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltProviderTest.kt`
9. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachineTest.kt`
10. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt`
11. `docs/IMPLEMENTATION_NOTES.md`

Do **not** create `WackelbildPreview.kt`.

If any twelfth file becomes necessary, STOP and report why. Do not expand scope.

---

# 4. Capture File Resolution

In `WackelbildViewModel.kt`:

- keep existing `referenceFile`
- add:
  - `captureFile = File(context.filesDir, "sessions/$sessionId/capture.jpg")`

Do not use originals.
Do not use HQ files.
Do not parse metadata.

Both persisted preview files are required for a functional Block-3 Wackelbild preview.

If either file is missing or undecodable, the screen uses the same existing local fallback UI.

Do not degrade to a one-image mode.

---

# 5. Visible Image State

Add a small explicit enum/state:

- `REFERENCE`
- `CAPTURE`

Initial state:

- always `REFERENCE`

This state belongs in `WackelbildViewModel`.

Expose the minimal state needed by the screen.

Do not persist it in `SavedStateHandle`.

No date/order/network state is added.

---

# 6. TiltProvider

Create:

`TiltProvider.kt`

Requirements:

- use `Sensor.TYPE_ROTATION_VECTOR`
- no runtime permission
- model structure directly on existing `CompassProvider`
- leave `CompassProvider.kt` unchanged
- constructor pattern:
  - internal `SensorManager?` constructor for tests
  - public `Context` constructor for production
- `isAvailable()`
- `startUpdates(displayRotationProvider, onRollChanged)`
- `stopUpdates()`
- exception-safe behavior matching the existing provider style

Use the same display-rotation remap logic as `CompassProvider`.

After `SensorManager.getOrientation(...)`, read:

`orientationAngles[2]`

for roll.

Do not persist/log/transmit sensor data.

---

# 7. TiltHysteresisStateMachine

Create a pure JVM-testable state machine.

It must not depend on Android framework classes.

Use explicit states such as:

- `NEUTRAL`
- `TOWARD_REFERENCE`
- `TOWARD_CAPTURE`

Implement:

- threshold crossing
- neutral/re-arm band
- angle-wrap-safe delta calculation
- discrete transition output only
- no continuous animation/progress

Placeholder tuning constants may be used, but must be named and clearly marked as requiring real-device validation later.

Do not claim the threshold values are final.

---

# 8. Neutral Position Behavior

Neutral is owned by the ViewModel, not the provider.

Behavior:

- first sensor reading after `onScreenActive()` establishes neutral
- no image switch is emitted from that calibration reading
- on `onScreenInactive()` neutral is cleared
- next `onScreenActive()` recalibrates from the current physical device posture
- after device rotation / configuration change, the next active/calibration cycle uses the new posture/rotation
- no neutral value persists across process death or a new screen visit

This keeps interaction relative to the current device posture.

---

# 9. Swipe / Sensor Arbitration — REQUIRED EXACT RULE

This is a hard requirement.

After a manual swipe:

1. the visible image toggles immediately
2. `swipeOverrideActive = true`
3. the sensor/hysteresis state continues observing
4. sensor output is ignored while override is active
5. the override must **not** clear merely because the hysteresis state changes
6. the user must first return into the `NEUTRAL` / re-arm zone
7. only after that neutral return is observed may the sensor become eligible again
8. then a **new threshold crossing** is required before sensor control can change the visible image

In other words:

`SWIPE → neutral/re-arm observed → new threshold crossing → sensor control resumes`

The current tilt state at swipe time must never immediately undo the manual choice.

Avoid timers/timeouts.

Use deterministic state.

Add explicit ViewModel unit tests for this sequence.

---

# 10. Swipe Gesture

Implement on the preview region only, inside `WackelbildScreen.kt`.

Do not create another preview file.

Requirements:

- deliberate horizontal swipe toggles exactly once
- swipe direction does not matter
- no image drag progress
- no slider behavior
- no partial reveal
- no animation
- no haptic
- no sound
- clearly vertical gesture does not toggle
- ambiguous small movement does not toggle

Use the smallest stable Compose pointer-input pattern consistent with existing project code.

Do not eagerly consume every pointer event.

Consume only once the gesture is clearly classified as a valid horizontal swipe, where practical.

---

# 11. Screen Layout Change

Current Block-2 preview sits inside a vertically scrollable column.

For Block 3, restructure only as much as needed:

- preview region becomes structurally outside the vertical scroll container
- the lower interaction-hint/content area may scroll
- preview remains centered/moderate in size
- intrinsic aspect ratio behavior from Block 2 remains unchanged
- no new crop

Do not redesign the whole screen.

Do not create a new responsive-layout system.

---

# 12. Two-Image Preview Loading

Extend the existing Block-2 image-loading logic in `WackelbildScreen.kt`.

Requirements:

- both `reference.jpg` and `capture.jpg` must load successfully
- Reference is initially displayed
- displayed image switches based on ViewModel `visibleImage`
- use `ContentScale.Fit`
- derive layout/aspect ratio from the actual loaded image dimensions, preserving Block 2's no-metadata/no-hardcoded-ratio rule
- no crossfade/animation
- no labels on the image

If either image fails to decode:

- show the existing local fallback
- do not show the interaction UI
- no one-image degraded mode

Do not modify persisted files.

---

# 13. Sensor Availability Hint

Add only the interaction-hint strings needed for this block.

## German

- `Handy leicht neigen`
- `Sieh dir dein Wackelbild an.`
- sensor-unavailable fallback:
  - `Über das Bild wischen`

## English

Use the already-approved implementation-plan wording:

- `Tilt your phone`
- `See your lenticular print in action.`
- `Swipe over the image`

The screen chooses:

- tilt title when sensor is available
- swipe title when sensor is unavailable
- same supporting text in both cases

Do not add date/order/transfer strings.

---

# 14. Accessibility

Add a non-tilt accessibility action on the preview.

Requirements:

- current visible image is semantically distinguishable
- screen-reader user can trigger a toggle action
- action toggles Reference/Capture exactly once
- no additional visible control
- swipe remains available

Use localized content/action strings if needed.

Do not add an accessibility mode or setting.

---

# 15. Lifecycle Wiring — SCREEN OWNS OBSERVER

This correction is mandatory.

`WackelbildScreen.kt` / composable layer owns:

- `LifecycleOwner`
- `LifecycleEventObserver`
- `DisposableEffect`

On events:

- `ON_RESUME` → `viewModel.onScreenActive()`
- `ON_PAUSE` → `viewModel.onScreenInactive()`

On composable disposal:

- `viewModel.onScreenLeft()`

The ViewModel may own and start/stop `TiltProvider`, but it must **not**:

- import/use `Lifecycle`
- import/use `LifecycleOwner`
- import/use `LifecycleEventObserver`

Keep Android lifecycle observation out of ViewModel code.

---

# 16. ViewModel Responsibilities

`WackelbildViewModel` may own:

- `referenceFile`
- `captureFile`
- visible-image state
- sensor-availability state
- neutral roll
- hysteresis state machine
- swipe-override state
- `TiltProvider`
- `onScreenActive()`
- `onScreenInactive()`
- `onScreenLeft()`
- swipe/accessibility toggle handlers
- raw-roll handling

It must not own:

- Compose state
- lifecycle objects
- navigation
- date state
- HQ state
- network state
- upload state
- order state

---

# 17. Existing Block-2 Fallback

Preserve the existing local fallback style.

Extend it so the same fallback appears when:

- reference missing
- reference corrupt/undecodable
- capture missing
- capture corrupt/undecodable

Do not add:

- retry button
- repair flow
- metadata edit
- network behavior

---

# 18. Instrumentation Tests

Modify only:

`WackelbildScreenTest.kt`

Update the old `noLaterBlockUi_existsYet()` assertion so it no longer incorrectly expects Capture-related Block-3 UI to be absent.

Do not weaken unrelated Block-2 assertions.

Add tests for:

1. initial visible image is Reference
2. valid Capture can be displayed after toggle
3. missing Capture → same fallback
4. corrupt Capture → same fallback
5. horizontal swipe toggles once
6. reverse-direction horizontal swipe also toggles once
7. vertical gesture does not toggle
8. sensor available → tilt hint shown
9. sensor unavailable → swipe hint shown
10. accessibility action toggles image
11. lifecycle screen callbacks invoke ViewModel active/inactive/left behavior where testable
12. existing portrait/landscape intrinsic-ratio/no-crop tests remain green
13. no date/order/loading UI exists yet

Use existing test patterns only.

Do not introduce a new test library.

---

# 19. Unit Tests

## TiltProviderTest

Create tests mirroring `CompassProviderTest` style:

- sensor available
- sensor unavailable
- register listener
- stop unregisters
- roll callback uses rotation-vector orientation path
- exception-safe behavior where precedent exists

## TiltHysteresisStateMachineTest

Test at minimum:

- neutral start
- positive threshold crossing
- negative threshold crossing
- no switch from jitter inside threshold
- re-arm behavior
- angle wrap
- transition only after re-arm

## WackelbildViewModelTest

Test at minimum:

- initial image = Reference
- swipe toggles
- accessibility toggle toggles
- first sensor reading calibrates neutral only
- threshold crossing changes image
- swipe override prevents unchanged/current sensor state from undoing swipe
- **override does not clear before neutral is reached**
- neutral return arms sensor control again
- **only a subsequent new threshold crossing changes image**
- pause clears neutral/stops sensor
- resume recalibrates neutral
- screen-left stops sensor
- unavailable sensor never starts updates

---

# 20. IMPLEMENTATION_NOTES

Update only:

`docs/IMPLEMENTATION_NOTES.md`

Add a concise Block-3 entry recording:

- Capture preview added
- direct Reference/Capture switching
- tilt via `TYPE_ROTATION_VECTOR`
- horizontal swipe fallback
- neutral/re-arm hysteresis
- swipe-override rule
- lifecycle-bound sensor
- accessibility action
- no runtime permission
- real-device threshold tuning still pending
- no date/HQ/network yet

Do not update unrelated docs.

---

# 21. Files Explicitly Forbidden

Do not modify/create:

- `MainActivity.kt`
- `CompareScreen.kt`
- `CompassProvider.kt`
- `CameraScreen.kt`
- `CameraViewModel.kt`
- `WackelbildPreview.kt`
- date files
- HQ/image renderer files
- `ShareImageRenderer.kt`
- temp-file manager
- network/API files
- OkHttp
- Gradle
- AndroidManifest
- `INTERNET`
- partner-key config
- `androidx.browser`
- Custom Tabs
- ordering CTA
- transfer disclosure
- upload/loading/error state machine
- fallback-quality dialog
- release/privacy docs
- DeinWackelbild spec/plan
- project instructions

No unrelated refactor.
No formatting cleanup.
No renames.

---

# 22. Regression Safety

Must remain unchanged:

- Block-1 menu behavior
- Block-2 navigation
- Back navigation
- Block-2 local fallback style
- intrinsic image-ratio handling
- no-crop behavior
- existing session files
- all unrelated screens
- existing Compass/GPS behavior

`CompassProvider` must remain byte-for-byte untouched.

---

# 23. Verification

After implementation run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. narrow `WackelbildScreenTest` Managed Device task on `pixel2Api29`
4. `./gradlew assembleDebug`
5. `git diff --check`
6. `git status --short`

Also verify:

- exactly the 11 authorized files changed
- no forbidden file changed
- no runtime permission added
- no debug logging left behind

## Real-device validation

Block 3 is not fully product-validated until real-device testing is done.

On a physical device verify:

- slight left/right tilt feels intuitive
- no jitter from normal hand movement
- neutral recalibrates after background/resume
- neutral/re-arm-after-swipe behavior feels correct
- portrait and landscape device orientation both feel correct
- swipe remains reliable with sensor available

If real-device testing is not performed in this implementation run, report it explicitly as still required.

Do not silently claim placeholder thresholds are final.

---

# 24. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List exactly the authorized files actually changed.

State explicitly whether any unauthorized file changed.

## 3. Implementation Summary

Cover:

- Capture resolution
- visible-image state
- TiltProvider
- hysteresis
- neutral calibration
- swipe
- exact neutral→new-threshold arbitration rule
- sensor-unavailable hint
- accessibility action
- lifecycle wiring
- fallback behavior
- tests
- IMPLEMENTATION_NOTES

Explicitly confirm:

- no `WackelbildPreview.kt`
- no lifecycle types in ViewModel
- `CompassProvider.kt` untouched
- no date/HQ/network work

## 4. Regression Safety

Confirm Block 1/2 and Compass/GPS behavior remained unchanged.

## 5. Tests / Verification

Report exact commands and results:

- unit tests
- AndroidTest compile
- Managed Device Wackelbild tests
- assembleDebug
- `git diff --check`
- final `git status --short`

## 6. Real-Device Validation

State exactly what was tested physically and what remains pending.

## 7. Diff Scope

Confirm exact file scope and no unrelated edits.

## 8. Remaining Work

State only:

- real-device threshold/hysteresis tuning if still pending
- Block 4 will add date overlay behavior
- no Block 4 work was performed

## 9. Gate Result

Choose exactly one:

- **BLOCK 3 COMPLETE — READY FOR REVIEW**
- **BLOCK 3 INCOMPLETE — USER DECISION REQUIRED**

Do not begin Block 4 automatically.

---

# Final Rule

Implement exactly Block 3.

No extra preview file.
No date.
No HQ.
No network.
No manifest.
No Gradle.
No Custom Tabs.
No unrelated cleanup.

Swipe override must follow:

**SWIPE → neutral/re-arm → new threshold crossing → sensor resumes**
