# SESSION_METADATA_EDITOR_IMPLEMENTATION_PLAN.md

## 1. Document Status

This document is the **working implementation plan** for the Session Metadata Editor in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Code review and regression-safe follow-up work

It supplements `SESSION_METADATA_EDITOR_V1.md` without replacing it.

**Authoritative sources:**

| Document | Role |
|---|---|
| `SESSION_METADATA_EDITOR_V1.md` | UX specification, field scope, save/navigation contracts |
| `SESSION_METADATA_V1.md` | Metadata schema, field semantics |
| `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md` | Storage function contracts |
| `COMPARE_FLOW_V1.md` | CompareScreen navigation contract |
| `IMPLEMENTATION_NOTES.md` | Current verified implementation state |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Architecture constraints, change discipline |

---

## 2. Scope

This plan covers the full implementation of the V1 Session Metadata Editor as specified in `SESSION_METADATA_EDITOR_V1.md`:

- Replacement of the "Edit Title" dialog in `CompareScreen` with a navigation entry to `EditSessionScreen`
- New fullscreen `EditSessionScreen` composable with 5 fields: Title, Reference date, Location display name, City, Country
- New `EditSessionViewModel` that manages form state, initial data loading, dirty tracking, validation, and save orchestration
- Overflow menu refactor in `CompareScreen`: "Edit Title" + "Remove Title" → "Edit Session"
- Navigation route for `EditSessionScreen` in `MainActivity`
- `CompareScreen` title display continues via `sessionTitle` parameter; freshness via `refreshSavedSessions()` after save
- All affected `CompareScreenTest` tests updated

---

## 3. Out of Scope

The following are explicitly out of scope for this plan:

- Tags, Description, Favorite, Visibility (not in V1)
- GPS-based location auto-fill or reverse geocoding
- `ScannedSession` extension with new fields (Option B from spec; plan uses Option A: direct metadata.json read)
- New metadata storage functions (all required functions — `updateTitle`, `updateReferenceDate`, `updateLocation` — are already implemented)
- `CompareLibraryScreen` entry point for the editor
- Any change to compare rendering, slider, or session storage logic
- `CameraViewModel.updateSessionTitle()` removal (it becomes unused but is not removed; cleanup is future scope)

---

## 4. Risks

| Risk | Severity | Block |
|---|---|---|
| `CompareScreenTest` has ~11 test methods that reference `onSaveTitle`, the title dialog, or "Remove Title". All break atomically when the dialog is removed in Block A. | **High** | A |
| `EditSessionViewModel` validation for Reference date must exactly match `SessionStorage.isValidReferenceDate()`. Any divergence allows the UI to accept a date the storage layer rejects (silently fails on save). | **Medium** | D |
| After save, title freshness in `CompareScreen` depends on `refreshSavedSessions()` scan + recompose cycle rather than instant local state update (old behavior). Timing gap is short but visible on slow IO. | **Medium** | F |
| `setCompareContent()` test helper in `CompareScreenTest` has `onSaveTitle` as a parameter. All usages must be updated atomically in Block A to avoid test compilation failures. | **Medium** | A |
| `EditSessionViewModel` is a new `hiltViewModel()` scoped to the edit session back stack entry. Hilt module setup must be correct or the app will crash on navigation. | **Low** | B |
| Partial save consistency: if `updateTitle()` succeeds but `updateReferenceDate()` fails, the session is partially updated. Documented as acceptable in spec §13; no rollback required. | **Low** | F |

---

## 5. Dependencies

### Storage Functions (Already Implemented)

All required storage functions are implemented and tested in `SessionStorage.kt`:

| Function | Status |
|---|---|
| `SessionStorage.updateTitle(sessionsRoot, sessionId, title)` | Completed (prior feature) |
| `SessionStorage.updateReferenceDate(sessionsRoot, sessionId, date)` | Completed (Block E, 2026-06-09) |
| `SessionStorage.updateLocation(sessionsRoot, sessionId, displayName, city, country)` | Completed (Block F, 2026-06-09) |

### ViewModel Infrastructure (Available)

