# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 6 IMPLEMENTATION

## Status / Authorization

**BLOCK 6A has been reviewed and explicitly approved.**

You are now authorized to implement **exactly the corrected Block 6 scope** defined below.

This is an implementation gate.

Do not expand the scope.  
Do not implement future handoff/network behavior.  
Do not add artificial production code solely for testing.

---

# 1. Objective

Implement the corrected Block 6 boundary:

1. complete the disposable Wackelbild temp-file cleanup infrastructure;
2. safely remove stale/orphaned Wackelbild cache contents on fresh Wackelbild ViewModel creation;
3. update the implementation documentation so the source-of-truth accurately reflects the corrected sequencing.

Block 6 does **not** yet implement active-operation cancellation because no real Wackelbild preparation/handoff operation exists in production yet.

The real operation lifecycle — including `operationJob`, `operationDir`, `finally`/`NonCancellable`, `onCleared()` cancellation, success/error/cancel cleanup and force-kill-during-active-operation validation — remains deferred until the block that introduces the real operation trigger.

---

# 2. Mandatory pre-implementation verification

Before editing anything, re-read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Then inspect the current versions of:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManagerTest.kt`
- `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt`

Also verify current `git status --short`, branch and HEAD.

If repository state materially differs from the approved Block 6A baseline or if a new source-of-truth conflict appears, **STOP without implementing** and report the conflict.

Do not silently adapt scope.

---

# 3. Authorized files

You may modify **only these six files**:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
3. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManagerTest.kt`
4. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt`
5. `docs/IMPLEMENTATION_NOTES.md`
6. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

If implementation genuinely requires a seventh file, **STOP** and report why. Do not modify it.

Pre-existing unrelated working-tree files, prompt archives or other user-owned untracked files must remain untouched.

---

# 4. WackelbildTempFileManager implementation

Preserve the existing creation API and behavior.

The resulting manager should expose the existing methods plus the approved cleanup methods:

```kotlin
class WackelbildTempFileManager(private val cacheDir: File) {
    fun createOperationDir(operationId: String = UUID.randomUUID().toString()): File
    fun referenceCandidateFile(operationDir: File): File
    fun captureCandidateFile(operationDir: File): File

    fun deleteOperationDir(dir: File)
    fun sweepStaleOperationDirs()
}
```

Adapt only if the current source uses an equivalent existing signature that must be preserved for compatibility. Do not refactor unrelated code.

## 4.1 Dedicated root

All disposable Wackelbild operation files belong exclusively below:

`<cacheDir>/wackelbild/`

No cleanup method may delete anything outside that dedicated root.

In particular, cleanup must never touch:

- `filesDir/sessions/`
- session originals
- `reference.jpg`
- `capture.jpg`
- `metadata.json`
- MediaStore
- arbitrary cache locations outside the Wackelbild root.

## 4.2 `deleteOperationDir(dir)`

Implement targeted recursive cleanup with these requirements:

- `dir` must resolve to a valid direct child of the dedicated Wackelbild cache root;
- use canonical/normalized filesystem containment logic robust enough to prevent `..`/path-traversal escape;
- the Wackelbild root itself must not be accepted as an operation directory;
- a sibling/outside path must not be deleted;
- a path resolving outside the root must not be deleted;
- missing path = safe no-op;
- repeated cleanup = safe/idempotent;
- nested files/directories inside a valid operation directory are recursively removed;
- cleanup is best-effort and must not crash the app merely because an individual disposable file cannot be removed.

Do not weaken containment safety for convenience.

Do not follow a path outside the dedicated root during recursive cleanup.

Use the smallest implementation consistent with the repository's current Java/Kotlin/Android filesystem conventions.

## 4.3 `sweepStaleOperationDirs()`

Implement the approved no-argument sweep.

Semantics:

- if `<cacheDir>/wackelbild/` does not exist: safe no-op;
- enumerate its direct children;
- every direct child is stale/orphaned at fresh ViewModel creation and may be removed;
- valid operation directories are removed recursively;
- unknown files directly inside the dedicated Wackelbild root are also disposable and must be removed safely;
- do not invent an age threshold;
- do not add an `excluding` parameter;
- do not preserve previous-operation contents;
- never traverse/delete outside the dedicated root;
- failure to delete one disposable child must not cause unrelated session/user data to be touched.

The root may remain present and empty after sweep. Do not add unnecessary root recreation/deletion behavior unless the current implementation naturally requires it.

---

# 5. WackelbildViewModel implementation

Add only the currently justified integration:

**one orphan sweep per fresh Wackelbild ViewModel instance.**

Use the existing dispatcher/testability conventions already present in this ViewModel.

Expected behavior:

```text
WackelbildViewModel created
        ↓
init
        ↓
