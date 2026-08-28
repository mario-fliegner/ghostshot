# Gate 4 — Scope Confirmation: API 36 Documentation Synchronization + Issue #1 Closure

## Objective

Confirm the exact documentation-only scope for closing the completed Android 16 / API 36 migration.

This prompt is **scope confirmation only**.

Do not modify files.
Do not edit GitHub issues.
Do not commit, push, merge, or close Issue #1.

The intended implementation is:
- update exactly two current source-of-truth documents
- preserve all historical audit/planning records
- prepare a final documentation commit that closes GitHub Issue #1, but only if the live issue now clearly describes the API-36 migration

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
   - `targetSdk = 36`
   - `minSdk = 29`
5. Confirm the Gate 3B managed-device commit is present.
6. Confirm standalone `pixel2Api36` is present.
7. If anything differs unexpectedly, STOP and report exact state.

Do not stash/reset/discard anything.

---

## Source of Truth

Re-read at minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also use the completed Gate 4 analysis report as the approved basis.

Do not modify any historical audit or implementation-plan document.

---

## Mandatory Live Re-Verification of GitHub Issue #1

The user has now manually corrected GitHub Issue #1.

Before approving the docs implementation scope, verify the **current live issue** again:

`https://github.com/mario-fliegner/sameview/issues/1`

Use `gh issue view 1`, GitHub access, or equivalent.

Confirm:

- issue number: `#1`
- exact title
- state
- body now describes the Android 16 / API 36 migration
- body no longer contains the unrelated walkthrough-replay UX content
- label state, if relevant

### Required classification

Choose exactly one:

- **MIGRATION ISSUE — SAFE TO CLOSE**
- **MIGRATION ISSUE — REFERENCE ONLY**
- **TITLE/BODY MISMATCH — DO NOT CLOSE**
- **UNRELATED ISSUE — DO NOT REFERENCE**
- **UNAVAILABLE / CANNOT VERIFY**

Only if the result is:

**MIGRATION ISSUE — SAFE TO CLOSE**

may the future final commit use:

`Closes #1`

If the issue is still inconsistent, STOP the issue-closing part and report the mismatch.

Do not edit or close the issue in this scope-confirmation prompt.

---

# Approved Expected Documentation Scope

Exactly two files are expected to change:

1. `CLAUDE_PROJECT_INSTRUCTION.md`
2. `IMPLEMENTATION_NOTES.md`

If any additional current documentation file is genuinely required, explain why and mark the scope **BLOCKED** rather than expanding silently.

---

# 1. `CLAUDE_PROJECT_INSTRUCTION.md`

Confirm the exact current SDK baseline section.

Expected factual update only:

```diff
- minSdk = 29
- targetSdk = 35
- compileSdk = 35
+ minSdk = 29
+ targetSdk = 36
+ compileSdk = 36
```

Do not change:
- architecture rules
- permission allow-list
- storage rules
- privacy/offline guarantees
- Hosted Comparison addendum
- change-discipline rules
- unrelated wording

No new migration history should be added here unless absolutely required.

This document should remain a concise current source of truth, not a changelog.

---

# 2. `IMPLEMENTATION_NOTES.md`

Confirm the exact current baseline line and the exact insertion point for the new Android 16 / API 36 status block.

Expected baseline change:

```diff
- minSdk 29 / targetSdk 35
+ minSdk 29 / targetSdk 36 / compileSdk 36
```

Expected new concise status block under the current project status / release status area:

### Android 16 / API 36 migration

