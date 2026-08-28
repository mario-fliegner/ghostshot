# Gate 3B — Analysis Only: API 36 Gradle Managed Device

## Objective
Analyze the exact minimal way to add automated Android 16 / API 36 instrumentation coverage to SameView using a Gradle Managed Device. **Analysis only.** Do not modify files, add a device, install SDK components, run the full suite, update docs, or commit.

Repository: `C:\data\work\privat\git-repos\sameview`
Required branch: `upgrade/android-16-api-36`

## 1. Baseline
Confirm:
- branch exactly `upgrade/android-16-api-36`
- clean working tree
- current HEAD/latest commit
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- Gate 3A is committed

If anything differs, STOP. Do not stash/reset/discard.

## 2. Source of truth
Read at minimum:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- current `app/build.gradle.kts`
- relevant Gradle/version configuration
- any docs defining managed devices, instrumentation expectations, supported API levels or emulator/ABI constraints

Do not update documentation.

Treat Gate 3A as complete: 828/828 unit tests, 930/930 API-35 instrumentation tests, build/release/bundle/lint green, and real-device Android-16 targetSdk-36 verification passed. Do not reopen those fixes.

## 3. Existing Managed Devices
Inspect `testOptions.managedDevices` and report every existing device:
- Gradle name
- hardware model
- API level
- `systemImageSource`
- explicit ABI/tested ABI if any
- group membership
- other relevant configuration

Identify the exact repository pattern the new API-36 device should follow.

## 4. Determine the actual local API-36 images
This is critical. Use read-only inspection only.

Determine:
- Android SDK root used by this project/Android Studio
- installed platforms around API 36
- installed system images around API 36
- available (but not installed) relevant system images
- whether the SDK exposes base API 36, API 36.1/QPR1, or both
- image sources (`aosp`, `google_apis`, Play Store etc.)
- available ABIs (`x86`, `x86_64`, `arm64-v8a`, etc.)

You may use read-only commands such as `sdkmanager --list_installed`, `sdkmanager --list`, SDK directory inspection and Gradle task/model inspection.

**Do not install or uninstall anything.**

Gate 2B proved only that `compileSdk = 36` resolves against the installed `android-36.1` platform. Do not infer the managed-device configuration from that. Determine it empirically.

## 5. AGP 9.1.1 / API 36 compatibility
The project uses AGP 9.1.1. Determine from local Gradle/SDK evidence:
1. exact `apiLevel`
2. whether `apiPreview`, extension/vendor API or another property is required
3. exact `systemImageSource`
4. whether 36.1/QPR1 needs handling different from base 36
5. whether the existing Pixel 2 profile is valid
6. whether explicit ABI/tested ABI is required

Do not guess. If not conclusively knowable, classify it as a small implementation experiment.

## 6. ABI / NDK translation
Earlier API-29 managed-device runs warned that unspecified `testedAbi` defaults to x86 today but will change to arm64-v8a in AGP 10, and that the image did not support NDK translation.

Analyze:
- host architecture
- API-36 image ABIs
- whether SameView/native dependencies make ABI relevant
- whether the new device should explicitly set `testedAbi`
- whether doing so only for the new device is appropriate
- whether this is Gate-3B-relevant or should remain out of scope

Do not modify old devices merely to clean warnings.

## 7. Minimal Gate 3B scope
Identify the smallest repository change.

Preferred expectation:
- only `app/build.gradle.kts`
- one new API-36 managed-device definition
- add it to an existing group only if genuinely appropriate

Do not combine refactors, old-device cleanup, dependency upgrades, tests, source code, docs or SDK-level changes.

If group membership would unexpectedly enlarge normal test execution, recommend initially keeping it standalone.

## 8. Staged verification
Define the safest staged sequence:
1. Gradle configuration/task discovery
2. smallest useful API-36 provisioning/test
3. narrow high-risk instrumentation class first
4. only then full API-36 instrumentation task
5. compare with API-35 results

Determine exact task names if possible.

Do not assume the API-36 count must equal 930. Explain whether API-conditioned tests/skips could legitimately alter it.

## 9. Existing high-risk API-36 tests
Find exact existing test class names covering, where present:
- CameraScreen / CameraControlsOverlay
- camera top-right/insets
- Compare fullscreen/back
- Compare Library selection/back
- Edit Session back/dialog
- Create Video back/rendering
- Settings permission/rationale
- Walkthrough
- Photo Picker / MediaStore / original-location behavior
- responsive/insets
- accessibility live regions

Do not add tests. If a genuine blocking coverage hole exists, report it as a separate future gate.

## 10. Android Studio SDK Manager check
Based on actual local evidence, tell the user exactly what to inspect in:
`Android Studio → Tools → SDK Manager`

State:
- which Android 16 platform/image row should exist
- whether another image download is required
- whether `Show Package Details` is needed
- expected API/revision/source/ABI wording as far as evidence supports it

Do not tell the user to install anything unless analysis proves it is required.

## 11. Risk classification
For each, classify as **NO ISSUE**, **VERIFICATION REQUIRED**, **PREREQUISITE REQUIRED**, or **BLOCKER**:
- API 36 vs 36.1 image selection
- AGP 9.1.1 syntax
- Pixel 2 profile
- ABI
- provisioning
- test-count differences
- disk/time cost
- CI/local workflow implications

## 12. Required decision
Choose exactly one:
- **PATH A — READY TO ADD API-36 MANAGED DEVICE**
- **PATH B — INSTALL SDK IMAGE FIRST**
- **PATH C — SMALL CONFIGURATION EXPERIMENT REQUIRED**
- **PATH D — BLOCKED**

## Required final report
Return exactly:

### 1. Branch / Baseline State
### 2. Existing Managed Devices
### 3. Local API 36 SDK / System Image Evidence
### 4. Correct Proposed API-36 Device Configuration
Include device name, model, apiLevel, systemImageSource, ABI/testedAbi decision, group decision.
### 5. AGP 9.1.1 / API 36.1 Compatibility Conclusion
### 6. ABI Analysis
### 7. Minimal Gate 3B File Scope
### 8. Staged Verification Plan
### 9. API-36 High-Risk Test Coverage
### 10. Android Studio SDK Manager Check
### 11. Risks / Prerequisites
### 12. Recommended Path
### 13. Gate 3B Verdict
Choose **READY FOR SCOPE CONFIRMATION** or **BLOCKED**. If ready, state only the exact next step.

Then STOP.

## Final safety rules
- Analysis only.
- No tracked changes.
- No SDK installation/uninstallation.
- No managed-device addition.
- No source/test/docs/dependency changes.
- No SDK-level changes.
- No commit/push.
