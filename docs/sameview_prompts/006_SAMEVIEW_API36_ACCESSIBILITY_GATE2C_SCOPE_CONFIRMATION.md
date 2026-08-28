# Gate 2C — Scope Confirmation: Replace API 36 Accessibility Announcements

## Objective

Confirm the exact scope for replacing the two API-36-deprecated `View.announceForAccessibility()` calls in `CameraScreen.kt`.

This prompt is **scope confirmation only**.

Do **not** modify any file yet.
Do **not** implement the accessibility change yet.
Do **not** change `targetSdk`.
Do **not** commit anything.

The goal is to lock down:
- the exact production-file changes
- the exact repeat-tap behavior strategy
- the exact automated test coverage
- the manual TalkBack verification

before implementation.

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
4. Confirm:
   - `compileSdk = 36`
   - `targetSdk = 35`
5. If the tree is not clean, STOP and report exact state.
6. Do not stash, reset, discard, modify, or commit anything.

---

## Source-of-Truth Review

Read at minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also use the completed Gate 2C analysis report as context.

Important:
- CameraScreen is high risk.
- Accessibility behavior must not silently regress.
- No visual/layout change is allowed.
- No unrelated cleanup is allowed.

---

## Known Approved Direction

The analysis concluded that the preferred replacement is Compose-native accessibility semantics:

`Modifier.semantics { liveRegion = LiveRegionMode.Polite }`

instead of:

`view.announceForAccessibility(bubbleText)`

for these two call sites:

- `OverlayVisibilityWarning`
- `FormatMismatchHint`

Both are inside:

`app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`

---

## Critical Repeat-Tap Requirement

The current behavior re-announces on every tap, including a second tap while the bubble is already visible.

A naive `liveRegion = Polite` replacement may not re-announce identical text when the semantics node remains mounted.

This behavior must **not** be silently lost.

Before implementation, determine and confirm the exact minimal strategy that will preserve repeat-tap announcement behavior.

### You must compare at least:

1. `key(hintRequest) { ... }` around the live-region bubble semantics node
2. any alternative Compose-native approach that can force a semantics-node refresh/re-announcement without changing layout or user-visible behavior

Do not propose imperative `AccessibilityEvent` dispatch unless Compose-native parity is impossible.

### Required conclusion

Choose exactly one concrete implementation strategy.

For the chosen strategy, explain:

- why it should re-trigger the live-region announcement on a second tap
- whether it changes composition identity only
- whether it changes layout
- whether it changes visual timing
- whether it changes text
- whether it risks duplicate announcements
- whether it preserves the existing 1800 ms bubble visibility timing

If exact repeat-tap parity cannot be guaranteed by code inspection alone, say so explicitly and make real-device TalkBack verification mandatory.

---

## Expected Production Scope

The preferred scope is one production file only:

`app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`

Expected changes may include only:

- remove both `LocalView.current` references if they become unused
- remove both `view.announceForAccessibility(bubbleText)` calls
- add the necessary Compose semantics imports
- add `liveRegion = LiveRegionMode.Polite` to the bubble semantics node
- add the minimal composition-identity mechanism required for repeat-tap behavior, if needed

No other production file should change unless you can prove it is required.

If any second production file appears necessary, STOP and explain why.

---

## Required Automated Test Scope

Unlike the prior analysis, the accessibility semantics change must receive automated coverage.

Inspect:

`app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraControlsOverlayTest.kt`

and any directly relevant camera/accessibility test file.

The future implementation must include a targeted test that verifies the bubble semantics node is configured as:

`LiveRegionMode.Polite`

for both:
- Overlay visibility warning bubble
- Format mismatch bubble

Determine whether this can be covered in one parameterized/helper-based test or two explicit tests without unnecessary refactoring.

Do not change unrelated assertions.

The tests must not:
- depend on actual TalkBack
- require accessibility service activation
- rewrite existing test architecture
- alter production behavior for testability

### Repeat-tap automated coverage

Determine whether the Compose semantics tree can meaningfully prove the repeat-tap re-announcement mechanism.

If not, state that this exact behavioral parity remains manual-only.

Do not invent a fake automated guarantee.

---

## Manual TalkBack Verification — Mandatory

The future implementation must require real-device validation with TalkBack enabled.

The scope confirmation must define the exact manual checks.

At minimum:

### Overlay visibility warning
1. Make the warning visible.
2. Enable TalkBack.
3. Tap the warning icon.
4. Confirm the full existing bubble text is announced once.
5. Tap the warning icon again quickly while the bubble remains visible.
6. Confirm the same text is announced again.
7. Confirm no duplicate double-announcement occurs for a single tap.

### Format mismatch hint
Repeat the same sequence.

Also verify:
- bubble remains visible for approximately the existing 1800 ms behavior
- no visual/layout shift
- camera controls remain usable
- no extra snackbar or navigation side effect

---

## Required Verification Commands for Future Implementation

After implementation, run:

1. `./gradlew clean`
2. `./gradlew assembleDebug`
3. `./gradlew testDebugUnitTest`
4. targeted instrumentation test task covering `CameraControlsOverlayTest`
5. `./gradlew lintDebug`

Then compare against the known current baseline.

Do not suppress warnings or failing tests.

The two `announceForAccessibility` compiler warnings should disappear if the fix is correct.

Any new warning must be reported.

---

## Documentation Impact

Confirm whether any source-of-truth document must change.

Expected:
- no mandatory behavioral doc update if the user-visible behavior remains identical
- optional documentation hygiene only

Do not update docs in this scope-confirmation prompt.

---

## Required Final Output

Return exactly these sections.

### 1. Branch State
- branch
- HEAD
- working tree
- compileSdk
- targetSdk

### 2. Files to Modify
List every production/test file that would change.

### 3. Exact Production Change
Describe the exact changes to both call sites.

### 4. Repeat-Tap Strategy
State the one chosen implementation strategy and justify it.

### 5. Automated Test Scope
List exact test files and exact assertions to add.

### 6. Manual TalkBack Validation
Give the exact real-device checklist.

### 7. Risks
Only risks relevant to the accessibility mechanism replacement.

### 8. Documentation Impact
State whether docs need updating.

### 9. Scope Verdict

Choose one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **BLOCKED**

If confirmed, STOP.

Do not implement until the user explicitly approves.

---

## Final Safety Rules

- No file modifications.
- No code changes.
- No test changes.
- No docs.
- No dependency changes.
- No `targetSdk` change.
- No commit.
- No push.
- Stop after scope confirmation.
