# Issue #3 — IMPLEMENTATION: Reference Date Must Not Be After Capture Date

## Authorization

The preceding scope confirmation is **APPROVED**.

Implement GitHub Issue #3 exactly according to the confirmed scope and product decisions.

Do not start another analysis.
Do not start another scope-confirmation round.
Do not reopen settled product decisions.

This is now the implementation step.

---

## Repository / Branch Setup

Repository:

`C:\data\work\privat\git-repos\sameview`

Current expected repository state:

- `main` contains Issue #2 commit `00ba901c` — `feat: add localized country selection and display`
- `origin/main` should be at the same commit
- working tree clean
- Issue #2 closed
- Issue #3 open

### Before editing

1. Confirm current branch.
2. Confirm `main` and `origin/main` both contain `00ba901c`.
3. Confirm working tree is clean.
4. Confirm GitHub Issue #3 is OPEN.
5. Confirm no existing `issue/3-reference-date-validation` branch exists.
6. If state differs materially, STOP and report exact state.

### Create dedicated branch

Create and check out exactly:

`issue/3-reference-date-validation`

from current `main`.

After creation confirm:

- branch name
- base commit = current `main`
- working tree clean

No code changes before the branch is created.

Do not rebase, amend, reset, stash, or rewrite prior history.

---

# Source of Truth

Read before implementation:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/CAMERA_WORKFLOW_UX_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Issue #3 is the approved behavior change.

---

# Binding Product Decisions

## Validation rule

Reference Date must not be later than Capture Date.

Valid:
- earlier
- same date

Invalid:
- later

No silent correction.

The invalid user-entered value remains visible after rejection.

---

## Legacy grandfathering

Existing sessions that already contain:

`reference.date > capture date`

must remain readable and editable.

If Reference Date itself is **unchanged**:

- viewing allowed
- opening/cancelling editor allowed
- Title-only edit may save
- Location-only edit may save
- unrelated edits may save
- old invalid Reference Date remains unchanged

If Reference Date is **explicitly changed**:

- newly entered later value -> reject
- same date -> accept
- earlier date -> accept

Do not migrate or repair old metadata automatically.

---

## Partial precision

Supported inputs remain:

- `YYYY`
- `YYYY-MM`
- `YYYY-MM-DD`

Compare only at the precision actually supplied.

Capture date example:

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

---

## Timezone

Derive the Capture calendar date from `capture.timestampMs` using SameView's established **local/default timezone** convention.

Use the same semantic convention as:

- displayed `Captured on`
- existing Compare label date extraction
- existing local `Calendar` usage

Do not introduce UTC comparison semantics.

The DatePicker's own UTC millisecond representation remains an internal picker detail and must not redefine capture-date validation.

---

## DatePicker

Do **not** constrain selectable dates.

Do not add `SelectableDates`.
Do not disable later dates.

The authoritative rule is Save-time validation.

---

## Validation timing

Keep the existing Save-triggered validation UX.

On Save:

1. existing format/plausibility validation runs as before
2. if the Reference Date has changed from its original loaded value and is non-blank/format-valid:
   - run the new order validation
3. if later than Capture:
   - reject Save
   - show the new inline error
   - keep entered value unchanged

A legacy invalid value that was not changed must not hit the new order rule.

---

# Error UX

Reuse the existing Reference Date inline error mechanism.

Do not add snackbar/dialog.

The UI must distinguish:

1. existing format/plausibility error
2. new date-order error

Locked strings:

### EN

`Reference date can't be later than the capture date.`

### DE

`Das Referenzdatum darf nicht nach dem Aufnahmedatum liegen.`

Keep existing format/plausibility error text unchanged.

Use the smallest error-kind representation consistent with current code.

---

# Approved File Scope

Modify exactly these files unless repository reality proves one additional file is absolutely required.

## Production

1. `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`

## Resources

