# Gate 3A — Implementation: `targetSdk 35 → 36`

## Objective

Implement exactly one Android 16 / API 36 migration change:

- `targetSdk = 35` → `targetSdk = 36`

No other project change is allowed in this gate.

This gate activates targetSdk-36 runtime behavior and must therefore be followed by both automated regression verification and mandatory real-device Android 16 checks.

Do **not** add the API 36 Gradle Managed Device in this gate. That is deferred to Gate 3B.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

---

## Pre-Implementation Safety Check

Before editing:

1. Confirm the active branch is exactly:
   `upgrade/android-16-api-36`
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. Confirm:
   - `compileSdk = 36`
   - `targetSdk = 35`
   - `minSdk = 29`
5. Confirm Gate 2C accessibility fix is committed.
6. If anything differs unexpectedly, STOP and report the exact state.
7. Do not stash, reset, discard, overwrite, or merge unrelated work.

---

## Source-of-Truth Review

Before implementation, re-check the relevant constraints in:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Use the completed Gate 3 analysis and Gate 3 scope-confirmation report as the approved implementation basis.

Do **not** update documentation in this gate.

---

## Approved Scope

### File allowed to change

Exactly one file:

`app/build.gradle.kts`

### Exact approved edit

Change:

```kotlin
targetSdk = 35
```

to:

```kotlin
targetSdk = 36
```

That is the entire implementation.

---

## Explicitly Unchanged

Do not change:

- `compileSdk = 36`
- `minSdk = 29`
- `versionCode`
- `versionName`
- AGP
- Gradle wrapper
- Kotlin
- KSP
- Hilt
- CameraX
- Compose BOM
- Navigation Compose
- Lifecycle
- AndroidX Core
- Material 3
- ExifInterface
- manifests
- permissions
- source files
- tests
- documentation
- resources
- signing configuration
- managed-device definitions

Do not add `pixel2Api36` here.
Do not fix any runtime issue unless this gate explicitly fails and a separate fix gate is approved later.

---

## Implementation

Make only the one-line edit in:

`app/build.gradle.kts`

After editing, inspect:

`git diff -- app/build.gradle.kts`

The diff must contain exactly:

```diff
-        targetSdk = 35
+        targetSdk = 36
```

If any additional diff appears, STOP and remove only your own unintended change before continuing.

---

# Automated Verification

