# Issue #3 — ANALYSIS: Reference Date Must Not Be After Capture Date

## Purpose

Analyze GitHub Issue #3 for the SameView Android app and determine the smallest correct implementation.

This is a **new Claude session**. Do not assume any prior chat context.

This step is **ANALYSIS ONLY**.

Do not modify files.
Do not write implementation code.
Do not update docs.
Do not commit, push, merge, rebase, stash, reset, discard, or modify GitHub issues.

After the analysis report, STOP. The next step will be one scope confirmation, followed by implementation after explicit user approval.

---

# Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Before doing anything:

1. Inspect the current branch, HEAD and working tree.
2. Confirm Issue #2 work has already been completed/merged or identify any remaining uncommitted state.
3. Do not alter any existing working-tree changes.
4. Read GitHub Issue #3 directly and quote/summarize its actual current title/body/acceptance criteria in the report.
5. Confirm Issue #3 is OPEN.

If the repository contains unexpected uncommitted changes that make reliable analysis impossible, STOP and report that.

---

# Mandatory Source-of-Truth Review

Read the relevant current project specifications before analyzing code.

At minimum:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/CAMERA_WORKFLOW_UX_V1.md`

Also inspect any additional spec that actually governs date display/editing or session persistence.

The Markdown specifications are authoritative over implementation unless Issue #3 explicitly changes the contract.

Issue #3 is expected to require a specification update to `SESSION_METADATA_EDITOR_V1.md`; verify the live issue rather than assuming this wording.

Do not silently implement against a contradictory current spec. Identify the contradiction precisely.

---

# Product Requirement to Analyze

The intended rule is:

> A session's reference date must not be later than its capture date.

Examples:

- Reference `2020-05-01`, Capture `2026-08-27` -> valid
- Reference `2026-08-27`, Capture `2026-08-27` -> valid
- Reference `2026-08-28`, Capture `2026-08-27` -> invalid

The reference date represents when the historical/reference image was taken.
The capture date represents the SameView recreation/capture.

The app must not silently “fix” an invalid date entered by the user.

Existing sessions containing historically invalid data must remain readable and must not be silently migrated or rewritten merely because the app now knows this rule.

Use the live Issue #3 body as authoritative if it differs from this summary.

---

# Core UX Question — Analyze From the User's Perspective

Do not treat this as merely a string-comparison or validation problem.

Determine the best user-facing behavior for editing Reference Date.

Analyze these alternatives against the current UI:

### A. Prevent impossible future choices in the date picker

For example, if Capture Date is `27.08.2026`, dates after that are disabled/unselectable.

Pros may include:
- user cannot create invalid data through normal picker interaction
- immediate constraint visibility

Cons may include:
- legacy invalid values may behave awkwardly
- the existing picker API/design may not support this cleanly
- it may hide the reason rather than communicate it
- manual/text input behavior may differ

### B. Allow selection but reject Save with inline validation

Pros may include:
- explicit explanation
- legacy value can remain visible
- no automatic correction

Cons:
- user discovers the problem later

### C. Combination

Potentially:
- date picker prevents new invalid selections
- validation still exists as the authoritative safety net
- legacy invalid sessions remain visible and editable
- Save is blocked only when required by the final contract

Do not assume C is automatically best. Inspect the actual current date-picker/edit UX and choose the smallest, clearest user-facing behavior consistent with SameView.

---

# Critical Legacy-Session Question

This is the most important part of the analysis.

Suppose an existing metadata file contains:

```text
reference.date = 2026-09-01
capture date   = 2026-08-27
```

This session existed before Issue #3.

Determine exactly what should happen when the user:

1. merely views the session
2. opens Edit Session and cancels/back
3. edits only Title
4. edits only Location
5. changes Reference Date to another still-invalid later date
6. changes Reference Date to Capture Date
7. changes Reference Date to an earlier valid date
8. saves an unrelated edit while the legacy invalid Reference Date remains unchanged

The requirement says legacy invalid sessions remain readable and must not be silently rewritten.

But that does **not automatically answer** whether unrelated edits should be saveable while the pre-existing invalid value remains unchanged.

Analyze both models:

### Strict whole-form validation
Any Save is blocked while Reference Date > Capture Date, even if that invalid value was pre-existing and untouched.

### Touched-field / grandfathered legacy validation
A pre-existing invalid Reference Date may remain untouched while unrelated fields are edited/saved; but once the user explicitly changes Reference Date, the newly selected value must satisfy the rule.

Evaluate which model best matches:
- Issue #3 wording
- existing editor behavior
- the project's legacy-preservation philosophy from other metadata features
- user expectations
- risk of trapping users in an unrelated edit
- metadata integrity

Do not invent a rule without evidence. If the live issue clearly decides this, follow it. If not, make a concrete recommendation and flag whether user decision is required.

---

# Capture Date Source — Trace Exactly

Determine the authoritative Capture Date used for validation.

Trace:

- metadata schema
- `capture.timestampMs`
- any derived/formatted capture date
- timezone conversion
- device locale/timezone
- Edit Session initial state
- scanner/session model
- any fallback if timestamp is absent/malformed

Critical question:

**What exactly constitutes “same date” when `capture.timestampMs` is an instant but `reference.date` is a date-only `YYYY-MM-DD` value?**

Determine the existing SameView convention for converting `capture.timestampMs` to a calendar date.

Do not introduce a new UTC/local-time interpretation unless required.

Explicitly analyze timezone boundary cases:
- capture shortly before/after midnight
- whether validation should use the same local-date conversion already used for displayed Capture Date

The validation and displayed Capture Date must not disagree.

---

# Reference Date Representation — Trace Exactly

Determine:

- storage representation
- parsing code
- ViewModel representation
- UI representation
- picker representation
- formatter/parser locale behavior
- malformed legacy behavior

Verify whether lexical ISO `YYYY-MM-DD` comparison is safe in the actual code path or whether the app already uses `LocalDate`/Calendar/another type.

Prefer semantic date comparison over fragile string comparison if existing platform/API support makes that appropriate, but do not introduce unnecessary dependencies or broad date refactors.

Remember `minSdk = 29`.

---

# Current Edit Session Flow

Trace the exact code path for:

1. scanner/session loading
2. Edit Session ViewModel initialization
3. Reference Date field state
4. date picker opening
5. date selection
6. dirty-state calculation
7. validation
8. Save enablement
9. Save execution
10. storage update
11. error display

Identify exact files/classes/functions.

Determine whether validation belongs in:
- UI only
- ViewModel only
- storage layer
- a combination

The authoritative business rule must not rely solely on a visual disabled-date picker if another programmatic path can persist invalid values.

At the same time, do not add redundant validation layers without a concrete need.

---

# Existing Validation / Error UX

Inspect current Edit Session validation conventions.

Determine:

- how validation errors are represented
- whether `OutlinedTextField` uses `isError`
- supporting/error text conventions
- whether Save is disabled or Save triggers validation
- focus behavior
- snackbar/dialog usage
- DE/EN strings
- accessibility semantics for errors
- whether current validation is immediate, on blur, on Save, or mixed

Issue #3 must follow the established UX rather than inventing a parallel validation system.

Analyze the most natural wording.

Do not finalize translation from guesswork if the project already has terminology conventions.

Potential semantic wording only:

EN:
`Reference date cannot be after capture date.`

DE:
`Das Referenzdatum darf nicht nach dem Aufnahmedatum liegen.`

Verify whether SameView actually calls these concepts “Reference date” / “Capture date” and `Referenzdatum` / `Aufnahmedatum`.

---

# Date Picker Constraint

Inspect the exact Compose Material date-picker implementation/version currently used.

Determine whether it supports a selectable-date constraint cleanly, e.g. `SelectableDates`, and whether that API is compatible with the project's Compose version and API 29.

If using a picker max-date constraint would:
- require experimental APIs not already used,
- duplicate ViewModel validation awkwardly,
- break legacy selected-date rendering,
- or create locale/timezone inconsistency,

say so.

If it is clean and best practice, identify the exact minimal approach.

Do not implement it in this analysis.

---

# Save / Dirty-State Semantics

Determine exact expected state for:

### New/current valid session
- Reference <= Capture -> save allowed
- Reference > Capture -> invalid

### Legacy invalid session
Analyze untouched vs touched behavior as described above.

### Clearing Reference Date
Determine whether Reference Date is optional under the current spec.
If clearing is allowed:
- null/blank should presumably be valid
- verify from spec/code

### Same-day value
Must be valid.

### Malformed legacy date
Do not accidentally convert Issue #3 into a general metadata repair project.
Determine current behavior and keep scope minimal.

---

# Storage Layer Question

Analyze whether `SessionStorage.updateReferenceDate(...)` or equivalent should reject invalid Reference Date.

Important: the storage method may not have Capture Date available.

Do not expand storage APIs or parse metadata twice merely for theoretical purity unless necessary.

Determine the smallest layer that:
- owns both values
- prevents new invalid persistence
- remains testable
- preserves legacy behavior

State clearly why.

---

# Other Entry Points

Search repository-wide for every way `reference.date` can be written or changed.

At minimum inspect:

- session creation
- EXIF-derived reference date
- manual Reference Date editing
- import/restore
- backup restore
- any test helper or migration
- any future/session recreation flow

Classify each production writer:

| Writer | Can create `reference > capture`? | User-controlled? | Must change for Issue #3? |

Critical: Issue #3 may be specifically about the metadata editor rather than retroactively imposing the rule on session creation/import. Use the issue/spec evidence.

Do not expand scope to backup/import unless the rule genuinely requires it.

---

# Camera / Session Creation Interaction

Reference date may originate from EXIF or manual metadata before/around capture.

Analyze whether a reference image could legitimately have an EXIF date after the SameView capture timestamp because of:
- bad camera clock
- malformed metadata
- future-dated source image

Determine whether Issue #3 requires preventing creation of such a session, or only preventing the user from saving an invalid edited Reference Date.

Again: use the live issue wording.

Do not silently broaden the feature.

---

# Compare / Display Impact

Determine whether invalid legacy dates currently affect:
- Compare labels
- ordering
- library sorting
- video/share labels
- date formatting

Issue #3 should not redesign these surfaces.

Identify whether any verification-only regression check is warranted.

---

# Documentation Impact

The live issue reportedly requires `SESSION_METADATA_EDITOR_V1.md` to be updated first.

Determine exact required documentation changes.

Likely:

`docs/SESSION_METADATA_EDITOR_V1.md`
- validation rule
- same-day allowed
- no silent correction
- legacy behavior
- error UX / save behavior
- picker behavior if constrained

Potentially:

`docs/SESSION_METADATA_V1.md`
- only if the metadata schema contract itself should state the semantic relationship

Do not automatically edit `SESSION_METADATA_V1.md` if the rule is editor-only.

Classify every relevant doc:

- REQUIRED
- OPTIONAL
- NO CHANGE
- HISTORICAL — PRESERVE

Do not update historical audits/plans merely because they mention dates.

---

# Tests — Analyze Exact Required Coverage

Identify exact existing test classes and minimal new tests.

Expected ViewModel/unit cases may include:

1. reference before capture -> valid
2. reference same date -> valid
3. reference after capture -> invalid
4. blank reference -> valid if optional
5. invalid value does not save
6. valid correction saves
7. no automatic correction
8. legacy untouched invalid behavior according to chosen contract
9. dirty-state behavior
10. timezone/date conversion consistency

Expected instrumentation cases may include:

- field error visible with correct DE/EN semantics where practical
- Save blocked/allowed according to contract
- picker prevents dates after capture if that is chosen
- same-day selectable
- legacy invalid field remains visible
- unrelated edit behavior for legacy invalid session

Storage tests only if storage code actually changes.

Do not add redundant tests across every layer.

---

# API / Compatibility

Consider:

- minSdk 29
- target/compile SDK 36
- Compose Material3 version actually in repo
- Java/Kotlin date APIs available on API 29
- timezone behavior
- locale behavior
- accessibility
- release build stability
- offline/privacy behavior

No network should be needed.

---

# Scope Discipline

This is ONE issue.

Forbidden unless Issue #3 demonstrably requires it:

- general date-system refactor
- changing capture timestamp storage
- metadata version bump
- metadata migration
- automatic repair of old sessions
- EXIF redesign
- Compare redesign
- Library redesign
- Share/Video changes unrelated to verification
- navigation changes
- Camera lifecycle changes
- new dependencies
- new permissions
- analytics/telemetry
- Issue #2 changes
- unrelated cleanup
- formatting unrelated code

Prefer the smallest reversible fix.

---

# Required Analysis Report

Return exactly these sections:

## 1. Repository / Branch / Working Tree State

Include branch, HEAD, modified/untracked files, Issue #3 state/title.

## 2. Issue #3 Binding Requirements

Summarize the actual live issue and distinguish explicit requirements from your recommendations.

## 3. Source-of-Truth Findings

Table:

| Document | Current contract | Conflict with Issue #3? | Required action |

## 4. Current Date Data Model

Explain reference date, capture timestamp/date, parsing, timezone and display conversion.

## 5. Exact Current Edit Flow

Trace load -> picker/input -> ViewModel -> validation -> save -> storage.

## 6. Current Validation / Error UX

Explain established project pattern to reuse.

## 7. Repository-Wide Reference-Date Writer Inventory

Table:

| File / writer | Purpose | Can create/change reference date? | Issue #3 impact |

## 8. Legacy Invalid Session Decision

Analyze all eight legacy scenarios listed above.

Choose a recommended contract.

If the live issue does not settle strict-vs-grandfathered behavior and this materially affects UX, say **USER DECISION REQUIRED** rather than guessing.

## 9. Date Picker Decision

Choose:
- constrain picker
- validation only
- both

Explain why, based on actual Compose/API implementation.

## 10. Validation Ownership Decision

State exact layer(s) and why.

## 11. Timezone / Same-Day Decision

State exactly how Capture Date should be derived for comparison so UI and validation agree.

## 12. Error UX / Localization Decision

Exact recommended behavior and wording based on existing project terminology.

## 13. Documentation Impact

Table:

| File | Classification | Exact section/rule affected |

## 14. Proposed Minimal Production File Scope

List exact repo-relative files that would need modification if the recommendation is approved.

No code.

## 15. Proposed Test File Scope

List exact existing/new test files and what each must prove.

## 16. Verification Plan

Exact Gradle/test commands appropriate for the touched areas, including whether API 29/API 36 real or managed-device checks are required and whether real-device validation is required.

## 17. Regression / Release Risks

Table:

| Risk | Classification | Mitigation |

Include legacy sessions, timezone boundary, dirty-state, storage integrity, accessibility, offline/privacy, release stability.

## 18. Open Questions

Only genuine unresolved product decisions. Do not manufacture questions.

## 19. Analysis Verdict

Choose exactly one:

- **ANALYSIS COMPLETE — READY FOR SCOPE CONFIRMATION**
- **USER DECISION REQUIRED**
- **BLOCKED**

If complete, state in one sentence what the next scope confirmation must lock down.

Then STOP.

---

# Final Reminder

ANALYSIS ONLY.

No files modified.
No implementation.
No docs edited.
No tests changed.
No commits.
No push/merge.
No GitHub issue modification.

Do not solve Issue #3 yet; determine the exact correct fix first.
