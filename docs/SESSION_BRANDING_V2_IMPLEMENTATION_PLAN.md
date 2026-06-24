# SESSION_BRANDING_V2_IMPLEMENTATION_PLAN.md

## Document Status

**Status:** Final — ready for implementation.

UX basis: `SESSION_BRANDING_V2_UX_REWORK.md` (approved).

This document covers implementation blocks, affected files, risks, tests, and documentation impact only. It does not redefine UX decisions.

---

## Prerequisites

The following files are stable and are **not modified** by any block in this plan:

- `BrandingHandleRenderer.kt`
- `BrandingPreviewCircle.kt`
- `BrandingNormalizer.kt`
- `BuiltinSymbolRenderer.kt`
- `GlobalBrandingRepository.kt`
- `SessionStorage` branding functions
- Metadata schema v6
- Backup integration
- `SliderRenderStrategy` branding extension
- All storage, normalization, and rendering tests

---

## Implementation Order

```
Block 1  Settings layout + wording
Block 2  Symbol BottomSheet              ← callers: Settings + EditSession
Block 3  EditSession card redesign       ← depends on Block 2
Block 4  ShareComparison extraction      ← independent of Blocks 1–3
Block 5  String cleanup                  ← after Blocks 1–4 are complete
```

---

## Block 1 — Settings: Layout and Wording

### Goal

Redesign the Settings branding card to V2 layout: new section title, always-visible description, placeholder circle in empty state, renamed action labels, corrected conditional visibility for Remove.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
app/src/androidTest/java/com/isardomains/sameview/ui/settings/SettingsScreenTest.kt
```

### Changes — `SettingsScreen.kt`

`SettingsScreenContent()`, branding `SettingsCard` (currently from line 343):

**Remove:**
- `SettingsCard(title = stringResource(R.string.settings_branding_section_title))`
- Conditional `BrandingPreviewCircle` rendering (`if (globalBrandingFile != null)`)
- `Text(settings_branding_description)` at its current position (after the circle)
- Test tags: `settings_branding_preview`, `settings_branding_choose_image`, `settings_branding_choose_symbol`, `settings_branding_remove`

**Add:**
- `SettingsCard(title = stringResource(R.string.settings_logo_section_title))`
- Description text **always visible**, above the circle row: `Text(settings_logo_description)`
- Circle row (64 dp circle + label to the right):
  - When `globalBrandingFile == null`: placeholder circle + `Text(settings_logo_none, testTag="settings_logo_placeholder")`
  - When `globalBrandingFile != null`: `BrandingPreviewCircle` + `Text(settings_logo_current, testTag="settings_logo_preview")`
- Placeholder circle spec: `Box(64.dp, CircleShape)`, 2 dp `SameViewAccent` border, `#F5F7FA` fill, centered 24 dp image-add icon in `SameViewSettingsSecondaryText`
- Buttons row: `TextButton(settings_logo_choose_photo, testTag="settings_logo_choose_photo")` + `TextButton(settings_logo_use_symbol, testTag="settings_logo_use_symbol")`
- Remove button: `if (hasBranding) TextButton(settings_logo_remove, testTag="settings_logo_remove", fullWidth)`

**No changes to:**
- `SettingsViewModel.kt` — no state changes required
- `SettingsScreenContent()` signature — `hasBranding`, `globalBrandingFile`, `onChooseImage`, `onChooseSymbol`, `onRemoveBranding` parameters remain identical
- `BuiltinSymbolPickerDialog` — still present in Block 1; replaced in Block 2

### New string keys (EN + DE)

```
settings_logo_section_title
settings_logo_description
settings_logo_none
settings_logo_current
settings_logo_choose_photo
settings_logo_use_symbol
settings_logo_remove
settings_logo_load_error
```

Deprecated V1 keys are **not removed** in this block — removal happens in Block 5.

### Risks

- **Low.** UI-only change. No ViewModel code changes.
- Card height increases (description now always visible). No known scroll-position dependencies.
- Test tags change — `SettingsScreenTest` must be updated accordingly.

