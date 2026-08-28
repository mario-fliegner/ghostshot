# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 1A: ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

The DeinWackelbild V1 product specification and corrected implementation plan are already approved as the basis for implementation:

- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`

This prompt covers **Implementation Block 1 only** and follows the SameView strict workflow:

1. analysis only;
2. scope confirmation;
3. STOP and wait for explicit approval;
4. no implementation yet.

Do not write code.
Do not modify files.
Do not create a commit.
Do not begin Block 2.

---

# 1. Block 1 Objective

Implement only the CompareScreen entry point for DeinWackelbild.

The approved behavior for Block 1 is:

- existing CompareScreen Share/Export menu remains the entry point;
- preserve existing menu entries and their behavior;
- add a divider after the existing second item;
- add a third menu item:
  - German: `Wackelbild erstellen`
  - English value from current approved localization plan: `Create lenticular print`
- add a callback surface from `CompareScreen` for this menu action;
- do not add the real navigation destination yet;
- do not add `WackelbildScreen`;
- do not add a stub screen;
- do not add a temporary feature flag;
- do not add network code;
- do not add `INTERNET`;
- do not add OkHttp;
- do not add Custom Tabs;
- do not add sensor/image/date logic.

This block is intentionally tiny.

---

# 2. Authoritative Inputs

Read fully before analyzing:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Also inspect the current authoritative Compare flow documentation:

- `docs/COMPARE_FLOW_V1.md`

Then inspect the actual current implementation of:

- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- the current caller(s) of `CompareScreen`
- current relevant string resources
- current CompareScreen tests covering the existing Share/Export dropdown.

Do not rely on the implementation plan alone if the repository differs.

If code and authoritative MD conflict, report the conflict explicitly and do not silently implement against the MD contract.

---

# 3. Repository Baseline

Before analysis, report:

- current branch;
- current HEAD;
- `git status --short`;
- any pre-existing modified or untracked files.

Do not touch unrelated working-tree content.

---

# 4. Analysis Requirements

Analyze the exact current Share/Export menu implementation and answer:

1. What is the exact current `CompareScreen` callback/signature structure for Share Image / Create Video?
2. What is the exact current visible wording/order of the two existing menu items?
3. What is the smallest additive callback change required for `Wackelbild erstellen`?
4. How can the caller compile in Block 1 without introducing:
   - navigation,
   - a stub screen,
   - a fake route,
   - a temporary feature flag,
   - throwaway code?
5. Which exact string-resource files require new entries?
6. Which exact test file(s) currently cover the Share/Export dropdown?
7. What exact new tests should Block 1 add?
8. Is a `HorizontalDivider()` already available/imported in this file, or does only an import need adding?
9. Does `COMPARE_FLOW_V1.md` need to be updated in this same block according to the approved implementation plan?
10. Is any file beyond the expected small Block-1 scope actually required?

Do not propose unrelated cleanup or refactoring.

---

# 5. Strict Block 1 Scope

Expected implementation intent from the approved plan:

## Production

Likely modify:

- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- English string resources
- German string resources

Potentially modify the existing direct caller only if necessary to keep the new callback compile-safe without navigation.

The plan explicitly states that **MainActivity navigation belongs to Block 2**, so do not move real route/navigation work into Block 1.

## Tests

Add only the minimal tests required to prove:

- new menu item appears in the correct order after the divider;
- existing Share Image behavior remains unchanged;
- existing Create Video behavior remains unchanged;
- new callback is invoked when the new item is tapped;
- menu closes correctly after the tap if that is the current established behavior;
- new item is available under the same stored-session condition as the existing export actions.

Do not rewrite or weaken existing tests.

## Documentation

Per the approved implementation plan, assess whether `docs/COMPARE_FLOW_V1.md` should be updated in Block 1 to reflect the third item + divider.

If yes, include it in scope.
If no, explain why not.

Do not modify `IMPLEMENTATION_NOTES.md` in this analysis/scope gate.

---

# 6. Forbidden in Block 1

Do not include any of the following in scope:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- route constants
- `MainActivity` navigation destination
- sensor code
- swipe code
- date overlay code
- HQ image code
- temp-file code
- OkHttp
- network DTOs
- API state machine
- API key
- `BuildConfig` secret wiring
- `INTERNET`
- AndroidManifest changes
- `androidx.browser`
- Custom Tabs
- fallback-quality logic
- lifecycle/browser-return logic
- release/privacy changes
- unrelated formatting/refactoring.

If any of these appear necessary for Block 1, stop and explain why rather than expanding scope.

---

# 7. Regression-Safety Review

Explicitly assess risk to:

- existing Share Image menu action;
- existing Create Video menu action;
- menu open/close behavior;
- CompareScreen top-bar layout;
- stored-session gating;
- existing Compose tests.

This block should be low risk and additive only.

If your proposed scope would require changing existing behavior, treat that as a blocker and report it.

---

# 8. STEP 2 — Required Scope Confirmation Output

After analysis, return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- working-tree state

## 2. Current Implementation Evidence

List:

- exact current menu implementation location;
- exact current callbacks;
- exact current visible menu strings;
- exact current test coverage;
- exact current caller relevant to the callback.

Use repository-relative paths and line ranges where practical.

## 3. Root Cause / Required Change

Explain why Block 1 requires the identified changes and why nothing broader is needed.

## 4. Files Proposed for Modification

Table:

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

This must list **ALL** files you intend to touch in Block 1 implementation.

No hidden files.

## 5. Files Explicitly NOT Touched

Confirm at minimum:

- `MainActivity.kt` navigation destination/route work — Block 2 only;
- AndroidManifest;
- Gradle;
- network code;
- sensor/image code;
- Wackelbild screen/ViewModel;
- unrelated docs.

If `MainActivity.kt` genuinely needs a compile-only callback parameter change because it is the direct caller, distinguish that from navigation work and explain the exact minimal need. Do not add routes.

## 6. Exact Implementation Plan for Block 1

Describe the precise edits, but **do not provide code**.

Include:

- callback signature change;
- menu divider placement;
- new menu item placement;
- callback invocation behavior;
- string resources;
- test changes;
- documentation update if required.

## 7. Risks

List only real Block-1 risks and mitigations.

## 8. Verification Planned After Implementation

State the exact commands/tests to run after approval.

At minimum assess:

- `./gradlew testDebugUnitTest`
- the relevant CompareScreen instrumentation test task / Managed Device task used by this repository
- `./gradlew assembleDebug`

Do not run them yet unless strictly required to establish a factual baseline.

Also state whether real-device validation is needed for Block 1.

## 9. Scope Confirmation

End with exactly:

**BLOCK 1 SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

# 9. Critical Workflow Rule

This prompt is ANALYSIS + SCOPE CONFIRMATION only.

Do not:

- modify files;
- output code;
- create implementation snippets;
- apply patches;
- run formatting tools;
- commit;
- push.

Wait for explicit user approval before any implementation prompt/block begins.
