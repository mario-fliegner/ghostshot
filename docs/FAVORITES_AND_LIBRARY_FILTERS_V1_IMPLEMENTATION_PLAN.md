# FAVORITES_AND_LIBRARY_FILTERS_V1_IMPLEMENTATION_PLAN.md

## 1. Document Status

### 1.1 Purpose

This document is the **working implementation plan** for the Favorites, Library Filter,
and Library Sort feature in SameView.

It supplements `FAVORITES_AND_LIBRARY_FILTERS_V1.md` without replacing it.
Where this document describes implementation steps, the spec governs behavioral
requirements. Where this document specifies test expectations, the spec governs
acceptance criteria.

### 1.2 Relationship to Spec

| Document | Role |
|---|---|
| `FAVORITES_AND_LIBRARY_FILTERS_V1.md` | Authoritative specification — defines WHAT must happen |
| This document | Implementation plan — defines HOW to reach it, in which order, block by block |

If this plan conflicts with the spec, the spec wins.

### 1.3 Scope

This plan covers implementation of:
- `SessionStorage.updateFavorite()`
- `ScannedSession.isFavorite` field
- `SessionScanner` reading `additional.isFavorite`
- `CameraViewModel.toggleFavorite()`
- Favorite star in `CompareScreen` TopAppBar
- Favorite star on `CompareLibraryScreen` tiles
- Library Filter (all / favorites only) via DataStore
- Library Sort (newest / oldest) via DataStore
- Library overflow menu with filter and sort
- Favorites-specific empty state
- Select All behavior correction in filtered view
- All required string resources
- All required tests
- `IMPLEMENTATION_NOTES.md` update

### 1.4 Explicit Non-Goals for This Plan

The following are not part of this implementation and must not be touched:
- Compare rendering pipeline (`ReferenceRenderer`, `VideoFrameRenderer`, any render engine)
- Session backup ZIP structure or `SessionBackupExporter`
- Video export pipeline (`VideoExportPipeline`, `VideoEncoder`, `MediaStoreVideoWriter`)
- Camera capture flow (`CameraX`, `MediaStore` save, `SessionStorage.saveSession`)
- GPS guidance system
- Settings screen entries or Settings screen UI
- Navigation graph structure beyond what is explicitly required for passing new parameters
- Any file not listed in the per-block scope sections

### 1.5 Source-of-Truth Hierarchy

1. `FAVORITES_AND_LIBRARY_FILTERS_V1.md` — feature spec
2. `CLAUDE_PROJECT_INSTRUCTION.md` — architecture and change discipline
3. `IMPLEMENTATION_NOTES.md` — verified implementation state
4. This document — implementation order and block contract
5. The code itself — resolves ambiguity when specs are silent

---

## 2. Implementation Principles

- **One block at a time.** Each block must compile, pass its required tests, and meet
  its Definition of Done before the next block begins.
- **No unrelated refactoring.** Do not rename, restructure, or optimize code that is
  not in the explicit scope of the current block.
- **Existing behavior preserved** unless the spec explicitly supersedes it (e.g., Select
  All behavior change in PD-08). All changes outside the stated scope are scope violations.
- **Compare rendering is untouched.** `isFavorite` never enters the rendering pipeline.
- **Backup ZIP structure is untouched.** `isFavorite` is already included in
  `metadata.json`; no ZIP format changes are needed.
- **Video export is untouched.**
- **Camera capture flow is untouched.**
- **Failing tests must not be suppressed, hidden, or removed** to make a block pass.
  If a test fails due to a legitimate pre-existing flakiness (e.g., Samsung IS_PENDING
  race), it must be documented as such.
- **Block G must not be skipped.** Documentation and release verification are
  implementation deliverables, not optional cleanup.

---

## 3. Block Overview

| Block | Name | Risk Level | Prerequisite |
|---|---|---|---|
| A | Storage + Scanner Foundation | Low | None |
| B | ViewModel Favorite Toggle | Low | Block A |
| C | CompareScreen Favorite Action | Medium | Block B |
| D | Library Tile Favorite Star | High | Block B |
| E | Library Filter + Sort DataStore | Low | None (parallel with A–D possible) |
| F | Library Filter + Sort UI | Medium | Blocks D + E |
| G | Documentation + Release Verification | None | All blocks complete |

---

## 4. Block A — Storage + Scanner Foundation

### 4.1 Goal

Establish the read/write data layer for `isFavorite`. This block has no UI component.
It produces two independently testable units: `SessionStorage.updateFavorite()` and
the `SessionScanner` reading path, plus the `ScannedSession` field extension.

