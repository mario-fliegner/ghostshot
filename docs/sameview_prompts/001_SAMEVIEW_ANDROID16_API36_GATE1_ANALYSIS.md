# Gate 1 — Android 16 / API 36 Migration Analysis on Dedicated Branch

## Objective

Prepare the SameView Android app for the Google Play target API requirement by analyzing the migration from the current Android 15 / API 35 baseline to Android 16 / API 36.

This task is **analysis only**.

Do **not** implement the SDK migration yet.
Do **not** modify production code, Gradle configuration, dependencies, tests, documentation, or resources as part of this prompt.

The only repository-state change allowed in this prompt is creating and checking out the dedicated migration branch described below.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

You have direct repository access.

Before doing anything else, inspect the current repository state and confirm the active branch, HEAD, and working tree status.

---

## Step 0 — Create a Dedicated Migration Branch

The Android 16 / API 36 migration must be isolated from normal issue work.

### Required branch

Create and check out:

`upgrade/android-16-api-36`

### Branch source

The branch must be created from the current `main` branch.

Before creating it:

1. Confirm that `main` is checked out or identify the current branch.
2. Confirm whether the working tree is clean.
3. Confirm the current `main` HEAD commit.
4. Do **not** discard, stash, overwrite, reset, or otherwise alter any existing user work without explicit approval.
5. If the working tree is not clean or creating the branch safely is ambiguous, **STOP** and report the exact state instead of proceeding.

After creation, confirm:

- branch name
- branch base commit
- working tree status

Do not commit anything in this analysis task.

---

## Source-of-Truth Review — Mandatory

Before analyzing the migration, read the relevant project specifications in the repository.

At minimum inspect:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `SETTINGS_UX_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `SESSION_METADATA_V1.md`
- `SESSION_ORIGINALS_V1.md`
- `SESSION_ORIGINALS_PRIVACY_V1.md`
- `SESSION_BACKUP_EXPORT_V1.md`
- `SHARE_COMPARISON_IMAGE_V1.md`
- `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also inspect any newer or more specific specifications that are relevant to areas affected by Android 16.

### Important documented conflict

The current project instruction still documents:

- `targetSdk = 35`
- `compileSdk = 35`

The purpose of this task is explicitly to analyze a future migration to API 36.

Treat that as an **explicit user-authorized analysis scope override only**.

Do **not** silently edit the project instruction or any other specification during this analysis.

Your report must identify which documentation would need to be updated if the migration is later approved and implemented.

---

## Current Migration Goal

Analyze what is required to move SameView to:

- `compileSdk = 36`
- `targetSdk = 36`

while preserving:

- `minSdk = 29`
- existing app behavior
- existing release behavior
- existing permissions unless Android 16 genuinely requires a change
- current architecture
- current UI/UX contracts
- CameraX lifecycle correctness
- deterministic compare rendering
- session compatibility
- offline/local behavior where specified
- Play Store compliance
- current release stability

The migration must be minimal and isolated.

---

## Strict Scope

### Allowed in this prompt

- create/check out the dedicated branch
- inspect repository files
- inspect Gradle configuration
- inspect dependency versions
- inspect manifests
- inspect build logic
- inspect tests
- inspect Android 16 migration implications
- run read-only/build/test commands if useful for establishing the baseline
- report findings
- propose a migration sequence

### Forbidden in this prompt

Do **not**:

- change `compileSdk`
- change `targetSdk`
- change `minSdk`
- change AGP
- change Gradle
- change Kotlin
- change Compose
- change CameraX
- change Hilt
- change KSP
- update dependencies
- edit manifests
- change permissions
- modify source files
- modify tests
- modify docs
- apply Android Studio migration suggestions
- run automatic refactoring that writes files
- fix GitHub issues
- combine unrelated modernization work
- perform cleanup
- reformat files
- rename anything
- commit anything

This is an analysis gate only.

---

## Required Repository Analysis

Inspect the actual current build configuration and report exact values and file locations for:

- `compileSdk`
- `targetSdk`
- `minSdk`
- `versionCode`
- `versionName`
- Android Gradle Plugin version
- Gradle wrapper version
- Kotlin version
- Compose BOM/versioning strategy
- Compose compiler configuration
- Java/JVM target
- Hilt version
- KSP version
- CameraX versions
- Navigation Compose version
- Activity Compose version
- Lifecycle versions
- AndroidX Core version
- Material 3 version
- Window Size Class / adaptive layout dependencies
- ExifInterface version
- any other dependency that is plausibly relevant to API 36 compatibility

