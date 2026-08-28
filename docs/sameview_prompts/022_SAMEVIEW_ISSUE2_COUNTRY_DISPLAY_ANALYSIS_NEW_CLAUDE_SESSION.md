# Issue #2 — Analysis: Locale-Aware Country Display Across ALL User-Facing Surfaces

## Context for this new Claude session

You are continuing work on the SameView Android repository in a **new Claude session**. Do not assume prior chat context. Verify everything against repository state and source-of-truth docs.

Repository: `C:\data\work\privat\git-repos\sameview`
Expected branch: `issue/2-country-selection`
Expected committed HEAD before the current uncommitted implementation: `e24930d` — `docs: define canonical country metadata for issue #2`

There are currently **uncommitted Issue #2 implementation changes in 11 approved files**. They already passed focused automated verification. Preserve them exactly during this analysis: do not reset, stash, discard, overwrite, format, commit, push, merge, amend, or edit GitHub issues.

**ANALYSIS ONLY. No file modifications.** After analysis STOP. A separate scope confirmation follows.

## Current Issue #2 implementation context

Already-approved behavior:
- Place Name and City remain user-authored free text.
- Country uses a fully offline localized picker.
- Full list immediately visible; filtering starts at first character.
- No network, geocoding, INTERNET permission, new permission, or dependency.
- Metadata remains v6; no v7.
- `location.country` is optional human-readable localized snapshot.
- `location.countryCode` is optional canonical uppercase ISO 3166-1 alpha-2 identity.
- Explicit Country selection writes both.
- Explicit clear removes both.
- Legacy Country strings remain untouched until explicit Country action.
- Picker open/cancel causes no mutation.
- Non-canonical legacy values never block unrelated edits.

Current uncommitted files reportedly are:
1. `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt`
4. `app/src/main/java/com/isardomains/sameview/ui/compare/CountryCatalog.kt` (new)
5. `app/src/main/java/com/isardomains/sameview/ui/compare/CountryPickerSheet.kt` (new)
6. `app/src/main/res/values/strings.xml`
7. `app/src/main/res/values-de/strings.xml`
8. `app/src/test/java/com/isardomains/sameview/ui/compare/CountryCatalogTest.kt` (new)
9. `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt`
10. `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`
11. `app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt`

Reported implementation uses `Locale.getISOCountries()`, locale display names, `Collator`, `Normalizer`, explicit `countryCode` persistence, ViewModel original/current code state, and no legacy inference. Verify, do not assume.

## NEW binding product clarification

**ALL user-facing Country displays must use the current SameView UI language whenever a valid canonical `countryCode` exists.**

Stored:
```json
{"location":{"city":"Berlin","country":"Germany","countryCode":"DE"}}
```

German SameView displays: `Berlin, Deutschland`
English SameView displays: `Berlin, Germany`

The JSON is NOT rewritten just because app language changes.

`location.country` remains compatibility/fallback snapshot.
`location.countryCode` is canonical identity used for localized display.

### Place Name and City MUST NOT be translated

Stored:
```json
{"location":{"displayName":"Marienplatz","city":"München","country":"Deutschland","countryCode":"DE"}}
```

English UI may show `Marienplatz · München, Germany`.

It must NOT automatically change `München` to `Munich`. SameView has no canonical city/place identity and must preserve user-authored text.

Required semantic rule:

| Metadata | User-facing display |
|---|---|
| Place Name | exactly stored/user-entered |
| City | exactly stored/user-entered |
| Country without usable `countryCode` | stored `country` exactly |
| Country with usable canonical `countryCode` | localized name for current SameView UI locale |

## Legacy and malformed data

No code:
- `"country":"Germany"` in German UI stays `Germany`.
- `"country":"Östereich"` in English UI stays `Östereich`.
No inference, correction, translation, normalization, or backfill.

Malformed/unknown code, e.g. `"countryCode":"ZZZ"` or invalid ISO code:
- no crash
- no metadata mutation
- no guessing
- fall back to stored `country`
- if stored country absent too, show no Country

Analyze exact code validation. Do not silently normalize malformed persisted metadata.

## Central architectural question

Do NOT independently solve localization in Edit Session, Compare, Share, Video, etc.

Analyze a small centralized resolver, conceptually:
`resolveCountryDisplayName(country, countryCode, locale)`

Desired semantics:
1. valid canonical code -> localized name via same offline source as picker
2. missing/invalid/unresolvable code -> stored country unchanged
3. no metadata mutation
4. Place Name/City untouched
5. offline/API29+/testable
6. no duplicated display logic

Determine whether existing uncommitted `CountryCatalog` is the correct home. Prefer it unless repository architecture proves otherwise.

## Mandatory repository-wide user-facing Country audit