### 4.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/camera/SessionStorage.kt` | Add `updateFavorite()` function |
| `app/src/main/java/.../ui/camera/SessionScanner.kt` | Add `isFavorite` to `ScannedSession`; read from `additional` block |
| `app/src/androidTest/.../storage/SessionStorageMetadataTest.kt` | New `updateFavorite_*` tests |
| `app/src/androidTest/.../storage/SessionScannerTest.kt` | New `isFavorite_*` tests |

### 4.3 Exact Scope

**`SessionStorage.updateFavorite()`:**
- Signature: `updateFavorite(sessionsRoot: File, sessionId: String, isFavorite: Boolean): Boolean`
- Path traversal validation: identical pattern to `updateTitle()`, `updateLocation()`,
  `updateReferenceDate()` in the same file
- Reads `metadata.json`, reads or creates `additional` block, sets `isFavorite`,
  writes full JSON back atomically
- Returns `true` on success, `false` on any failure
- Must not modify any field outside the `additional` block
- When creating the `additional` block on an older session that lacks it:
  must only write `isFavorite`; must not add `visibility` or `source` if not already
  present (do not pre-populate new fields for older sessions)

**`ScannedSession` data class:**
- Add `val isFavorite: Boolean = false` as a trailing field with a default value
- Default `false` preserves backward compatibility for all existing call sites

**`SessionScanner.validateUnsafe()`:**
- Read `json.optJSONObject("additional")?.optBoolean("isFavorite", false) ?: false`
- Populate `isFavorite` field of the returned `ScannedSession`
- Missing `additional` block, missing key, or non-Boolean value all resolve to `false`

### 4.4 Out of Scope

- No UI changes of any kind
- No ViewModel changes
- No `MainActivity` changes
- No string resources

### 4.5 Risks

**Low risk block.** The `updateFavorite()` function is structurally identical to
`updateTitle()` and `updateLocation()`, both of which are fully tested. The only
novel element is reading `additional.isFavorite` in the scanner, where the opt*
fallback chain is well-established.

One edge case: sessions created before v4 may not have an `additional` block at all.
`updateFavorite()` must write only `isFavorite` into a new `additional` block for
these sessions, not pre-populate `visibility` or `source`. This must be covered by a
dedicated test.

### 4.6 Required Tests

All tests are instrumentation tests on a connected device.

| Test | Class | Type |
|---|---|---|
| `updateFavorite_setsTrue` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_setsFalse` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_togglesFromTrueToFalse` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_preservesAllOtherFields` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_preservesOtherAdditionalFields` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_pathTraversal_returnsFalse` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_missingSession_returnsFalse` | `SessionStorageMetadataTest` | Instrumentation |
| `updateFavorite_createsAdditionalBlock_whenAbsent` | `SessionStorageMetadataTest` | Instrumentation |
| `isFavorite_true_whenSetInMetadata` | `SessionScannerTest` | Instrumentation |
| `isFavorite_false_whenSetFalseInMetadata` | `SessionScannerTest` | Instrumentation |
| `isFavorite_false_whenAdditionalBlockAbsent` | `SessionScannerTest` | Instrumentation |
| `isFavorite_false_whenFieldAbsentInAdditionalBlock` | `SessionScannerTest` | Instrumentation |
| `isFavorite_false_whenValueIsInvalidType` | `SessionScannerTest` | Instrumentation |

### 4.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.tests_regex="updateFavorite.*|isFavorite.*"
```

Full suite after block: not required at this stage; targeted run is sufficient.

### 4.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL
- All 13 new instrumentation tests PASSED on device
- All existing `SessionStorageMetadataTest` and `SessionScannerTest` tests remain green
- `ScannedSession` has `isFavorite: Boolean = false` trailing field
- No existing call site required updating (confirmed by successful compile)

---

## 5. Block B — ViewModel Favorite Toggle

### 5.1 Goal

Add `toggleFavorite(sessionId)` to `CameraViewModel`. This block makes the in-memory
`savedSessions` state react to favorite toggles with optimistic update and revert-on-
failure behavior. No UI is introduced in this block.

### 5.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/camera/CameraViewModel.kt` | Add `toggleFavorite()` function |
| `app/src/test/.../ui/camera/CameraViewModelTest.kt` | New `toggleFavorite_*` unit tests |

### 5.3 Exact Scope

**`CameraViewModel.toggleFavorite(sessionId: String)`:**
- Finds the current `isFavorite` value for `sessionId` in `_uiState.value.savedSessions`
- If the sessionId is not found in `savedSessions`, the function returns silently with no
  side effect
- Optimistically updates `_uiState` with the flipped `isFavorite` for that session
  (all other sessions unchanged)
- Calls `SessionStorage.updateFavorite()` on the IO dispatcher
- On success: no further action; in-memory state already reflects new value
- On failure: reverts the affected session's `isFavorite` in `_uiState` to the
  pre-toggle value; emits a `UiEvent` Snackbar with message key
  `compare_session_favorite_update_failed` (string resource added in Block C / Block D)

