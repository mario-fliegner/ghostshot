# Gate 4 — Analysis Only: API 36 Documentation Synchronization + GitHub Issue #1

## Objective
Analyze exactly which SameView source-of-truth documents must be updated now that the Android 16 / API 36 migration is technically complete and verified. This is analysis only.

Do not modify files, documentation, code, Gradle, tests, GitHub issues, commits, branches, or history.

Repository: `C:\data\work\privat\git-repos\sameview`
Required branch: `upgrade/android-16-api-36`

## Baseline
Confirm before analysis:
- exact branch
- clean working tree
- current HEAD
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- Gate 3B commit present
- standalone `pixel2Api36` present

If anything differs unexpectedly, STOP.

Treat these as completed and do not reopen them:
- compileSdk 36
- targetSdk 36
- API-36 accessibility migration
- real-device TalkBack verification
- real-device Android-16 predictive-back/navigation/camera/compare/insets verification
- 828/828 unit tests
- 930/930 API-35 instrumentation tests
- 930/930 API-36 instrumentation tests
- debug/release/bundle builds green
- lint 0 errors
- no dependency modernization required
- no new production permission introduced

## 1. Mandatory documentation review
Read at minimum:
- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `SETTINGS_UX_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `SESSION_ORIGINALS_V1.md`
- `SESSION_ORIGINALS_PRIVACY_V1.md`
- `SESSION_BACKUP_EXPORT_V1.md`
- `SHARE_COMPARISON_IMAGE_V1.md`
- `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `VIDEO_EXPORT_V1.md`

Also inspect any newer current doc that states SDK levels, Android support, managed-device coverage, Android 16 behavior, predictive back, accessibility announcements, or release-validation status.

Do not rewrite historical implementation plans or dated audit records simply because they mention older SDK levels.

## 2. SDK/API documentation inventory
Search the documentation tree for:
- `compileSdk`
- `targetSdk`
- `minSdk`
- `API 35`
- `API 36`
- `Android 15`
- `Android 16`
- `SDK 35`
- `SDK 36`

Classify every relevant occurrence:
- CURRENT SOURCE OF TRUTH — UPDATE REQUIRED
- CURRENT SOURCE OF TRUTH — NO CHANGE
- HISTORICAL RECORD — PRESERVE
- STALE BUT OUTSIDE THIS MIGRATION — DO NOT TOUCH
- AMBIGUOUS — NEEDS USER DECISION

For every required update report exact file, heading/section, current wording, proposed factual replacement, and reason.

Do not do a global 35→36 replacement.

## 3. Facts the current docs may need to record
Determine where these belong, if anywhere:

### SDK baseline
- minSdk 29
- compileSdk 36
- targetSdk 36

### Android 16 verification
Real-device verification passed for:
- Camera + predictive-back cancel/complete
- marker edit
- Compare normal/fullscreen
- Library selection mode
- Edit Session dirty-state guard
- video-rendering back guard
- Settings dialog flow
- Walkthrough
- system-bar/inset smoke checks

### Automated coverage
- API 35: 930/930 instrumentation tests
- API 36: 930/930 instrumentation tests
- standalone `pixel2Api36`
- existing API 29/33/35 devices remain

### Accessibility migration
- deprecated `announceForAccessibility()` removed
- Compose `LiveRegionMode.Polite`
- repeat announcement verified with real TalkBack

### Build/release
- debug build passed
- release APK passed
- release AAB passed
- R8/resource shrinking passed
- local release artifacts remain unsigned if still true

Prefer `IMPLEMENTATION_NOTES.md` for implementation/test status rather than stuffing verification history into UX specs.

## 4. Historical integrity
Be strict:
- preserve historical implementation plans
- preserve historical audit facts/dates
- do not rewrite `RELEASE_HARDENING_AUDIT_V1.md`
- if `RELEASE_HARDENING_AUDIT_V2.md` is a dated snapshot, determine whether it should remain historically intact rather than being rewritten
- do not rewrite old versionCode history

If current source-of-truth docs point to stale historical data, propose the cleanest current-status correction without falsifying history.

## 5. Predictive Back documentation
The migration verified targetSdk-36 predictive back with existing Compose `BackHandler`; no production-code change was required.