Search the **entire Android repository** for every production use of:
- `location.country`
- `locationCountry`
- `"country"` JSON reads
- Country from `ScannedSession`
- location-formatting helpers
- `city + country`
- `displayName + city + country`
- Compare headers/details/fullscreen
- Compare Library/session cards
- Edit Session
- Share image preview/final bitmap
- Video preview/final render
- guides/details
- accessibility descriptions containing location
- any other visible location output

Classify every occurrence:
A. USER-FACING — MUST LOCALIZE
B. STORAGE/SERIALIZATION — MUST preserve raw snapshot/code
C. INTERNAL/TEST-ONLY
D. AMBIGUOUS

Produce a complete inventory with exact file paths/purpose. The goal is to prevent a half-fix where Edit Session says `Deutschland` but Compare/Share/Video say `Germany`.

## ScannedSession / metadata propagation

Trace actual current code after the uncommitted changes:
- Does `ScannedSession` contain `locationCountryCode`?
- Does `SessionScanner` parse it?
- If not, how does Edit Session currently get it?
- Which consumers currently receive only `locationCountry`?
- Is metadata parsed centrally or independently?
- What is the smallest architecture-consistent way to propagate `countryCode` to all user-facing consumers?

Do not duplicate raw JSON parsing per screen if central scanning is established. Do not expand models unnecessarily if a cleaner existing path exists.

## Edit Session semantics — analyze carefully

Stored:
```json
"country":"Germany","countryCode":"DE"
```
After app language changes EN -> DE, Country trigger must display `Deutschland` without rewriting metadata.

Settle:
1. persisted/original snapshot vs localized display state
2. language switch must NOT mark session dirty
3. unrelated save after language switch must NOT silently rewrite `"Germany"` to `"Deutschland"`
4. only explicit Country selection replaces stored snapshot
5. explicit clear removes both
6. legacy no-code Country displays exact stored value

Check current specs for any contradiction and identify required wording correction.

## Compare / Library

Audit every visible location surface:
- source of country
- availability of code
- locale source
- formatting helper
- tests affected

Do not alter punctuation/order unless required for localization.

## Share Comparison Image

Read `docs/SHARE_COMPARISON_IMAGE_V1.md` and implementation.

Determine:
- where location line is built
- preview vs final bitmap path
- whether code can be propagated cleanly
- locale availability at render time

Binding decision: generated share image uses current SameView UI language for Country when valid code exists. Place Name/City unchanged. No metadata rewrite.

## Video Export

Read `docs/VIDEO_EXPORT_V1.md` and implementation.

Determine:
- preview location text path
- final video overlay path
- whether both share formatting
- how locale reaches renderer/background work
- whether locale must be passed explicitly to prevent system/default mismatch

Binding decision: video Country text uses current SameView UI language at export creation/rendering time when valid code exists. Preview and final output must agree. Place Name/City unchanged. No metadata rewrite.

Treat this as high-risk and trace actual code, do not guess.

## Authoritative app locale

Verify how SameView handles language. Determine authoritative locale for:
- Compose UI
- non-Compose helpers
- Compare
- Share bitmap rendering
- Video/background rendering

Do not accidentally use device language when SameView is running another language. If SameView simply follows system locale, establish that from code. If app-specific locales exist, identify the correct source.

## CountryCatalog audit

Inspect current uncommitted `CountryCatalog.kt`.

Determine if it can cleanly provide:
- localized display name by code/locale
- ISO-code validation
- display resolver fallback

Analyze `Locale("", code).getDisplayCountry(locale)` for valid code, invalid two-letter code, invalid length, lowercase, missing display name. Non-empty output alone must not be treated as proof of valid ISO identity. Validation likely needs the real ISO set; verify.

No third-party country library.

## Required behavior matrix

State expected visible result for all surfaces:

A. `country=Germany`, `countryCode=DE`, UI DE -> `Deutschland`
B. same, UI EN -> `Germany`
C. `country=Deutschland`, `countryCode=DE`, UI EN -> `Germany`
D. legacy `country=Germany`, no code, UI DE -> `Germany`
E. legacy `country=Östereich`, no code, UI EN -> `Östereich`
F. `country=Germany`, malformed `countryCode=ZZZ`, UI DE -> `Germany`
G. only `countryCode=DE`, no country, UI DE -> analyze; preferred result `Deutschland` because canonical identity is sufficient
H. neither -> no Country output

All surfaces should use one resolver contract.

## Storage/serialization must remain stable

Display localization must NOT alter persistence:
- metadata v6 unchanged
- raw `country` snapshot preserved
- canonical `countryCode` preserved
- backups/raw metadata preserve values
- no language-switch migration
- no automatic rewrite
- no version bump
- stored snapshot changes only after explicit Country selection

