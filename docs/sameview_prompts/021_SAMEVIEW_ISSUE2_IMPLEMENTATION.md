# Issue #2 — Implementation: Offline Country Picker + Canonical `countryCode`

## Objective

Implement GitHub Issue #2 exactly according to the already-approved specs and confirmed implementation scope.

Do not re-analyze the feature.
Do not reopen product decisions.
Do not expand scope.

Implement only the confirmed Country Picker / `countryCode` change, then run the agreed verification and report results.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`issue/2-country-selection`

Expected HEAD before implementation:

`e24930d` — `docs: define canonical country metadata for issue #2`

Before editing:

1. Confirm active branch exactly.
2. Confirm current HEAD.
3. Confirm working tree is clean.
4. Confirm Issue #2 remains OPEN.
5. Confirm:
   - `METADATA_VERSION = 6`
   - supported versions remain `{2,3,4,5,6}`
6. If anything differs unexpectedly, STOP.

Do not stash/reset/discard/rebase/amend anything.

---

# Source of Truth

Read and follow:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

The committed Issue #2 specs are authoritative.

---

# Binding Implementation Decisions

Implement exactly these decisions:

## Country source
Use platform/JVM locale data:

- `Locale.getISOCountries()`
- localized display names via locale APIs
- current app locale from `LocalConfiguration.current.locales.get(0)`
- locale-aware sort via `Collator`
- diacritic-friendly normalization via `Normalizer`
- fully offline
- no new dependency
- no network
- no geocoding
- no INTERNET permission
- no new permission

## Metadata
- remain metadata v6
- no v7
- `location.country` remains optional localized display snapshot
- add optional `location.countryCode`
- `countryCode` = uppercase ISO 3166-1 alpha-2 canonical identity
- explicit selection writes both
- explicit clear removes both
- legacy values remain untouched until explicit Country action
- no inference/backfill

## Search
- list visible immediately
- filter from first character
- case-insensitive
- locale-aware
- diacritic-friendly
- rank startsWith matches before contains matches
- ISO alpha-2 may match as secondary convenience
- no minimum search length

---

# Approved File Scope

Only these files may change.

## Production
1. `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`
4. new `app/src/main/java/com/isardomains/sameview/ui/compare/CountryCatalog.kt`
5. new `app/src/main/java/com/isardomains/sameview/ui/compare/CountryPickerSheet.kt`

## Resources
6. `app/src/main/res/values/strings.xml`
7. `app/src/main/res/values-de/strings.xml`

## Tests
8. new `app/src/test/java/com/isardomains/sameview/ui/compare/CountryCatalogTest.kt`
9. `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt`
10. `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`
11. `app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt`

If any other file appears necessary, STOP and report before changing it.

Do not modify docs in this implementation step.

---

# Critical Storage Safety Rule

The new `countryCode` parameter must **not** cause an existing code to be deleted accidentally by unrelated legacy callers.

Before changing `SessionStorage.updateLocation()`:

1. inspect every production caller
2. determine whether each caller is meant to:
   - preserve existing `countryCode`
   - explicitly replace it
   - explicitly clear it
3. do not rely blindly on `countryCode: String? = null` if `null` would mean "remove field" and thereby erase an existing code during an unrelated update

The implementation must distinguish preservation from explicit clear if needed.

Use the smallest safe design.

Do not introduce a broad validation subsystem.

This is mandatory.

---

# Required Production Behavior

## `SessionStorage.updateLocation()`

Extend location persistence to support `countryCode`.

Required behavior:

- preserve existing atomic metadata write
- preserve existing sanitization conventions
- support write/remove of `countryCode`
- include `countryCode` in whole-location-object removal logic
- explicit clear of Country removes both `country` and `countryCode`
- unrelated location updates must not accidentally erase an existing `countryCode`
- malformed legacy Country values remain untouched
- no version bump

If the originally proposed default-null signature would violate preservation semantics, use the smallest alternative that distinguishes:
- preserve
- replace
- clear