The revert path must be tested. The optimistic update must be visible to observers
before the IO write completes.

`toggleFavorite` does not trigger a full `SessionScanner.scan()` rescan. It updates
only the in-memory representation.

### 5.4 Out of Scope

- No UI changes
- No `CompareScreen` or `CompareLibraryScreen` changes
- No string resources (error key referenced but string added in Block C or D)
- No navigation changes

### 5.5 Risks

**Low risk.** The optimistic update pattern is already established in `CameraViewModel`
for delete operations (`deleteSessions` uses `_uiState.update { it.copy(...) }`).

The revert path requires careful test coverage: the mock for `SessionStorage.updateFavorite`
must return `false` to trigger the revert.

### 5.6 Required Tests

All unit tests (JVM, no device needed).

| Test | Class | Type |
|---|---|---|
| `toggleFavorite_flipsInMemoryStateOptimistically` | `CameraViewModelTest` | Unit |
| `toggleFavorite_revertsOnWriteFailure` | `CameraViewModelTest` | Unit |
| `toggleFavorite_emitsSnackbarOnWriteFailure` | `CameraViewModelTest` | Unit |
| `toggleFavorite_onlyAffectsTargetSession` | `CameraViewModelTest` | Unit |
| `toggleFavorite_noSideEffect_whenSessionIdNotFound` | `CameraViewModelTest` | Unit |

### 5.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### 5.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL, all 5 new `toggleFavorite_*` tests PASSED
- All existing `CameraViewModelTest` tests remain green
- No full rescan occurs on favorite toggle (verified by test: no `SessionScanner.scan`
  call is made)

---

## 6. Block C — CompareScreen Favorite Action

### 6.1 Goal

Add the favorite star to the CompareScreen TopAppBar. The star is visible only when a
valid `sessionId` context exists. Its state is derived from the ViewModel's `savedSessions`,
consistent with PD-12 (no local isFavorite state in CompareScreen). Tapping it calls
`toggleFavorite`.

### 6.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/compare/CompareScreen.kt` | New `isFavorite` parameter; new `onToggleFavorite` callback; star icon in TopAppBar |
| `app/src/main/java/.../MainActivity.kt` | Derive `isFavorite` from `uiState.savedSessions`; wire `onToggleFavorite`; pass to `CompareScreen` |
| `app/src/main/res/values/strings.xml` | New string keys: `compare_screen_favorite_mark`, `compare_screen_favorite_remove`, `compare_session_favorite_update_failed` |
| `app/src/main/res/values-de/strings.xml` | German translations for the same keys |
| `app/src/androidTest/.../ui/compare/CompareScreenTest.kt` | New `favoriteButton_*` tests |

### 6.3 Exact Scope

**`CompareScreen` parameter additions:**
- `isFavorite: Boolean = false` — current favorite status, derived from ViewModel
- `onToggleFavorite: (() -> Unit)? = null` — called when star is tapped; null in
  transient (sessionId == null) contexts

**TopAppBar action area:**
- Favorite star added alongside existing Create Video and Delete Session actions
- Visible only when `sessionId != null` (consistent with PD-10)
- Icon: outline star when `isFavorite == false`; filled star when `isFavorite == true`
- Icon tint: standard `onSurface` when outline; theme-defined amber/yellow constant
  `SameViewStarFavorited` when filled
- Content description: `compare_screen_favorite_mark` / `compare_screen_favorite_remove`
  (dynamic, reflects current state)
- Tapping the star calls `onToggleFavorite?.invoke()`
- The exact position of the star among the action icons is an implementation decision
  within the product intent defined in spec §6.1 (Favorite before Create Video is
  preferred; compact-width adjustment is permitted)
- `SameViewStarFavorited` color constant must be defined in the existing theme file
  (e.g., `SameViewTheme.kt` or the colors file), not hardcoded inline

**`MainActivity` wiring:**
- `isFavorite` is derived at the CompareScreen call site from
  `uiState.savedSessions.find { it.sessionId == sessionId }?.isFavorite ?: false`
- `onToggleFavorite` calls `cameraViewModel.toggleFavorite(sessionId)`
- When `sessionId` is null (transient compare), both are omitted / null

**No local favorite state is introduced inside `CompareScreen`.** The Composable
reacts to the `isFavorite` parameter value, which updates when `savedSessions` changes
in the ViewModel.

**String resources added in this block:**

| Key | English |
|---|---|
| `compare_screen_favorite_mark` | `"Mark as favorite"` |
| `compare_screen_favorite_remove` | `"Remove from favorites"` |
| `compare_session_favorite_update_failed` | `"Couldn't update favorite"` |

### 6.4 Out of Scope

- Library tile star (Block D)
- Library filter/sort (Blocks E–F)
- `CompareLibraryScreen` changes
- Slider, rendering, fullscreen mode, compare mechanics — all unchanged

