# FAVORITES_AND_LIBRARY_FILTERS_V1.md

## 1. Document Status

This document is the **authoritative specification** for Favorites, Library Filter,
and Library Sort in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This document defines:
- the Favorites data contract (storage read/write)
- the ViewModel contract for favorite toggling
- the Favorites UX in CompareScreen
- the Favorites UX in CompareLibraryScreen
- the Favorites UX in EditSessionScreen (decided per §18.3)
- the Library Filter and Sort system
- the Library overflow menu entry point
- i18n requirements
- accessibility requirements
- explicit non-goals
- testing expectations
- risk register

This document does NOT define:
- compare rendering (defined in `COMPARE_SESSION_RENDERING_V1.md`)
- compare flow and navigation contracts (defined in `COMPARE_FLOW_V1.md`)
- session metadata storage schema (defined in `SESSION_METADATA_V1.md`)
- session backup export (defined in `SESSION_BACKUP_EXPORT_V1.md`)
- video export (defined in `VIDEO_EXPORT_V1.md`)
- GPS guidance (defined in `GPS_RECREATION_SYSTEM_V1.md`)

**Authoritative cross-references:**

| Document | Relationship |
|---|---|
| `SESSION_METADATA_V1.md` | Defines `additional.isFavorite` field semantics and ownership |
| `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md` | Defines `additional` block creation at session save time |
| `COMPARE_FLOW_V1.md` | Defines CompareScreen and CompareLibraryScreen navigation contracts |
| `SETTINGS_UX_V1.md` | Governs settings architecture; filter and sort are not settings-screen entries |
| `IMPLEMENTATION_NOTES.md` | Tracks verified implementation state; must be updated after completion |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Architecture and change discipline rules |

---

## 2. Purpose

SameView stores compare sessions as long-lived records of photographic recreation events.
As a user's session library grows, two capabilities become essential:

1. **Curation** — the ability to mark selected sessions as favorites for quick retrieval
2. **Navigation** — the ability to filter and sort the library to find specific sessions

Both capabilities are implemented locally, offline, without any account, cloud connection,
or external data dependency.

The data foundation for Favorites already exists: `additional.isFavorite` is written as
`false` at session creation for every new session (v4 schema). This specification adds
the UI and ViewModel layer that makes that field visible and interactive.

---

## 3. Fixed Product Decisions

The following decisions are final. They must not be re-evaluated during implementation.

| # | Decision |
|---|---|
| PD-01 | Favorites are stored exclusively in `metadata.json` under `additional.isFavorite`. No secondary index or external favorites store is introduced. |
| PD-02 | Favorite status never affects compare rendering, session file structure, or session identity. |
| PD-03 | The favorite star in CompareLibraryScreen is a separate interactive element from the tile's primary tap and long-press areas. It must not trigger tile navigation or multi-select when tapped. |
| PD-04 | Library Filter and Sort are accessed exclusively through the overflow menu (⋮) in the CompareLibraryScreen normal-mode TopAppBar. No persistent filter chip, segment control, or extra button is introduced. |
| PD-05 | Library Filter and Sort preferences are persisted via SettingsRepository/DataStore in the existing `sameview_settings` store. No Settings screen entry is introduced for these preferences. |
| PD-06 | Default filter: all comparisons. Default sort: newest first. These defaults match current library behavior. |
| PD-07 | When filter = Favorites only and a tile's isFavorite is toggled to false, the tile disappears immediately from the filtered view. |
| PD-08 | Select All in multi-select mode selects all sessions in the **current filtered view**, not all sessions unconditionally. This supersedes the current "all sessions regardless of filter" behavior documented in `IMPLEMENTATION_NOTES.md`. |
| PD-09 | The favorite star in the Library tile is hidden during multi-select mode. The selection checkbox (top-right) remains the sole interactive element overlaid on the tile in that mode. |
| PD-10 | The favorite star in CompareScreen is only shown when a valid `sessionId` context is present. Transient compare views (no saved session) show no star. |
| PD-11 | Backup ZIP structure is not modified. `isFavorite` is already included in `metadata.json` and therefore already included in every backup by the full-fidelity rule. No new fields or files are added to the ZIP. |
| PD-12 | `CameraViewModel` is the single source of truth for `isFavorite` state. No screen-local duplicate of the favorite boolean is maintained. |

---

## 4. Data Layer

### 4.1 Existing Schema — `additional.isFavorite`

`additional.isFavorite` is defined in `SESSION_METADATA_V1.md §10.1` and implemented
in `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md Block B`.

Current state:
- Every new session created by the app sets `additional.isFavorite = false`
- The field is stored in the `additional` block of `metadata.json`
- The field is mutable after session save (per `SESSION_METADATA_V1.md §4`, Category 4)
- Old sessions (v2/v3) may not have an `additional` block; this is treated as `false`

No schema change is required. No new field is introduced.

### 4.2 `SessionStorage.updateFavorite()`

A new write function is added to `SessionStorage`:

```
updateFavorite(sessionsRoot, sessionId, isFavorite: Boolean): Boolean
```

