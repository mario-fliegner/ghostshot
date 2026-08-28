# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 3C: TILT THRESHOLD TUNING — IMPLEMENTATION

## Task

Implement the already analyzed and approved Block 3C real-device tuning fix.

Real-device feedback: the phone currently has to be tilted too far before the image switches.

Approved production change:

- `THRESHOLD_DEGREES = 12f` → `9f`
- `REARM_DEGREES = 6f` remains unchanged

Review additionally requires one focused regression test protecting the actual production defaults.

Do not begin Block 4.

## Authoritative inputs

Read first:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current:
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt`
- `app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachineTest.kt`

Record branch, HEAD and `git status --short` before editing. Block 3 changes are currently uncommitted; preserve them.

## Exact authorized scope

Modify exactly these three files:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt`
2. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachineTest.kt`
3. `docs/IMPLEMENTATION_NOTES.md`

If another file is necessary, STOP and report why.

## Production change

In `TiltHysteresisStateMachine.kt`, change only the production trigger constant:

- `THRESHOLD_DEGREES = 12f` → `9f`

Keep:

- `REARM_DEGREES = 6f`

Do not change states, transition logic, comparison operators, angle wrapping, constructor structure, neutral logic, or re-arm logic.

## Production-default regression test

In `TiltHysteresisStateMachineTest.kt`, add one focused test that constructs:

`TiltHysteresisStateMachine()`

with **no explicit threshold/re-arm arguments**.

It must prove the real production defaults:

1. from neutral, a value just below 9° does not transition;
2. reaching 9° produces the expected `TOWARD_*` transition;
3. while tilted, a value still outside the 6° re-arm zone does not re-arm;
4. reaching the 6° re-arm boundary returns to `NEUTRAL`.

Use existing test conventions.

Do not rewrite the existing generic tests that intentionally use explicit `12f/6f`; they remain valid generic state-machine tests.

No new test library.

## Documentation

In `docs/IMPLEMENTATION_NOTES.md`, update only the existing Block 3 factual threshold entry:

- `THRESHOLD_DEGREES = 12f` → `THRESHOLD_DEGREES = 9f`
- preserve `REARM_DEGREES = 6f`
- preserve wording that real-device tuning/validation is required where applicable.

No broader documentation rewrite.

## Forbidden changes

Do not modify:
- `WackelbildViewModel.kt`
- `WackelbildScreen.kt`
- `WackelbildScreenTest.kt`
- `TiltProvider.kt`
- `TiltProviderTest.kt`
- `WackelbildViewModelTest.kt`
- `CompassProvider.kt`
- resources/strings
- navigation
- swipe behavior
- swipe/sensor arbitration
- lifecycle behavior
- neutral calibration
- sensor axis/remapping
- permissions
- Gradle
- AndroidManifest
- date/HQ/network functionality
- feature spec or implementation plan.

Do not change `REARM_DEGREES`.
Do not add filtering, smoothing, timers, refactors, renames, or unrelated cleanup.

## Regression contract

Only one production behavior may change:

**less physical tilt is required for the initial image switch.**

Everything else remains unchanged, including:

`SWIPE → neutral/re-arm → new threshold crossing → sensor resumes`

The 9° trigger and 6° re-arm retain a positive 3° hysteresis gap. Do not claim this is finally optimal until physically revalidated.

## Verification

Run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew assembleDebug`
3. `git diff --check`
4. `git status --short`

Explicitly verify:
- the new production-default test passes;
- existing state-machine tests remain green;
- all unit tests remain green;
- exactly the three authorized files changed in this tuning iteration;
- no diagnostic/debug code was added.

No instrumentation run is required because current instrumentation tests do not depend on the numeric threshold.

Do not suppress failures.

## Physical-device revalidation

After implementation, physical-device validation is still required:

1. switching requires noticeably less tilt;
2. normal hand jitter does not cause false switching;
3. repeated left/right switching stays stable;
4. swipe still toggles once;
5. after swipe, sensor control still requires neutral/re-arm followed by a new threshold crossing.

If 9° is still wrong, do not choose another value automatically. Report it for a separate iteration.

## Required final report

Return exactly:

### 1. Repository Baseline
- branch
- HEAD
- initial status

### 2. Files Modified
List the three authorized files and confirm no unauthorized file changed in this tuning step.

### 3. Implementation
Confirm:
- `12f → 9f`
- `6f` unchanged
- no logic change
- production-default regression test added
- documentation corrected

### 4. Regression Test
Explain exactly what the new no-argument default test proves.

### 5. Verification
Report commands/results for unit tests, assembleDebug, diff check and final status.

### 6. Scope Confirmation
Confirm no other sensor/swipe/lifecycle/UI/navigation/date/HQ/network behavior changed.

### 7. Real-Device Status
State that physical-device revalidation of 9°/6° is still required.

### 8. Gate Result

Choose exactly one:

- **BLOCK 3C IMPLEMENTED — READY FOR REAL-DEVICE REVALIDATION**
- **BLOCK 3C INCOMPLETE — USER DECISION REQUIRED**

Then STOP. Do not begin Block 4.