Do not assume any versions from old documentation if the repository differs.
The repository is the implementation truth for the current dependency state.

---

## Android 16 / API 36 Impact Analysis

Analyze Android 16 changes that become relevant when targeting API 36.

Do not provide a generic Android 16 changelog.

For every potentially relevant behavior change, classify it as:

- **Affected — code/config change required**
- **Affected — verification required, no change proven yet**
- **Not affected**
- **Unknown — needs targeted experiment**

For every item, provide repository evidence showing why.

Pay particular attention to the following SameView areas.

### 1. Edge-to-edge / system bars / insets

Audit:

- `MainActivity`
- theme/window setup
- `enableEdgeToEdge`
- `WindowCompat`
- status/navigation bar handling
- `systemBarsPadding`
- `navigationBarsPadding`
- `WindowInsets`
- fullscreen Compare behavior
- CameraScreen portrait and landscape controls
- dialogs/sheets
- small-height landscape layouts

This is high risk because SameView already has explicit system-bar and responsive behavior.

Do not propose a visual redesign.

Determine only what Android 16 requires to preserve the existing specifications.

### 2. Camera and CameraX lifecycle

Audit:

- CameraX provider binding/unbinding
- Preview
- ImageCapture
- rotation
- lifecycle ownership
- activity recreation
- foreground/background transitions
- camera permission handling
- any API-level conditionals

Camera lifecycle is high risk.

Do not redesign or refactor it.

### 3. Navigation and back behavior

Audit:

- Navigation Compose
- system back
- `BackHandler`
- fullscreen Compare exit behavior
- modal/dialog back behavior
- predictive back compatibility if relevant
- activity/task behavior if relevant

Preserve current navigation contracts.

### 4. Permissions

Audit the current handling of:

- CAMERA
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_MEDIA_LOCATION

Determine whether API 36 requires any manifest or runtime-flow changes.

Do not introduce any new permission unless it is strictly required.

Specifically confirm that:

- no background location is introduced
- no legacy media/storage permission is introduced
- no new unnecessary dangerous permission is introduced

### 5. MediaStore and storage

Audit:

- image capture writes
- video writes
- image export writes
- SAF backup
- EXIF updates
- URI access
- pending MediaStore items
- scoped-storage assumptions
- any API-specific behavior

Preserve existing session/storage contracts.

### 6. Photo Picker / SAF / original media access

Audit:

- Android Photo Picker
- SAF fallback
- `MediaStore.setRequireOriginal()`
- ACCESS_MEDIA_LOCATION
- HEIC/HEIF/AVIF/JPEG handling
- URI lifetime assumptions

Identify API 36 risks only.

### 7. Foreground-only GPS and sensors

Audit:

- `LocationManager`
- sensor listeners
- lifecycle activation/deactivation
- foreground restrictions
- precision permission behavior
- any API-level branching

GPS must remain architecturally isolated from compare rendering.

### 8. Compose / responsive layout compatibility

Audit whether the current Compose, Material 3, Activity, Window Size Class, and Insets APIs are compatible with:

- compileSdk 36
- targetSdk 36

Do not recommend library upgrades merely because newer versions exist.

Only identify upgrades that are:

- mandatory for build compatibility
- mandatory for API 36 behavior correctness
- strongly required because the current version has a known incompatibility

Everything else must remain out of scope.

### 9. Native / packaging / build system

Audit:

- AGP compatibility with compileSdk 36
- Gradle compatibility
- JDK compatibility
- KSP compatibility
- Hilt/KSP wiring
- R8/proguard
- resource shrinking
- release signing configuration
- packaging
- manifest merging
- lint behavior

Determine the **minimum necessary build-tool upgrade**, if any.

Do not suggest “upgrade everything to latest”.

### 10. Tests and managed devices

Inspect:

- unit tests
- instrumentation tests
- managed devices
- API levels currently configured
- real-device assumptions
- tests that are sensitive to insets/system bars/camera/media/location

Determine what API 36 verification coverage is missing.

---

## Android Studio Upgrade Assistant

The user still uses Android Studio.

As part of the report, explain exactly how the user should use Android Studio's SDK Upgrade Assistant for this project **after this analysis gate**, including:

- where to find it in Android Studio
- what target API to choose
- which categories/results are relevant
- which suggested changes must be reviewed manually rather than applied blindly
- which results should be compared against your repository findings

Do not rely on the Upgrade Assistant as the sole authority.

The project's specifications and actual implementation remain authoritative for preserving SameView behavior.

---

## Baseline Verification

Before recommending migration steps, establish the current branch baseline as far as practical.

At minimum determine whether these commands are currently expected to be relevant:

- `./gradlew clean`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew bundleRelease`
- `./gradlew connectedDebugAndroidTest`
- any configured Gradle Managed Device test tasks

You may run commands that do not modify source/configuration if useful.

If you run commands, report:

- exact command
- pass/fail
- relevant failures
- whether the failure is pre-existing or migration-related

Do not suppress or bypass failures.

If a command is not run, explicitly state that it was not run.

---

## Required Migration Plan

The final report must propose a **minimal staged implementation sequence**.

Do not implement it.

The sequence should separate concerns, for example:

1. build-tool/API-36 prerequisite changes only
2. compileSdk 36
3. targetSdk 36 behavior activation
4. targeted Android 16 compatibility fixes, one problem at a time
5. API 36 instrumentation / managed-device verification
6. real-device validation
7. release build verification
8. documentation synchronization
9. merge back to `main`

However, do not assume this exact sequence is correct.
Derive the actual sequence from the repository.

Each proposed stage must identify:

- exact files expected to change
- why the stage is needed
- what must not change
- tests required before proceeding
- rollback boundary
- risk level

---

## GitHub Issues — Explicitly Out of Scope

Do not fix or analyze the normal GitHub issue backlog in this task except where an existing issue is directly relevant evidence for the API 36 migration.

The intended order is:

1. complete Android 16 / API 36 migration first
2. merge the verified migration into `main`
3. then address normal GitHub issues separately, one issue/fix at a time

Do not combine issue fixes with the SDK migration.

---

## Release-Safety Review

Your report must explicitly assess:

- Play Store target API compliance
- permission impact
- privacy impact
- offline behavior impact
- camera lifecycle risk
- compare rendering risk
- session/storage compatibility
- navigation risk
- accessibility/insets risk
- release build stability
- upgrade/install implications for existing users

If you identify any behavior that could silently change for existing users, call it out explicitly.

---

## Required Final Report Structure

Return exactly these sections.

### 1. Branch State

- original branch
- `main` HEAD
- created branch
- branch base commit
- working tree state
- confirmation that no files were modified

### 2. Current SDK / Build Baseline

Table with exact current values and source file paths.

### 3. Source-of-Truth Review

- specs reviewed
- relevant binding constraints
- stale/conflicting documentation
- documentation that would need updating after approved implementation

### 4. Android 16 / API 36 Compatibility Matrix

Table:

| Area | Status | Evidence | Required action |
|---|---|---|---|

Use only:
- change required
- verification required
- not affected
- unknown / targeted experiment required

### 5. Required Build-Tool / Dependency Changes

Separate into:

- mandatory
- probably required
- not required
- unknown

Every proposed upgrade must include evidence.

### 6. High-Risk SameView Areas

Cover at minimum:

- Camera lifecycle
- Edge-to-edge / system bars
- Compare fullscreen
- Navigation/back
- Permissions
- MediaStore/storage
- GPS/sensors
- Compose responsive layouts
- Hilt/KSP/build pipeline

### 7. Android Studio Upgrade Assistant Procedure

Exact user-facing procedure for Android Studio.

### 8. Test Baseline

- commands run
- results
- commands not run
- current managed-device coverage
- missing API 36 coverage
- real-device validation requirements

### 9. Minimal Migration Sequence

Numbered stages.

For each stage:

- goal
- files expected to change
- exact type of change
- tests
- risk
- rollback point

### 10. Release / Play Risk Assessment

Explicit verdict on whether anything is likely to block the next Play update after target 36 migration.

### 11. Gate 1 Verdict

Choose one:

- **READY FOR GATE 2**
- **BLOCKED**

If ready, state exactly what the **first implementation step only** should be.

Do not implement it.

---

## Final Safety Rules

- One migration problem per implementation step later.
- No combined modernization.
- No opportunistic refactoring.
- No unrelated issue fixes.
- No “latest dependency” sweep.
- No source modifications in this Gate 1 prompt.
- No documentation modifications in this Gate 1 prompt.
- No commit.
- Stop after the analysis report.
