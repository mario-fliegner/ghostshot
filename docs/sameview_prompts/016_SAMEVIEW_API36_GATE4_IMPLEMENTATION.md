# Gate 4 — Implementation: API 36 Documentation Synchronization + Close Issue #1

## Objective

Implement the final documentation synchronization for the completed Android 16 / API 36 migration.

This is a **docs-only implementation gate**.

Approved outcomes:

- update the current SDK baseline in `CLAUDE_PROJECT_INSTRUCTION.md`
- update the current SDK baseline and add one concise Android 16 / API 36 migration-status block in `IMPLEMENTATION_NOTES.md`
- preserve all historical audit/planning records
- create the final documentation commit with:
  `Closes #1`

Do not change code, Gradle, tests, manifests, dependencies, permissions, resources, or Git history.

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
5. Confirm the Gate 3B commit is present.
6. Confirm standalone `pixel2Api36` is present and is not part of `allPixel2Devices`.
7. Confirm GitHub Issue #1 is still:
   - OPEN
   - titled `Target Android 16 / API level 36 for Google Play compliance`
   - body describes the Android 16 / API 36 migration
8. If anything differs unexpectedly, STOP and report exact state.

Do not stash, reset, discard, amend, squash, rebase, or rewrite prior commits.

---

## Source of Truth

Re-read immediately before editing:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Use the completed Gate 4 analysis and Gate 4 scope-confirmation report as authoritative for this docs-only implementation.

---

# Approved Files

Exactly two files may change:

1. `CLAUDE_PROJECT_INSTRUCTION.md`
2. `IMPLEMENTATION_NOTES.md`

If any third file appears necessary, STOP and report why.

---

# 1. `CLAUDE_PROJECT_INSTRUCTION.md`

Update only the current SDK baseline.

Expected current block:

```text
minSdk = 29
targetSdk = 35
compileSdk = 35
```

Change only to:

```text
minSdk = 29
targetSdk = 36
compileSdk = 36
```

Do not change:
- architecture rules
- permission allow-list
- storage rules
- privacy/offline guarantees
- Hosted Comparison addendum
- workflow/change-discipline rules
- historical notes
- unrelated formatting

This document must remain a concise current source of truth, not a migration changelog.

---

# 2. `IMPLEMENTATION_NOTES.md`

## A. Current baseline line

Change:

```text
minSdk 29 / targetSdk 35
```

to:

```text
minSdk 29 / targetSdk 36 / compileSdk 36
```

Do not alter adjacent unrelated release/status text.

---

## B. Add one concise migration-status block

Insert under the current project/release status area, at the scope-confirmed insertion point, before the section separator and without duplicating long historical logs.

Use this heading:

```markdown
### Android 16 / API 36 migration
```

Record only the following verified facts, concisely and factually:

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29` unchanged
- deprecated `announceForAccessibility()` usage for the two Camera warning bubbles was replaced by Compose `LiveRegionMode.Polite`
- real-device TalkBack verification passed, including repeat announcement
- real-device Android 16 verification passed for:
  - Camera predictive-back cancel/complete
  - marker-edit back
  - Compare normal/fullscreen back behavior
  - Compare Library selection-mode back
  - Edit Session dirty-state guard
  - Create Video render-state back guard
  - Settings permission-dialog back behavior
  - Walkthrough
  - system-bar/inset smoke checks
- automated verification:
  - 828/828 unit tests
  - 930/930 instrumentation tests on API 35
  - 930/930 instrumentation tests on API 36
  - standalone `pixel2Api36` managed device
  - existing API 29/33/35 managed devices unchanged
- build/release verification:
  - debug build passed
  - release APK build passed
  - release AAB build passed
  - R8/resource shrinking passed
  - local release artifacts remain unsigned if that is still factually true

Keep the style consistent with the existing file.

Do not add speculative statements.
Do not add dependency-upgrade discussion beyond the verified fact that none was required if that fact belongs naturally in the block.
Do not rewrite older chronological entries.

---

# Historical Preservation — Mandatory

These must remain untouched:

- `RELEASE_HARDENING_AUDIT_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- all historical implementation-plan documents
- old versionCode/date snapshots
- prior historical `SM-S911B (Android 16)` test-run entries in `IMPLEMENTATION_NOTES.md`

