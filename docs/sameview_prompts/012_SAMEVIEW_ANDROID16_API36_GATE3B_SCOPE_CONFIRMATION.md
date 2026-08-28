# Gate 3B — Scope Confirmation: Add API 36 Gradle Managed Device

## Objective
Confirm the exact scope for adding one standalone Android 16 / API 36 Gradle Managed Device to SameView.

**Scope confirmation only. Do not modify files.**

## Repository / baseline
Repository: `C:\data\work\privat\git-repos\sameview`
Required branch: `upgrade/android-16-api-36`

Confirm:
- branch exact
- working tree clean
- current HEAD
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- Gate 3A commit present

If anything differs, STOP.

## Source of truth
Re-check:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- `app/build.gradle.kts`

Use the completed Gate 3B analysis as the approved basis. Do not update docs.

## Analysis conclusion to validate
The analysis found:
- plain API 36 has the required `default`/AOSP system image
- API 36.1 does not provide the repo's `aosp`/`default` pattern
- host is x86_64
- `system-images;android-36;default;x86_64` is available but not currently installed
- AGP 9.1.1 supports the existing managed-device DSL pattern
- Pixel 2 is the existing repository hardware profile
- the new device should initially remain standalone
- only `app/build.gradle.kts` needs modification

Confirm these facts still hold.

## Exact proposed change
Exactly one file may change later:

`app/build.gradle.kts`

Add one block inside the existing `testOptions.managedDevices.localDevices` section:

```kotlin
create("pixel2Api36") {
    device = "Pixel 2"
    sdkVersion = 36
    systemImageSource = "aosp"
    testedAbi = "x86_64"
}
```

Do not add it to `allPixel2Devices`.

### Critical DSL verification
Before approving implementation, verify directly against the AGP 9.1.1 DSL/model that `testedAbi = "x86_64"` is a valid property on this exact managed-device type.

Do not rely only on recollection or the earlier warning text.

If `testedAbi` is not valid in this exact block, classify as BLOCKED and report the correct property/location rather than approving invalid Gradle syntax.

## Explicitly unchanged
Confirm no changes to:
- existing `pixel2Api29`, `pixel2Api33`, `pixel2Api35`
- `allPixel2Devices`
- compileSdk/targetSdk/minSdk
- versionCode/versionName
- dependencies/plugins
- source code
- tests
- manifests/permissions
- resources
- docs
- CI
- old ABI warnings

## System-image provisioning
Confirm whether Gradle Managed Devices will automatically obtain the missing `system-images;android-36;default;x86_64` package on first execution in this local environment.

If Android SDK license acceptance or another manual prerequisite could block auto-provisioning, state it explicitly.

Do not install anything during scope confirmation.

## Staged verification after implementation
Confirm exact commands, in this order:

1. Task/config discovery:
   `./gradlew help --task pixel2Api36DebugAndroidTest`

2. Small provisioning smoke:
   `./gradlew pixel2Api36DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ExampleInstrumentedTest`

3. High-risk camera/accessibility class:
   `./gradlew pixel2Api36DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraControlsOverlayTest`

4. Full API-36 suite:
   `./gradlew pixel2Api36DebugAndroidTest`

5. Final diff/state:
   `git status --short`
   `git diff -- app/build.gradle.kts`

Before approving scope, verify that `ExampleInstrumentedTest` actually exists under the stated fully qualified class name. If not, choose the smallest existing trivial instrumentation class and name it exactly.

Also confirm the exact expected count for `CameraControlsOverlayTest` from current HEAD if readily determinable, but do not require a hard-coded count for success if SDK-conditional behavior can legitimately differ.

For the full suite:
- compare against API 35's 930/930 baseline
- do not automatically treat a different test count as success
- if count differs, identify exactly which tests were skipped/not discovered/conditioned and explain why
- any failure is reported, not fixed in this gate

## Risks
Confirm only Gate-3B-relevant risks:
- first-time image download / disk/time
- Pixel 2 + API 36 provisioning
- x86_64 ABI/native transitive libraries
- API-conditioned test differences
- local SDK license/provisioning state

No unrelated cleanup.

## Documentation
No docs changed in Gate 3B. Confirm SDK documentation synchronization remains deferred until the complete API-36 migration is finished.

## Required final report

### 1. Branch / Baseline
### 2. File to Modify
### 3. Exact Device Block
### 4. `testedAbi` DSL Verification
### 5. Explicitly Unchanged
### 6. Provisioning / SDK Prerequisites
### 7. Exact Verification Commands
### 8. Risks
### 9. Documentation Impact
### 10. Scope Verdict

Verdict:
- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **BLOCKED**

Then STOP.

## Final rules
- No file modifications.
- No SDK installation.
- No Gradle managed-device addition.
- No tests run that provision/download an image.
- No docs/dependencies/source changes.
- No commit/push.
