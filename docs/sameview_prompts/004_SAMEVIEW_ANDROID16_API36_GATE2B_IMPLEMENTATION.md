# Gate 2B — Implementation: `compileSdk 35 → 36`

## Objective

Implement the **first actual Android 16 / API 36 migration change** for SameView.

This implementation is deliberately minimal:

- change `compileSdk` from `35` to `36`
- keep `targetSdk = 35`
- keep `minSdk = 29`
- change nothing else

No other migration work is allowed in this prompt.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

---

## Pre-Implementation Safety Check

Before editing:

1. Confirm the active branch is exactly `upgrade/android-16-api-36`.
2. Confirm the working tree is clean.
3. Confirm current HEAD is still:
   `2a38aad587abffffc36ba27a1cf8c20062bb685b`
   unless the user has explicitly created a newer approved commit since Gate 2B scope confirmation.
4. If the branch is wrong or the working tree is not clean, STOP and report the exact state.
5. Do not stash, reset, discard, or overwrite unrelated work.

---

## Source of Truth

Before implementation, re-check the relevant constraints in:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

The user has explicitly approved this one compile-SDK migration step.

The current documentation still says `compileSdk = 35` / `targetSdk = 35`.
Do **not** update documentation in this prompt.
Documentation synchronization is deferred until the migration is complete and verified.

---

## Approved Scope

### File allowed to change

Exactly one file:

`app/build.gradle.kts`

### Exact approved edit

Change:

```kotlin
compileSdk = 35
```

to:

```kotlin
compileSdk = 36
```

That is the entire implementation.

---

## Explicitly Forbidden

Do **not** change:

- `targetSdk = 35`
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
- any manifest
- any permission
- any source file
- any test
- any documentation
- any resource
- any managed-device definition
- any signing configuration
- any formatting outside the approved line

Do not:
- upgrade dependencies
- fix warnings
- refactor
- clean up code
- add API 36 emulator configuration
- change `targetSdk`
- commit
- merge
- push

---

## Implementation

Make only the approved one-line edit in:

`app/build.gradle.kts`

After editing, inspect `git diff -- app/build.gradle.kts`.

The diff must contain exactly:

```diff
-    compileSdk = 35
+    compileSdk = 36
```

If any additional diff appears, STOP and revert only your own unintended changes before continuing.

Do not touch unrelated pre-existing user work.

---

## Required Verification

Run these commands in this order:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`

Do not suppress failures.
Do not modify code to fix a failure in this prompt.
If a command fails, stop implementation work and report the exact failure.

### Gate 2A baseline for comparison

Before this change:

- `assembleDebug` → PASS
- `testDebugUnitTest` → 828/828 passed
- `lintDebug` → 0 errors, 115 warnings, 6 hints

Report any delta exactly.

Pay particular attention to:

- whether `compileSdk = 36` resolves correctly against the installed SDK
- new compile errors
- new lint errors
- new deprecation warnings
- KSP/Hilt failures
- any warning count change

Do not fix any newly surfaced warning here.
This prompt is verification only after the one-line change.

---

## Post-Verification Git Check

After all commands:

Run:

- `git status --short`
- `git diff -- app/build.gradle.kts`

Confirm:

- only `app/build.gradle.kts` is modified
- the only tracked diff is `compileSdk 35 → 36`
- no generated/build output is tracked
- no unrelated file changed

Do not commit.

---

## Manual Android Studio Check for the User

After Claude finishes successfully, the user should verify manually in Android Studio:

1. Gradle Sync completes successfully.
2. No SDK/platform resolution error appears.
3. No new KSP/Hilt sync error appears.
4. `app/build.gradle.kts` shows:
   - `compileSdk = 36`
   - `targetSdk = 35`
   - `minSdk = 29`
5. Build window contains no new compile errors.

No functional Camera/Compare/manual app test is required in this step because `targetSdk` remains 35.

---

## Required Final Report Structure

Return exactly these sections.

### 1. Modified Files

List all modified files.

Expected:
- `app/build.gradle.kts`

### 2. Exact Implementation

Show the exact before/after property value.

### 3. Verification Results

Table:

| Command | Result | Delta vs Gate 2A |
|---|---|---|

Include:
- `clean`
- `assembleDebug`
- `testDebugUnitTest`
- `lintDebug`

### 4. Lint / Warning Delta

Report:
- error count
- warning count
- hint count
- any new findings attributable to `compileSdk = 36`

### 5. Final Git State

Report:
- branch
- HEAD
- modified files
- exact diff scope
- confirmation that no commit was made

### 6. Gate 2B Verdict

Choose one:

- **GATE 2B PASSED**
- **GATE 2B FAILED**

If passed:
- state that `compileSdk = 36` is verified
- state that `targetSdk` is still `35`
- stop

Do **not** proceed to `targetSdk = 36`.
Do **not** add a managed API 36 device.
Do **not** start Gate 3 work.

---

## Final Safety Rules

- One file.
- One line.
- No unrelated changes.
- No opportunistic fixes.
- No dependency updates.
- No documentation updates.
- No commit.
- No push.
- Stop after verification report.
