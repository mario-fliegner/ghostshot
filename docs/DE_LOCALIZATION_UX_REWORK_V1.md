# DE_LOCALIZATION_UX_REWORK_V1

## 1. Document Status

This document is a decision record for German (`values-de`) localization issues and their
layout impact, derived from a prior analysis pass over `values-de/strings.xml`,
`values/strings.xml`, and the Compose screens that consume the affected strings.

This is a decision record only. It does not implement any change and does not modify any
file other than this one.

Source analysis covered:

- `docs/FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `docs/GUIDE_TIPS_UX_V1.md`
- `docs/SESSION_BRANDING_V2_UX_REWORK.md`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt`
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/branding/BuiltinBrandingSymbol.kt`
- `app/src/main/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheet.kt`
- `app/src/main/java/com/isardomains/sameview/guide/WalkthroughScreen.kt`
- `app/src/main/java/com/isardomains/sameview/guide/WalkthroughContent.kt`

## 2. Non-Goals

This document does not:

- change any source file, resource file, or test
- implement any of the proposed strings or layout fixes
- perform new analysis beyond what is recorded here
- decide implementation order beyond the block grouping in §4

## 3. Finding Index

| ID | Area | Current (DE) | Proposed (DE) | Type | Risk level | Status |
| --- | --- | --- | --- | --- | --- | --- |
| F-01 | Camera bottom bar — Reference button | „Referenzfoto“ | „Referenz“ | String-only | Low | Decided — final |
| F-02 | Marker edit mode — empty hint | „Lang drücken zum Platzieren“ | „Marker durch langes Drücken setzen“ | String-only | High — verify layout at implementation | **Decided — final** |
| F-03 | Branding buttons — text centering | „Foto auswählen“ / „Symbol verwenden“ (unchanged) | no text change — `textAlign = TextAlign.Center` (layout fix) | Layout | Low | **Final product decision** |
| F-04 | Share Comparison — leftover default-logo button | „Standard-Logo verwenden“ (active) | Remove button + backing code | String + Code | Medium | **Final product decision** |
| F-05 | Symbol picker — symbol names | „Heart“, „Star“, „Camera“, „Home“, „Pin“, „Fire“ (unlocalized) | „Herz“, „Stern“, „Kamera“, „Haus“, „Standort“, „Flamme“ | String + Code | Low | **Final product decision** |
| F-06 | Walkthrough page 1 — title | „Einen Moment nachstellen“ | „Damals und heute“ | String-only | Low | **Final product decision** |
| F-07 | Walkthrough page 1 — body | 3-line body, line 2/3 long | 3-line body, final wording (see §5) | String-only | Low | **Final product decision** |
| F-08 | Walkthrough page 2 — title & body | „Foto ausrichten“ / 1-sentence body | „Foto wählen und ausrichten“ / 3-line body (see §5) | String-only | Low | **Final product decision** |
| F-09 | Walkthrough page 3 — title & body | „Foto aufnehmen“ / 1-sentence body | title unchanged / 2-line body, same wording (see §5) | String-only | Low | **Final product decision** |
| F-10 | Walkthrough page 4 — title | „Sieh, was sich verändert hat“ | „Veränderungen entdecken“ | String-only | Low | **Final product decision** |
| F-11 | Walkthrough page 4 — body | 3-line body, up to 37 chars/line | 3-line body, final wording (see §5) | String-only | Low | **Final product decision** |
| F-12 | Walkthrough — navigation | Skip shown on pages 1–3 | Skip shown on page 1 only (exact per-page button set in §5) | Layout/Nav | Medium | **Decided — final** |

## 4. Implementation Blocks

Grouping only — not a sequencing mandate.

- **Block A — String-only, cleared for implementation:** F-01
- **Block B — String-only, decision final; layout risk to verify specifically during implementation:** F-02
- **Block C — Layout fix (Compose):** F-03
- **Block D — Code removal, decision final (UI + ViewModel + strings):** F-04
- **Block E — New string resources + small code change, decision final:** F-05
- **Block F — Navigation/behavior, decision final (Compose + tests):** F-12
- **Block G — Walkthrough page titles & bodies, decision final, cleared for implementation:** F-06, F-07, F-08, F-09, F-10, F-11

---

## 5. Findings

### F-01 — Camera bottom bar: Reference button label

**Current text (DE):** `„Referenzfoto“`
**Proposed text (DE):** `„Referenz“`

**Reason:** The button box has no maximum width (`widthIn(min = CameraSecondaryActionMinWidth)`,
no `maxLines` on the label `Text`), so it grows exactly as wide as the label forces it to. In
portrait, this button is anchored bottom-start while `MarkerDoneButton` ("Fertig") is
bottom-center — a wider label pushes the button further toward screen center and narrows the
gap to "Fertig" in marker edit mode. „Referenz“ is already used elsewhere in the app
(`compare_label_reference`) for the same concept, so this introduces no new term. It is also
shorter than the English source string ("Reference", 9 chars).

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `select_reference_image_label`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` (context only — `ReferenceAction`, lines ~2201–2269; no code change required for this finding)