### 6.5 Risks

**Medium risk — TopAppBar crowding on Compact width:**
Four action icons (star, create video, delete, overflow) in a Material 3 TopAppBar may
appear crowded on 360 dp portrait screens. Manual verification on a narrow device is
required before this block is considered done. If necessary, the arrangement may be
adjusted (e.g., grouping) without requiring a spec change, as long as all actions remain
accessible.

**Low risk — isFavorite derivation:**
If the sessionId passed to CompareScreen does not match any session in `savedSessions`
(e.g., session was deleted concurrently), `isFavorite` defaults to `false`. This is
correct and safe behavior.

### 6.6 Required Tests

| Test | Class | Type |
|---|---|---|
| `favoriteButton_isVisibleWhenSessionIdPresent` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_isNotVisibleWhenSessionIdNull` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_showsOutlineIconWhenNotFavorited` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_showsFilledIconWhenFavorited` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_tap_invokesToggleCallback` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_doesNotTriggerNavigation` | `CompareScreenTest` | Instrumentation |
| `favoriteButton_doesNotAffectSlider` | `CompareScreenTest` | Instrumentation |

### 6.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareScreenTest
```

### 6.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL
- All 7 new `favoriteButton_*` tests PASSED on device
- All existing `CompareScreenTest` tests remain green (currently 86 tests)
- Manual verification: star visible and functional on real device in portrait Compact
  width; all three primary actions accessible

---

## 7. Block D — CompareLibrary Tile Favorite Star

### 7.1 Goal

Add the favorite star to each `CompareSessionTile` in `CompareLibraryScreen`. This is
the highest-risk block due to the gesture separation requirement. The star must toggle
the favorite without triggering tile navigation or multi-select. In multi-select mode,
the star is hidden.

### 7.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/compare/CompareLibraryScreen.kt` | Star overlay on tile; callback wiring; multi-select hiding |
| `app/src/main/java/.../MainActivity.kt` | Wire `onToggleFavorite` callback from `CameraViewModel` to `CompareLibraryScreen` and down to tile |
| `app/src/main/res/values/strings.xml` | `compare_library_tile_favorite_mark`, `compare_library_tile_favorite_remove` |
| `app/src/main/res/values-de/strings.xml` | German translations |
| `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt` | New tile-star tests |

### 7.3 Exact Scope

**Tile star element:**
- Rendered at `Alignment.TopStart` on the tile's root container
- Touch target: minimum 48 dp × 48 dp; visual icon approximately 18–20 dp, centered
- Icon: outline star (not favorited) / filled star (favorited)
- Tint: consistent with §6.2 of the spec — `SameViewStarFavorited` for filled;
  `onSurface` for outline
- Optional semi-transparent scrim behind the icon to ensure contrast over thumbnails

**Gesture separation:**
The star's interactive area must not interfere with the tile's primary tap (open session)
or long-press (activate multi-select). The implementation must satisfy all three behavioral
requirements:
1. Tapping the star → only `onToggleFavorite(session.sessionId)` is invoked
2. Tapping the star → session-open callback is NOT invoked
3. Tapping the star → multi-select is NOT activated

The implementation technique is not prescribed by this plan. The implementer must verify
all three behaviors via instrumentation tests before this block is considered done.

**Multi-select hiding:**
- When `isSelectionMode == true`: the star element is not rendered (conditional composable)
- When `isSelectionMode == false`: the star renders according to `session.isFavorite`

**`CompareLibraryScreen` parameter additions:**
- `onToggleFavorite: (sessionId: String) -> Unit = {}` — new callback parameter,
  forwarded from `MainActivity` → `CameraViewModel.toggleFavorite()`

**Accessibility:**
- Dynamic `contentDescription` on the star element:
  `compare_library_tile_favorite_mark` when not favorited,
  `compare_library_tile_favorite_remove` when favorited
- Star is a separate semantic node from the tile
- Tile root's `stateDescription` includes favorite status for TalkBack

### 7.4 Out of Scope

- Library filter and sort (Blocks E–F)
- Overflow menu in Library (Block F)
- `CompareScreen` changes (already done in Block C)

### 7.5 Risks

**HIGH RISK — Gesture Separation**

This is the most technically delicate point of the entire feature. In Jetpack Compose,
tap events on a nested clickable element may propagate to the parent's `combinedClickable`
depending on the implementation. The risk is that a star tap accidentally opens the
session or triggers multi-select.

Mitigation:
- The star element must be placed such that its click does not propagate to the tile's
  primary `combinedClickable`
- Tests `tile_starTap_doesNotOpenSession` and `tile_starTap_doesNotActivateMultiSelect`
  are mandatory before this block is accepted
- If both tests pass on a connected device, the gesture separation is confirmed

**MEDIUM RISK — Multi-Select Regression**

