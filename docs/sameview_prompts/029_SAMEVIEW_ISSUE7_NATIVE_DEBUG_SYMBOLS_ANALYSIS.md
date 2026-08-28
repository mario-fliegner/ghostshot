# Issue #7 — ANALYSIS ONLY: Google Play Native Debug Symbols

## Objective

Analyze GitHub Issue #7 for the SameView Android app and determine the smallest correct fix for Google Play's native debug-symbol warning.

This is a **new Claude session**.

This step is **ANALYSIS ONLY**.

Do not modify production files.
Do not modify Gradle files.
Do not modify tests.
Do not modify docs.
Do not commit, push, merge, rebase, amend, stash, reset, or edit GitHub issues.

The only repository-state change allowed in this prompt is creating/checking out a dedicated Issue #7 branch from current `main`, provided the working tree is clean.

After the report, STOP. The next step will be one scope confirmation, followed by implementation after explicit user approval.

---

# Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

## Pre-analysis Git safety

1. Confirm current branch.
2. Confirm current HEAD.
3. Confirm working tree is clean.
4. Confirm local `main` and `origin/main` relationship.
5. Confirm Issue #2 and Issue #3 are already merged/closed as expected.
6. Read GitHub Issue #7 live with `gh issue view 7` or equivalent.
7. If `main` is current and clean, create and check out exactly:

`issue/7-native-debug-symbols`

from current `main`.

8. Report the base commit.
9. Do not commit anything.

If the working tree is dirty or `main` is unexpectedly behind/diverged, STOP and report exact state before creating the branch.

---

# Mandatory Source-of-Truth Review

Before analyzing the Gradle/NDK setup, read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md`

Also inspect:

- `app/build.gradle.kts`
- root `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- any ProGuard/R8 config
- any CMake/NDK/native configuration if present
- merged manifest if relevant

If a current source-of-truth doc already states something about release packaging/native code/debug symbols, treat it as authoritative unless Issue #7 intentionally changes it.

---

# Live Issue #7 — Mandatory

Read the live issue and report:

- issue number
- exact title
- state
- labels
- body summary
- exact Google Play warning/problem it records
- any acceptance criteria
- whether it is release-blocking or advisory

Do not rely on memory.

---

# Core Analysis Question

Determine **why SameView's release bundle contains native code** and why Google Play does not currently receive native debug symbols.

Do not guess.

Trace the actual release dependency graph and AAB contents.

At minimum determine:

1. Which `.so` libraries are present in the release AAB/APK?
2. Which dependency/module contributes each native library?
3. Is the native code authored by SameView or transitive third-party code?
4. Is an NDK/CMake build actually configured in SameView?
5. Is `ndkVersion` configured?
6. Is any native library stripped during release packaging?
7. Does AGP currently generate native debug-symbol output?
8. If not, what exact Gradle configuration is missing?
9. Does Google Play require:
   - FULL symbols
   - SYMBOL_TABLE
   - either
   - something else for this app's actual native artifacts?
10. What is the smallest configuration that satisfies Play while preserving release behavior?

---

# Inspect Actual Release Artifacts

Use the current clean branch baseline.

You may run **read-only verification/build commands** during analysis if needed, because they do not modify source files.

Recommended evidence:

- `./gradlew :app:dependencies`
- `./gradlew :app:bundleRelease`
- inspect the generated `.aab`
- list entries under `base/lib/**`
- inspect build intermediates related to stripped/unstripped native libraries
- inspect whether `native-debug-symbols.zip` or equivalent output already exists

Do not sign/publish/upload anything.

Do not change Gradle to "try" a fix during analysis.

Report exact paths and native library names.

---

# Google Play / AGP Behavior to Verify

Using repository evidence and available tooling/docs, determine the correct AGP mechanism for native debug symbols.

Analyze whether the minimal expected setting is something like:

`android.buildTypes.release.ndk.debugSymbolLevel = ...`

but do **not** assume exact DSL/property placement until verified against the project's current AGP version.

Confirm:

- actual AGP version
- exact DSL supported by that version
- allowed values (`FULL`, `SYMBOL_TABLE`, etc.)
- whether this applies when the app itself has no custom native source but transitive dependencies ship `.so` files
- whether AGP can package symbols for third-party prebuilt `.so` libraries
- whether all required unstripped symbols are actually available from those dependencies
- whether a symbol ZIP is generated automatically by Gradle/Android Studio
- exact expected output location after `bundleRelease`
- whether Google Play Console can also extract sufficient symbols directly from the AAB in this configuration or still expects a separate upload

If any native library is pre-stripped and no unstripped symbols exist, identify that explicitly.

---

# Determine Whether This Is Actually Fixable in SameView

Classify each native library as:

- FULL SYMBOLS AVAILABLE
- SYMBOL TABLE AVAILABLE
- PRE-STRIPPED / NO USEFUL SYMBOLS
- UNKNOWN

Then answer:

- Can SameView generate a useful native debug-symbol package for the whole bundle?
- If only some libraries can provide symbols, is that still worthwhile/accepted?
- Will Google Play warning disappear or only improve symbolication coverage?
- Is the issue safely fixable with one Gradle setting?
- Does any dependency upgrade appear necessary?

Do **not** upgrade dependencies unless unavoidable. This analysis should prefer configuration-only fixes.

