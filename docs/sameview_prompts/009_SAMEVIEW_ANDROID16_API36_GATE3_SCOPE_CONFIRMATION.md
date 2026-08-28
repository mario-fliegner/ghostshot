# Gate 3 — Scope Confirmation: `targetSdk 35 → 36`

## Objective

Confirm the exact implementation and verification scope for activating Android 16 / API 36 target-SDK behavior in SameView.

This is **scope confirmation only**. Do not modify files, run implementation, add devices, commit, push, or merge.

## Repository / baseline

Repository: `C:\data\work\privat\git-repos\sameview`
Required branch: `upgrade/android-16-api-36`

Confirm before proceeding:
- working tree clean
- current HEAD
- `compileSdk = 36`
- `targetSdk = 35`
- `minSdk = 29`
- Gate 2C accessibility fix committed

If any baseline differs unexpectedly, STOP.

## Source of truth

Re-read the relevant project specifications, especially:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Use the completed Gate 3 analysis as the basis for this scope confirmation.

Do not update stale SDK documentation yet.

## Gate 3 analysis conclusion to validate

The analysis concluded:
- no preparatory production-code fix is required
- the actual production change can be exactly one line:
  `targetSdk = 35` → `targetSdk = 36`
- predictive back is the main runtime verification risk
- existing modern Compose `BackHandler` usage should be verified rather than rewritten preemptively
- no permission/storage/GPS/schema migration is required

Confirm these conclusions still hold against the current clean HEAD.

## Important scope correction: API 36 managed device

The Gate 3 analysis suggested adding `pixel2Api36` in the same implementation gate.

For change discipline, **do not combine that configuration change with the one-line targetSdk change**.

This gate must explicitly split the work:

### Gate 3A
One production/config change only:
- `app/build.gradle.kts`
- `targetSdk = 35` → `targetSdk = 36`

No managed-device definition added in Gate 3A.

### Gate 3B
Only after Gate 3A builds/tests cleanly:
- separately scope and add an API 36 Gradle Managed Device
- run the appropriate API-36 instrumentation suite

Confirm whether this separation is technically valid. If it is not, explain the concrete blocker and STOP.

## Gate 3A exact allowed change

Expected file:
`app/build.gradle.kts`

Expected diff:
```diff
-        targetSdk = 35
+        targetSdk = 36
```

Confirm:
- `compileSdk` remains 36
- `minSdk` remains 29
- versionCode/versionName unchanged
- dependencies unchanged
- manifests unchanged
- source code unchanged
- tests unchanged
- docs unchanged
- permissions unchanged
- managed-device definitions unchanged

If another file is required merely to make `targetSdk=36` compile/build, report it as a blocker rather than expanding scope.

## Gate 3A automated verification plan

Confirm the exact commands to run immediately after the one-line implementation:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`
5. `./gradlew assembleRelease`
6. `./gradlew bundleRelease`

Also identify whether an existing API 35 or API 33 managed-device smoke/instrumentation task should run in Gate 3A before API-36 device provisioning in Gate 3B. If yes, name the smallest useful existing task and why. Do not add or alter tests.

Report all warning/lint deltas against the current verified baseline. Do not suppress or fix findings in this gate.

## Mandatory manual verification after Gate 3A

Define a concise real-device Android 16 checklist focused on targetSdk-36 behavior. It must include at least:

### Camera
- normal camera entry and live preview
- predictive-back swipe and cancel while preview is active
- completed back gesture from CameraScreen
- marker-edit mode: back exits marker edit according to existing contract

### Compare
- normal Compare back
- fullscreen Compare: predictive-back cancel leaves fullscreen stable
- fullscreen Compare: completed back exits fullscreen only, not the screen in the same gesture

### Compare Library
- selection mode: back exits selection mode and clears selection

### Edit Session
- dirty state: back shows discard confirmation
- saving state if practically reproducible: back must not silently leave

### Create Video
- during rendering: back shows cancel confirmation and does not silently abandon rendering

### Settings
- location rationale dialog: back dismissal follows the existing denial/dismiss contract

### Walkthrough
- first-run/mid-walkthrough back behavior remains coherent and does not corrupt the first-run state

### General visual smoke
- system bars/insets on Camera, Compare Library, Settings and Guide
- no controls under status/navigation bars
- no obvious predictive-back visual corruption

For each manual item, state expected behavior from existing code/specs where known.

Do not invent new UX requirements for undocumented behavior.

## Risks to confirm

Classify the Gate 3A risks:
- targetSdk-36 runtime behavior activation
- predictive back
- edge-to-edge/insets
- CameraX/live preview during back gesture
- permission/storage/GPS regression
- release build stability

Explicitly distinguish:
- risks requiring code now
- risks requiring verification only

## Documentation impact

Confirm that SDK/version statements in source-of-truth docs are now stale or will become stale after Gate 3A.

Do not update them in Gate 3A.

State which docs will require synchronization only after the API-36 migration is fully verified.

## Required final report

Return exactly:

### 1. Branch / Baseline
### 2. Files for Gate 3A
### 3. Exact Gate 3A Change
### 4. Explicitly Unchanged
### 5. Gate 3A Risks
### 6. Automated Verification Plan
### 7. Real-Device Android 16 Verification Plan
### 8. Gate 3B Managed-Device Separation
### 9. Documentation Impact
### 10. Scope Verdict

Verdict must be one:
- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **BLOCKED**

If confirmed, STOP. Do not implement `targetSdk=36`.
