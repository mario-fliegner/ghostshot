# Issue #2 — Gate 2A: Specification Scope Confirmation for Country Picker + Canonical Country Metadata

## Objective

Define the exact **documentation/specification changes** required for GitHub Issue #2 before any production implementation begins.

This is **scope confirmation only**.

Do not modify files.
Do not modify production code.
Do not modify tests.
Do not modify Gradle, manifests, resources, dependencies, permissions, or GitHub issues.
Do not commit, push, merge, rebase, or amend anything.

The product decision is already made. Your job is to translate it precisely into the existing SameView source-of-truth contracts with the smallest possible documentation scope.

---

## Repository / Branch

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch: the dedicated Issue #2 branch created during Gate 1.

Before analysis:

1. Confirm exact active branch.
2. Confirm it is the Issue #2 branch based on current `main`.
3. Confirm current HEAD/base.
4. Confirm working tree is clean.
5. If anything differs unexpectedly, STOP.

Do not stash/reset/discard anything.

---

# Binding Product Decision

The following is approved product behavior and must be reflected in the specs.

## User-facing principle

Design this from the **user's perspective**, not from implementation convenience.

The user should experience Country as an easy, localized, offline country selector — not as an ISO-code editor or technical metadata field.

`Place Name` and `City` remain free-text fields.

`Country` becomes the controlled field.

---

# 1. Country Picker UX Contract

When editing session metadata:

- `Country` is no longer ordinary free text for new/actively changed values.
- Tapping the Country field opens a dedicated country picker.
- The picker must work completely offline.
- No network request.
- No geocoding service.
- No remote country-list API.
- No INTERNET permission.
- No new permission of any kind.

The picker should present:
- localized title, e.g. DE `Land auswählen`, EN `Select country`
- local search/filter field
- alphabetically sorted country list in the current app language
- localized country names:
  - DE: `Deutschland`, `Österreich`, …
  - EN: `Germany`, `Austria`, …
- tapping a country selects it and returns to the editor

The user must never be required to know or select an ISO code.

---

# 2. Search / Filtering UX

Do **not** require three characters.

The approved behavior is:

- full alphabetically sorted list is visible immediately when the picker opens
- user may scroll and select without searching
- filtering starts from the **first entered character**
- filtering is local/offline
- case-insensitive
- should handle localized names naturally
- diacritic handling should be user-friendly where practical
- ISO alpha-2 code may additionally be accepted as a search match, but must not become the primary visible UX

Example:
- German UI: typing `deu` should lead to `Deutschland`
- English UI: typing `ger` should lead to `Germany`
- searching `DE` may also match Germany/Deutschland as a convenience

Do not introduce server-style minimum-query-length behavior.

---

# 3. Offline Country Dataset

The country list must be available locally on-device.

No runtime internet dependency is permitted.

During this specification gate, determine the best contract wording so the spec does **not unnecessarily lock implementation** to one Java/Kotlin API unless needed.

The implementation may later use a platform/local ISO dataset such as ISO country codes plus localized locale data, or an app-bundled deterministic dataset, but the product/spec contract is:

- ISO 3166-1 alpha-2 identity
- localized display names
- completely offline
- deterministic enough for supported SameView locales
- no remote lookup

If implementation-source choice has compatibility consequences, flag that for the later code scope gate rather than solving it here.

---

# 4. Metadata Contract — Canonical Country Identity

The current metadata model stores a human-readable:

`location.country`

That must remain supported for backward compatibility.

Add a new optional field:

`location.countryCode`

Contract:

- ISO 3166-1 alpha-2
- uppercase
- e.g. `DE`, `AT`, `US`, `GB`
- optional, so all existing metadata without it remains valid
- when a user selects a country through the new picker, `countryCode` becomes the canonical language-neutral identity

The existing `location.country` remains present as a human-readable localized snapshot when a country is actively selected.

Example with German app language:

```json
{
  "location": {
    "country": "Deutschland",
    "countryCode": "DE"
  }
}
```

Example with English app language:

```json
{
  "location": {
    "country": "Germany",
    "countryCode": "DE"
  }
}
```

The two examples represent the **same country**.

`countryCode` is the stable semantic identity.

`country` is the localized display snapshot written by the app at save time.

---

# 5. Cross-Locale / Future Consumer Contract

The metadata must support future consumers such as SameView Web displaying the country in the consumer's own language.

Example:

Android app saved:

```json
"country": "Germany",
"countryCode": "DE"
```

A German-language consumer may display:

`Deutschland`

by resolving `countryCode = DE` in its own locale.

Likewise an English consumer can display:

`Germany`.

Important:
- do not turn this Android issue into a WebApp implementation task
- do not modify any WebApp repository
- only define the metadata contract cleanly enough that another consumer can localize by `countryCode`
- legacy metadata without `countryCode` must still be usable via the existing `country` string

