# Issue #2 — Gate 1 Analysis + Dedicated Branch Creation

## Objective

Start work on GitHub Issue #2 in the SameView Android repository using the project's strict one-issue / one-fix workflow.

This prompt is **analysis only**.

The only repository-state change allowed is creating and checking out a dedicated branch for Issue #2, provided the working tree is clean and the branch can be created safely.

Do **not** implement a fix.
Do **not** modify production code, tests, docs, Gradle, manifests, resources, dependencies, or permissions.
Do **not** commit anything.

The goal is to determine:
- whether Issue #2 is still valid on the current `main`
- whether it is reproducible
- the exact root cause
- the smallest possible fix strategy
- the exact files that would likely need modification later
- the exact verification required after a future implementation

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

The Android 16 / API 36 migration has already been merged to `main`.

Current expected baseline:
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- API-36 migration fully verified
- Issue #1 closed
- `main` is the authoritative starting point for Issue #2

---

# Step 0 — Inspect Git State Before Branching

Before doing anything else:

1. Confirm the active branch.
2. Confirm current HEAD.
3. Confirm whether the working tree is clean.
4. Confirm `main` and `origin/main` relationship.
5. Do not stash, reset, discard, overwrite, amend, rebase, or clean anything.

If the working tree is not clean, STOP and report exact state.

If `main` is not current with `origin/main`, report that before proceeding.

---

# Step 1 — Read GitHub Issue #2 Live

Inspect the current live issue:

`https://github.com/mario-fliegner/sameview/issues/2`

Use `gh issue view 2`, GitHub access, or equivalent.

Report:
- issue number
- exact title
- open/closed state
- full purpose/body summary
- labels
- acceptance criteria if present
- screenshots/attachments/reproduction steps if present
- whether the issue describes:
  - confirmed bug
  - UX problem
  - improvement
  - release/compliance task
  - uncertain behavior/question

Do not rely on memory or an old issue snapshot.

If Issue #2 is already closed, STOP and report that before creating a branch.

---

# Step 2 — Derive the Branch Name

Create a dedicated branch from the current `main`.

Use this naming convention:

`issue/2-<short-kebab-summary>`

Derive `<short-kebab-summary>` from the actual Issue #2 title/body.

Requirements:
- concise
- lowercase
- no spaces
- no unrelated wording
- clearly tied to Issue #2

Before creation:
- confirm exact `main` HEAD
- confirm working tree clean

Then create/check out the branch.

After creation, report:
- branch name
- base commit
- working tree status

Do not commit anything.

---

# Step 3 — Mandatory Source-of-Truth Review

Before analyzing root cause, identify and read the project MD specifications relevant to Issue #2.

Always read:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`

Then read every feature-specific spec relevant to the issue.

Potential examples include:
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `SETTINGS_UX_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `SESSION_METADATA_EDITOR_V1.md`
- `FAVORITES_AND_LIBRARY_FILTERS_V1.md`
- `VIDEO_EXPORT_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `SESSION_ORIGINALS_PRIVACY_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Do not assume which spec applies until the live issue is read.

If code and spec conflict:
- the current source-of-truth MD wins unless explicitly superseded
- report the inconsistency
- do not silently recommend changing behavior against the spec

---

# Step 4 — Reproduce / Validate the Issue

Do not assume the issue still exists.

Use the current post-API-36 `main` baseline.

Determine whether Issue #2 is:
- reproducible
- partially reproducible
- no longer reproducible
- expected/documented behavior
- unclear / needs user confirmation

Use repository evidence and, where practical, targeted read-only/build/test execution.

If the issue concerns UI, navigation, storage, lifecycle, permissions, camera, compare, DataStore, MediaStore, or other Android behavior:
- inspect existing tests first
- identify whether a current test already covers the behavior
- do not add/change tests yet

If reproduction requires a real physical device and cannot be proven in Claude's environment:
- say so explicitly
- define the exact manual reproduction steps the user should later perform

Do not guess.

---

# Step 5 — Root-Cause Analysis

Identify the exact failing point.

Required:
- exact file(s)
- exact class/composable/function
- relevant line numbers
- current logic
- why that logic produces the reported behavior
- whether the behavior is intentional per spec
- whether the issue was introduced by a known prior change
- whether API-36 migration changes are relevant or irrelevant