Introducing a new interactive element overlaid on the tile could interfere with
existing long-press multi-select behavior. The existing test
`longPress_activatesSelectionModeAndSelectsItem` must remain green, and the new test
`longPress_stillActivatesMultiSelect_withStarPresent` confirms no regression.

### 7.6 Required Tests

| Test | Class | Type |
|---|---|---|
| `tile_starVisible_whenNotInSelectionMode` | `CompareLibraryScreenTest` | Instrumentation |
| `tile_starHidden_whenInSelectionMode` | `CompareLibraryScreenTest` | Instrumentation |
| `tile_starTap_doesNotOpenSession` | `CompareLibraryScreenTest` | Instrumentation |
| `tile_starTap_doesNotActivateMultiSelect` | `CompareLibraryScreenTest` | Instrumentation |
| `tile_starTap_invokesToggleFavorite` | `CompareLibraryScreenTest` | Instrumentation |
| `longPress_stillActivatesMultiSelect_withStarPresent` | `CompareLibraryScreenTest` | Instrumentation |
| `starContentDescription_markAsFavorite_whenNotFavorited` | `CompareLibraryScreenTest` | Instrumentation |
| `starContentDescription_removeFromFavorites_whenFavorited` | `CompareLibraryScreenTest` | Instrumentation |

All 8 tests are **mandatory before this block is accepted**. No block completion may be
claimed if any of these tests is failing.

### 7.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.tests_regex="tile_star.*|longPress_stillActivates.*|starContent.*"
```

### 7.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL
- All 8 new star-related tests PASSED on device (including both gesture-separation tests)
- All existing `CompareLibraryScreenTest` tests remain green (currently 49 tests)
- No regressions in `longPress_activatesSelectionModeAndSelectsItem` or any
  multi-select test

---

## 8. Block E — Library Filter + Sort DataStore

### 8.1 Goal

Add two new DataStore preference keys (`library_filter`, `library_sort_order`) to
`SettingsRepository`. This block has no UI component. It creates the persistence
foundation that Block F's UI layer will consume.

### 8.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/settings/SettingsRepository.kt` | Two new keys, two new Flow properties, two new suspend setter functions |
| `app/src/test/.../ui/settings/SettingsRepositoryTest.kt` | New DataStore persistence tests (if file exists; create otherwise) |

### 8.3 Exact Scope

**New `SettingsRepository` additions:**

Two new `stringPreferencesKey` constants in the `Keys` object:
- `LIBRARY_FILTER = stringPreferencesKey("library_filter")`
- `LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")`

Two new `Flow<String>` properties following the existing pattern (with catch → emptyPreferences):
- `libraryFilter`: maps stored value to `"all"` (default on null/unknown) or `"favorites"`
- `librarySortOrder`: maps stored value to `"newest_first"` (default) or `"oldest_first"`

Two new `suspend fun set*()` functions:
- `setLibraryFilter(value: String)` — stores the raw string value
- `setLibrarySortOrder(value: String)` — stores the raw string value

**No enum types need to be defined at this layer.** The ViewModel/Composable may define
local enums or sealed classes that map to these string values. The repository stores
and retrieves raw strings with safe defaults.

**No Settings screen changes.** No UI entry is introduced anywhere. `SettingsScreen.kt`
is not touched.

### 8.4 Out of Scope

- `CompareLibraryScreen` UI (Block F)
- `MainActivity` changes
- `SettingsScreen.kt` — explicitly untouched
- `SettingsViewModel.kt` — explicitly untouched

### 8.5 Risks

**Low risk.** This block follows the identical pattern of every existing DataStore key
in `SettingsRepository`. The `catch { emit(emptyPreferences()) }` pattern is already
established for all existing keys and is reused here.

### 8.6 Required Tests

| Test | Class | Type |
|---|---|---|
| `libraryFilter_defaultsToAll_whenNoValueStored` | `SettingsRepositoryTest` | Unit |
| `libraryFilter_returnsFavorites_whenStored` | `SettingsRepositoryTest` | Unit |
| `libraryFilter_defaultsToAll_whenUnknownValueStored` | `SettingsRepositoryTest` | Unit |
| `librarySortOrder_defaultsToNewest_whenNoValueStored` | `SettingsRepositoryTest` | Unit |
| `librarySortOrder_returnsOldest_whenStored` | `SettingsRepositoryTest` | Unit |
| `librarySortOrder_defaultsToNewest_whenUnknownValueStored` | `SettingsRepositoryTest` | Unit |

Note: `SettingsRepository` unit tests use an in-memory DataStore (test dependency) and
do not require a connected device.

### 8.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### 8.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL, all 6 new preference tests PASSED
- All existing `SettingsRepositoryTest` tests remain green
- No `SettingsScreen.kt` or `SettingsViewModel.kt` is modified