Run in this order:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`
5. `./gradlew assembleRelease`
6. `./gradlew bundleRelease`
7. `./gradlew pixel2Api35DebugAndroidTest`

Do not suppress failures.
Do not modify code to make a failing check pass in this prompt.
If anything fails, report the exact failure and stop.

## Baseline for comparison

Known baseline before `targetSdk = 36`:

- unit tests: 828/828 passed
- `assembleDebug`: PASS
- `lintDebug`: 0 errors, 114 warnings, 6 hints
- `assembleRelease`: PASS
- `bundleRelease`: PASS

Expected lint change:
- the existing `[OldTargetApi]` warning tied to `targetSdk = 35` should disappear
- any other delta must be reported explicitly

Do not assume the final warning count in advance; report actual results.

---

# Mandatory Real-Device Android 16 Verification

Automated verification alone does **not** complete Gate 3A.

The user will validate the resulting build on a real Android 16 device.

Claude must therefore finish with this section marked:

**REAL-DEVICE VALIDATION: NOT RUN — STILL REQUIRED**

Do not claim Gate 3A fully passed until the user reports the manual checks below.

## 1. CameraScreen

### Normal camera entry
Expected:
- CameraScreen opens normally
- live CameraX preview is visible
- controls are reachable

### Predictive-back swipe and cancel
Perform a back gesture slowly enough to begin predictive-back behavior, then cancel it.

Expected:
- CameraScreen remains active
- camera preview remains intact
- no blank/frozen preview
- no navigation occurs

### Completed back gesture
Expected:
- normal NavHost back behavior
- app leaves CameraScreen according to current navigation stack

### Marker edit mode
Enter marker-edit mode, then perform back.

Expected:
- marker-edit mode exits
- CameraScreen itself remains open
- existing marker-back contract is preserved

---

## 2. CompareScreen

### Normal mode
Back gesture should navigate back normally.

### Fullscreen — cancel gesture
Enter fullscreen, begin predictive-back gesture, then cancel.

Expected:
- fullscreen remains active
- comparison remains stable
- no unintended screen navigation

### Fullscreen — complete gesture
Complete back while fullscreen.

Expected:
- fullscreen exits
- CompareScreen remains open
- the same gesture must **not** also navigate away from CompareScreen

This is a binding contract from `COMPARE_FLOW_V1.md`.

---

## 3. CompareLibraryScreen

Enter multi-select / selection mode.

Back gesture expected:
- selection mode exits
- selected session IDs are cleared
- library remains open

---

## 4. EditSessionScreen

Create a dirty/unsaved state.

Back gesture expected:
- discard-confirmation dialog appears
- changes are not silently discarded
- screen does not silently close

If saving-state back behavior can be reproduced safely:
- back must show the existing blocking/saving behavior
- screen must not silently leave

---

## 5. CreateVideoScreen

During active video rendering:

Back gesture expected:
- existing cancel-confirmation dialog appears
- render is not silently abandoned
- no broken/dangling state

If reproducing active rendering is impractical, explicitly note that this one real-device subcheck remains outstanding.

---

## 6. SettingsScreen

Open the location-permission rationale dialog.

Back gesture expected:
- existing dismiss-as-denial path executes
- no silent bypass of the current permission-flow behavior

Do not alter permissions for the sake of this test unless necessary to reach the existing rationale flow.

---

## 7. Walkthrough

While mid-walkthrough:

Back gesture expected:
- current NavHost behavior remains coherent
- no crash
- no broken first-run/walkthrough persistence state

There is no stronger existing product contract here; do not invent one.

---

## 8. System Bars / Insets Smoke Check

Visually check at least:

- CameraScreen
- CompareLibraryScreen
- SettingsScreen
- Guide

Expected:
- no important controls hidden underneath status/navigation bars
- no obvious new inset regression
- no obvious predictive-back visual corruption

This is a smoke check, not a redesign review.

---

# Post-Verification Git Check

After automated verification, run:

- `git status --short`
- `git diff -- app/build.gradle.kts`

Confirm:
- only `app/build.gradle.kts` is modified
- exact diff is one line
- no generated/build output is tracked
- no commit was made

Do not commit.

---

# Required Final Report

Return exactly these sections.

## 1. Modified Files

Expected:
- `app/build.gradle.kts`

## 2. Exact Implementation

Show:
- `targetSdk 35 → 36`
- confirm `compileSdk = 36`
- confirm `minSdk = 29`

## 3. Automated Verification Results

Table:

| Command | Result | Delta vs baseline |
|---|---|---|

Include all seven commands.

## 4. Lint / Warning Delta

Report:
- lint errors
- lint warnings
- lint hints
- whether `[OldTargetApi]` disappeared
- any new target-36-related findings

## 5. Release Build Verification

Report:
- `assembleRelease`
- `bundleRelease`
- whether R8/resource shrinking still succeed

## 6. Existing API 35 Managed-Device Regression Result

Report:
- `pixel2Api35DebugAndroidTest`
- pass/fail counts
- any regression

## 7. Final Git State

Report:
- branch
- HEAD
- modified files
- exact diff scope
- no commit

## 8. Real-Device Android 16 Validation

State exactly:

**NOT RUN — STILL REQUIRED**

Then reproduce the manual checklist from this prompt in concise form.

## 9. Gate 3A Verdict

Choose one:

- **AUTOMATED VERIFICATION PASSED — REAL-DEVICE VALIDATION REQUIRED**
- **GATE 3A FAILED**

Do not call Gate 3A fully complete until the user reports successful real-device validation.

Do not proceed to Gate 3B.
Do not add `pixel2Api36`.
Do not update docs.
Do not commit, push, or merge.

Stop.
