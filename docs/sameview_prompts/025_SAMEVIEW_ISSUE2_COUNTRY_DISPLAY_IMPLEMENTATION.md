# Issue #2 — IMPLEMENTATION: Locale-Aware Country Display Across All User-Facing Surfaces

## Authorization

The preceding scope confirmation is **APPROVED**.

Implement exactly the confirmed Issue #2 scope now.

Do **not** start another analysis gate.
Do **not** produce another scope-confirmation gate.
Do **not** stop for approval unless repository reality contradicts the confirmed scope or a genuine blocker appears.

One fix only: finish Issue #2 so canonical Country identity is localized consistently across every confirmed user-facing Android surface.

---

## Repository / Safety Baseline

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`issue/2-country-selection`

Expected committed HEAD before the current uncommitted work:

`e24930d` — `docs: define canonical country metadata for issue #2`

The working tree already contains the previously approved, tested, **uncommitted Issue #2 picker/storage implementation**.

Before editing:

1. Confirm branch and HEAD.
2. Confirm the working tree contains only the expected Issue #2 files plus no unrelated changes.
3. Preserve all existing uncommitted Issue #2 work.
4. Do not reset, stash, discard, overwrite, rebase, amend, or reformat unrelated code.
5. Confirm metadata remains v6.
6. Confirm Issue #2 is still OPEN.

If repository state materially differs, STOP and report the blocker. Otherwise implement immediately.

---

# Binding Behavior

## Canonical display rule

For every user-facing Country display:

- valid canonical `location.countryCode` -> localized country name for the **current SameView UI locale**
- missing/invalid/unresolvable `countryCode` -> stored `location.country` unchanged as fallback
- valid code + no stored country -> localized country name
- neither -> no Country

Examples:

| Stored metadata | UI locale | Visible Country |
|---|---|---|
| `country=Germany`, `countryCode=DE` | DE | `Deutschland` |
| `country=Germany`, `countryCode=DE` | EN | `Germany` |
| `country=Deutschland`, `countryCode=DE` | EN | `Germany` |
| `country=Germany`, no code | DE | `Germany` |
| `country=Östereich`, no code | EN | `Östereich` |
| `country=Germany`, `countryCode=ZZZ` | DE | `Germany` |
| no country, `countryCode=DE` | DE | `Deutschland` |
| neither | any | no Country |

Lowercase persisted `"de"` is **invalid legacy metadata** for resolver purposes. Do not silently normalize/repair it. Fall back to stored Country.

## Place Name and City

Never translate or canonicalize:

- `location.displayName`
- `location.city`

`München` remains `München` in English UI.

## Persistence

Display localization must NEVER:

- rewrite `metadata.json`
- rewrite the stored `country` snapshot
- infer/backfill a code
- mark Edit Session dirty because locale changed
- trigger a metadata version bump

Only explicit picker selection/clear changes stored Country metadata.

---

# Implementation Scope

Implement the scope confirmed in the previous report.

## 1. `CountryCatalog.kt`

Extend the existing uncommitted `CountryCatalog` with the centralized resolver.

Required semantics:

- ISO alpha-2 validation against `Locale.getISOCountries()`
- exact/case-sensitive uppercase membership
- explicit `Locale` parameter
- no implicit `Locale.getDefault()` inside the resolver
- valid code -> localized name
- invalid/missing code -> raw stored Country fallback
- valid code without snapshot -> localized name
- neither -> null/empty according to existing nullable convention
- no mutation
- no inference from Country text
- offline
- no dependency

Do not create a second resolver elsewhere.

---

## 2. Edit Session

Relevant existing uncommitted files include:

- `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`

Implement localization **only at render/display time**.

The ViewModel's raw `locationCountryField` / `locationCountryCodeField` and dirty-state baseline must remain persistence-oriented.

Required:

- collect/use the existing Country code state in the screen
- resolve visible Country through `CountryCatalog`
- use the current Compose UI locale explicitly
- locale-only recomposition must not mark dirty
- unrelated save must preserve raw stored snapshot/code
- explicit picker selection/clear semantics remain exactly as already implemented

Do not move localized display values into ViewModel persistence state.

If `EditSessionViewModel.kt` requires no further change for this clarification, leave it untouched.

---

## 3. `SessionScanner` / `ScannedSession`

Modify:

`app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt`

Add optional `locationCountryCode` to `ScannedSession`.

