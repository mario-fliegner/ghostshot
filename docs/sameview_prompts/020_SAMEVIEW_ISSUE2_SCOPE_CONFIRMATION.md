# Issue #2 — Scope Confirmation: Country Picker Implementation

## Objective

Confirm the exact implementation scope for GitHub Issue #2 based on the already-approved and committed specifications.

This prompt is **scope confirmation only**.

Do not modify any file.
Do not implement code.
Do not modify tests, resources, docs, Gradle, manifests, dependencies, permissions, or GitHub issues.
Do not commit, push, merge, rebase, or amend anything.

After this report, STOP and wait for explicit user approval.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`issue/2-country-selection`

Expected HEAD includes the spec commit:

`e24930d` — `docs: define canonical country metadata for issue #2`

Before doing anything:

1. Confirm exact active branch.
2. Confirm current HEAD.
3. Confirm working tree is clean.
4. Confirm GitHub Issue #2 is still OPEN.
5. Confirm metadata remains:
   - `METADATA_VERSION = 6`
   - supported versions include v2-v6
6. If anything differs unexpectedly, STOP and report exact state.

Do not stash/reset/discard anything.

---

# Source of Truth

Read and treat as authoritative:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

The specs already define the required behavior. Do not reopen product decisions.

---

# Binding Product Contract

The implementation must satisfy all of this:

- Place Name remains free text.
- City remains free text.
- Country becomes a localized offline picker.
- Full country list visible immediately.
- Search starts from the first typed character.
- Country names displayed in current app language.
- Supported languages: DE / EN.
- No network.
- No geocoding.
- No INTERNET permission.
- No new permission.
- metadata remains v6.
- `location.country` remains optional localized display snapshot.
- `location.countryCode` is new optional uppercase ISO 3166-1 alpha-2 canonical identity.
- explicit country selection writes both `country` + `countryCode`.
- legacy Country strings remain untouched until user explicitly changes Country.
- opening/cancelling picker changes nothing.
- explicit clear removes both fields.
- non-canonical legacy values never block unrelated saves.
- error handling must not corrupt metadata.
- UI must be accessible and localized.

---

# Scope Confirmation Tasks

## 1. Decide the exact offline country-source implementation

Use repository/code evidence and choose one implementation only:

### Preferred candidates

A. Platform/JVM ISO + localized locale data  
B. App-bundled deterministic ISO dataset

Choose the smallest, safest, fully offline approach.

For the chosen approach, confirm:

- API 29 support
- DE/EN display-name behavior
- deterministic enough for persisted `country` snapshots
- alphabetical sorting by current app locale
- search behavior
- fallback if localized name is missing
- no dependency required
- no network required

Do not keep this open after this scope report. Choose one.

---

## 2. Confirm app-locale source

Inspect how SameView currently determines its app language.

State exactly which locale source the implementation will use so:

- picker list language matches current SameView UI language
- persisted `country` snapshot matches the visible selected country name
- DE stores e.g. `Deutschland`
- EN stores e.g. `Germany`
- both use `countryCode = "DE"`

Do not invent a separate locale mechanism.

---

## 3. Confirm exact metadata touchpoints

Trace the new `countryCode` through the current codebase.

Determine exactly which of these must change and which must stay unchanged:

- metadata data/model classes
- `SessionScanner`
- `ScannedSession`
- `SessionStorage`
- `EditSessionViewModel`
- `EditSessionScreen`
- backup/export/import
- share/video consumers
- tests constructing metadata JSON

For each, state:
- CHANGE REQUIRED
- TEST ONLY
- NO CHANGE

---

## 4. Confirm scanner behavior

Choose one exact implementation.

Expected behavior:

- read optional `location.countryCode`
- missing code -> `null`
- do not infer from legacy `country`
- malformed/unknown persisted code must not crash
- malformed code must not silently rewrite metadata
- user may correct it only by explicit Country selection/clear

State whether `ScannedSession` gets a new `locationCountryCode: String?` field.

If not, explain the smaller existing path used instead.

---

## 5. Confirm storage contract

Inspect `SessionStorage.updateLocation()` and all call sites.

Confirm the exact future signature and behavior.

Expected direction:

- `displayName`
- `city`
- `country`
- `countryCode`

Lock down:

- null/blank handling
- uppercasing/validation of `countryCode`
- explicit clear
- removal of stale `countryCode`
- whether the whole `location` object is removed if all values are absent
- atomic write behavior
- pair consistency between `country` and `countryCode`
- malformed legacy values preservation

Do not introduce a new validation subsystem.

---

## 6. Confirm ViewModel / dirty-state behavior

State exact fields/state that must exist.

The scope must preserve these cases:

### Legacy invalid Country
Example:
`country = "Östereich"`, no code

If user edits Title only:
- Country unchanged
- no code invented

### Valid-looking legacy Country
Example:
`country = "Germany"`, no code

If user edits unrelated field:
- no code invented

### Picker open/cancel
- no mutation
- no false dirty-state

### Explicit country select
- selected localized display name + ISO code become current state
- dirty-state becomes true when different from original state

