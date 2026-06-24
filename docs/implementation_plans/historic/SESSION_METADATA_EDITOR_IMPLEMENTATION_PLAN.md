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

### Block A Test Results

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `CompareScreenTest` — 79/79 PASSED on SM-S911B (Android 16), BUILD SUCCESSFUL in 2m 47s

---

## Block B — EditSessionViewModel: Initial State Loading

**Status:** Completed (2026-06-09)

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

**`MainActivity.kt` — no changes required.** `EditSessionScreen` uses `hiltViewModel()` as a default parameter (identical pattern to `CreateVideoScreen`). The nav route was already wired in Block A.

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | New |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified (added `viewModel: EditSessionViewModel = hiltViewModel()` parameter) |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | New |

### Risks

- `SavedStateHandle` injection requires the `sessionId` nav argument name to match exactly. Mismatch causes `IllegalArgumentException` at runtime. Verified: `ARG_EDIT_SESSION_ID = "sessionId"` matches `SavedStateHandle["sessionId"]`.
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

### Block B Test Results (2026-06-09)

- `initialState_titleLoaded_fromMetadata` — PASSED
- `initialState_referenceDate_loaded_fromMetadata` — PASSED
- `initialState_locationFields_loaded_fromMetadata` — PASSED
- `initialState_allFieldsEmpty_whenMetadataAbsent` — PASSED
- `initialState_allFieldsEmpty_whenBlocksAbsent` — PASSED
- `initialState_isLoading_trueInitially_falseAfterLoad` — PASSED
- `testDebugUnitTest` — BUILD SUCCESSFUL, 394/394 unit tests passed, 0 failures

---

## Block C — Title Field — Title Field

**Status:** Completed (2026-06-09)

### Goal

Add the Title `OutlinedTextField` to `EditSessionScreen`. Wire it to `EditSessionViewModel`'s title state. The field is pre-populated with the loaded initial value. Changes are held in ViewModel state.

### What Changes

**`EditSessionScreen.kt`:**
- `titleField` collected via `collectAsStateWithLifecycle()`
- `OutlinedTextField` with label `edit_session_field_title`, `singleLine = true`, `ImeAction.Done` + `clearFocus()`, `fillMaxWidth()`, `padding(horizontal = 16.dp, vertical = 8.dp)`
- `onValueChange = viewModel::onTitleChanged`
- Save button remains `enabled = false` — no save logic in this block
- KDoc updated to Block C

**`EditSessionViewModel.kt`:**
- Added `onTitleChanged(value: String)` — sets `_titleField.value = value` directly; no coroutine needed
- Note: `_titleField: MutableStateFlow<String>` already existed from Block B; no new state declaration needed

**`strings.xml`:**
- Added `edit_session_field_title` → "Title"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Required Tests

- `onTitleChanged_updatesState` — calling `onTitleChanged("foo")` produces `titleField.value == "foo"`
- `titleField_initializedFromLoadedMetadata` — covered by existing `initialState_titleLoaded_fromMetadata` from Block B; no duplicate added

### Definition of Done

- Title field visible in editor, pre-populated, editable
- Build green, tests pass

### Block C Test Results (2026-06-09)

- `onTitleChanged_updatesState` — PASSED
- All 6 Block B tests — PASSED (unchanged)
- `EditSessionViewModelTest` — 7/7 PASSED
- `testDebugUnitTest` — BUILD SUCCESSFUL, 395/395 unit tests passed, 0 failures
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block D — Reference Date Field

**Status:** Completed (2026-06-09)

### Goal

Add the Reference Date `OutlinedTextField` to `EditSessionScreen`. Wire to ViewModel. Implement validation logic in `EditSessionViewModel`. Expose validation error state. Show inline error when format is invalid.

### What Changes

**`SessionStorage.kt`:**
- `private fun isValidReferenceDate` → `internal fun isValidReferenceDate` (1-word change)
- No logic change; exposes the function to the same Gradle module so `EditSessionViewModel` can delegate directly