### Tests — `SettingsScreenTest.kt`

**Add:**

```
brandingCard_descriptionAlwaysVisible_whenNoBranding()
    setContent(hasBranding = false, globalBrandingFile = null)
    onNodeWithText(settings_logo_description).assertIsDisplayed()

brandingCard_descriptionAlwaysVisible_whenBrandingSet()
    setContent(hasBranding = true, globalBrandingFile = <tmpFile>)
    onNodeWithText(settings_logo_description).assertIsDisplayed()

brandingCard_placeholderCircle_visibleWhenNoBranding()
    setContent(hasBranding = false)
    onNodeWithTag("settings_logo_placeholder").assertIsDisplayed()

brandingCard_previewCircle_visibleWhenBrandingSet()
    setContent(hasBranding = true, globalBrandingFile = <tmpFile>)
    onNodeWithTag("settings_logo_preview").assertIsDisplayed()

brandingCard_choosePhoto_alwaysVisible()
    setContent(hasBranding = false)
    onNodeWithTag("settings_logo_choose_photo").performScrollTo().assertIsDisplayed()

brandingCard_useSymbol_alwaysVisible()
    setContent(hasBranding = false)
    onNodeWithTag("settings_logo_use_symbol").performScrollTo().assertIsDisplayed()

brandingCard_removeLogo_hiddenWhenNoBranding()
    setContent(hasBranding = false)
    onNodeWithTag("settings_logo_remove").assertDoesNotExist()

brandingCard_removeLogo_visibleWhenBrandingSet()
    setContent(hasBranding = true, globalBrandingFile = <tmpFile>)
    onNodeWithTag("settings_logo_remove").performScrollTo().assertIsDisplayed()
```

**Update:** All existing tests referencing `settings_branding_*` test tags or string keys — update to new V2 keys.

---

## Block 2 — Symbol BottomSheet Migration

### Goal

New composable `BrandingSymbolPickerSheet.kt` (ModalBottomSheet with handle-preview cells). Both callers (`SettingsScreen.kt`, `EditSessionScreen.kt`) replace `BuiltinSymbolPickerDialog` with the new sheet.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheet.kt  ← NEW
app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt
app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
app/src/androidTest/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheetTest.kt  ← NEW
app/src/androidTest/java/com/isardomains/sameview/ui/settings/SettingsScreenTest.kt
app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt
```

### Changes

**`BrandingSymbolPickerSheet.kt` (new, package `ui.branding`):**
- `@OptIn(ExperimentalMaterial3Api::class) @Composable fun BrandingSymbolPickerSheet(onSymbolSelected: (BuiltinBrandingSymbol) -> Unit, onDismiss: () -> Unit)`
- `ModalBottomSheet(onDismissRequest = onDismiss)`
- Title: `Text(stringResource(R.string.branding_symbol_picker_title))`
- `LazyVerticalGrid(GridCells.Fixed(3))` — 6 cells from `BuiltinBrandingSymbol.entries`
- Each cell: 56 dp handle-preview circle (SameViewAccent ring with 12° top/bottom gaps, `#F5F7FA` fill, symbol at 72% diameter in `SameViewSettingsLabelText`) + name label in `labelSmall`
- Cell test tag: `"symbol_cell_${symbol.id}"`
- Cancel `TextButton(android.R.string.cancel, onClick = onDismiss)`

**`SettingsScreen.kt`:**
- `var showSymbolDialog` → `var showSymbolSheet`
- `BuiltinSymbolPickerDialog(...)` → `BrandingSymbolPickerSheet(...)`

**`EditSessionScreen.kt`:**
- `var showBrandingSymbolDialog` → `var showBrandingSymbolSheet`
- `BuiltinSymbolPickerDialog(...)` → `BrandingSymbolPickerSheet(...)`

After Block 2, `BuiltinSymbolPickerDialog` has no callers. The function remains in `SettingsScreen.kt` until Block 5 removes it.