In particular:
- do **not** change the dated `RELEASE_HARDENING_AUDIT_V2.md` header from SDK 35 to 36
- do **not** create `RELEASE_HARDENING_AUDIT_V3.md`
- do **not** rewrite history to make it look current

---

# Explicitly Forbidden

Do not change:

- `app/build.gradle.kts`
- any Kotlin source
- any test
- any manifest
- permissions
- resources
- dependencies/plugins
- managed-device configuration
- versionCode/versionName
- GitHub Issue #1 body/title/labels
- any historical audit or plan
- any prior commit

No refactor.
No cleanup.
No unrelated doc formatting.

---

# Verification

After editing, run exactly these checks.

## 1. Git status

```text
git status --short
```

Expected:
- only `CLAUDE_PROJECT_INSTRUCTION.md`
- only `IMPLEMENTATION_NOTES.md`

## 2. Diff hygiene

```text
git diff --check
```

Must pass.

## 3. Exact docs diff

```text
git diff -- CLAUDE_PROJECT_INSTRUCTION.md IMPLEMENTATION_NOTES.md
```

Review and confirm:
- only approved SDK baseline changes
- one concise API-36 migration-status block
- no unrelated formatting or historical rewrite

## 4. Current SDK grep

Run a targeted grep/search over current docs for:
- `compileSdk`
- `targetSdk`
- `minSdk`
- `API 35`
- `API 36`
- `Android 15`
- `Android 16`
- `SDK 35`
- `SDK 36`

Classify any remaining SDK-35 occurrence:
- current source-of-truth error
- historical record that must remain

Success requires:
- no CURRENT source-of-truth still incorrectly stating `compileSdk = 35` or `targetSdk = 35`
- historical dated records may still contain 35 and must remain untouched

## 5. Historical-file protection check

Confirm by git status/diff that:
- `RELEASE_HARDENING_AUDIT_V1.md` unchanged
- `RELEASE_HARDENING_AUDIT_V2.md` unchanged
- implementation-plan history unchanged

## 6. No Gradle rerun

Do **not** rerun:
- `clean`
- unit tests
- instrumentation tests
- `assembleDebug`
- `assembleRelease`
- `bundleRelease`
- lint

This is a docs-only change and the full migration was already technically verified.

If any non-doc file changes unexpectedly, STOP.

---

# Commit

Only after all verification checks pass:

Create exactly one final documentation commit with:

```text
docs: finalize Android 16 API 36 migration

Closes #1
```

Do not amend or squash prior migration commits.

Do not push or merge.

After commit, verify:

```text
git status --short
git log -1 --oneline
```

Expected:
- working tree clean
- latest commit is the docs-finalization commit

---

# GitHub Issue #1

Because the live issue has already been re-verified as:

**MIGRATION ISSUE — SAFE TO CLOSE**

the commit footer:

```text
Closes #1
```

is approved.

Do not manually close the issue in this prompt.

The issue should close through the normal GitHub repository workflow when the commit reaches the default branch, depending on repository/GitHub semantics.

Do not falsely report it as already closed while still only on the migration branch.

---

# Required Final Report

Return exactly:

## 1. Modified Files

## 2. Exact Documentation Changes

Separate:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`

## 3. Historical Preservation

Confirm historical audit/planning documents stayed unchanged.

## 4. Verification Results

Report:
- `git status --short`
- `git diff --check`
- current-SDK grep result
- historical-SDK occurrences and why preserved
- exact diff scope

## 5. Tests / Builds

State explicitly:
- NOT RUN
- not required because this gate changed docs only
- technical migration had already passed all required build/test/device verification

## 6. Commit Result

Report:
- exact commit hash
- exact subject/body
- confirm `Closes #1`
- confirm no amend/squash/rewrite

## 7. Final Git State

Report:
- branch
- HEAD
- clean/dirty
- files modified after commit

## 8. Issue #1 Status Note

State:
- commit contains `Closes #1`
- issue may remain open until this commit reaches the default branch
- do not manually claim closure yet unless GitHub already shows it closed

## 9. Gate 4 Verdict

Choose exactly one:

- **GATE 4 PASSED — MIGRATION READY FOR MERGE**
- **GATE 4 FAILED**

If passed:
- state that the Android 16 / API 36 migration branch is ready for final merge/review
- stop

Do not merge to `main`.
Do not push unless separately instructed.
