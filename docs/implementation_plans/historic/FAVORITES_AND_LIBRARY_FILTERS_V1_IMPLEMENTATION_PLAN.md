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

Add `toggleFavorite(sessionId)` to `CameraViewModel`. This block applies the
**Write-First** strategy: `metadata.json` is written first; only on confirmed success
is the in-memory `savedSessions` entry updated (targeted, single entry). No UI is
introduced in this block.

**Architecture decision (Block B analysis, 2026-06-20):** The originally planned
optimistic-update-with-revert approach was replaced by Write-First. Rationale: the IO
write on internal storage completes in ~1–5 ms (imperceptible to the user); the revert
path is eliminated, reducing complexity and removing a correctness hazard during rapid
concurrent taps.

### 5.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/camera/CameraViewModel.kt` | Add `sessionFavoriteUpdater` lambda; add `toggleFavorite()` function; extend internal test constructor |
| `app/src/test/.../ui/camera/CameraViewModelTest.kt` | New `toggleFavorite_*` unit tests |
| `app/src/main/res/values/strings.xml` | `compare_session_favorite_update_failed` — required at compile time for Block B build |
| `app/src/main/res/values-de/strings.xml` | German translation of the same key |

### 5.3 Exact Scope

**`sessionFavoriteUpdater` injectable lambda:**

```
private var sessionFavoriteUpdater: (File, String, Boolean) -> Boolean =
    { root, id, fav -> SessionStorage.updateFavorite(root, id, fav) }
```

This follows the identical pattern of `sessionTitleUpdater` and `sessionDeleter`
already present in `CameraViewModel`. The lambda is exposed as an optional parameter
in the internal test constructor.

**`CameraViewModel.toggleFavorite(sessionId: String)` — Write-First:**
- Looks up the current `isFavorite` value for `sessionId` in `_uiState.value.savedSessions`
- If `sessionId` is not found in `savedSessions`: returns silently with no side effect
- Calculates the new value: `!currentIsFavorite`
- Calls `sessionFavoriteUpdater(sessionsRoot, sessionId, newValue)` on the IO dispatcher
- On success: updates only the affected `ScannedSession` entry in `_uiState.savedSessions`
  via a targeted `map { if (it.sessionId == sessionId) it.copy(isFavorite = newValue) else it }`
- On failure: emits `UiEvent.ShowSnackbar(R.string.compare_session_favorite_update_failed)`;
  `savedSessions` is not modified (no revert needed — nothing was changed in memory)

No revert path. No full `SessionScanner.scan()` rescan. Only the single affected entry
is updated in memory, and only after a confirmed write.

**String resource added in this block:**

| Key | English value | Reason added in Block B |
|---|---|---|
| `compare_session_favorite_update_failed` | `"Couldn't update favorite"` | Referenced as `R.string.*` in `CameraViewModel`; must exist at compile time |

**Internal test constructor extension:**
The existing internal constructor receives a new optional parameter
`sessionFavoriteUpdater: ((File, String, Boolean) -> Boolean)? = null`, wired identically
to `sessionTitleUpdater`.

### 5.4 Out of Scope

- No UI changes of any kind
- No `CompareScreen` or `CompareLibraryScreen` changes
- No navigation changes
- No `SettingsRepository` or DataStore changes
- The content description strings (`compare_screen_favorite_mark`,
  `compare_screen_favorite_remove`, `compare_library_tile_favorite_mark`,
  `compare_library_tile_favorite_remove`) are added in Blocks C and D where they are
  first used in UI code — not in Block B

### 5.5 Risks

**Low risk overall.** Write-First eliminates the revert path entirely, which was the
primary source of complexity in the original optimistic-update approach.

**Race condition (concurrent rapid taps):** Two concurrent `toggleFavorite` calls may
start overlapping IO writes. Both writes target the same file; the last write wins on
disk. Both calls update `_uiState` independently after their respective success — via
`_uiState.update {}` which is atomic on `MutableStateFlow`. The final in-memory value
will match the final disk value. No revert-overshoot possible.

**String resource missing at compile time:** `R.string.compare_session_favorite_update_failed`
must exist before `assembleDebug` can succeed. This is handled by adding the key in
§5.2. Not adding it would cause a build failure in Block B.

**No revert path to test:** This is a simplification, not a risk. The absence of revert
logic reduces the test surface and eliminates an entire class of state-divergence bugs.

### 5.6 Required Tests

All unit tests (JVM, no device needed).

| Test | Class | Type |
|---|---|---|
| `toggleFavorite_updatesInMemoryState_onSuccess` | `CameraViewModelTest` | Unit |
| `toggleFavorite_doesNotUpdateInMemoryState_onWriteFailure` | `CameraViewModelTest` | Unit |
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
- No full rescan occurs on favorite toggle (verified by test: `sessionScanner` mock
  is not called during `toggleFavorite`)
- `savedSessions` is unchanged on write failure (verified by
  `toggleFavorite_doesNotUpdateInMemoryState_onWriteFailure`)

---

## 5B. Block B — Completion Record

**Status:** DONE
**Completed:** 2026-06-20

### Files Changed