---

# 6. Legacy Country Values — Mandatory Preservation

Existing SameView sessions may contain arbitrary free-text Country values, including:

- `USA`
- `Deutschland`
- `UK`
- `Östereich`
- trailing whitespace
- old/custom text
- other values that do not map cleanly to the new canonical list

These values must **not** be silently rewritten, normalized, corrected, mapped, trimmed, or deleted merely because the session is opened or another metadata field is edited.

Examples:

If an existing session contains:

```json
"country": "Östereich"
```

and the user changes only the title:

- `country` must remain `Östereich`
- no `countryCode` should be invented
- save must not be blocked merely because this legacy Country is non-canonical

If the user opens the Country picker and cancels/dismisses it:

- the legacy value remains unchanged
- no `countryCode` is invented
- this action alone must not create a Country change

Only an explicit valid country selection converts the field into canonical form.

Example:

Legacy:

```json
"country": "Östereich"
```

User explicitly chooses `Österreich`.

German app saves:

```json
"country": "Österreich",
"countryCode": "AT"
```

English app choosing Austria saves:

```json
"country": "Austria",
"countryCode": "AT"
```

No fuzzy/automatic correction of legacy values is allowed.

---

# 7. Existing Valid Legacy Values

Analyze and define the safest contract for an existing `country` string that happens to exactly match a localized country name but has no `countryCode`.

The default safety principle is:

**Do not silently mutate metadata just because SameView can infer a likely country.**

Therefore opening/saving unrelated fields must not automatically add a `countryCode`.

If the user explicitly interacts with Country and makes a valid selection, both fields may then be written canonically.

If you believe a different rule is required by an existing metadata contract, flag the conflict instead of silently changing this decision.

---

# 8. Clear Country Behavior

The user must retain the ability to remove Country metadata.

Define a clear user-facing action in the picker/editor.

When Country is deliberately cleared:

- remove/clear `location.country`
- remove/clear `location.countryCode`
- do not leave a stale code behind
- City and Place Name remain untouched

The exact visual affordance should fit existing SameView editor conventions; do not invent unrelated redesign.

If the current editor already has an established clear-field pattern, prefer consistency with it.

---

# 9. Error / Edge-State Contract

Error handling must be explicitly specified.

At minimum cover:

### Picker data unavailable / unexpectedly empty
- no crash
- no metadata corruption
- existing Country value remains untouched
- show a localized, understandable user-facing failure/empty state
- no network fallback

### Missing localized display name for a valid ISO code
- no crash
- use a deterministic safe fallback
- assess whether visible ISO alpha-2 code is the correct last-resort fallback
- do not invent a translated country name

### Legacy value cannot be mapped
- not an error during ordinary editing
- do not block save of unrelated fields
- preserve it unchanged
- when Country is actively edited, user can choose a valid country

### Picker dismissed/cancelled
- no Country mutation
- no new `countryCode`
- no false dirty-state solely from opening/cancelling

### Valid selection
- update `country`
- update `countryCode`
- values must correspond to the same country

### Explicit clear
- clear both fields

### Persistence failure
- use the existing metadata-editor save/error contract
- do not invent a second storage-error system

---

# 10. Accessibility / Interaction

Country selection must remain accessible.

Determine what the existing editor/spec conventions require for:
- Country field semantics
- picker title
- search field
- selectable rows
- clear action
- keyboard/focus behavior where applicable
- TalkBack content

Do not redesign the whole editor.

Only specify what is needed to make the new Country interaction usable and consistent.

---

# 11. Localization

The feature must support SameView's supported app languages, currently DE and EN.

Required user-facing strings must be localized.

Country names must be displayed according to the current app language.

Do not hard-code a German or English country-name list into UI code unless a later implementation analysis proves that to be the safest local source.

The specification should describe behavior, not prematurely prescribe implementation architecture.

---

# 12. Source-of-Truth Documents to Inspect

