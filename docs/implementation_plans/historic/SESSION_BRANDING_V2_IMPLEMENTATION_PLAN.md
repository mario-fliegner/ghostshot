# SESSION_BRANDING_V2_IMPLEMENTATION_PLAN.md

## Document Status

**Status:** Final — corrected architecture edition. Ready for implementation.

UX basis: `SESSION_BRANDING_V2_UX_REWORK.md` (Architecture Corrected Edition).

This plan replaces all prior implementation blocks. The original V2 blocks are no longer valid.

---

## Current State

The V2 UX rework was implemented based on the original V2 specification. That implementation is partially correct and must be modified, not discarded. The following is already correct and requires no change:

- `SettingsScreen.kt` — "Your logo" section with full management ✓
- `BrandingSymbolPickerSheet.kt` — ModalBottomSheet with 6 symbol cells (one bug: see Block 1) ✓
- `BrandingPreviewCircle.kt` — reusable preview composable ✓
- `BrandingHandleRenderer.kt` — export-time handle renderer ✓
- `SliderRenderStrategy.kt` — accepts `brandingBitmap: Bitmap?` ✓
- `GlobalBrandingRepository.kt` — global branding read/write ✓
- `SettingsViewModel.kt` — global branding state ✓
- `SettingsScreenTest.kt` — all logo card tests ✓
- Metadata schema v6 — valid; existing sessions with `branding-handle.png` remain scannable ✓

The following must change:

- `BrandingSymbolPickerSheet.kt` — symbol color bug (Block 1)
- `EditSessionScreen.kt` — Logo card must be removed (Block 2)
- `EditSessionViewModel.kt` — branding state and operations must be removed (Block 2)
- `EditSessionScreenTest.kt` — branding tests must be deleted (Block 2)
- `EditSessionViewModelTest.kt` — branding tests must be deleted (Block 2)
- `ShareComparisonScreen.kt` — Logo card must gain full management UI (Block 3)
- `ShareComparisonViewModel.kt` — add global-default copy on init; add branding write operations (Block 3)
- `ShareComparisonScreenTest.kt` — tests must be updated and extended (Block 3)
- `ShareComparisonViewModelTest.kt` — branding tests must be added (Block 3)
- `strings.xml` / `strings-de.xml` — add, update, and remove keys (Block 4)

No changes to `ShareRenderConfig.kt`, `ShareImageRenderer.kt`, or `SliderRenderStrategy.kt` — they already operate correctly on the session branding file.

---

## Architecture Change Summary

### Before (V2 original)

```
GlobalBrandingRepository (filesDir/branding/handle.png)
    ↓ copied at session creation
Session Branding (branding-handle.png in session folder)
    ↓ read at export time
Share Comparison Image export
```

Edit Session: managed session-specific branding  
Share Comparison: toggle only (Show logo / Hide logo)

### After (V2 corrected)

```
GlobalBrandingRepository (filesDir/branding/handle.png)
    ↓ copied into session folder on first Share Comparison open (if session has no branding)
Session Branding (branding-handle.png in session folder)
    ↓ managed by Share Comparison (choose photo / use symbol / remove)
    ↓ read at export time by ShareImageRenderer — unchanged
Share Comparison Image export
```

Edit Session: no branding  
Share Comparison: toggle + full management (choose photo, use symbol, remove)

The persistence model is identical to V1. `branding-handle.png` is written to the session folder and included in backups. The only change is **which screen writes it**: Edit Session no longer does; Share Comparison now does. `ShareRenderConfig`, `ShareImageRenderer`, and `SliderRenderStrategy` are unchanged — they already read from the session folder.

---

## Implementation Order

```
Block 1  Fix symbol picker contrast        ← safe to land immediately; no dependencies
Block 2  Remove branding from Edit Session ← independent of Block 1
Block 3  Share Comparison management       ← depends on nothing new; most work
Block 4  String cleanup                    ← after Blocks 1–3 are complete
```

Blocks 1 and 2 are independent. Block 3 is the largest change. Block 4 is cleanup.

---

## Block 1 — Fix Symbol Picker Contrast

### Goal

Remove the incorrect `colorFilter` from `BrandingSymbolPickerSheet` that makes symbol icons white on the `#F5F7FA` circle background. This is a self-contained regression fix.