### New string keys (EN + DE)

```
branding_symbol_picker_title
```

### Risks

- **Medium.** `ModalBottomSheet` has different dismissal lifecycle than `AlertDialog`.
  - Back gesture and drag-down trigger `onDismissRequest` → must map to the Cancel path (no symbol selected). Requires explicit test coverage.
  - `ModalBottomSheet` requires `@OptIn(ExperimentalMaterial3Api::class)`.
- Symbol cell arc geometry (12° gaps) must be implemented via Canvas, not via `CircleShape` border. Reference: `BrandingHandleRenderer.draw()` for the same arc specification.

### Tests — `BrandingSymbolPickerSheetTest.kt` (new)

```
symbolSheet_titleVisible()
    Show sheet → onNodeWithText(branding_symbol_picker_title).assertIsDisplayed()

symbolSheet_allSixSymbolCellsVisible()
    Show sheet → assert all 6 test tags:
    "symbol_cell_heart", "symbol_cell_star", "symbol_cell_camera",
    "symbol_cell_home", "symbol_cell_pin", "symbol_cell_fire"

symbolSheet_tapSymbol_callsCallback()
    var selected: BuiltinBrandingSymbol? = null
    onSymbolSelected = { selected = it }
    tap "symbol_cell_heart" → assertEquals(HEART, selected)

symbolSheet_tapSymbol_dismissesSheet()
    tap symbol → onNodeWithText(branding_symbol_picker_title).assertDoesNotExist()

symbolSheet_cancelButton_dismissesWithoutCallback()
    var called = false; onSymbolSelected = { called = true }
    tap Cancel → assertFalse(called)
    onNodeWithText(branding_symbol_picker_title).assertDoesNotExist()

symbolSheet_dragToDismiss_doesNotCallCallback()
    var called = false; onSymbolSelected = { called = true }
    drag down (performTouchInput swipe) → assertFalse(called)
```

**`SettingsScreenTest.kt`:** Add test verifying "Use a symbol" tap opens the sheet (not the dialog).

**`EditSessionScreenTest.kt`:** Add test verifying "Use a symbol" tap opens the sheet in both empty and populated states.

---

## Block 3 — Edit Session: Logo Card Redesign

### Goal

`EditSessionScreen.kt`: V2 card layout, symmetric actions, placeholder circle, type indicator. `EditSessionViewModel.kt`: two new StateFlows for logo type information.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt
app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt
app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt
```

### Changes — `EditSessionViewModel.kt`

**`InitialSessionFields` (line 56) — add two new fields with defaults:**
```
brandingType: String? = null       // "image" | "builtin" | null
brandingBuiltinId: String? = null  // symbol id | null
```

**`metadataReader` lambda — read new fields:**
```
val brandingObj = json.optJSONObject("branding")
brandingType    = brandingObj?.optString("type", "")?.takeIf { it.isNotEmpty() }
brandingBuiltinId = brandingObj?.optString("builtinId", "")?.takeIf { it.isNotEmpty() }
```

**New StateFlows:**
```
private val _sessionLogoType = MutableStateFlow<String?>(null)
val sessionLogoType: StateFlow<String?> = _sessionLogoType.asStateFlow()