viewModelScope.launch(ioDispatcher)
        ↓
tempFileManager.sweepStaleOperationDirs()
```

Requirements:

- filesystem sweep runs off the main thread in production;
- it runs once per ViewModel creation;
- it does not run on Compose recomposition;
- it does not run on every `ON_RESUME`;
- it does not alter the existing tilt lifecycle;
- it does not change current date-metadata behavior;
- it does not change session/reference/capture resolution behavior;
- it does not add UI state;
- it does not create an operation directory.

Follow the existing internal constructor/test-seam style only as much as necessary to inject/control `WackelbildTempFileManager` in JVM tests.

Do not introduce a broad DI/refactor.

---

# 6. Explicitly forbidden ViewModel additions

Do **not** add:

- `operationJob`
- `operationDir`
- `onCleared()` operation cancellation
- renderer ownership
- `WackelbildPrintRenderer` dependency/reference
- `WackelbildDimensionResolver` dependency/reference
- `DateBadgeRenderer` dependency/reference
- `finally`/`NonCancellable` around a fake operation
- fake preparation methods
- debug preparation methods
- hidden/non-UI renderer trigger
- busy/upload state
- CTA state
- upload state
- retry state
- checkout state.

There is no real operation yet, so none of this belongs in Block 6.

---

# 7. Tests — WackelbildTempFileManagerTest

Add focused tests for the real cleanup implementation.

At minimum cover:

1. `deleteOperationDir_removesDirectoryRecursively`
2. `deleteOperationDir_isIdempotent`
3. `deleteOperationDir_missingDirectory_isNoOp`
4. `deleteOperationDir_removesNestedCandidateFiles`
5. `deleteOperationDir_pathOutsideRoot_isRejectedSafely`
6. `deleteOperationDir_cannotDeleteSessionsDirectory`
7. `sweepStaleOperationDirs_removesOrphanOperationDirectories`
8. `sweepStaleOperationDirs_removesUnknownChildFiles`
9. `sweepStaleOperationDirs_missingRoot_isSafeNoOp`
10. `sweepStaleOperationDirs_emptyRoot_isSafeNoOp`
11. `sweepStaleOperationDirs_doesNotTraverseOutsideRoot`

The tests must prove safety, not merely line coverage.

For the outside-root/session tests:

- create real sentinel files;
- invoke cleanup;
- assert those sentinel files still exist and their contents are unchanged where useful.

For recursive cleanup:

- use nested directories/files so the test proves recursion.

For idempotency:

- execute cleanup more than once.

If symlink behavior is practical and supported reliably by the existing JVM test environment, inspect whether a containment regression test is warranted. Do **not** introduce flaky platform-dependent tests merely to force symlink coverage.

Do not weaken existing tests.

---

# 8. Tests — WackelbildViewModelTest

Add only tests for the real init-time sweep behavior.

At minimum:

1. `init_triggersSweepExactlyOnce`
2. `init_sweep_removesStaleOperationDirectory`
3. `init_sweep_doesNotMutateSessionFiles`

Requirements:

- use the existing deterministic dispatcher strategy;
- do not use sleeps;
- do not depend on real wall-clock timing;
- prove one ViewModel instance does not repeatedly sweep due to unrelated lifecycle/state activity;
- keep all existing tilt/date/metadata tests unchanged unless a minimal constructor adjustment is required by the approved dependency injection.

If an existing test helper needs a minimal parameter/default update to construct the ViewModel with the temp-file manager, make only that targeted change.

---

# 9. Tests explicitly NOT to add

Do not create tests for behavior that still does not exist:

- renderer cancellation;
- operation-job cancellation;
- cleanup-after-render failure;
- cleanup-after-render success;
- upload-success cleanup;
- upload-cancel cleanup;
- final-upload-error cleanup;
- `onCleared()` active-operation cleanup;
- ViewModel ↔ `WackelbildPrintRenderer` integration;
- Custom Tab behavior;
- force-kill during active preparation.

Those belong to the block where a real operation exists.

No instrumentation test is required solely for this Block 6 implementation unless a real implementation issue discovered during work proves JVM testing insufficient. If that happens, STOP before expanding file scope.

---

# 10. Documentation — implementation plan

Update:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

with the **smallest possible sequencing correction**.

The correction must make clear that:

### Implemented in Block 6
- temp cleanup primitives;
- safe targeted operation-dir deletion;
- unconditional orphan sweep at fresh Wackelbild ViewModel creation.

### Resequenced to the first block that introduces the real operation
- active `operationJob`;
- active `operationDir`;
- cancellation;
- `finally`/`NonCancellable` cleanup;
- `onCleared()` cancellation;
- cleanup on real success/cancel/final-error;
- force-kill-during-active-operation validation.

Important:

- this is a sequencing correction, **not an architectural reversal**;
- the eventual three-tier lifecycle design remains:
  1. real operation `finally` cleanup for normal in-process termination/cancellation;
  2. ViewModel destruction cancels the real active operation;
  3. next-entry sweep recovers leftovers after hard process death;
- explicitly avoid claiming `onCleared()` or `finally` is guaranteed during hard process death.

Do not rewrite unrelated sections.

---

# 11. Documentation — IMPLEMENTATION_NOTES

Append/update the appropriate Block 6 status entry in:

`docs/IMPLEMENTATION_NOTES.md`

Record only what actually shipped:

- `WackelbildTempFileManager` cleanup support;
- containment/idempotency;
- one-time ViewModel init sweep;
- no UI/network/render-operation wiring;
- active-operation cancellation and `onCleared()` deliberately deferred because no production operation exists yet;
- hard process death recovered by next-entry sweep, not by a false teardown guarantee.

Do not rewrite historical Block 1–5 entries.

---

# 12. Regression safety

Explicitly preserve:

- Block 1 export-menu behavior;
- Block 2 navigation/preview;
- Block 3 tilt/swipe behavior and 9°/6° tuning;
- Block 4 date toggle/badge behavior;
- Block 4D responsive no-scroll preview geometry;
- Block 5 print renderer;
- Block 5E dynamic ratio parity correction;
- session/original immutability;
- offline behavior outside explicitly approved future online flow.

No changes to:

- UI
- strings
- navigation
- camera
- sensors
- renderer
- session storage
- manifest
- Gradle
- permissions.

---

# 13. Forbidden scope

Strictly forbidden in this block:

- network/API code
- `INTERNET` permission
- OkHttp
- Retrofit
- partner key
- BuildConfig key injection
- uploads
- retry/backoff
- Custom Tabs
- checkout URL
- CTA
- confirmation dialog
- pricing/product UI
- order status
- WorkManager
- foreground service
- DataStore
- telemetry
- analytics
- tracking
- unrelated refactoring
- formatting cleanup
- renames.

---

# 14. Required verification

Run:

```text
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Because this corrected Block 6 scope contains no new UI and no real Android lifecycle operation, an instrumentation run is not required by default.

