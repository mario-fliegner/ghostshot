# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 10C: A/B REGRESSION CHECK FOR 5 INSTRUMENTATION FAILURES

## Purpose

Block 10 implementation is complete, but commit is intentionally paused because the required `pixel2Api29` instrumentation run produced 5 reproducible failures outside the Block-10 change surface.

The current claim "pre-existing/unrelated" is plausible but not yet proven by a direct A/B baseline comparison.

This gate exists only to answer one question:

**Do the exact same 5 tests also fail on the pre-Block-10 baseline commit `2cb743c` on the same `pixel2Api29` Managed Device?**

This is a verification-only gate.

Do not modify production code.
Do not modify tests.
Do not modify docs.
Do not fix the 5 failures.
Do not change Block 10.
Do not commit anything.
Do not add files to the repository.
Do not make a live network request.
Do not expose the real partner key.

---

# 1. Exact failing tests to verify

Run exactly these five tests on the baseline:

### CompareScreenTest
- `metadataHeader_landscape_showsTitleAndLocation`
- `metadataHeader_landscape_showsLocationWhenNoTitle`
- `metadataHeader_landscape_noSeparateHeaderComponent`
- `metadataHeader_landscape_showsCreatedFallback_whenNoMetadata`

### EditSessionScreenTest
- `referenceDate_laterThanCapture_showsOrderErrorText_de`

Do not run unrelated tests unless needed to establish the test command syntax.

---

# 2. Baseline commit

The authoritative pre-Block-10 baseline is:

`2cb743c`

This is the Block-9 commit immediately before Block-10 implementation.

Do not reset or alter the user's current primary working tree.

The current Block-10 changes must remain untouched and uncommitted in the primary working tree.

---

# 3. Isolation mechanism

Use a detached temporary Git worktree based on exactly:

`2cb743c`

Preferred pattern:

```bash
git worktree add --detach <temporary-path> 2cb743c
```

Requirements:

- worktree must live outside the primary repository working directory
- do not copy any tracked Block-10 files into it
- do not checkout/reset the primary worktree
- do not stash the user's Block-10 changes
- do not commit anything.

If Android SDK configuration requires `local.properties` to build:

- do **not** read/print its contents;
- do **not** modify the real primary `local.properties`;
- if necessary, copy the file byte-for-byte into the temporary worktree only;
- never print the key/content;
- this copied file must remain untracked and be deleted with the temporary worktree.

If copying `local.properties` is not necessary because the environment already resolves the SDK, do not copy it.

---

# 4. Before running tests

Inside the temporary worktree verify:

```bash
git rev-parse HEAD
git status --short
```

Expected:

- HEAD exactly `2cb743c`
- clean working tree, except possibly an untracked/ignored copied `local.properties`.

Record this in the report.

Do not inspect or output secret values.

---

# 5. Exact test execution

Use the already-configured Gradle Managed Device:

`pixel2Api29`

Run the narrowest possible invocation selecting exactly the 5 tests.

Preferred single invocation if supported by the project's instrumentation runner:

```bash
./gradlew pixel2Api29DebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class="com.isardomains.sameview.ui.compare.CompareScreenTest#metadataHeader_landscape_showsTitleAndLocation,com.isardomains.sameview.ui.compare.CompareScreenTest#metadataHeader_landscape_showsLocationWhenNoTitle,com.isardomains.sameview.ui.compare.CompareScreenTest#metadataHeader_landscape_noSeparateHeaderComponent,com.isardomains.sameview.ui.compare.CompareScreenTest#metadataHeader_landscape_showsCreatedFallback_whenNoMetadata,com.isardomains.sameview.ui.editsession.EditSessionScreenTest#referenceDate_laterThanCapture_showsOrderErrorText_de"
```

Before running, verify the exact package/class path of `EditSessionScreenTest` from the baseline source. Do not guess the package if it differs.

If the runner cannot filter across two classes in one `class` argument, use two invocations:

1. the four `CompareScreenTest` methods
2. the one `EditSessionScreenTest` method

Do not broaden to the full suite unless filter syntax itself fails.

---

# 6. Authoritative result source

Do not rely only on Gradle console summary.

Read the generated JUnit XML results and report for each of the five tests:

- pass/fail
- exception type
- source line if applicable
- assertion/message summary.