---

## 9. Block F — Library Filter + Sort UI

### 9.1 Goal

Add the overflow menu (⋮) to the CompareLibraryScreen normal-mode TopAppBar. The menu
contains filter and sort options. The filtered and sorted session list is derived in-
memory. The Favorites-specific empty state is added. The Select All behavior is corrected
to operate on the filtered view.

This block depends on Block D (tile star is already present) and Block E (DataStore keys
are already available).

### 9.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/compare/CompareLibraryScreen.kt` | Overflow in normal TopAppBar; filter+sort pipeline; Favorites empty state; Select All correction |
| `app/src/main/java/.../MainActivity.kt` | Read `libraryFilter` and `librarySortOrder` from `SettingsRepository`; pass to `CompareLibraryScreen` or wire callbacks |
| `app/src/main/res/values/strings.xml` | All filter/sort/empty-state string keys |
| `app/src/main/res/values-de/strings.xml` | German translations |
| `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt` | New filter/sort/empty-state/Select All tests |

### 9.3 Exact Scope

**Overflow menu in normal-mode TopAppBar:**
- New `IconButton` (⋮) in the `actions` slot of the normal-mode `TopAppBar`
- Opens a `DropdownMenu` with the structure defined in spec §10.1
- Two sections: Filter (All comparisons / Favorites only) and Sort (Newest first /
  Oldest first)
- Section headers are non-clickable text labels
- Active option shows a leading checkmark; inactive option has no leading icon
- Selecting an option updates the preference via `SettingsRepository` and closes the menu
- No overflow in selection-mode TopAppBar — that TopAppBar is unchanged

**Filter + Sort pipeline in `CompareLibraryScreen`:**
- `sessions` parameter remains the full unfiltered list passed from `MainActivity`
- Inside the Composable, the displayed list is derived: filter first, then sort
- Filter: when `libraryFilter == "favorites"`, show only sessions where `isFavorite == true`
- Sort: `"newest_first"` → descending by timestamp; `"oldest_first"` → ascending
- The `LazyVerticalGrid` renders the derived list, not the raw `sessions`

**Filter and sort preferences:**
- `CompareLibraryScreen` receives `libraryFilter` and `librarySortOrder` as parameters
  (String or local enum), read from `SettingsRepository` via `collectAsStateWithLifecycle`
  in `MainActivity`
- Alternatively, `CompareLibraryScreen` may take setter callbacks and read state
  directly from a ViewModel — the approach is an implementation decision; what matters
  is that preferences persist (via `SettingsRepository`) and the Composable reacts to
  them

**Favorites empty state:**
- When `libraryFilter == "favorites"` and the filtered list is empty:
  show the Favorites-specific empty state (spec §8.3)
- The existing "no sessions at all" empty state is unchanged and still shows when the
  full `sessions` list is empty

**Select All correction (PD-08):**
- The Select All action selects sessions from the **filtered list**, not from the full
  `sessions` list
- This is a deliberate, documented behavioral change (spec PD-08)
- The existing test `selectAll_selectsAllSessions` must be updated to reflect the new
  behavior when filter = All (same result) and a new test must cover filter = Favorites

**New string resources:**

| Key | English |
|---|---|
| `compare_library_filter_header` | `"Filter"` |
| `compare_library_filter_all` | `"All comparisons"` |
| `compare_library_filter_favorites` | `"Favorites only"` |
| `compare_library_sort_header` | `"Sort by"` |
| `compare_library_sort_newest_first` | `"Newest first"` |
| `compare_library_sort_oldest_first` | `"Oldest first"` |
| `compare_library_empty_favorites_title` | `"No favorites yet"` |
| `compare_library_empty_favorites_body` | `"Tap the star on any comparison to add it here."` |

### 9.4 Out of Scope

- `SettingsScreen.kt` — no entry added (confirmed from spec PD-05)
- Any change to the selection-mode TopAppBar structure
- Any new navigation levels or screens
- Any sort options beyond Newest / Oldest (spec §14 explicitly excludes sort by title,
  location, reference date)

### 9.5 Risks

**MEDIUM RISK — Select All behavior change:**

The existing test `selectAll_selectsAllSessions` will fail after this block because
it expects all sessions to be selected, but the corrected behavior selects only the
filtered sessions. This is an intentional, spec-mandated change (PD-08). The test
must be updated to reflect the new semantics, not suppressed.

A note must be added to `IMPLEMENTATION_NOTES.md` in Block G documenting this change.

**LOW RISK — Filter pipeline derivation:**

The filter+sort derivation is a pure in-memory transformation. If implemented in the
Composable's `remember` or in a ViewModel, Compose will recompose naturally when inputs
change. The risk of stale state is low if the derivation is based on reactive state.

### 9.6 Required Tests