private val _sessionLogoBuiltinId = MutableStateFlow<String?>(null)
val sessionLogoBuiltinId: StateFlow<String?> = _sessionLogoBuiltinId.asStateFlow()
```

**Init block:** set `_sessionLogoType.value = fields.brandingType` and `_sessionLogoBuiltinId.value = fields.brandingBuiltinId`

**Branding write callbacks** (`onImageUriSelectedForBranding`, `onSetSessionBrandingFromSymbol`, `onCopyFromGlobalBranding`): update `_sessionLogoType` and `_sessionLogoBuiltinId` after each successful write.

**`onRemoveSessionBranding`:** reset both flows to `null`.

### Changes — `EditSessionScreen.kt`

Branding card (currently from line 511):

**Remove:**
- `SettingsCard(title = stringResource(R.string.edit_session_card_branding))`
- `Text(edit_session_branding_none)` in empty state
- `TextButton(edit_session_branding_change)` in populated state (image-only — removed)
- Test tags: `edit_session_branding_none_text`, `edit_session_branding_change`, `edit_session_branding_remove`

**Add:**
- `SettingsCard(title = stringResource(R.string.edit_session_card_logo))`
- Description always visible: `Text(edit_session_logo_description)`
- Circle row: placeholder when `!hasBranding`, `BrandingPreviewCircle(sessionBrandingFile)` when `hasBranding`
  - Placeholder: identical spec to Block 1
- Type label beside circle:
  - Empty: `Text(edit_session_logo_none, testTag="edit_session_logo_none_text")`
  - Populated photo: `Text(edit_session_logo_type_photo, testTag="edit_session_logo_type_label")`
  - Populated symbol: `Text(stringResource(edit_session_logo_type_symbol, symbolDisplayName(sessionLogoBuiltinId)), testTag="edit_session_logo_type_label")`
- Conditional "Use your default logo": `if (!hasBranding && hasGlobalBranding)` → `TextButton(edit_session_logo_use_default, testTag="edit_session_logo_use_default")`
- Actions row **always visible** in both states:
  - `TextButton(edit_session_logo_choose_photo, testTag="edit_session_logo_choose_photo")`
  - `TextButton(edit_session_logo_use_symbol, testTag="edit_session_logo_use_symbol")`
- Remove: `if (hasBranding)` → `TextButton(edit_session_logo_remove, testTag="edit_session_logo_remove", fullWidth)`
- Private `symbolDisplayName(id: String?): String` helper maps symbol IDs to display names: `"heart"→"Heart"`, `"star"→"Star"`, `"camera"→"Camera"`, `"home"→"Home"`, `"pin"→"Pin"`, `"fire"→"Fire"`

### New string keys (EN + DE)

```
edit_session_card_logo
edit_session_logo_description
edit_session_logo_none
edit_session_logo_type_photo
edit_session_logo_type_symbol          (format string: "Symbol: %s")
edit_session_logo_use_default
edit_session_logo_choose_photo
edit_session_logo_use_symbol
edit_session_logo_remove
edit_session_logo_error
```

### Risks

- **Medium.** ViewModel extension. `InitialSessionFields` uses default values — no existing test call sites break.
- Symmetric actions in populated state: confirm that `onImageUriSelectedForBranding()` and `onSetSessionBrandingFromSymbol()` overwrite existing branding atomically when called on an already-branded session. V1 behavior via `updateSessionBranding()` temp-file-rename is already atomic — verify no guard condition blocks the call when `hasBranding == true`.
- `sessionLogoType`/`sessionLogoBuiltinId` must be updated after all four write paths — verify all four: choose photo, choose symbol, use default, remove.

### Tests — `EditSessionViewModelTest.kt`

```
sessionLogoType_isNull_whenNoBranding()
sessionLogoType_isImage_whenPhotoBrandingSet()
sessionLogoType_isBuiltin_whenSymbolBrandingSet()
sessionLogoBuiltinId_isNull_whenNoBranding()
sessionLogoBuiltinId_matchesSymbolId_whenSymbolSet()
sessionLogoType_updatesToImage_afterChoosePhoto()
sessionLogoType_updatesToBuiltin_afterChooseSymbol()
sessionLogoType_resetsToNull_afterRemove()
sessionLogoBuiltinId_resetsToNull_afterRemove()
```

### Tests — `EditSessionScreenTest.kt`

```
logoCard_placeholderCircle_visibleWhenNoBranding()
logoCard_previewCircle_visibleWhenBrandingSet()
logoCard_typeLabel_showsPhoto_whenPhotoBrandingSet()
logoCard_typeLabel_showsSymbolHeart_whenHeartBrandingSet()
logoCard_useDefaultLogo_visibleWhenNoBrandingAndGlobalExists()
logoCard_useDefaultLogo_hiddenWhenNoBrandingAndNoGlobal()
logoCard_useDefaultLogo_hiddenWhenBrandingAlreadySet()
logoCard_choosePhoto_visibleInEmptyState()
logoCard_choosePhoto_visibleInPopulatedState()
logoCard_useSymbol_visibleInEmptyState()
logoCard_useSymbol_visibleInPopulatedState()
logoCard_removeLogo_hiddenWhenNoBranding()
logoCard_removeLogo_visibleWhenBrandingSet()
logoCard_noChangeBrandingButton_exists()     ← assertDoesNotExist() for "edit_session_branding_change"
```

---

## Block 4 — Share Comparison: Logo Card Extraction

### Goal

`ShareComparisonScreen.kt`: remove branding toggle from Style card. Add standalone "Logo on handle" card between Style card and Information card. Slider-only conditional rendering. Toggle label renamed.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
app/src/androidTest/java/com/isardomains/sameview/ui/compare/ShareComparisonScreenTest.kt
```

