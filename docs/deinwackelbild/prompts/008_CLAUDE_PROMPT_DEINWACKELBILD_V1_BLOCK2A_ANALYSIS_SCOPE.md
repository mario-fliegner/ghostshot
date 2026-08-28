# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 2A: ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Block 1 is complete and committed.

This prompt covers **Implementation Block 2 only** and follows the SameView strict workflow:

1. analysis only;
2. scope confirmation;
3. STOP and wait for explicit approval;
4. no implementation yet.

Do not write code.
Do not modify files.
Do not create a commit.
Do not begin Block 3.

---

# 1. Block 2 Objective

Implement the first real DeinWackelbild destination and navigation shell.

Approved Block 2 behavior:

- `CompareScreen`'s new `onCreateWackelbild` callback is wired to real navigation.
- A dedicated Wackelbild destination is created.
- The new destination receives the current saved `sessionId`.
- The screen uses the existing SameView full-screen pattern:
  - `Scaffold`
  - `TopAppBar`
  - Back action
  - title `Wackelbild erstellen`
- The initial local preview loads and displays the existing `reference.jpg`.
- No sensor behavior yet.
- No swipe behavior yet.
- No date toggle yet.
- No HQ rendering yet.
- No network.
- No `INTERNET`.
- No OkHttp.
- No API state machine.
- No Custom Tab.
- No temp-file flow.

Block 2 should establish only:

`CompareScreen → real Wackelbild route → Wackelbild screen → local Reference preview → Back`

---

# 2. Authoritative Inputs

Read fully before analyzing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Also inspect current relevant authoritative docs:

- `docs/COMPARE_FLOW_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`

Inspect the current implementation of:

- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt`
- the current session-storage/session-path APIs needed to locate `reference.jpg`
- current image-loading pattern/dependencies (e.g. Coil if already used)
- current instrumentation tests for navigation and screen shells
- current string resources.

If the repository differs materially from the approved implementation plan, report it and stop rather than silently changing architecture.

---

# 3. Repository Baseline

Before analysis, report:

- current branch;
- current HEAD;
- `git status --short`;
- any pre-existing modified/untracked files.

Do not touch unrelated working-tree state.

---

# 4. Analysis Requirements

Answer all of the following from repository evidence:

1. What exact route pattern should Block 2 follow?
2. What exact route constants/helper names are appropriate?
3. What exact `MainActivity.kt` call site should now pass `onCreateWackelbild`?
4. What exact navigation destination block should be added?
5. What exact package/path should contain `WackelbildScreen.kt`?
6. Is a `WackelbildViewModel.kt` required already in Block 2, or can the screen safely load `reference.jpg` directly?
7. If a ViewModel is required, what is the smallest state it should own in Block 2?
8. How is `sessionId` obtained today in analogous screens?
9. What exact repository API should be used to resolve the current session directory and `reference.jpg`?
10. What exact image-loading mechanism should display `reference.jpg`?
11. How should the preview preserve its image aspect ratio without introducing any additional crop?
12. What responsive shell/layout pattern should be reused?
13. What exact strings are needed now?
14. What tests are required for:
    - navigation from CompareScreen,
    - Back,
    - initial Reference preview,
    - missing/unreadable `reference.jpg`,
    - no network activity?
15. Does Block 2 require any documentation update beyond `IMPLEMENTATION_NOTES.md`, or should docs wait for a later block?
16. Is any file beyond the minimal Block-2 scope actually required?

Do not design sensor/date/network behavior yet.

---

# 5. Product Constraints That Apply Now

Preserve these approved decisions:

- Entry only from an opened saved CompareScreen.
- Dedicated real screen, not dialog/bottom sheet.
- Top app bar with Back.
- Screen title: `Wackelbild erstellen`.
- Initial visible image is always Reference.
- Local preview source: existing persisted `reference.jpg`.
- The preview must show the stored Comparison content without introducing another crop.
- Portrait Comparison stays Portrait.
- Landscape Comparison stays Landscape.
- Preview should be moderate in size, not stretched to fill every available pixel.
- Opening the screen must perform no network request.
- Existing session/original files are read-only.
- No metadata edit on this screen.

Do not add placeholder product controls that belong to later blocks.

---

# 6. Strict Block 2 Scope

Expected likely production scope:

- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- new `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- likely new `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- string resources as needed
- relevant tests

Potential documentation scope:

- `docs/IMPLEMENTATION_NOTES.md` only if project convention requires per-block implementation logging immediately.
- Do not update unrelated specs unless the implementation genuinely changes their documented contract.

Do not assume this list blindly. Confirm exact files from the repository.

---

# 7. Forbidden in Block 2

Do not include:

- sensor code;
- `TiltProvider`;
- hysteresis logic;
- swipe gesture;
- date toggle;
- date formatting;
- date badge rendering;
- HQ reconstruction;
- `ShareImageRenderer` changes;
- dimension resolver;
- temp files;
- OkHttp;
- DTOs;
- API state machine;
- partner key;
- Gradle changes;
- AndroidManifest changes;
- `INTERNET`;
- `androidx.browser`;
- Custom Tabs;
- fallback-quality warning;
- upload/loading/error state machine;
- release/privacy changes;
- unrelated refactors.

If any forbidden item appears necessary, stop and explain why instead of expanding scope.

---

# 8. Error / Missing-File Behavior for Block 2

The implementation plan's later permanent error UX applies to the full product flow, but Block 2 only establishes the local preview shell.

Analyze how the screen should behave if `reference.jpg` is unexpectedly missing/unreadable at this stage.

Do not invent a new product flow if existing SameView patterns already define how broken session assets are handled.

State whether Block 2 should:

- show a minimal local error state on the Wackelbild screen; or
- rely on the fact that a normally-opened CompareScreen already implies a valid `reference.jpg`.

This must be evidence-based and implementation-minimal.

---

# 9. Regression-Safety Review

Explicitly assess risk to:

- existing Compare navigation;
- Share Image;
- Create Video;
- CompareScreen callback behavior;
- session loading;
- app back stack;
- responsive layout;
- Compose lifecycle;
- existing tests.

Block 2 must be additive and isolated.

---

# 10. STEP 2 — Required Scope Confirmation Output

Return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- working-tree state

## 2. Current Implementation Evidence

List:

- exact existing route pattern;
- exact `MainActivity` wiring precedent;
- exact screen-shell precedent;
- exact sessionId/ViewModel precedent;
- exact session/reference-file resolution API;
- exact image-loading precedent;
- exact responsive-layout precedent;
- exact relevant test files.

Use repository-relative paths and line ranges where practical.

## 3. Root Cause / Required Change

Explain why Block 2 needs the identified changes and why nothing broader is required.

## 4. Files Proposed for Modification

Table:

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

List **ALL** files intended for Block 2 implementation.

No hidden files.

## 5. Files Explicitly NOT Touched

Confirm at minimum:

- sensor files;
- image/HQ renderer files;
- Gradle;
- AndroidManifest;
- network/API files;
- Custom Tabs;
- DeinWackelbild spec/plan unless a real conflict is found.

## 6. Exact Implementation Plan for Block 2

Describe precise edits, but **do not provide code**.

Include:

- route constants;
- route builder;
- Compare callback wiring;
- navigation destination;
- screen shell;
- ViewModel state if needed;
- reference image loading;
- Back behavior;
- responsive sizing;
- strings;
- tests;
- documentation update if required.

## 7. Risks

List only real Block-2 risks and mitigations.

## 8. Verification Planned After Implementation

State exact commands/tests to run after approval.

At minimum assess:

- `./gradlew testDebugUnitTest`
- relevant navigation/screen instrumentation tests
- relevant Managed Device task
- `./gradlew assembleDebug`

State whether real-device validation is required for Block 2.

## 9. Scope Confirmation

End with exactly:

**BLOCK 2 SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

# Final Rule

This prompt is ANALYSIS + SCOPE CONFIRMATION only.

No code.
No file modifications.
No navigation implementation yet.
No Block 3.
No unrelated cleanup.

Wait for explicit approval.
