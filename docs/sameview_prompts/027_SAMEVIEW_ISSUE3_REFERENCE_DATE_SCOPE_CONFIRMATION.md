# Issue #3 — Scope Confirmation: Reference Date Must Not Be After Capture Date

## Objective

Confirm the exact implementation scope for GitHub Issue #3 based on the completed analysis and the now-approved product decisions.

This is **scope confirmation only**.

Do not modify any file.
Do not implement code.
Do not modify tests, resources, docs, Gradle, manifests, dependencies, permissions, or GitHub issues.
Do not commit, push, merge, rebase, amend, stash, reset, or discard anything.

After the report, STOP and wait for explicit user approval.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Before analysis:

1. Confirm the current branch.
2. Confirm current HEAD.
3. Confirm working tree state.
4. Confirm Issue #2 is closed.
5. Confirm Issue #3 is OPEN.
6. If still on `issue/2-country-selection`, do not implement there. Propose the exact dedicated Issue #3 branch name to create from current `main`, but do not create it in this scope-confirmation step unless the repository workflow already requires branch creation before scope confirmation.
7. Do not alter existing repository state.

If there are unexpected uncommitted changes, STOP and report exact state.

---

# Source of Truth

Read and treat as authoritative:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/CAMERA_WORKFLOW_UX_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Also inspect the live GitHub Issue #3.

---

# Binding Product Decisions

These decisions are final. Do not reopen them.

## 1. Validation rule

Reference Date must not be later than Capture Date.

Valid:
- earlier date
- same date

Invalid:
- later date

No automatic correction.

The invalid value remains visible so the user can fix it.

---

## 2. Legacy sessions — grandfathered

Existing sessions that already contain `reference.date > capture date` remain readable and editable.

If the user does not touch Reference Date:

- viewing is allowed
- opening/cancelling Edit Session is allowed
- editing Title is allowed
- editing Location is allowed
- saving unrelated edits is allowed
- the legacy invalid Reference Date remains unchanged

If the user explicitly changes Reference Date:

- the newly entered value must satisfy the rule
- another invalid later date is rejected
- same date is accepted
- earlier date is accepted

Do not silently repair or migrate old data.

---

## 3. Partial-precision dates

SameView supports:

- `YYYY`
- `YYYY-MM`
- `YYYY-MM-DD`

Validation compares only at the precision actually entered.

Example Capture Date:

`2026-08-27`

Expected:

- `2025` -> valid
- `2026` -> valid
- `2027` -> invalid
- `2026-07` -> valid
- `2026-08` -> valid
- `2026-09` -> invalid
- `2026-08-26` -> valid
- `2026-08-27` -> valid
- `2026-08-28` -> invalid

Do not invent missing month/day values.

Use a semantic precision-aware comparison, not fragile lexical tricks.

---

## 4. Capture-date timezone

Capture Date for validation must be derived from `capture.timestampMs` using the same local/default timezone convention already used by SameView's displayed Capture Date and Compare label logic.

Do not introduce UTC validation semantics.

Validation and visible Capture Date must agree.

---

## 5. DatePicker behavior

Do **not** constrain/disable later dates in the picker.

Use the existing picker unchanged.

Reason:
- picker only covers day precision
- Reference Date also supports year/month precision
- Save-time validation is the authoritative rule
- no duplicated max-date logic

---

## 6. Validation timing

Use the existing Save-time validation pattern.

Do not introduce live validation or blur validation unless existing code requires it.

Typing/changing the field may clear prior error state as today.

On Save:
- format/plausibility validation still applies
- new order validation applies only when Reference Date has been changed from its initial value, preserving grandfathered legacy behavior

---

## 7. Error UX

Reuse the existing inline Reference Date error pattern.

Required wording:

EN:
`Reference date can't be later than the capture date.`

DE:
`Das Referenzdatum darf nicht nach dem Aufnahmedatum liegen.`

Do not use snackbar/dialog for this error.

Do not replace the existing format/plausibility error wording.

The UI must distinguish:
- existing format/plausibility error
- new date-order error

Use the smallest mechanism consistent with current code.

---

# Exact Validation Ownership

Confirm the smallest correct design.

Expected:

## Pure comparison helper

Likely home:
`app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt`

Reason:
- existing partial-precision date parsing helpers already live there
- capture date local-timezone extraction already follows the needed convention
- helper can be unit-tested independently

Confirm whether this remains the best home.

The helper should conceptually determine:

`referenceDate > captureDate ?`

without mutating anything.

Do not refactor unrelated Compare logic.

## ViewModel

`EditSessionViewModel.kt` remains the authoritative Save-time owner because it holds both:
- current Reference Date
- Capture timestamp

Confirm:
- validate order only if Reference Date changed from original
- legacy untouched invalid value does not block unrelated Save
- existing format validation continues
- no storage API expansion required

## Storage

Expected:
`SessionStorage.updateReferenceDate()` unchanged.

Confirm no storage change is required.

---

# Exact Legacy Behavior — Lock Tests

Confirm all cases:

### Existing invalid session

Stored:
`reference = 2026-09-01`
Capture:
`2026-08-27`

1. View only -> allowed
2. Open Edit Session, back/cancel -> allowed
3. Change Title only -> Save allowed
4. Change Location only -> Save allowed
5. Change Reference to `2026-09-02` -> Save rejected with new error
6. Change Reference to `2026-08-27` -> Save allowed
7. Change Reference to `2026-08-26` -> Save allowed
8. Save unrelated edit while Reference untouched -> invalid legacy value preserved exactly

No auto-correction.

---