Contract:
- Path traversal validation identical to `updateTitle()`, `updateLocation()`, etc.
- Reads the existing `metadata.json` for the session
- Reads or creates the `additional` JSON block
- Sets `additional.isFavorite` to the given Boolean value
- Writes the full `metadata.json` back atomically
- Returns `true` on success, `false` on any failure (invalid sessionId, missing file,
  IO error, security error)
- Must not modify any rendering contract fields, system identity fields, GPS fields,
  or any field outside the `additional` block
- When creating the `additional` block for the first time on an older session, must set
  only `isFavorite` and must not overwrite or remove pre-existing `visibility` or
  `source` values if the block already partially exists

### 4.3 `ScannedSession` Extension

`ScannedSession` is extended with a new field:

```
isFavorite: Boolean = false
```

Default value `false` ensures backward compatibility: existing call sites that create
`ScannedSession` without specifying `isFavorite` continue to compile and behave correctly.

### 4.4 `SessionScanner` — Reading `isFavorite`

`SessionScanner.validateUnsafe()` is extended to read `isFavorite` from the `additional`
block. If the `additional` block is absent, or if the `isFavorite` key is absent or
carries a non-Boolean value, the result is `false`.

Rules:
- Missing `additional` block → `isFavorite = false`
- Missing `isFavorite` field inside an otherwise-present `additional` block → `isFavorite = false`
- Invalid (non-Boolean) value → `isFavorite = false` (fail-safe)
- v2 and v3 sessions without `additional` block → `isFavorite = false`

---

## 5. ViewModel Layer

### 5.1 `toggleFavorite()` Contract

`CameraViewModel` receives a new function:

```
toggleFavorite(sessionId: String)
```

Behavior:
1. Determines the current `isFavorite` value for the session from the in-memory
   `savedSessions` StateFlow
2. Optimistically applies the flipped value to `savedSessions` immediately, before the
   storage write completes
3. Calls `SessionStorage.updateFavorite(sessionsRoot, sessionId, newValue)` on the IO
   dispatcher
4. On success: no further action needed; the in-memory state already reflects the new value
5. On failure: reverts `savedSessions` to the previous `isFavorite` value for that session
   and emits a `favorite_update_failed` Snackbar event

The optimistic update ensures zero-perceptible-latency for the user.

### 5.2 `savedSessions` StateFlow — In-Memory Update

On a favorite toggle, only the affected `ScannedSession` entry in `savedSessions` is
updated. No full rescan of the sessions directory is performed.

The update replaces the affected session with a copy carrying the new `isFavorite` value.
All other sessions in the list are unchanged.

### 5.3 CompareScreen Favorite State

`CameraViewModel` is the **single source of truth** for `isFavorite`. CompareScreen
derives the current favorite status by observing the relevant `ScannedSession` entry
from `savedSessions` via the ViewModel.

CompareScreen must not maintain a separate local copy of the `isFavorite` boolean that
is independently synchronized with the ViewModel. Any approach that duplicates the
isFavorite state outside the ViewModel violates PD-12 and is not permitted.

When the user taps the star in CompareScreen:
- `CameraViewModel.toggleFavorite(sessionId)` is called
- The ViewModel updates `savedSessions` optimistically
- CompareScreen reacts to the updated ViewModel state via its existing observation path

---

## 6. Favorites UX — CompareScreen

### 6.1 TopAppBar Structure

When a valid session context is present (`sessionId != null`), the Favorite star is
added as a TopAppBar action alongside the existing Create Video and Delete Session actions.

**Product intent for the TopAppBar action area:**
- The Favorite star, Create Video, and Delete Session are the preferred primary actions
- The overflow (⋮) remains available for secondary actions (Edit Session, Backup Session)
- The Favorite star is placed before Create Video to reflect the action ordering:
  curation (soft) → creation (positive) → deletion (destructive)

The exact arrangement of these icons in the TopAppBar may be adjusted during
implementation to accommodate compact-width screens, without requiring a spec change,
as long as all three primary actions remain accessible and the product intent is preserved.

When no session context is present (transient compare view), the TopAppBar shows only
the back navigation. No Favorite star, no Create Video, no Delete — unchanged from
current transient CompareScreen behavior.

### 6.2 Icon Visual States

| State | Icon | Tint |
|---|---|---|
| Not favorited | Outline star icon | Standard icon tint (`onSurface` or equivalent) |
| Favorited | Filled star icon | Theme-defined amber/yellow favorite color |

The filled star uses a warm amber/yellow that conveys "marked" while remaining legible
against the dark app background. This color is defined as a named theme constant
(e.g., `SameViewStarFavorited`) and must not be hardcoded as a hex value. The exact
color value is an implementation decision within the amber/yellow range.

No animation is required for the toggle transition. An instant icon swap is sufficient.

### 6.3 Toggle Behavior

Tapping the star:
1. Calls `CameraViewModel.toggleFavorite(sessionId)`
2. The ViewModel updates state optimistically; the icon reflects the new state
   as soon as the Compose recomposition occurs