- `CameraViewModel.refreshSavedSessions()` — already public; called by `MainActivity` after editor save completes
- `hiltViewModel()` pattern — established by `CreateVideoViewModel` and `SettingsViewModel`
- `UiEvent` sealed interface pattern — established in `CameraViewModel`; `EditSessionViewModel` introduces a parallel sealed interface for its own events

### Navigation Pattern (Available)

`ROUTE_CREATE_VIDEO_WITH_ARGS` in `MainActivity` is the template for a new route with a required `sessionId` argument.

---

## 6. Block Order

### Recommended sequence

```
Block A → Block B → Block C → Block D → Block E → Block F → Block G → Block H
```

Each block must leave the build green and the test suite passing before the next block begins.

Blocks C, D, E are independent of each other once Block B is complete and may be parallelized in separate sessions if desired. However, sequential delivery is simpler and the blocks are small.

---

## Block A — CompareScreen Overflow Refactor + Navigation Shell

**Status:** Completed (2026-06-09)

### Goal

Replace the title dialog in `CompareScreen` with a navigation entry to the new `EditSessionScreen`. Replace "Edit Title" and "Remove Title" overflow items with a single "Edit Session" item. Create the `EditSessionScreen` composable as an opaque form shell (no form fields yet). Add the navigation route in `MainActivity`. Update all affected `CompareScreenTest` tests atomically so the suite remains green.

### What Changes

**`CompareScreen.kt`:**
- Remove `onSaveTitle: ((String?) -> Unit)?` parameter
- Add `onEditSession: (() -> Unit)?` parameter
- Remove all local state: `showTitleDialog`, `titleInput`
- Remove `currentTitle` local state (title is now read-only from `sessionTitle` param)
- Remove the title `AlertDialog` composable entirely
- In the overflow `DropdownMenu`: remove "Edit Title" and "Remove Title" items; add "Edit Session" item that calls `onEditSession?.invoke()`
- Overflow button visibility: change condition from `if (onSaveTitle != null || sessionId != null)` to `if (onEditSession != null || sessionId != null)`
- Keep `sessionTitle: String?` parameter (now read-only display; no local mutation)

**`MainActivity.kt`:**
- Add `ROUTE_EDIT_SESSION` and `ARG_EDIT_SESSION_ID` constants
- Add `ROUTE_EDIT_SESSION_WITH_ARGS` pattern (mirrors `ROUTE_CREATE_VIDEO_WITH_ARGS`)
- In `ROUTE_COMPARE_WITH_ARGS` composable: remove `onSaveTitle` from `CompareScreen` call; add `onEditSession = if (sessionId != null) { { navController.navigate(editSessionRoute(sessionId)) } } else null`
- Add `ROUTE_EDIT_SESSION_WITH_ARGS` composable: receives `sessionId` as required arg, renders `EditSessionScreen` (shell)
- Add `editSessionRoute(sessionId: String)` private helper function

**`EditSessionScreen.kt`** (new file):
- Package: `com.isardomains.sameview.ui.compare`
- Opaque fullscreen `Scaffold` with:
  - `TopAppBar`: back icon, "Edit Session" screen title, Save `TextButton`/`IconButton`
  - Scrollable body content area (empty in this block)
  - `BackHandler` stub (no dirty-state logic yet; just `onBack()`)
- Accepts: `sessionId: String`, `onBack: () -> Unit`, `onSave: () -> Unit` (no ViewModel yet)

**`strings.xml`:**
- Add `edit_session_overflow_item` → "Edit session"
- Add `edit_session_screen_title` → "Edit session"
- Add `edit_session_save` → "Save"
- Add `edit_session_back_content_description` → "Back"

