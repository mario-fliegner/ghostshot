# Issue #2 — Gate 2B: Specification Implementation — Country Picker + Canonical `countryCode` in Metadata v6

## Objective

Implement the already-approved specification changes for GitHub Issue #2.

This gate changes **documentation only**.

The product decision is final:

- `Place Name` remains free text.
- `City` remains free text.
- `Country` becomes a localized, fully offline country picker for actively edited/new values.
- `location.countryCode` is added as the canonical ISO 3166-1 alpha-2 identity.
- `location.country` remains the localized human-readable snapshot.
- Existing/legacy Country strings are preserved until the user explicitly changes Country.
- **Metadata remains version 6. There is NO v7 bump.**
- Historical v4/v5 definitions/examples must not be rewritten to pretend `countryCode` existed earlier.

Do not implement production code yet.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`issue/2-country-selection`

Before editing:

1. Confirm active branch exactly.
2. Confirm working tree is clean.
3. Confirm current HEAD/base.
4. Confirm current production metadata writer uses metadata version `6`.
5. Confirm current reader compatibility range and unknown-field behavior from repository evidence.
6. Confirm GitHub Issue #2 remains OPEN.
7. If anything differs unexpectedly, STOP.

Do not stash/reset/discard/rebase/amend anything.

---

# Mandatory Source-of-Truth Re-read

Before modifying anything, re-read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`

Also inspect any version-history sections in `SESSION_METADATA_V1.md` needed to distinguish historical v4/v5 definitions from the current v6 contract.

---

# Critical Versioning Decision — Binding

## Metadata stays at v6

Do **not**:
- bump `METADATA_VERSION`
- introduce v7
- modify production constants
- describe `countryCode` as requiring v7

Reason:

`location.countryCode` is:
- optional
- not required for session validity
- additive
- safely ignored by older readers under the existing forward-compatibility contract
- absent from legacy sessions without making them invalid

Therefore it is an additive extension of the current v6 metadata contract.

## Historical integrity

Do not retroactively add `countryCode` to historical v4/v5 schema definitions/examples if those sections are documenting what those versions originally contained.

The documentation must clearly distinguish:
- historical schema/version records
- current v6 schema contract

If `SESSION_METADATA_V1.md` structure makes this distinction awkward, make the **smallest possible documentation adjustment** needed to state that `countryCode` is an optional additive field supported by current v6 writers/readers.

Do not rewrite historical version evolution.

---

# Approved Metadata Contract

## `location.country`

Remains:

- optional `String`
- existing storage path unchanged
- legacy values may contain arbitrary historical free text
- for a new explicit picker selection, stores the localized display snapshot in the app's current language

Examples:

German app:
```json
"country": "Deutschland"
```

English app:
```json
"country": "Germany"
```

Previously saved snapshots are not automatically retranslated when the app language changes.

## `location.countryCode`

New optional field:

```json
"countryCode": "DE"
```

Contract:
- ISO 3166-1 alpha-2
- uppercase
- optional
- canonical language-neutral country identity
- written only after explicit valid Country selection
- never inferred/backfilled automatically from a legacy `country`
- when both `country` and `countryCode` exist, they must identify the same country

Example German:

```json
{
  "location": {
    "country": "Deutschland",
    "countryCode": "DE"
  }
}
```

Example English:

```json
{
  "location": {
    "country": "Germany",
    "countryCode": "DE"
  }
}
```

Both represent the same country.

Future consumers may localize from `countryCode` in their own UI language. Do not turn this into a WebApp implementation task.

---

# Approved Country Picker UX Contract

Document the behavior from the user's perspective.

Country interaction:

- tapping Country opens a dedicated picker
- completely offline
- no network/API/geocoding
- no INTERNET permission
- no new permission
- localized title
- localized search field
- complete country list immediately visible
- alphabetical ordering in current app language
- localized country names in current app language
- tap country → select and return
- ISO code is not exposed as the primary selection UX

Supported app languages currently DE/EN.

Examples:
- DE → `Deutschland`, `Österreich`
- EN → `Germany`, `Austria`

---

# Search Contract

Document:

- no three-character minimum
- complete list visible immediately
- filtering starts with first typed character
- local/offline
- case-insensitive
- locale-aware
- diacritic-friendly where practical
- alpha-2 code may additionally match search
- code is not the primary visible label

---

# Legacy Preservation Contract

This is mandatory and must be explicit.

Existing arbitrary Country values may include:

- `USA`
- `Deutschland`
- `UK`
- `Östereich`
- custom strings
- whitespace or old formatting

Rules:

### Unrelated edit
If user changes another field:
- preserve legacy `country` unchanged
- do not infer `countryCode`
- do not normalize/trim/correct/map Country
- do not block save because Country is non-canonical

### Valid-looking legacy value
Even if a legacy value appears to map exactly:
- do not silently add `countryCode`
- do not mutate metadata without explicit Country selection

### Picker opened then cancelled
- preserve old value
- do not create `countryCode`
- do not create false Country dirty-state solely from open/cancel

### Explicit valid selection
- replace `country` with localized selected display name
- write corresponding canonical `countryCode`
- treat the pair consistently

### Explicit clear
- clear/remove `country`
- clear/remove `countryCode`
- do not touch City or Place Name

No fuzzy automatic legacy correction.

---

# Error / Edge-State Contract

Document these user-visible guarantees:

### Country dataset unavailable/empty
- no crash
- existing Country untouched
- localized understandable failure/empty state
- no network fallback
- no metadata corruption

### Missing localized display name
- no crash
- deterministic fallback
- ISO alpha-2 code is acceptable as last-resort visible fallback
- do not invent translations

### Unmappable legacy value
- not an ordinary-edit validation error
- does not block unrelated save
- remains unchanged until user explicitly changes Country

### Picker cancel
- no mutation
- no false dirty-state

### Valid selection
- `country` + `countryCode` updated consistently

### Clear
- both removed

### Persistence failure
- reuse existing metadata-editor storage-write error behavior
- no new parallel storage error system

---

# Accessibility / Localization Contract

Document only the necessary interaction contract:

- Country field must communicate that it opens a selection rather than behaving as editable free text
- picker title localized
- search field localized/accessibly labeled
- country rows selectable/accessibly exposed
- clear action accessibly labeled
- DE/EN strings required
- existing SameView accessibility/touch-target conventions remain applicable

Do not redesign the editor.

---

# File Scope

Exactly these two behavioral source-of-truth documents are approved for modification:

1. `docs/SESSION_METADATA_EDITOR_V1.md`
2. `docs/SESSION_METADATA_V1.md`

Do not modify:
- `docs/IMPLEMENTATION_NOTES.md` yet
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`
- Compare specs
- Share specs
- Video specs
- privacy specs
- historical audits/plans