3. On ViewModel failure: icon reverts to its previous state via the ViewModel revert;
   Snackbar shows `compare_session_favorite_update_failed` message

The star tap must not:
- Navigate away from CompareScreen
- Open any dialog
- Interfere with the slider, fullscreen mode, or compare rendering
- Block or disable any other TopAppBar action

### 6.4 Availability

The Favorite star is shown if and only if: `sessionId != null`.

This is consistent with the availability rule for the Delete Session icon and the Create
Video icon.

---

## 7. Favorites UX — CompareLibraryScreen

### 7.1 Star Icon Position and Design

Each `CompareSessionTile` shows a star icon overlaid on the **top-left corner** of the
image area (above the two thumbnails, not in the text area below them).

Visual design:
- Icon: outline star (not favorited) / filled star (favorited)
- Tint: consistent with §6.2 — standard tint for outline, theme-defined amber/yellow for filled
- Visual icon size: approximately 18–20 dp
- Touch target: minimum 48 dp × 48 dp

**Visual icon positioning within the touch target:**
The visual icon must appear near the TopStart corner of the tile, NOT centered within
the touch target area. The goal is visual corner-proximity consistent with the Selection
Checkbox at TopEnd: both should appear to sit at their respective tile corners when
viewed at a glance.

A touch target of 48 dp with a centered icon places the icon center approximately 28 dp
from the corner — which reads as "inside the image," not "at the corner." The correct
approach: the icon is anchored to the TopStart edge of the touch target, such that the
visual icon center is approximately 12–16 dp from the tile corner. The touch target then
extends inward to cover the required 48 dp, not outward from the icon.

Exact inner padding and anchor alignment are implementation decisions, constrained by
this visual goal: the star should look like it belongs to the corner, the same way the
Selection Checkbox belongs to its corner.

Positioning:
- Touch target: `Alignment.TopStart` of the tile's root container
- The touch target may start at or near the tile corner (0–4 dp outer padding)
- The visual icon anchors to TopStart within the touch target, not to its center

Visual contrast aid:
- A subtle semi-transparent scrim behind the icon is recommended to ensure
  legibility over both bright and dark thumbnail images
- The scrim should be sized and positioned to match the icon's anchor, not the full
  48 dp touch target center
- Scrim radius and opacity are implementation decisions; prefer minimal visual weight
  (e.g., 20–22 dp radius, 25–35 % opacity) to avoid the scrim appearing as a
  secondary UI element

### 7.2 Gesture Behavior

The following behavioral requirements must hold. The implementation technique is left to
the implementer:

**Required behavior:**
- Tapping the star toggles the favorite status for that session
- Tapping the star must not open the session (must not invoke the session-open callback)
- Tapping the star must not activate multi-select mode
- Long-pressing on the tile body (outside the star area) must still activate multi-select

The star's interactive area must be independent from the tile's primary tap and long-press
behavior. The implementation must ensure these interactions do not interfere with each
other.

### 7.3 Behavior in Multi-Select Mode

When `isSelectionMode == true`:
- The favorite star is **not rendered** (hidden via conditional composable)
- The selection checkbox (top-right, existing) remains visible and functional
- The tile's tap behavior toggles the selection state (unchanged)

When `isSelectionMode` returns to `false`:
- The favorite star re-appears for each tile according to its `isFavorite` value

### 7.4 Immediate Visual Update

When the star is tapped in Library view:
1. `CameraViewModel.toggleFavorite(sessionId)` is called
2. The ViewModel updates `savedSessions` optimistically (§5.2)
3. The Compose recomposition reflects the new `isFavorite` immediately on the tile
4. If filter = `FILTER_FAVORITES` and the session was just un-favorited: the tile
   disappears from the list on the next recomposition (session is filtered out)
5. On write failure: ViewModel reverts `savedSessions`; Snackbar shows
   `compare_session_favorite_update_failed` message

No full rescan. No navigation. No delay.

---

## 8. Library Filter

### 8.1 Entry Point

Filter options are accessible exclusively through the overflow menu (⋮) in the
CompareLibraryScreen **normal-mode** TopAppBar.

Normal-mode TopAppBar (new structure):

```
← Back    Comparisons                               ⋮
```

The ⋮ is a new element in normal mode. It did not exist previously.

The selection-mode TopAppBar is unchanged:

```
✗    N selected    [Select All]    [Archive]    [Delete]
```

There is no overflow in selection mode.

### 8.2 Filter Options

The filter section of the overflow menu contains exactly two options:

| User-facing label | Internal identifier | Default |
|---|---|---|
| All comparisons | `FILTER_ALL` | Yes |
| Favorites only | `FILTER_FAVORITES` | No |

Only one filter option is active at a time. The currently active option shows a
leading checkmark indicator consistent with Material 3 dropdown menu semantics.

Filter is applied to the in-memory session list. No re-scan of the sessions directory
is triggered when the filter changes.

### 8.3 Empty State — Favorites Only, No Favorites

When `filter == FILTER_FAVORITES` and the filtered session list is empty, a dedicated
empty state is shown. This is distinct from the "no sessions at all" empty state.