4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-de/strings.xml`

## Documentation

6. `docs/SESSION_METADATA_EDITOR_V1.md`

## Tests

7. `app/src/test/java/com/isardomains/sameview/ui/compare/CompareLabelLogicTest.kt`
8. `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt`
9. `app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt`

Do not modify `docs/SESSION_METADATA_V1.md`; scope confirmation concluded it is not required.

If another file appears necessary, STOP before touching it and explain why.

---

# Implementation Details

## 1. `CompareLabelLogic.kt`

Add one small pure helper for order comparison.

Conceptual responsibility:

`isReferenceDateAfterCapture(referenceDate, captureTimestampMs)`

Exact naming may follow current file conventions.

Requirements:

- no mutation
- no dependency
- API 29 compatible
- local/default timezone capture date
- precision-aware:
  - year-only compares year
  - year-month compares year/month
  - full date compares year/month/day
- same date/precision equal -> false
- earlier -> false
- later -> true
- helper assumes structurally valid date input, because format validation occurs first
- do not alter `computeCompareLabels()` behavior
- reuse existing parsing helpers where clean
- do not refactor unrelated date logic

---

## 2. `EditSessionViewModel.kt`

Implement the new Save-time rule.

Requirements:

- preserve all existing format/plausibility validation
- determine whether Reference Date changed using the existing normalized-field comparison convention
- only run new order check if Reference Date changed
- blank Reference Date remains valid according to existing optionality
- if new order error:
  - set new error kind
  - keep value unchanged
  - do not write
  - remain on editor
- if legacy invalid but unchanged:
  - unrelated Save proceeds
  - old Reference Date remains untouched
- same-day and earlier changed values save normally
- no automatic correction
- no new storage API
- no metadata version change

### Error type

Replace/extend the current single generic reference error sentinel with the smallest explicit representation that can distinguish:

- format/plausibility error
- date-order error

Do not create a broad validation framework.

---

## 3. `EditSessionScreen.kt`

Render the correct inline error string based on the error kind.

Keep:

- existing field
- existing `isError`
- existing helper/error placement
- existing DatePicker
- existing Save flow
- existing layout

Do not change DatePicker selectable dates.

---

## 4. String resources

Add only the new date-order error key in EN and DE.

Use exactly:

EN:
`Reference date can't be later than the capture date.`

DE:
`Das Referenzdatum darf nicht nach dem Aufnahmedatum liegen.`

Do not alter unrelated strings.

---

## 5. Documentation

Update only:

`docs/SESSION_METADATA_EDITOR_V1.md`

Add/clarify:

- Reference Date must be <= Capture Date
- same-day valid
- partial-precision comparison
- no automatic correction
- Save-time validation
- picker remains unconstrained
- grandfathering:
  - untouched pre-existing invalid values do not block unrelated saves
  - explicit changed Reference Date must satisfy rule
- new inline error rule

Preserve historical text and unrelated sections.

---

# Repository Writer Boundaries

Do **not** modify:

- EXIF/session-creation Reference Date population
- `SessionStorage.isValidReferenceDate()`
- `SessionStorage.updateReferenceDate()`
- backup/import/restore
- Compare rendering
- Camera
- Share
- Video
- navigation

Issue #3 is the Edit Session validation behavior only.

---

# Tests

## `CompareLabelLogicTest.kt`

Add focused pure-helper tests:

- day before -> false
- day equal -> false
- day after -> true
- year same -> false
- later year -> true
- month same -> false
- later month -> true
- earlier month/year -> false
- local-timezone capture date behavior at a boundary if practical using current test conventions

Do not rewrite existing Compare label tests.

---

## `EditSessionViewModelTest.kt`

Add focused cases:

1. earlier changed date -> Save allowed
2. same-day changed date -> Save allowed
3. later changed date -> Save rejected
4. date-order error kind emitted
5. entered invalid value remains unchanged
6. blank reference remains valid
7. legacy invalid untouched + Title edit -> Save allowed
8. legacy invalid untouched + Location edit -> Save allowed
9. legacy invalid changed to another invalid -> rejected
10. legacy invalid changed to same capture date -> accepted
11. legacy invalid changed earlier -> accepted
12. dirty-state remains correct