### Explicit clear
- both Country + code cleared
- dirty-state updates correctly

Confirm exactly how original/current values are compared.

---

## 7. Confirm picker UX implementation

Inspect the existing `BrandingSymbolPickerSheet` and other `ModalBottomSheet` patterns.

Choose the exact minimal UX pattern.

Confirm:

- Country trigger field appearance
- current value visible
- legacy invalid value visible
- sheet title
- search field
- full list visible immediately
- row behavior
- selected/current indication if used
- clear action
- dismiss/cancel behavior
- no-result state
- unexpected dataset-empty/error state
- keyboard/focus handling
- responsive behavior
- accessibility behavior

Do not redesign unrelated Edit Session UI.

---

## 8. Confirm search / sorting algorithm

Choose exact behavior:

- filter from first character
- case-insensitive
- localized names
- ISO alpha-2 code as secondary match
- diacritic-friendly

Choose the exact matching rule:
- startsWith
- contains
- startsWith-first-then-contains
- other

Choose exact sorting approach:
- locale-aware Collator
- another justified method

Confirm API 29 support.

---

## 9. Confirm exact resource strings

List all exact new/changed string resource keys required in:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

Include only strings actually needed.

Also decide what happens to the old Country free-text placeholder:
- remove if unused
- keep if still referenced
- do not leave dead resource changes without reason

---

## 10. Confirm exact tests

List all tests to modify/add.

At minimum verify scope for:

### Unit tests
- country dataset/helper
- locale-specific names
- search/filter normalization
- sorting
- scanner optional code
- storage write/remove behavior
- ViewModel legacy preservation
- explicit selection
- clear
- dirty-state
- malformed code handling

### Instrumentation tests
- Country field opens picker
- list visible immediately
- EN display
- DE display
- filtering from first character
- ISO code search
- select
- cancel
- legacy invalid value remains visible
- unrelated edit preserves legacy value
- explicit select canonicalizes
- clear removes both
- no-result state
- accessibility semantics where meaningful

List exact existing/new test file paths.

Do not add tests in this prompt.

---

# Expected File Scope

Determine the exact full list.

Likely candidates include, but must be verified:

- `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/data/SessionScanner.kt`
- `app/src/main/java/com/isardomains/sameview/data/SessionStorage.kt`
- metadata model/data-class file(s), if actually required
- one new country picker/helper file, if required
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- relevant unit tests
- `app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt`

Do not include a file unless actual repository evidence proves it must change.

No docs should change in this implementation scope; specs are already committed.

---

# Explicitly Unchanged

Confirm no changes to:

- metadata version number
- supported metadata versions
- Gradle
- dependencies
- manifests
- permissions
- INTERNET permission
- GPS
- geocoding
- Camera
- Compare rendering
- Video export behavior
- Share behavior
- WebApp repository
- backup format version
- unrelated UI
- Issue #3

---

# Verification Plan After Implementation

Confirm exact commands.

At minimum determine whether to run:

1. focused unit tests
2. focused instrumentation tests
3. `./gradlew assembleDebug`
4. `./gradlew lintDebug`
5. API 29 targeted/full relevant instrumentation
6. API 36 targeted/full relevant instrumentation
7. `./gradlew assembleRelease`
8. `./gradlew bundleRelease`

Do not automatically require the entire 930-test matrix unless justified.

Also define required manual validation, especially:
- DE UI
- EN UI
- picker UX
- legacy value behavior
- offline behavior
- short TalkBack/accessibility pass

---

# Risks

List only Issue #2 risks:

- silent legacy migration
- country/code mismatch
- malformed persisted code
- incorrect localization snapshot
- locale switching
- dirty-state regression
- storage regression
- backup/export compatibility
- accessibility
- responsive sheet layout
- API 29
- API 36
- release stability
- privacy/offline guarantee

For each classify:
- code prevention required
- verification only

---

# Documentation / Issue Closure

Specs are already updated and committed.

Confirm:
- no behavioral doc change during implementation
- `IMPLEMENTATION_NOTES.md` may receive only a completion note after implementation verification, in a later finalization step if required
- Issue #2 remains open during implementation
- final verified closure may use `Closes #2`

---

# Required Final Report

Return exactly:

## 1. Branch / Baseline

## 2. Offline Country Source Decision

## 3. App Locale Decision

## 4. Metadata Touchpoints

Table:
| File/Component | Classification | Exact reason |
|---|---|---|

## 5. Scanner Contract

## 6. Storage Contract

## 7. ViewModel / Dirty-State Contract

## 8. Picker UX Contract

## 9. Search / Sorting Contract

## 10. Resource Scope

## 11. Exact Files to Modify

Table:
| File | Type | Exact change |
|---|---|---|

## 12. Exact Tests to Add/Modify

## 13. Explicitly Unchanged

## 14. Verification Plan

## 15. Risks

## 16. Documentation / Issue Closure

## 17. Scope Verdict

Choose exactly one:

- **SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **USER DECISION REQUIRED**
- **BLOCKED**

Then STOP.

No implementation until explicit approval.