### Root cause

`BrandingSymbolPickerSheet.kt:95` applies `colorFilter = ColorFilter.tint(SameViewSettingsLabelText)`. `SameViewSettingsLabelText` is `Color(0xFFFFFFFF)` (pure white). The symbol cell background is `#F5F7FA` (near-white). Result: white icons on near-white background (~1.07:1 contrast). Meanwhile the actual export renders the VectorDrawable's native fill (`#17202F`, dark navy) — ~14.6:1 contrast. Violates spec rule P-03 (preview must equal export result).

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheet.kt
```

### Change

**Remove one line** from the `Image` composable inside the symbol cell:

```kotlin
// BEFORE (line 92–97)
Image(
    painter = painterResource(symbol.drawableRes),
    contentDescription = symbol.id,
    colorFilter = ColorFilter.tint(SameViewSettingsLabelText),   ← DELETE
    modifier = Modifier.size(40.dp)
)

// AFTER
Image(
    painter = painterResource(symbol.drawableRes),
    contentDescription = symbol.id,
    modifier = Modifier.size(40.dp)
)
```

Also remove unused imports if `ColorFilter` and the `SameViewSettingsLabelText` import are no longer referenced elsewhere in the file.

### Risks

None. Single-line deletion. The VectorDrawable native color (`#17202F`) is the correct color per spec. No other composable, ViewModel, or test is affected.

### Tests

**Update `BrandingSymbolPickerSheetTest`:**

```
symbolSheet_symbolIcons_visibleAgainstBackground()
    Show sheet → for each symbol cell, confirm the icon is rendered
    and that no white-tint colorFilter is present.
    (Implementation: verify the rendered output has sufficient contrast
    or verify no colorFilter parameter is passed to the Image composable.)
```

All existing `BrandingSymbolPickerSheetTest` tests remain valid and must continue to pass.

**Build verification:**
```
./gradlew assembleDebug   → BUILD SUCCESSFUL
./gradlew testDebugUnitTest → all unit tests pass
```

---

## Block 2 — Remove Branding from Edit Session

### Goal

Remove the entire branding feature from `EditSessionScreen` and `EditSessionViewModel`. After this block, Edit Session has no branding UI, no branding state, and no branding operations.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt
app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt
app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt
app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt
```

### Changes — `EditSessionViewModel.kt`

**Remove StateFlows:**
```kotlin
val hasBranding: StateFlow<Boolean>
val hasGlobalBranding: StateFlow<Boolean>
val sessionBrandingFile: StateFlow<File?>
val sessionLogoType: StateFlow<String?>
val sessionLogoBuiltinId: StateFlow<String?>
```

**Remove SharedFlow:**
```kotlin
val brandingError: SharedFlow<Unit>
```

**Remove functions:**
```kotlin
fun onImageUriSelectedForBranding(uri: Uri)
fun onSetSessionBrandingFromSymbol(symbol: BuiltinBrandingSymbol)
fun onCopyFromGlobalBranding()
fun onRemoveSessionBranding()
```

**Remove `InitialSessionFields` fields** (if present):
```
brandingType: String?
brandingBuiltinId: String?
```

**Remove `init` block reads** of the `branding` JSON block.

**Remove imports** no longer needed: `GlobalBrandingRepository`, `BrandingNormalizer`, `BuiltinBrandingSymbol`, any branding-related imports.

### Changes — `EditSessionScreen.kt`

**Remove from state collection block:**
```kotlin
val hasBranding by viewModel.hasBranding.collectAsStateWithLifecycle()
val hasGlobalBranding by viewModel.hasGlobalBranding.collectAsStateWithLifecycle()
val sessionBrandingFile by viewModel.sessionBrandingFile.collectAsStateWithLifecycle()
val sessionLogoType by viewModel.sessionLogoType.collectAsStateWithLifecycle()
val sessionLogoBuiltinId by viewModel.sessionLogoBuiltinId.collectAsStateWithLifecycle()
```

**Remove from dialog state:**
```kotlin
var showBrandingSymbolSheet by remember { mutableStateOf(false) }
```

**Remove the photo picker launcher:**
```kotlin
val brandingImageLauncher = rememberLauncherForActivityResult(...)
```

**Remove the branding error snackbar LaunchedEffect and message:**
```kotlin
val brandingErrorMessage = stringResource(R.string.edit_session_logo_error)
LaunchedEffect(Unit) { viewModel.brandingError.collect { ... } }
```

**Remove the `BrandingSymbolPickerSheet` call block:**
```kotlin
if (showBrandingSymbolSheet) {
    BrandingSymbolPickerSheet(...)
}
```

**Remove the entire Logo card** (currently the last card in the form, `SettingsCard(title = stringResource(R.string.edit_session_card_logo))`):
The card and all its contents are deleted. The form ends with the Location card.

**Remove imports** no longer needed: `BrandingPreviewCircle`, `BrandingSymbolPickerSheet`, branding-related string resources, `ActivityResultContracts.PickVisualMedia` if not used elsewhere in the file, `BuiltinBrandingSymbol`.

After this block, the `EditSessionScreen` card order is: Session → Reference photo → Current photo → Location.

### Tests — `EditSessionScreenTest.kt`

**Delete all tests with names containing `logo` or `branding`:**

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
logoCard_noChangeBrandingButton_exists()
favoriteButton_*   ← keep these (unrelated to branding)
```

