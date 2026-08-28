# Gate 2C — Analysis Only: API 36 `announceForAccessibility()` Deprecation

## Objective

Analyze the two new API-36 compiler deprecation warnings introduced after the verified `compileSdk = 36` change.

This task is **analysis only**.

Do **not** modify any file.
Do **not** implement a fix.
Do **not** change `targetSdk`.
Do **not** commit anything.

The goal is to identify the exact root cause and the smallest safe replacement strategy that preserves SameView's existing accessibility behavior.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

The `compileSdk = 36` change has already been implemented, verified, and committed by the user.

Before analysis:

1. Confirm the active branch is exactly `upgrade/android-16-api-36`.
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. Confirm `compileSdk = 36`.
5. Confirm `targetSdk = 35`.
6. If the tree is not clean, STOP and report exact status.

Do not stash, reset, discard, modify, or commit anything.

---

## Known Finding

After the `compileSdk = 36` change, two new Kotlin compiler warnings appeared:

```text
CameraScreen.kt:2046:18
'fun announceForAccessibility(p0: CharSequence!): Unit' is deprecated. Deprecated in Java.

CameraScreen.kt:2126:18
'fun announceForAccessibility(p0: CharSequence!): Unit' is deprecated. Deprecated in Java.
```

These warnings did not appear under `compileSdk = 35`.

They are therefore directly attributable to compiling against the API 36 SDK stubs.

---

## Source-of-Truth Review — Mandatory

Before analyzing the code, read:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- `IMPLEMENTATION_NOTES.md`

Also inspect any accessibility-related tests and comments around the affected call sites.

Important:
- CameraScreen is a high-risk area.
- Accessibility behavior must not silently regress.
- No UI redesign is allowed.
- No unrelated cleanup is allowed.

---

## Required Analysis

### 1. Identify the exact call sites

Inspect both affected locations in:

`app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`

For each call site, report:

- containing composable/function
- exact event that triggers the announcement
- exact announcement text source
- whether the announcement is user-visible elsewhere
- whether it is tied to:
  - marker workflow
  - reference workflow
  - guide tips
  - snackbar behavior
  - accessibility-only feedback
  - another feature

Do not infer from line numbers alone; trace the actual surrounding logic.

---

### 2. Determine why API 36 deprecates the method

Establish what changed in API 36 regarding:

`View.announceForAccessibility(CharSequence)`

Determine the platform-recommended replacement approach.

Prefer authoritative Android/AndroidX guidance if available in the local SDK sources/docs.

If you have access to authoritative online Android documentation, you may consult it.

Do not rely on random blogs or Stack Overflow as the primary authority.

Report whether the recommended replacement is based on:

- Compose semantics
- accessibility live regions
- `AccessibilityManager`
- `AccessibilityEvent`
- a newer View API
- another AndroidX API

---

### 3. Evaluate Compose-native alternatives

Because SameView uses Jetpack Compose, determine whether these announcements should be replaced with a Compose-native accessibility mechanism.

Inspect whether the affected UI already uses:

- `semantics`
- `liveRegion`
- `contentDescription`
- state-driven text
- invisible accessibility nodes
- Snackbar semantics
- other accessibility patterns

For each viable replacement, analyze:

- behavior parity
- timing
- whether repeated announcements still fire
- whether the announcement is tied to visible state
- whether it survives recomposition correctly
- whether it risks duplicate TalkBack output
- whether it requires a new composable/state holder
- whether it changes UI layout
- whether it affects tests

---

### 4. Preserve current behavior exactly

The chosen strategy must preserve the existing user-facing contract:

- same trigger
- same announcement text
- same approximate timing
- no duplicate announcements
- no announcement loss
- no unrelated layout change
- no new permission
- no navigation change
- no camera lifecycle change
- no compare behavior change

If exact parity is not possible, say so explicitly.

---

### 5. Minimal-fix comparison

Compare at least the realistic options.

For example, if applicable:

- keep deprecated call temporarily
- replace with Compose `liveRegion`
- dispatch an `AccessibilityEvent`
- use another supported Android/AndroidX accessibility API

For each option, classify:

- technically valid
- recommended
- not recommended
- unsafe / unnecessary

Explain why.

Do not choose based on "modernity" alone.
Choose the smallest safe change.

---

### 6. Test impact

Identify existing tests that currently cover these call sites or accessibility behavior.

Determine whether the eventual fix would require:

- unit test changes
- Compose UI test changes
- instrumentation tests
- TalkBack/manual accessibility validation
- no automated test change

Do not implement tests.

List exact test file paths if they exist.

---

### 7. Scope for a future fix

If a minimal fix is clear, list all files that would need modification.

Expected outcome should ideally be very small.

If more than one production file is required, explain exactly why.

Do not modify them.

---

## Required Final Report Structure

Return exactly these sections:

### 1. Branch / Baseline State
- branch
- HEAD
- working tree
- compileSdk
- targetSdk

### 2. Affected Call Sites

Table:

| Call site | Trigger | Current behavior | Text source |
|---|---|---|---|

### 3. Root Cause

Explain why API 36 surfaces the deprecation.

### 4. Replacement Options

Table:

| Option | Behavior parity | Risk | Recommendation |
|---|---|---|---|

### 5. Minimal Fix Strategy

State the smallest safe strategy.

No code.

### 6. Files That Would Change

List every production/test/doc file that would need modification.

### 7. Verification Plan

State:
- automated tests required
- manual Android Studio checks
- real-device/TalkBack validation requirement

### 8. Documentation Impact

State whether any source-of-truth docs need updating.

### 9. Gate Verdict

Choose one:

- **READY FOR SCOPE CONFIRMATION**
- **BLOCKED**

If ready, summarize the exact future fix in one sentence.

Then STOP.

---

## Final Safety Rules

- Analysis only.
- No code changes.
- No test changes.
- No docs changes.
- No dependency changes.
- No `targetSdk` change.
- No commit.
- No push.
- Stop after the report.