**Favorites-empty-state content:**
- Icon: filled star icon (large, decorative, reduced opacity)
- Title: string resource `compare_library_empty_favorites_title` → "No favorites yet"
- Body: string resource `compare_library_empty_favorites_body` → "Tap the star on any
  comparison to add it here."
- No CTA button

The "no sessions at all" empty state (existing) is unchanged.

### 8.4 Persistence

Filter and sort preferences are persisted through `SettingsRepository` / DataStore in the
existing `sameview_settings` store. No new DataStore file or separate preference store
is introduced.

**No Settings screen entry is introduced for these preferences.** They are internal
presentation preferences for the Compare Library and are not surfaced in the Settings
screen (`SettingsScreen`). `SETTINGS_UX_V1.md` is not affected by this feature.

Filter DataStore key:

| Key | Type | Default |
|---|---|---|
| `library_filter` | String | `"all"` |

Allowed stored values: `"all"` → `FILTER_ALL`; `"favorites"` → `FILTER_FAVORITES`.
Unknown values fall back to `FILTER_ALL`.

---

## 9. Library Sort

### 9.1 Sort Options

The sort section of the overflow menu contains exactly two options:

| User-facing label | Internal identifier | Default |
|---|---|---|
| Newest first | `SORT_NEWEST_FIRST` | Yes |
| Oldest first | `SORT_OLDEST_FIRST` | No |

Sort is applied by `capture.timestampMs` (canonical capture timestamp, read from
`ScannedSession.timestamp`).

`SORT_NEWEST_FIRST`: descending by timestamp — identical to current library behavior.
`SORT_OLDEST_FIRST`: ascending by timestamp.

### 9.2 Default and Persistence

Default: `SORT_NEWEST_FIRST`. This matches the current library display order.

Sort preferences are persisted through `SettingsRepository` / DataStore in the existing
`sameview_settings` store alongside the filter preference (§8.4). **No Settings screen
entry is introduced.** This preference is internal to the Compare Library view.

| Key | Type | Default |
|---|---|---|
| `library_sort_order` | String | `"newest_first"` |

Allowed stored values: `"newest_first"` → `SORT_NEWEST_FIRST`;
`"oldest_first"` → `SORT_OLDEST_FIRST`. Unknown values fall back to `SORT_NEWEST_FIRST`.

### 9.3 Filter + Sort Combination

Sort is always applied **after** filter. The processing order is:

```
savedSessions (full list)
  → apply filter (FILTER_ALL or FILTER_FAVORITES)
  → apply sort (SORT_NEWEST_FIRST or SORT_OLDEST_FIRST)
  → result passed to LazyVerticalGrid
```

This derivation is a pure in-memory transformation. No re-scan is triggered by filter
or sort changes. When the sort changes, scroll position resets to the top of the grid.

---

## 10. Overflow Menu — CompareLibraryScreen Normal Mode

### 10.1 Full Menu Structure

```
┌─────────────────────────────────┐
│  Filter                         │  ← non-clickable section header
│  ✓  All comparisons             │  ← active option (checkmark)
│     Favorites only              │  ← inactive option
│  ─────────────────────          │  ← visual divider
│  Sort by                        │  ← non-clickable section header
│  ✓  Newest first                │  ← active option (checkmark)
│     Oldest first                │  ← inactive option
└─────────────────────────────────┘
```

Section headers ("Filter", "Sort by") are non-clickable text labels styled as secondary
text within the DropdownMenu.

Selecting any menu item applies the new preference immediately, persists it, and closes
the menu. Selecting the already-active option is a no-op (menu closes, no state change).

### 10.2 Interaction with Existing Library TopAppBar

In normal mode, the existing TopAppBar currently has a back navigation icon and a title.
After this feature: the TopAppBar also has an overflow icon (end) that opens the menu
described in §10.1. No other changes to the normal-mode TopAppBar are made.

### 10.3 Visual Design Language — AppBar Overflow Menus

All TopAppBar overflow menus in SameView must share the same visual design language.
This applies to the CameraScreen overflow (Settings / About) and the CompareLibraryScreen
overflow (Filter / Sort) equally.

**Shared design base:**
- Shape: Material 3 menu default shape (consistent rounded corners)
- Container color: Material 3 menu default surface / container color
- Base typography: Material 3 `bodyLarge` for all interactive menu items
- Padding and elevation: Material 3 menu defaults

**Differences are permitted only by content, not by style:**
- A simple navigation menu (Camera) has fewer items and no internal structure
- A state-change menu (Library) has sections, active indicators, and a divider
- These structural differences are appropriate and expected
- They must not produce a different visual "feel" due to styling divergence

**Section headers within the Library overflow:**
Section headers ("Filter", "Sort by") must be rendered as semantic label elements —
not as disabled interactive items. A disabled `DropdownMenuItem` conveys "unavailable
action," which is semantically incorrect for a category label. The correct approach is
a plain text element with secondary styling (`labelSmall`, `onSurfaceVariant`), rendered
directly inside the menu without the interactive-item wrapper.