Reuse existing helpers. No unrelated test refactor.

---

## `EditSessionScreenTest.kt`

Add focused instrumentation:

- new later-date inline error visible
- correct EN text
- correct DE text
- same-day accepted
- legacy invalid value visible on open
- unrelated edit Save allowed with legacy invalid untouched
- DatePicker remains behaviorally unchanged

Do not add picker max-date assertions.

---

# Failure Handling

If a test/build fails:

1. identify the exact failing point
2. classify:
   - production bug caused by this change
   - stale test expectation
   - environment/device limitation
3. fix only within the approved file scope if directly caused by Issue #3
4. never suppress tests/lint
5. never add lint baseline
6. never refactor unrelated code

If a fix genuinely requires a tenth file, STOP before changing it and report.

---

# Verification

Run in this order.

## 1. Focused JVM tests

`./gradlew :app:testDebugUnitTest --tests "*CompareLabelLogicTest*" --tests "*EditSessionViewModelTest*"`

Report exact counts.

## 2. Instrumentation compile

`./gradlew :app:compileDebugAndroidTestKotlin`

## 3. Debug build

`./gradlew :app:assembleDebug`

## 4. Lint

`./gradlew :app:lintDebug`

Report error/warning delta. Do not suppress.

## 5. API 29 targeted instrumentation

Run only `EditSessionScreenTest` on the existing API 29 managed device using the repo's established runner-class filtering syntax.

## 6. API 36 targeted instrumentation

Run only `EditSessionScreenTest` on `pixel2Api36` using the same established filtering approach.

If the current Claude environment cannot run managed devices/ADB:

- do not pretend they passed
- report them as NOT RUN
- still compile instrumentation
- leave them for the user's Android Studio/local environment

## 7. Release verification

Run:

- `./gradlew :app:assembleRelease`
- `./gradlew :app:bundleRelease`

## 8. Diff hygiene

Run:

- `git diff --check`
- `git status --short`
- full diff review

Confirm only the 9 approved files changed.

---

# Manual Validation

After automated verification, mark these as required on a real device unless they were actually run:

- earlier date saves
- same-day date saves
- later date rejected
- invalid entered value remains unchanged
- EN error text
- DE error text
- one legacy invalid session:
  - Title-only save works
  - Location-only save works
  - Reference Date unchanged
  - changing Reference Date to invalid later value is blocked
  - changing to same/earlier succeeds

No Camera/Compare/Share/Video manual regression pass required.

---

# Issue #3 / Commit Rules

Do **not** commit automatically.

Do **not** close Issue #3 yet.

Leave implementation uncommitted after verification.

After automated + manual validation is confirmed by the user, final commit may use:

`Closes #3`

Do not push or merge.

---

# Required Final Report

Return exactly:

## 1. Branch / Baseline

Include:
- created branch
- base commit
- Issue #3 status

## 2. Modified Files

Confirm exactly the approved files.

## 3. Comparison Helper

Explain precision and timezone behavior.

## 4. ViewModel Validation

Explain changed-only grandfathering.

## 5. Error UX

Explain error-kind distinction and strings.

## 6. Legacy Behavior

Walk through untouched vs changed legacy invalid date.

## 7. Documentation Change

Exact section/rule updated.

## 8. Test Changes

Exact tests added.

## 9. Automated Verification Results

Table:
| Command/Test | Result | Count/Notes |

## 10. Failures / Scoped Fixes

List any encountered failures and fixes.

## 11. Lint / Warning Delta

## 12. API 29 Result

## 13. API 36 Result

## 14. Release Build Result

## 15. Final Git State

Branch, HEAD, modified files, no commit.

## 16. Manual Validation Required

## 17. Issue #3 Status

Must remain OPEN.

## 18. Verdict

Choose exactly:

- **IMPLEMENTATION VERIFIED AUTOMATICALLY — MANUAL VALIDATION REQUIRED**
- **IMPLEMENTATION COMPLETE**
- **BLOCKED**

Then STOP.

Do not start another issue.