**`EditSessionScreen.kt`:**
- `referenceDate` and `referenceError` collected via `collectAsStateWithLifecycle()`
- Title field `ImeAction.Done` → `ImeAction.Next` + `onNext = { focusManager.moveFocus(FocusDirection.Down) }`
- Reference Date `OutlinedTextField` with label, placeholder hint, `singleLine = true`, `isError = referenceError != null`, conditional `supportingText`, `ImeAction.Done` + `clearFocus()`
- Added `import androidx.compose.ui.focus.FocusDirection`

**`EditSessionViewModel.kt`:**
- Added `import com.isardomains.sameview.ui.camera.SessionStorage`
- Added `internal val _referenceDateError = MutableStateFlow<String?>(null)` (internal for test access)
- Added `val referenceDateError: StateFlow<String?>`
- Added `onReferenceDateChanged(value: String)` — updates field, clears error immediately
- Added `internal fun isValidReferenceDateInput(value: String): Boolean` — trims value; empty/blank → true; non-empty → delegates to `SessionStorage.isValidReferenceDate(trimmed)`. Single source of truth, no duplication.
- Note: `_referenceDateField: MutableStateFlow<String>` already existed from Block B; no new declaration needed

**`strings.xml`:**
- Added `edit_session_field_reference_date` → "Reference date"
- Added `edit_session_reference_date_hint` → "e.g. 2008 or 2008-06"
- Added `edit_session_reference_date_error` → "Enter a year (e.g. 2008), year-month (e.g. 2008-06), or full date (e.g. 2008-06-15)."

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt` | Modified — visibility only |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |

### Required Tests

- `onReferenceDateChanged_clearsPreviousError` — sets `_referenceDateError` manually, calls `onReferenceDateChanged()`, asserts null
- `validateReferenceDate_emptyString_isValid`
- `validateReferenceDate_blankString_isValid`
- `validateReferenceDate_yearOnly_isValid` — `"2008"`
- `validateReferenceDate_yearMonth_isValid` — `"2008-06"`
- `validateReferenceDate_fullDate_isValid` — `"2008-06-15"`
- `validateReferenceDate_invalidMonth_isInvalid` — `"2008-13"`
- `validateReferenceDate_invalidCalendarDay_isInvalid` — `"2008-02-31"`
- `validateReferenceDate_yearBefore1826_isInvalid`
- `validateReferenceDate_yearAfterCurrentYear_isInvalid`
- `validateReferenceDate_wrongFormat_isInvalid` — `"2008/06/15"`
- `validateReferenceDate_singleDigitMonth_isInvalid` — `"2008-6"`

### Definition of Done

- Reference date field visible, pre-populated, editable
- Validation infrastructure present; error state stays null until Block F activates it via `onSave()`
- Error UI (isError, supportingText) ready but dormant in Block D
- Title field ImeAction changed to Next
- All validation unit tests pass
- Build green

### Block D Test Results (2026-06-09)

- All 12 new Block D tests — PASSED
- All 7 existing tests — PASSED (unchanged)
- `EditSessionViewModelTest` — 19/19 PASSED
- `testDebugUnitTest` — BUILD SUCCESSFUL, 407/407 unit tests passed, 0 failures
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block E — Location Fields

**Status:** Completed (2026-06-09)

### Goal

Add the three location `OutlinedTextField` fields to `EditSessionScreen`: Location display name, City, Country. Wire to `EditSessionViewModel`.

### What Changes

**`EditSessionScreen.kt`:**
- Added `locationDisplayName`, `locationCity`, `locationCountry` via `collectAsStateWithLifecycle()`
- Reference Date field `ImeAction.Done` → `ImeAction.Next` + `moveFocus(FocusDirection.Down)` (chains keyboard into location fields)
- Three new `OutlinedTextField` fields added under Reference Date: Location (displayName), City, Country
- IME chain: Title → Reference Date → Location → City → Country (Done + clearFocus)
- No section labels; layout consistent with Title and Reference Date fields
- KDoc updated to Block E

**`EditSessionViewModel.kt`:**
- Added `onLocationDisplayNameChanged(value: String)` — sets `_locationDisplayNameField.value = value` directly
- Added `onLocationCityChanged(value: String)` — sets `_locationCityField.value = value` directly
- Added `onLocationCountryChanged(value: String)` — sets `_locationCountryField.value = value` directly
- Note: `_locationDisplayNameField`, `_locationCityField`, `_locationCountryField` already existed from Block B; no new state declarations needed

**`strings.xml`:**
- Added `edit_session_field_location_display_name` → "Location"
- Added `edit_session_field_city` → "City"
- Added `edit_session_field_country` → "Country"

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

### Block E Test Results (2026-06-09)

- `onLocationDisplayNameChanged_updatesState` — PASSED
- `onLocationCityChanged_updatesState` — PASSED
- `onLocationCountryChanged_updatesState` — PASSED
- All 19 existing tests — PASSED (unchanged)
- `EditSessionViewModelTest` — 22/22 PASSED
- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block F — Save Workflow

**Status:** Completed (2026-06-09)

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

### Block F Implementation Notes

**Deviations from the Block F plan spec:**

- `isDirty` and `isSaving` `StateFlow`s were implemented in Block F (not Block G), as these are required to enable/disable the Save button. The Save button is `enabled = isDirty && !isSaving`.
- `EditSessionScreen` receives `viewModel: EditSessionViewModel` as a **required parameter** (no `= hiltViewModel()` default). `MainActivity` creates the ViewModel via `hiltViewModel()` and passes it explicitly. This is necessary so `MainActivity` can observe `viewModel.events` independently of the composable lifecycle.
- `normalizeField(s: String): String? = s.trim().ifEmpty { null }` — blank → null for all fields at save time. Display values are not modified.
- `isDirty` is computed via manual `updateIsDirty()` calls (not `combine + stateIn`) to avoid a race condition in `init` where `combine` would re-evaluate before `initial*` vars are set.
- `createViewModel` in `EditSessionViewModelTest` has `reader` as the **last** parameter so existing trailing-lambda syntax `createViewModel { _, _ -> InitialSessionFields(...) }` continues to bind correctly to `reader`. New Block F tests use named parameters.

### Block F Test Results (2026-06-09)

- `isDirty_falseInitially` — PASSED
- `isDirty_trueAfterTitleChanged` — PASSED
- `isDirty_trueAfterReferenceDateChanged` — PASSED
- `isDirty_trueAfterLocationFieldChanged` — PASSED
- `isDirty_falseAfterRevertingToInitialValue` — PASSED
- `isDirty_falseWhenInitialAndCurrentBothBlank` — PASSED
- `onSave_withValidTitle_callsTitleUpdater` — PASSED
- `onSave_withUnchangedTitle_doesNotCallTitleUpdater` — PASSED
- `onSave_withBlankTitle_callsTitleUpdaterWithNull` — PASSED
- `onSave_withValidReferenceDate_callsReferenceDateUpdater` — PASSED
- `onSave_withBlankReferenceDate_callsReferenceDateUpdaterWithNull` — PASSED
- `onSave_withInvalidReferenceDate_setsError_doesNotCallUpdater` — PASSED
- `onSave_withLocationFields_callsLocationUpdater_withTrimmedValues` — PASSED
- `onSave_withAllLocationFieldsBlank_callsLocationUpdater_withNulls` — PASSED
- `onSave_success_emitsSaveComplete` — PASSED
- `onSave_noFieldChanged_emitsSaveComplete_withoutCallingAnyUpdater` — PASSED
- `onSave_titleUpdaterFails_emitsSaveFailed` — PASSED
- `onSave_referenceDateUpdaterFails_emitsSaveFailed` — PASSED
- `onSave_locationUpdaterFails_emitsSaveFailed` — PASSED
- `isDirty_falseAfterSuccessfulSave` — PASSED
- `onSave_storageOrderIsTitleThenReferenceDateThenLocation` — PASSED
- `isSaving_falseBeforeAndAfterSave` — PASSED
- `onSave_titleUpdaterFails_doesNotEmitSaveComplete` — PASSED
- All 22 existing tests — PASSED (unchanged)
- `EditSessionViewModelTest` — 46/46 PASSED (22 existing + 24 new)
- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block G — Dirty State + Discard Dialog

**Status:** Completed (2026-06-09)

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

### Block G Implementation Notes

**Deviations and additions vs. plan:**

- `isSaving` back-blocking was added as a second dialog path (not in the original Block G plan). When `isSaving == true`, a "Saving changes" information dialog is shown instead of the discard dialog. Navigation is blocked until the user dismisses the dialog (the save continues running). This applies to both system back and the TopAppBar back icon.
- `isDirty` was already implemented and tested in Block F. No ViewModel changes were needed for Block G.
- `BackHandler(enabled = isSaving || isDirty)` — the handler fires for both states. Inside, `isSaving` is checked first: `if (isSaving) showSavingDialog = true else showDiscardDialog = true`.
- TopAppBar back button: `if (isSaving) showSavingDialog = true else if (isDirty) showDiscardDialog = true else onBack()`.
- Both dialog states are local Compose state (`var showXxxDialog by remember { mutableStateOf(false) }`).
- SaveComplete path in `MainActivity` is unchanged — `isDirty` is already `false` before `popBackStack()` is called, so no dialog can appear.
- 4 required ViewModel unit tests from Block G's plan were already passing from Block F. No new unit tests added.
- Instrumentation tests for this block are deferred to Block H (`EditSessionScreenTest.kt`).

**String keys added:**

- `edit_session_discard_dialog_title` / `_body` / `_confirm` / `_cancel`
- `edit_session_saving_dialog_title` / `_body` / `_confirm`

### Block G Test Results (2026-06-09)

- All 46 existing `EditSessionViewModelTest` — PASSED (unchanged)
- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX2 — Session Metadata Editor UX Refinement V2

**Status:** Completed (2026-06-09)

### Goal

Unify sentence case across all visible text, correct placeholders to use concrete examples, move Session date from the Reference photo card into the Session card, remove the Filename display entirely, add a Current photo card (capture thumbnail only), and reduce the Reference photo card to thumbnail + reference date field only.

### Binding Product Decisions

- Sentence case is the only accepted casing for all visible labels, card titles, button labels, and screen titles in the Session Metadata Editor.
- Session date belongs to the Session card, not the Reference photo card.
- Filename is not user-relevant and must not be displayed.
- Current photo card shows only the capture thumbnail — no labels, no metadata, no actions.
- Card order: Session → Reference photo → Current photo → Location.

### What Changes

**`EditSessionScreen.kt`:**
- Removed `val referenceSourceDisplayName by viewModel.referenceSourceDisplayName.collectAsStateWithLifecycle()` (no longer needed in screen)
- Removed `val referenceFilename = remember(...)` derivation
- Added `val captureImageUri = remember(viewModel.sessionId) { Uri.fromFile(...capture.jpg) }`
- Session card: added session date display (`edit_session_label_session_date` + formatted date) below Description field, inside the card, guarded by `captureDate.isNotEmpty()`
- Reference photo card: replaced Row-with-column layout (thumbnail + filename + session date + Column) with simple thumbnail-only block; kept existing reference date field and help text unchanged
- Added Current photo card (between Reference photo and Location): `SettingsCard(edit_session_card_current_photo)` containing only the capture thumbnail; no labels, no buttons
- Removed unused imports: `Row`, `Alignment`, `width` (Spacer), `TextOverflow`

**`strings.xml`:**
- `edit_session_screen_title`: "Edit Session" → "Edit session" (sentence case)
- `edit_session_save_changes`: "Save Changes" → "Save changes" (sentence case)
- `edit_session_card_reference_photo`: "Reference Photo" → "Reference photo" (sentence case)
- `edit_session_label_session_date`: "Session Date" → "Session date" (sentence case)
- `edit_session_placeholder_title`: "Add a title" → "e.g. Summer vacation in Italy"
- `edit_session_placeholder_description`: "Add a description" → "Add notes about this comparison"
- `edit_session_placeholder_place_name`: "Add a place name" → "e.g. Marienplatz"
- `edit_session_placeholder_city`: "Add a city" → "e.g. Munich"
- `edit_session_placeholder_country`: "Add a country" → "e.g. Germany"
- `edit_session_label_filename`: **removed** (string no longer used; filename display removed)
- `edit_session_card_current_photo`: **added** → "Current photo"

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Modified |
| `app/src/main/res/values/strings.xml` | Modified |
| `docs/implementation_plans/historic/SESSION_METADATA_EDITOR_IMPLEMENTATION_PLAN.md` | Modified |
| `../../IMPLEMENTATION_NOTES.md` | Modified |

### Unchanged

- `EditSessionViewModel.kt` — no changes; `referenceSourceDisplayName` StateFlow remains (populated from metadata, exposed to screen); unused in screen after this block but ViewModel is not refactored per scope rules
- All storage functions, navigation, dialog logic, save workflow, dirty tracking — unchanged
- `EditSessionViewModelTest.kt` — no changes; no string-value assertions were present

### Block UX2 Test Results (2026-06-09)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX7 — Final Visual Alignment Fix

**Status:** Completed (2026-06-10)

### Goal

Two visual corrections to finalize card appearance before Block H test authoring. No ViewModel, storage, navigation, validation, or DatePicker changes.

### What Changes

**`EditSessionScreen.kt`:**
- Session card: `SettingsCard(title = stringResource(R.string.edit_session_card_session))` → `SettingsCard(title = if (captureDateWithTime.isNotEmpty()) stringResource(R.string.edit_session_created, captureDateWithTime) else null)`. Body-`Text` + `Spacer(8.dp)` for "Captured on ..." removed. Header renders in SettingsCard's native `titleMedium` / `SameViewSettingsHeaderText` style, identical to the old "Session" header.
- Current photo card: `Row(verticalAlignment = Alignment.Top)` → `Row(verticalAlignment = Alignment.CenterVertically)` — thumbnail and display-Box share the same vertical midpoint.

**`app/src/main/res/values/strings.xml`:**
- Added `edit_session_created` → `"Created %s"`

**`app/src/main/res/values-de/strings.xml`:**
- Added `edit_session_created` → `"Erstellt am %s"`

### Unchanged

- `edit_session_captured_on` strings (kept, now unused in code)
- Reference photo card — layout, alignment, help text, error text all unchanged
- Current photo card Box size, width, inner content, border — unchanged
- ViewModel, storage, save workflow, DatePicker, Location card — all unchanged

### Block UX7 Test Results (2026-06-10)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX6 — Final UX Cleanup Before Block H

**Status:** Completed (2026-06-09)

### Goal

Three layout corrections to finalize visual quality before Block H test authoring. No ViewModel, storage, navigation, or validation changes.

### What Changes

**`EditSessionScreen.kt`:**
- Session card: two-line "Session date" label + value replaced by single `Text(stringResource(R.string.edit_session_captured_on, captureDateWithTime))` in `bodySmall` / `SameViewSettingsSecondaryText`; one line, no separate label
- Reference photo card: `supportingText` parameter removed from `OutlinedTextField`; `isError` retained; help text and error text rendered as free `Text` composables below the Row, full card width; `Spacer(4.dp)` between Row and text; error text uses `MaterialTheme.colorScheme.error`
- Current photo card: plain `Column` replaced by `Box` with `Modifier.border(1.dp, colorScheme.outline, shapes.extraSmall)` + `heightIn(min = 56.dp)` + `weight(1f)` + inner padding `(start=16, end=16, top=8, bottom=8.dp)`; no focus, cursor, or click affordance; visually matches OutlinedTextField weight
- Added imports: `androidx.compose.foundation.border`, `androidx.compose.foundation.layout.heightIn`

**`app/src/main/res/values/strings.xml`:**
- Added `edit_session_captured_on` → `"Captured on %s"`

**`app/src/main/res/values-de/strings.xml`:**
- Added `edit_session_captured_on` → `"Aufgenommen am %s"`

### Unchanged

- ViewModel, storage, dirty state, save workflow, DatePicker, validation — all unchanged
- `isError` flag on OutlinedTextField — retained (red outline remains active on validation error)
- Location card, Session card fields, Reference photo thumbnail, Current photo thumbnail — unchanged

### Block UX6 Test Results (2026-06-09)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX5 — Pre-Block-H UX Fix

**Status:** Completed (2026-06-09)

### Goal

Three targeted corrections before Block H test authoring to ensure no misleading strings get frozen into test assertions and the Session card presents information in priority order.

### What Changes

**`EditSessionScreen.kt`:**
- Session card: "Session date" block moved to top (before Title and Description fields)
- Session card: changed from `captureDate` (date only) to `captureDateWithTime` (date + time) — consistent with Current photo card and CompareScreen
- Removed `captureDate` derivation (now unused) and `locale` variable (now unused)
- Removed `LocalConfiguration` import (now unused)

**`app/src/main/res/values/strings.xml`:**
- `edit_session_reference_date_help`: "Reference photo date.\nExamples: 2008, June 2008, or June 15, 2008." → "When the reference photo was taken.\nExamples: 2008, 2008-06, or 2008-06-15."

**`app/src/main/res/values-de/strings.xml`:**
- `edit_session_reference_date_help`: "Aufnahmedatum des Referenzfotos.\nBeispiele: 2008, Juni 2008 oder 15. Juni 2008." → "Wann das Referenzfoto aufgenommen wurde.\nBeispiele: 2008, 2008-06 oder 2008-06-15."

### Unchanged

- Current photo card (plain text, no change)
- Reference photo card layout, DatePicker, validation, storage, dirty state, navigation — all unchanged

### Block UX5 Test Results (2026-06-09)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX4 — Reference Card Layout Refinement

**Status:** Completed (2026-06-09)

### Goal

Rebuild the Reference photo card body from a vertical stack (thumbnail → spacer → full-width field) to a horizontal Row (thumbnail left, field right), matching the visual structure of the Current photo card.

### What Changes

**`EditSessionScreen.kt`:**
- Reference photo card: wrapped thumbnail and `OutlinedTextField` in `Row(verticalAlignment = Alignment.Top)`
- Replaced vertical `Spacer(height = 12.dp)` with horizontal `Spacer(width = 12.dp)` between thumbnail and field
- Changed `modifier = Modifier.fillMaxWidth()` on the field to `modifier = Modifier.weight(1f)` so the field fills the remaining row width
- All other field properties unchanged: label, placeholder, trailingIcon (DatePicker), singleLine, isError, supportingText, keyboardOptions, keyboardActions

### Unchanged

- Session card, Current photo card, Location card — unchanged
- DatePicker trigger and dialog logic — unchanged
- Validation, dirty state, save workflow, strings — unchanged

### Block UX4 Test Results (2026-06-09)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

---

## Block UX3 — Session Metadata Editor UX Polish

**Status:** Completed (2026-06-09)

### Goal

Three targeted UX corrections to the already-implemented Session Metadata Editor. No new features, no storage changes, no navigation changes, no ViewModel changes.

### What Changes

**`EditSessionScreen.kt`:**
- Current photo card: label changed from `edit_session_label_captured_on` ("Captured on") to `edit_session_label_session_date` ("Session date") — consistent with Session card and app terminology
- Reference photo thumbnail: `.size(64.dp)` → `.size(80.dp)`
- Current photo thumbnail: `.size(64.dp)` → `.size(80.dp)`

**`app/src/main/res/values/strings.xml`:**
- `edit_session_reference_date_help`: "Approximate date of the reference photo.\nExamples: 2008, 2008-06, 2008-06-15" → "Reference photo date.\nExamples: 2008, June 2008, or June 15, 2008."

**`app/src/main/res/values-de/strings.xml`:**
- `edit_session_reference_date_help`: "Ungefähres Datum des Referenzfotos.\nBeispiele: 2008, 2008-06, 2008-06-15" → "Aufnahmedatum des Referenzfotos.\nBeispiele: 2008, Juni 2008 oder 15. Juni 2008."

### Unchanged

- Storage formats and validation (`isValidReferenceDate`, `updateReferenceDate`) — unchanged
- `edit_session_reference_date_error` string — unchanged
- Save workflow, dirty state, DatePicker, navigation, card order, description, placeholders, CompareScreen — unchanged

### Block UX3 Test Results (2026-06-09)

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

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
| Block B | EditSessionViewModel: initial state loading | Completed |
| Block C | Title field | Completed |
| Block D | Reference date field + validation | Completed |
| Block E | Location fields | Completed |
| Block F | Save workflow | Completed |
| Block G | Dirty state + discard dialog | Completed |
| Block UX | Session Metadata Editor UX Correction (Pre-Block-H) | Completed |
| Block UX2 | Session Metadata Editor UX Refinement V2 | Completed |
| Block H | Full test coverage + regression verification | Not started |

---

## Block UX — Session Metadata Editor UX Correction (Pre-Block-H)

**Status:** Completed (2026-06-09)

### Goal

Rebuild `EditSessionScreen` to product-ready UX quality. Add `description` field. Add `SessionStorage.updateContent()` as the atomic content-block write function. Extend `EditSessionViewModel` with description, captureTimestampMs, and referenceSourceDisplayName. Migrate the editor's write path from `updateTitle` to `updateContent` (title + description as an atomic pair). Add reference photo thumbnail and session date display. Add DatePicker for reference date. Reorganize into 3 `SettingsCard` groups with a sticky Save button in `Scaffold.bottomBar`.

### Binding Product Decisions

- Label for capture date in Reference Photo card: **"Session Date"** (not "Captured")
- `updateContent()` is the **sole** write path for title + description in the editor; `updateTitle()` is preserved but no longer called by the editor
- `content` block is **never removed** even when both title and description are null → `"content": {}`
- Description field: `minLines = 3`, no `maxLines`
- Thumbnail source: `reference.jpg` exclusively
- `referenceImageUri` is a UI-level derivation (not a ViewModel StateFlow)
- Capture date format: `DateFormat.MEDIUM` + `LocalConfiguration.current.locales[0]`
- Save button: `enabled = isDirty && !isSaving` — verbindlich

### What Changes

**`SessionStorage.kt`:**
- Added `fun updateContent(sessionsRoot, sessionId, title, description): Boolean`
  - Trims both fields; blank → null
  - Reads or creates content JSONObject
  - Sets/removes title and description
  - Always writes `json.put("content", content)` — never removes the block
  - Returns false on invalid sessionId, path traversal, missing metadata.json, IO/security errors

**`EditSessionViewModel.kt`:**
- `InitialSessionFields` extended with `description: String = ""`, `captureTimestampMs: Long = 0L`, `referenceSourceDisplayName: String = ""` (all with defaults for backward compat with existing test call sites)
- Added `_descriptionField`, `descriptionField`, `onDescriptionChanged()`
- Added `_captureTimestampMs`, `captureTimestampMs` (read-only, loaded from `capture.timestampMs`)
- Added `_referenceSourceDisplayName`, `referenceSourceDisplayName` (read-only, loaded from `reference.sourceDisplayName`)
- Removed `sessionTitleUpdater` lambda; added `sessionContentUpdater: (File, String, String?, String?) -> Boolean`
- `metadataReader` updated to read description, captureTimestampMs, referenceSourceDisplayName
- `updateIsDirty()` extended to include description
- `onSave()` uses `sessionContentUpdater` for content group (title+description atomically)
- `initialDescription` reset after successful save

**`EditSessionScreen.kt`:**
- Full rebuild:
  - TopAppBar: subtitle column (`SameViewSettingsSecondaryText`), no Save in actions slot
  - `Scaffold.bottomBar`: `Button` with `imePadding()` + `navigationBarsPadding()`; `enabled = isDirty && !isSaving`
  - 3 `SettingsCard` groups: **Session** (title + description), **Reference Photo** (thumbnail row + reference date field), **Location** (place name + city + country)
  - `Column(Arrangement.spacedBy(14.dp))` in content area (mirrors SettingsScreen layout)
  - Thumbnail: `rememberAsyncImagePainter` + `androidx.compose.foundation.Image`, `size(64.dp)`, `clip(MaterialTheme.shapes.small)`, `ContentScale.Crop`
  - Reference metadata: "Filename" and "Session Date" labels with `SameViewSettingsSecondaryText` / `SameViewSettingsLabelText`
  - DatePicker: `DatePickerDialog` triggered by calendar `IconButton` trailing icon on reference date field
  - `referenceImageUri`: `remember(viewModel.sessionId) { Uri.fromFile(...) }`
  - `referenceFilename`: `remember(referenceSourceDisplayName) { Uri.parse(...).lastPathSegment }`
  - `captureDate`: `remember(captureTimestampMs, locale)` using `DateFormat.MEDIUM`
  - Location fields use `edit_session_field_place_name` label (was `edit_session_field_location_display_name`)
  - All dialogs (discard, saving-in-progress) preserved from Block G

**`strings.xml`:**
- Updated `edit_session_screen_title` value: "Edit Session" (capital S)
- Added: `edit_session_subtitle`, `edit_session_save_changes`, `edit_session_card_session`, `edit_session_card_reference_photo`, `edit_session_card_location`, `edit_session_field_description`, `edit_session_placeholder_title`, `edit_session_placeholder_description`, `edit_session_placeholder_reference_date`, `edit_session_reference_date_help`, `edit_session_label_filename`, `edit_session_label_session_date`, `edit_session_pick_date_content_description`, `edit_session_field_place_name`, `edit_session_placeholder_place_name`, `edit_session_placeholder_city`, `edit_session_placeholder_country`

**`EditSessionViewModelTest.kt`:**
- `createViewModel()` helper: `titleUpdater` param → `contentUpdater: (File, String, String?, String?) -> Boolean`
- 7 existing tests migrated from `titleUpdater` to `contentUpdater`:
  - `onSave_withValidTitle_callsTitleUpdater` → `onSave_withChangedTitle_callsContentUpdater`
  - `onSave_withUnchangedTitle_doesNotCallTitleUpdater` → `onSave_withUnchangedTitle_doesNotCallContentUpdater`
  - `onSave_withBlankTitle_callsTitleUpdaterWithNull` → `onSave_withBlankTitle_callsContentUpdaterWithNullTitle`
  - `onSave_titleUpdaterFails_emitsSaveFailed` → `onSave_contentUpdaterFails_emitsSaveFailed`
  - `onSave_titleUpdaterFails_doesNotEmitSaveComplete` → `onSave_contentUpdaterFails_doesNotEmitSaveComplete`
  - `onSave_storageOrderIsTitleThenReferenceDateThenLocation` → `onSave_storageOrderIsContentThenReferenceDateThenLocation`
  - `onSave_noFieldChanged_emitsSaveComplete_withoutCallingAnyUpdater` (updated to use `contentUpdater`)
- 4 new description tests added:
  - `initialState_descriptionLoaded_fromMetadata`
  - `onDescriptionChanged_updatesState`
  - `isDirty_trueAfterDescriptionChanged`
  - `onSave_withChangedDescription_callsContentUpdater`

**`SessionStorageMetadataTest.kt`:**
- Added `createSessionWithContentFields()` helper
- Added 6 `updateContent` tests:
  - `updateContent_writesTitleAndDescription`
  - `updateContent_removesTitleWhenNull`
  - `updateContent_removesDescriptionWhenNull`
  - `updateContent_keepsContentBlockWhenBothNull`
  - `updateContent_rejectsPathTraversal`
  - `updateContent_returnsFalseWhenMetadataMissing`

### Affected Files

| File | Change Type |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt` | Modified |
| `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionScreen.kt` | Rebuilt |
| `app/src/main/res/values/strings.xml` | Modified |
| `app/src/test/java/com/isardomains/sameview/ui/compare/EditSessionViewModelTest.kt` | Modified |
| `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt` | Modified |

### Block UX Test Results (2026-06-09)

- All 50 `EditSessionViewModelTest` — PASSED (46 existing migrated + 4 new)
- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL
- `SessionStorageMetadataTest.updateContent_*` — 6 tests added (require instrumented device run)

---

*Document created 2026-06-09. Based on codebase analysis of CompareScreen.kt, MainActivity.kt, SessionScanner.kt, CameraViewModel.kt, SessionStorage.kt, CompareScreenTest.kt, and related source files.*