**Add one regression guard:**

```
editSession_noBrandingCard_present()
    setContent(...)
    onNodeWithText(stringResource(R.string.edit_session_card_logo)).assertDoesNotExist()
    onNodeWithTag("edit_session_logo_placeholder").assertDoesNotExist()
    onNodeWithTag("edit_session_logo_preview").assertDoesNotExist()
    onNodeWithTag("edit_session_logo_choose_photo").assertDoesNotExist()
    onNodeWithTag("edit_session_logo_use_symbol").assertDoesNotExist()
```

### Tests — `EditSessionViewModelTest.kt`

**Delete all tests with names containing `logo`, `branding`, or `sessionLogo`:**

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

All other `EditSessionViewModelTest` tests (title, description, reference date, location, isDirty, isSaving, favorites) are unaffected and must remain green.

### Risks

- **Low.** Removal is straightforward. No new logic is introduced.
- Verify that removing the `brandingError` SharedFlow and its `LaunchedEffect` does not affect the existing save-error snackbar (which uses a separate event flow).
- Confirm that `PickVisualMedia` launcher removal does not break the DatePicker or any other reference image picker in EditSession. Check if `EditSessionScreen` uses `PickVisualMedia` for the reference photo; if so, only the branding-specific launcher is removed.

### Verification

```
./gradlew assembleDebug                → BUILD SUCCESSFUL
./gradlew testDebugUnitTest            → all unit tests pass (EditSessionViewModelTest green)
./gradlew connectedDebugAndroidTest    → EditSessionScreenTest passes
```

---

## Block 3 — Share Comparison: Session Branding Management

### Goal

Redesign `ShareComparisonViewModel` and `ShareComparisonScreen` so that the Logo card supports full session branding management (choose photo, use symbol, remove) in addition to the existing Show logo toggle. The persistence model is unchanged from V1: branding is written atomically to `branding-handle.png` in the session folder. The global default is copied into the session on first open when no session branding exists.

No changes are required to `SliderRenderStrategy`, `ShareRenderConfig`, or `ShareImageRenderer`. They already operate on the session folder and the `branding-handle.png` file. `BrandingPreviewCircle` accepts a `File` and continues to receive `File(sessionDir, "branding-handle.png")` — no API change needed.

### Affected files

```
app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt   ← UPDATE
app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt      ← UPDATE
app/src/test/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModelTest.kt       ← UPDATE
app/src/androidTest/java/com/isardomains/sameview/ui/compare/ShareComparisonScreenTest.kt   ← UPDATE
```

No changes to: `BrandingHandleRenderer.kt`, `BrandingNormalizer.kt`, `GlobalBrandingRepository.kt`, `BrandingPreviewCircle.kt`, `BrandingSymbolPickerSheet.kt`, `SliderRenderStrategy.kt`, `ShareRenderConfig.kt`, `ShareImageRenderer.kt`.

### Changes — `ShareComparisonViewModel.kt`

#### Branding state — what already exists (keep)