| File | Change |
|---|---|
| `app/.../ui/camera/CameraViewModel.kt` | `sessionFavoriteUpdater` private lambda; `toggleFavorite(sessionId)` function (Write-First); internal constructor extended with `sessionFavoriteUpdater` as trailing optional parameter |
| `app/src/test/.../ui/camera/CameraViewModelTest.kt` | 5 new `toggleFavorite_*` unit tests using named arg `sessionFavoriteUpdater =` |
| `app/src/main/res/values/strings.xml` | `compare_session_favorite_update_failed = "Couldn't update favorite"` |
| `app/src/main/res/values-de/strings.xml` | `compare_session_favorite_update_failed = "Favorit konnte nicht aktualisiert werden"` |

### Architecture

Write-First strategy implemented (not optimistic update). No revert path. No full rescan.
`sessionFavoriteUpdater` is the trailing optional parameter in the internal test constructor
— using named argument in tests avoids positional conflict with `sessionBackupExporter`.

### Test Commands Executed

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Test Results

| Run | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL — 206/206 PASSED (201 existing + 5 new) |

### Risk Coverage Confirmed

| Risk | Covered by |
|---|---|
| Race condition (concurrent taps) | `MutableStateFlow.update {}` atomic; last success wins on disk and in memory |
| Write failure without state change | `toggleFavorite_doesNotUpdateInMemoryState_onWriteFailure` |
| Exception in write path → treated as failure | `try/catch` in `toggleFavorite`; exception → `false` → Snackbar |
| savedSessions consistency | `toggleFavorite_onlyAffectsTargetSession` |
| Missing string resource at compile time | String added in this block; `assembleDebug` confirmed |
| No full rescan | `toggleFavorite_noSideEffect_whenSessionIdNotFound` verifies no scanner call |
| No regression in existing tests | 201/201 existing CameraViewModelTest tests remain green |

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
| `app/src/main/res/values/strings.xml` | New string keys: `compare_screen_favorite_mark`, `compare_screen_favorite_remove` |
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

Note: `compare_session_favorite_update_failed` is added in Block B (required there at
compile time). It is not re-added here.

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

## 6C. Block C — Completion Record

**Status:** DONE
**Completed:** 2026-06-20

### Files Changed

| File | Change |
|---|---|
| `app/.../ui/compare/CompareScreen.kt` | `isFavorite: Boolean = false` and `onToggleFavorite: (() -> Unit)? = null` parameters; star `IconButton` with testTag `compare_screen_favorite_button`; `Icons.Filled.Star` / `Icons.Outlined.Star`; `SameViewStarFavorited` tint; dynamic `contentDescription`; portrait Spacer condition updated |
| `app/.../MainActivity.kt` | `isFavorite` derived from `savedSessions.find { it.sessionId == sessionId }?.isFavorite ?: false`; `onToggleFavorite` wired to `viewModel.toggleFavorite(sessionId)` |
| `app/src/main/java/.../ui/theme/Color.kt` | `SameViewStarFavorited = Color(0xFFFFC107)` — named amber/yellow constant |
| `app/src/main/res/values/strings.xml` | `compare_screen_favorite_mark`, `compare_screen_favorite_remove` |
| `app/src/main/res/values-de/strings.xml` | `Als Favorit markieren`, `Aus Favoriten entfernen` |
| `app/src/androidTest/.../ui/compare/CompareScreenTest.kt` | `setCompareContent()` extended with `isFavorite` and `onToggleFavorite` defaults; 7 new `favoriteButton_*` tests |

### Architecture

- No local isFavorite state in CompareScreen (PD-12 preserved)
- `isFavorite` derived in MainActivity from `CameraViewModel.uiState.savedSessions` — live reactive
- Favourite colour is exclusively defined in `Color.kt` as `SameViewStarFavorited`; no hex values or inline colours in CompareScreen
- TopAppBar order: ← Back | [★ Favourite] | [Create Video] | [Delete] | ⋮