If a third file appears genuinely necessary, STOP instead of expanding scope.

---

# Required Changes — `SESSION_METADATA_EDITOR_V1.md`

Update only the sections necessary to establish the new behavior.

At minimum:

## Editable fields / storage mapping
Reflect:
- Place Name → `location.displayName`
- City → `location.city`
- Country localized snapshot → `location.country`
- canonical Country identity → `location.countryCode`

Make clear that `countryCode` is metadata managed by the Country selection interaction, not a separate user-facing ISO-code input field.

## Free-text rules
Replace contradictory blanket statements.

New rule:
- Place Name and City remain free text
- Country is controlled for new/explicitly changed values via picker
- legacy Country strings remain valid/preserved for backward compatibility

## Validation
Clarify:
- legacy Country values never block saving unrelated edits
- picker-generated Country selections are canonical by construction
- no arbitrary new Country string can be typed through the new picker UX

## Country Picker section
Add the approved UX/search/offline/localization/legacy/error/accessibility contracts.

Use the existing document's style and section organization.
Avoid unnecessary renumbering if possible.

## Storage/write behavior
Update location persistence contract to include `countryCode`.

If the document currently says three location values are persisted together, update it to four metadata values:
- displayName
- city
- country
- countryCode

This does not mean four user-facing text fields.

Document pair consistency:
- explicit Country selection updates `country` + `countryCode`
- clear removes both