```kotlin
val hasBranding: StateFlow<Boolean>      // backed by sessionBrandingFile.exists()
val useBranding: StateFlow<Boolean>      // toggle state
fun onToggleUseBranding()                // unchanged
```

The `sessionBrandingFile` derivation (`File(sessionDir, "branding-handle.png")`) remains. `hasBranding` continues to be derived from `sessionBrandingFile.exists()`, refreshed after every branding write.

#### Initialization — add global-default copy

Extend the existing `init` block (or the session metadata load coroutine) with:

```kotlin
// After session metadata is loaded:
viewModelScope.launch(Dispatchers.IO) {
    val sessionFile = File(sessionDir, "branding-handle.png")
    if (!sessionFile.exists()) {
        val globalFile = globalBrandingRepository.getBrandingFile()
        if (globalFile != null && globalFile.exists()) {
            SessionStorage.copyGlobalBrandingToSession(
                sessionsRoot = sessionsRoot,
                sessionId = sessionId,
                globalBrandingFile = globalFile,
                globalBrandingMeta = globalBrandingRepository.getBrandingMeta()
            )
            // Refresh hasBranding — sessionBrandingFile.exists() now returns true
            _hasBranding.value = sessionFile.exists()
            if (_hasBranding.value) _useBranding.value = true
        }
    }
}
```

This runs once per screen open, before the screen becomes interactive (hide the Logo card behind `isLoadingBranding` until complete). The global file is never modified. Only the session folder receives the copy.

#### New branding operations — add

These follow the exact same pattern as the removed `EditSessionViewModel` branding functions:

```kotlin
val brandingError: SharedFlow<Unit>   // MutableSharedFlow, replay = 0

fun onImageUriSelectedForBranding(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val success = /* normalize uri → brandingPng ByteArray, then call */
            SessionStorage.updateSessionBranding(sessionsRoot, sessionId, brandingPng, "image", null)
        if (success) {
            _hasBranding.value = true
            _useBranding.value = true
        } else {
            brandingErrorEmitter.emit(Unit)
        }
    }
}

fun onSetSessionBrandingFromSymbol(symbol: BuiltinBrandingSymbol) {
    viewModelScope.launch(Dispatchers.IO) {
        val success = /* render symbol → brandingPng ByteArray, then call */
            SessionStorage.updateSessionBranding(sessionsRoot, sessionId, brandingPng, "builtin", symbol.id)
        if (success) {
            _hasBranding.value = true
            _useBranding.value = true
        } else {
            brandingErrorEmitter.emit(Unit)
        }
    }
}

fun onRemoveSessionBranding() {
    viewModelScope.launch(Dispatchers.IO) {
        SessionStorage.removeSessionBranding(sessionsRoot, sessionId)
        _hasBranding.value = false
        _useBranding.value = false
    }
}
```

**`GlobalBrandingRepository` is never written to by any of these functions.**

#### Inject `GlobalBrandingRepository`

Add `GlobalBrandingRepository` as an injected dependency if not already present. It is needed for the initialization copy only.

### Changes — `ShareComparisonScreen.kt`

#### New launcher and sheet

Add (directly reused from the removed `EditSessionScreen` code):

```kotlin
val brandingImageLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    if (uri != null) viewModel.onImageUriSelectedForBranding(uri)
}

var showBrandingSymbolSheet by remember { mutableStateOf(false) }

if (showBrandingSymbolSheet) {
    BrandingSymbolPickerSheet(
        onSymbolSelected = { symbol ->
            showBrandingSymbolSheet = false
            viewModel.onSetSessionBrandingFromSymbol(symbol)
        },
        onDismiss = { showBrandingSymbolSheet = false }
    )
}
```

Add branding error snackbar to the existing `LaunchedEffect`:

```kotlin
val brandingErrorMessage = stringResource(R.string.share_comparison_logo_error)
LaunchedEffect(Unit) {
    launch { viewModel.brandingError.collect { snackbarHostState.showSnackbar(brandingErrorMessage) } }
    launch { viewModel.events.collect { event -> ... } }  // existing
}
```

The `sessionBrandingFile` derivation already exists in the current screen code and requires no change:

```kotlin
val sessionBrandingFile = remember(sessionDir) { File(sessionDir, "branding-handle.png") }
```