Determine minimal documentation:
- whether existing `COMPARE_FLOW_V1.md` back contract already suffices
- whether `IMPLEMENTATION_NOTES.md` should record Android-16 verification
- avoid adding platform mechanics to UX specs unless required

## 6. Accessibility documentation
The implementation mechanism changed, user-visible behavior did not.

Determine whether:
- `CAMERA_WORKFLOW_UX_V1.md` needs an update
- `IMPLEMENTATION_NOTES.md` is sufficient
- `GUIDE_TIPS_UX_V1.md` should remain untouched because it concerns a different feature

Prefer minimal docs.

## 7. Managed-device documentation
Determine whether standalone `pixel2Api36` belongs in:
- `IMPLEMENTATION_NOTES.md`
- another current testing/release section
- nowhere

Do not imply it is part of `allPixel2Devices` if it is not.

## 8. GitHub Issue #1 — mandatory live verification
The user explicitly wants Issue #1 considered in the final migration commit:

`https://github.com/mario-fliegner/sameview/issues/1`

Inspect the live issue using GitHub/`gh issue view 1` or equivalent.

Report:
- issue number
- exact current title
- open/closed state
- body purpose summary
- labels if relevant
- whether the body actually describes the API-36 migration

### Known inconsistency to verify
Immediately before this prompt, the live GitHub page showed:

Title:
`Target Android 16 / API level 36 for Google Play compliance`

but the body described the unrelated walkthrough-replay UX question ("Show walkthrough again" returns to How It Works) and explicitly said it was not a confirmed bug.

Do not ignore this mismatch.

Classify Issue #1 as exactly one:
- MIGRATION ISSUE — SAFE TO CLOSE
- MIGRATION ISSUE — REFERENCE ONLY
- TITLE/BODY MISMATCH — DO NOT CLOSE
- UNRELATED ISSUE — DO NOT REFERENCE
- UNAVAILABLE / CANNOT VERIFY

If title/body still conflict:
- do not recommend `Closes #1`
- do not edit/close the issue
- clearly report what must be corrected in GitHub before a final closing commit, if appropriate

## 9. Final commit / issue-reference strategy
Analyze only.

If Issue #1 is truly the migration issue, choose the correct final reference:
- `Closes #1`
- `Fixes #1`
- `Refs #1`

Base this on actual issue content and whether the final commit truly resolves it.

Do not squash/rewrite prior migration commits unless explicitly requested.

Also decide whether the final documentation step should be:
- a documentation-only commit referencing #1
- or another form of migration-closure commit

## 10. Expected future file scope
List all and only documentation files that genuinely need modification.

For each:
- purpose
- whether behavior changes (expected: no)
- how historical content is preserved

If a doc does not need updating, exclude it.

## 11. Verification plan for future docs implementation
At minimum consider:
- `git diff --check`
- targeted grep confirming no CURRENT source-of-truth still incorrectly states compileSdk/targetSdk 35
- targeted grep proving historical docs were not unintentionally rewritten
- `git status --short`
- exact diff review

State which Gradle/build/test commands do NOT need rerunning for docs-only changes.

## Required final report
Return exactly:

### 1. Branch / Baseline State
### 2. SDK/API Documentation Inventory
Table: File | Section | Classification | Required action
### 3. Documents That Must Change
### 4. Exact Proposed Documentation Changes
### 5. Historical Documents to Preserve
### 6. Predictive Back Documentation Decision
### 7. Accessibility Documentation Decision
### 8. Managed-Device Documentation Decision
### 9. GitHub Issue #1 Verification
Include live title/body/state and classification.
### 10. Final Commit / Issue Reference Recommendation
### 11. Verification Plan
### 12. Gate 4 Verdict

Verdict:
- READY FOR DOCUMENTATION SCOPE CONFIRMATION
- BLOCKED BY ISSUE #1 INCONSISTENCY
- BLOCKED

If the live Issue #1 title/body mismatch still exists, prefer `BLOCKED BY ISSUE #1 INCONSISTENCY` for the issue-closing/final-commit decision while still completing the documentation analysis.

Then STOP.

## Final safety rules
- Analysis only.
- No file changes.
- No GitHub issue edits.
- No code/Gradle/test changes.
- No commit/push/merge.
- Preserve historical records.
- Never use `Closes #1` unless the live issue is unambiguously the API-36 migration issue.