However:

- do not suppress failures;
- do not modify unrelated tests to make the suite green;
- if a failure appears unrelated, investigate enough to establish whether it is pre-existing/independent;
- if implementation reveals Android-specific behavior that cannot honestly be verified by JVM tests, STOP and report that before expanding scope.

No physical-device force-kill-during-preparation test is required now because no real preparation operation exists yet.

---

# 15. Implementation discipline

Implement only after verifying the baseline.

Keep changes minimal.

Do not:

- refactor unrelated code;
- rename classes/files;
- reformat unrelated code;
- add abstractions for future use;
- add dormant state;
- add test-only production behavior;
- "prepare" networking;
- touch a seventh file.

If any approved assumption proves false, STOP rather than redesigning.

---

# 16. Required final report

After implementation, report exactly:

## 1. Repository Baseline
- branch
- HEAD at start
- initial status

## 2. Files Modified
List all modified files and confirm exactly six authorized files or explain why implementation stopped before changes.

## 3. TempFileManager Implementation
Describe:
- containment
- recursive deletion
- idempotency
- sweep behavior
- unknown-child handling
- outside-root/session protection

## 4. ViewModel Integration
Describe:
- one-time init sweep
- dispatcher
- test injection
- confirm no operation state/cancellation/renderer wiring was added

## 5. Documentation Correction
Describe the exact sequencing correction.

## 6. Regression Safety
Explicitly confirm protected Blocks 1–5 behavior was untouched.

## 7. Tests / Verification
For every command:
- exact command
- result
- failures if any

Include counts for the new/updated tests where available.

## 8. Deferred Lifecycle Work
Explicitly list:
- operationJob
- operationDir
- finally/NonCancellable
- onCleared cancellation
- real success/cancel/final-error cleanup
- force-kill active-operation validation

and state why they remain deferred.

## 9. Diff Scope
Confirm no unauthorized file/refactor/format churn.

## 10. Physical-Device Status
State that no active-operation force-kill validation was performed because no real operation exists yet.

## 11. Gate Result

If successful, end exactly:

**BLOCK 6 IMPLEMENTED — READY FOR REVIEW**

If blocked before/during implementation, end:

**BLOCK 6 BLOCKED — USER DECISION REQUIRED**

and explain the precise blocker.

---

# Final instruction

The purpose of this block is **temp-file safety now**, not simulated lifecycle completeness.

Implement the smallest real production behavior that exists at this stage:

**safe disposable-cache cleanup + next-entry orphan recovery.**

Do not manufacture an operation merely so cancellation code has something to cancel.