`BrandingPreviewCircle(brandingFile = sessionBrandingFile)` continues to work unchanged.

#### Logo card redesign

Replace the current Logo card content with the full management layout per spec §4.

**Empty state** (`!hasBranding`):

```kotlin
// Placeholder circle
Box(
    modifier = Modifier.size(64.dp).clip(CircleShape)
        .background(Color(0xFFF5F7FA)).border(2.dp, SameViewAccent, CircleShape)
        .testTag("share_comparison_logo_placeholder"),
    contentAlignment = Alignment.Center
) {
    Icon(Icons.Outlined.AddPhotoAlternate, tint = SameViewSettingsSecondaryText, ...)
}
Spacer(12.dp)
Text(stringResource(R.string.share_comparison_logo_none), ...)

// Action buttons row (below the circle row)
Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
    TextButton(
        onClick = { brandingImageLauncher.launch(PickVisualMedia.ImageOnly) },
        modifier = Modifier.weight(1f).testTag("share_comparison_logo_choose_photo")
    ) { Text(stringResource(R.string.share_comparison_logo_choose_photo)) }
    TextButton(
        onClick = { showBrandingSymbolSheet = true },
        modifier = Modifier.weight(1f).testTag("share_comparison_logo_use_symbol")
    ) { Text(stringResource(R.string.share_comparison_logo_use_symbol)) }
}
```

**Populated state** (`hasBranding`):

```kotlin
// Circle + toggle row
Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.alpha(if (useBranding) 1f else 0.4f)) {
        BrandingPreviewCircle(
            brandingFile = sessionBrandingFile,
            modifier = Modifier.testTag("share_comparison_logo_preview")
        )
    }
    Spacer(12.dp)
    Box(Modifier.weight(1f)) {
        SettingsSwitchRow(
            label = stringResource(R.string.share_comparison_logo_show),
            checked = useBranding,
            onCheckedChange = { viewModel.onToggleUseBranding() },
            testTag = "share_comparison_toggle_logo"
        )
    }
}
Spacer(8.dp)
// Action buttons row (same as empty state)
Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
    TextButton(...choose photo...) { ... }
    TextButton(...use symbol...) { ... }
}
// Remove button
TextButton(
    onClick = { viewModel.onRemoveSessionBranding() },
    modifier = Modifier.fillMaxWidth().testTag("share_comparison_logo_remove")
) { Text(stringResource(R.string.share_comparison_logo_remove)) }
```

Remove: the old empty-state `share_comparison_logo_hint` text and its string reference.

#### Loading state

Add `val isLoadingBranding by viewModel.isLoadingBranding.collectAsStateWithLifecycle()`. While `isLoadingBranding == true`, render the placeholder circle only (no action buttons, no toggle). This prevents a brief empty-state flash before the global-default copy completes.

### Tests — `ShareComparisonViewModelTest.kt`

**Add new branding-specific unit tests:**