| Test | Class | Type |
|---|---|---|
| `overflowMenu_isVisibleInNormalMode` | `CompareLibraryScreenTest` | Instrumentation |
| `overflowMenu_isNotVisibleInSelectionMode` | `CompareLibraryScreenTest` | Instrumentation |
| `overflowMenu_containsFilterAndSortSections` | `CompareLibraryScreenTest` | Instrumentation |
| `filter_favorites_showsOnlyFavoritedSessions` | `CompareLibraryScreenTest` | Instrumentation |
| `filter_all_showsAllSessions` | `CompareLibraryScreenTest` | Instrumentation |
| `sort_newestFirst_correctOrder` | `CompareLibraryScreenTest` | Instrumentation |
| `sort_oldestFirst_correctOrder` | `CompareLibraryScreenTest` | Instrumentation |
| `filterAndSort_combined_correct` | `CompareLibraryScreenTest` | Instrumentation |
| `emptyState_favorites_shownWhenNoFavorites` | `CompareLibraryScreenTest` | Instrumentation |
| `emptyState_favorites_disappears_whenFavoriteAdded` | `CompareLibraryScreenTest` | Instrumentation |
| `selectAll_inFavoritesFilter_selectsOnlyFavorites` | `CompareLibraryScreenTest` | Instrumentation |
| `filterAndSort_persistsAfterScreenReopen` | `CompareLibraryScreenTest` | Instrumentation |

**Updated test (existing, must be corrected):**

| Test | Class | Change |
|---|---|---|
| `selectAll_selectsAllSessions` | `CompareLibraryScreenTest` | Must be updated to clarify it operates on the filtered list; when filter = All, behavior is unchanged (selects all sessions) |