**Active state indicator (checkmark):**
Active options show a leading checkmark icon. All options in a group must maintain
consistent horizontal text alignment, regardless of whether a checkmark is shown.
If one option in a group shows a leading icon, all options in that group must reserve
the same leading space — either with the checkmark icon or with a consistent spacer —
so that text does not shift horizontally between active and inactive states.

This design language rule applies to any future overflow menus added to SameView
TopAppBars. It is NOT a requirement to build a shared technical component. The goal
is visual consistency, not code reuse.

---

## 11. Select All Behavior in Filtered View

Current behavior (from `IMPLEMENTATION_NOTES.md`):
> "Select All selects all sessions in the complete scanned session list, not just
> visible tiles."

**This behavior is superseded by PD-08.**

New behavior:
- When filter = `FILTER_ALL`: Select All selects all sessions — same as before
- When filter = `FILTER_FAVORITES`: Select All selects only the currently visible
  favorited sessions

The Select All action operates on the **filtered session list** (the same list currently
rendered in the grid), not on the full `savedSessions`.

`IMPLEMENTATION_NOTES.md` must be updated to reflect this change.

---

## 12. i18n

### 12.1 New String Resources Required

All new user-facing text uses string resources. The following keys must be added to
`values/strings.xml` and `values-de/strings.xml`.

**Favorite star content descriptions (not user-visible text, but screen reader text):**

| Key | English value |
|---|---|
| `compare_screen_favorite_mark` | `"Mark as favorite"` |
| `compare_screen_favorite_remove` | `"Remove from favorites"` |
| `compare_library_tile_favorite_mark` | `"Mark as favorite"` |
| `compare_library_tile_favorite_remove` | `"Remove from favorites"` |

**Library overflow — Filter section:**

| Key | English value |
|---|---|
| `compare_library_filter_header` | `"Filter"` |
| `compare_library_filter_all` | `"All comparisons"` |
| `compare_library_filter_favorites` | `"Favorites only"` |

**Library overflow — Sort section:**

| Key | English value |
|---|---|
| `compare_library_sort_header` | `"Sort by"` |
| `compare_library_sort_newest_first` | `"Newest first"` |
| `compare_library_sort_oldest_first` | `"Oldest first"` |

**Favorites empty state:**

| Key | English value |
|---|---|
| `compare_library_empty_favorites_title` | `"No favorites yet"` |
| `compare_library_empty_favorites_body` | `"Tap the star on any comparison to add it here."` |

**Error feedback:**

| Key | English value | Rationale |
|---|---|---|
| `compare_session_favorite_update_failed` | `"Couldn't update favorite"` | Namespaced to the feature area; "update" is more precise than "toggle" (internal technical term); consistent with `compare_screen_title_save_failed` naming style |

### 12.2 Sentence Case Rule

All new visible strings use sentence case per the `IMPLEMENTATION_NOTES.md` English
Sentence Case Rule: first word capitalized, all others lowercase unless proper noun.
Examples: "Favorites only", "Newest first", "All comparisons", "No favorites yet",
"Mark as favorite" — all correct.

### 12.3 German Strings

German translations are provided in `values-de/strings.xml` following the existing
informal tone rule (`du`/`dir`/`dein`). German keys mirror English keys exactly.
German translation is in scope for this feature.

---

## 13. Accessibility

### 13.1 Star Content Descriptions — CompareScreen

The TopAppBar star icon button uses a dynamic content description that reflects the
current state:
- When not favorited: `compare_screen_favorite_mark` → "Mark as favorite"
- When favorited: `compare_screen_favorite_remove` → "Remove from favorites"

The content description must update immediately when the ViewModel state changes.

### 13.2 Star Content Descriptions — CompareLibrary Tile

The star element in the tile uses a dynamic content description reflecting its state:
- When not favorited: `compare_library_tile_favorite_mark`
- When favorited: `compare_library_tile_favorite_remove`

The tile's existing semantics should also communicate favorite status. A `stateDescription`
semantic property on the tile root is the appropriate mechanism, so TalkBack can announce
the tile's current favorite status alongside its primary description without conflating
the star's interactive semantics with the tile's navigation semantics.

### 13.3 Touch Targets

- CompareScreen star action: standard Material 3 `IconButton` touch target — no additional
  sizing required
- Library tile star: minimum 48 dp × 48 dp touch target (icon visually smaller, centered
  within the touch target area)

TalkBack must be able to focus the star independently from the tile itself. The star
and the tile must be separate focusable semantic nodes.

### 13.4 Overflow Menu Accessibility

Section headers ("Filter", "Sort by") in the dropdown must be marked as non-interactive
for TalkBack so they are not announced as tappable items.

---

## 14. Non-Goals (V1)

The following are explicitly out of scope. They must not be pre-implemented, hinted at,
or stubbed in V1:

- Tags (`content.tags`) — schema ready but no UI in this feature
- Free-text search in the Library
- Favorites synchronization of any kind
- Account or cloud features
- Map view or location-grouped view
- Side-panel layout or new navigation levels
- Bulk-edit (e.g., mark multiple sessions as favorite via multi-select)
- Undo for session deletion
- Sort by title, location, or reference date
- Favorites groups or named collections
- Sharing or export functionality as part of this feature
- Changes to compare rendering
- Changes to session backup ZIP structure
- Changes to video export pipeline
- Filter by date range, location, or reference date
- Settings screen entries for Library Filter or Sort

---

## 15. Testing Expectations

### 15.1 Unit Tests — `SessionStorage`

| Test | Condition | Expected |
|---|---|---|
| `updateFavorite_setsTrue` | Call with `isFavorite = true` | Field is `true` in written JSON |
| `updateFavorite_setsFalse` | Call with `isFavorite = false` | Field is `false` in written JSON |
| `updateFavorite_togglesFromTrueToFalse` | Write true, then write false | Final value is `false` |
| `updateFavorite_preservesAllOtherFields` | Call with `isFavorite = true` | All other metadata fields unchanged |
| `updateFavorite_preservesOtherAdditionalFields` | Existing `visibility = "private"`, `source = "sameview"` | Both preserved after update |
| `updateFavorite_pathTraversal_returnsFalse` | `sessionId = "../other"` | Returns `false`, no write |
| `updateFavorite_missingSession_returnsFalse` | Non-existent sessionId | Returns `false` |
| `updateFavorite_createsAdditionalBlock_whenAbsent` | Session has no `additional` block | Block created with `isFavorite` set |

### 15.2 Unit Tests — `SessionScanner`

| Test | Condition | Expected |
|---|---|---|
| `isFavorite_true_whenSetInMetadata` | `additional.isFavorite = true` | `ScannedSession.isFavorite == true` |
| `isFavorite_false_whenSetFalseInMetadata` | `additional.isFavorite = false` | `ScannedSession.isFavorite == false` |
| `isFavorite_false_whenAdditionalBlockAbsent` | No `additional` block (v2/v3 session) | `ScannedSession.isFavorite == false` |
| `isFavorite_false_whenFieldAbsentInAdditionalBlock` | `additional` present but no `isFavorite` key | `ScannedSession.isFavorite == false` |
| `isFavorite_false_whenValueIsInvalidType` | `additional.isFavorite = "yes"` (string) | `ScannedSession.isFavorite == false` |

### 15.3 Unit Tests — `CameraViewModel`

| Test | Condition | Expected |
|---|---|---|
| `toggleFavorite_flipsInMemoryStateOptimistically` | Initial `isFavorite = false`; call `toggleFavorite` | `savedSessions` immediately shows `isFavorite = true` for that session |
| `toggleFavorite_revertsOnWriteFailure` | `updateFavorite` returns `false` | `savedSessions` reverts to `isFavorite = false`; error event emitted |
| `toggleFavorite_onlyAffectsTargetSession` | Two sessions; toggle one | Other session's `isFavorite` unchanged |

### 15.4 Instrumentation Tests — `CompareScreenTest`

| Test | Condition | Expected |
|---|---|---|
| `favoriteButton_isVisibleWhenSessionIdPresent` | `sessionId != null` | Star icon visible |
| `favoriteButton_isNotVisibleWhenSessionIdNull` | `sessionId == null` | Star icon not visible |
| `favoriteButton_showsOutlineIconWhenNotFavorited` | `isFavorite = false` | Outline star rendered |
| `favoriteButton_showsFilledIconWhenFavorited` | `isFavorite = true` | Filled star rendered |
| `favoriteButton_tap_invokesViewModelToggle` | Tap star | ViewModel `toggleFavorite` called |
| `favoriteButton_doesNotTriggerNavigation` | Tap star | No navigation event |
| `favoriteButton_doesNotAffectSlider` | Tap star | Slider state unchanged |

### 15.5 Instrumentation Tests — `CompareLibraryScreenTest`

| Test | Condition | Expected |
|---|---|---|
| `tile_starVisible_whenNotInSelectionMode` | Normal mode | Star icon visible on tile |
| `tile_starHidden_whenInSelectionMode` | After long-press | Star icon not visible |
| `tile_starTap_doesNotOpenSession` | Tap star | Session-open callback NOT invoked |
| `tile_starTap_doesNotActivateMultiSelect` | Tap star | Selection mode NOT activated |
| `tile_starTap_invokesToggleFavorite` | Tap star | `toggleFavorite` invoked with correct sessionId |
| `longPress_stillActivatesMultiSelect_withStarPresent` | Long-press tile body | Multi-select activated |
| `filter_favorites_showsOnlyFavoritedSessions` | 1 favorited, 1 not; filter = Favorites | Only favorited tile visible |
| `filter_all_showsAllSessions` | 1 favorited, 1 not; filter = All | Both tiles visible |
| `sort_newestFirst_correctOrder` | Two sessions; sort = Newest | Newer session appears first |
| `sort_oldestFirst_correctOrder` | Two sessions; sort = Oldest | Older session appears first |
| `filterAndSort_combined_correct` | 3 sessions, 2 favorited; filter = Favorites, sort = Oldest | 2 favorited sessions, older first |
| `emptyState_favorites_shownWhenNoFavorites` | 1 unfavorited session; filter = Favorites | Favorites empty state visible |
| `emptyState_favorites_disappears_whenFavoriteAdded` | Empty favorites view; toggle star on a session | Empty state disappears, tile appears |
| `selectAll_inFavoritesFilter_selectsOnlyFavorites` | 1 favorited + 1 unfavorited; filter = Favorites; select all | Only favorited session selected |
| `overflowMenu_isVisibleInNormalMode` | Normal mode | Overflow ⋮ button visible |
| `overflowMenu_containsFilterAndSortSections` | Open overflow | "Filter" and "Sort by" sections present |
| `filterAndSort_persistsAfterScreenReopen` | Set Favorites filter; reopen screen | Filter still Favorites |