```
sessionBranding_autocopiedFromGlobal_whenSessionHasNoBranding()
    Provide sessionDir with no branding-handle.png.
    Mock globalBrandingRepository to return a valid global file.
    Init ViewModel → SessionStorage.copyGlobalBrandingToSession called once.
    hasBranding == true, useBranding == true.

sessionBranding_notOverwritten_whenSessionAlreadyHasBranding()
    Provide sessionDir with existing branding-handle.png.
    Mock globalBrandingRepository to return a valid global file.
    Init ViewModel → SessionStorage.copyGlobalBrandingToSession NOT called.
    hasBranding == true.

sessionBranding_startsEmpty_whenNoSessionBrandingAndNoGlobal()
    Provide sessionDir with no branding-handle.png.
    Mock globalBrandingRepository to return null.
    Init ViewModel → hasBranding == false, useBranding == false.

onImageUriSelectedForBranding_withValidUri_setsBrandingAndEnablesToggle()
    Call onImageUriSelectedForBranding(validUri) →
    SessionStorage.updateSessionBranding called.
    hasBranding == true, useBranding == true.

onImageUriSelectedForBranding_withFailure_emitsBrandingError_doesNotChangeBranding()
    Mock SessionStorage.updateSessionBranding to return false.
    Call onImageUriSelectedForBranding(uri) →
    brandingError emitted, hasBranding unchanged.

onSetSessionBrandingFromSymbol_writesBrandingToSession()
    Call onSetSessionBrandingFromSymbol(HEART) →
    SessionStorage.updateSessionBranding called with type="builtin", builtinId="heart".
    hasBranding == true, useBranding == true.

onRemoveSessionBranding_clearsBrandingAndDisablesToggle()
    Set hasBranding = true. Call onRemoveSessionBranding() →
    SessionStorage.removeSessionBranding called.
    hasBranding == false, useBranding == false.

onToggleUseBranding_togglesState_whenBrandingPresent()
    hasBranding = true. Toggle once → useBranding == false. Toggle again → useBranding == true.

globalDefault_neverModified_byChoosePhoto()
    Call onImageUriSelectedForBranding(uri) →
    globalBrandingRepository write methods are never called.

globalDefault_neverModified_byChooseSymbol()
    Call onSetSessionBrandingFromSymbol(symbol) →
    globalBrandingRepository write methods are never called.

globalDefault_neverModified_byRemove()
    Call onRemoveSessionBranding() →
    globalBrandingRepository write methods are never called.

shareRenderConfig_passesBrandingFromSessionFile_whenToggleOn()
    hasBranding = true, useBranding = true.
    Trigger share → ShareImageRenderer loads branding-handle.png from sessionDir.
    (Verify via the existing useBranding path already in the renderer.)

shareRenderConfig_noBranding_whenToggleOff()
    hasBranding = true, useBranding = false.
    Trigger share → branded handle NOT rendered.
```

**Existing tests for style, quality, caption, and isRendering are unchanged.**

### Tests — `ShareComparisonScreenTest.kt`

**Update tests that referenced old toggle-only Logo card state** — add setup for new `hasBranding` scenarios.

**Tests to add:**

```
logoCard_empty_showsActionButtons_whenNoBranding()
    Launch(hasBranding = false) →
    onNodeWithTag("share_comparison_logo_choose_photo").assertIsDisplayed()
    onNodeWithTag("share_comparison_logo_use_symbol").assertIsDisplayed()
    onNodeWithTag("share_comparison_logo_remove").assertDoesNotExist()
    onNodeWithTag("share_comparison_toggle_logo").assertDoesNotExist()

logoCard_populated_showsAllElements_whenBrandingSet()
    Launch(hasBranding = true, useBranding = true) →
    onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()
    onNodeWithTag("share_comparison_logo_choose_photo").assertIsDisplayed()
    onNodeWithTag("share_comparison_logo_use_symbol").assertIsDisplayed()
    onNodeWithTag("share_comparison_logo_remove").assertIsDisplayed()

logoCard_remove_transitionsToEmptyState()
    Launch(hasBranding = true)
    onNodeWithTag("share_comparison_logo_remove").performClick()
    onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()
    onNodeWithTag("share_comparison_toggle_logo").assertDoesNotExist()

logoCard_choosePhoto_opensPhotoPicker()
    onNodeWithTag("share_comparison_logo_choose_photo").performClick()
    → verify photo picker launched

logoCard_useSymbol_opensSheet()
    onNodeWithTag("share_comparison_logo_use_symbol").performClick()
    onNodeWithText(branding_symbol_picker_title).assertIsDisplayed()

logoCard_noHintText_addOneInEditSession()
    Launch(hasBranding = false)
    onNodeWithText("Add one in Edit session.").assertDoesNotExist()

logoCard_toggle_dimmsPreviewWhenOff()
    Launch(hasBranding = true, useBranding = false)
    onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    (alpha verified visually or via screenshot comparison)

sideByStyle_logoCard_absent()
    Launch(style = SIDE_BY_SIDE, hasBranding = true)
    onNodeWithText(share_comparison_logo_card_title).assertDoesNotExist()
    onNodeWithTag("share_comparison_logo_choose_photo").assertDoesNotExist()
```

**Tests to retain from original V2:**

```
logoCard_visible_whenSliderSelected()
logoCard_absent_whenSideBySideSelected()
logoCard_toggle_showsLogo_label()
logoCard_noBrandingToggle_insideStyleCard()
logoCard_disappears_onSwitchToSideBySide()
logoCard_reappears_onSwitchBackToSlider()
```

### Regression guards