**`CompareScreenTest.kt`:**
- Update `setCompareContent()` helper: replace `onSaveTitle: (() -> Unit)?` with `onEditSession: (() -> Unit)?`
- Remove tests: `editTitleDialog_opensOnMenuItemClick`, `editTitleDialog_prefillsCurrentTitle`, `editTitleDialog_save_invokesCallback`, `editTitleDialog_cancel_doesNotInvokeCallback`, `removeTitle_visibleAndWorksWhenTitleIsSet`, `removeTitle_notVisibleWhenNoTitleSet`
- Rename/update: `moreMenuButton_hiddenWhenOnSaveTitleNull` → `moreMenuButton_hiddenWhenNoSessionContext`, `moreMenuButton_visibleWhenOnSaveTitleProvided` → `moreMenuButton_visibleWhenEditSessionProvided`
- Update `moreMenu_opensOnClick`: verify "Edit session" text appears instead of "Edit title"
- Add: `moreMenu_editSessionItem_invokesCallback` — tapping "Edit session" invokes `onEditSession`
- Update `deleteButton_independentOfTitleFeature`: replace `onSaveTitle = {}` with `onEditSession = {}`
- Update `backupSessionItem_notVisibleInOverflowWhenSessionIdIsNull`: replace `onSaveTitle = {}` with `onEditSession = {}`
- Update any other test that passes `onSaveTitle` as an argument

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | New |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt` | Modified |

### Risks

- Removing `onSaveTitle` breaks all tests that use it. Must update all occurrences in `setCompareContent()` and every test that passes `onSaveTitle` directly. Missing any one reference causes a compilation failure.
- `currentTitle` local mutation was the mechanism for instant title display update after edit. After this block, title display is purely from `sessionTitle` prop — it will not update until `refreshSavedSessions()` completes (happens in Block F). This is acceptable behavior per the spec.

### Required Tests

- `moreMenuButton_hiddenWhenNoSessionContext` — overflow button absent when `onEditSession = null` and `sessionId = null`
- `moreMenuButton_visibleWhenEditSessionProvided` — overflow button visible when `onEditSession != null`
- `moreMenu_opensOnClick` — "Edit session" item visible in open overflow menu
- `moreMenu_editSessionItem_invokesCallback` — tapping "Edit session" invokes `onEditSession`
- All remaining title/backup/delete tests compile and pass

### Definition of Done

- `CompareScreen` has no `onSaveTitle` parameter, no title dialog, no "Edit Title" / "Remove Title" overflow items
- "Edit Session" overflow item is present and functional
- `EditSessionScreen` renders as an opaque screen with TopAppBar; navigation to and from it works
- `assembleDebug` builds successfully
- `testDebugUnitTest` passes
- `CompareScreenTest` passes with no compilation errors
- All other instrumentation tests unaffected

### Test Results

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `CompareScreenTest` — 79/79 PASSED on SM-S911B (Android 16), BUILD SUCCESSFUL in 2m 47s

---

## Block B — EditSessionViewModel: Initial State Loading

**Status:** Not started

### Goal

Create `EditSessionViewModel` with Hilt injection. On `init`, load initial field values for all 5 fields from `metadata.json` by reading the file directly on the IO dispatcher. Expose field state as `StateFlow` values. No save logic in this block.

### Architecture

`EditSessionViewModel` is a new `HiltViewModel` scoped to its back stack entry.

It is **not** tied to `CameraViewModel`. It has its own injectable storage lambdas (following the `CameraViewModel` test-injection pattern).

**Internal state exposed:**

```
titleField: StateFlow<String>            // "" when absent
referenceDateField: StateFlow<String>    // "" when absent
locationDisplayNameField: StateFlow<String>
locationCityField: StateFlow<String>
locationCountryField: StateFlow<String>
isLoading: StateFlow<Boolean>            // true while metadata.json read is in progress
```

**Reading metadata.json:**

```
sessionsRoot = File(context.filesDir, "sessions")
sessionDir = File(sessionsRoot, sessionId)
metadataFile = File(sessionDir, "metadata.json")
```

Read `content.title`, `reference.date`, `location.displayName`, `location.city`, `location.country` using `optJSONObject()` / `optString()`. Missing blocks and absent fields return empty string. This read is best-effort: any exception results in all fields initialized to empty string (editor opens blank; user can fill in fields manually).

**sessionId:**

`sessionId` is passed to the ViewModel via a `SavedStateHandle` key (Hilt + Navigation compose pattern). Not via constructor parameter.

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | New |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | Modified (wire ViewModel to EditSessionScreen) |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | New |

### Risks

- `SavedStateHandle` injection requires the `sessionId` nav argument name to match exactly. Mismatch causes `IllegalArgumentException` at runtime.
- Large `metadata.json` files (unlikely but possible) could cause visible loading state. `isLoading` guards the UI.

### Required Tests

- `initialState_titleLoaded_fromMetadata` — title from content.title is correct
- `initialState_referenceDate_loaded_fromMetadata` — reference.date is correct
- `initialState_locationFields_loaded_fromMetadata` — all three location fields correct
- `initialState_allFieldsEmpty_whenMetadataAbsent` — missing metadata.json → all empty strings
- `initialState_allFieldsEmpty_whenBlocksAbsent` — metadata exists but no content/reference/location blocks → all empty
- `initialState_isLoading_trueInitially_falseAfterLoad` — loading state transitions correctly

### Definition of Done

- `EditSessionViewModel` loads initial state from disk without crashing
- Fields are initialized correctly from metadata.json
- Missing or corrupt metadata produces empty fields without throwing
- All listed unit tests pass
- Build green

---

## Block C — Title Field

**Status:** Not started

### Goal

Add the Title `OutlinedTextField` to `EditSessionScreen`. Wire it to `EditSessionViewModel`'s title state. The field is pre-populated with the loaded initial value. Changes are held in ViewModel state.

### What Changes

**`EditSessionScreen.kt`:**
- Accept `viewModel: EditSessionViewModel = hiltViewModel()` (or passed as parameter)
- Collect `titleField` from ViewModel
- Render `OutlinedTextField` with label "Title"
- `onValueChange` calls a ViewModel `onTitleChanged(String)` function
- No character limit enforcement in V1 (spec §11 — no max length enforced by validation)

**`EditSessionViewModel.kt`:**
- Add `onTitleChanged(value: String)` function that updates `titleField` state
- Add `_titleField: MutableStateFlow<String>`

**`strings.xml`:**
- Add `edit_session_field_title` → "Title"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Required Tests

- `onTitleChanged_updatesState` — calling `onTitleChanged("foo")` produces `titleField.value == "foo"`
- `titleField_initializedFromLoadedMetadata` — pre-population from Block B is preserved

### Definition of Done

- Title field visible in editor, pre-populated, editable
- Build green, tests pass

---

## Block D — Reference Date Field

**Status:** Not started

### Goal

Add the Reference Date `OutlinedTextField` to `EditSessionScreen`. Wire to ViewModel. Implement validation logic in `EditSessionViewModel`. Expose validation error state. Show inline error when format is invalid.

### What Changes

**`EditSessionScreen.kt`:**
- Add `OutlinedTextField` with label "Reference date" and placeholder hint (`"e.g. 2008 or 2008-06"`)
- `isError = referenceDateError != null`
- If `referenceDateError != null`, show supporting text with error message below field

**`EditSessionViewModel.kt`:**
- Add `_referenceDateField: MutableStateFlow<String>`
- Add `_referenceDateError: MutableStateFlow<String?>` (null = valid or empty)
- Add `onReferenceDateChanged(value: String)` — updates field, clears error immediately on change (validation runs on Save, not on change)
- Add internal `validateReferenceDate(value: String): Boolean` — mirrors `SessionStorage.isValidReferenceDate()`:
  - Empty string → valid (means "remove")
  - Non-empty: must match `"YYYY"`, `"YYYY-MM"`, or `"YYYY-MM-DD"` with plausibility filter (year 1826–current year) and non-lenient Calendar check for month/day validity

**`strings.xml`:**
- Add `edit_session_field_reference_date` → "Reference date"
- Add `edit_session_reference_date_hint` → "e.g. 2008 or 2008-06"
- Add `edit_session_reference_date_error` → "Enter a year (e.g. 2008), year–month (e.g. 2008-06), or full date (e.g. 2008-06-15)."

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Risks

- `validateReferenceDate()` in `EditSessionViewModel` must be an exact replica of `SessionStorage.isValidReferenceDate()`. Any divergence allows the UI to accept a value the storage layer rejects. The safest approach: extract the validation logic into a shared internal utility function (e.g., a `companion object` function in `SessionStorage` made `internal`) and call it from both places. Alternatively, duplicate the logic carefully and add parallel unit tests.

### Required Tests

- `validateReferenceDate_emptyString_isValid`
- `validateReferenceDate_yearOnly_isValid` — `"2008"`
- `validateReferenceDate_yearMonth_isValid` — `"2008-06"`
- `validateReferenceDate_fullDate_isValid` — `"2008-06-15"`
- `validateReferenceDate_invalidMonth_isInvalid` — `"2008-13"`
- `validateReferenceDate_invalidCalendarDay_isInvalid` — `"2008-02-31"`
- `validateReferenceDate_yearBefore1826_isInvalid`
- `validateReferenceDate_yearAfterCurrentYear_isInvalid`
- `validateReferenceDate_wrongFormat_isInvalid` — `"2008/06/15"`
- `validateReferenceDate_singleDigitMonth_isInvalid` — `"2008-6"`
- `onReferenceDateChanged_clearsPreviousError`

### Definition of Done

- Reference date field visible, pre-populated, editable
- Validation runs on Save (not on change)
- Error shown inline when invalid
- All validation unit tests pass
- Build green

---

## Block E — Location Fields

**Status:** Not started

### Goal

Add the three location `OutlinedTextField` fields to `EditSessionScreen`: Location display name, City, Country. Wire to `EditSessionViewModel`.

### What Changes

**`EditSessionScreen.kt`:**
- Add three `OutlinedTextField` fields, each with appropriate label
- Each field wired to its ViewModel state and `onValueChange` handler

**`EditSessionViewModel.kt`:**
- Add `_locationDisplayNameField`, `_locationCityField`, `_locationCountryField: MutableStateFlow<String>`
- Add corresponding `onLocationDisplayNameChanged`, `onLocationCityChanged`, `onLocationCountryChanged` functions

**`strings.xml`:**
- Add `edit_session_field_location_display_name` → "Location"
- Add `edit_session_field_city` → "City"
- Add `edit_session_field_country` → "Country"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Required Tests

- `onLocationDisplayNameChanged_updatesState`
- `onLocationCityChanged_updatesState`
- `onLocationCountryChanged_updatesState`

### Definition of Done

- All 3 location fields visible, pre-populated, editable
- Build green, tests pass

---

## Block F — Save Workflow

**Status:** Not started

### Goal

Implement the Save button behavior. On Save: validate fields, call the appropriate storage functions for changed fields only, emit navigation or error events. In `MainActivity`: handle `SaveComplete` by calling `cameraViewModel.refreshSavedSessions()` then `navController.popBackStack()`.

### Save Logic in `EditSessionViewModel`

```
onSave():
  1. Validate: call validateReferenceDate(referenceDateField)
     → if invalid: set referenceDateError, return (do not write anything)
  2. For each field group where current value ≠ initial value:
     a. Title: call sessionTitleUpdater(sessionsRoot, sessionId, normalizedTitle)
     b. Reference date: call sessionReferenceDateUpdater(sessionsRoot, sessionId, normalizedDate)
     c. Location: call sessionLocationUpdater(sessionsRoot, sessionId, displayName, city, country)
        (all three location fields always passed together as a unit)
  3. If any write returns false: emit SaveFailed event; return
  4. If all writes succeed (or no writes needed): emit SaveComplete event