### 15.6 Accessibility Tests

| Test | Condition | Expected |
|---|---|---|
| `starContentDescription_markAsFavorite_whenNotFavorited` | Star not filled | Content description = "Mark as favorite" |
| `starContentDescription_removeFromFavorites_whenFavorited` | Star filled | Content description = "Remove from favorites" |

---

## 16. Risks and Mitigations

### R-01 — Gesture Separation in Library Tile

**Risk:** The star's tap behavior triggers the tile's primary action (open session or
activate multi-select) instead of — or in addition to — the favorite toggle.

**Severity:** High (breaks primary navigation UX contract)

**Mitigation:**
- The star must be implemented as an interactive element separate from the tile's primary
  tap and long-press targets
- The implementation must verify that a tap landing on the star does not propagate to
  the tile's session-open or selection-trigger behavior
- Tests `tile_starTap_doesNotOpenSession` and `tile_starTap_doesNotActivateMultiSelect`
  are mandatory before release

### R-02 — TopAppBar Space in CompareScreen

**Risk:** Adding the Favorite star alongside Create Video, Delete, and Overflow may
produce a crowded TopAppBar on narrow Compact-width screens.

**Severity:** Medium (visual quality, not functional breakage)

**Mitigation:**
- PD-12 explicitly allows the implementer to adjust the exact arrangement to accommodate
  compact widths, provided all three primary actions remain accessible
- Manual verification on a 360 dp portrait screen is required before release

### R-03 — Immediate UI Refresh After Favorite Toggle

**Risk:** After toggling isFavorite in the Library tile, the tile icon does not update
until the next full session re-scan, creating a stale state.

**Severity:** High (user-visible inconsistency)

**Mitigation:**
- The optimistic in-memory update in `toggleFavorite()` (§5.1–5.2) ensures `savedSessions`
  carries the new value before the IO write completes, triggering immediate recomposition
- No full re-scan is required for a single favorite toggle

### R-04 — DataStore Persistence Failure

**Risk:** Reading or writing filter/sort preferences fails; the app starts with an
undefined state.

**Severity:** Low (defaults are well-defined and safe)

**Mitigation:**
- DataStore reads use the existing `SettingsRepository` catch-with-default pattern
- Unknown stored values fall back to defaults (§8.4, §9.2)
- Failure is not surfaced as a user-visible error; the safe default is applied silently

### R-05 — Backward Compatibility of Old Sessions

**Risk:** v2/v3 sessions without an `additional` block cause incorrect `isFavorite`
parsing or a crash in `SessionScanner`.

**Severity:** Medium (could affect all pre-v4 sessions)

**Mitigation:**
- `SessionScanner` must treat absent `additional` block as `isFavorite = false` (§4.4)
- `SessionScannerTest.isFavorite_false_whenAdditionalBlockAbsent` must pass before release

### R-06 — Multi-Select Mode Regression

**Risk:** The star interactive element breaks existing long-press multi-select activation
or selection-count tracking.

**Severity:** High (existing functionality regression)

**Mitigation:**
- The star must not use long-press as an interaction
- The star area must not intercept long-press events intended for the tile body
- Existing test `longPress_activatesSelectionModeAndSelectsItem` must remain green
- New test `longPress_stillActivatesMultiSelect_withStarPresent` verifies no regression

### R-07 — Select All Behavior Change

**Risk:** The intentional change to Select All (PD-08) breaks the existing test
`selectAll_selectsAllSessions`.

**Severity:** Medium (known and intentional breakage; not a product regression)

**Mitigation:**
- The affected test must be updated to reflect the new filter-aware behavior
- New test `selectAll_inFavoritesFilter_selectsOnlyFavorites` is added

### R-08 — Release / Privacy Implications

**Assessment:** None.

`additional.isFavorite` is already stored in every new session's `metadata.json` (value:
`false`) since Session Metadata V4 Block B. This feature adds the ability to set it to
`true`. No new data category is introduced. The field carries no location, identity, or
personal data. No permissions are required. No network communication is introduced.
Backup export includes `isFavorite` unchanged under the existing full-fidelity rule.

---

## 18. Open UX Topics and Future Extensions

The following items were identified during manual verification after initial implementation.
They are documented here for completeness. They do not block Block G (Release Verification)
but must be resolved before or alongside it.

