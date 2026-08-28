# Gate 3B — Implementation: Add API 36 Gradle Managed Device

## Objective

Implement exactly one test-infrastructure change for SameView:

- add a standalone `pixel2Api36` Gradle Managed Device
- keep all existing devices unchanged
- do not alter app/runtime behavior

This gate exists only to add automated Android 16 / API 36 instrumentation coverage.

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
   - `targetSdk = 36`
   - `minSdk = 29`
5. Confirm Gate 3A commit is present.
6. If anything differs unexpectedly, STOP and report exact state.
7. Do not stash, reset, discard, overwrite, merge, or reformat unrelated work.

---

## Source of Truth

Re-check at minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- `app/build.gradle.kts`

Use the completed Gate 3B analysis and Gate 3B scope-confirmation report as the approved basis.

Do not update documentation in this gate.

---

## Approved Scope

### File allowed to change

Exactly one file:

`app/build.gradle.kts`

### Exact approved addition

Inside the existing:

`testOptions.managedDevices.localDevices`

add exactly this device block:

```kotlin
create("pixel2Api36") {
    device = "Pixel 2"
    sdkVersion = 36
    systemImageSource = "aosp"
    testedAbi = "x86_64"
}
```

### Placement

Follow the existing ordering convention.

Preferred placement:

- after `pixel2Api35`
- before the `groups` block

### Group membership

Do **not** add `pixel2Api36` to:

`allPixel2Devices`

The new device must remain standalone in this gate.

---

## Explicitly Unchanged

Do not change:

- `pixel2Api29`
- `pixel2Api33`
- `pixel2Api35`
- `allPixel2Devices`
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- versionCode
- versionName
- dependencies
- plugins
- source code
- tests
- manifests
- permissions
- resources
- documentation
- CI
- signing configuration
- existing old-device ABI warnings

Do not:
- refactor the managed-device block
- add comments unless required for syntax
- change formatting outside the inserted block
- fix unrelated warnings
- add API 36.1
- add a Google APIs / Play Store image
- install/uninstall SDK components manually
- commit, push, or merge

---

## Implementation Diff Check

After editing, run:

`git diff -- app/build.gradle.kts`

The diff must show only the new `pixel2Api36` block.

If any unrelated diff appears, STOP and remove only your own unintended changes before continuing.

---

# Required Verification

## Step 1 — Task discovery

Run:

`./gradlew help --task pixel2Api36DebugAndroidTest`

Expected:
- task resolves successfully
- Gradle recognizes the new managed device
- no DSL/configuration error from `testedAbi`

If this fails, STOP and report the exact error.

---

## Step 2 — Minimal provisioning smoke

Run:

`./gradlew pixel2Api36DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ExampleInstrumentedTest`

This is expected to:
- auto-download `system-images;android-36;default;x86_64` if still missing
- create/provision the managed device
- boot it
- install the debug/test APKs
- run only `ExampleInstrumentedTest`

Report:
- whether image download occurred
- whether license/provisioning succeeded
- whether Pixel 2 API 36 booted
- pass/fail count
- any emulator/ABI warnings

If provisioning fails, STOP. Do not manually work around it in this prompt.

---

## Step 3 — Narrow high-risk API-36 class

If Step 2 passes, run:

`./gradlew pixel2Api36DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraControlsOverlayTest`

Known reference:
- API 35 / prior Gate 2C count: 61 tests

Report actual API-36 count and result.

A count difference must be explained; do not assume it is benign.

Success requires:
- no failures
- both live-region semantics tests pass
- no camera-overlay regression

---

## Step 4 — Full API-36 instrumentation suite

If Step 3 passes, run:

`./gradlew pixel2Api36DebugAndroidTest`

API-35 reference baseline:

- 930/930 passed

For API 36:

- do not require exactly 930 blindly
- if count differs, identify the exact missing/skipped/additional tests and why
- any failure must be reported, not fixed in this gate

Pay particular attention to these existing high-risk classes:

- `CameraControlsOverlayTest`
- `CameraTopRightNavigationTest`
- `CompareScreenTest`
- `CompareLibraryScreenTest`
- `CompareLibraryNavigationTest`
- `EditSessionScreenTest`
- `CreateVideoScreenTest`
- `SettingsScreenTest`
- `WalkthroughScreenTest`
- `WalkthroughNavigationTest`
- `FirstRunWalkthroughGateTest`
- `ResolveSourceUriTest`
- `ReferenceImageMetadataReaderTest`
- `MediaStoreWriterGpsTest`
- `SessionStorageGpsTest`

Do not add or change tests in this gate.

---

## Step 5 — Final Git Check

Run:

- `git status --short`
- `git diff -- app/build.gradle.kts`

Confirm:
- only `app/build.gradle.kts` is modified
- the only diff is the new `pixel2Api36` block
- no generated/system-image/build artifact is tracked
- no commit was made

---

# Failure Handling

If any verification step fails:

- do not fix it
- do not change the device config unless the failure proves the approved configuration itself is invalid
- do not touch tests/source/dependencies
- report the exact failure
- stop

A failing API-36 test is a new finding and must become a separate analysis/fix gate.

---

# Manual Verification

No new real-device manual test is required in Gate 3B if the managed-device suite passes, because Gate 3A already completed real Android 16 validation on the S23 with `targetSdk = 36`.

Do not repeat the full manual predictive-back checklist unless a Gate-3B result contradicts Gate 3A.

---

# Required Final Report

Return exactly these sections.

## 1. Modified Files

Expected:
- `app/build.gradle.kts`

## 2. Exact Implementation

Show the inserted device block and confirm:
- standalone
- not in `allPixel2Devices`
- existing devices unchanged

## 3. Task Discovery Result

Report:
- command
- pass/fail
- whether `testedAbi` was accepted

## 4. Provisioning Result

Report:
- whether the API 36 x86_64 system image was downloaded
- whether provisioning/boot succeeded
- whether license acceptance blocked anything
- `ExampleInstrumentedTest` result

## 5. Narrow API-36 Test Result

Report:
- `CameraControlsOverlayTest`
- actual test count
- pass/fail
- live-region test status

## 6. Full API-36 Instrumentation Result

Report:
- total tests
- passed/skipped/failed/errors
- comparison against API-35 930/930
- explanation for any count difference

## 7. High-Risk Coverage Summary

Summarize pass/fail for the listed high-risk classes.

## 8. ABI / Emulator Warnings

Report any warning related to:
- `testedAbi`
- NDK translation
- emulator architecture
- system image
- Pixel 2 profile

## 9. Final Git State

Report:
- branch
- HEAD
- modified files
- exact diff scope
- no commit

## 10. Gate 3B Verdict

Choose exactly one:

- **GATE 3B PASSED**
- **GATE 3B FAILED**

If passed:
- state that automated API-36 instrumentation coverage is now established
- stop

Do not:
- commit
- add the device to `allPixel2Devices`
- update docs
- merge to `main`
- start the next migration gate

Stop.