Do not change unrelated storage APIs.

---

## `EditSessionViewModel.kt`

Add only the state needed for:

- initial/current `country`
- initial/current `countryCode`
- dirty-state comparison
- explicit country selection
- explicit clear

Requirements:

### legacy value
Existing:
```json
"country": "Östereich"
```
with no code

Changing Title only must save without altering Country or inventing a code.

### valid-looking legacy
Existing:
```json
"country": "Germany"
```
with no code

Do not infer `DE`.

### cancel
Opening/dismissing picker:
- no mutation
- no false dirty-state

### select
Selecting Germany in EN:
- `country = "Germany"`
- `countryCode = "DE"`

Selecting Germany in DE:
- `country = "Deutschland"`
- `countryCode = "DE"`

### clear
Clear both fields together.

Use existing dirty-state patterns. No parallel state architecture.

---

## `CountryCatalog.kt`

Pure/testable logic only.

Responsibilities:

- load ISO alpha-2 codes
- localize display names for provided locale
- fallback to code if display name unavailable
- sort with locale-aware `Collator`
- normalize search text
- support diacritic-insensitive matching
- rank startsWith before contains
- secondary ISO-code match
- no Android network/service dependency
- no Compose UI

Keep it minimal.

---

## `CountryPickerSheet.kt`

Use existing SameView `ModalBottomSheet` conventions.

Required UX:

- localized title
- localized search field
- full list visible immediately
- searchable country rows
- tap row -> select and dismiss
- clear action
- dismiss/cancel -> no state change
- no-results state
- dataset-empty/error state
- standard accessible Material semantics
- responsive behavior consistent with existing sheets
- no unnecessary selection highlighting unless already clearly appropriate from existing conventions

Do not overdesign.

---

## `EditSessionScreen.kt`

Replace only the Country free-text entry.

Preserve:
- Place Name field
- City field
- surrounding layout
- unrelated editor behavior

Country trigger must:

- show current Country value exactly, including invalid legacy strings
- clearly behave as a selection trigger, not editable free text
- open `CountryPickerSheet`
- use current app locale from existing SameView locale pattern
- write localized name + ISO code only after explicit selection
- clear both values on explicit clear
- dismiss without mutation

---

# Resources

Add only required strings in EN + DE.

Expected keys:

- `edit_session_country_picker_title`
- `edit_session_country_picker_search_hint`
- `edit_session_country_picker_empty`
- `edit_session_country_clear_content_description`

Remove `edit_session_placeholder_country` only if it has zero remaining references after implementation.

Do not remove unrelated resources.

---

# Tests

## New `CountryCatalogTest.kt`

Cover:

- DE vs EN display names for same ISO code
- alphabetical locale-aware sorting
- startsWith-before-contains ranking
- case-insensitive search
- diacritic normalization
- ISO-code secondary search
- fallback-to-code behavior
- basic ISO-list sanity/completeness

Do not hardcode brittle full-country-list ordering beyond what is necessary.

---

## `EditSessionViewModelTest.kt`

Add/adjust only required tests:

- legacy invalid Country + unrelated edit -> unchanged
- valid-looking legacy Country + unrelated edit -> no code invented
- explicit selection updates name + code
- explicit clear clears both
- dirty-state correct for selection/clear
- malformed/unexpected persisted code does not crash
- cancel/open behavior must not change ViewModel state

Update lambda signatures only as required by the storage contract.

---

## `SessionStorageMetadataTest.kt`

Add focused coverage for:

- writing country + code
- explicit clear removes both
- existing code is preserved by unrelated location updates
- legacy Country without code remains valid
- whole location removal only when all location fields are absent

Do not rewrite unrelated storage tests.

---

## `EditSessionScreenTest.kt`

Update the old Country free-text test and add focused picker coverage:

- Country trigger opens picker
- list visible immediately
- EN display
- DE display
- first-character filtering
- ISO-code search match
- select updates trigger value
- cancel preserves value
- invalid legacy value remains visible
- explicit select canonicalizes display
- clear removes Country/code
- no-result state
- accessibility semantics where meaningful