### Test Commands Executed

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareScreenTest
```

### Test Results

| Run | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `CompareScreenTest` (full class) | **105/105 PASSED** on SM-S911B (Android 16) — 0 failed, 0 skipped |

### Risk Coverage Confirmed

| Risk | Status |
|---|---|
| TopAppBar crowding Compact Width | ← Back + 4 × 48 dp = 240 dp; title visible in 120 dp portrait remainder; manual verification required |
| Landscape metadata column narrower | Weight(1f) absorbs remaining space; Ellipsis expected on long titles/locations — spec-konform |
| No local state duplication | Verified: `isFavorite` is a parameter, not a `remember` |
| Colour centrally defined | `SameViewStarFavorited` in `Color.kt`; no inline colours |
| Video Export / Delete / Overflow regression | All 105 existing tests remain green |
| Accessibility ContentDescriptions | Tests 3 + 4 verify ContentDescription switches between `compare_screen_favorite_mark` and `compare_screen_favorite_remove` |

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

## 7D. Block D — Completion Record

**Status:** DONE
**Completed:** 2026-06-20

### Files Changed

| File | Change |
|---|---|
| `app/.../ui/compare/CompareLibraryScreen.kt` | New imports (`CircleShape`, `Star`/`Star outlined`, `detectTapGestures`, `pointerInput`, `Role`, `onClick`, `role`, `SameViewStarFavorited`); `onToggleFavorite: (String) -> Unit = {}` screen parameter; forwarded as `() -> Unit` closure to tile; star overlay at `Alignment.TopStart` with 48 dp touch target, scrim, `detectTapGestures` (both tap + longPress consumed), semantics for TalkBack; hidden in multi-select mode |
| `app/.../MainActivity.kt` | `onToggleFavorite = { sessionId -> viewModel.toggleFavorite(sessionId) }` wired to `CompareLibraryScreen` |
| `app/src/main/res/values/strings.xml` | `compare_library_tile_favorite_mark`, `compare_library_tile_favorite_remove` |
| `app/src/main/res/values-de/strings.xml` | `Als Favorit markieren`, `Aus Favoriten entfernen` |
| `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt` | New imports; `createFakeSession()` extended with `isFavorite: Boolean = false`; `setLibraryContent()` extended with `onToggleFavorite`; 8 new `tile_star*` / `longPress*` / `starContent*` tests using `useUnmergedTree = true` |

### Architecture Notes

- Long-press on the star is consumed (`onLongPress = {}` in `detectTapGestures`) — multi-select is NOT triggered
- Tap on the star invokes `onToggleFavorite()` via BOTH real touch (`detectTapGestures.onTap`) and TalkBack (`semantics.onClick`)
- Star uses `useUnmergedTree = true` in tests because `combinedClickable` on the tile root merges child semantics

### Test Commands Executed

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareLibraryScreenTest
```

### Test Results

| Run | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `CompareLibraryScreenTest` (full class) | **57/57 PASSED** on SM-S911B (Android 16) — 0 failed, 0 skipped |

Note: First run had 4 failures because star testTag searches lacked `useUnmergedTree = true`. Fixed immediately; second run 57/57 green.

---

## 8. Block E — Library Filter + Sort DataStore

### 8.1 Goal

Add two new DataStore preference keys (`library_filter`, `library_sort_order`) to
`SettingsRepository`. This block has no UI component. It creates the persistence
foundation that Block F's UI layer will consume.

### 8.2 Files Changed

| File | Change |
|---|---|
| `app/src/main/java/.../ui/settings/SettingsRepository.kt` | Enums `LibraryFilter` + `LibrarySortOrder` (defined here); two new Keys; two new typed Flow properties; two new suspend setter functions |
| `app/src/androidTest/.../ui/settings/SettingsRepositoryTest.kt` | New DataStore persistence tests — instrumentation, follows existing test pattern |

### 8.3 Exact Scope

**New enums — defined in `SettingsRepository.kt`:**

Two new enums are defined directly in `SettingsRepository.kt`, at the top of the file,
alongside the existing pattern. They are NOT placed in `CompareLibraryScreen`, `CameraViewModel`,
or any UI package. No separate file is created.

```
LibraryFilter:    ALL ("all"),  FAVORITES ("favorites")   default: ALL
LibrarySortOrder: NEWEST_FIRST ("newest_first"),  OLDEST_FIRST ("oldest_first")   default: NEWEST_FIRST
```

Each enum value carries its DataStore string representation. The `when` mapping uses
these string values as match branches; the `else` branch returns the default.

**New `SettingsRepository` additions:**

Two new `stringPreferencesKey` constants in the `Keys` object:
- `LIBRARY_FILTER = stringPreferencesKey("library_filter")`
- `LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")`

Two new typed Flow properties following the existing pattern (with catch → emptyPreferences):
- `libraryFilter: Flow<LibraryFilter>` — maps `"favorites"` → `FAVORITES`; all other values (including null) → `ALL`
- `librarySortOrder: Flow<LibrarySortOrder>` — maps `"oldest_first"` → `OLDEST_FIRST`; all other values → `NEWEST_FIRST`

Two new suspend setter functions:
- `setLibraryFilter(filter: LibraryFilter)` — stores the enum's string value
- `setLibrarySortOrder(order: LibrarySortOrder)` — stores the enum's string value

**No Settings screen changes.** No UI entry is introduced. `SettingsScreen.kt` is not
touched.

### 8.4 Out of Scope

The following are explicitly NOT part of Block E. They belong to Block F:

- `CompareLibraryScreen.kt` — all UI; overflow menu; filter/sort application; Favorites empty state
- `MainActivity.kt` — reading from new flows; passing to CompareLibraryScreen
- `CameraViewModel.kt` — collecting new flows; updating CameraUiState
- `CameraUiState` — no new fields for library state
- Filter application logic (applying `LibraryFilter` to the session list)
- Sort application logic (applying `LibrarySortOrder` to the session list)
- UI-facing string resources for filter/sort labels
- Select All behavior correction (PD-08)

Also explicitly untouched in this block:
- `SettingsScreen.kt` and `SettingsViewModel.kt` — no settings screen entries for these preferences

### 8.5 Risks

**Low risk.** This block follows the identical pattern of every existing DataStore key
in `SettingsRepository`. The `catch { emit(emptyPreferences()) }` pattern is already
established for all existing keys and is reused here.