### 9.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareLibraryScreenTest
```

Full suite run recommended after Block F (all blocks now complete):
```
./gradlew connectedDebugAndroidTest
```

### 9.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL
- All 12 new filter/sort tests PASSED on device
- `selectAll_selectsAllSessions` updated and PASSED
- All previously passing `CompareLibraryScreenTest` tests remain green
- Manual verification: overflow menu opens correctly; filter and sort work; Favorites
  empty state shows; Select All in Favorites view selects only favorites
- `SettingsScreen.kt` is untouched (confirmed)

---

## 10. Block G — Documentation + Release Verification

### 10.1 Goal

Update `IMPLEMENTATION_NOTES.md` to reflect the completed feature. Run the full
instrumentation test suite. Confirm release build. Document any residual risks.

### 10.2 Files Changed

| File | Change |
|---|---|
| `docs/IMPLEMENTATION_NOTES.md` | Update Compare Library section; add new DataStore keys; note Select All behavior change; note `ScannedSession.isFavorite` field |

No production source code is changed in this block.

### 10.3 `IMPLEMENTATION_NOTES.md` Required Updates

- **Compare Library section:** Add entries for:
  - Favorites star visible on tiles in normal mode; hidden in multi-select mode
  - Filter/Sort overflow in normal-mode TopAppBar
  - Favorite star in CompareScreen TopAppBar (session context required)
  - Favorites-specific empty state
  - Select All now operates on the filtered view (supersedes prior behavior)

- **Session Storage section:** Add `updateFavorite()` to the list of update functions

- **ScannedSession:** Note `isFavorite: Boolean = false` field added

- **SettingsRepository / DataStore keys:** Add `library_filter` and `library_sort_order`

- **`IMPLEMENTATION_NOTES.md` line:** Update Select All note from "selects all sessions
  in the complete scanned session list" to "selects all sessions in the current filtered
  view (see FAVORITES_AND_LIBRARY_FILTERS_V1.md PD-08)"

### 10.4 Release Verification Test Commands

Full unit test suite:
```
./gradlew testDebugUnitTest
```

Full instrumentation suite:
```
./gradlew connectedDebugAndroidTest
```

Release build:
```
./gradlew assembleRelease
```

### 10.5 Manual Verification Checklist

The following must be verified on a real device before considering the feature complete
for release:

**CompareScreen:**
- [ ] Favorite star visible in TopAppBar when session is open (sessionId present)
- [ ] Favorite star NOT visible in TopAppBar for transient compare (no sessionId)
- [ ] Tapping outline star → star fills; session remains open; slider unchanged
- [ ] Tapping filled star → star outlines; session remains open; slider unchanged
- [ ] After returning to Library, the favorited session shows a filled star on its tile
- [ ] All existing CompareScreen actions still work: Create Video, Delete Session, Overflow

**CompareLibraryScreen — Tile Star:**
- [ ] Outline star visible on non-favorited tile
- [ ] Filled star visible on favorited tile
- [ ] Tapping tile star toggles favorite; tile stays in place; library grid does not navigate
- [ ] Long-pressing tile body (away from star) activates multi-select
- [ ] In multi-select mode, star is not visible on tiles; checkbox is visible

**CompareLibraryScreen — Overflow:**
- [ ] Overflow ⋮ visible in normal mode
- [ ] Overflow ⋮ NOT visible in selection mode
- [ ] "All comparisons" and "Favorites only" visible in Filter section
- [ ] "Newest first" and "Oldest first" visible in Sort section
- [ ] Active option shows checkmark
- [ ] Switching filter → grid updates immediately
- [ ] Switching sort → grid re-orders immediately
- [ ] Filter and sort preferences survive app restart

**Favorites Empty State:**
- [ ] When filter = Favorites only and no sessions are favorited: Favorites empty state
  shown (star icon, "No favorites yet", body text)
- [ ] After tapping a star: Favorites empty state disappears; tile appears

**Select All:**
- [ ] Filter = All, Select All: selects all sessions
- [ ] Filter = Favorites only, Select All: selects only favorited sessions

**Compact-width device:**
- [ ] CompareScreen TopAppBar shows all three primary actions (Favorite, Create Video,
  Delete) without overflow or truncation on portrait phone

### 10.6 Definition of Done (Block G)

- `testDebugUnitTest` BUILD SUCCESSFUL
- Full `connectedDebugAndroidTest` suite run on device (pre-existing flakiness
  documented; no new failures introduced)
- `assembleRelease` BUILD SUCCESSFUL
- `IMPLEMENTATION_NOTES.md` updated as specified in §10.3
- All manual verification items checked off

---

## 11. Complete File List

All files expected to be modified across all blocks:

**Production source:**
- `app/src/main/java/.../ui/camera/SessionStorage.kt`
- `app/src/main/java/.../ui/camera/SessionScanner.kt`
- `app/src/main/java/.../ui/camera/CameraViewModel.kt`
- `app/src/main/java/.../ui/compare/CompareScreen.kt`
- `app/src/main/java/.../ui/compare/CompareLibraryScreen.kt`
- `app/src/main/java/.../ui/settings/SettingsRepository.kt`
- `app/src/main/java/.../MainActivity.kt`

**Resources:**
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

**Theme (color constant):**
- Color/theme file where `SameViewStarFavorited` color constant is defined
  (e.g., `ui/theme/Color.kt` or equivalent)

**Tests:**
- `app/src/androidTest/.../storage/SessionStorageMetadataTest.kt`
- `app/src/androidTest/.../storage/SessionScannerTest.kt`
- `app/src/test/.../ui/camera/CameraViewModelTest.kt`
- `app/src/androidTest/.../ui/compare/CompareScreenTest.kt`
- `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt`
- `app/src/test/.../ui/settings/SettingsRepositoryTest.kt` (create if not present)

**Documentation:**
- `docs/IMPLEMENTATION_NOTES.md`

**Not modified (explicitly):**
- `SessionStorage.saveSession()` — no changes to session creation path
- `SessionDeleter.kt` — no changes to delete behavior
- `SessionBackupExporter.kt` — no changes to backup ZIP structure
- `VideoExportPipeline.kt` and all video rendering files — untouched
- `SettingsScreen.kt`, `SettingsViewModel.kt` — no settings screen entries
- `GPS_RECREATION_SYSTEM_V1.md` and all GPS files — untouched
- `ReferenceRenderer.kt` and all rendering files — untouched

---

## 12. Risk Register Summary

| Risk | Block | Severity | Test Gate |
|---|---|---|---|
| Gesture separation in Library tile | D | High | `tile_starTap_doesNotOpenSession`, `tile_starTap_doesNotActivateMultiSelect` |
| Multi-select regression | D | High | `longPress_stillActivatesMultiSelect_withStarPresent` + existing tests |
| CompareScreen TopAppBar crowding on Compact | C | Medium | Manual on 360 dp portrait |
| Optimistic update rollback on failure | B | Medium | `toggleFavorite_revertsOnWriteFailure` |
| Select All behavior change breaks existing test | F | Medium | Update `selectAll_selectsAllSessions` |
| DataStore unknown value handling | E | Low | `*_defaultsTo*_whenUnknownValueStored` |
| Backward compatibility for missing `additional.isFavorite` | A | Low | `isFavorite_false_whenAdditionalBlockAbsent` |
| `updateFavorite` overwrites existing additional fields | A | Low | `updateFavorite_preservesOtherAdditionalFields` |

---

## 13. Progress Table

| Block | Description | Status |
|---|---|---|
| A | Storage + Scanner Foundation | Not started |
| B | ViewModel Favorite Toggle | Not started |
| C | CompareScreen Favorite Action | Not started |
| D | CompareLibrary Tile Favorite Star | Not started |
| E | Library Filter + Sort DataStore | Not started |
| F | Library Filter + Sort UI | Not started |
| G | Documentation + Release Verification | Not started |