Mandatory:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`

Also inspect if relevant:
- privacy/session-original specs
- backup/export specs
- sharing/export specs
- any parser/schema compatibility documentation that constrains optional metadata fields

Search repository docs for:
- `location.country`
- `country`
- `countryCode`
- `metadata.json`
- `formatVersion`
- free text / Freitext
- controlled vocabulary
- backward compatibility / compatibility

Do not assume only two docs need modification until the repository evidence confirms it.

However, prefer the smallest possible source-of-truth scope.

---

# 13. Schema / Version Compatibility Analysis

Explicitly determine:

1. Can optional `location.countryCode` be added without incrementing `formatVersion` under the existing metadata compatibility rules?
2. Will older SameView versions ignore the unknown optional field safely?
3. Will current readers tolerate sessions that have:
   - `country` only
   - `country` + `countryCode`
   - neither
4. Is `countryCode` nullable/optional rather than required?
5. Does any export/import/backup path serialize the whole metadata model automatically, or does it need explicit schema handling later?
6. Are there any consumers/tests that assume the exact set of fields in `location`?
7. Does any privacy contract change? Expected answer: no, because country already exists and the code is a normalized representation of the same user-provided country information — but verify against source-of-truth docs.

Do not change format version during this gate.

If a format-version bump appears necessary, mark the scope blocked and explain why.

---

# 14. Documentation Conflict to Resolve

Gate 1 identified that the current metadata-editor specification treats all location fields as free text / no controlled vocabulary.

That is intentionally changing.

The new contract must clearly state:

- Place Name → free text
- City → free text
- Country → controlled localized picker backed by canonical ISO country identity

Find every current statement contradicted by this decision.

Do not leave both old and new rules in the source-of-truth docs.

---

# 15. Expected Documentation Scope

The likely files are:

- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`

But this is not permission to modify them yet.

For each proposed file:
- exact section
- current contract
- proposed contract
- why change is required

If `IMPLEMENTATION_NOTES.md` should eventually record completion, distinguish that from the behavioral source-of-truth change. Do not automatically add it to this spec-edit gate unless necessary.

If any third behavioral spec truly must change, report it and explain why.

---

# 16. No Production Implementation Yet

Do not design the Kotlin structure in detail in this gate.

You may identify implementation implications needed to validate the spec, but do not:
- output Kotlin
- choose final composable/class names
- edit ViewModels
- add dependencies
- add resources
- write tests

The later implementation gate will derive code from the approved specs.

---

# 17. Future Verification Requirements

For the later implementation, identify the behavior that must eventually be verified, including at minimum:

- DE localized country list
- EN localized country list
- alphabetical ordering per current locale
- filtering from first character
- scroll/select without search
- offline operation
- valid selection persists localized `country` + canonical `countryCode`
- same ISO code produces different localized display name under DE vs EN
- legacy invalid value preserved during unrelated edit
- legacy invalid value preserved after picker cancel
- legacy value replaced only after explicit valid selection
- explicit clear removes both fields
- empty/missing country dataset does not corrupt metadata
- fallback behavior for missing localized display name
- no false dirty-state on picker open/cancel
- backward-compatible read of metadata without `countryCode`
- serialization/deserialization with optional `countryCode`
- relevant export/import/backup roundtrip if repository analysis shows those paths require explicit verification
- TalkBack/accessibility where appropriate
- API 29 compatibility
- API 36 compatibility

Do not run or implement these tests now.

---

# 18. GitHub Issue #2

Do not close or edit Issue #2.

Determine whether the newly defined behavior fully resolves the issue once implemented.

If yes, the eventual final implementation commit may use:

`Closes #2`

But only after implementation and verification are complete.

---

# Required Final Report

Return exactly:

## 1. Branch / Repository State

## 2. Source-of-Truth Documents Reviewed

## 3. Existing Contract Conflicts

List every statement that must change because Country is no longer free text.

## 4. Metadata Schema Decision

State explicitly:
- `country`
- `countryCode`
- canonical identity
- localization behavior
- optionality
- formatVersion decision
- old-reader compatibility

## 5. Country Picker UX Contract

Describe the approved user-visible behavior.

## 6. Legacy Data Contract

Include:
- invalid legacy values
- valid-looking legacy values
- unrelated edits
- picker cancel
- explicit selection
- explicit clear

## 7. Offline Country Data Contract

State what is guaranteed and what implementation choice remains open.

## 8. Search / Filtering Contract

## 9. Error / Edge-State Contract

## 10. Accessibility / Localization Contract

## 11. Exact Documentation Files Proposed for Modification

Table:

| File | Section(s) | Exact purpose |
|---|---|---|

## 12. Exact Planned Spec Changes

Describe precise changes but do not edit files.

## 13. Compatibility / Privacy Impact

## 14. Future Implementation Implications

No code; identify only the areas later implementation must address.

## 15. Future Verification Plan

## 16. Issue #2 Closure Strategy

## 17. Gate 2A Verdict

Choose exactly one:

- **SPEC SCOPE CONFIRMED — READY FOR USER APPROVAL**
- **USER DECISION REQUIRED**
- **BLOCKED**

Then STOP.

---

# Final Safety Rules

- Issue #2 only.
- Scope confirmation only.
- No file modifications.
- No code.
- No tests.
- No docs edits.
- No Gradle/dependency changes.
- No manifest/permission changes.
- No GitHub edits.
- No commit.
- No push.
- No merge.
- Do not start Issue #3.
- Preserve legacy metadata unless the user explicitly changes Country.
- Country selection must remain fully offline.
- Design the contract from the user's experience first; implementation details are subordinate to that contract.