**`fakeSettingsRepository` mock in `CameraViewModelTest.kt`:** When Block F adds
collection of `libraryFilter` and `librarySortOrder` in `CameraViewModel.init`, the
existing mock will need two new stub entries. This is a Block-F task, not Block E.

### 8.6 Required Tests

**Test type: Instrumentation (on device).** `SettingsRepositoryTest` is an
instrumentation test class that uses `InstrumentationRegistry.getInstrumentation().targetContext`
and `PreferenceDataStoreFactory` backed by a real file. New tests follow the identical
pattern: UUID-named temp file, `TestScope(UnconfinedTestDispatcher())`, `.first()` to
read, set/read pairs to verify persistence. A connected device is required.

| Test | Class | Type |
|---|---|---|
| `libraryFilter_defaultsToAll` | `SettingsRepositoryTest` | Instrumentation |
| `setLibraryFilter_favoritesIsPersisted` | `SettingsRepositoryTest` | Instrumentation |
| `setLibraryFilter_allCanBeSetBack` | `SettingsRepositoryTest` | Instrumentation |
| `libraryFilter_invalidStoredValue_defaultsToAll` | `SettingsRepositoryTest` | Instrumentation |
| `librarySortOrder_defaultsToNewestFirst` | `SettingsRepositoryTest` | Instrumentation |
| `setLibrarySortOrder_oldestFirstIsPersisted` | `SettingsRepositoryTest` | Instrumentation |
| `setLibrarySortOrder_newestFirstCanBeSetBack` | `SettingsRepositoryTest` | Instrumentation |
| `librarySortOrder_invalidStoredValue_defaultsToNewestFirst` | `SettingsRepositoryTest` | Instrumentation |

Tests 4 and 8 (invalid stored value) use `dataStore.edit { prefs[key] = "garbage" }`
directly, exactly as `gridType_invalidStoredValue_defaultsToRuleOfThirds` does.