```

**Blank normalization at save time:**
- Title blank → null (passed to `updateTitle`)
- Reference date blank → null (passed to `updateReferenceDate`)
- Location fields: each trimmed; `updateLocation` handles null normalization internally

**`EditSessionViewModel.kt`** — sealed events:

```kotlin
sealed interface EditSessionEvent {
    data object SaveComplete : EditSessionEvent
    data object SaveFailed : EditSessionEvent
}
```

Exposed as `SharedFlow<EditSessionEvent>`.

**Injectable lambdas in `EditSessionViewModel`** (testable pattern matching `CameraViewModel`):

```kotlin
var sessionTitleUpdater: (File, String, String?) -> Boolean
var sessionReferenceDateUpdater: (File, String, String?) -> Boolean
var sessionLocationUpdater: (File, String, String?, String?, String?) -> Boolean
var metadataReader: (File, String) -> InitialSessionFields   // reads metadata.json
```

**`MainActivity.kt`** — `ROUTE_EDIT_SESSION_WITH_ARGS` composable:

```kotlin
val viewModel: EditSessionViewModel = hiltViewModel()
val cameraViewModel: CameraViewModel = hiltViewModel(cameraEntry)

LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
        when (event) {
            EditSessionEvent.SaveComplete -> {
                cameraViewModel.refreshSavedSessions()
                navController.popBackStack()
            }
            EditSessionEvent.SaveFailed -> {
                // show snackbar via snackbarHostState
            }
        }
    }
}