```
settings_brandingFile_neverModified_byShareComparison()
    Verify GlobalBrandingRepository.setBranding() and removeBranding()
    are never called from ShareComparisonViewModel.

sideBySide_neverRendersBranding()
    Export Side-by-side with session branding set →
    SliderRenderStrategy not invoked (SideBySideStrategy runs instead).
    Branded handle never appears.

toggleOff_rendersStandardHandle()
    Export Slider with hasBranding = true and useBranding = false →
    ShareImageRenderer passes brandingBitmap = null to SliderRenderStrategy.
    Standard SameView handle rendered.

sessionFile_persistsAcrossScreenReopen()
    Set session branding via onImageUriSelectedForBranding.
    Close ViewModel. Create new ViewModel for same sessionId.
    hasBranding == true (file was written to disk).
```

### Risks

- **Medium — ViewModel initialization race:** The global-default copy is IO-bound. The `isLoadingBranding` guard must prevent the screen from showing the empty state while the copy is in progress. Test that the loading guard prevents a flash of the empty state followed by the populated state.

- **Low — `GlobalBrandingRepository` injection:** If `ShareComparisonViewModel` does not already have `GlobalBrandingRepository` injected (it may not, since V2 previously read the session file only), add the Hilt injection. This is additive and does not affect other callers.

- **Low — No `BrandingPreviewCircle` API change needed:** The screen already constructs `sessionBrandingFile = File(sessionDir, "branding-handle.png")`. Since branding writes now go to that same file, the existing `BrandingPreviewCircle(brandingFile = sessionBrandingFile)` call continues to work. Coil invalidates its cache when the file changes on disk (or force-invalidate via a `key` parameter if needed).

---

## Block 4 — String Cleanup

### Goal

Remove all deprecated branding string keys. Add new Share Comparison management keys. Update changed keys. Verify clean build.

### Affected files

```
app/src/main/res/values/strings.xml
app/src/main/res/values-de/strings.xml
```

### Changes

**Remove (deprecated V1 keys):**

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

**Remove (V2 Edit Session keys, now obsolete):**

```
edit_session_card_logo
edit_session_logo_description
edit_session_logo_none
edit_session_logo_type_photo
edit_session_logo_type_symbol
edit_session_logo_use_default
edit_session_logo_choose_photo
edit_session_logo_use_symbol
edit_session_logo_remove
edit_session_logo_error
```

**Remove (V2 Share Comparison key — concept no longer exists):**

```
share_comparison_logo_hint    ← was "Add one in Edit session." — removed; user adds directly in Share Comparison
```

**Keep unchanged (value stays "No logo for this comparison."):**

```
share_comparison_logo_none    ← no text update needed; "comparison" remains the correct noun
```

**Add (new V2 Share Comparison management keys):**

```xml
<string name="share_comparison_logo_choose_photo">Choose photo</string>
<string name="share_comparison_logo_use_symbol">Use a symbol</string>
<string name="share_comparison_logo_remove">Remove logo</string>
<string name="share_comparison_logo_error">Couldn't set logo</string>
```

**Unchanged (keep):**

```
settings_logo_section_title
settings_logo_description
settings_logo_none
settings_logo_current
settings_logo_choose_photo
settings_logo_use_symbol
settings_logo_remove
settings_logo_load_error
share_comparison_logo_card_title
share_comparison_logo_show
branding_symbol_picker_title
```

Update `values-de/strings.xml` correspondingly, following the project informal address rule (`du/dein`).

### Risks

- **Low.** Compile-time errors surface any remaining references immediately. Self-validating block.
- Grep for removed keys before deletion to confirm no production or test code references them.

### Tests

No new tests. Verification:

```
./gradlew assembleDebug    → BUILD SUCCESSFUL
./gradlew assembleRelease  → BUILD SUCCESSFUL
./gradlew testDebugUnitTest → all unit tests pass
./gradlew connectedDebugAndroidTest → all instrumentation tests pass
```

---

## Complete Test Matrix

| Block | Unit Tests | Instrumentation Tests |
|---|---|---|
| 1 | — | `BrandingSymbolPickerSheetTest` (1 new/updated) |
| 2 | `EditSessionViewModelTest` (delete ~9 branding tests; 1 new regression guard) | `EditSessionScreenTest` (delete all logo tests; 1 new regression guard) |
| 3 | `ShareComparisonViewModelTest` (~13 new branding tests) | `ShareComparisonScreenTest` (~9 new; ~6 updated) |
| 4 | — | Build verification only |