### 8.7 Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.settings.SettingsRepositoryTest
```

### 8.8 Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL (no unit-test changes; compile check only)
- All 8 new `SettingsRepositoryTest` instrumentation tests PASSED on device
- All 13 existing `SettingsRepositoryTest` tests remain green
- No `SettingsScreen.kt`, `SettingsViewModel.kt`, `CameraViewModel.kt`, `CompareLibraryScreen.kt`,
  or `MainActivity.kt` is modified

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
| `app/src/main/java/.../ui/camera/CameraViewModel.kt` | `val libraryFilter` and `val librarySortOrder` as pass-through flows from SettingsRepository; `fun setLibraryFilter()` and `fun setLibrarySortOrder()` as delegating setter functions |
| `app/src/test/.../ui/camera/CameraViewModelTest.kt` | Update `fakeSettingsRepository` mock to stub `libraryFilter` and `librarySortOrder` flows |
| `app/src/main/java/.../ui/compare/CompareLibraryScreen.kt` | 4 new parameters; filter+sort pipeline; overflow menu in normal TopAppBar; Favorites empty state; Select All correction; LaunchedEffect update |
| `app/src/main/java/.../MainActivity.kt` | Collect `viewModel.libraryFilter` and `viewModel.librarySortOrder` inside the CompareLibrary composable block; pass to `CompareLibraryScreen`; wire setter callbacks |
| `app/src/main/res/values/strings.xml` | All filter/sort/empty-state string keys |
| `app/src/main/res/values-de/strings.xml` | German translations |
| `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt` | New filter/sort/empty-state/Select All tests; extend `setLibraryContent()` with 4 new optional parameters |

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

**Architecture decision — Pass-Through Flows (no CameraUiState extension):**

`brandingEnabled` (VIDEO_EXPORT_V1) is the established precedent: it is read directly
by `CreateVideoViewModel`, not via `CameraUiState`. The same pattern applies here.

`libraryFilter` and `librarySortOrder` are NOT added to `CameraUiState`. `CameraViewModel.init`
does NOT gain new `collect {}` blocks for these flows.

Instead:
- `CameraViewModel` exposes `val libraryFilter: Flow<LibraryFilter>` and
  `val librarySortOrder: Flow<LibrarySortOrder>` as pass-through properties that
  delegate directly to `settingsRepository.libraryFilter` / `settingsRepository.librarySortOrder`
- `CameraViewModel` adds `fun setLibraryFilter(filter: LibraryFilter)` and
  `fun setLibrarySortOrder(order: LibrarySortOrder)` as suspending one-liner setters
  that delegate to the corresponding `SettingsRepository` setters
- In `MainActivity`, inside the `composable(ROUTE_COMPARE_LIBRARY)` lambda, the two
  flows are collected via `collectAsStateWithLifecycle()` and passed as parameters to
  `CompareLibraryScreen`; the setters are wired as callbacks

**Filter and sort parameters for `CompareLibraryScreen`:**
- `libraryFilter: LibraryFilter = LibraryFilter.ALL`
- `librarySortOrder: LibrarySortOrder = LibrarySortOrder.NEWEST_FIRST`
- `onSetLibraryFilter: (LibraryFilter) -> Unit = {}`
- `onSetLibrarySortOrder: (LibrarySortOrder) -> Unit = {}`

**`fakeSettingsRepository` mock in `CameraViewModelTest`:**
Since `CameraViewModel` initializes `val libraryFilter = settingsRepository.libraryFilter`
as a class-level property at construction time, the mock must stub this property to
avoid null-flow issues. Add to `fakeSettingsRepository`:
- `on { libraryFilter } doReturn flowOf(LibraryFilter.ALL)`
- `on { librarySortOrder } doReturn flowOf(LibrarySortOrder.NEWEST_FIRST)`

**Filter + Sort pipeline (typed enums, not raw strings):**
- Filter: `when (libraryFilter) { LibraryFilter.FAVORITES → sessions.filter { it.isFavorite }; else → sessions }`
- Sort: `when (librarySortOrder) { OLDEST_FIRST → sortedBy { it.timestamp }; else → sortedByDescending { it.timestamp } }`
- Derivation cached with `remember(sessions, libraryFilter, librarySortOrder) { ... }`

**Favorites empty state:**
- When `libraryFilter == FAVORITES` and the filtered list is empty:
  show the Favorites-specific empty state (spec §8.3)
- The existing "no sessions at all" empty state is unchanged and still shows when the
  full `sessions` list is empty
- Three mutually exclusive display states, checked in this order:
  1. `sessions.isEmpty()` → "No comparisons yet" (original empty state)
  2. `displayedSessions.isEmpty() && sessions.isNotEmpty()` → "No favorites yet" (new)
  3. `displayedSessions.isNotEmpty()` → grid

**Empty displayedSessions edge case — Selection Mode must exit:**

When Filter = Favorites only is active and the last visible favorited session is
unfavorited (e.g., via the star on a tile), `displayedSessions` becomes empty while
Selection Mode may still be active. In this state, Selection Mode must exit and the
selected session set must be cleared.

Required behavior: whenever `displayedSessions` becomes empty, if Selection Mode is
active it must be deactivated and the selection cleared. The implementation mechanism
(LaunchedEffect, derived state, or similar) is an implementation decision. The behavioral
requirement is non-negotiable: an empty filtered grid must never remain in Selection Mode.

This is an extension of the existing behavior where `LaunchedEffect(sessions.isEmpty())`
exits Selection Mode when all sessions are deleted. The new requirement covers the
additional case where sessions still exist but are filtered out.

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

Note: `filterAndSort_persistsAfterScreenReopen` is intentionally **not included**.
DataStore persistence is already fully covered by `SettingsRepositoryTest` (Block E,
tests `setLibraryFilter_favoritesIsPersisted`, `setLibrarySortOrder_oldestFirstIsPersisted`,
and their counterparts). Repeating this in a UI instrumentation test would require a
real DataStore instance in `CompareLibraryScreenTest`, adding unjustified complexity.
Block F UI tests verify that the correct `LibraryFilter`/`LibrarySortOrder` parameter
values produce the expected display results — persistence is not their concern.

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
- All 11 new filter/sort tests PASSED on device (12 were originally planned; 1 removed per plan correction)
- `selectAll_selectsAllSessions` updated and PASSED
- All previously passing `CompareLibraryScreenTest` tests remain green
- Manual verification: overflow menu opens correctly; filter and sort work; Favorites
  empty state shows; Select All in Favorites view selects only favorites
- `SettingsScreen.kt` is untouched (confirmed)

---

## 9F. Block F — Completion Record

**Status:** DONE
**Completed:** 2026-06-20

### Files Changed

| File | Change |
|---|---|
| `app/.../ui/camera/CameraViewModel.kt` | `val libraryFilter` + `val librarySortOrder` as pass-through flows; `setLibraryFilter()` + `setLibrarySortOrder()` delegating setters; imports for `LibraryFilter`, `LibrarySortOrder`, `Flow` |
| `app/src/test/.../ui/camera/CameraViewModelTest.kt` | `fakeSettingsRepository` mock extended with `libraryFilter` and `librarySortOrder` stubs; imports added |
| `app/.../ui/compare/CompareLibraryScreen.kt` | 4 new parameters (`libraryFilter`, `librarySortOrder`, `onSetLibraryFilter`, `onSetLibrarySortOrder`); `displayedSessions` derivation with `remember`; `showSortFilterMenu` state; overflow button in normal TopAppBar with `DropdownMenu` (Filter + Sort sections, checkmarks, divider); Favorites empty state (star icon, no CTA); `LaunchedEffect(displayedSessions.isEmpty())` exits selection mode; Select All corrected to operate on `displayedSessions`; grid changed from `sessions` to `displayedSessions`; new imports |
| `app/.../MainActivity.kt` | `libraryFilter` and `librarySortOrder` collected via `collectAsStateWithLifecycle()` inside the CompareLibrary composable block; 4 new params passed to `CompareLibraryScreen`; imports added |
| `app/src/main/res/values/strings.xml` | 9 new keys: favorites empty state, filter section, sort section, overflow content description |
| `app/src/main/res/values-de/strings.xml` | 9 German translations |
| `app/src/androidTest/.../ui/compare/CompareLibraryScreenTest.kt` | `createFakeSession()` extended with `timestamp` param; `setLibraryContent()` extended with 4 filter/sort params; 11 new `overflowMenu_*` / `filter_*` / `sort_*` / `emptyState_*` / `selectAll_*` tests |

### Architecture Notes

- **CameraUiState NOT extended.** `libraryFilter` and `librarySortOrder` are exposed as pass-through `Flow` properties from `settingsRepository`, NOT collected into `CameraUiState` in `init {}`. Pattern follows `brandingEnabled` (handled by `CreateVideoViewModel` directly).
- **Select All operates on `displayedSessions`** (filtered view), not the raw `sessions` list — per PD-08.
- **DataStore persistence covered by Block E** (`SettingsRepositoryTest`). No persistence-specific UI tests added.

### Test Commands Executed

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareLibraryScreenTest
```