**Change type:** String-only

**Risk:** Low. Shortening this label cannot itself cause new wrapping; it reduces existing overlap risk. Verify visually in marker edit mode after the change.

---

### F-02 — Marker edit mode: empty hint text

**Status: Decided — final.** Not an open decision.

**Current text (DE):** `„Lang drücken zum Platzieren“` (27 characters)
**Proposed text (DE):** `„Marker durch langes Drücken setzen“` (34 characters) — **final, exact wording**

**Reason:** Binding product decision. The text must explicitly explain how a marker is placed, including the required long-press gesture. Shorter alternatives (`„Marker setzen“`, `„Marker platzieren“`) were considered and rejected because they name the action ("set"/"place a marker") without explaining the required long-press gesture — the gesture itself is the information the hint exists to convey, so a shorter wording that omits it does not satisfy the requirement. Length reduction is explicitly not a goal for this string; the text must not be shortened on that basis.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `markers_empty_hint`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt` (context only — hint rendered centered in a fixed-size, non-clipping `Box` sized to the reference image rect, lines ~882–913; no code change required for this finding)

**Change type:** String-only

**Risk:** **High — documented, accepted, to be verified at implementation, not before.** The final text is 7 characters (+26%) longer than the current text. It is rendered centered in a `Box` sized to the on-screen reference-image rect (which can be narrower than the full screen for portrait-cropped reference photos), with `textAlign = Center` but no `maxLines` — the box does not clip, so if the text needs more lines than the box height accounts for, it can visually overlap the surrounding camera preview. This risk does not change the text and does not block the decision. **At implementation time, verify the rendered hint against a narrow/portrait reference-photo crop** and address any overlap through a layout fix (e.g. `maxLines`/clipping/box sizing) — not by shortening the approved wording.

---

### F-03 — Branding buttons: text not cleanly centered when wrapped

**Status: Final product decision.** Not an open decision.

**Final product decision:** The problem with the Branding/Logo buttons is not text length. The texts stay unchanged. The following texts are explicitly kept as-is:

- „Foto auswählen“
- „Symbol verwenden“

No shorter alternative is being introduced. Explicitly rejected and not to be used:

- „Foto wählen“
- „Bild auswählen“
- „Symbol wählen“
- „Symbol nutzen“
- any other variant/rewording of these two labels

**Root cause:** The audit found that the actual problem is how multi-line button text is rendered, not string length. When a label wraps to two lines, it currently reads visually left-heavy and unbalanced.

**Final UX decision:** Multi-line button text must:

- be horizontally centered
- be centered as a multi-line block (each line centered, not just the block as a whole)
- render visually identically across all Branding/Logo areas

**Affected areas:**

Settings:
- „Standard-Logo“ (section — context only, not itself a button text under this finding)
- „Foto auswählen“ (button — text unchanged; layout fix applies)
- „Symbol verwenden“ (button — text unchanged; layout fix applies)

Share Comparison Image:
- „Vergleichslogo“ (section — context only, not itself a button text under this finding)
- „Foto auswählen“ (button — text unchanged; layout fix applies)
- „Symbol verwenden“ (button — text unchanged; layout fix applies)

**Reason:** Final, binding product decision. Root cause is a missing `textAlign = TextAlign.Center` on the `Text` composable inside each button — not string length. German strings ("Symbol verwenden" = 16 chars, "Foto auswählen" = 14 chars) are close to the wrap threshold on 360–412dp devices, which is why the layout defect becomes visible in practice primarily in German — but the fix is layout, not text.

**Affected files:**
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt:433–448` — `settings_logo_choose_photo` / `settings_logo_use_symbol` buttons (Settings)
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt:275–299` — same button pair, empty-branding state (Share Comparison Image)
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt:337–361` — same button pair, populated-branding state (Share Comparison Image)

