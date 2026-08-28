# Gate 2B — Scope Confirmation for `compileSdk 35 → 36`

## Objective

Prepare the **first actual Android 16 / API 36 migration change** for SameView.

This prompt is **scope confirmation only**.

Do **not** modify any file yet.
Do **not** implement the SDK change yet.
Do **not** commit anything.

The purpose of this gate is to confirm the exact one-file, one-line change and its verification plan before implementation.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

Before doing anything:

1. Confirm the active branch is exactly `upgrade/android-16-api-36`.
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. If the tree is not clean, STOP and report the exact state.
5. Do not stash, reset, discard, modify, or commit anything.

---

## Source-of-Truth Review

Read at minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also use the completed Gate 1 and Gate 2A reports as context if available.

Important:
The current specs still document `compileSdk = 35` and `targetSdk = 35`.
The user has explicitly approved analyzing a migration toward API 36, but **documentation must not be changed in this gate**.

---

## Proposed Change to Confirm

The intended implementation step is exactly:

**File:**

`app/build.gradle.kts`

**Change:**

```kotlin
compileSdk = 35
```

to:

```kotlin
compileSdk = 36
```

That is the only intended project change.

### Must remain unchanged

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
- all manifests
- all source files
- all tests
- all documentation
- all managed-device definitions
- all permissions
- all signing configuration

Do not add API 36 managed devices in this step.
Do not perform any dependency update.
Do not fix any warning or unrelated issue.

---

## Required Scope Confirmation

Before implementation, report:

### 1. Files to be modified

List **all** files that would be modified.

Expected answer:

- `app/build.gradle.kts`

If any additional file appears necessary, STOP and explain why before implementation.

### 2. Exact change

State the exact property and value transition:

- `compileSdk: 35 → 36`

Confirm explicitly:

- `targetSdk` remains `35`
- `minSdk` remains `29`

### 3. Unrelated code protection

Confirm explicitly that no unrelated code, tests, dependencies, manifests, docs, resources, or formatting will be touched.

### 4. Risk assessment

Assess only risks caused by raising `compileSdk` while `targetSdk` stays at 35.

Expected framing:

- compile-time/API-surface compatibility risk
- possible new deprecation/lint findings
- possible build-tool/API-stub resolution issue
- **no targetSdk-36 runtime behavior activation yet**

Do not speculate about unrelated modernization.

### 5. Verification plan after implementation

The implementation step, once approved, must be followed by:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`

Compare results against Gate 2A baseline:

- 828/828 unit tests passed
- `assembleDebug` passed
- `lintDebug`: 0 errors, 115 warnings, 6 hints

Report any delta.

Do not suppress warnings or failures.

### 6. Manual Android Studio verification after implementation

After the one-line change is implemented and Gradle sync completes, the user should manually verify in Android Studio:

- Gradle Sync completes successfully
- no unresolved SDK/platform error
- no new KSP/Hilt sync error
- `app/build.gradle.kts` shows `compileSdk = 36`
- `targetSdk` still shows `35`
- Build window contains no new compile errors

No camera/compare/manual functional app test is required at this stage because `targetSdk` remains 35.

---

## Required Final Output

Return exactly these sections:

### 1. Branch State
- branch
- HEAD
- clean/dirty

### 2. Files to Modify
List every file that would change.

### 3. Exact Planned Change
Show the one property transition only.

### 4. Explicitly Unchanged
List the important values/components that will remain untouched.

### 5. Risks
Only risks relevant to `compileSdk 35 → 36`.

### 6. Verification Plan
Commands and expected baseline comparison.

### 7. Manual Android Studio Check
Short checklist for the user after implementation.

### 8. Scope Verdict

Choose one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **BLOCKED**

If confirmed, STOP.

Do not implement anything until the user explicitly approves the implementation step.

---

## Final Safety Rules

- No file modifications.
- No code/config changes.
- No dependency updates.
- No docs.
- No managed-device additions.
- No tests changed.
- No commits.
- Stop after scope confirmation.