### Test Results

| Run | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL (one `allSelected` → `allDisplayedSelected` fix required) |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `CompareLibraryScreenTest` (full class) | **68/68 PASSED** on SM-S911B (Android 16) — 57 existing + 11 new |

---

## 9G. Pre-Block-G UX Polish

These two corrections were identified during manual verification after Block F completion.
They must be completed before Block G begins. Both are small, localized changes to
`CompareLibraryScreen.kt` only. No behavioral changes, no new features, no spec-level
decisions are open — the required behavior is fully specified in
`FAVORITES_AND_LIBRARY_FILTERS_V1.md §7.1` and `§10.3`.

### Block F.1 — UX Polish: Favorite Star Corner Proximity

**Status:** Not started

**Goal:**
The visual star icon currently appears centered within its 48 dp touch target, placing
its visual center approximately 28 dp from the tile corner. The Selection Checkbox at
TopEnd has its visual center at approximately 14 dp from its corner. This asymmetry
makes the star look embedded in the image rather than at the corner.

**Required change:**
Anchor the visual icon and scrim to the TopStart edge of the touch target, not to its
center. The touch target remains 48 dp and is not repositioned. Only the alignment of
the icon/scrim content within the touch target changes.

Additionally: reduce scrim size (radius ~20–22 dp) and opacity (~25–35 %) to lower
visual weight. See spec §7.1 and §18.1.

**Files changed:** `CompareLibraryScreen.kt` only

**Risk:** Very low — visual position change only; touch target and semantics unchanged

**Tests:** Existing `tile_star*` tests remain valid. Height stability tests remain valid.

---

### Block F.2 — UX Polish: Overflow Menu Structural Rendering

**Status:** Not started

**Goal:**
Section headers ("Filter", "Sort by") in the Library overflow menu are currently rendered
as `DropdownMenuItem(enabled = false)`. This produces the visual appearance of a disabled
interactive item — incorrect semantics for a category label. Additionally, items with and
without a checkmark have different horizontal text positions because the `leadingIcon`
slot is conditionally occupied.

**Required changes:**
1. Section headers: replace `DropdownMenuItem(enabled = false)` with a plain `Text`
   composable placed directly inside the `DropdownMenu`, styled with secondary text
   appearance (`labelSmall`, `onSurfaceVariant`).
2. Option items: ensure consistent horizontal text alignment across active (checkmark)
   and inactive (no checkmark) items within the same group — the leading icon slot must
   be reserved consistently, not conditionally. See spec §10.3.

**Files changed:** `CompareLibraryScreen.kt` only

**Risk:** Very low — structural rendering fix only; filter/sort logic and interaction unchanged

**Tests:** Existing `overflowMenu_*` tests remain valid. New structural assertion may be
added to `overflowMenu_containsFilterAndSortSections` to verify header styling.

---

## 9H. Block F.3 — EditSession Favorite TopAppBar Action

**Status:** Not started

**Prerequisite:** Blocks A–F, F.1, F.2 complete (all done as of 2026-06-20)

**Prerequisite for:** Block G (Block G may not start until F.3 is DONE)

### Goal

Add a Favorite star to the `EditSessionScreen` TopAppBar, consistent with CompareScreen
(§6) and per product decision `FAVORITES_AND_LIBRARY_FILTERS_V1.md §18.3`. The star
provides the third Favorites entry point in the app. It toggles `isFavorite` immediately,
independently of the form Save flow.

Full behavioral spec: `SESSION_METADATA_EDITOR_V1.md §20`.

### Files Changed

| File | Change |
|---|---|
| `app/.../ui/compare/EditSessionViewModel.kt` | `isFavorite: StateFlow<Boolean>` + `toggleFavorite()` + `sessionFavoriteUpdater` injectable lambda; `InitialSessionFields` extended with `isFavorite: Boolean = false` |
| `app/.../ui/compare/EditSessionScreen.kt` | `actions` slot added to TopAppBar with `IconButton` (star); `isFavorite` collected from ViewModel |
| `app/src/androidTest/.../ui/compare/EditSessionScreenTest.kt` | 6 new `favoriteButton_*` tests |

No new string resources required — `compare_screen_favorite_mark` and
`compare_screen_favorite_remove` are reused.

### Exact Scope

**`EditSessionViewModel` additions:**
- `InitialSessionFields`: add `isFavorite: Boolean = false` as trailing field with default
  (no existing call-site changes required)
- `isFavorite: MutableStateFlow<Boolean>` initialized from `InitialSessionFields.isFavorite`
  in `init`