# Reference Date Optionality

Verify from current spec/code whether Reference Date may be blank/null.

If optional:
- blank remains valid
- new cross-field rule does not apply when blank

If not optional, report the current contract.

Do not change optionality in this issue.

---

# Malformed Legacy Dates

Do not expand Issue #3 into general metadata repair.

Confirm current behavior for malformed legacy Reference Date and preserve it unless Issue #3 explicitly requires otherwise.

The new order validator should only operate after existing format/plausibility validation says the entered value is structurally valid.

---

# Repository-Wide Writer Scope

Confirm the analysis result that:

- manual Edit Session save is the primary target
- EXIF/session creation path is not changed
- backup/import/restore is not changed
- no metadata migration is added

List every production writer and classify:
- NO CHANGE
- VERIFICATION ONLY
- CHANGE REQUIRED

Do not broaden scope.

---

# Documentation Scope

The analysis concluded:

## Required
`docs/SESSION_METADATA_EDITOR_V1.md`

Update only the relevant current sections to document:
- Reference <= Capture
- same-day allowed
- no automatic correction
- grandfathered legacy invalid values
- Save-time validation
- DatePicker remains unconstrained
- partial-precision comparison rule
- inline validation message behavior

## Optional
`docs/SESSION_METADATA_V1.md`

Determine whether a minimal cross-reference is actually necessary for consistency.

Preferred:
- do not change it unless current wording would otherwise be misleading
- schema itself is unchanged

## No change
- `docs/CAMERA_WORKFLOW_UX_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/IMPLEMENTATION_NOTES.md` during implementation
- historical audits/plans

Do not modify docs in this scope-confirmation step.

---

# Exact Production File Scope

Lock down the exact files expected to change.

Likely:

1. `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`
4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-de/strings.xml`
6. `docs/SESSION_METADATA_EDITOR_V1.md`
7. optionally `docs/SESSION_METADATA_V1.md` only if truly necessary

Verify every file from repository evidence.

Do not include any uncertain file.

If another file is required, explain exactly why.

---

# Exact Test Scope

Lock down the exact test files and cases.

Expected:

## `CompareLabelLogicTest.kt`

Add focused pure comparison tests:

- day before
- day equal
- day after
- year-only valid same year
- year-only invalid later year
- month-only valid same month
- month-only invalid later month
- timezone/local-date consistency if existing test harness supports fixed timezone
- blank/null behavior if helper accepts it

## `EditSessionViewModelTest.kt`

Add/extend:

- valid earlier date saves
- same-day saves
- later date rejected
- correct error kind emitted
- no auto-correction
- untouched legacy invalid + Title edit saves
- untouched legacy invalid + Location edit saves
- explicit changed invalid rejected
- correction to same/earlier accepted
- dirty-state remains correct
- blank valid if optional

## `EditSessionScreenTest.kt`

Add/extend:

- later date shows the new inline error
- DE string
- EN string
- same-day accepted
- legacy invalid value visible
- unrelated edit allowed under grandfathering
- DatePicker itself remains unconstrained unless current test setup needs no change

Do not add storage tests if storage code remains unchanged.

---

# Verification Plan

Confirm exact commands.

At minimum:

1. focused unit:
   - `CompareLabelLogicTest`
   - `EditSessionViewModelTest`
2. compile instrumentation:
   - `compileDebugAndroidTestKotlin`
3. targeted `EditSessionScreenTest`
4. `assembleDebug`
5. `lintDebug`
6. targeted API 29 managed-device instrumentation
7. targeted API 36 managed-device instrumentation
8. `assembleRelease`
9. `bundleRelease`
10. `git diff --check`
11. full diff/status review

No full 930-test suite unless a concrete shared-code regression justifies it.

Manual real-device validation should be limited to:
- earlier/same/later date
- DE/EN error UI
- one grandfathered legacy session if practical
- no auto-correction

No camera/compare/share/video manual regression pass unless touched code actually affects them.

---

# Risks

Classify only Issue #3 risks:

- legacy sessions becoming uneditable
- timezone boundary mismatch
- partial-precision false rejection
- error-kind regression
- dirty-state regression
- accidental automatic correction
- DatePicker inconsistency
- release build stability
- accessibility of inline error
- offline/privacy impact

For each:
- code prevention required
- verification only
- no impact

---

# Branching / Issue Closure

Issue #3 should use a dedicated branch from current `main`.

Propose exact branch name, e.g.:

`issue/3-reference-date-validation`

Do not create it in this scope-confirmation step unless current project workflow explicitly requires branch creation here.

Issue #3 remains OPEN.

After implementation + automated verification + manual validation, final commit may use:

`Closes #3`

Do not close/reference now.

---

# Required Final Report

Return exactly:

## 1. Repository / Branch Baseline

## 2. Dedicated Issue #3 Branch Plan

## 3. Binding Validation Contract

## 4. Partial-Precision Contract

## 5. Legacy Grandfathering Contract

## 6. Timezone / Capture-Date Contract

## 7. Validation Ownership

## 8. Error UX / Localization

## 9. Repository Writer Impact

Table:
| Writer / File | Classification | Exact reason |

## 10. Documentation Scope

Table:
| File | Required? | Exact change |

## 11. Exact Files to Modify

Table:
| File | Type | Exact change |

No uncertain entries.

## 12. Exact Test Scope

## 13. Verification Plan

## 14. Risks

## 15. Issue #3 Closure Plan

## 16. Scope Verdict

Choose exactly one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **USER DECISION REQUIRED**
- **BLOCKED**

Then STOP.

No implementation until explicit user approval.
