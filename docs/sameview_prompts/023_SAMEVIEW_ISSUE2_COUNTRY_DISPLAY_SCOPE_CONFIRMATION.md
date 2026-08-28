# Issue #2 — Scope Confirmation: Locale-Aware Country Display Across All User-Facing Surfaces

## Objective

Confirm the exact implementation scope for the final Issue #2 correction:

> Whenever a valid canonical `location.countryCode` exists, **all user-facing Country displays in SameView** must render the localized country name for the current SameView UI locale.

This is **scope confirmation only**.

Do not modify files.
Do not implement code.
Do not modify tests/resources/docs.
Do not commit, push, merge, rebase, amend, stash, reset, discard, or edit GitHub issues.

After the report, STOP and wait for explicit user approval.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`issue/2-country-selection`

Expected committed HEAD before the existing uncommitted implementation:

`e24930d` — `docs: define canonical country metadata for issue #2`

There are already uncommitted Issue #2 changes in the working tree. Preserve them.

Before analysis:

1. Confirm exact active branch.
2. Confirm current HEAD.
3. Confirm working tree is dirty only with the existing Issue #2 implementation files.
4. List every modified/untracked file.
5. Confirm no unrelated file is modified.
6. Confirm Issue #2 remains OPEN.
7. Confirm metadata remains v6.
8. Do not clean or alter the working tree.

If any unexpected file/state exists, STOP.

---

# Source of Truth

Read and treat as authoritative:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/VIDEO_EXPORT_V1.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Also inspect the current uncommitted Issue #2 implementation.

---

# Binding Product Rule

This is final and must not be reopened.

## Country display

If a valid canonical `countryCode` exists:

- DE UI: `DE` -> `Deutschland`
- EN UI: `DE` -> `Germany`

If `countryCode` is missing/invalid/unresolvable:

- display the stored `country` string exactly as fallback

If only a valid `countryCode` exists and `country` is absent:

- display the localized country name from the code

If neither exists:

- display no Country

## Metadata mutation

Display localization must never mutate metadata.

Changing app language:
- must not rewrite `location.country`
- must not add/remove `countryCode`
- must not mark Edit Session dirty

Unrelated saves:
- must preserve the stored `country` snapshot unless the user explicitly changes Country

Only explicit Country selection/clear changes stored Country metadata.

## Place Name / City

Never translate:
- `location.displayName`
- `location.city`

Example EN UI:

`Marienplatz · München, Germany`

not:

`Marienplatz · Munich, Germany`

---

# Central Resolver — Confirm Exact Design

The prior analysis concluded that the current/new `CountryCatalog` should be the central home for user-facing Country resolution.

Confirm exact API/contract.

The resolver must:

1. accept:
   - stored `country: String?`
   - stored `countryCode: String?`
   - explicit `Locale`
2. validate `countryCode` against the actual ISO alpha-2 set
3. if valid:
   - return localized display name for the supplied locale
4. if missing/invalid/unresolvable:
   - return stored `country` unchanged
5. if valid code exists but country snapshot is absent:
   - return localized name
6. if neither exists:
   - return null/empty according to existing formatting convention
7. never mutate data
8. never infer from raw Country text
9. remain offline/API29+
10. remain dependency-free

Confirm whether lowercase persisted codes such as `"de"` count as invalid fallback or are normalized only for lookup.

Preferred safety rule:
- do not silently “repair” malformed persisted metadata
- explicit picker-generated codes are uppercase and valid
- malformed legacy persisted code falls back to stored `country`

If current specs require otherwise, report the conflict.

---

# Explicit Locale Rule

Confirm that every user-facing resolver call receives an explicit `Locale`.

Do not allow Share/Video/ViewModels to resolve using implicit `Locale.getDefault()` on their own.

Use the current SameView UI locale source appropriate for each context.

Confirm exact source for:

- Compose screens
- Compare
- Library
- Share image generation
- Video preview
- Video final render/background work

If non-Compose rendering requires locale to be passed through an existing ViewModel/render request/data object, identify the smallest path.

---

# Edit Session — Required Scope

Confirm exact changes needed in the existing uncommitted files.

Required behavior:

Stored:
```json
"country": "Germany",
"countryCode": "DE"
```

DE UI trigger shows:
`Deutschland`

EN UI trigger shows:
`Germany`

But ViewModel dirty-tracked/persisted snapshot remains:
`Germany`

until explicit Country action.

Confirm:

- localization happens at display/render level
- ViewModel does not replace stored snapshot just because locale changed
- language-only re-render does not create dirty state
- unrelated save preserves original `country`
- explicit select writes localized name + code
- explicit clear removes both
- legacy no-code Country displays exact stored value

Identify exact existing uncommitted files needing revision.

---

# ScannedSession / SessionScanner — Required Scope

The audit found current `ScannedSession`/`SessionScanner` do not carry `countryCode`, while Compare/Library depend on them.

Confirm exact changes required:

- add optional `locationCountryCode` to `ScannedSession` if this is still the smallest central path
- parse optional `location.countryCode`
- missing code -> null
- malformed code preserved as raw string or null? choose one based on resolver design
- no validation/mutation during scan
- no inference from `country`
- no metadata-version change

List every consumer affected by the `ScannedSession` signature change.

---

# Complete User-Facing Surfaces — Lock Scope

The prior repo-wide audit found exactly these additional user-facing Country surfaces beyond Edit Session:

1. Compare
2. Compare Library
3. Share Comparison Image
4. Video Export