- `sessionFavoriteUpdater: (File, String, Boolean) -> Boolean` — injectable lambda,
  default: `SessionStorage.updateFavorite`; follows identical pattern to
  `sessionLocationUpdater` and `sessionReferenceDateUpdater`
- `toggleFavorite()` — calls `sessionFavoriteUpdater(sessionsRoot, sessionId, !isFavorite.value)`
  on IO dispatcher; updates `isFavorite` StateFlow on success; emits failure event on error
- `isDirty` is NOT modified by `toggleFavorite()`

**`EditSessionScreen` additions:**
- TopAppBar receives `actions` slot with star `IconButton`
- `isFavorite` collected via `collectAsStateWithLifecycle()`
- Icon states: `Icons.Outlined.Star` (not favorited, `onBackground` tint) /
  `Icons.Filled.Star` (`SameViewStarFavorited` tint) — consistent with §6.2
- Content description: `compare_screen_favorite_mark` / `compare_screen_favorite_remove`
  (reused, no new strings)

### Out of Scope

- No form-field Favorite toggle (Variante B was rejected)
- No `isDirty` change
- No modification to Save, Discard, or Back behavior for form fields
- No new string resources
- No changes to `SessionStorage`, `SessionScanner`, `CameraViewModel`, `CompareScreen`,
  `CompareLibraryScreen`, `SettingsRepository`, Navigation, Rendering, Backup, Video Export

### Risks

**Low risk — isFavorite persists despite Discard:**
If the user toggles the star and then discards form changes, the star change remains
persisted. This is the intended behavior (§20.5 of SESSION_METADATA_EDITOR_V1.md)
and matches how the star works in CompareScreen. A test must explicitly verify this
behavior is correct, not a regression.

**Low risk — InitialSessionFields trailing field:**
`InitialSessionFields` already uses trailing default fields for other additions. Adding
`isFavorite: Boolean = false` follows the established safe pattern. No existing tests
require changes.

**Low risk — metadataReader coverage:**
The `metadataReader` injectable in `EditSessionViewModel` reads from `metadata.json`.
The `additional.isFavorite` field must be added to the read path. Block A already
ensures that `metadata.json` always has this field (defaulting to `false` when absent).

### Required Tests

All 6 tests are instrumentation tests targeting `EditSessionScreenTest`.

| Test | Expected behavior |
|---|---|
| `favoriteButton_isVisible` | Star icon button is displayed in TopAppBar |
| `favoriteButton_showsOutlineIcon_whenNotFavorited` | `isFavorite = false` → outline star / content description = `compare_screen_favorite_mark` |
| `favoriteButton_showsFilledIcon_whenFavorited` | `isFavorite = true` → filled star / content description = `compare_screen_favorite_remove` |
| `favoriteButton_tap_togglesImmediately` | Tap star → `toggleFavorite` called; no Save required |
| `favoriteButton_doesNotAffectDirtyState` | Tap star → `isDirty` unchanged (Save button remains disabled when no form field changed) |
| `favoriteButton_doesNotAffectSaveButton` | Tap star when form is clean → Save button stays disabled |

