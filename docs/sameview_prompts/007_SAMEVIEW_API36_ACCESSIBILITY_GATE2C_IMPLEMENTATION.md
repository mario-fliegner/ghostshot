# Gate 2C — Implementation: API 36 Accessibility Announcement Replacement

## Objective

Implement exactly the approved Gate 2C accessibility fix for the two API-36-deprecated `View.announceForAccessibility()` calls in `CameraScreen.kt`, including the approved targeted instrumentation coverage.

Do not perform any other migration work.

## Repository / branch

Repository: `C:\data\work\privat\git-repos\sameview`

Required branch: `upgrade/android-16-api-36`

Before editing:
1. Confirm the exact branch.
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. Confirm `compileSdk = 36`, `targetSdk = 35`, `minSdk = 29`.
5. If anything differs unexpectedly, STOP and report it.
6. Do not stash/reset/discard unrelated work.

## Source of truth

Re-read the relevant constraints in:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

The approved Gate 2C scope confirmation is authoritative for this implementation step.

## Approved files

Exactly two files may change:

1. `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
2. `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraControlsOverlayTest.kt`

If another file becomes necessary, STOP. Do not expand scope.

## Production implementation

For both private composables:
- `OverlayVisibilityWarning`
- `FormatMismatchHint`

perform only the approved replacement:

- remove the local `LocalView.current` reference used solely for `announceForAccessibility`
- remove `view.announceForAccessibility(bubbleText)`
- preserve the existing `LaunchedEffect(hintRequest)` visibility logic and `delay(1800)` unchanged
- preserve the existing outer `AnimatedVisibility`, its existing test tag, fade behavior, modifiers, layout and timing unchanged
- wrap only the existing inner bubble-content `Box` in `key(hintRequest) { ... }`
- add a dedicated inner test tag for each bubble content node
- add `.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }` to that inner bubble-content node
- add only the imports required by this implementation
- reuse the existing `key` import if already present
- remove `LocalView` import only if it becomes unused repo/compiler-wise in this file

Do not alter strings, visual styling, dimensions, animation, click handling, camera behavior, navigation, permissions or other semantics.

## Test implementation

Modify only `CameraControlsOverlayTest.kt`.

Add exactly two focused instrumentation tests following the file's existing test style:

1. Overlay visibility warning bubble:
   - render the condition
   - tap the existing warning icon
   - locate the new inner bubble-content test tag using the appropriate semantics tree
   - assert `SemanticsProperties.LiveRegion == LiveRegionMode.Polite`

2. Format mismatch bubble:
   - same pattern
   - assert `LiveRegionMode.Polite`

Use the existing helpers/setup patterns where already available. Do not refactor the test suite or unrelated tests.

Do not pretend these tests prove TalkBack repeat-announcement behavior. They prove only that the intended live-region semantics is present.

## Forbidden

Do not:
- change `compileSdk`, `targetSdk`, or `minSdk`
- update dependencies
- change AGP/Gradle/Kotlin/KSP/Hilt/CameraX/Compose versions
- change manifests or permissions
- change documentation
- fix unrelated warnings
- refactor unrelated code
- reformat unrelated code
- add managed devices
- commit, push, or merge

## Required verification

After implementation, first inspect the diff and confirm only the two approved files changed.

Then run:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. the narrowest available instrumentation/managed-device command that executes `CameraControlsOverlayTest`
5. `./gradlew lintDebug`

If the repository's configured instrumentation task cannot target that class directly, use the smallest existing applicable instrumentation task and state exactly what ran.

Do not suppress failures or alter unrelated code to make tests pass.

Known baseline before this fix:
- unit tests: 828/828 passed
- assembleDebug: PASS
- lintDebug: 0 errors, 114 warnings, 6 hints
- two new Kotlin compiler warnings existed specifically for `announceForAccessibility()` in `CameraScreen.kt`

Success requires those two deprecation warnings to disappear without introducing replacement warnings/errors.

## Manual validation remains required

Automated verification does not complete this fix.

After Claude's implementation report, the user must still validate on a real device with TalkBack:

For each of the two warning/info bubbles:
1. Trigger the relevant warning state.
2. Enable TalkBack.
3. Tap the icon once.
4. Confirm the complete existing text is announced once.
5. Tap again quickly while the bubble remains visible.
6. Confirm the same text is announced again.
7. Confirm one tap never causes a duplicate/double announcement.
8. Confirm the bubble's visual behavior/timing remains unchanged and camera controls remain usable.

Claude must explicitly mark this real-device validation as **NOT RUN / STILL REQUIRED**.

## Post-verification git check

Run:
- `git status --short`
- `git diff -- app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraControlsOverlayTest.kt`

Confirm no other tracked file changed.

Do not commit.

## Required final report

### 1. Modified Files
List exactly what changed.

### 2. Exact Production Implementation
Describe both call-site changes and confirm outer `AnimatedVisibility`/1800ms behavior stayed untouched.

### 3. Exact Test Implementation
Name both added tests and what they prove.

### 4. Verification Results
Table with each command, result, and relevant counts.

### 5. Warning/Lint Delta
Compare against baseline and explicitly state whether both `announceForAccessibility` warnings disappeared and whether anything new appeared.

### 6. Final Git State
Branch, HEAD, modified files, no commit.

### 7. Real-Device Validation
State explicitly: **NOT RUN — STILL REQUIRED**, followed by the TalkBack checks.

### 8. Gate 2C Verdict
Choose:
- **IMPLEMENTATION VERIFIED AUTOMATICALLY — MANUAL TALKBACK VALIDATION REQUIRED**
- **GATE 2C FAILED**

Do not call Gate 2C fully complete until the user reports successful real-device TalkBack validation.

Stop. Do not proceed to `targetSdk = 36`.