Parse `location.countryCode` centrally.

Rules:

- missing -> null
- preserve malformed raw code as read; resolver decides validity
- do not infer from Country
- do not mutate metadata
- do not validate/drop legacy values at scan time
- no metadata-version change

Use a trailing/defaulted nullable model field if compatible with current model construction.

---

## 4. MainActivity / Compare propagation

Modify:

`app/src/main/java/com/isardomains/sameview/MainActivity.kt`

Propagate `locationCountryCode` through the existing Compare data path only as required.

No navigation changes.
No unrelated MainActivity cleanup.

---

## 5. Compare

Modify:

`app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`

Resolve Country once before the existing location-formatting/candidate logic.

Use explicit current Compose locale.

Do not change:

- location ordering
- separators
- truncation logic
- responsive behavior
- fullscreen behavior
- Compare navigation
- any non-Country rendering

---

## 6. Compare Library

Modify:

`app/src/main/java/com/isardomains/sameview/ui/compare/CompareLibraryScreen.kt`

Resolve the session Country using `session.locationCountryCode` before existing location formatting.

The same resolved location should naturally feed existing visible text/accessibility description if they already share the value.

Do not redesign tiles or accessibility semantics.

---

## 7. Share Comparison Image

Modify only the necessary production path, expected:

`app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`

Add Country code to the existing metadata snapshot/read path.

Resolve Country in the central location-line computation using an explicit locale from the current SameView resource configuration.

Preview and final bitmap must use the same resolved location line.

Do not modify renderers if they already consume precomputed text.

Do not alter:

- Share layout
- image quality
- dimensions
- branding
- date formatting
- City/Place Name
- metadata persistence

---

## 8. Video Export

Modify only the necessary production path, expected:

`app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt`

Add Country code to the existing overlay metadata snapshot/read path.

Resolve Country in the central location-line computation using an explicit locale from current SameView resource configuration.

Preview and final rendered overlay must use the same resolved location line.

Do not modify the renderer/pipeline if it already receives precomputed text.

Do not alter:

- rendering algorithm
- export timing
- quality
- aspect ratio
- overlays other than localized Country text
- date formatting
- City/Place Name

---

# Documentation

Update only the docs confirmed necessary:

- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/VIDEO_EXPORT_V1.md`

Keep changes minimal.

Document the distinction:

- `country` = persisted human-readable snapshot/fallback
- `countryCode` = canonical identity
- SameView user-facing surfaces localize Country from valid code
- localization is display-only
- raw metadata is not rewritten on language change
- City/Place Name remain stored free text
- scanner now carries optional `locationCountryCode` where relevant
- Share/Video output use current SameView locale and preview/final agree

Do not rewrite historical sections or unrelated specs.

Do not modify `IMPLEMENTATION_NOTES.md` yet unless the source-of-truth rules make it strictly necessary for consistency; the confirmed scope says no change.

---

# Tests

Modify/add only the confirmed focused tests.

## Resolver

`app/src/test/java/com/isardomains/sameview/ui/compare/CountryCatalogTest.kt`

Cover:

- DE + `DE` -> `Deutschland`
- EN + `DE` -> `Germany`
- stored `Deutschland` + `DE` + EN -> `Germany`
- missing code -> exact stored fallback
- invalid `ZZZ` -> exact stored fallback
- lowercase `de` -> exact stored fallback
- valid code + missing Country -> localized name
- neither -> null/no Country

## Edit Session

`app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt`

Cover the localized visible value and prove persistence/dirty-state remains raw.

Reuse existing tests where they already prove explicit selection, clear, legacy preservation, and unrelated-save preservation. Do not duplicate tests unnecessarily.

## Scanner

`app/src/androidTest/java/com/isardomains/sameview/storage/SessionScannerTest.kt`

Add focused Country-code parsing/preservation coverage.

If an existing `SessionScannerV6Test` is the correct established home for one of these assertions, use it only if actually present and necessary.

## Compare

`app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt`

Add focused DE/EN localized Country coverage.

## Library

`app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareLibraryScreenTest.kt`

Add focused localized Country coverage.

## Share

`app/src/test/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModelTest.kt`

Prove localized Country and legacy fallback in the existing computed location/caption path.

## Video

`app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt`

Prove localized Country and preview/final overlay location parity.

---

# Failure Handling

If a test fails:

1. Identify the exact failing point.
2. Determine whether it is:
   - production defect introduced by this implementation,
   - stale test expectation,
   - environment/provisioning issue.
3. Fix only if the correction is inside this approved Issue #2 scope.
4. Do not suppress tests.
5. Do not disable lint.
6. Do not introduce baselines.
7. Do not refactor unrelated code.
8. Report every failure and correction honestly.

If fixing a genuine failure requires an unapproved file outside this scope, STOP before editing that file and report the blocker.

---

# Verification

Run focused tests first, then build/release verification.

Use the repository's actual supported Gradle syntax. Do not blindly use `--tests` with instrumentation if unsupported; use the already-established instrumentation runner class filtering / managed-device pattern.

Required:

1. focused unit tests for:
   - `CountryCatalogTest`
   - `ShareComparisonViewModelTest`
   - `CreateVideoViewModelTest`
2. focused instrumentation for:
   - `EditSessionScreenTest`
   - `SessionScannerTest` (and V6 scanner test only if touched/needed)
   - `CompareScreenTest`
   - `CompareLibraryScreenTest`
3. `./gradlew assembleDebug`
4. `./gradlew lintDebug`
5. targeted affected instrumentation on `pixel2Api29`
6. targeted affected instrumentation on `pixel2Api36`
7. `./gradlew assembleRelease`
8. `./gradlew bundleRelease`
9. `git diff --check`
10. full diff/status review

Do not run the full 930-test instrumentation suite unless a failure or shared-code effect gives a concrete reason.

Report exact test counts and any delta.

---

# Manual Validation

After automated verification, report these as still required unless you actually perform them on a real device:

- DE Edit Session
- EN Edit Session
- DE/EN Compare
- DE/EN Compare Library
- Share preview + generated image
- Video preview + exported video
- legacy no-code Country
- airplane mode/offline
- language change does not mutate metadata

No additional TalkBack pass is required unless this implementation changes interaction semantics unexpectedly.

---

# Forbidden Changes

Do not:

- introduce metadata v7
- alter `SUPPORTED_VERSIONS`
- translate City
- translate Place Name
- infer Country code from legacy Country text
- normalize malformed persisted codes
- add network/geocoder calls
- add INTERNET
- add dependencies
- change permissions
- change GPS
- refactor location formatting
- redesign UI
- alter Camera
- alter navigation
- alter Compare behavior beyond Country display
- alter Share layout/rendering beyond Country text
- alter Video rendering beyond Country text
- touch Issue #3
- push/merge
- close Issue #2 before manual validation
- commit before reporting implementation + verification results

---

# Git / Commit

Do **not** commit automatically at the end of implementation.

Leave the verified implementation uncommitted.

Issue #2 remains OPEN.

After automated + manual validation is confirmed by the user, a later final commit can use `Closes #2`.

---

# Required Final Report

Return exactly:

## 1. Baseline / Modified Files

List every modified/new file, distinguishing pre-existing Issue #2 uncommitted work from files newly touched by this implementation.

## 2. Central Resolver Implementation

Exact validation/fallback behavior.

## 3. Edit Session Result

Display, dirty-state, persistence.

## 4. Scanner / Propagation Result

`ScannedSession`, scanner, MainActivity.

## 5. Compare Result

## 6. Compare Library Result

## 7. Share Result

Explicit preview/final behavior.

## 8. Video Result

Explicit preview/final behavior.

## 9. Metadata / Legacy Result

Confirm v6, raw snapshot preservation, malformed-code fallback, no inference.

## 10. Documentation Changes

Exact docs/sections changed.

## 11. Test Changes

Exact test files and cases added/changed.

## 12. Automated Verification Results

Table:
| Command / Test | Result | Count / Notes |

Include failures encountered and exact scoped fixes.

## 13. Lint / Warning Delta

## 14. API 29 Result

## 15. API 36 Result

## 16. Release Build Result

## 17. Final Git State

Branch, HEAD, all modified/untracked files, no commit.

## 18. Manual Validation Still Required

## 19. Issue #2 Status

Must remain OPEN.

## 20. Verdict

Choose exactly:

- **IMPLEMENTATION VERIFIED AUTOMATICALLY — MANUAL VALIDATION REQUIRED**
- **IMPLEMENTATION COMPLETE**
- **BLOCKED**

Then STOP.

Do not start Issue #3.