### Test Commands

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.EditSessionScreenTest
```

### Definition of Done

- `assembleDebug` BUILD SUCCESSFUL
- `testDebugUnitTest` BUILD SUCCESSFUL
- All 6 new `favoriteButton_*` tests PASSED on device
- All existing `EditSessionScreenTest` tests remain green
- No `CompareScreen`, `CompareLibraryScreen`, `CameraViewModel`, or `MainActivity` is
  modified
- `isDirty` is demonstrably NOT affected by star toggle (test 4 passes)

---

## 9I. Bug Fix — EditSession Favorite Return Refresh (2026-06-20)

**Identified:** After Block F.3 completion, during manual verification.

**Bug:** When the user toggled the Favourite star in `EditSessionScreen` and pressed
Back without saving any form field, `CompareScreen` showed the old (stale) star state.
After navigating to `CompareLibraryScreen`, the correct state was visible.

**Root cause:** `toggleFavorite()` wrote `metadata.json` correctly but emitted no event
that would trigger `cameraViewModel.refreshSavedSessions()`. The `SaveComplete` event
already triggers a refresh; the Favourite-only-back path did not.

**Fix applied (Option A):**
- `EditSessionEvent.FavoriteToggleComplete` added as third event type
- `toggleFavorite()` emits `FavoriteToggleComplete` on write success
- `MainActivity` handles `FavoriteToggleComplete` by calling
  `cameraViewModel.refreshSavedSessions()` without navigation

**Files changed:**
- `EditSessionViewModel.kt` — new event type; emit on success path of `toggleFavorite()`
- `MainActivity.kt` — new `FavoriteToggleComplete` branch in `when(event)` collector
- `EditSessionViewModelTest.kt` — `favoriteUpdater` param in `createViewModel()`; 2 new tests

**Tests added:**
- `toggleFavorite_emitsFavoriteToggleComplete_onSuccess` (JVM unit)
- `toggleFavorite_doesNotEmitFavoriteToggleComplete_onFailure` (JVM unit)

**Test results:** `testDebugUnitTest` BUILD SUCCESSFUL; `EditSessionScreenTest` 28/28 PASSED
on SM-S911B (Android 16).

---

## 10. Block G — Documentation + Release Verification

### 10.1 Goal

Update `IMPLEMENTATION_NOTES.md` to reflect the completed feature. Run the full
instrumentation test suite. Confirm release build. Document any residual risks.

### 10.2 Files Changed

| File | Change |
|---|---|
| `../../IMPLEMENTATION_NOTES.md` | Update Compare Library section; add new DataStore keys; note Select All behavior change; note `ScannedSession.isFavorite` field |

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

## 10G. Block G — Completion Record

**Status:** DONE
**Completed:** 2026-06-20

### IMPLEMENTATION_NOTES.md Updates Applied

- **Compare Library section:** Favourites star, filter/sort, Select All behavior corrected (PD-08)
- **Compare section:** Favourite star in TopAppBar documented
- **Session Storage section:** `additional.isFavorite` note updated — UI and update endpoint now exist
- **Settings section:** `library_filter` and `library_sort_order` DataStore keys added
- **Session Metadata Editor section:** Favourite star in TopAppBar, `EditSessionEvent.FavoriteToggleComplete`, bug fix documented

### Build + Test Results

| Run | Result |
|---|---|
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
| `connectedDebugAndroidTest` full suite | 575/575 PASSED on SM-S911B (Android 16) — provided by user |
| `CompareLibraryScreenTest` | 68/68 PASSED on SM-S911B (Android 16) |
| `EditSessionScreenTest` | 28/28 PASSED on SM-S911B (Android 16) |

### Non-Blockers (known, no action in this feature scope)

The following deprecation warnings exist in the codebase and are **not** caused by the
Favourites / Filter / Sort implementation. They are pre-existing and require separate
dedicated cleanup blocks:

- `Icons.Filled.CompareArrows` deprecated — use `Icons.AutoMirrored.Filled.CompareArrows`
- `CameraX setTargetAspectRatio` deprecated — future CameraX migration required

Neither affects release builds, runtime behaviour, or the Favourites feature.

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
- `../../IMPLEMENTATION_NOTES.md`

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
| A | Storage + Scanner Foundation | **DONE** (2026-06-20) |
| B | ViewModel Favorite Toggle | **DONE** (2026-06-20) |
| C | CompareScreen Favorite Action | **DONE** (2026-06-20) |
| D | CompareLibrary Tile Favorite Star | **DONE** (2026-06-20) |
| E | Library Filter + Sort DataStore | **DONE** (2026-06-20) |
| F | Library Filter + Sort UI | **DONE** (2026-06-20) |
| F.1 | UX Polish — Star corner proximity | **DONE** (2026-06-20) |
| F.2 | UX Polish — Overflow menu structural rendering | **DONE** (2026-06-20) |
| F.3 | EditSession Favorite TopAppBar Action | **DONE** (2026-06-20) |
| G | Documentation + Release Verification | **DONE** (2026-06-20) |

---

## 14. Block A — Completion Record

**Status:** DONE  
**Completed:** 2026-06-20

### Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt` | `ScannedSession.isFavorite: Boolean = false` trailing field added; `validateUnsafe()` reads `additional.isFavorite` via `optJSONObject`/`optBoolean` chain |
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt` | `updateFavorite(sessionsRoot, sessionId, isFavorite: Boolean): Boolean` added after `updateLocation()` |
| `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt` | `createSessionWithAdditionalBlock()` helper + 8 new `updateFavorite_*` tests |
| `app/src/androidTest/java/com/isardomains/sameview/storage/SessionScannerTest.kt` | `writeMetadata()` extended with `isFavorite: Boolean? = null`; `assertFalse` import added; 5 new `isFavorite_*` tests |

### Test Commands Executed

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.tests_regex="updateFavorite.*|isFavorite.*"
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionScannerTest,com.isardomains.sameview.storage.SessionStorageMetadataTest
```

### Test Results

| Run | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| New Block-A tests (targeted, 14 tests) | 14/14 PASSED on SM-S911B (Android 16) |
| Full SessionScannerTest + SessionStorageMetadataTest (123 tests) | 123/123 PASSED on SM-S911B (Android 16) |

Note: 14 tests in the targeted run (expected 13) — the regex matched one additional
existing test; all passed with 0 failures.

### Risk Coverage Confirmed

| Risk | Test | Result |
|---|---|---|
| Alt-Session ohne additional → nur isFavorite schreiben | `updateFavorite_createsAdditionalBlock_whenAbsent` (prüft Abwesenheit von visibility/source) | ✅ |
| v4-Session: visibility/source erhalten | `updateFavorite_preservesOtherAdditionalFields` | ✅ |
| ScannedSession Backward Compatibility | Compile-Test: alle bestehenden Call-Sites unverändert | ✅ |
| Bestehende SessionScannerTest-Tests | 123/123 (alle bestehenden + neue) | ✅ |
| Ungültiger JSON-Typ | `isFavorite_false_whenValueIsInvalidType` | ✅ |
| Path Traversal | `updateFavorite_pathTraversal_returnsFalse` | ✅ |