Confirm the XML contains exactly the intended tests for each invocation.

If line numbers differ between Block 9 and Block 10 due to additive lines, compare the underlying source statement, not just the numeric line.

---

# 7. Comparison against Block-10 result

Compare baseline behavior to the already-observed Block-10 behavior:

## Block-10 observed failures

### CompareScreenTest
All four:
- FAIL
- `java.lang.NullPointerException`
- at the existing `CountryCatalog.resolveDisplayName(...)` path in `CompareScreen.kt`

### EditSessionScreenTest
- FAIL
- assertion cannot find the German order-error string after scroll

Determine whether baseline is:

### Outcome A — same failures on baseline
If all 5 fail on `2cb743c` with materially identical failure modes:

Classification:

**PRE-EXISTING / INDEPENDENT OF BLOCK 10**

Block 10 may be approved for commit from a regression standpoint.

### Outcome B — any test passes on baseline
If one or more of the five pass on `2cb743c` but fail with Block 10:

Classification:

**POTENTIAL BLOCK-10 REGRESSION**

Do not modify anything.
Do not guess cause.
Stop and report exact difference.

### Outcome C — baseline fails differently
If a test fails on baseline but with a materially different exception/assertion:

Classification:

**NOT YET PROVEN INDEPENDENT**

Do not approve Block 10.
Report exact divergence.

---

# 8. Primary working tree protection

After test execution:

- return to the primary worktree;
- verify primary branch/HEAD and `git status --short`;
- confirm the exact Block-10 uncommitted file set is unchanged from before this verification gate.

Do not modify any Block-10 file.

Then remove the temporary worktree cleanly:

```bash
git worktree remove <temporary-path>
git worktree prune
```

If emulator/helper processes temporarily lock the scratch worktree, stop only those disposable helper processes as needed and remove the worktree.

Do not touch repository content to work around cleanup.

Finally verify:

```bash
git worktree list
```

Only the primary worktree should remain.

---

# 9. No extra fixes

Strictly forbidden in this gate:

- editing `CompareScreen.kt`
- editing `EditSessionScreen.kt`
- editing either test class
- locale fixes
- country-catalog fixes
- Managed Device config changes
- manifest changes
- Block-10 changes
- test suppressions
- retries to "make it green"
- baselines.

This gate only establishes causality.

---

# 10. Required final report

Return exactly:

## 1. Primary Working Tree Before Verification
- branch
- HEAD
- `git status --short`
- confirm Block-10 changes remain uncommitted

## 2. Baseline Worktree
- temp mechanism
- exact baseline HEAD
- baseline working-tree cleanliness
- whether `local.properties` had to be copied, without revealing contents

## 3. Tests Run
List exact command(s).

## 4. Baseline Results
Table:

| Test | Baseline result | Failure type / assertion |

Exactly the five tests.

## 5. Comparison With Block 10
Table:

| Test | Baseline | Block 10 | Classification |

Use one of:
- PRE-EXISTING/INDEPENDENT
- POTENTIAL BLOCK-10 REGRESSION
- NOT YET PROVEN INDEPENDENT

## 6. Causality Verdict

If all five are materially identical on baseline:

**ALL 5 FAILURES PRE-EXIST ON BLOCK-9 BASELINE — BLOCK 10 DID NOT INTRODUCE THEM**

If not, state the exact contrary result.

## 7. Primary Working Tree After Verification
- branch
- HEAD
- final `git status --short`
- confirm no Block-10 file changed during verification

## 8. Worktree Cleanup
Confirm temporary worktree removed and `git worktree list` contains only primary worktree.

## 9. Gate Result

If Outcome A:

**BLOCK 10 BASELINE VERIFIED — 5 FAILURES PRE-EXISTING/INDEPENDENT — BLOCK 10 READY FOR COMMIT**

If Outcome B:

**BLOCK 10 BASELINE CHECK FAILED — POTENTIAL REGRESSION — DO NOT COMMIT**

If Outcome C:

**BLOCK 10 BASELINE CHECK INCONCLUSIVE — DO NOT COMMIT**

Then STOP.

---

# Final instruction

Verification only.

Do not implement or fix anything.

The sole purpose is to prove or disprove whether the 5 Managed-Device failures existed on commit `2cb743c` before Block 10.