### 18.1 UX Polish — Favorite Star Corner Proximity (Pre-Block-G)

During manual verification, the favorite star in the CompareLibrary tile was found to
appear visually too far from the tile corner compared to the Selection Checkbox at TopEnd.

**Root cause:** The visual icon was centered within the 48 dp touch target (icon center at
~28 dp from corner). The Selection Checkbox visual indicator sits at ~14 dp from its
corner. The 14 dp visual gap creates a perception that the star is "inside the image"
rather than "at the corner."

**Required correction (pre-Block-G):** The visual icon must anchor to the TopStart edge
of the touch target, not to its center. See §7.1 for the corrected positioning spec.
Touch target (48 dp minimum) and accessibility behavior are unchanged. The scrim behind
the icon should be sized proportionally to the icon's new anchor position, not to the
touch target center.

This is a visual-only correction. No behavioral change.

### 18.2 UX Polish — Library Overflow Menu Structural Consistency (Pre-Block-G)

During manual verification, the CompareLibrary overflow menu was found to render section
headers as disabled `DropdownMenuItem` elements. This conveys "unavailable action" rather
than "category label," which is semantically incorrect.

**Required correction (pre-Block-G):** Section headers must be rendered as non-interactive
text labels, not as disabled menu items. See §10.3 for the visual design language
requirement. Interactive items within each section must maintain consistent horizontal
text alignment between active and inactive states (leading icon slot must be reserved
uniformly, not conditionally).

This is a structural rendering correction. No functional change.

### 18.3 EditSession Favorite Star — Pre-Block-G (Decided)

**Decision: Variant A — Favorite star in the EditSessionScreen TopAppBar.**

This decision was made after manual verification confirmed that a user editing session
metadata has no visual indication of whether the session is favorited, and no way to
change that status from the editor.

**Decided behavior:**
- A Favorite star is added to the TopAppBar `actions` slot of `EditSessionScreen`
- Visual states: outline star (not favorited) / filled star with `SameViewStarFavorited`
  tint (favorited) — consistent with CompareScreen §6.2
- The star toggles `isFavorite` status **immediately** via `SessionStorage.updateFavorite()`
- The star does NOT affect `isDirty` — it is architecturally separate from the form fields
- The Discard dialog (triggered by Back with unsaved form changes) applies ONLY to form
  fields (title, description, reference date, location). A star toggle that has already
  been written to disk is NOT reverted when the user discards form changes.
- Availability: star is always visible in EditSessionScreen (sessionId is always present)
- The TopAppBar `actions` slot is currently empty — no crowding with existing elements

**Pattern consistency:**
- CompareScreen: star in TopAppBar → toggles immediately ✅
- CompareLibraryScreen: star on tile → toggles immediately ✅
- EditSessionScreen: star in TopAppBar → toggles immediately ✅

This is the third Favorites entry point in the app. The unified mental model:
"Tapping the star always takes effect immediately, everywhere."

**Authoritative detail spec:** `SESSION_METADATA_EDITOR_V1.md §20` (added as part of
this decision)

**Implementation:** Block F.3 in `FAVORITES_AND_LIBRARY_FILTERS_V1_IMPLEMENTATION_PLAN.md`
(required before Block G)

---

## 17. Relationship to Other Specifications

| Specification | Relationship |
|---|---|
| `SESSION_METADATA_V1.md` | Defines `additional.isFavorite` as a Category 4 (User Content, mutable) field. This spec implements the UI and storage write path for that field. |
| `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md` | Block B implemented the `additional` block at session creation. `updateFavorite()` follows the same patterns as `updateTitle()`, `updateLocation()`, `updateReferenceDate()`. |
| `COMPARE_FLOW_V1.md` | The CompareScreen TopAppBar action area (§5.1 of VIDEO_EXPORT_V1.md) is extended by this spec to include the Favorite star. The CompareLibraryScreen (§30) is extended with the star on tiles, the overflow menu, and filter/sort behavior. |
| `COMPARE_SESSION_RENDERING_V1.md` | Not affected. Favorites have no rendering role. The compare pipeline is architecturally isolated from all user content fields. |
| `SESSION_BACKUP_EXPORT_V1.md` | Not affected. `isFavorite` is already included in backups via the full-fidelity rule. No ZIP structure change. |
| `VIDEO_EXPORT_V1.md` | Not affected. Video export does not read `isFavorite`. |
| `RESPONSIVE_LAYOUT_SYSTEM_V1.md` | Not affected. The tile layout changes (star overlay) do not alter column count, grid height, or breakpoint behavior. The overflow menu in the Library TopAppBar is valid at all width classes. |
| `SETTINGS_UX_V1.md` | Library filter and sort preferences are persisted through `SettingsRepository`/DataStore in the existing `sameview_settings` store. No Settings screen entry and no Settings screen UI change is introduced by this feature. |
| `IMPLEMENTATION_NOTES.md` | Must be updated after implementation: Select All behavior change (PD-08), Favorites star in Library tiles, Filter/Sort overflow in Library, Favorite star in CompareScreen, new DataStore keys, `ScannedSession.isFavorite` field. |