Record these verified facts only:

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29` unchanged
- API-36 accessibility migration:
  - deprecated `announceForAccessibility()` removed from the two Camera warning bubbles
  - replaced with Compose `LiveRegionMode.Polite`
  - real-device TalkBack verification passed, including repeat announcement
- real-device Android 16 verification passed for:
  - Camera predictive-back cancel/complete
  - marker edit back
  - Compare normal/fullscreen predictive back
  - Compare Library selection-mode back
  - Edit Session dirty-state back guard
  - Create Video render-state back guard
  - Settings permission-dialog back behavior
  - Walkthrough
  - system-bar/inset smoke checks
- automated coverage:
  - 828/828 unit tests
  - 930/930 instrumentation tests on API 35
  - 930/930 instrumentation tests on API 36
  - standalone `pixel2Api36`
  - existing API 29/33/35 devices unchanged
- build/release verification:
  - debug build passed
  - release APK passed
  - release AAB passed
  - R8/resource shrinking passed
  - local release artifacts remain unsigned if that is still factually true

Keep this concise and factual.

Do not duplicate large chronological test logs already present elsewhere in the file.

---

# Historical Documents — Must Remain Untouched

Confirm explicitly that these remain byte-for-byte untouched:

- `RELEASE_HARDENING_AUDIT_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- historical implementation plans
- old versionCode/date snapshots
- historical `SM-S911B (Android 16)` test-run entries in `IMPLEMENTATION_NOTES.md`

The historical `RELEASE_HARDENING_AUDIT_V2.md` header must not be rewritten from 35→36 because it reflects the SDK state at its audit date.

If a future V3 audit is desirable, mention it as a separate future task only; do not create it here.

---

# Predictive Back Documentation

Confirm:

- `COMPARE_FLOW_V1.md` does not need a change because its existing back contract remains correct
- no predictive-back implementation detail needs to be added to UX specs
- `IMPLEMENTATION_NOTES.md` is the appropriate place to record successful Android-16 verification

---

# Accessibility Documentation

Confirm:

- `CAMERA_WORKFLOW_UX_V1.md` does not need a change because user-visible behavior did not change
- `GUIDE_TIPS_UX_V1.md` remains untouched
- `IMPLEMENTATION_NOTES.md` is sufficient for recording the internal API-36 accessibility mechanism migration

---

# Managed Device Documentation

Confirm:

- `IMPLEMENTATION_NOTES.md` may record the new standalone `pixel2Api36`
- do not imply it belongs to `allPixel2Devices`
- do not alter existing managed-device group configuration

---

# Future Implementation Verification Plan

For the later docs-only implementation, confirm these checks:

1. `git status --short`
2. `git diff --check`
3. `git diff -- CLAUDE_PROJECT_INSTRUCTION.md IMPLEMENTATION_NOTES.md`
4. targeted grep for current SDK statements:
   - `compileSdk`
   - `targetSdk`
   - `minSdk`
   - `API 35`
   - `API 36`
5. verify current source-of-truth no longer incorrectly states targetSdk/compileSdk 35
6. verify historical audit/planning files were not touched
7. verify only the two approved docs changed

No Gradle build/test rerun is required for a docs-only change.

Explicitly state:
- `clean` not required
- unit tests not required
- instrumentation not required
- assembleDebug not required
- assembleRelease not required
- bundleRelease not required
- lint not required

unless a non-doc file unexpectedly changes.

---

# Final Commit Strategy

If Issue #1 live verification returns:

**MIGRATION ISSUE — SAFE TO CLOSE**

then the future final documentation commit should use a commit message that clearly communicates migration closure and includes:

`Closes #1`

Recommended shape:

```text
docs: finalize Android 16 API 36 migration

Closes #1
```

Do not commit in this scope-confirmation prompt.

Do not squash or rewrite prior migration commits.

---

# Required Final Report

Return exactly:

## 1. Branch / Baseline

## 2. GitHub Issue #1 Re-Verification

Include:
- title
- state
- body summary
- labels if relevant
- classification

## 3. Files to Modify

Expected:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`

## 4. Exact Planned Changes

Describe each file separately.

## 5. Explicitly Unchanged / Historical Preservation

## 6. Predictive Back Documentation Decision

## 7. Accessibility Documentation Decision

## 8. Managed-Device Documentation Decision

## 9. Verification Plan

## 10. Final Commit Strategy

Only recommend `Closes #1` if live issue verification is unambiguously safe.

## 11. Scope Verdict

Choose exactly one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **BLOCKED BY ISSUE #1**
- **BLOCKED**

Then STOP.

---

## Final Safety Rules

- No file changes.
- No GitHub issue edits.
- No docs edits.
- No code/Gradle/test changes.
- No commit.
- No push.
- No merge.
- Preserve historical documents.
- Never use `Closes #1` unless live verification confirms the issue is now the API-36 migration issue.