---

# Release Safety

Issue #7 must not alter runtime behavior.

Analyze risks to:

- APK/AAB contents
- bundle size
- release signing
- R8/minification/resource shrinking
- ABI packaging
- native library stripping
- Play Store upload compatibility
- crash/ANR symbolication
- install/runtime behavior on API 29–36
- offline/privacy guarantees

Explicitly state whether the change can affect runtime code or only release metadata/artifacts.

---

# Native Symbol Size / Choice

If both `FULL` and `SYMBOL_TABLE` are valid:

Compare them for SameView.

Assess:

### FULL
- size
- debugging value
- whether source-level native debugging is meaningful for transitive libraries

### SYMBOL_TABLE
- size
- crash/ANR symbolication value
- Google Play suitability
- whether it is more appropriate when SameView does not own native C/C++ source

Choose exactly one recommended setting and justify it.

Do not leave this open after analysis unless repository/tooling evidence genuinely prevents a decision.

---

# Documentation Impact

Determine whether any current source-of-truth doc must change after implementation.

Likely candidates:

- `docs/IMPLEMENTATION_NOTES.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md` — probably historical and should remain untouched
- `docs/CLAUDE_PROJECT_INSTRUCTION.md` — only if it contains a current release-build contract affected by this change

Classify:

- REQUIRED
- OPTIONAL
- HISTORICAL — PRESERVE
- NO CHANGE

Do not modify docs now.

---

# Verification Plan for Future Implementation

Define exact verification after the fix.

At minimum consider:

1. `./gradlew clean`
2. `./gradlew :app:assembleRelease`
3. `./gradlew :app:bundleRelease`
4. `./gradlew :app:lintRelease` or existing release lint equivalent
5. inspect AAB native libraries
6. inspect generated native debug-symbol package
7. verify ZIP contents are non-empty and map to the shipped ABIs/libraries
8. verify release APK/AAB still builds
9. compare release artifact size before/after if relevant
10. verify no manifest/permission/runtime changes
11. optionally inspect bundle with Android Studio App Bundle Analyzer

Determine whether unit/instrumentation tests need rerunning. Expected: likely not for a pure release-packaging change, but verify against the actual change.

Determine whether a real-device install/smoke check is warranted after release packaging changes.

---

# Manual Android Studio / Play Console Verification

Provide a later user-facing manual verification plan, but do not perform it now.

Analyze what the user should inspect in Android Studio after implementation:

- where the generated native symbol artifact appears
- App Bundle Analyzer
- native libraries/ABIs
- build output

And what to verify in Play Console on the next upload:

- whether the warning disappears
- where native debug symbols are shown/uploaded
- whether a manual ZIP upload is required

Do not claim Play behavior without evidence.

---

# Scope Discipline

This is one issue only.

Forbidden unless Issue #7 proves it necessary:

- dependency upgrades
- AGP upgrade
- Kotlin upgrade
- target/compile SDK changes
- minSdk changes
- source refactors
- UI changes
- Camera changes
- Compare changes
- metadata changes
- permissions
- networking
- analytics
- Issue #4/#5/#6 work
- unrelated lint cleanup
- build cleanup

Prefer the smallest reversible release-configuration fix.

---

# Required Final Report

Return exactly:

## 1. Repository / Branch State

Include:
- original branch
- main/origin-main state
- created Issue #7 branch
- base commit
- working tree state

## 2. GitHub Issue #7

Include:
- exact title
- state
- labels
- warning/body summary
- acceptance criteria

## 3. Current Release / Build Toolchain

Include:
- AGP
- Gradle
- Kotlin
- compileSdk/targetSdk/minSdk
- NDK/CMake presence/absence
- release minify/shrink state

## 4. Native Library Inventory

Table:

| Native library | ABI(s) | Contributing dependency/module | SameView-owned? | Symbol availability |

## 5. Root Cause

Explain exactly why Play sees native code and why symbols are missing.

## 6. Correct AGP Symbol Mechanism

State:
- exact supported DSL/property
- exact allowed values
- whether it works for the discovered libraries

## 7. FULL vs SYMBOL_TABLE Decision

Choose one and justify.

## 8. Minimal Fix Strategy

One fix only.

No code.

## 9. Exact Expected File Scope

Table:

| File | Why it would change |

Do not include uncertain files.

## 10. Documentation Impact

Table:

| Doc | Classification | Reason |

## 11. Verification Plan

Exact commands and artifact inspections.

## 12. Android Studio / Play Console Manual Verification

## 13. Risks

Table:

| Risk | Classification | Mitigation |

## 14. Issue #7 Closure Strategy

State whether the eventual final verified commit should use:
- `Closes #7`
- `Refs #7`
- or neither

## 15. Analysis Verdict

Choose exactly one:

- **ANALYSIS COMPLETE — READY FOR SCOPE CONFIRMATION**
- **USER DECISION REQUIRED**
- **BLOCKED**

If complete, state the exact next step in one sentence.

Then STOP.

---

# Final Rules

- Analysis only.
- Branch creation is the only permitted repository-state change.
- No source/Gradle/docs modifications.
- No commit.
- No push.
- No merge.
- No GitHub issue edit.
- No dependency/toolchain upgrade unless analysis proves unavoidable.
- Do not start another issue.
