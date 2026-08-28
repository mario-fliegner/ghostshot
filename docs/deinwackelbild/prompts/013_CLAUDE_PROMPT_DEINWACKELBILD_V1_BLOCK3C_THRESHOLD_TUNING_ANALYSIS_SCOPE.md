# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 3C: TILT THRESHOLD TUNING — ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Block 3 has been implemented and is under real-device tuning.

Real-device feedback:

> The phone currently has to be tilted too far before the image switches.

Current reported placeholder values:

- `THRESHOLD_DEGREES = 12f`
- `REARM_DEGREES = 6f`

Proposed single tuning change:

- `THRESHOLD_DEGREES: 12f -> 9f`
- keep `REARM_DEGREES = 6f` unchanged

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files yet.
Do not output code.
Do not change any other sensor behavior.
Do not begin Block 4.

---

## 1. Authoritative Inputs

Read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt`
- `app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachineTest.kt`
- `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt`

Inspect any other test only if it directly asserts the current `12f` threshold or threshold-boundary behavior.

---

## 2. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Do not touch unrelated working-tree content.

---

## 3. Exact Analysis

Confirm from code:

1. where `THRESHOLD_DEGREES = 12f` is defined;
2. whether it is the actual switch trigger used by the hysteresis state machine;
3. whether `REARM_DEGREES = 6f` independently controls the neutral/re-arm zone;
4. whether changing only `12f -> 9f` preserves:
   - direct A/B switching;
   - hysteresis;
   - swipe override;
   - `SWIPE -> neutral/re-arm -> new threshold crossing -> sensor resumes`;
   - lifecycle;
   - no-permission behavior;
   - no animation/haptic/sound;
5. which tests contain explicit boundary values tied to `12f`.

Do not guess.

---

## 4. Strict Scope

Expected implementation scope should be limited to:

- `TiltHysteresisStateMachine.kt`
- only directly affected unit-test file(s)

Do not modify:

- `WackelbildScreen.kt`
- `TiltProvider.kt`
- `CompassProvider.kt`
- lifecycle code
- swipe code
- strings
- navigation
- date/HQ/network code

Do not modify `WackelbildViewModel.kt` unless repository evidence shows a test-only threshold constant there that must be adjusted.

Do not change `IMPLEMENTATION_NOTES.md` unless it explicitly records `12f` as a factual current value that would become wrong.

---

## 5. Forbidden Changes

Do not:

- change `REARM_DEGREES`
- change hysteresis states
- change neutral logic
- change swipe override logic
- change sensor provider
- change roll-axis handling
- change display-rotation mapping
- add smoothing/filtering
- add timers
- add UI
- refactor
- rename
- reformat unrelated code
- begin Block 4

This is one tuning fix only.

---

## 6. Regression Safety

Assess the effect of lowering the trigger from 12° to 9° while keeping re-arm at 6°.

Expected rationale:

- less physical tilt required;
- 6° re-arm remains unchanged;
- 3° separation still preserves hysteresis;
- real-device validation remains required.

If current code makes that reasoning wrong, report it and stop.

---

## 7. Required Scope Confirmation Output

Return exactly:

### 1. Repository Baseline

- branch
- HEAD
- working-tree state

### 2. Current Threshold Evidence

- exact file
- current threshold
- current re-arm
- exact code path using them

### 3. Root Cause

Explain why 12° matches the reported "too much tilt required" symptom.

### 4. Proposed Change

Confirm whether the minimal change is exactly:

- `THRESHOLD_DEGREES = 9f`
- `REARM_DEGREES = 6f` unchanged

### 5. Files Proposed for Modification

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

List every file that would be touched.

### 6. Files Explicitly NOT Touched

Confirm no changes to:

- ViewModel behavior
- TiltProvider
- CompassProvider
- swipe
- lifecycle
- UI
- strings
- navigation
- date/HQ/network code

### 7. Tests to Update / Run

State:

- exact existing tests requiring numeric boundary updates, if any;
- tests that remain unchanged;
- planned verification commands.

At minimum assess:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

No instrumentation run is required unless current instrumentation tests directly depend on the numeric threshold.

### 8. Real-Device Revalidation

After implementation the user must re-test:

- tilt amount feels lower/more natural;
- normal hand jitter does not cause false switching;
- repeated left/right switching remains stable;
- swipe override still requires neutral/re-arm then a new threshold crossing.

### 9. Scope Confirmation

End with exactly:

**BLOCK 3C SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

## Final Rule

Analysis only.

Expected fix:

`THRESHOLD_DEGREES: 12f -> 9f`

Do not implement until explicitly approved.