Use existing test patterns/helpers. No unrelated test refactor.

---

# Explicitly Unchanged

Do not change:

- metadata version
- supported versions
- docs
- Gradle
- dependencies
- manifests
- permissions
- INTERNET permission
- GPS
- geocoding
- Camera
- Compare rendering
- Share behavior
- Video behavior
- backup format version
- WebApp
- Issue #3
- unrelated UI
- historical data

---

# Verification

Run in this order.

## 1. Focused unit tests

Run the narrowest Gradle unit-test command(s) covering:

- `CountryCatalogTest`
- `EditSessionViewModelTest`

Report exact counts/results.

## 2. Compile/build sanity

Run:

`./gradlew assembleDebug`

## 3. Lint

Run:

`./gradlew lintDebug`

Do not suppress findings.

## 4. API 29 focused instrumentation

Run filtered managed-device tests for:

- `SessionStorageMetadataTest`
- `EditSessionScreenTest`

using `pixel2Api29DebugAndroidTest`.

## 5. API 36 focused instrumentation

Run the same filtered classes using:

`pixel2Api36DebugAndroidTest`

## 6. Release verification

Run:

- `./gradlew assembleRelease`
- `./gradlew bundleRelease`

## 7. Git diff/state

Run:

- `git status --short`
- `git diff --check`
- full diff review for all approved files

Confirm no extra file changed.

---

# Manual Validation Required

After automated verification passes, mark the implementation:

**AUTOMATED VERIFICATION PASSED — MANUAL UX VALIDATION REQUIRED**

Do not call Issue #2 complete yet.

The user must still manually check:

## German
- Country picker opens
- list shows German names
- search works from first character
- Germany appears as `Deutschland`
- selecting it persists `country = Deutschland`, `countryCode = DE`

## English
- list shows English names
- Germany appears as `Germany`
- same code `DE`

## Legacy
- pre-existing invalid value remains visible
- unrelated edit does not alter it
- opening/cancelling picker does not alter it
- explicit valid selection replaces it

## Offline
- airplane mode
- picker still fully works

## Accessibility
- short TalkBack pass:
  - Country trigger
  - title
  - search
  - rows
  - clear action

Do not commit yet.

---

# Failure Handling

If any test/build/lint check fails:

- do not suppress
- do not broaden scope
- do not refactor unrelated code
- fix only if the failure is directly caused by this Issue #2 implementation and remains inside the confirmed file scope
- otherwise STOP and report

If a third-party/platform locale behavior makes the approved UX impossible or inconsistent, STOP and report instead of silently changing the contract.

---

# Required Final Report

Return exactly:

## 1. Modified Files

## 2. Exact Implementation
Separate:
- storage
- ViewModel
- CountryCatalog
- CountryPickerSheet
- EditSessionScreen
- resources

## 3. Legacy Preservation Behavior

## 4. Metadata v6 Behavior

## 5. Storage Preservation Safety
Explicitly explain how unrelated callers avoid deleting an existing `countryCode`.

## 6. Test Changes

## 7. Automated Verification Results

Table:
| Command | Result | Counts/Notes |
|---|---|---|

## 8. Lint / Warning Delta

## 9. API 29 Result

## 10. API 36 Result

## 11. Release Build Result

## 12. Final Git State
- branch
- HEAD
- modified files
- no commit

## 13. Manual Validation
State:
**NOT RUN — STILL REQUIRED**

Then list the concise checklist.

## 14. Issue #2 Status
Confirm still OPEN.

## 15. Verdict

Choose exactly one:

- **IMPLEMENTATION VERIFIED AUTOMATICALLY — MANUAL VALIDATION REQUIRED**
- **IMPLEMENTATION FAILED**

Then STOP.

Do not commit.
Do not close Issue #2.
Do not update `IMPLEMENTATION_NOTES.md`.
Do not start Issue #3.