## Scan/editor state
Add optional `locationCountryCode` state only where the existing spec documents scanned/editor metadata fields.

Backward compatibility:
- missing code → null
- no inference from legacy country

---

# Required Changes — `SESSION_METADATA_V1.md`

First inspect the document's actual v4→v5→v6 organization.

Then:

## Preserve historical versions
Do not rewrite historical v4/v5 definitions/examples to include `countryCode` if it did not exist then.

## Current v6 contract
Add `location.countryCode` to the **current v6 schema contract** as an optional additive field.

If the document currently lacks a clean current-v6 schema subsection, add the smallest precise clarification necessary rather than falsifying an older version example.

Document:
- optional `String`
- ISO 3166-1 alpha-2 uppercase
- canonical identity
- absent in legacy metadata
- ignored by older readers
- no format-version bump
- explicit selection writes it with localized `country`
- no automatic backfill

## `location.country`
Update only current/general semantics:
- legacy free-text remains supported
- actively selected values are localized snapshots
- not automatically retranslated
- may exist without `countryCode`

## Compatibility
Make explicit:
- v6 remains v6
- `countryCode` is optional
- metadata with only `country` remains valid
- metadata with both remains valid
- metadata with neither remains valid
- unknown-field forward compatibility preserves older-reader behavior

Do not change the existing general versioning rules unless they are factually wrong.

---

# No Production Implementation

Do not modify:

- Kotlin
- ViewModels
- SessionStorage
- metadata constants
- serializers
- Compose UI
- strings.xml
- tests
- Gradle
- manifests
- dependencies
- permissions

No `METADATA_VERSION` change.

---

# Verification After Documentation Edit

Run:

1. `git status --short`
2. `git diff --check`
3. `git diff -- docs/SESSION_METADATA_EDITOR_V1.md docs/SESSION_METADATA_V1.md`
4. Search docs for:
   - `countryCode`
   - `location.country`
   - `free text`
   - `controlled vocabulary`
   - `formatVersion`
   - `v4`
   - `v5`
   - `v6`
5. Confirm:
   - no contradictory current statement still says all Location fields are free text
   - Place Name/City remain explicitly free text
   - Country controlled picker behavior is explicit
   - legacy Country preservation is explicit
   - `countryCode` is documented as optional/current-v6 additive metadata
   - no v7 introduced
   - historical v4/v5 definitions/examples were not falsified
6. Confirm only the two approved files changed.

No Gradle/build/test execution is required for this documentation-only gate.

---

# Commit

After verification passes, create one documentation commit:

```text
docs: define canonical country metadata for issue #2
```

Do **not** use `Closes #2` yet.

Issue #2 remains open until production implementation and verification are complete.

Do not push or merge.

---

# Required Final Report

Return exactly:

## 1. Branch / Baseline

## 2. Modified Files

## 3. Metadata Version Decision
Explicitly confirm:
- current version remains 6
- no v7
- historical versions preserved

## 4. `SESSION_METADATA_EDITOR_V1.md` Changes

## 5. `SESSION_METADATA_V1.md` Changes

## 6. Legacy Compatibility Contract

## 7. Country Picker UX Contract

## 8. Error / Accessibility / Localization Contract

## 9. Historical Version Preservation
State exactly which historical schema/examples remained untouched.

## 10. Verification Results
Include:
- git status
- diff check
- contradiction search
- exact diff scope

## 11. Tests / Builds
State NOT RUN and why.

## 12. Commit Result
Include hash and exact subject.

## 13. Final Git State

## 14. Issue #2 Status
Confirm still open and not closed by this docs commit.

## 15. Gate 2B Verdict

Choose exactly one:
- **GATE 2B PASSED — READY FOR IMPLEMENTATION ANALYSIS**
- **GATE 2B FAILED**

Then STOP.

---

# Final Safety Rules

- Issue #2 only.
- Documentation implementation only.
- Exactly two approved docs.
- Metadata remains v6.
- No v7.
- No production code.
- No tests.
- No resources.
- No Gradle.
- No manifest/permissions.
- No GitHub edits.
- No `Closes #2`.
- No push.
- No merge.
- Do not start Issue #3.