**No changes to `ShareComparisonViewModel.kt`** — `hasBranding`, `useBranding`, and `onToggleUseBranding()` are unchanged.

### Changes — `ShareComparisonScreen.kt`

**Remove from Style card (currently from line 179):**
- `val brandingHintText = when { ... }`
- `InfoToggleRow(label = stringResource(R.string.share_comparison_branding_label), ...)`
- `HorizontalDivider` between branding toggle and preview
- Test tag `share_comparison_toggle_branding`

Remaining Style card content: `SameViewSegmentControl` + `HorizontalDivider` + `ShareComparisonPreview`. The preview call retains `useBranding = useBranding && hasBranding`.

**Insert new card after Style card:**

```
if (style == ShareComparisonStyle.SLIDER) {
    SettingsCard(title = stringResource(R.string.share_comparison_logo_card_title)) {
        Row {
            // Left: 64dp circle
            if (!hasBranding) {
                PlaceholderCircle(testTag = "share_comparison_logo_placeholder")
            } else {
                BrandingPreviewCircle(
                    brandingFile = sessionBrandingFile,
                    alpha = if (useBranding) 1f else 0.4f,
                    testTag = "share_comparison_logo_preview"
                )
            }
            // Right: state content
            if (!hasBranding) {
                Column {
                    Text(share_comparison_logo_none)
                    Text(share_comparison_logo_hint)
                }
            } else {
                SettingsSwitchRow(
                    label = stringResource(R.string.share_comparison_logo_show),
                    checked = useBranding,
                    onCheckedChange = { viewModel.onToggleUseBranding() },
                    testTag = "share_comparison_toggle_logo"
                )
            }
        }
    }
}
```

Note: `BrandingPreviewCircle` does not currently accept an `alpha` parameter. If needed, wrap in `Box(Modifier.alpha(...))` at the call site.

### New string keys (EN + DE)

```
share_comparison_logo_card_title
share_comparison_logo_none
share_comparison_logo_hint
share_comparison_logo_show
```

### Risks

- **Medium.** Screen height increases due to new card. Verify scroll behavior in tests, especially that the Share button remains reachable.
- On style switch Slider → Side-by-side: card disappears. `useBranding` state in ViewModel is preserved. On switch back to Slider: card reappears with previous toggle state. Test both transitions.
- `share_comparison_style_control` test tag and Style card structure are unchanged — no regression risk on Style card tests.

### Tests — `ShareComparisonScreenTest.kt`

**Add:**

