# Gate 2A — API 36 SDK / Toolchain Verification (No Project Changes)

## Objective

Verify that the current SameView Android project can move toward Android 16 / API 36 **without changing any tracked project file yet**.

This gate is preparation only.

Do **not** change:
- `compileSdk`
- `targetSdk`
- `minSdk`
- dependencies
- Gradle versions
- Kotlin
- KSP
- Hilt
- CameraX
- manifests
- source code
- tests
- documentation
- managed-device configuration

Do not commit anything.

The goal is to establish whether the local Android/Gradle toolchain is ready for API 36 and whether any build-tool prerequisite is actually required before the first real migration change.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

Before analysis:

1. Confirm the active branch is exactly `upgrade/android-16-api-36`.
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. If the working tree is not clean, STOP and report exact status.
5. Do not stash, reset, discard, modify, or commit anything.

---

## Source of Truth

Read at minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also use the previous Gate 1 report as context if available.

Important:
The current project specs still define `compileSdk = 35` / `targetSdk = 35`.
This gate does **not** override those values.
No tracked file may be changed.

---

## Task A — Verify API 36 SDK Availability

Determine whether Android SDK Platform 36 is installed locally.

Inspect the actual SDK installation and report:

- Android SDK root
- whether `platforms/android-36` exists
- whether `platforms/android-36/android.jar` exists
- installed build-tools versions
- installed platform-tools version if readily available
- whether Android Studio should already be able to select API 36

Do not install anything automatically.

If API 36 is missing:
- report exactly what is missing
- tell the user what to install in Android Studio SDK Manager
- STOP before any migration attempt

---

## Task B — Verify Current Build Toolchain Against API 36

Current known baseline from Gate 1:

- AGP `9.1.1`
- Gradle `9.3.1`
- Kotlin `2.2.10`
- KSP `2.3.6`
- Hilt `2.59`

Verify these from the repository again.

Then determine whether there is **evidence of an actual incompatibility** with compileSdk 36.

Do not assume a dependency/tool version must change just because its version string looks unusual.

For each of these, classify:

- confirmed compatible
- likely compatible / no blocker found
- incompatible
- unknown

For:
- AGP
- Gradle
- Kotlin
- KSP
- Hilt
- Compose compiler plugin
- Compose BOM strategy

Use actual repository/build evidence where possible.

If external documentation is available in your environment, use authoritative vendor documentation.
If not, be explicit that compatibility is inferred from local build behavior rather than proven from vendor docs.

---

## Task C — Resolve the KSP Question Properly

Gate 1 flagged `KSP 2.3.6` as suspicious because the version does not follow the older `<kotlinVersion>-<suffix>` naming convention.

Do not treat this as a problem unless proven.

Verify:

1. the exact plugin declaration and repository source
2. whether Gradle resolves KSP cleanly
3. whether current compilation with KSP succeeds
4. whether Hilt/KSP-generated sources are produced correctly
5. whether there is any warning/error that indicates the KSP/Kotlin combination is unsupported

If the existing project builds cleanly with the configured KSP version, state that clearly.

Do not recommend changing KSP unless there is concrete evidence.

---

## Task D — Establish a Clean Baseline Before compileSdk Change

Run a meaningful no-change baseline.

Required commands:

1. `./gradlew clean`
2. `./gradlew testDebugUnitTest`
3. `./gradlew assembleDebug`
4. `./gradlew lintDebug`

You may combine commands only if the result remains individually attributable.

Do not modify source/configuration to make them pass.

Report:
- pass/fail
- duration if available
- warnings relevant to API 36 migration
- deprecations relevant to future compileSdk 36
- lint findings relevant to Android 16
- whether any failure is pre-existing

If `lintDebug` fails, do not suppress anything. Report exact findings.

---

## Task E — Verify Release Build Capability Without Assuming Signing Is a Blocker

Gate 1 noted that no `signingConfigs` block exists in the repository.

Do not infer from that alone that release builds are impossible.

Determine:

- whether `./gradlew assembleRelease` can run in the current environment
- whether `./gradlew bundleRelease` can run in the current environment
- whether they fail specifically because of signing, configuration, or something else

Run:

1. `./gradlew assembleRelease`
2. `./gradlew bundleRelease`

If either fails:
- report the exact failure reason
- do not modify signing config
- do not create keystores
- do not add secrets

If both pass:
- explicitly correct the prior assumption that missing in-repo `signingConfigs` was a blocker.

---

## Task F — Android Studio Verification Guidance

Provide a short user-facing checklist for what the user should inspect manually in Android Studio **at this gate**, without changing the project.

The checklist must include:

### SDK Manager
Path:
- `Tools → SDK Manager`

User should verify:
- Android 16 / API 36 platform installed
- Android SDK Platform 36 checked
- relevant Build Tools installed
- no need to change project SDK values yet

### SDK Upgrade Assistant
Path:
- `Tools → SDK Upgrade Assistant`

At this gate:
- user may open it
- select/inspect API 36 recommendations
- do **not** click Apply
- do **not** use Apply All
- note categories/recommendations only

### Gradle / Sync
User should:
- confirm project sync is green
- confirm no unresolved Gradle plugin warning appears
- confirm no KSP/Hilt sync error appears

### Build output
User should know exactly what successful state looks like after this gate:
- clean project
- tests green
- debug build green
- lint result known
- release build capability known
- no tracked files changed

---

## Task G — Decide Whether Gate 2B Is Safe

At the end, answer only this migration question:

> Is it safe to perform the first real project change next: `compileSdk 35 → 36`, while keeping `targetSdk = 35`?

Do not perform that change.

Classify one of:

- **READY FOR GATE 2B**
- **BLOCKED**

If blocked:
- identify the exact blocker
- identify the minimum prerequisite
- do not fix it

If ready:
- state that the next step must change only `compileSdk`
- state the exact file and line expected to change
- state which tests should run immediately afterward

---

## Required Final Report Structure

Return exactly these sections.

### 1. Branch / Working Tree
- branch
- HEAD
- clean/dirty
- files modified by this gate

### 2. API 36 SDK Installation Status
- SDK root
- API 36 installed: yes/no
- android.jar present: yes/no
- build-tools summary

### 3. Toolchain Compatibility
Table:

| Component | Version | Status | Evidence |
|---|---:|---|---|

### 4. KSP Verification
- exact configuration
- resolution/build status
- whether KSP is actually a blocker

### 5. Baseline Build / Test Results
Table with:
- command
- result
- relevant warnings/errors

### 6. Release Build Verification
- assembleRelease
- bundleRelease
- actual signing behavior
- correction of any previous assumption if applicable

### 7. Android Studio Manual Verification Checklist
Short, concrete checklist for the user.

### 8. Gate 2B Readiness
Choose:
- **READY FOR GATE 2B**
- **BLOCKED**

If ready, name the one and only next code/config change:
`compileSdk 35 → 36`

Do not implement it.

---

## Final Safety Rules

- No tracked file changes.
- No dependency upgrades.
- No SDK value changes.
- No managed-device additions.
- No documentation updates.
- No fixes.
- No commits.
- Stop after the report.