Confirm there are no other production user-facing Country displays.

For each, lock the exact file(s), code path, locale source, and required behavior.

## Compare

Confirm:
- exact file/function building visible location
- use `countryCode` when present
- current locale passed explicitly
- formatting/punctuation unchanged

## Compare Library

Confirm:
- exact card/list/header path
- localized Country from valid code
- no other visual change

## Share Comparison Image

Confirm:
- preview and final bitmap use the same localized Country rule
- exact file(s)
- explicit locale propagation
- no metadata rewrite
- City/Place Name untouched

## Video Export

Confirm:
- preview and final rendered overlay use the same localized Country rule
- exact file(s)
- explicit locale propagation to background/rendering path if required
- no preview/final mismatch
- no metadata rewrite
- City/Place Name untouched

Do not alter unrelated rendering/layout.

---

# Storage / Serialization — Explicitly Unchanged

Confirm no additional changes are required to:

- metadata v6
- `SessionStorage` persistence semantics beyond existing Issue #2 work
- raw backup export
- import/restore format
- Share metadata serialization
- Video metadata persistence
- version constants
- supported versions

Display localization must not leak into persistence.

---

# Documentation Scope

The prior analysis found that the committed Issue #2 specs may need a minimal clarification because storage snapshot and user-facing display are now explicitly different.

Determine exact docs that must change.

Expected likely candidates:

- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/VIDEO_EXPORT_V1.md`

But include only those whose current wording would otherwise contradict the new display rule.

For each:
- exact section
- exact rule to add/change
- preserve historical content
- do not rewrite unrelated docs

Do not modify docs in this scope-confirmation step.

---

# Test Scope — Lock Exact Tests

Confirm exact test files/classes.

## Country resolver / catalog

Must cover at least:

- DE + `DE` -> `Deutschland`
- EN + `DE` -> `Germany`
- stored `Deutschland` + code `DE` + EN -> `Germany`
- missing code -> stored fallback unchanged
- invalid code -> stored fallback unchanged
- valid code + no stored country -> localized name
- neither -> no country
- malformed/lowercase rule
- no mutation

## Edit Session

Must cover:

- UI display localizes from code
- language/display change does not dirty ViewModel
- unrelated save does not rewrite stored snapshot
- explicit selection writes localized snapshot + code
- legacy no-code remains exact
- explicit clear unchanged from current Issue #2 work

## Compare

Must cover localized Country display from code.

## Compare Library

Must cover localized Country display from code.

## Share

Must cover:
- preview
- final generated bitmap/text path if separately testable
- DE/EN localization

## Video

Must cover:
- preview
- final render text path
- preview/final parity
- DE/EN localization

## API coverage

Choose targeted API 29 + API 36 instrumentation where relevant.

Do not require full 930-test suites unless actual touched shared code justifies them.

## Manual validation

Confirm final manual checks:
- DE/EN
- Compare
- Library
- Share preview/generated image
- Video preview/export
- legacy no-code Country
- offline
- no extra TalkBack pass unless interaction semantics changed beyond the already-tested picker

---

# Exact File Scope — Mandatory

Produce the exact full file list for the next implementation.

Separate:

## A. Existing uncommitted files that need revision

## B. Additional production files

## C. Additional test files

## D. Documentation files

For each:
- full repo-relative path
- exact reason

Do not include “maybe” files.

If any file remains uncertain, verdict must be BLOCKED/USER DECISION REQUIRED.

---

# Explicitly Unchanged

Confirm no changes to:

- metadata version
- supported versions
- Gradle
- dependencies
- manifests
- permissions
- INTERNET
- geocoding/GPS
- City translation
- Place Name translation
- formatting punctuation/order
- Camera
- unrelated Compare behavior
- unrelated Share layout
- unrelated Video behavior
- backup format
- WebApp repo
- Issue #3
- historical records

---

# Verification Plan After Implementation

Lock exact commands.

At minimum determine:

1. focused unit tests
2. focused instrumentation tests
3. `./gradlew assembleDebug`
4. `./gradlew lintDebug`
5. targeted API 29 instrumentation
6. targeted API 36 instrumentation
7. `./gradlew assembleRelease`
8. `./gradlew bundleRelease`

If documentation files are changed in the same implementation:
- `git diff --check`
- exact doc diff review

Do not over-test unrelated functionality.

---

# Issue #2 Closure

Issue #2 remains OPEN during implementation.

Once:
- original picker behavior
- metadata persistence
- all user-facing Country localization
- DE/EN
- legacy fallback
- Share/Video parity
- manual checks

are verified, the final commit may use:

`Closes #2`

Do not close/reference now.

---

# Required Final Report

Return exactly:

## 1. Branch / Working Tree Baseline

## 2. Central Resolver Contract

## 3. Locale Propagation Contract

## 4. Edit Session Scope

## 5. ScannedSession / Scanner Scope

## 6. Compare Scope

## 7. Compare Library Scope

## 8. Share Scope

## 9. Video Scope

## 10. Storage / Serialization Explicitly Unchanged

## 11. Documentation Scope

Table:
| File | Section | Exact change |

## 12. Exact Files to Modify

Table:
| File | Existing/New | Type | Exact change |

No uncertain entries.

## 13. Exact Test Scope

## 14. Verification Plan

## 15. Risks

Only risks caused by this clarification.

## 16. Issue #2 Closure Plan

## 17. Scope Verdict

Choose exactly one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **USER DECISION REQUIRED**
- **BLOCKED**

Then STOP.

No implementation until explicit user approval.