EditSessionScreen(
    onBack = { navController.popBackStack() },
    onSave = { viewModel.onSave() }
)
```

**`strings.xml`:**
- Add `edit_session_save_failed` → "Couldn't save changes"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Risks

- Reference date validation must run before any write call. If validation is skipped and an invalid date reaches `updateReferenceDate()`, the storage function returns `false` (per contract), which would surface as a generic save error. The correct behavior is to show the inline field error instead. Make sure `onSave()` checks validation first and does not proceed to writes on validation failure.
- "Changed" detection: compare trimmed current value against trimmed initial value. If they are equal, skip the write. If initial was null/absent and current is blank, skip the write (both represent absence).

### Required Tests

- `onSave_withValidTitle_callsTitleUpdater` — updater called with correct args
- `onSave_withUnchangedTitle_doesNotCallTitleUpdater` — updater not called when title unchanged
- `onSave_withBlankTitle_callsTitleUpdaterWithNull` — blank → null
- `onSave_withValidReferenceDate_callsReferenceDateUpdater`
- `onSave_withBlankReferenceDate_callsReferenceDateUpdaterWithNull`
- `onSave_withInvalidReferenceDate_setsError_doesNotCallUpdater`
- `onSave_withLocationFields_callsLocationUpdater_withTrimmedValues`
- `onSave_withAllLocationFieldsBlank_callsLocationUpdater_withNulls`
- `onSave_success_emitsSaveComplete`
- `onSave_titleUpdaterFails_emitsSaveFailed`
- `onSave_referenceDateUpdaterFails_emitsSaveFailed`
- `onSave_locationUpdaterFails_emitsSaveFailed`
- `onSave_noFieldChanged_emitsSaveComplete_withoutCallingAnyUpdater`

### Definition of Done

- Tapping Save in the editor triggers validation, then storage writes for changed fields
- On success: `CompareScreen` is shown with updated title after `refreshSavedSessions()` recompose
- On storage failure: snackbar shown, user remains on editor
- On validation failure: inline error shown on Reference date field, no writes
- All listed unit tests pass
- Build green

---

## Block G — Dirty State + Discard Dialog

**Status:** Not started

### Goal

Track whether any field has been changed from its initial state. On Back with dirty state, show a confirmation dialog. Back without dirty state navigates immediately.

### What Changes

**`EditSessionViewModel.kt`:**
- Add `isDirty: StateFlow<Boolean>` — derived from comparing each current field value to its corresponding initial value (after trim normalization)
- The comparison must match exactly the blank-normalization rules used in `onSave()`:
  - `normalizedCurrent == normalizedInitial` → not dirty for that field
  - Initial was absent and current is blank → not dirty

**`EditSessionScreen.kt`:**
- Add `var showDiscardDialog by remember { mutableStateOf(false) }`
- `BackHandler(enabled = isDirty)` — when dirty: set `showDiscardDialog = true`
- When not dirty: call `onBack()` directly
- Discard `AlertDialog`:
  - Title: "Discard changes?"
  - Body: "Your changes have not been saved."
  - Confirm: "Discard" → `onBack()`
  - Cancel: "Keep editing" → dismiss dialog
- System back (hardware/gesture): same logic — if dirty, show dialog; if clean, pop

**`strings.xml`:**
- Add `edit_session_discard_dialog_title` → "Discard changes?"
- Add `edit_session_discard_dialog_body` → "Your changes have not been saved."
- Add `edit_session_discard_confirm` → "Discard"
- Add `edit_session_discard_cancel` → "Keep editing"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Required Tests

- `isDirty_falseInitially`
- `isDirty_trueAfterTitleChanged`
- `isDirty_trueAfterReferenceDateChanged`
- `isDirty_trueAfterLocationFieldChanged`
- `isDirty_falseAfterRevertingToInitialValue` — changing back to the loaded value makes isDirty false
- `isDirty_falseWhenInitialAndCurrentBothBlank` — initial absent + current blank = not dirty

### Definition of Done

- Back with unchanged fields: immediate navigation, no dialog
- Back with changed fields: discard dialog shown
- Confirming "Discard": navigates back without saving
- Cancelling dialog: user remains on editor
- All listed unit tests pass
- Build green

---

## Block H — Full Test Coverage + Regression Verification

**Status:** Not started

### Goal

Add `EditSessionScreenTest.kt` (instrumentation tests for the full editor flow). Verify that all existing tests pass. Run the full test suite.

### New Instrumentation Tests

**`EditSessionScreenTest.kt`** (new file, package `com.isardomains.sameview.ui.compare`):

- `editSessionScreen_rendersTopBar_withSaveButton`
- `editSessionScreen_rendersAllFiveFields`
- `titleField_prePopulatedFromSessionTitle`
- `referenceDateField_prePopulatedFromSessionDate`
- `locationFields_prePopulatedFromSessionLocation`
- `saveButton_invokesOnSaveCallback`
- `backButton_withNoChanges_invokesOnBackWithoutDialog`
- `backButton_withChanges_showsDiscardDialog`
- `discardDialog_confirm_invokesOnBack`
- `discardDialog_cancel_dismissesDialog`
- `referenceDateField_invalidInput_showsInlineError`
- `referenceDateField_validInput_noError`

**CompareScreen integration (regression):**
- Verify "Edit session" item opens navigator to edit session screen (in `CompareNavigationTest` or a new integration test in `CompareScreenTest`)

### Regression Scope

The following must remain unaffected and pass:

- `CompareScreenTest` (all remaining tests after Block A updates)
- `CompareLibraryScreenTest`
- `SessionStorageMetadataTest` (all 68 tests)
- `SessionScannerTest`
- `CameraViewModelTest`
- `VideoExportPipelineTest`, `VideoExportPipelineStandardTest`
- `SessionBackupExporterInstrumentedTest`
- `ReferenceImageMetadataReaderTest`
- `testDebugUnitTest` (all unit tests)
- `assembleRelease`

### Affected Files

| File | Change Type |
|---|---|
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/EditSessionScreenTest.kt` | New |