Do not broaden into unrelated cleanup.

Do not refactor.

Do not recommend architecture changes unless a minimal fix is impossible.

---

# Step 6 — Minimal Fix Strategy

Propose exactly one minimal fix strategy.

The strategy must:
- solve only Issue #2
- preserve all unrelated working behavior
- avoid refactors
- avoid dependency upgrades
- avoid permission changes unless the issue strictly requires them
- avoid UI changes outside Issue #2
- remain reversible

If multiple plausible fixes exist:
- compare them briefly
- choose one
- explain why it is the smallest/safest

Do not output code.

---

# Step 7 — Expected File Scope

List all files that would likely change in a future implementation.

For each:
- full repository-relative path
- why it must change
- production/test/doc classification

If the exact scope cannot yet be determined, say so.

Do not silently include unrelated files.

If a source-of-truth MD must change because Issue #2 intentionally changes documented behavior, identify it now.

---

# Step 8 — Regression Risk Assessment

Assess only risks caused by the proposed Issue #2 fix.

Explicitly consider, where relevant:
- Camera lifecycle
- Compare flow
- navigation/back behavior
- Compose state
- session storage
- DataStore
- MediaStore
- permissions
- Hilt/KSP
- responsive layout
- accessibility
- offline/privacy guarantees
- release build stability

If none apply, say so.

Do not reopen unrelated migration risks.

---

# Step 9 — Test / Verification Plan

Define the smallest sufficient verification for the future implementation.

At minimum specify:
- exact unit test(s) to run or add
- exact instrumentation test(s) to run or add
- exact managed-device task if relevant
- whether API 29, API 35, API 36 coverage matters
- whether real-device validation is required
- whether `assembleDebug`
- whether `assembleRelease`
- whether `bundleRelease`
- whether `lintDebug`
- whether `clean` is warranted

Do not over-test unrelated areas, but do not under-test high-risk behavior.

If an existing test should be extended, identify the exact class.

If a new regression test is needed, identify where it belongs.

Do not implement tests in this prompt.

---

# Step 10 — GitHub Issue Closure Strategy

Determine whether a future verified fix should close Issue #2 via:

`Closes #2`

or whether Issue #2 should only be referenced.

Use:
- `Closes #2` only if the future implementation fully resolves the issue
- `Refs #2` if the change is partial or investigative

Do not edit or close the issue now.

---

# Required Final Report

Return exactly these sections:

## 1. Git / Branch State
- original branch
- main HEAD
- origin/main relation
- created branch
- branch base commit
- working tree state

## 2. GitHub Issue #2
- title
- state
- labels
- body summary
- issue type
- acceptance criteria

## 3. Source-of-Truth Review
- docs read
- binding constraints
- conflicts/stale statements relevant to Issue #2

## 4. Reproduction Result
Choose:
- REPRODUCED
- PARTIALLY REPRODUCED
- NOT REPRODUCED
- EXPECTED / SPEC-COMPLIANT
- USER CONFIRMATION REQUIRED

Include evidence.

## 5. Exact Root Cause
- file
- function/composable/class
- line range
- causal explanation

## 6. Minimal Fix Strategy
One fix only.

## 7. Expected File Scope
Table:
| File | Type | Why |
|---|---|---|

## 8. Regression Risks

## 9. Verification Plan
Exact commands/tests/manual checks.

## 10. Documentation Impact
State whether any MD must change.

## 11. Issue #2 Closure Strategy
Recommend:
- `Closes #2`
- `Refs #2`
- or no issue reference yet

## 12. Gate 1 Verdict

Choose exactly one:
- **READY FOR SCOPE CONFIRMATION**
- **BLOCKED**
- **NO FIX REQUIRED**
- **USER DECISION REQUIRED**

If ready, state the exact next step only.

Then STOP.

---

## Final Safety Rules

- One issue only: #2.
- Analysis only.
- Branch creation is the only allowed repository-state change.
- No code changes.
- No test changes.
- No docs changes.
- No dependency changes.
- No Gradle changes.
- No manifest/permission changes.
- No commit.
- No push.
- No merge.
- Do not start Issue #3.