**Change type:** Layout only. No string change is planned or approved — `settings_logo_choose_photo`, `settings_logo_use_symbol`, `share_comparison_logo_choose_photo`, and `share_comparison_logo_use_symbol` keep their current values in both `values/strings.xml` and `values-de/strings.xml`. A layout correction (adding `textAlign = TextAlign.Center`, applied identically at all three occurrences) is required.

**Risk:** Low. Purely additive text-alignment fix; verify no visual regression on single-line (non-wrapped) button states in both languages, and that the centered multi-line result looks identical across Settings and Share Comparison Image. Note the same pattern is duplicated three times (no shared composable) — each occurrence must be fixed individually or consolidated into one shared component.

---

### F-04 — Share Comparison: leftover "use default logo" button

**Status: Final product decision.** Not an open decision.

**Final product decision:** The found button — „Standard-Logo verwenden“ / „Use default logo“ (`share_comparison_logo_use_default`) — is removed.

**Confirmation basis:** A dedicated citation analysis against the authoritative spec confirmed this UI path is explicitly forbidden. Documented findings:

- **V-04 confirmed:** `SESSION_BRANDING_V2_UX_REWORK.md` rule V-04 states verbatim: *"'Use your default logo' does not exist anywhere in the UI. The concept has been removed."*
- The concept was removed in V2. The spec's master replacement table additionally lists the V1 predecessor concept, `"Copy from default branding"`, as `*(removed — concept no longer exists)*`.
- The only permitted default-logo path is automatic adoption when the screen opens and no logo exists yet (`SESSION_BRANDING_V2_UX_REWORK.md` §8, Share Comparison "Permitted actions": *"Copy global default into session as starting logo (automatic, on screen open when session has no branding)"*) — not a manual button.
- The found button is a spec-contradicting leftover, not a misreading of the spec by the audit. This was independently re-verified through direct citation comparison (V-04; master replacement table; the exhaustive R-01–R-07 replacement-rules list, which has no "restore default" path; and §8's "automatic, on screen open" qualifier) — the contradiction is confirmed, not an audit misinterpretation.

**Current text (DE):** `„Standard-Logo verwenden“` (`share_comparison_logo_use_default`) — currently an active, visible `TextButton` in `ShareComparisonScreen.kt`.

**Proposed text (DE):** *(none — remove the button, its string resources in both languages, and its backing ViewModel logic)*

**Reason:** Final, binding product decision. `SESSION_BRANDING_V2_UX_REWORK.md` rule **V-04** states: *"'Use your default logo' does not exist anywhere in the UI. The concept has been removed."* The current implementation contradicts the authoritative spec — this is a leftover from an earlier three-ownership-level design that was superseded by the corrected two-ownership-level architecture (§ "Architecture Summary — Corrected Edition" of that document). This was not one of the originally reported problems but was found during the terminology consistency check (task item 4).

**Spec status:** No spec change is required. `SESSION_BRANDING_V2_UX_REWORK.md` already documents the intended end state correctly (V-04 plus the automatic-only default-copy behavior in §8); the implementation is what must change to comply with the existing spec, not the other way around.

**At implementation, the button is removed:**

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `share_comparison_logo_use_default`
- `app/src/main/res/values/strings.xml` — `share_comparison_logo_use_default`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt:300–309, 362–371` — button UI (both empty/populated state occurrences)
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt` — `hasGlobalBranding` / `onUseDefaultLogo()`

**Change type:** String removal + code removal (not a pure string edit)

**Risk:** Medium. This is behavioral, not textual — removing only the string resource without removing the button/ViewModel logic would leave a button with a missing label or break the build. Must be removed as one coordinated change across UI and ViewModel. Confirm no other caller depends on `hasGlobalBranding`/`onUseDefaultLogo()` before removal.

---

### F-05 — Symbol picker: symbol names not localized

**Status: Final product decision.** Not an open decision.

**Final product decision:** The German symbol names are fixed as follows:

| Symbol ID | Current display | Final (DE) |
|---|---|---|
| `heart` | Heart | Herz |
| `star` | Star | Stern |
| `camera` | Camera | Kamera |
| `home` | Home | Haus |
| `pin` | Pin | Standort |
| `fire` | Fire | Flamme |

No further alternatives are open. `pin` → „Standort“ is fixed as the final translation for `Pin`.

**Current state:** `BuiltinBrandingSymbol` (enum) has a stable, non-translatable `id` (`"heart"`, `"star"`, …) used for metadata persistence. The picker UI derives its visible label directly from this technical id (`symbol.id.replaceFirstChar { it.uppercase() }`, `BrandingSymbolPickerSheet.kt:118`) and also uses the raw lowercase id as the accessibility `contentDescription` (`BrandingSymbolPickerSheet.kt:101`). No string resource exists for either purpose, so the picker always shows English names regardless of device locale.

**Reason:** Final, binding product decision. The current terms are not localized string resources — they are generated from technical enum ids. Explicit, localized labels are introduced for the German UI. The technical enum ids (`heart`, `star`, `camera`, `home`, `pin`, `fire`) remain unchanged — they are persisted in `metadata.json` as part of the backup/restore contract and are not user-facing. Only the visible UI labels are localized.

**Affected files:**
- `app/src/main/res/values/strings.xml` — 6 new keys needed (e.g. `branding_symbol_heart`, `branding_symbol_star`, `branding_symbol_camera`, `branding_symbol_home`, `branding_symbol_pin`, `branding_symbol_fire`)
- `app/src/main/res/values-de/strings.xml` — same 6 keys, German values per table above
- `app/src/main/java/com/isardomains/sameview/branding/BuiltinBrandingSymbol.kt:15–26` — add a `labelRes: Int` (`@StringRes`) field to the enum
- `app/src/main/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheet.kt:100–121` — replace `symbol.id`-derived label and `contentDescription` with `stringResource(symbol.labelRes)`

**Change type:** New string resources + small code change (enum field + one consumer site)

**Risk:** Low. Additive change; the persisted `id` values (used in `metadata.json`/backup contract) are explicitly untouched — only the display layer changes. Existing test `BrandingSymbolPickerSheetTest.kt` may assert on the current English labels and will need updating alongside this change (test update is out of scope for this document).

---

### F-06 — Walkthrough page 1: title

**Status: Final product decision.** *(product decision labeled "Seite 1 — Überblick")*

**Current text (DE):** `„Einen Moment nachstellen“`
**Final text (DE):** `„Damals und heute“`

**Previously proposed (rejected):** `„Moment nachstellen“` — superseded by the final text above; do not use.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_then_and_now_title`

**Change type:** String-only

**Risk:** Low.

---

### F-07 — Walkthrough page 1: body

**Status: Final product decision.** *(product decision labeled "Seite 1 — Überblick")*

**Current text (DE):**
```
Wähle ein Foto aus.
Geh zurück, wo es aufgenommen wurde.
Sieh, wie es sich verändert hat.
```

**Final text (DE):**
```
Wähle ein Foto aus.
Kehre an den Aufnahmeort zurück.
Schau dir an, was sich verändert hat.
```

**Previously proposed (rejected):**
```
Wähle ein Foto aus.
Geh zurück an diesen Ort.
Sieh den Unterschied.
```
— superseded by the final text above; do not use.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_then_and_now_body`

**Change type:** String-only

**Risk:** Low.

---

### F-08 — Walkthrough page 2: title & body

**Status: Final product decision.** *(product decision labeled "Seite 2 — Schritt 1")*. Scope is extended from the original finding, which covered the body only — the final decision also introduces a new page title, which was not previously flagged as an issue and had no prior proposal.

**Current title (DE):** `„Foto ausrichten“`
**Final title (DE):** `„Foto wählen und ausrichten“`

**Current body (DE):** `„Geh zurück an denselben Standort. Verschiebe und skaliere es, bis alles passt.“`

**Final body (DE):**
```
Wähle ein Referenzfoto aus.
Verschiebe und skaliere es,
bis die Perspektive passt.
```

**Previously proposed body (rejected):** `„Geh zurück an den Ort. Verschiebe und skaliere, bis alles passt.“` — superseded by the final text above; do not use.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_align_overlay_title`, `walkthrough_page_align_overlay_body`

**Change type:** String-only

**Risk:** Low.

---

### F-09 — Walkthrough page 3: title & body

**Status: Final product decision.** *(product decision labeled "Seite 3 — Schritt 2")*. Scope is extended from the original finding, which recorded the body as "no change" and did not evaluate the title — the final decision formally confirms both.

**Current title (DE):** `„Foto aufnehmen“`
**Final title (DE):** `„Foto aufnehmen“` — unchanged, formally confirmed as final

**Current body (DE):** `„Wenn alles ausgerichtet ist, halte den Moment fest.“` (single continuous sentence)

**Final body (DE):**
```
„Wenn alles ausgerichtet ist, 
halte den Moment fest.
```
Wording is unchanged from the current text; the final decision formalizes it as an explicit two-line fragment instead of one continuous sentence.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_take_shot_body` (line-break formatting only; wording unchanged). `walkthrough_page_take_shot_title` requires no edit — its current value already matches the final decision.

**Change type:** String-only

**Risk:** Low.

---

### F-10 — Walkthrough page 4: title

**Status: Final product decision.** *(product decision labeled "Seite 4 — Ergebnis")*. Supersedes the prior interim decision to keep the current text unchanged.

**Current text (DE):** `„Sieh, was sich verändert hat“`
**Final text (DE):** `„Veränderungen entdecken“`

**Previously proposed (rejected):** ~~„Vorher und nachher“~~ — remains rejected; do not use.
**Previous interim decision (superseded):** keeping the current text unchanged — superseded by the final text above.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_see_what_changed_title`

**Change type:** String-only

**Risk:** Low.

---

### F-11 — Walkthrough page 4: body

**Status: Final product decision.** *(product decision labeled "Seite 4 — Ergebnis")*

**Current text (DE):**
```
Das Foto, das du ausgewählt hast.
Der Moment, den du nachgestellt hast.
Der Unterschied zwischen beiden.
```

**Final text (DE):**
```
Vergleiche beide Aufnahmen direkt miteinander und entdecke die Unterschiede.
```

**Previously proposed (rejected):**
```
Das Foto, das du gewählt hast.
Der Moment von heute.
Der Unterschied dazwischen.
```
— superseded by the final text above; do not use.

**Reason:** Final, binding product decision.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` — `walkthrough_page_see_what_changed_body`

**Change type:** String-only

**Risk:** Low.

---

### F-12 — Walkthrough navigation: Skip button placement

**Status: Decided — final.** Not an open decision.

**Current state:** `Skip` is shown on pages 1–3 (three-button row on pages 2–3: Skip + Back + Next). Page 4 has no Skip (Back + Start only).

**Final navigation, exact per page:**

| Page | Buttons shown |
| --- | --- |
| 1 | „Überspringen“ + „Weiter“ |
| 2 | „Zurück“ + „Weiter“ |
| 3 | „Zurück“ + „Weiter“ |
| 4 | „Zurück“ + „Start“ |

„Überspringen“ is shown exclusively on page 1.

**Reason:** Binding product decision. On pages 2–3, three `weight(1f)` buttons previously shared the row with only 8dp spacing (`WalkthroughScreen.kt:326–354`); on a 360dp-wide device this left roughly 70–80dp of text width per button after padding — "Überspringen" (12 characters) was the tightest fit found anywhere in this audit. Removing Skip from pages 2–3 removes that layout-crowding risk and makes the navigation pattern consistent across all four pages (exit only at the start, forward/back only afterward).

**Note for context (decision stands regardless):** `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §9 specifies Skip + Back both available on intermediate pages, reasoning that a user should not have to navigate backward first to exit. This was weighed against the layout constraint above; the decision above is final and supersedes that spec section for this app.

**Affected files:**
- `app/src/main/res/values-de/strings.xml` / `app/src/main/res/values/strings.xml` — no string change required (`walkthrough_skip`, `walkthrough_back`, `walkthrough_next`, `walkthrough_start` values are unaffected; only visibility per page changes)
- `app/src/main/java/com/isardomains/sameview/guide/WalkthroughScreen.kt:266–357` — `WalkthroughButtons()` branch logic (`pageIndex == 0` vs. `else` cases)
- `app/src/androidTest/java/com/isardomains/sameview/guide/WalkthroughScreenTest.kt` — likely asserts Skip visibility per page
- `app/src/androidTest/java/com/isardomains/sameview/guide/WalkthroughNavigationTest.kt` — likely asserts Skip navigation behavior on pages 2–3

**Change type:** Layout/behavior (Compose code change; no string resource change)

**Risk:** Medium. This is a behavior change, not just presentation — both listed test files will need updating to match the final per-page button set once implemented.

---

## 6. Open Decisions Before Implementation

All findings (F-01 through F-12) are decided (see §5). No open product decisions remain in this document.

(F-04's pre-removal caller check and F-05's `values.xml`/`values-de.xml` key naming are routine implementation-time steps, recorded under those findings' own Affected files / Risk fields in §5 — not open product decisions.)