### Definition of Done

- All new instrumentation tests pass
- Full `connectedDebugAndroidTest` suite passes on a real device
- `assembleRelease` builds successfully
- No regressions in any existing test

---

## 7. Test Strategy

### Unit Tests (`src/test/`)

**`EditSessionViewModelTest.kt`** (new):
- Uses `StandardTestDispatcher` / `UnconfinedTestDispatcher` (follows `CameraViewModelTest` pattern)
- All storage lambdas injected as test doubles
- Covers: initial state loading, field change state, dirty tracking, validation, save paths, event emission

### Instrumentation Tests (`src/androidTest/`)

**`CompareScreenTest.kt`** (modified in Block A):
- All references to `onSaveTitle`, title dialog, "Remove Title" replaced
- New tests for "Edit Session" overflow item and `onEditSession` callback

**`EditSessionScreenTest.kt`** (new in Block H):
- Tests the full editor composable as a black-box UI component
- Uses `createEmptyComposeRule()` + `ActivityScenario<ComponentActivity>` (follows `CompareScreenTest` pattern)
- No Hilt wiring required — passes lambdas directly to `EditSessionScreen`

### Test-Injection Pattern

`EditSessionViewModel` follows the `CameraViewModel` pattern: storage lambdas are `internal var` fields defaulting to real implementations, overridable in tests. No Mockito/mocking required for unit tests; lambda replacement is sufficient.

---

## 8. Progress Table

| Block | Description | Status |
|---|---|---|
| Block A | CompareScreen overflow refactor + navigation shell | Completed |
| Block B | EditSessionViewModel: initial state loading | Not started |
| Block C | Title field | Not started |
| Block D | Reference date field + validation | Not started |
| Block E | Location fields | Not started |
| Block F | Save workflow | Not started |
| Block G | Dirty state + discard dialog | Not started |
| Block H | Full test coverage + regression verification | Not started |

---

*Document created 2026-06-09. Based on codebase analysis of CompareScreen.kt, MainActivity.kt, SessionScanner.kt, CameraViewModel.kt, SessionStorage.kt, CompareScreenTest.kt, and related source files.*