---

## Test Categories by Requirement

### Tests that must be removed (branding leaves Edit Session)

| Test file | Tests to delete |
|---|---|
| `EditSessionViewModelTest.kt` | All `sessionLogoType_*` and `sessionLogoBuiltinId_*` tests |
| `EditSessionScreenTest.kt` | All `logoCard_*` tests |

### Tests that must be rewritten (branding moves to Share Comparison)

| Test | Direction |
|---|---|
| `ShareComparisonScreenTest`: logo empty state | Remove hint text check; add action button checks |
| `ShareComparisonScreenTest`: logo populated state | Add choose photo, use symbol, remove button checks |
| `ShareComparisonViewModelTest`: hasBranding / useBranding | Update to reflect global-default initialization |

### New tests required for Share Comparison branding management

- Global default auto-copied to session on screen open when session has no branding
- Session branding NOT overwritten when session already has branding
- Choose photo writes normalized PNG to session `branding-handle.png`
- Use symbol renders and writes to session `branding-handle.png`
- Remove deletes `branding-handle.png` from session folder
- Normalization failure emits error, leaves existing file intact
- Session branding persists across screen re-opens (written to disk, not in-memory)
- Toggle controls whether the session branding bitmap is passed to the renderer

### Tests verifying global default is never modified by Share Comparison actions

- `globalDefault_neverModified_byChoosePhoto()`
- `globalDefault_neverModified_byChooseSymbol()`
- `globalDefault_neverModified_byRemove()`
(All three in `ShareComparisonViewModelTest` — assert `GlobalBrandingRepository` write methods are never called)

### Tests verifying session self-containment

- `sessionBranding_autocopiedFromGlobal_whenSessionHasNoBranding()` — global is copied into session
- `sessionFile_persistsAcrossScreenReopen()` — file survives ViewModel recreation
- `sessionBranding_notOverwritten_whenSessionAlreadyHasBranding()` — existing session branding is not replaced by global

### Tests verifying Split view automatically uses global default branding (via session copy)

- `sessionBranding_autocopiedFromGlobal_whenSessionHasNoBranding()`
- `sessionBranding_startsEmpty_whenNoSessionBrandingAndNoGlobal()`

### Tests verifying Side-by-side never renders branding

- `sideByStyle_logoCard_absent()`
- `sideBySide_neverRendersBranding()` — Side-by-side path does not invoke `SliderRenderStrategy`; branded handle never appears regardless of `hasBranding` state

### Tests verifying Show logo toggle continues to control visibility

- `onToggleUseBranding_togglesState_whenBrandingPresent()`
- `shareRenderConfig_noBranding_whenToggleOff()` — renderer receives no branding when toggle is OFF
- `logoCard_toggle_dimmsPreviewWhenOff()`

### Regression tests — existing functionality must not break

- All `SettingsScreenTest` logo section tests (unchanged)
- All `BrandingSymbolPickerSheetTest` tests (one updated for color fix in Block 1)
- All `EditSessionViewModelTest` non-branding tests (title, date, location, favorites, dirty, saving)
- All `EditSessionScreenTest` non-branding tests (screen structure, save flow, favorites, discard dialog)
- All `ShareComparisonScreenTest` non-branding tests (style switch, quality, information, share button)
- All `ShareComparisonViewModelTest` non-branding tests (style, quality, caption, isRendering)

---

## Documentation Updates Required After Implementation

After all four blocks are complete:

| Document | Section | Update required |
|---|---|---|
| `SESSION_BRANDING_V1.md` | §12 | Mark branding card as removed from Edit Session |
| `SESSION_BRANDING_V1.md` | §13 | Mark Share Comparison section as updated per V2 corrected |
| `SESSION_METADATA_EDITOR_V1.md` | §21 | Remove branding card specification |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18, §15.3 | Update to full management card |
| `SETTINGS_UX_V1.md` | §11 | Confirm Settings section unchanged |
| `IMPLEMENTATION_NOTES.md` | Branding entries | Update after each block is completed and verified |
