# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 1C: BASELINE TEST VERIFICATION ONLY

## Purpose

Verify whether the four failing `CompareScreenTest` tests reported after DeinWackelbild Block 1 already fail on the unchanged pre-Block-1 baseline.

This is a **verification-only gate**.

Do not modify code.
Do not modify tests.
Do not modify documentation.
Do not fix the failures.
Do not begin Block 2.
Do not commit or push.

Baseline commit to verify:

`2572f89f3f887cb4337866ea073279f67143933b`

The four tests are:

- `metadataHeader_landscape_showsTitleAndLocation`
- `metadataHeader_landscape_showsLocationWhenNoTitle`
- `metadataHeader_landscape_noSeparateHeaderComponent`
- `metadataHeader_landscape_showsCreatedFallback_whenNoMetadata`

The verification target is the configured Gradle Managed Device:

`pixel2Api29`

---

# 1. Preserve the Current Working Tree

The current working tree contains approved/uncommitted Block 1 work plus pre-existing documentation changes/untracked directories.

Do **not** discard, reset, stash-and-forget, overwrite, or otherwise endanger those changes.

Before doing anything, record:

- current branch;
- current HEAD;
- `git status --short`.

Use a safe isolated mechanism to test the baseline commit, preferably a temporary Git worktree checked out at the exact baseline commit.

Do not switch the existing working directory itself to the baseline if that risks the current uncommitted work.

The temporary baseline worktree must not alter the primary working tree.

---

# 2. Baseline Verification

In the isolated baseline checkout at exactly:

`2572f89f3f887cb4337866ea073279f67143933b`

confirm:

- `git rev-parse HEAD` equals the baseline hash;
- baseline worktree is clean.

Then run only the four named tests against `pixel2Api29`.

Use the narrowest valid Gradle Managed Device command supported by this repository, with instrumentation runner arguments selecting those exact methods if practical.

If Gradle/Android instrumentation filtering cannot reliably select four individual methods in one invocation, run them individually.

Do not run the entire suite unless method-level filtering is technically unavailable.

Do not modify tests to make filtering work.

---

# 3. Interpretation

There are only three valid outcomes.

## Outcome A — all four also fail on baseline

This demonstrates that the four failures were already reproducible before Block 1 on the same baseline/device configuration.

Conclusion:

**Block 1 did not introduce these four failures.**

Do not fix them here.

They may be tracked separately outside DeinWackelbild scope.

## Outcome B — one or more pass on baseline

This means the prior claim that all four failures are pre-existing is not established.

Report exactly which tests differ.

Do not attempt a fix.

Conclusion:

**Block 1 requires regression investigation before approval.**

## Outcome C — baseline verification cannot be executed reliably

Examples:

- Managed Device infrastructure failure;
- baseline cannot build for an unrelated environmental reason;
- exact test selection cannot be made and no safe alternative exists.

Report the blocker precisely.

Do not infer a result.

---

# 4. Cleanup

After the baseline run:

- remove the temporary worktree safely if one was created;
- return attention to the original working tree;
- verify its branch, HEAD, and `git status --short`;
- confirm the existing Block 1 changes and pre-existing changes are still present and untouched.

Do not run `git reset --hard` against the primary working tree.

Do not clean untracked project directories from the primary working tree.

---

# 5. Required Final Response

Return exactly:

## 1. Original Working Tree

- branch
- HEAD
- initial `git status --short`

## 2. Baseline Checkout

- mechanism used
- exact baseline HEAD
- confirmation that it was isolated from the current Block 1 working tree

## 3. Tests Run

For each of the four tests, report:

- exact Gradle command or filtering mechanism;
- PASS / FAIL;
- failure exception/location if failed.

## 4. Comparison With Block 1 Run

Table:

| Test | Baseline | Block 1 run | Result |
|---|---|---|---|
| metadataHeader_landscape_showsTitleAndLocation | ... | FAIL | ... |
| metadataHeader_landscape_showsLocationWhenNoTitle | ... | FAIL | ... |
| metadataHeader_landscape_noSeparateHeaderComponent | ... | FAIL | ... |
| metadataHeader_landscape_showsCreatedFallback_whenNoMetadata | ... | FAIL | ... |

Use `PRE-EXISTING/INDEPENDENT` only when the same test fails on baseline in the same relevant way.

Use `REGRESSION INVESTIGATION REQUIRED` if it passes on baseline or fails materially differently.

## 5. Original Working Tree After Verification

- branch
- HEAD
- final `git status --short`
- confirm no project file was modified by this verification gate

## 6. Gate Result

Choose exactly one:

- **BLOCK 1 BASELINE VERIFIED — FOUR FAILURES PRE-EXISTING/INDEPENDENT — BLOCK 1 READY FOR APPROVAL**
- **BLOCK 1 BASELINE MISMATCH — REGRESSION INVESTIGATION REQUIRED**
- **BLOCK 1 BASELINE VERIFICATION BLOCKED**

Then STOP.

Do not begin Block 2.

---

# Final Rule

This gate answers one question only:

**Did those same four tests already fail at commit `2572f89f3f887cb4337866ea073279f67143933b` on `pixel2Api29`?**

No fixes.
No implementation.
No cleanup.
No Block 2.