```
logoCard_visible_whenSliderSelected()
    launch(style=SLIDER, hasBranding=false)
    onNodeWithText(share_comparison_logo_card_title).assertIsDisplayed()

logoCard_absent_whenSideBySideSelected()
    launch(style=SIDE_BY_SIDE)
    onNodeWithText(share_comparison_logo_card_title).assertDoesNotExist()

logoCard_emptyState_whenNoBranding()
    launch(style=SLIDER, hasBranding=false)
    onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()
    onNodeWithText(share_comparison_logo_none).assertIsDisplayed()
    onNodeWithText(share_comparison_logo_hint).assertIsDisplayed()

logoCard_populatedState_whenBrandingSet()
    launch(style=SLIDER, hasBranding=true)
    onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()

logoCard_toggle_showsLogo_label()
    launch(style=SLIDER, hasBranding=true)
    onNodeWithText(share_comparison_logo_show).assertIsDisplayed()

logoCard_noBrandingToggle_insideStyleCard()
    onNodeWithTag("share_comparison_toggle_branding").assertDoesNotExist()

logoCard_noSliderOnlyHint_visible()
    onNodeWithText(share_comparison_branding_hint_slider_only).assertDoesNotExist()

logoCard_disappears_onSwitchToSideBySide()
    launch(style=SLIDER, hasBranding=true)
    switch to SIDE_BY_SIDE
    onNodeWithText(share_comparison_logo_card_title).assertDoesNotExist()

logoCard_reappears_onSwitchBackToSlider()
    launch(style=SLIDER, hasBranding=true)
    switch to SIDE_BY_SIDE → switch back to SLIDER
    onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()
```

**Update:** All existing tests referencing `share_comparison_branding_*` strings or `share_comparison_toggle_branding` test tag.

---

## Block 5 — String Cleanup

### Goal

Remove all deprecated V1 branding string keys. Remove the unused `BuiltinSymbolPickerDialog` composable. Verify clean build.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
```

### Changes

**`strings.xml` + `strings-de.xml` — remove:**

```
settings_branding_section_title
settings_branding_description
settings_branding_choose_image
settings_branding_choose_symbol
settings_branding_remove
settings_branding_load_error
edit_session_card_branding
edit_session_branding_none
edit_session_branding_change
edit_session_branding_remove
edit_session_branding_copy_global
edit_session_branding_error
share_comparison_branding_label
share_comparison_branding_hint_edit_session
share_comparison_branding_hint_slider_only
```

**`SettingsScreen.kt`:** Remove `BuiltinSymbolPickerDialog` composable function and its associated imports. The function was replaced by `BrandingSymbolPickerSheet` in Block 2 and has no remaining callers after Blocks 1–4.

### Risks

- **Low.** Compile-time errors surface any remaining references immediately. The block is self-validating.
- Verify that no test code references deprecated keys via `R.string.*`.

### Tests

No new tests. Verification:

```
./gradlew assembleDebug    → BUILD SUCCESSFUL
./gradlew assembleRelease  → BUILD SUCCESSFUL
./gradlew testDebugUnitTest → all existing unit tests pass
```

---

## Complete Test Matrix

| Block | Unit Tests | Instrumentation Tests |
|---|---|---|
| 1 | — | `SettingsScreenTest` (new + updated) |
| 2 | — | `BrandingSymbolPickerSheetTest` (new), `SettingsScreenTest`, `EditSessionScreenTest` |
| 3 | `EditSessionViewModelTest` (new) | `EditSessionScreenTest` (new + updated) |
| 4 | — | `ShareComparisonScreenTest` (new + updated) |
| 5 | — | Build verification only |

---

## Documentation Impact

After all five blocks are complete, the following documents must be updated. The exact scope is defined in `SESSION_BRANDING_V2_UX_REWORK.md §11`.

| Document | Sections | Reason |
|---|---|---|
| `SESSION_BRANDING_V1.md` | §11, §12.2, §13.1, §13.3 | Layout, actions, toggle behavior superseded |
| `SESSION_METADATA_EDITOR_V1.md` | §21.2, §21.5 | Card contents and symbol picker type superseded |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18, §15.3 | Toggle placement and screen layout superseded |
| `SETTINGS_UX_V1.md` | §11.1 | Action labels and description text superseded |
| `IMPLEMENTATION_NOTES.md` | Branding entries | Update after each block is completed and verified |