## Source-of-truth docs

Mandatory review:
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/SESSION_METADATA_EDITOR_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/VIDEO_EXPORT_V1.md`
- `docs/SESSION_BACKUP_EXPORT_V1.md`
- any other relevant spec found during audit

Classify doc changes REQUIRED / OPTIONAL / NO CHANGE.

The current wording that persisted snapshot is not automatically retranslated remains correct for **storage**, but any wording implying raw snapshot must always be used for **display** must be corrected.

Prefer minimal current-contract changes; preserve historical records.

## Test impact

Analyze exact tests required.

Pure/unit resolver tests:
- DE `DE` -> Deutschland
- EN `DE` -> Germany
- German snapshot + DE + EN -> Germany
- missing code -> exact stored fallback
- malformed code -> exact stored fallback
- valid code + missing snapshot -> localized name
- neither -> no country
- lowercase/malformed behavior per chosen validation
- no metadata mutation

Edit Session:
- localized display from code
- language change/display not dirty
- unrelated save does not rewrite snapshot
- explicit selection writes current localized snapshot + code
- legacy no-code remains exact

Compare/Library:
- localized visible Country where applicable

Share:
- localized preview/final location output

Video:
- localized preview/final render parity

Determine targeted API29/API36 tests. Do not demand full 930-test suite unless touched architecture justifies it.

Manual final check should likely cover DE/EN, Compare, Share image, Video preview/export, legacy session, offline; TalkBack only where interaction semantics changed.

## Scope discipline

Scope may expand beyond current 11 files only where required for this all-user-facing-display rule.

Forbidden:
- unrelated refactoring
- City translation
- Place Name translation
- city IDs/geocoding
- GPS changes
- formatting redesign
- metadata version changes
- network/dependencies/permissions
- Issue #3
- unrelated cleanup

Expected surgical direction, subject to repository evidence:
1. central Country display resolver
2. propagate `countryCode` to user-facing consumers
3. replace visible raw Country usage with resolver
4. focused tests/spec corrections
5. storage semantics unchanged

## Release/privacy/regression analysis

Assess:
- metadata compatibility
- legacy sessions
- language switching
- dirty-state
- Compare rendering
- Share bitmap
- Video preview/render parity
- background locale propagation
- API29/API36 locale behavior
- accessibility text
- backup/raw export preservation
- offline/privacy guarantees
- release stability

Classify each as code-change required / verification only / no impact.

# Required Final Report

Return exactly:

## 1. Repository / Branch / Working Tree State
Branch, HEAD, all modified/untracked files, confirmation existing work stayed untouched.

## 2. Current Implementation Findings
What the uncommitted implementation actually does today.

## 3. Final Display Semantics
Exact contract for valid/missing/malformed code, missing snapshot, legacy, Place Name, City.

## 4. Central Resolver Decision
Recommended home, responsibilities, validation, fallback, why no duplication.

## 5. Authoritative App Locale Decision
Compose, Compare, Share, Video/background.

## 6. Complete User-Facing Country Inventory
| File / Component | Current source | User-facing? | Must localize? | countryCode currently available? | Required action |

Include every discovered production occurrence.

## 7. Storage / Serialization Inventory
| File / Component | Purpose | Preserve raw snapshot/code? | Change required? |

## 8. ScannedSession / Metadata Propagation Decision

## 9. Edit Session Decision
Language change, dirty-state, unrelated save, selection, clear, legacy.

## 10. Compare / Library Decision

## 11. Share Comparison Image Decision

## 12. Video Export Decision
Explicit preview/final parity and locale propagation.

## 13. Legacy / Malformed Metadata Matrix
A-H with expected visible results.

## 14. Documentation Impact
| Doc | Required? | Exact reason / section |

## 15. Exact Additional / Revised File Scope
Separate:
- already modified files needing revision
- additional production files
- additional tests
- docs needing correction

Do not modify them.

## 16. Test Strategy
Exact test classes and what each proves.

## 17. Risks
| Risk | Classification | Mitigation |

## 18. Issue #2 Completion Impact
Confirm whether this remains part of Issue #2 and whether issue stays open.

## 19. Analysis Verdict
Choose exactly:
- **ANALYSIS COMPLETE — READY FOR SCOPE CONFIRMATION**
- **USER DECISION REQUIRED**
- **BLOCKED**

If complete, one sentence stating what the next scope confirmation must lock down.

Then STOP.

# Final Rules

Analysis only. Preserve current working tree. No edits, commits, pushes, merges, GitHub changes, Issue #3, metadata-version changes, City/Place Name translation, network, dependencies, or permissions. All user-facing Country displays must ultimately localize from a valid `countryCode`; missing/invalid code falls back to stored `country` unchanged; display localization never silently mutates metadata.
